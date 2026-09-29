package com.phoenix.game.world.meta.values;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Strings;
import com.phoenix.game.func.Boolf;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.meta.I18n;
import com.phoenix.game.world.meta.StatValue;
import io.anuke.mindustry.gen.Tex;

import static com.phoenix.game.Vars.content;

/**
 * 统计项：加成输入表（"哪些液体能加速这个方块，各能加到多少装填速度"）。
 * 参照 Mindustry mindustry.world.meta.values.BoosterListValue 移植。
 *
 * <p><b>与本项目的差异（均为等价替代，C 类）</b>：
 * <ul>
 *   <li>原版 {@code c.addImage(...)} / {@code c.add(String)} 需要全局 skin；本项目没有 skin，
 *       一律自建 {@code Image} / {@link Label}（与 {@code ui/LiquidDisplay} 的做法一致）。</li>
 *   <li>原版 {@code liquid.icon(Cicon.medium)} 由 {@code UnlockableContent} 提供；本项目 {@code Liquid}
 *       继承 {@code MappableContent}（见 {@code 转换.md}）→ 退化成按 {@code liquid-<name>} 直接查图集。</li>
 *   <li>原版 {@code liquid.localizedName} → 退化成内部名 {@code liquid.name}。</li>
 *   <li>原版 {@code Core.bundle.format("bullet.reload", ...)} 在本项目会因缺键返回 {@code ???key???}
 *       → 走 {@link I18n#get} 双形态判定 + 英文兜底，再做 {@code {0}} 替换。</li>
 * </ul>
 */
public class BoosterListValue implements StatValue{
    protected float reload, maxUsed, multiplier;
    protected boolean baseReload;
    protected Boolf<Liquid> filter;

    public BoosterListValue(float reload, float maxUsed, float multiplier, boolean baseReload, Boolf<Liquid> filter){
        this.reload = reload;
        this.maxUsed = maxUsed;
        this.baseReload = baseReload;
        this.multiplier = multiplier;
        this.filter = filter;
    }

    @Override
    public void display(Table table){
        table.row();

        Table c = new Table();
        c.left();

        for(int i = 0; i < content.liquids().size; i++){
            Liquid liquid = content.liquids().get(i);
            if(!filter.get(liquid)) continue;

            c.add(new Image(icon(liquid))).size(Scl.scl(3 * 8)).padRight(Scl.scl(4f)).right().top();
            c.add(new Label(liquid.name, Styles.defaultLabel)).padRight(Scl.scl(10f)).left().top();

            //原版是 c.table(Tex.underline, bt -> {...})（libgdx 的 Table 没有"带背景的子表"重载）→ 自建
            Table bt = new Table();
            bt.setBackground(Tex.underline);
            bt.left().defaults().padRight(Scl.scl(3f)).left();

            float reloadRate = (baseReload ? 1f : 0f) + maxUsed * multiplier * liquid.heatCapacity;
            float standardReload = baseReload ? reload : reload / (maxUsed * multiplier * 0.4f);
            float result = standardReload / (reload / reloadRate);
            bt.add(new Label(bundle("bullet.reload", "[stat]Reload time: [white]{0}[lightgray]x", Strings.fixed(result, 1)), Styles.defaultLabel));

            c.add(bt).left().padTop(-Scl.scl(9f));
            c.row();
        }

        //原版是 colspan(table.getColumns())；本项目 BlockStats.display 传进来的表可能还没有列（getColumns()==0），
        //libgdx 的 colspan(0) 会让该格排不进任何列，这里兜到 1
        table.add(c).colspan(Math.max(table.getColumns(), 1));
        table.row();
    }

    /**
     * @return 液体图标区域。
     * <p>等价替代（C 类）：原版是 {@code liquid.icon(Cicon.medium)}（由 {@code UnlockableContent} 提供）；
     * 本项目 {@code Liquid} 继承 {@code MappableContent}，没有该方法 → 退化成按 {@code liquid-<name>} 查图集
     * （与 {@code ui/LiquidDisplay} 同一套退化查图，图集里的液体图标正好是 32×32 = Cicon.medium）。
     */
    private static TextureRegion icon(Liquid liquid){
        return Core.atlas == null ? null : Core.atlas.findRegion("liquid-" + liquid.name);
    }

    /**
     * bundle 取值 + {@code {0}} 替换，对应原版的 {@code Core.bundle.format(key, args)}。
     * <p>本项目 bundle 缺键返回 {@code ???key???}（不是键本身），所以先按 {@link I18n} 双形态判定取文本。
     */
    private static String bundle(String key, String fallback, Object... args){
        return Strings.format(I18n.get(key, fallback), args);
    }
}
