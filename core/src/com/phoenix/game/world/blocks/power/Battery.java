package com.phoenix.game.world.blocks.power;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.world.Block;

/**
 * 电池。参照 Mindustry mindustry.world.blocks.power.Battery 移植。
 * <p>用 {@code consumes.powerBuffered(capacity)} 声明储能容量；
 * {@link PowerGraph} 会在电量富余时充入、不足时取出，电量记在 {@code entity.power.stored}。
 * <p>大电池只是容量更大的实例（原版 battery-large 走同一个类）。
 */
public class Battery extends Block{
    public Battery(String name, float capacity){
        super(name);
        update = true;
        solid = true;
        health = 150;
        hasPower = true;
        consumes.powerBuffered(capacity);
        entityType = BatteryEntity::new;
    }

    /** 电池实体：储量存在 PowerModule.stored，无额外状态。 */
    public class BatteryEntity extends TileEntity{
    }
}
