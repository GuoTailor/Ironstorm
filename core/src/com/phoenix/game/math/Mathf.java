package com.phoenix.game.math;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.phoenix.game.core.Time;

/**
 * create by GYH on 2025/3/28
 * 参照 Mindustry arc.math.Mathf 移植，底层尽量使用 libgdx 的 MathUtils 实现。
 */
public class Mathf {
    public static final float PI = (float)Math.PI;
    public static final float PI2 = PI * 2f;
    public static final float radiansToDegrees = 180f / PI;
    public static final float degreesToRadians = PI / 180f;
    public static final boolean[] booleans = {false, true};
    /** 左右两种朝向，用于对称绘制（对应 arc 的 Mathf.signs） */
    public static final int[] signs = {-1, 1};
    /** shared random instance */
    public static final RandomXS128 random = new RandomXS128();

    public static float dst(float x1, float y1){
        return (float)Math.sqrt(x1 * x1 + y1*y1);
    }
    public static float dst2(float x1, float y1) {
        return x1 * x1 + y1 * y1;
    }
    public static float dst2(float x1, float y1, float x2, float y2){
        final float xd = x2 - x1;
        final float yd = y2 - y1;
        return xd * xd + yd * yd;
    }
    /** @return distance between the two points. */
    public static float dst(float x1, float y1, float x2, float y2){
        final float xd = x2 - x1;
        final float yd = y2 - y1;
        return (float)Math.sqrt(xd * xd + yd * yd);
    }
    public static boolean within(float x1, float y1, float dst) {
        return dst2(x1, y1) < dst * dst;
    }
    /** @return whether dst(x1, y1, x2, y2) < dst */
    public static boolean within(float x1, float y1, float x2, float y2, float dst){
        return dst2(x1, y1, x2, y2) < dst*dst;
    }

    public static int mod(int x, int n) {
        return (x % n + n) % n;
    }

    public static float mod(float x, float n) {
        return (x % n + n) % n;
    }

    /**Returns -1 if f<0, 1 otherwise.*/
    public static int sign(float f){
        return (f < 0 ? -1 : 1);
    }

    /** Returns 1 if true, -1 if false. */
    public static int sign(boolean b){
        return b ? 1 : -1;
    }

    public static float clamp(float value){
        return clamp(value, 0f, 1f);
    }

    public static float clamp(float value, float min, float max){
        return MathUtils.clamp(value, min, max);
    }

    public static int clamp(int value, int min, int max){
        return MathUtils.clamp(value, min, max);
    }

    public static float lerp(float from, float to, float progress){
        return MathUtils.lerp(from, to, progress);
    }

    /** Delta-corrected lerp, for use with a 60fps-ish tick rate. */
    public static float lerpDelta(float from, float to, float progress){
        return lerp(from, to, 1f - (float)Math.pow(1f - progress, Time.delta()));
    }

    /** Angle interpolation. Equivalent to arc's Mathf.slerp. */
    public static float slerp(float from, float to, float progress){
        return MathUtils.lerpAngleDeg(from, to, progress);
    }

    /** Delta-corrected angle interpolation. Equivalent to arc's Mathf.slerpDelta. */
    public static float slerpDelta(float from, float to, float progress){
        return slerp(from, to, 1f - (float)Math.pow(1f - progress, Time.delta()));
    }

    /** @return a random float between 0 and 1 (inclusive). */
    public static float random(){
        return random.nextFloat();
    }

    /** @return a random float between 0 and range. */
    public static float random(float range){
        return random.nextFloat() * range;
    }

    /** @return a random float between min and max. */
    public static float random(float min, float max){
        return min + random.nextFloat() * (max - min);
    }

    /** @return a random int between 0 (inclusive) and range (exclusive). */
    public static int random(int range){
        return random.nextInt(Math.max(range, 1));
    }

    /** @return a random float between -amount and amount. */
    public static float range(float amount){
        return random(-amount, amount);
    }

    /** @return true with the specified chance (0-1). */
    public static boolean chance(float chance){
        return random.nextFloat() < chance;
    }

    /** 由种子派生的随机实例（对应 arc 的 Mathf.seedr）。 */
    private static final RandomXS128 seedr = new RandomXS128();

    /**
     * @return 由种子确定的 [0,1) 随机数（对应 arc 的 {@code Mathf.randomSeed(long)}）。
     * <p>同一 seed 恒返回同一值，用于「按瓦片坐标确定性地挑变体贴图」这类场景。
     */
    public static float randomSeed(long seed){
        seedr.setSeed(seed * 99999L);
        return seedr.nextFloat();
    }

    /** @return 由种子确定的 [min,max] 随机整数（对应 arc 的 {@code Mathf.randomSeed(long,int,int)}）。 */
    public static int randomSeed(long seed, int min, int max){
        seedr.setSeed(seed);
        int range = max - min + 1;
        //arc 在区间长度为 2 的幂时会先丢弃一个值，这里保持一致
        if(range > 0 && (range & (range - 1)) == 0){
            seedr.nextInt();
        }
        return seedr.nextInt(Math.max(range, 1)) + min;
    }

    /** @return sine wave that is always positive. */
    public static float absin(float time, float speed, float amplitude){
        return Math.abs((float)Math.sin(time / speed * PI2) * amplitude);
    }

    /** @return sin(x / y)，对应 arc 的 Mathf.sin(x, y)。 */
    public static float sin(float x, float y){
        return (float)Math.sin(x / y);
    }

    /** @return sin(x / y) * scl，对应 arc 的 Mathf.sin(x, y, scl)。 */
    public static float sin(float x, float y, float scl){
        return (float)Math.sin(x / y) * scl;
    }

    public static float absin(float time, float speed){
        return absin(time, speed, 1f);
    }

    public static float len(float x, float y){
        return dst(x, y);
    }

    public static float sqrt(float value){
        return (float)Math.sqrt(value);
    }

    public static float pow(float a, float b){
        return (float)Math.pow(a, b);
    }

    public static float round(float value){
        return MathUtils.round(value);
    }

    public static float floor(float value){
        return (float)Math.floor(value);
    }

    public static float ceil(float value){
        return (float)Math.ceil(value);
    }

    public static float max(float a, float b){
        return Math.max(a, b);
    }

    public static float min(float a, float b){
        return Math.min(a, b);
    }
}
