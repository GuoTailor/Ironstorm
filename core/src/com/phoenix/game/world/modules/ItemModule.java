package com.phoenix.game.world.modules;

import com.phoenix.game.content.Items;
import com.phoenix.game.type.Item;

/**
 * 物品存储模块。参照 Mindustry mindustry.world.modules.ItemModule 最小移植。
 * <p>按物品 id 索引的 int 数组；容量无上限（演示期共享库存）。
 */
public class ItemModule{
    private final int[] items = new int[Items.all.size];
    private int total;
    /** 轮转起点：避免每次都取同一种物品（对应原版 ItemModule.takeRotation）。 */
    private int takeRotation;

    /** @return 某物品数量。 */
    public int get(Item item){
        return items[item.id];
    }

    /** 设置某物品数量。 */
    public void set(Item item, int amount){
        total += amount - items[item.id];
        items[item.id] = amount;
    }

    /** 增加某物品。 */
    public void add(Item item, int amount){
        set(item, get(item) + amount);
    }

    /** 减少某物品（不小于 0）。 */
    public void remove(Item item, int amount){
        set(item, Math.max(0, get(item) - amount));
    }

    /** @return 是否有足量该物品。 */
    public boolean has(Item item, int amount){
        return get(item) >= amount;
    }

    /** @return 库存总量。 */
    public int total(){
        return total;
    }

    /** 清空全部库存（对应原版 ItemModule.clear）。 */
    public void clear(){
        java.util.Arrays.fill(items, 0);
        total = 0;
    }

    /**
     * 取走任意一个物品（对应原版 ItemModule.take）。
     * <p>用轮转起点避免连续取到同一种，让多种物品都能被运出去。
     * @return 取到的物品；库存为空返回 null
     */
    public Item take(){
        for(int i = 0; i < items.length; i++){
            int index = (i + takeRotation) % items.length;
            if(items[index] > 0){
                items[index]--;
                total--;
                takeRotation = index + 1;
                return Items.all.get(index);
            }
        }
        return null;
    }

    /** @return 库存里的任意一种物品（不取走）；空返回 null。 */
    public Item first(){
        for(int i = 0; i < items.length; i++){
            if(items[i] > 0) return Items.all.get(i);
        }
        return null;
    }
}
