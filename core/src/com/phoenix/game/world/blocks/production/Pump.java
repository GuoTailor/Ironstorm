package com.phoenix.game.world.blocks.production;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Interval;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.struct.Array;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.LiquidBlock;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.StatUnit;

import static com.phoenix.game.Vars.tilesize;

/**
 * 抽水机：从脚下的液体地板抽取液体（多格时按覆盖到的液体格数加速）。参照 Mindustry
 * mindustry.world.blocks.production.Pump 移植。
 * <p>与原版的差异：
 * <ul>
 *     <li>原版把 {@code update(Tile)} 写在方块上、由 {@code Block.update(Tile)} 调用；
 *         本项目写在实体内部类 {@link PumpEntity#update()}（见 {@code Drill}/{@code GenericCrafter}）。</li>
 *     <li>原版用 {@code tile.getLinkedTilesAs(this, drawTiles)} / {@code tile.getLinkedTiles(updateTiles)}
 *         复用两个缓存数组；本项目 {@code Tile} 没有这两个方法，改为等价的私有
 *         {@link #linkedTiles(Tile, Array)}（与 {@code Drill.linkedTiles} 同一套多格范围算法）。</li>
 *     <li>原版 {@code timers++} 的计时器槽位本项目没有，改为实体自带的 {@link Interval}。</li>
 *     <li>原版 {@code layer = Layer.overlay}：本项目 {@code Block} 没有 {@code layer} 字段（渲染不分层），刻意降级。</li>
 * </ul>
 * <p>已接线：{@code Block.floating}（浮空放置判定）、{@code Block.drawPlace} / {@code drawPlaceText}
 * （放置预览的水速文字与液体图标）。{@code Liquid.icon(Cicon)} 用 {@code liquid-<name>} 查图集替代，
 * {@code Core.bundle.formatFloat} 用 {@link com.phoenix.game.core.Strings} 替代。
 * <p>注意：{@code Floor.liquidDrop} 与 {@code TileEntity.efficiency()} 都已补齐
 * （见 {@link com.phoenix.game.world.Floor#liquidDrop}），{@code maxPump} 已按原版乘 {@code efficiency()}。
 */
public class Pump extends LiquidBlock{
    protected final Array<Tile> drawTiles = new Array<>();
    protected final Array<Tile> updateTiles = new Array<>();

    /** 内容检查计时器槽位（原版 {@code timers++}）。 */
    public final int timerContentCheck = 0;

    /** Pump amount, total. */
    protected float pumpAmount = 1f;

    public Pump(String name){
        super(name);
        //TODO 刻意降级：原版 layer = Layer.overlay。graphics/Layer 枚举已存在，但 Block 没有 layer 字段、
        //     Renderer 也不按层排序，故无法设置（缺 Renderer 分层渲染设施）
        //layer = Layer.overlay;
        //group 由 LiquidBlock 设为 BlockGroup.liquids
        floating = true;
        entityType = PumpEntity::new;
    }

    @Override
    public void load(){
        super.load();

        //原版是 Core.atlas.find(...)（缺图返回 error 区域）；本项目用 findRegion（缺图返回 null）
        liquidRegion = Core.atlas == null ? null : Core.atlas.findRegion("pump-liquid");
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(BlockStat.output, 60f * pumpAmount, StatUnit.liquidSecond);
    }

    /**
     * 绘制本体 + 液体存量。
     * <p>⚠ <b>坐标口径修正</b>：原版 {@code Draw.rect(region, tile.drawx(), tile.drawy())} 里
     * {@code drawx()} 在原版坐标口径下**就是方块中心**；本项目 {@code Tile.drawx()} 少算了半格
     * （= {@code centerX(tile) - tilesize/2f}），而 {@code Draw.rect} 是中心语义 → 直接用会整体偏左下 4。
     * 故改用 {@code Block.centerX/centerY(tile)}（等价于 {@code drawx() + tilesize/2f}）。
     */
    @Override
    public void draw(Tile tile){
        if(region != null) Draw.rect(region, centerX(tile), centerY(tile));

        if(tile.entity == null || tile.entity.liquids == null) return;

        Liquid current = tile.entity.liquids.current();
        Draw.color(current.color);
        Draw.alpha(tile.entity.liquids.total() / liquidCapacity);
        if(liquidRegion != null) Draw.rect(liquidRegion, centerX(tile), centerY(tile));
        Draw.color();
    }

