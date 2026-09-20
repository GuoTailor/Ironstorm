package com.phoenix.game.core;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.phoenix.game.Vars;
import com.phoenix.game.content.UnitTypes;
import com.phoenix.game.core.GameState.State;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.game.Team;
import com.phoenix.game.net.Net;
import com.phoenix.game.net.NetworkIO;
import com.phoenix.game.net.Packets;
import com.phoenix.game.net.PhoenixNetProvider;
import com.phoenix.game.type.UnitType;

import java.util.UUID;

/**
 * 多人客户端。对应原版 Mindustry 的 mindustry.core.NetClient（适配 phoenix 模型）。
 * <p>thin client：服务器权威。职责：connect 连接并握手、接收世界数据（WorldStream → 重建本地世界）、
 * 接收 SpawnPlayer 生成本地单位、每帧上报输入（PlayerInput，含 WASD 方向与瞄准角）、
 * 接收单位快照（EntitySnapshot）并驱动本地/远端单位、聊天、被踢处理。
 * <p>远端单位：EntitySnapshot 一个包携带多个单位（含波次敌人）与其队伍，客户端按 id 建立代理单位加入 Units，
 * 位置用插值平滑（见 {@link #interpolateRemoteUnits()}）；id 未知的即视为新单位。
 * <p>子弹不走这里的位置同步：服务端只广播一次生成事件，客户端本地建子弹自行仿真。
 */
public class NetClient {
    /** 本地单位 id（服务器分配）。 */
    private int playerUnitId = -1;
    /** 是否已连接。 */
    private boolean connecting, connected;
    /** 上次连接参数（断线重连用）。 */
    private String lastIp, lastName;
    private int lastPort;
    /** 远端玩家单位代理（服务器实体 id → 本地代理单位）。 */
    private final IntMap<BaseUnit> remoteUnits = new IntMap<>();
    /** 最近一次上报输入的时间（节流，tick）。 */
    private float inputTimer;
    /** 聊天回调（UI 层设置；null 仅打印）。 */
    public java.util.function.BiConsumer<String, String> onChat;
    /** 被踢回调（UI 层设置）。 */
    public java.util.function.Consumer<String> onKick;
    /** 世界加载完成回调（UI 层设置；用于关加载框）。 */
    public Runnable onWorldLoaded;

    /** 传输层。 */
    public final PhoenixNetProvider provider = new PhoenixNetProvider();
    /** 门面。 */
    public final Net net = new Net(provider);

    public NetClient(){
        setupHandlers();
    }

    /** 连接服务器。 */
    public void connect(String ip, int port, String name){
        if(connected || connecting) return;
        connecting = true;
        connectingSince = tickCounter;
        lastIp = ip;
        lastPort = port;
        lastName = name;
        lost = false;
        reconnectTimer = 0f;
        boolean ok = net.connect(ip, port, () -> {
            connected = true;
            connecting = false;
            net.setClientConnected();
            lastReceiveTick = tickCounter;

            //握手（在任何用数据包前先标记为未加载，世界数据是关键包会先处理）
            Packets.ConnectPacket cp = new Packets.ConnectPacket();
            cp.version = 126;
            cp.versionType = "phoenix";
            cp.name = name;
            cp.uuid = UUID.randomUUID().toString();
            cp.usid = "";
            net.send(cp, Net.SendMode.tcp);

            System.out.println("[客户端] 已连接 " + ip + ":" + port + "，发送握手");
        });
        //连接失败立即回滚 connecting，否则要空等满 connectTimeoutTicks 才能重试
        if(!ok) connecting = false;
    }

    /** 尝试重连到上次的服务器（供断线重连调用）。 */
    public boolean reconnect(){
        if(connected || connecting || lastIp == null) return false;
        System.out.println("[客户端] 尝试重连 " + lastIp + ":" + lastPort);
        connect(lastIp, lastPort, lastName);
        return true;
    }

    // ---- 心跳 / 超时 / 自动重连 ----

    /** 心跳发送间隔（tick，约 1 秒）。 */
    public static final float pingInterval = 60f;
    /** 超时阈值（tick，约 8 秒无任何收包视为断线）。 */
    public static final float timeoutTicks = 60f * 8f;
    /** 自动重连尝试间隔（tick，约 5 秒）。 */
    public static final float reconnectInterval = 60f * 5f;
    /** 单次连接尝试的超时（tick，约 6 秒）。兜底用：正常失败路径已由 connect 同步回滚。 */
    public static final float connectTimeoutTicks = 60f * 6f;

