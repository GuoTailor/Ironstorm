package com.phoenix.game.core;

import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.content.UnitTypes;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.game.Team;
import com.phoenix.game.net.Administration;
import com.phoenix.game.net.Net;
import com.phoenix.game.net.NetConnection;
import com.phoenix.game.net.NetworkIO;
import com.phoenix.game.net.Packets;
import com.phoenix.game.net.PhoenixNetProvider;
import com.phoenix.game.world.Tile;

import java.io.ByteArrayInputStream;

/**
 * 多人服务器。对应原版 Mindustry 的 mindustry.core.NetServer（最小版，适配 phoenix 模型）。
 * <p>服务端为权威仿真：客户端只上报单位输入（{@link Packets.PlayerInput}），服务端据此驱动单位移动，
 * 并周期性把所有单位状态（{@link Packets.EntitySnapshot}，含波次敌人）广播给所有客户端。
 * <p>职责：openServer 监听、握手（ConnectPacket）校验、世界数据下发、玩家单位生成/同步/移除、
 * 聊天广播、踢出。入站包在 {@link #update()} 每帧 drain，保证游戏状态单线程修改。
 * <p>注：RPC 不用原版 @Remote 注解处理器（其生成代码落在 io.anuke 包名，规避侵权风险），改用 phoenix 原生包。
 */
public class NetServer {
    /** 单位状态同步间隔（tick）。 */
    public static final int syncInterval = 12;
    /** 同步计时器。 */
    private final Interval timer = new Interval();
    /** 玩家管理。 */
    public final Administration admins = new Administration();
    /** 传输层。 */
    public final PhoenixNetProvider provider = new PhoenixNetProvider();
    /** 网络门面。 */
    public final Net net = new Net(provider);
    /** 已连接玩家（con + player + unit）。 */
    public final Array<RemotePlayer> players = new Array<>();
    /** 监听端口。 */
    public int port = Vars.port;

    /** LAN 发现响应器。 */
    private com.phoenix.game.net.Discovery.Responder discovery;
    /** 上一帧同步过的单位 id（用于差集检测消失并通知客户端移除）。 */
    private final com.badlogic.gdx.utils.IntSet syncedUnits = new com.badlogic.gdx.utils.IntSet();
    /** 服务器展示名（LAN 发现与列表用）。 */
    public String serverName = "Phoenix Server";

    private boolean hosting;

    public void init(){
        setupHandlers();
        registerCommands();
    }

    /** 在指定端口开启服务器。 */
    public void openServer(int port){
        this.port = port;
        try{
            net.host(port);
            hosting = true;
            System.out.println("服务器已监听端口 " + port);
            startDiscovery(port);
        }catch(Exception e){
            System.err.println("开启服务器失败(端口 " + port + "): " + e);
            hosting = false;
        }
    }

    /** 启动 LAN 发现响应（供同网段客户端自动发现）。 */
    private void startDiscovery(int port){
        if(discovery != null) discovery.stop();
        discovery = new com.phoenix.game.net.Discovery.Responder();
        discovery.serverName = serverName;
        discovery.gamePort = port;
        //状态提供者：人数 + 上限 + 当前地图名
        discovery.statusSupplier = () -> players.size + "\t" + admins.playerLimit + "\t" + currentMapName();
        discovery.start();
    }

    /** @return 当前地图显示名（无世界返回 "-"）。 */
    private String currentMapName(){
        return Vars.world == null ? "-" : (Vars.world.width + "x" + Vars.world.height);
    }

    /** 每帧调用：drain 入站包 + 周期性同步单位状态。 */
    public void update(){
        if(!hosting) return;

        //drain 网络线程投递的入站包
        while(provider.hasInbound()){
            try{
                PhoenixNetProvider.InboundFrame f = provider.pollInbound();
                net.handleServerReceived(f.connection, f.packet);
            }catch(Exception e){
                System.out.println("NetServer 处理入站包出错: " + e);
            }
        }

        sync();
    }

