package com.phoenix.game.world.meta.values;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.core.Strings;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.meta.StatUnit;
import com.phoenix.game.world.meta.StatValue;

/**
 * 带单位的数值（对应原版 mindustry.world.meta.values.NumberValue）。
 * <p>小数位数按数值自动取：整数不带小数点、一位小数就显示一位、否则两位
 * （所以"0.5 秒"不会显示成"0.50 秒"）。
 */
public class NumberValue implements StatValue{
    private final StatUnit unit;
    private final float value;

    public NumberValue(float value, StatUnit unit){
        this.unit = unit;
        this.value = value;
    }

    @Override
    public void display(Table table){
        int precision = Math.abs((int)value - value) <= 0.001f ? 0
            : Math.abs((int)(value * 10) - value * 10) <= 0.001f ? 1 : 2;

        table.add(new Label(Strings.fixed(value, precision), Styles.defaultLabel));
        table.add(new Label((unit.space ? " " : "") + unit.localized(), Styles.defaultLabel));
    }
}
