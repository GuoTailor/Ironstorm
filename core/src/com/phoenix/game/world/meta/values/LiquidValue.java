package com.phoenix.game.world.meta.values;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.ui.LiquidDisplay;
import com.phoenix.game.world.meta.StatValue;

/**
 * 统计项：一种液体 + 数量。参照 Mindustry mindustry.world.meta.values.LiquidValue 移植。
 * <p>依赖 {@code com.phoenix.game.ui.LiquidDisplay}（对应原版 mindustry.ui.LiquidDisplay），
 * 该类**已移植**（液体图标 + 数量 + 名称的 Table 子类），直接复用。
 */
public class LiquidValue implements StatValue{
    private final Liquid liquid;
    private final float amount;
    private final boolean perSecond;

    public LiquidValue(Liquid liquid, float amount, boolean perSecond){
        this.liquid = liquid;
        this.amount = amount;
        this.perSecond = perSecond;
    }

    @Override
    public void display(Table table){
        table.add(new LiquidDisplay(liquid, amount, perSecond));
    }
}
