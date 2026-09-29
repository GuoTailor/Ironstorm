package com.phoenix.game.entities.traits;

import com.phoenix.game.core.Time;
import com.phoenix.game.math.Mathf;

/**
 * 参照原版 mindustry.entities.traits.TimeTrait 移植。
 * <p>**刻意偏离**：原版是 {@code extends Scaled, Entity}，{@code Scaled} 接口只提供 {@code fin()}；
 * 本项目没有移植 {@code Scaled}（{@link com.phoenix.game.entities.Effects.EffectContainer} 也是直接写 fin()），
 * 所以这里直接把 {@code fin()} 声明在本接口上。
 * <p>**刻意偏离**：原版靠 {@code Entity.remove()} 移除自身，本项目 {@code Entity} 接口尚未声明该方法
 * （只有 BaseEntity 有），这里在本接口上补声明，等 Entity 补齐后可删。
 */
public interface TimeTrait extends Entity{

    float lifetime();

    void time(float time);

    float time();

    /** @return 生命周期进度 0~1（对应未移植的 {@code Scaled.fin()}）。 */
    float fin();

    /** 移除自身（对应原版 {@code Entity.remove()}）。 */
    void remove();

    default void updateTime(){
        time(Mathf.clamp(time() + Time.delta(), 0, lifetime()));

        if(time() >= lifetime()){
            remove();
        }
    }

    //fin() is not implemented due to compiler issues with iOS/RoboVM
}
