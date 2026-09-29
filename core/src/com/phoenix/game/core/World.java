package com.phoenix.game.core;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.game.EventType;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.Simplex;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Pos;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockFlag;

import static com.phoenix.game.Vars.tilesize;

/**
 * 最小实现：地图/世界。参照 Mindustry mindustry.core.World 移植。
 * 目前支持程序化生成地图、瓦片查询与射线遍历；存档读写由 {@link com.phoenix.game.io.SaveIO} 负责（自有 PHNX 格式）。
 */
public class World {
    public Tile[] tiles = {};
    public int width, height;
    private boolean generating;

    /** 当前加载的地图；程序化生成的图没有 Map 对象，保持 null（对应原版 {@code World.currentMap}，原版 World.java:27）。 */
    private com.phoenix.game.maps.Map currentMap;

    /** @return 当前地图；程序化生成的图为 null（对应原版 {@code World.getMap()}，原版 World.java:61-63）。 */
    public com.phoenix.game.maps.Map getMap(){
        return currentMap;
    }

    /** 设置当前地图（对应原版 {@code World.setMap(Map)}，原版 World.java:65-67）。 */
    public void setMap(com.phoenix.game.maps.Map map){
        this.currentMap = map;
    }

    /** 程序化生成一张地图（内部走 {@link #createTiles} + {@link #generateTerrain}）。 */
    public void createMap(int width, int height){
        generating = true;
        Tile[][] grid = createTiles(width, height);
        generateTerrain(grid, width, height);
        //普通开局路径不经 {@link #endMapLoad}（本项目自加的入口）→ 暗化在这里补跑
        //（对应原版"所有建图路径最终都会经 endMapLoad 跑 addDarkness"的语义）；
        //headless 服务器无渲染，跳过（与原版 `if(!headless)` 一致）
        if(!Vars.headless){
            addDarkness();
        }
        generating = false;
        rebuildPowerGraphs();
    }

    /**
     * 建立 w×h 的空瓦片网格（对应原版 {@code World.createTiles}）。
     * <p>本项目的一维 {@link #tiles} 是权威存储，二维网格是**同一批 Tile 对象的视图**；
     * 生成器若在 {@code generate} 里 {@code new Tile(...)} 替换对象，必须由
     * {@link #endMapLoad(Tile[][])} 展平回一维（{@code Generator} 类注释已预告这一点）。
     * @return 二维视图（索引一律 {@code grid[x][y]}，与原版 {@code Generator.generate(Tile[][])} 的契约一致）
     */
    public Tile[][] createTiles(int w, int h){
        this.width = w;
        this.height = h;
        this.tiles = new Tile[w * h];

        Tile[][] grid = new Tile[w][h];
        for(int x = 0; x < w; x++){
            for(int y = 0; y < h; y++){
                Tile tile = new Tile(x, y);
                grid[x][y] = tile;
                tiles[y * w + x] = tile;
            }
        }
        return grid;
    }

    /** 生成前：挂起逐瓦片通知（对应原版 {@code World.beginMapLoad}）。 */
    public void beginMapLoad(){
        generating = true;
    }

