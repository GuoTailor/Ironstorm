package com.phoenix.game.game;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.phoenix.game.Vars;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.modules.ItemModule;

/**
 * 最小实现：阵营关系与核心查询。参照 Mindustry mindustry.game.Teams 移植。
 */
public class Teams{
    /** 各队共享物品库存（演示期无传送带，按队伍统一存取）。 */
    private final ItemModule[] itemModules = new ItemModule[Team.all().length];

    /**
     * 各队"被摧毁过、可重建"的方块队列（对应原版 {@code Teams.TeamData.brokenBlocks}）。
     * <p>原版把队列挂在 {@code TeamData} 上（还有 cores/enemies 等字段），本项目 {@code Teams} 是
     * 最小实现、没有 {@code TeamData} → 这里用 {@code IntMap<队伍 id, 队列>} 单独存一份，
     * 由 {@code core.Control} 的两个事件监听器维护（对应原版 {@code core.Logic} 构造器里的同款监听器），
     * 由 {@code core.Renderer.drawDestroyed()} 绘制"重建幽灵轮廓"。
     * <p>已参与存档（原版会把 brokenBlocks 写进存档）：{@code SaveVersion.writeEntities} 逐队写出、
     * {@code SaveVersion.readEntities} 逐条读回（对应原版 {@code SaveVersion.java:215-229 / 258-267}）。
     * 每局 {@code new Teams()} 时自然清空。
     */
    private final IntMap<Array<BrokenBlock>> brokenBlockMap = new IntMap<>();

    /**
     * 各队"核心锚点瓦片"列表（对应原版 {@code Teams.TeamData.cores}）。
     * <p>原版元素是 {@code CoreEntity}；本项目 {@code Teams} 没有 {@code TeamData} 包装 → 直接存 {@link Tile}
     * （**只存锚点瓦片**，多格核心的卫星格不入列）。
     * <p>维护方：{@code world/blocks/storage/CoreBlock.CoreEntity.init()} 注册、
     * {@code CoreBlock.onProximityRemoved()} 注销（映射原版 {@code CoreBlock.placed()/removed()}）。
     * 每局 {@code new Teams()} 时自然清空（{@code Control.playTutorial/playZone} 与 {@code SaveIO.readMap}
     * 都会重建 {@code state.teams}）。
     */
    //原版 Teams.java:147 public final Array<CoreEntity> cores = new Array<>();（挂在 TeamData 上）
    private final IntMap<Array<Tile>> coreMap = new IntMap<>();

    /** @return 某队伍的共享物品库存（惰性创建）。 */
    public ItemModule items(Team team){
        int index = team.id & 0xFF;
        if(itemModules[index] == null){
            itemModules[index] = new ItemModule();
        }
        return itemModules[index];
    }

    /** @return 某队伍的"被摧毁方块"队列（惰性创建；对应原版 {@code state.teams.get(team).brokenBlocks}）。 */
    public Array<BrokenBlock> brokenBlocks(Team team){
        int index = team.id & 0xFF;
        Array<BrokenBlock> arr = brokenBlockMap.get(index);
        if(arr == null){
            arr = new Array<>();
            brokenBlockMap.put(index, arr);
        }
        return arr;
    }

    /**
     * @return 所有非 derelict 的基础队伍（对应原版 {@code state.teams.getActive()}）。
     * <p>原版返回的是"有核心的 {@code TeamData}"列表；本项目 {@code Teams} 没有 {@code TeamData}
     * → 用"队伍本身"代替（{@code SaveVersion.writeEntities} 用它决定写哪些队伍的 brokenBlocks）。
     */
    //原版 Teams.java:100-103 getActive(){ active.removeAll(t -> !t.active()); return active; }
    public Array<Team> getActive(){
        Array<Team> out = new Array<>();
        for(Team team : Team.base()){
            if(team != null && isActive(team)) out.add(team);
        }
        return out;
    }

    /** @return 两个阵营是否敌对（最小实现：不同阵营即敌对）。 */
    public boolean areEnemies(Team a, Team b){
        return a != b;
    }

    /** @return 是否为友军。 */
    public boolean areAllies(Team a, Team b){
        return a == b;
    }

    public boolean isActive(Team team){
        //⚠ 刻意保持现状：原版 TeamData.active() 是 (team == rules.waveTeam && rules.waves) || cores.size > 0，
        // 但本项目 getActive() 的唯一消费者是 SaveVersion.writeEntities（决定写哪些队伍的 brokenBlocks），
        // 改成"有核心才算 active"会让存档行为变化（且 SaveVersion 还是死兼容层）→ 不动。
        return team != null && team != Team.derelict;
    }

