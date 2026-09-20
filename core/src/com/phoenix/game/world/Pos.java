package com.phoenix.game.world;

/**
 * 最小实现：把瓦片坐标打包成 int。参照 Mindustry mindustry.world.Pos 移植。
 */
public class Pos {
    public static int get(int x, int y){
        return (x & 0xFFFF) << 16 | (y & 0xFFFF);
    }

    public static int x(int pos){
        return (short)((pos >>> 16) & 0xFFFF);
    }

    public static int y(int pos){
        return (short)(pos & 0xFFFF);
    }
}
