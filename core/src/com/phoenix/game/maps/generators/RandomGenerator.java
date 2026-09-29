package com.phoenix.game.maps.generators;

import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Tile;

/**
 * 「逐格回调」式生成器基类。参照 Mindustry {@code mindustry.maps.generators.RandomGenerator} 移植。
 *
 * <p>流程：对每个瓦片先把 {@link #floor}/{@link #block}/{@link #ore} 重置成 {@link Blocks#air}，
 * 调子类的 {@link #generate(int, int)} 让子类改写这三个字段，再把结果写成瓦片；
 * 全部走完后调子类的 {@link #decorate(Tile[][])} 做二次修饰（矿脉/地形/出生点等）。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li>{@code Tile} 的构造器签名不同：原版是 {@code new Tile(x, y, floor.id, ore.id, block.id)}（三个内容 id），
 *       本项目是 {@code new Tile(int, int, Floor, Floor, Block)} → 这里对 {@code floor}/{@code ore} 做
 *       {@code (Floor)} 强转（本项目 {@link Tile#setOverlay} 内部也是这么转的，约定「覆盖层必须是 Floor」）。</li>
 *   <li>{@code world.setMap(new Map(new StringMap()))} <b>已接线</b>（S1 批次给 {@code World} 补了
 *       {@code getMap/setMap/rawTile}）→ 生成完调 {@code Vars.world.setMap(new Map(new StringMap()))}。</li>
 *   <li>原版 {@code generate()} 没有 {@code throws}，本项目也没有（{@code SaveIO.load} 的受检异常在
 *       {@code MapGenerator} 内部自行包装）。</li>
 * </ul>
 */
public abstract class RandomGenerator extends Generator{
    protected Block floor;
    protected Block block;
    protected Block ore;

    public RandomGenerator(int width, int height){
        super(width, height);
    }

    @Override
    public void generate(Tile[][] tiles){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                floor = Blocks.air;
                block = Blocks.air;
                ore = Blocks.air;
                generate(x, y);
                //刻意：原版 = new Tile(x, y, floor.id, ore.id, block.id)（三个 id）；
                //本项目 Tile 直接收 Floor/Block 对象 → floor/ore 强转（覆盖层必须是 Floor）
                tiles[x][y] = new Tile(x, y, (Floor)floor, (Floor)ore, block);
            }
        }

        decorate(tiles);

        //原版 RandomGenerator.java:34 = world.setMap(new Map(new StringMap()))：
        //用一张"无文件、无元数据"的空 Map 表示"当前是程序生成的图"（这样 getMap() 非 null，
        //高分记录会落到该 Map 的 nameWithoutExtension() 上）
        //⚠ 本项目 Map(StringMap) 的 file 派生自 tags.get("name","unknown")（Map.java:116-118）
        //   → 空 tags 会得到 maps/unknown 这个"假文件名"，nameWithoutExtension() == "unknown"
        //   （这是原版语义在本项目的映射结果，见 S1 报告 ③）
        if(Vars.world != null){
            Vars.world.setMap(new com.phoenix.game.maps.Map(new com.phoenix.game.struct.StringMap()));
        }
    }

    public abstract void decorate(Tile[][] tiles);

    /**
     * Sets {@link #floor} and {@link #block} to the correct values as output.
     * Before this method is called, both are set to {@link Blocks#air} as defaults.
     */
    public abstract void generate(int x, int y);
}
