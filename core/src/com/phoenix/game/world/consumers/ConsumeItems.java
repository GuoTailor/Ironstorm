package com.phoenix.game.world.consumers;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.ItemStack;

/**
 * 物品消耗器。参照 Mindustry mindustry.world.consumers.ConsumeItems 移植。
 * <p>替换原先散落在各方块里的 {@code inputItem[]} / {@code ammoItem} 硬编码字段。
 */
public class ConsumeItems extends Consume{
    /** 每次消耗的物品清单。 */
    public final ItemStack[] items;

    public ConsumeItems(ItemStack[] items){
        this.items = items;
    }

    @Override
    public ConsumeType type(){
        return ConsumeType.item;
    }

    @Override
    public void trigger(TileEntity entity){
        if(entity.items == null) return;
        for(ItemStack stack : items){
            entity.items.remove(stack.item, stack.amount);
        }
    }

    @Override
    public boolean valid(TileEntity entity){
        if(entity.items == null) return false;
        for(ItemStack stack : items){
            if(!entity.items.has(stack.item, stack.amount)) return false;
        }
        return true;
    }

    /** @return 本消耗器是否消耗该物品（供炮塔的 acceptItem 过滤弹药）。 */
    public boolean hasItem(Item item){
        for(ItemStack stack : items){
            if(stack.item == item) return true;
        }
        return false;
    }
}
