package com.phoenix.game.net;

import com.badlogic.gdx.utils.Pools;

import java.nio.ByteBuffer;

/**
 * 网络帧编码器。对应原版 ArcNetProvider 的 PacketSerializer。
 * <p>线路帧格式：<b>1 字节注册表 ID</b> + 该包 {@link Packet#write} 的字节体。
 * 反序列化时按 ID 从对象池取一个实例再 {@link Packet#read}。
 * <p>读写用的是同一段可增长 buffer；帧边界由传输层处理（TCP 加 4 字节长度、UDP 一个数据报一帧）。
 */
public final class PacketSerializer {
    /** 单帧缓冲上限（防恶意超大包）。 */
    public static final int MAX_FRAME_SIZE = 1024 * 1024 * 16;

    private PacketSerializer(){
    }

    /**
     * 把一个 {@link Packet} 序列化进 buffer（含 1 字节包 ID 前缀）。
     * @return 写入后的 buffer（position 位于末尾，可 flip 读出字节）。
     */
    public static ByteBuffer write(ByteBuffer buffer, Object object){
        if(!(object instanceof Packet)){
            throw new IllegalArgumentException("Cannot serialize non-Packet: " + object);
        }
        int id = Registrator.getID(object.getClass());
        if(id < 0){
            throw new IllegalArgumentException("Unregistered packet type: " + object.getClass());
        }

        buffer.put((byte)id);
        ((Packet)object).write(buffer);
        return buffer;
    }

    /**
     * 从 buffer 读取一个包对象（消耗 1 字节 ID + 包体）。
     * 从对象池取出，调用方处理完后必须用 {@link Pools#free} 归还。
     */
    public static Packet read(ByteBuffer buffer){
        byte id = buffer.get();
        Packet packet = (Packet)Registrator.getByID(id).constructor.get();
        packet.read(buffer);
        return packet;
    }
}