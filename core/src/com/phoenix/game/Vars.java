package com.phoenix.game;

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
    /** 波次生成器（世界加载时自行挂接 WorldLoadEvent 扫描出生点）。 */
    public static com.phoenix.game.ai.WaveSpawner spawner;
    /** 存档槽位管理器（应用启动后 load()，读到本地槽位）。 */
    public static com.phoenix.game.game.Saves saves;
    /** 地图管理（服务端地图列表/导出）。 */
    public static com.phoenix.game.maps.Maps maps;
    /** 模组/插件加载器。 */
    public static com.phoenix.game.mod.Mods mods;

    /** HUD 根组件。 */
    public static com.phoenix.game.ui.fragments.HudFragment hud;
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

        if(loadLocales){
            //load locales
            String[] stra = Core.files.internal("locales").readString().split("\n");
            locales = new Locale[stra.length];
            for(int i = 0; i < locales.length; i++){
                String code = stra[i];
                if(code.contains("_")){
                    locales[i] = Locale.of(code.split("_")[0], code.split("_")[1]);
                }else{
                    locales[i] = Locale.of(code);
                }
            }
            Arrays.sort(locales, (o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getDisplayName(), o2.getDisplayName()));
        }

        collisions = new EntityCollisions();
        pathfinder = new com.phoenix.game.ai.Pathfinder();
        spawner = new com.phoenix.game.ai.WaveSpawner();
        saves = new com.phoenix.game.game.Saves();
        saves.load();
        maps = new com.phoenix.game.maps.Maps();
        maps.load();
        mods = new com.phoenix.game.mod.Mods();
        mods.load();
    }

}
