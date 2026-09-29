package com.phoenix.game.maps.zonegen;

import com.phoenix.game.content.Blocks;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.maps.generators.BasicGenerator;
import com.phoenix.game.world.Tile;

/**
 * 战役区域「蔓生」（overgrowth）的地形生成器。参照 Mindustry
 * {@code mindustry.maps.zonegen.OvergrowthGenerator} 移植。
 *
 * <p>产出：全苔藓地底 + 孢子松林 → 用 A* 刷出两条蜿蜒通道 → 扭曲 → 反向洪水填充把林间封成岩壁
 * → 终点放出生点、起点放开局蓝图。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li>{@code terrain(tiles, Blocks.sporePine, ...)} <b>已回填</b>（sporePine 已注册为 StaticTree）。</li>
 *   <li>{@code inverseFloodFill(..., Blocks.sporerocks)} <b>已回填</b>（sporerocks 已注册）。</li>
 *   <li>{@code noise(tiles, Blocks.darksandTaintedWater, Blocks.duneRocks, ...)} <b>已回填</b>
 *       （darksandTaintedWater 已注册为液体地板）。</li>
 *   <li>{@code Mathf.range(int)} <b>已回填</b>（本项目 Mathf 补了 {@code range(int)}）→ 照原版写。</li>
 *   <li>{@code schematics.placeLoadout(loadout, spawnX, spawnY)} <b>已回填</b>（T8a 把
 *       {@code Schematics.placeLoadout} 补了回来）→ 这里仍调基类 {@code Generator.placeLoadout}
 *       包装方法，它内部转调 {@code Schematics.placeLoadout}（等价）。</li>
 * </ul>
 */
public class OvergrowthGenerator extends BasicGenerator{

    public OvergrowthGenerator(int width, int height){
        super(width, height, Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal, Blocks.oreCopper);
    }

    @Override
    public void generate(int x, int y){
        floor = Blocks.moss;
    }

    @Override
    public void decorate(Tile[][] tiles){
        ores(tiles);
        terrain(tiles, Blocks.sporePine, 70f, 1.4f, 1f);

        int rand = 40;
        int border = 25;
        int spawnX = Mathf.clamp(30 + Mathf.range(rand), border, width - border), spawnY = Mathf.clamp(30 + Mathf.range(rand), border, height - border);
        int endX = Mathf.clamp(width - 30 + Mathf.range(rand), border, width - border), endY = Mathf.clamp(height - 30 + Mathf.range(rand), border, height - border);

        brush(tiles, pathfind(tiles, spawnX, spawnY, endX, endY, tile -> (tile.solid() ? 5f : 0f) + (float)sim.octaveNoise2D(1, 1, 1f / 50f, tile.x, tile.y) * 50, manhattan), 6);
        brush(tiles, pathfind(tiles, spawnX, spawnY, endX, endY, tile -> (tile.solid() ? 4f : 0f) + (float)sim.octaveNoise2D(1, 1, 1f / 90f, tile.x+999, tile.y) * 70, manhattan), 5);

        erase(tiles, endX, endY, 10);
        erase(tiles, spawnX, spawnY, 20);
        distort(tiles, 20f, 4f);
        inverseFloodFill(tiles, tiles[spawnX][spawnY], Blocks.sporerocks);

        noise(tiles, Blocks.darksandTaintedWater, Blocks.duneRocks, 4, 0.7f, 120f, 0.64f);
        //scatter(tiles, Blocks.sporePine, Blocks.whiteTreeDead, 1f);

        tiles[endX][endY].setOverlay(Blocks.spawn);
        //照原版：把开局蓝图放到起点（走基类包装方法 → 内部转调 Schematics.placeLoadout）
        placeLoadout(spawnX, spawnY);
    }
}
