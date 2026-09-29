package com.phoenix.game.entities.traits;

public interface AbsorbTrait extends Entity, TeamTrait, DamageTrait{
    void absorb();

    default boolean canBeAbsorbed(){
        return true;
    }

    default float getShieldDamage(){
        return damage();
    }
}
