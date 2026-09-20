package com.phoenix.game.net;

import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.phoenix.game.net.Packets.*;

import java.util.function.Supplier;

/**
 * 网络包类型注册表。对应原版 Mindustry 的 mindustry.net.Registrator。
 * <p>数组下标即线路上携带的 1 字节包 ID，最多 127 个。新包类型在 {@code classes} 数组末尾追加。
 * 注意：ID 是写入线路的二进制协议格式，追加后不可随意调整已有项的顺序。
 */
public class Registrator {
    private static ClassEntry[] classes = {
        new ClassEntry(StreamBegin.class, StreamBegin::new),
        new ClassEntry(StreamChunk.class, StreamChunk::new),
        new ClassEntry(WorldStream.class, WorldStream::new),
        new ClassEntry(ConnectPacket.class, ConnectPacket::new),
        new ClassEntry(InvokePacket.class, InvokePacket::new),
        new ClassEntry(SpawnPlayer.class, SpawnPlayer::new),
        new ClassEntry(PlayerInput.class, PlayerInput::new),
        new ClassEntry(EntitySnapshot.class, EntitySnapshot::new),
        new ClassEntry(EntityRemove.class, EntityRemove::new),
        new ClassEntry(ChatMessage.class, ChatMessage::new),
        new ClassEntry(Kick.class, Kick::new),
        new ClassEntry(BuildRequest.class, BuildRequest::new),
        new ClassEntry(DeconstructRequest.class, DeconstructRequest::new),
        new ClassEntry(BlockState.class, BlockState::new),
        new ClassEntry(BlockRemove.class, BlockRemove::new),
        new ClassEntry(ShootWeapon.class, ShootWeapon::new),
        new ClassEntry(WorldState.class, WorldState::new),
        new ClassEntry(Ping.class, Ping::new),
        new ClassEntry(Pong.class, Pong::new),
        new ClassEntry(TileDamage.class, TileDamage::new),
        //RTS 命令系统：追加在末尾（1 字节包 ID = 数组下标，已有顺序不可调整）
        new ClassEntry(UnitCommandPacket.class, UnitCommandPacket::new),
    };
    private static final ObjectIntMap<Class<?>> ids = new ObjectIntMap<>();
    private static final IntMap<ClassEntry> byID = new IntMap<>();

    static {
        if(classes.length > 127){
            throw new RuntimeException("Can't have more than 127 registered classes!");
        }
        for(int i = 0; i < classes.length; i++){
            ids.put(classes[i].type, i);
            byID.put(i, classes[i]);
        }
    }

    /** 按 1 字节 ID 取条目。 */
    public static ClassEntry getByID(byte id){
        return byID.get(id & 0xFF);
    }

    /** 按类型取 ID，未注册返回 -1。 */
    public static byte getID(Class<?> type){
        return (byte)ids.get(type, -1);
    }

    public static ClassEntry[] getClasses(){
        return classes;
    }

    /** 单条注册：类型 + 无参构造器。 */
    public static class ClassEntry {
        public final Class<?> type;
        public final Supplier<?> constructor;

        public <T extends Packet> ClassEntry(Class<T> type, Supplier<T> constructor){
            this.type = type;
            this.constructor = constructor;
        }
    }
}