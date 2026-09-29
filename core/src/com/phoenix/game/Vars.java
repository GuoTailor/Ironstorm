package com.phoenix.game;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.core.Control;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.GameState;
import com.phoenix.game.entities.EntityCollisions;
import com.phoenix.game.core.World;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Locale;

public class Vars {
    /** Whether to load locales.*/
    public static boolean loadLocales = true;
    /** Whether the logger is loaded. */
    public static boolean loadedLogger = false, loadedFileLogger = false;
    /** Maximum schematic size.*/
    public static final int maxSchematicSize = 32;
    /** All schematic base64 starts with this string.*/
    public static final String schematicBaseStart ="bXNjaAB";
    /** IO buffer size. */
    public static final int bufferSize = 8192;
    /** global charset, since Android doesn't support the Charsets class */
    public static final Charset charset = Charset.forName("UTF-8");
    /** main application name, capitalized */
    public static final String appName = "Mindustry";
    /** URL for itch.io donations. */
    public static final String donationURL = "https://anuke.itch.io/mindustry/purchase";
    /** URL for discord invite. */
    public static final String discordURL = "https://discord.gg/mindustry";
    /** URL for sending crash reports to */
    public static final String crashReportURL = "http://192.99.169.18/report";
    /** URL the links to the wiki's modding guide.*/
    public static final String modGuideURL = "https://mindustrygame.github.io/wiki/modding/";
    /** URL to the JSON file containing all the global, public servers. Not queried in BE. */
    public static final String serverJsonURL = "https://raw.githubusercontent.com/Anuken/Mindustry/master/servers.json";
    /** URL to the JSON file containing all the BE servers. Only queried in BE. */
    public static final String serverJsonBeURL = "https://raw.githubusercontent.com/Anuken/Mindustry/master/servers_be.json";
    /** URL of the github issue report template.*/
    public static final String reportIssueURL = "https://github.com/Anuken/Mindustry/issues/new?template=bug_report.md";
    /** list of built-in servers.*/
    public static final Array<String> defaultServers = Array.with();
    /** maximum distance between mine and core that supports automatic transferring */
    public static final float mineTransferRange = 220f;
    /** whether to enable editing of units in the editor */
    public static final boolean enableUnitEditing = false;
    /** max chat message length */
    public static final int maxTextLength = 150;
    /** max player name length in bytes */
    public static final int maxNameLength = 40;
    /** displayed item size when ingame, TODO remove. */
    public static final float itemSize = 5f;
    /** extra padding around the world; units outside this bound will begin to self-destruct. */
    public static final float worldBounds = 100f;
    /** units outside of this bound will simply die instantly */
    public static final float finalWorldBounds = worldBounds + 500;
    /** ticks spent out of bound until self destruct. */
    public static final float boundsCountdown = 60 * 7;
    /** for map generator dialog */
    public static boolean updateEditorOnChange = false;
    /** size of tiles in units */
    public static final int tilesize = 8;
    /** all choosable player colors in join/host dialog */
    public static final Color[] playerColors = {
        Color.valueOf("82759a"),
        Color.valueOf("c0c1c5"),
        Color.valueOf("ffffff"),
        Color.valueOf("7d2953"),
        Color.valueOf("ff074e"),
        Color.valueOf("ff072a"),
        Color.valueOf("ff76a6"),
        Color.valueOf("a95238"),
        Color.valueOf("ffa108"),
        Color.valueOf("feeb2c"),
        Color.valueOf("ffcaa8"),
        Color.valueOf("008551"),
        Color.valueOf("00e339"),
        Color.valueOf("423c7b"),
        Color.valueOf("4b5ef1"),
        Color.valueOf("2cabfe"),
    };
    /** default server port */
    public static final int port = 6567;
    /** multicast discovery port.*/
    public static final int multicastPort = 20151;
    /** multicast group for discovery.*/
    public static final String multicastGroup = "227.2.7.7";
    /** if true, UI is not drawn */
    public static boolean disableUI;
    /** if true, game is set up in mobile mode, even on desktop. used for debugging */
    public static boolean testMobile;
    /** whether the game is running on a mobile device */
    public static boolean mobile;
    /** whether the game is running on an iOS device */
    public static boolean ios;
    /** whether the game is running on an Android device */
    public static boolean android;
    /** whether the game is running on a headless server */
    public static boolean headless;
    /** whether steam is enabled for this game */
    public static boolean steam;
    /** whether typing into the console is enabled - developers only */
    public static boolean enableConsole = false;
    /** map file extension */
    public static final String mapExtension = "msav";
    /** save file extension */
    public static final String saveExtension = "msav";
    /** schematic file extension */
    public static final String schematicExtension = "msch";

