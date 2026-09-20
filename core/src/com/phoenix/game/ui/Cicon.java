package com.phoenix.game.ui;

/** 内容图标尺寸，对应 Mindustry ui.Cicon。 */
public enum Cicon{
    full(0), tiny(8 * 2), small(8 * 3), medium(8 * 4), large(8 * 5), xlarge(8 * 6);

    public final int size;

    Cicon(int size){
        this.size = size;
    }
}
