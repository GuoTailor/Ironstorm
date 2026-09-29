package com.phoenix.game.maps.generators;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntFloatMap;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.func.Intc2;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.Simplex;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.struct.Array;
import com.phoenix.game.struct.GridBits;
import com.phoenix.game.util.Structs;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Pos;
import com.phoenix.game.world.Tile;

import java.util.PriorityQueue;

/**
 * 默认地形生成器基类（地形 / 矿脉 / 装饰）。参照 Mindustry
 * {@code mindustry.maps.generators.BasicGenerator} 移植。
 *
 * <p>核心机制是 {@link #pass(Tile[][], Intc2)}：它把「每格的 floor/block/ore 读进
 * {@link #floor}/{@link #block}/{@link #ore} 字段 → 调回调改写 → 再写回新瓦片」这套
 * 读-改-写循环包起来。{@link #ores}/{@link #terrain}/{@link #noise}/{@link #overlay}/
 * {@link #scatter}/{@link #distort}/{@link #tech} 全都是它的封装。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li>{@code Simplex} 构造器：原版可无参（{@code new Simplex()}）；本项目只有 {@code Simplex(int seed)}
 *       → 写成 {@code new Simplex(0)}，随后由 {@link #generate(Tile[][])} 重设种子。</li>
 *   <li>{@code Simplex.octaveNoise2D} 的签名不同：原版 {@code (double,double,double,double,double) -> double}，
 *       本项目 {@code (int octaves, float falloff, float scl, float x, float y) -> float}
 *       → 所有 double 字面量要写 {@code 0.7f} 这种，且 octaves 位置不能传 {@code 1f}。</li>
 *   <li>{@code Geometry.d4} 在本项目是 {@code GridPoint2[]}（原版 {@code Point2[]}），且元素顺序相反
 *       （本项目 0=+y 起、原版 0=+x 起）。原版 {@code Geometry.d4(rotation)} 取方向的方法本项目没有
 *       → 写成 {@code Geometry.d4[rotation]} 下标取。因为 {@code Tile.relativeTo} 与本项目 d4 是同一套
 *       索引约定，自洽，不需要像其它移植点那样做 90° 换算。</li>
 *   <li>{@code IntFloatMap} 直接用 libgdx 的 {@code com.badlogic.gdx.utils.IntFloatMap}（同名同语义）。</li>
 *   <li>{@code Structs.inBounds(x, y, width, height)} 本项目 <b>没有</b>（只有数组版）
 *       → {@link #erase} 里内联成 {@code wx >= 0 && wy >= 0 && wx < width && wy < height}。</li>
 *   <li>{@link #tech(Tile[][])} 里的 {@code Blocks.darkPanel3}/{@code Blocks.darkPanel4} <b>已注册</b>
 *       （T8a 还原）→ 照原版用双色深色地板。</li>
 *   <li>{@code Mathf.chance(double)} 本项目只有 {@code chance(float)} → {@code 0.7} 要写 {@code 0.7f}。</li>
 *   <li>{@code pass()} 里重建瓦片同样要对 floor/ore 做 {@code (Floor)} 强转（见 {@link RandomGenerator}）。</li>
 *   <li>{@code Tile.cost} 在本项目是 {@code byte}（原版 {@code short}）——赋值 {@code 2} 不受影响。</li>
 *   <li><b>⚠读方块一律用 {@code blockRaw()}，不能用 {@code block()}</b>：原版 {@code Tile.block()} 返回瓦片
 *       自己存的方块；本项目 {@code block()} 会**解析多格链接**（卫星瓦片转发到中心方块）。
 *       地形生成是"读原始状态 → 改写"的循环，用 {@code block()} 会把卫星瓦片覆盖成中心方块、
 *       拆散多格建筑。{@link #pass}/{@link #noise}/{@link #inverseFloodFill} 已全部改用 {@code blockRaw()}。</li>
 *   <li><b>⚠瓦片对象会被替换</b>：{@link #pass} 与 {@link RandomGenerator#generate} 都是
 *       {@code tiles[x][y] = new Tile(...)}（原版同样如此）。原版之所以安全，是因为
 *       {@code World.createTiles} 把**同一个二维数组**交给生成器，替换即等于改世界；
 *       本项目 {@code World} 用一维 {@code Tile[]}，如果调用方只是把瓦片"拷"进世界，
 *       生成器替换掉的旧对象仍留在世界里 → <b>调用方必须在生成结束后把二维数组重新同步回
 *       {@code Vars.world.tiles}</b>（目前没有这样的调用方，见 {@link Generator} 的类注释）。</li>
 * </ul>
 */
