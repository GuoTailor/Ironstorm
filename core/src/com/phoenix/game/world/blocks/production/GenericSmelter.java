package com.phoenix.game.world.blocks.production;

import com.badlogic.gdx.graphics.Color;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Time;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;

/**
 * 冶炼炉。参照 Mindustry mindustry.world.blocks.production.GenericSmelter 移植。
 * <p>与 {@link GenericCrafter} 的差别只在视觉（火焰颜色 / 熔炼特效），生产逻辑完全一致，
 * 因此直接继承。
 * <p>已接线（S5b 批次）：{@code drawLight(Tile)}（原版 {@code renderer.lights.add(...)}）——
 * 见 {@link #drawLight(Tile)}。
 */
public class GenericSmelter extends GenericCrafter{
    /** 火焰颜色（对应原版 flameColor，见原版 GenericSmelter.java:14）。 */
    public Color flameColor = Color.valueOf("ffc999");

    public GenericSmelter(String name){
        super(name);
    }

    /**
     * 熔炼炉发光（对应原版 {@code GenericSmelter.drawLight}，见原版 GenericSmelter.java:51-56）。
     * <p>已接线（S5b 批次）：本项目已移植灯光管线（{@code graphics/LightRenderer}），
     * 本方法由 {@code core/Renderer.drawLights} 逐格调用。
     * <p>⚠ 坐标口径：原版 {@code tile.drawx()/drawy()} 是方块中心；本项目 {@code worldx()/worldy()} 是瓦片角点
     * → 用 {@link com.phoenix.game.world.Block#centerX}/{@link com.phoenix.game.world.Block#centerY}。
     * <p>⚠ API 差异：原版 {@code Mathf.absin(10f, 5f)} 是 arc 的 {@code absin(scl, mag)}（时间取 Time.time）；
     * 本项目两参重载的语义已在 S5b 对齐 arc（见 {@code Mathf.absin} 的注释）→ 这里写三参 {@code (Time.time(), scl, mag)} 更明确。
     * <p>{@code warmup} 已由 S5b 补进 {@link GenericCrafter.CraftEntity}（原版 GenericCrafter.java:95-101/153）→ 直接用实体字段。
     */
    @Override
    public void drawLight(Tile tile){
        if(!(tile.entity instanceof CraftEntity)) return;
        if(Vars.renderer == null) return;

        CraftEntity e = (CraftEntity)tile.entity;

        Vars.renderer.lights.add(centerX(tile), centerY(tile),
            (60f + Mathf.absin(Time.time(), 10f, 5f)) * e.warmup * size, flameColor, 0.65f);
    }
}
