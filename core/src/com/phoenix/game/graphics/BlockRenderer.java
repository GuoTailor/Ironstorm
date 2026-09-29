package com.phoenix.game.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Fill;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.game.EventType;
import com.phoenix.game.core.World;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 方块层渲染的两张"全图烘焙"缓冲。参照 Mindustry {@code mindustry.graphics.BlockRenderer} 里的
 * <b>方块投影（shadows）与地图变暗（fog）</b>两个子系统移植。
 *
 * <p>两张缓冲都是 <b>1 像素 = 1 格</b>、尺寸 = 地图格数，在地图加载时整张烘焙一次，之后只对
 * {@code TileChangeEvent} 涉及的格子增量重烘焙：
 * <ul>
 *   <li><b>shadows（方块投影）</b>：底色白；有 {@code hasShadow} 的方块所在格涂 {@code shadowColor}
 *       （黑 71% 透明）。绘制时用 {@link Shaders#fog} 把"暗处"转成不透明黑再叠加，
 *       因为纹理是 {@code Linear} 过滤、1 格只占 1 像素 → 放大后得到柔和的方块落影。
 *       画在<b>地板之后、方块之前</b>。</li>
 *   <li><b>fog（地图变暗）</b>：底色白；靠近地图边缘 2 格内按距离渐变涂黑（地图四周的暗角），
 *       以及 {@code tile.rotation() > 0} 且该格是"可填充实体方块"（{@code solid && fillsTile && !synthetic()}）
 *       的格子按 rotation 涂黑 —— 这是原版战役地图用来标记"黑暗区域"的编码方式。
 *       画在<b>方块层之后、顶层之前</b>。</li>
 * </ul>
 *
 * <p><b>本类只搬了这两个子系统</b>：原版 {@code BlockRenderer} 还有方块请求队列（{@code requests}）、
 * 分缓存层的批量绘制（{@code drawBlocks(Layer)}）、{@code drawDestroyed} 等，本项目这些分别由
 * {@code core/Renderer.drawTiles} 与 {@code Renderer.drawDestroyed} 直接实现 → 不重复搬。
 * 因此这里的类名只是"对齐原版"，并不包含完整的方块渲染器。
 *
 * <p><b>与 libgdx 的差异</b>：
 * <ul>
 *   <li>原版字段初始化就 {@code new FrameBuffer(2, 2)}；本项目 {@code Renderer} 是 {@code ClientLauncher}
 *       的字段（构造早于 GL 上下文就绪）→ 只能懒创建（第一次烘焙时按地图尺寸建）。</li>
 *   <li>libgdx 1.14.2 的 {@code GLFrameBuffer} 没有 {@code resize(w,h)} → 尺寸变化只能 dispose 后重建，
 *       并且 {@code Linear} 过滤要在重建**之后**再设一次（重建会丢过滤设置）。</li>
 *   <li>原版用 arc 的 {@code Draw.proj().setOrtho(0, 0, w, h)} 切到"1 单位 1 像素"的正交投影；
 *       本项目等价物是 {@code Draw.proj().setToOrtho2D(0, 0, w, h)}（libgdx 的 4 参版本）。</li>
 *   <li>原版在 {@code buffer.begin()} 之前 {@code Core.graphics.clear(Color.white)} 清的是默认帧缓冲
 *       （被后面盖掉、等于空操作）；本项目改成在 {@code begin()} 之后清 FBO。</li>
 * </ul>
 */
public class BlockRenderer implements Disposable{
    /** 方块落影颜色（对应原版 {@code BlockRenderer.shadowColor}）。 */
    private static final Color shadowColor = new Color(0, 0, 0, 0.71f);

    /** 变暗层在地图边缘的渐变格数（对应原版 {@code edgeBlend}）。 */
    private static final int edgeBlend = 2;

    /** 方块投影缓冲（1 像素 1 格）。 */
    private FrameBuffer shadows;
    /** 地图变暗缓冲（1 像素 1 格）。 */
    private FrameBuffer fog;

    /** 本帧需要重烘焙投影的格子（对应原版 {@code shadowEvents}）。 */
    private final Array<Tile> shadowEvents = new Array<>();

    public BlockRenderer(){
        //地图加载时整张烘焙（对应原版 Renderer 构造器里的 WorldLoadEvent 监听）
        Events.on(EventType.WorldLoadEvent.class, e -> bakeAll());
        //格子内容变化 → 排队增量重烘焙（对应原版 TileChangeEvent 监听；原版还顺带按相机范围
        //失效方块请求缓存，本项目没有请求队列，只留影子这一件事）
        Events.on(EventType.TileChangeEvent.class, e -> shadowEvents.add(e.tile));
    }

    /** 按地图尺寸建/重建两张缓冲（libgdx 1.14.2 没有 {@code resize}）。 */
    private void ensureBuffers(int w, int h){
        if(shadows != null && shadows.getWidth() == w && shadows.getHeight() == h) return;
        if(shadows != null) shadows.dispose();
        if(fog != null) fog.dispose();
        shadows = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        shadows.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        fog = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        fog.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    }

    /** 清一张缓冲到白色（"无遮挡"）。 */
    private static void clearWhite(){
        Gdx.gl.glClearColor(1f, 1f, 1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    /**
     * 往 FBO 里用几何（{@link Fill}）画东西之前，把"标准 alpha 混合"显式打开。
     *
     * <p>⚠ <b>不能指望 GL 状态本来就是对的</b>：libgdx 的 {@code SpriteBatch.end()} 会
     * {@code glDisable(GL_BLEND)}，而本类的整张烘焙挂在 {@code WorldLoadEvent} 上（世界加载
     * 发生在渲染批次之外）→ 那时混合是关的，{@code Fill.rect} 会把 {@code shadowColor}
     * （黑 71% 透明）**当成纯黑直接写进缓冲**：影子像素的 r 变成 0，{@code Shaders.fog} 里
     * {@code 1.0 - color.r} 就成了 1.0 → 方块落影与地图暗角都变成 100% 不透明黑
     * （症状：影子黑得发实、盖住周围地板，而原版是 71% 的柔和半透明）。
     * 增量重烘焙虽然发生在 {@code Renderer.drawTiles} 的批次内，但批次也可能正处在加色混合等
     * 非标准状态，统一在这里设一次最稳。
     * <p>安全性：{@code SpriteBatch.flush()} 每次都会重新应用自己的混合函数，所以这里改了
     * 不会污染后续的精灵绘制。
     */
    private static void beginBlend(){
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** 整张烘焙两张缓冲（对应原版 WorldLoadEvent 里的两段 begin/end）。 */
    private void bakeAll(){
        World world = Vars.world;
        if(world == null || world.width() == 0 || world.height() == 0) return;

        Shaders.init();
        ensureBuffers(world.width(), world.height());

        int w = world.width(), h = world.height();

        //先收尾上一帧遗留的几何会话：Drawf.begin 只在"会话未开"时同步投影矩阵，
        //不先 end 的话切到 FBO 正交投影后 ShapeRenderer 还用着旧矩阵。
        Draw.flush();

        //==== 1) 方块投影 ====
        shadows.begin();
        clearWhite();
        beginBlend();
        Draw.proj().setToOrtho2D(0, 0, w, h);
        Draw.color(shadowColor);
        for(int x = 0; x < w; x++){
            for(int y = 0; y < h; y++){
                Tile tile = world.tile(x, y);
                if(tile != null && tile.block() != null && tile.block().hasShadow){
                    Fill.rect(tile.x + 0.5f, tile.y + 0.5f, 1, 1);
                }
            }
        }
        Draw.flush();
        Draw.color();
        shadows.end();

        //==== 2) 地图变暗 ====
        fog.begin();
        clearWhite();
        beginBlend();
        Draw.proj().setToOrtho2D(0, 0, w, h);
        for(int x = 0; x < w; x++){
            for(int y = 0; y < h; y++){
                Tile tile = world.tile(x, y);
                if(tile == null) continue;
                Block block = tile.block();
                float rot = tile.rotation();
                boolean fillable = block != null && block.solid && block.fillsTile && !block.synthetic();
                int edgeDst = Math.min(x, Math.min(y, Math.min(Math.abs(x - (w - 1)), Math.abs(y - (h - 1)))));
                if(edgeDst <= edgeBlend){
                    rot = Math.max((edgeBlend - edgeDst) * (4f / edgeBlend), fillable ? rot : 0);
                }
                if(rot > 0 && (fillable || edgeDst <= edgeBlend)){
                    Draw.color(0f, 0f, 0f, Math.min((rot + 0.5f) / 4f, 1f));
                    Fill.rect(tile.x + 0.5f, tile.y + 0.5f, 1, 1);
                }
            }
        }
        Draw.flush();
        Draw.color();
        fog.end();

        //还原世界相机投影
        Draw.proj(Core.camera.combined);
        shadowEvents.clear();
    }

    /**
     * 绘制方块投影（对应原版 {@code BlockRenderer.drawShadows}）。
     * <p>调用点：{@code Renderer.drawTiles} 里"地板那趟"之后、"方块那趟"之前。
     * <p>⚠ 本方法内部会开 FrameBuffer，所以<b>必须在 batch 还开着的时候调用</b>也没问题 ——
     * 开 FBO 前先 {@code Draw.flush()} 把已排队的精灵落盘到当前（默认）帧缓冲。
     */
    public void drawShadows(){
        World world = Vars.world;
        if(world == null || shadows == null) return;

        if(shadowEvents.size > 0){
            Draw.flush();

            //⚠⚠ libgdx 的 FrameBuffer.end() 固定 glBindFramebuffer(GL_FRAMEBUFFER, 0) **并把视口重置成整屏**，
            //不是恢复到上一个 FBO → 若外层还开着 FBO（Pixelator 的像素化缓冲），这里一 end() 就会把后续世界绘制
            //打回默认帧缓冲。所以进出前存取/恢复当前绑定与视口（S10；见 FboUtil 的类注释）。
            int[] prevFbo = FboUtil.save();

            shadows.begin();
            beginBlend();
            Draw.proj().setToOrtho2D(0, 0, shadows.getWidth(), shadows.getHeight());
            for(int i = 0; i < shadowEvents.size; i++){
                Tile tile = shadowEvents.get(i);
                if(tile == null) continue;
                //先擦成白（"无遮挡"）
                Draw.color(Color.WHITE);
                Fill.rect(tile.x + 0.5f, tile.y + 0.5f, 1, 1);
                //再按该格有没有落影涂上
                Block block = tile.block();
                Draw.color(block == null || !block.hasShadow ? Color.WHITE : shadowColor);
                Fill.rect(tile.x + 0.5f, tile.y + 0.5f, 1, 1);
            }
            Draw.flush();
            Draw.color();
            shadows.end();
            //恢复进入前的绑定（否则会把外层的像素化 FBO 踢掉）
            FboUtil.restore(prevFbo);
            shadowEvents.clear();

            Draw.proj(Core.camera.combined);
        }

        drawBuffer(shadows);
    }

    /**
     * 绘制地图变暗层（对应原版 {@code BlockRenderer.drawFog}）。
     * <p>调用点：{@code Renderer.drawTiles} 里"方块那趟"之后、"顶层那趟"之前。
     */
    public void drawFog(){
        if(Vars.world == null || fog == null) return;
        drawBuffer(fog);
    }

    /**
     * 把一张"1 像素 1 格"的全图缓冲按可见世界矩形贴到屏幕上，并用 {@link Shaders#fog}
     * 把暗处转成不透明黑。
     * <p>uv 的推导：缓冲的正交投影是 {@code (0,0,地图格数)} 且 1 单位 = 1 像素 →
     * 世界坐标 {@code wx} 对应缓冲归一化坐标 {@code wx / (地图格数 * tilesize)}。
     * <p>⚠⚠ <b>必须像原版那样把 v/v2 对调</b>：libgdx 的 {@code SpriteBatch.draw(TextureRegion, x, y, w, h)}
     * 把 {@code region.v2} 放在**底边**、{@code region.v} 放在**顶边**（与 arc 相反，见 libgdx 源码里
     * {@code float v = region.v2; ... float v2 = region.v; }）。而 FrameBuffer 纹理的 {@code v=0} 是
     * GL 的底行（= 图的下边），所以要让"屏幕下方 = 世界 y 小的一侧"，就得让 {@code region.v2} 取小的那个 v。
     * 少了这次对调，整层影子/变暗会**上下翻转**（症状：影子跑到方块上方几格、且只在有水的地方明显）。
     */
    private void drawBuffer(FrameBuffer buffer){
        World world = Vars.world;
        float ww = world.width() * tilesize, wh = world.height() * tilesize;
        float viewW = Vars.renderer.viewWidth(), viewH = Vars.renderer.viewHeight();
        float x = Core.camera.position.x + tilesize / 2f, y = Core.camera.position.y + tilesize / 2f;
        float u = (x - viewW / 2f) / ww,
              v = (y - viewH / 2f) / wh,
              u2 = (x + viewW / 2f) / ww,
              v2 = (y + viewH / 2f) / wh;

        //⚠ libgdx 1.14.2 的 TextureRegion 把 arc 的 `set(...)` 改名成了 `setRegion(...)`
        Tmp.tr1.setRegion(buffer.getColorBufferTexture());
        //v/v2 对调（见方法注释）
        Tmp.tr1.setRegion(u, v2, u2, v);

        Draw.shader(Shaders.fog);
        Draw.rect(Tmp.tr1, Core.camera.position.x, Core.camera.position.y, viewW, viewH);
        Draw.shader();
    }

    @Override
    public void dispose(){
        if(shadows != null) shadows.dispose();
        if(fog != null) fog.dispose();
        shadows = fog = null;
    }
}
