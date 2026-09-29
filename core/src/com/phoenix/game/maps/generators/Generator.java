package com.phoenix.game.maps.generators;

import com.phoenix.game.Vars;
import com.phoenix.game.game.Schematic;
import com.phoenix.game.world.Tile;

/**
 * 地图生成器基类。参照 Mindustry {@code mindustry.maps.generators.Generator} 移植。
 *
 * <p>生成器只负责往一张 {@code Tile[][]} 里写瓦片，**不负责把它装进世界** ——
 * 原版由 {@code World.loadGenerator} 先 {@code createTiles(w, h)} 再把同一个数组交给
 * {@link #generate(Tile[][])}，生成完由 {@code World.endMapLoad()} 收尾。
 *
 * <p><b>调用方必须注意两件事</b>（原版由 {@code World.beginMapLoad/endMapLoad} 保证）：
 * <ol>
 *   <li>生成前后要 {@code Vars.world.setGenerating(true/false)}：否则每次
 *       {@code Tile.setBlock} 都会走 {@code World.notifyChanged}（重建电网 + 刷新寻路 + 发事件），
 *       整张图生成会退化成 O(瓦片数²)。</li>
 *   <li>生成结束后要把二维数组同步回 {@code Vars.world.tiles}（本项目 World 是一维
 *       {@code Tile[]}，而且 {@link #generate} 内部会用 {@code new Tile(...)} 替换瓦片对象）。</li>
 * </ol>
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li>{@code loadout} 的类型是 {@link Schematic}（原版 {@code mindustry.game.Schematic}）——
 *       本项目同名类已在 {@code game/} 包下。</li>
 *   <li>{@link #placeLoadout(int, int)} <b>是委托包装</b>：原版该方法在 {@code Schematics} 上
 *       （{@code schematics.placeLoadout(loadout, x, y)}）；T8a 已把
 *       {@code Schematics.placeLoadout} 补回来 → 本方法只做空值保护后转调它，
 *       保留是为了不打断 {@code BasicGenerator} 子类既有的 {@code placeLoadout(x, y)} 调用。</li>
 *   <li>本项目没有 {@code World.loadGenerator}（{@code World} 用一维 {@code Tile[]} 且没有
 *       {@code createTiles}）→ <b>生成器目前没有调用入口</b>，见类注释末尾。</li>
 * </ul>
 *
 * <p><b>未解决的阻塞</b>：原版入口链是
 * {@code Control.play → world.loadGenerator(zone.generator) → beginMapLoad/createTiles/generate/endMapLoad}，
 * 本项目 {@code Control} 只有程序化生成 {@code play()}（直接调 {@code World.createMap}），
 * 没有 {@code World.loadGenerator}（{@code Zone.generator} 字段本身已在 T3 回填，
 * 但 {@code Control} 仍未接生成器链路）
 * → 本包的生成器可以被构造、可以 {@code generate(Tile[][])}，但没有任何生产代码会调它们。
 */
public abstract class Generator{
    public int width, height;
    protected Schematic loadout;

    public Generator(int width, int height){
        this.width = width;
        this.height = height;
    }

    public Generator(){
    }

    public void init(Schematic loadout){
        this.loadout = loadout;
    }

    public abstract void generate(Tile[][] tiles);

    /**
     * 在 (x, y) 处放置开局蓝图。对应原版 {@code Vars.schematics.placeLoadout(loadout, x, y)}。
     *
     * <p>T8a 已把 {@code Schematics.placeLoadout} 补回（原版该方法就在 {@code Schematics} 上）
     * → 本方法只做空值保护后转调，具体降级点（{@code configureAny}/{@code posConfig}/
     * 钻头铺矿分支）见 {@link com.phoenix.game.game.Schematics#placeLoadout}。
     */
    protected void placeLoadout(int x, int y){
        if(loadout == null || Vars.world == null || Vars.schematics == null) return;
        Vars.schematics.placeLoadout(loadout, x, y);
    }
}
