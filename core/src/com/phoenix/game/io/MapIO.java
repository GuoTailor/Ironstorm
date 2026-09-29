package com.phoenix.game.io;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.core.Version;
import com.phoenix.game.game.Team;
import com.phoenix.game.maps.Map;
import com.phoenix.game.maps.filters.GenerateFilter;
import com.phoenix.game.struct.StringMap;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.LegacyColorMapper;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.WorldContext;
import com.phoenix.game.world.blocks.storage.CoreBlock;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

/**
 * 最小实现：地图 IO 工具。参照 Mindustry mindustry.io.MapIO 移植，目前搬了小地图上色用到的
 * {@link #colorFor(Block, Block, Block, Team)}（地图文件的读写由 {@link SaveIO} 负责），
 * 以及地图编辑器需要的 {@link #loadMap(Map)} / {@link #loadMap(Map, WorldContext)} /
 * {@link #readPixmap(Pixmap, Tile[][])}（2026-09-23 地图批 D2a 追加）。
 *
 * <p><b>T19 追加</b>：{@link #isImage(File)}（PNG 魔数判定）与 {@link #createMap(File, boolean)}
 * （读存档 meta 构造 {@code Map}）—— 原版 {@code MapsDialog} 导入地图要用这两个；
 * 消费点见 {@code ui/dialogs/MapsDialog} 的"导入地图"按钮。
 *
 * <p><b>2026-09-23（地图批 D1c）新增：实现 {@link GenerateFilter.GenerateInput.TileProvider}</b>
 * <ul>
 *   <li>原版里 {@code GenerateInput.buffer} 的实参是 {@code World.FilterContext} 传的 lambda
 *       {@code (x, y) -> tiles[x][y]}（不是 {@code MapIO} —— {@code MapIO} 自己也声明了一个同名的
 *       嵌套接口 {@code TileProvider}，但全工程无人使用，是死代码）。本项目上一批移植
 *       {@code GenerateFilter} 时留下了这个缺口：{@code buffer} 没有任何实现者。
 *       这里把 {@link MapIO} 本身做成 {@code TileProvider}，作为「按坐标取当前世界的瓦片」的统一入口。</li>
 *   <li>{@link #get(int, int)} 直接委托 {@code Vars.world.tile(x, y)}：本项目 {@code World.tile}
 *       内部已经做了越界守卫（越界返回 null），所以这里只需要额外守 {@code Vars.world == null}
 *       （菜单/headless 未建世界时）。</li>
 *   <li><b>不改任何既有方法签名</b>：{@link #colorFor(Block, Block, Block, Team)} 保持原样，
 *       本类仍是"无状态工具类"，只是多了一个实例方法，调用方 {@code new MapIO()} 即可当 provider 用。</li>
 *   <li>TODO：原版「载入地图时逐个滤镜改写瓦片」的循环在 {@code World.FilterContext.end()} 里
 *       （{@code input.begin(filter, width(), height(), (x,y) -> tiles[x][y])} + 双层循环 apply）。
 *       本项目 {@code World} 还没有对应实现（已 grep 确认无 {@code FilterContext}）→
 *       {@code GenerateInput.begin(...)} 目前**没有调用点**，本类只是把 provider 这一半补齐。</li>
 * </ul>
 *
 * <p><b>F14 追加：地图缩略图生成</b> —— {@link #generatePreview(Map)}（离线读存档文件，不解世界）与
 * {@link #generatePreview(Tile[][])}（从内存瓦片生成，原版重载）。见方法注释。
 */
public class MapIO implements GenerateFilter.GenerateInput.TileProvider{

    /** PNG 魔数（对应原版 {@code MapIO.pngHeader}）：用来判"这是张图片、不是地图"。 */
    private static final int[] pngHeader = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    /**
     * 按坐标取当前世界的瓦片（{@link GenerateFilter.GenerateInput.TileProvider} 的实现）。
     * <p>越界由 {@link com.phoenix.game.core.World#tile(int, int)} 负责（返回 null）。
     * @return 该坐标的瓦片；世界未建立或越界时返回 null
     */
    @Override
    public Tile get(int x, int y){
        if(Vars.world == null) return null;
        return Vars.world.tile(x, y);
    }

