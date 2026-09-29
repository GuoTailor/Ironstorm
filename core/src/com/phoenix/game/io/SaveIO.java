package com.phoenix.game.io;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.content.Items;
import com.phoenix.game.content.UnitTypes;
import com.phoenix.game.core.World;
import com.phoenix.game.ctype.ContentType;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.game.Rules;
import com.phoenix.game.game.Stats;
import com.phoenix.game.game.Team;
import com.phoenix.game.game.Teams;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.UnitType;
import com.phoenix.game.type.Zone;
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
 *
 * <p><b>原版 MSAV 存档</b>：本类只**识别**（{@link #msavHeader} / {@link #isOriginalSave(File)} /
 * {@link #readHeader} 里的分派点 {@link #unsupportedOriginalSave()}），**暂不读取** ——
 * 真正读 MSAV 需要先给 {@code io/SaveVersion} 那套兼容层接线。{@code net/TypeIO} 与
 * {@code ContentLoader.setTemporaryMapper} **已就位**，剩余缺口见 {@link #unsupportedOriginalSave()} 的清单。
 */
public class SaveIO{
    /** 文件头（ASCII "PHNX"）。 */
    public static final byte[] header = {'P', 'H', 'N', 'X'};
    /**
     * 原版 Mindustry 存档的文件头（ASCII "MSAV"，对应原版 {@code SaveIO.header = {77,83,65,86}}）。
     * <p>⚠ 它和本项目的 {@link #header} 一样是**压缩流里面**的头：两边都是整体 zlib，
     * 所以判魔数必须先 {@code InflaterInputStream} 解开再读前 4 字节（见 {@link #isOriginalSave(File)}）。
     */
    public static final byte[] msavHeader = {'M', 'S', 'A', 'V'};
    /** 当前格式版本。
     * <p>v3（2026-09-27 存档落库批）：meta 扩到 {@code Rules} 全字段 + 区域名/阵营 id/禁建方块名 + 本局统计；
     * 实体区追加**液体段**（{@link com.phoenix.game.entities.type.TileEntity#write}）→ **不兼容 v2 旧档**
     * （v2 档读到这里会报"不支持的存档版本"）。
     * <p>v4：实体区给每个非玩家单位追加**载物背包**（item/amount），并在末尾追加**玩家背包**记录
     * （玩家单位被刻意跳过、由 {@code Control.enterWorld} 重建，见 {@link #writeEntities}）→ **不兼容 v3 旧档**。
     * <p>v5（本批）：队伍段给每队追加**被摧毁可重建方块队列** {@code Teams.brokenBlocks}
     * （对应原版 {@code Teams.TeamData.brokenBlocks}，由 {@code Control} 的监听器维护、
     * 由 {@code Renderer.drawDestroyed()} 绘制重建幽灵轮廓、由 {@code BuilderDrone} 消费自动重建）
     * → **不兼容 v4 旧档**。 */
    public static final int version = 5;
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

    /**
     * 读档时暂存的**玩家背包**（v4）。
     * <p>玩家单位被刻意跳过（见 {@link #writeEntities}），由 {@code Control.enterWorld} 重建；而读档
     * （{@link #read}）发生在重建之前 → 只能先存这里，等 {@code Control.load} 建好玩家后套用。
     * <p>消费点只有 {@code Control.load} 一处，读完即 {@link #clearPendingPlayer()}，不会残留。
     */
    private static Item pendingPlayerItem;
    private static int pendingPlayerAmount;

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
        }catch(Exception e){
            //主文件坏了就回退到备份（对应原版 SaveIO.load 的行为）。
            //⚠ 捕 Exception 而不只是 IOException：readMap 里会抛 RuntimeException（数据损坏/字段错位），
            //  只捕 IOException 的话备份回退在真正需要它的时候反而不会触发。
            File backup = backupFileFor(file);
            if(backup.exists()){
                System.err.println("存档读取失败，回退到备份: " + e);
                try(DataInputStream in = new DataInputStream(new InflaterInputStream(new FileInputStream(backup)))){
                    read(in);
                    return;
                }
            }
            if(e instanceof IOException) throw (IOException)e;
            throw new IOException("读取存档失败: " + file, e);
        }
    }

    /** @return 与存档同目录的备份文件。 */
    public static File backupFileFor(File file){
        return new File(file.getParentFile(), file.getName() + "-backup");
    }

    /** @return 读档暂存的玩家背包物品（null 表示空）；由 {@code Control.load} 在重建玩家后套用。 */
    public static Item pendingPlayerItem(){
        return pendingPlayerItem;
    }

    /** @return 读档暂存的玩家背包数量。 */
    public static int pendingPlayerAmount(){
        return pendingPlayerAmount;
    }

    /** 清空玩家背包暂存（{@code Control.load} 套用后调用；{@link #read} 开头也会清一次）。 */
    public static void clearPendingPlayer(){
        pendingPlayerItem = null;
        pendingPlayerAmount = 0;
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

    /**
     * @return 该文件是否是**原版 Mindustry 的存档**（压缩流内以 {@code MSAV} 开头）。
     * <p>用于在存档列表里把原版存档与损坏文件区分开：{@link #isValid(File)} 对两者都返回 false，
     * 但只有原版存档值得提示"暂不支持"而不是"文件损坏"。
     * <p>读不出来（不存在/不是 zlib/太短）一律返回 false。
     */
    public static boolean isOriginalSave(File file){
        try(DataInputStream in = new DataInputStream(new InflaterInputStream(new FileInputStream(file)))){
            byte[] bytes = new byte[msavHeader.length];
            in.readFully(bytes);
            return Arrays.equals(bytes, msavHeader);
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
        rm.waveTeamId = rules.waveTeam == null ? -1 : rules.waveTeam.id;

        //---- v3 补充：Rules 其余字段（⚠ 这里是**手工白名单**：Rules 加字段必须同步改
        //     SaveMeta.RulesMeta / 本方法 / applyMeta 三处，漏了不报错、只是读档后回到默认值）----
        rm.pvp = rules.pvp;
        rm.editor = rules.editor;
        rm.tutorial = rules.tutorial;
        rm.unitDrops = rules.unitDrops;
        rm.reactorExplosions = rules.reactorExplosions;
        rm.limitedRespawns = rules.limitedRespawns;
        rm.attackMode = rules.attackMode;
        rm.canGameOver = rules.canGameOver;
        rm.lighting = rules.lighting;
        rm.unitBuildSpeedMultiplier = rules.unitBuildSpeedMultiplier;
        rm.playerHealthMultiplier = rules.playerHealthMultiplier;
        rm.blockHealthMultiplier = rules.blockHealthMultiplier;
        rm.playerDamageMultiplier = rules.playerDamageMultiplier;
        rm.buildCostMultiplier = rules.buildCostMultiplier;
        rm.buildSpeedMultiplier = rules.buildSpeedMultiplier;
        rm.enemyCoreBuildRadius = rules.enemyCoreBuildRadius;
        rm.respawnTime = rules.respawnTime;
        rm.bossWaveMultiplier = rules.bossWaveMultiplier;
        rm.launchWaveMultiplier = rules.launchWaveMultiplier;
        rm.solarPowerMultiplier = rules.solarPowerMultiplier;
        rm.respawns = rules.respawns;
        rm.defaultTeamId = rules.defaultTeam == null ? -1 : rules.defaultTeam.id;
        //区域与禁建方块按**名字**存：内容列表顺序变动后旧档仍能正确对应
        rm.zoneName = rules.zone == null ? null : rules.zone.name;
        rm.bannedBlockNames = new String[rules.bannedBlocks.size];
        int bannedIndex = 0;
        for(Block banned : rules.bannedBlocks){
            rm.bannedBlockNames[bannedIndex++] = banned.name;
        }

        //---- v3 补充：本局统计（消费点 GameOverDialog）----
        meta.launched = Vars.state.launched;
        Stats stats = Vars.state.stats == null ? new Stats() : Vars.state.stats;
        SaveMeta.StatsMeta sm = meta.stats;
        sm.enemyUnitsDestroyed = stats.enemyUnitsDestroyed;
        sm.wavesLasted = stats.wavesLasted;
        sm.timeLasted = stats.timeLasted;
        sm.buildingsBuilt = stats.buildingsBuilt;
        sm.buildingsDeconstructed = stats.buildingsDeconstructed;
        sm.buildingsDestroyed = stats.buildingsDestroyed;
        //ObjectIntMap<Item> → 两个并行数组（名字 + 数量）：libgdx Json 对泛型元素类型推断不稳
        sm.deliveredItems = new String[stats.itemsDelivered.size];
        sm.deliveredAmounts = new int[stats.itemsDelivered.size];
        int deliveredIndex = 0;
        for(ObjectIntMap.Entry<Item> entry : stats.itemsDelivered){
            sm.deliveredItems[deliveredIndex] = entry.key.name;
            sm.deliveredAmounts[deliveredIndex] = entry.value;
            deliveredIndex++;
        }

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

            //v5：该队"被摧毁、可重建"的方块队列（对应原版 Teams.TeamData.brokenBlocks，
            //    原版写在 SaveVersion.writeEntities 的实体区之前）。
            Array<Teams.BrokenBlock> broken = Vars.state.teams.brokenBlocks(team);
            out.writeInt(broken.size);
            for(int i = 0; i < broken.size; i++){
                Teams.BrokenBlock b = broken.get(i);
                out.writeShort(b.x);
                out.writeShort(b.y);
                out.writeShort(b.rotation);
                out.writeShort(b.block);
                out.writeInt(b.config);
            }
        }

        //单位（含载物背包）。
        //⚠ **跳过玩家单位**：读档路径是 Control.load → enterWorld，而 enterWorld 必定重建一个新的
        //   玩家单位（spawnPlayerUnit）。若把存档里的旧玩家单位也读回来，就会多出一只无人控制的
        //   "幽灵机"（且位置是存档时的旧位置）。先数一遍有效数量，再写，避免"计数与实际条数不一致"。
        int unitCount = 0;
        for(int i = 0; i < Units.units.size; i++){
            if(!Units.units.get(i).isPlayer) unitCount++;
        }
        out.writeInt(unitCount);
        for(int i = 0; i < Units.units.size; i++){
            BaseUnit unit = Units.units.get(i);
            if(unit.isPlayer) continue;
            out.writeShort(UnitTypes.all.indexOf(unit.getType(), true));
            out.writeByte(unit.getTeam().id);
            out.writeFloat(unit.x);
            out.writeFloat(unit.y);
            out.writeFloat(unit.health());
            out.writeFloat(unit.rotation);
            //v4：单位载物背包（对应原版 Unit.write 里的 item）
            out.writeShort(unit.item == null ? -1 : Items.all.indexOf(unit.item, true));
            out.writeInt(unit.itemAmount);
        }

        //v4：玩家背包。
        //玩家单位不入档（读档时 Control.enterWorld 会重建），所以玩家背包单独存一条记录；
        //读档时先存进静态 pending 字段，等 Control.enterWorld 建好玩家后再由 Control.load 套用。
        //⚠ 范围：只存背包。selectedType/level/xp/respawns 仍不入档（属另一条既有缺口）。
        Item playerItem = Vars.player == null ? null : Vars.player.item;
        out.writeShort(playerItem == null ? -1 : Items.all.indexOf(playerItem, true));
        out.writeInt(Vars.player == null ? 0 : Vars.player.itemAmount);
    }

    // ==================== 读 ====================

    /** 从输入流读入，替换当前世界与状态。 */
    public static void read(DataInputStream in) throws IOException{
        //先清掉上一次读档暂存的玩家背包，避免解析中途失败时残留旧值
        clearPendingPlayer();
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
        //阵营 id：-1 表示"档里没记录"→ 保持 Rules 自己的默认（别用 Team.get(-1) 拿到 derelict）
        if(rm.waveTeamId >= 0) rules.waveTeam = Team.get(rm.waveTeamId);

        //---- v3 补充：Rules 其余字段（与 writeMeta 逐项对应）----
        rules.pvp = rm.pvp;
        rules.editor = rm.editor;
        rules.tutorial = rm.tutorial;
        rules.unitDrops = rm.unitDrops;
        rules.reactorExplosions = rm.reactorExplosions;
        rules.limitedRespawns = rm.limitedRespawns;
        rules.attackMode = rm.attackMode;
        rules.canGameOver = rm.canGameOver;
        rules.lighting = rm.lighting;
        rules.unitBuildSpeedMultiplier = rm.unitBuildSpeedMultiplier;
        rules.playerHealthMultiplier = rm.playerHealthMultiplier;
        rules.blockHealthMultiplier = rm.blockHealthMultiplier;
        rules.playerDamageMultiplier = rm.playerDamageMultiplier;
        rules.buildCostMultiplier = rm.buildCostMultiplier;
        rules.buildSpeedMultiplier = rm.buildSpeedMultiplier;
        rules.enemyCoreBuildRadius = rm.enemyCoreBuildRadius;
        rules.respawnTime = rm.respawnTime;
        rules.bossWaveMultiplier = rm.bossWaveMultiplier;
        rules.launchWaveMultiplier = rm.launchWaveMultiplier;
        rules.solarPowerMultiplier = rm.solarPowerMultiplier;
        rules.respawns = rm.respawns;
        if(rm.defaultTeamId >= 0) rules.defaultTeam = Team.get(rm.defaultTeamId);

        //区域按名字反查（查不到 = 该区域在当前版本里被删了 → 退化成"非区域局"，不抛异常废掉整档）
        if(rm.zoneName != null && !rm.zoneName.isEmpty()){
            Zone zone = Vars.content.getByName(ContentType.zone, rm.zoneName);
            if(zone != null){
                rules.zone = zone;
            }else{
                System.err.println("存档引用了未知区域 \"" + rm.zoneName + "\"，按非区域局处理");
            }
        }

        //禁建方块按名字反查；查不到的**跳过 + err**（不能因为一个被删方块就废掉整档）
        if(rm.bannedBlockNames != null){
            for(String name : rm.bannedBlockNames){
                Block banned = Vars.content.getByName(ContentType.block, name);
                if(banned != null){
                    rules.bannedBlocks.add(banned);
                }else{
                    System.err.println("存档引用了未知禁建方块 \"" + name + "\"，已跳过");
                }
            }
        }

        //spawns/loadout 不入档：由 Control.load 在开局时按当前版本重建
        Vars.state.rules = rules;
        //"发射"标记（战役进度）
        Vars.state.launched = meta.launched;

        //---- v3 补充：本局统计 ----
        SaveMeta.StatsMeta sm = meta.stats == null ? new SaveMeta.StatsMeta() : meta.stats;
        Stats stats = new Stats();
        stats.enemyUnitsDestroyed = sm.enemyUnitsDestroyed;
        stats.wavesLasted = sm.wavesLasted;
        stats.timeLasted = sm.timeLasted;
        stats.buildingsBuilt = sm.buildingsBuilt;
        stats.buildingsDeconstructed = sm.buildingsDeconstructed;
        stats.buildingsDestroyed = sm.buildingsDestroyed;
        if(sm.deliveredItems != null && sm.deliveredAmounts != null){
            //两个并行数组按较短的那个截断，避免长度不一致时越界
            int count = Math.min(sm.deliveredItems.length, sm.deliveredAmounts.length);
            for(int i = 0; i < count; i++){
                Item item = Vars.content.getByName(ContentType.item, sm.deliveredItems[i]);
                if(item != null){
                    stats.itemsDelivered.put(item, sm.deliveredAmounts[i]);
                }else{
                    System.err.println("存档统计引用了未知物品 \"" + sm.deliveredItems[i] + "\"，已跳过");
                }
            }
        }
        Vars.state.stats = stats;
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

            //v5：该队"被摧毁、可重建"的方块队列。先清空（读档替换世界时内存里可能还留着旧队列），
            //    再按写入顺序追加。
            Array<Teams.BrokenBlock> broken = Vars.state.teams.brokenBlocks(Team.get(teamId));
            broken.clear();
            int brokenCount = in.readInt();
            for(int j = 0; j < brokenCount; j++){
                short bx = in.readShort(), by = in.readShort(), brot = in.readShort(), bblock = in.readShort();
                int bcfg = in.readInt();
                broken.add(new Teams.BrokenBlock(bx, by, brot, bblock, bcfg));
            }
        }

        //单位（含载物背包）
        Units.units.clear();
        int unitCount = in.readInt();
        for(int i = 0; i < unitCount; i++){
            int typeIndex = in.readUnsignedShort();
            int teamId = in.readUnsignedByte();
            float x = in.readFloat(), y = in.readFloat();
            float health = in.readFloat();
            float rotation = in.readFloat();
            //v4：单位载物背包（必须在 type == null 的 continue 之前读掉，否则流会错位）
            int itemIndex = in.readShort();
            int itemAmount = in.readInt();

            UnitType type = typeIndex < unitTable.size ? unitTable.get(typeIndex) : null;
            if(type == null) continue;

            BaseUnit unit = type.create();
            unit.setTeam(Team.get(teamId));
            unit.set(x, y);
            unit.health(health);
            unit.rotation = rotation;
            //注意：type.create() 内部已 Units.add，不要再加一次
            if(itemIndex >= 0 && itemIndex < Items.all.size){
                unit.addItem(Items.all.get(itemIndex), itemAmount);
            }
        }

        //v4：玩家背包（暂存，等 Control.load 在 enterWorld 重建玩家后套用）
        int playerItemIndex = in.readShort();
        pendingPlayerItem = playerItemIndex >= 0 && playerItemIndex < Items.all.size
            ? Items.all.get(playerItemIndex) : null;
        pendingPlayerAmount = in.readInt();
    }

    // ==================== 工具 ====================

    /** 校验文件头。 */
    public static void readHeader(java.io.DataInput in) throws IOException{
        byte[] bytes = new byte[header.length];
        in.readFully(bytes);
        //按魔数分派：原版 MSAV 存档走专门的分支（将来要支持读取，就把这里接到 SaveVersion 上）
        if(Arrays.equals(bytes, msavHeader)){
            throw unsupportedOriginalSave();
        }
        if(!Arrays.equals(bytes, header)){
            throw new IOException("不是有效的存档文件（文件头不匹配）");
        }
    }

    /**
     * 遇到原版 MSAV 存档时的统一出口 —— 这就是"按魔数分派"的分派点。
     * <p>目前**只识别、不读取**：真正读 MSAV 需要把 {@code io/SaveVersion} + {@code io/SaveFileReader} +
     * {@code io/versions/Save1..3} 那套兼容层接线。
     * <p><b>剩余缺口清单（2026-09 重新核实；旧注释列的 {@code net/TypeIO}/{@code setTemporaryMapper} 已过时）</b>：
     * <ul>
     *   <li>✅ {@code net/TypeIO} —— **已存在**（{@code net/TypeIO.java}），不再是缺口；</li>
     *   <li>✅ {@code ContentLoader.setTemporaryMapper(MappableContent[][])} —— **已存在**
     *       （{@code core/ContentLoader.java:152}），{@code SaveVersion.readContentHeader} 已在调用；</li>
     *   <li>❌ 原版 {@code SaveMeta} 的 version/build/mods/stats 字段（本项目 {@code io/SaveMeta} 是自有格式）；</li>
     *   <li>✅ {@code World.getMap()/setMap()/rawTile()} —— **已存在**（S1 批次，{@code core/World.java}）；</li>
     *   <li>❌ {@code Tile.floorID()/overlayID()/blockID()}；</li>
     *   <li>✅ {@code Teams.BrokenBlock} —— **已存在并已接线**（{@code game/Teams.java:39} 的
     *       {@code brokenBlocks(Team)} + {@code :136} 的 {@code BrokenBlock}；{@code SaveVersion.writeEntities/
     *       readEntities} 已按原格式读写）。本项目没有原版的 {@code TeamData} 包装类（用队伍本身代替）；</li>
     *   <li>❌ {@code Mods.getModStrings()}、{@code Vars.defaultWaves}（后者在 {@code SaveVersion.writeMeta}
     *       用 {@code new DefaultWaves().get()} 等价替代）。</li>
     * </ul>
     * 等这些补齐后，把这里换成 {@code SaveVersion.read(stream, ...)} 即可，调用方（{@link #load}/{@link #readMeta}）不用动。
     */
    private static IOException unsupportedOriginalSave(){
        return new IOException("这是原版 Mindustry 的存档（MSAV），本项目暂不支持读取"
            + "（需要先补齐 SaveVersion 兼容层的 SaveMeta/World/Tile 等缺口，见 转换.md）");
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
