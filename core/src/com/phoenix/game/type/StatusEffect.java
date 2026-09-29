package com.phoenix.game.type;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.ObjectMap;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Effects.Effect;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.units.StatusEntry;
import com.phoenix.game.math.Mathf;

/**
 * 最小实现：状态效果。参照 Mindustry mindustry.type.StatusEffect 移植。
 * <p>继承 {@link com.phoenix.game.ctype.UnlockableContent}：名字/本地化名来自它，构造时自动注册进 {@code Vars.content}。
 * <p>状态转移系统（对应原版 {@code transitions}/{@code init}/{@code trans}/{@code opposite}）：两个"相克"状态同时施加时，
 * 由 {@link #getTransition} 决定结果（如湿↔燃、冰↔熔），消费点在 {@code Statuses#handleApply}。
 * {@link #init(Runnable)} 注册的转移表由 {@code ContentLoader.init()} → {@link #init()} 触发。
 */
public class StatusEffect extends com.phoenix.game.ctype.UnlockableContent{
    public Color color = Color.WHITE.cpy();

    /** 速度倍率 */
    public float speedMultiplier = 1f;
    /** 伤害倍率 */
    public float damageMultiplier = 1f;
    /** 生命倍率 */
    public float healthMultiplier = 1f;
    /** 护甲倍率（百分比） */
    public float armorMultiplier = 1f;
    /** 每秒伤害（负值表示回血，见 {@link #update}） */
    public float damage = 0f;
    /** 状态期间随机叠加在单位身上的粒子特效（对应原版 {@code StatusEffect.effect}）。 */
    public Effect effect = Fx.none;
    /** 状态转移表（对应原版 {@code transitions}）。 */
    protected ObjectMap<StatusEffect, TransitionHandler> transitions = new ObjectMap<>();
    /** {@link #init(Runnable)} 注册的初始化块，由 {@link #init()} 执行。 */
    protected Runnable initblock = () -> {};

    public StatusEffect(String name){
        super(name);
    }

    @Override
    public com.phoenix.game.ctype.ContentType getContentType(){
        return com.phoenix.game.ctype.ContentType.status;
    }

    /** 全部内容创建完成后调用（{@code ContentLoader.init()}）：执行转移表注册块。 */
    @Override
    public void init(){
        initblock.run();
    }

    /** 注册一个初始化块（原版用于在内容加载后建立状态转移表）。 */
    public void init(Runnable run){
        this.initblock = run;
    }

    /** 状态持续期间每帧调用。 */
    public void update(BaseUnit unit, float time){
        if(damage > 0.001f){
            unit.damage(damage * Time.delta() / 60f);
        }else if(damage < -0.001f){
            //负伤害 = 回血（对应原版 unit.healBy(damage * Time.delta())）
            unit.healBy(damage * Time.delta());
        }

        //状态期间随机喷粒子（对应原版 Effects.effect(effect, x + range, y + range)）
        if(effect != Fx.none && effect != null && Mathf.chance(Time.delta() * 0.15f)){
            Effects.effect(effect, unit.x + Mathf.range(unit.getSize() / 2f), unit.y + Mathf.range(unit.getSize() / 2f));
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

    /** 注册一个状态转移处理器（对应原版 {@code trans}）。 */
    protected void trans(StatusEffect effect, TransitionHandler handler){
        transitions.put(effect, handler);
    }

    /** 注册"相克"转移：两者同时施加时互相抵消一半时长（对应原版 {@code opposite}）。 */
    protected void opposite(StatusEffect... effect){
        for(StatusEffect sup : effect){
            trans(sup, (unit, time, newTime, result) -> {
                time -= newTime * 0.5f;
                if(time > 0){
                    result.set(this, time);
                    return;
                }
                result.set(sup, newTime);
            });
        }
    }

    /** @return 本状态是否与另一状态存在转移关系。 */
    public boolean reactsWith(StatusEffect effect){
        return transitions.containsKey(effect);
    }

    /**
     * 状态转移结算（对应原版 {@code getTransition}）。
     * @param to 施加的新状态
     * @param time 当前状态的剩余时长
     * @param newTime 新状态将持续的时长
     * @param result 结算结果写入此处
     */
    public StatusEntry getTransition(BaseUnit unit, StatusEffect to, float time, float newTime, StatusEntry result){
        if(transitions.containsKey(to)){
            transitions.get(to).handle(unit, time, newTime, result);
            return result;
        }

        return result.set(to, newTime);
    }

    @Override
    public String toString(){
        return name;
    }

    /** 状态转移处理器（对应原版 {@code StatusEffect.TransitionHandler}）。 */
    public interface TransitionHandler{
        void handle(BaseUnit unit, float time, float newTime, StatusEntry result);
    }
}
