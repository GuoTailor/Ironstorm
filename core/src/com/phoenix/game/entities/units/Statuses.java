package com.phoenix.game.entities.units;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Time;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.entities.traits.Saveable;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.ctype.ContentType;
import com.phoenix.game.type.StatusEffect;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * 最小实现：单位状态效果容器。参照 Mindustry mindustry.entities.units.Statuses 移植。
 * <p>状态色混合 {@link #getStatusColor()}（原版 Statuses.java:61-73）与存档序列化
 * {@link #writeSave}/{@link #readSave}（原版 Statuses.java:123-148，任务书里写的 StatusComp
 * 在 Build 126.2 里不存在，状态逻辑都集中在 Statuses）已补齐。
 */
public class Statuses implements Saveable{
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

    /**
     * 状态色混合（对应原版 {@code Statuses.getStatusColor()}，mindustry/entities/units/Statuses.java:61-73）：
     * 把所有激活状态的 {@code effect.color} 的 r/g/b **取简单平均**（原版并没有按剩余时间加权），
     * 无状态时返回白色。供单位绘制染色用。
     * <p>单位绘制时可能嵌套在渲染迭代里（热路径约束）→ 用下标循环。
     */
    public Color getStatusColor(){
        if(effects.size == 0){
            return Tmp.c1.set(Color.WHITE);
        }

        float r = 0f, g = 0f, b = 0f;
        for(int i = 0; i < effects.size; i++){
            StatusEntry entry = effects.get(i);
            r += entry.effect.color.r;
            g += entry.effect.color.g;
            b += entry.effect.color.b;
        }
        return Tmp.c1.set(r / effects.size, g / effects.size, b / effects.size, 1f);
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

    /**
     * 状态列表存档（对应原版 {@code Statuses.writeSave}，mindustry/entities/units/Statuses.java:123-130）：
     * {@code byte 数量} + 每项 {@code byte effect.id + float time}。
     * <p>effect.id 是 {@code ContentType.status} 的注册序（本项目 {@code Content.id} 为 short，
     * 状态数远小于 128，窄化到 byte 与原版一致）。调用点：{@code io/SaveIO.writeEntities}（v6 起单位记录末尾）。
     */
    @Override
    public void writeSave(DataOutput stream) throws IOException{
        stream.writeByte(effects.size);
        for(int i = 0; i < effects.size; i++){
            StatusEntry entry = effects.get(i);
            stream.writeByte(entry.effect.id);
            stream.writeFloat(entry.time);
        }
    }

    /**
     * 状态列表读档（对应原版 {@code Statuses.readSave}，mindustry/entities/units/Statuses.java:132-148）。
     * <p>两处刻意偏离：① 原版条目走 {@code Pools}（free/obtain），本项目 StatusEntry 无池化 → 直接
     * {@code clear()} + {@code new}；② 原版 {@code content.getByID(...)} 无 null 守卫（坏 id 会往列表里塞
     * null，之后在 update 里 NPE），本项目 {@code ContentLoader.getByID} 越界同样返回 null → 保持原行为不守卫。
     */
    @Override
    public void readSave(DataInput stream, byte version) throws IOException{
        effects.clear();

        byte amount = stream.readByte();
        for(int i = 0; i < amount; i++){
            byte id = stream.readByte();
            float time = stream.readFloat();
            effects.add(new StatusEntry().set(Vars.content.getByID(ContentType.status, id), time));
        }
    }
}
