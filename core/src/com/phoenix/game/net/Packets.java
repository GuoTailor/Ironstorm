package com.phoenix.game.net;

import com.badlogic.gdx.utils.Array;

import java.nio.ByteBuffer;
import java.util.Base64;

/**
 * 全部网络数据包类型与枚举。对应原版 Mindustry 的 mindustry.net.Packets。
 * <p>线路上每包 = 1 字节注册表 ID（{@link Registrator}）+ 该包 write() 的字节体。
 * {@link InvokePacket} 承载注解处理器生成 RPC 的原始载荷（后续阶段由 Call/RemoteRead* 填充）。
 */
public class Packets {

    /** 踢出原因。gameover 是静默踢出（客户端不显示）。 */
    public enum KickReason {
        kick, clientOutdated, serverOutdated, banned, gameover(true), recentKick,
        nameInUse, idInUse, nameEmpty, customClient, serverClose, vote, typeMismatch,
        whitelist, playerLimit, serverRestarting;

        public final boolean quiet;

        KickReason(){
            this(false);
        }

        KickReason(boolean quiet){
            this.quiet = quiet;
        }
    }

    /** 服务端管理动作（@Remote onAdminRequest 的参数，后续阶段接入）。 */
    public enum AdminAction {
        kick, ban, trace, wave
    }

    /** 客户端连接建立（TCP）时发给服务端。 */
    public static class Connect implements Packet {
        public String addressTCP;

        @Override
        public boolean isImportant(){
            return true;
        }
    }

    /** 客户端断开。 */
    public static class Disconnect implements Packet {
        public String reason;

        @Override
        public boolean isImportant(){
            return true;
        }
    }

    /** 世界数据流（服务端 → 客户端，压缩后的整局世界）。 */
    public static class WorldStream extends Streamable {
    }

    /** 注解处理器生成 RPC 的统一载体：原始字节载荷 + 类型 + 优先级。 */
    public static class InvokePacket implements Packet {
        public byte type, priority;
        public ByteBuffer writeBuffer;
        public int writeLength;

        @Override
        public void read(ByteBuffer buffer){
            type = buffer.get();
            priority = buffer.get();
            writeLength = buffer.getShort();
            byte[] bytes = new byte[writeLength];
            buffer.get(bytes);
            writeBuffer = ByteBuffer.wrap(bytes);
        }

        @Override
        public void write(ByteBuffer buffer){
            buffer.put(type);
            buffer.put(priority);
            buffer.putShort((short)writeLength);
            writeBuffer.position(0);
            for(int i = 0; i < writeLength; i++){
                buffer.put(writeBuffer.get());
            }
        }

        @Override
        public void reset(){
            priority = 0;
        }

        @Override
        public boolean isImportant(){
            return priority == 1;
        }

        @Override
        public boolean isUnimportant(){
            return priority == 2;
        }
    }

    /** 流的起始标记：携带总长度与目标包类型。 */
    public static class StreamBegin implements Packet {
        private static int lastid;

        public int id = lastid++;
        public int total;
        public byte type;

        @Override
        public void write(ByteBuffer buffer){
            buffer.putInt(id);
            buffer.putInt(total);
            buffer.put(type);
        }

        @Override
        public void read(ByteBuffer buffer){
            id = buffer.getInt();
            total = buffer.getInt();
            type = buffer.get();
        }
    }

    /** 流的一块数据（每块 ≤512B）。 */
    public static class StreamChunk implements Packet {
        public int id;
        public byte[] data;

        @Override
        public void write(ByteBuffer buffer){
            buffer.putInt(id);
            buffer.putShort((short)data.length);
            buffer.put(data);
        }

        @Override
        public void read(ByteBuffer buffer){
            id = buffer.getInt();
            data = new byte[buffer.getShort()];
            buffer.get(data);
        }
    }

    /** 客户端加入时的握手包（版本/名称/身份/客户端标识）。 */
    public static class ConnectPacket implements Packet {
        public int version;
        public String versionType;
        public Array<String> mods = new Array<>();
        public String name, uuid, usid;
        public boolean mobile;
        public int color;

