package com.phoenix.game.world;

/**
 * 世界上下文：生成器用来操作瓦片数组的接口。
 * 参照原版 mindustry.world.WorldContext 移植。
 */
public interface WorldContext{

    /** Return a tile in the tile array.*/
    Tile tile(int x, int y);

    /** Create the tile array.*/
    void resize(int width, int height);

    /** This should create a tile and put it into the tile array, then return it. */
    Tile create(int x, int y, int floorID, int overlayID, int wallID);

    /** Returns whether the world is already generating.*/
    boolean isGenerating();

    /** Begins generating.*/
    void begin();

    /** End generating, prepares tiles.*/
    void end();

}
