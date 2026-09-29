package com.phoenix.game.maps.filters;

import com.phoenix.game.content.Blocks;
import com.phoenix.game.maps.filters.FilterOption.BlockOption;
import com.phoenix.game.maps.filters.FilterOption.SliderOption;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Block;

import static com.phoenix.game.maps.filters.FilterOption.floorsOnly;
import static com.phoenix.game.maps.filters.FilterOption.wallsOnly;

public class TerrainFilter extends GenerateFilter{
    float scl = 40, threshold = 0.9f, octaves = 3f, falloff = 0.5f, magnitude = 1f, circleScl = 2.1f;
    Block floor = Blocks.stone, block = Blocks.rocks;

    {
        options(
        new SliderOption("scale", () -> scl, f -> scl = f, 1f, 500f),
        new SliderOption("mag", () -> magnitude, f -> magnitude = f, 0f, 2f),
        new SliderOption("threshold", () -> threshold, f -> threshold = f, 0f, 1f),
        new SliderOption("circle-scale", () -> circleScl, f -> circleScl = f, 0f, 3f),
        new SliderOption("octaves", () -> octaves, f -> octaves = f, 1f, 10f),
        new SliderOption("falloff", () -> falloff, f -> falloff = f, 0f, 1f),
        new BlockOption("floor", () -> floor, b -> floor = b, floorsOnly),
        new BlockOption("wall", () -> block, b -> block = b, wallsOnly)
        );
    }

    @Override
    public void apply(){
        float noise = noise(in.x, in.y, scl, magnitude, octaves, falloff) + Mathf.dst((float)in.x / in.width, (float)in.y / in.height, 0.5f, 0.5f) * circleScl;

        in.floor = floor;
        in.ore = Blocks.air;

        if(noise >= threshold){
            in.block = block;
        }else{
            in.block = Blocks.air;
        }
    }
}