    /**
     * @return 该文件是否是 PNG 图片（读前 8 字节比对 PNG 魔数）。对应原版 {@code MapIO.isImage(Fi)}。
     * <p>比"看扩展名"更准：地图导入时用它把图片挡在门外（原版 MapsDialog 就是这么用的）。
     * 读不出来（不存在/太短/IO 错）一律返回 false。
     */
    public static boolean isImage(File file){
        try(InputStream in = new FileInputStream(file)){
            for(int expected : pngHeader){
                if(in.read() != expected) return false;
            }
            return true;
        }catch(IOException e){
            return false;
        }
    }

    /**
     * 从一个 {@code .msav} 文件构造 {@link Map}。对应原版 {@code MapIO.createMap(Fi, boolean)}。
     *
     * <p><b>与原版的差异</b>：原版直接解压存档的 meta region，把里面的 stringMap 当 tags，
     * 再从 {@code tags.getInt("width"/"height")} 取尺寸；本项目 meta 是**结构化的** {@link SaveMeta}
     * （tags 只装自定义标签、宽高是独立字段）→ 这里用 {@link SaveIO#readMeta(File)} 取 tags/宽高，
     * 再把宽高补进 tags（与 {@code Map.filters()} 等按 tags 取值的地方口径一致）。
     * <p>⚠ 本项目 {@code SaveMeta.rules} 是结构化 {@code RulesMeta}、不是 {@code tags["rules"]} 的 JSON
     * → 造出的 {@code Map} 读不出地图自带规则（{@link Map#rules()} 会退回默认规则），这是已知差异。
     *
     * @param custom 是否算作自定义地图（地图导入传 true）
     * @throws IOException 文件不是本版本可读的存档（{@link SaveIO#readMeta} 抛出）
     */
    public static Map createMap(File file, boolean custom) throws IOException{
        SaveMeta meta = SaveIO.readMeta(file);

        StringMap tags = new StringMap();
        if(meta.tags != null){
            for(java.util.Map.Entry<String, String> entry : meta.tags.entrySet()){
                tags.put(entry.getKey(), entry.getValue());
            }
        }
        tags.put("width", String.valueOf(meta.width));
        tags.put("height", String.valueOf(meta.height));

        return new Map(file, meta.width, meta.height, tags, custom, SaveIO.version, Version.build);
    }

    /**
     * 取瓦片在小地图/地图预览上的颜色。对应原版 {@code MapIO.colorFor}：
     * <ul>
     *   <li>人造方块（{@link Block#synthetic()}，即 {@code update || destructible}）→ 阵营色；</li>
     *   <li>否则实心方块 → 方块自身颜色（由 {@code Block.loadMinimapColor} 从图标中心像素采样）；</li>
     *   <li>否则有覆盖层（矿物/出生点）→ 覆盖层颜色；</li>
     *   <li>否则 → 地板颜色。</li>
     * </ul>
     * @param floor 地板
     * @param wall 方块（可为 air）
     * @param ore 覆盖层（可为 air）
     * @param team 阵营
     * @return RGBA8888 颜色
     */
    public static int colorFor(Block floor, Block wall, Block ore, Team team){
        if(wall.synthetic()){
            return Color.rgba8888(team.color);
        }
        return Color.rgba8888(wall.solid ? wall.color : ore == Blocks.air ? floor.color : ore.color);
    }

