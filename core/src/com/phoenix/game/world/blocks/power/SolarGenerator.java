package com.phoenix.game.world.blocks.power;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

/** 固定功率太阳能发电机。参照 Mindustry SolarGenerator 最小移植。 */
public class SolarGenerator extends Block{
    public SolarGenerator(String name){
        super(name);
        solid = true;
        update = true;
        health = 80;
        hasPower = true;
        powerProduction = 0.35f;
        entityType = SolarEntity::new;
    }

    /** 太阳能实体，无额外状态。 */
    public class SolarEntity extends TileEntity{
    }
}
