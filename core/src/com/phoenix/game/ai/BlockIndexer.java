package com.phoenix.game.ai;

import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.core.Events;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.func.Boolf;
import com.phoenix.game.game.EventType;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.struct.Array;
import com.phoenix.game.struct.GridBits;
import com.phoenix.game.type.Item;
import com.phoenix.game.util.Pack;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.BuildBlock;
import com.phoenix.game.world.meta.BlockFlag;

import java.util.EnumSet;

import static com.phoenix.game.Vars.state;
import static com.phoenix.game.Vars.tilesize;
import static com.phoenix.game.Vars.world;

/**
 * 为 AI 建立"特殊目标方块"的索引。参照 Mindustry {@code mindustry.ai.BlockIndexer} 移植。
 *
 * <p>能索引什么（本批实际落地程度）：
 * <ul>
 *   <li><b>按 BlockFlag 的同盟建筑索引</b>（{@code flagMap}）：核心/炮塔/工厂等按 {@code BlockFlag} 分组，
 *       供 {@code getAllied} 直接取用；{@link EventType.TileChangeEvent} 增量维护。</li>
 *   <li><b>按队伍的四分位占据位图</b>（{@code structQuadrants}，16x16 格一个象限）：
 *       {@code findTile} 先按象限粗筛，再在象限内逐格找最近的可选目标建筑。</li>
 *   <li><b>受损建筑索引</b>（{@code damagedTiles}）：{@code notifyTileDamaged} 登记、{@code getDamaged} 取用并自清。</li>
 *   <li><b>矿脉象限索引</b>（{@code ores}）：{@code getOrePositions}/{@code findClosestOre} 找最近矿脉。</li>
 * </ul>
 *
 * <p><b>与原版的差异（降级，逐条）</b>：
 * <ol>
 *   <li><b>{@code BlockFlag.all} 不存在</b>（本项目枚举没有 {@code all} 字段）→ 用局部
 *       {@code allFlags = BlockFlag.values()}。</li>
 *   <li><b>{@code Block.targetable} 不存在</b>（原版字段，默认 true，126.2 里只有 {@code ShockMine} 设为 false）
 *       → 退化为 {@link #targetable(Tile)} = {@code tile.entity != null}（与原版默认语义等价；
 *       {@code ShockMine} 那处 opt-out 无法表达，需要给 {@code Block} 补字段）。</li>
 *   <li><b>{@code Block.priority}（{@code TargetPriority}）不存在</b> → {@code findTile} 的
 *       {@code usePriority} 参数成为**无操作**（只按距离取最近目标，不做优先级覆盖）。
 *       本项目 {@code TargetPriority} 枚举已存在，但 {@code Block} 上没有对应字段，
 *       补字段属于改既有文件，本批不做。</li>
 *   <li><b>{@code Team.enemies()} 不存在</b> → {@code getEnemy} 改用
 *       {@code state.teams.enemiesOf(team)}（本项目 Teams 已有，语义一致）。</li>
 *   <li><b>{@code Item.getAllOres()} 已实现</b>（基于本项目实际注册的矿脉类 {@code OreFloor}
 *       + {@code Floor.itemDrop}）→ {@code scanOres} 与原版一样在 {@code WorldLoadEvent} 开头
 *       一次性灌入全部矿石物品（不再只覆盖"本图有的矿"）。</li>
 *   <li><b>{@code Geometry.findClosest(x, y, ObjectSet)} 不存在</b> → 本地实现
 *       {@link #findClosestTile(float, float, ObjectSet)}（线性扫描取 dst2 最小）。</li>
 *   <li><b>{@code TileEntity.damaged()} 不存在</b>（本项目 TileEntity 没有实现 HealthTrait 的那部分）
 *       → 退化为 {@code entity.health() < entity.maxHealth() - 0.0001f}（原版 {@code HealthTrait.damaged()} 的原式）。</li>
 *   <li><b>{@code Vars.indexer}</b>：F5 已补该实例（启动器里 {@code new BlockIndexer()}）→
 *       原版 {@code indexer.findTile(...)} 的调用点可以改成本类的 {@code findTile(...)}（就是自己）。</li>
 *   <li>原版 {@code tile.block()} 读的是"瓦片自己存的方块"；本项目 {@code Tile.block()} 会**解析多格链接**
 *       （卫星格转发到中心）→ 凡原版读原始方块的地方一律改用 {@link Tile#blockRaw()}
 *       （否则一个 3x3 核心会被索引 9 次，见 {@code 转换.md} D1c 第 1 条）。</li>
 *   <li>{@code Mathf.ceil} 在本项目返回 {@code float}（原版返回 {@code int}）→ 建 {@link GridBits} 时显式取整。</li>
 *   <li>数组下标统一写 {@code team.id & 0xFF}：本项目 {@code Team.id} 是 {@code byte}，
 *       id ≥ 128 时会变负数，原版直接 {@code arr[team.id]} 会越界（本项目沿用 {@code Team.get} 的取模约定）。</li>
 * </ol>
 *
 * <p>接线状态（F5）：实例已在启动器里建好（{@code Vars.indexer}），构造器自己挂
 * WorldLoadEvent/TileChangeEvent；{@code RepairDrone}/{@code MinerDrone} 已改为优先走本索引
 * （indexer 为 null 时退回就地全瓦片扫描）。{@code BaseDrone} 仍在用
 * {@code BaseUnit.getClosest(BlockFlag)}（等价的 O(瓦片数) 扫描）—— 因为
 * {@link #getAllied(Team, BlockFlag)} 在第一次 WorldLoadEvent 之前会返回 null 的
 * {@code ObjectSet}（{@code flagMap} 内层只在事件里填），要接得先给它补守卫。
 */
