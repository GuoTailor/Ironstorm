package io.anuke.mindustry.gen;

/**
 * 位打包的「一次瓦片编辑」记录（对应原版 {@code mindustry.gen.TileOp}）。
 *
 * <p><b>为什么手写</b>：原版这个类是注解处理器从 {@code DrawOperation} 里的
 * {@code @Struct class TileOpStruct{ short x; short y; byte type; short value; }} 生成的；
 * 本项目的注解处理器只生成 {@code Icon/Musics/Sounds/Tex}，不认 {@code @Struct}
 * （硬约束要求 {@code @Remote}/{@code @Serialize} 保持注释，故也不宜启用注解处理器）。
 * 这里按原版生成结果**逐位照抄**，位布局与语义完全一致：
 * <pre>
 * x     [0..16]
 * y     [16..32]
 * type  [32..40]
 * value [40..56]
 * </pre>
 */
public final class TileOp{
    public static short x(long tileop){
        return (short)((tileop >>> 0) & 0xFFFFL);
    }

    public static long x(long tileop, short value){
        return (tileop & 0xFFFFL) | ((long)value << 0L);
    }

    public static short y(long tileop){
        return (short)((tileop >>> 16) & 0xFFFFL);
    }

    public static long y(long tileop, short value){
        return (tileop & 0xFFFFFFFF0000L) | ((long)value << 16L);
    }

    public static byte type(long tileop){
        return (byte)((tileop >>> 32) & 0xFFL);
    }

    public static long type(long tileop, byte value){
        return (tileop & 0xFFFFFF00FFFFFFFFL) | ((long)value << 32L);
    }

    public static short value(long tileop){
        return (short)((tileop >>> 40) & 0xFFFFL);
    }

    public static long value(long tileop, short value){
        return (tileop & 0xFF00000000FFFFFFL) | ((long)value << 40L);
    }

    public static long get(short x, short y, byte type, short value){
        return (((long)x << 0L) & 0xFFFFL)
            | (((long)y << 16L) & 0xFFFFFFFF0000L)
            | (((long)type << 32L) & 0xFFFFFF00FFFFFFFFL)
            | (((long)value << 40L) & 0xFF00000000FFFFFFL);
    }

    private TileOp(){
    }
}
