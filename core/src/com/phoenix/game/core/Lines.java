package com.phoenix.game.core;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;

/**
 * 描边图元（对应 arc 的 {@code arc.graphics.g2d.Lines}）。
 *
 * <p>原版靠 {@code Mesh} 直接写三角形；这里用 {@link Drawf} 的 ShapeRenderer 会话实现。
 * 线宽不用 {@code glLineWidth}（现代 GL 核心只有 1px），而是把每条线段画成一根有宽度的四边形，
 * 所以 {@link #stroke(float)} 的宽度在任意粗细下都真实生效。
 *
 * <p>颜色沿用 {@link Draw#color}/{@link Draw#alpha} 设置的批次颜色，与精灵绘制共用一套状态。
 */
public class Lines{

    /** 设置后续描边的线宽（对应 arc 的 {@code Lines.stroke}）。 */
    public static void stroke(float width){
        Drawf.stroke(width);
    }

    /** 画一条线段。 */
    public static void line(float x, float y, float x2, float y2){
        Drawf.begin(ShapeRenderer.ShapeType.Filled);
        Drawf.renderer().rectLine(x, y, x2, y2, Drawf.stroke());
    }

    /** 从 (x,y) 沿 angle 方向画一条长 len 的线段。 */
    public static void lineAngle(float x, float y, float angle, float len){
        line(x, y, x + Angles.trnsx(angle, len), y + Angles.trnsy(angle, len));
    }

    /** 以 (x,y) 为中心、长 len 的线段（对应 arc 的 {@code Lines.lineAngleCenter}）。 */
    public static void lineAngleCenter(float x, float y, float angle, float len){
        float hx = Angles.trnsx(angle, len / 2f), hy = Angles.trnsy(angle, len / 2f);
        line(x - hx, y - hy, x + hx, y + hy);
    }

    /** 空心圆。 */
    public static void circle(float x, float y, float radius){
        circle(x, y, radius, segments(radius));
    }

    /** 空心圆，指定分段数。 */
    public static void circle(float x, float y, float radius, int segments){
        if(segments < 3) segments = 3;
        float step = 360f / segments;
        for(int i = 0; i < segments; i++){
            float a1 = i * step, a2 = (i + 1) * step;
            line(x + Angles.trnsx(a1, radius), y + Angles.trnsy(a1, radius),
                 x + Angles.trnsx(a2, radius), y + Angles.trnsy(a2, radius));
        }
    }

    /** 圆弧（start 起、扫过 degrees 度）。 */
    public static void arc(float x, float y, float radius, float start, float degrees){
        arc(x, y, radius, start, degrees, segments(radius));
    }

    public static void arc(float x, float y, float radius, float start, float degrees, int segments){
        if(segments < 2) segments = 2;
        float step = degrees / segments;
        for(int i = 0; i < segments; i++){
            float a1 = start + i * step, a2 = start + (i + 1) * step;
            line(x + Angles.trnsx(a1, radius), y + Angles.trnsy(a1, radius),
                 x + Angles.trnsx(a2, radius), y + Angles.trnsy(a2, radius));
        }
    }

    /** 空心正方形，radius 为半边长。 */
    public static void square(float x, float y, float radius){
        square(x, y, radius, 0f);
    }

    /** 空心正方形，可绕中心旋转。 */
    public static void square(float x, float y, float radius, float rotation){
        float[] cx = new float[4], cy = new float[4];
        for(int i = 0; i < 4; i++){
            float a = i * 90f + 45f + rotation;
            cx[i] = x + Angles.trnsx(a, radius * 1.4142135f);
            cy[i] = y + Angles.trnsy(a, radius * 1.4142135f);
        }
        for(int i = 0; i < 4; i++){
            int j = (i + 1) % 4;
            line(cx[i], cy[i], cx[j], cy[j]);
        }
    }

    /** 空心正多边形。 */
    public static void poly(float x, float y, int sides, float radius){
        poly(x, y, sides, radius, 0f);
    }

    /** 空心正多边形，可旋转。 */
    public static void poly(float x, float y, int sides, float radius, float rotation){
        if(sides < 3) sides = 3;
        for(int i = 0; i < sides; i++){
            float a1 = rotation + i * 360f / sides, a2 = rotation + (i + 1) * 360f / sides;
            line(x + Angles.trnsx(a1, radius), y + Angles.trnsy(a1, radius),
                 x + Angles.trnsx(a2, radius), y + Angles.trnsy(a2, radius));
        }
    }

    /** 沿圆周向外的一圈尖刺（对应 arc 的 {@code Lines.spikes}）。 */
    public static void spikes(float x, float y, float radius, float length, int spikes){
        spikes(x, y, radius, length, spikes, 0f);
    }

    public static void spikes(float x, float y, float radius, float length, int spikes, float offset){
        if(spikes < 1) spikes = 1;
        for(int i = 0; i < spikes; i++){
            float a = offset + i * 360f / spikes;
            line(x + Angles.trnsx(a, radius), y + Angles.trnsy(a, radius),
                 x + Angles.trnsx(a, radius + length), y + Angles.trnsy(a, radius + length));
        }
    }

    /** @return 让圆看起来足够圆滑的分段数（半径越大分得越细，但有上限）。 */
    private static int segments(float radius){
        return Mathf.clamp((int)(radius * 1.5f) + 8, 10, 96);
    }
}
