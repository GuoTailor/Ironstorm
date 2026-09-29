package com.phoenix.game.type;

import com.badlogic.gdx.graphics.Color;
import com.phoenix.game.content.StatusEffects;
import com.phoenix.game.type.StatusEffect;

/**
 * 液体资源。参照 Mindustry mindustry.type.Liquid 移植。
 * <p>继承 {@link com.phoenix.game.ctype.MappableContent}：名字来自它，构造时自动注册进 {@code Vars.content}。
 * <p><b>与原版的差异</b>：原版继承 {@code UnlockableContent}（多一个 localizedName/description/icon）；
 * 本项目按 {@code 转换.md} 的继承链结论保留 {@code MappableContent}，所以没有 {@code displayInfo}，
 * 另外就地补了一个恒为 false 的 {@link #isHidden()}（统计显示要用）。
 */
public class Liquid extends com.phoenix.game.ctype.MappableContent{
    /** 液体颜色，用于储量条与绘制。 */
    public final Color color;

    /** Color used in bars. */
    public Color barColor;
    /** Color used to draw lights. Note that the alpha channel is used to dictate brightness. */
    public Color lightColor = Color.CLEAR.cpy();
    /** 0-1, 0 is completely inflammable, anything above that may catch fire when exposed to heat, 0.5+ is very flammable. */
    public float flammability;
    /** temperature: 0.5 is 'room' temperature, 0 is very cold, 1 is molten hot */
    public float temperature = 0.5f;
    /** how much heat this liquid can store. 0.4=water (decent), anything lower is probably less dense and bad at cooling. */
    public float heatCapacity = 0.5f;
    /** how thick this liquid is. 0.5=water (relatively viscous), 1 would be something like tar (very slow) */
    public float viscosity = 0.5f;
    /** how prone to exploding this liquid is, when heated. 0 = nothing, 1 = nuke */
    public float explosiveness;
    /** The associated status effect. */
    public StatusEffect effect = StatusEffects.none;

    public Liquid(String name, Color color){
        super(name);
        this.color = color;
    }

    /** For modding only.*/
    public Liquid(String name){
        this(name, new Color(Color.BLACK));
    }

    public boolean canExtinguish(){
        return flammability < 0.1f && temperature <= 0.5f;
    }

    public Color barColor(){
        return barColor == null ? color : barColor;
    }

    /** @return 是否在内容信息对话框里隐藏。TODO 原版由 UnlockableContent 提供，本项目 Liquid 继承 MappableContent。 */
    public boolean isHidden(){
        return false;
    }

    @Override
    public com.phoenix.game.ctype.ContentType getContentType(){
        return com.phoenix.game.ctype.ContentType.liquid;
    }
}
