package com.phoenix.game.world.blocks.power;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.world.Block;

/** 固定耗电建筑基类。供 Drill/GenericCrafter 等生产方块复用。 */
public class PowerConsumer extends Block{
    public PowerConsumer(String name){
        super(name);
        solid = true;
        update = true;
        hasPower = true;
        powerConsumption = 0.1f;
        health = 60;
        entityType = ConsumerEntity::new;
    }

    public class ConsumerEntity extends TileEntity{
    }
}