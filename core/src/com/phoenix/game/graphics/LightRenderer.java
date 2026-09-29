package com.phoenix.game.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.Position;

/**
 * 灯光叠加层渲染器。参照 Mindustry {@code mindustry.graphics.LightRenderer} 移植（仅客户端）。
 *
 * <p>工作方式：发光方块/单位/子弹在收集阶段把自己登记成一个 {@link Runnable}（"在某处画一个
 * {@code circle-shadow} 圆形光斑"），到 {@link #draw()} 时统一渲染进一张
 * <b>1/{@link #scaling} 屏幕分辨率</b>的 {@link FrameBuffer}（RGB 累加、ALPHA 取最大），
 * 再用 {@link Shaders#light} 把这张"光照图"叠回屏幕：
 * 暗处的 alpha 高（按环境光把场景压暗）、亮处的 alpha 低（把场景提亮成光源颜色）。
 *
 * <p><b>{@code line(...)}（S5b 批次已补）</b>：原版用它画"激光须条"式光束带（唯一调用点是
 * {@code graphics/Drawf.java:82} 的 {@code laser}）。两处 API 差异：
 * <ul>
 *   <li>原版靠 arc 的 {@code Draw.vert(Texture, float[], int, int)} 直接喂顶点数组 →
 *       本项目改用 libgdx 原生的 {@code Core.batch.draw(Texture, float[], int, int)}（等价物）；</li>
 *   <li>⚠ <b>顶点步长不同</b>：arc 的 SpriteBatch 顶点是 <b>6 个 float</b>/顶点
 *       （x, y, color, u, v, <b>mixColor</b>），原版因此用 {@code float[24]}（4 顶点 × 6）；
 *       libgdx 的 SpriteBatch 只有 <b>5 个 float</b>/顶点（x, y, color, u, v，没有 mixColor）→
 *       这里改用 {@code float[20]} 并按 5 步长排布（原版那个第 6 位恒为 0，丢掉即等价）。</li>
 * </ul>
 * 贴图用 {@code circle-end}/{@code circle-mid}；本项目 {@code findRegion} 缺图返回 null → 缺图直接跳过。
 *
 * <p><b>与 arc/libgdx 的差异</b>：
 * <ul>
 *   <li>原版字段初始化就 {@code new FrameBuffer(2, 2)}；本项目 {@code Renderer} 是 {@code ClientLauncher}
 *       的字段（构造早于 GL 上下文就绪）→ 只能懒建（{@link #draw()} 里按需建/重建）。</li>
 *   <li>libgdx 1.14.2 的 {@code GLFrameBuffer} 没有 {@code resize(w,h)} → 尺寸变化只能 dispose 后重建
 *       （同 {@link BlockRenderer}）。</li>
 *   <li>arc 的 {@code FrameBuffer.begin()/end()} 内部会 {@code Draw.flush()}；libgdx 的不会 →
 *       进 FBO 前、出 FBO 前都必须自己 {@code Draw.flush()}，否则排队的精灵会落到错误的帧缓冲。</li>
 *   <li>本项目 {@code Draw} 没有 {@code Draw.reset()} 与 {@code Draw.rect(String, ...)} 重载 →
 *       复位用 {@code Draw.color()}，取图用 {@code Core.atlas.findRegion("circle-shadow")}（缓存）。</li>
 *   <li>libgdx 的 {@code GL20} 没有 {@code GL_MAX} 常量（在 {@code GL30} 里）→
 *       {@code Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_ADD, GL30.GL_MAX)}。</li>
 *   <li>本项目 {@code tile.worldx()/worldy()} 是<b>瓦片角点</b>语义（原版是中心）→ 调用方一律用
 *       {@code Block.centerX/centerY} 传坐标，本类只认传入的世界坐标。</li>
 * </ul>
 */
public class LightRenderer implements Disposable{
    /** 灯光缓冲相对屏幕的降采样倍率（对应原版 {@code LightRenderer.scaling}）。 */
    private static final int scaling = 4;

    /** 灯光缓冲（懒建；窗口尺寸变化时 dispose 后重建）。 */
    private FrameBuffer buffer;

