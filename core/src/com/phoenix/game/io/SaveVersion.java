package com.phoenix.game.io;

import com.badlogic.gdx.utils.Array;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.core.Time;
import com.phoenix.game.core.Version;
import com.phoenix.game.ctype.Content;
import com.phoenix.game.ctype.ContentType;
import com.phoenix.game.ctype.MappableContent;
import com.phoenix.game.entities.EntityGroup;
import com.phoenix.game.entities.traits.SaveTrait;
import com.phoenix.game.game.DefaultWaves;
import com.phoenix.game.game.Rules;
import com.phoenix.game.game.Team;
import com.phoenix.game.game.Teams;
import com.phoenix.game.maps.Map;
import com.phoenix.game.struct.StringMap;
import com.phoenix.game.type.TypeID;
import com.phoenix.game.util.io.CounterInputStream;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.WorldContext;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

import static com.phoenix.game.Vars.*;

/**
 * 当前存档格式的读写实现。参照 Mindustry {@code mindustry.io.SaveVersion} 移植。
 *
 * <p>它把"一个存档"拆成 4 个区块：{@code meta}（元数据）/ {@code content}（内容名字表）/
 * {@code map}（地形 + 方块 + 方块实体）/ {@code entities}（队伍数据 + 可存档实体）。
 * 子类（{@code versions/Save1..Save3}）只覆写"版本差异"的部分。
 *
 * <p><b>本项目的整体关系</b>：本项目 {@link SaveIO} 是**重写版**（自有 PHNX 文件头 + 整体 zlib +
 * 名字表式的 content 区），区块逻辑内联在它自己里，不走 {@link SaveFileReader}；
 * 所以本类与 {@code versions/*} 是**刻意保留的兼容层**：能编译、能构造，但**刻意不接线**
 * （本项目不读原版 MSAV —— {@code SaveIO.readHeader} 遇到 {@code MSAV} 魔数直接抛
 * {@code unsupportedOriginalSave()}）。
 * 分派点**已就位**：{@code SaveIO.readHeader} 会识别 {@code MSAV} 魔数并走
 *      {@code SaveIO.unsupportedOriginalSave()}（目前只报错）。等真要读原版存档时，
 *      把那个方法体换成"调用 {@code SaveVersion.read(...)}"即可，{@code SaveIO} 的调用方不用动。
 *
 * <p><b>与原版的差异 + 刻意降级/等价替代（逐条）</b>
 * <ul>
 *   <li><b>{@code SaveMeta} 类型不同</b>：原版 {@code getMeta} 返回 {@code mindustry.io.SaveMeta}
 *       （含 version/build/mods/stats 与 {@code Map} 对象）。本项目 {@code io/SaveMeta} 是**自有格式**的
 *       JSON 元数据快照（字段集完全不同，且原版 {@code SaveMeta.java} 不在本批）→
 *       {@link #getMeta(DataInput)} 只映射两边都有的标量（wave/wavetime/playtime/saved/mapname/width/height），
 *       version/build/mods/stats/rules 全部 TODO。</li>
 *   <li><b>{@code Tile} 缺少 {@code floorID()/overlayID()/blockID()}</b> →
 *       分别写成 {@code floor().id / overlay().id / blockRaw().id}
 *       （注意用 {@code blockRaw()} 而不是 {@code block()}：本项目 {@code block()} 会解析多格链接，
 *       存档必须写"本格实际存的方块"，否则卫星格会被写成中心方块、拆散多格建筑）。</li>
 *   <li><b>{@code World.rawTile()} 已存在</b>（S1 批次）→ 照原版用 {@code world.rawTile(x, y)}
 *       （⚠ 本项目 {@code rawTile} 与原版一致<b>不做越界检查</b>，调用点仍保留自己的 null 兜底分支）。</li>
 *   <li><b>{@code TileEntity} 的流类型是具体的</b>：本项目 {@code TileEntity.write(DataOutputStream)} /
 *       {@code read(DataInputStream, byte)}，而本类（照原版）用的是 {@code DataOutput}/{@code DataInput} →
 *       在区块回调里做一次强转（{@code SaveFileReader.writeChunk/readChunk} 只会把
 *       {@code dataBytes/dataBytesSmall} 或调用方传进来的 {@code DataInputStream} 交给我们，所以强转安全）。
 *       另外原版 {@code entity.version()} 在本项目叫 {@code revision()}。</li>
 *   <li><b>{@code Teams} 没有 {@code TeamData} 包装类</b>（原版 {@code state.teams.getActive()} 用来存
 *       "被打坏的方块"以便重进时还原）→ 本项目 {@code Teams.getActive()} 返回"队伍本身"、
 *       {@code Teams.brokenBlocks(team)} 存 {@code Teams.BrokenBlock}（字段已存在，{@code Teams.java:39/136}）；
 *       {@link #writeEntities(DataOutput)} / {@link #readEntities(DataInput)} 已按原格式读写
 *       （**已接线**：不再"写 0 个队伍 / 读后丢弃"；原版 {@code SaveVersion.java:215-229 / 258-267}）。</li>
 *   <li><b>{@code entities.all()} 已可用</b>：F6 给 {@code Entity} 补了 {@code getGroup()/setGroup()}、
 *       {@code EntityGroup} 的延迟 add/remove 也做了池化回收防护，{@code Vars.entities} 里
 *       player/puddle/fire 三个组已接线 → {@link #writeEntities(DataOutput)} 已换成**遍历
 *       {@code Vars.entities.all()}**（与原版一致：只写"组内首个实体是 SaveTrait"的组）。
 *       目前真正实现 {@link SaveTrait} 的实体只有 {@code Puddle}（单位/火尚未实现 SaveTrait），
 *       所以写出来通常只有 puddle 一组 —— 与原版相比少了 fire/unit 组，属**尚未接线**而非格式差异。</li>
 *   <li><b>{@code GameState.stats} 已接</b>：{@code game/Stats} 已移植，{@code writeMeta} 用
 *       {@code JsonIO.write(state.stats)} 落盘、{@code readMeta} 用 {@code JsonIO.read(Stats.class, ...)}
 *       读回（原注释"GameState 没有 stats → 写 {} / 整段未实现"**已过时**，grep 已证伪）。</li>
 *   <li><b>{@code "build"} 已接 {@code Version.build}</b>（=126）。读取侧 {@code lastReadBuild}
 *       目前只被 {@code Save1}（兼容层，刻意不接线）用来选 {@code LegacyTypeTable} 那张旧下标表，
 *       本项目自己的读写路径不读它（无"按 build 选内容映射表"的分支）→ 写 126 不会走错分支；
 *       老存档缺 {@code build} 键时仍回退 {@code -1}。</li>
 *   <li><b>{@code World.getMap()/setMap()} 已存在</b>（S1 批次）→ {@code writeMeta} 按原版从
 *       {@code world.getMap()} 取地图名、{@code readMeta} 按原版调 {@code world.setMap(...)}；
 *       程序生成图 {@code getMap()} 为 null → 地图名仍以 {@code state.mapName} 兜底。</li>
 *   <li><b>没有 {@code Vars.defaultWaves}</b> → 用 {@code new DefaultWaves().get()}（语义一致）。</li>
 *   <li><b>{@code Mods} 没有 {@code getModStrings()}</b> → {@code "mods"} 写 {@code "[]"}。</li>
 *   <li><b>计时来源不同</b>：原版取 {@code Vars.control.saves.getTotalPlaytime()}；本项目计时在
 *       {@code Vars.saves}（{@code game/Saves}）/ {@code state.playtime}，没有原版的
 *       {@code getTotalPlaytime()} → 游玩时长改用 {@code state.playtime}（秒）。</li>
 *   <li>{@code content.getByID} 在"临时映射表缺项"时会返回 null（原版也一样会 NPE 在 {@code .constructor} 上）
 *       → 读取处都加了 null 守卫。</li>
 * </ul>
 */
