package com.phoenix.game.world.blocks.power;

import com.phoenix.game.Vars;
import com.phoenix.game.game.Rules;
import com.phoenix.game.world.meta.BlockFlag;
import com.phoenix.game.world.meta.StatUnit;

import java.util.EnumSet;

/**
 * 太阳能发电机（solar-panel / solar-panel-large）。参照 Mindustry
 * mindustry.world.blocks.power.SolarGenerator 移植。
 * <p>属 {@link PowerGenerator} 家族，没有燃料：{@link SolarEntity#update()} 每帧按
 * {@code state.rules.solarPowerMultiplier}/{@code lighting}/{@code ambientLight} 折算
 * {@code productionEfficiency}（默认 -1f 且 lighting=false → 恒为 1，即发电量恒为 {@link #powerProduction}）。
 */
public class SolarGenerator extends PowerGenerator{
    public SolarGenerator(String name){
        super(name);
        //solid/update/hasPower（以及 group）由 PowerBlock 统一设置
        //原版 SolarGenerator.java 构造器不设 health（Blocks.java:1141/1146 也未覆盖）→ 走 Block.init 的
        //size*size*40 默认（solar-panel size1 = 40、solar-panel-large size3 = 360）；此处不再显式赋值。
        //powerProduction 不设默认值：由 Blocks 按实例赋值（solar-panel = 0.06 / solar-panel-large = 0.9）
        //原版注释：去掉 BlockFlag.producer 让它比其他发电机优先级更低（本项目枚举没有 producer，去掉等价的 generator）
        //注：java.util.EnumSet 没有无参 of()（原版是 arc 的 EnumSet.of()），空集用 noneOf
        flags = EnumSet.noneOf(BlockFlag.class);
        entityType = SolarEntity::new;
    }

    @Override
    public void setStats(){
        super.setStats();
        //原版注释：太阳能没有"效率"这一说，对它们而言 100% 就是 1.0f —— 移除效率条，直接按满效率登记发电量
        stats.remove(generationType);
        stats.add(generationType, powerProduction * 60f, StatUnit.powerSecond);
    }

    /** 太阳能实体（对应原版直接用的 {@code PowerGenerator.GeneratorEntity} + {@code SolarGenerator.update(Tile)}）。 */
    public class SolarEntity extends GeneratorEntity{
        @Override
        public void update(){
            //照原版：solarPowerMultiplier 为负时按"光照"折算，否则直接用倍率本身。
            //Rules.solarPowerMultiplier/lighting/ambientLight 均已存在；默认 -1f 且 lighting=false → 恒为 1f
            //（与收敛前"永远满效率"的行为一致）。
            Rules rules = Vars.state.rules;
            productionEfficiency = rules.solarPowerMultiplier < 0f
                ? (rules.lighting ? 1f - rules.ambientLight.a : 1f)
                : rules.solarPowerMultiplier;
        }
    }
}
