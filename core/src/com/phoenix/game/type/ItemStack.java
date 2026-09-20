package com.phoenix.game.type;

/**
 * 物品堆叠（物品 + 数量）。参照 Mindustry mindustry.type.ItemStack 移植。
 */
public class ItemStack{
    public final Item item;
    public final int amount;

    public ItemStack(Item item, int amount){
        this.item = item;
        this.amount = amount;
    }

    @Override
    public String toString(){
        return amount + "x " + item.name;
    }
}
