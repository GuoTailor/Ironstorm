package com.phoenix.game.content;

import com.badlogic.gdx.graphics.Color;
import com.phoenix.game.type.StatusEffect;

/**
 * 最小实现：状态效果定义。参照 Mindustry mindustry.content.StatusEffects 移植。
 */
public class StatusEffects {
    public static final StatusEffect none = new StatusEffect("none");

    /** 燃烧 */
    public static final StatusEffect burning = new StatusEffect("burning"){{
        color = Color.valueOf("ffa665");
        damage = 0.5f;
        reactive = true;
    }};

    /** 冰冻：减速 */
    public static final StatusEffect freezing = new StatusEffect("freezing"){{
        color = Color.valueOf("8fb2e6");
        speedMultiplier = 0.6f;
    }};

    /** 潮湿：减速 */
    public static final StatusEffect wet = new StatusEffect("wet"){{
        color = Color.valueOf("5296d6");
        speedMultiplier = 0.85f;
    }};
}
