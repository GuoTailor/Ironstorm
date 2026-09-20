package com.phoenix.game.world.blocks.production;

import com.phoenix.game.core.Time;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.world.Block;

/**
 * 合成器。参照 Mindustry mindustry.world.blocks.production.GenericCrafter 最小移植。
 * <p>输入从自身库存取（由传送带/相邻建筑送入），合成 craftTime 帧后产出到自身库存并尝试交给相邻建筑。
 * 未移植：电力/液体消耗、craftEffect、warmup 动画。
 */
public class GenericCrafter extends Block{
    /** 输入配方。 */
    public ItemStack[] inputItem;
    /** 产出。 */
    public ItemStack outputItem;
    /** 合成一次所需帧数。 */
    public float craftTime = 60f;

    public GenericCrafter(String name){
        super(name);
        update = true;
        solid = true;
        health = 60;
        hasItems = true;
        itemCapacity = 20;
        hasPower = true;
        powerConsumption = 0.12f;
        entityType = CraftEntity::new;
    }

    public class CraftEntity extends TileEntity{
        /** 合成进度（帧）。 */
        public float progress;

        @Override
        public void update(){
            float status = power == null ? 1f : power.status;
            if(status <= 0.001f){
                return;
            }

            //输入不足则暂停（输入留在库存中，不得外送，否则配方永远凑不齐）
            if(!hasInputs()){
                progress = 0f;
                dumpOutput();
                return;
            }

            progress += Time.delta() * status;
            if(progress >= craftTime){
                progress = 0f;

                //扣输入
                for(ItemStack stack : inputItem){
                    items.remove(stack.item, stack.amount);
                }
                //产出到自身库存，再尝试交给相邻建筑
                if(outputItem != null){
                    items.add(outputItem.item, outputItem.amount);
                }
            }

            dumpOutput();
        }

        /** 只外送产出物，输入物留在库存中等待配方凑齐。 */
        private void dumpOutput(){
            if(outputItem != null){
                tryDump(tile, outputItem.item);
            }
        }

        /** @return 自身库存是否满足全部输入配方。 */
        private boolean hasInputs(){
            if(inputItem == null) return true;
            for(ItemStack stack : inputItem){
                if(!items.has(stack.item, stack.amount)) return false;
            }
            return true;
        }
    }
}
