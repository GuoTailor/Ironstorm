package com.phoenix.game.world.blocks.distribution;

import com.phoenix.game.core.Time;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.DirectionalItemBuffer;
import com.phoenix.game.world.ItemBuffer;
import com.phoenix.game.world.Tile;

/**
 * 交叉器。参照 Mindustry mindustry.world.blocks.distribution.Junction 移植。
 * <p>两条互相垂直的传送带在此处"直行穿越"而不会互相串线：
 * 物品按**来源方向**排进对应的方向队列，到点后原样从对面送出（不换向）。
 * <p>目标暂时不接受时**继续等待**（不丢件）——这是它与 Router 的关键区别。
 */
public class Junction extends Block{
    /** 穿过本交叉器所需的帧数。 */
    public float speed = 26f;
    /** 每个方向的队列容量。 */
    public int capacity = 6;

    public Junction(String name){
        super(name);
        update = true;
        solid = true;
        unloadable = false;
        entityType = JunctionEntity::new;
    }

    @Override
    public boolean outputsItems(){
        return true;
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        JunctionEntity e = (JunctionEntity)tile.entity;
        if(e == null || source == null) return false;

        int relative = source.relativeTo(tile.x, tile.y);
        if(relative == -1 || !e.buffer.accepts(relative)) return false;

        //对面的目标必须存在且能收，否则不接（避免堵在自己队列里）
        Tile to = tile.getNearby(relative);
        return to != null && to.link().entity != null && to.getTeam() == tile.getTeam();
    }

    @Override
    public void handleItem(Item item, Tile tile, Tile source){
        JunctionEntity e = (JunctionEntity)tile.entity;
        if(e == null || source == null) return;

        int relative = source.relativeTo(tile.x, tile.y);
        if(relative != -1) e.buffer.accept(relative, item);
    }

    public class JunctionEntity extends TileEntity{
        /** 四方向队列（方向索引同 {@link Tile#relativeTo}）。 */
        public final DirectionalItemBuffer buffer = new DirectionalItemBuffer(capacity, speed);

        @Override
        public void update(){
            for(int i = 0; i < 4; i++){
                if(buffer.indexes[i] <= 0) continue;

                if(buffer.indexes[i] > capacity) buffer.indexes[i] = capacity;

                long l = buffer.buffers[i][0];
                float time = ItemBuffer.time(l);

                //时间未到就等下一帧（Time.time 回绕时立即放行）
                if(Time.time < time + speed && Time.time >= time) continue;

                Item item = ItemBuffer.item(ItemBuffer.itemId(l));
                Tile dest = tile.getNearby(i);
                if(dest != null) dest = dest.link();

                //目标不想要就继续等，不丢件
                if(item == null || dest == null || dest.entity == null
                    || !dest.block().acceptItem(item, dest, tile) || dest.getTeam() != tile.getTeam()){
                    continue;
                }

                dest.block().handleItem(item, dest, tile);
                System.arraycopy(buffer.buffers[i], 1, buffer.buffers[i], 0, buffer.indexes[i] - 1);
                buffer.indexes[i]--;
            }
        }
    }
}
