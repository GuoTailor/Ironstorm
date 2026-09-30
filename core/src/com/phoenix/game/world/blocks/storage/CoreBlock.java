package com.phoenix.game.world.blocks.storage;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Lines;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.game.EventType;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockFlag;

import java.util.EnumSet;

import static com.phoenix.game.Vars.tilesize;

/**
 * 核心建筑。参照 Mindustry mindustry.world.blocks.storage.CoreBlock 最小移植。
 * <p>作为双方可被摧毁的寻路目标，并接收物品：核心库存即本队共享库存（原版语义）。
 * 原版的单位重生/近邻容量/物品拆分等依赖 SpawnerTrait 与 proximity 系统，尚未移植。
 * <p>核心列表（{@code Teams.cores/playerCores}）已接线（S3 批次）：{@code CoreEntity.init()} 注册、
 * {@code onProximityRemoved()} 注销（对应原版 {@code CoreBlock.placed()/removed()}）。
 */
public class CoreBlock extends Block{
    /** 选中框贴图（图集里的 block-select，四角各画一块）。 */
    public TextureRegion selectRegion;

    public CoreBlock(String name){
        super(name);

        solid = true;
        update = true;
        destructible = true;
        //不写 health / itemCapacity：原版 CoreBlock 类不设这两个字段，全部由 Blocks.java 给
        //（health：shard 1100 / foundation 2000 / nucleus 4000；itemCapacity：4000 / 9000 / 13000）
        //size 默认 3 与 core-shard 一致，foundation/nucleus 在 Blocks.java 覆盖成 4 / 5
        size = 3;
        flags = EnumSet.of(BlockFlag.core);
        hasItems = true;
        entityType = CoreEntity::new;
    }

    @Override
    public void load(){
        super.load();
        selectRegion = Core.atlas == null ? null : Core.atlas.findRegion("block-select");
    }

    /**
     * 悬停核心时，把核心本体 + 所有"与它共享库存的相邻仓库"逐格描边（对应原版 {@code CoreBlock.drawSelect}）。
     * <p>判据是"库存对象是同一个引用"（{@code other.entity.items == entity.items}）：
     * 只有被 {@link #onProximityUpdate} 链接过的仓库才会共享核心的库存对象，
     * 所以贴着核心的容器会被一起高亮，独立摆的容器不会。
     * <p><b>与原版的差异</b>：
     * <ul>
     *     <li>原版用 {@code Geometry.d8edge}（四角方向 {右上,左上,左下,右下}）；本项目 {@code Geometry} 没有该数组，
     *         就地内联成两个 int 数组。四角贴图的旋转角 {@code i * 90} 是**纯几何角**（不是瓦片 rotation 索引），
     *         两边角度约定相同，故照抄。</li>
     *     <li>原版用 {@code proximity().contains(pred) / each(pred, cons)}；本项目 {@code TileEntity.proximity}
     *         是 libgdx 的 {@code Array}（没有谓词重载），改成下标循环。</li>
     *     <li>⚠ 坐标口径：原版 {@code t.drawx()/drawy()} 是贴图中心；本项目是「锚点瓦片左下角 + offset()」，
     *         故改用 {@link Block#centerX}/{@link Block#centerY}（多格方块还要先 {@code link()} 到锚点）。</li>
     * </ul>
     */
    @Override
    public void drawSelect(Tile tile){
        if(selectRegion == null) return;

        Tile anchor = tile.link();
        if(!(anchor.entity instanceof CoreEntity)) return;
        CoreEntity entity = (CoreEntity)anchor.entity;

        //四角方向（对应原版 Geometry.d8edge：右上/左上/左下/右下）
        int[] ex = {1, -1, -1, 1}, ey = {1, 1, -1, -1};

        //原版先 Lines.stroke(1f, Pal.accent)：既设线宽也把精灵染色设成 accent（block-select 贴图因此是暖黄色）
        Lines.stroke(1f, Pal.accent);

        boolean hasContainer = false;
        for(int i = 0; i < entity.proximity.size; i++){
            Tile other = entity.proximity.get(i);
            if(isContainer(other) && other.entity.items == entity.items){
                hasContainer = true;
                break;
            }
        }

        if(hasContainer) drawOutline(anchor, ex, ey);

        for(int i = 0; i < entity.proximity.size; i++){
            Tile other = entity.proximity.get(i);
            if(isContainer(other) && other.entity.items == entity.items){
                drawOutline(other, ex, ey);
            }
        }

        Draw.color();
    }

    /** 给一格方块的四角画 block-select 贴图（对应原版 drawSelect 里的 {@code outline} 闭包）。 */
    private void drawOutline(Tile t, int[] ex, int[] ey){
        //offset = 原版 -max(size-1, 0)/2 * tilesize：多格方块的四角贴图向内收，贴着方块轮廓
        float off = -Math.max(t.block().size - 1, 0) / 2f * tilesize;

        for(int i = 0; i < 4; i++){
            Draw.rect(selectRegion, t.block().centerX(t) + off * ex[i], t.block().centerY(t) + off * ey[i], i * 90f);
        }
    }

    /** @return 该瓦片是否为仓库（对应原版 {@code CoreBlock.isContainer}）。 */
    public boolean isContainer(Tile tile){
        return tile.entity instanceof StorageBlock.StorageEntity;
    }

