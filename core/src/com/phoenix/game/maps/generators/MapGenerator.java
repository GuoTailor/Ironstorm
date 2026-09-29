package com.phoenix.game.maps.generators;

import com.badlogic.gdx.math.GridPoint2;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.game.Schematic;
import com.phoenix.game.game.Team;
import com.phoenix.game.io.SaveIO;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.maps.Map;
import com.phoenix.game.struct.Array;
import com.phoenix.game.type.Item;
import com.phoenix.game.util.Structs;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.storage.CoreBlock;
import com.phoenix.game.world.blocks.storage.StorageBlock;

import java.io.IOException;

/**
 * 「读一张已有地图 → 二次加工」的生成器。参照 Mindustry
 * {@code mindustry.maps.generators.MapGenerator} 移植。
 *
 * <p>用途：战役区域图（{@code Zones.groundZero} 等）在仓库里预置了一张手摆的 {@code .msav}，
 * 载入后由本类做三件事 —— ① 拆掉玩家核心与卫星格、记下核心位置；② 按 {@link Decoration}
 * 撒装饰物；③ 随机挑选敌方出生点并清出空地；最后把开局蓝图放回核心位置。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li>{@code init()} 原版是 {@code maps.loadInternalMap(mapName)}（从 jar 内的内置地图目录读），
 *       本项目 {@code Maps} 没有 {@code loadInternalMap} → 退化到 {@code Vars.maps.byName(mapName)}
 *       （扫 {@code maps/} 目录，按显示名匹配）；找不到时 {@code map} 保持 null，{@link #generate}
 *       会抛异常（原版靠"内置地图一定存在"来保证，本项目不能保证）。</li>
 *   <li><b>瓦片数组的来源不同</b>：原版传入的 {@code tiles} 与 {@code world.tiles} 是同一份数据
 *       （{@code World.createTiles} 把数组交给生成器）；本项目 {@code World} 是「一维 {@code Tile[]}
 *       + width/height」，而且 {@link SaveIO#load} 会**整个替换 {@code Vars.world}**
 *       → 传入的二维数组与加载后的世界不是同一批对象，所以载入后要按 {@code Vars.world} 重建二维视图。</li>
 *   <li>{@code SaveIO.load(File)} 在本项目抛受检 {@code IOException}（原版包成运行时异常）
 *       → 这里 catch 后包成 {@code RuntimeException}。</li>
 *   <li>{@code state.rules.defaultTeam} <b>已消费</b>（T2 补的字段）→ 直接读 {@code Vars.state.rules.defaultTeam}。</li>
 *   <li><b>⚠判方块一律用 {@code blockRaw()}/{@code isLinked()}</b>：原版 {@code Tile.block()} 返回瓦片自己
 *       存的方块，所以 {@code instanceof CoreBlock} 只会命中核心的锚点格、{@code instanceof BlockPart}
 *       只会命中卫星格；本项目 {@code block()} 会解析多格链接（卫星转发到中心方块）
 *       → 照抄会让一个 3x3 核心被当成 9 个核心（并且卫星格判不出 BlockPart）。</li>
 *   <li>{@code decor.wall.isOverlay()} / {@code isFloor()} <b>已补</b>（T8a 给 {@code Block} 追加了
 *       {@code isFloor()}/{@code isOverlay()}/{@code asFloor()}，语义 = {@code instanceof Floor}/
 *       {@code instanceof OverlayFloor}）→ 照原版写；判地板时仍要显式排除 {@code Blocks.air}
 *       （本项目 {@code air} 是 Floor）。</li>
 *   <li>{@code Blocks.rock}/{@code snowrock}/{@code ignarock}/{@code pebbles} <b>均已注册</b>
 *       （{@code rock}/{@code snowrock} 是 {@code Rock}、{@code ignarock} 是 Floor、
 *       {@code pebbles} 是 DoubleOverlayFloor）→ 装饰表照原版还原，见字段注释。</li>
 *   <li>{@code world.getZone() != null} 的「给储物方块预装区域资源」分支：<b>F5 已回填</b>
 *       （{@code World.getZone()} 与 {@code Rules.zone} 都补上了）；判据改用 {@code blockRaw()}
 *       以避开"卫星格 block() 转发到中心方块"的坑。
 *       注意本条在本项目仍是<b>实际不可达</b>：{@code Control.play/playZone} 都还没接生成器链路。</li>
 *   <li>{@code tile.clearOverlay()} <b>已补</b>（T8a 追加到 {@code Tile}）→ 照原版写。</li>
 *   <li>{@code schematics.placeLoadout(...)} <b>已补</b>（T8a 追加到 {@code Schematics}）→ 照原版写。</li>
 *   <li>{@code world.setMap(map)} <b>已接线</b>（S1 批次给 {@code World} 补了 {@code getMap/setMap/rawTile}）；
 *       {@code world.prepareTiles(tiles)} 则<b>刻意不做</b> —— 原版它只铺多格方块的卫星格（{@code BlockPart}），
 *       本项目 {@code Tile.setBlock} 自己就会铺、且 {@code Tile.updateOcclusion()} 是空实现 → 加了是死代码。
 *       理由见 {@link #generate} 末尾注释。</li>
 * </ul>
 */
