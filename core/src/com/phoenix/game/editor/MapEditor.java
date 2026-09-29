package com.phoenix.game.editor;

import com.badlogic.gdx.graphics.Pixmap;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.core.World;
import com.phoenix.game.func.Boolf;
import com.phoenix.game.func.Cons;
import com.phoenix.game.game.Team;
import com.phoenix.game.io.MapIO;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.maps.Map;
import com.phoenix.game.struct.StringMap;
import com.phoenix.game.util.Fi;
import com.phoenix.game.util.Structs;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.WorldContext;
import com.phoenix.game.world.blocks.BlockPart;
import io.anuke.mindustry.gen.TileOp;

/**
 * 地图编辑器模型。参照 Mindustry {@code mindustry.editor.MapEditor} 移植。
 *
 * <p>持有：瓦片二维视图、撤销栈（{@link OperationStack} + 当前 {@link DrawOperation}）、
 * 画布渲染器（{@link MapRenderer}）、地图元数据 tags，以及画笔状态（{@link #brushSize}/
 * {@link #drawBlock}/{@link #drawTeam}/{@link #rotation}）。
 *
 * <p><b>瓦片二维视图（本批最大的落地差异）</b>：原版 {@code world} 自身就是「二维
 * {@code Tile[][]} + createTiles/getTiles/rawTile」，编辑器直接改世界。本项目 {@code World}
 * 是「一维 {@code Tile[]} + width/height」，且 {@code SaveIO.load} 会**整个替换 {@code Vars.world}**
 * → 这里由 MapEditor 自己维护 {@code Tile[][] tiles}，并在每次结构性改动后
 * {@link #installWorld()} 把它同步进 {@code Vars.world}（{@code Tile} 的多格展开 / {@code BlockPart.linked}
 * 都依赖 {@code Vars.world}，所以两边必须是同一批对象）。{@link #tiles()} / {@link #tile(int,int)}
 * 读的就是这份视图。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li>{@code World.rawTile(...)} <b>已存在</b>（S1 批次）；但 {@code world.createTiles(w,h)} /
 *       {@code world.getTiles()} 本项目仍没有，且编辑器维护的是**自己的二维视图**（原因见上：原版 world 自身
 *       就是二维、编辑器直接改世界；本项目 World 是一维且 {@code SaveIO.load} 会整个替换 {@code Vars.world}）
 *       → {@link #createTiles(int,int)} / {@link #tiles()} / {@link #tile(int,int)} 仍读编辑器自己那份，
 *       这是<b>刻意的</b>，不是缺 API。</li>
 *   <li>{@code Vars.world} 在菜单里可能是 null（本项目只在进战役时建世界）→ {@code beginEdit} 会按需
 *       {@code new World()} 并装上编辑器瓦片。</li>
 *   <li>{@code MapIO.loadMap(map, context)} / {@code MapIO.readPixmap(...)} 本项目原先没有
 *       → 已追加进 {@link MapIO}（loadMap 是降级实现，见其 javadoc）。</li>
 *   <li>{@code Block.isFloor()} / {@code isOverlay()} 本项目没有 → {@code instanceof Floor} /
 *       {@code instanceof OverlayFloor}；且<b>本项目 {@code Blocks.air} 是 Floor</b>，判地板要显式排除 air。</li>
 *   <li><b>判方块一律用 {@code blockRaw()}/{@code isLinked()}</b>：原版 {@code Tile.block()} 是"瓦片自己存的
 *       方块"，本项目会解析多格链接 → 照抄会把卫星格当中心方块、拆散多格建筑（见 转换.md 地图批 D1c）。</li>
 *   <li>{@code Tile.set(block, team)} 本项目没有 → 用等价的 {@code Tile.setBlock(block, team)}
 *       （本项目把原版 {@code set} 的多格展开内联进了 {@code setBlock(Block,Team,int)}）。</li>
 *   <li>{@code map.file.parent().parent().name().equals("1127400") && steam}（Steam 创意工坊目录判定）
 *       → 本项目没有 Steam 集成，整段省略（{@code Vars.steam} 恒 false）。</li>
 *   <li>{@code Mathf.clamp(int,int,int)} 本项目有 int 重载，无需改动。</li>
 * </ul>
 */