    /**
     * 周期性把游戏状态广播给所有人：单位快照 + 波次状态。
     * <p>子弹不在这里同步 —— 改由 {@code Bullet.create} 在服务端生成时广播一次生成事件，
     * 客户端本地仿真（对应原版 createBullet 的 @Remote 模型）。
     */
    private void sync(){
        if(!timer.get(syncInterval)) return;
        if(players.size == 0) return;

        //在建/待拆方块：进度是逐帧变化的量，只在这个周期同步里发（不必每帧刷包）。
        //客机据此画出"在建"方块与进度，不需要自己跑建造队列（避免双端分叉）。
        broadcastBuildingBlocks();

        //单位快照：遍历**所有单位**（含波次敌人，不只玩家），按字节预算分片批量发送。
        //客户端据此创建/更新代理单位——这也是敌人能被看见、子弹不再"凭空出现"的前提。
        com.badlogic.gdx.utils.IntSet frameUnits = new com.badlogic.gdx.utils.IntSet();
        Packets.EntitySnapshot snap = new Packets.EntitySnapshot();
        int packed = 0;

        for(int i = 0; i < Units.units.size; i++){
            BaseUnit u = Units.units.get(i);
            if(u == null || u.isDead()) continue;

            int typeId = UnitTypes.all.indexOf(u.getType(), true);
            if(typeId < 0) continue;

            frameUnits.add(u.getID());
            snap.add(u.getID(), (byte)typeId, u.getTeam().id, u.x, u.y, u.rotation, u.health());
            packed++;

            if(packed >= Packets.EntitySnapshot.MAX_ENTRIES){
                net.send(snap, Net.SendMode.udp);
                snap = new Packets.EntitySnapshot();
                packed = 0;
            }
        }
        if(packed > 0) net.send(snap, Net.SendMode.udp);

        //移除：上一帧同步过、本帧不在集合里的（阵亡/被清掉）→ 通知客户端删除。
        //走 TCP：移除是一次性语义，UDP 丢一个包客户端就永久残留一个幽灵单位。
        if(syncedUnits.size > 0){
            com.badlogic.gdx.utils.IntSet.IntSetIterator it = syncedUnits.iterator();
            com.badlogic.gdx.utils.IntArray removed = new com.badlogic.gdx.utils.IntArray();
            while(it.hasNext){
                int id = it.next();
                if(!frameUnits.contains(id)){
                    net.send(new Packets.EntityRemove(id), Net.SendMode.tcp);
                    removed.add(id);
                }
            }
            for(int i = 0; i < removed.size; i++){
                syncedUnits.remove(removed.get(i));
            }
        }

        //本帧集合成为新的已同步集合
        syncedUnits.clear();
        com.badlogic.gdx.utils.IntSet.IntSetIterator fit = frameUnits.iterator();
        while(fit.hasNext){
            syncedUnits.add(fit.next());
        }

        //波次状态
        Packets.WorldState ws = new Packets.WorldState();
        ws.wave = Vars.state.wave;
        ws.enemies = Vars.state.enemies;
        ws.wavetime = Vars.state.wavetime;
        net.send(ws, Net.SendMode.udp);
    }

    /** 踢出某个连接。 */
    public void kick(NetConnection con, String reason){
        if(con == null) return;
        Packets.Kick k = new Packets.Kick();
        k.reason = reason;
        con.send(k, Net.SendMode.tcp);
        Time.run(2f, con::close);
    }

    /** 踢出全部连接。 */
    public void kickAll(String reason){
        for(int i = players.size - 1; i >= 0; i--){
            RemotePlayer rp = players.get(i);
            kick(rp.con, reason);
            onDisconnect(rp.con);
        }
    }

    /** 关闭服务器。 */
    public void closeServer(){
        if(hosting){
            net.closeServer();
            hosting = false;
        }
        if(discovery != null){
            discovery.stop();
            discovery = null;
        }
    }

    /** 是否正在托管。 */
    public boolean isHosting(){
        return hosting;
    }

    /**
     * @return 服务器是否正在等待玩家（对应原版 {@code NetServer.isWaitingForPlayers}）。
     * <p>语义照原版：仅 PvP 局判断——"有玩家占用的队伍数 &lt; 2"即视为等待；非 PvP 恒返回 false。
     * <p>⚠ 偏离：原版遍历 {@code state.teams.getActive()}（活跃队伍）再按队伍统计玩家数；
     * 本项目 {@code Teams} 没有 {@code getActive()} → 改为遍历 {@link Vars#playerGroup} 收集各玩家队伍去重计数。
     */
    public boolean isWaitingForPlayers(){
        if(Vars.state == null || Vars.state.rules == null || !Vars.state.rules.pvp) return false;
        if(Vars.playerGroup == null) return false;

        com.badlogic.gdx.utils.IntSet used = new com.badlogic.gdx.utils.IntSet();
        com.badlogic.gdx.utils.Array<Player> all = Vars.playerGroup.all();
        for(int i = 0; i < all.size; i++){
            Player p = all.get(i);
            if(p != null) used.add(p.getTeam().id);
        }
        return used.size < 2;
    }

    // ---- 包处理 ----

