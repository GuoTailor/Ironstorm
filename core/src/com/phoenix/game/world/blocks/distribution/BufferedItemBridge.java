package com.phoenix.game.world.blocks.distribution;

import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.ItemBuffer;
import com.phoenix.game.world.Tile;

/**
 * 带缓冲的物品桥。参照 Mindustry mindustry.world.blocks.distribution.BufferedItemBridge 移植。
 * <p>即图集里的 {@code bridge-conveyor}。与基类的差别：物品先进 {@link ItemBuffer} 排队（有运输耗时），
 * 再按固定节奏送出，因此能吸收上游的突发流量。
 */
public class BufferedItemBridge extends ExtendingItemBridge{
    /** 缓冲出队速度（帧）。 */
    public float speed = 40f;
    /** 缓冲容量。 */
    public int bufferCapacity = 50;

    public BufferedItemBridge(String name){
        super(name);
        hasPower = false;
        hasItems = true;
        entityType = BufferedItemBridgeEntity::new;
    }

    /** 库存 → 缓冲 → 对端（对应原版 updateTransport）。 */
    @Override
    public void updateTransport(Tile tile, Tile other){
        if(!(tile.entity instanceof BufferedItemBridgeEntity)) return;
        BufferedItemBridgeEntity e = (BufferedItemBridgeEntity)tile.entity;

        //先把手里的物品塞进缓冲
        if(e.buffer.accepts() && e.items.total() > 0){
            e.buffer.accept(e.items.take());
        }

        //每 4 帧尝试送出一件
        e.acceptTimer += e.delta();
        if(e.acceptTimer < 4f) return;
        e.acceptTimer = 0f;

        Item item = e.buffer.poll();
        if(item != null && other.block().acceptItem(item, other, tile)){
            e.cycleSpeed = Mathf.lerpDelta(e.cycleSpeed, 4f, 0.05f);
            other.block().handleItem(item, other, tile);
            e.buffer.remove();
        }else{
            e.cycleSpeed = Mathf.lerpDelta(e.cycleSpeed, 0f, 0.008f);
        }
    }

    public class BufferedItemBridgeEntity extends ItemBridgeEntity{
        /** 待发送缓冲。 */
        public final ItemBuffer buffer = new ItemBuffer(bufferCapacity, speed);
        /** 发送节奏计时。 */
        public float acceptTimer;
    }
}
