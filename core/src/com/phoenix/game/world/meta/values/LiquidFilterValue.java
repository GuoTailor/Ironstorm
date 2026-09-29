package com.phoenix.game.world.meta.values;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.func.Boolf;
import com.phoenix.game.struct.Array;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.ui.LiquidDisplay;
import com.phoenix.game.world.meta.StatValue;

import static com.phoenix.game.Vars.content;

/**
 * 统计项：一组满足过滤条件的液体（用 {@code /} 分隔）。参照 Mindustry
 * mindustry.world.meta.values.LiquidFilterValue 移植。
 * <p>依赖 {@code com.phoenix.game.ui.LiquidDisplay}（对应原版 mindustry.ui.LiquidDisplay），
 * 该类**已移植**（液体图标 + 数量 + 名称的 Table 子类），直接复用。
 */
public class LiquidFilterValue implements StatValue{
    private final Boolf<Liquid> filter;
    private final float amount;
    private final boolean perSecond;

    public LiquidFilterValue(Boolf<Liquid> filter, float amount, boolean perSecond){
        this.filter = filter;
        this.amount = amount;
        this.perSecond = perSecond;
    }

    @Override
    public void display(Table table){
        Array<Liquid> list = new Array<>();

        for(int i = 0; i < content.liquids().size; i++){
            Liquid item = content.liquids().get(i);
            if(!item.isHidden() && filter.get(item)) list.add(item);
        }

        for(int i = 0; i < list.size; i++){
            table.add(new LiquidDisplay(list.get(i), amount, perSecond)).padRight(5);

            if(i != list.size - 1){
                table.add("/");
            }
        }
    }
}
