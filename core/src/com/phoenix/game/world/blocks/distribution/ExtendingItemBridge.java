package com.phoenix.game.world.blocks.distribution;

/**
 * 可伸缩物品桥。参照 Mindustry mindustry.world.blocks.distribution.ExtendingItemBridge 移植。
 * <p>与 {@link ItemBridge} 的唯一差别是**绘制**：桥体从两端向中间伸出，长度随 {@code uptime} 变化。
 * <p>TODO（C 类，刻意降级）本项目暂用父类的"直接连线"绘制（视觉差异很小）：原版 {@code drawLayer} 用
 * {@code Lines.line(bridgeRegion, ..., CapStyle.none, 0f)}（带贴图 + 线帽的重载）画可伸缩桥体与箭头，
 * 本项目 {@code Lines} 没有该重载（父类 {@code ItemBridge.drawLayer} 改用 {@code batch.draw} 直接画桥面），
 * 伸缩动画与箭头暂不补。
 */
public class ExtendingItemBridge extends ItemBridge{
    public ExtendingItemBridge(String name){
        super(name);
        hasItems = true;
    }
}
