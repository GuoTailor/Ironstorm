package com.phoenix.game.world.blocks.liquid;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.LiquidBlock;
import com.phoenix.game.world.meta.BlockStat;

/**
 * 液体交叉口：把收到的液体转发到"来源的反方向"（让两条垂直的导管可以交叉而过）。
 * 参照 Mindustry mindustry.world.blocks.liquid.LiquidJunction 移植。
 * <p>刻意偏离（本项目结构）：
 * <ul>
 *     <li>原版 {@code setBars()} 里 {@code bars.remove("liquid")}：本项目 {@code Block} 没有 {@code bars} 字段
 *         （{@code BlockBars} 尚未接线），未移植。</li>
 *     <li>原版 {@code draw(Tile)} 用 {@code Draw.rect(name, ...)}；本项目贴图存在 {@code region} 字段里。</li>
 * </ul>
 * <p><b>依赖已就位</b>：{@code Block.getLiquidDestination} 已补上，且
 * {@code Block.tryDumpLiquid} / {@code tryMoveLiquid} 都会调用它（照原版）→ 本类的
 * {@link #getLiquidDestination} 现在是真正的虚覆写，转发走 {@code next.block().getLiquidDestination(...)}。
 * <p>⚠ 交叉口自己**不接受**液体（它没有液体消耗器，{@code consumes.liquidfilters} 为空，
 * {@code Block.acceptLiquid} 会返回 false）—— 液流是靠上面的重定向**绕过**它直接送到对侧的，与原版一致。
 */
public class LiquidJunction extends LiquidBlock{

    public LiquidJunction(String name){
        super(name);
        entityType = LiquidJunctionEntity::new;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.remove(BlockStat.liquidCapacity);
    }

    @Override
    public void draw(Tile tile){
        if(region != null) Draw.rect(region, tile.worldx(), tile.worldy());
    }

    @Override
    public TextureRegion[] generateIcons(){
        if(Core.atlas == null) return super.generateIcons();
        return new TextureRegion[]{Core.atlas.findRegion(name)};
    }

    /** 液体流向下一格（对应原版 {@code LiquidJunction.getLiquidDestination}）。 */
    @Override
    public Tile getLiquidDestination(Tile tile, Tile source, Liquid liquid){
        int dir = source.relativeTo(tile.x, tile.y);
        dir = (dir + 4) % 4;
        Tile next = tile.getNearbyLink(dir);
        if(next == null || (!next.block().acceptLiquid(next, tile, liquid, 0f) && !(next.block() instanceof LiquidJunction))){
            return tile;
        }
        return next.block().getLiquidDestination(next, tile, liquid);
    }

    /** 交叉口实体：只为承载液体模块（原版用 {@code Block} 默认的 {@code TileEntity}）。 */
    public class LiquidJunctionEntity extends TileEntity{
    }
}
