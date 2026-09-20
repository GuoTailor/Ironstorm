package com.phoenix.game.world.blocks.power;

import com.phoenix.game.type.Item;

/**
 * 放射性衰变发电机（rtg-generator）。参照 Mindustry mindustry.world.blocks.power.DecayGenerator 移植。
 * <p>靠放射性物品衰变发电，效率取物品的放射性：钍（1.0）满功率、相织物（0.6）六成功率。
 * 单份燃料烧得久（{@link #itemDuration} 很长）但功率低，优点是无需维护、不会停机。
 */
public class DecayGenerator extends ItemGenerator{
    public DecayGenerator(String name){
        super(name);
        health = 160;
    }

    @Override
    public float getItemEfficiency(Item item){
        return item.radioactivity;
    }
}
