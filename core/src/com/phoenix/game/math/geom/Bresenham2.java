package com.phoenix.game.math.geom;

import com.phoenix.game.func.Intc2;

/**
 * 整数网格上的直线遍历。参照 {@code arc.math.geom.Bresenham2} 移植（arc 的替代层类型）。
 *
 * <p>原版编辑器（{@code EditorTool.line} / {@code MapView.touchDragged}）用它把「起点→终点」
 * 展开成一串格子坐标；原版 {@code Placement} 的 {@code lineNoDiagonal}（不带对角步进的变体）
 * 已在 {@code input/Placement} 内联实现，不经过本类。
 * 本项目此前没有这个类，这里只补编辑器需要的 {@link #line}。
 *
 * <p>算法与 {@code World.raycastEach} 完全一致（标准 Bresenham），只把回调换成 {@link Intc2}。
 */
public class Bresenham2{

    /** 沿 (x1,y1)→(x2,y2) 逐格回调（含两端点）。 */
    public static void line(int x1, int y1, int x2, int y2, Intc2 cons){
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        while(true){
            cons.get(x1, y1);
            if(x1 == x2 && y1 == y2) return;

            int e2 = 2 * err;
            if(e2 > -dy){
                err -= dy;
                x1 += sx;
            }
            if(e2 < dx){
                err += dx;
                y1 += sy;
            }
        }
    }

    private Bresenham2(){
    }
}
