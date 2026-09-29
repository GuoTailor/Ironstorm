package com.phoenix.game.maps;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntSet;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.core.Core;
import com.phoenix.game.io.MapIO;
import com.phoenix.game.io.SaveIO;
import com.phoenix.game.io.SaveMeta;
import com.phoenix.game.maps.filters.GenerateFilter;
import com.phoenix.game.util.Fi;
import com.phoenix.game.util.Log;
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
 * {@code ShuffleMode}/{@code MapProvider}（轮换策略）。
 * 缩略图链已补（F14：{@link #loadPreviews}/{@link #queueNewPreview}/{@link #createNewPreview} +
 * {@code MapIO.generatePreview}）；
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
        //对应原版 Maps.saveMap 的收尾：落盘后立刻生成缩略图（文件刚写出，离线读与内存瓦片等价）
        createNewPreview(map, null);
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
            //应用地图自带生成滤镜（对应原版 World.loadMap 里 FilterContext.end() 的双层循环；
            //F18 补齐——此前带 genfilters 标签的地图载入时滤镜不生效）
            if(Vars.world != null) Vars.world.applyFilters(map);
            System.out.println("已加载地图 " + map.name);
            return true;
        }catch(IOException e){
            System.err.println("加载地图失败 " + map.name + ": " + e);
            return false;
        }
    }

    /**
     * 把一张外部地图文件导入地图目录并加入列表（对应原版 {@code Maps.importMap(Fi)}，T14 追加）。
     * <p>流程与原版一致：复制到 {@link #nextMapFile()} 生成的新文件名 → 建 {@link Map} 并加进列表 →
     * 同步生成缩略图（{@link #createNewPreview}，F14 补齐）。
     * <p>⚠ 与原版的一处**刻意偏离**：原版在缩略图生成失败时会回滚（从列表移除 + 删文件 + 抛
     * {@code IOException}）——它假设"预览读不了 = 地图坏"。本项目放宽：v6 旧格式地图**可正常游玩**
     * 但离线预览读不了（见 {@code SaveIO} version 注释），此时保留地图、缩略图占位 nomap、
     * 只记日志，不再回滚也不抛异常。
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
        createNewPreview(map, null);
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

    // ---- 缩略图（F14；对应原版 Maps 的 loadPreviews / queueNewPreview / createNewPreview）----

    /**
     * 启动时恢复每张地图的预览纹理。对应原版 {@code Maps.loadPreviews()}（在 {@code Vars.init} 里
     * {@link #load()} 之后调用一次）。
     * <p>预览 PNG 已存在 → 直接 {@code new Texture(FileHandle)} 同步加载（**刻意偏离**：原版走
     * AssetManager + {@code MapPreviewLoader} 异步加载，本项目 {@code Core.assets} 没有逐帧
     * {@code update()} 循环，注册异步加载器只会卡死；预览 PNG 很小，同步加载的开销可忽略，
     * {@code MapPreviewLoader} 保持未注册）；PNG 缺失/未生成过 → {@link #queueNewPreview} 延后生成。
     * <p>「缓存侧车文件」（原版 {@code -cache.dat}，存 spawns/teams）不需要：本项目把这两个统计
     * 写在存档 meta 的 tags 里，{@link #load()} 时已恢复。
     * <p>headless（{@code Core.graphics == null}）直接返回：服务端不生成也不加载预览。
     */
    public void loadPreviews(){
        if(Core.graphics == null) return;

        for(int i = 0; i < maps.size; i++){
            Map map = maps.get(i);
            Fi preview = map.previewFile();
            if(preview.exists()){
                try{
                    if(map.texture != null) map.texture.dispose();
                    map.texture = new Texture(preview.handle());
                }catch(Exception e){
                    //PNG 损坏：删掉让它走重新生成（对应原版"加载失败 → queueNewPreview"的口径）
                    System.err.println("地图预览 PNG 加载失败 " + map.name + ": " + e);
                    preview.delete();
                    map.texture = null;
                    queueNewPreview(map);
                }
            }else{
                queueNewPreview(map);
            }
        }
    }

    /**
     * 把地图排进预览生成队列。对应原版 {@code Maps.queueNewPreview(Map)}。
     * <p>**刻意偏离**：原版 post 进 {@code previewList}、由 content 加载完成回调统一 drain
     * （{@code createAllPreviews}）；本项目没有那个时序锚点，直接 post 单张生成任务
     * （{@link #createNewPreview}），同样保证生成发生在主线程、不阻塞当前帧。
     * 消费点：{@link #loadPreviews}（PNG 缺失）与 {@code MapPreviewLoader} 加载失败兜底。
     */
    public void queueNewPreview(Map map){
        Core.app.postRunnable(() -> createNewPreview(map, null));
    }

    /**
     * 同步生成一张地图的缩略图并挂到 {@code map.texture}。对应原版 {@code Maps.createNewPreview(Map, Cons)}
     * （原版 private，这里公开给编辑器保存/导入/导出三个入档点复用）。
     * <p>流程：{@link MapIO#generatePreview(Map)} 离线读存档渲出 1px=1 格的 Pixmap →
     * {@code new Texture(pixmap)} 挂纹理 → 写预览 PNG（下次启动 {@link #loadPreviews} 直载）。
     * <p>与原版的差异（已声明）：
     * <ul>
     *   <li>原版 PNG 写盘走 {@code AsyncExecutor} 异步线程；本项目同步写（缩略图最多几百 KB，
     *       且必须先于 {@code pix.dispose()}，异步反而要复制像素）。</li>
     *   <li>原版不释放旧纹理（依赖 AssetManager unload 兜底）；本项目不走 AssetManager →
     *       换纹理前显式 {@code dispose} 旧的。</li>
     *   <li>原版还会顺手写 {@code -cache.dat}；本项目 spawns/teams 在 meta tags 里（见 {@link #loadPreviews}）。</li>
     *   <li>headless（无 GL）与生成失败都不抛异常：失败时记日志、调 {@code failed}（可为 null）、
     *       {@code map.texture} 保持原值（调用方按 {@code texture == null} 显示占位图）。</li>
     * </ul>
     * @param failed 生成失败回调（原版用于导入回滚；可为 null = 只记日志）
     */
    public void createNewPreview(Map map, com.phoenix.game.func.Cons<Exception> failed){
        try{
            if(Core.graphics == null) return;

            Pixmap pix = MapIO.generatePreview(map);
            try{
                //先摘掉旧引用再释放旧纹理：若 new Texture 半路抛异常，map.texture 不会指向已释放对象
                Texture old = map.texture;
                map.texture = null;
                if(old != null) old.dispose();
                map.texture = new Texture(pix);
                try{
                    map.previewFile().writePNG(pix);
                }catch(Exception pe){
                    //PNG 落盘失败不回滚纹理：本次会话仍有图，下次启动再重生成
                    Log.err(pe);
                }
            }finally{
                pix.dispose();
            }
        }catch(Exception e){
            Log.err(e);
            if(failed != null) failed.get(e);
        }
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