public abstract class SaveVersion extends SaveFileReader{
    public int version;

    //HACK stores the last read build of the save file, valid after read meta call
    protected int lastReadBuild;

    public SaveVersion(int version){
        this.version = version;
    }

    /**
     * 只读 meta 区块（存档列表用它，不必解析整张地图）。
     * <p>刻意降级：原版返回 {@code mindustry.io.SaveMeta}（含 version/build/mods/stats 与 Map）；
     * 本项目 {@code io/SaveMeta} 是自有格式的 JSON 元数据，字段集不同 → 只映射共有标量。
     */
    public SaveMeta getMeta(DataInput stream) throws IOException{
        stream.readInt(); //length of data, doesn't matter here
        StringMap map = readStringMap(stream);

        SaveMeta meta = new SaveMeta();
        meta.wave = map.getInt("wave");
        meta.wavetime = map.getFloat("wavetime", meta.wavetime);
        meta.playtime = map.getLong("playtime", 0L);
        meta.saved = map.getLong("saved", 0L);
        meta.mapname = map.get("mapname", "unknown");
        meta.width = map.getInt("width", 0);
        meta.height = map.getInt("height", 0);
        //TODO 原版还会带出 version/build/mods/stats/rules（本项目 SaveMeta 没有对应字段）
        //TODO 原版 new SaveMeta(...) 里会 maps.all().find(...) 解析出 Map 对象
        return meta;
    }

