package com.phoenix.game.world.blocks.power;

import com.phoenix.game.entities.type.TileEntity;

/**
 * 电池。参照 Mindustry mindustry.world.blocks.power.Battery 移植。
 * <p>用 {@code consumes.powerBuffered(capacity)} 声明储能容量；
 * {@link PowerGraph} 会在电量富余时充入、不足时取出，电量记在 {@code entity.power.stored}。
 * <p>大电池只是容量更大的实例（原版 battery-large 走同一个类）。
 * <p>收敛继承链：原版 {@code extends PowerDistributor}（→ {@code PowerBlock}，后者负责 update/solid/hasPower/group）。
 */
public class Battery extends PowerDistributor{
    public Battery(String name, float capacity){
        super(name);
        //原版 Battery.java 构造器不设 health（Blocks.java:1089/1094 也未覆盖）→ 走 Block.init 的 size*size*40 默认
        //（battery size1 = 40、battery-large size3 = 360）；此处不再显式赋值，交给 init() 补默认。
        consumes.powerBuffered(capacity);
        entityType = BatteryEntity::new;
    }

    /** 电池实体：储量存在 PowerModule.stored，无额外状态。 */
    public class BatteryEntity extends TileEntity{
    }
}
