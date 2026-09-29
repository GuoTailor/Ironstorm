package com.phoenix.game.world.meta;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.core.Scl;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.meta.values.BooleanValue;
import com.phoenix.game.world.meta.values.FloorValue;
import com.phoenix.game.world.meta.values.ItemListValue;
import com.phoenix.game.world.meta.values.NumberValue;
import com.phoenix.game.world.meta.values.StringValue;

/**
 * 一个方块的统计项集合（对应原版 mindustry.world.meta.BlockStats）。
 * <p>原版用 {@code OrderedMap<StatCategory, OrderedMap<BlockStat, Array<StatValue>>>} 存，
 * 靠 {@code toMap()} 按枚举 ordinal 排序；本项目没有 OrderedMap，改成
 * "按 {@link BlockStat#ordinal()} 下标索引的数组" —— 显示时按 StatCategory × BlockStat
 * 的枚举顺序双层遍历，结果与原版排序后完全一致，还省掉了 map 与排序。
 */
public class BlockStats{
    /** 下标 = BlockStat.ordinal()；null 表示该项没登记。 */
    @SuppressWarnings("unchecked")
    private final Array<StatValue>[] values = new Array[BlockStat.values().length];

    // ---- 登记 ----

    public void add(BlockStat stat, StatValue value){
        int index = stat.ordinal();
        if(values[index] == null) values[index] = new Array<>();
        values[index].add(value);
    }

    /** 一个浮点数（带单位，按数值自动保留 0~2 位小数）。 */
    public void add(BlockStat stat, float value, StatUnit unit){
        add(stat, new NumberValue(value, unit));
    }

    /** 是/否。 */
    public void add(BlockStat stat, boolean value){
        add(stat, new BooleanValue(value));
    }

    /** 单个物品（数量 1）。 */
    public void add(BlockStat stat, Item item){
        add(stat, new ItemListValue(new ItemStack(item, 1)));
    }

    /** 一组物品。 */
    public void add(BlockStat stat, ItemStack stack){
        add(stat, new ItemListValue(stack));
    }

    /** 一组物品（数组形式，如方块的 requirements）。 */
    public void add(BlockStat stat, ItemStack[] stacks){
        add(stat, new ItemListValue(stacks));
    }

    /** 一种液体 + 数量（对应原版 {@code BlockStats.add(BlockStat, Liquid, float, boolean)}）。 */
    public void add(BlockStat stat, com.phoenix.game.type.Liquid liquid, float amount, boolean perSecond){
        add(stat, new com.phoenix.game.world.meta.values.LiquidValue(liquid, amount, perSecond));
    }

    /**
     * 把"含有该地形属性的地板"逐个列出来（对应原版 {@code BlockStats.add(BlockStat, Attribute)}）。
     * <p>原版遍历 {@code Vars.content.blocks()} 取 {@code block.isFloor() && block.asFloor().attributes.get(attr) != 0}；
     * 本项目 {@code Block} 没有 isFloor()/asFloor()，改用 {@code instanceof Floor} + {@code Blocks.all}
     * （内容加载器里 blocks 列表的同一个实例，语义等价）。
     */
    public void add(BlockStat stat, Attribute attr){
        if(com.phoenix.game.content.Blocks.all == null) return;

        for(int i = 0; i < com.phoenix.game.content.Blocks.all.size; i++){
            com.phoenix.game.world.Block block = com.phoenix.game.content.Blocks.all.get(i);
            if(!(block instanceof com.phoenix.game.world.Floor)) continue;

            com.phoenix.game.world.Floor floor = (com.phoenix.game.world.Floor)block;
            if(Math.abs(floor.attributes.get(attr)) < 0.0001f) continue;

            add(stat, new FloorValue(floor));
        }
    }

    /** 格式化字符串（{@code {0}}、{@code {1}} 占位）。 */
    public void add(BlockStat stat, String format, Object... args){
        add(stat, new StringValue(format, args));
    }

    /**
     * 去掉某一项（对应原版 {@code BlockStats.remove}）。
     * <p>原版在项不存在时抛异常；这里静默处理 —— 子类 remove 的是基类"按条件才加"的项，
     * 条件不成立时不该把游戏炸掉。
     */
    public void remove(BlockStat stat){
        values[stat.ordinal()] = null;
    }

    public void clear(){
        java.util.Arrays.fill(values, null);
    }

    public boolean has(BlockStat stat){
        Array<StatValue> list = values[stat.ordinal()];
        return list != null && list.size > 0;
    }

    // ---- 显示 ----

    /**
     * 按分类分组把统计项加进表格（对应原版 {@code ContentDisplay.displayBlock} 的双层遍历）。
     * <p>每个有内容的分类先加一行分类标题（accent 色），再逐项加"名称: 值"。
     */
    public void display(Table table){
        for(StatCategory category : StatCategory.values()){
            boolean headerAdded = false;

            for(BlockStat stat : BlockStat.values()){
                if(stat.category != category) continue;

                Array<StatValue> list = values[stat.ordinal()];
                if(list == null || list.size == 0) continue;

                if(!headerAdded){
                    table.add(new Label("[accent]" + category.localized(), Styles.defaultLabel))
                        .left().padTop(Scl.scl(6f)).row();
                    headerAdded = true;
                }

                Table line = new Table();
                line.left();
                line.add(new Label("[LIGHT_GRAY]" + stat.localized() + ":[] ", Styles.defaultLabel)).left();

                Table inset = new Table();
                inset.left();
                for(int i = 0; i < list.size; i++){
                    list.get(i).display(inset);
                }
                line.add(inset).left();

                table.add(line).fillX().left().padLeft(Scl.scl(10f)).row();
            }
        }
    }
}
