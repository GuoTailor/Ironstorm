package com.phoenix.game.world.blocks.distribution;

import com.phoenix.game.core.Time;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Edges;
import com.phoenix.game.world.Tile;

/**
 * 溢流闸门。参照 Mindustry mindustry.world.blocks.distribution.OverflowGate 移植。
 * <p>默认（{@code invert = false}）：**正前方优先**，正前方收不下才走两侧（多余的溢流出去）。
 * {@code invert = true} 即 UnderflowGate：**两侧优先**，都不行才走正前方。
 * <p>同样禁止闸门串闸门（否则物品会在闸门之间来回弹）。
 */
public class OverflowGate extends Block{
    /** 送出间隔（帧）。 */
    public float speed = 1f;
    /** 反转优先级（UnderflowGate）。 */
    public boolean invert;

    public OverflowGate(String name){
        super(name);
        hasItems = true;
        solid = true;
        update = true;
        unloadable = false;
        entityType = OverflowGateEntity::new;
    }

    @Override
    public boolean outputsItems(){
        return true;
    }

    @Override
    public int removeStack(Tile tile, Item item, int amount){
        OverflowGateEntity e = (OverflowGateEntity)tile.entity;
        int result = super.removeStack(tile, item, amount);
        if(result != 0 && e != null && item == e.lastItem){
            e.lastItem = null;
        }
        return result;
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        OverflowGateEntity e = (OverflowGateEntity)tile.entity;
        return e != null && source != null && tile.getTeam() == source.getTeam()
            && e.lastItem == null && e.items.total() == 0;
    }

    @Override
    public void handleItem(Item item, Tile tile, Tile source){
        OverflowGateEntity e = (OverflowGateEntity)tile.entity;
        if(e == null) return;

        e.items.add(item, 1);
        e.lastItem = item;
        e.time = 0f;
        e.lastInput = source;

        //收到就立刻尝试送出，减少一帧延迟（对应原版 handleItem 里直接调 update）
        e.update();
    }

    /**
     * 计算输出目标。
     * @param src 来源瓦片（决定"正前方"是哪个方向）
     * @param flip true 时推进两侧轮询指针
     */
    public Tile getTileTarget(Tile tile, Item item, Tile src, boolean flip){
        if(src == null) return null;

        int from = tile.relativeTo(src.x, src.y);
        if(from == -1) return null;

        Tile to = tile.getNearbyLink((from + 2) % 4);
        if(to == null) return null;

        Tile edge = Edges.getFacingEdge(tile, to);
        boolean canForward = to.block().acceptItem(item, to, edge) && to.getTeam() == tile.getTeam()
            && !(to.block() instanceof OverflowGate);

        if(!canForward || invert){
            Tile a = tile.getNearbyLink(Mathf.mod(from - 1, 4));
            Tile b = tile.getNearbyLink(Mathf.mod(from + 1, 4));
            boolean ac = a != null && a.block().acceptItem(item, a, edge)
                && !(a.block() instanceof OverflowGate) && a.getTeam() == tile.getTeam();
            boolean bc = b != null && b.block().acceptItem(item, b, edge)
                && !(b.block() instanceof OverflowGate) && b.getTeam() == tile.getTeam();

            if(!ac && !bc){
                //两侧都堵：UnderflowGate 可以退回正前方，OverflowGate 只能等
                return invert && canForward ? to : null;
            }

            if(ac && !bc){
                to = a;
            }else if(bc && !ac){
                to = b;
            }else{
                //两侧都能收：用 rotation 当轮询指针交替
                if(tile.rotation() == 0){
                    to = a;
                    if(flip) tile.rotation(1);
                }else{
                    to = b;
                    if(flip) tile.rotation(0);
                }
            }
        }

        return to;
    }

    public class OverflowGateEntity extends TileEntity{
        public Item lastItem;
        public Tile lastInput;
        public float time;

        @Override
        public void update(){
            if(lastItem == null && items.total() > 0){
                items.clear();
            }

            if(lastItem == null) return;

            if(lastInput == null){
                lastItem = null;
                return;
            }

            time += 1f / speed * Time.delta();
            Tile target = getTileTarget(tile, lastItem, lastInput, false);

            if(target != null && time >= 1f){
                getTileTarget(tile, lastItem, lastInput, true);
                target.block().handleItem(lastItem, target, Edges.getFacingEdge(tile, target));
                items.remove(lastItem, 1);
                lastItem = null;
            }
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            time = 0f;
            //物品由基类从 items 恢复，这里捡回 lastItem，避免下一帧的残留清理把它丢掉
            lastItem = items == null ? null : items.first();
        }

        @Override
        public void afterRead(){
            //lastInput 是瓦片引用、不入档：邻接表重建后挑一个邻居当输入方向
            if(lastItem != null && lastInput == null && proximity.size > 0){
                lastInput = proximity.first();
            }
        }
    }
}
