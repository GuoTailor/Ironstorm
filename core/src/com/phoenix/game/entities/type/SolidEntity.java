package com.phoenix.game.entities.type;


import com.badlogic.gdx.math.Vector2;
import com.phoenix.game.entities.traits.SolidTrait;

public abstract class SolidEntity extends BaseEntity implements SolidTrait {
    protected transient Vector2 velocity = new Vector2(0f, 0.0001f);
    private transient Vector2 lastPosition = new Vector2();

    @Override
    public Vector2 lastPosition(){
        return lastPosition;
    }

    @Override
    public Vector2 velocity(){
        return velocity;
    }
}
