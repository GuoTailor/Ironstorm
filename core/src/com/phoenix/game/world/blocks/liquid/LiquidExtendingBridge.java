package com.phoenix.game.world.blocks.liquid;

import com.phoenix.game.Vars;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.distribution.ExtendingItemBridge;
import com.phoenix.game.world.blocks.distribution.ItemBridge;
import com.phoenix.game.world.meta.BlockGroup;

/**
 * 可伸缩液体桥。参照 Mindustry mindustry.world.blocks.liquid.LiquidExtendingBridge 移植。
 * <p>与 {@link LiquidBridge} 的唯一差别是绘制（桥体从两端伸出，见父类 {@link ExtendingItemBridge}）
 * 与"有电才能达到满速"的坡度；逻辑同样是"把父类的物品输送换成液体输送"。
 * <p>刻意偏离（本项目结构）：原版把逻辑写在 {@code Block.update(Tile)} 里，本项目写在实体内部类
 * （见 {@code ItemBridge.ItemBridgeEntity}），所以这里覆盖 {@link LiquidExtendingBridgeEntity#update()}。
 * <p>依赖已就位：{@code Block.tryDumpLiquid/tryMoveLiquid}、{@code LiquidModule.current()}、
 * {@code BlockGroup.liquids} 均已移植，本类的 update 是真实运行路径。
 */
public class LiquidExtendingBridge extends ExtendingItemBridge{

    public LiquidExtendingBridge(String name){
        super(name);
        hasItems = false;
        hasLiquids = true;
        outputsLiquid = true;
        //分组：液体（对应原版 group = BlockGroup.liquids）
        group = BlockGroup.liquids;

        entityType = LiquidExtendingBridgeEntity::new;
    }

    /** 液体桥不接受物品。 */
    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        return false;
    }

    /** 桥实体：把父类的物品输送换成液体输送（伸缩桥不需要额外判断供电坡度）。 */
    public class LiquidExtendingBridgeEntity extends ItemBridgeEntity{

        @Override
        public void update(){
            time += cycleSpeed * delta();
            time2 += (cycleSpeed - 1f) * delta();

            Tile other = Vars.world == null ? null : Vars.world.tile(link);
            if(!linkValid(tile, other)){
                tryDumpLiquid(tile, liquids.current());
            }else{
                ((ItemBridgeEntity)other.entity).incoming.add(tile.pos());

                if(cons.valid()){
                    uptime = Mathf.lerpDelta(uptime, 1f, 0.04f);
                }else{
                    uptime = Mathf.lerpDelta(uptime, 0f, 0.02f);
                }

                if(uptime >= 0.5f){
                    if(tryMoveLiquid(tile, other, false, liquids.current()) > 0.1f){
                        cycleSpeed = Mathf.lerpDelta(cycleSpeed, 4f, 0.05f);
                    }else{
                        cycleSpeed = Mathf.lerpDelta(cycleSpeed, 1f, 0.01f);
                    }
                }
            }
        }
    }
}
