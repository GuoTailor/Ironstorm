package com.phoenix.game.world;

import com.badlogic.gdx.math.GridPoint2;
import com.phoenix.game.math.Mathf;

import java.util.Arrays;

import static com.phoenix.game.Vars.world;

/**
 * 多格建筑的外围瓦片偏移表。参照 Mindustry mindustry.world.Edges 移植。
 * <p>用于两件事：
 * <ul>
 *     <li>{@link #getEdges(int)}：生成建筑实体的 {@code proximity} 邻接表（外围一圈格）；</li>
 *     <li>{@link #getFacingEdge}：多格建筑接收/输出物品时，定位"面向对方"的那个边缘格
 *     （原版逻辑块按贴边格收发，本项目坐标是角点约定，偏移公式与原版一致）。</li>
 * </ul>
 */
public class Edges{
    private static final int maxSize = 11;
    private static final GridPoint2[][] edges = new GridPoint2[maxSize][0];
    private static final GridPoint2[][] edgeInside = new GridPoint2[maxSize][0];

    static{
        for(int i = 0; i < maxSize; i++){
            int bot = -(int)(i / 2f) - 1;
            int top = (int)(i / 2f + 0.5f) + 1;
            edges[i] = new GridPoint2[(i + 1) * 4];

            int idx = 0;

            for(int j = 0; j < i + 1; j++){
                //bottom
                edges[i][idx++] = new GridPoint2(bot + 1 + j, bot);
                //top
                edges[i][idx++] = new GridPoint2(bot + 1 + j, top);
                //left
                edges[i][idx++] = new GridPoint2(bot, bot + j + 1);
                //right
                edges[i][idx++] = new GridPoint2(top, bot + j + 1);
            }

            //原版按极角排序（Mathf.angle 从 +x 轴起算），顺序会影响 Router 之类的轮询输出起点，照搬
            Arrays.sort(edges[i], (e1, e2) -> Float.compare(
                (float)Math.atan2(e1.y, e1.x), (float)Math.atan2(e2.y, e2.x)));

            edgeInside[i] = new GridPoint2[edges[i].length];

            for(int j = 0; j < edges[i].length; j++){
                GridPoint2 point = edges[i][j];
                edgeInside[i][j] = new GridPoint2(
                    Mathf.clamp(point.x, -(int)((i) / 2f), (int)(i / 2f + 0.5f)),
                    Mathf.clamp(point.y, -(int)((i) / 2f), (int)(i / 2f + 0.5f)));
            }
        }
    }

    /** @return 本建筑面向 other 的那个边缘格；非多格返回自身所在瓦片。 */
    public static Tile getFacingEdge(Tile tile, Tile other){
        return getFacingEdge(tile.blockRaw(), tile.x, tile.y, other);
    }

    /**
     * 取多格建筑面向 other 的边缘格（对应原版 {@code Edges.getFacingEdge}）。
     * <p>偏移公式与 {@link Tile#setBlock} 的铺开范围一致（{@code -(size-1)/2} 起步，支持偶数尺寸）。
     */
    public static Tile getFacingEdge(Block block, int tilex, int tiley, Tile other){
        if(!block.isMultiblock()) return world.tile(tilex, tiley);
        int size = block.size;
        return world.tile(tilex + Mathf.clamp(other.x - tilex, -(size - 1) / 2, (size / 2)),
                          tiley + Mathf.clamp(other.y - tiley, -(size - 1) / 2, (size / 2)));
    }

    /** @return size x size 建筑的"外围一圈"偏移（不含内部格），共 (size) * 4 个。 */
    public static GridPoint2[] getEdges(int size){
        if(size < 1 || size > maxSize) throw new RuntimeException("Block size must be between 1 and " + maxSize);
        return edges[size - 1];
    }

    /** @return 与 {@link #getEdges} 同顺序，但把越界的点夹回建筑内部格（原版用于取内侧贴边格）。 */
    public static GridPoint2[] getInsideEdges(int size){
        if(size < 1 || size > maxSize) throw new RuntimeException("Block size must be between 1 and " + maxSize);
        return edgeInside[size - 1];
    }
}
