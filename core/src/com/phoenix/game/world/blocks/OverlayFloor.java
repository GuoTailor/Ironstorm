package com.phoenix.game.world.blocks;

import com.phoenix.game.world.Floor;

/**
 * 叠加在地表上的装饰层（如出生点标记）。参照 Mindustry mindustry.world.blocks.OverlayFloor 移植。
 * 目前唯一的用途是波次出生点：Renderer 只绘制 floor，不绘制 overlay，所以它对玩家不可见。
 */
public class OverlayFloor extends Floor{
    public OverlayFloor(String name){
        super(name);
        variants = 0;
    }
}
