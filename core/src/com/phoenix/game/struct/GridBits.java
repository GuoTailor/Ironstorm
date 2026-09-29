package com.phoenix.game.struct;

import com.badlogic.gdx.utils.Bits;

/**
 * 二维位图。参照 {@code arc.struct.GridBits} 移植（原版用于地图编辑器的选区/放置记录）。
 * <p>底层直接用 libgdx 的 {@code Bits}，索引 = {@code x + y * width}。
 */
public class GridBits{

    private final Bits bits = new Bits();
    private final int width;
    private final int height;

    public GridBits(int width, int height){
        this.width = width;
        this.height = height;
    }

    public void set(GridBits other){
        bits.clear();
        bits.or(other.bits);
    }

    public boolean get(int x, int y){
        return bits.get(index(x, y));
    }

    public void set(int x, int y){
        bits.set(index(x, y));
    }

    public void set(int x, int y, boolean value){
        if(value){
            set(x, y);
        }else{
            bits.clear(index(x, y));
        }
    }

    public void clear(){
        bits.clear();
    }

    public int width(){
        return width;
    }

    public int height(){
        return height;
    }

    private int index(int x, int y){
        return x + y * width;
    }
}
