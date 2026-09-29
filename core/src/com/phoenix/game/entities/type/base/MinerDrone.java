package com.phoenix.game.entities.type.base;

import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.entities.traits.MinerTrait;
import com.phoenix.game.entities.units.UnitState;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.util.Structs;
import com.phoenix.game.world.Build;
import com.phoenix.game.world.Pos;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.modules.ItemModule;

import java.io.*;

import static com.phoenix.game.Vars.world;

/**
 * 只采矿的无人机。参照 Mindustry mindustry.entities.type.base.MinerDrone 移植。
 * <p>默认状态 {@code mine}：找一个有矿的瓦片飞过去采；采满后切到 {@code drop} 飞到核心卸货。
 *
 * <p><b>与本项目的差异（逐条对应原版写法）</b>：
 * <ul>
 *   <li>{@code getClosestCore()}：原版返回 {@code TileEntity}，本项目 {@code BaseUnit} 返回核心**瓦片**
 *       （材料走队伍共享库存）。</li>
 *   <li>单位载物背包（{@code acceptsItem/clearItem/getItemCapacity}）与 {@code Block.acceptStack(...)}、
 *       {@code Call.transferItemTo(...)} <b>已全部就位</b>（玩家背包/单位背包批次补齐）→
 *       {@code mine} 状态的"核心满 → 清空 / 背包满 → 去卸货"与 {@code drop} 状态的"飞到核心卸货"都按原版接上。</li>
 *   <li>{@code indexer.findClosestOre}：已接 {@code Vars.indexer}（indexer 为 null 时退回就地全瓦片扫描）；
 *       {@code indexer.hasOre} **本批已接** —— 原注释称"仍未移植"已过时，{@code ai.BlockIndexer.hasOre}
 *       早已存在（{@code allOres} 集合）→ {@link #findItem()} 现按原版 {@code Structs.findMin} 选矿。</li>
 *   <li>{@code getSpawner()}：原版返回出生点实体，本项目返回 int 坐标 → 改用 {@code getClosestSpawner()}。</li>
 *   <li>{@code moveTo(float)}：已上移到 {@code FlyingUnit}（原版位置），本类不再就地复制。</li>
 *   <li>{@code drawOver()}：{@code BaseUnit} 已有该绘制钩子（原注释称"没有"已过时），本批已加
 *       {@code @Override} 并在 {@code Renderer.drawUnits()} 追加的 drawOver 通道里被调用（画采矿光束）。</li>
 * </ul>
 */
public class MinerDrone extends BaseDrone implements MinerTrait{
    protected Item targetItem;
    //mineTile / getMineTile / setMineTile / getMinePower / canMine / offloadImmediately 现在继承自
    //BaseUnit（BaseUnit implements MinerTrait，供玩家单位复用）→ 本类不再重复声明

    public final UnitState

    mine = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            //TODO 原版 getClosestCore() 返回 TileEntity；本项目返回核心瓦片（材料走队伍共享库存）
            Tile core = getClosestCore();

            if(core == null) return;

            findItem();

            //核心已装满该物品 → 清空背包原地等待（对应原版 `core full of the target item, do nothing`）
            if(targetItem != null && core.entity != null
                && core.block().acceptStack(targetItem, 1, core, MinerDrone.this) == 0){
                MinerDrone.this.clearItem();
                return;
            }

