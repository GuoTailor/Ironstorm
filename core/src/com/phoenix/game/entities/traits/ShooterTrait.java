package com.phoenix.game.entities.traits;

import com.phoenix.game.core.Interval;
import com.phoenix.game.type.Weapon;

public interface ShooterTrait extends VelocityTrait, TeamTrait{

    Interval getTimer();

    int getShootTimer(boolean left);

    Weapon getWeapon();
}
