package com.phoenix.game.maps.zonegen;

import com.phoenix.game.content.Blocks;
import com.phoenix.game.maps.generators.BasicGenerator;
import com.phoenix.game.world.Tile;

/**
 * 战役区域「焦油田」（tarFields）的地形生成器。主题：暗沙底 + 焦油湖 + 页岩岩层。
 *
 * <p>原版该区域靠内置 {@code .msav} 地图文件；本项目 {@code SaveIO} 与原版 MSAV 刻意不字节兼容、
 * 读不了内置图，故改为程序化生成，让本区域有专属辨识度（否则会退化成通用噪声地形、各区域长得一样）。
 *
 * <p>产出：全暗沙地底 → 铺焦油湖（配页岩岩壁）→ 页岩团块 → 页岩上的页岩巨石 → 科技网格地板
 * → 暗沙上的沙丘岩装饰 → 四角放波次出生点、地图中上方放开局蓝图。
 */
public class TarFieldsGenerator extends BasicGenerator{

    public TarFieldsGenerator(int width, int height){
        super(width, height, Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal, Blocks.oreThorium);
    }

    @Override
    public void generate(int x, int y){
        floor = Blocks.darksand;
    }

    @Override
    public void decorate(Tile[][] tiles){
        ores(tiles);
        //焦油湖（配页岩岩壁）
        noise(tiles, Blocks.tar, Blocks.shaleRocks, 5, 0.6f, 150f, 0.62f);
        //页岩岩层
        //⚠ terrain() 写的是 **block 槽** → dst 必须是 StaticWall；shale 是 Floor → 改用 shaleRocks
        terrain(tiles, Blocks.shaleRocks, 60f, 0.8f, 0.55f);
        //页岩上的页岩巨石
        noise(tiles, Blocks.shale, Blocks.shaleBoulder, 4, 0.7f, 100f, 0.65f);
        tech(tiles);
        //暗沙上的沙丘岩装饰
        //⚠ duneRocks 是 StaticWall，overlay() 会把它写进 ore 槽并强转 (Floor) → ClassCastException
        //   → 用 decorateBlock()（写 block 槽，语义等价）
        decorateBlock(tiles, Blocks.darksand, Blocks.duneRocks, 0.12f);

        //放 4 个波次出生点（WaveSpawner 靠这些 spawn overlay 刷怪）
        spawnPoints(tiles);
        //照原版：把开局蓝图放到地图中上方
        placeLoadout(width / 2, (int)(height * 0.3f));
    }
}
