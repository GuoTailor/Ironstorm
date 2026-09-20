package com.phoenix.game.world.blocks;

import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

/**
 * 多格建筑部件。参照 Mindustry mindustry.world.blocks.BlockPart 移植。
 * <p>多格建筑中非中心瓦片使用本方块；所有事件（碰撞/绘制/链接）均转发到中心瓦片。
 * 卫星瓦片不绘制自身，solid 由中心瓦片决定。
 */
public class BlockPart extends Block{
    /** 多格建筑最大尺寸（瓦片）。 */
    public static final int maxSize = 9;
    private static final BlockPart[][] parts = new BlockPart[maxSize][maxSize];

    private final int dx, dy;

    private BlockPart(int dx, int dy){
        super("part_" + dx + "_" + dy);
        this.dx = dx;
        this.dy = dy;
        solid = false;
        parts[dx + maxSize / 2][dy + maxSize / 2] = this;
    }

    /** 取 (dx,dy) 偏移（相对建筑中心，-4..4，不含 0,0）对应的部件。 */
    public static BlockPart get(int dx, int dy){
        if(dx == 0 && dy == 0) throw new IllegalArgumentException("Why are you getting a [0,0] blockpart? Stop it.");
        if(Math.abs(dx) > maxSize / 2 || Math.abs(dy) > maxSize / 2) return null;
        int ix = dx + maxSize / 2, iy = dy + maxSize / 2;
        if(parts[ix][iy] == null) parts[ix][iy] = new BlockPart(dx, dy);
        return parts[ix][iy];
    }

    /** 卫星瓦片链接到中心瓦片：按自身记录的偏移反向查找。 */
    @Override
    public Tile linked(Tile tile){
        Tile out = tile.getNearby(-dx, -dy);
        return out == null ? tile : out;
    }

    @Override
    public boolean isSolidFor(Tile tile){
        //卫星瓦片是否阻挡取决于中心瓦片（原版：block.solid || link().solid()）
        Tile link = linked(tile);
        return link != tile && link.solid();
    }

    /** 卫星瓦片不绘制（由中心瓦片整体绘制）。 */
    @Override
    public boolean isHidden(){
        return true;
    }

    @Override
    public String toString(){
        return "BlockPart[" + dx + ", " + dy + "]";
    }
}
