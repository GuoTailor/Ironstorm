package com.phoenix.game.ai;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.IntArray;
import com.phoenix.game.struct.IntQueue;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.World;
import com.phoenix.game.game.EventType;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.world.Pos;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockFlag;

import java.util.ArrayList;
import java.util.function.BiConsumer;

/**
 * 流场寻路。参照 Mindustry mindustry.ai.Pathfinder 移植（arc 库 → libgdx）。
 * <p>后台 daemon 线程逐帧扩散 BFS 生成“成本场”（flow field），主线程通过
 * {@link #getTargetTile} 查询下一个应前往的瓦片，从而绕开墙体向目标流动。
 * <p>硬约束：{@code list} 只能在后台线程访问；主线程只读 {@code pathMap}/{@code created}。
 * <p>占位：PathTarget 目标源暂用地图中心代替（核心/出生点系统未移植），见枚举注释。
 */
public class Pathfinder implements Runnable{
    /** 每帧寻路总时间预算上限（毫秒）。 */
    private static final long maxUpdate = 4_000_000; // 4ms in nanos
    private static final int updateFPS = 60;
    private static final int updateInterval = 1000 / updateFPS;

    /** tile 数据，见 {@link PathTile}。 */
    private int[][] tiles;
    /** 各 PathData 无序数组，仅供迭代。后台线程专用，勿在主线程访问。 */
    private final ArrayList<PathData> list = new ArrayList<>();
    /** 队伍 + 目标组合 -> 该目标的有效寻路数据。主线程可读。 */
    private PathData[][] pathMap = new PathData[Team.all().length][PathTarget.all.length];
    /** 已请求创建的寻路数据标记，避免重复入队。 */
    private boolean[][] created = new boolean[Team.all().length][PathTarget.all.length];
    /** 后台线程任务调度队列。 */
    private final TaskQueue queue = new TaskQueue();
    /** 当前寻路线程。 */
    private volatile Thread thread;

    public Pathfinder(){
        Events.on(EventType.WorldLoadEvent.class, e -> onWorldLoad());
        Events.on(EventType.ResetEvent.class, e -> stop());
    }

    /** 把瓦片打包进 PathTile 内部表示。核心（可被摧毁的寻路目标）视为可通行，否则流场无法到达目标。 */
    private int packTile(Tile tile){
        boolean passable = tile.block().flags.contains(BlockFlag.core) || (!tile.solid() && tile.floor().drownTime <= 0f);
        return PathTile.get(tile.cost, tile.getTeam().id, (byte)0, passable);
    }

    /** 启动（或重启）寻路后台线程。 */
    private void start(){
        stop();
        thread = new Thread(this, "Pathfinder");
        thread.setDaemon(true);
        thread.start();
    }

    /** 停止寻路线程并清空任务队列。 */
    private void stop(){
        Thread t = thread;
        if(t != null){
            t.interrupt();
            thread = null;
        }
        queue.clear();
    }

    /** 世界加载：重建内部网格并启动线程。挂接在 WorldLoadEvent 上。 */
    private void onWorldLoad(){
        stop();

        World w = Vars.world;
        tiles = new int[w.width()][w.height()];
        pathMap = new PathData[Team.all().length][PathTarget.all.length];
        created = new boolean[Team.all().length][PathTarget.all.length];
        list.clear();

        for(int x = 0; x < w.width(); x++){
            for(int y = 0; y < w.height(); y++){
                Tile t = w.tile(x, y);
                if(t == null) continue;
                tiles[x][y] = packTile(t);
            }
        }

        start();
    }

    /** 更新一个瓦片的寻路数据（建筑改变时调用），并要求现有流场重算。 */
    public void updateTile(Tile tile){
        if(tile == null || tiles == null) return;
        int x = tile.x, y = tile.y;
        if(x < 0 || y < 0 || x >= tiles.length || y >= tiles[0].length) return;
        tiles[x][y] = packTile(tile);

        //建筑放置/摧毁可能改变目标集合（例如核心被摧毁）。
        for(PathData[] paths : pathMap){
            for(PathData path : paths){
                if(path == null) continue;
                synchronized(path.targets){
                    path.targets.clear();
                    path.target.getTargets(path.team, path.targets);
                }
            }
        }

        queue.post(() -> {
            for(PathData path : list){
                updateTargets(path, x, y);
            }
        });
    }

