package com.phoenix.game.world.blocks.distribution;

/**
 * 钛传送带。参照 Mindustry mindustry.content.Blocks.titaniumConveyor 移植。
 * <p>比普通传送带快（speed 0.03 → 0.08），血量更高。
 */
public class TitaniumConveyor extends Conveyor{
    public TitaniumConveyor(String name){
        super(name);
        speed = 0.08f;
        displayedSpeed = 10f;
        health = 65;
    }
}