    @Override
    public final void write(DataOutputStream stream) throws IOException{
        write(stream, new StringMap());
    }

    @Override
    public final void read(DataInputStream stream, CounterInputStream counter, WorldContext context) throws IOException{
        region("meta", stream, counter, this::readMeta);
        region("content", stream, counter, this::readContentHeader);

        try{
            region("map", stream, counter, in -> readMap(in, context));
            region("entities", stream, counter, this::readEntities);
        }finally{
            content.setTemporaryMapper(null);
        }
    }

    public final void write(DataOutputStream stream, StringMap extraTags) throws IOException{
        region("meta", stream, out -> writeMeta(out, extraTags));
        region("content", stream, this::writeContentHeader);
        region("map", stream, this::writeMap);
        region("entities", stream, this::writeEntities);
    }

    public void writeMeta(DataOutput stream, StringMap tags) throws IOException{
        StringMap map = new StringMap();
        map.put("saved", String.valueOf(Time.millis()));
        //刻意降级：原版取 control.saves.getTotalPlaytime()；本项目计时在 Vars.saves / state.playtime，
        //没有原版的 getTotalPlaytime() → 用 state.playtime（秒）
        map.put("playtime", String.valueOf(headless ? 0L : (long)state.playtime));
        //已接 Version.build（原版同款；读取侧 lastReadBuild 只被兼容层 Save1 读取，本项目路径不读它）
        map.put("build", String.valueOf(Version.build));
        //原版 SaveVersion.java:68 = world.getMap() == null ? "unknown" : world.getMap().name()；
        //本项目 World 已有 getMap（S1 批次），但程序生成图 getMap() 为 null → 仍以 state.mapName 兜底
        Map cmap = world == null ? null : world.getMap();
        map.put("mapname", cmap != null ? cmap.name : (state.mapName == null ? "unknown" : state.mapName));
        map.put("wave", String.valueOf(state.wave));
        map.put("wavetime", String.valueOf(state.wavetime));
        //本局统计（对应原版 JsonIO.write(state.stats)）。
        //⚠ 已知降级：Stats.itemsDelivered 是 ObjectIntMap<Item>，libgdx 的 Json 会把它写成
        //  {"<物品名>":数量}，但**读回来时 key 是 String 而不是 Item**（libgdx 的 ObjectIntMap 读分支
        //  直接 result.put(child.name, ...)，没有 key 反序列化钩子）→ 只有 zone 的"已发射资源"用到它，
        //  本项目 zone 未接线，实际不受影响。
        map.put("stats", JsonIO.write(state.stats));
        map.put("rules", JsonIO.write(state.rules));
        //TODO 原版 JsonIO.write(mods.getModStrings().toArray(String.class))（Mods 没有 getModStrings）→ 写空数组
        map.put("mods", "[]");
        map.put("width", String.valueOf(world.width()));
        map.put("height", String.valueOf(world.height()));
        //原版是 StringMap.of(...).merge(tags)；libgdx 的 ObjectMap 没有 merge → 直接 putAll
        map.putAll(tags);

        writeStringMap(stream, map);
    }