            //背包满 或 已不再接受该物品 → 切去卸货（对应原版 `if inventory is full, drop it off.`）
            if(itemAmount >= getItemCapacity() || (targetItem != null && !acceptsItem(targetItem))){
                setState(drop);
            }else{
                if(retarget() && targetItem != null){
                    //原版 indexer.findClosestOre(x, y, targetItem)
                    target = findClosestOre(x, y, targetItem);
                }

                if(target instanceof Tile){
                    moveTo(type.range / 1.5f);

                    if(dst(target) < type.range && mineTile != target){
                        setMineTile((Tile)target);
                    }

                    if(((Tile)target).block() != Blocks.air){
                        setState(drop);
                    }
                }else{
                    //nothing to mine anymore, core full: circle spawnpoint
                    //TODO 原版 getSpawner()（返回自己的出生点实体）；本项目 getSpawner() 返回 int 坐标
                    //     → 改用 getClosestSpawner()（最近出生点瓦片），与 RepairDrone 的处理一致
                    Tile spawner = getClosestSpawner();
                    if(spawner != null){
                        target = spawner;
                        circle(40f);
                        target = null;
                    }
                }
            }
        }

        public void exited(){
            setMineTile(null);
        }
    },

    drop = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            //背包空 或 装的是非材料物品 → 清空并回去采矿（对应原版
            //`if(item.amount == 0 || item.item.type != ItemType.material){ clearItem(); setState(mine); return; }`）
            if(itemAmount == 0 || item == null || item.type != com.phoenix.game.type.ItemType.material){
                clearItem();
                setState(mine);
                return;
            }

            target = getClosestCore();

            if(target == null) return;

            Tile core = (Tile)target;

            if(dst(target) < type.range){
                if(core.entity != null && core.block().acceptStack(item, itemAmount, core, MinerDrone.this) > 0){
                    //原版 Call.transferItemTo(item.item, item.amount, x, y, tile.tile)（服务端执行）；
                    //本项目 transferItemTo 直接入库 + 播飞行动画
                    com.phoenix.game.entities.effect.ItemTransfer.transferItemTo(item, itemAmount, x, y, core);
                }

                clearItem();
                setState(mine);
            }

            circle(type.range / 1.8f);
        }
    };

    @Override
    public UnitState getStartState(){
        return mine;
    }

    @Override
    public void update(){
        super.update();

        updateMining();
    }

    @Override
    protected void updateRotation(){
        if(mineTile != null && shouldRotate() && mineTile.dst(this) < type.range){
            rotation = Mathf.slerpDelta(rotation, angleTo(mineTile), 0.3f);
        }else{
            rotation = Mathf.slerpDelta(rotation, velocity.angle(), 0.3f);
        }
    }

    @Override
    public boolean shouldRotate(){
        return isMining();
    }

    /** 绘制采矿光束（对应原版 {@code Unit.drawOver()}；由 {@code Renderer.drawUnits()} 的 drawOver 通道调用）。 */
    @Override
    public void drawOver(){
        drawMining();
    }

    @Override
    public void write(DataOutput data) throws IOException{
        super.write(data);
        data.writeInt(mineTile == null || !state.is(mine) ? Pos.invalid : mineTile.pos());
    }

    @Override
    public void read(DataInput data) throws IOException{
        super.read(data);
        mineTile = world.tile(data.readInt());
    }

    protected void findItem(){
        Tile core = getClosestCore();
        if(core == null){
            return;
        }

        //原版：
        //  targetItem = Structs.findMin(type.toMine, indexer::hasOre,
        //      (a, b) -> -Integer.compare(entity.items.get(a), entity.items.get(b)));
        //本项目核心库存走队伍共享库存（Build.items(team)），indexer.hasOre 已存在（ai.BlockIndexer.allOres）。
        //⚠ 原版比较器语义 = 取"核心库存最多"的那种可采矿（findMin 配负比较），这里照抄。
        ItemModule inventory = Build.items(team);

        if(Vars.indexer != null){
            targetItem = Structs.findMin(type.toMine,
                item -> Vars.indexer.hasOre(item),
                (a, b) -> inventory == null ? 0 : -Integer.compare(inventory.get(a), inventory.get(b)));
        }else{
            //indexer 未就绪（headless 等）→ 退回"地图上第一种可采的矿"。
            //必须按 toMine 过滤：否则会盯上钛/沙这类本型号采不了的矿，
            //而 updateMining() 的 !canMine(...) 会立刻把 mineTile 清掉 → 反复空转。
            targetItem = findFirstOre();
        }
    }

    /** @return 地图上第一种**本型号可采**（{@code type.toMine}）的矿脉物品；没有则 null（indexer 未就绪时的回退）。 */
    private Item findFirstOre(){
        if(world == null) return null;

        for(int i = 0; i < world.tiles.length; i++){
            Tile tile = world.tiles[i];
            if(tile != null && tile.drop() != null && canMine(tile.drop())) return tile.drop();
        }
        return null;
    }

    /**
     * 找离 (xp,yp) 最近的、矿物为 {@code item} 的瓦片（对应原版 {@code BlockIndexer.findClosestOre}）。
     * <p>{@code Vars.indexer} 就绪时直接走矿脉象限索引（原版路径）；为 null 时（headless 未接线等）
     * 退回全瓦片扫描 —— 调用点已被 {@code retarget()} 限制到每 20 帧一次。
     */
    private static Tile findClosestOre(float xp, float yp, Item item){
        if(Vars.indexer != null){
            return Vars.indexer.findClosestOre(xp, yp, item);
        }

        if(world == null) return null;

        Tile best = null;
        float bestDst = Float.POSITIVE_INFINITY;

        for(int i = 0; i < world.tiles.length; i++){
            Tile tile = world.tiles[i];
            if(tile == null || tile.drop() != item) continue;

            float dst = Mathf.dst2(xp, yp, tile.worldx(), tile.worldy());
            if(dst < bestDst){
                bestDst = dst;
                best = tile;
            }
        }

        return best;
    }
}
