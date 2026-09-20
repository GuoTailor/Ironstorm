package com.phoenix.game.entities.traits;


import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.phoenix.game.Vars;

public interface SolidTrait extends VelocityTrait, Entity{

    void hitbox(Rectangle rect);

    void hitboxTile(Rectangle rect);

    Vector2 lastPosition();

    default boolean collidesGrid(int x, int y){
        return true;
    }

    default float getDeltaX(){
        return getX() - lastPosition().x;
    }

    default float getDeltaY(){
        return getY() - lastPosition().y;
    }

    default boolean collides(SolidTrait other){
        return true;
    }

    default void collision(SolidTrait other, float x, float y){
    }

    default void move(float x, float y){
        Vars.collisions.move(this, x, y);
    }
}
