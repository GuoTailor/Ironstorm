package com.phoenix.game.world.meta.values;

import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.core.Scl;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.meta.StatValue;

/**
 * 地板（图标 + 名称）。参照原版 mindustry.world.meta.values.FloorValue 移植。
 * <p>注意本项目的地板在 {@code com.phoenix.game.world.Floor}（原版在 world.blocks 包）。
 */
public class FloorValue implements StatValue{
    private final Floor floor;

    public FloorValue(Floor floor){
        this.floor = floor;
    }

    @Override
    public void display(Table table){
        //原版是 table.add(floor.localizedName())（Table.add(String) 需要 skin）；本项目统一用 Label + Styles
        table.add(new Image(floor.icon(Cicon.small))).padRight(Scl.scl(3f));
        table.add(new Label(floor.localizedName(), Styles.defaultLabel)).padRight(Scl.scl(3f));
    }
}