    /** 最近一次收到服务端包的时间（tick 累计）。 */
    private float lastReceiveTick;
    /** 心跳计时。 */
    private float pingTimer;
    /** 重连计时。 */
    private float reconnectTimer;
    /** 最近一次 RTT（毫秒，-1 未知）。 */
    private int ping = -1;
    /** 是否自动重连（断线后）。 */
    public boolean autoReconnect = true;
    /** 断线回调（UI 层设置）。 */
    public java.util.function.Consumer<String> onDisconnect;
    /** 上次连接因超时/断开而丢失（区分主动 disconnect）。 */
    private boolean lost;
    /** 当前连接尝试的开始时刻（tick，超时回滚用）。 */
    private float connectingSince;

    /** @return 最近一次 RTT 毫秒；-1 表示未知。 */
    public int getPing(){
        return ping;
    }

    /** 每帧心跳与超时检测。 */
    private void updateHeartbeat(){
        //按时间累加而非按帧自增：常量以 60fps 的 tick 为单位，帧率变化时按帧计数会偏快
        pingTimer += com.phoenix.game.core.Time.delta();
        if(pingTimer >= pingInterval){
            pingTimer = 0;
            Packets.Ping p = new Packets.Ping();
            p.time = System.currentTimeMillis();
            net.send(p, Net.SendMode.tcp);
        }

        if(tickCounter - lastReceiveTick > timeoutTicks){
            System.out.println("[客户端] 连接超时（" + (int)(timeoutTicks / 60f) + " 秒无响应）");
            handleLost("连接超时");
        }
    }

    /** 断线处理：标记丢失、清理、回调。 */
    private void handleLost(String reason){
        if(!connected) return;
        connected = false;
        lost = true;
        playerUnitId = -1;
        clearRemoteUnits();
        try{
            net.disconnect();
        }catch(Exception ignored){
        }
        reconnectTimer = 0f;
        if(onDisconnect != null) onDisconnect.accept(reason);
    }

    /** 未连接时按间隔自动重连。 */
    private void updateReconnect(){
        //兜底回滚：connect 失败已同步回滚 connecting，这里只防 provider 既不回调也不抛异常
        if(connecting){
            if(tickCounter - connectingSince > connectTimeoutTicks){
                System.out.println("[客户端] 连接尝试超时，稍后重试");
                connecting = false;
            }
            return;
        }

        if(!autoReconnect || !lost || lastIp == null) return;
        //同样按时间累加，保证重试间隔是真实的 reconnectInterval
        reconnectTimer += com.phoenix.game.core.Time.delta();
        if(reconnectTimer >= reconnectInterval){
            reconnectTimer = 0f;
            System.out.println("[客户端] 自动重连 " + lastIp + ":" + lastPort);
            connecting = true;
            connectingSince = tickCounter;
            boolean ok = net.connect(lastIp, lastPort, () -> {
                connected = true;
                connecting = false;
                lost = false;
                net.setClientConnected();
                lastReceiveTick = tickCounter;
                Packets.ConnectPacket cp = new Packets.ConnectPacket();
                cp.version = 126;
                cp.versionType = "phoenix";
                cp.name = lastName;
                cp.uuid = UUID.randomUUID().toString();
                cp.usid = "";
                net.send(cp, Net.SendMode.tcp);
                System.out.println("[客户端] 重连成功，重新握手");
            });
            //失败立即回滚，使下轮重试严格按 reconnectInterval 计时
            if(!ok) connecting = false;
        }
    }

    /** 单调递增的 tick 计数（近似帧数，用于超时/心跳计时）。 */
    private float tickCounter;

