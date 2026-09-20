package com.phoenix.game.world.blocks.production;

import com.phoenix.game.core.Time;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.world.Block;

/**
 * 合成器。参照 Mindustry mindustry.world.blocks.production.GenericCrafter 移植。
 * <p>输入配方与耗电统一声明在 {@code consumes} 里（{@code consumes.items(...)} / {@code consumes.power(...)}），
 * 运行时用 {@code entity.cons.valid()} 判断能否开工、{@code cons.trigger()} 扣料
 * ——不再各自维护 {@code inputItem} / {@code powerConsumption} 字段。
 * <p>产出到自身库存并尝试交给相邻建筑；输入物留在库存里等配方凑齐。
 * <p>未移植：液体消耗、craftEffect、warmup 动画。
 */
public class GenericCrafter extends Block{
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
        //不在这里写 hasPower：Block.init() 会按 consumes 里有没有电力消耗器自动开启，
        //这样"无电配方"（如石墨压机）不会平白多出一个电力模块
        entityType = CraftEntity::new;
    }

    public class CraftEntity extends TileEntity{
        /** 合成进度（帧）。 */
        public float progress;

        @Override
        public void update(){
            //输入不足或没电：暂停（进度清零），但产出仍要外送
            if(!cons.valid()){
                progress = 0f;
                dumpOutput();
                return;
            }

            //有电则按供电率推进
            float status = power == null ? 1f : power.status;
            progress += Time.delta() * status;

            if(progress >= craftTime){
                progress = 0f;

                //扣输入（配方由 ConsumeItems 表达）
                cons.trigger();

                //产出到自身库存
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

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeFloat(progress);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            progress = in.readFloat();
        }
    }
}
