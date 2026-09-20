package com.phoenix.game.entities.bullet;

import io.anuke.mindustry.gen.Sounds;

/**
 * 投掷炸弹。参照 Mindustry mindustry.entities.bullet.BombBulletType 移植。
 * <p>低速、高阻力、不继承发射者速度，靠 {@code lifetime} 落地后由 {@code despawned} 触发范围伤害。
 * <p>与原版差异：原版设 {@code collides = collidesTiles = false}（依赖落地检测）；phoenix 保持碰撞开启，
 * 否则炸弹会直接穿过目标，见 {@link ArtilleryBulletType} 的同类说明。
 */
public class BombBulletType extends BasicBulletType{

    public BombBulletType(float damage, float radius){
        this(damage, radius, "shell");
    }

    public BombBulletType(float damage, float radius, String sprite){
        super(0.7f, 0, sprite);

        splashDamageRadius = radius;
        splashDamage = damage;
        shrinkY = 0.7f;
        lifetime = 30f;
        drag = 0.05f;
        keepVelocity = false;
        collidesAir = false;
        hitSound = Sounds.explosion;
    }
}
