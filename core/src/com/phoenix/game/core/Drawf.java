package com.phoenix.game.core;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.phoenix.game.math.Angles;

/**
 * 立即模式几何绘制（对应 arc 的 {@code arc.graphics.g2d.Drawf}）。
 *
 * <p>原版 arc 用一个共享 {@code Mesh} 直接写三角形顶点；libgdx 的 {@link com.badlogic.gdx.graphics.g2d.SpriteBatch}
 * 只能画带贴图的四边形，所以这里改用 {@link ShapeRenderer}。
 *
 * <p><b>绘制顺序</b>：几何与精灵混排时，靠「画几何前先 {@code batch.flush()}」保证批次里的精灵先落盘，
 * 几何再叠上去；反过来，{@link Draw#rect} 画精灵前会先 {@link #end()} 收尾几何会话，
 * 所以「精灵 → 几何 → 精灵」的先后关系严格成立。
 *
 * <p><b>会话</b>：连续画几何只开一次（同一帧内避免反复开关 GL 状态）；
 * {@link #end()} 关闭，{@link Draw#flush()} 与特效绘制结束处都会调。
 * 几何与精灵共用同一套 {@link Draw#color}/{@link Draw#alpha} 颜色状态。
 */
public class Drawf{

    private static ShapeRenderer shapes;
    /** 几何会话是否已开启（必须自己记账：{@code ShapeRenderer.begin} 重复调用会抛异常）。 */
    private static boolean open;
    private static ShapeRenderer.ShapeType type;

    /** 描边宽度（对应 arc 的 {@code Lines.stroke}）。 */
    private static float stroke = 1f;

    /** @return 几何会话是否开启（画精灵前据此先收尾）。 */
    public static boolean isOpen(){
        return open;
    }

    static void stroke(float width){
        stroke = width;
    }

    static float stroke(){
        return stroke;
    }

    /**
     * 确保几何会话以指定类型开启。
     * <p>首次开启时先 flush 精灵批次（顺序），并把相机矩阵同步给 ShapeRenderer。
     */
    static void begin(ShapeRenderer.ShapeType t){
        if(shapes == null){
            shapes = new ShapeRenderer(5000);
            //同帧内 Filled/Line 混排时自动切换（切换时才 flush，不是每个图元都 flush）
            shapes.setAutoShapeType(true);
        }

        if(!open){
            Core.batch.flush();
            shapes.setProjectionMatrix(Core.camera.combined);
            shapes.begin(t);
            open = true;
            type = t;
        }else if(type != t){
            shapes.set(t);
            type = t;
        }

        //libgdx 的 ImmediateModeRenderer20 没有容量检查，顶点写满会直接数组越界。
        //每个图元开始前留出一整个图元的余量（最大的圆 96 段 = 576 顶点），接近上限就先落盘。
        com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer renderer = shapes.getRenderer();
        if(renderer.getNumVertices() > renderer.getMaxVertices() - 1024){
            shapes.flush();
        }

        shapes.setColor(Draw.getColor());
    }

    /** 关闭几何会话：把已累积的三角形落盘。 */
    public static void end(){
        if(open){
            shapes.end();
            open = false;
            type = null;
        }
    }

    /** 同步当前 Draw 颜色（同一会话内画多个不同颜色的图元时用）。 */
    static void color(){
        shapes.setColor(Draw.getColor());
    }

    static ShapeRenderer renderer(){
        return shapes;
    }

    // ---------------- 公开原语 ----------------

    /**
     * 三角形：底边以 (x,y) 为中点、半宽 width，顶点沿 rotation 方向偏 height
     * （对应 arc 的 {@code Drawf.tri}，炮口闪光用的就是它）。
     */
    public static void tri(float x, float y, float width, float height, float rotation){
        float wx = Angles.trnsx(rotation + 90f, width);
        float wy = Angles.trnsy(rotation + 90f, width);
        tri(x - wx, y - wy, x + wx, y + wy,
            x + Angles.trnsx(rotation, height), y + Angles.trnsy(rotation, height));
    }

    /** 任意三角形（三个顶点）。 */
    public static void tri(float x1, float y1, float x2, float y2, float x3, float y3){
        begin(ShapeRenderer.ShapeType.Filled);
        shapes.triangle(x1, y1, x2, y2, x3, y3);
    }

    /** 一条直线，线宽取当前 stroke。 */
    public static void line(float x, float y, float x2, float y2){
        Lines.line(x, y, x2, y2);
    }
}
