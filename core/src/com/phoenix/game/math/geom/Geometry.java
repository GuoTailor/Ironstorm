package com.phoenix.game.math.geom;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.phoenix.game.math.Mathf;

/**
 * create by GYH on 2025/10/21
 */
public final class Geometry {
    private static final Vector2 tmp1 = new Vector2(), tmp2 = new Vector2(), tmp3 = new Vector2();

    /** 四方向（上下左右），用于流场四邻域扩散。参照 arc Geometry.d4。 */
    public static final GridPoint2[] d4 = {
            new GridPoint2(0, 1), new GridPoint2(1, 0), new GridPoint2(0, -1), new GridPoint2(-1, 0)
    };
    /** 八方向（含对角），用于寻路选格。参照 arc Geometry.d8。 */
    public static final GridPoint2[] d8 = {
            new GridPoint2(0, 1), new GridPoint2(1, 1), new GridPoint2(1, 0), new GridPoint2(1, -1),
            new GridPoint2(0, -1), new GridPoint2(-1, -1), new GridPoint2(-1, 0), new GridPoint2(-1, 1)
    };
    /**
     * @return (x, y) 是否落在以 (cx, cy) 为中心、d 为"水平外接半宽"的正六边形内
     * （六边形顶点朝左右，即 {@code Fill.poly(x, y, 6, d/2)} 画出来的那个形状）。
     * <p>逐字照抄 arc {@code Intersector.isInsideHexagon}（{@code Arc/arc-core/src/arc/math/geom/Intersector.java:107-113}），
     * 原版力场投影器（{@code ForceProjector.shieldConsumer}）用 d = {@code realRadius * 2f} 判定护盾覆盖范围。
     * <p>⚠ 唯一差异：arc 的 {@code Mathf.sqrt3} 常量本项目没有 → 就地用 {@code Mathf.sqrt(3f)} 等价替换。
     */
    public static boolean isInsideHexagon(float cx, float cy, float d, float x, float y){
        float dx = Math.abs(x - cx) / d;
        float dy = Math.abs(y - cy) / d;
        float a = 0.25f * Mathf.sqrt(3f);
        return (dy <= a) && (a * dx + 0.25 * dy <= 0.5 * a);
    }

    /**
     * Checks for collisions between two rectangles, and returns the correct delta vector of A.
     * Note: The same vector instance is returned each time!
     */
    public static Vector2 overlap(Rectangle a, Rectangle b, boolean x){
        float penetration = 0f;

        float ax = a.x + a.width / 2, bx = b.x + b.width / 2;
        float ay = a.y + a.height / 2, by = b.y + b.height / 2;

        //Vector from A to B
        float nx = ax - bx,
                ny = ay - by;

        // Calculate half extends along x axis
        float aex = a.width / 2,
                bex = b.width / 2;

        // Overlap on x axis
        float xoverlap = aex + bex - Math.abs(nx);
        if(Math.abs(xoverlap) > 0){

            // Calculate half extends along y axis
            float aey = a.height / 2,
                    bey = b.height / 2;

            // Overlap on x axis
            float yoverlap = aey + bey - Math.abs(ny);
            if(Math.abs(yoverlap) > 0){

                // Find out which axis is the axis of least penetration
                if(Math.abs(xoverlap) < Math.abs(yoverlap)){
                    // Point towards B knowing that n points from A to B
                    tmp1.x = nx < 0 ? 1 : -1;
                    tmp1.y = 0;
                    penetration = xoverlap;
                }else{
                    // Point towards B knowing that n points from A to B
                    tmp1.x = 0;
                    tmp1.y = ny < 0 ? 1 : -1;
                    penetration = yoverlap;
                }

            }
        }

        float m = Math.max(penetration, 0.0f);

        // Apply correctional impulse
        float cx = m * tmp1.x,
                cy = m * tmp1.y;

        tmp1.x = -cx;
        tmp1.y = -cy;

        return tmp1;
    }
}
