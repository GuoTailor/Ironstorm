package com.phoenix.game.world.consumers;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.core.Scl;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.func.Boolf;
import com.phoenix.game.type.Item;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.ui.ItemImage;
import com.phoenix.game.ui.MultiReqImage;
import com.phoenix.game.ui.ReqImage;
import com.phoenix.game.util.ArcAnnotate.*;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.BlockStats;
import com.phoenix.game.world.meta.values.ItemFilterValue;

import static com.phoenix.game.Vars.content;
import static com.phoenix.game.Vars.data;
import static com.phoenix.game.Vars.world;

/**
 * "满足条件的物品"消耗器。参照 Mindustry mindustry.world.consumers.ConsumeItemFilter 移植。
 * <p>与 {@link ConsumeItems} 的区别：不写死物品清单，而是给一个 {@link Boolf} 过滤条件
 * （如"任何可燃物"），运行时从全部物品里挑第一个满足条件的扣掉。
 * <p>说明：{@code ItemFilterValue}（统计显示）、{@code MultiReqImage}/{@code ReqImage}（配置界面）、
 * {@code world.isZone()}、{@code data.isUnlocked}（战役解锁，S4a 批）都已存在；{@code build} 已按原版补全
 * （S4b 批），由 {@code Block.displayConsumption} → {@code PlacementFragment.addTileDetail} 在建筑详情面板里调用。
 * <p>注意：{@code Consume.build(Tile, Table)} / {@code Consume.getIcon()} 钩子已补齐（见 {@link Consume}）。
 */
public class ConsumeItemFilter extends Consume{
    public final @NonNull Boolf<Item> filter;

    public ConsumeItemFilter(Boolf<Item> filter){
        this.filter = filter;
    }

    /** 把满足条件的物品登记进"可接受物品"位图（{@code Consume.applyItemFilter} 钩子，见 {@link Consume}）。 */
    public void applyItemFilter(com.badlogic.gdx.utils.Bits arr){
        //content.items() 是 libgdx 的 Array（没有 each(Boolf, Cons)）→ 包一层 struct.Array
        new com.phoenix.game.struct.Array<Item>(content.items()).each(filter, item -> arr.set(item.id));
    }

    @Override
    public ConsumeType type(){
        return ConsumeType.item;
    }

    /** 建筑详情面板里的"可接受物品"图标行（对应原版 {@code build}；由 Block.displayConsumption 调用）。 */
    public void build(Tile tile, Table table){
        //原版 world/consumers/ConsumeItemFilter.java:36-41
        MultiReqImage image = new MultiReqImage();
        //content.items() 是 libgdx 的 Array（没有 each(Boolf, Cons)）→ 包一层 struct.Array（同 applyItemFilter）
        new com.phoenix.game.struct.Array<Item>(content.items()).each(
            i -> filter.get(i) && (!world.isZone() || data.isUnlocked(i)),
            item -> image.add(new ReqImage(new ItemImage(item.icon(Cicon.medium), 1),
                //原版写 tile.entity.items.has(item)（本项目 ItemModule 只有 has(item, amount)）
                () -> tile.entity != null && tile.entity.items != null && tile.entity.items.has(item, 1))));

        table.add(image).size(Scl.scl(8 * 4));
    }

    /** @return 配置界面用的图标名（对应原版 {@code Consume.getIcon}）。 */
    public String getIcon(){
        return "icon-item";
    }

    @Override
    public void update(TileEntity entity){

    }

    /** 扣掉第一个满足条件的物品（每次 1 个）。 */
    @Override
    public void trigger(TileEntity entity){
        for(int i = 0; i < content.items().size; i++){
            Item item = content.item(i);
            //原版写 entity.items.has(item)（ItemModule 只有 has(item, amount)）
            if(entity.items != null && entity.items.get(item) > 0 && this.filter.get(item)){
                entity.items.remove(item, 1);
                break;
            }
        }
    }

    /** @return 库存里是否有任意满足条件的物品。 */
    @Override
    public boolean valid(TileEntity entity){
        for(int i = 0; i < content.items().size; i++){
            Item item = content.item(i);
            if(entity.items != null && entity.items.get(item) > 0 && this.filter.get(item)){
                return true;
            }
        }
        return false;
    }

    @Override
    public void display(BlockStats stats){
        stats.add(booster ? BlockStat.booster : BlockStat.input, new ItemFilterValue(filter));
    }
}
