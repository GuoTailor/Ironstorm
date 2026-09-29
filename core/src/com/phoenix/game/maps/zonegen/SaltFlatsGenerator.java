package com.phoenix.game.maps.zonegen;

import com.phoenix.game.content.Blocks;
import com.phoenix.game.maps.generators.BasicGenerator;
import com.phoenix.game.world.Tile;

/**
 * 战役区域「盐滩」（saltFlats，200×200）的地形生成器。
 *
 * <p>原版该区域靠内置 .msav 地图文件；本项目 {@code SaveIO} 与原版 MSAV 刻意不字节兼容、读不了，
 * 故改为程序化生成。主题：白花花的盐碱地 —— 大片盐晶/盐岩团块、暗沙沙丘与沙丘岩，
 * 矿脉更丰富（铜/铅/煤/钛四种），一小片科技网格地板，四角放波次出生点、中部偏上放开局蓝图。
 *
 * <p>本类照配方原样实现，无需偏离：这里用到的 {@code overlay(tiles, Blocks.sand, Blocks.pebbles, ...)}
 * 中 {@code pebbles} 是 {@code DoubleOverlayFloor}（属于 Floor），可以安全进 overlay 层。
 */
public class SaltFlatsGenerator extends BasicGenerator{

    public SaltFlatsGenerator(int width, int height){
        super(width, height, Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal, Blocks.oreTitanium);
    }

    @Override
    public void generate(int x, int y){
        floor = Blocks.sand;
    }

    @Override
    public void decorate(Tile[][] tiles){
        ores(tiles);
        //成片盐岩（saltRocks 是 StaticWall，实心挡路）
        terrain(tiles, Blocks.saltRocks, 60f, 0.8f, 0.55f);
        //盐晶地面 + 盐岩斑块
        noise(tiles, Blocks.salt, Blocks.saltRocks, 5, 0.6f, 200f, 0.42f);
        //暗沙沙丘 + 沙丘岩斑块
        noise(tiles, Blocks.darksand, Blocks.duneRocks, 5, 0.7f, 120f, 0.68f);
        //一小片废墟网格地板
        tech(tiles);
        //沙地上的碎石覆盖层
        overlay(tiles, Blocks.sand, Blocks.pebbles, 0.15f, 5, 0.8f, 30f, 0.62f);

        //必须调：四角放波次出生点（否则 WaveSpawner 扫不到 spawn overlay、敌人无处可刷）
        spawnPoints(tiles);
        //必须调：把开局蓝图放到地图中部偏上
        placeLoadout(width / 2, (int)(height * 0.3f));
    }
}
