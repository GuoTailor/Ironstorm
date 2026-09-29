package com.phoenix.game.maps.zonegen;

import com.phoenix.game.content.Blocks;
import com.phoenix.game.maps.generators.BasicGenerator;
import com.phoenix.game.world.Tile;

/**
 * 战役区域「核设施」（nuclearComplex）的地形生成器。主题：金属地板底 + 面板废墟 + 残雪。
 *
 * <p>原版该区域靠内置 {@code .msav} 地图文件；本项目 {@code SaveIO} 与原版 MSAV 刻意不字节兼容、
 * 读不了内置图，故改为程序化生成，让本区域有专属辨识度（否则会退化成通用噪声地形、各区域长得一样）。
 *
 * <p>产出：全金属地板底 → 金属地板变体（配金属面板）→ 橙金属板与深色面板废墟 → 残雪（配雪岩）
 * → 科技网格地板 → 破损金属地板上的深色面板装饰 → 四角放波次出生点、地图中上方放开局蓝图。
 */
public class NuclearComplexGenerator extends BasicGenerator{

    public NuclearComplexGenerator(int width, int height){
        super(width, height, Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal, Blocks.oreThorium);
    }

    @Override
    public void generate(int x, int y){
        floor = Blocks.metalFloor;
    }

    @Override
    public void decorate(Tile[][] tiles){
        ores(tiles);
        //金属地板变体（配金属面板）
        noise(tiles, Blocks.metalFloor2, Blocks.metalFloor3, 4, 0.6f, 120f, 0.55f);
        //橙金属板与深色面板废墟
        noise(tiles, Blocks.metalFloor5, Blocks.darkPanel3, 4, 0.65f, 90f, 0.6f);
        //残雪（配雪岩）
        noise(tiles, Blocks.snow, Blocks.snowrock, 4, 0.7f, 150f, 0.6f);
        tech(tiles);
        //破损金属地板上的深色面板装饰
        overlay(tiles, Blocks.metalFloorDamaged, Blocks.darkPanel4, 0.12f, 4, 0.8f, 40f, 0.6f);

        //放 4 个波次出生点（WaveSpawner 靠这些 spawn overlay 刷怪）
        spawnPoints(tiles);
        //照原版：把开局蓝图放到地图中上方
        placeLoadout(width / 2, (int)(height * 0.3f));
    }
}
