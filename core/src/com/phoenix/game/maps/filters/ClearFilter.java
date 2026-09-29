package com.phoenix.game.maps.filters;

import com.phoenix.game.content.Blocks;
import com.phoenix.game.world.Block;

import static com.phoenix.game.maps.filters.FilterOption.*;

public class ClearFilter extends GenerateFilter{
    protected Block block = Blocks.air;

    {
        options(
        new BlockOption("block", () -> block, b -> block = b, wallsOnly)
        );
    }

    @Override
    public void apply(){

        if(in.block == block){
            in.block = Blocks.air;
        }
    }
}
