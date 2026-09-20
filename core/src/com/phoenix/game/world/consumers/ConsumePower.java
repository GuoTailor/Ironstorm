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

    @Override
    public boolean valid(TileEntity entity){
        //储能型不参与"能否工作"的判定
        if(buffered) return true;
        return status(entity) > 0.0001f;
    }
}