    /** 每帧调用：drain 入站 + 上报本地单位输入 + 心跳/超时检测。 */
    public void update(){
        //推进内部 tick 计数（心跳/超时计时基准），每帧恰好一次
        tickCounter += com.phoenix.game.core.Time.delta();

        //未连接：若配置了自动重连且有过连接，按间隔尝试重连
        if(!connected){
            updateReconnect();
            return;
        }

        boolean received = false;
        while(provider.hasInbound()){
            try{
                PhoenixNetProvider.InboundFrame f = provider.pollInbound();
                net.handleClientReceived(f.packet);
                received = true;
            }catch(Exception e){
                System.out.println("NetClient 处理入站包出错: " + e);
            }
        }

        //收到任何包都刷新存活时间（超时检测只看是否还有响应）
        if(received) lastReceiveTick = tickCounter;

        //把远端代理单位从上次快照位置平滑推进到本次快照位置（快照间隔约 200ms，不插值会一跳一跳）
        interpolateRemoteUnits();

        updateHeartbeat();
        //上报本地单位输入（服务端权威移动）：WASD 方向 + 瞄准角
        if(playerUnitId >= 0 && Vars.player != null && Vars.player.unit() != null && !Vars.player.isDead()){
            BaseUnit unit = Vars.player.unit();
            Packets.PlayerInput pi = new Packets.PlayerInput();
            pi.id = playerUnitId;
            //归一化移动方向（长度≤1，服务端乘 maxVelocity）
            float vx = com.phoenix.game.input.InputHandler.movementX();
            float vy = com.phoenix.game.input.InputHandler.movementY();
            float len = (float)Math.sqrt(vx * vx + vy * vy);
            if(len > 1f){
                vx /= len;
                vy /= len;
            }
            pi.vx = vx;
            pi.vy = vy;
            pi.shooting = Vars.player.isShooting;
            pi.rotation = unit.rotation;
            //瞄准点（服务器据此驱动武器开火）
            pi.aimX = Vars.player.pointerX;
            pi.aimY = Vars.player.pointerY;
            net.send(pi, Net.SendMode.udp);
        }
    }

    /** 发送聊天。 */
    public void sendChat(String message){
        if(!connected) return;
        Packets.ChatMessage cm = new Packets.ChatMessage();
        cm.message = message;
        net.send(cm, Net.SendMode.tcp);
    }

    /** 发送建造请求（服务端校验并权威执行；本地等待 BlockState 确认）。 */
    public void sendBuild(int tileX, int tileY, com.phoenix.game.world.Block block, int rotation){
        if(!connected) return;
        Packets.BuildRequest req = new Packets.BuildRequest();
        req.x = tileX;
        req.y = tileY;
        req.blockId = com.phoenix.game.content.Blocks.all.indexOf(block, true);
        req.rotation = rotation;
        net.send(req, Net.SendMode.tcp);
    }

    /** 发送拆除请求。 */
    public void sendDeconstruct(int tileX, int tileY){
        if(!connected) return;
        Packets.DeconstructRequest req = new Packets.DeconstructRequest();
        req.x = tileX;
        req.y = tileY;
        net.send(req, Net.SendMode.tcp);
    }

    /** 断开连接并清理远端代理单位。 */
    public void disconnect(){
        if(connected){
            net.disconnect();
            connected = false;
            lost = false; //主动断开：不触发自动重连
            playerUnitId = -1;
            clearRemoteUnits();
        }
    }

    /** 是否已连接。 */
    public boolean isConnected(){
        return connected;
    }

    // ---- 远端单位代理 ----

    /** 每帧把远端代理单位的显示位置向最近一次快照目标插值（对应原版 SyncTrait.interpolate）。 */
    private void interpolateRemoteUnits(){
        for(IntMap.Entry<BaseUnit> e : remoteUnits.entries()){
            BaseUnit u = e.value;
            if(u.isDead() || u.interpolator == null) continue;

            float a = u.interpolator.alpha();
            u.set(u.interpolator.x(a), u.interpolator.y(a));
            u.rotation = u.interpolator.rotation(a);
        }
    }

    /** 清理全部远端代理单位（断开/回到菜单时）。 */
    private void clearRemoteUnits(){
        for(IntMap.Entry<BaseUnit> e : remoteUnits.entries()){
            BaseUnit u = e.value;
            if(!u.isDead()) u.remove();
        }
        remoteUnits.clear();
    }

    /** 按 id 找单位：远端代理单位 或 本机玩家单位。 */
    private BaseUnit findUnit(int id){
        BaseUnit remote = remoteUnits.get(id);
        if(remote != null) return remote;
        if(id == playerUnitId && Vars.player != null) return Vars.player.unit();
        return null;
    }