public abstract class BasicGenerator extends RandomGenerator{
    protected static final DistanceHeuristic manhattan = (x1, y1, x2, y2) -> Math.abs(x1 - x2) + Math.abs(y1 - y2);

    protected Array<Block> ores;
    //刻意：原版 = new Simplex()（无参）；本项目 Simplex 只有带种子构造器 → new Simplex(0)，随后 setSeed
    protected Simplex sim = new Simplex(0);
    protected Simplex sim2 = new Simplex(0);

    public BasicGenerator(int width, int height, Block... ores){
        super(width, height);
        this.ores = Array.with(ores);
    }

    @Override
    public void generate(Tile[][] tiles){
        int seed = Mathf.random(99999999);
        sim.setSeed(seed);
        sim2.setSeed(seed + 1);
        super.generate(tiles);
    }

    /** 按两层噪声铺矿脉（稀有矿判定在前、命中即停）。 */
    public void ores(Tile[][] tiles){
        pass(tiles, (x, y) -> {
            if(ores != null){
                int offsetX = x - 4, offsetY = y + 23;
                for(int i = ores.size - 1; i >= 0; i--){
                    Block entry = ores.get(i);
                    if(Math.abs(0.5f - sim.octaveNoise2D(2, 0.7f, 1f / (40 + i * 2), offsetX, offsetY + i*999)) > 0.26f &&
                    Math.abs(0.5f - sim2.octaveNoise2D(1, 1, 1f / (30 + i * 4), offsetX, offsetY - i*999)) > 0.37f){
                        ore = entry;
                        break;
                    }
                }
            }
        });
    }

    /** 按噪声把成片区域变成实心岩壁（{@code dst}），并让地图边缘更密。 */
    public void terrain(Tile[][] tiles, Block dst, float scl, float mag, float cmag){
        pass(tiles, (x, y) -> {
            double rocks = sim.octaveNoise2D(5, 0.5f, 1f / scl, x, y) * mag
            + Mathf.dst((float)x / width, (float)y / height, 0.5f, 0.5f) * cmag;

            double edgeDist = Math.min(x, Math.min(y, Math.min(Math.abs(x - (width - 1)), Math.abs(y - (height - 1)))));
            double transition = 5;
            if(edgeDist < transition){
                rocks += (transition - edgeDist) / transition / 1.5;
            }

            if(rocks > 0.9){
                block = dst;
            }
        });
    }

    /** 按噪声换地板；该格原本是实心方块时连方块一起换。 */
    public void noise(Tile[][] tiles, Block floor, Block block, int octaves, float falloff, float scl, float threshold){
        sim.setSeed(Mathf.random(99999));
        pass(tiles, (x, y) -> {
            if(sim.octaveNoise2D(octaves, falloff, 1f / scl, x, y) > threshold){
                Tile tile = tiles[x][y];
                this.floor = floor;
                //刻意：同 pass()，本项目 block() 会解析多格链接 → 用 blockRaw()
                if(tile.blockRaw().solid){
                    this.block = block;
                }
            }
        });
    }

    /** 在指定地板上按噪声+概率铺覆盖层（矿脉/装饰）。 */
    public void overlay(Tile[][] tiles, Block floor, Block block, float chance, int octaves, float falloff, float scl, float threshold){
        sim.setSeed(Mathf.random(99999));
        pass(tiles, (x, y) -> {
            if(sim.octaveNoise2D(octaves, falloff, 1f / scl, x, y) > threshold && Mathf.chance(chance) && tiles[x][y].floor() == floor){
                ore = block;
            }
        });
    }

