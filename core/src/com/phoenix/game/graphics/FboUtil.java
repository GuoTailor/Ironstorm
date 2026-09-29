package com.phoenix.game.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.IntBuffer;

/**
 * 帧缓冲绑定 + 视口的保存/恢复工具（S10 批）。
 *
 * <p>⚠⚠ <b>libgdx 的 {@code FrameBuffer.begin()/end()} 是"绑定/解绑到默认帧缓冲"的语义</b>：
 * {@code GLFrameBuffer.begin()}（1.14.2 `:367`）= {@code glBindFramebuffer(fbo)} + {@code glViewport(0,0,fboW,fboH)}；
 * {@code end()}（`:390`）= {@code glBindFramebuffer(0)} + {@code glViewport(0,0,backBufferW,backBufferH)} ——
 * **不是**恢复到进入前的 FBO/视口（arc 的 {@code FrameBuffer} 会恢复）。所以 **FBO 不能嵌套** ——
 * 内层 FBO 一 {@code end()} 就会把外层 FBO 与视口一起踢掉，之后所有绘制都落到默认帧缓冲、且视口被放大成整屏。
 *
 * <p>本项目唯一的嵌套场景：{@code Pixelator}（像素化）在外层开着 FBO 时调用 {@code Renderer.draw()}，
 * 其中的方块落影 FBO（{@code BlockRenderer.drawShadows}）、灯光 FBO（{@link LightRenderer}）与
 * 护盾 FBO（{@code Renderer.shieldBuffer}）一 {@code end()} 就把后续绘制打回默认帧缓冲
 * → 表现为"像素化时灯光/特效不生效"（两个设置都默认关，所以一直没暴露）。
 *
 * <p>用法：进 FBO <b>之前</b> {@code int[] st = FboUtil.save();}，出 FBO <b>之后</b> {@code FboUtil.restore(st);}。
 * ⚠ <b>只恢复绑定是不够的</b> —— 必须连 {@code GL_VIEWPORT} 一起恢复，否则内层 FBO 会把外层 FBO 的视口
 * 改成整屏尺寸，后续内容画错位置（实测表现为"整屏全黑只剩 HUD"）。
 */
public final class FboUtil{
    /** 复用同一个缓冲，避免每帧分配（这三个调用点每帧最多各一次）。4 个 int 是给 {@code GL_VIEWPORT} 用的。 */
    private static final IntBuffer buf = BufferUtils.newIntBuffer(4);

    private FboUtil(){
    }

    /**
     * @return {@code [帧缓冲句柄, 视口x, 视口y, 视口w, 视口h]}（默认帧缓冲句柄 = 0）。
     */
    public static int[] save(){
        int[] state = new int[5];
        buf.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, buf);
        state[0] = buf.get(0);
        buf.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, buf);
        state[1] = buf.get(0);
        state[2] = buf.get(1);
        state[3] = buf.get(2);
        state[4] = buf.get(3);
        return state;
    }

    /** 恢复到 {@link #save()} 记下的帧缓冲与视口（libgdx 的 {@code FrameBuffer.end()} 不会自动做这件事）。 */
    public static void restore(int[] state){
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, state[0]);
        Gdx.gl.glViewport(state[1], state[2], state[3], state[4]);
    }
}
