package com.phoenix.game.world.blocks.defense.turrets;

import com.phoenix.game.core.Time;
import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;

import static com.phoenix.game.Vars.tilesize;

/**
 * 点射炮塔：一次装填按 {@link #burstSpacing} 的间隔连发 {@code shots} 发。参照 Mindustry
 * mindustry.world.blocks.defense.turrets.BurstTurret 移植。
 * <p>与原版的差异：原版把 {@code shoot(Tile, BulletType)} 写在方块上、由 {@code Block.update(Tile)} 调用；
 * 本项目写在实体内部类（见 {@link BurstTurretEntity}）。
 */
public class BurstTurret extends ItemTurret{
    public float burstSpacing = 5;

    public BurstTurret(String name){
        super(name);
        entityType = BurstTurretEntity::new;
    }

    /** 炮塔实体：用 {@code Time.run} 把连发拆到后续若干帧（原版 {@code BurstTurret.shoot}）。 */
    public class BurstTurretEntity extends ItemTurretEntity{

        @Override
        protected void shoot(){
            heat = 1f;

            for(int i = 0; i < BurstTurret.this.shots; i++){
                Time.run(burstSpacing * i, () -> {
                    //原版 isTurret(tile)：实体已被替换/移除就跳过这一发
                    if(isDead() || !hasAmmo()) return;

                    BulletType type = peekAmmo();
                    if(type == null) return;

                    recoil = BurstTurret.this.recoil;

                    //炮口横向随机抖动（对应原版 tr.trns(rotation, size * tilesize / 2, Mathf.range(xRand))）
                    float xr = Mathf.range(xRand);
                    float ox = Angles.trnsx(rotation, size * tilesize / 2f, xr);
                    float oy = Angles.trnsy(rotation, size * tilesize / 2f, xr);

                    Bullet.create(type, this, getTeam(), cx() + ox, cy() + oy,
                        rotation + Mathf.range(inaccuracy));

                    effects(cx() + ox, cy() + oy);
                    useAmmo();
                });
            }
        }
    }
}