    /** 本帧收集到的光源绘制任务（对应原版 {@code lights}）。 */
    private final Array<Runnable> lights = new Array<>();

    /** 缓存的 {@code circle-shadow} 区域（本项目 {@code Draw} 没有 {@code rect(String,...)} 重载 → 不能每帧 findRegion）。 */
    private TextureRegion circleShadow;

    /** {@code line()} 用的 {@code circle-end}/{@code circle-mid} 区域（同样懒取 + 缓存；缺图返回 null）。 */
    private TextureRegion circleEnd, circleMid;

    /**
     * {@code line()} 的顶点暂存数组（对应原版 {@code LightRenderer.vertices}）。
     * <p>⚠ 原版是 {@code float[24]}（arc 顶点 6 float：x,y,color,u,v,mixColor）；
     * libgdx 的 SpriteBatch 顶点只有 5 float → 这里是 {@code float[20]}（4 顶点 × 5）。
     */
    private final float[] vertices = new float[20];

    /** 登记一个自定义绘制任务（对应原版 {@code add(Runnable)}）。 */
    public void add(Runnable run){
        if(!enabled()) return;

        lights.add(run);
    }

    /** 以位置对象登记圆形光斑（对应原版 {@code add(Position, ...)}）。 */
    public void add(Position pos, float radius, Color color, float opacity){
        add(pos.getX(), pos.getY(), radius, color, opacity);
    }

    /** 在世界坐标 {@code (x, y)} 登记一个直径 {@code radius*2} 的圆形光斑（对应原版 {@code add(float,...)}）。 */
    public void add(float x, float y, float radius, Color color, float opacity){
        if(!enabled()) return;

        //原版是 Draw.rect("circle-shadow", ...)；本项目没有按名字取图的 rect 重载 → 先取区域（缺图则跳过）
        TextureRegion region = circleShadow();
        if(region == null) return;


        float res = color.toFloatBits();
        add(() -> {
            Draw.color(res);
            Draw.alpha(opacity);
            Draw.rect(region, x, y, radius * 2f, radius * 2f);
        });
    }

    /** 在世界坐标 {@code (x, y)} 登记一个自定义区域的光斑（对应原版 {@code add(float, float, TextureRegion, ...)}）。 */
    public void add(float x, float y, TextureRegion region, Color color, float opacity){
        if(!enabled()) return;
        if(region == null) return;

        float res = color.toFloatBits();
        add(() -> {
            Draw.color(res);
            Draw.alpha(opacity);
            Draw.rect(region, x, y);
        });
    }

