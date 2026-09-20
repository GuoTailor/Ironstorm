package com.phoenix.game.world.blocks.storage;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

/**
 * 仓库。参照 Mindustry mindustry.world.blocks.storage.StorageBlock 移植。
 * <p>接收相邻建筑送来的物品并留存，**不主动外送**（要靠 {@link Unloader} 或单位取用）。
 * <p>若紧邻本队核心，核心会在 {@code onProximityUpdate} 里把 {@link StorageEntity#linkedCore} 指向自己，
 * 此后仓库收到的物品**直接进核心的共享库存**（容量叠加），而不是存在仓库自己身上。
 */
public class StorageBlock extends Block{

    public StorageBlock(String name){
        super(name);
        update = true;
        solid = true;
        destructible = true;
        health = 120;
        hasItems = true;
        itemCapacity = 200;
        entityType = StorageEntity::new;
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        StorageEntity e = (StorageEntity)tile.entity;
        if(e == null) return false;

        //已链接核心：物品直接进核心（共享库存）
        if(e.linkedCore != null && e.linkedCore.entity != null){
            return e.linkedCore.block().acceptItem(item, e.linkedCore, source);
        }

        return tile.entity.items.get(item) < getMaximumAccepted(tile, item);
    }

    /** 仓库不主动往外送物品（对应原版 {@code StorageBlock.outputsItems}）。 */
    @Override
    public boolean outputsItems(){
        return false;
    }

    /** 仓库实体：可被核心链接；物品由 {@link #handleItem} 存入。 */
    public class StorageEntity extends TileEntity{
        /** 紧邻的本队核心（由核心的 {@code onProximityUpdate} 设置）；非 null 时物品直接进核心。 */
        public Tile linkedCore;
    }
}
