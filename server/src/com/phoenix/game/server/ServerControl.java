package com.phoenix.game.server;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.content.Items;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.GameState;
import com.phoenix.game.core.Logic;
import com.phoenix.game.core.Time;
import com.phoenix.game.core.World;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.game.DefaultWaves;
import com.phoenix.game.game.EventType;
import com.phoenix.game.game.Rules;
import com.phoenix.game.game.Team;
import com.phoenix.game.game.Teams;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.world.Build;
import com.phoenix.game.world.Tile;

/**
 * 服务端主控制器（headless）。对应原版 Mindustry 的 mindustry.server.ServerControl。
 * <p>阶段 1 职责：
 * <ul>
 *   <li>初始化 Core/Vars 单例（headless 环境，无渲染/输入/本地玩家）。</li>
 *   <li>启动一张世界地图并逐帧推进世界仿真（复用 {@link Logic#update()}）。</li>
 *   <li>标准输入控制台线程 + 基础命令（help/status/runwave/exit 等）。</li>
 * </ul>
 * 网络层、地图轮换、存档、封禁管理等在后续阶段接入，本阶段只搭 headless 骨架。
 */
public class ServerControl extends ApplicationAdapter {

    /** 地图尺寸（瓦片），与客户端 Control 的默认战役一致。 */
    private static final int mapWidth = 120, mapHeight = 120;

    private boolean started;
    /** 是否正在退出（退出后停止渲染循环与控制台线程）。 */
    private volatile boolean quitting;
    /** 控制台线程。 */
    private Thread consoleThread;
    /** 插件注册的命令表（name → 命令）。 */
    private final com.badlogic.gdx.utils.ObjectMap<String, com.phoenix.game.mod.CommandHandler.Command> pluginCommands =
        new com.badlogic.gdx.utils.ObjectMap<>();

    public ServerControl(String[] args){
        //阶段 1 暂不解析启动参数，保留字段供后续地图/模式解析使用
    }

    /** headless 启动：初始化单例并启动世界。对应原版 ServerLauncher.init。 */
    @Override
    public void create(){
        //headless 后端提供了 Gdx.app/files/graphics，填充 Core 静态单例（Core 是 arc.Core 的替代层）
        Core.app = Gdx.app;
        Core.graphics = Gdx.graphics;
        Core.files = Gdx.files;
        Core.input = Gdx.input;

        //内容 load() 会查询 Core.atlas.findRegion(...)；headless 无图集，给一个空图集让 findRegion 返回 null，
        //渲染期才用到的贴图字段在 headless 下保持 null，仿真逻辑不依赖它们
        Core.atlas = new com.badlogic.gdx.graphics.g2d.TextureAtlas();

        //UnitType 构造会读 Core.bundle 做本地化名称（如单位显示名），需要加载 bundle
        Core.bundle = com.badlogic.gdx.utils.I18NBundle.createBundle(
            Gdx.files.internal("i18n/bundle"), java.util.Locale.getDefault());

        Vars.headless = true;
        Vars.loadLocales = false; //headless 不加载本地化，避免依赖资源文件

        //初始化单例：collisions / pathfinder / spawner / saves
        Vars.init();

        //内容定义
        Blocks.load();
        new com.phoenix.game.content.UnitTypes().load();

        //网络服务端
        Vars.netServer = new com.phoenix.game.core.NetServer();
        Vars.netServer.init();
        Vars.netServer.admins.load(); //读入封禁/白名单/管理员档案
        Vars.netServer.openServer(Vars.port);

        //进入世界
        startGame();

        started = true;

        //标准输入控制台线程（daemon，读一行执行一条命令）
        consoleThread = new Thread(this::consoleLoop, "Server Console");
        consoleThread.setDaemon(true);
        consoleThread.start();

        //加载插件并按需初始化、注册服务端命令
        initPlugins();

        System.out.println("Phoenix 服务器已启动（headless）。输入 help 查看命令，exit 退出。");
    }

