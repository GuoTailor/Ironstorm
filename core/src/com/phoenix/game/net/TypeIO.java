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

    /**
     * 写入玩家追踪信息（对应原版 {@code TypeIO.writeTraceInfo}）。
     * <p>字节序照原版：ip 字符串 → uuid 字符串 → modded 字节 → mobile 字节。
     * <p>⚠ 本项目**不使用** {@code @WriteClass}/{@code @ReadClass} 注解：注解处理器会报
     * {@code No @WriteClass method} 直接中断编译（见 CODEBUDDY.md 硬约束），所以这里只保留方法、不加注解。
     */
    public static void writeTraceInfo(ByteBuffer buffer, Administration.TraceInfo trace){
        writeString(buffer, trace.ip);
        writeString(buffer, trace.uuid);
        buffer.put(trace.modded ? (byte)1 : 0);
        buffer.put(trace.mobile ? (byte)1 : 0);
    }

    /** 读取玩家追踪信息（对应原版 {@code TypeIO.readTraceInfo}）。不加注解的原因见 {@link #writeTraceInfo}。 */
    public static Administration.TraceInfo readTraceInfo(ByteBuffer buffer){
        return new Administration.TraceInfo(readString(buffer), readString(buffer), buffer.get() == 1, buffer.get() == 1);
    }
}