package com.phoenix.game.world.blocks.distribution;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntSet;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Lines;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Edges;
import com.phoenix.game.world.Pos;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 物品桥。参照 Mindustry mindustry.world.blocks.distribution.ItemBridge 移植。
 * <p>两台**同型**桥之间按「同行/同列且距离 ≤ {@link #range}」互相连接（{@code link} 存对端瓦片坐标），
 * 物品从一台的库存按 {@link #transportTime} 的间隔直接送到对端。
 * <p>{@code incoming} 记录"谁在往我这儿送"，用于禁止物品从对端方向回灌（否则会在两桥之间来回弹）。
 * <p>放置时自动连接上一次放置的同类桥（对应原版 playerPlaced）；手动点选配置待 HUD 任务补。
 */
public class ItemBridge extends Block{
    /** 传输间隔（帧）。 */
    public float transportTime = 2f;
    /** 最大连接距离（格）。 */
    public int range;

    /** 桥面/端点/箭头贴图（区域名 = 名称-bridge / -end / -arrow）。 */
    public TextureRegion endRegion, bridgeRegion, arrowRegion;

    /** 上一次放置的桥位置（对应原版静态 lastPlaced，新放置的桥自动连它）。 */
    protected static int lastPlaced = Pos.invalid;

    public ItemBridge(String name){
        super(name);
        update = true;
        solid = true;
        hasItems = true;
        itemCapacity = 10;
        posConfig = true;
        configurable = true;
        unloadable = false;
        entityType = ItemBridgeEntity::new;
    }

    @Override
    public void load(){
        super.load();
        if(Core.atlas == null) return;
        endRegion = Core.atlas.findRegion(name + "-end");
        bridgeRegion = Core.atlas.findRegion(name + "-bridge");
        arrowRegion = Core.atlas.findRegion(name + "-arrow");
    }

    @Override
    public void configured(Tile tile, Player player, int value){
        if(tile.entity instanceof ItemBridgeEntity){
            ((ItemBridgeEntity)tile.entity).link = value;
        }
    }

    /** 新放置时自动连上"上一次放置的同类桥"（对应原版 playerPlaced）。 */
    @Override
    public void playerPlaced(Tile tile){
        Tile link = findLink(tile.x, tile.y);
        if(linkValid(tile, link)){
            link.configure(tile.pos());
        }
        lastPlaced = tile.pos();
    }

    /** @return 上一次放置的桥；若与本格可连接则返回它，否则 null。 */
    public Tile findLink(int x, int y){
        if(Vars.world == null || lastPlaced == Pos.invalid || lastPlaced == Pos.get(x, y)) return null;

        Tile self = Vars.world.tile(x, y);
        Tile other = Vars.world.tile(lastPlaced);
        return linkValid(self, other) ? other : null;
    }

    public boolean linkValid(Tile tile, Tile other){
        return linkValid(tile, other, true);
    }

    /**
     * @param checkDouble true 时还要求对方没有连回自己（避免双向连接）
     * @return 两格是否构成合法连接（同型桥 + 同行/同列 + 距离 ≤ range）
     */
    public boolean linkValid(Tile tile, Tile other, boolean checkDouble){
        if(tile == null || other == null) return false;
        if(!(other.entity instanceof ItemBridgeEntity)) return false;

        if(tile.x == other.x){
            if(Math.abs(tile.y - other.y) > range) return false;
        }else if(tile.y == other.y){
            if(Math.abs(tile.x - other.x) > range) return false;
        }else{
            return false;
        }

        return other.block() == this && (!checkDouble || ((ItemBridgeEntity)other.entity).link != tile.pos());
    }

    /** 把库存里的一件物品按间隔送到对端（对应原版 updateTransport）。 */
    public void updateTransport(Tile tile, Tile other){
        if(!(tile.entity instanceof ItemBridgeEntity)) return;
        ItemBridgeEntity e = (ItemBridgeEntity)tile.entity;

        e.transportTimer += e.delta();
        if(e.uptime < 0.5f || e.transportTimer < transportTime) return;

        e.transportTimer = 0f;

        Item item = e.items.take();
        if(item != null && other.block().acceptItem(item, other, tile)){
            other.block().handleItem(item, other, tile);
            e.cycleSpeed = Mathf.lerpDelta(e.cycleSpeed, 4f, 0.05f);
        }else{
            e.cycleSpeed = Mathf.lerpDelta(e.cycleSpeed, 1f, 0.01f);
            if(item != null) e.items.add(item, 1);
        }
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        if(source == null || !(tile.entity instanceof ItemBridgeEntity)) return false;
        if(tile.getTeam() != source.getTeam()) return false;

        ItemBridgeEntity e = (ItemBridgeEntity)tile.entity;
        Tile other = Vars.world == null ? null : Vars.world.tile(e.link);

        if(linkValid(tile, other)){
            //已连接：禁止从"对端方向"再灌进来（否则物品会两桥对穿）
            int rel = tile.absoluteRelativeTo(other.x, other.y);
            int rel2 = tile.relativeTo(source.x, source.y);
            if(rel == rel2) return false;
        }else{
            //未连接：只接受"对端桥"直接送来的
            return source.block() instanceof ItemBridge
                && source.entity instanceof ItemBridgeEntity
                && ((ItemBridgeEntity)source.entity).link == tile.pos()
                && e.items.total() < itemCapacity;
        }

        return e.items.total() < itemCapacity;
    }

    /**
     * 是否接受液体（对应原版 {@code ItemBridge.acceptLiquid}）。
     * <p>判据与原版一致：只接受"对端桥"送来的液体（禁止从对端方向回灌），且没装满、且槽内液体兼容。
     * <p>{@code LiquidBridge}/{@code LiquidExtendingBridge} 通过继承本覆写绕开
     * {@code Block.acceptLiquid} 的 {@code liquidfilters} 判据（桥没有液体消耗器）。
     */
    @Override
    public boolean acceptLiquid(Tile tile, Tile source, Liquid liquid, float amount){
        if(source == null || tile.getTeam() != source.getTeam() || !hasLiquids) return false;
        if(!(tile.entity instanceof ItemBridgeEntity) || tile.entity.liquids == null) return false;

        ItemBridgeEntity e = (ItemBridgeEntity)tile.entity;
        Tile other = Vars.world == null ? null : Vars.world.tile(e.link);

        if(linkValid(tile, other)){
            //已连接：禁止从"对端方向"再灌进来（否则液体会两桥对穿）
            int rel = tile.absoluteRelativeTo(other.x, other.y);
            int rel2 = tile.relativeTo(source.x, source.y);

            if(rel == rel2) return false;
        }else if(!(source.block() instanceof ItemBridge && source.entity instanceof ItemBridgeEntity
            && ((ItemBridgeEntity)source.entity).link == tile.pos())){
            return false;
        }

        return tile.entity.liquids.get(liquid) + amount < liquidCapacity
            && (tile.entity.liquids.current() == liquid || tile.entity.liquids.get(tile.entity.liquids.current()) < 0.2f);
    }

    @Override
    public boolean canDump(Tile tile, Tile to, Item item){
        if(!(tile.entity instanceof ItemBridgeEntity)) return true;
        ItemBridgeEntity e = (ItemBridgeEntity)tile.entity;

        Tile other = Vars.world == null ? null : Vars.world.tile(e.link);

        if(!linkValid(tile, other)){
            //未连接：不能往"已经在给我送东西"的方向丢（否则来回弹）
            Tile edge = Edges.getFacingEdge(to, tile);
            int i = tile.absoluteRelativeTo(edge.x, edge.y);

            IntSet.IntSetIterator it = e.incoming.iterator();
            while(it.hasNext){
                int v = it.next();
                if(tile.absoluteRelativeTo(Pos.x(v), Pos.y(v)) == i) return false;
            }
            return true;
        }

        int rel = tile.absoluteRelativeTo(other.x, other.y);
        int rel2 = tile.relativeTo(to.x, to.y);
        return rel != rel2;
    }

    /**
     * 放置预览（对应原版 {@code ItemBridge.drawPlace}）：
     * 四条从本格向外指到最大连接距离的虚线（range 格内可连的方向），
     * 若能与"上一次放置的桥"连上，再画两桥之间的矩形框 + 指向对端的桥头箭头。
     * <p><b>与原版的差异（坐标口径）</b>：原版把 {@code x * tilesize} 当作贴图中心（它的 {@code drawx()} 就是那个值），
     * 本项目贴图中心是「锚点瓦片左下角 + offset() + 半格」（见 {@link Block#centerX}），故所有落笔点都要再加
     * {@code offset() + tilesize/2f}（ItemBridge 是 1x1，实际就是 + 半格）；照抄会整体偏半格。
     * <p><b>与原版的差异（缺 API）</b>：原版画矩形用 {@code Lines.rect(x, y, w, h)}（(x,y) 为左下角），
     * 本项目 {@code Lines} 没有该重载 → 用 4 条 {@link Lines#line} 拼出同样的框。
     * <p><b>与原版的差异（d4 旋向）</b>：四条虚线四向都画，方向集合相同，索引顺序不影响结果；
     * 但桥头箭头用 {@code Geometry.d4[rot]} 定方向，本项目 d4 = {上,右,下,左}（原版 {右,上,左,下}），
     * 且 {@code absoluteRelativeTo} 返回的是本项目口径的方向索引，所以位置公式可照抄，
     * 而贴图旋转角要换算：原版 {@code rot * 90} → 本项目 {@code (1 - rot) * 90}（两边贴图基准都朝右）。
     */
    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        Tile link = findLink(x, y);

        //本格贴图中心（见方法注释：原版直接用 x*tilesize）
        float cx = x * tilesize + offset() + tilesize / 2f, cy = y * tilesize + offset() + tilesize / 2f;

        Lines.stroke(2f, Pal.placing);
        for(int i = 0; i < 4; i++){
            Lines.dashLine(
                cx + Geometry.d4[i].x * (tilesize / 2f + 2),
                cy + Geometry.d4[i].y * (tilesize / 2f + 2),
                cx + Geometry.d4[i].x * (range + 0.5f) * tilesize,
                cy + Geometry.d4[i].y * (range + 0.5f) * tilesize,
                range);
        }

        Draw.color(Pal.placing);
        Lines.stroke(1f);
        if(link != null){
            int rot = link.absoluteRelativeTo(x, y);
            float w = (link.x == x ? tilesize : Math.abs(link.x - x) * tilesize - tilesize);
            float h = (link.y == y ? tilesize : Math.abs(link.y - y) * tilesize - tilesize);

            //两桥中心的中点 + 半格（原版中点 = ((x + link.x) / 2f) * tilesize）
            float mx = (x + link.x) / 2f * tilesize + offset() + tilesize / 2f;
            float my = (y + link.y) / 2f * tilesize + offset() + tilesize / 2f;
            float rx = mx - w / 2f, ry = my - h / 2f;
            Lines.line(rx, ry, rx + w, ry);
            Lines.line(rx + w, ry, rx + w, ry + h);
            Lines.line(rx + w, ry + h, rx, ry + h);
            Lines.line(rx, ry + h, rx, ry);

            //桥头箭头：落在"连接点旁一格"，指向新桥方向
            if(arrowRegion != null){
                Draw.rect(arrowRegion,
                    (link.x + Geometry.d4[rot].x) * tilesize + offset() + tilesize / 2f,
                    (link.y + Geometry.d4[rot].y) * tilesize + offset() + tilesize / 2f,
                    (1 - rot) * 90f);
            }
        }
        Draw.color();
    }

    /** 绘制桥体：沿两桥连线拉伸一条桥面 + 两端端点（对应原版 drawLayer 的 Lines.line）。 */
    @Override
    public void drawLayer(Tile tile){
        if(!(tile.entity instanceof ItemBridgeEntity)) return;
        ItemBridgeEntity e = (ItemBridgeEntity)tile.entity;

        Tile other = Vars.world == null ? null : Vars.world.tile(e.link);
        if(!linkValid(tile, other) || bridgeRegion == null) return;

        //桥体不透明度设置（对应原版 ItemBridge.drawLayer：bridgeopacity 是整数百分比，0 = 不画）
        float opacity = Core.settings.getInt("bridgeopacity", 75) / 100f;
        if(opacity <= 0f) return;

        int i = tile.absoluteRelativeTo(other.x, other.y);
        float x1 = tile.worldx() + tilesize / 2f, y1 = tile.worldy() + tilesize / 2f;
        float x2 = other.worldx() + tilesize / 2f, y2 = other.worldy() + tilesize / 2f;
        float len = Vector2.dst(x1, y1, x2, y2);
        float ang = Angles.angle(x1, y1, x2, y2);

        float alpha = Math.max(e.uptime, 0.25f) * opacity;
        Core.batch.setColor(1f, 1f, 1f, alpha);

        //桥面：以起点为原点画一条旋转的矩形
        Core.batch.draw(bridgeRegion, x1, y1 - 2f, 0f, 2f, len, 4f, 1f, 1f, ang);

        if(endRegion != null){
            float ea = (1 - (i & 3)) * 90f;
            Core.batch.draw(endRegion, x1 - tilesize / 2f, y1 - tilesize / 2f, tilesize / 2f, tilesize / 2f,
                tilesize, tilesize, 1f, 1f, ea);
            Core.batch.draw(endRegion, x2 - tilesize / 2f, y2 - tilesize / 2f, tilesize / 2f, tilesize / 2f,
                tilesize, tilesize, 1f, 1f, ea + 180f);
        }

        Core.batch.setColor(Color.WHITE);
    }

    public class ItemBridgeEntity extends TileEntity{
        /** 对端桥的位置（{@link Pos} 打包坐标）；{@link Pos#invalid} 表示未连接。 */
        public int link = Pos.invalid;

        @Override
        public int config(){
            return link;
        }
        /** 谁在往我这儿送（打包坐标集合），用于禁止回灌。 */
        public final IntSet incoming = new IntSet();
        /** 传输可用度（0~1，无电时下降）。 */
        public float uptime;
        /** 动画计时。 */
        public float time, time2;
        /** 传输节奏（成功时加速到 4，仅用于视觉）。 */
        public float cycleSpeed = 1f;
        /** 距离下次传输的计时。 */
        public float transportTimer;

        @Override
        public void update(){
            time += cycleSpeed * delta();
            time2 += (cycleSpeed - 1f) * delta();

            //清理失效的 incoming（对端被拆/断开）
            IntSet.IntSetIterator it = incoming.iterator();
            while(it.hasNext){
                int v = it.next();
                Tile o = Vars.world == null ? null : Vars.world.tile(v);
                if(!linkValid(tile, o, false) || !(o.entity instanceof ItemBridgeEntity)
                    || ((ItemBridgeEntity)o.entity).link != tile.pos()){
                    it.remove();
                }
            }

            Tile other = Vars.world == null ? null : Vars.world.tile(link);
            if(!linkValid(tile, other)){
                //没连接：库存倒给邻居，桥体收起
                tryDump(tile);
                uptime = 0f;
                return;
            }

            ((ItemBridgeEntity)other.entity).incoming.add(tile.pos());

            //有电（或本来就不耗电）才能工作
            boolean powered = !hasPower || (power != null && power.status > 0.001f);
            uptime = Mathf.lerpDelta(uptime, powered ? 1f : 0f, powered ? 0.04f : 0.02f);

            updateTransport(tile, other);
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeInt(link);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            link = in.readInt();
            //incoming 由对端每帧重新登记；其余是动画/节奏量，下一帧自然重算
            incoming.clear();
            uptime = 0f;
            time = 0f;
            time2 = 0f;
            cycleSpeed = 1f;
            transportTimer = 0f;
        }
    }
}