    /** 遍历所有已加载模组/插件，调用 init() 并注册服务端命令到统一命令处理器。 */
    private void initPlugins(){
        Vars.mods.eachClass(mod -> mod.init());

        com.phoenix.game.mod.CommandHandler pluginHandler = new com.phoenix.game.mod.CommandHandler("");
        Vars.mods.eachClass(mod -> mod.registerServerCommands(pluginHandler));
        //把插件注册的服务端命令合入控制台命令表
        for(com.phoenix.game.mod.CommandHandler.Command cmd : pluginHandler.getCommands()){
            registerExtraCommand(cmd);
        }
        if(Vars.mods.list().size > 0){
            System.out.println("已加载插件/模组 " + Vars.mods.list().size + " 个");
        }
    }

    /** 把插件命令包装进主控制台分发。 */
    private void registerExtraCommand(com.phoenix.game.mod.CommandHandler.Command cmd){
        pluginCommands.put(cmd.name, cmd);
    }

    /**
     * 启动一张世界地图并进入 playing 状态（headless 安全路径）。
     * <p>对应客户端 {@code Control.play()}，但去掉玩家单位/相机/输入/渲染器等客户端耦合，
     * 只保留世界、规则、队伍、核心与波次生成。
     */
    public void startGame(){
        Vars.world = new World();
        Vars.world.createMap(mapWidth, mapHeight);

        //队伍与规则必须在放置核心前重置：核心实体初始化时会挂到本队共享库存上
        Vars.state.rules = new Rules();
        Vars.state.teams = new Teams();
        Vars.state.gameOver = false;

        //波次：默认编成 + 开局宽限
        Vars.state.wave = 1;
        Vars.state.enemies = 0;
        Vars.state.rules.spawns = new DefaultWaves().get();
        Vars.state.wavetime = Vars.state.rules.waveSpacing * 2f;

        //建造材料返还比例 + 开局材料（AI 阵营用不到，但保持状态一致）
        Build.refundMultiplier = Vars.state.rules.deconstructRefundMultiplier;
        Vars.state.rules.loadout = new ItemStack[]{
            new ItemStack(Items.copper, 200), new ItemStack(Items.lead, 100), new ItemStack(Items.silicon, 60)
        };
        com.phoenix.game.world.modules.ItemModule startItems = Vars.state.teams.items(Team.sharded);
        for(ItemStack stack : Vars.state.rules.loadout){
            startItems.add(stack.item, stack.amount);
        }

        //双方核心（客户端战役布局一致：crux 在下、sharded 在上）
        placeCore(mapWidth / 2, (int)(mapHeight * 0.35f), Team.crux);
        placeCore(mapWidth / 2, (int)(mapHeight * 0.65f), Team.sharded);

        Vars.state.set(GameState.State.playing);

        Units.units.clear();
        Bullet.all.clear();
        Effects.clear();
        Time.clear();

        //世界加载事件：触发寻路器重建网格、波次生成器扫描出生点（与客户端 Control.play 一致）
        Events.fire(new EventType.WorldLoadEvent());
        Vars.spawner.reset();

        //把新世界重发给所有在线客户端，并在新世界里重建他们的单位。
        //少了这一步，终局重开地图时客户端会一直停留在旧世界上（旧世界 + 孤儿玩家单位）。
        if(Vars.netServer != null){
            Vars.netServer.resendWorldToAll();
        }

        System.out.println("服务器世界已启动：" + mapWidth + "x" + mapHeight + "，第 " + Vars.state.wave + " 波");
    }

    /** 在指定瓦片放置核心（带队营）。 */
    private void placeCore(int x, int y, Team team){
        Tile tile = Vars.world.tile(x, y);
        if(tile == null) return;
        tile.setBlock(Blocks.core, team, 0);
    }

    /** 每帧推进世界仿真（headless 后端会在独立线程循环调用）。 */
    @Override
    public void render(){
        if(quitting) return;

        //终局：自动轮换到下一张地图（阶段 4）
        if(Vars.state.gameOver){
            rotateMap();
            return;
        }

        if(Vars.state.isPlaying()){
            Logic.update();
        }
        //网络：drain 入站包 + 周期性同步单位状态
        if(Vars.netServer != null){
            Vars.netServer.update();
        }
    }

