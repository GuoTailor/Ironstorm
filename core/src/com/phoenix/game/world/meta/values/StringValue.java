package com.phoenix.game.world.meta.values;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.core.Strings;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.meta.StatValue;

/** 格式化字符串（对应原版 mindustry.world.meta.values.StringValue）。 */
public class StringValue implements StatValue{
    private final String value;

    public StringValue(String value, Object... args){
        this.value = Strings.format(value, args);
    }

    @Override
    public void display(Table table){
        table.add(new Label(value, Styles.defaultLabel));
    }
}
