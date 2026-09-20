package com.phoenix.game.graphics;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Pools;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Fonts;
import com.phoenix.game.core.Scl;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.game.EventType;
import com.phoenix.game.io.MapIO;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;
import io.anuke.mindustry.gen.Tex;

import static com.phoenix.game.Vars.tilesize;

/**
 * 小地图渲染器。参照 Mindustry mindustry.graphics.MinimapRenderer 移植。
 * <p>整张地图预渲染成「1 像素 = 1 格」的 {@link Pixmap} + {@link Texture}：世界加载时全量重建
 * （{@link #updateAll()}），瓦片变化时只改一个像素（{@link #update(Tile)}）。
 * 单位不在纹理里，绘制时按阵营色叠加（{@link #drawEntities}），与原版一致。
 * <p>挂载点 {@code Vars.renderer.minimap}（对应原版 {@code Vars.renderer.minimap}）。
 */
public class MinimapRenderer implements Disposable{
    /** 小地图窗口的基准格数（对应原版同名字段）。 */
    private static final float baseSize = 16f;
    /** 复用的单位列表，避免每次绘制都新建。 */
    private final Array<BaseUnit> units = new Array<>();
    private Pixmap pixmap;
    private Texture texture;
    private TextureRegion region;
    /** 复用矩形，避免绘制时分配。 */
    private final Rectangle rect = new Rectangle();
    private float zoom = 4;
    /** 像素图有改动、纹理待上传（见 {@link #flush()}）。 */
    private boolean dirty;

    public MinimapRenderer(){
        Events.on(EventType.WorldLoadEvent.class, event -> {
            reset();
            updateAll();
        });
        //原版这里用 Core.app.post 切到图形线程；phoenix 的逻辑与渲染同线程，直接更新即可
        Events.on(EventType.TileChangeEvent.class, event -> update(event.tile));
    }

    public Pixmap getPixmap(){
        return pixmap;
    }

    public Texture getTexture(){
        return texture;
    }

    public void zoomBy(float amount){
        zoom += amount;
        setZoom(zoom);
    }

    public void setZoom(float amount){
        com.phoenix.game.core.World world = Vars.world;
        if(world == null || world.width <= 0 || world.height <= 0) return;
        zoom = Mathf.clamp(amount, 1f, Math.min(world.width, world.height) / baseSize / 2f);
    }

    public float getZoom(){
        return zoom;
    }

    /** 世界被替换（新战役/读档/换图/联机收世界）：重建像素图。 */
    public void reset(){
        if(pixmap != null){
            pixmap.dispose();
            texture.dispose();
            pixmap = null;
            texture = null;
        }

        com.phoenix.game.core.World world = Vars.world;
        if(world == null || world.width <= 0 || world.height <= 0) return;

        setZoom(4f);
        pixmap = new Pixmap(world.width, world.height, Pixmap.Format.RGBA8888);
        texture = new Texture(pixmap);
        //小地图是 1 像素 1 格，放大后要看到清晰的方块而不是糊成一团
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        region = new TextureRegion(texture);
    }

    /** 在已绘制的小地图上叠加单位标记。 */
    public void drawEntities(float x, float y, float w, float h, float scaling, boolean withLabels){
        if(texture == null || Vars.world == null) return;

        if(!withLabels){
            updateUnitArray();
        }else{
            units.clear();
            Units.all(units::add);
        }

        float sz = baseSize * zoom;
        float dx = (Core.camera.position.x / tilesize);
        float dy = (Core.camera.position.y / tilesize);
        dx = Mathf.clamp(dx, sz, Vars.world.width - sz);
        dy = Mathf.clamp(dy, sz, Vars.world.height - sz);

        rect.set((dx - sz) * tilesize, (dy - sz) * tilesize, sz * 2 * tilesize, sz * 2 * tilesize);

        //下标遍历：libgdx Array 的迭代器不能嵌套（项目硬约束）
        for(int i = 0; i < units.size; i++){
            BaseUnit unit = units.get(i);
            if(unit == null || unit.isDead()) continue;

            float rx = !withLabels ? (unit.x - rect.x) / rect.width * w : unit.x / (Vars.world.unitWidth()) * w;
            float ry = !withLabels ? (unit.y - rect.y) / rect.width * h : unit.y / (Vars.world.unitHeight()) * h;

            //原版用 Draw.mixcol(team.color, 1f) 把单位图标染成阵营色（arc 的 mixcol 是整体替换色）；
            //phoenix 的 Draw 没有 mixcol，用 libgdx 的 batch.setColor 做同等的染色
            Core.batch.setColor(unit.getTeam().color);
            float scale = Scl.scl(1f) / 2f * scaling * 32f;
            Draw.rect(unit.getIconRegion(), x + rx, y + ry, scale, scale, unit.rotation - 90);
            Core.batch.setColor(Color.WHITE);

            if(withLabels && unit.isPlayer){
                //phoenix 的 Player 不是单位子类（原版 Player 本身就是单位），只给其他玩家显示名字：
                //isPlayer 标记的是「被玩家控制的单位」，本地玩家由 Vars.player.unit() 指认
                if(unit != (Vars.player == null ? null : Vars.player.unit())){
                    drawLabel(x + rx, y + ry, unit.playerName, unit.getTeam().color);
                }
            }
        }

        Core.batch.setColor(Color.WHITE);
    }

    public void drawEntities(float x, float y, float w, float h){
        drawEntities(x, y, w, h, 1f, true);
    }