    /** list of all locales that can be switched to */
    public static Locale[] locales;

    public static EntityCollisions collisions;

    /**
     * mod 文件树（对应原版 {@code Vars.tree}）：mod 里的文件优先，找不到时回退到游戏内资源。
     * <p><b>只声明不 new</b>：赋值时机照 {@link #hud}/{@link #ui} 的惯例由启动器负责，
     * 且必须早于 {@link #init()} —— 原版顺序是
     * {@code if(tree == null) tree = new FileTree(); if(mods == null) mods = new Mods();}
     * （{@code init()} 内部会 {@code mods.load()}，mod 解析要用到它）。
     * <p>本项目 mod 内容加载尚未接线（{@code ContentParser} 里的 {@code Vars.tree.get(...)} 仍是注释），
     * 所以本实例目前是"可用的空树"：{@link com.phoenix.game.core.FileTree#get} 找不到就走
     * {@code Core.files.internal(path)}。
     */
    public static com.phoenix.game.core.FileTree tree;

    /**
     * 实体分组容器（对应原版 {@code Vars.entities}）。
     * <p><b>只声明不 new</b>：赋值时机照 {@link #hud}/{@link #ui} 的惯例由启动器负责，
     * 必须在 {@code Vars.content.load()} 之后（{@link com.phoenix.game.entities.EntityGroup} 的构造会建
     * {@code QuadTree}，且组内类型全是内容相关的实体类）。
     * <p><b>接线现状</b>：{@code Entity} 已补上 {@code getGroup()/setGroup()}，{@code EntityGroup}
     * 的延迟 add/remove 也做了池化回收防护（见 {@code EntityGroup} 类注释）。目前**已接线**的是
     * {@link #puddleGroup} / {@link #playerGroup} / {@link #fireGroup} / {@link #effectGroup}
     * 四个组（对应的 {@code Puddle}/{@code Player}/{@code Fire}/{@code ItemTransfer} 在创建/移除时
     * 自行 {@code add()}/{@code remove()}）；其余组（tile/bullet/groundEffect/shield/unit）仍为空 ——
     * 它们的仿真仍以 {@code Units.units} / {@code Bullet.all} 等静态数组为准（{@code Bullet} 是池化实体，
     * 接线需要先照原版把 {@code removed()} 也改成延迟调用，见 {@code EntityGroup} 类注释）。
     */
    public static com.phoenix.game.entities.Entities entities;

    /** 本地/远程玩家组（对应原版 {@code Vars.playerGroup}）。 */
    public static com.phoenix.game.entities.EntityGroup<com.phoenix.game.entities.type.Player> playerGroup;
    /** 燃烧实体组（对应原版 {@code Vars.fireGroup}）。 */
    public static com.phoenix.game.entities.EntityGroup<com.phoenix.game.entities.effect.Fire> fireGroup;
    /** 液体水洼实体组（对应原版 {@code Vars.puddleGroup}）。 */
    public static com.phoenix.game.entities.EntityGroup<com.phoenix.game.entities.effect.Puddle> puddleGroup;
    /**
     * 特效实体组（对应原版 {@code Vars.effectGroup}）。
     * <p>原版是 {@code entities.add(EffectEntity.class, false)}（组 id 3）。本项目目前**只有
     * {@code ItemTransfer} 用它**（{@code EffectEntity} 本身尚未由 {@code Effects} 创建）：
     * 它不在任何静态数组里，只能靠这个组每帧 update/draw，并在生命周期结束时 {@code remove()}
     * → {@code removed()} 里执行"物品真正入库"的回调。
     * <p>由 {@code Logic.update()} 调 {@code update()}、{@code Renderer.draw()} 调 {@code draw()}。
     */
    public static com.phoenix.game.entities.EntityGroup<com.phoenix.game.entities.type.EffectEntity> effectGroup;

