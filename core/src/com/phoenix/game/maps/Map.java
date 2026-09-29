package com.phoenix.game.maps;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.IntSet;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Events;
import com.phoenix.game.game.DefaultWaves;
import com.phoenix.game.game.EventType;
import com.phoenix.game.game.Gamemode;
import com.phoenix.game.game.Rules;
import com.phoenix.game.io.JsonIO;
import com.phoenix.game.maps.filters.GenerateFilter;
import com.phoenix.game.mod.Mods;
import com.phoenix.game.struct.Array;
import com.phoenix.game.struct.StringMap;
import com.phoenix.game.type.Publishable;
import com.phoenix.game.util.ArcAnnotate.*;
import com.phoenix.game.util.Fi;
import com.phoenix.game.world.meta.I18n;

import java.io.File;

/**
 * 一张地图。参照 Mindustry mindustry.maps.Map 移植（{@code implements Comparable<Map>, Publishable}）。
 *
 * <p><b>本类是从 {@link Maps} 的内部类「提升」为独立类的</b>：原内部类只有 {@code file}/{@code name}
 * 两个字段（语义 = 「一个 {@code .msav} 存档文件条目」），本批把原版 {@code Map} 的完整元数据
 * （{@code width}/{@code height}/{@code tags}/{@code teams}/{@code spawns}/{@code custom}…）搬了进来，
 * 内部类已删除，全工程改用本类。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li>{@code file} 的类型是 {@code java.io.File}（原版是 {@code arc.files.Fi}）——
 *       本项目 {@link com.phoenix.game.io.SaveIO} 与 {@link Maps} 都按 {@code File} 读写存档，
 *       保持 {@code File} 可让调用点零改动。需要 {@code Fi} 时用 {@link #fi()}。</li>
 *   <li>{@code name} 是 <b>final 字段</b>（原版是读 {@code tags["name"]} 的 {@code name()} 方法）：
 *       本项目一张地图就是一个 {@code .msav} 存档、没有独立的元数据区，显示名由**去扩展名的文件名**派生；
 *       若 {@code tags} 里带了 {@code name}（原版路径）则优先用它。因此原版的 {@code name()} 方法没有移植。</li>
 *   <li>{@code rules(Rules)} 已按原版经 {@link com.phoenix.game.io.JsonIO} 从 {@code tags["rules"]} 反序列化覆盖 base。</li>
 *   <li>{@code filters()} 委托 {@code Vars.maps.readFilters(tags["genfilters"])}（{@link Maps#readFilters} 已补）。</li>
 *   <li>{@code previewFile()}/{@code cacheFile()} 原版落在 {@code Vars.mapPreviewDirectory} 下，
 *       本项目没有该目录 → 刻意降级到与地图文件同目录（预览 {@code <name>.png}、缓存 {@code <name>-cache.dat}）。</li>
 *   <li>{@code safeTexture()} 原版退回 {@code Core.assets.get("sprites/error.png")}，本项目改用图集里的
 *       {@code error} 区域（已存在），缺失时再退回 {@code nomap}；注意它返回的是**整张图集纹理**，调用方要自己判 {@link #texture}。</li>
 *   <li>{@code setHighScore} 原版调 {@code Vars.data.modified()}；本项目 {@code GlobalData} 已有该方法，
 *       额外补一次 {@code Core.settings.save()}（本项目 Settings 不自动落盘）。</li>
 *   <li>Publishable 的 {@code addSteamID}/{@code removeSteamID}/{@code prePublish} 原版会同步写回地图编辑器
 *       （{@code ui.editor}）→ 本项目无地图编辑器，该段省略。</li>
 *   <li>{@code toString()} 沿用内部类时代的「返回显示名」（原版是冗长的调试串）。</li>
 * </ul>
 */
public class Map implements Comparable<Map>, Publishable{
    /**
     * 地图目录名（应用本地目录下）。原版对应 {@code Vars.customMapDirectory}；本项目 {@code Vars} 里
     * 没有任何目录常量 → 就地命名常量，与 {@link Maps} 的 {@code MAPS_DIR} 同值（{@code "maps"}）。
     */
    private static final String MAPS_DIR = "maps";

    /** Whether this is a custom map. */
    public final boolean custom;
    /** Metadata. Author description, display name, etc. */
    public final StringMap tags;
    /** Base file of this map. 原版是 {@code Fi}，本项目是 {@code File}（见类注释）。 */
    public final File file;
    /** 显示名：优先 {@code tags["name"]}，否则去扩展名的文件名。 */
    public final String name;
    /** Format version. */
    public final int version;
    /** Whether this map is managed, e.g. downloaded from the Steam workshop. */
    public boolean workshop;
    /** Map width/height, shorts. */
    public int width, height;
    /** Preview texture. 本项目尚未加载每张地图的预览 PNG，恒为 null（见 {@link #safeTexture()}）。 */
    public Texture texture;
    /** Build that this map was created in. -1 = unknown or custom build. */
    public int build;
    /** All teams present on this map. 对应原版 {@code arc.struct.IntSet} → libgdx {@code IntSet}。 */
    public IntSet teams = new IntSet();
    /** Number of enemy spawns on this map. */
    public int spawns = 0;
    /** Associated mod. If null, no mod is associated. */
    public @Nullable Mods.LoadedMod mod;