    /** 后台线程实现。 */
    @Override
    public void run(){
        while(true){
            try{
                queue.run();

                //总更新耗时不超过 maxUpdate
                for(PathData data : list){
                    updateFrontier(data, maxUpdate / Math.max(list.size(), 1));
                }

                try{
                    Thread.sleep(updateInterval);
                }catch(InterruptedException e){
                    //被打断则停止循环
                    return;
                }
            }catch(Throwable e){
                e.printStackTrace();
            }
        }
    }

    /** 主线程专用：取得单位下一步应前往的瓦片。数据未就绪时返回原瓦片（退化为直线）。 */
    public Tile getTargetTile(Tile tile, Team team, PathTarget target){
        if(tile == null) return null;

        PathData data = pathMap[team.id][target.ordinal()];

        if(data == null){
            //该组合尚未创建时，现场请求创建
            if(!created[team.id][target.ordinal()]){
                created[team.id][target.ordinal()] = true;
                //getTargets 必须在主线程运行
                IntArray targets = target.getTargets(team, new IntArray());
                queue.post(() -> createPath(team, target, targets));
            }
            return tile;
        }

        int[][] values = data.weights;
        int value = values[tile.x][tile.y];

        Tile current = null;
        int tl = 0;
        for(GridPoint2 point : Geometry.d8){
            int dx = tile.x + point.x, dy = tile.y + point.y;

            Tile other = Vars.world.tile(dx, dy);
            if(other == null) continue;

            if(values[dx][dy] < value && (current == null || values[dx][dy] < tl) && !other.solid() && other.floor().drownTime <= 0 &&
            !(point.x != 0 && point.y != 0 && (solid(tile.x + point.x, tile.y) || solid(tile.x, tile.y + point.y)))){ //对角卡角
                current = other;
                tl = values[dx][dy];
            }
        }

        if(current == null || tl == PathTile.impassable) return tile;

        return current;
    }

    /** 某坐标是否实心（越界视为实心）。 */
    private boolean solid(int x, int y){
        Tile t = Vars.world.tile(x, y);
        return t == null || t.solid();
    }

    /** @return 该瓦片是否可被此队伍通行。后台线程专用。 */
    private boolean passable(int x, int y, Team team){
        int tile = tiles[x][y];
        return PathTile.passable(tile) || (PathTile.team(tile) != team.id && PathTile.team(tile) != (int)Team.derelict.id);
    }

    /**
     * 清空前沿、递增搜索并设置全部流场源。
     * 后台线程专用。
     */
    private void updateTargets(PathData path, int x, int y){
        if(x < 0 || y < 0 || x >= path.weights.length || y >= path.weights[0].length) return;

        if(path.weights[x][y] == 0){
            //此位置曾是目标
            path.frontier.clear();
        }else if(!path.frontier.isEmpty()){
            //该路径正在处理中，跳过
            return;
        }

        //把该瓦片设为不可通行
        if(!passable(x, y, path.team)){
            path.weights[x][y] = PathTile.impassable;
        }

        //递增搜索、清空前沿
        path.search++;
        path.frontier.clear();

        synchronized(path.targets){
            //加入目标
            for(int i = 0; i < path.targets.size; i++){
                int pos = path.targets.get(i);
                int tx = Pos.x(pos), ty = Pos.y(pos);

                path.weights[tx][ty] = 0;
                path.searches[tx][ty] = (short)path.search;
                path.frontier.addFirst(pos);
            }
        }
    }