    private void setupHandlers(){
        net.handleServer(Packets.Connect.class, (con, p) -> {
            //连接建立（内部生命周期包，provider 已投递；无操作）
        });

        net.handleServer(Packets.Disconnect.class, (con, p) -> onDisconnect(con));

        net.handleServer(Packets.ConnectPacket.class, (con, p) -> onConnect(con, p));

        net.handleServer(Packets.PlayerInput.class, (con, p) -> {
            RemotePlayer rp = findByConnection(con);
            if(rp == null || rp.unit == null) return;
            //服务端权威移动：按客户端方向*最大速度设置速度
            rp.unit.velocity().set(p.vx * rp.unit.getType().maxVelocity, p.vy * rp.unit.getType().maxVelocity);
            rp.unit.rotation = p.rotation;

            //服务端权威开火：客户端只上报瞄准点，子弹由服务器生成并同步
            rp.player.isShooting = p.shooting;
            if(p.shooting){
                rp.aimX = p.aimX;
                rp.aimY = p.aimY;
                com.phoenix.game.type.Weapon weapon = rp.unit.getWeapon();
                //F12c：canShoot 钩子（trident-ship 速度门槛等）—— 必须与 InputHandler.updateShooting 同判定（双端逻辑一致）
                if(weapon != null && rp.unit.getType().canShoot(rp.unit)){
                    weapon.update(rp.unit, p.aimX, p.aimY);
                }
            }
        });

        net.handleServer(Packets.ChatMessage.class, (con, p) -> onChat(con, p.message));

        //心跳：原样回 Pong（客户端据此算 RTT）
        net.handleServer(Packets.Ping.class, (con, p) -> {
            Packets.Pong pong = new Packets.Pong();
            pong.time = p.time;
            con.send(pong, Net.SendMode.tcp);
        });

        net.handleServer(Packets.BuildRequest.class, (con, p) -> {
            RemotePlayer rp = findByConnection(con);
            if(rp == null || Vars.world == null) return;

            com.phoenix.game.world.Tile tile = Vars.world.tile(p.x, p.y);
            if(tile == null) return;
            com.phoenix.game.world.Block block = blockById(p.blockId);
            if(block == null) return;

            //交给该玩家的单位去建造：与单机**同一套队列逻辑**（先铺占位方块，再逐帧推进度）。
            //进度由服务端权威推进，客机只从周期广播的 BlockState 里看到"在建"状态。
            if(rp.unit != null && !rp.unit.isDead()){
                rp.unit.addBuildRequest(new com.phoenix.game.world.BuildRequest(p.x, p.y, p.rotation & 3, block));
            }
        });

        net.handleServer(Packets.DeconstructRequest.class, (con, p) -> {
            RemotePlayer rp = findByConnection(con);
            if(rp == null || Vars.world == null) return;

            com.phoenix.game.world.Tile tile = Vars.world.tile(p.x, p.y);
            if(tile == null) return;

            //拆除同样走队列（先铺占位方块，进度退到 0 才真正移除）
            if(rp.unit != null && !rp.unit.isDead()){
                rp.unit.addBuildRequest(new com.phoenix.game.world.BuildRequest(p.x, p.y));
            }
        });

        //RTS 命令：校验队伍归属后权威应用，并转发给其他客户端做表现（对应原版 @Remote forward = true）
        net.handleServer(Packets.UnitCommandPacket.class, (con, p) -> {
            RemotePlayer rp = findByConnection(con);
            if(rp == null || Vars.world == null || p.unitIds.length == 0) return;

            BaseUnit unitTarget = p.targetUnitId >= 0 ? com.phoenix.game.input.InputHandler.findUnitById(p.targetUnitId) : null;

            if(p.type == Packets.TYPE_ORDERS){
                com.phoenix.game.input.InputHandler.applyCommand(rp.player.getTeam(), p.unitIds, unitTarget, p.x, p.y, p.queue);
            }else if(p.type == Packets.TYPE_COMMAND && p.commandId >= 0 && p.commandId < com.phoenix.game.ai.UnitCommand.all.size){
                com.phoenix.game.input.InputHandler.applySetCommand(rp.player.getTeam(), p.unitIds,
                    com.phoenix.game.ai.UnitCommand.all.get(p.commandId));
            }else if(p.type == Packets.TYPE_STANCE && p.commandId >= 0 && p.commandId < com.phoenix.game.ai.UnitStance.all.size){
                com.phoenix.game.input.InputHandler.applySetStance(rp.player.getTeam(), p.unitIds,
                    com.phoenix.game.ai.UnitStance.all.get(p.commandId));
            }else{
                return;
            }

            //转发给除发送者外的所有客户端（表现层：画线/特效）
            Vars.net.sendExcept(con, p, Net.SendMode.tcp);
        });

        //F13f：客户端管理员操作请求（对应原版 Call.onAdminRequest）
        net.handleServer(Packets.AdminRequest.class, (con, p) -> onAdminRequest(con, p));
    }

    // ---- 服务端聊天命令（对应原版 NetServer.registerCommands + NetClient.sendChatMessage 的命令分支）----

    /** 服务端命令描述（仅用于 /help 列表；对应原版 arc CommandHandler 的命令表）。 */
    private static class ServerCommand{
        final String name, params, description;

        ServerCommand(String name, String params, String description){
            this.name = name;
            this.params = params;
            this.description = description;
        }

        /** @return 命令使用说明（含参数），对应原版 {@code Command.text}。 */
        String text(){
            return (params == null || params.isEmpty()) ? name : name + " " + params;
        }
    }