    public boolean canInteract(Team a, Team b){
        return a == b;
    }

    /** @return 与指定阵营敌对的所有阵营。 */
    public Array<Team> enemiesOf(Team team){
        Array<Team> array = new Array<>();
        for(Team other : Team.base()){
            if(other != null && areEnemies(team, other)){
                array.add(other);
            }
        }
        return array;
    }

    /** @return 某队伍的核心列表（惰性创建；对应原版 {@code Teams.cores(Team)}，原版注释 "Do not modify!"）。 */
    //原版 Teams.java:79-82 public Array<CoreEntity> cores(Team team){ return get(team).cores; }
    public Array<Tile> cores(Team team){
        if(team == null) return new Array<>();

        int index = team.id & 0xFF;
        Array<Tile> arr = coreMap.get(index);
        if(arr == null){
            arr = new Array<>();
            coreMap.put(index, arr);
        }
        return arr;
    }

    /** @return 玩家阵营（{@code state.rules.defaultTeam}）的核心列表（对应原版 {@code Teams.playerCores()}）。 */
    //原版 Teams.java:75-77 public Array<CoreEntity> playerCores(){ return get(state.rules.defaultTeam).cores; }
    public Array<Tile> playerCores(){
        //⚠ 本项目 Vars.state / state.rules 都可能为 null（原版是 final 非空）→ 返回空数组而不是 NPE
        if(Vars.state == null || Vars.state.rules == null) return new Array<>();
        return cores(Vars.state.rules.defaultTeam);
    }

    /** @return 某队伍是否还有核心（对应原版 {@code TeamData.hasCore()}）。 */
    //原版 Teams.java:160-162 public boolean hasCore(){ return cores.size > 0; }
    public boolean hasCore(Team team){
        if(team == null) return false;
        Array<Tile> arr = coreMap.get(team.id & 0xFF);
        return arr != null && arr.size > 0;
    }

    /** @return 某队伍是否已无核心（对应原版 {@code TeamData.noCores()}）。 */
    //原版 Teams.java:164-166 public boolean noCores(){ return cores.isEmpty(); }
    public boolean noCores(Team team){
        return !hasCore(team);
    }

    /**
     * @return 某队伍的第一个核心（对应原版 {@code TeamData.core()}，即 {@code cores.first()}）。
     * <p>⚠ 与原版的差异：原版无核心时 {@code first()} 会抛异常；本项目返回 {@code null}
     * （调用方遍布渲染/教程/音乐，抛异常会直接崩帧）。
     */
    //原版 Teams.java:168-170 public CoreEntity core(){ return cores.first(); }
    public Tile core(Team team){
        if(team == null) return null;
        Array<Tile> arr = coreMap.get(team.id & 0xFF);
        return (arr == null || arr.isEmpty()) ? null : arr.first();
    }

    /**
     * 登记一个核心锚点（对应原版 {@code Teams.registerCore}，幂等：已在列就不重复加）。
     * <p>⚠ 归一：多格核心的卫星格也可能传进来（{@code BlockPart}）→ 只登记锚点瓦片（复用 {@link #anchor}）。
     * <p>⚠ 刻意不照抄原版里的 {@code updateEnemies()} / {@code indexer.updateTeamIndex(...)}：
     * 本项目 {@code Teams} 没有 {@code active}/{@code enemies} 列表，{@code BlockIndexer} 的接线不在本批。
     */
    //原版 Teams.java:105-118 registerCore(CoreEntity core)
    public void registerCore(Tile core){
        //⚠ core 可能为 null（调用点不保证）；anchor() 走 BlockPart.linked → 需要 world
        if(core == null || Vars.world == null) return;

        Tile anchor = anchor(core);
        if(anchor == null) return;

        Array<Tile> arr = cores(anchor.getTeam());
        if(!arr.contains(anchor, true)){
            arr.add(anchor);
        }
    }

    /** 注销一个核心（对应原版 {@code Teams.unregisterCore}）。 */
    //原版 Teams.java:120-129 unregisterCore(CoreEntity entity)
    public void unregisterCore(Tile core){
        if(core == null || Vars.world == null) return;

        Tile anchor = anchor(core);
        if(anchor == null) return;

        Array<Tile> arr = coreMap.get(anchor.getTeam().id & 0xFF);
        if(arr != null){
            arr.removeValue(anchor, true);
        }
    }

