package com.phoenix.game.type;

import com.badlogic.gdx.graphics.Color;

/**
 * 液体资源。参照 Mindustry mindustry.type.Liquid 最小移植。
 * <p>当前只用于建筑的液体储量展示（对应原版详情面板的“液体”条），未接入管道流动。
 */
public class Liquid{
    public final String name;
    /** 液体颜色，用于储量条与绘制。 */
    public final Color color;
    /** 是否可燃（原版用于爆炸/燃烧计算），暂未使用但保留以对齐字段。 */
    public float flammability;

    public Liquid(String name, Color color){
        this.name = name;
        this.color = color;
    }

    @Override
    public String toString(){
        return name;
    }
}