    /**
     * 登记一条"光束带"光源（对应原版 {@code LightRenderer.line(float,float,float,float)}，见原版
     * LightRenderer.java:53-174）：中段用 {@code circle-mid} 拉一条 30f 半宽的带子，两端各贴一张
     * {@code circle-end} 端帽（端帽沿光束方向再外推 stroke）。
     *
     * <p>⚠ 与本项目 API 的对应关系（逐个核对过）：
     * <ul>
     *   <li>{@code Draw.color(Color, float)} / {@code Draw.getColor()} —— 本项目都有（转发到 batch），照抄；</li>
     *   <li>{@code Mathf.angleExact(x, y)} → 本项目 {@link Mathf#angle(float, float)}（atan2，度）；</li>
     *   <li>{@code Tmp.v1.trnsExact(rot, len)} → 本项目 {@link Angles#trnsx}/{@link Angles#trnsy}
     *       （{@code Tmp.v1} 是 libgdx 的 {@code Vector2}，没有 {@code trns} 方法）；</li>
     *   <li>{@code Draw.vert(Texture, float[], int, int)} → {@code Core.batch.draw(Texture, float[], int, int)}
     *       （libgdx 原生重载，语义就是"直接提交顶点数组"）；</li>
     *   <li>⚠ 顶点步长 6 → 5（见 {@link #vertices} 说明）；</li>
     *   <li>⚠ {@code Color.orange}：arc 是 {@code 0xffa500}，libgdx 的 {@code Color.ORANGE} 是 {@code 0xff7f00}
     *       （本项目 {@code graphics/Colors} 就是把 "orange" 映射到 {@code Color.ORANGE}）→ 沿用本项目映射。</li>
     * </ul>
     */
    public void line(float x, float y, float x2, float y2){
        if(!enabled()) return;

        //原版 Core.atlas.find(...) 缺图返回 error 占位图；本项目 findRegion 返回 null → 缺任一张就整条跳过
        TextureRegion ledge = circleEnd(), lmid = circleMid();
        if(ledge == null || lmid == null) return;

        add(() -> {
            Draw.color(Color.ORANGE, 0.3f);

            float stroke = 30f;
            float rot = Mathf.angle(x2 - x, y2 - y);

            float color = Draw.getColor().toFloatBits();
            float u = lmid.getU(), v = lmid.getV2(), u2 = lmid.getU2(), v2 = lmid.getV();

            //原版 Tmp.v1.trnsExact(rot + 90f, stroke)：垂直于光束的半宽偏移
            float v1x = Angles.trnsx(rot + 90f, stroke), v1y = Angles.trnsy(rot + 90f, stroke);
            float lx1 = x - v1x, ly1 = y - v1y,
                  lx2 = x + v1x, ly2 = y + v1y,
                  lx3 = x2 + v1x, ly3 = y2 + v1y,
                  lx4 = x2 - v1x, ly4 = y2 - v1y;

            //中段（circle-mid）
            quad(lmid.getTexture(), lx1, ly1, lx2, ly2, lx3, ly3, lx4, ly4, u, v, u2, v2, color);

            //原版 Tmp.v2.trnsExact(rot, stroke)：沿光束方向外推端帽
            float v3x = Angles.trnsx(rot, stroke), v3y = Angles.trnsy(rot, stroke);

            u = ledge.getU(); v = ledge.getV2(); u2 = ledge.getU2(); v2 = ledge.getV();

            //起点端帽（circle-end）
            quad(ledge.getTexture(), lx4, ly4, lx3, ly3, lx3 + v3x, ly3 + v3y, lx4 + v3x, ly4 + v3y,
                u, v, u2, v2, color);
            //终点端帽
            quad(ledge.getTexture(), lx2, ly2, lx1, ly1, lx1 - v3x, ly1 - v3y, lx2 - v3x, ly2 - v3y,
                u, v, u2, v2, color);
        });
    }

    /**
     * 用一个 quad 的 4 个角 + 区域坐标填满 {@link #vertices} 并提交（对应原版重复三次的
     * {@code vertices[...] = ...} + {@code Draw.vert(...)}）。
     * <p>顶点布局 = libgdx SpriteBatch 的 5 float/顶点：{@code x, y, color, u, v}。
     */
    private void quad(Texture texture, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
                      float u, float v, float u2, float v2, float color){
        vertices[0] = x1;  vertices[1] = y1;  vertices[2] = color; vertices[3] = u;  vertices[4] = v;
        vertices[5] = x2;  vertices[6] = y2;  vertices[7] = color; vertices[8] = u;  vertices[9] = v2;
        vertices[10] = x3; vertices[11] = y3; vertices[12] = color; vertices[13] = u2; vertices[14] = v2;
        vertices[15] = x4; vertices[16] = y4; vertices[17] = color; vertices[18] = u2; vertices[19] = v;

        //本项目没有 arc 的 Draw.vert → 用 libgdx 原生的"提交顶点数组"重载（等价物）
        Core.batch.draw(texture, vertices, 0, vertices.length);
    }

    /**
     * @return 是否开启全局光照（对应原版 {@code state.rules.lighting}）。
     * <p>本项目加守卫：菜单/服务端 {@code Vars.state} 或 {@code rules} 可能为 null。
     */
    public boolean enabled(){
        return Vars.state != null && Vars.state.rules != null && Vars.state.rules.lighting;
    }


    /** 懒取并缓存 {@code circle-shadow}（图集未就绪或缺图时返回 null）。 */
    private TextureRegion circleShadow(){
        if(circleShadow == null && Core.atlas != null){
            circleShadow = Core.atlas.findRegion("circle-shadow");
        }
        return circleShadow;
    }

    /** 懒取并缓存 {@code circle-end}（{@code line()} 的端帽；缺图返回 null）。 */
    private TextureRegion circleEnd(){
        if(circleEnd == null && Core.atlas != null){
            circleEnd = Core.atlas.findRegion("circle-end");
        }
        return circleEnd;
    }

