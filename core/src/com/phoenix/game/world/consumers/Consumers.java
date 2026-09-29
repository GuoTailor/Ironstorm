package com.phoenix.game.world.consumers;

import com.badlogic.gdx.utils.Array;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.ItemStack;

/**
 * 方块的消耗器集合。参照 Mindustry mindustry.world.consumers.Consumers 移植。
 * <p>每种 {@link ConsumeType} 最多登记一个消耗器（按 ordinal 存槽位），
 * {@link #init()} 之后拍平成数组供热路径遍历。
 */
public class Consumers{
    private final Consume[] map = new Consume[ConsumeType.values().length];
    private Consume[] results = {};
    private Consume[] optionalResults = {};

    /**
     * "本方块接受的物品"位图（下标 = {@code Item.id}），对应原版 {@code Consumers.itemFilters}。
     * <p>由 {@link #init()} 调用各消耗器的 {@code applyItemFilter} 填充。
     * <p>⚠ 当前**没有消费点**：{@code BlockStorage.acceptItem} 刻意不接这条判据（见该处注释与 转换.md）。
     * 位图仍保持完整填充，这样将来若按原版恢复判据，不必再排查"哪些消耗器没填"。
     */
    public final com.badlogic.gdx.utils.Bits itemFilters = new com.badlogic.gdx.utils.Bits();
    /**
     * "本方块接受的液体"位图（下标 = {@code Liquid.id}），对应原版 {@code Consumers.liquidfilters}。
     * <p>由 {@link #init()} 调用各消耗器的 {@code applyLiquidFilter} 填充（冷却炮塔的加成表也读它）。
     */
    public final com.badlogic.gdx.utils.Bits liquidfilters = new com.badlogic.gdx.utils.Bits();

    /** 由 {@code Block.init()} 调用：把槽位表拍平成数组（之后不再变动）。 */
    public void init(){
        Array<Consume> all = new Array<>();
        Array<Consume> optional = new Array<>();

        for(Consume cons : map){
            if(cons == null) continue;
            all.add(cons);
            if(cons.isOptional()) optional.add(cons);
        }

        results = all.toArray(Consume.class);
        optionalResults = optional.toArray(Consume.class);

        //登记"接受的物品/液体"位图（对应原版 Consumers.init 末尾的两行）
        for(Consume cons : results){
            cons.applyItemFilter(itemFilters);
            cons.applyLiquidFilter(liquidfilters);
        }
    }

    public boolean hasPower(){
        return has(ConsumeType.power);
    }

    /** 直接耗电（不缓冲）：没电则方块不能工作。 */
    public ConsumePower power(float powerPerTick){
        return add(new ConsumePower(powerPerTick, 0f, false));
    }

    /** 储能（电池）：容量单位与耗电一致。 */
    public ConsumePower powerBuffered(float capacity){
        return add(new ConsumePower(0f, capacity, true));
    }

    public ConsumeItems item(Item item){
        return item(item, 1);
    }

    public ConsumeItems item(Item item, int amount){
        return add(new ConsumeItems(new ItemStack[]{ new ItemStack(item, amount) }));
    }

    public ConsumeItems items(ItemStack... items){
        return add(new ConsumeItems(items));
    }

    public <T extends Consume> T add(T consume){
        map[consume.type().ordinal()] = consume;
        return consume;
    }

    public void remove(ConsumeType type){
        map[type.ordinal()] = null;
    }

    public boolean has(ConsumeType type){
        return map[type.ordinal()] != null;
    }

    @SuppressWarnings("unchecked")
    public <T extends Consume> T get(ConsumeType type){
        if(map[type.ordinal()] == null){
            throw new IllegalArgumentException("方块没有该类型的消耗器: " + type);
        }
        return (T)map[type.ordinal()];
    }

    /** @return 全部消耗器（含可选的）。 */
    public Consume[] all(){
        return results;
    }

    /** @return 仅可选消耗器（加成输入）。 */
    public Consume[] optionals(){
        return optionalResults;
    }

    /**
     * 把各消耗器登记到方块的统计里（对应原版 {@code Consumers.display}）。
     * <p>由 {@code Block.setStats()} 调用 —— 必须在 {@link #init()} 之后（它拍平 {@link #results}）。
     */
    public void display(com.phoenix.game.world.meta.BlockStats stats){
        for(Consume cons : results){
            if(cons != null) cons.display(stats);
        }
    }
}