@SuppressWarnings("unchecked")
public class BlockIndexer{
    /** Size of one quadrant. */
    private final static int quadrantSize = 16;
    /** {@code BlockFlag} 的缓存值数组（替代原版 {@code BlockFlag.all}）。 */
    private final static BlockFlag[] allFlags = BlockFlag.values();

    /** Set of all ores that are being scanned. */
    private final ObjectSet<Item> scanOres = new ObjectSet<>();
    private final ObjectSet<Item> itemSet = new ObjectSet<>();
    /** Stores all ore quadtrants on the map. */
    private ObjectMap<Item, ObjectSet<Tile>> ores = new ObjectMap<>();
    /** Maps each team ID to a quarant. A quadrant is a grid of bits, where each bit is set if and only if there is a block of that team in that quadrant. */
    private GridBits[] structQuadrants;
    /** Stores all damaged tile entities by team. */
    private ObjectSet<Tile>[] damagedTiles = new ObjectSet[Team.all().length];
    /** All ores available on this map. */
    private ObjectSet<Item> allOres = new ObjectSet<>();
    /** Stores teams that are present here as tiles. */
    private ObjectSet<Team> activeTeams = new ObjectSet<>();

    /**
     * Maps teams to a map of flagged tiles by type.
     * <p>⚠ <b>内层 {@code ObjectSet} 必须在这里就建好</b>：原版只在 WorldLoadEvent 里填，
     * 因为原版 {@code World.notifyChanged} 走 {@code Core.app.post}（延后一帧）、且地图生成全程
     * 在 {@code generating} 窗口内 —— 也就是"第一次 TileChangeEvent 一定晚于第一次 WorldLoadEvent"。
     * 本项目 {@code notifyChanged} 是**同步**派发，而 {@code Control.play()} 在
     * {@code world.createMap()} 之后、{@code enterWorld()}（发 WorldLoadEvent）之前就
     * {@code placeCore(...)} → 第一块核心的 TileChangeEvent 会落在 WorldLoadEvent 之前，
     * 那时 {@code getFlagged(team)[flag]} 是 null → {@code process()} 直接 NPE。
     * 这里提前建好（WorldLoadEvent 里仍会整体重建一遍），语义与原版一致。
     */
    private ObjectSet<Tile>[][] flagMap = newFlagMap();
    /** Maps tile positions to their last known tile index data. */
    private IntMap<TileIndex> typeMap = new IntMap<>();
    /** Empty set used for returning. */
    private ObjectSet<Tile> emptySet = new ObjectSet<>();
    /** Array used for returning and reusing. */
    private Array<Tile> returnArray = new Array<>();

    private static ObjectSet<Tile>[][] newFlagMap(){
        ObjectSet<Tile>[][] map = new ObjectSet[Team.all().length][allFlags.length];
        for(int i = 0; i < map.length; i++){
            for(int j = 0; j < allFlags.length; j++){
                map[i][j] = new ObjectSet<>();
            }
        }
        return map;
    }

