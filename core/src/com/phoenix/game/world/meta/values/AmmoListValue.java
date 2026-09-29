package com.phoenix.game.world.meta.values;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.ObjectMap;
import com.phoenix.game.content.StatusEffects;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Strings;
import com.phoenix.game.ctype.MappableContent;
import com.phoenix.game.ctype.UnlockableContent;
import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.meta.I18n;
import com.phoenix.game.world.meta.StatValue;
import io.anuke.mindustry.gen.Tex;

import static com.phoenix.game.Vars.tilesize;

/**
 * 统计项：弹药列表（"哪种炮塔/单位用什么子弹，子弹有什么属性"）。
 * 参照 Mindustry mindustry.world.meta.values.AmmoListValue 移植。
 * <p>每行 = 弹药图标 + 名称 + 一小段子弹属性（伤害/溅射/弹药倍率/击退/燃烧/冰冻/追踪/闪电/破片）。
 *
 * <p><b>与本项目的差异（均为等价替代，C 类）</b>：
 * <ul>
 *   <li>原版 {@code table.addImage(region)} / {@code table.add(String)} 需要全局 skin；本项目没有 skin，
 *       一律自建 {@code Image} / {@link Label}（与 {@code ui/ItemDisplay} 的做法一致）。</li>
 *   <li>原版 {@code Core.bundle.format(key, ...)} 在本项目会因"缺键返回 {@code ???key???}"而失效
 *       （见 {@code 转换.md}）→ 统一走 {@link I18n#get} 双形态判定 + 原版英文文本兜底，再做 {@code {0}} 替换。</li>
 *   <li>原版 {@code Mathf.equal} 尚未移植 → 就地用 {@link #equal} 做等值判定。</li>
 *   <li>{@code StatusEffects.melting} / {@code StatusEffects.tarred} 均已存在（{@code content/StatusEffects}）
 *       → 对应的 Incendiary / Tarred 分支已按原版还原。</li>
 * </ul>
 */
public class AmmoListValue<T extends MappableContent> implements StatValue{
    private final ObjectMap<T, BulletType> map;

    public AmmoListValue(ObjectMap<T, BulletType> map){
        this.map = map;
    }

    @Override
    public void display(Table table){
        table.row();
        for(T t : map.keys()){
            BulletType type = map.get(t);
            table.add(new Image(icon(t))).size(Scl.scl(3 * 8)).padRight(Scl.scl(4f)).right().top();
            table.add(new Label(name(t), Styles.defaultLabel)).padRight(Scl.scl(10f)).left().top();

            //原版是 table.table(Tex.underline, bt -> {...})；libgdx 的 Table 没有"带背景的子表"重载 → 自建
            Table bt = new Table();
            bt.setBackground(Tex.underline);
            bt.left().defaults().padRight(Scl.scl(3f)).left();

            if(type.damage > 0){
                bt.add(new Label(bundle("bullet.damage", "[stat]Damage: [white]{0}", type.damage), Styles.defaultLabel));
            }

            if(type.splashDamage > 0){
                sep(bt, bundle("bullet.splashdamage", "[stat]Splash damage: [white]{0}[lightgray] ~[white]{1}[lightgray] blocks",
                    (int)type.splashDamage, Strings.fixed(type.splashDamageRadius / tilesize, 1)));
            }

            if(!equal(type.ammoMultiplier, 1f)){
                sep(bt, bundle("bullet.multiplier", "[stat]Ammo multiplier: [white]{0}[lightgray]x", (int)type.ammoMultiplier));
            }

            if(!equal(type.reloadMultiplier, 1f)){
                sep(bt, bundle("bullet.reload", "[stat]Reload time: [white]{0}[lightgray]x", Strings.fixed(type.reloadMultiplier, 1)));
            }

            if(type.knockback > 0){
                sep(bt, bundle("bullet.knockback", "[stat]Knockback: [white]{0}", Strings.fixed(type.knockback, 1)));
            }

            if((type.status == StatusEffects.burning || type.status == StatusEffects.melting) || type.incendAmount > 0){
                sep(bt, I18n.get("bullet.incendiary", "[stat]Incendiary"));
            }

            if(type.status == StatusEffects.freezing){
                sep(bt, I18n.get("bullet.freezing", "[stat]Freezing"));
            }

            if(type.status == StatusEffects.tarred){
                sep(bt, I18n.get("bullet.tarred", "[stat]Tarred"));
            }

            if(type.homingPower > 0.01f){
                sep(bt, I18n.get("bullet.homing", "[stat]Homing"));
            }

            if(type.lightining > 0){
                sep(bt, I18n.get("bullet.shock", "[stat]Shock"));
            }

            if(type.fragBullet != null){
                sep(bt, I18n.get("bullet.frag", "[stat]Fragmentation"));
            }

            table.add(bt).left().padTop(-Scl.scl(9f));
            table.row();
        }
    }

    void sep(Table table, String text){
        table.row();
        table.add(new Label(text, Styles.defaultLabel));
    }

    TextureRegion icon(T t){
        //等价替代（C 类）：本项目 Liquid 继承 MappableContent（没有 icon(Cicon)）→ 与 ui/LiquidDisplay 同一套退化查图
        if(t instanceof UnlockableContent) return ((UnlockableContent)t).icon(Cicon.medium);
        return Core.atlas == null ? null : Core.atlas.findRegion("liquid-" + t.name);
    }

    /** @return 显示名：{@code UnlockableContent} 用 localizedName，其余（如 Liquid）退化成内部名。 */
    private static String name(MappableContent t){
        return t instanceof UnlockableContent ? ((UnlockableContent)t).localizedName : t.name;
    }

    /** 对应原版 {@code Mathf.equal}（本项目 Mathf 尚未移植该方法）。 */
    private static boolean equal(float a, float b){
        return Math.abs(a - b) < 0.0001f;
    }

    /**
     * bundle 取值 + {@code {0}} 替换，对应原版的 {@code Core.bundle.format(key, args)}。
     * <p>本项目 bundle 缺键返回 {@code ???key???}（不是键本身），所以先按 {@link I18n} 双形态判定取文本。
     */
    private static String bundle(String key, String fallback, Object... args){
        return Strings.format(I18n.get(key, fallback), args);
    }
}
