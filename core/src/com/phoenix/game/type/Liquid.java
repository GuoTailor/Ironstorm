package com.phoenix.game.type;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.content.StatusEffects;
import com.phoenix.game.core.Core;
import com.phoenix.game.type.StatusEffect;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.world.meta.I18n;

/**
 * 液体资源。参照 Mindustry mindustry.type.Liquid 移植。
 * <p>继承 {@link com.phoenix.game.ctype.MappableContent}：名字来自它，构造时自动注册进 {@code Vars.content}。
 * <p><b>与原版的差异</b>：原版继承 {@code UnlockableContent}（多一个 localizedName/description/icon）；
 * 本项目按 {@code 转换.md} 的继承链结论保留 {@code MappableContent}（F-LOW 批），
 * 但把内容详情面板要用的 {@code localizedName/description/icon(Cicon)/displayInfo} 就地补上
 * （原版这三个成员的实现就是"查 bundle/图集"三行，照搬即可，不需要继承链）；
 * 另外就地补了一个恒为 false 的 {@link #isHidden()}（统计显示要用）。
 */
public class Liquid extends com.phoenix.game.ctype.MappableContent{
    /** 本地化正式名，永不为 null；bundle 缺键时退回内部名（对应原版 UnlockableContent.localizedName）。 */
    public String localizedName;
    /** 本地化描述，可能为 null（对应原版 UnlockableContent.description）。 */
    public String description;

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
        //对应原版 UnlockableContent 构造器（bundle 键 liquid.<name>.name/.description）
        this.localizedName = I18n.get("liquid." + name + ".name", name);
        this.description = I18n.get("liquid." + name + ".description", null);
    }

    /** For modding only.*/
    public Liquid(String name){
        this(name, new Color(Color.BLACK));
    }

    /**
     * @return 液体图标（对应原版 {@code UnlockableContent.icon(Cicon)} 的查找链，图集里液体只有单张
     * {@code liquid-<name>}，所以链退化为 {@code liquid-<name>} → {@code <name>}；找不到返回 null）。
     */
    public TextureRegion icon(Cicon icon){
        if(Core.atlas == null) return null;
        TextureRegion found = Core.atlas.findRegion("liquid-" + name);
        if(found == null) found = Core.atlas.findRegion(name);
        return found;
    }

    public boolean canExtinguish(){
        return flammability < 0.1f && temperature <= 0.5f;
    }

    public Color barColor(){
        return barColor == null ? color : barColor;
    }

    /** 把液体信息渲染进表格（对应原版 {@code Liquid.displayInfo} → {@code ContentDisplay.displayLiquid}）。 */
    public void displayInfo(Table table){
        com.phoenix.game.ui.ContentDisplay.displayLiquid(table, this);
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