    /** 终局自动加载下一张地图（列表为空则重启当前默认图）。 */
    private void rotateMap(){
        if(Vars.maps.all().size == 0){
            System.out.println("无可用地图，重启当前默认图");
            startGame();
            return;
        }
        //选一张不同于当前名字的地图
        String current = currentMapName();
        com.phoenix.game.maps.Maps.Map next = null;
        for(com.phoenix.game.maps.Maps.Map m : Vars.maps.all()){
            if(!m.name.equals(current)){ next = m; break; }
        }
        if(next == null) next = Vars.maps.all().first();
        loadMapFromFile(next);
    }

    /** @return 当前地图显示名（world 为 null 或未绑定地图时返回空）。 */
    private String currentMapName(){
        if(Vars.world == null) return "";
        //程序生成的默认图没有文件名，返回固定名
        return "default";
    }

    /** 从指定 Map 读档替换世界（替换后触发世界加载事件与网络重发）。 */
    private void loadMapFromFile(com.phoenix.game.maps.Maps.Map map){
        try{
            Vars.maps.loadMap(map);
            afterWorldLoad(map.name);
        }catch(Exception e){
            System.err.println("载入地图失败 " + map.name + ": " + e);
        }
    }

    /** 世界替换后的公共收尾：状态、波次、寻路、出生点、网络重发。 */
    private void afterWorldLoad(String mapName){
        Vars.state.gameOver = false;
        Vars.state.set(GameState.State.playing);
        Vars.state.wave = 1;
        Vars.state.enemies = 0;
        Vars.state.rules.spawns = new DefaultWaves().get();
        Vars.state.wavetime = Vars.state.rules.waveSpacing * 2f;
        Build.refundMultiplier = Vars.state.rules.deconstructRefundMultiplier;

        Units.units.clear();
        Bullet.all.clear();
        Effects.clear();
        Time.clear();

        //世界加载事件：重建寻路 + 出生点
        Events.fire(new EventType.WorldLoadEvent());
        Vars.spawner.reset();

        //换图同样要把新世界重发给在线客户端（与 startGame 一致）
        if(Vars.netServer != null){
            Vars.netServer.resendWorldToAll();
        }

        System.out.println("服务器地图已切换：" + mapName + "（第 " + Vars.state.wave + " 波）");
    }

    /** 退出服务端。 */
    public void exit(){
        quitting = true;
        System.out.println("Phoenix 服务器已退出。");
        Gdx.app.exit();
    }

    // ---- 控制台 ----

    /** 标准输入读取循环（daemon 线程）。读到一行就解析并执行一条命令。 */
    private void consoleLoop(){
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
        String line;
        try{
            while(!quitting && (line = reader.readLine()) != null){
                if(line.trim().isEmpty()) continue;
                handleCommand(line.trim());
            }
        }catch(Exception e){
            if(!quitting){
                System.err.println("控制台读取异常: " + e);
            }
        }
    }