    public void readMeta(DataInput stream) throws IOException{
        StringMap map = readStringMap(stream);

        state.wave = map.getInt("wave");
        state.wavetime = map.getFloat("wavetime", state.rules.waveSpacing);
        //本局统计（对应原版 state.stats = JsonIO.read(Stats.class, map.get("stats", "{}"))）
        state.stats = JsonIO.read(com.phoenix.game.game.Stats.class, map.get("stats", "{}"));
        state.rules = JsonIO.read(Rules.class, map.get("rules", "{}"));
        if(state.rules.spawns.isEmpty()){
            //TODO 原版 Vars.defaultWaves.get()（Vars 没有 defaultWaves）→ new DefaultWaves().get()
            state.rules.spawns = new DefaultWaves().get();
        }
        lastReadBuild = map.getInt("build", -1);

        //原版 SaveVersion.java:89-93 = world.setMap(worldmap == null ? new Map(StringMap.of(
        //    "name", ..., "width", 1, "height", 1)) : worldmap)；
        //本项目 World 已有 setMap（S1 批次）→ 照原版接上（world 可能为 null，加守卫）
        Map worldmap = maps == null ? null : maps.byName(map.get("mapname", "\\\\\\"));
        if(worldmap != null){
            state.mapName = worldmap.name;
        }else{
            state.mapName = map.get("mapname", "Unknown");
        }
        if(world != null){
            world.setMap(worldmap == null ? new Map(StringMap.of(
                "name", map.get("mapname", "Unknown"),
                "width", 1,
                "height", 1
            )) : worldmap);
        }
    }

    public void writeMap(DataOutput stream) throws IOException{
        int total = world.width() * world.height();

        //write world size
        stream.writeShort(world.width());
        stream.writeShort(world.height());

        //floor + overlay
        for(int i = 0; i < total; i++){
            //原版 SaveVersion.java:104 world.rawTile(i % w, i / w)（S1 批次 World 已有 rawTile）。
            //⚠ 本项目 rawTile 与原版一致**不做越界检查** → 保留下面的 null 兜底分支（原版没有，这是刻意的）
            Tile tile = world.rawTile(i % world.width(), i / world.width());
            if(tile == null){
                //理论上不会发生（total 由宽高算出）；越界时按 stone/air 兜底，保证字节数不漂移
                stream.writeShort(Blocks.stone.id);
                stream.writeShort(Blocks.air.id);
                stream.writeByte(0);
                continue;
            }

            //TODO 原版 tile.floorID()/overlayID()（本项目 Tile 没有）→ floor().id / overlay().id
            short floorid = tile.floor().id, overlayid = tile.overlay().id;
            stream.writeShort(floorid);
            stream.writeShort(overlayid);
            int consecutives = 0;

            for(int j = i + 1; j < total && consecutives < 255; j++){
                Tile nextTile = world.tile(j % world.width(), j / world.width());

                if(nextTile == null || nextTile.floor().id != floorid || nextTile.overlay().id != overlayid){
                    break;
                }

                consecutives++;
            }

            stream.writeByte(consecutives);
            i += consecutives;
        }

        //blocks
        for(int i = 0; i < total; i++){
            Tile tile = world.tile(i % world.width(), i / world.width());
            //TODO 原版 tile.blockID()；本项目没有 → blockRaw().id（**不能**用 block()，它会解析多格链接）
            Block block = tile == null ? Blocks.air : tile.blockRaw();
            stream.writeShort(block.id);

            if(tile != null && tile.entity != null){
                //刻意降级（同义改名）：原版 entity.version()；本项目叫 revision()
                //刻意降级（类型收窄）：本项目 TileEntity.write 的形参是具体的 DataOutputStream（原版是 DataOutput）
                //     → 强转。writeChunk 只会把 dataBytes/dataBytesSmall（都是 DataOutputStream）交给我们，安全。
                writeChunk(stream, true, out -> {
                    out.writeByte(tile.entity.revision());
                    tile.entity.write((DataOutputStream)out);
                });
            }else{
                //write consecutive non-entity blocks
                int consecutives = 0;

                for(int j = i + 1; j < total && consecutives < 255; j++){
                    Tile nextTile = world.tile(j % world.width(), j / world.width());
                    Block nextBlock = nextTile == null ? Blocks.air : nextTile.blockRaw();

                    if(nextBlock != block){
                        break;
                    }

                    consecutives++;
                }

                stream.writeByte(consecutives);
                i += consecutives;
            }
        }
    }

