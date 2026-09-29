package com.phoenix.game.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.core.Scl;
import com.phoenix.game.type.Item;

/**
 * 物品图标 + 数量 + 名称。参照 Mindustry mindustry.ui.ItemDisplay 移植。
 * <p>用于内容信息对话框的"建造花费 / 输入 / 输出"等物品列表。
 */
public class ItemDisplay extends Table{
    public final Item item;
    public final int amount;

    public ItemDisplay(Item item){
        this(item, 0);
    }

    public ItemDisplay(Item item, int amount){
        this(item, amount, true);
    }

    public ItemDisplay(Item item, int amount, boolean showName){
        add(new ItemImage(item.icon(), amount)).size(Scl.scl(Cicon.medium.size));
        if(showName){
            add(new Label(item.localizedName(), Styles.defaultLabel)).padLeft(Scl.scl(4f));
        }

        this.item = item;
        this.amount = amount;
    }
}
