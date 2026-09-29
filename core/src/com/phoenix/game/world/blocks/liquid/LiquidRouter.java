package com.phoenix.game.world.blocks.liquid;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.LiquidBlock;

/**
 * 液体路由器：把收到的液体分给所有邻接（每帧调一次 {@code tryDumpLiquid} 轮询分配）。
 * 参照 Mindustry mindustry.world.blocks.liquid.LiquidRouter 移植。
 * <p>与原版的差异：原版把 {@code update(Tile)} 写在方块上、由 {@code Block.update(Tile)} 调用；
 * 本项目写在实体内部类 {@link LiquidRouterEntity#update()}（见 {@code Drill}/{@code GenericCrafter}）。
 * <p>贴图说明：图集里没有名为 {@code liquid-router} 的单图，只有
 * {@code liquid-router-bottom/-liquid/-top}，所以 {@code region} 会退回 blank，
 * 但 {@code LiquidBlock.draw} 用的是这三层贴图，视觉正常（图标走 {@code generateIcons}）。
 */
public class LiquidRouter extends LiquidBlock{

    public LiquidRouter(String name){
        super(name);
        entityType = LiquidRouterEntity::new;
    }

    @Override
    public boolean acceptLiquid(Tile tile, Tile source, Liquid liquid, float amount){
        return tile.entity.liquids.get(liquid) + amount < liquidCapacity
            && (tile.entity.liquids.current() == liquid || tile.entity.liquids.get(tile.entity.liquids.current()) < 0.2f);
    }

    /** 路由器实体：每帧把库存液体轮询丢给相邻建筑（原版 {@code LiquidRouter.update}）。 */
    public class LiquidRouterEntity extends TileEntity{

        @Override
        public void update(){
            if(liquids.total() > 0.01f){
                tryDumpLiquid(tile, liquids.current());
            }
        }
    }
}
