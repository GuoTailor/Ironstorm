package com.phoenix.game.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.IntMap;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.ctype.ContentList;

/**
 * 老地图（Mindustry v3/v4 的 PNG 地图）颜色 → 方块的映射表。
 * 参照 Mindustry mindustry.world.LegacyColorMapper 移植。
 * <p>把像素颜色（{@code ff0000} 这类 6 位十六进制）映射成"地板 + 墙体 + 矿脉"三元组，
 * 用于导入老格式地图。
 * <p><b>已对齐原版</b>：此前标注为"尚未移植"的 7 个方块（{@code pine}/{@code deepwater}/{@code ignarock}/
 * {@code snowrocks}/{@code holostone}/{@code magmarock}/{@code tar}）早已在 {@code content/Blocks} 注册，
 * 全部条目已按原版逐字还原（曾退化成 stone/water/darksand/rocks 的条目已修正）。
 * <p>映射表是**存档兼容**用的，键值必须与原版 {@code mindustry.world.LegacyColorMapper} 完全一致，
 * 否则旧地图会整片变色。
 * <p>消费点：{@code io/MapIO.readPixmap} 逐像素调 {@link #get(int)} 取三元组（老格式 PNG 地图导入）。
 */
public class LegacyColorMapper implements ContentList{
    private static IntMap<LegacyBlock> blockMap = new IntMap<>();
    private static LegacyBlock defaultValue;

    public static LegacyBlock get(int color){
        return blockMap.get(color, defaultValue);
    }

    @Override
    public void load(){
        defaultValue = new LegacyBlock(Blocks.stone, Blocks.air);

        map("ff0000", Blocks.stone, Blocks.air, Blocks.spawn);
        map("00ff00", Blocks.stone);
        map("323232", Blocks.stone);
        map("646464", Blocks.stone, Blocks.rocks);
        map("50965a", Blocks.grass);
        map("5ab464", Blocks.grass, Blocks.pine);
        map("506eb4", Blocks.water);
        map("465a96", Blocks.deepwater);
        map("252525", Blocks.ignarock);
        map("575757", Blocks.ignarock, Blocks.duneRocks);
        map("988a67", Blocks.sand);
        map("e5d8bb", Blocks.sand, Blocks.duneRocks);
        map("c2d1d2", Blocks.snow);
        map("c4e3e7", Blocks.ice);
        map("f7feff", Blocks.snow, Blocks.snowrocks);
        map("6e501e", Blocks.holostone);
        map("ed5334", Blocks.magmarock);
        map("292929", Blocks.tar);
        map("c3a490", Blocks.stone, Blocks.air, Blocks.oreCopper);
        map("161616", Blocks.stone, Blocks.air, Blocks.oreCoal);
        map("6277bc", Blocks.stone, Blocks.air, Blocks.oreTitanium);
        map("83bc58", Blocks.stone, Blocks.air, Blocks.oreThorium);
    }

    private void map(String color, Block block, Block wall, Block ore){
        //libgdx 的 Color 没有实例方法 rgba()（arc 才有）→ 用静态 Color.rgba8888(Color)，打包格式一致
        blockMap.put(Color.rgba8888(Color.valueOf(color)), new LegacyBlock(block, wall, ore));
    }

    private void map(String color, Block block, Block wall){
        blockMap.put(Color.rgba8888(Color.valueOf(color)), new LegacyBlock(block, wall));
    }

    private void map(String color, Block block){
        blockMap.put(Color.rgba8888(Color.valueOf(color)), new LegacyBlock(block, Blocks.air));
    }

    public static class LegacyBlock{
        public final Floor floor;
        public final Block wall;
        public final Block ore;

        public LegacyBlock(Block floor, Block wall){
            this(floor, wall, Blocks.air);
        }

        public LegacyBlock(Block floor, Block wall, Block ore){
            this.floor = (Floor)floor;
            this.wall = wall;
            this.ore = ore;
        }
    }
}
