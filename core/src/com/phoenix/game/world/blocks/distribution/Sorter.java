package com.phoenix.game.world.blocks.distribution;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.content.Items;
import com.phoenix.game.core.Core;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 分类器。参照 Mindustry mindustry.world.blocks.distribution.Sorter 移植。
 * <p>按配置的物品分流：匹配的物品**直行**，其余走**两侧**；两侧都可用时轮流（{@code rotation} 当指针）。
 * {@code invert = true} 即 InvertedSorter（反选：不匹配的直行，匹配的走两侧）。
 * <p>不缓存物品（{@code instantTransfer}）：收到即转发，因此禁止 Sorter 接 Sorter 接 Sorter 的链式堆叠。
 */
public class Sorter extends Block{
    /** 最近一次配置的物品（新放置的分类器自动沿用，对应原版静态 lastItem）。 */
    private static Item lastItem;

    /** 反选模式（对应原版 InvertedSorter）。 */
    public boolean invert;

    public Sorter(String name){
        super(name);
        update = true;
        solid = true;
        instantTransfer = true;
        configurable = true;
        unloadable = false;
        entityType = SorterEntity::new;
    }

    @Override
    public boolean outputsItems(){
        return true;
    }

    /** 新放置时沿用上一次选的物品，省得每次都点一遍。 */
    @Override
    public void playerPlaced(Tile tile){
        if(lastItem != null){
            tile.configure(lastItem.id);
        }
    }

    /** 配置界面：物品选择表（对应原版 ItemSelection.buildTable）。 */
    @Override
    public void buildConfiguration(Tile tile, com.badlogic.gdx.scenes.scene2d.ui.Table table){
        SorterEntity e = tile.entity instanceof SorterEntity ? (SorterEntity)tile.entity : null;
        com.phoenix.game.ui.fragments.BlockConfigFragment.itemPicker(table, e == null ? null : e.sortItem,
            item -> tile.configure(item == null ? -1 : item.id));
    }

    @Override
    public void configured(Tile tile, Player player, int value){
        SorterEntity e = (SorterEntity)tile.entity;
        if(e == null) return;
        e.sortItem = value < 0 || value >= Items.all.size ? null : Items.all.get(value);
    }

    /** 在方块中心画一个配置物品的色块（对应原版 Sorter.draw 的 "center" 贴图）。 */
    @Override
    public void draw(Tile tile){
        super.draw(tile);

        SorterEntity e = (SorterEntity)tile.entity;
        if(e == null || e.sortItem == null) return;

        TextureRegion center = Core.atlas == null ? null : Core.atlas.findRegion("center");
        if(center == null) return;

        float s = tilesize * 0.45f;
        Core.batch.setColor(e.sortItem.color);
        Core.batch.draw(center, tile.worldx() + (tilesize - s) / 2f, tile.worldy() + (tilesize - s) / 2f, s, s);
        Core.batch.setColor(Color.WHITE);
    }

    @Override
    public int minimapColor(Tile tile){
        SorterEntity e = (SorterEntity)tile.entity;
        if(e == null || e.sortItem == null) return 0;
        Color c = e.sortItem.color;
        return Color.rgba8888(c.r, c.g, c.b, c.a);
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        Tile to = getTileTarget(item, tile, source, false);
        return to != null && to.block().acceptItem(item, to, tile) && to.getTeam() == tile.getTeam();
    }

    @Override
    public void handleItem(Item item, Tile tile, Tile source){
        Tile to = getTileTarget(item, tile, source, true);
        if(to != null){
            to.block().handleItem(item, to, tile);
        }
    }

    /** 目标是否也是分类器（用于禁止三连链，对应原版 Sorter.isSame）。 */
    boolean isSame(Tile tile, Tile other){
        return other != null && other.block() instanceof Sorter;
    }

    /**
     * 计算物品该去哪个方向。
     * @param dest 本分类器所在瓦片
     * @param source 来源瓦片（决定"直行"方向）
     * @param flip true 时推进两侧轮询指针
     * @return 目标瓦片；无处可去返回 null
     */
    Tile getTileTarget(Item item, Tile dest, Tile source, boolean flip){
        if(!(dest.entity instanceof SorterEntity) || source == null) return null;
        SorterEntity e = (SorterEntity)dest.entity;

        int dir = source.relativeTo(dest.x, dest.y);
        if(dir == -1) return null;

        Tile to;

        if((item == e.sortItem) != invert){
            //直行：防止分类器三连（Sorter → Sorter → Sorter）
            if(isSame(dest, source) && isSame(dest, dest.getNearbyLink(dir))){
                return null;
            }
            to = dest.getNearbyLink(dir);
        }else{
            //两侧分流
            Tile a = dest.getNearbyLink(Mathf.mod(dir - 1, 4));
            Tile b = dest.getNearbyLink(Mathf.mod(dir + 1, 4));
            boolean ac = a != null && !(a.block().instantTransfer && source.block().instantTransfer)
                && a.block().acceptItem(item, a, dest);
            boolean bc = b != null && !(b.block().instantTransfer && source.block().instantTransfer)
                && b.block().acceptItem(item, b, dest);

            if(ac && !bc){
                to = a;
            }else if(bc && !ac){
                to = b;
            }else if(!bc){
                return null;
            }else{
                //两侧都能收：用 rotation 当轮询指针交替
                if(dest.rotation() == 0){
                    to = a;
                    if(flip) dest.rotation(1);
                }else{
                    to = b;
                    if(flip) dest.rotation(0);
                }
            }
        }

        return to;
    }

    public class SorterEntity extends TileEntity{
        /** 配置的分类物品；null 表示未配置。 */
        public Item sortItem;

        @Override
        public int config(){
            return sortItem == null ? -1 : sortItem.id;
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeShort(sortItem == null ? -1 : com.phoenix.game.content.Items.all.indexOf(sortItem, true));
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            int index = in.readShort();
            sortItem = index < 0 || index >= com.phoenix.game.content.Items.all.size
                ? null : com.phoenix.game.content.Items.all.get(index);
        }
    }
}
