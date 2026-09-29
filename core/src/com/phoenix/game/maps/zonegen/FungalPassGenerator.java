package com.phoenix.game.maps.zonegen;

import com.phoenix.game.content.Blocks;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.maps.generators.BasicGenerator;
import com.phoenix.game.world.Tile;

/**
 * 战役区域「真菌通道」（fungalPass）的地形生成器。
 *
 * <p><b>主题</b>：苔藓谷地 + 孢子丛 + 孢子松。以 {@code moss} 为底，用 {@code sporeMoss} 铺出成片孢子苔原、
 * {@code sporerocks} 堆岩壁，再密布 {@code sporeCluster} 与 {@code sporePine}，整体是「孢子弥漫的通道」。
 *
 * <p>原版该区域靠内置的 {@code .msav} 地图文件（{@code fungal-pass.msav}）；本项目 {@code SaveIO}
 * 与原版 MSAV 刻意不做字节兼容、读不了内置图，因此改为程序化生成（本类）。
 *
 * <p>产出顺序：矿脉 → 孢子苔原 → 孢子岩噪声 → 科技网格 → 孢子丛 → 孢子松散布
 * → 四角波次出生点 → 开局蓝图。
 *
 * <p><b>与规格的两处必要偏离</b>（都是同一个根因：{@code pass()} 会把 {@code floor}/{@code ore}
 * 强转 {@code (Floor)}，见 {@code BasicGenerator.java:224}，而 {@code sporeCluster}/{@code sporePine}
 * 都是 {@code Rock}/{@code StaticTree}，非 Floor）：
 * <ul>
 *   <li>{@code overlay(tiles, Blocks.moss, Blocks.sporeCluster, 0.03f, 4, 0.8f, 40f, 0.6f)}
 *       —— overlay 把第二个方块写进 {@code ore} 槽 → 强转崩溃。改为用等价 {@code pass()} 复刻
 *       「噪声 + 概率 + 限定地板」判定，结果写进 {@code block} 槽。</li>
 *   <li>{@code scatter(tiles, Blocks.moss, Blocks.sporePine, 0.02f)} —— 同理，改为写 {@code block} 槽。</li>
 * </ul>
 */
public class FungalPassGenerator extends BasicGenerator{

    public FungalPassGenerator(int width, int height){
        super(width, height, Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal, Blocks.oreTitanium);
    }

    @Override
    public void generate(int x, int y){
        floor = Blocks.moss;
    }

    @Override
    public void decorate(Tile[][] tiles){
        ores(tiles);
        //成片孢子岩壁
        //⚠ terrain() 写的是 **block 槽** → dst 必须是 StaticWall；sporeMoss 是 Floor → 改用 sporerocks
        terrain(tiles, Blocks.sporerocks, 60f, 0.9f, 0.6f);
        //孢子苔原地板（写 floor 槽）+ 岩壁岩化
        noise(tiles, Blocks.sporeMoss, Blocks.sporerocks, 5, 0.6f, 110f, 0.50f);
        //科技网格残迹
        tech(tiles);
        //孢子丛：sporeCluster 是 Rock(Block) → 写 block 槽（等价复刻规格的 overlay 判定）
        sim.setSeed(Mathf.random(99999));
        pass(tiles, (x, y) -> {
            if(sim.octaveNoise2D(4, 0.8f, 1f / 40f, x, y) > 0.6f && Mathf.chance(0.03f)
                && floor == Blocks.moss && block == Blocks.air){
                block = Blocks.sporeCluster;
            }
        });
        //孢子松散布：sporePine 是 StaticTree(Block) → 写 block 槽（等价于规格的 scatter）
        pass(tiles, (x, y) -> {
            if(floor == Blocks.moss && block == Blocks.air && Mathf.chance(0.02f)){
                block = Blocks.sporePine;
            }
        });

        //放波次出生点（敌人生成靠它）
        spawnPoints(tiles);
        //照原版：把开局蓝图放到地图中部偏上
        placeLoadout(width / 2, (int)(height * 0.3f));
    }
}
