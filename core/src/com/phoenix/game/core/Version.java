package com.phoenix.game.core;

/**
 * 版本信息。参照 Mindustry {@code mindustry.core.Version} 移植。
 *
 * <p><b>与原版的差异（本批最重要的一点）</b>：原版 {@link #init()} 从资源文件
 * {@code version.properties} 读取（{@code type}/{@code number}/{@code modifier}/{@code build}，
 * 其中 {@code build} 形如 {@code "126.2"} 会被拆成 {@code build=126} + {@code revision=2}）。
 * <b>本项目没有该资源文件</b>，所以这里直接**硬编码**一组常量，
 * {@link #init()} 退化为空实现（保留方法是为了让启动器可以照原版调用它）。
 *
 * <p>硬编码取值与依据：
 * <ul>
 *   <li>{@code type = "official"} —— 与参考工程 {@code core/assets/version.properties} 一致。</li>
 *   <li>{@code modifier = ""} —— 按本批要求留空（原版官方构建这里是 {@code "release"}）。</li>
 *   <li>{@code build = 126} —— 必须与 {@code NetClient}/{@code Packets} 里硬编码的
 *       {@code cp.version = 126} 保持一致，否则联机握手时版本号对不上。</li>
 *   <li>{@code revision = 2} —— "126.2" 的小数部分（与原版 {@code build="126.2"} 的拆解方式一致）。</li>
 *   <li>{@code number = 6} —— <b>主版本号</b>（Build 126 属于 Mindustry v6 时代）。
 *       注意：原版 {@code number} 是 {@code int}，所以任务描述里的 {@code number = 126.2f}
 *       无法直接表达，"126.2" 由 {@link #build} + {@link #revision} 承载。</li>
 * </ul>
 *
 * <p>原版 126.2 的 {@code Version} <b>没有</b> {@code combined}/{@code values}/{@code properties}
 * 这些成员（那是 Mindustry v7+ 才有的），本类同样不提供，避免臆造。
 *
 * <p><b>调用点已回填</b>（此前这些位置都写着"本项目没有 Version 类 → 降级"，现已接到本类）：
 * <ul>
 *   <li>{@code core/NetClient} —— 握手 {@code ConnectPacket.version} 由硬编码 {@code 126} 改为
 *       {@link #build}（首连 + 重连两处）。</li>
 *   <li>{@code io/SaveVersion} —— 存档 meta 的 {@code build} 字段写 {@link #build}。</li>
 *   <li>{@code net/CrashSender} —— 补回"自定义构建不上报"（{@link #build} {@code == -1}）与
 *       "版本号解析失败不上报"（{@link #number} {@code == 0}）两条判定。</li>
 *   <li>{@code net/BeControl#active()} —— 改为 {@code type.equals("bleeding-edge")}。</li>
 *   <li>{@code ui/dialogs/JoinDialog#safeConnect} —— 补回 {@link #build} 与服务器版本的比对。</li>
 *   <li>{@code ui/dialogs/HostDialog#runHost} —— 补回 {@link #modifier}{@code .contains("beta")} 分支。</li>
 *   <li>{@code ClientLauncher#create}/{@code ServerLauncher#main}/{@code ServerControl#create} —— 照原版调
 *       {@link #init()}。</li>
 * </ul>
 */
public class Version{
    /** Build type. 'official' for official releases; 'custom' or 'bleeding edge' are also used. */
    public static String type = "official";
    /** Build modifier, e.g. 'alpha' or 'release'. */
    public static String modifier = "";
    /** Number specifying the major version, e.g. '6'. */
    public static int number = 6;
    /** Build number, e.g. '126'. set to '-1' for custom builds. */
    public static int build = 126;
    /** Revision number. Used for hotfixes. Does not affect server compatibility. */
    public static int revision = 2;
    /** Whether version loading is enabled. */
    public static boolean enabled = true;

    /**
     * 原版从 {@code version.properties} 读入全部版本字段。
     * <p>刻意降级：本项目没有该资源文件，字段已在声明处硬编码 → 这里不再读取任何文件。
     * 保留空实现是为了让调用方（启动器/崩溃上报）能照原版写 {@code Version.init()}。
     */
    public static void init(){
        //原版：
        //Fi file = OS.isAndroid || OS.isIos ? Core.files.internal("version.properties")
        //                                  : new Fi("version.properties", FileType.internal);
        //ObjectMap<String, String> map = new ObjectMap<>();
        //PropertiesUtils.load(map, file.reader());
        //type = map.get("type"); number = Integer.parseInt(map.get("number", "4")); ...
        //—— 本项目没有 version.properties，字段值已在上面硬编码，故此处为空。
    }
}
