package com.phoenix.game.world.meta;

import java.util.Locale;

/**
 * 方块的一条统计项（对应原版 mindustry.world.meta.BlockStat）。
 * <p>枚举顺序与分类归属都照抄原版：内容信息对话框先按 {@link StatCategory} 分组，
 * 组内再按本枚举的 ordinal 排序显示，所以顺序不能随手调整。
 */
public enum BlockStat{
    health(StatCategory.general),
    size(StatCategory.general),
    buildTime(StatCategory.general),
    buildCost(StatCategory.general),

    itemCapacity(StatCategory.items),
    itemsMoved(StatCategory.items),
    launchTime(StatCategory.items),

    liquidCapacity(StatCategory.liquids),

    powerCapacity(StatCategory.power),
    powerUse(StatCategory.power),
    powerDamage(StatCategory.power),
    powerRange(StatCategory.power),
    powerConnections(StatCategory.power),
    basePowerGeneration(StatCategory.power),

    tiles(StatCategory.crafting),
    input(StatCategory.crafting),
    output(StatCategory.crafting),
    productionTime(StatCategory.crafting),
    drillTier(StatCategory.crafting),
    drillSpeed(StatCategory.crafting),
    maxUnits(StatCategory.crafting),

    speedIncrease(StatCategory.shooting),
    repairTime(StatCategory.shooting),
    range(StatCategory.shooting),
    shootRange(StatCategory.shooting),
    inaccuracy(StatCategory.shooting),
    shots(StatCategory.shooting),
    reload(StatCategory.shooting),
    powerShot(StatCategory.shooting),
    targetsAir(StatCategory.shooting),
    targetsGround(StatCategory.shooting),
    damage(StatCategory.shooting),
    ammo(StatCategory.shooting),

    booster(StatCategory.optional),
    boostEffect(StatCategory.optional),
    affinities(StatCategory.optional);

    /** 本统计项属于哪个分类（决定显示时的分组标题）。 */
    public final StatCategory category;

    BlockStat(StatCategory category){
        this.category = category;
    }

    public String localized(){
        return I18n.get("blocks." + name().toLowerCase(Locale.ROOT), name());
    }
}