    public BlockIndexer(){
        Events.on(EventType.TileChangeEvent.class, event -> {
            if(typeMap.get(event.tile.pos()) != null){
                TileIndex index = typeMap.get(event.tile.pos());
                for(BlockFlag flag : index.flags){
                    getFlagged(index.team)[flag.ordinal()].remove(event.tile);
                }
            }
            process(event.tile);
            updateQuadrant(event.tile);
        });

        Events.on(EventType.WorldLoadEvent.class, event -> {
            scanOres.clear();
            //原版 BlockIndexer.java:63 = scanOres.addAll(Item.getAllOres())
            scanOres.addAll(Item.getAllOres());
            damagedTiles = new ObjectSet[Team.all().length];
            //整体重建（与字段初始化同一个方法，避免两处写法漂移）
            flagMap = newFlagMap();

            typeMap.clear();
            allOres.clear();
            ores = null;

            //create bitset for each team type that contains each quadrant
            structQuadrants = new GridBits[Team.all().length];

            for(int x = 0; x < world.width(); x++){
                for(int y = 0; y < world.height(); y++){
                    Tile tile = world.tile(x, y);
                    if(tile == null) continue;

                    process(tile);

                    if(tile.entity != null && damaged(tile.entity)){
                        notifyTileDamaged(tile.entity);
                    }

                    //原版 BlockIndexer.java:90 = if(tile.drop() != null) allOres.add(tile.drop())
                    Item drop = tile.drop();
                    if(drop != null){
                        allOres.add(drop);
                    }
                }
            }

            for(int x = 0; x < quadWidth(); x++){
                for(int y = 0; y < quadHeight(); y++){
                    updateQuadrant(world.tile(x * quadrantSize, y * quadrantSize));
                }
            }

            scanOres();
        });
    }

    /**
     * @return 该建筑能否作为 AI 目标（替代原版 {@code Block.targetable}）。
     * <p>原版 {@code Block.targetable} 默认 **true**，126.2 里只有 {@code ShockMine} 把它设成 false
     * → 这里退化成"有实体即可"，与默认语义等价。
     */
    private static boolean targetable(Tile tile){
        return tile.entity != null;
    }

    /**
     * @return 该实体是否受过伤（替代原版 {@code TileEntity.damaged()}）。
     * <p>原版实现在 {@code HealthTrait.damaged()}：{@code health() < maxHealth() - 0.0001f}，这里照抄。
     */
    private static boolean damaged(TileEntity entity){
        return entity.health() < entity.maxHealth() - 0.0001f;
    }

    /** @return 集合中离 (x, y) 最近的瓦片（替代原版 {@code Geometry.findClosest}）。 */
    private static Tile findClosestTile(float x, float y, ObjectSet<Tile> set){
        Tile closest = null;
        float best = Float.MAX_VALUE;
        for(Tile tile : set){
            float dst = Mathf.dst2(x, y, tile.getX(), tile.getY());
            if(dst < best){
                best = dst;
                closest = tile;
            }
        }
        return closest;
    }

    private ObjectSet<Tile>[] getFlagged(Team team){
        return flagMap[team.id & 0xFF];
    }

    private GridBits structQuadrant(Team t){
        int id = Pack.u(t.id);
        if(structQuadrants[id] == null){
            structQuadrants[id] = new GridBits((int)Mathf.ceil(world.width() / (float)quadrantSize),
                (int)Mathf.ceil(world.height() / (float)quadrantSize));
        }
        return structQuadrants[id];
    }

    /** Updates all the structure quadrants for a newly activated team. */
    public void updateTeamIndex(Team team){
        if(structQuadrants == null) return;

        //go through every tile... ouch
        for(int x = 0; x < world.width(); x++){
            for(int y = 0; y < world.height(); y++){
                Tile tile = world.tile(x, y);
                if(tile != null && tile.getTeam() == team){
                    int quadrantX = tile.x / quadrantSize;
                    int quadrantY = tile.y / quadrantSize;
                    structQuadrant(team).set(quadrantX, quadrantY);
                }
            }
        }
    }

    /** @return whether this item is present on this map. */
    public boolean hasOre(Item item){
        return allOres.contains(item);
    }

    /** Returns all damaged tiles by team. */
    public ObjectSet<Tile> getDamaged(Team team){
        returnArray.clear();

        if(damagedTiles[team.id & 0xFF] == null){
            damagedTiles[team.id & 0xFF] = new ObjectSet<>();
        }

        ObjectSet<Tile> set = damagedTiles[team.id & 0xFF];
        for(Tile tile : set){
            if((tile.entity == null || tile.entity.getTeam() != team || !damaged(tile.entity)) || tile.blockRaw() instanceof BuildBlock){
                returnArray.add(tile);
            }
        }

        for(int i = 0; i < returnArray.size; i++){
            set.remove(returnArray.get(i));
        }

        return set;
    }

    /** Get all allied blocks with a flag. */
    public ObjectSet<Tile> getAllied(Team team, BlockFlag type){
        return flagMap[team.id & 0xFF][type.ordinal()];
    }

