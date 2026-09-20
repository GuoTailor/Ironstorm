package com.phoenix.game.type;

import com.badlogic.gdx.graphics.Color;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.type.BaseUnit;

/**
 * 最小实现：状态效果。参照 Mindustry mindustry.type.StatusEffect 移植。
 */
public class StatusEffect {
    public final String name;
    public Color color = Color.WHITE.cpy();
    /** 是否对被施加者持续造成伤害 */
    public boolean reactive;

    /** 速度倍率 */
    public float speedMultiplier = 1f;
    /** 伤害倍率 */
    public float damageMultiplier = 1f;
    /** 生命倍率 */
    public float healthMultiplier = 1f;
    /** 护甲倍率（百分比） */
    public float armorMultiplier = 1f;
    /** 每秒伤害 */
    public float damage = 0f;

    public StatusEffect(String name){
        this.name = name;
    }

    /** 状态持续期间每帧调用。 */
    public void update(BaseUnit unit, float time){
        if(damage > 0.001f){
            unit.damage(damage * Time.delta() / 60f);
        }
    }

    /** 施加时调用。 */
    public void applied(BaseUnit unit){
    }

    /** 施加状态到单位。 */
    public void apply(BaseUnit unit, float duration){
        if(unit == null) return;
        unit.status().handleApply(unit, this, duration);
    }

    @Override
    public String toString(){
        return name;
    }
}