        @Override
        public void write(ByteBuffer buffer){
            buffer.putInt(version);
            TypeIO.writeString(buffer, versionType);
            TypeIO.writeString(buffer, name);
            TypeIO.writeString(buffer, usid);
            buffer.put(mobile ? (byte)1 : 0);
            //uuid 是 16 字节二进制；原版在收/发之间做 base64 往返
            TypeIO.writeString(buffer, uuid);
            buffer.put((byte)color);
            buffer.put((byte)mods.size);
            for(String mod : mods){
                TypeIO.writeString(buffer, mod);
            }
        }

        @Override
        public void read(ByteBuffer buffer){
            version = buffer.getInt();
            versionType = TypeIO.readString(buffer);
            name = TypeIO.readString(buffer);
            usid = TypeIO.readString(buffer);
            mobile = buffer.get() == 1;
            uuid = TypeIO.readString(buffer);
            color = buffer.get();
            int totalMods = buffer.get();
            mods = new Array<>(totalMods);
            for(int i = 0; i < totalMods; i++){
                mods.add(TypeIO.readString(buffer));
            }
        }
    }

    // ---- 阶段 3 服务端/客户端同步包（phoenix 原生最小协议，原版协议对齐放阶段 6） ----

    /** 服务端 → 客户端：告知玩家被分配的单位（id/类型/队伍/出生点）。 */
    public static class SpawnPlayer implements Packet {
        public int id;
        public int typeId;
        public byte teamId;
        public float x, y, rotation;

        @Override
        public void write(ByteBuffer buffer){
            buffer.putInt(id);
            buffer.putInt(typeId);
            buffer.put(teamId);
            buffer.putFloat(x);
            buffer.putFloat(y);
            buffer.putFloat(rotation);
        }

        @Override
        public void read(ByteBuffer buffer){
            id = buffer.getInt();
            typeId = buffer.getInt();
            teamId = buffer.get();
            x = buffer.getFloat();
            y = buffer.getFloat();
            rotation = buffer.getFloat();
        }
    }

    /** 客户端把自己的单位输入发送给服务端（服务端权威移动 + 开火）。 */
    public static class PlayerInput implements Packet {
        public int id;
        public float vx, vy;
        public boolean shooting;
        public float rotation;
        /** 瞄准点世界坐标（服务器据此驱动武器开火）。 */
        public float aimX, aimY;

        @Override
        public void write(ByteBuffer buffer){
            buffer.putInt(id);
            buffer.putFloat(vx);
            buffer.putFloat(vy);
            buffer.put(shooting ? (byte)1 : 0);
            buffer.putFloat(rotation);
            buffer.putFloat(aimX);
            buffer.putFloat(aimY);
        }

        @Override
        public void read(ByteBuffer buffer){
            id = buffer.getInt();
            vx = buffer.getFloat();
            vy = buffer.getFloat();
            shooting = buffer.get() == 1;
            rotation = buffer.getFloat();
            aimX = buffer.getFloat();
            aimY = buffer.getFloat();
        }
    }

    /**
     * 服务端 → 所有客户端：**单位快照**（一个包携带多个单位，UDP）。
     * <p>对应原版 {@code Call.onEntitySnapshot}：按字节预算分片批量发送，遍历**所有单位**（含波次敌人），
     * 每项带队伍，客户端据此创建/更新代理单位。分片阈值参考原版 maxSnapshotSize = 430 字节。
     */
    public static class EntitySnapshot implements Packet {
        /** 每项固定字节数：id(4) + typeId(1) + teamId(1) + x/y/rotation/health(16)。 */
        public static final int ENTRY_BYTES = 22;
        /** 单包最大实体数（430 字节预算）。 */
        public static final int MAX_ENTRIES = 430 / ENTRY_BYTES;

        public int count;
        public int[] ids = new int[0];
        public byte[] typeIds = new byte[0], teamIds = new byte[0];
        public float[] xs = new float[0], ys = new float[0], rotations = new float[0], healths = new float[0];

