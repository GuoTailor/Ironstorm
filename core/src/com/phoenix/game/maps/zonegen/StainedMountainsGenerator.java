package com.phoenix.game.maps.zonegen;

import com.phoenix.game.content.Blocks;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.maps.generators.BasicGenerator;
import com.phoenix.game.world.Tile;

/**
 * 战役区域「染色的山」（stainedMountains）的地形生成器。
 *
 * <p><b>主题</b>：页岩山地 + 山体岩壁 + 巨砾。以 {@code shale} 为底，用 {@code stone} 堆出成片山体、
 * {@code cliffs} 补崖壁、{@code shaleBoulder} 撒巨砾，再铺科技网格残迹与页岩碎石，整体是「被污染浸染过的荒山」。
 *
 * <p>原版该区域靠内置的 {@code .msav} 地图文件（{@code stained-mountains.msav}）；本项目 {@code SaveIO}
 * 与原版 MSAV 刻意不做字节兼容、读不了内置图，因此改为程序化生成（本类）。
 *
 * <p>产出顺序：矿脉 → 山体岩层 → 崖壁噪声 → 页岩巨砾噪声 → 科技网格 → 页岩碎石覆盖层
 * → 四角波次出生点 → 开局蓝图。
 *
 * <p><b>与规格的一处必要偏离</b>：规格的
 * {@code overlay(tiles, Blocks.shale, Blocks.shaleRocks, 0.1f, 4, 0.8f, 35f, 0.6f)} 在本项目会崩 ——
 * {@code overlay()} 把第二个方块写进 {@code ore} 槽，而 {@code pass()} 会把它强转 {@code (Floor)}
 * （见 {@code BasicGenerator.java:224}），{@code shaleRocks} 是 {@code StaticWall}（非 Floor）
 * → {@code ClassCastException}。故用等价的 {@code pass()} 复刻其「噪声 + 概率 + 限定地板」判定，
 * 但把结果写进 {@code block} 槽。
 */
public class StainedMountainsGenerator extends BasicGenerator{

    public StainedMountainsGenerator(int width, int height){
        super(width, height, Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal, Blocks.oreTitanium);
    }

    @Override
    public void generate(int x, int y){
        floor = Blocks.shale;
    }

    @Override
    public void decorate(Tile[][] tiles){
        ores(tiles);
        //主山体：成片崖壁
        //⚠ terrain() 写的是 **block 槽** → dst 必须是 StaticWall；stone 是 Floor → 改用 cliffs
        terrain(tiles, Blocks.cliffs, 55f, 1.05f, 0.75f);
        //山体地板换成石质（写 floor 槽）+ 山脊岩化
        noise(tiles, Blocks.stone, Blocks.cliffs, 5, 0.6f, 100f, 0.50f);
        //页岩巨砾
        noise(tiles, Blocks.shale, Blocks.shaleBoulder, 4, 0.7f, 90f, 0.68f);
        //科技网格残迹
        tech(tiles);
        //页岩碎石覆盖层：shaleRocks 是 StaticWall(Block) → 写 block 槽（等价复刻规格的 overlay 判定）
        sim.setSeed(Mathf.random(99999));
        pass(tiles, (x, y) -> {
            if(sim.octaveNoise2D(4, 0.8f, 1f / 35f, x, y) > 0.6f && Mathf.chance(0.1f)
                && floor == Blocks.shale && block == Blocks.air){
                block = Blocks.shaleRocks;
            }
        });

        //放波次出生点（敌人生成靠它）
        spawnPoints(tiles);
        //照原版：把开局蓝图放到地图中部偏上
        placeLoadout(width / 2, (int)(height * 0.3f));
    }
}
