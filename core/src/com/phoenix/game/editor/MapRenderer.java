package com.phoenix.game.editor;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.IntSet;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.BlockPart;

import static com.phoenix.game.Vars.tilesize;

/**
 * 地图编辑器的画布渲染器。参照 Mindustry {@code mindustry.editor.MapRenderer} 移植。
 *
 * <p>把整张地图（地板 + 方块 + 队伍边框 + 覆盖层）缩放绘制到 {@link MapView} 给的那块矩形里。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li><b>渲染管线整体降级</b>：原版按 {@code chunkSize=64} 把地图切成 chunk，每块一个
 *       {@code IndexedRenderer}（自带 Shader 的静态网格），只在瓦片变化时重画该 chunk，
 *       {@code draw()} 里用 {@code mesh.getTransformMatrix().setToTranslation(...).scale(...)} 贴到目标矩形。
 *       <b>本项目没有 {@code IndexedRenderer}</b>（{@code graphics/Shaders} 类**存在**，但只有 {@code fog}，
 *       缺 {@code build}/{@code shield} 等成员，见 转换.md 的「Shaders」条）
 *       → 这里退化成「每帧用 SpriteBatch 逐格即时绘制」，缩放系数用与原版 mesh 完全相同的
 *       {@code tx + vx * (tw / (width * tilesize))}。编辑器画布通常只有几十×几十格，性能无压力。
 *       {@code chunks} 字段因此删除；{@code updates}/{@code delayedUpdates} 仅保留 API 形状（即时模式下不再需要脏标记）。</li>
 *   <li>{@code Draw.proj()} 本项目没有 → 即时绘制直接用 {@code Core.batch}（它已由 Stage 设好投影）。</li>
 *   <li>{@code Core.atlas.isFound(...)} libgdx 没有，且 arc 的 {@code atlas.find} 找不到会返回 error 贴图
 *       → 统一走 {@link #find(String)}：找不到退回 {@code clear-editor}（1x1 全透明，已验证像素为 0,0,0,0）。</li>
 *   <li>{@code wall.editorIcon()} / {@code floor.editorVariantRegions()}：本项目图集里没有
 *       {@code <name>-icon-editor} / {@code editor-<variant>} 系列 → {@link Block#editorIcon()} 与
 *       {@link Block#editorVariantRegions()} 已按「找不到就退回原贴图」实现（见这两个方法的 javadoc）。</li>
 *   <li><b>卫星格（BlockPart）不绘制</b>：原版会画 BlockPart 自己的 editorIcon；本项目 BlockPart
 *       没有图标，退化会拿到 {@code blank}（1x1 **纯白**，已验证）把整格涂白 → 显式画 {@code clear-editor}。</li>
 *   <li><b>air 的覆盖层分支加了 {@code != Blocks.air} 守卫</b>：原版判据是 {@code tile.overlay() != null}，
 *       但本项目 overlay 恒非 null（默认就是 {@code Blocks.air}，而 air 的变体贴图是纯白的 blank）
 *       → 不加守卫会让每个空格子都被涂白。</li>
 *   <li>{@code region.getWidth()} → {@code getRegionWidth()}（libgdx 的 TextureRegion 没有 getWidth）。</li>
 *   <li><b>旋转角度</b>：原版 {@code tile.rotation()*90 - 90}（原版 rotation 0 = +x、编辑图标朝上）；
 *       本项目 rotation 0 = +y、贴图基准朝右 → 角度按 {@code (1 - rotation) * 90} 换算（见 转换.md）。</li>
 *   <li>{@code Events.on(ContentReloadEvent.class, ...)} 本项目没有该事件 → 构造函数里只取一次贴图。</li>
 * </ul>
 */
public class MapRenderer{
    private static final int chunkSize = 64;
    private final IntSet updates = new IntSet();
    private final IntSet delayedUpdates = new IntSet();
    private final MapEditor editor;
    private int width, height;
    private Texture texture;

    public MapRenderer(MapEditor editor){
        this.editor = editor;
        TextureRegion region = Core.atlas == null ? null : Core.atlas.findRegion("clear-editor");
        this.texture = region == null ? null : region.getTexture();
        //TODO 原版监听 ContentReloadEvent 重新取 clear-editor 贴图；本项目贴图重载未接线（没有该事件）
    }

    public void resize(int width, int height){
        this.width = width;
        this.height = height;
        updates.clear();
        delayedUpdates.clear();
        //刻意降级：原版在这里按 chunkSize 建 IndexedRenderer 网格并 updateAll()；
        //     本项目没有 IndexedRenderer（graphics/Shaders 类存在、但缺所需成员）→ 改为逐帧即时绘制，无需缓存与预渲染
    }

