package com.phoenix.game.world.modules;

import java.util.Arrays;

import com.phoenix.game.Vars;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Liquid;

/**
 * 液体存储模块。参照 Mindustry mindustry.world.modules.LiquidModule 移植。
 * <p>内部是按下标（{@code liquid.id}）索引的 {@code float[]}，**可同时装多种液体**（与原版同构）。
 * {@link #current()} 是"最后一次收到/读入的液体"（原版注释：Last recieved or loaded liquid），
 * 只对"一次只装一种液体"的方块有意义。
 * <p>⚠ {@link #add(Liquid, float)} 与原版一样**不按容量截断**：容量由调用点用
 * {@code Block.liquidCapacity} 配合 {@code get(liquid)}（按液体分别判断）来约束。
 * 若在模块内按"总量"截断，"水灌满 + 产出另一种液体"的方块（{@code cryofluidmixer}）会因为总量已满
 * 而永远产不出产物、`tryDumpLiquid` 也无液可输出 → 整机停摆（见 {@code 转换.md} 第 39 / 75 条）。
 * <p><b>与原版的差异（刻意）</b>：
 * <ul>
 *   <li>新增 {@link #capacity} / {@link #setCapacity(float)} / {@link #fullness()} / {@link #isEmpty()}
 *       —— 本项目的详情面板（{@code Block.addLiquidBar}）与 {@code Drill} 的液体加成判定需要；
 *       原版这两处都在调用点自己算。</li>
 *   <li>{@link #remove(Liquid, float)} 不会把储量扣成负数（原版是 {@code add(liquid, -amount)}，
 *       超额扣除会得到负储量），也不会像原版那样顺手把 {@code current} 改成"被扣的那种液体"。</li>
 *   <li>原版的 {@code write(DataOutput)} / {@code read(DataInput)}（存档 + 网络同步）未移植 ——
 *       与同目录的 {@link ItemModule} 一致，本项目的存档/同步系统尚未移植。</li>
 * </ul>
 */
public class LiquidModule{
    /** 按 {@code liquid.id} 索引的储量（对应原版私有字段 {@code liquids}）。 */
    private final float[] liquids;
    /** 全部液体之和（对应原版私有字段 {@code total}）。 */
    private float total;
    /**
     * Last recieved or loaded liquid. 与原版一致：字段初值就是 {@code content.liquid(0)}，正常流程下永不 null。
     * <p>（旧实现把它当"唯一那种液体"且初值为 null，导致调用点到处要加 null 守卫，已修正。）
     */
    private Liquid current;
    /** 平滑后的储量（供光照/进度条用）。 */
    private float smoothLiquid;
    /** 容量上限（本项目新增；由 {@code TileEntity.init} 按 {@code block.liquidCapacity} 设置）。 */
    public float capacity = 1f;

    public LiquidModule(){
        //与原版同构：长度 = 液体总数。唯一构造点是 TileEntity.init()，必然在 content 加载之后
        //（与同目录 ItemModule 用 Items.all.size 是同一约定）。
        liquids = new float[Vars.content.liquids().size];
        //原版字段初值就是 content.liquid(0)，正常流程下永不 null
        current = Vars.content.liquid(0);
    }

    /** 设置容量上限（建筑初始化时调用）。 */
    public void setCapacity(float capacity){
        this.capacity = Math.max(0.0001f, capacity);
    }

    /** 每帧推进平滑值（对应原版 {@code LiquidModule.update()}）。 */
    public void update(){
        smoothLiquid = Mathf.lerpDelta(smoothLiquid, currentAmount(), 0.1f);
    }

    /** @return 平滑后的储量（对应原版 {@code smoothAmount()}）。 */
    public float smoothAmount(){
        return smoothLiquid;
    }

    /** @return 液体总量（对应原版 {@code total()}）。 */
    public float total(){
        return total;
    }

    /** Last recieved or loaded liquid. Only valid for liquid modules with 1 type of liquid. */
    public Liquid current(){
        return current;
    }

    /** 清空并直接写入一种液体（对应原版 {@code reset(Liquid, float)}）。 */
    public void reset(Liquid liquid, float amount){
        Arrays.fill(liquids, 0f);
        liquids[liquid.id] = amount;
        total = amount;
        current = liquid;
    }

    /** @return 当前液体种类的储量（对应原版 {@code currentAmount()}）。 */
    public float currentAmount(){
        return liquids[current.id];
    }

    /** @return 指定液体的储量（对应原版 {@code get(Liquid)}，按 id 索引）。 */
    public float get(Liquid liquid){
        return liquids[liquid.id];
    }

    /** 清空储量（对应原版 {@code clear()}；原版同样不重置 {@code current}）。 */
    public void clear(){
        total = 0f;
        Arrays.fill(liquids, 0f);
    }

    /** 存入液体（对应原版 {@code add(Liquid, float)}；不按容量截断，见类注释）。 */
    public void add(Liquid liquid, float amount){
        liquids[liquid.id] += amount;
        total += amount;
        current = liquid;
    }

    /** 取出液体（对应原版 {@code remove(Liquid, float)}；不会扣成负数，见类注释）。 */
    public void remove(Liquid liquid, float amount){
        float removed = Math.min(liquids[liquid.id], amount);
        if(removed <= 0f) return;

        liquids[liquid.id] -= removed;
        total -= removed;
    }

    /** 遍历所有储量为正的液体（对应原版 {@code each(LiquidConsumer)}）。 */
    public void each(LiquidConsumer cons){
        for(int i = 0; i < liquids.length; i++){
            if(liquids[i] > 0f){
                cons.accept(Vars.content.liquid(i), liquids[i]);
            }
        }
    }

    /** 对所有储量为正的液体做累加（对应原版 {@code sum(LiquidCalculator)}；{@code Block.getFlammability} 用它）。 */
    public float sum(LiquidCalculator calc){
        float sum = 0f;
        for(int i = 0; i < liquids.length; i++){
            if(liquids[i] > 0f){
                sum += calc.get(Vars.content.liquid(i), liquids[i]);
            }
        }
        return sum;
    }

    /** @return 是否为空（本项目新增，供 {@code Block} 详情面板与 {@code Drill} 液体加成判定用）。 */
    public boolean isEmpty(){
        return total <= 0.0001f;
    }

    /** @return 充满度（0~1），供进度条使用（本项目新增；多液时总量可能超过容量，故 clamp）。 */
    public float fullness(){
        return Math.max(0f, Math.min(1f, total / capacity));
    }

    public interface LiquidConsumer{
        void accept(Liquid liquid, float amount);
    }

    public interface LiquidCalculator{
        float get(Liquid liquid, float amount);
    }
}