    /**
     * 放置预览：显示"预计抽水速度"与液体图标（对应原版 {@code Pump.drawPlace}）。
     * <p>由 {@code InputHandler.drawTop} → {@code Block.drawPlace} 钩子调用。
     */
    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        Tile tile = Vars.world == null ? null : Vars.world.tile(x, y);
        if(tile == null) return;

        float tiles = 0f;
        Liquid liquidDrop = null;

        for(Tile other : linkedTiles(tile, drawTiles)){
            if(isValid(other)){
                liquidDrop = other.floor().liquidDrop;
                tiles++;
            }
        }

        if(liquidDrop != null){
            float width = drawPlaceText(com.phoenix.game.core.Strings.format(
                com.phoenix.game.world.meta.I18n.get("bar.pumpspeed", "{0}/s"),
                com.phoenix.game.core.Strings.fixed(tiles * pumpAmount / size / size * 60f, 0)), x, y, valid);
            float dx = x * tilesize + offset() - width / 2f - 4f, dy = y * tilesize + offset() + size * tilesize / 2f + 5;

            //原版用 Draw.mixcol(Color.darkGray, 1f) 画深色阴影；本项目用 Draw.color 等价替代
            //（Liquid 继承 MappableContent 没有 icon(Cicon)，按 liquid-<name> 查图集）
            TextureRegion icon = Core.atlas == null ? null : Core.atlas.findRegion("liquid-" + liquidDrop.name);
            if(icon != null){
                Draw.color(com.badlogic.gdx.graphics.Color.DARK_GRAY);
                Draw.rect(icon, dx, dy - 1f);
                Draw.color();
                Draw.rect(icon, dx, dy);
            }
        }
    }

    @Override
    public TextureRegion[] generateIcons(){
        if(Core.atlas == null) return super.generateIcons();
        return new TextureRegion[]{Core.atlas.findRegion(name)};
    }

    @Override
    public boolean canPlaceOn(Tile tile){
        if(isMultiblock()){
            Liquid last = null;
            for(Tile other : linkedTiles(tile, drawTiles)){
                if(other.floor().liquidDrop == null)
                    continue;
                if(other.floor().liquidDrop != last && last != null)
                    return false;
                last = other.floor().liquidDrop;
            }
            return last != null;
        }else{
            return isValid(tile);
        }
    }

    /** @return 该格是否是可抽取的液体地板（对应原版 {@code Pump.isValid}）。 */
    protected boolean isValid(Tile tile){
        return tile != null && tile.floor().liquidDrop != null;
    }

    /** 覆盖面瓦片（与 {@code Tile.setBlock} 的多格铺开范围一致，支持偶数尺寸）。 */
    private Array<Tile> linkedTiles(Tile tile, Array<Tile> out){
        out.clear();
        int offset = -(size - 1) / 2;
        for(int dx = 0; dx < size; dx++){
            for(int dy = 0; dy < size; dy++){
                Tile other = tile.getNearby(dx + offset, dy + offset);
                if(other != null) out.add(other);
            }
        }
        return out;
    }

    /** 抽水机实体：从脚下液体地板抽液（原版 {@code Pump.update(Tile)}）。 */
    public class PumpEntity extends TileEntity{
        /** 内容检查计时器（对应原版 {@code tile.entity.timer.get(timerContentCheck, 10)}）。 */
        public final Interval timer = new Interval(1);

        @Override
        public void update(){
            float tiles = 0f;
            Liquid liquidDrop = null;

            if(isMultiblock()){
                for(Tile other : linkedTiles(tile, updateTiles)){
                    //注意：TileEntity 现在实现了 TargetTrait（带无参 isValid()），会把外层 Pump.isValid(Tile) 遮住
                    if(Pump.this.isValid(other)){
                        liquidDrop = other.floor().liquidDrop;
                        tiles++;
                    }
                }
            }else{
                tiles = 1f;
                liquidDrop = tile.floor().liquidDrop;
            }

            if(cons.valid() && liquidDrop != null){
                //乘 TileEntity.efficiency()（超频/电力效率），与原版一致
                float maxPump = Math.min(liquidCapacity - liquids.total(), tiles * pumpAmount * delta() / size / size) * efficiency();
                liquids.add(liquidDrop, maxPump);
            }

            if(liquids.currentAmount() > 0f && timer.get(timerContentCheck, 10)){
                useContent(tile, liquids.current());
            }

            tryDumpLiquid(tile, liquids.current());
        }
    }
}
