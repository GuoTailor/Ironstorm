package com.phoenix.game.world.blocks.storage;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.content.Items;
import com.phoenix.game.core.Core;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 装卸器。参照 Mindustry mindustry.world.blocks.storage.Unloader 移植。
 * <p>周期性从相邻的"可卸载"存储（{@code unloadable} 且有物品）取一件，再丢给其他相邻建筑
 * （{@link #canDump} 禁止丢回存储，否则会在容器之间来回倒）。
 * <p>可配置只取某一种物品（配置后中心显示该物品颜色）。
 */
public class Unloader extends Block{
    /** 取物间隔（帧）。 */
    public float speed = 1f;

    /** 最近一次配置的物品（新放置的装卸器自动沿用）。 */
    private static Item lastItem;

    public Unloader(String name){
        super(name);
        update = true;
        solid = true;
        health = 70;
        hasItems = true;
        itemCapacity = 10;
        configurable = true;
        entityType = UnloaderEntity::new;
    }

    @Override
    public void playerPlaced(Tile tile){
        if(lastItem != null){
            tile.configure(lastItem.id);
        }
    }

    /** 配置界面：物品选择表（对应原版 ItemSelection.buildTable）。 */
    @Override
    public void buildConfiguration(Tile tile, com.badlogic.gdx.scenes.scene2d.ui.Table table){
        UnloaderEntity e = tile.entity instanceof UnloaderEntity ? (UnloaderEntity)tile.entity : null;
        com.phoenix.game.ui.fragments.BlockConfigFragment.itemPicker(table, e == null ? null : e.sortItem,
            item -> tile.configure(item == null ? -1 : item.id));
    }

    @Override
    public void configured(Tile tile, Player player, int value){
        UnloaderEntity e = (UnloaderEntity)tile.entity;
        if(e == null) return;
        e.items.clear();
        e.sortItem = value < 0 || value >= Items.all.size ? null : Items.all.get(value);
    }

    /** 禁止把物品丢回存储建筑（否则物品会在容器之间空转）。 */
    @Override
    public boolean canDump(Tile tile, Tile to, Item item){
        return !(to.block() instanceof StorageBlock);
    }

    /** 在中心画配置物品的色块（对应原版 "unloader-center" 贴图）。 */
    @Override
    public void draw(Tile tile){
        super.draw(tile);

        UnloaderEntity e = (UnloaderEntity)tile.entity;
        if(e == null || e.sortItem == null) return;

        TextureRegion center = Core.atlas == null ? null : Core.atlas.findRegion("center");
        if(center == null) return;

        float s = tilesize * 0.45f;
        Core.batch.setColor(e.sortItem.color);
        Core.batch.draw(center, tile.worldx() + (tilesize - s) / 2f, tile.worldy() + (tilesize - s) / 2f, s, s);
        Core.batch.setColor(Color.WHITE);
    }

    public class UnloaderEntity extends TileEntity{
        /** 配置只取的物品；null 表示取任意。 */
        public Item sortItem;

        @Override
        public int config(){
            return sortItem == null ? -1 : sortItem.id;
        }

        /** 取物计时（0~1，满 1 取一件）。 */
        public float unloadTimer;

        @Override
        public void update(){
            unloadTimer += delta() / Math.max(speed, 0.01f);

            if(unloadTimer >= 1f && items.total() == 0){
                unloadTimer = 0f;

                for(int i = 0; i < proximity.size; i++){
                    Tile other = proximity.get(i);
                    if(other.entity == null || other.entity.items == null) continue;
                    if(!other.interactable(tile.getTeam())) continue;
                    if(!other.block().unloadable || !other.block().hasItems) continue;

                    boolean has = sortItem == null ? other.entity.items.total() > 0 : other.entity.items.has(sortItem, 1);
                    if(!has) continue;

                    Item item;
                    if(sortItem == null){
                        item = other.entity.items.take();
                    }else{
                        other.entity.items.remove(sortItem, 1);
                        item = sortItem;
                    }
                    if(item == null) continue;

                    offloadNear(tile, item);
                    break;
                }
            }

            //手里有东西就试着丢给邻居
            if(items.total() > 0){
                tryDump(tile);
            }
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeShort(sortItem == null ? -1 : com.phoenix.game.content.Items.all.indexOf(sortItem, true));
            out.writeFloat(unloadTimer);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            int index = in.readShort();
            sortItem = index < 0 || index >= com.phoenix.game.content.Items.all.size
                ? null : com.phoenix.game.content.Items.all.get(index);
            unloadTimer = in.readFloat();
        }
    }
}