    /**
     * 离线生成一张地图的缩略图（1 像素 = 1 格）。对应原版 {@code MapIO.generatePreview(Map)}（F14）。
     *
     * <p><b>与原版同构的流式读法</b>：原版把 MSAV 的 meta/content/map 三个 region 逐段解开、用轻量
     * {@code WorldContext}（不建真世界）逐格上色。本项目存档（PHNX 格式）分块方式一致，逐段解析：
     * <ul>
     *   <li>meta 区：跳过（尺寸从 map 区头部取，spawns/teams 由本次扫描重算 —— 与原版相同）；</li>
     *   <li>content 区：解析**方块名字表**到本方法的局部数组（不走 {@link SaveIO#readContentHeader}，
     *       避免污染它的静态表）；</li>
     *   <li>map 区：地板/覆盖层 + 方块/队伍；实体数据是 v7 的「2B 长度前缀子块」（对应原版的
     *       {@code writeChunk} 内嵌子块）→ 按长度整段跳过，**无需解析任何实体格式**。</li>
     * </ul>
     * <p>逐格上色规则与原版一致：人造方块 → 阵营色；实心方块 → 方块色；覆盖层/地板色依次兜底
     * （{@link #colorFor}）。原版在瓦片回调里逐格写 {@code CachedTile.setBlock}，本项目存档里多格建筑
     * 只在**中心格**存方块（卫星格是 air，读档时 {@code setBlock} 铺开）→ 这里按 {@code block.size}
     * 把阵营色/方块色补画到整个占地区域（视觉等价）。
     *
     * @param map 已有 {@code file} 的地图对象（{@code spawns}/{@code teams} 会被重算覆盖）
     * @return 宽高 = 地图格数的 Pixmap（调用方负责创建纹理后 {@code dispose}）
     * @throws IOException 文件不是本工程存档、版本过旧（v6 实体内联无法离线跳过）或数据损坏
     */
    public static Pixmap generatePreview(Map map) throws IOException{
        try(DataInputStream in = new DataInputStream(new InflaterInputStream(new FileInputStream(map.file)))){
            SaveIO.readHeader(in);
            int ver = in.readInt();
            if(ver != SaveIO.version){
                throw new IOException("地图 \"" + map.name + "\" 是 v" + ver + " 旧格式，离线预览只支持 v"
                    + SaveIO.version + "（旧图在编辑器里重存一次即可生成缩略图）");
            }

            //meta 区：跳过（本项目 meta 是 JSON，spawns/teams 直接从瓦片重算，与原版口径一致）
            SaveIO.readChunk(in);

            //content 区：局部名字表（未知方块 → air 占位，与 SaveIO.readContentHeader 同口径）
            DataInputStream content = new DataInputStream(new ByteArrayInputStream(SaveIO.readChunk(in)));
            int blockCount = content.readUnsignedShort();
            java.util.ArrayList<Block> blocks = new java.util.ArrayList<>(blockCount);
            for(int i = 0; i < blockCount; i++){
                String name = content.readUTF();
                Block block = Vars.content.getByName(com.phoenix.game.ctype.ContentType.block, name);
                if(block == null){
                    System.err.println("地图 \"" + map.name + "\" 引用了未知方块 \"" + name + "\"，按 air 处理");
                    block = Blocks.air;
                }
                blocks.add(block);
            }

            //map 区
            DataInputStream mapData = new DataInputStream(new ByteArrayInputStream(SaveIO.readChunk(in)));
            int width = mapData.readUnsignedShort();
            int height = mapData.readUnsignedShort();

            Pixmap floors = new Pixmap(width, height, Pixmap.Format.RGBA8888);
            Pixmap walls = new Pixmap(width, height, Pixmap.Format.RGBA8888);
            int black = 255;
            int shade = Color.rgba8888(0f, 0f, 0f, 0.5f);

            map.spawns = 0;
            map.teams.clear();

            try{
                //第一遍：地板与覆盖层（原版在 create() 回调里逐格画）
                for(int y = 0; y < height; y++){
                    for(int x = 0; x < width; x++){
                        Block floor = blockAt(blocks, mapData.readUnsignedShort());
                        Block overlay = blockAt(blocks, mapData.readUnsignedShort());

                        if(overlay != Blocks.air){
                            floors.drawPixel(x, height - 1 - y, colorFor(Blocks.air, Blocks.air, overlay, Team.derelict));
                            if(overlay == Blocks.spawn) map.spawns++;
                        }else{
                            floors.drawPixel(x, height - 1 - y, colorFor(floor, Blocks.air, Blocks.air, Team.derelict));
                        }
                    }
                }

                //第二遍：方块与队伍（实体子块整段跳过）
                byte[] scratch = new byte[64 * 1024];
                for(int y = 0; y < height; y++){
                    for(int x = 0; x < width; x++){
                        Block block = blockAt(blocks, mapData.readUnsignedShort());
                        //卫星格/未知方块：与 SaveIO.readMap 同口径，不消费后续字节
                        if(block == Blocks.air) continue;

                        int teamId = mapData.readUnsignedByte();
                        mapData.readUnsignedByte(); //rotation（预览用不到，读掉保持对齐）

                        if(block.hasEntity()){
                            //v7 实体子块：[1B revision][2B 长度][数据] → 整段跳过
                            //⚠ revision 1 字节必须读掉（与 SaveIO.writeMap 的 writeByte(revision) 对齐），
                            //  漏读会让每个实体格错位 1 字节 → 后续 EOF
                            mapData.readUnsignedByte(); //revision（预览用不到）
                            int len = mapData.readUnsignedShort();
                            if(len > scratch.length) scratch = new byte[len];
                            mapData.readFully(scratch, 0, len);
                        }

                        if(block instanceof CoreBlock){
                            map.teams.add(teamId);
                        }

                        int c = colorFor(Blocks.air, block, Blocks.air, Team.get(teamId));
                        if(c == black) continue;

                        //占地区域（多格建筑按 size 补画卫星格；单格就是本格）
                        int size = block.size;
                        int offset = -(size - 1) / 2;
                        for(int dy = 0; dy < size; dy++){
                            for(int dx = 0; dx < size; dx++){
                                int px = x + dx + offset, py = height - 1 - (y + dy + offset);
                                if(px < 0 || px >= width || py < 0 || py >= height) continue;
                                walls.drawPixel(px, py, c);
                                //原版：每个方块格在「下一格」画半透明阴影（py+1 = 世界坐标再低一格）
                                if(py + 1 < height) floors.drawPixel(px, py + 1, shade);
                            }
                        }
                    }
                }

                floors.drawPixmap(walls, 0, 0);
            }finally{
                walls.dispose();
            }

            return floors;
        }
    }