    public Map(File file, int width, int height, StringMap tags, boolean custom, int version, int build){
        this.custom = custom;
        this.tags = tags;
        this.file = file;
        this.width = width;
        this.height = height;
        this.version = version;
        this.build = build;
        this.name = resolveName(file, tags);
    }

    public Map(File file, int width, int height, StringMap tags, boolean custom, int version){
        this(file, width, height, tags, custom, version, -1);
    }

    public Map(File file, int width, int height, StringMap tags, boolean custom){
        this(file, width, height, tags, custom, -1);
    }

    /** 本项目的主构造器（原内部类的签名）：一张地图 = 一个 {@code .msav} 存档文件。 */
    public Map(File file){
        this(file, 0, 0, new StringMap(), true, -1, -1);
    }

    /**
     * 原版 {@code Map(StringMap)}：文件落在 {@code Vars.customMapDirectory} 下。
     * <p>刻意降级：本项目没有 {@code Vars.customMapDirectory}（Vars 无任何目录常量）→ 落到与 {@link Maps}
     * 同值的 {@link #MAPS_DIR}（{@code maps/}）目录。
     */
    public Map(StringMap tags){
        this(new File(mapsDirectory(), tags.get("name", "unknown")), 0, 0, tags, true, -1, -1);
    }

    /** @return 本类的 {@code file} 对应的 {@code Fi} 句柄（原版里 {@code file} 本身就是 {@code Fi}）。 */
    public Fi fi(){
        return new Fi(file);
    }

