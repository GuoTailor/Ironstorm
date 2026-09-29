package com.phoenix.game.world.blocks.power;

import com.phoenix.game.type.Item;
import com.phoenix.game.type.Liquid;

/**
 * 单一类型输入发电机。参照 Mindustry mindustry.world.blocks.power.SingleTypeGenerator 移植。
 * <p>在 {@link ItemLiquidGenerator} 的基础上把"燃料效率"固定：任何物品的效率都是 1，任何液体的效率都是 0
 * —— 即只烧物品、且不看物品属性（如燃烧发电机看可燃性、衰变发电机看放射性）。原版用它做
 * {@code differential-generator} 这类"任意物品同效率"的发电机。
 *
 * <p><b>本类当前是孤立类（不在继承链上）</b>：本工程的发电机继承链已扁平化，
 * 现有发电机都直接 {@code extends Block}（详见 {@link PowerGenerator} 类注释），
 * 所以本类暂时不会被实例化，只作为后续收敛继承链那一批的落点。
 */
public class SingleTypeGenerator extends ItemLiquidGenerator{

    public SingleTypeGenerator(String name){
        super(name);
        defaults = true;
    }

    @Override
    protected float getItemEfficiency(Item item){
        return 1f;
    }

    @Override
    protected float getLiquidEfficiency(Liquid liquid){
        return 0f;
    }
}
