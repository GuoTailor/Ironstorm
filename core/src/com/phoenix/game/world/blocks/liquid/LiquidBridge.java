package com.phoenix.game.world.blocks.liquid;

import com.phoenix.game.Vars;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.distribution.ItemBridge;
import com.phoenix.game.world.meta.BlockGroup;

/**
 * 液体桥：两台桥之间直接输送液体。参照 Mindustry mindustry.world.blocks.liquid.LiquidBridge 移植。
 * <p>与物品桥共用连接判定（{@link ItemBridge#linkValid}），只是把"送物品"换成"送液体"。
 * <p>刻意偏离（本项目结构）：原版把逻辑写在 {@code Block.update(Tile)} 里，本项目写在实体内部类
 * （见 {@code ItemBridge.ItemBridgeEntity}），所以这里覆盖 {@link LiquidBridgeEntity#update()}。
 * <p>依赖已就位：{@code Block.tryDumpLiquid/tryMoveLiquid}、{@code LiquidModule.current()}、
 * {@code TileEntity.efficiency()}、{@code BlockGroup.liquids} 均已移植，本类的 update 是真实运行路径。
 */
public class LiquidBridge extends ItemBridge{

    public LiquidBridge(String name){
        super(name);
        hasItems = false;
        hasLiquids = true;
        outputsLiquid = true;
        //分组：液体（对应原版 group = BlockGroup.liquids）
        group = BlockGroup.liquids;

        entityType = LiquidBridgeEntity::new;
    }

    /** 液体桥不接受物品。 */
    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        return false;
    }

    /** 桥实体：把父类的物品输送换成液体输送。 */
    public class LiquidBridgeEntity extends ItemBridgeEntity{

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
                    float alpha = 0.04f;
                    if(hasPower){
                        alpha *= efficiency(); // Exceed boot time unless power is at max.
                    }
                    uptime = Mathf.lerpDelta(uptime, 1f, alpha);
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
