package com.phoenix.game.ui;

/** 图标尺寸档位。参照 Mindustry mindustry.ui.IconSize 移植。 */
public enum IconSize{
    def(48),
    small(32),
    smaller(30),
    tiny(16);

    public final int size;

    IconSize(int size){
        this.size = size;
    }
}
