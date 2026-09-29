package com.phoenix.game.world.blocks.defense.turrets;

import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.StatUnit;

/**
 * 耗电炮塔：不消耗物品，开火完全靠电力。参照 Mindustry
 * mindustry.world.blocks.defense.turrets.PowerTurret 移植。
 * <p>原版语义：<b>没电也能转向，但装填速度为 0 所以不开火</b> —— 靠
 * {@code baseReloadSpeed(tile) = tile.entity.power.status} 实现。
 * <p>刻意偏离（本项目结构）：
 * <ul>
 *     <li>原版 {@code useAmmo/hasAmmo/peekAmmo/baseReloadSpeed} 是方块方法，本项目写在实体上
 *         （见 {@link PowerTurretEntity}）。</li>
 *     <li>本项目 {@code Turret} 用单一 {@code bullet} 字段选弹（原版是 {@code peekAmmo(tile)}），
 *         所以 {@code init()} 里把 {@link #shootType} 同步给 {@code bullet}，父类的开火逻辑才认得它。</li>
 *     <li>原版 {@code Consumers.powerCond(powerUse, entity -> ((TurretEntity)entity).target != null)}
 *         （有目标才耗电）；本项目 {@code Consumers} 只有无条件 {@code power(...)}，无 {@code powerCond}
 *         （{@code RepairPoint} 也走"没目标就不推进"的等价降级）。</li>
 * </ul>
 */
public class PowerTurret extends CooledTurret{
    public BulletType shootType;
    public float powerUse = 1f;

    public PowerTurret(String name){
        super(name);
        hasPower = true;
        entityType = PowerTurretEntity::new;
    }

    @Override
    public void setStats(){
        super.setStats();

        if(shootType != null) stats.add(BlockStat.damage, shootType.damage, StatUnit.none);
    }

    @Override
    public void init(){
        //刻意降级（B 类）：原版是条件耗电 powerCond(powerUse, entity -> ((TurretEntity)entity).target != null)；
        //  Consumers 无 powerCond → 退化成无条件耗电（有目标才耗电的优化缺失）
        consumes.power(powerUse);

        //本项目 Turret 用单一 bullet 字段（原版是 peekAmmo(tile)）：把 shootType 同步过去
        bullet = shootType;

        super.init();
    }

    /** 炮塔实体：无电时"能转向但不装填"。 */
    public class PowerTurretEntity extends CooledTurretEntity{

        /** 原版 {@code PowerTurret.hasAmmo}：可以一直转向，但没电永远不开火。 */
        @Override
        public boolean hasAmmo(){
            //you can always rotate, but never shoot if there's no power
            return true;
        }

        /** 原版 {@code PowerTurret.baseReloadSpeed(tile)}：装填速度 = 供电率（作弊炮塔恒满速）。 */
        @Override
        protected float baseReloadSpeed(){
            return isEnemyCheat(tile) ? 1f : (power == null ? 1f : power.status);
        }

        @Override
        protected void updateShooting(){
            //原版靠 baseReloadSpeed = power.status 让"没电 = 装填速度 0"；本项目 Turret 没有该钩子，
            //这里显式判一次，否则 hasAmmo() 恒真会让炮塔无电也开火
            if(power != null && power.status <= 0.001f) return;

            super.updateShooting();
        }
    }
}
