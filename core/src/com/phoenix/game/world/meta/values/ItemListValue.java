package com.phoenix.game.world.meta.values;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.core.Scl;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.ui.ItemDisplay;
import com.phoenix.game.world.meta.StatValue;

/**
 * 物品列表（图标 + 数量 + 名称）。对应原版 mindustry.world.meta.values.ItemListValue。
 * <p>建造花费、合成输入/输出都用它。
 */
public class ItemListValue implements StatValue{
    private final ItemStack[] stacks;
    private final boolean displayName;

    public ItemListValue(ItemStack... stacks){
        this(true, stacks);
    }

    public ItemListValue(boolean displayName, ItemStack... stacks){
        this.stacks = stacks;
        this.displayName = displayName;
    }

    @Override
    public void display(Table table){
        for(ItemStack stack : stacks){
            table.add(new ItemDisplay(stack.item, stack.amount, displayName)).padRight(Scl.scl(5f));
        }
    }
}
