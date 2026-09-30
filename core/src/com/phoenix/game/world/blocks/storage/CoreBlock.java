package com.phoenix.game.world.blocks.storage;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Fx;
import com.phoenix.game.content.Mechs;
import com.phoenix.game.content.UnitTypes;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Lines;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.traits.SpawnerTrait;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.game.EventType;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.Mech;
import com.phoenix.game.type.UnitType;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.RespawnBlock;
import com.phoenix.game.world.meta.BlockFlag;
import io.anuke.mindustry.gen.Sounds;

import java.util.EnumSet;

import static com.phoenix.game.Vars.tilesize;

/**
 * 核心建筑。参照 Mindustry mindustry.world.blocks.storage.CoreBlock 移植。
 * <p>作为双方可被摧毁的寻路目标，并接收物品：核心库存即本队共享库存（原版语义）。
 * <p>核心列表（{@code Teams.cores/playerCores}）已接线（S3 批次）：{@code CoreEntity.init()} 注册、
 * {@code onProximityRemoved()} 注销（对应原版 {@code CoreBlock.placed()/removed()}）。
 * <p>重生链已接线（M8/D5 批次）：玩家死亡后由最近的核心（{@link CoreEntity implements SpawnerTrait}）
 * 推进重生进度（{@code progress}）与动画热度（{@code heat}），进度满时 {@link #onUnitRespawn} 在核心中心
 * 把玩家重新生成并给一个向上的冲量（对应原版 {@code CoreBlock.update/drawLayer/onUnitRespawn}）。
 * <p><b>刻意降级</b>：原版 {@code activeSound = Sounds.respawning}（工作音效）本项目没有工作音效系统
 * （{@code Block} 无 {@code activeSound} 字段、无播放路径）→ {@link #shouldActiveSound} 仅保留语义、无人调用；
 * 原版 {@code layer = Layer.overlay} 本项目 {@code Block} 无 {@code layer} 字段 → 重生动画由
 * {@code Renderer} 在 {@code block.draw(tile)} 之后紧跟的 {@link #drawLayer} 里绘制（效果等价）。
 */
public class CoreBlock extends Block{
    /** 选中框贴图（图集里的 block-select，四角各画一块）。 */
    public TextureRegion selectRegion;

