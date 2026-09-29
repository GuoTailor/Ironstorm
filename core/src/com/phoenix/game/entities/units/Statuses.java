package com.phoenix.game.entities.units;

import com.badlogic.gdx.utils.Array;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.type.StatusEffect;

/**
 * 最小实现：单位状态效果容器。参照 Mindustry mindustry.entities.units.Statuses 移植。
 */
public class Statuses{
    /** 最多同时存在的状态数量 */
    public static final int maxEffects = 8;

    /** 状态转移结算的临时对象（对应原版 {@code globalResult}）。 */
    private static final StatusEntry globalResult = new StatusEntry();

    private final Array<StatusEntry> effects = new Array<>();

    public void clear(){
        effects.clear();
    }

    /** 更新所有状态，移除过期状态。 */
    public void update(BaseUnit unit){
        for(int i = 0; i < effects.size; i++){
            StatusEntry entry = effects.get(i);
            entry.time -= Time.delta();
            entry.effect.update(unit, entry.time);

            if(entry.time <= 0f){
                effects.removeIndex(i);
                i--;
            }
        }
    }

    /** 施加状态（已存在则取时间更长的那个；与已有状态"相克"时走转移结算）。 */
    public void handleApply(BaseUnit unit, StatusEffect effect, float duration){
        if(effect == null || effect == com.phoenix.game.content.StatusEffects.none || unit.isImmune(effect)) return;

        for(StatusEntry entry : effects){
            if(entry.effect == effect){
                //同种状态：延长到较长时长
                if(duration > entry.time){
                    entry.time = duration;
                }
                return;
            }else if(entry.effect.reactsWith(effect)){
                //相克状态：交给转移处理器决定结果（可能保留原状态、换成新状态或第三种状态）
                globalResult.effect = entry.effect;
                entry.effect.getTransition(unit, effect, entry.time, duration, globalResult);
                entry.time = globalResult.time;

                if(globalResult.effect != entry.effect){
                    entry.effect = globalResult.effect;
                }
                return;
            }
        }

        if(effects.size >= maxEffects) return;

        effects.add(new StatusEntry().set(effect, duration));
        effect.applied(unit);
    }

    public boolean hasEffect(StatusEffect effect){
        return hasEffect(effect, 0f);
    }

    public boolean hasEffect(StatusEffect effect, float minDuration){
        for(StatusEntry entry : effects){
            if(entry.effect == effect && entry.time > minDuration) return true;
        }
        return false;
    }

    public float getSpeedMultiplier(){
        float mult = 1f;
        for(StatusEntry entry : effects) mult *= entry.effect.speedMultiplier;
        return mult;
    }

    public float getDamageMultiplier(){
        float mult = 1f;
        for(StatusEntry entry : effects) mult *= entry.effect.damageMultiplier;
        return mult;
    }

    public float getArmorMultiplier(){
        float mult = 1f;
        for(StatusEntry entry : effects) mult *= entry.effect.armorMultiplier;
        return mult;
    }

    public float getHealthMultiplier(){
        float mult = 1f;
        for(StatusEntry entry : effects) mult *= entry.effect.healthMultiplier;
        return mult;
    }

    public Array<StatusEntry> all(){
        return effects;
    }
}