    /**
     * @return 距离最近的己方核心所在瓦片（多格核心返回**锚点**）。
     * <p>对应原版 {@code Teams.closestCore}（{@code Geometry.findClosest(x, y, cores)}）；
     * 本项目 {@code Geometry} 没有 {@code findClosest} → 就地按下标循环取"世界坐标平方距离最小"。
     * <p>⚠ 距离用 {@link Block#centerX}/{@link Block#centerY}（方块几何中心），**不用** {@code worldx()/worldy()}
     * ——本项目是角点语义，直接用会偏左下。
     */
    //原版 Teams.java:36-38 public CoreEntity closestCore(float x, float y, Team team){ return Geometry.findClosest(x, y, get(team).cores); }
    public Tile closestCore(float x, float y, Team team){
        return closest(x, y, cores(team));
    }

    /** @return 距离最近的敌方核心所在瓦片（同样返回锚点，见 {@link #closestCore}）。 */
    //原版 Teams.java:24-34 closestEnemyCore：遍历 active 里敌对的 TeamData、各自取 cores 里的最近核心
    //⚠ 原版实现是"返回第一个有核心的敌对队伍的那个最近核心"（并非全局最近，见报告③）；这里按"全局最近"实现。
    public Tile closestEnemyCore(float x, float y, Team team){
        Tile best = null;
        float bestDst = Float.MAX_VALUE;

        for(IntMap.Entry<Array<Tile>> entry : coreMap.entries()){
            Team other = Team.get(entry.key);
            if(other == null || !areEnemies(team, other)) continue;

            Tile candidate = closest(x, y, entry.value);
            if(candidate == null) continue;

            float dst = dst2(x, y, candidate);
            if(dst < bestDst){
                bestDst = dst;
                best = candidate;
            }
        }
        return best;
    }

    /** @return 核心列表中离 (x,y) 最近的瓦片；列表为空返回 null。 */
    private static Tile closest(float x, float y, Array<Tile> cores){
        Tile best = null;
        float bestDst = Float.MAX_VALUE;

        for(int i = 0; i < cores.size; i++){
            Tile t = cores.get(i);
            if(t == null) continue;

            float dst = dst2(x, y, t);
            if(dst < bestDst){
                bestDst = dst;
                best = t;
            }
        }
        return best;
    }

    /** @return (x,y) 到核心瓦片几何中心的平方距离（世界坐标）。 */
    private static float dst2(float x, float y, Tile t){
        Block block = t.block();
        if(block == null) return Float.MAX_VALUE;

        float dx = block.centerX(t) - x, dy = block.centerY(t) - y;
        return dx * dx + dy * dy;
    }

    /**
     * 把"可能是多格方块卫星格"的瓦片归一到锚点（{@code Tile.link()}）。
     * <p>用途：{@code registerCore}/{@code unregisterCore} 收到卫星格时只登记锚点瓦片。
     * <p>历史背景：S3 之前 {@code closestCore} 走 {@code World.closestTile}（按 {@code block().flags} 匹配，
     * 3×3 核心的**卫星格**（{@code BlockPart}）也会命中）——卫星格的 {@code entity} 是 null，
     * 所有依赖 {@code core.entity} 的逻辑（MinerTrait 卸货、MinerDrone 回核心入库、UnitDrops 掉给敌方核心、
     * 玩家重生点）都会静默失效，典型症状是"物品被 {@code clearItem()} 清掉、却没进核心"。
     */
    private static Tile anchor(Tile t){
        return t == null ? null : t.link();
    }

    /**
     * 遍历所有**敌方**核心瓦片并逐个回调（对应原版 {@code Teams.eachEnemyCore}）。
     * <p><b>已接线（S3 批次）</b>：直接遍历各队核心列表（{@link #cores} / {@code coreMap}），
     * 列表里天然只有锚点瓦片（{@code registerCore} 已归一）→ 不会重复回调卫星格。
     */
    //原版 Teams.java:57-65 eachEnemyCore(Team, Cons<TileEntity>)：遍历 active 里敌对队伍的 cores
    public void eachEnemyCore(Team team, com.phoenix.game.func.Cons<Tile> cons){
        for(IntMap.Entry<Array<Tile>> entry : coreMap.entries()){
            Team other = Team.get(entry.key);
            if(other == null || !areEnemies(team, other)) continue;

            Array<Tile> arr = entry.value;
            for(int i = 0; i < arr.size; i++){
                cons.get(arr.get(i));
            }
        }
    }

    /**
     * 某个队伍建造的、在地图上被**摧毁**的方块（对应原版 {@code Teams.BrokenBlock}）。
     * <p>注意原版注释：**不含被拆除的方块**（拆除是玩家主动行为，不需要提示重建）。
     */
    public static class BrokenBlock{
        public final short x, y, rotation, block;
        public final int config;

        public BrokenBlock(short x, short y, short rotation, short block, int config){
            this.x = x;
            this.y = y;
            this.rotation = rotation;
            this.block = block;
            this.config = config;
        }
    }
}
