package com.phoenix.game.world.blocks.distribution;

import com.badlogic.gdx.utils.Array;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Edges;
import com.phoenix.game.world.Tile;

/**
 * 路由器。参照 Mindustry mindustry.world.blocks.distribution.Router 移植。
 * <p>只存 1 个物品，每帧轮询邻接表把它丢给第一个能收的邻居（{@code tile.rotation()} 当轮询指针，
 * 每丢一次指针前移，实现轮流输出）。{@link #Distributor} 只是它的 2x2 版本。
 */
public class Router extends Block{
    /** 送出间隔（帧）。 */
    public float speed = 8f;

    public Router(String name){
        super(name);
        solid = true;
        update = true;
        hasItems = true;
        itemCapacity = 1;
        unloadable = false;
        entityType = RouterEntity::new;
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        RouterEntity e = (RouterEntity)tile.entity;
        //只收一个：自己空了才能再收
        return e != null && source != null && tile.getTeam() == source.getTeam()
            && e.lastItem == null && e.items.total() == 0;
    }

    @Override
    public void handleItem(Item item, Tile tile, Tile source){
        RouterEntity e = (RouterEntity)tile.entity;
        if(e == null) return;

        e.items.add(item, 1);
        e.lastItem = item;
        e.time = 0f;
        e.lastInput = source;
    }

    @Override
    public int removeStack(Tile tile, Item item, int amount){
        RouterEntity e = (RouterEntity)tile.entity;
        int result = super.removeStack(tile, item, amount);
        if(result != 0 && e != null && item == e.lastItem){
            e.lastItem = null;
        }
        return result;
    }

    /**
     * 从邻接表里找一个能接收的目标。
     * @param set true 时把轮询指针前移（真正送出时调用一次）
     */
    public Tile getTileTarget(Tile tile, Item item, Tile from, boolean set){
        RouterEntity e = (RouterEntity)tile.entity;
        Array<Tile> proximity = e.proximity;
        int counter = tile.rotation();

        for(int i = 0; i < proximity.size; i++){
            Tile other = proximity.get((i + counter) % proximity.size);
            if(set) tile.rotation((tile.rotation() + 1) % proximity.size);
            //溢出闸门来的物品不再送回它，避免来回弹
            if(other == from && from.block() == Blocks.overflowGate) continue;
            if(other.block().acceptItem(item, other, Edges.getFacingEdge(tile, other))){
                return other;
            }
        }
        return null;
    }

    public class RouterEntity extends TileEntity{
        /** 待送出的物品（同时最多 1 个）。 */
        public Item lastItem;
        /** 上一件物品来自哪里（防止回灌）。 */
        public Tile lastInput;
        /** 送出计时。 */
        public float time;

        @Override
        public void update(){
            //物品被别的途径取走时清掉残留计数
            if(lastItem == null && items.total() > 0){
                items.clear();
            }

            if(lastItem == null) return;

            time += 1f / speed * Time.delta();
            Tile target = getTileTarget(tile, lastItem, lastInput, false);

            //目标是另一个 Router 时必须等满一格时间，避免同一帧连锁传递
            if(target != null && (time >= 1f || !(target.block() instanceof Router))){
                getTileTarget(tile, lastItem, lastInput, true);
                target.block().handleItem(lastItem, target, Edges.getFacingEdge(tile, target));
                items.remove(lastItem, 1);
                lastItem = null;
            }
        }
    }
}
