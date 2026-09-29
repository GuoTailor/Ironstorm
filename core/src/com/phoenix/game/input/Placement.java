package com.phoenix.game.input;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.IntFloatMap;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.Pools;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.math.geom.Point2;
import com.phoenix.game.struct.Array;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Pos;
import com.phoenix.game.world.Tile;

import java.util.PriorityQueue;

import static com.phoenix.game.Vars.*;

public class Placement{
    private static final NormalizeResult result = new NormalizeResult();
    private static final NormalizeDrawResult drawResult = new NormalizeDrawResult();
    private static Array<Point2> points = new Array<>();

    //for pathfinding
    private static IntFloatMap costs = new IntFloatMap();
    private static IntIntMap parents = new IntIntMap();
    private static IntSet closed = new IntSet();

    /** Normalize a diagonal line into points. */
    public static Array<Point2> pathfindLine(boolean conveyors, int startX, int startY, int endX, int endY){
        Pools.freeAll(points);

        points.clear();
        if(conveyors && com.phoenix.game.core.Core.settings.getBool("conveyorpathfinding")){
            if(astar(startX, startY, endX, endY)){
                return points;
            }else{
                return normalizeLine(startX, startY, endX, endY);
            }
        }else{
            return lineNoDiagonal(startX, startY, endX, endY, points);
        }
    }

    /** Normalize two points into one straight line, no diagonals. */
    public static Array<Point2> normalizeLine(int startX, int startY, int endX, int endY){
        Pools.freeAll(points);
        points.clear();
        if(Math.abs(startX - endX) > Math.abs(startY - endY)){
            //go width
            for(int i = 0; i <= Math.abs(startX - endX); i++){
                points.add(Pools.obtain(Point2.class).set(startX + i * Mathf.sign(endX - startX), startY));
            }
        }else{
            //go height
            for(int i = 0; i <= Math.abs(startY - endY); i++){
                points.add(Pools.obtain(Point2.class).set(startX, startY + i * Mathf.sign(endY - startY)));
            }
        }
        return points;
    }

    /**
     * 原版依赖 {@code arc.math.geom.Bresenham2.lineNoDiagonal(...)}；本项目 {@code math/geom/Bresenham2}
     * 类<b>存在</b>但只移植了 {@code line}、没有 {@code lineNoDiagonal} 成员 → <b>已在本文件内联实现</b>
     * （见下方 {@code lineNoDiagonal(...)}；每次迭代只沿一个轴走一步，所以不会出现"对角跳跃"）。
     */
    private static Array<Point2> lineNoDiagonal(int startX, int startY, int endX, int endY, Array<Point2> output){
        int x = startX, y = startY;
        int dx = Math.abs(endX - startX), dy = -Math.abs(endY - startY);
        int sx = startX < endX ? 1 : -1, sy = startY < endY ? 1 : -1;
        int err = dx + dy;

        output.add(Pools.obtain(Point2.class).set(x, y));
        while(x != endX || y != endY){
            if(2 * err - dy > dx - 2 * err){
                err += dy;
                x += sx;
            }else{
                err += dx;
                y += sy;
            }
            output.add(Pools.obtain(Point2.class).set(x, y));
        }
        return output;
    }

    /**
     * 原版是 {@code Block.canReplace(other)}（{@code (other != this || rotate) && group != none && other.group == this.group}）；
     * 本项目 {@code Block} 没有该方法 → <b>已在本文件内联实现</b>（见下方 {@code canReplace(...)}），
     * 按"同种方块可互相替换，或对方本来就 alwaysReplace"简化。
     */
    private static boolean canReplace(Block block, Block other){
        return block != null && (other == block || other.alwaysReplace);
    }

    /** 原版是 {@code other.floor().isDeep()}；本项目 {@code Floor} 没有该方法 → <b>已在本文件内联实现</b>
     *  （见下方 {@code isDeep(...)}，按原版公式；{@code world/Build.java} 另有一份同款实现）。 */
    private static boolean isDeep(Floor floor){
        return floor.isLiquid && floor.drownTime > 0f;
    }

    private static float tileHeuristic(Tile tile, Tile other){
        //本项目 InputHandler 的当前方块字段叫 buildBlock（原版是 control.input.block）
        Block block = control.input.buildBlock;

        if((!other.block().alwaysReplace && !canReplace(block, other.block())) || isDeep(other.floor())){
            return 20;
        }else{
            if(parents.containsKey(tile.pos())){
                Tile prev = world.tile(parents.get(tile.pos(), 0));
                if(prev != null && tile.relativeTo(prev) != other.relativeTo(tile)){
                    return 8;
                }
            }
        }
        return 1;
    }