    /**
     * 玩家在该核心重生时使用的机甲（对应原版 {@code CoreBlock.mech = Mechs.starter}）。
     * <p>本项目没有机甲装备系统，{@code mech} 只用于重生动画里的机甲剪影
     * （{@link RespawnBlock#drawRespawn} 的 {@code to.icon(Cicon.full)}）；真正的重生机型由
     * {@link #respawnType()} 按同名 {@link UnitType} 解析（见 {@code Mechs} 类注释的降级说明）。
     * <p>⚠ 字段初始化取 {@code Mechs.starter} 是安全的：本项目内容加载顺序为
     * {@code ... Mechs → UnitTypes → Blocks}（见 {@code ContentLoader.lists()}），与原版一致，
     * 所以构造本类时 {@code Mechs.starter} 已就绪。
     */
    public Mech mech = Mechs.starter;

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
     * @return 在该核心重生时使用的单位类型。
     * <p>原版 {@code CoreEntity.updateSpawning} 里做的是 {@code player.mech = mech}（把玩家的机甲换成核心的）；
     * 本项目没有机甲装备系统、重生机型落在 {@link Player#selectedType}（见 {@code Player} 类注释）→
     * 这里按 {@link #mech} 的**同名** {@link UnitType} 解析（{@code Mechs.starter = dart-ship} ↔
     * {@code UnitTypes.dartShip}，两边名字一致，见 {@code Mechs.dart} 的构造名）。
     * 解析不到时退回 {@code UnitTypes.dartShip}（原版出生机甲）。
     */
    public UnitType respawnType(){
        if(mech != null){
            for(UnitType type : UnitTypes.all){
                if(type.name.equals(mech.name)) return type;
            }
        }
        return UnitTypes.dartShip;
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

    /**
     * 重生动画（对应原版 {@code CoreBlock.drawLayer}，原版 CoreBlock.java:186-192）：
     * {@code heat > 0.001} 时在核心上画"四角光环 + 进度条 + 机甲剪影"。
     * <p>⚠ 本项目 {@code Block} 没有 {@code layer} 字段（原版是 {@code Layer.overlay}），但
     * {@code Renderer} 会在 {@code block.draw(tile)} 之后紧跟调用 {@code drawLayer(tile)}，
     * 所以重生动画照样画在方块本体之上（效果等价）。
     */
    @Override
    public void drawLayer(Tile tile){
        if(!(tile.entity instanceof CoreEntity)) return;
        CoreEntity entity = (CoreEntity)tile.entity;

        if(entity.heat > 0.001f){
            RespawnBlock.drawRespawn(tile, entity.heat, entity.progress, entity.time, entity.spawnPlayer, mech);
        }
    }

    /**
     * @return 是否应播放"正在重生"的工作音效（对应原版 {@code CoreBlock.shouldActiveSound}）。
     * <p>⚠ <b>刻意降级</b>：本项目没有工作音效系统（{@code Block} 无 {@code activeSound} 字段、无播放路径）
     * → 本方法无人调用，仅按原版保留语义（同 {@code LaserTurret.shouldActiveSound} 的处置）。
     */
    public boolean shouldActiveSound(Tile tile){
        return tile.entity instanceof CoreEntity && ((CoreEntity)tile.entity).spawnPlayer != null;
    }

    /**
     * 重生完成（对应原版 {@code CoreBlock.onUnitRespawn}，原版 CoreBlock.java:42-53）：
     * 在核心处放 {@code Fx.spawn}、把玩家落地、给一个向上的冲量，然后清空核心的 {@code spawnPlayer}。
     * <p>⚠ 原版是 {@code @Remote(called = Loc.server)} 的 RPC；本项目没有 RPC 层 → 由
     * {@link CoreEntity#update()} 在服务端/单机直接调用（{@code Call.onUnitRespawn} 的等价写法）。
     * <p>⚠ 原版 {@code player.applyImpulse(0, 8f)} 里 Player 本身就是 Unit；本项目 Player 只是
     * "被控单位的持有者" → 冲量落到 {@code player.unit()}（见 {@code Player} 类注释）。
     */
    public static void onUnitRespawn(Tile tile, Player player){
        if(player == null || !(tile.entity instanceof CoreEntity)) return;
        CoreEntity entity = (CoreEntity)tile.entity;

        Effects.effect(Fx.spawn, entity);
        entity.progress = 0f;
        entity.spawnPlayer = player;
        entity.spawnPlayer.onRespawn(tile);
        if(entity.spawnPlayer.unit() != null){
            entity.spawnPlayer.unit().applyImpulse(0f, 8f);
        }
        entity.spawnPlayer = null;
    }

    /** 核心实体：库存指向本队共享模块，使全队核心共用同一份物品；同时作为玩家的重生点（{@link SpawnerTrait}）。 */
    public class CoreEntity extends TileEntity implements SpawnerTrait{
        //⚠ 原版这 4 个字段是 protected；本项目沿用 MechPadEntity 的既有口径改为 public
        //  （{@code MechPadEntity.player/progress/heat} 就是 public），便于调试与外部探针读取。
        /** 正在等待重生的玩家；null 表示空闲（对应原版 {@code CoreEntity.spawnPlayer}）。 */
        public Player spawnPlayer;
        /** 重生进度 0~1（对应原版 {@code CoreEntity.progress}）。 */
        public float progress;
        /** 重生动画计时（对应原版 {@code CoreEntity.time}）。 */
        public float time;
        /** 重生热度 0~1（对应原版 {@code CoreEntity.heat}，驱动 {@link RespawnBlock#drawRespawn}）。 */
        public float heat;

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

        /** @return 本实体所在瓦片（对应原版 {@code SpawnerTrait.getTile()}）。 */
        @Override
        public Tile getTile(){
            return tile;
        }

        /**
         * @return 指定单位是否属于本核心正在重生的那个玩家（对应原版 {@code CoreEntity.hasUnit}）。
         * <p>⚠ 原版 Player 本身就是 Unit，所以原式是 {@code unit == spawnPlayer}；本项目 Player 只是
         * "被控单位的持有者" → 改成比较玩家当前控制的单位（{@code spawnPlayer.unit()}）。
         * 该方法当前无调用方（原版用于网络序列化 {@code spawner.hasUnit(this)}），仅保留语义。
         */
        @Override
        public boolean hasUnit(BaseUnit unit){
            return spawnPlayer != null && spawnPlayer.unit() == unit;
        }

        /**
         * 玩家请求在此核心重生（对应原版 {@code CoreEntity.updateSpawning}，原版 CoreBlock.java:246-254）：
         * 空闲且服务端不在等待玩家时接管该玩家、把进度清零、把他的重生机型换成核心的 mech，
         * 再让他进入"开始重生"流程。
         */
        @Override
        public void updateSpawning(Player player){
            if((Vars.netServer == null || !Vars.netServer.isWaitingForPlayers()) && spawnPlayer == null){
                spawnPlayer = player;
                progress = 0f;
                //原版 player.mech = mech：本项目没有机甲装备系统 → 落到 selectedType（见 respawnType()）
                player.selectedType = respawnType();
                player.beginRespawning(this);
            }
        }

        /**
         * 每帧推进重生（对应原版 {@code CoreBlock.update(Tile)}，原版 CoreBlock.java:204-225）。
         * <p>⚠ 本项目 {@code Block.update} 是 boolean 字段、不是方法（见其注释）→ 每帧钩子是
         * {@link TileEntity#update()}（由 {@code World.updateTiles} 调用，受 {@code block.update} 门控）。
         * <p>⚠ 坐标口径：原版 {@code tile.drawx()/drawy()} 是方块中心 → 用 {@code block.centerX/centerY}。
         * <p>⚠ 原版判据含 {@code !spawnPlayer.isAdded()}（玩家已被移出实体组也要停）；本项目
         * {@code Player.isDead()} 已覆盖"单位已死/已移除"，这里只用它。
         */
        @Override
        public void update(){
            if(spawnPlayer != null){
                if(!spawnPlayer.isDead()){
                    spawnPlayer = null;
                    return;
                }

                //等待重生期间把玩家钉在核心中心（原版 spawnPlayer.set(tile.drawx(), tile.drawy())）
                spawnPlayer.setX(block.centerX(tile));
                spawnPlayer.setY(block.centerY(tile));
                heat = Mathf.lerpDelta(heat, 1f, 0.1f * delta());
                time += delta();
                progress += 1f / Vars.state.rules.respawnTime * delta();

                if(progress >= 1f){
                    onUnitRespawn(tile, spawnPlayer);
                }
            }else{
                heat = Mathf.lerpDelta(heat, 0f, 0.1f * delta());
            }
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