    /** @return 当前视野（相机为中心的窗口）在整图纹理中的子区域；纹理未就绪时返回 null。 */
    public TextureRegion getRegion(){
        if(texture == null || Vars.world == null || Vars.world.width <= 0) return null;

        float sz = Mathf.clamp(baseSize * zoom, baseSize, Math.min(Vars.world.width, Vars.world.height));
        //小地图窗口不能超过半张地图，否则下面的 clamp 会出现 min > max
        sz = Math.min(sz, Math.min(Vars.world.width, Vars.world.height) / 2f);

        float dx = (Core.camera.position.x / tilesize);
        float dy = (Core.camera.position.y / tilesize);
        dx = Mathf.clamp(dx, sz, Vars.world.width - sz);
        dy = Mathf.clamp(dy, sz, Vars.world.height - sz);

        float invTexWidth = 1f / texture.getWidth();
        float invTexHeight = 1f / texture.getHeight();
        float x = dx - sz, y = Vars.world.height - dy - sz, width = sz * 2, height = sz * 2;
        region.setRegion(x * invTexWidth, y * invTexHeight, (x + width) * invTexWidth, (y + height) * invTexHeight);
        return region;
    }

    /** 全量重刷像素图（世界加载后调用一次）。 */
    public void updateAll(){
        if(pixmap == null || Vars.world == null) return;

        for(int x = 0; x < Vars.world.width; x++){
            for(int y = 0; y < Vars.world.height; y++){
                pixmap.drawPixel(x, pixmap.getHeight() - 1 - y, colorFor(Vars.world.tile(x, y)));
            }
        }
        dirty = true;
    }

    /** 单个瓦片变化：只改一个像素。 */
    public void update(Tile tile){
        if(pixmap == null || tile == null || Vars.world == null) return;
        //地图生成/换图期间会带着旧世界的坐标进来，越界直接跳过（新世界随后走 WorldLoadEvent 全量重建）
        if(tile.x < 0 || tile.y < 0 || tile.x >= pixmap.getWidth() || tile.y >= pixmap.getHeight()) return;

        pixmap.drawPixel(tile.x, pixmap.getHeight() - 1 - tile.y, colorFor(Vars.world.tile(tile.x, tile.y)));
        dirty = true;
    }

    /**
     * 把待上传的像素上传到纹理。必须在 GL 线程、绘制之前调用。
     * <p>原版用 {@code Pixmaps.drawPixel} 单像素直接写纹理；libgdx 的 {@code Texture} 只有
     * {@code draw(Pixmap, x, y)} 这一种整张上传的 API，所以这里用脏标记把同一帧内的多次瓦片变化
     * 合并成一次上传（战斗中一帧可能拆掉几十个方块，逐次整张上传会很浪费）。
     */
    public void flush(){
        if(dirty && texture != null && pixmap != null){
            texture.draw(pixmap, 0, 0);
            dirty = false;
        }
    }

    /** 收集相机窗口内的单位（对应原版 updateUnitArray）。 */
    public void updateUnitArray(){
        float sz = baseSize * zoom;
        float dx = (Core.camera.position.x / tilesize);
        float dy = (Core.camera.position.y / tilesize);
        dx = Mathf.clamp(dx, sz, Vars.world.width - sz);
        dy = Mathf.clamp(dy, sz, Vars.world.height - sz);

        units.clear();
        Units.nearby((dx - sz) * tilesize, (dy - sz) * tilesize, sz * 2 * tilesize, sz * 2 * tilesize, units::add);
    }

    /**
     * 取瓦片的小地图颜色（对应原版 {@code MinimapRenderer.colorFor}）。
     * <p>原版还会按 {@code block.cacheLayer == CacheLayer.walls} 用朝向压暗；phoenix 没有 CacheLayer，省略该步。
     */
    private int colorFor(Tile tile){
        if(tile == null) return 0;
        tile = tile.link();

        int bc = tile.block().minimapColor(tile);
        if(bc != 0){
            return bc;
        }
        return MapIO.colorFor(tile.floor(), tile.block(), tile.overlay(), tile.getTeam());
    }

    @Override
    public void dispose(){
        if(pixmap != null && texture != null){
            pixmap.dispose();
            texture.dispose();
            texture = null;
            pixmap = null;
        }
    }

    /** 在小地图上画玩家名（对应原版 drawLabel；原版的黑底用 arc 的 Fill.rect，这里用 batch 画白块）。 */
    public void drawLabel(float x, float y, String text, Color color){
        BitmapFont font = Fonts.outline;
        GlyphLayout l = Pools.obtain(GlyphLayout.class);
        boolean ints = font.usesIntegerPositions();
        font.getData().setScale(1 / 1.5f / Scl.scl(1f));
        font.setUseIntegerPositions(false);

        l.setText(font, text, color, 90f, Align.left, true);
        float yOffset = 20f;
        float margin = 3f;

        Core.batch.setColor(0f, 0f, 0f, 0.2f);
        Core.batch.draw(Tex.whiteui.getRegion(), x, y + yOffset - l.height / 2f, l.width + margin, l.height + margin);
        Core.batch.setColor(Color.WHITE);
        font.setColor(color);
        font.draw(Core.batch, text, x - l.width / 2f, y + yOffset, 90f, Align.left, true);
        font.setUseIntegerPositions(ints);

        font.getData().setScale(1f);

        Pools.free(l);
    }
}
