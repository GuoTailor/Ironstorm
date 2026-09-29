package com.phoenix.game.world.meta.values;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.content.Items;
import com.phoenix.game.core.Scl;
import com.phoenix.game.func.Boolf;
import com.phoenix.game.struct.Array;
import com.phoenix.game.type.Item;
import com.phoenix.game.ui.ItemDisplay;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.meta.StatValue;

/**
 * 按条件筛选的物品列表（如"可接受物品"）。参照原版 mindustry.world.meta.values.ItemFilterValue 移植。
 * <p>原版用 {@code content.items().select(filter)}；本项目没有 ContentLoader，
 * 用 {@link Items#all} 等价替代（同样按 id 顺序）。
 */
public class ItemFilterValue implements StatValue{
    private final Boolf<Item> filter;

    public ItemFilterValue(Boolf<Item> filter){
        this.filter = filter;
    }

    @Override
    public void display(Table table){
        Array<Item> list = new Array<Item>(Items.all).select(filter);

        for(int i = 0; i < list.size; i++){
            Item item = list.get(i);

            table.add(new ItemDisplay(item)).padRight(Scl.scl(5f));

            if(i != list.size - 1){
                //原版是 table.add("/")（需要 skin），本项目统一用 Label
                table.add(new Label("/", Styles.defaultLabel));
            }
        }
    }
}
