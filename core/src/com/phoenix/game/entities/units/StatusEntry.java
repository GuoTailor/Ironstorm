package com.phoenix.game.entities.units;

import com.phoenix.game.type.StatusEffect;

/**
 * 参照 Mindustry mindustry.entities.units.StatusEntry 移植。
 */
public class StatusEntry{
    public static final StatusEntry tmp = new StatusEntry();

    public StatusEffect effect;
    public float time;

    public StatusEntry set(StatusEffect effect, float time){
        this.effect = effect;
        this.time = time;
        return this;
    }
}
