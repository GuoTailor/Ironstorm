package com.phoenix.game.world.blocks.power;

import com.phoenix.game.type.Item;

/**
 * 放射性衰变发电机（rtg-generator）。参照 Mindustry mindustry.world.blocks.power.DecayGenerator 移植。
 * <p>靠放射性物品衰变发电，效率取物品的放射性：钍（1.0）满功率、相织物（0.6）六成功率。
 * 单份燃料烧得久（{@link #itemDuration} 很长）但功率低，优点是无需维护、不会停机。
 * <p>原版继承 {@link ItemLiquidGenerator}：构造器照原版传 {@code (true, false, name)} 后再显式写一遍
 * {@code hasItems = true; hasLiquids = false;}（原版就这么写的）；液体效率用基类的 0，
 * 因为 {@code hasLiquids = false} 时液体分支根本不会进。
 */
public class DecayGenerator extends ItemLiquidGenerator{
    public DecayGenerator(String name){
        super(true, false, name);
        hasItems = true;
        hasLiquids = false;
        health = 160;
    }

    @Override
    protected float getItemEfficiency(Item item){
        return item.radioactivity;
    }
}