    /** 按 id 取（或创建）远端代理单位。 */
    private BaseUnit getRemoteUnit(int id, byte typeId, byte teamId){
        BaseUnit unit = remoteUnits.get(id);
        if(unit == null){
            UnitType type = typeId >= 0 && typeId < UnitTypes.all.size ? UnitTypes.all.get(typeId) : UnitTypes.dagger;
            unit = type.create();
            unit.isPlayer = false;
            unit.isRemoteProxy = true; //本地不跑 AI/物理，位置由服务器快照驱动
            unit.setTeam(Team.get(teamId));
            Units.add(unit);
            remoteUnits.put(id, unit);
            System.out.println("[客户端] 远端单位进入 id=" + id + " type=" + type.name);
        }
        return unit;
    }

    // ---- 包处理 ----

    private void setupHandlers(){
        //世界数据：重建本地世界，然后开始接收常规包
        //注意：服务端终局重开地图时会**再次**下发本包，所以这里必须把客户端侧的世界相关状态一并清干净，
        //否则旧的代理单位/子弹/玩家单位 id 会残留（SaveIO.read 只负责清 Units.units）。
        net.handleClient(Packets.WorldStream.class, ws -> {
            System.out.println("[客户端] 收到世界数据 " + ws.stream.available() + " 字节");

            remoteUnits.clear();
            playerUnitId = -1;
            com.phoenix.game.entities.type.Bullet.all.clear();
            com.phoenix.game.entities.Effects.clear();

            NetworkIO.loadWorld(readAll(ws));
            //重建依赖世界尺寸/方块的派生数据（寻路流场网格、出生点），
            //否则换到不同尺寸的地图后旧网格会越界。
            com.phoenix.game.core.Events.fire(new com.phoenix.game.game.EventType.WorldLoadEvent());
            Vars.state.set(State.playing);
            net.setClientLoaded(true);
            System.out.println("[客户端] 世界已加载 " + Vars.world.width + "x" + Vars.world.height);
            if(onWorldLoaded != null) onWorldLoaded.run();
        });

        net.handleClient(Packets.SpawnPlayer.class, sp -> {
            System.out.println("[客户端] 获得玩家单位 id=" + sp.id + " team=" + sp.teamId);
            playerUnitId = sp.id;

            //单位快照（UDP）可能先于本包（TCP）到达，那时自己的单位已被当成远端代理创建过 → 先移除避免重复
            BaseUnit stale = remoteUnits.remove(sp.id);
            if(stale != null && !stale.isDead()) stale.remove();

            UnitType type = sp.typeId >= 0 && sp.typeId < UnitTypes.all.size ? UnitTypes.all.get(sp.typeId) : UnitTypes.dagger;
            BaseUnit unit = type.create();
            unit.setTeam(Team.get(sp.teamId));
            unit.isPlayer = true;
            unit.set(sp.x, sp.y);
            unit.rotation = sp.rotation;
            unit.health(unit.maxHealth());
            Units.add(unit);

            if(Vars.player == null){
                Vars.player = new Player();
            }
            Vars.player.team = Team.get(sp.teamId);
            Vars.player.unit(unit);
        });

        //单位快照（一个包多个单位）：本地玩家单位由服务端权威覆盖，其余创建/更新为远端代理
        net.handleClient(Packets.EntitySnapshot.class, snap -> {
            for(int i = 0; i < snap.count; i++){
                int id = snap.ids[i];

                if(id == playerUnitId){
                    if(Vars.player != null && Vars.player.unit() != null){
                        BaseUnit unit = Vars.player.unit();
                        unit.set(snap.xs[i], snap.ys[i]);
                        unit.rotation = snap.rotations[i];
                        unit.health(snap.healths[i]);
                    }
                    continue;
                }

                BaseUnit remote = getRemoteUnit(id, snap.typeIds[i], snap.teamIds[i]);
                if(remote != null){
                    if(remote.interpolator == null){
                        //新建的代理：直接就位，避免从 (0,0) 飞过来
                        remote.interpolator = new com.phoenix.game.net.Interpolator();
                        remote.interpolator.snap(snap.xs[i], snap.ys[i], snap.rotations[i]);
                        remote.set(snap.xs[i], snap.ys[i]);
                        remote.rotation = snap.rotations[i];
                    }else{
                        //已有代理：记下起点/终点，由 interpolateRemoteUnits() 每帧平滑推进
                        remote.interpolator.read(remote.x, remote.y, remote.rotation,
                                snap.xs[i], snap.ys[i], snap.rotations[i]);
                    }
                    remote.health(snap.healths[i]);
                }
            }
        });

        net.handleClient(Packets.EntityRemove.class, er -> {
            if(er.id == playerUnitId){
                System.out.println("[客户端] 我的单位被移除");
                playerUnitId = -1;
                return;
            }
            //远端代理单位移除
            BaseUnit remote = remoteUnits.get(er.id);
            if(remote != null){
                if(!remote.isDead()) remote.remove();
                remoteUnits.remove(er.id);
                System.out.println("[客户端] 远端玩家单位离开 id=" + er.id);
            }
        });

        net.handleClient(Packets.ChatMessage.class, cm -> {
            System.out.println("[" + cm.name + "] " + cm.message);
            if(onChat != null) onChat.accept(cm.name, cm.message);
        });

        net.handleClient(Packets.Kick.class, k -> {
            System.out.println("[客户端] 被踢出: " + k.reason);
            //被踢是主动断开（不自动重连）
            autoReconnect = false;
            disconnect();
            if(onKick != null) onKick.accept(k.reason);
        });

        //心跳回应：算 RTT
        net.handleClient(Packets.Pong.class, p -> {
            ping = (int)(System.currentTimeMillis() - p.time);
        });

        // ---- 6b：建筑 / 子弹 / 世界状态同步 ----

        //建筑状态：放置/更新（含发起者自己的确认）
        net.handleClient(Packets.BlockState.class, st -> {
            if(Vars.world == null) return;
            com.phoenix.game.world.Block block = com.phoenix.game.content.Blocks.all.get(
                Math.max(0, Math.min(st.blockId, com.phoenix.game.content.Blocks.all.size - 1)));
            com.phoenix.game.world.Tile tile = Vars.world.tile(st.x, st.y);
            if(tile == null) return;

            //已一致则跳过（发起者本地预放置后服务器确认）
            if(tile.block() == block && tile.getTeam() == com.phoenix.game.game.Team.get(st.teamId)){
                if(tile.entity != null) tile.entity.health(st.health);
                return;
            }
            tile.setBlock(block, com.phoenix.game.game.Team.get(st.teamId), st.rotation);
            if(tile.entity != null) tile.entity.health(st.health);
        });

        //建筑血量纠偏：客机不本地扣血，服务端每次命中后广播权威血量（对应原版 Call.onTileDamage 的接收端）
        net.handleClient(Packets.TileDamage.class, td -> {
            if(Vars.world == null) return;
            com.phoenix.game.world.Tile tile = Vars.world.tile(td.x, td.y);
            if(tile != null && tile.entity != null){
                tile.entity.health(td.health);
            }
        });

        //建筑移除
        net.handleClient(Packets.BlockRemove.class, rm -> {
            if(Vars.world == null) return;
            com.phoenix.game.world.Tile tile = Vars.world.tile(rm.x, rm.y);
            if(tile != null && tile.block() != com.phoenix.game.content.Blocks.air){
                tile.remove();
            }
        });

        //开火事件：找到射手，本地调用同一套 shootDirect 生成并仿真子弹（对应原版 onGenericShootWeapon 接收端）
        net.handleClient(Packets.ShootWeapon.class, sw -> {
            BaseUnit shooter = findUnit(sw.shooterId);
            if(shooter == null) return;

            //射手是本机玩家单位：本地 InputHandler 已经开过火了，跳过服务端回声
            //（对应原版 onPlayerShootWeapon 里 net.client() && player == Vars.player 的跳过）
            if(Vars.player != null && shooter == Vars.player.unit()) return;

            com.phoenix.game.type.Weapon weapon = shooter.getWeapon();
            if(weapon != null){
                weapon.shootDirect(shooter, sw.x, sw.y, sw.angle, sw.left);
            }
        });

        //世界状态：波次/倒计时/敌人数量（HUD 显示用）
        net.handleClient(Packets.WorldState.class, ws -> {
            Vars.state.wave = ws.wave;
            Vars.state.enemies = ws.enemies;
            Vars.state.wavetime = ws.wavetime;
        });
    }

    /** 读取 Streamable 的 stream 全量字节（WorldStream 携带）。 */
    private byte[] readAll(Packets.WorldStream ws){
        try{
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while((n = ws.stream.read(buf)) > 0){
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }
}