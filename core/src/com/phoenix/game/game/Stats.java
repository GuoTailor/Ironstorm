package com.phoenix.game.game;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.ItemType;
import com.phoenix.game.type.Zone;

/**
 * 单局/单区统计。参照 Mindustry mindustry.game.Stats 移植。
 * <p><b>与本项目的差异</b>：
 * <ul>
 *   <li>原版 {@code @Serialize}（{@code mindustry.annotations.Annotations.Serialize}）会触发注解处理器
 *       生成 {@code io.anuke.mindustry.gen.Serialization}；本项目那个处理器生成的代码引用了并不存在的
 *       {@code io.anuke.arc.Settings.TypeSerializer}，打开会直接中断编译 → 保持注释状态
 *       （与 {@code @Remote} 同一约定）。</li>
 *   <li>{@link #calculateRank(Zone, boolean)} <b>已启用</b>（{@code Item.type} / {@code Zone} /
 *       {@code Schematic.findCore()} 都已就绪）；原版的 {@code zone.getRules().attackMode}
 *       条件也已接线（T2 给 {@code Rules} 补了 {@code attackMode}，与 {@code Zone.metCondition()} 一致）。</li>
 * </ul>
 */
//@Serialize
public class Stats{
    /** Items delivered to global resoure counter. Zones only. */
    public ObjectIntMap<Item> itemsDelivered = new ObjectIntMap<>();
    /** Enemy (red team) units destroyed. */
    public int enemyUnitsDestroyed;
    /** Total waves lasted. */
    public int wavesLasted;
    /** Total (ms) time lasted in this save/zone. */
    public long timeLasted;
    /** Friendly buildings fully built. */
    public int buildingsBuilt;
    /** Friendly buildings fully deconstructed. */
    public int buildingsDeconstructed;
    /** Friendly buildings destroyed. */
    public int buildingsDestroyed;

    /** 评级计算（对应原版 {@code Stats.calculateRank}）。 */
    public RankResult calculateRank(Zone zone, boolean launched){
        float score = 0;

        if(launched && zone.getRules().attackMode){
            score += 3f;
        }else if(wavesLasted >= zone.conditionWave){
            //each new launch period adds onto the rank 'points'
            //注意：原版这里就是 int 除法（wavesLasted/conditionWave/launchPeriod 全是 int），别改成 float 除法
            score += (float)((wavesLasted - zone.conditionWave) / zone.launchPeriod + 1) * 1.2f;
        }

        int capacity = zone.loadout.findCore().itemCapacity;

        //weigh used fractions
        float frac = 0f;

        //原版：Array.with(zone.resources).select(i -> i.type == ItemType.material)
        //  （arc 的 with(Iterable) 会复制一份，select 返回"过滤后的新数组"）
        //⚠ libgdx 1.14 的 Array.select(Predicate) 返回的是 Iterable（既不是新数组、也不是原地过滤）→
        //  这里改成"复制 + 手写下标过滤"，语义与 arc 一致（不会动到 zone.resources 本身）
        Array<Item> obtainable = new Array<>(zone.resources);
        for(int i = obtainable.size - 1; i >= 0; i--){
            if(obtainable.get(i).type != ItemType.material){
                obtainable.removeIndex(i);
            }
        }

        for(int i = 0; i < obtainable.size; i++){
            Item item = obtainable.get(i);
            frac += Mathf.clamp((float)itemsDelivered.get(item, 0) / capacity) / (float)obtainable.size;
        }

        score += frac * 1.6f;

        if(!launched){
            score *= 0.5f;
        }

        int rankIndex = Mathf.clamp((int)(score), 0, Rank.values().length - 1);
        Rank rank = Rank.values()[rankIndex];
        String sign = Math.abs((rankIndex + 0.5f) - score) < 0.2f || rank.name().contains("S") ? "" : (rankIndex + 0.5f) < score ? "-" : "+";

        return new RankResult(rank, sign);
    }

    public static class RankResult{
        public final Rank rank;
        /** + or - */
        public final String modifier;

        public RankResult(Rank rank, String modifier){
            this.rank = rank;
            this.modifier = modifier;
        }
    }

    public enum Rank{
        F, D, C, B, A, S, SS
    }
}
