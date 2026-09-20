package com.phoenix.game.world.modules;

/** 建筑电力状态。功率单位按“每帧”计算，避免引入 arc 时间类型。 */
public class PowerModule{
    /** 当前储能。最小版无电池，保留字段供后续扩展。 */
    public float stored;
    /** 本帧发电量。 */
    public float produced;
    /** 本帧需求量。 */
    public float needed;
    /** 供电满足率（0~1）。 */
    public float status = 1f;
    /** 当前所属电网（由 PowerGraph 分配）。 */
    public com.phoenix.game.world.blocks.power.PowerGraph graph;

    /** 清除本帧统计量。 */
    public void reset(){
        produced = 0f;
        needed = 0f;
        status = 1f;
    }
}