    /**
     * 核心发光（对应原版 {@code CoreBlock.drawLight}，见原版 CoreBlock.java:67-70）。
     * <p>已接线（S5b 批次）：本项目已移植灯光管线（{@code graphics/LightRenderer}），
     * 本方法由 {@code core/Renderer.drawLights} 逐格调用。
     * <p>⚠ 坐标口径：原版 {@code tile.drawx()/drawy()} 是方块中心；本项目 {@code worldx()/worldy()} 是瓦片角点
     * → 用 {@link #centerX}/{@link #centerY}（{@code Renderer.drawLights} 已用 {@code tile.isLinked()}
     * 跳过卫星瓦片，传进来的就是锚点瓦片）。
     * <p>⚠ API 差异：原版 {@code Mathf.absin(20f, 0.1f)} 是 arc 的 {@code absin(scl, mag)}（时间取 Time.time）；
     * 本项目两参重载是 {@code (time, speed)} 语义不同 → 改写成三参 {@code (Time.time(), scl, mag)}。
     */
    @Override
    public void drawLight(Tile tile){
        if(Vars.renderer == null) return;

        Vars.renderer.lights.add(centerX(tile), centerY(tile), 30f * size, Pal.accent,
            0.5f + Mathf.absin(Time.time(), 20f, 0.1f));
    }

    /** 核心不可被手动拆除（对应原版 {@code CoreBlock.canBreak}，原版 CoreBlock.java:147-149）；判据消费点在 {@code Build.canDeconstruct}。 */
    @Override
    public boolean canBreak(com.phoenix.game.world.Tile tile){
        return false;
    }

    /**
     * 接收物品（照抄原版 {@code CoreBlock.handleItem}，原版 CoreBlock.java:194-202）：
     * 只在"服务端或单机"时真正入库；教程模式下额外派发 {@code CoreItemDeliverEvent}
     * —— 教程 {@code conveyor} 步骤的 {@code event("coreitem")} 就靠它完成（此前该步骤永不完成）。
     * <p>⚠ 网络判据：原版写 {@code net.server() || !net.active()}；本项目没有 {@code net.active()}，
     * 等价写法为 {@code Vars.isServer() || !Vars.isClient()}（{@code isServer()}=本机开服、
     * {@code isClient()}=已连远程且非主机；单机两者皆 false → 入库。纯客机由服务端同步，不入库）。
     */
    @Override
    public void handleItem(Item item, Tile tile, Tile source){
        if(Vars.isServer() || !Vars.isClient()){
            super.handleItem(item, tile, source);
            if(Vars.state != null && Vars.state.rules != null && Vars.state.rules.tutorial){
                Events.fire(new EventType.CoreItemDeliverEvent());
            }
        }
    }

    /** 核心实体：库存指向本队共享模块，使全队核心共用同一份物品。 */
    public class CoreEntity extends TileEntity{
        @Override
        public TileEntity init(com.phoenix.game.world.Tile tile, boolean update){
            super.init(tile, update);
            //核心库存 = 本队共享库存
            if(Vars.state != null && Vars.state.teams != null){
                this.items = Vars.state.teams.items(tile.getTeam());

                //原版 CoreBlock.java:92 onProximityUpdate 里 registerCore + :182 placed() 里再 registerCore。
                //⚠ 本项目必须放在 init() 里：SaveIO 读档走 world.setGenerating(true) 批量放方块 →
                //  Tile.changed 里 entity.updateProximity() 被跳过 → onProximityUpdate **不会**触发，
                //  只挂在那里的话读档后 playerCores() 为空，Logic.checkGameOver 会立刻误判失败。
                Vars.state.teams.registerCore(tile);
            }
            return this;
        }
    }

    /**
     * 核心邻接表刷新时，把紧邻的仓库链接到自己（对应原版 {@code CoreBlock.onProximityUpdate}）。
     * <p>链接后仓库收到的物品直接进核心的共享库存（容量叠加），这是原版"容器贴着核心扩容"的行为。
     */
    @Override
    public void onProximityUpdate(com.phoenix.game.world.Tile tile){
        if(!(tile.entity instanceof CoreEntity)) return;
        CoreEntity e = (CoreEntity)tile.entity;

        for(int i = 0; i < e.proximity.size; i++){
            com.phoenix.game.world.Tile other = e.proximity.get(i);
            if(other.block() instanceof StorageBlock && other.entity instanceof StorageBlock.StorageEntity){
                //关键：容器的 items **直接指向**核心的共享库存（引用共享，不是拷贝），
                //这样物品"存进容器"就等于存进核心，容量也随核心一起算（对应原版 CoreBlock.onProximityUpdate）
                other.entity.items = e.items;
                ((StorageBlock.StorageEntity)other.entity).linkedCore = tile;
            }
        }
    }

    /** 核心被拆/摧毁时解除仓库链接，否则仓库会一直往一个不存在的核心塞物品。 */
    @Override
    public void onProximityRemoved(com.phoenix.game.world.Tile tile){
        //原版 CoreBlock.java:166 removed() 里 unregisterCore
        //⚠ Tile.changed() 里 entity.removeFromProximity() 是**无条件**调用的（批量/读档路径也会走到），
        //  所以注销放这里能覆盖全部移除路径。
        if(Vars.state != null && Vars.state.teams != null){
            Vars.state.teams.unregisterCore(tile);
        }

        if(Vars.world == null) return;

        for(com.badlogic.gdx.math.GridPoint2 p : com.phoenix.game.world.Edges.getEdges(size)){
            com.phoenix.game.world.Tile other = Vars.world.ltile(tile.x + p.x, tile.y + p.y);
            if(other != null && other.entity instanceof StorageBlock.StorageEntity){
                StorageBlock.StorageEntity se = (StorageBlock.StorageEntity)other.entity;
                if(se.linkedCore == tile) se.linkedCore = null;
            }
        }
    }
}
