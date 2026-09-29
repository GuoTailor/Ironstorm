package com.phoenix.game.game;

import com.phoenix.game.world.meta.I18n;

/** Presets for time between waves. Currently unused.*/
public enum Difficulty{
    easy(1.4f),
    normal(1f),
    hard(0.5f),
    insane(0.25f);

    /** Multiplier of the time between waves. */
    public final float waveTime;

    private String value;

    Difficulty(float waveTime){
        this.waveTime = waveTime;
    }

    @Override
    public String toString(){
        if(value == null){
            //原版是 Core.bundle.get("setting.difficulty." + name())；本项目 bundle 缺键返回 ???key???，
            //所以统一走 I18n.get 的双形态判定，缺键回退到枚举名。
            value = I18n.get("setting.difficulty." + name(), name());
        }
        return value;
    }
}
