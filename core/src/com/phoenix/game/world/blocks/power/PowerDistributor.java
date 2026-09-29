package com.phoenix.game.world.blocks.power;

import com.phoenix.game.world.blocks.PowerBlock;

/**
 * 电力分配器。参照 Mindustry mindustry.world.blocks.power.PowerDistributor 移植。
 * <p>自身不发电也不耗电，只负责把接进来的电网连成一张图 —— 原版靠
 * {@code PowerNode.laserRange} 做远距离节点连线，本项目的电网连通性由
 * {@code PowerGraph} + {@code PowerModule} 的邻接 BFS 决定（见 {@code World.rebuildPowerGraphs}）。
 * <p><b>与原版的差异（B 类）</b>：原版构造器里写
 * <pre>
 * consumesPower = false;
 * outputsPower = true;
 * </pre>
 * 本项目 {@code Block} 没有这两个字段（同名字段在 {@code BlockStorage} 里，但 {@code Block} 目前
 * **不继承** {@code BlockStorage}，见该类注释）；且本项目的电网拓扑由 {@code getPowerConnections} +
 * {@code PowerGraph} 的邻接 BFS 决定、不做"产电/耗电"分类，所以补了字段也没有消费方。
 * 输电能力由 {@code hasPower} + {@code PowerModule}/{@code PowerGraph} 表达，耗电与否看 {@code consumes.power(...)}。
 */
public class PowerDistributor extends PowerBlock{

    public PowerDistributor(String name){
        super(name);
        //原版：
        //consumesPower = false;
        //outputsPower = true;
    }
}
