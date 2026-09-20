package com.phoenix.game.core;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;

/**
 * 填充图元（对应 arc 的 {@code arc.graphics.g2d.Fill}）。
 *
 * <p>原版靠 {@code Mesh} 直接写三角形；这里用 {@link Drawf} 的 ShapeRenderer 会话实现。
 * 颜色沿用 {@link Draw#color}/{@link Draw#alpha} 设置的批次颜色，与精灵绘制共用一套状态。
 */
public class Fill{

    /** 实心圆。 */
    public static void circle(float x, float y, float radius){
        Drawf.begin(ShapeRenderer.ShapeType.Filled);
        Drawf.renderer().circle(x, y, radius, Mathf.clamp((int)(radius * 2f) + 4, 8, 64));
    }

    /** 实心圆，指定分段数。 */
    public static void circle(float x, float y, float radius, int segments){
        Drawf.begin(ShapeRenderer.ShapeType.Filled);
        Drawf.renderer().circle(x, y, radius, Math.max(segments, 3));
    }

    /** 实心正方形，size 为边长。 */
    public static void square(float x, float y, float size){
        square(x, y, size, 0f);
    }

    /** 实心正方形，可绕中心旋转。 */
    public static void square(float x, float y, float size, float rotation){
        rect(x, y, size, size, rotation);
    }

    /** 实心矩形，可绕中心旋转。 */
    public static void rect(float x, float y, float width, float height, float rotation){
        Drawf.begin(ShapeRenderer.ShapeType.Filled);
        Drawf.renderer().rect(x - width / 2f, y - height / 2f, width / 2f, height / 2f,
            width, height, 1f, 1f, rotation);
    }

    /** 实心矩形，轴对齐。 */
    public static void rect(float x, float y, float width, float height){
        Drawf.begin(ShapeRenderer.ShapeType.Filled);
        Drawf.renderer().rect(x - width / 2f, y - height / 2f, width, height);
    }

    /**
     * 三角形：底边以 (x,y) 为中点、半宽 width，顶点沿 rotation 方向偏 height
     * （对应 arc 的 {@code Fill.tri}）。
     */
    public static void tri(float x, float y, float width, float height, float rotation){
        Drawf.begin(ShapeRenderer.ShapeType.Filled);
        float wx = Angles.trnsx(rotation + 90f, width);
        float wy = Angles.trnsy(rotation + 90f, width);
        Drawf.renderer().triangle(x - wx, y - wy, x + wx, y + wy,
            x + Angles.trnsx(rotation, height), y + Angles.trnsy(rotation, height));
    }

    /** 任意三角形（三个顶点）。 */
    public static void tri(float x1, float y1, float x2, float y2, float x3, float y3){
        Drawf.begin(ShapeRenderer.ShapeType.Filled);
        Drawf.renderer().triangle(x1, y1, x2, y2, x3, y3);
    }

    /** 实心正多边形（按圆周均匀取点）。 */
    public static void poly(float x, float y, int sides, float radius){
        poly(x, y, sides, radius, 0f);
    }

    public static void poly(float x, float y, int sides, float radius, float rotation){
        if(sides < 3) sides = 3;
        Drawf.begin(ShapeRenderer.ShapeType.Filled);
        float step = 360f / sides;
        for(int i = 0; i < sides; i++){
            Drawf.renderer().triangle(x, y,
                x + Angles.trnsx(rotation + i * step, radius), y + Angles.trnsy(rotation + i * step, radius),
                x + Angles.trnsx(rotation + (i + 1) * step, radius), y + Angles.trnsy(rotation + (i + 1) * step, radius));
        }
    }
}
