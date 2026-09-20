package com.phoenix.game.ai;

import com.badlogic.gdx.math.Vector2;
import com.phoenix.game.Vars;
import com.phoenix.game.core.World;
import com.phoenix.game.entities.type.BaseUnit;

/**
 * RTS 命令系统：一次多点命令的编队。参照 Mindustry v7/v8 {@code mindustry.ai.UnitGroup} 移植。
 *
 * <p>同一批被命令去同一点的单位编为一组：算出各单位相对组中心的偏移量
 * （{@link #positions}，每单位 2 个 float），单位移动时把偏移加到共同目的地上，
 * 到达后整队保持相对队形，而不是全部挤在同一点。
 *
 * <p>与原版的刻意偏离（最小实现）：
 * <ul>
 *   <li>原版在后台线程跑“压缩 + 四叉树圆碰撞物理”（{@code IntQuadTree}）；本工程选择集通常很小，
 *       直接在命令下达时**同步**计算（压缩 lerp + O(n²) 成对推开，20+6 轮迭代），
 *       省掉 quadtree/线程池依赖。</li>
 *   <li>可达性修正：从目的地向各单位槽位做瓦片步进射线，撞墙就把槽位截到墙前
 *       （对应原版 {@code updateRaycast} 的 {@code raycastFastAvoid}）。</li>
 * </ul>
 */
public class UnitGroup{
    /** 组内单位（下标与 {@link #positions} 成对）。 */
    public final com.badlogic.gdx.utils.Array<BaseUnit> units = new com.badlogic.gdx.utils.Array<>();
    /** 每单位相对组中心的偏移 [x0, y0, x1, y1, ...]；{@link #valid} 前为 null。 */
    public volatile float[] positions;
    /** 计算完成标记（对应原版 volatile valid）。 */
    public volatile boolean valid;

    /**
     * 以 dest 为锚点计算编队槽位（对应原版 {@code calculateFormation(dest, collisionLayer)}）。
     * 调用前先把成员加入 {@link #units}。
     */
    public void calculateFormation(Vector2 dest){
        float cx = 0f, cy = 0f;
        for(int i = 0; i < units.size; i++){
            BaseUnit unit = units.get(i);
            cx += unit.x;
            cy += unit.y;
        }
        cx /= units.size;
        cy /= units.size;

        float[] pos = new float[units.size * 2];

        //所有位置先记录为“相对中心”的当前队形
        for(int i = 0; i < units.size; i++){
            BaseUnit unit = units.get(i);
            pos[i * 2] = unit.x - cx;
            pos[i * 2 + 1] = unit.y - cy;
            //组内下标：CommandAI 用它取自己的槽位偏移
            unit.command().groupIndex = i;
        }

        //压缩：偏移向中心 lerp，直到圆面积占用率达标（对应原版压缩阶段）
        float maxSpaceUsage = 0.7f;
        boolean compress = true;
        int compressionIterations = 0;
        for(int iteration = 0; iteration < 20 && compress; iteration++){
            float maxDst = 1f, totalArea = 0f;
            for(int i = 0; i < units.size; i++){
                float px = pos[i * 2] * 0.7f, py = pos[i * 2 + 1] * 0.7f;
                pos[i * 2] = px;
                pos[i * 2 + 1] = py;

                float rad = units.get(i).getSize() * 0.5f;
                maxDst = Math.max(maxDst, (float)Math.sqrt(px * px + py * py) + rad);
                totalArea += (float)Math.PI * rad * rad;
            }

            float boundingArea = (float)Math.PI * maxDst * maxDst;
            float spaceUsed = totalArea / boundingArea;
            compress = spaceUsed <= maxSpaceUsage && ++compressionIterations < 20;
        }

        //散开：成对圆碰撞推开（对应原版物理阶段；O(n²)，选择集小所以同步可接受）
        int physicsIterations = 0;
        int maxPhysicsIterations = Math.min(1 + (int)Math.pow(units.size, 0.65) / 10, 6);
        for(int iteration = 0; iteration < 40 && physicsIterations < maxPhysicsIterations; iteration++){
            physicsIterations++;

            for(int a = 0; a < units.size; a++){
                float ax = pos[a * 2], ay = pos[a * 2 + 1];
                float ar = units.get(a).getSize() * 0.5f;

                for(int b = a + 1; b < units.size; b++){
                    float bx = pos[b * 2], by = pos[b * 2 + 1];
                    float br = units.get(b).getSize() * 0.5f;

                    float rs = (ar + br) * 1.2f;
                    float dx = ax - bx, dy = ay - by;
                    float dst = (float)Math.sqrt(dx * dx + dy * dy);

                    if(dst < rs){
                        if(dst < 0.001f){
                            //完全重合：随机方向推开
                            dx = 1f;
                            dy = 0f;
                            dst = 1f;
                        }
                        float push = (rs - dst) / dst;
                        float massA = units.get(a).getSize(), massB = units.get(b).getSize();
                        float ms = massA + massB;
                        float m1 = massB / ms, m2 = massA / ms;

                        pos[a * 2] += dx * push * m1;
                        pos[a * 2 + 1] += dy * push * m1;
                        pos[b * 2] -= dx * push * m2;
                        pos[b * 2 + 1] -= dy * push * m2;

                        ax = pos[a * 2];
                        ay = pos[a * 2 + 1];
                    }
                }
            }
        }

        positions = pos;

        //可达性修正：撞墙的槽位截到墙前（对应原版 updateRaycast）
        for(int i = 0; i < units.size; i++){
            updateRaycast(i, dest);
        }

        valid = true;
    }

    /**
     * 命令队列推进到下一段时重新修正该成员的槽位（对应原版 {@code updateRaycast(index, dest)}）。
     */
    public void updateRaycast(int index, Vector2 dest){
        if(positions == null) return;

        World world = Vars.world;
        if(world == null) return;

        //槽位的世界坐标
        float wx = positions[index * 2] + dest.x;
        float wy = positions[index * 2 + 1] + dest.y;

        int destX = world.toTile(dest.x), destY = world.toTile(dest.y);
        com.phoenix.game.world.Tile destTile = world.tile(destX, destY);
        if(destTile != null && destTile.solid()){
            return; //目的地本身在墙里，无从修正
        }

        //从目的地向槽位步进，撞到实心格就截断
        float dx = wx - dest.x, dy = wy - dest.y;
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if(length < 0.001f) return;

        int steps = (int)(length / (Vars.tilesize / 2f));
        for(int i = 1; i <= steps; i++){
            float t = i / (float)steps;
            com.phoenix.game.world.Tile tile = world.tile(world.toTile(dest.x + dx * t), world.toTile(dest.y + dy * t));
            if(tile == null || tile.solid()){
                //截断到最后一个空闲点半径之外
                float clipped = Math.max(length * (i - 1) / (float)steps - Vars.tilesize - 4f, 0f);
                float scale = clipped / length;
                positions[index * 2] = dx * scale;
                positions[index * 2 + 1] = dy * scale;
                return;
            }
        }
    }
}
