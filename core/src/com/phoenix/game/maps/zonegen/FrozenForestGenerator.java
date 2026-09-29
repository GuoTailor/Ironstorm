package com.phoenix.game.maps.zonegen;

import com.phoenix.game.content.Blocks;
import com.phoenix.game.maps.generators.BasicGenerator;
import com.phoenix.game.world.Tile;

/**
 * 战役区域「雪林」（frozenForest，200×200）的地形生成器。
 *
 * <p>原版该区域靠内置 .msav 地图文件；本项目 {@code SaveIO} 与原版 MSAV 刻意不字节兼容、读不了，
 * 故改为程序化生成。主题：终年积雪的针叶林 —— 成片雪岩、冰面与冰岩、零星雪松与孢子簇点缀，
 * 一小片科技网格地板，四角放波次出生点、中部偏上放开局蓝图。
 *
 * <p><b>与配方的刻意偏离（4 处，均为"槽位规则"修正）</b>
 * <ol>
 *   <li>{@code terrain(tiles, Blocks.iceSnow, ...)} → {@code terrain(tiles, Blocks.snowrocks, ...)}：
 *       {@code terrain} 写 <b>block 槽</b>，dst 必须是 StaticWall/Rock 这类真正的方块；
 *       {@code iceSnow} 是 Floor，塞进 block 槽会变成"方块槽里的地板"（视觉错误）。
 *       改用同主题的 StaticWall {@code snowrocks}（雪覆岩壁）。</li>
 *   <li>{@code overlay(tiles, Blocks.snow, Blocks.sporeCluster, ...)} →
 *       {@link BasicGenerator#decorateBlock}：{@code overlay} 把第二个参数写进 <b>ore 槽</b>并被强转
 *       {@code (Floor)}，而 {@code sporeCluster} 是 Rock（非 Floor）→ 命中即 {@code ClassCastException}。</li>
 *   <li>{@code scatter(tiles, Blocks.snow, Blocks.snowPine, ...)} → {@code decorateBlock}：同因，
 *       {@code scatter} 会把 dst 写进 floor 槽，{@code snowPine} 是 StaticTree（非 Floor）。</li>
 *   <li>{@code scatter(tiles, Blocks.snow, Blocks.snowrock, ...)} → {@code decorateBlock}：同因，
 *       {@code snowrock} 是 Rock（非 Floor）。</li>
 * </ol>
 * 上述 4 处原本都会在生成时抛 {@code ClassCastException}，整张图生成失败；
 * 改为写 block 槽后语义不变（"雪地上冒出孢子簇 / 雪松 / 雪岩"）。
 */
public class FrozenForestGenerator extends BasicGenerator{

    public FrozenForestGenerator(int width, int height){
        super(width, height, Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal);
    }

    @Override
    public void generate(int x, int y){
        floor = Blocks.snow;
    }

    @Override
    public void decorate(Tile[][] tiles){
        ores(tiles);
        //成片雪覆岩壁（snowrocks 是 StaticWall，实心挡路）
        terrain(tiles, Blocks.snowrocks, 60f, 0.9f, 0.6f);
        //噪声：换出冰雪地板；已是实心的岩壁再换成雪岩（snowrock 是 Rock，可破坏、不挡路）
        noise(tiles, Blocks.iceSnow, Blocks.snowrock, 4, 0.6f, 100f, 0.55f);
        //噪声：换出冰面地板；仍是实心的岩壁换成冰岩（icerocks 是 StaticWall）
        noise(tiles, Blocks.ice, Blocks.icerocks, 5, 0.7f, 140f, 0.66f);
        //一小片废墟网格地板
        tech(tiles);
        //稀疏孢子簇 / 雪松 / 雪岩（走 block 槽，详见类注释）
        decorateBlock(tiles, Blocks.snow, Blocks.sporeCluster, 0.02f);
        decorateBlock(tiles, Blocks.snow, Blocks.snowPine, 0.015f);
        decorateBlock(tiles, Blocks.snow, Blocks.snowrock, 0.01f);

        //必须调：四角放波次出生点（否则 WaveSpawner 扫不到 spawn overlay、敌人无处可刷）
        spawnPoints(tiles);
        //必须调：把开局蓝图放到地图中部偏上
        placeLoadout(width / 2, (int)(height * 0.3f));
    }
}
