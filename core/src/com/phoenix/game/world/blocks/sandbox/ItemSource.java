package com.phoenix.game.world.blocks.sandbox;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Items;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

/**
 * 物品源（沙盒）。参照 Mindustry mindustry.world.blocks.sandbox.ItemSource 移植。
 * <p>配置一种物品后，每帧往自己库存里放一个并尝试输出给邻居（邻居收不下就丢掉，不会堆积）。
 * <p>配置界面用 {@link #buildConfiguration} 里的物品选择表（见 {@link com.phoenix.game.ui.fragments.BlockConfigFragment}）。
 */
public class ItemSource extends Block{
    /** 上一次选过的物品，新放置时自动沿用（对应原版 static lastItem）。 */
    private static Item lastItem;

    public ItemSource(String name){
        super(name);
        sandboxOnly = true;
        update = true;
        solid = true;
        destructible = true;
        health = 160;
        hasItems = true;
        itemCapacity = 1;
        configurable = true;
        entityType = ItemSourceEntity::new;
    }

    @Override
    public void configured(Tile tile, Player player, int value){
        if(tile.entity instanceof ItemSourceEntity){
            ((ItemSourceEntity)tile.entity).outputItem = value < 0 || value >= Items.all.size ? null : Items.all.get(value);
            if(((ItemSourceEntity)tile.entity).outputItem != null){
                lastItem = ((ItemSourceEntity)tile.entity).outputItem;
            }
        }
    }

    /** 新放置时沿用上次选的物品。 */
    @Override
    public void playerPlaced(Tile tile){
        if(lastItem != null) tile.configure(lastItem.id);
    }

    /** 物品源不接受输入（只出不进）。 */
    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        return false;
    }

    @Override
    public void buildConfiguration(Tile tile, Table table){
        ItemSourceEntity entity = tile.entity instanceof ItemSourceEntity ? (ItemSourceEntity)tile.entity : null;
        com.phoenix.game.ui.fragments.BlockConfigFragment.itemPicker(table,
            entity == null ? null : entity.outputItem,
            item -> tile.configure(item == null ? -1 : item.id));
    }

    public class ItemSourceEntity extends TileEntity{
        /** 输出哪种物品；null 表示未配置。 */
        public Item outputItem;

        @Override
        public int config(){
            return outputItem == null ? -1 : outputItem.id;
        }

        @Override
        public void update(){
            if(outputItem == null || items == null) return;

            //放一个再尝试送出去；送不掉就丢弃（不占库存，否则会堵住）
            items.set(outputItem, 1);
            block.tryDump(tile, outputItem);
            items.set(outputItem, 0);
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeShort(outputItem == null ? -1 : com.phoenix.game.content.Items.all.indexOf(outputItem, true));
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            int index = in.readShort();
            outputItem = index < 0 || index >= com.phoenix.game.content.Items.all.size
                ? null : com.phoenix.game.content.Items.all.get(index);
        }
    }
}