    /** 新建一个流向某目标的流场。后台线程专用。 */
    private PathData createPath(Team team, PathTarget target, IntArray targets){
        PathData path = new PathData(team, target, Vars.world.width(), Vars.world.height());

        list.add(path);
        pathMap[team.id][target.ordinal()] = path;

        //从传入数组取目标
        synchronized(path.targets){
            path.targets.clear();
            path.targets.addAll(targets);
        }

        //默认填满不可通行
        for(int x = 0; x < Vars.world.width(); x++){
            for(int y = 0; y < Vars.world.height(); y++){
                path.weights[x][y] = PathTile.impassable;
            }
        }

        //加入目标
        for(int i = 0; i < path.targets.size; i++){
            int pos = path.targets.get(i);
            path.weights[Pos.x(pos)][Pos.y(pos)] = 0;
            path.frontier.addFirst(pos);
        }

        return path;
    }

    /** 更新某路径的前沿。后台线程专用。 */
    private void updateFrontier(PathData path, long nsToRun){
        long start = System.nanoTime();

        while(path.frontier.size > 0 && (nsToRun < 0 || System.nanoTime() - start <= nsToRun)){
            Tile tile = Vars.world.tile(path.frontier.removeLast());
            if(tile == null || path.weights == null) return; //出错，放弃
            int cost = path.weights[tile.x][tile.y];

            //寻路溢出时放弃，下一轮瓦片更新会处理
            if(path.frontier.size >= Vars.world.width() * Vars.world.height()){
                path.frontier.clear();
                return;
            }

            if(cost != PathTile.impassable){
                for(GridPoint2 point : Geometry.d4){
                    int dx = tile.x + point.x, dy = tile.y + point.y;
                    Tile other = Vars.world.tile(dx, dy);

                    if(other != null && (path.weights[dx][dy] > cost + other.cost || path.searches[dx][dy] < path.search) && passable(dx, dy, path.team)){
                        if(other.cost < 0) throw new IllegalArgumentException("Tile cost cannot be negative! " + other);
                        path.frontier.addFirst(Pos.get(dx, dy));
                        path.weights[dx][dy] = cost + other.cost;
                        path.searches[dx][dy] = (short)path.search;
                    }
                }
            }
        }
    }

    /** 路径目标：定义一组目标。 */
    public enum PathTarget{
        enemyCores((team, out) -> {
            //核心/索引系统尚未独立移植，直接扫描地图中的核心方块。
            if(Vars.world == null) return;
            for(Tile tile : Vars.world.tiles){
                if(tile != null && tile.entity != null && tile.block().flags.contains(BlockFlag.core) && team.isEnemy(tile.getTeam())){
                    out.add(tile.pos());
                }
            }
        }),
        rallyPoints((team, out) -> {
            //TODO：集结建筑尚未移植
        });

        public static final PathTarget[] all = values();

        private final BiConsumer<Team, IntArray> targeter;

        PathTarget(BiConsumer<Team, IntArray> targeter){
            this.targeter = targeter;
        }

        /** 取目标。必须跑在主线程。 */
        public IntArray getTargets(Team team, IntArray out){
            targeter.accept(team, out);
            return out;
        }
    }

    /** 一个流向某组目标的流场的数据。 */
    class PathData{
        /** 该路径所属队伍。 */
        final Team team;
        /** 被寻路的目标标记。 */
        final PathTarget target;
        /** 到各瓦片的通行成本。 */
        final int[][] weights;
        /** 各位置搜索 ID——最高/最新搜索优先，会被覆盖。 */
        final short[][] searches;
        /** 搜索前沿，存放 Pos。 */
        final IntQueue frontier = new IntQueue();
        /** 全部目标位置；成本为 0，访问需同步。 */
        final IntArray targets = new IntArray();
        /** 当前搜索 ID。 */
        int search = 1;

        PathData(Team team, PathTarget target, int width, int height){
            this.team = team;
            this.target = target;

            this.weights = new int[width][height];
            this.searches = new short[width][height];
            this.frontier.ensureCapacity((width + height) * 3);
        }
    }
}