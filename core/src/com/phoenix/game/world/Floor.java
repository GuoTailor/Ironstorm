package com.phoenix.game.world;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.content.Fx;
import com.phoenix.game.content.StatusEffects;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.type.StatusEffect;
import com.phoenix.game.world.meta.Attributes;

/**
 * 最小实现：地板。参照 Mindustry mindustry.world.blocks.Floor 移植。
 */
public class Floor extends Block {
    /**
     * 地表直接掉落的物品（对应原版 Floor.itemDrop）。
     * <p>原版只有 sand / darksand 掉沙，其余资源全部来自矿脉 overlay（OreFloor）；
     * 钻头产出去 {@link Tile#drop()} 查（矿脉优先）。
     */
    public Item itemDrop = null;
    /**
     * 地表直接产出的液体（对应原版 {@code Floor.liquidDrop}）：抽水机靠它判定"这格能抽什么液体"。
     * <p>参照原版 mindustry.world.blocks.Floor 移植（原版每个液体地板挂一个 Liquid 内容）。
     */
    public Liquid liquidDrop = null;
    /**
     * 地形属性表（对应原版 {@code Floor.attributes}）：地热/孢子/水/油含量，
     * 供 {@code Block.sumAttribute(Attribute, x, y)} 按多格范围累加（培育机/热能发电机用）。
     * <p>参照原版 mindustry.world.blocks.Floor 移植。
     * <p>注意：本项目早先移植时给地板单独加了 {@link #heat} 字段（{@code ThermalGenerator} 直接读它）；
     * T5 起热源地板（{@code hotrock}/{@code magmarock}）已同时写入 {@code attributes.set(Attribute.heat, ...)}
     * 与 {@link #heat}，两条取值路径都有值（见 {@code content/Blocks} 对应行）。
     */
    public Attributes attributes = new Attributes();
    /** 变体贴图数量 */
    public int variants = 3;
    /** 单位速度倍率 */
    public float speedMultiplier = 1f;
    /** 单位阻力倍率 */
    public float dragMultiplier = 1f;
    /** 每 tick 受到的伤害 */
    public float damageTaken = 0f;
    /**
     * 地热强度（对应原版 {@code Attribute.heat}）：供 {@code ThermalGenerator} 按邻接地板累加发电。
     * <p>热源地板 {@code hotrock}（0.5）与 {@code magmarock}（0.75）均已注册并在此字段 + {@link #attributes}
     * 双写（见 {@code content/Blocks}）；{@code shale} 只带油含量、不产地热（与原版一致）。
     */
    public float heat = 0f;
    /** 淹没所需 tick */
    public float drownTime = 0f;
    /**
     * 玩家能否**手工**开采这种地板（对应原版 {@code Floor.playerUnmineable}）。
     * <p>原版 sand/darksand 置 true：它们的 {@code itemDrop} 是沙，但不允许玩家手采（必须用钻头），
     * 否则开局就能徒手刷沙。{@code InputHandler.canMine} 会据此拒绝。
     */
    public boolean playerUnmineable = false;
    /** 是否为液体 */
    public boolean isLiquid;
    /**
     * 是否作为「默认矿脉」在 {@code Maps.readFilters("")} 里自动生成（对应原版 {@code Floor.oreDefault}）。
     * <p>参照原版 mindustry.world.blocks.Floor 移植；本项目只有 oreCopper/oreLead/oreCoal/oreTitanium/oreThorium 为 true。
     */
    public boolean oreDefault = false;
    /** 默认矿脉的噪声缩放与阈值（对应原版 {@code Floor.oreScale}/{@code oreThreshold}），供 {@code Maps.addDefaultOres} 建 OreFilter。 */
    public float oreScale = 24f, oreThreshold = 0.828f;
    /**
     * 地板自身的可燃性（对应原版液体地板的 {@code liquidDrop.flammability}）。
     * <p>火系统用它算蔓延与熄灭：水上（0）没有燃料，火会以 8 倍速烧完，等于被浇灭。
     * <p>刻意偏离：原版每个液体地板挂一个 {@code Liquid} 内容并读它的 flammability，
     * 本工程没有液体地板内容，直接把可燃性标在地板上。
     */
    public float flammability = 0f;
    /** 行走特效 */
    public Effects.Effect walkEffect = Fx.none;
    /** 溺水特效 */
    public Effects.Effect drownUpdateEffect = Fx.none;
    /** 施加的状态 */
    public StatusEffect status = StatusEffects.none;
    /** 状态持续时间 */
    public float statusDuration = 60f;

