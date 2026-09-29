package com.phoenix.game.world.blocks.sandbox;

import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.power.PowerNode;
import com.phoenix.game.world.meta.BuildVisibility;

/**
 * 电力源（沙盒）。参照 Mindustry mindustry.world.blocks.sandbox.PowerSource 移植。
 * <p>继承电力节点（因此也能远距离连其他节点），并恒定输出 {@link #powerProduction} 的电。
 */
public class PowerSource extends PowerNode{
    public PowerSource(String name){
        super(name);
        //沙盒可见性（对应原版 Blocks.java:1824 requirements(Category.power, BuildVisibility.sandboxOnly, ...)；
        //类别/解锁在 content.Blocks 里设）。T2 批：替代原先的 sandboxOnly 布尔补丁。
        buildVisibility = BuildVisibility.sandboxOnly;
        maxLinks = 100;
        powerProduction = 10000f;
    }

    @Override
    public float getPowerProduction(Tile tile){
        return powerProduction;
    }
}
