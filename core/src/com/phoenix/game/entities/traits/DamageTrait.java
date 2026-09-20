package com.phoenix.game.entities.traits;

/**
 * 参照 Mindustry mindustry.entities.traits.DamageTrait 移植。
 */
public interface DamageTrait{
    float damage();

    default void killed(Entity other){
    }

    /** 碰撞时施加的伤害（护盾类实体可重写）。 */
    default float getShieldDamage(){
        return damage();
    }
}
