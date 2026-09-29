package com.phoenix.game.world.consumers;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Bits;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.func.Boolf;
import com.phoenix.game.struct.Array;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.ui.MultiReqImage;
import com.phoenix.game.ui.ReqImage;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.BlockStats;
import com.phoenix.game.world.meta.values.LiquidFilterValue;

import static com.phoenix.game.Vars.content;

/**
 * 按条件筛选的液体消耗器。参照 Mindustry mindustry.world.consumers.ConsumeLiquidFilter 移植。
 * <p>与 {@link ConsumeLiquid} 的区别：不锁定单一液体，而是按 {@link #filter} 筛选，
 * 消耗的是 {@code entity.liquids.current()}（原版语义："最后一次收到的液体"）。
 * <p>⚠ {@code LiquidModule} 现在与原版一致是**按 liquid.id 索引的多槽**（可同时装多种液体），
 * 但本消耗器仍只认 {@code current()} 那一种 —— 与原版一致，别改成"遍历所有液体"。
 * <p>刻意偏离：原版 {@code build(Tile, Table)} 用 {@code ReqImage + liquid.icon(Cicon.medium)}；
 * 本项目 {@code Liquid} 继承的是 {@code MappableContent}（没有 {@code icon(Cicon)}，见 {@code 转换.md}
 * 的继承链结论），退化成按 {@code liquid-<name>} 直接查图集（与 {@code ui/LiquidDisplay} 的做法一致）。
 */
public class ConsumeLiquidFilter extends ConsumeLiquidBase{
    public final Boolf<Liquid> filter;

    public ConsumeLiquidFilter(Boolf<Liquid> liquid, float amount){
        super(amount);
        this.filter = liquid;
    }

    @Override
    public void applyLiquidFilter(Bits arr){
        //content.liquids() 是 libgdx 的 Array（没有 each(Boolf, Cons)）→ 包一层 struct.Array
        new Array<Liquid>(content.liquids()).each(filter, item -> arr.set(item.id));
    }

    @Override
    public void build(Tile tile, Table table){
        //libgdx 的 Array 也有 select(Predicate)（返回 Iterable），传 lambda 会与 struct.Array.select(Boolf) 撞重载
        //→ 先用 Boolf 变量固定住重载
        Boolf<Liquid> valid = l -> !l.isHidden() && filter.get(l);
        Array<Liquid> list = new Array<Liquid>(content.liquids()).select(valid);
        MultiReqImage image = new MultiReqImage();
        list.each(liquid -> image.add(new ReqImage(liquidIcon(liquid),
            () -> tile.entity != null && tile.entity.liquids != null && tile.entity.liquids.get(liquid) >= use(tile.entity))));

        table.add(image).size(Scl.scl(8 * 4));
    }

    @Override
    public String getIcon(){
        return "icon-liquid-consume";
    }

    @Override
    public void update(TileEntity entity){
        //守卫只防"方块没有液体模块"；LiquidModule.current() 现已与原版一致（初值 content.liquid(0)，永不 null）
        if(entity == null || entity.liquids == null) return;
        entity.liquids.remove(entity.liquids.current(), use(entity));
    }

    @Override
    public boolean valid(TileEntity entity){
        //守卫只防"方块没有液体模块"；current() 永不 null，可以照原版直接喂给 filter
        if(entity == null || entity.liquids == null) return false;
        return filter.get(entity.liquids.current()) && entity.liquids.currentAmount() >= use(entity);
    }

    @Override
    public void display(BlockStats stats){
        stats.add(booster ? BlockStat.booster : BlockStat.input, new LiquidFilterValue(filter, amount * timePeriod, timePeriod == 60f));
    }

    /**
     * @return 液体图标区域。
     * <p>刻意降级：原版是 {@code liquid.icon(Cicon.medium)}（由 {@code UnlockableContent} 提供）；
     * 本项目 {@code Liquid} 继承 {@code MappableContent}，没有该方法 → 退化成按 {@code liquid-<name>} 查图集。
     */
    private static TextureRegion liquidIcon(Liquid liquid){
        return Core.atlas == null ? null : Core.atlas.findRegion("liquid-" + liquid.name);
    }
}
