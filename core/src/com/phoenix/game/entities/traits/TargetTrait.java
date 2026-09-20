package com.phoenix.game.entities.traits;


import com.phoenix.game.math.Position;

/**
 * Base interface for targetable entities.
 */
public interface TargetTrait extends Position, VelocityTrait{

    boolean isDead();

    default float getTargetVelocityX(){
        if(this instanceof SolidTrait){
            return ((SolidTrait)this).getDeltaX();
        }
        return velocity().x;
    }

    default float getTargetVelocityY(){
        if(this instanceof SolidTrait){
            return ((SolidTrait)this).getDeltaY();
        }
        return velocity().y;
    }

    /**
     * Whether this entity is a valid target.
     */
    default boolean isValid(){
        return !isDead();
    }
}
