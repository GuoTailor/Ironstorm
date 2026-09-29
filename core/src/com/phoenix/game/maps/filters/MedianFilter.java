package com.phoenix.game.maps.filters;

import com.badlogic.gdx.utils.IntArray;
import com.phoenix.game.Vars;
import com.phoenix.game.maps.filters.FilterOption.*;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;

public class MedianFilter extends GenerateFilter{
    float radius = 2;
    float percentile = 0.5f;
    //arc.struct.IntArray → libgdx 的 IntArray（同名同用法：clear/add/sort/get + 公开 size 字段）
    IntArray blocks = new IntArray(), floors = new IntArray();

    {
        buffered = true;
        options(
            new SliderOption("radius", () -> radius, f -> radius = f, 1f, 12f),
            new SliderOption("percentile", () -> percentile, f -> percentile = f, 0f, 1f)
        );
    }

    @Override
    public void apply(){
        int rad = (int)radius;
        blocks.clear();
        floors.clear();
        for(int x = -rad; x <= rad; x++){
            for(int y = -rad; y <= rad; y++){
                if(Mathf.dst2(x, y) > rad*rad) continue;

                Tile tile = in.tile(in.x + x, in.y + y);
                blocks.add(tile.block().id);
                floors.add(tile.floor().id);
            }
        }

        floors.sort();
        blocks.sort();

        int index = Math.min((int)(floors.size * percentile), floors.size - 1);
        int floor = floors.get(index), block = blocks.get(index);

        in.floor = Vars.content.block(floor);
        if(!Vars.content.block(block).synthetic() && !in.block.synthetic()) in.block = Vars.content.block(block);
    }
}
