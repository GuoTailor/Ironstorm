package com.phoenix.game.world.blocks.production;

import com.phoenix.game.core.Time;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.StatUnit;

/**
 * 合成器。参照 Mindustry mindustry.world.blocks.production.GenericCrafter 移植。
 * <p>输入配方与耗电统一声明在 {@code consumes} 里（{@code consumes.items(...)} / {@code consumes.power(...)}），
 * 运行时用 {@code entity.cons.valid()} 判断能否开工、{@code cons.trigger()} 扣料
 * ——不再各自维护 {@code inputItem} / {@code powerConsumption} 字段。
 * <p>产出到自身库存并尝试交给相邻建筑；输入物留在库存里等配方凑齐。
 * <p>未移植：craftEffect、warmup 动画。
 * <p>本批（F1c）补上了 {@code outputLiquid} 的产出：原版 {@code GenericCrafter.update} 在合成完成时
 * 会 {@code handleLiquid(tile, tile, outputLiquid.liquid, outputLiquid.amount)}，此前本项目只处理
 * {@code outputItem} → melter / spore-press 这类"物品进、液体出"的方块只耗不产。这里按
 * {@link LiquidConverter} 的同款写法补上（null 守卫，对 outputLiquid == null 的既有方块零影响）。
 */
public class GenericCrafter extends Block{
    /** 产出。 */
    public ItemStack outputItem;
    /** 液体产出（对应原版 GenericCrafter.outputLiquid）；非 null 时 init() 会打开 outputsLiquid。 */
    public com.phoenix.game.type.LiquidStack outputLiquid;
    /** 合成一次所需帧数。 */
    public float craftTime = 60f;

    public GenericCrafter(String name){
        super(name);
        update = true;
        solid = true;
        health = 60;
        hasItems = true;
        itemCapacity = 10;
        //不在这里写 hasPower：Block.init() 会按 consumes 里有没有电力消耗器自动开启，
        //这样"无电配方"（如石墨压机）不会平白多出一个电力模块
        entityType = CraftEntity::new;
    }

    @Override
    public void init(){
        outputsLiquid = outputLiquid != null;
        super.init();
    }

    @Override
    public void setStats(){
        super.setStats();

        //对应原版 GenericCrafter.setStats：制造分类下显示产出与生产时间（输入由 ConsumeItems 登记）
        stats.add(BlockStat.productionTime, craftTime / 60f, StatUnit.seconds);
        if(outputItem != null){
            stats.add(BlockStat.output, outputItem);
        }
        if(outputLiquid != null){
            stats.add(BlockStat.output, outputLiquid.liquid, outputLiquid.amount, false);
        }
    }

    /**
     * @return 输出未满才消耗输入（对应原版 {@code GenericCrafter.shouldConsume}）。
     * <p>没有这层门槛时，{@code items.add} 不受 {@code itemCapacity} 限制 → 输出会无限堆积、
     * 输入被持续消耗（满仓也不停产）；补上后 {@code ConsumeModule} 在输出满时不再扣料/耗电。
     */
    @Override
    public boolean shouldConsume(Tile tile){
        if(outputItem != null && tile.entity.items.get(outputItem.item) >= itemCapacity){
            return false;
        }
        return outputLiquid == null || !(tile.entity.liquids.get(outputLiquid.liquid) >= liquidCapacity - 0.001f);
    }

    public class CraftEntity extends TileEntity{
        /** 合成进度（帧）。 */
        public float progress;
        /**
         * 开工平滑值（对应原版 {@code GenericCrafterEntity.warmup}，原版 GenericCrafter.java:153）：
         * 0 = 停机、1 = 稳定运转，输入不足时按原版 :101 缓慢回落到 0。
         * <p>消费者：{@code GenericSmelter.drawLight} 的发光强度（原版 GenericSmelter.java:52-56）。
         * <p>⚠ 刻意**不存档**：原版 write/read 存它，但本项目加字段会让 `SaveIO` 的实体序列化错位、
         * 必须升 `SaveIO.version`（所有旧档作废）—— 而 warmup 只影响视觉（读档后几秒内自然 lerp 回 1），
         * 不值得这个代价。
         */
        public float warmup;

        @Override
        public void update(){
            //输入不足或没电：暂停（进度清零），但产出仍要外送
            if(!cons.valid()){
                progress = 0f;
                //原版 GenericCrafter.java:101 entity.warmup = Mathf.lerp(entity.warmup, 0f, 0.02f)
                warmup = Mathf.lerp(warmup, 0f, 0.02f);
                dumpOutput();
                return;
            }

            //原版 GenericCrafter.java:95 entity.warmup = Mathf.lerpDelta(entity.warmup, 1f, 0.02f)
            warmup = Mathf.lerpDelta(warmup, 1f, 0.02f);

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

                //液体产出（对应原版 handleLiquid(tile, tile, outputLiquid.liquid, outputLiquid.amount)；
                //写法同 LiquidConverter，库存满则少存或不存）
                if(outputLiquid != null){
                    float use = Math.min(outputLiquid.amount, liquidCapacity - liquids.get(outputLiquid.liquid));
                    if(use > 0f){
                        useContent(tile, outputLiquid.liquid);
                        liquids.add(outputLiquid.liquid, use);
                    }
                }
            }

            dumpOutput();
        }

        /** 只外送产出物，输入物留在库存中等待配方凑齐。 */
        private void dumpOutput(){
            if(outputItem != null){
                tryDump(tile, outputItem.item);
            }
            if(outputLiquid != null){
                tryDumpLiquid(tile, outputLiquid.liquid);
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