    /**
     * 方块索引器（对应原版 {@code Vars.indexer}）：AI 索敌 / 受损建筑 / 矿脉的索引。
     * <p><b>只声明不 new</b>：赋值时机由启动器负责，但必须早于第一次
     * {@link com.phoenix.game.game.EventType.WorldLoadEvent}（{@code Control.play/playMap/load} 都会发）——
     * {@link com.phoenix.game.ai.BlockIndexer} 的构造器自己挂 WorldLoadEvent/TileChangeEvent，
     * 世界加载时做首遍全图扫描。
     */
    public static com.phoenix.game.ai.BlockIndexer indexer;

    /**
     * 全部游戏内容（对应原版 {@code Vars.content}）。
     * <p><b>必须早于任何内容类创建</b>：{@code Content} 的构造函数会向它注册并取 id。
     * 内容集合由 {@code content.createBaseContent()} 触发加载。
     */
    public static com.phoenix.game.core.ContentLoader content = new com.phoenix.game.core.ContentLoader();

    /** 当前地图（进入战役后赋值）。 */
    public static World world;
    /** 当前游戏状态。 */
    public static GameState state = new GameState();
    /** 游戏控制（开始战役/加载地图）。 */
    public static Control control = new Control();
    /** 本地玩家（进入战役时创建，对应原版 Vars.player）。 */
    public static com.phoenix.game.entities.type.Player player;
    /** 世界渲染器（相机缩放等状态保存在这里）。 */
    public static com.phoenix.game.core.Renderer renderer;
    /** 流场寻路器（世界加载时在 Pathfinder 内自行挂接 WorldLoadEvent 启动后台线程）。 */
    public static com.phoenix.game.ai.Pathfinder pathfinder;
    /** RTS 命令寻路器（按目的地缓存流场，对应原版 Vars.controlPath）。 */
    public static com.phoenix.game.ai.ControlPathfinder controlPath;
    /** 波次生成器（世界加载时自行挂接 WorldLoadEvent 扫描出生点）。 */
    public static com.phoenix.game.ai.WaveSpawner spawner;
    /** 存档槽位管理器（应用启动后 load()，读到本地槽位）。 */
    public static com.phoenix.game.game.Saves saves;
    /** 地图管理（服务端地图列表/导出）。 */
    public static com.phoenix.game.maps.Maps maps;
    /** 模组/插件加载器。 */
    public static com.phoenix.game.mod.Mods mods;
    /** 全局库存（战役跨区运输，对应原版 Vars.data）。 */
    public static com.phoenix.game.game.GlobalData data;
    /** 蓝图管理（读 local/schematics/*.msch）。 */
    public static com.phoenix.game.game.Schematics schematics;

    /** HUD 根组件。 */
    public static com.phoenix.game.ui.fragments.HudFragment hud;
    /**
     * UI 门面（对话框 + showXxx 便捷弹窗，对应原版 {@code Vars.ui}）。
     * <p>只声明不 new —— 赋值时机与 {@link #hud} 一样由启动器负责，
     * 且必须在 {@code Vars.content.load()}（科技树 root 就绪）与
     * {@code FloatingDialog.scene = stage}（对话框需要 Stage）之后。
     */
    public static com.phoenix.game.core.UI ui;
    /**
     * 地图编辑器模型（对应原版 {@code Vars.ui.editor.editor}）。
     * <p>原版从 UI 门面取（{@code ui.editor} 是 {@code MapEditorDialog}），本项目的
     * {@code MapEditorDialog} 属于 D2b 尚未移植、{@link #ui} 里也没有 {@code editor} 字段
     * → 先用这个全局字段当入口：{@code EditorTile} 记录撤销操作时要用它（{@code op(...)}）。
     * <p><b>只声明不 new</b>（与 {@link #hud}/{@link #ui} 一样由启动器/对话框赋值）；
     * D2b 完成后可改为从 {@code Vars.ui.editor} 取。
     */
    public static com.phoenix.game.editor.MapEditor editor;
    /** 服务端（多人服务器，阶段 3 起）。 */
    public static com.phoenix.game.core.NetServer netServer;
    /** 客户端（连接服务器，阶段 3 起）。 */
    public static com.phoenix.game.core.NetClient netClient;
    /** 网络门面（传输层实例由 netServer/netClient 持有）。 */
    public static com.phoenix.game.net.Net net;

