package com.phoenix.game.world.blocks.production;

import com.badlogic.gdx.graphics.Color;

/**
 * 冶炼炉。参照 Mindustry mindustry.world.blocks.production.GenericSmelter 移植。
 * <p>与 {@link GenericCrafter} 的差别只在视觉（火焰颜色 / 熔炼特效），生产逻辑完全一致，
 * 因此直接继承。
 */
public class GenericSmelter extends GenericCrafter{
    /** 火焰颜色（对应原版 flameColor；当前未用于绘制，保留字段供后续补熔炼动画）。 */
    public Color flameColor = Color.valueOf("ffc099");

    public GenericSmelter(String name){
        super(name);
    }
}