    /**
     * 铺「科技网格」地板（战役图里的深色地板方块阵）。照原版用 {@code Blocks.darkPanel3}（格线内）
     * 与 {@code Blocks.darkPanel4}（格线外）两种深色地板（均已注册）。
     */
    public void tech(Tile[][] tiles){
        Block[] blocks = {Blocks.darkPanel3};
        int secSize = 20;
        pass(tiles, (x, y) -> {
            int mx = x % secSize, my = y % secSize;
            int sclx = x / secSize, scly = y / secSize;
            if(noise(sclx, scly, 10f, 1f) > 0.63f && (mx == 0 || my == 0 || mx == secSize - 1 || my == secSize - 1)){
                if(Mathf.chance(noise(x + 0x231523, y, 40f, 1f))){
                    floor = Structs.random(blocks);
                    if(Mathf.dst(mx, my, secSize/2, secSize/2) > secSize/2f + 2){
                        floor = Blocks.darkPanel4;
                    }
                }

                if(block.solid && Mathf.chance(0.7f)){
                    block = Blocks.darkMetal;
                }
            }
        });
    }

    /** 按噪声把整张图做扭曲（地板与方块一起采样邻格）。 */
    public void distort(Tile[][] tiles, float scl, float mag){
        Block[][] blocks = new Block[width][height];
        Floor[][] floors = new Floor[width][height];

        each((x, y) -> {
            float cx = x + noise(x, y, scl, mag) - mag / 2f, cy = y + noise(x, y + 1525215f, scl, mag) - mag / 2f;
            Tile other = tiles[Mathf.clamp((int)cx, 0, width-1)][Mathf.clamp((int)cy, 0, height-1)];
            //刻意：原版 = other.block()（原始值）；本项目 block() 会解析多格链接 → 用 blockRaw()
            blocks[x][y] = other.blockRaw();
            floors[x][y] = other.floor();
        });

        pass(tiles, (x, y) -> {
            floor = floors[x][y];
            block = blocks[x][y];
        });
    }

    /** 按概率把目标地板/方块换成另一种。 */
    public void scatter(Tile[][] tiles, Block target, Block dst, float chance){
        pass(tiles, (x, y) -> {
            if(!Mathf.chance(chance)) return;
            if(floor == target){
                floor = dst;
            }else if(block == target){
                block = dst;
            }
        });
    }

    /**
     * 在指定地板的**空格**上按概率放一个「方块类」装饰（StaticWall / Rock / StaticTree）。
     *
     * <p>⚠ <b>为什么需要这个方法</b>：{@link #scatter}/{@link #overlay} 会把 dst 写进 **floor/ore 槽**，
     * 而 {@link #pass} 写回瓦片时对这两个槽做 `(Floor)` 强转（本项目 `Tile` 收对象、原版收 id）
     * → 传 `StaticWall`/`Rock`/`StaticTree`（**都不是 Floor**）会抛 `ClassCastException`。
     * 放岩壁/树本来就该写 **block 槽**，所以用本方法（等价于"把装饰放对槽位"）。
     *
     * @param floor  只在**该地板**的空格上放（传 {@code null} 表示不限地板）
     * @param dst    要放的方块类装饰
     * @param chance 每格概率
     */
    public void decorateBlock(Tile[][] tiles, Block floor, Block dst, float chance){
        pass(tiles, (x, y) -> {
            if((floor == null || this.floor == floor) && block == Blocks.air && Mathf.chance(chance)){
                block = dst;
            }
        });
    }

