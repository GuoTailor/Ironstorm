package com.phoenix.game.world.consumers;

import com.phoenix.game.entities.type.TileEntity;

/**
 * 电力消耗器。参照 Mindustry mindustry.world.consumers.ConsumePower 移植。
 * <p>两种形态：
 * <ul>
 *     <li>直接耗电（{@code usage > 0, buffered = false}）：没电就不能工作；</li>
 *     <li>储能（{@code buffered = true}，如电池）：永远"有效"，容量由 {@link #capacity} 决定。</li>
 * </ul>
 */
public class ConsumePower extends Consume{
    /** 每帧耗电（供电率 100% 时）。 */
    public float usage;
    /** 储能容量（电池用）。 */
    public float capacity;
    /** 是否为储能型（电池）。 */
    public boolean buffered;

    public ConsumePower(float usage, float capacity, boolean buffered){
        this.usage = usage;
        this.capacity = capacity;
        this.buffered = buffered;
    }

    @Override
    public ConsumeType type(){
        return ConsumeType.power;
    }

    /** @return 当前供电率（0~1）；没有电力模块时视为满供。 */
    public float status(TileEntity entity){
        return entity.power == null ? 1f : entity.power.status;
    }

    /**
     * @return 本方块本帧申请的电量（对应原版 {@code ConsumePower.requestedPower}）。
     * <p>储能型按"还差多少充满"申请；直接耗电型按 {@code shouldConsume} 决定本帧要不要申请
     * （不生产时不占电网需求，与原版一致）。
     * <p>调用点：{@code Block.getPowerNeeded(Tile)} —— 即 {@code PowerGraph} 每帧统计耗电的入口。
     * <p>⚠ 本项目的电池充放电由 {@code PowerGraph} 单独处理，所以 {@code Block.getPowerNeeded}
     * 只会用到非储能分支；储能分支保留以对齐原版（供未来 PowerGraph 收敛用）。
     */
    public float requestedPower(TileEntity entity){
        if(entity == null || entity.tile == null) return 0f;
        if(buffered){
            return (1f - status(entity)) * capacity;
        }else{
            return usage * (entity.block().shouldConsume(entity.tile) ? 1f : 0f);
        }
    }

    @Override
    public boolean valid(TileEntity entity){
        //储能型不参与"能否工作"的判定
        if(buffered) return true;
        return status(entity) > 0.0001f;
    }

    @Override
    public void display(com.phoenix.game.world.meta.BlockStats stats){
        //储能型显示"能量容量"，耗电型显示"能量使用"（每秒 = 每帧 × 60，对应原版 usage * 60f）
        if(buffered){
            stats.add(com.phoenix.game.world.meta.BlockStat.powerCapacity, capacity,
                com.phoenix.game.world.meta.StatUnit.none);
        }else{
            stats.add(com.phoenix.game.world.meta.BlockStat.powerUse, usage * 60f,
                com.phoenix.game.world.meta.StatUnit.powerSecond);
        }
    }
}
