package com.phoenix.game.world.blocks.liquid;

/**
 * 液体储罐（原版方块名 {@code liquid-tank}）。参照 Mindustry
 * mindustry.world.blocks.liquid.LiquidTank 移植。
 * <p>原版就是一个"容量很大的 {@link LiquidRouter}"——行为与路由器完全相同（每帧把库存液体
 * 轮询丢给相邻建筑），差别只在容量（原版注册时给 1500）与尺寸（3x3）。
 * <p>本类不做任何覆写，保持与原版一致。
 */
public class LiquidTank extends LiquidRouter{

    public LiquidTank(String name){
        super(name);
    }
}
