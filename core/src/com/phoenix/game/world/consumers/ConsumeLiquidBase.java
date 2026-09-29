package com.phoenix.game.world.consumers;

import com.phoenix.game.entities.type.TileEntity;

/**
 * 液体消耗器基类。参照 Mindustry mindustry.world.consumers.ConsumeLiquidBase 移植。
 * <p>只描述"每帧用多少液体"与"显示用的时间周期"，具体接受哪种液体由子类（ConsumeLiquid /
 * ConsumeLiquidFilter）决定。
 */
public abstract class ConsumeLiquidBase extends Consume{
    /** amount used per frame */
    public final float amount;
    /**
     * How much time is taken to use this liquid, in ticks. Used only for visual purposes.
     * Example: a normal ConsumeLiquid with 10/s and a 10 second timePeriod would display as "100 seconds".
     * Without a time override, it would display as "10 liquid/second".
     * This is used for generic crafters.
     */
    public float timePeriod = 60;

    public ConsumeLiquidBase(float amount){
        this.amount = amount;
    }

    @Override
    public ConsumeType type(){
        return ConsumeType.liquid;
    }

    protected float use(TileEntity entity){
        return Math.min(amount * entity.delta(), entity.block.liquidCapacity);
    }
}