    /** @return 名字表里第 {@code index} 个方块；越界/缺失按 air（与 {@code SaveIO.blockOf} 同口径）。 */
    private static Block blockAt(java.util.ArrayList<Block> blocks, int index){
        return index > 0 && index < blocks.size() ? blocks.get(index) : Blocks.air;
    }

    /**
     * 从内存瓦片生成缩略图（1 像素 = 1 格）。对应原版 {@code MapIO.generatePreview(Tile[][])}（F14）。
     * <p>注意 {@code tiles} 是 {@code [x][y]} 索引（原版 {@code World.getTiles()} 的形状）；
     * 本调用点目前没有生产者（编辑器/导出路径统一走 {@link #generatePreview(Map)} 的文件路径），
     * 保留是为 API 完整性。
     */
    public static Pixmap generatePreview(Tile[][] tiles){
        Pixmap pixmap = new Pixmap(tiles.length, tiles[0].length, Pixmap.Format.RGBA8888);
        for(int x = 0; x < pixmap.getWidth(); x++){
            for(int y = 0; y < pixmap.getHeight(); y++){
                Tile tile = tiles[x][y];
                pixmap.drawPixel(x, pixmap.getHeight() - 1 - y, colorFor(tile.floor(), tile.block(), tile.overlay(), tile.getTeam()));
            }
        }
        return pixmap;
    }

