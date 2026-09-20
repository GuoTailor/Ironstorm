package com.phoenix.game.world.blocks.distribution;

/**
 * 可伸缩物品桥。参照 Mindustry mindustry.world.blocks.distribution.ExtendingItemBridge 移植。
 * <p>与 {@link ItemBridge} 的唯一差别是**绘制**：桥体从两端向中间伸出，长度随 {@code uptime} 变化。
 * <p>TODO 本项目暂用父类的"直接连线"绘制（视觉差异很小），待有需要再补伸缩动画。
 */
public class ExtendingItemBridge extends ItemBridge{
    public ExtendingItemBridge(String name){
        super(name);
        hasItems = true;
    }
}