    public void readMap(DataInput stream, WorldContext context) throws IOException{
        int width = stream.readUnsignedShort();
        int height = stream.readUnsignedShort();

        boolean generating = context.isGenerating();

        if(!generating) context.begin();
        try{

            context.resize(width, height);

            //read floor and create tiles first
            for(int i = 0; i < width * height; i++){
                int x = i % width, y = i / width;
                short floorid = stream.readShort();
                short oreid = stream.readShort();
                int consecutives = stream.readUnsignedByte();
                if(content.block(floorid) == Blocks.air) floorid = Blocks.stone.id;

                context.create(x, y, floorid, oreid, (short)0);

                for(int j = i + 1; j < i + 1 + consecutives; j++){
                    int newx = j % width, newy = j / width;
                    context.create(newx, newy, floorid, oreid, (short)0);
                }

                i += consecutives;
            }

            //read blocks
            for(int i = 0; i < width * height; i++){
                int x = i % width, y = i / width;
                Block block = content.block(stream.readShort());
                Tile tile = context.tile(x, y);
                if(block == null) block = Blocks.air;
                //本项目 block() 会解析多格链接 → 这里用 setBlock 显式铺开（与原版一致）
                if(tile != null) tile.setBlock(block);

                if(tile != null && tile.entity != null){
                    try{
                        readChunk(stream, true, in -> {
                            byte version = in.readByte();
                            //刻意降级（类型收窄）：本项目 TileEntity.read 的形参是具体的 DataInputStream（原版是 DataInput）→ 强转
                            tile.entity.read((DataInputStream)in, version);
                        });
                    }catch(Exception e){
                        throw new IOException("Failed to read tile entity of block: " + block, e);
                    }
                }else{
                    int consecutives = stream.readUnsignedByte();

                    for(int j = i + 1; j < i + 1 + consecutives; j++){
                        int newx = j % width, newy = j / width;
                        Tile next = context.tile(newx, newy);
                        if(next != null) next.setBlock(block);
                    }

                    i += consecutives;
                }
            }
        }finally{
            if(!generating) context.end();
        }
    }

    public void writeEntities(DataOutput stream) throws IOException{
        //write team data with entities.
        //原版 SaveVersion.java:215-229：写 state.teams.getActive() 的 TeamData.brokenBlocks（被摧毁、重进时可还原的方块）。
        //本项目 Teams 没有 TeamData → getActive() 返回"队伍本身"（非 derelict 的基础队伍），
        //brokenBlocks 从 state.teams 取；字节顺序/字段宽度与原版一致（short,short,short,short,int）。
        //⚠ state/state.teams 可能为 null（本类目前是"刻意不接线"的兼容层）→ 为 null 时按原格式写 0 个队伍。
        Array<Team> activeTeams = (state == null || state.teams == null) ? null : state.teams.getActive();
        stream.writeInt(activeTeams == null ? 0 : activeTeams.size);
        if(activeTeams != null){
            for(int i = 0; i < activeTeams.size; i++){
                Team team = activeTeams.get(i);
                //原版 (int)team.team.id（本项目 Team.id 是 byte → 自动加宽为 int）
                stream.writeInt(team.id);
                Array<Teams.BrokenBlock> broken = state.teams.brokenBlocks(team);
                stream.writeInt(broken.size);
                for(int j = 0; j < broken.size; j++){
                    Teams.BrokenBlock block = broken.get(j);
                    stream.writeShort(block.x);
                    stream.writeShort(block.y);
                    stream.writeShort(block.rotation);
                    stream.writeShort(block.block);
                    stream.writeInt(block.config);
                }
            }
        }

        //write entity chunk：遍历 Vars.entities.all()，只写"组内首个实体是 SaveTrait"的组（与原版一致）。
        //字节格式 = 1 字节组数 + 每组的 int 数量 + 每实体一个区块，与原版兼容。
        if(entities == null){
            stream.writeByte(0);
            return;
        }

        Array<EntityGroup<?>> groups = entities.all();

        int count = 0;
        for(int i = 0; i < groups.size; i++){
            EntityGroup<?> group = groups.get(i);
            if(!group.isEmpty() && group.all().first() instanceof SaveTrait) count++;
        }

        stream.writeByte(count);

        for(int i = 0; i < groups.size; i++){
            EntityGroup<?> group = groups.get(i);
            if(group.isEmpty() || !(group.all().first() instanceof SaveTrait)) continue;

            stream.writeInt(group.size());

            for(int j = 0; j < group.all().size; j++){
                SaveTrait save = (SaveTrait)group.all().get(j);
                //each entity is a separate chunk.
                writeChunk(stream, true, out -> {
                    out.writeByte(save.getTypeID().id);
                    out.writeByte(save.version());
                    save.writeSave(out);
                });
            }
        }
    }