    /**
     * 边缘融合组（对应原版 {@code Floor.blendGroup}，默认是**自己**）。
     * <p>用途：同组地板之间不画边缘（比如 {@code craters}/{@code char} 融合进 {@code stone}，
     * 由 {@code stone} 那套边缘贴图负责过渡）。原版 {@code Generators} 也据此**跳过**生成它们自己的
     * {@code <name>-edge}（见 {@code tools/Generators.EDGE_EXCLUDE}）。
     */
    public Block blendGroup = this;
    /**
     * 3x3 的地板边缘贴图（由生成器产出的 {@code <name>-edge} 按 32px 切分而来，对应原版 {@code Floor.edges}）。
     * <p>索引口径见 {@link #edge(Floor, int, int)}：libgdx 的 {@code split} 是 {@code [row][col]}（row 0 = 图像顶行）
     * → 邻格在 (dx,dy) 时取 {@code edges[1 + dy][1 - dx]}（dx/dy ∈ {-1,0,1}）。
     */
    protected TextureRegion[][] edges;
    /** {@link #drawEdges} 的临时容器（原版也是实例字段，逐格复用、避免每格分配）。 */
    protected final com.badlogic.gdx.utils.Array<Block> blenders = new com.badlogic.gdx.utils.Array<>();
    /** 与 {@link #blenders} 配套的去重集合（按 {@code Block.id}）。 */
    protected final com.badlogic.gdx.utils.IntSet blended = new com.badlogic.gdx.utils.IntSet();

    public Floor(String name){
        super(name);
    }

    @Override
    public void load(){
        //矿脉（OreFloor）的方块名是 ore-<物品>，贴图却是物品源图（copper1..3），走 textureName 指向
        variantRegions = loadRegions(textureName != null ? textureName : name, variants);
        if(variantRegions.length > 0) region = variantRegions[0];

        //地板边缘过渡贴图（对应原版 Floor.load 里的 `Core.atlas.find(name + "-edge").split(size, size)`）：
        //`<name>-edge` 是 3x3 个 32px 小图，size = tilesize / Draw.scl = 8 / 0.25 = 32。
        //⚠ Draw.scl 在 ClientLauncher 里于 `content.load()` **之前**由 `control.loadAsync()` 设好，此处取值有效。
        int edgeSize = (int)(com.phoenix.game.Vars.tilesize / com.phoenix.game.core.Draw.scl);
        TextureRegion edge = com.phoenix.game.core.Core.atlas == null
            ? null : com.phoenix.game.core.Core.atlas.findRegion(name + "-edge");
        edges = (edge == null || edgeSize <= 0) ? null : edge.split(edgeSize, edgeSize);
    }

    /**
     * 绘制与相邻地板的**边缘过渡**（对应原版 {@code Floor.drawEdges(Tile)} / {@code drawEdges(Tile, boolean)}）。
     * <p>算法照抄原版：
     * <ol>
     *   <li>扫 8 邻格，挑出"要往我这格压边缘"的地板（{@link #doEdge}），按 id 去重收集；</li>
     *   <li>按 id 排序后，对每个这样的地板再扫 8 邻格，把对应方向的边缘小图叠在本格上
     *       （{@code edges()[1 - dx][1 + dy]}）。</li>
     * </ol>
     * <p>⚠ <b>偏离</b>：
     * <ul>
     *   <li>原版 {@code doEdge} 里还有一项 {@code other.cacheLayer.ordinal() > this.cacheLayer.ordinal()}；
     *       本项目 {@code Block} <b>没有 cacheLayer 字段</b>（{@code graphics/CacheLayer} 类<b>存在</b>——
     *       enum，water/tar/normal/walls 四层，但 {@code beginShader/endShader} 是 TODO 空壳、没有离屏缓冲，
     *       也没有任何方块给它赋值 → 所有地板等价于 {@code normal}）→ 省略该项，只留 {@code !sameLayer}。</li>
     *   <li>原版还维护一个 {@code eq} 位掩码，但全工程没有任何读取方（死代码）→ 不搬。</li>
     *   <li>原版 {@code drawNonLayer(tile)}（= {@code drawEdges(tile, true)}）由 {@code FloorRenderer}
     *       在"非 normal 缓存层"那一趟调用；本项目地板只有一趟 → 不搬（等价于永远 {@code sameLayer = false}）。</li>
     * </ul>
     * <p>⚠ 原版是 {@code protected}（由 {@code Floor.draw} 内部调用）；本项目地板由 {@code core.Renderer}
     * 直接绘制，没有走 {@code Floor.draw} → 这里放开为 {@code public}。
     */
    public void drawEdges(Tile tile){
        drawEdges(tile, false);
    }

