package com.phoenix.game.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Disposable;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;

/**
 * 像素化后处理。参照 Mindustry {@code mindustry.graphics.Pixelator} 移植。
 *
 * <p>设置项 {@code pixelate} 打开时，世界不直接画到屏幕，而是：
 * <ol>
 *   <li>把相机缩放**取整**（{@code scale = (int)getScale()}）—— 让 1 个世界单位对应整数个像素，
 *       像素化后不会出现半像素抖动；</li>
 *   <li>相机位置也取整（宽/高为奇数时再补 0.5，对应原版那两行三元表达式）；</li>
 *   <li>按 {@code 屏幕尺寸 / scale} 建一张 {@code Nearest} 过滤的 FrameBuffer，
 *       世界先渲进它（= 1 像素 1 世界单位）；</li>
 *   <li>把这张低分辨率缓冲在**世界坐标**下放大铺满可见区域贴回屏幕；</li>
 *   <li>最后单独画一遍玩家名 —— 文字不参与像素化（与原版一致）。</li>
 * </ol>
 *
 * <p><b>与 libgdx 的差异</b>：
 * <ul>
 *   <li>原版用 arc 的 {@code FrameBuffer}/{@code Draw.wrap(texture)}/{@code Draw.blend(Blending.disabled)}；
 *       本项目用 libgdx 的 {@code FrameBuffer} + {@code TextureRegion} 包一层 + {@code setBlendFunction}。</li>
 *   <li><b>libgdx 1.14.2 的 {@code GLFrameBuffer} 没有 {@code resize(w,h)}</b>（只有 {@code getWidth/getHeight}）
 *       → 尺寸变化时只能 dispose 后重建（原版是 {@code buffer.resize(w, h)}）。</li>
 *   <li>原版在 {@code buffer.begin()} **之前** {@code graphics.clear(0,0,0,1)}（清的是默认帧缓冲，其实被后面的
 *       整屏贴回盖掉，等于空操作）。本项目改成在 {@code buffer.begin()} **之后**清一次 FBO —— 相机贴到地图
 *       边缘时视口里有一部分没有地板，不清会看到上一帧的残影。</li>
 *   <li>⚠ <b>贴回的矩形是「左下角 + 正高度」，不是原版的「中心 + 负高度」</b>：原版走 arc 的
 *       {@code Draw.rect(region, camX, camY, w, -h)}，arc 的 {@code Draw.rect} 是**中心**语义（内部自己
 *       {@code x - w/2}）；本项目直接调 libgdx 的 {@code Core.batch.draw}，那是**左下角**语义。照抄原版的
 *       中心点 + 负高度会把矩形画到可视区**下方**（y 从 {@code camY - h/2} 再往下掉 h），表现为「世界全黑、
 *       只剩 HUD」。负高度在 arc 那边还兼做上下翻转，libgdx 这边不需要 —— FBO 纹理的 v=0 就是 GL 的
 *       底行，直接贴回即正立。</li>
 * </ul>
 *
 * <p>调用点：{@code ClientLauncher.render()} 在 {@code renderer.draw()} 的位置二选一
 * （对应原版 {@code Renderer.update()} 里 {@code if(pixelator.enabled()) ... else draw();}）。
 */
public class Pixelator implements Disposable{
    /**
     * 低分辨率缓冲。⚠ 原版在字段初始化时就 {@code new FrameBuffer(2, 2)}，但本项目
     * {@code Renderer} 是 {@code ClientLauncher} 的**字段**（在 GL 上下文就绪之前构造），
     * 那时 {@code Gdx.graphics} 还是 null → 只能懒创建（第一次真正像素化时建）。
     */
    private FrameBuffer buffer;

    /** 本帧是否启用像素化（对应原版 {@code Pixelator.enabled()}）。 */
    public boolean enabled(){
        return Core.settings.getBool("pixelate");
    }

    /** 按需创建/重建缓冲（libgdx 1.14.2 的 {@code GLFrameBuffer} 没有 {@code resize}）。 */
    private void ensureBuffer(int w, int h){
        if(buffer != null && buffer.getWidth() == w && buffer.getHeight() == h) return;
        if(buffer != null) buffer.dispose();
        buffer = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        buffer.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    }

    public void drawPixelate(){
        float pre = Vars.renderer.getScale();
        float scale = (int)pre;
        Vars.renderer.setScale(scale);

        //相机位置取整（宽/高为奇数时补 0.5），避免像素化后整屏抖动
        float px = Core.camera.position.x, py = Core.camera.position.y, pz = Core.camera.position.z;
        Core.camera.position.set(
            (int)px + (((int)Core.camera.viewportWidth % 2 == 0) ? 0f : 0.5f),
            (int)py + (((int)Core.camera.viewportHeight % 2 == 0) ? 0f : 0.5f), pz);

        //FBO 尺寸 = 屏幕尺寸 / 整数缩放 = 可见区域的世界尺寸（1 像素 1 世界单位）
        int w = Math.max(1, (int)(Core.camera.viewportWidth / scale));
        int h = Math.max(1, (int)(Core.camera.viewportHeight / scale));

        //libgdx 1.14.2 的 GLFrameBuffer 没有 resize → 尺寸变了就重建
        ensureBuffer(w, h);

        buffer.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Vars.renderer.draw();
        buffer.end();

        //把低分辨率缓冲贴回屏幕：世界坐标下铺满可见区域（左下角 + 正高度，见类注释里那条差异说明）
        float viewW = Vars.renderer.viewWidth(), viewH = Vars.renderer.viewHeight();
        Core.batch.setProjectionMatrix(Core.camera.combined);
        Core.batch.begin();
        //⚠ 必须复位 batch 颜色：HUD 的 Scene2D Stage 与 Core.batch 共享同一个 SpriteBatch，上一帧
        //stage.draw() 会把颜色残留成 alpha=0（详见 Renderer.draw() 里同一处复位的注释）。Renderer.draw()
        //自带复位，但这次贴回发生在它**之外** → 不复位的话整块贴图以 alpha=0 绘制，世界全黑只剩背景色。
        Core.batch.setColor(1f, 1f, 1f, 1f);
        //禁用混合（对应原版 Draw.blend(Blending.disabled)），否则半透明处会叠加出错误亮度
        Core.batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ZERO);
        //⚠⚠ 必须把 v/v2 对调：libgdx 的 SpriteBatch.draw(TextureRegion,x,y,w,h) 把 region.v2 放在**底边**、
        //region.v 放在**顶边**（与 arc 相反），而 FrameBuffer 纹理的 v=0 是 GL 的底行（= 图的下边）。
        //正着贴（v=0,v2=1）会让画面**上下翻转** —— 原版用 `Draw.rect(..., -h)` 达到同样目的。
        com.phoenix.game.core.Tmp.tr1.setRegion(buffer.getColorBufferTexture());
        com.phoenix.game.core.Tmp.tr1.setRegion(0f, 1f, 1f, 0f);
        Core.batch.draw(com.phoenix.game.core.Tmp.tr1,
            Core.camera.position.x - viewW / 2f, Core.camera.position.y - viewH / 2f, viewW, viewH);
        Core.batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        //玩家名不参与像素化（对应原版在贴回缓冲之后单独 `playerGroup.draw(..., Player::drawName)`）
        Vars.renderer.drawNames();
        Core.batch.end();

        //还原相机
        Core.camera.position.set(px, py, pz);
        Vars.renderer.setScale(pre);
    }

    @Override
    public void dispose(){
        if(buffer != null) buffer.dispose();
    }
}
