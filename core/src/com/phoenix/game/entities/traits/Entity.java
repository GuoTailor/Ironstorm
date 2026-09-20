package com.phoenix.game.entities.traits;


public interface Entity extends MoveTrait{

    int getID();

    void resetID(int id);

    default void update(){}

    default void removed(){}

    default void added(){}

}