public class MapEditor{
    public static final int[] brushSizes = {1, 2, 3, 4, 5, 9, 15, 20};

    private final Context context = new Context();
    private StringMap tags = new StringMap();
    private final MapRenderer renderer = new MapRenderer(this);

    private final OperationStack stack = new OperationStack();
    private DrawOperation currentOp;
    private boolean loading;

    /** 编辑器的瓦片二维视图；与 {@code Vars.world} 是同一批对象（见类注释）。 */
    private Tile[][] tiles = new Tile[0][0];

    public int brushSize = 1;
    public int rotation;
    public Block drawBlock = Blocks.stone;
    public Team drawTeam = Team.sharded;

    public StringMap getTags(){
        return tags;
    }

    public void beginEdit(int width, int height){
        reset();

        loading = true;
        createTiles(width, height);
        renderer.resize(width(), height());
        loading = false;
    }

    public void beginEdit(Map map){
        reset();

        loading = true;
        tags.putAll(map.tags);
        //TODO 原版还会在 Steam 创意工坊目录（1127400）下补 steamid；本项目没有 Steam 集成 → 省略
        MapIO.loadMap(map, context);
        checkLinkedTiles();
        renderer.resize(width(), height());
        loading = false;
    }

    public void beginEdit(Pixmap pixmap){
        reset();

        createTiles(pixmap.getWidth(), pixmap.getHeight());
        load(() -> MapIO.readPixmap(pixmap, tiles()));
        renderer.resize(width(), height());
    }

    /** adds missing blockparts */
    public void checkLinkedTiles(){
        Tile[][] tiles = tiles();

        //clear block parts first
        for(int x = 0; x < width(); x++){
            for(int y = 0; y < height(); y++){
                //本项目 block() 会解析多格链接 → 判卫星格必须用 blockRaw()
                if(tiles[x][y].blockRaw() instanceof BlockPart){
                    tiles[x][y].setBlock(Blocks.air);
                }
            }
        }

        //set up missing blockparts
        for(int x = 0; x < width(); x++){
            for(int y = 0; y < height(); y++){
                //同上：用 blockRaw()（原版 block() 就是原始值）
                if(tiles[x][y].blockRaw().isMultiblock()){
                    //原版 = tiles[x][y].set(block, team)；本项目 setBlock(Block,Team) 已含多格展开
                    tiles[x][y].setBlock(tiles[x][y].blockRaw(), tiles[x][y].getTeam());
                }
            }
        }
    }

    public void load(Runnable r){
        loading = true;
        r.run();
        loading = false;
    }