    private static float distanceHeuristic(int x1, int y1, int x2, int y2){
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    private static boolean validNode(Tile tile, Tile other){
        Block block = control.input.buildBlock;
        if(block != null && canReplace(block, other.block())){
            return true;
        }else{
            return other.block().alwaysReplace;
        }
    }

    private static boolean astar(int startX, int startY, int endX, int endY){
        Tile start = world.tile(startX, startY);
        Tile end = world.tile(endX, endY);
        if(start == end || start == null || end == null) return false;

        costs.clear();
        closed.clear();
        parents.clear();

        int nodeLimit = 1000;
        int totalNodes = 0;

        PriorityQueue<Tile> queue = new PriorityQueue<>(10, (a, b) -> Float.compare(costs.get(a.pos(), 0f) + distanceHeuristic(a.x, a.y, end.x, end.y), costs.get(b.pos(), 0f) + distanceHeuristic(b.x, b.y, end.x, end.y)));
        queue.add(start);
        boolean found = false;
        while(!queue.isEmpty() && totalNodes++ < nodeLimit){
            Tile next = queue.poll();
            float baseCost = costs.get(next.pos(), 0f);
            if(next == end){
                found = true;
                break;
            }
            closed.add(Pos.get(next.x, next.y));
            //注意：本项目 Geometry.d4 的元素顺序与原版相反（从 +y 起），这里只是遍历四邻，顺序无影响
            for(GridPoint2 point : Geometry.d4){
                int newx = next.x + point.x, newy = next.y + point.y;
                Tile child = world.tile(newx, newy);
                if(child != null && validNode(next, child)){
                    if(closed.add(child.pos())){
                        parents.put(child.pos(), next.pos());
                        costs.put(child.pos(), tileHeuristic(next, child) + baseCost);
                        queue.add(child);
                    }
                }
            }
        }

        if(!found) return false;
        int total = 0;

        points.add(Pools.obtain(Point2.class).set(endX, endY));

        Tile current = end;
        while(current != start && total++ < nodeLimit){
            if(current == null) return false;
            int newPos = parents.get(current.pos(), Pos.invalid);

            if(newPos == Pos.invalid) return false;

            points.add(Pools.obtain(Point2.class).set(Pos.x(newPos),  Pos.y(newPos)));
            current = world.tile(newPos);
        }

        points.reverse();

        return true;
    }

    /**
     * Normalizes a placement area and returns the result, ready to be used for drawing a rectangle.
     * Returned x2 and y2 will <i>always</i> be greater than x and y.
     * @param block block that will be drawn
     * @param startx starting X coordinate
     * @param starty starting Y coordinate
     * @param endx ending X coordinate
     * @param endy ending Y coordinate
     * @param snap whether to snap to a line
     * @param maxLength maximum length of area
     */
    public static NormalizeDrawResult normalizeDrawArea(Block block, int startx, int starty, int endx, int endy, boolean snap, int maxLength, float scaling){
        normalizeArea(startx, starty, endx, endy, 0, snap, maxLength);

        float offset = block.offset();

        drawResult.x = result.x * tilesize;
        drawResult.y = result.y * tilesize;
        drawResult.x2 = result.x2 * tilesize;
        drawResult.y2 = result.y2 * tilesize;

        drawResult.x -= block.size * scaling * tilesize / 2;
        drawResult.x2 += block.size * scaling * tilesize / 2;


        drawResult.y -= block.size * scaling * tilesize / 2;
        drawResult.y2 += block.size * scaling * tilesize / 2;

        drawResult.x += offset;
        drawResult.y += offset;
        drawResult.x2 += offset;
        drawResult.y2 += offset;

        return drawResult;
    }

    /**
     * Normalizes a placement area and returns the result.
     * Returned x2 and y2 will <i>always</i> be greater than x and y.
     * @param tilex starting X coordinate
     * @param tiley starting Y coordinate
     * @param endx ending X coordinate
     * @param endy ending Y coordinate
     * @param snap whether to snap to a line
     * @param rotation placement rotation
     * @param maxLength maximum length of area
     */
    public static NormalizeResult normalizeArea(int tilex, int tiley, int endx, int endy, int rotation, boolean snap, int maxLength){

        if(snap){
            if(Math.abs(tilex - endx) > Math.abs(tiley - endy)){
                endy = tiley;
            }else{
                endx = tilex;
            }
        }

        if(Math.abs(endx - tilex) > maxLength){
            endx = Mathf.sign(endx - tilex) * maxLength + tilex;
        }

        if(Math.abs(endy - tiley) > maxLength){
            endy = Mathf.sign(endy - tiley) * maxLength + tiley;
        }

        int dx = endx - tilex, dy = endy - tiley;

        //注意：原版 rotation 0 = 右(+x)，本项目 rotation 0 = 上(+y)，两者相差 90°
        //（本项目 = (1 - 原版) mod 4，见 转换.md）→ 这里换算成本项目约定，不要照抄原版的 0/1/2/3
        if(Math.abs(dx) > Math.abs(dy)){
            if(dx >= 0){
                rotation = 1;
            }else{
                rotation = 3;
            }
        }else if(Math.abs(dx) < Math.abs(dy)){
            if(dy >= 0){
                rotation = 0;
            }else{
                rotation = 2;
            }
        }

        if(endx < tilex){
            int t = endx;
            endx = tilex;
            tilex = t;
        }
        if(endy < tiley){
            int t = endy;
            endy = tiley;
            tiley = t;
        }

        result.x2 = endx;
        result.y2 = endy;
        result.x = tilex;
        result.y = tiley;
        result.rotation = rotation;

        return result;
    }

    public static class NormalizeDrawResult{
        float x, y, x2, y2;
    }

    public static class NormalizeResult{
        public int x, y, x2, y2, rotation;

        boolean isX(){
            return Math.abs(x2 - x) > Math.abs(y2 - y);
        }

        /**
         * Returns length of greater edge of the selection.
         */
        int getLength(){
            return Math.max(x2 - x, y2 - y);
        }

        /**
         * Returns the X position of a specific index along this area as a line.
         */
        int getScaledX(int i){
            return x + (x2 - x > y2 - y ? i : 0);
        }

        /**
         * Returns the Y position of a specific index along this area as a line.
         */
        int getScaledY(int i){
            return y + (x2 - x > y2 - y ? 0 : i);
        }
    }

    public interface DistanceHeuristic{
        float cost(int x1, int y1, int x2, int y2);
    }

    public interface TileHueristic{
        float cost(Tile tile, Tile other);
    }
}
