package com.phoenix.game.maps.zonegen;

import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.maps.generators.BasicGenerator;
import com.phoenix.game.world.Tile;

/**
 * 战役区域「荒漠」（desertWastes）的地形生成器。参照 Mindustry
 * {@code mindustry.maps.zonegen.DesertWastesGenerator} 移植。
 *
 * <p>产出：全沙地底 + 沙岩团块 → 用 A* 刷出两条蜿蜒通道 → 扭曲 → 反向洪水填充封死不可达区域
 * → 铺暗沙/沙丘岩 → 科技网格地板 → 终点放出生点、起点放开局蓝图。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li>{@code Mathf.range(int)} <b>已回填</b>（本项目 Mathf 补了 {@code range(int)}）→ 照原版写。</li>
 *   <li>{@code noise(tiles, Blocks.salt, Blocks.saltRocks, ...)}（盐地/盐岩）<b>已回填</b>
 *       （salt / saltRocks 均在 Blocks 里）。</li>
 *   <li>{@code overlay(tiles, Blocks.sand, Blocks.pebbles, ...)}（沙地上的碎石装饰）<b>已回填</b>
 *       （pebbles 已注册）。</li>
 *   <li>{@code schematics.placeLoadout(loadout, spawnX, spawnY)} <b>已回填</b>（T8a 把
 *       {@code Schematics.placeLoadout} 补了回来）→ 照原版写。</li>
 *   <li>{@code tech(tiles)} 里的 {@code darkPanel3}/{@code darkPanel4} <b>已注册</b>
 *       → {@code BasicGenerator.tech} 照原版用双色深色地板。</li>
 * </ul>
 */
public class DesertWastesGenerator extends BasicGenerator{

    public DesertWastesGenerator(int width, int height){
        super(width, height, Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal, Blocks.oreCopper);
    }

    @Override
    public void generate(int x, int y){
        floor = Blocks.sand;
    }

    @Override
    public void decorate(Tile[][] tiles){
        ores(tiles);
        terrain(tiles, Blocks.sandRocks, 60f, 1.5f, 0.9f);

        int rand = 40;
        int border = 25;
        int spawnX = Mathf.clamp(30 + Mathf.range(rand), border, width - border), spawnY = Mathf.clamp(30 + Mathf.range(rand), border, height - border);
        int endX = Mathf.clamp(width - 30 + Mathf.range(rand), border, width - border), endY = Mathf.clamp(height - 30 + Mathf.range(rand), border, height - border);

        brush(tiles, pathfind(tiles, spawnX, spawnY, endX, endY, tile -> tile.solid() ? 5f : 0f, manhattan), 6);
        brush(tiles, pathfind(tiles, spawnX, spawnY, endX, endY, tile -> tile.solid() ? 4f : 0f + (float)sim.octaveNoise2D(1, 1, 1f / 40f, tile.x, tile.y) * 20, manhattan), 5);

        erase(tiles, endX, endY, 10);
        erase(tiles, spawnX, spawnY, 20);
        distort(tiles, 20f, 4f);
        inverseFloodFill(tiles, tiles[spawnX][spawnY], Blocks.sandRocks);

        noise(tiles, Blocks.salt, Blocks.saltRocks, 5, 0.6f, 200f, 0.55f);
        noise(tiles, Blocks.darksand, Blocks.duneRocks, 5, 0.7f, 120f, 0.5f);

        tech(tiles);
        overlay(tiles, Blocks.sand, Blocks.pebbles, 0.15f, 5, 0.8f, 30f, 0.62f);
        //scatter(tiles, Blocks.sandRocks, Blocks.creeptree, 1f);

        tiles[endX][endY].setOverlay(Blocks.spawn);
        //照原版：把开局蓝图放到起点
        Vars.schematics.placeLoadout(loadout, spawnX, spawnY);
    }
}
