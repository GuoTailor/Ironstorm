package com.phoenix.game.world.blocks.power;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.func.Boolf;
import com.phoenix.game.world.consumers.ConsumePower;

/**
 * 只在某些时候才耗电的电力消耗器。参照 Mindustry
 * mindustry.world.blocks.power.ConditionalConsumePower 移植。
 * <p>典型用途：方块待机时不该吃电，由 {@code consume} 条件决定本帧要不要申请电量。
 * <p>已接线：{@link ConsumePower#requestedPower(TileEntity)} 已补齐（原版缺失），
 * 本类覆写它；调用链 = {@code PowerGraph.update()} → {@code Block.getPowerNeeded(Tile)}
 * → {@code ConsumePower.requestedPower(TileEntity)}。
 * <p>⚠ 注意：本项目 {@code Consumers} 还没有原版 {@code Consumers.power(usage, Boolf)} 那个
 * 条件耗电注册重载，所以**还没有任何方块注册本类**；本项目的等价做法是方块直接覆写
 * {@code Block.getPowerNeeded}（见 {@code RepairPoint}）。等注册重载补上后本类即自动生效。
 */
public class ConditionalConsumePower extends ConsumePower{
    private final Boolf<TileEntity> consume;

    public ConditionalConsumePower(float usage, Boolf<TileEntity> consume){
        super(usage, 0, false);
        this.consume = consume;
    }

    /** @return 本帧需要申请的电量；条件不成立时为 0。 */
    @Override
    public float requestedPower(TileEntity entity){
        return consume.get(entity) ? usage : 0f;
    }
}