    /** 已注册的服务端命令（/help 列表按注册顺序展示）。 */
    private final com.badlogic.gdx.utils.Array<ServerCommand> commandList = new com.badlogic.gdx.utils.Array<>();

    /**
     * 注册服务端聊天命令（对应原版 {@code NetServer.registerCommands}，原版 core/NetServer.java:272-457）。
     * <p>⚠ 刻意偏离：原版用 arc 的 {@code CommandHandler}（带 ResponseType：noCommand / unknownCommand /
     * fewArguments / manyArguments，回调签名 {@code (args, Player)}）。本项目虽已有 {@code mod.CommandHandler}，
     * 但它的回调只有 {@code (args, Consumer<String> responder)}，**不带发送者** → 无法实现 /t（要发给全队）
     * 与 /sync（要把世界重发给发起者）→ 这里只登记命令描述，实际分发在 {@link #handleCommand}。
     * <p>{@code /vote} 与 {@code /votekick} 依赖未移植的投票系统（原版 VoteSession + Timer +
     * Config.enableVotekick）→ <b>保留为未实现</b>，不注册、不硬造。
     */
    private void registerCommands(){
        commandList.add(new ServerCommand("help", "[page]", "Lists all commands."));
        commandList.add(new ServerCommand("t", "<message...>", "Send a message only to your teammates."));
        commandList.add(new ServerCommand("sync", "", "Re-synchronize world state."));
    }

    /**
     * 处理一条以 "/" 开头的服务端聊天命令（对应原版 {@code NetClient.sendChatMessage:167-206} 的命令分支 +
     * {@code NetServer.registerCommands}）。只覆盖已移植的 /help、/t、/sync；其余一律回"未知命令"。
     * @param rp 发起者
     * @param message 原始消息（含前导 "/"）
     */
    private void handleCommand(RemotePlayer rp, String message){
        if(rp == null) return;
        String line = message.startsWith("/") ? message.substring(1) : message;
        String[] parts = line.trim().split("\\s+");
        if(parts.length == 0 || parts[0].isEmpty()){
            sendMessage(rp, "[scarlet]Unknown command. Check [lightgray]/help[scarlet].");
            return;
        }

        String cmd = parts[0].toLowerCase();
        String[] args = java.util.Arrays.copyOfRange(parts, 1, parts.length);
        Player player = rp.player;

        if(cmd.equals("help")){
            //分页列出命令（对应原版 help 命令，commandsPerPage = 6）
            if(args.length > 0 && !isInt(args[0])){
                sendMessage(rp, "[scarlet]'page' must be a number.");
                return;
            }
            int commandsPerPage = 6;
            int page = args.length > 0 ? Integer.parseInt(args[0]) : 1;
            int pages = (int)Math.ceil((float)commandList.size / commandsPerPage);
            page--;
            if(page >= pages || page < 0){
                sendMessage(rp, "[scarlet]'page' must be a number between[orange] 1[] and[orange] " + pages + "[scarlet].");
                return;
            }
            StringBuilder result = new StringBuilder();
            result.append(Strings.format("[orange]-- Commands Page[lightgray] {0}[gray]/[lightgray]{1}[orange] --\n\n", page + 1, pages));
            for(int i = commandsPerPage * page; i < Math.min(commandsPerPage * (page + 1), commandList.size); i++){
                ServerCommand command = commandList.get(i);
                result.append("[orange] /").append(command.text()).append("[lightgray] - ").append(command.description).append("\n");
            }
            sendMessage(rp, result.toString());
        }else if(cmd.equals("t")){
            //队伍频道（对应原版 "t" 命令）：只发给同队玩家。
            //⚠ 偏离：原版显示名上色（"[#" + team.color + "]<T>" + colorizeName(...)）；本项目聊天包只带纯名字
            //  字符串 → 保留 "<T>" 前缀，去掉颜色标记（客户端不一定认得任意 hex 色标）。
            if(args.length == 0){
                sendMessage(rp, "[scarlet]Usage:[lightgray] /t <message...>");
                return;
            }
            String text = joinArgs(args);
            String display = "<T>" + player.name;
            for(int i = 0; i < players.size; i++){
                RemotePlayer other = players.get(i);
                if(other.player.getTeam() == player.getTeam()){
                    sendMessage(other, display, text);
                }
            }
        }else if(cmd.equals("sync")){
            //重发世界（对应原版 "sync" 命令）。
            //⚠ 偏离：原版有 5 秒冷却（player.getInfo().lastSyncTime）；本项目 Administration.PlayerInfo 无该字段 → 略去。
            if(player.isLocal){
                sendMessage(rp, "[scarlet]Re-synchronizing as the host is pointless.");
            }else{
                sendWorldData(rp.con);
                sendMessage(rp, "[lightgray]World data re-sent.");
            }
        }else{
            sendMessage(rp, "[scarlet]Unknown command. Check [lightgray]/help[scarlet].");
        }
    }

