package com.phoenix.game.world.blocks.defense.turrets;

import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.consumers.ConsumeLiquidBase;
import com.phoenix.game.world.consumers.ConsumeLiquidFilter;
import com.phoenix.game.world.consumers.ConsumeType;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.StatUnit;
import com.phoenix.game.world.meta.values.BoosterListValue;

import static com.phoenix.game.Vars.tilesize;

/**
 * 激光炮塔：发射一束"驻留"的激光弹（{@link #shootDuration} 帧内每帧把它钉在炮口、时间归零），
 * 而不是一次性抛出子弹。参照 Mindustry mindustry.world.blocks.defense.turrets.LaserTurret 移植。
 * <p>冷却液只提供装填速度加成（{@code coolantMultiplier = 1f}，且必须持续供应冷却液才能开火）。
 * <p>与原版的差异：
 * <ul>
 *     <li>原版把 {@code update/updateShooting/bullet/turnToTarget} 写在方块上、由 {@code Block.update(Tile)}
 *         调用；本项目写在实体内部类（见 {@link LaserTurretEntity}）。</li>
 *     <li>原版 {@code turnToTarget(tile, rot)} 里按"激光是否驻留"把转速乘 {@link #firingMoveFract}
 *         （开火时转得更慢）；本项目 {@code Turret.TurretEntity.update()} 直接调 {@code Angles.moveToward}，
 *         没有该钩子 —— 所以 {@link #firingMoveFract} 目前不生效（保留以对齐原版字段）。</li>
 * </ul>
 * <p>已接线：{@code BoosterListValue}（{@link #setStats()} 里显示为"输入"）、
 * {@code Tile.isEnemyCheat()} 的等价实现（{@link Turret#isEnemyCheat(Tile)}，作弊时无视冷却液）。
 * <p>刻意降级（B 类）：工作音效系统（原版 {@code shouldActiveSound}）本项目无。
 */
public class LaserTurret extends PowerTurret{
    public float firingMoveFract = 0.25f;
    public float shootDuration = 100f;

    public LaserTurret(String name){
        super(name);
        canOverdrive = false;

        //原版这里用 0.01f 的冷却液过滤器覆盖父类的 0.2f（Consumers.add 按类型替换）
        consumes.add(new ConsumeLiquidFilter(liquid -> liquid.temperature <= 0.5f && liquid.flammability < 0.1f, 0.01f)).update(false);
        coolantMultiplier = 1f;
        entityType = LaserTurretEntity::new;
    }

    @Override
    public void setStats(){
        super.setStats();

        //原版把 booster 项移到这里显示为"输入"（BoosterListValue 已就绪）
        stats.remove(BlockStat.booster);
        stats.add(BlockStat.input, new BoosterListValue(reload, consumes.<ConsumeLiquidBase>get(ConsumeType.liquid).amount, coolantMultiplier, false, l -> consumes.liquidfilters.get(l.id)));

        stats.remove(BlockStat.damage);
        //damages every 5 ticks, at least in meltdown's case
        if(shootType != null) stats.add(BlockStat.damage, shootType.damage * 60f / 5f, StatUnit.perSecond);
    }

    /** @return 是否应该播放工作音效（对应原版 {@code LaserTurret.shouldActiveSound}）。
     *  <p>刻意降级（B 类）：本项目无工作音效系统（{@code Block} 无 {@code activeSound} 字段/播放路径），本方法无人调用。 */
    public boolean shouldActiveSound(Tile tile){
        LaserTurretEntity entity = (LaserTurretEntity)tile.entity;
        return entity.bulletLife > 0 && entity.bullet != null;
    }

    /** 炮塔实体：激光驻留期间把子弹钉在炮口（原版 {@code LaserTurret.update}）。 */
    public class LaserTurretEntity extends PowerTurretEntity{
        /** 驻留的激光弹（注意与外层 {@code Turret.bullet}（BulletType）同名，取弹药类型要写 {@code LaserTurret.this.bullet}）。 */
        Bullet bullet;
        float bulletLife;

        @Override
        public void update(){
            super.update();

            if(bulletLife > 0 && bullet != null){
                float ox = Angles.trnsx(rotation, size * tilesize / 2f);
                float oy = Angles.trnsy(rotation, size * tilesize / 2f);
                bullet.rot(rotation);
                bullet.set(cx() + ox, cy() + oy);
                bullet.time(0f);
                heat = 1f;
                recoil = LaserTurret.this.recoil;
                bulletLife -= Time.delta();
                if(bulletLife <= 0f){
                    bullet = null;
                }
            }
        }

        @Override
        protected void updateShooting(){
            //激光驻留期间不重新开火
            if(bulletLife > 0 && bullet != null){
                return;
            }

            //原版：作弊炮塔无视冷却液（直接按 maxUsed 记冷却）
            if(reload >= LaserTurret.this.reload && (cons.valid() || isEnemyCheat(tile))){
                shoot();
                reload = 0f;
            }else{
                Liquid liquid = liquids.current();
                float maxUsed = consumes.<ConsumeLiquidBase>get(ConsumeType.liquid).amount;

                float used = baseReloadSpeed() * (isEnemyCheat(tile) ? maxUsed : Math.min(liquids.get(liquid), maxUsed * Time.delta())) * liquid.heatCapacity * coolantMultiplier;
                reload += used;
                liquids.remove(liquid, used);

                if(Mathf.chance(0.06f * used)){
                    Effects.effect(coolEffect, cx() + Mathf.range(size * tilesize / 2f), cy() + Mathf.range(size * tilesize / 2f));
                }
            }
        }

        /**
         * 覆写父类的开火逻辑：原版 {@code LaserTurret.bullet(Tile, BulletType, float)} 不抛出子弹，
         * 而是创建一束驻留的激光弹（其余步骤与 {@code Turret.shoot} 一致）。
         */
        @Override
        protected void shoot(){
            BulletType type = LaserTurret.this.bullet;
            if(type == null) return;

            recoil = LaserTurret.this.recoil;
            heat = 1f;

            //炮口横向随机抖动（对应原版 tr.trns(rotation, size * tilesize / 2, Mathf.range(xRand))）
            float xr = Mathf.range(xRand);
            float ox = Angles.trnsx(rotation, size * tilesize / 2f, xr);
            float oy = Angles.trnsy(rotation, size * tilesize / 2f, xr);

            for(int i = 0; i < LaserTurret.this.shots; i++){
                bullet = Bullet.create(type, this, getTeam(), cx() + ox, cy() + oy, rotation);
                bulletLife = shootDuration;
            }

            effects(cx() + ox, cy() + oy);
            useAmmo();
        }
    }
}
