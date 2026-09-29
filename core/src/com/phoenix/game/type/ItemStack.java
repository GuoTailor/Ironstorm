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

    /**
     * 成对构造物品堆叠数组（对应原版 {@code ItemStack.with}）。
     * <p>格式：{@code with(item1, amount1, item2, amount2, ...)}；数量可以是任何 {@link Number}
     * （原版接受 int/float 字面量，统一按 {@code intValue()} 取整）。
     */
    public static ItemStack[] with(Object... items){
        ItemStack[] stacks = new ItemStack[items.length / 2];
        for(int i = 0; i < items.length; i += 2){
            stacks[i / 2] = new ItemStack((Item)items[i], ((Number)items[i + 1]).intValue());
        }
        return stacks;
    }

    @Override
    public String toString(){
        return amount + "x " + item.name;
    }
}