        /** 追加一个单位（容量不足时自动扩容）。 */
        public void add(int id, byte typeId, byte teamId, float x, float y, float rotation, float health){
            if(count >= ids.length){
                int cap = Math.max(4, ids.length * 2);
                ids = java.util.Arrays.copyOf(ids, cap);
                typeIds = java.util.Arrays.copyOf(typeIds, cap);
                teamIds = java.util.Arrays.copyOf(teamIds, cap);
                xs = java.util.Arrays.copyOf(xs, cap);
                ys = java.util.Arrays.copyOf(ys, cap);
                rotations = java.util.Arrays.copyOf(rotations, cap);
                healths = java.util.Arrays.copyOf(healths, cap);
            }
            ids[count] = id;
            typeIds[count] = typeId;
            teamIds[count] = teamId;
            xs[count] = x;
            ys[count] = y;
            rotations[count] = rotation;
            healths[count] = health;
            count++;
        }

        @Override
        public void write(ByteBuffer buffer){
            buffer.putShort((short)count);
            for(int i = 0; i < count; i++){
                buffer.putInt(ids[i]);
                buffer.put(typeIds[i]);
                buffer.put(teamIds[i]);
                buffer.putFloat(xs[i]);
                buffer.putFloat(ys[i]);
                buffer.putFloat(rotations[i]);
                buffer.putFloat(healths[i]);
            }
        }

        @Override
        public void read(ByteBuffer buffer){
            count = buffer.getShort() & 0xFFFF;
            ids = new int[count];
            typeIds = new byte[count];
            teamIds = new byte[count];
            xs = new float[count];
            ys = new float[count];
            rotations = new float[count];
            healths = new float[count];
            for(int i = 0; i < count; i++){
                ids[i] = buffer.getInt();
                typeIds[i] = buffer.get();
                teamIds[i] = buffer.get();
                xs[i] = buffer.getFloat();
                ys[i] = buffer.getFloat();
                rotations[i] = buffer.getFloat();
                healths[i] = buffer.getFloat();
            }
        }
    }

    /** 客户端自己的单位已移除（死亡/断开）。非重要包可丢弃。 */
    public static class EntityRemove implements Packet {
        public int id;

        public EntityRemove(){
        }

        public EntityRemove(int id){
            this.id = id;
        }

        @Override
        public void write(ByteBuffer buffer){
            buffer.putInt(id);
        }

        @Override
        public void read(ByteBuffer buffer){
            id = buffer.getInt();
        }
    }

    /** 聊天消息：服务端向所有人广播。 */
    public static class ChatMessage implements Packet {
        public String name, message;

        @Override
        public void write(ByteBuffer buffer){
            TypeIO.writeString(buffer, name);
            TypeIO.writeString(buffer, message);
        }

        @Override
        public void read(ByteBuffer buffer){
            name = TypeIO.readString(buffer);
            message = TypeIO.readString(buffer);
        }
    }

    /** 踢出通知（服务端 → 客户端）。 */
    public static class Kick implements Packet {
        public String reason;

        @Override
        public void write(ByteBuffer buffer){
            TypeIO.writeString(buffer, reason);
        }

        @Override
        public void read(ByteBuffer buffer){
            reason = TypeIO.readString(buffer);
        }
    }

    /** 客户端 → 服务端：建造请求（服务端校验后权威执行并广播）。 */
    public static class BuildRequest implements Packet {
        public int x, y, blockId, rotation;

        @Override
        public void write(ByteBuffer buffer){
            buffer.putInt(x);
            buffer.putInt(y);
            buffer.putInt(blockId);
            buffer.put((byte)rotation);
        }

        @Override
        public void read(ByteBuffer buffer){
            x = buffer.getInt();
            y = buffer.getInt();
            blockId = buffer.getInt();
            rotation = buffer.get();
        }
    }

    /** 客户端 → 服务端：拆除请求。 */
    public static class DeconstructRequest implements Packet {
        public int x, y;

        @Override
        public void write(ByteBuffer buffer){
            buffer.putInt(x);
            buffer.putInt(y);
        }

        @Override
        public void read(ByteBuffer buffer){
            x = buffer.getInt();
            y = buffer.getInt();
        }
    }

