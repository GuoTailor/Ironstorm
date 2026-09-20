package com.phoenix.game.world.blocks.distribution;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.GridPoint2;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 传送带。参照 Mindustry mindustry.world.blocks.distribution.Conveyor 移植。
 * <p>物品沿 rotation 方向前进；到末端调用 {@link #offloadDir} 交给前方建筑。
 * 贴图按"名称-拼接组-动画帧"加载（图集里只有 conveyor-0-0 ~ conveyor-4-3），
 * 拼接组按四邻是否有可拼接方块自动选择（对应原版 Autotiler.buildBlending）。
 * <p>接收规则（对应原版 acceptItem）：正后方输入只要尾部留出 {@code itemSpace} 就收；
 * 侧向输入更严格（尾部位置须 &gt; 0.7），侧向插入到带子中段并带左右偏移（xs）。
 */
public class Conveyor extends Block{
    /** 物品之间的最小间距（0~1，单位为带长比例）。 */
    protected static final float itemSpace = 0.4f;
    /** 同时容纳的物品数。 */
    protected static final int capacity = 4;

    /** 贴图：[拼接组 0~4][动画帧 0~3]，区域名 = 名称-拼接组-帧（对应原版 Conveyor.regions）。 */
    public TextureRegion[][] regions = new TextureRegion[5][4];
    /** 拼接结果缓存：[0]=拼接组，[1]=1 表示镜像（避免每帧分配）。 */
    private static final int[] tiling = new int[2];

    /** 物品移动速度（比例/帧）。 */
    public float speed = 0.03f;
    /** 展示用速度（对应原版 displayedSpeed，HUD 里显示"每秒搬运几个"）。 */
    public float displayedSpeed = 0f;

    public Conveyor(String name){
        super(name);
        rotate = true;
        update = true;
        solid = false;
        health = 45;
        hasItems = true;
        itemCapacity = capacity;
        entityType = ConveyorEntity::new;
    }

    /**
     * 加载贴图。图集里没有名为 conveyor 的单张图（只有 conveyor-拼接组-帧），
     * 因此不能走父类 loadRegions（那样会退回 blank 白块）。
     */
    @Override
    public void load(){
        for(int i = 0; i < regions.length; i++){
            for(int j = 0; j < regions[i].length; j++){
                regions[i][j] = Core.atlas == null ? null : Core.atlas.findRegion(name + "-" + i + "-" + j);
            }
        }
        //建造预览、HUD 图标等按 region 取图（用直行带的第一帧）
        region = regions[0][0];
        variantRegions = new TextureRegion[]{ region };
    }

    /**
     * 邻接表刷新时预计算拼接贴图与"下游带"引用（对应原版 Conveyor.onProximityUpdate）。
     * <p>放在这里而不是 draw 里，是为了避免每帧重算拼接；同时缓存下游带用于物品位置对齐。
     */
    @Override
    public void onProximityUpdate(Tile tile){
        if(!(tile.entity instanceof ConveyorEntity)) return;
        ConveyorEntity e = (ConveyorEntity)tile.entity;

        int[] t = tiling(tile);
        e.blendbits = t[0];
        e.blendsclx = 1f;
        e.blendscly = t[1] == 1 ? -1f : 1f;

        Tile front = tile.front();
        e.next = front == null ? null : front.entity;
        e.nextc = (e.next instanceof ConveyorEntity && e.next.getTeam() == tile.getTeam()) ? (ConveyorEntity)e.next : null;
        //下游带朝向与本带一致 = 直行衔接，此时物品的横向位置要传给下游（对应原版 aligned）
        e.aligned = e.nextc != null && tile.rotation() == e.nextc.tile.rotation();
    }

    /**
     * 绘制传送带本体：按预计算的拼接组选图、按时间走帧、按朝向旋转。
     * 对应原版 Conveyor.draw（贴图基准朝向为"右"，用 rotation*90 旋转）。
     */
    @Override
    public void draw(Tile tile){
        ConveyorEntity e = (ConveyorEntity)tile.entity;
        if(e == null) return;

        //blendbits 未初始化（-1）说明邻接表还没刷过，退化为现算，保证不画错
        int group = e.blendbits < 0 ? tiling(tile)[0] : e.blendbits;
        group = Mathf.clamp(group, 0, regions.length - 1);
        //堵住时停在静止帧（对应原版 clogHeat 判断）
        int frame = e.clogHeat <= 0.5f ? (int)(Time.time * speed * 8f * e.timeScale) % 4 : 0;

        TextureRegion reg = regions[group][Mathf.clamp(frame, 0, regions[0].length - 1)];
        if(reg == null) return;

        float cx = tile.worldx() + tilesize / 2f, cy = tile.worldy() + tilesize / 2f;
        //贴图基准朝向是"右"，本项目 rotation 0=上/1=右/2=下/3=左，故角度 = (1 - rotation) * 90
        float angle = (1 - (tile.rotation() & 3)) * 90f;

        Core.batch.draw(reg, cx - tilesize / 2f, cy - tilesize / 2f, tilesize / 2f, tilesize / 2f,
                tilesize, tilesize, e.blendsclx, e.blendscly, angle);
    }

    /**
     * 计算贴图拼接：对应原版 Autotiler.buildBlending + Conveyor.transformCase。
     * @return 长度为 2 的缓存数组：[0]=拼接组（0~4），[1]=1 表示需要镜像
     */
    private int[] tiling(Tile tile){
        int rotation = tile.rotation() & 3;
        boolean right = blends(tile, rotation, 1), back = blends(tile, rotation, 2), left = blends(tile, rotation, 3);

        int num =
            (back && right && left) ? 0 :
            (right && left) ? 1 :
            (right && back) ? 2 :
            (left && back) ? 3 :
            right ? 4 :
            left ? 5 : -1;

        tiling[0] = 0;
        tiling[1] = 0;

        if(num == 0){
            tiling[0] = 3;
        }else if(num == 1){
            tiling[0] = 4;
        }else if(num == 2){
            tiling[0] = 2;
        }else if(num == 3){
            tiling[0] = 2;
            tiling[1] = 1;
        }else if(num == 4){
            tiling[0] = 1;
            tiling[1] = 1;
        }else if(num == 5){
            tiling[0] = 1;
        }

        return tiling;
    }

    /**
     * @param direction 相对朝向的方向：1=右侧，2=后方，3=左侧（对应原版 blends 的 direction）
     * @return 该方向是否有可拼接的方块
     */
    private boolean blends(Tile tile, int rotation, int direction){
        GridPoint2 dir = Geometry.d4[Mathf.mod(rotation - direction, 4)];
        Tile other = tile.getNearby(dir.x, dir.y);
        if(other == null || other.entity == null) return false;

        //卫星瓦片解析到中心建筑
        other = other.link();
        if(other.getTeam() != tile.getTeam()) return false;

        Block otherBlock = other.block();
        if(!otherBlock.outputsItems()) return false;

        //无朝向的机器（钻头/硅炉/核心）贴哪边都算拼接；有朝向的（传送带）要求正面朝向我们
        if(!otherBlock.rotate) return true;

        Tile front = other.front();
        return front != null && front.link() == tile.link();
    }

    /**
     * 接收物品：正后方输入只需尾部留出间距；侧向输入要求尾部位置 &gt; 0.7。
     * 另外拒绝来自"正面朝向本带"的旋转方块（防止物品被反向灌回）。
     * @param item 物品
     * @param tile 本带所在瓦片
     * @param source 来源瓦片，可为 null
     * @return true 表示可接收
     */
    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        ConveyorEntity e = (ConveyorEntity)tile.entity;
        if(e == null || e.len >= capacity) return false;

        //方向索引差：0 = 正后方输入，奇数 = 侧向输入（本项目 d4 顺序与原版不同，但差值语义一致）
        int direction = source == null ? 0 : Math.abs(source.relativeTo(tile.x, tile.y) - tile.rotation());
        return (((direction == 0) && e.minitem >= itemSpace) || ((direction % 2 == 1) && e.minitem > 0.7f))
            && (source == null || !(source.block().rotate && (source.rotation() + 2) % 4 == tile.rotation()));
    }

    /**
     * 接收物品：正后方输入插到带尾（索引 0，位置 0）；侧向输入插到中段（{@code mid}，位置 0.5），
     * 并按来源左右记录横向偏移 xs。
     * @param item 物品
     * @param tile 本带所在瓦片
     * @param source 来源瓦片，可为 null
     */
    @Override
    public void handleItem(Item item, Tile tile, Tile source){
        ConveyorEntity e = (ConveyorEntity)tile.entity;
        if(e == null || e.len >= capacity) return;

        int r = tile.rotation();
        int rel = source == null ? r : source.relativeTo(tile.x, tile.y);
        int ang = rel - r;
        //来源在本带左侧则偏移 -1，右侧则 +1（0 表示正后方）
        float x = (ang == -1 || ang == 3) ? 1f : (ang == 1 || ang == -3) ? -1f : 0f;

        if(ang == 0){
            e.add(0);
            e.xs[0] = x;
            e.ys[0] = 0f;
            e.ids[0] = item;
            e.lastInserted = 0;
        }else{
            e.add(e.mid);
            e.xs[e.mid] = x;
            e.ys[e.mid] = 0.5f;
            e.ids[e.mid] = item;
            e.lastInserted = e.mid;
        }

        e.items.add(item, 1);
        e.minitem = 0f;
    }

    /**
     * 从带子上取下指定数量的物品（对应原版 Conveyor.removeStack，供装卸器/单位取物）。
     * @return 实际取下数量
     */
    @Override
    public int removeStack(Tile tile, Item item, int amount){
        if(!(tile.entity instanceof ConveyorEntity)) return 0;
        ConveyorEntity e = (ConveyorEntity)tile.entity;

        int removed = 0;
        for(int j = 0; j < amount; j++){
            for(int i = 0; i < e.len; i++){
                if(e.ids[i] == item){
                    e.remove(i);
                    removed++;
                    break;
                }
            }
        }

        e.items.remove(item, removed);
        return removed;
    }

    /**
     * 绘制带上的物品（叠在带贴图之上），含侧向输入的横向偏移。
     * @param tile 本带所在瓦片
     */
    @Override
    public void drawLayer(Tile tile){
        ConveyorEntity e = (ConveyorEntity)tile.entity;
        if(e == null || e.len == 0) return;

        TextureRegion white = Core.atlas == null ? null : Core.atlas.findRegion("white");
        if(white == null) return;

        // 前进单位向量：本项目 rotation 0=+y, 1=+x, 2=-y, 3=-x
        int dir = tile.rotation() & 3;
        float dirx = dir == 1 ? 1f : dir == 3 ? -1f : 0f;
        float diry = dir == 0 ? 1f : dir == 2 ? -1f : 0f;
        // 侧向单位向量（垂直于前进方向），xs 为正表示偏向该侧
        float perpx = diry, perpy = -dirx;

        float baseX = tile.worldx() + tilesize / 2f;
        float baseY = tile.worldy() + tilesize / 2f;
        float itemSize = tilesize * 0.3f;

        for(int i = 0; i < e.len; i++){
            float progress = Mathf.clamp(e.ys[i], 0f, 1f);
            float px = baseX + dirx * (progress - 0.5f) * tilesize + perpx * e.xs[i] * tilesize / 2f;
            float py = baseY + diry * (progress - 0.5f) * tilesize + perpy * e.xs[i] * tilesize / 2f;

            Core.batch.setColor(e.ids[i].color);
            Core.batch.draw(white, px - itemSize / 2f, py - itemSize / 2f, itemSize, itemSize);
            Core.batch.setColor(Color.WHITE);
        }
    }

    /** 传送带实体：并行数组保存物品与其在带上的位置。 */
    public class ConveyorEntity extends TileEntity{
        /** 物品（索引 0 为带尾最新，索引 len-1 为带首最前）。 */
        public final Item[] ids = new Item[capacity];
        /** 各物品的横向偏移（-1~1，侧向输入时非 0）。 */
        public final float[] xs = new float[capacity];
        /** 各物品在带上的位置，0（尾）~1（首）。 */
        public final float[] ys = new float[capacity];
        /** 物品数量。 */
        public int len;
        /** 尾部物品位置（0~1），用于判断能否再接收。 */
        public float minitem = 1f;
        /** 中段插入点（对应原版 mid），侧向输入插到这里。 */
        public int mid;
        /** 最后一次插入的索引，用于把横向偏移传给下游带。 */
        public int lastInserted;
        /** 堵塞热度（0~1），用于贴图静止帧的平滑过渡。 */
        public float clogHeat;

        /** 拼接贴图缓存（对应原版 blendbits/blendsclx/blendscly）；-1 表示尚未计算。 */
        public int blendbits = -1;
        public float blendsclx = 1f, blendscly = 1f;

        /** 正前方的建筑实体（对应原版 next）。 */
        public TileEntity next;
        /** 正前方的传送带（同队），对应原版 nextc。 */
        public ConveyorEntity nextc;
        /** 下游带与本带朝向一致（直行衔接）。 */
        public boolean aligned;

        /**
         * 在指定索引处腾出空位（其后元素后移一位）。
         * @param index 插入索引
         */
        public void add(int index){
            for(int i = len; i > index; i--){
                ids[i] = ids[i - 1];
                xs[i] = xs[i - 1];
                ys[i] = ys[i - 1];
            }
            len++;
        }

        /** 删除指定索引的物品（其后元素前移一位）。 */
        public void remove(int index){
            for(int i = index; i < len - 1; i++){
                ids[i] = ids[i + 1];
                xs[i] = xs[i + 1];
                ys[i] = ys[i + 1];
            }
            if(len > 0){
                ids[len - 1] = null;
                xs[len - 1] = 0f;
                ys[len - 1] = 0f;
            }
            len--;
        }

        @Override
        public void update(){
            minitem = 1f;
            mid = 0;

            if(len == 0){
                clogHeat = 0f;
                return;
            }

            //下游带如果与本带直行衔接，带首物品不能越过"下游带的尾部空隙"
            float nextMax = aligned && nextc != null ? 1f - Math.max(itemSpace - nextc.minitem, 0f) : 1f;

            for(int i = len - 1; i >= 0; i--){
                // 前方物品位置（带首视为无限远），本物品不得越过它 itemSpace
                float nextpos = (i == len - 1 ? 100f : ys[i + 1]) - itemSpace;
                float maxmove = Mathf.clamp(nextpos - ys[i], 0f, speed * delta());

                ys[i] += maxmove;

                if(ys[i] > nextMax) ys[i] = nextMax;
                if(ys[i] > 0.5f && i > 0) mid = i - 1;
                //横向偏移缓慢归零（对应原版 approachDelta）
                xs[i] = Mathf.lerpDelta(xs[i], 0f, speed * 2f);

                if(ys[i] >= 1f && offloadDir(tile, ids[i])){
                    //直行衔接到下游带时，把横向偏移传过去，物品位置不会在接缝处跳变
                    if(aligned && nextc != null && nextc.lastInserted >= 0 && nextc.lastInserted < capacity){
                        nextc.xs[nextc.lastInserted] = xs[i];
                    }
                    //丢弃该物品及其后方元素（正常只有带首会到 1）
                    for(int j = i; j < len; j++){
                        ids[j] = null;
                        xs[j] = 0f;
                        ys[j] = 0f;
                    }
                    len = i;
                }else if(ys[i] < minitem){
                    minitem = ys[i];
                }
            }

            //尾部堵住时热度上升，贴图平滑停在静止帧
            if(minitem < itemSpace + (blendbits == 1 ? 0.3f : 0f)){
                clogHeat = Mathf.lerpDelta(clogHeat, 1f, 0.02f);
            }else{
                clogHeat = 0f;
            }
        }
    }
}
