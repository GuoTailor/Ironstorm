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

    /**
     * 把消耗清单里的物品登记进"本方块接受的物品"位图（对应原版 {@code ConsumeItems.applyItemFilter}）。
     * <p>⚠ 登记后**当前没有消费点**：{@code BlockStorage.acceptItem} 刻意不接这条判据（见该处注释与 转换.md）。
     * 补这个方法是为了消除脆弱结构 —— 否则将来若要按原版恢复那条判据，依赖 ConsumeItems 的方块
     * （油压机的沙、内燃发电机的煤等）会因位图是空的而**突然全部停摆**。
     */
    @Override
    public void applyItemFilter(com.badlogic.gdx.utils.Bits filter){
        for(ItemStack stack : items){
            filter.set(stack.item.id);
        }
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

    @Override
    public void display(com.phoenix.game.world.meta.BlockStats stats){
        //加成输入（可选消耗）归"可选的增强物品"，否则算"输入"（对应原版 ConsumeItems.display）
        stats.add(booster ? com.phoenix.game.world.meta.BlockStat.booster
                : com.phoenix.game.world.meta.BlockStat.input,
            new com.phoenix.game.world.meta.values.ItemListValue(items));
    }
}