    /** 服务端 → 所有客户端：建筑状态快照（增量：只发有变化的建筑）。 */
    public static class BlockState implements Packet {
        public int x, y;
        public int blockId;
        public byte teamId, rotation;
        public float health;
        /**
         * 实体内部状态（{@code revision} 字节 + {@code TileEntity.write} 的字节）。
         * <p>与存档用**同一套序列化**：配置值、建造进度、传送带上的物品、炮塔角度……
         * 全部走它同步，避免"客户端看到空白格子/进度不动的在建方块"。
         * <p>没有实体的方块为 null。
         */
        public byte[] entityData;

        @Override
        public void write(ByteBuffer buffer){
            buffer.putInt(x);
            buffer.putInt(y);
            buffer.putInt(blockId);
            buffer.put(teamId);
            buffer.put(rotation);
            buffer.putFloat(health);

            if(entityData == null){
                buffer.putInt(-1);
            }else{
                buffer.putInt(entityData.length);
                buffer.put(entityData);
            }
        }

        @Override
        public void read(ByteBuffer buffer){
            x = buffer.getInt();
            y = buffer.getInt();
            blockId = buffer.getInt();
            teamId = buffer.get();
            rotation = buffer.get();
            health = buffer.getFloat();

            int length = buffer.getInt();
            if(length < 0){
                entityData = null;
            }else{
                entityData = new byte[length];
                buffer.get(entityData);
            }
        }
    }

    /** 服务端 → 所有客户端：建筑被移除（多格建筑只发中心格，客户端自行清理卫星）。 */
    public static class BlockRemove implements Packet {
        public int x, y;

        @Override
        public void write(ByteBuffer buffer){
            buffer.putInt(x);
            buffer.putInt(y);
        }

        @Override
        public void read(ByteBuffer buffer){
            x = buffer.getInt();
            y = buffer.getInt();
        }
    }

    /**
     * 服务端 → 所有客户端：**开火事件**（UDP）。
     * <p>对应原版 {@code Call.onGenericShootWeapon} / {@code onPlayerShootWeapon}：只广播「某单位在何处开了一炮」，
     * 客户端在本地调用同一套 {@code Weapon.shootDirect} 生成并仿真子弹 —— 不传子弹位置流。
     * <p>注意：建筑（炮塔）的子弹**不走这里** —— 炮塔两端都跑仿真，各自 {@code Bullet.create}。
     */
    public static class ShootWeapon implements Packet {
        /** 射手单位 id（客户端据此找到本地玩家单位或远端代理）。 */
        public int shooterId;
        /** 相对射手的挂载点偏移（x/y）与开火角度。 */
        public float x, y, angle;
        public boolean left;

        @Override
        public void write(ByteBuffer buffer){
            buffer.putInt(shooterId);
            buffer.putFloat(x);
            buffer.putFloat(y);
            buffer.putFloat(angle);
            buffer.put(left ? (byte)1 : 0);
        }

        @Override
        public void read(ByteBuffer buffer){
            shooterId = buffer.getInt();
            x = buffer.getFloat();
            y = buffer.getFloat();
            angle = buffer.getFloat();
            left = buffer.get() == 1;
        }
    }

    /**
     * 服务端 → 所有客户端：**建筑血量纠偏**。
     * <p>对应原版 {@code Call.onTileDamage(Tile, float)}：原版建筑扣血只发生在服务端
     * （客机上 {@code Call.onTileDamage} 是 no-op），客户端靠这个包把本地血量覆盖成权威值。
     * <p>传输走 TCP 而非原版的 UDP：UDP 无序且可丢，两次命中乱序到达会让客户端停在较高的旧血量上，
     * 而 phoenix 没有原版那种「每 8 秒全量 BlockSnapshot」兜底。与 {@link BlockRemove} 的处理保持一致。
     */
    public static class TileDamage implements Packet {
        public int x, y;
        public float health;

        @Override
        public void write(ByteBuffer buffer){
            buffer.putInt(x);
            buffer.putInt(y);
            buffer.putFloat(health);
        }

