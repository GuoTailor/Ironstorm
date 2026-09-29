package com.phoenix.game.maps.filters;

import com.badlogic.gdx.utils.IntArray;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.maps.filters.FilterOption.*;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.Rock;

public class OreMedianFilter extends GenerateFilter{
    public float radius = 2;
    public float percentile = 0.5f;

    //arc.struct.IntArray → libgdx 的 IntArray
    private IntArray blocks = new IntArray();

    {
        buffered = true;
        options(
            new SliderOption("radius", () -> radius, f -> radius = f, 1f, 12f),
            new SliderOption("percentile", () -> percentile, f -> percentile = f, 0f, 1f)
        );
    }

    @Override
    public void apply(){
        if(in.ore == Blocks.spawn) return;

        int cx = (in.x / 2) * 2;
        int cy = (in.y / 2) * 2;
        if(in.ore != Blocks.air){
            if(!(in.tile(cx + 1, cy).overlay() == in.ore && in.tile(cx, cy).overlay() == in.ore && in.tile(cx + 1, cy + 1).overlay() == in.ore && in.tile(cx, cy + 1).overlay() == in.ore &&
            !isStatic(in.tile(cx + 1, cy).block()) && !isStatic(in.tile(cx, cy).block()) && !isStatic(in.tile(cx + 1, cy + 1).block()) && !isStatic(in.tile(cx, cy + 1).block()))){
                in.ore = Blocks.air;
            }
        }

        int rad = (int)radius;

        blocks.clear();
        for(int x = -rad; x <= rad; x++){
            for(int y = -rad; y <= rad; y++){
                if(Mathf.dst2(x, y) > rad*rad) continue;

                Tile tile = in.tile(in.x + x, in.y + y);
                if(tile.overlay() != Blocks.spawn)
                blocks.add(tile.overlay().id);
            }
        }

        blocks.sort();

        int index = Math.min((int)(blocks.size * percentile), blocks.size - 1);
        int overlay = blocks.get(index);

        in.ore = Vars.content.block(overlay);
    }

    /**
     * 对应原版 {@code Block.isStatic()}（= {@code cacheLayer == CacheLayer.walls}）。
     * <p>TODO 本项目 {@code Block} 没有 {@code cacheLayer} 字段 → 退化成「是否自然岩壁」({@link Rock}，
     * 含 {@code StaticWall}) 的近似判断；语义上足以覆盖"矿脉不该压在岩壁上"的用法。
     */
    private static boolean isStatic(Block block){
        return block instanceof Rock;
    }
}
