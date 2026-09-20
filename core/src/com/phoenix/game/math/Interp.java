package com.phoenix.game.math;

/**
 * 缓动函数（对应 arc 的 {@code arc.math.Interp}）。
 *
 * <p>特效里用得多的是 {@link #pow3Out}（扩散"先快后慢"）。这里只收 arc 的幂函数族，
 * 指数/回弹/弹性族等暂未用到，等有调用点再补。
 */
public class Interp{

    public interface FloatFunction{
        float apply(float a);
    }

    public final String name;
    public final FloatFunction apply;

    public Interp(String name, FloatFunction apply){
        this.name = name;
        this.apply = apply;
    }

    public float apply(float a){
        return apply.apply(a);
    }

    @Override
    public String toString(){
        return name;
    }

    public static final Interp linear = new Interp("linear", a -> a);

    public static final Interp pow2 = new Interp("pow2", a -> a * a);
    public static final Interp pow3 = new Interp("pow3", a -> a * a * a);
    public static final Interp pow4 = new Interp("pow4", a -> a * a * a * a);
    public static final Interp pow5 = new Interp("pow5", a -> a * a * a * a * a);

    public static final Interp pow2Out = new Interp("pow2Out", a -> {
        float inv = 1f - a;
        return 1f - inv * inv;
    });
    public static final Interp pow3Out = new Interp("pow3Out", a -> {
        float inv = 1f - a;
        return 1f - inv * inv * inv;
    });
    public static final Interp pow4Out = new Interp("pow4Out", a -> {
        float inv = 1f - a;
        return 1f - inv * inv * inv * inv;
    });
    public static final Interp pow5Out = new Interp("pow5Out", a -> {
        float inv = 1f - a;
        return 1f - inv * inv * inv * inv * inv;
    });

    public static final Interp pow2InOut = new Interp("pow2InOut", a ->
        a < 0.5f ? 2f * a * a : 1f - 2f * (1f - a) * (1f - a));
    public static final Interp pow3InOut = new Interp("pow3InOut", a -> {
        if(a < 0.5f) return 4f * a * a * a;
        float inv = 1f - a;
        return 1f - 4f * inv * inv * inv;
    });
    public static final Interp pow4InOut = new Interp("pow4InOut", a -> {
        if(a < 0.5f) return 8f * a * a * a * a;
        float inv = 1f - a;
        return 1f - 8f * inv * inv * inv * inv;
    });
}