    public void readEntities(DataInput stream) throws IOException{
        int teamc = stream.readInt();
        for(int i = 0; i < teamc; i++){
            //原版 SaveVersion.java:258-267：Team team = Team.get(stream.readInt()); TeamData data = team.data();
            //本项目 Teams 没有 TeamData → 用 state.teams.brokenBlocks(team) 代替 team.data().brokenBlocks。
            //⚠ 读盘时 state/state.teams 可能尚未就绪（本类目前是"刻意不接线"的兼容层）→ 为 null 时保持原行为：
            //   按原格式消费字节但丢弃，避免 NPE。
            Team team = Team.get(stream.readInt());
            Array<Teams.BrokenBlock> broken = (state == null || state.teams == null || team == null)
                ? null : state.teams.brokenBlocks(team);
            int blocks = stream.readInt();
            for(int j = 0; j < blocks; j++){
                short bx = stream.readShort();     //x
                short by = stream.readShort();     //y
                short brot = stream.readShort();   //rotation
                short bblock = stream.readShort(); //block
                int bcfg = stream.readInt();       //config
                if(broken == null) continue;       //无可写入的目标队列 → 丢弃（保持兼容层原行为）

                //原版：content.block(short).id —— 把存档里的 id 经 content 归一化后再存
                //（本项目 Content.id 是 short，无需窄化；content.block 缺项返回 null → 回退原值，避免 NPE）。
                Block blk = content.block(bblock);
                broken.add(new Teams.BrokenBlock(bx, by, brot, blk == null ? bblock : blk.id, bcfg));
            }
        }

        byte groups = stream.readByte();

        for(int i = 0; i < groups; i++){
            int amount = stream.readInt();
            for(int j = 0; j < amount; j++){
                try{
                    readChunk(stream, true, in -> {
                        byte typeid = in.readByte();
                        byte version = in.readByte();
                        //原版：content.<TypeID>getByID(ContentType.typeid, typeid).constructor.get()
                        //本项目 getByID 在"临时映射表缺项"时会返回 null（原版会直接 NPE 在 .constructor 上）→ 加守卫
                        TypeID type = content.getByID(ContentType.typeid, typeid);
                        if(type == null) return;
                        SaveTrait trait = (SaveTrait)type.constructor.get();
                        trait.readSave(in, version);
                    });
                }catch(Exception e){
                    //对应原版 "throw exception on read fail"：坏实体带上序号抛出，便于定位
                    throw new IOException("Failed to read entity " + j + "/" + amount + ".", e);
                }
            }
        }
    }

    public void readContentHeader(DataInput stream) throws IOException{
        byte mapped = stream.readByte();

        MappableContent[][] map = new MappableContent[ContentType.values().length][0];

        for(int i = 0; i < mapped; i++){
            ContentType type = ContentType.values()[stream.readByte()];
            short total = stream.readShort();
            map[type.ordinal()] = new MappableContent[total];

            for(int j = 0; j < total; j++){
                String name = stream.readUTF();
                map[type.ordinal()][j] = content.getByName(type, fallback.get(name, name));
            }
        }

        content.setTemporaryMapper(map);
    }

    public void writeContentHeader(DataOutput stream) throws IOException{
        Array<Content>[] map = content.getContentMap();

        int mappable = 0;
        for(Array<Content> arr : map){
            if(arr.size > 0 && arr.first() instanceof MappableContent){
                mappable++;
            }
        }

        stream.writeByte(mappable);
        for(Array<Content> arr : map){
            if(arr.size > 0 && arr.first() instanceof MappableContent){
                stream.writeByte(arr.first().getContentType().ordinal());
                stream.writeShort(arr.size);
                for(Content c : arr){
                    stream.writeUTF(((MappableContent)c).name);
                }
            }
        }
    }
}