    /** Get all enemy blocks with a flag. */
    public Array<Tile> getEnemy(Team team, BlockFlag type){
        returnArray.clear();
        //原版 team.enemies()；本项目改用 Teams.enemiesOf（返回 libgdx Array）
        com.badlogic.gdx.utils.Array<Team> enemies = state.teams.enemiesOf(team);
        for(int i = 0; i < enemies.size; i++){
            Team enemy = enemies.get(i);
            if(state.teams.isActive(enemy)){
                ObjectSet<Tile> set = getFlagged(enemy)[type.ordinal()];
                if(set != null){
                    for(Tile tile : set){
                        returnArray.add(tile);
                    }
                }
            }
        }
        return returnArray;
    }

    public void notifyTileDamaged(TileEntity entity){
        int id = entity.getTeam().id & 0xFF;
        if(damagedTiles[id] == null){
            damagedTiles[id] = new ObjectSet<>();
        }

        ObjectSet<Tile> set = damagedTiles[id];
        set.add(entity.tile);
    }

    public TileEntity findEnemyTile(Team team, float x, float y, float range, Boolf<Tile> pred){
        for(Team enemy : activeTeams){
            if(!team.isEnemy(enemy)) continue;

            //原版 indexer.findTile(...)：本类就是 Vars.indexer 的实现 → 直接调自己
            TileEntity entity = findTile(enemy, x, y, range, pred, true);
            if(entity != null){
                return entity;
            }
        }

        return null;
    }

    public TileEntity findTile(Team team, float x, float y, float range, Boolf<Tile> pred){
        return findTile(team, x, y, range, pred, false);
    }

    /**
     * @param usePriority 原版用它决定"是否允许高优先级目标无视距离覆盖更近的低优先级目标"。
     *        <b>本项目降级为无操作</b>：{@code Block} 没有 {@code priority} 字段（见类注释第 3 条）。
     */
    public TileEntity findTile(Team team, float x, float y, float range, Boolf<Tile> pred, boolean usePriority){
        TileEntity closest = null;
        float dst = 0;
        float range2 = range*range;

        for(int rx = Math.max((int)((x - range) / tilesize / quadrantSize), 0); rx <= (int)((x + range) / tilesize / quadrantSize) && rx < quadWidth(); rx++){
            for(int ry = Math.max((int)((y - range) / tilesize / quadrantSize), 0); ry <= (int)((y + range) / tilesize / quadrantSize) && ry < quadHeight(); ry++){

                if(!getQuad(team, rx, ry)) continue;

                for(int tx = rx * quadrantSize; tx < (rx + 1) * quadrantSize && tx < world.width(); tx++){
                    for(int ty = ry * quadrantSize; ty < (ry + 1) * quadrantSize && ty < world.height(); ty++){
                        Tile other = world.ltile(tx, ty);

                        if(other == null) continue;

                        if(other.entity == null || other.getTeam() != team || !pred.get(other) || !targetable(other))
                            continue;

                        TileEntity e = other.entity;

                        float ndst = Mathf.dst2(x, y, e.x, e.y);
                        //原版这里还比较 Block.priority；本项目无该字段 → 纯距离最近
                        if(ndst < range2 && (closest == null || ndst < dst)){
                            dst = ndst;
                            closest = e;
                        }
                    }
                }
            }
        }

        return closest;
    }

    /**
     * Returns a set of tiles that have ores of the specified type nearby.
     * While each tile in the set is not guaranteed to have an ore directly on it,
     * each tile will at least have an ore within {@link #quadrantSize} / 2 blocks of it.
     * Only specific ore types are scanned. See {@link #scanOres}.
     */
    public ObjectSet<Tile> getOrePositions(Item item){
        return ores.get(item, emptySet);
    }

    /** Find the closest ore block relative to a position. */
    public Tile findClosestOre(float xp, float yp, Item item){
        Tile tile = findClosestTile(xp, yp, getOrePositions(item));

        if(tile == null) return null;

        for(int x = Math.max(0, tile.x - quadrantSize / 2); x < tile.x + quadrantSize / 2 && x < world.width(); x++){
            for(int y = Math.max(0, tile.y - quadrantSize / 2); y < tile.y + quadrantSize / 2 && y < world.height(); y++){
                Tile res = world.tile(x, y);
                if(res != null && res.blockRaw() == Blocks.air && res.drop() == item){
                    return res;
                }
            }
        }

        return null;
    }

