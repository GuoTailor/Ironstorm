package com.phoenix.game.world.blocks.power;

import com.phoenix.game.type.Item;

/**
 * 燃烧发电机。参照 Mindustry mindustry.world.blocks.power.BurnerGenerator 移植。
 * <p>烧**任何可燃物**发电，效率取物品的可燃性：煤（1.0）满功率，可燃性低于
 * {@link #minItemEfficiency} 的物品不收（原版判据相同）。
 * <p>原版属于 ItemLiquidGenerator（还能烧液体），本工程无液体，故继承 {@link ItemGenerator}。
 */
public class BurnerGenerator extends ItemGenerator{
    public BurnerGenerator(String name){
        super(name);
        health = 150;
    }

    @Override
    public float getItemEfficiency(Item item){
        return item.flammability;
    }
}
