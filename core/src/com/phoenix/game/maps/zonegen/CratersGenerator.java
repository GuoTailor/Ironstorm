package com.phoenix.game.maps.zonegen;

import com.phoenix.game.content.Blocks;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.maps.generators.BasicGenerator;
import com.phoenix.game.world.Tile;

/**
 * 战役区域「陨石坑」（craters）的地形生成器。
 *
 * <p><b>主题</b>：环形陨石坑群 + 暗沙荒漠 + 零星雪原。以大片 {@code craters}（坑底岩层）为主，
 * 叠加沙丘岩、雪地/雪岩，再铺一层科技网格残迹与孢子丛点缀，整体呈现「被天外撞击过的废土」。
 *
 * <p>原版该区域靠内置的 {@code .msav} 地图文件（{@code craters.msav}）；本项目 {@code SaveIO}
 * 与原版 MSAV 刻意不做字节兼容、读不了内置图，因此改为程序化生成（本类）。
 *
 * <p>产出顺序：矿脉 → 陨石坑团块 → 沙丘岩噪声 → 雪原噪声 → 科技网格 → 暗沙碎石覆盖层
 * → 孢子丛散布 → 四角波次出生点 → 开局蓝图。
 *
 * <p><b>与规格的一处必要偏离</b>：规格的 {@code scatter(tiles, Blocks.darksand, Blocks.sporeCluster, 0.004f)}
 * 在本项目会崩 —— {@code pass()} 把 {@code floor}/{@code ore} 强转 {@code (Floor)}
 * （见 {@code BasicGenerator.java:224}），而 {@code sporeCluster} 是 {@code Rock}（非 Floor），
 * 写进 floor 槽会 {@code ClassCastException}。故改为写进 {@code block} 槽（语义等价：暗沙空地上稀疏长孢子丛）。
 */
public class CratersGenerator extends BasicGenerator{

    public CratersGenerator(int width, int height){
        super(width, height, Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal, Blocks.oreTitanium);
    }

    @Override
    public void generate(int x, int y){
        floor = Blocks.darksand;
    }

    @Override
    public void decorate(Tile[][] tiles){
        ores(tiles);
        //主坑体：成片的坑壁岩
        //⚠ terrain() 写的是 **block 槽** → dst 必须是 StaticWall；craters 是 Floor，传进来会变成
        //   「方块槽里放地板」（不崩但视觉错误）→ 改用等价的 duneRocks
        terrain(tiles, Blocks.duneRocks, 70f, 0.9f, 0.65f);
        //坑底岩层（写 floor 槽）+ 已实心的坑壁岩化
        noise(tiles, Blocks.craters, Blocks.duneRocks, 5, 0.65f, 130f, 0.45f);
        //高纬雪原（雪地 + 雪岩）
        noise(tiles, Blocks.snow, Blocks.snowrock, 4, 0.7f, 160f, 0.70f);
        //科技网格残迹
        tech(tiles);
        //暗沙上的碎石装饰（pebbles 是 Floor，可安全走 overlay）
        overlay(tiles, Blocks.darksand, Blocks.pebbles, 0.12f, 4, 0.8f, 35f, 0.6f);
        //孢子丛点缀：sporeCluster 是 Rock(Block) → 只能写 block 槽（等价于规格的 scatter）
        pass(tiles, (x, y) -> {
            if(floor == Blocks.darksand && block == Blocks.air && Mathf.chance(0.004f)){
                block = Blocks.sporeCluster;
            }
        });

        //放波次出生点（敌人生成靠它）
        spawnPoints(tiles);
        //照原版：把开局蓝图放到地图中部偏上
        placeLoadout(width / 2, (int)(height * 0.3f));
    }
}