    private void process(Tile tile){
        if(tile == null) return;

        //原版读 tile.block()（瓦片自己存的方块）；本项目 block() 会解析多格链接 → 用 blockRaw()
        if(tile.blockRaw().flags.size() > 0 && tile.getTeam() != Team.derelict){
            ObjectSet<Tile>[] map = getFlagged(tile.getTeam());

            for(BlockFlag flag : tile.blockRaw().flags){

                ObjectSet<Tile> arr = map[flag.ordinal()];

                arr.add(tile);

                map[flag.ordinal()] = arr;
            }
            typeMap.put(tile.pos(), new TileIndex(tile.blockRaw().flags, tile.getTeam()));
        }
        activeTeams.add(tile.getTeam());

        if(ores == null) return;

        int quadrantX = tile.x / quadrantSize;
        int quadrantY = tile.y / quadrantSize;
        itemSet.clear();

        //注意 Mathf.clamp(float,float,float) 在本项目返回 float（原版有 int 重载）→ 显式取整
        Tile rounded = world.tile(
            (int)Mathf.clamp(quadrantX * quadrantSize + quadrantSize / 2, 0, world.width() - 1),
            (int)Mathf.clamp(quadrantY * quadrantSize + quadrantSize / 2, 0, world.height() - 1));

        //find all items that this quadrant contains
        for(int x = Math.max(0, rounded.x - quadrantSize / 2); x < rounded.x + quadrantSize / 2 && x < world.width(); x++){
            for(int y = Math.max(0, rounded.y - quadrantSize / 2); y < rounded.y + quadrantSize / 2 && y < world.height(); y++){
                Tile result = world.tile(x, y);
                if(result == null || result.drop() == null || !scanOres.contains(result.drop()) || result.blockRaw() != Blocks.air) continue;

                itemSet.add(result.drop());
            }
        }

        //update quadrant at this position
        for(Item item : scanOres){
            ObjectSet<Tile> set = ores.get(item);

            //update quadrant status depending on whether the item is in it
            if(!itemSet.contains(item)){
                set.remove(rounded);
            }else{
                set.add(rounded);
            }
        }
    }

    private void updateQuadrant(Tile tile){
        if(structQuadrants == null || tile == null) return;

        //this quadrant is now 'dirty', re-scan the whole thing
        int quadrantX = tile.x / quadrantSize;
        int quadrantY = tile.y / quadrantSize;

        for(Team team : activeTeams){
            GridBits bits = structQuadrant(team);

            //fast-set this quadrant to 'occupied' if the tile just placed is already of this team
            if(tile.getTeam() == team && targetable(tile)){
                bits.set(quadrantX, quadrantY);
                continue; //no need to process futher
            }

            bits.set(quadrantX, quadrantY, false);

            outer:
            for(int x = quadrantX * quadrantSize; x < world.width() && x < (quadrantX + 1) * quadrantSize; x++){
                for(int y = quadrantY * quadrantSize; y < world.height() && y < (quadrantY + 1) * quadrantSize; y++){
                    Tile result = world.ltile(x, y);
                    //when a targetable block is found, mark this quadrant as occupied and stop searching
                    if(result != null && result.entity != null && result.getTeam() == team){
                        bits.set(quadrantX, quadrantY);
                        break outer;
                    }
                }
            }
        }
    }

    private boolean getQuad(Team team, int quadrantX, int quadrantY){
        return structQuadrant(team).get(quadrantX, quadrantY);
    }

    private int quadWidth(){
        return (int)Mathf.ceil(world.width() / (float)quadrantSize);
    }

    private int quadHeight(){
        return (int)Mathf.ceil(world.height() / (float)quadrantSize);
    }

    private void scanOres(){
        ores = new ObjectMap<>();

        //initialize ore map with empty sets
        for(Item item : scanOres){
            ores.put(item, new ObjectSet<>());
        }

        for(int x = 0; x < world.width(); x++){
            for(int y = 0; y < world.height(); y++){
                int qx = (x / quadrantSize);
                int qy = (y / quadrantSize);

                Tile tile = world.tile(x, y);

                //add position of quadrant to list when an ore is found
                if(tile != null && tile.drop() != null && scanOres.contains(tile.drop()) && tile.blockRaw() == Blocks.air){
                    ores.get(tile.drop()).add(world.tile(
                    //make sure to clamp quadrant middle position, since it might go off bounds
                    (int)Mathf.clamp(qx * quadrantSize + quadrantSize / 2, 0, world.width() - 1),
                    (int)Mathf.clamp(qy * quadrantSize + quadrantSize / 2, 0, world.height() - 1)));
                }
            }
        }
    }

    private class TileIndex{
        public final EnumSet<BlockFlag> flags;
        public final Team team;

        public TileIndex(EnumSet<BlockFlag> flags, Team team){
            this.flags = flags;
            this.team = team;
        }
    }
}