    public void draw(float tx, float ty, float tw, float th){
        Draw.flush();

        //即时绘制模式：脏标记只做「清空」维护（原版靠它决定重画哪些 chunk）
        updates.clear();
        updates.addAll(delayedUpdates);
        delayedUpdates.clear();

        if(width <= 0 || height <= 0) return;

        float sx = tw / (width * tilesize), sy = th / (height * tilesize);

        //原版的 IndexedRenderer 有自己的颜色状态（默认白），不受 MapView 设的 Draw.color 影响
        //→ 这里显式复位成白色，否则地板会被 MapView 的 Pal.remove 染红
        Core.batch.setColor(Color.WHITE);

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                Tile tile = editor.tile(x, y);
                if(tile == null) continue;
                render(tile, x, y, tx, ty, sx, sy);
            }
        }

        Core.batch.setColor(Color.WHITE);
    }

    public void updatePoint(int x, int y){
        //即时绘制下没有 chunk 缓存可失效；保留记录以免调用点改动（原版 = updates.add(x + y*width)）
        updates.add(x + y * width);
    }

    public void updateAll(){
        //即时绘制下 draw() 每帧都会全量重画 → 无需预热
    }

    public void dispose(){
        //原版释放每个 chunk 的 IndexedRenderer；即时绘制没有 GPU 资源需要释放
        updates.clear();
        delayedUpdates.clear();
    }

    /**
     * 绘制一格。
     * @param tx,ty 目标矩形左下角（Stage 坐标）
     * @param sx,sy 世界单位 → 目标矩形的缩放（对应原版 mesh 的 scale）
     */
    private void render(Tile tile, int wx, int wy, float tx, float ty, float sx, float sy){
        if(Core.atlas == null) return;

        Team team = tile.getTeam();
        Block floor = tile.floor();
        //本项目 block() 会解析多格链接 → 这里要的是"本格实际存的方块"（原版 block() 就是原始值）
        Block wall = tile.blockRaw();
        boolean isPart = wall instanceof BlockPart;

        TextureRegion region;

        int idxWall = (wx % chunkSize) + (wy % chunkSize) * chunkSize;

        if(isPart){
            //刻意降级：原版 = wall.editorIcon()；本项目 BlockPart 无图标，退化会拿到纯白的 blank → 不绘制
            region = find("clear-editor");
        }else if(wall != Blocks.air && wall.synthetic()){
            region = wall.editorIcon();
            if(region == null) region = find("clear-editor");

            float baseX = tx + (wx * tilesize + wall.offset()) * sx;
            float baseY = ty + (wy * tilesize + wall.offset()) * sy;
            float rw = region.getRegionWidth() * Draw.scl * sx, rh = region.getRegionHeight() * Draw.scl * sy;

            if(wall.rotate){
                //本项目 rotation 0 = +y、贴图基准朝右 → 角度 = (1 - rotation) * 90
                Core.batch.draw(region, baseX, baseY, rw / 2f, rh / 2f, rw, rh, 1f, 1f, (1 - tile.rotation()) * 90f);
            }else{
                Core.batch.draw(region, baseX + (tilesize * sx - rw) / 2f, baseY + (tilesize * sy - rh) / 2f, rw, rh);
            }
        }else{
            TextureRegion[] variants = floor.editorVariantRegions();
            region = variants.length == 0 ? find("clear-editor")
                : variants[Mathf.randomSeed(idxWall, 0, variants.length - 1)];
            if(region == null) region = find("clear-editor");
            Core.batch.draw(region, tx + wx * tilesize * sx, ty + wy * tilesize * sy, 8f * sx, 8f * sy);
        }

        float offsetX = -(wall.size / 3) * tilesize, offsetY = -(wall.size / 3) * tilesize;
        boolean colored = false;

        if(wall.update || wall.destructible){
            //人造建筑：叠一层阵营色边框
            Core.batch.setColor(team.color);
            colored = true;
            region = find("block-border-editor");
        }else if(isPart){
            region = find("clear-editor");
        }else if(!wall.synthetic() && wall != Blocks.air){
            //自然岩壁：画方块自己的图标（居中）
            region = wall.editorIcon();
            if(region == null) region = find("clear-editor");
            offsetX = tilesize / 2f - region.getRegionWidth() / 2f * Draw.scl;
            offsetY = tilesize / 2f - region.getRegionHeight() / 2f * Draw.scl;
        }else if(wall == Blocks.air && tile.overlay() != Blocks.air){
            //覆盖层（矿脉/出生点）；原版判据是 overlay() != null，本项目 overlay 恒非 null → 显式排除 air
            TextureRegion[] variants = tile.overlay().editorVariantRegions();
            region = variants.length == 0 ? find("clear-editor")
                : variants[Mathf.randomSeed(idxWall, 0, variants.length - 1)];
            if(region == null) region = find("clear-editor");
        }else{
            region = find("clear-editor");
        }

        //贴图全部缺失时（图集里连 clear-editor 都没有）直接跳过，避免 NPE
        if(region == null) return;

        float width = region.getRegionWidth() * Draw.scl * sx, height = region.getRegionHeight() * Draw.scl * sy;
        if(!wall.synthetic() && wall != Blocks.air && !wall.isMultiblock()){
            offsetX = 0;
            offsetY = 0;
            width = tilesize * sx;
            height = tilesize * sy;
        }

        Core.batch.draw(region, tx + (wx * tilesize + offsetX) * sx, ty + (wy * tilesize + offsetY) * sy, width, height);
        if(colored) Core.batch.setColor(Color.WHITE);
    }

    /**
     * 按名取贴图；找不到退回 {@code clear-editor}（对应原版 {@code Core.atlas.find} 的 error 回退，
     * 但本项目用全透明的 clear-editor 而不是显眼的 error 贴图）。
     */
    private static TextureRegion find(String name){
        if(Core.atlas == null) return null;
        TextureRegion region = Core.atlas.findRegion(name);
        if(region == null && !"clear-editor".equals(name)){
            region = Core.atlas.findRegion("clear-editor");
        }
        return region;
    }
}
