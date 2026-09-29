package com.phoenix.game.maps.zonegen;

import com.phoenix.game.content.Blocks;
import com.phoenix.game.maps.generators.BasicGenerator;
import com.phoenix.game.world.Tile;

/**
 * 战役区域「荒凉裂谷」（desolateRift）的地形生成器。主题：暗沙底 + 热岩地热 + 炭化裂谷与崖壁。
 *
 * <p>原版该区域靠内置 {@code .msav} 地图文件；本项目 {@code SaveIO} 与原版 MSAV 刻意不字节兼容、
 * 读不了内置图，故改为程序化生成，让本区域有专属辨识度（否则会退化成通用噪声地形、各区域长得一样）。
 *
 * <p>产出：全暗沙地底 → 热岩地热区 → 炭化裂谷（配崖壁）→ 热岩上的岩石 → 科技网格地板
 * → 暗沙上的沙丘岩装饰 → 四角放波次出生点、地图中上方放开局蓝图。
 */
public class DesolateRiftGenerator extends BasicGenerator{

    public DesolateRiftGenerator(int width, int height){
        super(width, height, Blocks.oreCopper, Blocks.oreLead, Blocks.oreTitanium, Blocks.oreThorium);
    }

    @Override
    public void generate(int x, int y){
        floor = Blocks.darksand;
    }

    @Override
    public void decorate(Tile[][] tiles){
        ores(tiles);
        //热岩地热区
        //⚠ terrain() 写的是 **block 槽** → dst 必须是 StaticWall；hotrock 是 Floor → 改用 cliffs
        terrain(tiles, Blocks.cliffs, 70f, 1.05f, 0.75f);
        //炭化裂谷（配崖壁）
        noise(tiles, Blocks.charr, Blocks.cliffs, 5, 0.6f, 120f, 0.50f);
        //热岩上的岩石
        noise(tiles, Blocks.hotrock, Blocks.rocks, 4, 0.7f, 90f, 0.62f);
        tech(tiles);
        //暗沙上的沙丘岩装饰
        //⚠ duneRocks 是 StaticWall，overlay() 会把它写进 ore 槽并强转 (Floor) → ClassCastException
        //   → 用 decorateBlock()（写 block 槽，语义等价）
        decorateBlock(tiles, Blocks.darksand, Blocks.duneRocks, 0.1f);

        //放 4 个波次出生点（WaveSpawner 靠这些 spawn overlay 刷怪）
        spawnPoints(tiles);
        //照原版：把开局蓝图放到地图中上方
        placeLoadout(width / 2, (int)(height * 0.3f));
    }
}
