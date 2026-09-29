package com.phoenix.game.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.phoenix.game.Vars;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;

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
            //⚠ 用**批次当前**的投影矩阵而不是 Core.camera.combined：arc 的几何绘制同样取
            //`Draw.proj()`（= 批次投影），两者在正常世界绘制路径下是同一个矩阵。区别出现在
            //"临时把批次投影切成别的正交矩阵"的场合 —— BlockRenderer 烘焙 1 像素 1 格的全图缓冲
            //就是先 `Draw.proj().setToOrtho2D(...)` 再用 Fill 画格子，写死 camera.combined 会画错。
            shapes.setProjectionMatrix(Core.batch.getProjectionMatrix());
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

            //⚠⚠ 必须把 SpriteBatch 的 program 抢回来！
            //libgdx 的 {@code SpriteBatch.flush()} **不会** 重新 {@code glUseProgram}：
            //它只调 {@code Mesh.render(shader,...)} → {@code bind(shader)}，而 {@code Mesh.bind} 只重绑顶点属性
            //（见 libgdx 源码 Mesh.bind/render），program 的绑定完全依赖 {@code begin()} / {@code setShader()} 那两次。
            //而 {@code ShapeRenderer.end()} → {@code ImmediateModeRenderer20.flush()} 会 {@code shader.bind()}
            //（= {@code glUseProgram(几何着色器)}）并把它留在 GL 上 → 之后所有精灵批次都会**用几何着色器绘制**
            //（几何着色器只输出顶点色、不采样贴图 → 整屏画成纯色/花屏）。
            //症状实例：UnitFactory 的建造扫描线（Lines）之后，整个世界层被一个错配的全屏四边形覆盖。
            com.badlogic.gdx.graphics.glutils.ShaderProgram batchShader = Core.batch.getShader();
            if(batchShader != null) batchShader.bind();
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

    /**
     * 双层描边虚线圆（对应原版 {@code Drawf.dashCircle}）：先 3f 深灰描边、再 1f 本色叠画，
     * 于是圈上有一条深灰"外发光"。炮塔射程圈用的就是它。
     * <p>差异：原版结尾是 {@code Draw.reset()}，本项目没有该重载，按既有约定用 {@link Draw#color()} 复位到白色
     * （见 {@code Blocks} / {@code ItemTransfer} 的同类处理）。
     */
    public static void dashCircle(float x, float y, float rad, Color color){
        Lines.stroke(3f, Pal.gray);
        Lines.dashCircle(x, y, rad);
        Lines.stroke(1f, color);
        Lines.dashCircle(x, y, rad);
        Draw.color();
    }

    /** 双层描边实心圆（对应原版 {@code Drawf.circles}），本色取 {@link Pal#accent}。 */
    public static void circles(float x, float y, float rad){
        circles(x, y, rad, Pal.accent);
    }

    /** 双层描边实心圆：3f 深灰 + 1f 本色（对应原版 {@code Drawf.circles}）。 */
    public static void circles(float x, float y, float rad, Color color){
        Lines.stroke(3f, Pal.gray);
        Lines.circle(x, y, rad);
        Lines.stroke(1f, color);
        Lines.circle(x, y, rad);
        Draw.color();
    }

    /** 双层描边方框（对应原版 {@code Drawf.square}），本色取 {@link Pal#accent}。 */
    public static void square(float x, float y, float radius){
        square(x, y, radius, Pal.accent);
    }

    /**
     * 双层描边方框：3f 深灰 + 1f 本色（对应原版 {@code Drawf.square}）。
     * <p>{@code radius + 1f}（外扩 1）与 45° 都照抄原版；45° 叠加本项目 {@link Lines#square} 的角点约定后
     * 画出的是顶点朝上/下/左/右的菱形，朝向与原版一致。
     * <p>⚠ 尺寸差异：arc 的 {@code Lines.square(x,y,rad,rot)} 把 rad 当**外接圆半径**，
     * 本项目把它当**半边长**（与 arc 的 3 参版本语义对齐），所以同一个 rad 这里画出的菱形大 √2 倍。
     * 要完全复刻原版大小需要传 {@code (radius + 1f) / 1.4142135f}——为保持与项目其它 {@code Lines.square}
     * 调用点同一套口径，这里按任务要求原样传 {@code radius + 1f}。
     */
    public static void square(float x, float y, float radius, Color color){
        Lines.stroke(3f, Pal.gray);
        Lines.square(x, y, radius + 1f, 45f);
        Lines.stroke(1f, color);
        Lines.square(x, y, radius + 1f, 45f);
        Draw.color();
    }

    /** 指向型箭头（对应原版 {@code Drawf.arrow}），本色取 {@link Pal#accent}。 */
    public static void arrow(float x, float y, float x2, float y2, float length, float radius){
        arrow(x, y, x2, y2, length, radius, Pal.accent);
    }

    /**
     * 从 (x,y) 指向 (x2,y2) 的箭头（对应原版 {@code Drawf.arrow}）：
     * 三角尖端落在距起点 length 处（超过 length 的远端点会被 {@code limit} 截断），
     * 灰色大三角 + 本色小三角叠出描边效果。
     */
    public static void arrow(float x, float y, float x2, float y2, float length, float radius, Color color){
        float angle = Angles.angle(x, y, x2, y2);
        float space = 2f;
        Tmp.v1.set(x2, y2).sub(x, y).limit(length);
        float vx = Tmp.v1.x + x, vy = Tmp.v1.y + y;

        Draw.color(Pal.gray);
        Fill.poly(vx, vy, 3, radius + space, angle);
        Draw.color(color);
        Fill.poly(vx, vy, 3, radius, angle);
        Draw.color();
    }

    public static void laser(TextureRegion line, TextureRegion edge, float x, float y, float x2, float y2, float scale){
        laser(line, edge, x, y, x2, y2, Mathf.angle(x2 - x, y2 - y), scale);
    }

    public static void laser(TextureRegion line, TextureRegion edge, float x, float y, float x2, float y2){
        laser(line, edge, x, y, x2, y2, Mathf.angle(x2 - x, y2 - y), 1f);
    }

    /**
     * 激光束（对应原版 {@code Drawf.laser}）：两端各画一段"光晕"贴图（edge），中间拉一条 12f * scale 粗的线。
     * <p><b>与原版的差异（刻意降级）</b>：
     * <ul>
     *   <li>原版中段是 {@code Lines.line(line, ..., CapStyle.none, 0f)}（带贴图/线帽的重载）+ {@code Lines.precise}，
     *       本项目 {@code Lines} 没有这些 API，改用 {@code Lines.stroke + Lines.line} 画纯色中段
     *       （几何与端点位置照抄：两端各内缩 8f * scale * Draw.scl）。
     *       因此参数 {@code line}（中段贴图）在本实现里用不上，保留它只为让调用点的签名与原版一致。</li>
     * </ul>
     * <p>末尾的 {@code renderer.lights.line(x, y, x2, y2)}（原版 Drawf.java:82）已接线（S5b 批次）：
     * 见 {@code graphics/LightRenderer#line}（本项目改用 libgdx 的顶点提交重载）。
     */
    public static void laser(TextureRegion line, TextureRegion edge, float x, float y, float x2, float y2, float rotation, float scale){
        //原版 Tmp.v1.trns(rotation, 8f * scale * Draw.scl)；本项目 Tmp.v1 是 libgdx 的 Vector2（没有 trns 方法），直接算偏移
        float ox = Angles.trnsx(rotation, 8f * scale * Draw.scl);
        float oy = Angles.trnsy(rotation, 8f * scale * Draw.scl);

        Draw.rect(edge, x, y, edge.getRegionWidth() * scale * Draw.scl, edge.getRegionHeight() * scale * Draw.scl, rotation + 180);
        Draw.rect(edge, x2, y2, edge.getRegionWidth() * scale * Draw.scl, edge.getRegionHeight() * scale * Draw.scl, rotation);

        Lines.stroke(12f * scale);
        Lines.line(x + ox, y + oy, x2 - ox, y2 - oy);
        Lines.stroke(1f);

        //光束带光源（对应原版 Drawf.java:82；LightRenderer.enabled() 内部已判，renderer 可能为 null 时跳过）
        if(Vars.renderer != null){
            Vars.renderer.lights.line(x, y, x2, y2);
        }
    }
}