    /** Creates a 2-D array of EditorTiles with stone as the floor block. */
    private void createTiles(int width, int height){
        //本项目 World 没有原版的 createTiles(int,int) / getTiles() → 自行建二维视图并同步进世界
        if(Vars.world == null) Vars.world = new World();

        tiles = new Tile[width][height];
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                tiles[x][y] = new EditorTile(x, y, Blocks.stone.id, (short)0, (short)0);
            }
        }
        installWorld();
    }

    /** 把二维视图压平成 {@code Vars.world} 的一维数组（见类注释）。 */
    private void installWorld(){
        int w = width(), h = height();
        Vars.world.width = w;
        Vars.world.height = h;
        Vars.world.tiles = new Tile[w * h];
        for(int x = 0; x < w; x++){
            for(int y = 0; y < h; y++){
                Vars.world.tiles[y * w + x] = tiles[x][y];
            }
        }
    }

    public Map createMap(Fi file){
        //本项目 Map.file 是 java.io.File（原版是 Fi）→ 用 fi.file() 取出来
        return new Map(file.file(), width(), height(), new StringMap(tags), true);
    }

    private void reset(){
        clearOp();
        brushSize = 1;
        drawBlock = Blocks.stone;
        tags = new StringMap();
    }

    public Tile[][] tiles(){
        return tiles;
    }

    public Tile tile(int x, int y){
        //原版 world.rawTile(x, y) 不做越界检查；编辑器视图需要越界返回 null → 这里自己判（刻意偏离）
        if(x < 0 || y < 0 || x >= tiles.length || y >= tiles[x].length) return null;
        return tiles[x][y];
    }

    public int width(){
        return tiles.length;
    }

    public int height(){
        return tiles.length == 0 ? 0 : tiles[0].length;
    }

    public void drawBlocksReplace(int x, int y){
        //原版 = tile.block() != Blocks.air || drawBlock.isFloor()；本项目 block() 会解析多格链接 → blockRaw()
        drawBlocks(x, y, tile -> tile.blockRaw() != Blocks.air || drawBlock instanceof Floor);
    }

    public void drawBlocks(int x, int y){
        drawBlocks(x, y, false, tile -> true);
    }

    public void drawBlocks(int x, int y, Boolf<Tile> tester){
        drawBlocks(x, y, false, tester);
    }

    public void drawBlocks(int x, int y, boolean square, Boolf<Tile> tester){
        if(drawBlock.isMultiblock()){
            x = Mathf.clamp(x, (drawBlock.size - 1) / 2, width() - drawBlock.size / 2 - 1);
            y = Mathf.clamp(y, (drawBlock.size - 1) / 2, height() - drawBlock.size / 2 - 1);

            int offsetx = -(drawBlock.size - 1) / 2;
            int offsety = -(drawBlock.size - 1) / 2;

            for(int dx = 0; dx < drawBlock.size; dx++){
                for(int dy = 0; dy < drawBlock.size; dy++){
                    int worldx = dx + offsetx + x;
                    int worldy = dy + offsety + y;

                    if(Structs.inBounds(worldx, worldy, tiles())){
                        Tile tile = tile(worldx, worldy);

                        //本项目 block() 会解析多格链接 → 用 blockRaw()（原版 block() 就是原始值）
                        Block block = tile.blockRaw();

                        //bail out if there's anything blocking the way
                        if(block.isMultiblock() || block instanceof BlockPart){
                            return;
                        }

                        renderer.updatePoint(worldx, worldy);
                    }
                }
            }

            //原版 = tile(x, y).set(drawBlock, drawTeam)；本项目 setBlock(Block,Team) 等价
            //（加空值守卫：地图比方块还小时 clamp 会给出越界坐标，原版会直接 NPE）
            Tile center = tile(x, y);
            if(center != null){
                center.setBlock(drawBlock, drawTeam);
            }
        }else{
            //本项目 Blocks.air 是 Floor → 判"要放的是地板"必须排除 air
            boolean isFloor = drawBlock instanceof Floor && drawBlock != Blocks.air;

            Cons<Tile> drawer = tile -> {
                if(tile == null || !tester.get(tile)) return;

                //remove linked tiles blocking the way
                if(!isFloor && (tile.isLinked() || tile.blockRaw().isMultiblock())){
                    tile.link().remove();
                }

                if(isFloor){
                    tile.setFloor((Floor)drawBlock);
                }else{
                    tile.setBlock(drawBlock);
                    if(drawBlock.synthetic()){
                        tile.setTeam(drawTeam);
                    }
                    if(drawBlock.rotate){
                        tile.rotation(rotation);
                    }
                }
            };

            if(square){
                drawSquare(x, y, drawer);
            }else{
                drawCircle(x, y, drawer);
            }
        }
    }

    public void drawCircle(int x, int y, Cons<Tile> drawer){
        for(int rx = -brushSize; rx <= brushSize; rx++){
            for(int ry = -brushSize; ry <= brushSize; ry++){
                if(Mathf.dst2(rx, ry) <= (brushSize - 0.5f) * (brushSize - 0.5f)){
                    int wx = x + rx, wy = y + ry;

                    if(wx < 0 || wy < 0 || wx >= width() || wy >= height()){
                        continue;
                    }

                    drawer.get(tile(wx, wy));
                }
            }
        }
    }

    public void drawSquare(int x, int y, Cons<Tile> drawer){
        for(int rx = -brushSize; rx <= brushSize; rx++){
            for(int ry = -brushSize; ry <= brushSize; ry++){
                int wx = x + rx, wy = y + ry;

                if(wx < 0 || wy < 0 || wx >= width() || wy >= height()){
                    continue;
                }

                drawer.get(tile(wx, wy));
            }
        }
    }

    public MapRenderer renderer(){
        return renderer;
    }

    public void resize(int width, int height){
        clearOp();

        Tile[][] previous = tiles;
        int offsetX = -(width - width()) / 2, offsetY = -(height - height()) / 2;
        loading = true;

        //本项目 World 没有 createTiles(Tile[][]) → 自行建新二维数组并复用旧瓦片对象
        if(Vars.world == null) Vars.world = new World();

        Tile[][] created = new Tile[width][height];
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                int px = offsetX + x, py = offsetY + y;
                if(Structs.inBounds(px, py, previous)){
                    Tile tile = previous[px][py];
                    tile.x = (short)x;
                    tile.y = (short)y;
                    created[x][y] = tile;
                }else{
                    created[x][y] = new EditorTile(x, y, Blocks.stone.id, (short)0, (short)0);
                }
            }
        }

        tiles = created;
        installWorld();

        renderer.resize(width, height);
        loading = false;
    }

    public void clearOp(){
        stack.clear();
    }

    public void undo(){
        if(stack.canUndo()){
            stack.undo();
        }
    }

    public void redo(){
        if(stack.canRedo()){
            stack.redo();
        }
    }

    public boolean canUndo(){
        return stack.canUndo();
    }

    public boolean canRedo(){
        return stack.canRedo();
    }

    public void flushOp(){
        if(currentOp == null || currentOp.isEmpty()) return;
        stack.add(currentOp);
        currentOp = null;
    }

    public void addTileOp(long data){
        if(loading) return;

        if(currentOp == null) currentOp = new DrawOperation(this);
        currentOp.addOperation(data);

        renderer.updatePoint(TileOp.x(data), TileOp.y(data));
    }

    class Context implements WorldContext{
        @Override
        public Tile tile(int x, int y){
            return Vars.world == null ? null : Vars.world.tile(x, y);
        }

        @Override
        public void resize(int width, int height){
            //原版 = world.createTiles(width, height)；本项目自行维护二维视图
            createTiles(width, height);
        }

        @Override
        public Tile create(int x, int y, int floorID, int overlayID, int wallID){
            EditorTile tile = new EditorTile(x, y, floorID, overlayID, wallID);
            if(x >= 0 && y >= 0 && x < tiles.length && y < tiles[x].length){
                tiles[x][y] = tile;
                //同步回世界（installWorld 已保证 Vars.world 的一维数组与二维视图同尺寸）
                if(Vars.world != null && Vars.world.tiles != null && Vars.world.width == tiles.length){
                    Vars.world.tiles[y * tiles.length + x] = tile;
                }
            }
            return tile;
        }

        @Override
        public boolean isGenerating(){
            return Vars.world != null && Vars.world.isGenerating();
        }

        @Override
        public void begin(){
            if(Vars.world != null) Vars.world.setGenerating(true);
        }

        @Override
        public void end(){
            if(Vars.world != null) Vars.world.setGenerating(false);
        }
    }
}