    /**
     * 生成后收尾（对应原版 {@code World.endMapLoad}）：把二维网格展平回一维 {@link #tiles} 并重建电网。
     * <p>⚠ <b>必须先展平</b>：{@code BasicGenerator} 系列会 {@code tiles[x][y] = new Tile(...)} **替换对象**，
     * 一维数组里的旧引用就失效了。
     * <p>原版还会在这里做 {@code prepareTiles}（铺多格 BlockPart）/遮蔽/实体组 resize；本项目这些由
     * {@code Tile.setBlock} 自身与各系统在 {@code WorldLoadEvent} 里完成，故只保留展平 + 暗化 + 电网重建。
     */
    public void endMapLoad(Tile[][] grid){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                tiles[y * width + x] = grid[x][y];
            }
        }

        //世界暗化（对应原版 endMapLoad 里的 `if(!headless){ addDarkness(tiles); }`，原版 World.java:178-180）：
        //给自然墙按暴露程度写入 0-5 的暗度（复用 rotation 字段），渲染侧的 fog 烘焙据此涂黑幕
        if(!Vars.headless){
            addDarkness();
        }

        generating = false;
        rebuildPowerGraphs();
    }

    /**
     * 世界暗化计算（对应原版 {@code World.addDarkness}，原版 World.java:300-354）：
     * 自然墙（{@link Tile#isDarkened()}）先全部标记为最暗（4），再迭代 4 轮向暴露面衰减 ——
     * 每轮里"四邻存在更小暗度"的格子暗度减 1，得到由墙体外缘向内的渐变；
     * 完全被同类包围（自身与四邻暗度都是 4）的格子记 5，表示"连墙贴图都不必画"（被黑幕完全盖住）。
     * 结果直接写进 {@code tile.rotation} —— 自然墙不使用朝向语义，原版就是复用该字段存暗度；
     * 渲染侧（{@code graphics/BlockRenderer} 的 fog 烘焙 + {@code Renderer.drawTiles} 的墙贴图跳过）按它消费。
     * <p>⚠ 签名差异：原版是 {@code addDarkness(Tile[][] tiles)}（二维）；本项目一维存储，内部按
     * {@code y * width + x} 索引。{@code Structs.inBounds} → 手写范围判断；{@code Mathf.num} → 三元。
     */
    public void addDarkness(){
        byte[][] dark = new byte[width][height];
        byte[][] writeBuffer = new byte[width][height];

        byte darkIterations = 4;
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                Tile tile = tiles[y * width + x];
                if(tile.isDarkened()){
                    dark[x][y] = darkIterations;
                }
            }
        }

        for(int i = 0; i < darkIterations; i++){
            for(int x = 0; x < width; x++){
                for(int y = 0; y < height; y++){
                    boolean min = false;
                    for(GridPoint2 point : Geometry.d4){
                        int newX = x + point.x, newY = y + point.y;
                        if(newX >= 0 && newY >= 0 && newX < width && newY < height && dark[newX][newY] < dark[x][y]){
                            min = true;
                            break;
                        }
                    }
                    writeBuffer[x][y] = (byte)Math.max(0, dark[x][y] - (min ? 1 : 0));
                }
            }

            for(int x = 0; x < width; x++){
                System.arraycopy(writeBuffer[x], 0, dark[x], 0, height);
            }
        }

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                Tile tile = tiles[y * width + x];
                if(tile.isDarkened()){
                    tile.rotation(dark[x][y]);
                }
                if(dark[x][y] == 4){
                    boolean full = true;
                    for(GridPoint2 p : Geometry.d4){
                        int px = p.x + x, py = p.y + y;
                        if(px >= 0 && py >= 0 && px < width && py < height
                            && !(tiles[py * width + px].isDarkened() && dark[px][py] == 4)){
                            full = false;
                            break;
                        }
                    }

                    if(full) tile.rotation(5);
                }
            }
        }
    }

    /**
     * 用生成器加载一张地图（对应原版 {@code World.loadGenerator(Generator)}）。
     * <p>顺序照原版逐字：{@code beginMapLoad} → {@code createTiles(w, h)} → {@code generator.generate(grid)} → {@code endMapLoad}。
     */
    public void loadGenerator(com.phoenix.game.maps.generators.Generator generator){
        beginMapLoad();
        Tile[][] grid = createTiles(generator.width, generator.height);
        generator.generate(grid);
        endMapLoad(grid);
    }

    /**
     * 本项目的地形生成：边界墙 + 零星岩壁 + 矿脉 + 出生点。
     * <p>原版这一步由各 {@code Generator} 子类负责（`MapGenerator` 是"加载内置 .msav"）；
     * 本项目读不了原版 `.msav`（见 转换.md 324）→ 把 {@code createMap} 原有的程序化生成抽到这里，
     * 供 {@link #createMap} 与 {@code MapGenerator} 的退化分支共用。
     * <p>⚠ 会同时填 {@code grid} 与一维 {@link #tiles}（两者共享同一批对象）。
     */
    public void generateTerrain(Tile[][] grid, int w, int h){
        this.width = w;
        this.height = h;
        if(this.tiles.length != w * h) this.tiles = new Tile[w * h];

        for(int y = 0; y < h; y++){
            for(int x = 0; x < w; x++){
                Tile tile = grid[x][y] != null ? grid[x][y] : new Tile(x, y);
                grid[x][y] = tile;
                tiles[y * w + x] = tile;

                tile.setFloor(floorAt(x, y));

                //边界墙：原版拿 darkMetal 当地图边缘（实心、不可拆），顺便防止单位跑出地图
                if(x == 0 || y == 0 || x == w - 1 || y == h - 1){
                    tile.setBlock(Blocks.darkMetal);
                }else if(wallAt(x, y)){
                    //零星的自然岩壁：种类与所在地表匹配（对应原版按生物群系挑 StaticWall）
                    Block rock = rockFor(tile.floor());
                    if(rock != null) tile.setBlock(rock);
                }
            }
        }

        generateOres();
        placeSpawnPoints();
    }

    /**
     * 矿脉生成（参照 Mindustry OriginalGenerator/BasicGenerator.ores 的双层噪声结构）。
     * <p>每种矿需同时通过两层 Simplex 噪声：大尺度团块噪声（2 octaves，决定矿脉成片）+
     * 细尺度掩膜（1 octave，把团块打成不规则矿脉），稀有矿判定在前、命中即停。
     * <p>阈值按稀有度递增（对应原版 OreBlock.oreThreshold 0.81→0.882 的递进设计；
     * 数值按本项目噪声分布校准：铜约 7%、铅 6%、煤 5%、钛 2.6%、钍 1.8% 的可采覆盖率）。
     * <p>刻意收紧：液体地板与实心岩壁上不铺矿（原版允许，但矿盖在水面上视觉突兀、
     * 盖在岩壁下永远看不见）。
     */
    private void generateOres(){
        int seed = MathUtils.random(99999999);
        Simplex sim = new Simplex(seed);
        Simplex sim2 = new Simplex(seed + 1);
        //稀有度从低到高排列（原版数组序），从末尾（最稀有）向首遍历，命中即停
        Floor[] ores = {Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal, Blocks.oreTitanium, Blocks.oreThorium};
        //大尺度团块阈值（稀有度递增）
        float[] blobThresholds = {0.13f, 0.13f, 0.18f, 0.21f, 0.26f};
        float maskThreshold = 0.30f;

        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){
                Tile tile = tiles[y * width + x];
                if(tile.floor().isLiquid || tile.solid()) continue;

                int offsetX = x - 4, offsetY = y + 23;
                for(int i = ores.length - 1; i >= 0; i--){
                    if(Math.abs(0.5f - sim.octaveNoise2D(2, 0.7f, 1f / (40 + i * 2), offsetX, offsetY + i * 999)) > blobThresholds[i] &&
                       Math.abs(0.5f - sim2.octaveNoise2D(1, 1f, 1f / (30 + i * 4), offsetX, offsetY - i * 999)) > maskThreshold){
                        tile.setOverlay(ores[i]);
                        break;
                    }
                }
            }
        }
    }

    /**
     * 在地图四角内侧放置波次出生点（对应原版地图的 spawn overlay）。
     * 瓦片不可行走时向外螺旋查找最近的可行走瓦片；找不到就跳过。
     */
    private void placeSpawnPoints(){
        int inset = 6;
        int[][] points = {
            {inset, inset}, {width - 1 - inset, inset},
            {inset, height - 1 - inset}, {width - 1 - inset, height - 1 - inset}
        };

        for(int[] point : points){
            Tile spawn = findWalkable(point[0], point[1]);
            if(spawn != null){
                spawn.setOverlay(Blocks.spawn);
            }
        }
    }

    /** @return 以 (x,y) 为中心向外查找的最近可行走瓦片 */
    private Tile findWalkable(int x, int y){
        for(int radius = 0; radius < 10; radius++){
            for(int dx = -radius; dx <= radius; dx++){
                for(int dy = -radius; dy <= radius; dy++){
                    Tile tile = tile(x + dx, y + dy);
                    if(tile != null && !tile.solid() && tile.floor() != Blocks.water){
                        return tile;
                    }
                }
            }
        }

        return null;
    }

    /** 依据噪声决定地表类型。 */
    private Floor floorAt(int x, int y){
        float n = noise(x / 20f, y / 20f) * 0.65f + noise(x / 7f, y / 7f) * 0.35f;

        if(n < 0.34f) return Blocks.water;
        if(n < 0.44f) return Blocks.sand;
        if(n < 0.60f) return Blocks.grass;
        if(n < 0.74f) return Blocks.stone;
        if(n < 0.86f) return Blocks.darksand;
        if(n < 0.92f) return Blocks.shale;
        //测试图的热源带：原版热源地板是 hotrock（shale 只带油、不带地热）。
        //此前用「给 shale 加热」占位，T5 已按原版撤掉占位 → 这里补一条 hotrock 带，
        //保证地热发电机在测试图上仍可验证（而不是靠污染的 shale 属性）。
        return Blocks.hotrock;
    }

    /** 少量散布的岩壁。 */
    private boolean wallAt(int x, int y){
        return noise(x / 11f + 40f, y / 11f + 40f) > 0.83f;
    }

    /** @return 该地表上应该长的自然岩壁；水面不长岩壁（返回 null）。 */
    private Block rockFor(Floor floor){
        if(floor == Blocks.stone) return Blocks.rocks;
        if(floor == Blocks.sand) return Blocks.sandRocks;
        if(floor == Blocks.darksand) return Blocks.duneRocks;
        if(floor == Blocks.shale) return Blocks.shaleRocks;
        if(floor == Blocks.grass) return Blocks.shrubs;
        return null;
    }

    private float noise(float x, float y){
        int xi = (int)Math.floor(x), yi = (int)Math.floor(y);
        float xf = x - xi, yf = y - yi;
        float v00 = hash(xi, yi), v10 = hash(xi + 1, yi), v01 = hash(xi, yi + 1), v11 = hash(xi + 1, yi + 1);
        float sx = xf * xf * (3f - 2f * xf), sy = yf * yf * (3f - 2f * yf);
        return MathUtils.lerp(MathUtils.lerp(v00, v10, sx), MathUtils.lerp(v01, v11, sx), sy);
    }

    private float hash(int x, int y){
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        return ((h ^ (h >> 16)) & 0x7FFFFFFF) / (float)0x7FFFFFFF;
    }

    public Tile tile(int x, int y){
        if(x < 0 || y < 0 || x >= width || y >= height) return null;
        return tiles[y * width + x];
    }

    /**
     * 不做越界检查的瓦片取用（对应原版 {@code World.rawTile(int,int)}，原版 World.java:104-106 是 {@code tiles[x][y]}）。
     * <p>⚠ 本项目一维存储的索引是 {@code y * width + x}；**刻意不加越界守卫**（与原版一致，调用方自负责）。
     */
    public Tile rawTile(int x, int y){
        return tiles[y * width + x];
    }

    /** 通过打包坐标取瓦片（对应原版 World.tile(int pos)）。 */
    public Tile tile(int pos){
        if(tiles == null) return null;
        return tile(Pos.x(pos), Pos.y(pos));
    }

    public Tile tileWorld(float x, float y){
        return tile(toTile(x), toTile(y));
    }

    /**
     * 取瓦片并**解析多格链接**（对应原版 {@code World.ltile} 的 {@code tile.block().linked(tile)}）：
     * 卫星瓦片返回其中心瓦片，非多格瓦片返回自身。
     * <p>子弹/爆炸等「以建筑为单位」的判定必须走这里 —— 直接用 {@link #tile} 会拿到卫星瓦片，
     * 而卫星瓦片（{@code BlockPart}）没有实体（{@code entity == null}），
     * 结果多格建筑只有锚点那一格能被打到。
     */
    public Tile ltile(int x, int y){
        Tile tile = tile(x, y);
        return tile == null ? null : tile.link();
    }

    /** 世界坐标 → 瓦片坐标。注意：瓦片 n 覆盖 [n*tilesize, (n+1)*tilesize)，所以这里是向下取整而不是四舍五入。 */
    public int toTile(float coord){
        return (int)Math.floor(coord / tilesize);
    }

    public int width(){
        return width;
    }

    public int height(){
        return height;
    }

    public float unitWidth(){
        return width * tilesize;
    }

    public float unitHeight(){
        return height * tilesize;
    }

    public boolean isGenerating(){
        return generating;
    }

    /** @return 本局是否属于某个战役区域（对应原版 {@code World.isZone()}）。 */
    public boolean isZone(){
        return getZone() != null;
    }

    /**
     * @return 本局对应的战役区域（对应原版 {@code World.getZone()}，实现就是 {@code state.rules.zone}）。
     * <p>注意本项目 {@code state} 是 {@link Vars#state} 的静态字段，规则重置时 {@code rules} 会被整体替换
     * （{@code Control.play()} 里 {@code Vars.state.rules = new Rules()}）→ 这里必须每次现读，不能缓存。
     */
    public com.phoenix.game.type.Zone getZone(){
        return Vars.state == null || Vars.state.rules == null ? null : Vars.state.rules.zone;
    }

    /**
     * 批量加载/生成期间挂起逐瓦片通知（对应原版 WorldContext.begin/end 的 generating 标记）。
     * <p>读档时先打开，逐格 setBlock 才不会触发 O(瓦片数²) 的电网重建与寻路网格刷新；
     * 全部载入后再关闭并统一 {@link #rebuildPowerGraphs()}。
     */
    public void setGenerating(boolean generating){
        this.generating = generating;
    }

    /** 更新所有带实体的瓦片。 */
    public void updateTiles(){
        updatePower();
        for(Tile tile : tiles){
            if(tile != null && tile.entity != null && tile.block().update){
                //超频计时衰减（对应原版 TileEntity.update 开头）
                tile.entity.updateTimeScale();
                //先刷新消耗状态：方块逻辑要用 cons.valid() 判断能否工作
                if(tile.entity.cons != null) tile.entity.cons.update();
                //液体模块的每帧平滑（对应原版 TileEntity.update 里的 liquids.update()）：
                //本项目 liquids.update() 此前从未被调用 → smoothLiquid 恒为 0（Conduit/LiquidBlock 的液面绘制失真）
                if(tile.entity.liquids != null) tile.entity.liquids.update();
                tile.entity.update();
            }
        }
    }

    /** 遍历两点之间的瓦片（Bresenham），返回 true 时停止。 */
    public void raycastEach(int x1, int y1, int x2, int y2, TileRaycaster caster){
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        while(true){
            if(caster.accept(x1, y1)) return;
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

    /** 查找最近的指定标记方块。 */
    public Tile closestTile(float x, float y, Team team, BlockFlag flag, boolean enemy){
        Tile best = null;
        float bestDst = Float.MAX_VALUE;

        for(Tile tile : tiles){
            if(tile == null || tile.block() == Blocks.air || !tile.block().flags.contains(flag)) continue;
            Team tileTeam = tile.getTeam();
            if(enemy ? !team.isEnemy(tileTeam) : tileTeam != team) continue;

            float dst = Mathf.dst2(x, y, tile.getX(), tile.getY());
            if(dst < bestDst){
                bestDst = dst;
                best = tile;
            }
        }

        return best;
    }

    /** 全局电网列表（电网重建时替换）。 */
    public final com.badlogic.gdx.utils.Array<com.phoenix.game.world.blocks.power.PowerGraph> powerGraphs = new com.badlogic.gdx.utils.Array<>();

    /** 重建全部电网并更新一次电力分配。 */
    public void rebuildPowerGraphs(){
        powerGraphs.clear();

        //必须先清空归属：reflow 会给每个瓦片写上 power.graph，
        //若不清空，第二次重建时所有旧瓦片都会被下面的 graph != null 判据跳过，
        //结果每放一块建筑电网就只剩它自己（相邻建筑永不连通）。
        for(Tile tile : tiles){
            if(tile != null && tile.entity != null && tile.entity.power != null){
                tile.entity.power.graph = null;
            }
        }

        for(Tile tile : tiles){
            if(tile == null || tile.entity == null || tile.entity.power == null) continue;
            if(tile.entity.power.graph != null) continue;

            com.phoenix.game.world.blocks.power.PowerGraph graph = new com.phoenix.game.world.blocks.power.PowerGraph();
            graph.reflow(tile);
            powerGraphs.add(graph);
        }
    }

    /** 每帧更新全部电网。 */
    public void updatePower(){
        for(com.phoenix.game.world.blocks.power.PowerGraph graph : powerGraphs){
            graph.update();
        }
    }

    public void notifyChanged(Tile tile){
        //批量加载/生成期间不做逐瓦片通知：寻路网格与电网都在批量结束后统一重建，
        //否则每放一格都会重建一次电网（O(瓦片数²)）并刷新一次寻路网格
        if(generating) return;

        //建筑/地形改变：刷新寻路网格，让流场重算
        if(Vars.pathfinder != null){
            Vars.pathfinder.updateTile(tile);
        }
        rebuildPowerGraphs();
        //广播瓦片变化：小地图据此更新对应像素（对应原版 TileChangeEvent）。
        Events.fire(new EventType.TileChangeEvent(tile));
    }

    /** 瓦片射线回调。 */
    public interface TileRaycaster{
        /** @return true 表示停止遍历 */
        boolean accept(int x, int y);
    }
}
