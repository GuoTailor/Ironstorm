package com.phoenix.game.util;

/**
 * 位打包工具。参照 {@code arc.util.Pack} 移植（原版用于网络包与存档的紧凑编解码）。
 * <p>方法语义与 arc 完全一致：leftX/rightX 取高低半部，shortInt/longInt 拼接，
 * intBytes/bytes/shorts/longShorts 做字节序无关的拆分与合并。
 */
public class Pack{

    private Pack(){
    }

    public static int u(byte value){
        return value & 0xff;
    }

    public static int u(short value){
        return value & 0xffff;
    }

    public static long u(int value){
        return value & 0xffffffffL;
    }

    public static byte byteValue(boolean value){
        return (byte)(value ? 1 : 0);
    }

    /** 把两个 short 拼成一个 int（高 16 位 = a，低 16 位 = b）。 */
    public static int shortInt(short a, short b){
        return (a << 16) | (b & 0xffff);
    }

    /** 把两个 int 拼成一个 long。 */
    public static long longInt(int a, int b){
        return ((long)a << 32) | (b & 0xffffffffL);
    }

    public static byte byteByte(byte a, byte b){
        return (byte)((a << 4) | (b & 0xf));
    }

    public static byte leftByte(byte value){
        return (byte)((value >> 4) & 0xf);
    }

    public static byte rightByte(byte value){
        return (byte)(value & 0xf);
    }

    public static short leftShort(int value){
        return (short)((value >> 16) & 0xffff);
    }

    public static short rightShort(int value){
        return (short)(value & 0xffff);
    }

    public static int leftInt(long value){
        return (int)((value >> 32) & 0xffffffffL);
    }

    public static int rightInt(long value){
        return (int)(value & 0xffffffffL);
    }

    public static byte leftByte(short value){
        return (byte)((value >> 8) & 0xff);
    }

    public static byte rightByte(short value){
        return (byte)(value & 0xff);
    }

    public static short shortByte(byte a, byte b){
        return (short)((a << 8) | (b & 0xff));
    }

    /** 把 int 拆成 4 个字节写入 out（大端），返回 out。 */
    public static byte[] bytes(int value, byte[] out){
        out[0] = (byte)(value >> 24);
        out[1] = (byte)(value >> 16);
        out[2] = (byte)(value >> 8);
        out[3] = (byte)value;
        return out;
    }

    /** 把 long 拆成 4 个 short 写入 out（大端），返回 out。 */
    public static short[] shorts(long value, short[] out){
        out[0] = (short)(value >> 48);
        out[1] = (short)(value >> 32);
        out[2] = (short)(value >> 16);
        out[3] = (short)value;
        return out;
    }

    /** 把 4 个 short 合并成一个 long。 */
    public static long longShorts(short[] values){
        return ((long)values[0] << 48) | ((long)values[1] << 32) | ((long)values[2] << 16) | (values[3] & 0xffffL);
    }

    public static int intBytes(byte b0, byte b1, byte b2, byte b3){
        return ((b0 & 0xff) << 24) | ((b1 & 0xff) << 16) | ((b2 & 0xff) << 8) | (b3 & 0xff);
    }

    public static int intBytes(byte[] values){
        return intBytes(values[0], values[1], values[2], values[3]);
    }
}
