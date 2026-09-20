package com.phoenix.game.ai;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.LongMap;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.World;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.game.EventType;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.world.Tile;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * RTS 命令寻路。参照 Mindustry v7/v8 {@code mindustry.ai.ControlPathfinder} 移植。
 *
 * <p>为每个“命令目的地”维护一张流向该目的地的流场（BFS 成本场），被指挥的单位沿流场下坡走向目的地；
 * 有直线视野时直接直线走（对应原版 raycast 捷径）。
 *
 * <p>与原版的刻意偏离（“实在替代不了的按最小实现复制”）：
 * <ul>
 *   <li>原版是 HPA* 分簇 + 增量 flowfield（1658 行，{@code @Struct} 位打包、四叉树、
 *       {@code TaskQueue} 等 arc 基建）；本工程地图小，退化为**整图 BFS** ——
 *       一张场约 0.3ms，够用且无簇/portal 复杂度。</li>
 *   <li>对主线程的接口保持与原版一致：{@link #getPathPosition} 返回
 *       {@code {unreachable, move, next, dest}}，{@link com.phoenix.game.ai.types.CommandAI}
 *       的消费代码与原版逐行对应，日后升级为分簇实现时单位侧无需改动。</li>
 *   <li>瓦片变化时整体清空缓存按需重算（原版做增量簇更新）。</li>
 * </ul>
 *
 * <p>线程模型与 {@link Pathfinder} 相同：后台 daemon 线程算场，主线程只读结果；
 * 场未就绪时返回 {@code move=false}（单位原地等一帧）。
 */
public class ControlPathfinder implements Runnable{
    /** 缓存的最大流场数（超出的淘汰最旧的）。 */
    private static final int maxFields = 32;
    /** 寻路不可达标记（与 {@link PathTile#impassable} 同义）。 */
    public static final int impassable = -1;

    /** 一张流向某目的地的流场。主线程创建入队，后台线程填充 {@link #weights}。 */
    static class FieldCache{
        final Team team;
        final int goalX, goalY;
        final int[][] weights;
        /** 后台线程算完后置 true；主线程读。 */
        volatile boolean ready;
        /** 创建顺序（淘汰用）。 */
        final long seq;

        FieldCache(Team team, int goalX, int goalY, int width, int height, long seq){
            this.team = team;
            this.goalX = goalX;
            this.goalY = goalY;
            this.weights = new int[width][height];
            this.seq = seq;
        }
    }

    /** {@link #getPathPosition} 的返回值（对应原版 {@code ControlPathfinder.PathfindResult}）。 */
    public static class PathfindResult{
        /** 目的地不可达（被完全围死）。 */
        public boolean unreachable;
        /** 本帧是否应当移动。 */
        public boolean move;
        /** 流场建议的下一格（调试/舰船判定用）。 */
        public Tile next;
        /** 本帧应朝这里移动（下一格中心或直线终点）。 */
        public final Vector2 dest = new Vector2();

        void reset(){
            unreachable = false;
            move = false;
            next = null;
        }
    }

    /** 复用的返回对象：主线程单帧内使用（CommandAI 拿到结果立刻消费）。 */
    private final PathfindResult pathResult = new PathfindResult();

    /** 场缓存：key = team.id | goalX | goalY 位打包。主线程专用。 */
    private final LongMap<FieldCache> fields = new LongMap<>();
    /** 按创建顺序排列（淘汰最旧用）。主线程专用。 */
    private final com.badlogic.gdx.utils.Array<FieldCache> fieldList = new com.badlogic.gdx.utils.Array<>();
    /** 待计算队列（主线程 -> 后台线程）。 */
    private final ConcurrentLinkedQueue<FieldCache> pending = new ConcurrentLinkedQueue<>();
    private volatile Thread thread;
    private long seqCounter;

    public ControlPathfinder(){
        //对应原版 checkEvents()：世界加载重建、Reset 停止、瓦片变化全量失效
        Events.on(EventType.WorldLoadEvent.class, e -> clear());
        Events.on(EventType.ResetEvent.class, e -> clear());
        Events.on(EventType.TileChangeEvent.class, e -> clear());
    }

    /** 清空所有缓存场（世界加载 / 瓦片变化时）。 */
    public void clear(){
        fields.clear();
        fieldList.clear();
        pending.clear();
    }

    private void ensureThread(){
        if(thread == null || !thread.isAlive()){
            thread = new Thread(this, "Control Pathfinder");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        }
    }

    /** 后台线程：逐个计算待处理的流场。 */
    @Override
    public void run(){
        while(true){
            try{
                FieldCache field = pending.poll();
                if(field == null){
                    Thread.sleep(16);
                    continue;
                }
                computeField(field);
                field.ready = true;
            }catch(InterruptedException e){
                return;
            }catch(Throwable e){
                e.printStackTrace();
            }
        }
    }

    /** 从目标向外 BFS 成本场（后台线程专用；只读 Vars.world 的瓦片）。 */
    private void computeField(FieldCache field){
        World world = Vars.world;
        if(world == null) return;

        int width = world.width(), height = world.height();
        int[][] weights = field.weights;

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                weights[x][y] = impassable;
            }
        }

        //目标格直接置 0（即使是实心建筑 —— 单位要能走到建筑旁边/贴上它）
        weights[field.goalX][field.goalY] = 0;

        //FIFO 队列（IntArray + 头指针，避免装箱）
        com.badlogic.gdx.utils.IntArray queue = new com.badlogic.gdx.utils.IntArray(width * height / 4 + 16);
        int head = 0;
        queue.add(com.phoenix.game.world.Pos.get(field.goalX, field.goalY));

        while(head < queue.size){
            int pos = queue.get(head++);
            int x = com.phoenix.game.world.Pos.x(pos), y = com.phoenix.game.world.Pos.y(pos);
            int cost = weights[x][y];

            for(int i = 0; i < Geometry.d4.length; i++){
                int dx = x + Geometry.d4[i].x, dy = y + Geometry.d4[i].y;
                if(dx < 0 || dy < 0 || dx >= width || dy >= height) continue;
                if(weights[dx][dy] != impassable) continue;

                Tile tile = world.tile(dx, dy);
                if(tile == null || !passable(tile)) continue;

                weights[dx][dy] = cost + tile.cost;
                if(queue.size < width * height){
                    queue.add(com.phoenix.game.world.Pos.get(dx, dy));
                }
            }
        }
    }

    /** @return 该瓦片是否可通行（地面单位；核心格不可穿行 —— 与命令寻路的语义一致）。 */
    private boolean passable(Tile tile){
        return !tile.solid() && tile.floor() != null && tile.floor().drownTime <= 0f;
    }

    private boolean passable(int x, int y){
        World world = Vars.world;
        if(world == null) return false;
        Tile tile = world.tile(x, y);
        return tile != null && passable(tile);
    }

    /**
     * 主线程专用：查询单位下一步应移动的位置。
     * 对应原版 {@code ControlPathfinder.getPathPosition(unit, destination, mainDestination)}。
     *
     * @param unit 查询单位
     * @param destination 实际目的地（含编队偏移）
     * @param mainDestination 编队共享的主目的地（最小实现里未用于共享场，保留参数对齐原版签名）
     * @param result 结果写入这里
     * @return 结果对象（内部复用，调用方当帧消费）
     */
    public PathfindResult getPathPosition(BaseUnit unit, Vector2 destination, Vector2 mainDestination){
        PathfindResult result = pathResult;
        result.reset();

        World world = Vars.world;
        if(world == null){
            result.dest.set(destination);
            result.move = true;
            return result;
        }

        //飞行单位无视地形，直接飞过去（原版按 pathCostId 分层，phoenix 飞行不做地面寻路）
        if(unit.isFlying()){
            result.dest.set(destination);
            result.move = true;
            return result;
        }

        int gx = world.toTile(destination.x), gy = world.toTile(destination.y);
        //目标越出地图：不可达
        if(gx < 0 || gy < 0 || gx >= world.width() || gy >= world.height()){
            result.unreachable = true;
            return result;
        }

        //直线捷径：到目标格无遮挡就直接走（对应原版 raycastRect 快速通道）
        Tile from = unit.tileOn();
        if(from == null || lineOfSight(from.x, from.y, gx, gy)){
            result.dest.set(destination);
            result.move = true;
            return result;
        }

        FieldCache field = getField(unit.getTeam(), gx, gy);
        if(field == null || !field.ready){
            //场还在后台计算：本帧不动，等下一帧
            return result;
        }

        int current = field.weights[from.x][from.y];
        if(current == impassable){
            //单位所在区域与目标不连通
            result.unreachable = true;
            return result;
        }

        if(current == 0){
            //已经在目标格上：直接走向精确坐标
            result.dest.set(destination);
            result.move = true;
            return result;
        }

        //沿流场下坡走 4 邻域中权重更低、可通行的格子
        Tile best = null;
        int bestCost = current;
        for(int i = 0; i < Geometry.d4.length; i++){
            int dx = from.x + Geometry.d4[i].x, dy = from.y + Geometry.d4[i].y;
            if(dx < 0 || dy < 0 || dx >= world.width() || dy >= world.height()) continue;

            int cost = field.weights[dx][dy];
            if(cost < bestCost && passable(dx, dy)){
                best = world.tile(dx, dy);
                bestCost = cost;
            }
        }

        if(best == null){
            //已贴着目标（例如目标建筑旁）：直接走向精确坐标
            result.dest.set(destination);
            result.move = true;
            return result;
        }

        result.next = best;
        result.dest.set(best.worldx(), best.worldy());
        result.move = true;
        return result;
    }

    /** 取（或请求创建）一张流向 (gx, gy) 的场；返回 null 表示已请求、尚未就绪。 */
    private FieldCache getField(Team team, int gx, int gy){
        long key = key(team, gx, gy);
        FieldCache field = fields.get(key);
        if(field == null){
            ensureThread();
            field = new FieldCache(team, gx, gy, Vars.world.width(), Vars.world.height(), seqCounter++);
            fields.put(key, field);
            fieldList.add(field);
            pending.add(field);

            //淘汰最旧的场（对应原版 30 帧未用清理的最小实现）
            while(fieldList.size > maxFields){
                FieldCache oldest = fieldList.removeIndex(0);
                fields.remove(key(oldest.team, oldest.goalX, oldest.goalY));
            }
        }
        return field;
    }

    private static long key(Team team, int x, int y){
        return ((long)(team.id & 0xFF) << 48) | ((long)x << 24) | y;
    }

    /**
     * 两瓦片中心之间的直线是否无实心遮挡（步进采样；不含终点格本身 ——
     * 目标可以是建筑）。
     */
    private boolean lineOfSight(int x0, int y0, int x1, int y1){
        World world = Vars.world;
        float dx = x1 - x0, dy = y1 - y0;
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if(length < 0.001f) return true;

        //以半格为步长采样
        int steps = (int)(length * 2f);
        for(int i = 1; i < steps; i++){
            float t = i / (float)steps;
            int x = world.toTile((x0 + 0.5f) + dx * t);
            int y = world.toTile((y0 + 0.5f) + dy * t);
            if(!passable(x, y)) return false;
        }
        return true;
    }
}
