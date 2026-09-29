package com.phoenix.game.graphics;

/**
 * 缓存层（对应原版 {@code mindustry.graphics.CacheLayer}）。
 *
 * <p>原版用它把「水面/焦油」这类需要着色器动画的地板先画进一张离屏缓冲（{@code shieldBuffer}），
 * 再用 {@code Shaders.water}/{@code Shaders.tar} 把整张缓冲按波动的 UV 贴回屏幕，从而实现水面流动。
 *
 * <p><b>本项目的偏离（刻意）</b>：本项目的渲染器是自研的 {@code core.Renderer}，
 * 原版的 {@code BlockRenderer/FloorRenderer/shieldBuffer} **决定跳过不移植**（与自研 Renderer 重复）；
 * {@code graphics/Shaders} 类**存在**、但只实现了 {@code fog}（缺 {@code water/tar/build/shield}）—— 所以：
 * <ul>
 *   <li>{@code begin()}/{@code end()} 保留原版的枚举结构与调用点，但退化为空实现；</li>
 *   <li>{@code beginShader()}/{@code endShader(...)} 保留为 TODO 空壳 —— 没有离屏缓冲与水面着色器，
 *       动画水面暂时不做（地板按图集区域直接绘制）。</li>
 * </ul>
 */
public enum CacheLayer{
    water{
        @Override
        public void begin(){
            beginShader();
        }

        @Override
        public void end(){
            endShader();
        }
    },
    tar{
        @Override
        public void begin(){
            beginShader();
        }

        @Override
        public void end(){
            endShader();
        }
    },
    normal,
    walls;

    public void begin(){

    }

    public void end(){

    }

    //TODO 原版：if(!Core.settings.getBool("animatedwater")) return; renderer.blocks.floor.endc();
    //      renderer.shieldBuffer.begin(); Core.graphics.clear(Color.clear); renderer.blocks.floor.beginc();
    //      —— shieldBuffer / FloorRenderer 未移植，动画水面着色器（Shaders.water/tar）也未移植。
    void beginShader(){

    }

    //TODO 原版签名是 endShader(Shader shader)：离屏缓冲结束后 Draw.shader(shader) + 把缓冲贴回屏幕。
    //      这里没有 Shaders.water/tar（graphics/Shaders 类存在，但只实现了 fog），保留空壳（见类注释）。
    void endShader(){

    }
}
