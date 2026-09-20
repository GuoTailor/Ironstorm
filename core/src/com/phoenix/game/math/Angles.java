package com.phoenix.game.math;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * create by GYH on 2024/12/25
 */

public class Angles {
    private static final RandomXS128 random = new RandomXS128();
    private static final Vector2 rv = new Vector2();

    public Angles() {
    }

    public static float forwardDistance(float angle1, float angle2) {
        return angle1 > angle2 ? angle1 - angle2 : angle2 - angle1;
    }

    public static float backwardDistance(float angle1, float angle2) {
        return 360.0F - forwardDistance(angle1, angle2);
    }

    public static boolean within(float a, float b, float margin) {
        return angleDist(a, b) <= margin;
    }

    public static float angleDist(float a, float b) {
        return Math.min(a - b < 0.0F ? a - b + 360.0F : a - b, b - a < 0.0F ? b - a + 360.0F : b - a);
    }

    public static boolean near(float a, float b, float range) {
        return angleDist(a, b) < range;
    }

    public static float moveToward(float angle, float to, float speed) {
        if (Math.abs(angleDist(angle, to)) < speed) {
            return to;
        } else {
            angle = Mathf.mod(angle, 360.0F);
            to = Mathf.mod(to, 360.0F);
            if ((!(angle > to) || !(backwardDistance(angle, to) > forwardDistance(angle, to))) && (!(angle < to) || !(backwardDistance(angle, to) < forwardDistance(angle, to)))) {
                angle += speed;
            } else {
                angle -= speed;
            }

            return angle;
        }
    }

    /**
     * 计算从 (x,y) 指向 (x2,y2) 的角度（弧度制标准约定：0=+X 轴，逆时针为正）。
     * 注意：arc 的 Mathf.atan2(x, y) = libgdx 的 MathUtils.atan2(y, x)，参数是反的，
     * 这里必须传 (y2-y, x2-x)，否则角度会整体偏 90 度。
     */
    public static float angle(float x, float y, float x2, float y2) {
        float ang = MathUtils.atan2(y2 - y, x2 - x) * (180F / (float)Math.PI);
        if (ang < 0.0F) {
            ang += 360.0F;
        }

        return ang;
    }

    public static float trnsx(float angle, float len) {
        return len * MathUtils.cos(((float)Math.PI / 180F) * angle);
    }

    public static float trnsy(float angle, float len) {
        return len * MathUtils.sin(((float)Math.PI / 180F) * angle);
    }

    public static float trnsx(float angle, float x, float y) {
        return rv.set(x, y).rotateDeg(angle).x;
    }

    public static float trnsy(float angle, float x, float y) {
        return rv.set(x, y).rotateDeg(angle).y;
    }

    public static void loop(int max, Consumer<Integer> i) {
        for(int j = 0; j < max; ++j) {
            i.accept(j);
        }

    }

    public static void circle(int points, float offset, Consumer<Float> cons) {
        for(int i = 0; i < points; ++i) {
            cons.accept(offset + (float)i * 360.0F / (float)points);
        }

    }

    public static void circle(int points, Consumer<Float> cons) {
        for(int i = 0; i < points; ++i) {
            cons.accept((float)i * 360.0F / (float)points);
        }

    }

    public static void circleVectors(int points, float length, BiConsumer<Float, Float> pos) {
        for(int i = 0; i < points; ++i) {
            float f = (float)i * 360.0F / (float)points;
            pos.accept(trnsx(f, length), trnsy(f, length));
        }

    }

    public static void circleVectors(int points, float length, float offset, BiConsumer<Float, Float> pos) {
        for(int i = 0; i < points; ++i) {
            float f = (float)i * 360.0F / (float)points + offset;
            pos.accept(trnsx(f, length), trnsy(f, length));
        }

    }

    public static void shotgun(int points, float spacing, float offset, Consumer<Float> cons) {
        for(int i = 0; i < points; ++i) {
            cons.accept((float)i * spacing - (float)(points - 1) * spacing / 2.0F + offset);
        }

    }

    public static void randVectors(long seed, int amount, float length, BiConsumer<Float, Float> cons) {
        random.setSeed(seed);

        for(int i = 0; i < amount; ++i) {
            float vang = random.nextFloat() * 360.0F;
            rv.set(length, 0.0F).rotateDeg(vang);
            cons.accept(rv.x, rv.y);
        }

    }

    public static void randLenVectors(long seed, int amount, float length, BiConsumer<Float, Float> cons) {
        random.setSeed(seed);

        for(int i = 0; i < amount; ++i) {
            float scl = length * random.nextFloat();
            float vang = random.nextFloat() * 360.0F;
            rv.set(scl, 0.0F).rotateDeg(vang);
            cons.accept(rv.x, rv.y);
        }

    }

    public static void randLenVectors(long seed, int amount, float length, float angle, float range, BiConsumer<Float, Float> cons) {
        random.setSeed(seed);

        for(int i = 0; i < amount; ++i) {
            float scl = length * random.nextFloat();
            float vang = angle + random.nextFloat() * range * 2.0F - range;
            rv.set(scl, 0.0F).rotateDeg(vang);
            cons.accept(rv.x, rv.y);
        }

    }

    public static void randLenVectors(long seed, float fin, int amount, float length, ParticleConsumer cons) {
        random.setSeed(seed);

        for(int i = 0; i < amount; ++i) {
            float l = random.nextFloat();
            float scl = length * l * fin;
            float vang = random.nextFloat() * 360.0F;
            rv.set(scl, 0.0F).rotateDeg(vang);
            cons.accept(rv.x, rv.y, fin * l, (1.0F - fin) * l);
        }

    }

    public static void randLenVectors(long seed, float fin, int amount, float length, float angle, float range, ParticleConsumer cons) {
        random.setSeed(seed);

        for(int i = 0; i < amount; ++i) {
            float scl = length * random.nextFloat() * fin;
            float vang = angle + random.nextFloat() * range * 2.0F - range;
            rv.set(scl, 0.0F).rotateDeg(vang);
            cons.accept(rv.x, rv.y, fin * random.nextFloat(), 0.0F);
        }

    }

    public interface ParticleConsumer {
        void accept(float var1, float var2, float var3, float var4);
    }
}
