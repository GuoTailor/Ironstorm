package com.phoenix.game.content;

import com.badlogic.gdx.graphics.Color;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Events;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.game.EventType.Trigger;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.StatusEffect;

/**
 * 最小实现：状态效果定义。参照 Mindustry mindustry.content.StatusEffects 移植。
 * <p>状态效果用静态初始化创建（与原版一致），所以 {@code ContentLoader} 必须把本类列进内容集合，
 * 保证 {@code Vars.content} 已就绪后才会触发本类的静态初始化。
 * <p>F1e2 批已补齐原版的全部 11 个（none/burning/freezing/wet/melting/tarred/overdrive/shielded/boss/shocked/corroded）。
 * <p>T5 批补齐了原版的两项能力（此前缺失）：{@code effect}（状态期间随机喷粒子的 {@code Fx}）与
 * {@code transitions} 状态转移系统（burning↔wet↔freezing↔melting↔tarred 之间互相转化/抵消），
 * 分别由 {@code StatusEffect.update} 与 {@code Statuses#handleApply} 消费。
 */
public class StatusEffects implements com.phoenix.game.ctype.ContentList {
    public static final StatusEffect none = new StatusEffect("none");

    /** 燃烧 */
    public static final StatusEffect burning = new StatusEffect("burning"){{
        color = Color.valueOf("ffa665");
        damage = 0.06f;
        reactive = true;
        effect = Fx.burning;

        init(() -> {
            opposite(wet, freezing);
            trans(tarred, (unit, time, newTime, result) -> {
                unit.damage(1f);
                Effects.effect(Fx.burning, unit.x + Mathf.range(unit.getSize() / 2f), unit.y + Mathf.range(unit.getSize() / 2f));
                result.set(this, Math.min(time + newTime, 300f));
            });
        });
    }};

    /** 冰冻：减速 + 减护甲 */
    public static final StatusEffect freezing = new StatusEffect("freezing"){{
        color = Color.valueOf("8fb2e6");
        speedMultiplier = 0.6f;
        armorMultiplier = 0.8f;
        effect = Fx.freezing;

        init(() -> opposite(melting, burning));
    }};

    /** 潮湿：减速 */
    public static final StatusEffect wet = new StatusEffect("wet"){{
        color = Color.valueOf("5296d6");
        speedMultiplier = 0.9f;
        effect = Fx.wet;

        init(() -> {
            trans(shocked, (unit, time, newTime, result) -> {
                unit.damage(20f);
                if(unit.getTeam() == Vars.state.rules.waveTeam){
                    Events.fire(Trigger.shock);
                }
                result.set(this, time);
            });
            opposite(burning);
        });
    }};

    // ---- F1e2：照原版补回缺失的 7 个状态效果 ----
    // 追加在**末尾**（原版顺序里它们本来就在 wet 之后）→ 前 4 个的 id 不变，
    // 但 StatusEffects 整体在 Liquids/Bullets/Mechs/UnitTypes/Blocks 之前注册，新增 7 个会让后面这些类型 id 整体 +7。
    // 数值一律照抄原版（含 damage 的"每帧"口径；本项目 StatusEffect.update 里另做了 /60 折算，口径差异见类注释）。

    /** 熔化：被熔渣烫伤，减速 + 持续掉血（原版挂 Liquids.slag） */
    public static final StatusEffect melting = new StatusEffect("melting"){{
        //本项目风格：color 取自对应 Fx/液体色（原版 StatusEffect 不设 color，默认白）
        color = Color.valueOf("ffa166");
        speedMultiplier = 0.8f;
        armorMultiplier = 0.8f;
        damage = 0.3f;
        reactive = true;
        effect = Fx.melting;

        init(() -> {
            trans(tarred, (unit, time, newTime, result) -> result.set(this, Math.min(time + newTime / 2f, 140f)));
            opposite(wet, freezing);
        });
    }};

    /** 沾油：减速（原版挂 Liquids.oil，粒子用 Fx.oily） */
    public static final StatusEffect tarred = new StatusEffect("tarred"){{
        color = Color.valueOf("313131"); //= Liquids.oil 的颜色
        speedMultiplier = 0.6f;
        effect = Fx.oily;

        init(() -> {
            trans(melting, (unit, time, newTime, result) -> result.set(burning, newTime + time));
            trans(burning, (unit, time, newTime, result) -> result.set(burning, newTime + time));
        });
    }};

    /** 超频：加速 + 增伤 + 减护甲，并微量回血 */
    public static final StatusEffect overdrive = new StatusEffect("overdrive"){{
        color = Color.valueOf("ffd37f"); //= Pal.accent（原版 Fx.overdriven 用的颜色）
        armorMultiplier = 0.95f;
        speedMultiplier = 1.15f;
        damageMultiplier = 1.4f;
        damage = -0.01f;
        effect = Fx.overdriven;
        //damage < 0 = 回血，已由 StatusEffect.update 的负伤害分支处理
    }};

    /** 护盾：护甲 ×3 */
    public static final StatusEffect shielded = new StatusEffect("shielded"){{
        armorMultiplier = 3f;
    }};

    /** Boss 标记：护甲/伤害 ×3、速度 ×1.1（原版挂在波次 SpawnGroup.effect 上） */
    public static final StatusEffect boss = new StatusEffect("boss"){{
        armorMultiplier = 3f;
        damageMultiplier = 3f;
        speedMultiplier = 1.1f;
        //本项目 UnitType 另有 boss 布尔字段（独立血条/波次提示），两者互不影响
    }};

    /** 触电：本身无任何数值，只是 wet→shocked 的转移目标（见 {@link #wet} 的转移表） */
    public static final StatusEffect shocked = new StatusEffect("shocked");

    /** 腐蚀：只掉血，无其它效果 */
    public static final StatusEffect corroded = new StatusEffect("corroded"){{
        damage = 0.1f;
    }};

    @Override
    public void load(){
        //状态效果在静态初始化时已创建完毕
    }
}