public class MapGenerator extends Generator{
    private Map map;
    /** 本项目无内置地图时的默认地图边长（原版尺寸来自 .msav 文件，本项目读不出来，见 转换.md 324）。 */
    public static final int defaultSize = 200;
    private String mapName;
    private Array<Decoration> decorations = Array.with(new Decoration(Blocks.stone, Blocks.rock, 0.003f));
    /**
     * The amount of final enemy spawns used. -1 to use everything in the map.
     * This amount of enemy spawns is selected randomly from the map.
     */
    public int enemySpawns = -1;
    /** Whether floor is distorted along with blocks. */
    public boolean distortFloor = false;

    public MapGenerator(String mapName){
        this.mapName = mapName;
    }

    public MapGenerator(String mapName, int enemySpawns){
        this.mapName = mapName;
        this.enemySpawns = enemySpawns;
    }

    public MapGenerator decor(Decoration... decor){
        this.decorations.addAll(decor);
        return this;
    }
    public void removePrefix(String name){
        this.mapName = this.mapName.substring(name.length() + 1);
    }

    {
        //照原版：雪地长雪岩、火成岩上撒碎石（snowrock/ignarock/pebbles 均已注册）
        decor(new Decoration(Blocks.snow, Blocks.snowrock, 0.01f), new Decoration(Blocks.ignarock, Blocks.pebbles, 0.03f));
    }

    public Map getMap(){
        return map;
    }

    @Override
    public void init(Schematic loadout){
        this.loadout = loadout;
        //刻意降级：原版 = Vars.maps.loadInternalMap(mapName)（从 jar 内的内置地图读）；
        //本项目 Maps 没有 loadInternalMap，且 SaveIO 与原版 MSAV 刻意不字节兼容（见 转换.md 324）
        //→ 退化到扫 maps/ 目录按显示名查；查不到就走程序化生成（见 generate）。
        map = Vars.maps == null ? null : Vars.maps.byName(mapName);
        if(map != null){
            width = map.width;
            height = map.height;
        }else if(width <= 0 || height <= 0){
            //本项目没有内置地图 → 尺寸取默认值（原版尺寸来自 .msav，本项目读不出来）
            width = defaultSize;
            height = defaultSize;
        }
    }

