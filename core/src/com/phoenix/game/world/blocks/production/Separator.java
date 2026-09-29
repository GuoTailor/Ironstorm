package com.phoenix.game.world.blocks.production;

import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.util.ArcAnnotate.*;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.consumers.ConsumeLiquidBase;
import com.phoenix.game.world.consumers.ConsumeType;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.StatUnit;
import com.phoenix.game.world.meta.values.ItemFilterValue;

/**
 * 分离机。参照 Mindustry mindustry.world.blocks.production.Separator 移植。
 * <p>从"一个输入物品 + 一种输入液体"里按权重随机抽出一种产物。
 * <p>刻意偏离（本项目结构）：原版逻辑写在 {@code Block.update(Tile)} 里、实体复用 {@code GenericCrafterEntity}，
 * 本项目统一把逻辑写在实体内部类里（见 {@code Drill}/{@code GenericCrafter}），
 * 且 {@code GenericCrafterEntity} 在本项目是 {@code GenericCrafter.CraftEntity}（非静态内部类，
 * 无法在 {@code Block} 子类里实例化），所以这里自定义了等价的 {@link SeparatorEntity}。
 * <p>说明：{@code Block.reg(String)/reg(int)}（贴图索引）、{@code Block.useContent}、
 * {@code ItemFilterValue}（统计显示）都已存在并已接线；仅 {@code Block.timerDump/dumpTime}
 * （定时丢料槽位）刻意降级为每帧 {@code tryDump}（本项目 {@code Block} 无 timers 槽位，同 {@code GenericCrafter}）。
 */
public class Separator extends Block{
    public @NonNull ItemStack[] results;
    public float craftTime;

    public int liquidRegion, spinnerRegion;
    public float spinnerSpeed = 3f;

    public Separator(String name){
        super(name);
        update = true;
        solid = true;
        hasItems = true;
        hasLiquids = true;

        liquidRegion = reg("-liquid");
        spinnerRegion = reg("-spinner");
        entityType = SeparatorEntity::new;
    }

    @Override
    public void setStats(){
        if(consumes.has(ConsumeType.liquid)){
            ConsumeLiquidBase cons = consumes.get(ConsumeType.liquid);
            cons.timePeriod = craftTime;
        }

        super.setStats();

        stats.add(BlockStat.output, new ItemFilterValue(item -> {
            for(ItemStack i : results){
                if(item == i.item) return true;
            }
            return false;
        }));

        stats.add(BlockStat.productionTime, craftTime / 60f, StatUnit.seconds);
    }

    /**
     * @return 库存没满才消耗输入。
     * <p>对应原版 {@code Separator.shouldConsume}；{@code Block.shouldConsume} 钩子已补齐，
     * {@code ConsumeModule.update()} 会调用它（库存满时不再扣物品/液体）。
     */
    @Override
    public boolean shouldConsume(Tile tile){
        return tile.entity.items.total() < itemCapacity;
    }

    @Override
    public void draw(Tile tile){
        super.draw(tile);

        SeparatorEntity entity = (SeparatorEntity)tile.entity;

        Draw.color(tile.entity.liquids.current().color);
        Draw.alpha(tile.entity.liquids.total() / liquidCapacity);
        Draw.rect(reg(liquidRegion), centerX(tile), centerY(tile));

        //arc 的 Draw.reset() 在本项目等价于 Draw.color()（复位批次颜色）
        Draw.color();
        //libgdx 的 TextureAtlas 没有 isFound（arc 才有）→ findRegion 缺图返回 null，等价判空
        if(reg(spinnerRegion) != null){
            Draw.rect(reg(spinnerRegion), centerX(tile), centerY(tile), entity.totalProgress * spinnerSpeed);
        }
    }

    /** 分离机实体：进度 0~1（合成一次算 1），另有转子动画用的 warmup/totalProgress。 */
    public class SeparatorEntity extends TileEntity{
        public float progress, warmup, totalProgress;

        @Override
        public void update(){
            totalProgress += warmup * delta();

            if(cons.valid()){
                //对应原版 getProgressIncrease(entity, craftTime) = delta() * 供电率 / craftTime
                progress += delta() * (power == null ? 1f : power.status) / craftTime;
                warmup = Mathf.lerpDelta(warmup, 1f, 0.02f);
            }else{
                warmup = Mathf.lerpDelta(warmup, 0f, 0.02f);
            }

            if(progress >= 1f){
                progress = 0f;
                int sum = 0;
                for(ItemStack stack : results) sum += stack.amount;

                int i = Mathf.random(sum);
                int count = 0;
                Item item = null;

                //注：产物随机 → 两端各自 random 可能不同步（原版同样如此）
                for(ItemStack stack : results){
                    if(i >= count && i < count + stack.amount){
                        item = stack.item;
                        break;
                    }
                    count += stack.amount;
                }

                cons.trigger();

                if(item != null && items.get(item) < itemCapacity){
                    //记录"生产过该内容"（战役解锁/统计），对应原版 useContent(tile, item)
                    useContent(tile, item);
                    offloadNear(tile, item);
                }
            }

            //原版按 timerDump/dumpTime 的节拍丢料；本项目 Block 无定时器槽位，改为每帧尝试（同 GenericCrafter）
            tryDump(tile);
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeFloat(progress);
            out.writeFloat(warmup);
            out.writeFloat(totalProgress);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            progress = in.readFloat();
            warmup = in.readFloat();
            totalProgress = in.readFloat();
        }
    }
}
