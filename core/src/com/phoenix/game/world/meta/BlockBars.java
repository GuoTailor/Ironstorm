package com.phoenix.game.world.meta;

import com.badlogic.gdx.utils.OrderedMap;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.func.Func;
import com.phoenix.game.ui.Bar;

/** 建筑血条/进度条列表。参照原版 mindustry.world.meta.BlockBars 移植。 */
public class BlockBars{
    private OrderedMap<String, Func<TileEntity, Bar>> bars = new OrderedMap<>();

    public void add(String name, Func<TileEntity, Bar> sup){
        bars.put(name, sup);
    }

    public void remove(String name){
        if(!bars.containsKey(name))
            throw new RuntimeException("No bar with name '" + name + "' found; current bars: " + bars.keys().toArray());
        bars.remove(name);
    }

    public Iterable<Func<TileEntity, Bar>> list(){
        return bars.values();
    }
}