    @Override
    public void generate(Tile[][] tiles){
        if(map == null){
            //本项目退化：原版从内置地图读（SaveIO.load(map.file)），但本项目 SaveIO 与原版 MSAV
            //刻意不字节兼容（见 io/SaveIO 类注释与 转换.md 324）→ 改走本项目的地形生成，
            //这样每个区域仍能得到一张可玩的地图（地形/矿脉/出生点齐全），而不是直接抛异常。
            Vars.world.generateTerrain(tiles, width, height);
            return;
        }

        try{
            SaveIO.load(map.file);
        }catch(IOException e){
            //刻意降级：原版 SaveIO.load(Fi) 内部把 IOException 包成运行时异常；本项目 SaveIO.load 抛受检异常
            throw new RuntimeException(e);
        }

        //刻意降级：原版里 `tiles` 与 `world.tiles` 是同一份数据（World.createTiles 把数组交给生成器），
        //     所以原版只 `new Tile(x, y)` 占位、后面 SaveIO 直接写进同一批对象。
        //     本项目 World 是「一维 Tile[] + width/height」，且 SaveIO.load 会**整个替换 Vars.world**
        //     → 传入的二维数组与加载后的世界不是同一批对象，这里按 Vars.world 重建二维视图。
        width = Vars.world.width();
        height = Vars.world.height();
        for(int x = 0; x < width && x < tiles.length; x++){
            for(int y = 0; y < height && y < tiles[x].length; y++){
                Tile loaded = Vars.world.tile(x, y);
                if(loaded != null) tiles[x][y] = loaded;
            }
        }

        Array<GridPoint2> players = new Array<>();
        Array<GridPoint2> enemies = new Array<>();

        //已消费 state.rules.defaultTeam（T2 补的字段）
        Team playerTeam = Vars.state.rules.defaultTeam;

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                //本项目 Tile.block() 会解析多格链接（卫星瓦片转发到中心方块）→ 判"这一格是不是核心/卫星"
                //必须用 blockRaw()/isLinked()，否则一个 3x3 核心会被当成 9 个核心（原版 block() 就是原始值）
                if(tiles[x][y].blockRaw() instanceof CoreBlock && tiles[x][y].getTeam() == playerTeam){
                    players.add(new GridPoint2(x, y));
                    tiles[x][y].setBlock(Blocks.air);
                }

                if(tiles[x][y].overlay() == Blocks.spawn && enemySpawns != -1){
                    enemies.add(new GridPoint2(x, y));
                    tiles[x][y].setOverlay(Blocks.air);
                }

                if(tiles[x][y].isLinked()){
                    tiles[x][y].setBlock(Blocks.air);
                }
            }
        }

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                Tile tile = tiles[x][y];

                for(Decoration decor : decorations){
                    //本项目 Blocks.air 是 Floor → 判"装饰物是不是地板"时要显式排除 air
                    //（用 blockRaw()：本项目 block() 会解析多格链接，见上面第一条说明）
                    if(x > 0 && y > 0 && (tiles[x - 1][y].blockRaw() == decor.wall || tiles[x][y - 1].blockRaw() == decor.wall)){
                        continue;
                    }

                    if(tile.blockRaw() == Blocks.air && !decor.wall.isFloor() && tile.floor() == decor.floor && Mathf.chance((float)decor.chance)){
                        tile.setBlock(decor.wall);
                    }else if(tile.floor() == decor.floor && decor.wall.isOverlay() && Mathf.chance((float)decor.chance)){
                        tile.setOverlay(decor.wall);
                    }else if(tile.floor() == decor.floor && decor.wall.isFloor() && decor.wall != Blocks.air
                        && !decor.wall.isOverlay() && Mathf.chance((float)decor.chance)){
                        //air 也要排除（本项目 air 是 Floor）
                        tile.setFloor((Floor)decor.wall);
                    }
                }

                //原版：储物方块（非核心）在区域图里按 world.getZone().resources 随机预装材料。
                //F5 已补 World.getZone()（= state.rules.zone）→ 照原版接上。
                //⚠ 判据用 blockRaw()：本项目 block() 会解析多格链接（container/vault 是 2x2/3x3），
                //  卫星格会报 StorageBlock 但 entity 为 null → 用 block() 会 NPE（见 转换.md D1c 第 1 条）。
                if(Vars.world != null && Vars.world.getZone() != null
                    && tile.blockRaw() instanceof StorageBlock && !(tile.blockRaw() instanceof CoreBlock)
                    && tile.entity != null && tile.entity.items != null){
                    for(Item item : Vars.world.getZone().resources){
                        if(Mathf.chance(0.3f)){
                            tile.entity.items.add(item, Math.min(Mathf.random(500), tile.blockRaw().itemCapacity));
                        }
                    }
                }
            }
        }

        if(enemySpawns != -1){
            if(enemySpawns > enemies.size){
                throw new IllegalArgumentException("Enemy spawn pool greater than map spawn number for map: " + mapName);
            }

            enemies.shuffle();
            for(int i = 0; i < enemySpawns; i++){
                GridPoint2 point = enemies.get(i);
                tiles[point.x][point.y].setOverlay(Blocks.spawn);

                int rad = 10, frad = 12;

                for(int x = -rad; x <= rad; x++){
                    for(int y = -rad; y <= rad; y++){
                        int wx = x + point.x, wy = y + point.y;
                        double dst = Mathf.dst(x, y);
                        if(dst < frad && Structs.inBounds(wx, wy, tiles) && (dst <= rad || Mathf.chance(0.5f))){
                            Tile tile = tiles[wx][wy];
                            if(tile.overlay() != Blocks.spawn){
                                tile.clearOverlay();
                            }
                        }
                    }
                }
            }
        }

        GridPoint2 core = players.random();
        if(core == null){
            throw new IllegalArgumentException("All zone maps must have a core.");
        }

        Vars.schematics.placeLoadout(loadout, core.x, core.y);

        //原版 MapGenerator.java:154-156 = world.prepareTiles(tiles) + world.setMap(map)：
        // - prepareTiles：**刻意不做** —— 原版它只负责铺多格方块的卫星格（BlockPart），
        //   本项目 Tile.setBlock(Tile.java:130) 自己就会铺（BlockPart.get + Vars.world.tile），
        //   且 Tile.updateOcclusion() 是空实现 → 加了是死代码
        // - setMap：已接线（S1 批次给 World 补了 getMap/setMap/rawTile）
        //⚠ map 可能为 null（本项目地图按名字扫目录，查不到时 init 里就是 null，见 init 的"刻意降级"注释）
        //   → 必须守卫，不能把 null 塞进 setMap（null 与"没有 Map 对象"同义，但保持守卫更明确）
        if(map != null && Vars.world != null){
            Vars.world.setMap(map);
        }
        //注意：上面改的就是 Vars.world 自己的瓦片对象（见方法开头的说明），所以不需要再"装回世界"
    }

    public static class Decoration{
        public final Block floor;
        public final Block wall;
        public final double chance;

        public Decoration(Block floor, Block wall, double chance){
            this.floor = floor;
            this.wall = wall;
            this.chance = chance;
        }
    }
}