    /** 解析并执行一条控制台命令。阶段 1 只实现基础命令，命令表后续阶段扩充。 */
    private void handleCommand(String line){
        String[] parts = line.split("\\s+");
        String cmd = parts[0].toLowerCase();
        try{
            switch(cmd){
                case "help": {
                    System.out.println("可用命令：");
                    System.out.println("  help                显示帮助");
                    System.out.println("  status              显示服务器与游戏状态");
                    System.out.println("  version             显示版本");
                    System.out.println("  runwave             立即开一波");
                    System.out.println("  say <msg>           广播一条消息");
                    System.out.println("  host <port>         开启/更换监听端口");
                    System.out.println("  stopserver          关闭服务器监听");
                    System.out.println("  maps                列出可用地图");
                    System.out.println("  exportmap           把当前世界导出成一张地图");
                    System.out.println("  save               把当前局存到新槽位");
                    System.out.println("  saves              列出存档槽位");
                    System.out.println("  loadsave <index>   从存档槽位读档");
                    System.out.println("  delete <index>     删除存档槽位");
                    System.out.println("  kick <name>        踢出在线玩家");
                    System.out.println("  players            列出在线玩家");
                    System.out.println("  ban <name>         封禁玩家（按 uuid）");
                    System.out.println("  unban <name/uuid>  解封玩家");
                    System.out.println("  bans               列出封禁玩家");
                    System.out.println("  admin <add/remove> <name>  设置管理员");
                    System.out.println("  admins             列出管理员");
                    System.out.println("  whitelist          开启/关闭白名单，或查看状态");
                    System.out.println("  whitelist-add <uuid>  白名单加入");
                    System.out.println("  playerlimit <n/off>   设置人数上限");
                    System.out.println("  load <mapname>      加载指定地图");
                    System.out.println("  nextmap             终局后自动切到下一张");
                    System.out.println("  exit / stop         退出服务器");
                    break;
                }
                case "version": {
                    System.out.println("Phoenix server 0.1.0 (Mindustry 126.2 移植)");
                    break;
                }
                case "status": {
                    System.out.println("状态：" + (Vars.state.isPlaying() ? "playing" : Vars.state.getState()));
                    System.out.println("地图：" + (Vars.world != null ? Vars.world.width + "x" + Vars.world.height : "无"));
                    System.out.println("波次：" + Vars.state.wave + "，场上敌人：" + Vars.state.enemies);
                    System.out.println("单位：" + Units.units.size + "，子弹：" + Bullet.all.size);
                    System.out.println("瓦片实体数：" + countTileEntities());
                    break;
                }
                case "runwave": {
                    if(Vars.state.isPlaying()){
                        Logic.runWave();
                        System.out.println("已开一波，当前第 " + Vars.state.wave + " 波");
                    }else{
                        System.out.println("当前不在游戏中，无法开波。");
                    }
                    break;
                }
                case "say": {
                    String msg = line.substring("say".length()).trim();
                    if(Vars.netServer != null){
                        Vars.netServer.broadcastChat("[服务器]", msg);
                    }else{
                        System.out.println("[服务器] " + msg);
                    }
                    break;
                }
                case "exit":
                case "stop": {
                    if(Vars.netServer != null) Vars.netServer.closeServer();
                    exit();
                    break;
                }
                case "stopserver": {
                    if(Vars.netServer != null){
                        Vars.netServer.closeServer();
                        System.out.println("服务器已停止监听。");
                    }else{
                        System.out.println("服务器未在运行。");
                    }
                    break;
                }
                case "host": {
                    int p = parts.length > 1 ? Integer.parseInt(parts[1]) : Vars.port;
                    if(Vars.netServer != null){
                        Vars.netServer.closeServer();
                        Vars.netServer.openServer(p);
                    }
                    break;
                }
                case "maps": {
                    if(Vars.maps.all().size == 0){
                        System.out.println("无可用地图。输入 exportmap 导出当前世界为地图。");
                        break;
                    }
                    System.out.println("可用地图（" + Vars.maps.all().size + "）：");
                    for(com.phoenix.game.maps.Maps.Map m : Vars.maps.all()){
                        System.out.println("  " + m.name);
                    }
                    break;
                }
                case "exportmap": {
                    try{
                        java.io.File f = Vars.maps.nextMapFile();
                        com.phoenix.game.maps.Maps.Map m = Vars.maps.exportCurrent(f);
                        System.out.println("已导出地图 " + m.name + " -> " + f);
                    }catch(Exception e){
                        System.err.println("导出地图失败: " + e);
                    }
                    break;
                }
                case "save": {
                    try{
                        com.phoenix.game.game.Saves.SaveSlot slot = Vars.saves.addSave(null);
                        System.out.println("已存档 -> " + slot.file);
                    }catch(Exception e){
                        System.err.println("存档失败: " + e);
                    }
                    break;
                }
                case "saves": {
                    if(Vars.saves.getSlots().size == 0){
                        System.out.println("无存档槽位。输入 save 新建。");
                        break;
                    }
                    System.out.println("存档槽位（" + Vars.saves.getSlots().size + "）：");
                    for(int i = 0; i < Vars.saves.getSlots().size; i++){
                        com.phoenix.game.game.Saves.SaveSlot s = Vars.saves.getSlots().get(i);
                        System.out.println("  " + i + "  " + s.file.getName() + "  " + s.toString());
                    }
                    break;
                }
                case "loadsave": {
                    if(parts.length < 2){
                        System.out.println("用法: loadsave <index>");
                        break;
                    }
                    int idx = Integer.parseInt(parts[1]);
                    com.badlogic.gdx.utils.Array<com.phoenix.game.game.Saves.SaveSlot> slots = Vars.saves.getSlots();
                    if(idx < 0 || idx >= slots.size){
                        System.out.println("槽位越界: " + idx);
                        break;
                    }
                    try{
                        slots.get(idx).load();
                        afterWorldLoad(slots.get(idx).file.getName());
                    }catch(Exception e){
                        System.err.println("读档失败: " + e);
                        e.printStackTrace();
                    }
                    break;
                }
                case "delete": {
                    if(parts.length < 2){
                        System.out.println("用法: delete <index>");
                        break;
                    }
                    int idx = Integer.parseInt(parts[1]);
                    com.badlogic.gdx.utils.Array<com.phoenix.game.game.Saves.SaveSlot> slots = Vars.saves.getSlots();
                    if(idx < 0 || idx >= slots.size){
                        System.out.println("槽位越界: " + idx);
                        break;
                    }
                    com.phoenix.game.game.Saves.SaveSlot s = slots.get(idx);
                    Vars.saves.delete(s);
                    System.out.println("已删除槽位 " + idx + " (" + s.file.getName() + ")");
                    break;
                }
                case "load": {
                    if(parts.length < 2){
                        System.out.println("用法: load <mapname>");
                        break;
                    }
                    com.phoenix.game.maps.Maps.Map m = Vars.maps.byName(parts[1]);
                    if(m == null){
                        System.out.println("未找到地图: " + parts[1] + "（先 exportmap 或用 maps 查看）");
                        break;
                    }
                    loadMapFromFile(m);
                    break;
                }
                case "nextmap": {
                    rotateMap();
                    break;
                }
                case "kick": {
                    if(parts.length < 2){
                        System.out.println("用法: kick <name>");
                        break;
                    }
                    com.phoenix.game.core.NetServer.RemotePlayer rp = findOnlinePlayer(parts[1]);
                    if(rp == null){
                        System.out.println("未找到在线玩家: " + parts[1]);
                        break;
                    }
                    Vars.netServer.kick(rp.con, "踢出");
                    System.out.println("已踢出 " + rp.player.name);
                    break;
                }
                case "players": {
                    if(Vars.netServer.players.size == 0){
                        System.out.println("当前无在线玩家");
                        break;
                    }
                    System.out.println("在线玩家（" + Vars.netServer.players.size + "）：");
                    for(com.phoenix.game.core.NetServer.RemotePlayer rp : Vars.netServer.players){
                        System.out.println("  " + rp.player.name + "  ip=" + rp.con.address);
                    }
                    break;
                }
                case "ban": {
                    if(parts.length < 2){
                        System.out.println("用法: ban <name>");
                        break;
                    }
                    com.phoenix.game.core.NetServer.RemotePlayer rp = findOnlinePlayer(parts[1]);
                    if(rp == null){
                        System.out.println("未找到在线玩家: " + parts[1] + "（在线才可封禁）");
                        break;
                    }
                    Vars.netServer.admins.ban(rp.info.uuid, true);
                    Vars.netServer.kick(rp.con, "被封禁");
                    System.out.println("已封禁 " + rp.player.name);
                    break;
                }
                case "unban": {
                    if(parts.length < 2){
                        System.out.println("用法: unban <name/uuid>");
                        break;
                    }
                    com.phoenix.game.net.Administration adm = Vars.netServer.admins;
                    //先按档案名匹配
                    com.phoenix.game.net.Administration.PlayerInfo info = adm.findByName(parts[1]);
                    if(info != null){
                        adm.ban(info.id, false);
                        System.out.println("已解封 " + parts[1] + " (" + info.id + ")");
                    }else{
                        adm.ban(parts[1], false);
                        System.out.println("已解封 " + parts[1]);
                    }
                    break;
                }
                case "bans": {
                    com.badlogic.gdx.utils.Array<com.phoenix.game.net.Administration.PlayerInfo> banned = Vars.netServer.admins.getBanned();
                    if(banned.size == 0){
                        System.out.println("无封禁玩家");
                        break;
                    }
                    System.out.println("封禁玩家（" + banned.size + "）：");
                    for(com.phoenix.game.net.Administration.PlayerInfo info : banned){
                        System.out.println("  " + info.lastName + "  " + info.id + "  踢" + info.timesKicked + "次");
                    }
                    break;
                }
                case "admin": {
                    if(parts.length < 3){
                        System.out.println("用法: admin <add/remove> <name>");
                        break;
                    }
                    com.phoenix.game.core.NetServer.RemotePlayer rp = findOnlinePlayer(parts[2]);
                    if(rp == null){
                        System.out.println("未找到在线玩家: " + parts[2]);
                        break;
                    }
                    boolean add = parts[1].equalsIgnoreCase("add");
                    Vars.netServer.admins.setAdmin(rp.info.uuid, add);
                    System.out.println("已" + (add ? "设置" : "撤销") + "管理员 " + rp.player.name);
                    break;
                }
                case "admins": {
                    com.badlogic.gdx.utils.Array<com.phoenix.game.net.Administration.PlayerInfo> admins = Vars.netServer.admins.getAdmins();
                    if(admins.size == 0){
                        System.out.println("无管理员");
                        break;
                    }
                    System.out.println("管理员：");
                    for(com.phoenix.game.net.Administration.PlayerInfo info : admins){
                        System.out.println("  " + info.lastName + "  " + info.id);
                    }
                    break;
                }
                case "whitelist": {
                    com.phoenix.game.net.Administration adm = Vars.netServer.admins;
                    if(parts.length < 2){
                        System.out.println("白名单状态: " + (adm.whitelistEnabled ? "开启" : "关闭") + "，数量 " + adm.whitelist.size);
                        break;
                    }
                    String arg = parts[1];
                    if(arg.equalsIgnoreCase("on")){
                        adm.setWhitelistEnabled(true);
                        System.out.println("已开启白名单");
                    }else if(arg.equalsIgnoreCase("off")){
                        adm.setWhitelistEnabled(false);
                        System.out.println("已关闭白名单");
                    }else if(arg.equalsIgnoreCase("add") && parts.length >= 3){
                        adm.whitelist(parts[2]);
                        System.out.println("已加入白名单: " + parts[2]);
                    }else{
                        System.out.println("用法: whitelist [on/off/add <uuid>]");
                    }
                    break;
                }
                case "playerlimit": {
                    com.phoenix.game.net.Administration adm = Vars.netServer.admins;
                    if(parts.length < 2){
                        System.out.println("当前人数上限: " + (adm.playerLimit <= 0 ? "不限" : adm.playerLimit));
                        break;
                    }
                    if(parts[1].equalsIgnoreCase("off")){
                        adm.playerLimit = 0;
                    }else{
                        adm.playerLimit = Math.max(0, Integer.parseInt(parts[1]));
                    }
                    System.out.println("人数上限已设为: " + (adm.playerLimit <= 0 ? "不限" : adm.playerLimit));
                    break;
                }
                default: {
                    //插件命令
                    com.phoenix.game.mod.CommandHandler.Command pc = pluginCommands.get(cmd);
                    if(pc != null){
                        String[] args = java.util.Arrays.copyOfRange(parts, 1, parts.length);
                        pc.runner.run(args, msg -> System.out.println(msg));
                    }else{
                        System.out.println("未知命令: " + cmd + "（输入 help 查看帮助）");
                    }
                }
            }
        }catch(Exception e){
            System.err.println("执行命令 " + cmd + " 出错: " + e);
        }
    }

    /** 统计带实体的瓦片数（status 用）。 */
    private int countTileEntities(){        if(Vars.world == null || Vars.world.tiles == null) return 0;
        int count = 0;
        for(Tile tile : Vars.world.tiles){
            if(tile != null && tile.entity != null) count++;
        }
        return count;
    }

    /** 按名字查找在线玩家。 */
    private com.phoenix.game.core.NetServer.RemotePlayer findOnlinePlayer(String name){
        if(Vars.netServer == null) return null;
        for(com.phoenix.game.core.NetServer.RemotePlayer rp : Vars.netServer.players){
            if(name.equals(rp.player.name)) return rp;
        }
        return null;
    }
}