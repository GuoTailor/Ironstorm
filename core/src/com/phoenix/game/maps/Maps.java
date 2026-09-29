package com.phoenix.game.maps;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntSet;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.io.SaveIO;
import com.phoenix.game.io.SaveMeta;
import com.phoenix.game.maps.filters.GenerateFilter;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.storage.CoreBlock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * 地图管理。对应原版 Mindustry 的 mindustry.maps.Maps 最小版（headless 友好）。
 * <p>phoenix 无独立地图格式：一张地图 = 一个 SaveIO 序列化的世界存档（{@code .msav}）。
 * 程序生成的默认图在启动时「导出」到 maps 目录，随后扫描目录作为可玩地图列表。
 * <p>缩略图、steam workshop、地图过滤、预览等在客户端侧，服务端只关心名字与加载。
 *
 * <p><b>2026-09-23 改造</b>：原来的内部类 {@code Maps.Map}（只有 {@code file}/{@code name}）已删除，
 * 全工程改用独立类 {@link Map}（原版 {@code mindustry.maps.Map} 的移植版，带
 * {@code width}/{@code height}/{@code tags}/{@code teams}/{@code spawns} 等元数据）。
 * 为让 {@link com.phoenix.game.game.Gamemode} 的「模式是否适用这张图」判定能恢复原版写法，
 * 这里额外做两件事：
 * <ul>
 *   <li>{@link #exportCurrent(File)} 导出时按当前世界统计 {@code spawns}/{@code teams}，
 *       并把它们塞进存档 meta 的自定义 tags（原版是写 {@code -cache.dat} 侧车文件）；</li>
 *   <li>{@link #load()} 扫描目录时用 {@link SaveIO#readMeta} 只读 meta 区恢复
 *       {@code width}/{@code height}/{@code spawns}/{@code teams}（不解析整张地图）。</li>
 * </ul>
 * <p>TODO 原版 {@code Maps} 还有：内置地图列表、workshop 地图、mod 地图、
 * {@code loadPreviews}/{@code createNewPreview}（缩略图生成）、
 * {@code ShuffleMode}/{@code MapProvider}（轮换策略）。
 * 其中缩略图依赖未移植的 {@code MapIO.generatePreview}，暂缓；
 * {@code readFilters}/{@code addDefaultOres} 已补（见方法）；
 * {@code importMap}/{@code removeMap} 已补（T14，见方法）。
 */
public class Maps {
    /** 地图目录（应用本地目录下）。 */
    private static final String MAPS_DIR = "maps";
    /** 全部地图（含自定义），按名字排序。 */
    private final Array<Map> maps = new Array<>();

    /** 扫描地图目录，恢复地图列表。应用启动后调用一次。 */
    public void load(){
        maps.clear();
        File dir = Gdx.files.local(MAPS_DIR).file();
        if(!dir.exists() && !dir.mkdirs()){
            System.err.println("无法创建地图目录: " + dir);
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith("." + com.phoenix.game.Vars.saveExtension));
        if(files == null) return;

        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        for(File file : files){
            Map map = new Map(file);
            readStats(map);
            maps.add(map);
        }
    }

    /** 全部地图。 */
    public Array<Map> all(){
        return maps;
    }

    /** 按名字查找地图。 */
    public Map byName(String name){
        for(Map map : maps){
            if(map.name.equals(name)) return map;
        }
        return null;
    }

    /** 把当前世界导出成一张新地图（maps/map_N.msav），并加入列表。 */
    public Map exportCurrent(File file) throws IOException{
        Map map = new Map(file);
        //对应原版 Maps.saveMap 里对当前世界的统计（Gamemode 的模式校验依赖 spawns/teams）
        countWorld(map);

        //统计值随存档 meta 一起落盘，下次 load() 扫描时可直接恢复（原版是写 -cache.dat 侧车文件）
        java.util.HashMap<String, String> tags = new java.util.HashMap<>();
        tags.put("spawns", String.valueOf(map.spawns));
        tags.put("teams", encodeTeams(map.teams));
        SaveIO.save(file, tags);

        if(byName(map.name) == null){
            maps.add(map);
        }
        return map;
    }

    /** 生成一个不冲突的地图文件。 */
    public File nextMapFile(){
        File dir = Gdx.files.local(MAPS_DIR).file();
        if(!dir.exists() && !dir.mkdirs()){
            throw new IllegalStateException("无法创建地图目录: " + dir);
        }
        int i = 0;
        File file;
        do{
            file = new File(dir, "map_" + i + "." + com.phoenix.game.Vars.saveExtension);
            i++;
        }while(file.exists());
        return file;
    }

    /**
     * 从指定文件加载地图到当前世界（Vars.world 被替换）。
     * @return 是否加载成功；失败时 {@code Vars.world} 保持原样（调用方应据此放弃开局）
     */
    public boolean loadMap(Map map){
        try{
            SaveIO.load(map.file);
            System.out.println("已加载地图 " + map.name);
            return true;
        }catch(IOException e){
            System.err.println("加载地图失败 " + map.name + ": " + e);
            return false;
        }
    }

    /**
     * 把一张外部地图文件导入地图目录并加入列表（对应原版 {@code Maps.importMap(Fi)}，T14 追加）。
     * <p>原版流程：复制到 {@link #nextMapFile()} 生成的新文件名 → {@code loadMap(dest, true)}
     * （建 {@link Map} 并加进列表）→ 异步生成缩略图（失败则回滚并抛 {@code IOException}）。
     * 本项目没有缩略图生成（{@link #queueNewPreview} 只打日志）→ 同步建 {@code Map} + 读元数据 + 加入列表，
     * 因此不会因预览失败回滚（也就不抛那种异常）。
     * <p>⚠ 与 {@link #loadMap(Map)} 无关：那个方法把地图**载入当前世界**，本方法只做"导入到列表"，
     * {@code loadMap} 的现有行为未改动。
     * <p>⚠ 文件名由 {@code map_N.msav} 派生，显示名仍取自存档 meta 的 {@code name} 标签（与 {@link #load()} 同口径）。
     * @return 导入后的地图对象
     */
    public Map importMap(File source) throws IOException{
        File dest = nextMapFile();
        Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

        Map map = new Map(dest);
        readStats(map);
        maps.add(map);
        queueNewPreview(map);
        return map;
    }

    /**
     * 彻底移除一张地图（对应原版 {@code Maps.removeMap(Map)}，T14 追加）：释放预览纹理 + 从列表移除 + 删文件。
     * <p>原版 {@code maps.remove(map)} 用的是"值相等"语义；本项目 {@link Map} 未覆写 {@code equals}
     * → 用 libgdx 的 identity 重载 {@code removeValue(map, true)} 明确意图。
     */
    public void removeMap(Map map){
        if(map.texture != null){
            map.texture.dispose();
            map.texture = null;
        }

        maps.removeValue(map, true);
        map.file.delete();
    }

    /**
     * TODO 原版把地图排进预览生成队列（{@code Core.app.post} + AsyncExecutor 里生成缩略图并写盘）。
     * <p>本项目没有地图缩略图生成（{@code MapIO.generatePreview} 未移植）→ 只打日志，
     * 供 {@link MapPreviewLoader} 加载失败时调用。
     */
    public void queueNewPreview(Map map){
        System.out.println("TODO 地图缩略图生成未移植，跳过: " + map.name);
    }

    // ---- 元数据 ----

    /**
     * 读取地图生成滤镜的 JSON，返回一个新的滤镜数组。参照原版 {@code Maps.readFilters(String)} 移植。
     * <p>{@code str} 为空/缺省时构造一套默认滤镜（若干 {@link com.phoenix.game.maps.filters.ScatterFilter} +
     * {@link #addDefaultOres}），否则用 {@link com.phoenix.game.io.JsonIO} 反序列化；解析失败时退回默认滤镜。
     * <p>原版返回 {@code arc.struct.Array}，本项目返回等价的 {@link com.phoenix.game.struct.Array}
     * （= {@link Map#filters()} 的返回类型）。libgdx 的 {@code Json} 会按每个元素里的 {@code class} 字段
     * 多态构造 {@link GenerateFilter} 子类。
     */
    @SuppressWarnings("unchecked")
    public com.phoenix.game.struct.Array<GenerateFilter> readFilters(String str){
        if(str == null || str.isEmpty()){
            com.phoenix.game.struct.Array<GenerateFilter> filters = new com.phoenix.game.struct.Array<>();
            filters.add(new com.phoenix.game.maps.filters.ScatterFilter(){{
                flooronto = Blocks.stone;
                block = Blocks.rock;
            }});
            filters.add(new com.phoenix.game.maps.filters.ScatterFilter(){{
                flooronto = Blocks.shale;
                block = Blocks.shaleBoulder;
            }});
            filters.add(new com.phoenix.game.maps.filters.ScatterFilter(){{
                flooronto = Blocks.snow;
                block = Blocks.snowrock;
            }});
            filters.add(new com.phoenix.game.maps.filters.ScatterFilter(){{
                flooronto = Blocks.ice;
                block = Blocks.snowrock;
            }});
            filters.add(new com.phoenix.game.maps.filters.ScatterFilter(){{
                flooronto = Blocks.sand;
                block = Blocks.sandBoulder;
            }});

            addDefaultOres(filters);
            return filters;
        }else{
            try{
                return com.phoenix.game.io.JsonIO.read(com.phoenix.game.struct.Array.class, str);
            }catch(Exception e){
                e.printStackTrace();
                return readFilters("");
            }
        }
    }

    /** 给每个 {@code oreDefault} 的矿脉地板加一个 {@link com.phoenix.game.maps.filters.OreFilter}（对应原版 {@code Maps.addDefaultOres}）。 */
    public void addDefaultOres(com.phoenix.game.struct.Array<GenerateFilter> filters){
        com.badlogic.gdx.utils.Array<Block> blocks = Vars.content.blocks();
        for(int i = 0; i < blocks.size; i++){
            Block block = blocks.get(i);
            if(!block.isOverlay() || !block.asFloor().oreDefault) continue;

            com.phoenix.game.maps.filters.OreFilter filter = new com.phoenix.game.maps.filters.OreFilter();
            filter.threshold = block.asFloor().oreThreshold;
            filter.scl = block.asFloor().oreScale;
            filter.ore = block;
            filters.add(filter);
        }
    }

    /**
     * 按当前世界统计地图元数据（对应原版 {@code Maps.saveMap} 里的那段遍历）。
     * <p>本项目 {@code Tile.block()} 会把多格建筑的卫星格转发到中心格，所以核心会被重复命中，
     * 但 {@link IntSet} 自动去重，结果与原版一致。
     */
    private void countWorld(Map map){
        map.teams.clear();
        map.spawns = 0;
        if(Vars.world == null) return;

        map.width = Vars.world.width();
        map.height = Vars.world.height();

        Tile[] tiles = Vars.world.tiles;
        for(int i = 0; i < tiles.length; i++){
            Tile tile = tiles[i];
            if(tile == null) continue;
            if(tile.block() instanceof CoreBlock) map.teams.add(tile.getTeam().id);
            if(tile.overlay() == Blocks.spawn) map.spawns++;
        }
    }

    /**
     * 只读存档 meta 区，恢复 {@code width}/{@code height}/{@code spawns}/{@code teams}，
     * 并把 meta 的自定义 tags 灌进 {@link Map#tags}（原版 {@code Map.tags} 直接来自地图文件元数据）。
     * <p>灌 tags 是 {@link Map#rules()}/{@link Map#filters()}/{@link Map#tag(String)} 的数据源；没有它，
     * 这些方法即使接了 {@code JsonIO} 也读不到东西。失败（不是本版本存档 / 文件损坏）时保留文件名条目、
     * 元数据留空 —— 与原版「读缓存失败就退回默认值」的宽容口径一致。
     */
    private void readStats(Map map){
        try{
            SaveMeta meta = SaveIO.readMeta(map.file);
            map.width = meta.width;
            map.height = meta.height;
            if(meta.tags == null) return;

            for(java.util.Map.Entry<String, String> e : meta.tags.entrySet()){
                map.tags.put(e.getKey(), e.getValue());
            }

            String spawns = meta.tags.get("spawns");
            if(spawns != null) map.spawns = Integer.parseInt(spawns);

            map.teams.clear();
            String teams = meta.tags.get("teams");
            if(teams != null && !teams.isEmpty()){
                for(String part : teams.split(",")){
                    if(!part.isEmpty()) map.teams.add(Integer.parseInt(part));
                }
            }
        }catch(Exception e){
            System.err.println("读取地图元数据失败 " + map.name + ": " + e);
        }
    }

    /** 把阵营集合编码成 {@code "0,1,2"} 形式（存进 meta tags）。 */
    private static String encodeTeams(IntSet teams){
        StringBuilder sb = new StringBuilder();
        IntSet.IntSetIterator it = teams.iterator();
        while(it.hasNext){
            if(sb.length() > 0) sb.append(',');
            sb.append(it.next());
        }
        return sb.toString();
    }
}
