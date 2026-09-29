package com.phoenix.game.graphics;

/** 世界绘制的图层顺序。参照 Mindustry mindustry.graphics.Layer 移植。 */
public enum Layer{
    /** Base block layer. */
    block,
    /** for placement */
    placement,
    /** First overlay. Stuff like conveyor items. */
    overlay,
    /** "High" blocks, like turrets. */
    turret,
    /** Power lasers. */
    power,
    /** Extra layer that's always on top.*/
    lights
}