    /** 遍历全部坐标（只读，不改瓦片）。 */
    public void each(Intc2 r){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                r.get(x, y);
            }
        }
    }

    protected float noise(float x, float y, float scl, float mag){
        //刻意：原版写 sim2.octaveNoise2D(1f, ...)，但本项目 Simplex 的 octaves 是 int → 写 1
        return sim2.octaveNoise2D(1, 0f, 1f / scl, x + 0x361266f, y + 0x251259f) * mag;
    }

    /** 读-改-写循环：把每格的 floor/block/ore 读进字段 → 回调 → 写回新瓦片。 */
    public void pass(Tile[][] tiles, Intc2 r){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                floor = tiles[x][y].floor();
                //刻意：原版 block() 返回的就是瓦片自己存的方块；本项目 Tile.block() 会**解析多格链接**
                //     （卫星瓦片转发到中心方块）→ 地形读写必须用 blockRaw()，否则会把卫星瓦片覆盖成中心方块、
                //     拆散多格建筑
                block = tiles[x][y].blockRaw();
                ore = tiles[x][y].overlay();
                r.get(x, y);
                //刻意：本项目 Tile 直接收 Floor/Block 对象（原版是三个 id）→ floor/ore 强转
                tiles[x][y] = new Tile(x, y, (Floor)floor, (Floor)ore, block);
            }
        }
    }

    /** 沿路径用圆形笔刷清空方块。 */
    public void brush(Tile[][] tiles, Array<Tile> path, int rad){
        path.each(tile -> erase(tiles, tile.x, tile.y, rad));
    }

    /** 以 (cx, cy) 为圆心、rad 为半径清空方块。 */
    public void erase(Tile[][] tiles, int cx, int cy, int rad){
        for(int x = -rad; x <= rad; x++){
            for(int y = -rad; y <= rad; y++){
                int wx = cx + x, wy = cy + y;
                //刻意：原版 = Structs.inBounds(wx, wy, width, height)；本项目 Structs 只有数组版 inBounds → 内联
                if(wx >= 0 && wy >= 0 && wx < width && wy < height && Mathf.dst(x, y, 0, 0) <= rad){
                    Tile other = tiles[wx][wy];
                    other.setBlock(Blocks.air);
                }
            }
        }
    }

    /**
     * A* 寻路（四方向），返回从起点到终点的瓦片路径。
     * <p>每格的代价由 {@code th} 决定，启发式由 {@code dh} 决定；走过的方向会写进
     * {@code Tile.rotation}，回溯时靠 {@code Geometry.d4[rotation]} 反推上一格。
     */
    public Array<Tile> pathfind(Tile[][] tiles, int startX, int startY, int endX, int endY, TileHueristic th, DistanceHeuristic dh){
        Tile start = tiles[startX][startY];
        Tile end = tiles[endX][endY];
        GridBits closed = new GridBits(width, height);
        IntFloatMap costs = new IntFloatMap();
        PriorityQueue<Tile> queue = new PriorityQueue<>(tiles.length * tiles[0].length / 2, (a, b) -> Float.compare(costs.get(a.pos(), 0f) + dh.cost(a.x, a.y, end.x, end.y), costs.get(b.pos(), 0f) + dh.cost(b.x, b.y, end.x, end.y)));
        queue.add(start);
        boolean found = false;
        while(!queue.isEmpty()){
            Tile next = queue.poll();
            float baseCost = costs.get(next.pos(), 0f);
            if(next == end){
                found = true;
                break;
            }
            closed.set(next.x, next.y);
            for(GridPoint2 point : Geometry.d4){
                int newx = next.x + point.x, newy = next.y + point.y;
                //刻意：原版 = Structs.inBounds(newx, newy, width, height)；本项目 Structs 只有数组版 → 内联
                if(newx >= 0 && newy >= 0 && newx < width && newy < height){
                    Tile child = tiles[newx][newy];
                    if(!closed.get(child.x, child.y)){
                        closed.set(child.x, child.y);
                        child.rotation(child.relativeTo(next.x, next.y));
                        costs.put(child.pos(), th.cost(child) + baseCost);
                        queue.add(child);
                    }
                }
            }
        }

        Array<Tile> out = new Array<>();

        if(!found) return out;

        Tile current = end;
        while(current != start){
            out.add(current);
            //刻意：原版 = Geometry.d4(current.rotation())；本项目 Geometry 没有该方法 → 下标取
            GridPoint2 p = Geometry.d4[current.rotation()];
            current = tiles[current.x + p.x][current.y + p.y];
        }

        out.reverse();

        return out;
    }

    /**
     * 反向洪水填充：从 {@code start} 出发把**可达**的空地标记一遍，
     * 然后把「没被标记到、且是空气」的格子全部换成 {@code block}（即不可达区域封成岩壁）。
     */
    public void inverseFloodFill(Tile[][] tiles, Tile start, Block block){
        IntArray arr = new IntArray();
        arr.add(start.pos());
        while(!arr.isEmpty()){
            int i = arr.pop();
            int x = Pos.x(i), y = Pos.y(i);
            tiles[x][y].cost = 2;
            for(GridPoint2 point : Geometry.d4){
                int newx = x + point.x, newy = y + point.y;
                //刻意：同 pathfind，Structs.inBounds(x, y, width, height) 本项目没有 → 内联
                if(newx >= 0 && newy >= 0 && newx < width && newy < height){
                    Tile child = tiles[newx][newy];
                    //刻意：同 pass()，本项目 block() 会解析多格链接 → 用 blockRaw()（"这格是不是空的"）
                    if(child.blockRaw() == Blocks.air && child.cost != 2){
                        child.cost = 2;
                        arr.add(child.pos());
                    }
                }
            }
        }

        for(int x = 0; x < width; x ++){
            for(int y = 0; y < height; y++){
                Tile tile = tiles[x][y];
                //刻意：同上，用 blockRaw()
                if(tile.cost != 2 && tile.blockRaw() == Blocks.air){
                    tile.setBlock(block);
                }
            }
        }
    }

    /**
     * 在地图四角内侧放波次出生点（二维版；对应本项目 {@code World.placeSpawnPoints} 的语义）。
     * <p>供各区域生成器共用 —— {@code Control.playZone} 只补放玩家核心，**敌方波次的出生点必须由生成器放**，
     * 否则 {@code WaveSpawner} 扫不到 spawn overlay、敌人无处可刷。
     * <p>瓦片不可行走时向外螺旋查找最近的可行走瓦片（与 {@code World.findWalkable} 同口径）。
     */
    protected void spawnPoints(Tile[][] tiles){
        int inset = 6;
        int[][] points = {
            {inset, inset}, {width - 1 - inset, inset},
            {inset, height - 1 - inset}, {width - 1 - inset, height - 1 - inset}
        };

        for(int[] point : points){
            //⚠ 先在角落清出一小块空地：terrain() 的噪声尺度很大（scl=60+），一整片角落可能被岩壁填满，
            //   不清空的话 findWalkable 常常找不到落点 → 该角落没有 spawn overlay，
            //   WaveSpawner 刷不出敌人（实测 10 个新生成器里有 4 个 spawn=0）
            erase(tiles, point[0], point[1], 3);
            Tile spawn = findWalkable(tiles, point[0], point[1]);
            if(spawn != null){
                spawn.setOverlay(Blocks.spawn);
            }
        }
    }

    /** @return 以 (x,y) 为中心向外查找的最近可行走瓦片（二维版）。 */
    protected Tile findWalkable(Tile[][] tiles, int x, int y){
        //半径给到 24：角落被清空后 radius 0 就该命中；留大余量是为了「清空的那块正好是液体」等边角情况
        for(int radius = 0; radius < 24; radius++){
            for(int dx = -radius; dx <= radius; dx++){
                for(int dy = -radius; dy <= radius; dy++){
                    int tx = x + dx, ty = y + dy;
                    if(tx < 0 || ty < 0 || tx >= width || ty >= height) continue;
                    Tile tile = tiles[tx][ty];
                    //刻意：判据用 floor().isLiquid（原 World.findWalkable 只排除 Blocks.water，漏了
                    //     sandWater/deepwater/tar 等液体地板）→ 出生点不能落在液体上
                    if(tile != null && !tile.solid() && !tile.floor().isLiquid){
                        return tile;
                    }
                }
            }
        }
        return null;
    }

    public interface DistanceHeuristic{
        float cost(int x1, int y1, int x2, int y2);
    }

    public interface TileHueristic{
        float cost(Tile tile);
    }
}
