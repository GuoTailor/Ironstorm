package com.phoenix.game.net;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 网络字节流的字符串/基础类型编解码工具。对应原版 Mindustry 的 mindustry.io.TypeIO 精简版。
 * <p>字符串编码约定：2 字节小端长度前缀 + UTF-8 字节（与原版一致），null 写长度 -1。
 */
public final class TypeIO {
    private TypeIO(){
    }

    public static void writeString(ByteBuffer buffer, String value){
        if(value == null){
            buffer.putShort((short)-1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short)bytes.length);
        buffer.put(bytes);
    }

    /** @return 读取字符串；长度前缀为负返回 null。 */
    public static String readString(ByteBuffer buffer){
        short len = buffer.getShort();
        if(len < 0) return null;
        byte[] bytes = new byte[len];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeBytes(ByteBuffer buffer, byte[] bytes){
        buffer.putShort((short)bytes.length);
        buffer.put(bytes);
    }

    public static byte[] readBytes(ByteBuffer buffer){
        byte[] bytes = new byte[buffer.getShort()];
        buffer.get(bytes);
        return bytes;
    }
}