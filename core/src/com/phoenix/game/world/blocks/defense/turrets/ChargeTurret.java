package com.phoenix.game.world.blocks.defense.turrets;

import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 蓄力炮塔：开火前先蓄力 {@link #chargeTime} 帧（期间喷蓄力特效、不许转向），蓄满才真正打出子弹。
 * 参照 Mindustry mindustry.world.blocks.defense.turrets.ChargeTurret 移植。
 * <p>与原版的差异：
 * <ul>
 *     <li>原版把 {@code shoot(Tile, BulletType)} 写在方块上、由 {@code Block.update(Tile)} 调用；
 *         本项目写在实体内部类（见 {@link ChargeTurretEntity}）。</li>
 *     <li>原版 {@code shouldTurn(Tile)} 是 {@code Turret.update} 里的钩子（蓄力时锁住炮管）；
 *         本项目 {@code Turret} 已补上该钩子（{@code Turret.TurretEntity.update()} 里调 {@code shouldTurn(tile)}），
 *         所以 {@link #shouldTurn(Tile)} 已生效。</li>
 * </ul>
 */
public class ChargeTurret extends PowerTurret{

    public float chargeTime = 30f;
    public int chargeEffects = 5;
    public float chargeMaxDelay = 10f;
    public Effects.Effect chargeEffect = Fx.none;
    public Effects.Effect chargeBeginEffect = Fx.none;

    public ChargeTurret(String name){
        super(name);
        entityType = ChargeTurretEntity::new;
    }

    /**
     * 蓄力期间不许转向（对应原版 {@code ChargeTurret.shouldTurn}）。
     * <p>接线点：{@code Turret.TurretEntity.update()} → {@code shouldTurn(tile)}（钩子已补在 {@link Turret} 上）。
     */
    public boolean shouldTurn(Tile tile){
        return !(tile.entity instanceof ChargeTurretEntity) || !((ChargeTurretEntity)tile.entity).shooting;
    }

    /** 炮塔实体：把"一次性开火"拆成"蓄力 → 到点发射"（原版 {@code ChargeTurret.shoot}）。 */
    public class ChargeTurretEntity extends PowerTurretEntity{
        /** 是否正在蓄力。 */
        public boolean shooting;

        @Override
        protected void shoot(){
            BulletType type = ChargeTurret.this.bullet;
            if(type == null) return;

            useAmmo();

            float ox = Angles.trnsx(rotation, size * tilesize / 2f);
            float oy = Angles.trnsy(rotation, size * tilesize / 2f);
            Effects.effect(chargeBeginEffect, cx() + ox, cy() + oy, rotation);

            for(int i = 0; i < chargeEffects; i++){
                Time.run(Mathf.random(chargeMaxDelay), () -> {
                    //原版 isTurret(tile)：实体已被替换/移除就跳过
                    if(isDead()) return;
                    float tx = Angles.trnsx(rotation, size * tilesize / 2f);
                    float ty = Angles.trnsy(rotation, size * tilesize / 2f);
                    Effects.effect(chargeEffect, cx() + tx, cy() + ty, rotation);
                });
            }

            shooting = true;

            Time.run(chargeTime, () -> {
                if(isDead()) return;
                float bx = Angles.trnsx(rotation, size * tilesize / 2f);
                float by = Angles.trnsy(rotation, size * tilesize / 2f);
                recoil = ChargeTurret.this.recoil;
                heat = 1f;
                Bullet.create(type, this, getTeam(), cx() + bx, cy() + by, rotation + Mathf.range(inaccuracy));
                effects(cx() + bx, cy() + by);
                shooting = false;
            });
        }
    }
}
