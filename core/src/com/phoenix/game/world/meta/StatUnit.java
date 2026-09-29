package com.phoenix.game.world.meta;

import java.util.Locale;

/**
 * 统计数值的单位（对应原版 mindustry.world.meta.StatUnit）。
 * <p>{@link #space} 决定数值与单位之间是否留一个空格；{@link #none} 表示没有单位。
 */
public enum StatUnit{
    blocks,
    powerSecond,
    liquidSecond,
    itemsSecond,
    liquidUnits,
    powerUnits,
    degrees,
    seconds,
    perSecond,
    timesSpeed(false),
    percent(false),
    none,
    items;

    /** 数值与单位之间是否留一个空格。 */
    public final boolean space;

    StatUnit(boolean space){
        this.space = space;
    }

    StatUnit(){
        this(true);
    }

    public String localized(){
        if(this == none) return "";
        return I18n.get("unit." + name().toLowerCase(Locale.ROOT), name());
    }
}