    protected void drawEdges(Tile tile, boolean sameLayer){
        if(tile == null) return;

        blenders.clear();
        blended.clear();

        for(int i = 0; i < 8; i++){
            com.badlogic.gdx.math.GridPoint2 point = com.phoenix.game.math.geom.Geometry.d8[i];
            Tile other = tile.getNearby(point.x, point.y);
            if(other != null && doEdge(other.floor(), sameLayer) && other.floor().edges() != null){
                if(blended.add(other.floor().id)){
                    blenders.add(other.floor());
                }
            }
        }

        blenders.sort((a, b) -> Integer.compare(a.id, b.id));

        for(int bi = 0; bi < blenders.size; bi++){
            Block block = blenders.get(bi);
            for(int i = 0; i < 8; i++){
                com.badlogic.gdx.math.GridPoint2 point = com.phoenix.game.math.geom.Geometry.d8[i];
                Tile other = tile.getNearby(point.x, point.y);
                if(other != null && other.floor() == block){
                    TextureRegion edgeRegion = edge((Floor)block, 2 - (point.x + 1), 2 - (point.y + 1));
                    if(edgeRegion != null){
                        //原版写的是 `Draw.rect(region, tile.worldx(), tile.worldy())`（原版 worldx 即中心）；
                        //本项目 Tile.worldx() 是左下角，而 Draw.rect 是中心语义 → 补半格。
                        com.phoenix.game.core.Draw.rect(edgeRegion,
                            tile.worldx() + com.phoenix.game.Vars.tilesize / 2f,
                            tile.worldy() + com.phoenix.game.Vars.tilesize / 2f);
                    }
                }
            }
        }
    }

    /** @return 本融合组的 3x3 边缘贴图；没有则 null（对应原版 {@code Floor.edges()}）。 */
    protected TextureRegion[][] edges(){
        return ((Floor)blendGroup).edges;
    }

    /**
     * @return 某个方向上的边缘小图（对应原版 {@code Floor.edge(Floor, int, int)}）。
     * <p>⚠ <b>下标必须适配 libgdx 的 split</b>：原版 arc 的 {@code TextureRegion.split} 返回
     * {@code [col][row]}（第二下标 0 = 图像**顶**行），而 libgdx 的 split 返回 {@code [row][col]}
     * （第一下标 0 = 图像**顶**行）→ 照抄 arc 的 {@code edges[x][2 - y]} 会把边缘小图取成
     * **另一个方向**的那块（行列互换 + 行方向错位），表现为"地形交界处冒出一条一条错位的边条"。
     * 换成 {@code edges[2 - y][x]} 即与 arc 语义等价。
     * <p>注：同一适配在 {@code world/blocks/StaticWall}（{@code -large} 四象限）里已做过。
     */
    protected TextureRegion edge(Floor block, int x, int y){
        return block.edges()[2 - y][x];
    }

    /**
     * 相邻地板 {@code other} 是否要往本格压边缘（对应原版 {@code Floor.doEdge}）。
     * <p>原版判据是 {@code other.blendGroup.id > blendGroup.id} —— 用"注册顺序"当"谁压谁"的代理
     * （后注册的地板压先注册的）。本项目的地板注册顺序是**早先冻结的**（改 id 会让旧存档的方块错位，
     * 见 {@code content/Blocks} 地板区开头的说明），而 {@code water}/{@code deepwater} 等液体地板
     * 恰好排在 {@code grass}/{@code stone}/{@code sand} **之后** → 直接照抄算式会变成"水压陆地"
     * （岸线上是水的蓝边渗到沙地上），与原版观感相反。
     * <p>⚠ <b>刻意偏离</b>：这里补一条"液体永远在下、非液体压上去"的判据来修正岸线方向；
     * 同为液体（如 {@code water} vs {@code deepwater}）时仍走原版的 id 比较（两者颜色几乎一样，看不出差别）。
     */
    protected boolean doEdge(Floor other, boolean sameLayer){
        boolean higher;
        if(this.isLiquid != other.isLiquid){
            higher = !other.isLiquid;
        }else{
            higher = other.blendGroup.id > blendGroup.id;
        }
        return (higher || edges() == null) && other.edgeOnto(this) && !sameLayer;
    }

    /** 子类可覆写以禁止某类地板压边缘（对应原版 {@code Floor.edgeOnto}）。 */
    protected boolean edgeOnto(Floor other){
        return true;
    }

    /** 依据坐标确定性地取一个变体贴图。 */
    public TextureRegion variant(int x, int y){
        if(variantRegions.length == 0) return region;
        int hash = x * 31 + y * 17;
        return variantRegions[Math.abs(hash) % variantRegions.length];
    }

    @Override
    public boolean hasEntity(){
        return false;
    }
}
