package com.phoenix.game.maps.zonegen;

import com.phoenix.game.content.Blocks;
import com.phoenix.game.maps.generators.BasicGenerator;
import com.phoenix.game.world.Tile;

/**
 * 战役区域「起点平原」（groundZero，180×180）的地形生成器。
 *
 * <p>原版该区域靠内置 .msav 地图文件；本项目 {@code SaveIO} 与原版 MSAV 刻意不字节兼容、读不了，
 * 故改为程序化生成。主题：一望无际的草地平原 —— 成片裸岩、稀疏灌木与碎石、
 * 一小片科技网格地板（{@link BasicGenerator#tech}），四角放波次出生点、中部偏上放开局蓝图。
 *
 * <p><b>与配方的刻意偏离（2 处，均为"槽位规则"修正）</b>
 * <ol>
 *   <li>{@code terrain(tiles, Blocks.stone, ...)} → {@code terrain(tiles, Blocks.cliffs, ...)}：
 *       {@code terrain} 写的是 <b>block 槽</b>，dst 必须是 StaticWall/Rock 这类真正的方块；
 *       {@code stone} 是 Floor，塞进 block 槽只会变成"方块槽里的地板"（视觉错误）。
 *       按 stone→cliffs 的等价映射改用 {@code cliffs}。</li>
 *   <li>{@code scatter(tiles, Blocks.grass, Blocks.shrubs / Blocks.rocks, ...)} →
 *       {@link BasicGenerator#decorateBlock}：{@code scatter} 在 floor 命中时把 dst 写进 <b>floor 槽</b>，
 *       而 {@code pass()} 对该槽做 {@code (Floor)} 强转，{@code shrubs}/{@code rocks} 是 StaticWall（非 Floor）
 *       → 命中即 {@code ClassCastException}。{@code decorateBlock} 走 block 槽，语义等价。</li>
 * </ol>
 */
public class GroundZeroGenerator extends BasicGenerator{

    public GroundZeroGenerator(int width, int height){
        super(width, height, Blocks.oreCopper, Blocks.oreLead);
    }

    @Override
    public void generate(int x, int y){
        floor = Blocks.grass;
    }

    @Override
    public void decorate(Tile[][] tiles){
        ores(tiles);
        //成片裸岩（cliffs 是 StaticWall，实心挡路）
        terrain(tiles, Blocks.cliffs, 60f, 0.8f, 0.55f);
        //噪声：把部分地面换成石头地板；已是实心的裸岩格再换成岩石（rocks 是 StaticWall）
        noise(tiles, Blocks.stone, Blocks.rocks, 4, 0.6f, 120f, 0.6f);
        //一小片废墟网格地板
        tech(tiles);
        //草地上的碎石覆盖层（pebbles 是 Floor，可安全走 overlay）
        overlay(tiles, Blocks.grass, Blocks.pebbles, 0.15f, 4, 0.8f, 30f, 0.6f);
        //稀疏灌木 / 碎石（走 block 槽，详见类注释）
        decorateBlock(tiles, Blocks.grass, Blocks.shrubs, 0.02f);
        decorateBlock(tiles, Blocks.grass, Blocks.rocks, 0.01f);

        //必须调：四角放波次出生点（否则 WaveSpawner 扫不到 spawn overlay、敌人无处可刷）
        spawnPoints(tiles);
        //必须调：把开局蓝图放到地图中部偏上
        placeLoadout(width / 2, (int)(height * 0.3f));
    }
}