    /** 给单个玩家发一条系统消息（对应原版 {@code Player.sendMessage} → 单播）。 */
    private void sendMessage(RemotePlayer rp, String message){
        sendMessage(rp, "[服务器]", message);
    }

    /** 给单个玩家发一条聊天包（{@code name} 为显示名，用于队伍频道等自定义前缀）。 */
    private void sendMessage(RemotePlayer rp, String name, String message){
        if(rp == null) return;
        Packets.ChatMessage cm = new Packets.ChatMessage();
        cm.name = name;
        cm.message = message;
        rp.con.send(cm, Net.SendMode.tcp);
        System.out.println("[" + name + "] " + message);
    }

    /** @return 字符串能否解析为 int（对应原版 {@code Strings.canParseInt}）。 */
    private static boolean isInt(String s){
        try{
            Integer.parseInt(s);
            return true;
        }catch(NumberFormatException e){
            return false;
        }
    }

    /** 用空格把参数数组拼回一条消息（对应原版 {@code "<message...>"} 单参数捕获）。 */
    private static String joinArgs(String[] args){
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < args.length; i++){
            if(i > 0) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    /**
     * 玩家名清洗（对应原版 {@code NetServer.fixName}，原版 core/NetServer.java:792-814）：
     * 去首尾空白 → 把形如 {@code [xxx]} 的非法颜色标记剥掉 → 按字节数截断到 {@link Vars#maxNameLength}。
     */
    String fixName(String name){
        name = name.trim();
        if(name.equals("[") || name.equals("]")){
            return "";
        }

        for(int i = 0; i < name.length(); i++){
            if(name.charAt(i) == '[' && i != name.length() - 1 && name.charAt(i + 1) != '[' && (i == 0 || name.charAt(i - 1) != '[')){
                String prev = name.substring(0, i);
                String next = name.substring(i);
                String result = checkColor(next);

                name = prev + result;
            }
        }

        StringBuilder result = new StringBuilder();
        int curChar = 0;
        while(curChar < name.length() && result.toString().getBytes().length < Vars.maxNameLength){
            result.append(name.charAt(curChar++));
        }
        return result.toString();
    }

    /**
     * 校验并剥掉一个颜色标记（对应原版 {@code NetServer.checkColor}，原版 core/NetServer.java:816-840）：
     * 若 {@code [xxx]} 里的 xxx 是已知颜色名（且不透明度 &le; 0.8）或合法 hex 色 → 返回去掉该标记后的串；
     * 否则原样返回（让非法标记当字面量显示）。
     * <p>⚠ 等价简化：原版分别查 {@code Colors.get(大写)} 与 {@code Colors.get(小写)} 两次，本项目
     * {@code graphics.Colors.get} 的 key() 内部已 toLowerCase（大小写不敏感）→ 查一次即可，行为一致。
     */
    String checkColor(String str){
        for(int i = 1; i < str.length(); i++){
            if(str.charAt(i) == ']'){
                String color = str.substring(1, i);

                com.badlogic.gdx.graphics.Color result = com.phoenix.game.graphics.Colors.get(color);
                if(result != null){
                    if(result.a <= 0.8f){
                        return str.substring(i + 1);
                    }
                }else{
                    try{
                        com.badlogic.gdx.graphics.Color parsed = com.badlogic.gdx.graphics.Color.valueOf(color);
                        if(parsed.a <= 0.8f){
                            return str.substring(i + 1);
                        }
                    }catch(Exception e){
                        return str;
                    }
                }
            }
        }
        return str;
    }

    /**
     * 处理客户端的管理员操作请求（对应原版 {@code NetServer.onAdminRequest}）。
     * <p>鉴权照原版：① 发起者必须是管理员；② 目标存在；③ 不能操作管理员（除非发起者是房主）；
     * ④ 不能操作自己。动作：ban（封 IP + 踢）/ kick / trace（组 {@code TraceInfo} 回传给发起者）。
     */
    private void onAdminRequest(NetConnection con, Packets.AdminRequest p){
        RemotePlayer rp = findByConnection(con);
        if(rp == null) return;
        Player player = rp.player;

        if(!player.isAdmin){
            System.out.println("[服务器] 拒绝非管理员的管理请求（" + player.name + " / " + con.address + "）");
            return;
        }

        RemotePlayer other = findPlayerByUuid(p.uuid);
        if(other == null || (other.player.isAdmin && !player.isLocal) || other == rp){
            System.out.println("[服务器] 拒绝无效的管理对象（" + player.name + " -> " + p.uuid + "）");
            return;
        }

        Player o = other.player;
        if(p.action < 0 || p.action >= Packets.AdminAction.values().length) return;
        Packets.AdminAction action = Packets.AdminAction.values()[p.action];

        if(action == Packets.AdminAction.ban){
            admins.banIP(other.con.address);
            other.con.kick(Packets.KickReason.banned);
            System.out.println("[服务器] " + player.name + " 封禁了 " + o.name);
        }else if(action == Packets.AdminAction.kick){
            other.con.kick(Packets.KickReason.kick);
            System.out.println("[服务器] " + player.name + " 踢出了 " + o.name);
        }else if(action == Packets.AdminAction.trace){
            //对应原版 onAdminRequest 的 trace 分支：组 TraceInfo 回传给发起者
            //（原版走 Call.onTraceInfo(player.con, other, info)；本项目没有 RPC，改用自建响应包单播）
            Administration.TraceInfo info = new Administration.TraceInfo(
                other.con.address, other.player.uuid, other.con.modclient, other.con.mobile);
            Packets.TraceInfoPacket tp = new Packets.TraceInfoPacket();
            tp.playerUuid = other.player.uuid;
            tp.info = info;
            rp.con.send(tp, Net.SendMode.tcp);
            System.out.println("[服务器] " + player.name + " 请求追踪 " + o.name + "（ip=" + other.con.address + "）");
        }
    }

    /** 按 Blocks.all 下标取方块（与 SaveIO 一致）。 */
    private com.phoenix.game.world.Block blockById(int id){
        if(id < 0 || id >= com.phoenix.game.content.Blocks.all.size) return null;
        return com.phoenix.game.content.Blocks.all.get(id);
    }

    /** 周期广播全部"在建/待拆"方块的状态（建造队列的进度靠它同步给客机）。 */
    private void broadcastBuildingBlocks(){
        if(Vars.world == null || Vars.world.tiles == null) return;
        for(com.phoenix.game.world.Tile tile : Vars.world.tiles){
            if(tile == null) continue;
            //只看中心格：多格在建方块的卫星格是 BlockPart，不重复发
            if(tile.blockRaw() instanceof com.phoenix.game.world.blocks.BuildBlock){
                broadcastBlockState(tile);
            }
        }
    }

    /** 广播一格建筑的当前状态（含实体内部状态：配置/进度/物品……）。 */
    public void broadcastBlockState(com.phoenix.game.world.Tile tile){
        if(tile == null || tile.block() == null) return;
        Packets.BlockState st = new Packets.BlockState();
        st.x = tile.x;
        st.y = tile.y;
        st.blockId = com.phoenix.game.content.Blocks.all.indexOf(tile.block(), true);
        st.teamId = (byte)tile.getTeam().id;
        st.rotation = tile.rotation();
        st.health = tile.entity != null ? tile.entity.health() : tile.block().health;

        //实体内部状态：与存档共用 TileEntity.write（配置值、建造进度、传送带物品、炮塔角度…）
        if(tile.entity != null){
            try{
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                java.io.DataOutputStream dos = new java.io.DataOutputStream(bos);
                dos.writeByte(tile.entity.revision());
                tile.entity.write(dos);
                dos.flush();
                st.entityData = bos.toByteArray();
            }catch(java.io.IOException e){
                System.err.println("序列化建筑状态失败: " + e);
            }
        }

        net.send(st, Net.SendMode.tcp);
    }

    /** 广播一格建筑被移除（多格建筑只发中心格）。 */
    public void broadcastBlockRemove(com.phoenix.game.world.Tile tile){
        if(tile == null) return;
        Packets.BlockRemove rm = new Packets.BlockRemove();
        rm.x = tile.x;
        rm.y = tile.y;
        net.send(rm, Net.SendMode.tcp);
    }

    private void onConnect(NetConnection con, Packets.ConnectPacket p){
        //封禁 / 人数上限校验（对应原版 NetServer.java:109 的 isIPBanned || isSubnetBanned || isIDBanned）
        if(admins.isIPBanned(con.address) || admins.isSubnetBanned(con.address) || admins.isIDBanned(p.uuid)){
            kick(con, "banned");
            return;
        }
        if(admins.isServerFull(players.size)){
            kick(con, "server full");
            return;
        }

        //移除该连接旧玩家（重复加入）
        RemotePlayer old = findByConnection(con);
        if(old != null){
            removePlayer(old, true);
        }

        //更新档案
        admins.updatePlayerJoined(p.uuid, p.name, con.address);

        //玩家名清洗 + 空名拒绝（对应原版 NetServer.java:190-195 的 fixName + nameEmpty 校验）
        p.name = fixName(p.name);
        if(p.name.trim().isEmpty()){
            kick(con, "name empty");
            return;
        }

        //远程 Player（持单位引用，单位在 respawnPlayer 里生成）
        Player player = new Player();
        player.name = p.name;
        //F13b：填列表/权限相关字段（对应原版 Player 的这些字段；单机时不会有这条路径）
        player.uuid = p.uuid;
        player.usid = p.usid;
        player.con = con;
        player.isAdmin = admins.isAdmin(p.uuid);
        if(p.color != 0){
            //ConnectPacket.color 是 ARGB int（0 = 客户端未上报 → 保持默认白色）
            player.color.set(p.color);
        }
        player.add(); //登记进 playerGroup

        RemotePlayer rp = new RemotePlayer(con, p, player, null);
        players.add(rp);

        System.out.println("[服务器] " + p.name + " 加入（ip=" + con.address + "）");

        //生成单位 + 下发世界 + 告知单位分配
        respawnPlayer(rp);

        //广播加入提示
        broadcastChat("[服务器]", p.name + " 加入了游戏");

        //F13g：广播玩家列表快照（新玩家进来后所有人刷新列表）
        broadcastPlayerList();
    }

    /**
     * 生成该玩家的单位（出生在我方核心旁），并把**当前世界**与单位分配下发给他的客户端。
     * <p>加入时与地图重开时共用同一套流程 —— 地图重开后必须重发世界，否则客户端会停留在旧世界上。
     */
    public void respawnPlayer(RemotePlayer rp){
        //出生单位与单机 spawnPlayerUnit 同源：玩家自选类型（默认 dart-ship = 原版出生机甲
        //Mechs.starter，飞行 + 可采铜/铅）。此前写死 dagger（地面单位）→ 联机玩家出生即地面机甲，
        //在程序生成图上被水域挡住够不到矿，也无法像原版一样飞行。
        BaseUnit unit = (rp.player.selectedType != null ? rp.player.selectedType : UnitTypes.dartShip).create();
        unit.setTeam(Team.sharded);
        unit.isPlayer = true;

        Tile core = Vars.state.teams.closestCore(Vars.world.unitWidth() / 2f, Vars.world.unitHeight() / 2f, Team.sharded);
        float x = core != null ? core.getX() : Vars.world.unitWidth() * 0.5f;
        float y = core != null ? core.getY() + 32f : Vars.world.unitHeight() * 0.5f;
        unit.set(x, y);
        unit.health(unit.maxHealth());
        //与 spawnPlayerUnitAt 对齐：记下当前类型，后续死亡重生/容量计算都用它
        rp.player.selectedType = unit.getType();
        //注意：UnitTypes 的 create() 内部已 Units.add，不要再加一次

        rp.unit = unit;
        rp.player.unit(unit);

        //下发世界数据（分块流）
        sendWorldData(rp.con);

        //告知客户端其单位分配
        Packets.SpawnPlayer sp = new Packets.SpawnPlayer();
        sp.id = unit.getID();
        sp.typeId = UnitTypes.all.indexOf(unit.getType(), true);
        sp.teamId = (byte)unit.getTeam().id;
        sp.x = unit.x;
        sp.y = unit.y;
        sp.rotation = unit.rotation;
        rp.con.send(sp, Net.SendMode.tcp);
    }

    /**
     * 世界被整体替换（服务端终局重开 / 换图）后，把新世界重发给所有在线客户端并重建他们的单位。
     * <p>必须清空 {@code syncedUnits}：否则新世界的第一帧会被当成「上一帧同步过、本帧不在集合里」，
     * 把所有客户端单位误判成已移除。
     */
    public void resendWorldToAll(){
        syncedUnits.clear();
        if(players.size > 0){
            System.out.println("[服务器] 世界已重发，重建 " + players.size + " 名玩家的单位");
        }
        for(int i = 0; i < players.size; i++){
            respawnPlayer(players.get(i));
        }
    }

    /** 把整局世界压缩后作为 WorldStream 分块发给指定连接。 */
    private void sendWorldData(NetConnection con){
        try{
            Packets.WorldStream ws = new Packets.WorldStream();
            ws.stream = new ByteArrayInputStream(NetworkIO.writeWorldBytes());
            con.sendStream(ws);
        }catch(Exception e){
            System.err.println("下发世界数据失败: " + e);
        }
    }

    private void onChat(NetConnection con, String message){
        RemotePlayer rp = findByConnection(con);
        if(rp == null) return;

        //长度校验（对应原版 NetClient.sendChatMessage:161-163 的 maxTextLength 检查：超限直接丢弃、不广播）。
        //⚠ 偏离：原版抛 ValidateException（服务端只记一条 debug 日志）；本项目没有该异常链路 → 记日志后丢弃。
        if(message.length() > Vars.maxTextLength){
            System.out.println("[服务器] 丢弃超长聊天（" + rp.player.name + "，" + message.length() + " 字符）");
            return;
        }

        //命令优先分发（对应原版 sendChatMessage 里 netServer.clientCommands.handleMessage(message, player)）
        if(message.startsWith("/")){
            handleCommand(rp, message);
            return;
        }

        broadcastChat(rp.player.name, message);
    }

    /** 广播聊天消息（加[服务器]前缀的是系统消息）。 */
    public void broadcastChat(String name, String message){
        Packets.ChatMessage cm = new Packets.ChatMessage();
        cm.name = name;
        cm.message = message;
        net.send(cm, Net.SendMode.tcp);
        System.out.println("[" + name + "] " + message);
    }

    private void onDisconnect(NetConnection con){
        RemotePlayer rp = findByConnection(con);
        if(rp != null){
            removePlayer(rp, true);
        }
    }

    /** 移除玩家及其单位，并向其他客户端广播移除。 */
    private void removePlayer(RemotePlayer rp, boolean broadcast){
        players.removeValue(rp, true);
        //从 playerGroup 解绑（对应原版玩家断线时的 remove）
        if(rp.player != null) rp.player.remove();
        if(rp.unit != null){
            Packets.EntityRemove er = new Packets.EntityRemove(rp.unit.getID());
            //通知所有其他连接该单位已移除
            for(RemotePlayer other : players){
                if(other != rp){
                    other.con.send(er, Net.SendMode.tcp);
                }
            }
            rp.unit.remove();
            System.out.println("[服务器] " + rp.player.name + " 离开");
        }

        //F13g：广播玩家列表快照（有人离开后所有人刷新列表）
        broadcastPlayerList();
    }

    /**
     * 广播玩家列表整表快照（对应原版每个客户端的 Player 实体同步；本项目用快照包）。
     * <p>调用点：玩家加入（{@code onConnect}）/ 离开（{@code removePlayer}）/ 权限变更（{@code setPlayerAdmin}）。
     */
    public void broadcastPlayerList(){
        if(players.size == 0) return;

        Packets.PlayerListSync sync = new Packets.PlayerListSync();
        int total = players.size + (Vars.player != null ? 1 : 0);
        sync.uuids = new String[total];
        sync.names = new String[total];
        sync.usids = new String[total];
        sync.colors = new int[total];
        sync.admins = new boolean[total];
        sync.teams = new byte[total];

        int i = 0;
        //本机玩家（HOST 模式下的服务端玩家）排在最前
        if(Vars.player != null){
            sync.uuids[i] = Vars.player.uuid;
            sync.names[i] = Vars.player.name;
            sync.usids[i] = Vars.player.usid;
            sync.colors[i] = Vars.player.color.toIntBits();
            sync.admins[i] = Vars.player.isAdmin;
            sync.teams[i] = (byte)Vars.player.getTeam().id;
            i++;
        }
        for(RemotePlayer rp : players){
            Player pl = rp.player;
            sync.uuids[i] = pl.uuid;
            sync.names[i] = pl.name;
            sync.usids[i] = pl.usid;
            sync.colors[i] = pl.color.toIntBits();
            sync.admins[i] = pl.isAdmin;
            sync.teams[i] = (byte)pl.getTeam().id;
            i++;
        }

        for(RemotePlayer rp : players){
            rp.con.send(sync, Net.SendMode.tcp);
        }
    }

    /** 按 uuid 查找远程玩家（管理操作用）。 */
    public RemotePlayer findPlayerByUuid(String uuid){
        if(uuid == null) return null;
        for(RemotePlayer rp : players){
            if(uuid.equals(rp.player.uuid)) return rp;
        }
        return null;
    }

    /**
     * 设置某玩家的管理员权限（改档案 + 同步在场 Player 对象 + 广播列表）。
     * <p>对应原版 {@code Administration.adminPlayer/unAdminPlayer} 之上再补"通知客户端"那一步；
     * 本项目没有 RPC 层，服务端直接调用（客户端侧操作见候选 F13f）。
     */
    public void setPlayerAdmin(String uuid, boolean admin){
        if(uuid == null) return;
        admins.setAdmin(uuid, admin);
        //同步在场玩家对象
        if(Vars.player != null && uuid.equals(Vars.player.uuid)){
            Vars.player.isAdmin = admin;
        }
        RemotePlayer rp = findPlayerByUuid(uuid);
        if(rp != null){
            rp.player.isAdmin = admin;
        }
        //广播列表（客户端据此更新列表里的管理员标记/按钮）
        broadcastPlayerList();
    }

    /** 按连接查找玩家。 */
    public RemotePlayer findByConnection(NetConnection con){
        for(RemotePlayer rp : players){
            if(rp.con == con) return rp;
        }
        return null;
    }

    /** 远程玩家记录。 */
    public static class RemotePlayer {
        public final NetConnection con;
        public final Packets.ConnectPacket info;
        public final Player player;
        /** 当前单位。地图重开时会换新单位（旧单位随世界一起被清掉），所以不是 final。 */
        public BaseUnit unit;
        /** 最近一次上报的瞄准点（服务端开火用）。 */
        public float aimX, aimY;

        public RemotePlayer(NetConnection con, Packets.ConnectPacket info, Player player, BaseUnit unit){
            this.con = con;
            this.info = info;
            this.player = player;
            this.unit = unit;
        }
    }
}