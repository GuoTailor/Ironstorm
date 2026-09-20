package com.phoenix.game.world.blocks.power;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

/**
 * 地热发电机。参照 Mindustry mindustry.world.blocks.power.ThermalGenerator 移植。
 * <p>发电量 = 覆盖范围内地板热度之和 × {@link #powerProduction}：
 * 压在热源地板上的格子越多、热度越高，发电越多（对应原版 ThermalGenerator.sumHeat）。
 * <p>简化：原版还要求"同队属"与边缘格判定，这里只做范围累加。
 */
public class ThermalGenerator extends Block{
    public ThermalGenerator(String name){
        super(name);
        update = true;
        solid = true;
        health = 100;
        hasPower = true;
        entityType = ThermalEntity::new;
    }

    /** @return 本建筑覆盖范围内所有地板的热度之和。 */
    public float sumHeat(Tile tile){
        int offset = -(size - 1) / 2;
        float sum = 0f;

        for(int dx = 0; dx < size; dx++){
            for(int dy = 0; dy < size; dy++){
                Tile other = com.phoenix.game.Vars.world.tile(tile.x + dx + offset, tile.y + dy + offset);
                if(other != null && other.floor() != null){
                    sum += other.floor().heat;
                }
            }
        }
        return sum;
    }

    @Override
    public float getPowerProduction(Tile tile){
        return sumHeat(tile) * powerProduction;
    }

    public class ThermalEntity extends TileEntity{
    }
}
