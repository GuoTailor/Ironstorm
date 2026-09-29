package com.phoenix.game.world.blocks.power;

import com.phoenix.game.type.Item;
import com.phoenix.game.type.Liquid;

/**
 * 燃烧发电机。参照 Mindustry mindustry.world.blocks.power.BurnerGenerator 移植。
 * <p>烧**任何可燃物**发电，效率取物品的可燃性：煤（1.0）满功率，可燃性低于
 * {@link #minItemEfficiency} 的物品不收（原版判据相同）。
 * <p>原版继承 {@link ItemLiquidGenerator}（照原版构造器传 {@code (true, false, name)}：只烧物品），
 * 但液体分支仍然在 —— 燃气轮机（turbine-generator）就是在本类基础上打开 {@code hasLiquids} 并加耗水器。
 */
public class BurnerGenerator extends ItemLiquidGenerator{
    public BurnerGenerator(String name){
        super(true, false, name);
        //原版 BurnerGenerator/ItemLiquidGenerator 构造器均不设 health（Blocks.java:1100/1113 也未覆盖）
        //→ 走 Block.init 的 size*size*40 默认（combustion-generator size1 = 40、turbine-generator size2 = 160）。
        //⚠ 两者共用本构造器，故不能在此写死 health，否则会同时影响两个方块。
    }

    @Override
    protected float getLiquidEfficiency(Liquid liquid){
        return liquid.flammability;
    }

    @Override
    protected float getItemEfficiency(Item item){
        return item.flammability;
    }
}
