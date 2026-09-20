package com.phoenix.game.world.blocks.sandbox;

import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.power.PowerNode;

/**
 * 电力源（沙盒）。参照 Mindustry mindustry.world.blocks.sandbox.PowerSource 移植。
 * <p>继承电力节点（因此也能远距离连其他节点），并恒定输出 {@link #powerProduction} 的电。
 */
public class PowerSource extends PowerNode{
    public PowerSource(String name){
        super(name);
        sandboxOnly = true;
        maxLinks = 100;
        powerProduction = 10000f;
    }

    @Override
    public float getPowerProduction(Tile tile){
        return powerProduction;
    }
}
