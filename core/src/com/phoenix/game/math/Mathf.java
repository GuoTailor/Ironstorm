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

    /** 把 f 从 [min,max] 归一化到 [0,1]（超出范围夹住），对应 arc 的 Mathf.curve。 */
    public static float curve(float f, float min, float max){
        return clamp((f - min) / (max - min));
    }

    /**
     * 把 f 从 [0,max] 归一化到 [0,1]（超出范围夹住），对应 arc 的 {@code Mathf.curve(f, max)}。
     * <p>等价于 {@code curve(f, 0f, max)}（原版 Decal/Player 用的就是两参重载）。
     */
    public static float curve(float f, float max){
        return curve(f, 0f, max);
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

    /** @return a random int between -amount and amount (inclusive)，对应 arc 的 {@code Mathf.range(int)}。 */
    public static int range(int amount){
        return random.nextInt(amount * 2 + 1) - amount;
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

    /** @return 由种子确定的 [-range, range] 随机数（对应 arc 的 {@code Mathf.randomSeedRange(long,float)}）。 */
    public static float randomSeedRange(long seed, float range){
        return (randomSeed(seed) - 0.5f) * 2f * range;
    }

    /** @return (x,y) 与 x 轴正方向的夹角（度，对应 arc 的 {@code Mathf.angle}）。 */
    public static float angle(float x, float y){
        return MathUtils.atan2(y, x) * radiansToDegrees;
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

    /** @return sin(x)，对应 arc 的 Mathf.sin(float)。 */
    public static float sin(float x){
        return (float)Math.sin(x);
    }

    /** @return cos(x)，对应 arc 的 Mathf.cos(float)。 */
    public static float cos(float x){
        return (float)Math.cos(x);
    }

    /** @return cos(x / y)，对应 arc 的 Mathf.cos(x, y)。 */
    public static float cos(float x, float y){
        return (float)Math.cos(x / y);
    }

    /** @return cos(x / y) * scl，对应 arc 的 Mathf.cos(x, y, scl)。 */
    public static float cos(float x, float y, float scl){
        return (float)Math.cos(x / y) * scl;
    }

    /** 角度转弧度，对应 arc 的 Mathf.degRad。 */
    public static float degRad(float degrees){
        return degrees * MathUtils.degreesToRadians;
    }

    /** 弧度转角度，对应 arc 的 Mathf.radDeg。 */
    public static float radDeg(float radians){
        return radians * MathUtils.radiansToDegrees;
    }

    /**
     * 对应 arc 的 {@code Mathf.absin(scl, mag)}：以 {@code Time.time} 为时间轴的脉动，振幅 {@code mag}、周期 {@code scl}。
     * <p>⚠ <b>S5b 修正</b>：此前误实现为 {@code absin(time, speed)}（把第一个参数当时间、振幅恒 1）——
     * 于是所有**照抄原版两参写法**的调用（原版 `Tutorial.java:293`、`ItemLiquidGenerator.java:180`、
     * `NuclearReactor.java:157`）传入常量 `scl` 后**恒为 0**（脉动消失，例如核反应堆光源半径恒定 90）。
     * arc 的原意就是"两参版自动取 `Time.time`" → 这里对齐 arc。
     * <p>需要自定义时间轴时用三参版 {@link #absin(float, float, float)}。
     */
    public static float absin(float scl, float mag){
        return absin(Time.time, scl, mag);
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

    /** 四舍五入到 {@code step} 的整数倍（对应原版 {@code Mathf.round(float, int)}，T4 补）。 */
    public static int round(float value, int step){
        return (int)(Math.round(value / step) * step);
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
