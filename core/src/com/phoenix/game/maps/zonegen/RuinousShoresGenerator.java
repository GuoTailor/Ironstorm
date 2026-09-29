package com.phoenix.game.maps.zonegen;

import com.phoenix.game.content.Blocks;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.maps.generators.BasicGenerator;
import com.phoenix.game.world.Tile;

/**
 * 战役区域「废墟海岸」（ruinousShores）的地形生成器。
 *
 * <p><b>主题</b>：沙质海岸 + 水洼 + 废岩废墟。以 {@code sand} 为底，浅水/暗沙水交错铺出水洼，
 * 沙岩团块堆成残破岸线，再用碎岩散布出「坍塌的废墟」感。
 *
 * <p>原版该区域靠内置的 {@code .msav} 地图文件（{@code ruinous-shores.msav}）；本项目 {@code SaveIO}
 * 与原版 MSAV 刻意不做字节兼容、读不了内置图，因此改为程序化生成（本类）。
 *
 * <p>产出顺序：矿脉 → 沙地水洼 → 暗沙水洼 → 沙岩岸线 → 科技网格 → 沙地碎石覆盖层
 * → 废墟岩块散布 → 四角波次出生点 → 开局蓝图。
 *
 * <p><b>与规格的一处必要偏离</b>：规格的 {@code scatter(tiles, Blocks.sand, Blocks.rocks, 0.012f)}
 * 在本项目会崩 —— {@code pass()} 把 {@code floor}/{@code ore} 强转 {@code (Floor)}
 * （见 {@code BasicGenerator.java:224}），而 {@code rocks} 是 {@code StaticWall}（非 Floor），
 * 写进 floor 槽会 {@code ClassCastException}。故改为写进 {@code block} 槽（语义等价：沙地上散布实心废岩）。
 */
public class RuinousShoresGenerator extends BasicGenerator{

    public RuinousShoresGenerator(int width, int height){
        super(width, height, Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal, Blocks.oreScrap);
    }

    @Override
    public void generate(int x, int y){
        floor = Blocks.sand;
    }

    @Override
    public void decorate(Tile[][] tiles){
        ores(tiles);
        //浅水洼（沙地水 + 沙岩缘）
        noise(tiles, Blocks.sandWater, Blocks.sandRocks, 5, 0.65f, 110f, 0.62f);
        //暗沙深水洼
        noise(tiles, Blocks.darksandWater, Blocks.duneRocks, 5, 0.7f, 150f, 0.70f);
        //残破岸线岩壁
        terrain(tiles, Blocks.sandRocks, 60f, 0.9f, 0.6f);
        //科技网格残迹
        tech(tiles);
        //沙地上的碎石装饰（pebbles 是 Floor，可安全走 overlay）
        overlay(tiles, Blocks.sand, Blocks.pebbles, 0.15f, 5, 0.8f, 30f, 0.6f);
        //废墟岩块：rocks 是 StaticWall(Block) → 只能写 block 槽（等价于规格的 scatter）
        pass(tiles, (x, y) -> {
            if(floor == Blocks.sand && block == Blocks.air && Mathf.chance(0.012f)){
                block = Blocks.rocks;
            }
        });

        //放波次出生点（敌人生成靠它）
        spawnPoints(tiles);
        //照原版：把开局蓝图放到地图中部偏上
        placeLoadout(width / 2, (int)(height * 0.3f));
    }
}
