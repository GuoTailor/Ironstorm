package com.phoenix.game.world.blocks.sandbox;

import com.phoenix.game.type.Item;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

/**
 * 物品虚空（沙盒）。参照 Mindustry mindustry.world.blocks.sandbox.ItemVoid 移植。
 * <p>什么都收、收下就丢（不占库存），用来消化多余的物流产出。
 */
public class ItemVoid extends Block{
    public ItemVoid(String name){
        super(name);
        sandboxOnly = true;
        update = true;
        solid = true;
        destructible = true;
        health = 160;
        //必须给实体类型：hasEntity() 为真但 newEntity() 返回 null 的话，
        //这一格会"有方块、没实体"——血量/拆除都跟着失效
        entityType = ItemVoidEntity::new;
    }

    /** 无状态实体（虚空不需要库存，收下即丢）。 */
    public class ItemVoidEntity extends com.phoenix.game.entities.type.TileEntity{
    }

    /** 永远收得下。 */
    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        return true;
    }

    /** 收到就丢。 */
    @Override
    public void handleItem(Item item, Tile tile, Tile source){
    }
}
