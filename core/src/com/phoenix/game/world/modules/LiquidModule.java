package com.phoenix.game.world.modules;

import com.phoenix.game.type.Liquid;

/**
 * 液体存储模块。参照 Mindustry mindustry.world.modules.LiquidModule 最小移植。
 * <p>只记录一种液体（原版支持多液体混合并按索引选择 dominant），足以支撑详情面板的“液体”条。
 * 未接入管道运输：液体只能由建筑自身逻辑写入。
 */
public class LiquidModule{
    /** 当前液体种类；null 表示空。 */
    public Liquid current;
    /** 当前储量。 */
    public float amount;
    /** 容量上限。 */
    public float capacity = 1f;

    /** 设置容量上限（建筑初始化时调用）。 */
    public void setCapacity(float capacity){
        this.capacity = Math.max(0.0001f, capacity);
    }

    /**
     * 存入液体；已有其他种类液体时按比例混合（最小实现只保留量，不保留混合比）。
     * @return 实际存入量
     */
    public float add(Liquid liquid, float amount){
        if(amount <= 0f) return 0f;
        if(current == null) current = liquid;

        float accepted = Math.min(this.capacity - this.amount, amount);
        if(accepted <= 0f) return 0f;

        this.amount += accepted;
        return accepted;
    }

    /** @return 是否为空。 */
    public boolean isEmpty(){
        return amount <= 0.0001f;
    }

    /** @return 充满度（0~1），供进度条使用。 */
    public float fullness(){
        return Math.max(0f, Math.min(1f, amount / capacity));
    }
}