    /**
     * 载入一张地图（整世界替换）。对应原版 {@code MapIO.loadMap(Map)}。
     * <p>本项目委托 {@link SaveIO#load(java.io.File)}；原版把 {@code IOException} 包成运行时异常，
     * 这里照做，避免编辑器调用点被迫写 try/catch。
     */
    public static void loadMap(Map map){
        try{
            SaveIO.load(map.file);
        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    /**
     * 载入一张地图，并把瓦片逐个交给 {@code cons.create(...)} 重建。对应原版
     * {@code MapIO.loadMap(Map, WorldContext)}（编辑器用它建 {@code EditorTile}）。
     *
     * <p><b>降级实现</b>：原版是 {@code SaveIO.load(map.file, cons)}——读档时逐格直接调
     * {@code cons.create(x, y, floorID, overlayID, wallID)}。本项目 {@link SaveIO} 只有「整世界替换」
     * 的版本（{@code SaveIO.load} 内部会 {@code Vars.world = new World()}）→ 这里先整世界载入，
     * 把每格的 floor/overlay/block/队伍/朝向抄进临时数组，再 {@code cons.resize(w,h)} +
     * {@code cons.create(...)} 重建一遍。结果与"直接建"一致，只是多一趟拷贝。
     *
     * @param cons 目标上下文（{@code MapEditor.Context}）；为 null 时只做整世界载入
     */
    public static void loadMap(Map map, WorldContext cons){
        try{
            SaveIO.load(map.file);
        }catch(IOException e){
            throw new RuntimeException(e);
        }

        if(cons == null || Vars.world == null) return;

        int w = Vars.world.width(), h = Vars.world.height();
        short[] floors = new short[w * h], overlays = new short[w * h], walls = new short[w * h];
        int[] teams = new int[w * h], rotations = new int[w * h];

        for(int x = 0; x < w; x++){
            for(int y = 0; y < h; y++){
                Tile tile = Vars.world.tile(x, y);
                if(tile == null) continue;

                int i = y * w + x;
                floors[i] = tile.floor().id;
                overlays[i] = tile.overlay().id;
                //本项目 block() 会解析多格链接 → 这里要"本格实际存的方块"（原版 SaveIO 存的就是它）
                walls[i] = tile.blockRaw().id;
                teams[i] = tile.getTeam().id;
                rotations[i] = tile.rotation();
            }
        }

        cons.resize(w, h);

        for(int x = 0; x < w; x++){
            for(int y = 0; y < h; y++){
                int i = y * w + x;
                Tile tile = cons.create(x, y, floors[i], overlays[i], walls[i]);
                if(tile == null) continue;
                tile.setTeam(Team.get(teams[i]));
                tile.rotation(rotations[i]);
            }
        }
    }

    /**
     * 从一张老格式（v3/v4 PNG）地图读入瓦片。对应原版 {@code MapIO.readPixmap(Pixmap, Tile[][])}。
     * <p>与原版的差异：多加了「像素坐标越界」与「颜色查不到映射」两个守卫（原版不判，
     * 图集尺寸与瓦片数组不一致时会越界/返回 null）；{@code Blocks.coreShard} → 本项目字段名是 {@code Blocks.core}。
     */
    public static void readPixmap(Pixmap pixmap, Tile[][] tiles){
        for(int x = 0; x < pixmap.getWidth(); x++){
            for(int y = 0; y < pixmap.getHeight(); y++){
                if(x >= tiles.length || y >= tiles[x].length) continue;

                int color = pixmap.getPixel(x, pixmap.getHeight() - 1 - y);
                LegacyColorMapper.LegacyBlock block = LegacyColorMapper.get(color);
                if(block == null) continue;

                Tile tile = tiles[x][y];

                tile.setFloor(block.floor);
                tile.setBlock(block.wall);
                if(block.ore != null) tile.setOverlay(block.ore);

                //place core
                if(color == Color.rgba8888(Color.GREEN)){
                    //actual core parts
                    //刻意降级（同义字段名）：原版 = Blocks.coreShard；本项目是 Blocks.core（内部名 core-shard）
                    tile.setBlock(Blocks.core);
                    tile.setTeam(Team.sharded);
                }
            }
        }
    }
}
