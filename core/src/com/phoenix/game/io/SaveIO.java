package com.phoenix.game.io;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.content.Items;
import com.phoenix.game.content.UnitTypes;
import com.phoenix.game.core.World;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.game.Rules;
import com.phoenix.game.game.Team;
import com.phoenix.game.game.Teams;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.UnitType;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.BlockPart;
import com.phoenix.game.world.modules.ItemModule;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * 存档文件读写（参照 Mindustry {@code mindustry.io.SaveIO} + {@code SaveVersion} 重做）。
 *
 * <p>文件布局（**整体 zlib 压缩**）：
 * <pre>
 *   [4]      魔数 "PHNX"
 *   [4]      格式版本
 *   [chunk]  meta     —— JSON 元数据（地图名/波次/时长/规则/标签），见 {@link SaveMeta}
 *   [chunk]  content  —— 方块/物品/单位 **名字表**
 *   [chunk]  map      —— 宽高 + 地板/覆盖层 + 方块 + 实体数据
 *   [chunk]  entities —— 队伍共享库存 + 单位
 * </pre>
 * chunk = {@code [4] 长度 + 数据}。分区化是为了让 UI 能**只读 meta** 就列出存档
 * （{@link #readMeta(File)}），不必解析整张地图。
 *
 * <p>与原版 MSAV 的差异（刻意，不追求字节兼容）：
 * <ul>
 *   <li>content 区用**名字表**而非纯 id：方块/物品列表顺序调整后旧存档仍能正确对应；</li>
 *   <li>不写多格建筑的卫星格（存档里是 air），读档时由中心格 {@code setBlock} 自动铺开；</li>
 *   <li>不写每帧可重算的派生量（邻接表、消耗状态、电网归属、生产/需求）。</li>
 * </ul>
 */
public class SaveIO{
    /** 文件头（ASCII "PHNX"）。 */
    public static final byte[] header = {'P', 'H', 'N', 'X'};
    /** 当前格式版本。 */
    public static final int version = 2;
    /** 单个区块的长度上限（防损坏文件导致 OOM）。 */
    private static final int maxChunkLength = 64 * 1024 * 1024;

    private static final Json json = new Json();

    static{
        json.setOutputType(JsonWriter.OutputType.json);
        //关掉原型：所有字段都写出来，字段增删时读档更稳（meta 很小，不值得省这点体积）
        json.setUsePrototypes(false);
    }

    /** 读档时的内容名字表（由 {@link #readContentHeader} 填充，供 {@link #readMap}/{@link #readEntities} 使用）。 */
    private static final Array<Block> blockTable = new Array<>();
    private static final Array<Item> itemTable = new Array<>();
    private static final Array<UnitType> unitTable = new Array<>();

    // ==================== 对外接口 ====================

    /** 保存当前世界到文件（先写临时文件，成功后原子替换，并保留上一份为备份）。 */
    public static void save(File file) throws IOException{
        save(file, null);
    }

    /**
     * 保存当前世界到文件，并写入额外的自定义标签（会合进 meta 的 tags 区）。
     * @param tags 附加标签，可为 null
     */
    public static void save(File file, java.util.Map<String, String> tags) throws IOException{
        File dir = file.getParentFile();
        if(dir != null && !dir.exists() && !dir.mkdirs()){
            throw new IOException("无法创建存档目录: " + dir);
        }
        File tmp = new File(dir, file.getName() + ".tmp");

        try(DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(new FileOutputStream(tmp)))){
            write(out, tags);
        }catch(IOException e){
            tmp.delete();
            throw e;
        }

        //上一份留作备份（对应原版 backupFileFor），读档失败时可回退
        File backup = backupFileFor(file);
        if(file.exists()){
            if(backup.exists() && !backup.delete()){
                System.err.println("无法删除旧备份: " + backup);
            }
            if(!file.renameTo(backup)){
                System.err.println("无法备份旧存档，直接覆盖: " + file);
                file.delete();
            }
        }
        if(!tmp.renameTo(file)){
            tmp.delete();
            throw new IOException("无法替换存档文件: " + file);
        }
    }

    /** 读取存档并替换当前世界与状态。 */
    public static void load(File file) throws IOException{
        try(DataInputStream in = new DataInputStream(new InflaterInputStream(new FileInputStream(file)))){
            read(in);
        }catch(IOException e){
            //主文件坏了就回退到备份（对应原版 SaveIO.load 的行为）
            File backup = backupFileFor(file);
            if(backup.exists()){
                System.err.println("存档读取失败，回退到备份: " + e);
                try(DataInputStream in = new DataInputStream(new InflaterInputStream(new FileInputStream(backup)))){
                    read(in);
                    return;
                }
            }
            throw e;
        }
    }

    /** @return 与存档同目录的备份文件。 */
    public static File backupFileFor(File file){
        return new File(file.getParentFile(), file.getName() + "-backup");
    }

    /** 只读元数据（不加载世界）；供存档/地图列表显示。 */
    public static SaveMeta readMeta(File file) throws IOException{
        try(DataInputStream in = new DataInputStream(new InflaterInputStream(new FileInputStream(file)))){
            readHeader(in);
            int ver = in.readInt();
            if(ver != version) throw new IOException("不支持的存档版本: " + ver + "（当前 " + version + "）");
            return parseMeta(readChunk(in));
        }
    }

    /** @return 该文件是否是本版本可读的存档。 */
    public static boolean isValid(File file){
        try{
            readMeta(file);
            return true;
        }catch(Exception e){
            return false;
        }
    }

    // ==================== 写 ====================

    /** 把当前世界序列化到输出流（存档与网络传输共用同一格式）。 */
    public static void write(DataOutputStream out) throws IOException{
        write(out, null);
    }

    /** 把当前世界序列化到输出流，并附加自定义标签。 */
    public static void write(DataOutputStream out, java.util.Map<String, String> tags) throws IOException{
        out.write(header);
        out.writeInt(version);
        writeChunk(out, o -> writeMeta(o, tags));
        writeChunk(out, SaveIO::writeContentHeader);
        writeChunk(out, SaveIO::writeMap);
        writeChunk(out, SaveIO::writeEntities);
    }

    private static void writeMeta(DataOutputStream out, java.util.Map<String, String> tags) throws IOException{
        SaveMeta meta = new SaveMeta();
        meta.mapname = Vars.state.mapName == null ? "unknown" : Vars.state.mapName;
        meta.wave = Vars.state.wave;
        meta.wavetime = Vars.state.wavetime;
        meta.playtime = (long)Vars.state.playtime;
        meta.saved = System.currentTimeMillis();
        meta.width = Vars.world.width();
        meta.height = Vars.world.height();
        if(tags != null && !tags.isEmpty()){
            meta.tags.putAll(tags);
        }

        Rules rules = Vars.state.rules;
        SaveMeta.RulesMeta rm = meta.rules;
        rm.waves = rules.waves;
        rm.waveTimer = rules.waveTimer;
        rm.waitForWaveToEnd = rules.waitForWaveToEnd;
        rm.enemyCheat = rules.enemyCheat;
        rm.sandbox = rules.sandbox;
        rm.waveSpacing = rules.waveSpacing;
        rm.deconstructRefundMultiplier = rules.deconstructRefundMultiplier;
        rm.dropZoneRadius = rules.dropZoneRadius;
        rm.unitHealthMultiplier = rules.unitHealthMultiplier;
        rm.unitDamageMultiplier = rules.unitDamageMultiplier;
        rm.waveTeamId = rules.waveTeam == null ? 0 : rules.waveTeam.id;

        out.write(json.toJson(meta).getBytes(Vars.charset));
    }

    private static void writeContentHeader(DataOutputStream out) throws IOException{
        out.writeShort(Blocks.all.size);
        for(Block block : Blocks.all) out.writeUTF(block.name);
        out.writeShort(Items.all.size);
        for(Item item : Items.all) out.writeUTF(item.name);
        out.writeShort(UnitTypes.all.size);
        for(UnitType type : UnitTypes.all) out.writeUTF(type.name);
    }

    private static void writeMap(DataOutputStream out) throws IOException{
        World world = Vars.world;
        int width = world.width(), height = world.height();
        out.writeShort(width);
        out.writeShort(height);

        //地板层与覆盖层
        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){
                Tile tile = world.tile(x, y);
                out.writeShort(blockIndex(tile.floor()));
                out.writeShort(blockIndex(tile.overlay()));
            }
        }

        //方块层 + 实体数据
        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){
                Tile tile = world.tile(x, y);
                Block raw = tile.blockRaw();
                //卫星格（BlockPart）不在名字表里，统一记作 air；读档时由中心格 setBlock 自动铺开
                int index = raw instanceof BlockPart ? 0 : blockIndex(raw);
                out.writeShort(index);
                if(index == 0) continue;

                out.writeByte(tile.getTeam().id);
                out.writeByte(tile.rotation());
                //有实体的方块：实体数据（含各自的血量/库存/进度等，见 TileEntity.write）
                if(tile.entity != null){
                    out.writeByte(tile.entity.revision());
                    tile.entity.write(out);
                }
            }
        }
    }

    private static void writeEntities(DataOutputStream out) throws IOException{
        //队伍共享库存
        Team[] teams = Team.base();
        out.writeByte(teams.length);
        for(Team team : teams){
            if(team == null) continue;
            out.writeByte(team.id);
            ItemModule inv = Vars.state.teams.items(team);
            for(int i = 0; i < Items.all.size; i++){
                out.writeInt(inv.get(Items.all.get(i)));
            }
        }

        //单位
        out.writeInt(Units.units.size);
        for(BaseUnit unit : Units.units){
            out.writeShort(UnitTypes.all.indexOf(unit.getType(), true));
            out.writeByte(unit.getTeam().id);
            out.writeFloat(unit.x);
            out.writeFloat(unit.y);
            out.writeFloat(unit.health());
            out.writeFloat(unit.rotation);
        }
    }

    // ==================== 读 ====================

    /** 从输入流读入，替换当前世界与状态。 */
    public static void read(DataInputStream in) throws IOException{
        readHeader(in);
        int ver = in.readInt();
        if(ver != version){
            throw new IOException("不支持的存档版本: " + ver + "（当前 " + version + "）");
        }

        SaveMeta meta = parseMeta(readChunk(in));
        if(meta != null) applyMeta(meta);

        readContentHeader(new DataInputStream(new ByteArrayInputStream(readChunk(in))));
        readMap(new DataInputStream(new ByteArrayInputStream(readChunk(in))));
        readEntities(new DataInputStream(new ByteArrayInputStream(readChunk(in))));
    }

    /** 解析 meta 区字节。 */
    private static SaveMeta parseMeta(byte[] bytes) throws IOException{
        String text = new String(bytes, Vars.charset);
        if(text.isEmpty()) return null;
        try{
            return json.fromJson(SaveMeta.class, text);
        }catch(RuntimeException e){
            throw new IOException("存档元数据损坏", e);
        }
    }

    /** 把 meta 应用到当前游戏状态（世界本身由 readMap 重建）。 */
    private static void applyMeta(SaveMeta meta){
        Vars.state.wave = meta.wave;
        Vars.state.wavetime = meta.wavetime;
        Vars.state.mapName = meta.mapname;
        Vars.state.playtime = meta.playtime;

        SaveMeta.RulesMeta rm = meta.rules == null ? new SaveMeta.RulesMeta() : meta.rules;
        Rules rules = new Rules();
        rules.waves = rm.waves;
        rules.waveTimer = rm.waveTimer;
        rules.waitForWaveToEnd = rm.waitForWaveToEnd;
        rules.enemyCheat = rm.enemyCheat;
        rules.sandbox = rm.sandbox;
        rules.waveSpacing = rm.waveSpacing;
        rules.deconstructRefundMultiplier = rm.deconstructRefundMultiplier;
        rules.dropZoneRadius = rm.dropZoneRadius;
        rules.unitHealthMultiplier = rm.unitHealthMultiplier;
        rules.unitDamageMultiplier = rm.unitDamageMultiplier;
        rules.waveTeam = Team.get(rm.waveTeamId);
        //spawns/loadout 不入档：由 Control.load 在开局时按当前版本重建
        Vars.state.rules = rules;
    }

    private static void readContentHeader(DataInputStream in) throws IOException{
        blockTable.clear();
        itemTable.clear();
        unitTable.clear();

        int blocks = in.readUnsignedShort();
        for(int i = 0; i < blocks; i++){
            String name = in.readUTF();
            Block block = byName(Blocks.all, name);
            if(block == null){
                //存档来自更旧的版本/被删掉的方块：用 air 占位，让整张图还能读进来（丢这一种建筑）
                System.err.println("存档引用了未知方块 \"" + name + "\"，按 air 处理");
                block = Blocks.air;
            }
            blockTable.add(block);
        }

        int items = in.readUnsignedShort();
        for(int i = 0; i < items; i++){
            String name = in.readUTF();
            Item item = byName(Items.all, name);
            if(item == null){
                System.err.println("存档引用了未知物品 \"" + name + "\"");
            }
            itemTable.add(item);
        }

        int units = in.readUnsignedShort();
        for(int i = 0; i < units; i++){
            String name = in.readUTF();
            UnitType type = byName(UnitTypes.all, name);
            if(type == null){
                System.err.println("存档引用了未知单位 \"" + name + "\"");
            }
            unitTable.add(type);
        }
    }

    private static void readMap(DataInputStream in) throws IOException{
        int width = in.readUnsignedShort();
        int height = in.readUnsignedShort();

        World world = new World();
        world.width = width;
        world.height = height;
        world.tiles = new Tile[width * height];
        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){
                world.tiles[y * width + x] = new Tile(x, y);
            }
        }

        //队伍必须在方块实体创建前重置：核心实体初始化时会挂到本队共享库存上
        Vars.state.teams = new Teams();

        //提前挂到 Vars.world：多格建筑铺开卫星格要靠 world.tile(...) 找邻居。
        //读档全程在渲染线程内同步完成，期间不会有别的代码观察到半成品世界。
        Vars.world = world;
        //批量模式：逐格 setBlock 不触发电网重建 / 寻路刷新 / 瓦片事件
        world.setGenerating(true);

        try{
            //第一遍：地板与覆盖层
            for(int y = 0; y < height; y++){
                for(int x = 0; x < width; x++){
                    Tile tile = world.tiles[y * width + x];
                    tile.setFloor(floorOf(in.readUnsignedShort()));
                    tile.setOverlay(floorOf(in.readUnsignedShort()));
                }
            }

            //第二遍：方块与实体
            for(int y = 0; y < height; y++){
                for(int x = 0; x < width; x++){
                    Tile tile = world.tiles[y * width + x];
                    Block block = blockOf(in.readUnsignedShort());
                    if(block == Blocks.air){
                        //多格建筑的卫星格在存档里记作 air：若这一格已经被中心格铺成 BlockPart，
                        //必须保持不动，否则会把先铺好的卫星格清成空气，导致多格建筑缺角
                        continue;
                    }

                    int teamId = in.readUnsignedByte();
                    int rotation = in.readUnsignedByte();
                    tile.setBlock(block, Team.get(teamId), rotation);

                    if(tile.entity != null){
                        byte revision = in.readByte();
                        tile.entity.read(in, revision);
                    }
                }
            }
        }finally{
            world.setGenerating(false);
        }

        //批量结束后统一重建邻接表与电网（生成期间被挂起了）
        for(Tile tile : world.tiles){
            if(tile != null && tile.entity != null){
                tile.entity.updateProximity();
            }
        }
        //邻接表就绪后再让实体解析跨瓦片的引用（如 Router 的"上一件物品来自哪"）
        for(Tile tile : world.tiles){
            if(tile != null && tile.entity != null){
                tile.entity.afterRead();
            }
        }
        world.rebuildPowerGraphs();
    }

    private static void readEntities(DataInputStream in) throws IOException{
        //队伍共享库存
        int teamCount = in.readUnsignedByte();
        for(int i = 0; i < teamCount; i++){
            int teamId = in.readUnsignedByte();
            ItemModule inv = Vars.state.teams.items(Team.get(teamId));
            for(int j = 0; j < Items.all.size; j++){
                inv.set(Items.all.get(j), in.readInt());
            }
        }

        //单位
        Units.units.clear();
        int unitCount = in.readInt();
        for(int i = 0; i < unitCount; i++){
            int typeIndex = in.readUnsignedShort();
            int teamId = in.readUnsignedByte();
            float x = in.readFloat(), y = in.readFloat();
            float health = in.readFloat();
            float rotation = in.readFloat();

            UnitType type = typeIndex < unitTable.size ? unitTable.get(typeIndex) : null;
            if(type == null) continue;

            BaseUnit unit = type.create();
            unit.setTeam(Team.get(teamId));
            unit.set(x, y);
            unit.health(health);
            unit.rotation = rotation;
            Units.add(unit);
        }
    }

    // ==================== 工具 ====================

    /** 校验文件头。 */
    public static void readHeader(java.io.DataInput in) throws IOException{
        byte[] bytes = new byte[header.length];
        in.readFully(bytes);
        if(!Arrays.equals(bytes, header)){
            throw new IOException("不是有效的存档文件（文件头不匹配）");
        }
    }

    private interface ChunkWriter{
        void write(DataOutputStream out) throws IOException;
    }

    /** 写一个长度前缀的区块。 */
    private static void writeChunk(DataOutputStream out, ChunkWriter writer) throws IOException{
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream sub = new DataOutputStream(buffer);
        writer.write(sub);
        sub.flush();
        byte[] bytes = buffer.toByteArray();
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    /** 读一个长度前缀的区块。 */
    private static byte[] readChunk(DataInputStream in) throws IOException{
        int length = in.readInt();
        if(length < 0 || length > maxChunkLength){
            throw new IOException("存档区块长度非法: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }

    private static int blockIndex(Block block){
        int index = Blocks.all.indexOf(block, true);
        return index < 0 ? 0 : index;
    }

    private static Block blockOf(int index){
        if(index <= 0) return Blocks.air;
        return index < blockTable.size ? blockTable.get(index) : Blocks.air;
    }

    private static Floor floorOf(int index){
        Block block = blockOf(index);
        return block instanceof Floor ? (Floor)block : Blocks.air;
    }

    private static <T> T byName(Array<T> array, String name){
        for(int i = 0; i < array.size; i++){
            T value = array.get(i);
            String other = value instanceof Block ? ((Block)value).name
                : value instanceof Item ? ((Item)value).name
                : value instanceof UnitType ? ((UnitType)value).name : null;
            if(name.equals(other)) return value;
        }
        return null;
    }
}
