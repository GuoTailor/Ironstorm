package com.phoenix.game.world.meta;

import com.badlogic.gdx.scenes.scene2d.ui.Table;

/**
 * 一条统计值的显示方式（对应原版 mindustry.world.meta.StatValue）。
 * <p>实现类负责把显示本值所需的元素加进给定表格（纯文本就加一个 Label，
 * 物品列表就加图标 + 数量）。
 */
public interface StatValue{
    /** 把本值显示到表格里。 */
    void display(Table table);
}