    /** @return 去扩展名的文件名（本项目里等于默认显示名）。 */
    public String nameWithoutExtension(){
        String n = file.getName();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    public int getHightScore(){
        return Core.settings.getInt("hiscore" + nameWithoutExtension(), 0);
    }

    public Texture safeTexture(){
        if(texture != null) return texture;
        //原版返回 Core.assets.get("sprites/error.png")；本项目图集里已有 error 区域（sprites.atlas），缺失时退回 nomap
        if(Core.atlas == null) return null;
        TextureRegion region = Core.atlas.findRegion("error");
        if(region == null) region = Core.atlas.findRegion("nomap");
        return region == null ? null : region.getTexture();
    }

    /**
     * 刻意降级：原版 = {@code Vars.mapPreviewDirectory.child(...)}；本项目没有该目录
     * → 落在与地图文件同目录的同名 {@code .png}（与 {@link com.phoenix.game.io.SavePreview} 的约定一致）。
     */
    public Fi previewFile(){
        File parent = file.getParentFile();
        String base = workshop ? parent.getName() : nameWithoutExtension();
        return new Fi(new File(parent, base + ".png"));
    }

    /** 刻意降级（同 {@link #previewFile()}）：原版在 {@code Vars.mapPreviewDirectory} 下。 */
    public Fi cacheFile(){
        File parent = file.getParentFile();
        String base = workshop ? parent.getName() + "-workshop-cache.dat" : nameWithoutExtension() + "-cache.dat";
        return new Fi(new File(parent, base));
    }

    public void setHighScore(int score){
        Core.settings.put("hiscore" + nameWithoutExtension(), score);
        Core.settings.save();
        //原版只调 Vars.data.modified()（arc 的 Settings 自动落盘）；本项目 Settings 不自动落盘 → 补 save()
        if(Vars.data != null) Vars.data.modified();
    }

    /** Returns the result of applying this map's rules to the specified gamemode. */
    public Rules applyRules(Gamemode mode){
        //mode specific defaults have been applied
        Rules out = new Rules();
        mode.apply(out);

        //now apply map-specific overrides
        return rules(out);
    }

    /** This creates a new instance of Rules. */
    public Rules rules(){
        return rules(new Rules());
    }

    /**
     * 从地图元数据 {@code tags["rules"]} 反序列化规则覆盖 {@code base}（对应原版
     * {@code JsonIO.read(Rules.class, base, tags.get("rules", "{}"))}）。
     * <p>兼容性：元数据没有 {@code rules} 键时取 {@code "{}"} → 反序列化不覆盖任何字段，等价于原样返回 {@code base}
     * （再补默认波次）；元数据含未知字段时 {@code JsonIO} 已开启 {@code ignoreUnknownFields} → 忽略；
     * 解析失败时按原版行为退回一套全新默认规则（{@code new Rules()}）。
     */
    public Rules rules(Rules base){
        try{
            Rules result = JsonIO.read(Rules.class, base, tags.get("rules", "{}"));
            if(result.spawns == null || result.spawns.size == 0){
                result.spawns = new DefaultWaves().get();
            }
            return result;
        }catch(Exception e){
            //原版行为：读规则出错时忽略，返回一套默认规则
            e.printStackTrace();
            return new Rules();
        }
    }

    /**
     * 本图载入时要应用的地图生成滤镜。参照原版 {@code Map.filters()} 移植。
     *
     * <p><b>原版语义</b>：旧地图（{@code build < 83} 且非 -1、且没有 {@code genfilters} 标签）没有滤镜，
     * 直接返回空；否则委托 {@code Vars.maps.readFilters(tags["genfilters"])} —— 空串时构造一套默认滤镜
     * （若干 {@code ScatterFilter} + 默认矿脉），非空时 {@code JsonIO.read(Array.class, str)} 反序列化。
     */
    public Array<GenerateFilter> filters(){
        if(tags.getInt("build", -1) < 83 && tags.getInt("build", -1) != -1 && tags.get("genfilters", "").isEmpty()){
            return new Array<>();
        }
        //原版 = maps.readFilters(tags.get("genfilters", ""))；本项目 Maps.readFilters 已补（JsonIO 已就位）
        return Vars.maps.readFilters(tags.get("genfilters", ""));
    }

    public String author(){
        return tag("author");
    }

    public String description(){
        return tag("description");
    }

    /**
     * 取标签值；缺失或空白时回退到 "unknown"。
     * <p>原版回退到 {@code Core.bundle.get("unknown")}；本项目 bundle 缺键会得到 {@code ???key???}
     * → 走 {@link I18n#get} 的双形态判定。
     */
    public String tag(String key){
        return tags.containsKey(key) && !tags.get(key).trim().isEmpty() ? tags.get(key) : I18n.get("unknown", "unknown");
    }

    public boolean hasTag(String key){
        return tags.containsKey(key);
    }

    // ---- Publishable（Steam 创意工坊；本项目未接线，仅保持 API 完整）----

    @Override
    public String getSteamID(){
        return tags.get("steamid");
    }

    @Override
    public void addSteamID(String id){
        tags.put("steamid", id);
        //刻意降级：原版会把 id 写回地图编辑器（Vars.editor.getTags() + Vars.ui.editor.save()）。
        //本项目这两套 API 其实都在，但 addSteamID 只在 Steam 发布路径被调用，本项目无 Steam → 不可达。
        Events.fire(new EventType.MapPublishEvent());
    }

    @Override
    public void removeSteamID(){
        tags.remove("steamid");
        //刻意降级：同 addSteamID（API 都在，但 Steam 发布路径本项目不可达）。
    }

    @Override
    public String steamTitle(){
        return name;
    }

    @Override
    public String steamDescription(){
        return description();
    }

    @Override
    public String steamTag(){
        return "map";
    }

    /** 刻意降级：原版 = {@code Vars.tmpDirectory.child("map_" + id)}；本项目没有 tmpDirectory → 用系统临时目录。 */
    @Override
    public Fi createSteamFolder(String id){
        Fi dir = Fi.tempDirectory("map_" + id);
        fi().copyTo(dir.child("map.msav"));
        return dir;
    }

    @Override
    public Fi createSteamPreview(String id){
        return previewFile();
    }

    @Override
    public Array<String> extraTags(){
        Gamemode mode = Gamemode.attack.valid(this) ? Gamemode.attack : Gamemode.survival;
        return Array.with(mode.name());
    }

    @Override
    public boolean prePublish(){
        if(Vars.player != null){
            tags.put("author", Vars.player.name);
        }
        //刻意降级：原版还会把 author 同步写回地图编辑器（Vars.editor.getTags() + Vars.ui.editor.save()），
        //API 都在，但 prePublish 只在 Steam 发布路径被调用，本项目无 Steam → 不可达。
        return true;
    }

    @Override
    public int compareTo(Map map){
        int work = -Boolean.compare(workshop, map.workshop);
        if(work != 0) return work;
        int type = -Boolean.compare(custom, map.custom);
        if(type != 0) return type;
        int modes = Boolean.compare(Gamemode.pvp.valid(this), Gamemode.pvp.valid(map));
        if(modes != 0) return modes;

        return name.compareTo(map.name);
    }

    @Override
    public String toString(){
        return name;
    }

    // ---- 工具 ----

    private static String resolveName(File file, StringMap tags){
        if(tags != null && tags.containsKey("name")){
            String tagged = tags.get("name");
            if(tagged != null && !tagged.trim().isEmpty()) return tagged;
        }
        if(file == null) return "unknown";
        String n = file.getName();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private static File mapsDirectory(){
        return Gdx.files == null ? new File(MAPS_DIR) : Gdx.files.local(MAPS_DIR).file();
    }
}
