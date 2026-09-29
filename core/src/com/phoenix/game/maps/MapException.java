package com.phoenix.game.maps;

/** 地图相关的运行时异常。参照 Mindustry mindustry.maps.MapException 移植。 */
public class MapException extends RuntimeException{
    /** 出问题的地图。 */
    public final Map map;

    public MapException(Map map, String s){
        super(s);
        this.map = map;
    }
}
