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
                if(weapon != null){
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
            //服务端权威：校验后放置（扣材料），成功广播 BlockState 给所有人（含发起者确认）
            com.phoenix.game.world.Block block = blockById(p.blockId);
            if(block == null) return;
            com.phoenix.game.world.Tile tile = Vars.world.tile(p.x, p.y);
            if(com.phoenix.game.world.Build.placeBlock(tile, block, rp.player.getTeam(), p.rotation & 3)){
                broadcastBlockState(tile);
            }
        });

        net.handleServer(Packets.DeconstructRequest.class, (con, p) -> {
            RemotePlayer rp = findByConnection(con);
            if(rp == null || Vars.world == null) return;
            com.phoenix.game.world.Tile tile = Vars.world.tile(p.x, p.y);
            if(com.phoenix.game.world.Build.deconstruct(tile, rp.player.getTeam())){
                broadcastBlockRemove(tile);
            }
        });
    }

    /** 按 Blocks.all 下标取方块（与 SaveIO 一致）。 */
    private com.phoenix.game.world.Block blockById(int id){
        if(id < 0 || id >= com.phoenix.game.content.Blocks.all.size) return null;
        return com.phoenix.game.content.Blocks.all.get(id);
    }

    /** 广播一格建筑的当前状态。 */
    private void broadcastBlockState(com.phoenix.game.world.Tile tile){
        if(tile == null || tile.block() == null) return;
        Packets.BlockState st = new Packets.BlockState();
        st.x = tile.x;
        st.y = tile.y;
        st.blockId = com.phoenix.game.content.Blocks.all.indexOf(tile.block(), true);
        st.teamId = (byte)tile.getTeam().id;
        st.rotation = tile.rotation();
        st.health = tile.entity != null ? tile.entity.health() : tile.block().health;
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
        //封禁 / 人数上限校验
        if(admins.isIPBanned(con.address) || admins.isIDBanned(p.uuid)){
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

        //远程 Player（持单位引用，单位在 respawnPlayer 里生成）
        Player player = new Player();
        player.name = p.name;

        RemotePlayer rp = new RemotePlayer(con, p, player, null);
        players.add(rp);

        System.out.println("[服务器] " + p.name + " 加入（ip=" + con.address + "）");

        //生成单位 + 下发世界 + 告知单位分配
        respawnPlayer(rp);

        //广播加入提示
        broadcastChat("[服务器]", p.name + " 加入了游戏");
    }

    /**
     * 生成该玩家的单位（出生在我方核心旁），并把**当前世界**与单位分配下发给他的客户端。
     * <p>加入时与地图重开时共用同一套流程 —— 地图重开后必须重发世界，否则客户端会停留在旧世界上。
     */
    public void respawnPlayer(RemotePlayer rp){
        BaseUnit unit = UnitTypes.dagger.create();
        unit.setTeam(Team.sharded);
        unit.isPlayer = true;

        Tile core = Vars.state.teams.closestCore(Vars.world.unitWidth() / 2f, Vars.world.unitHeight() / 2f, Team.sharded);
        float x = core != null ? core.getX() : Vars.world.unitWidth() * 0.5f;
        float y = core != null ? core.getY() + 32f : Vars.world.unitHeight() * 0.5f;
        unit.set(x, y);
        unit.health(unit.maxHealth());
        Units.add(unit);

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
        String name = rp != null ? rp.player.name : con.address;
        broadcastChat(name, message);
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