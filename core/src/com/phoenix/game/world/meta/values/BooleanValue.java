package com.phoenix.game.world.meta.values;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.meta.I18n;
import com.phoenix.game.world.meta.StatValue;

/** 是/否（对应原版 mindustry.world.meta.values.BooleanValue）。 */
public class BooleanValue implements StatValue{
    private final boolean value;

    public BooleanValue(boolean value){
        this.value = value;
    }

    @Override
    public void display(Table table){
        //原版用 "$yes"/"$no"，靠 arc 的 Table.add 自动查 bundle；这里显式取键
        table.add(new Label(I18n.get(value ? "yes" : "no", value ? "yes" : "no"), Styles.defaultLabel));
    }
}