        @Override
        public void read(ByteBuffer buffer){
            x = buffer.getInt();
            y = buffer.getInt();
            health = buffer.getFloat();
        }
    }

    /** 服务端 → 客户端：世界游戏状态（波次/倒计时/敌人数量）。 */
    public static class WorldState implements Packet {
        public int wave, enemies;
        public float wavetime;

        @Override
        public void write(ByteBuffer buffer){
            buffer.putInt(wave);
            buffer.putInt(enemies);
            buffer.putFloat(wavetime);
        }

        @Override
        public void read(ByteBuffer buffer){
            wave = buffer.getInt();
            enemies = buffer.getInt();
            wavetime = buffer.getFloat();
        }
    }

    /** 客户端 → 服务端：心跳 + 延迟测量（服务端原样回 Pong）。 */
    public static class Ping implements Packet {
        public long time;

        @Override
        public void write(ByteBuffer buffer){
            buffer.putLong(time);
        }

        @Override
        public void read(ByteBuffer buffer){
            time = buffer.getLong();
        }
    }

    /** 服务端 → 客户端：心跳回应（携带客户端原始时间戳，客户端据此算 RTT）。 */
    public static class Pong implements Packet {
        public long time;

        @Override
        public void write(ByteBuffer buffer){
            buffer.putLong(time);
        }

        @Override
        public void read(ByteBuffer buffer){
            time = buffer.getLong();
        }
    }

    // ---- RTS 命令系统（对应原版 @Remote commandUnits / setUnitCommand / setUnitStance） ----

    /** 命令包子类型：下达移动/攻击命令。 */
    public static final int TYPE_ORDERS = 0;
    /** 命令包子类型：切换命令（setUnitCommand）。 */
    public static final int TYPE_COMMAND = 1;
    /** 命令包子类型：切换姿态 / 取消命令（setUnitStance）。 */
    public static final int TYPE_STANCE = 2;

    /**
     * RTS 命令。客户端 → 服务端为权威请求；服务端 → 其他客户端为表现转发
     * （画连线、播特效 —— 远端代理单位不跑 AI，只记录目标）。
     * <p>对应原版的四个 {@code @Remote} 方法，这里合成一个包按 {@code type} 分派。
     */
    public static class UnitCommandPacket implements Packet {
        /** 子类型：{@link #TYPE_ORDERS} / {@link #TYPE_COMMAND} / {@link #TYPE_STANCE}。 */
        public byte type;
        /** 目标单位 id 数组。 */
        public int[] unitIds = new int[0];
        /** {@code TYPE_COMMAND}/{@code TYPE_STANCE}：命令/姿态下标（all 数组下标）。 */
        public int commandId;
        /** {@code TYPE_ORDERS}：是否为攻击命令（false = 移动）。 */
        public boolean attack;
        /** {@code TYPE_ORDERS}：攻击目标单位 id（-1 = 无）。 */
        public int targetUnitId = -1;
        /** {@code TYPE_ORDERS}：目标坐标（移动目的地 / 攻击点）。 */
        public float x, y;
        /** {@code TYPE_ORDERS}：true = 追加队列命令（中键），false = 覆盖当前命令。 */
        public boolean queue;

        @Override
        public void write(ByteBuffer buffer){
            buffer.put(type);
            buffer.putInt(unitIds.length);
            for(int i = 0; i < unitIds.length; i++){
                buffer.putInt(unitIds[i]);
            }
            buffer.putInt(commandId);
            buffer.put((byte)(attack ? 1 : 0));
            buffer.putInt(targetUnitId);
            buffer.putFloat(x);
            buffer.putFloat(y);
            buffer.put((byte)(queue ? 1 : 0));
        }

        @Override
        public void read(ByteBuffer buffer){
            type = buffer.get();
            int count = Math.min(buffer.getInt(), 512);
            unitIds = new int[count];
            for(int i = 0; i < count; i++){
                unitIds[i] = buffer.getInt();
            }
            commandId = buffer.getInt();
            attack = buffer.get() == 1;
            targetUnitId = buffer.getInt();
            x = buffer.getFloat();
            y = buffer.getFloat();
            queue = buffer.get() == 1;
        }
    }
}