    /**
     * 当前是否为联机客机（对应原版 {@code net.client()}）。
     * <p>本机 hosting 时返回 false；单机（既非客机也非服务端）同样返回 false —— 单机跑完整仿真。
     */
    public static boolean isClient(){
        if(netServer != null && netServer.isHosting()) return false;
        return netClient != null && netClient.isConnected();
    }

    /** 当前是否为服务端（对应原版 {@code net.server()}）。 */
    public static boolean isServer(){
        return netServer != null && netServer.isHosting();
    }

//    @Override
    public void loadAsync(){
        init();
    }

    public static void init(){

        //平台判定（对应原版 Vars.init 里的 mobile/ios/android 赋值）。必须在选输入处理器之前算出来。
        mobile = Core.app.getType() == Application.ApplicationType.Android
            || Core.app.getType() == Application.ApplicationType.iOS || testMobile;
        ios = Core.app.getType() == Application.ApplicationType.iOS;
        android = Core.app.getType() == Application.ApplicationType.Android;

        //移动端用触摸输入，桌面端用键鼠（对应原版 Control.createPlayer 里的分支）
        control.setInput(mobile
            ? new com.phoenix.game.input.MobileInput()
            : new com.phoenix.game.input.DesktopInput());

        if(loadLocales){
            //load locales
            String[] stra = Core.files.internal("locales").readString().split("\n");
            locales = new Locale[stra.length];
            for(int i = 0; i < locales.length; i++){
                String code = stra[i];
                if(code.contains("_")){
                    //Locale.of 是 Java 19+ API，这里用构造器以便整个工程编译到 Java 17（Android 的 D8 不认 class v65）
                    locales[i] = new Locale(code.split("_")[0], code.split("_")[1]);
                }else{
                    locales[i] = new Locale(code);
                }
            }
            Arrays.sort(locales, (o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getDisplayName(), o2.getDisplayName()));
        }

        collisions = new EntityCollisions();
        pathfinder = new com.phoenix.game.ai.Pathfinder();
        controlPath = new com.phoenix.game.ai.ControlPathfinder();
        //RTS 命令系统内容（对应原版 ContentLoader.load 里的 UnitCommand.loadAll / UnitStance.loadAll）
        com.phoenix.game.ai.UnitCommand.loadAll();
        com.phoenix.game.ai.UnitStance.loadAll();
        spawner = new com.phoenix.game.ai.WaveSpawner();
        saves = new com.phoenix.game.game.Saves();
        saves.load();
        maps = new com.phoenix.game.maps.Maps();
        maps.load();
        mods = new com.phoenix.game.mod.Mods();
        mods.load();

        //全局库存（跨区运输）：本地持久化，启动时读一次
        data = new com.phoenix.game.game.GlobalData();
        data.load();

        //蓝图：扫描 local/schematics 下的 .msch
        schematics = new com.phoenix.game.game.Schematics();
        schematics.load();

        //界面缩放（对应原版 Vars.loadSettings 里的 `Scl.setProduct(settings.getInt("uiscale", 100) / 100f)`）。
        //此前该设置项在设置菜单里有滑块但**全项目没有调用方**（静默失效，见 plans/phoenix-silent-gaps.md）。
        //⚠ 必须在 UI 构建之前执行（Scl.scl 会被各处用来算尺寸），且此时 Core.app 已就绪（Scl.scl 内部要读它，
        //   不能在类初始化阶段调）。
        com.phoenix.game.core.Scl.setProduct(Core.settings.getInt("uiscale", 100) / 100f);
    }

}
