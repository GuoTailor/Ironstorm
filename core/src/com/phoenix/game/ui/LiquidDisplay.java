package com.phoenix.game.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Strings;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.world.meta.StatUnit;

/**
 * An ItemDisplay, but for liquids.
 * <p>参照 Mindustry mindustry.ui.LiquidDisplay 移植：液体图标 + 右下角数量 + （可选）/秒单位 + 名称。
 *
 * <p><b>与原版的两处差异（均为刻意降级）</b>：本项目 {@link Liquid} 继承的是
 * {@code ctype.MappableContent}（见 {@code 转换.md} 的继承链结论），而原版继承
 * {@code UnlockableContent} —— 后者才提供 {@code localizedName} 与 {@code icon(Cicon)}。
 * 这里分别退化成内部名 {@code liquid.name} 与按 {@code liquid-<name>} 直接查图集
 * （图集里的液体图标正好是 32×32，即 {@code Cicon.medium} 的尺寸）。
 * 另外原版 {@code Table.add(text).style(...)} 需要全局 skin，本项目没有 → 自建 {@link Label}。
 */
public class LiquidDisplay extends Table{
    public final Liquid liquid;
    public final float amount;
    public final boolean perSecond;

    public LiquidDisplay(Liquid liquid, float amount, boolean perSecond){
        this.liquid = liquid;
        this.amount = amount;
        this.perSecond = perSecond;

        add(new Stack(){{
            add(new Image(icon(liquid)));

            if(amount != 0){
                Table t = new Table().left().bottom();
                t.add(new Label(Strings.autoFixed(amount, 1), Styles.outlineLabel));
                add(t);
            }
        }}).size(Scl.scl(8 * 4)).padRight(Scl.scl(3 + (amount != 0 && Strings.autoFixed(amount, 1).length() > 2 ? 8 : 0)));

        if(perSecond){
            Label unit = new Label(StatUnit.perSecond.localized(), Styles.outlineLabel);
            unit.setColor(Color.LIGHT_GRAY);
            add(unit).padLeft(Scl.scl(2f)).padRight(Scl.scl(5f));
        }

        //刻意降级：原版是 add(liquid.localizedName)；Liquid 没有 localizedName（MappableContent 只给 name）
        //全项目统一做法：退化成内部名（同 world/meta/values/AmmoListValue、BoosterListValue、world/consumers/ConsumeLiquid）
        add(new Label(liquid.name, Styles.defaultLabel));
    }

    /**
     * @return 液体图标。
     * <p>刻意降级：原版是 {@code liquid.icon(Cicon.medium)}（由 {@code UnlockableContent} 提供）。
     * 本项目 Liquid 继承 MappableContent，没有该方法 → 退化成按 {@code liquid-<name>} 查图集
     * （图集里的液体图标正好是 32×32，即 {@code Cicon.medium} 的尺寸）。
     */
    private static TextureRegion icon(Liquid liquid){
        return Core.atlas == null ? null : Core.atlas.findRegion("liquid-" + liquid.name);
    }
}