    /** 懒取并缓存 {@code circle-mid}（{@code line()} 的中段；缺图返回 null）。 */
    private TextureRegion circleMid(){
        if(circleMid == null && Core.atlas != null){
            circleMid = Core.atlas.findRegion("circle-mid");
        }
        return circleMid;
    }

    /**
     * 把本帧收集到的光源渲染进 1/{@code scaling} 分辨率的缓冲，再叠加回屏幕（对应原版 {@code LightRenderer.draw()}）。
     * <p>⚠ 必须在 {@code Core.batch} 已 begin、且当前帧缓冲为默认帧缓冲时调用（见 {@code Renderer.draw}）。
     */
    public void draw(){
        //本项目 Shaders 是懒初始化（原版在 Renderer 构造器里建好）→ 这里补一次幂等初始化
        Shaders.init();

        int w = Gdx.graphics.getWidth() / scaling;
        int h = Gdx.graphics.getHeight() / scaling;
        if(w <= 0 || h <= 0) return;

        //libgdx 1.14.2 没有 buffer.resize(w, h) → 尺寸不符就 dispose 后重建
        if(buffer == null || buffer.getWidth() != w || buffer.getHeight() != h){
            if(buffer != null) buffer.dispose();
            buffer = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        }

        //⚠ arc 的 FrameBuffer.begin() 会 Draw.flush()，libgdx 的不会 → 必须自己先把排队的精灵落到默认帧缓冲
        Draw.flush();

        //⚠⚠ libgdx 的 FrameBuffer.end() 固定 glBindFramebuffer(GL_FRAMEBUFFER, 0) **并把视口重置成整屏**，
        //不是恢复到上一个 FBO → 若外层还开着 FBO（Pixelator 的像素化缓冲），这里一 end() 就会把后续世界绘制
        //打回默认帧缓冲。所以进出前存取/恢复当前绑定与视口（S10；见 FboUtil 的类注释）。
        int[] prevFbo = FboUtil.save();

        Draw.color();
        buffer.begin();
        //libgdx 没有 begin(Color) 重载（arc 有）→ 手动清成透明黑（等价 Color.clear）
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        //RGB 累加、ALPHA 取最大（对应原版 Gl.blendEquationSeparate(Gl.funcAdd, Gl.max)）
        Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_ADD, GL30.GL_MAX);

        for(int i = 0; i < lights.size; i++){
            lights.get(i).run();
        }
        //本项目没有 Draw.reset() → 用 color() 复位（等价于白色/alpha=1）
        Draw.color();
        //⚠ 出 FBO 前必须先落盘（同进入前，libgdx 的 end() 不会 flush）
        Draw.flush();
        buffer.end();
        //恢复进入前的绑定（否则会把外层的像素化 FBO 踢掉）
        FboUtil.restore(prevFbo);
        Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_ADD, GL20.GL_FUNC_ADD);

        Draw.color();
        Shaders.light.ambient.set(Vars.state.rules.ambientLight);
        Draw.shader(Shaders.light);
        //⚠ libgdx 没有 arc 的 Shader.apply() 钩子 → 显式灌 uniform。
        //此时 SpriteBatch.setShader 已在绘制中 flush 并 bind 了该 shader，setUniformf 才生效。
        Shaders.light.applyUniforms();

        //⚠⚠ FBO 纹理贴回必须把 v/v2 对调：libgdx 的 SpriteBatch 把 region.v2 放在底边（与 arc 相反），
        //而 FrameBuffer 纹理的 v=0 是 GL 底行 → 少了对调整层光照会上下颠倒（见 BlockRenderer.drawBuffer 的说明）。
        Tmp.tr1.setRegion(buffer.getColorBufferTexture());
        Tmp.tr1.setRegion(0f, 1f, 1f, 0f);
        Draw.rect(Tmp.tr1, Core.camera.position.x, Core.camera.position.y,
            Vars.renderer.viewWidth(), Vars.renderer.viewHeight());
        Draw.shader();

        lights.clear();
    }

    @Override
    public void dispose(){
        if(buffer != null) buffer.dispose();
        buffer = null;
    }
}
