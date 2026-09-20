package com.phoenix.game.entities.bullet;

import com.phoenix.game.content.Fx;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.type.Bullet;
import io.anuke.mindustry.gen.Sounds;

/**
 * 炮弹。参照 Mindustry mindustry.entities.bullet.ArtilleryBulletType 移植。
 * <p>飞行途中周期性撒出拖尾，绘制尺寸随 {@code fslope()} 先大后小。
 * <p>与原版差异：原版构造器设 {@code collides = collidesTiles = collidesAir = false}，
 * 依赖专门的抛物线落地检测来决定何时爆炸；phoenix 没有该检测，若照搬会让炮弹直接穿过目标，
 * 因此这里保持碰撞开启（对应「炮塔数值不变、只补原版行为」的移植口径）。
 */
public class ArtilleryBulletType extends BasicBulletType{
    protected Effects.Effect trailEffect = Fx.artilleryTrail;

    public ArtilleryBulletType(float speed, float damage){
        this(speed, damage, "shell");
    }

    public ArtilleryBulletType(float speed, float damage, String bulletSprite){
        super(speed, damage, bulletSprite);

        hitShake = 1f;
        hitSound = Sounds.explosion;
    }

    @Override
    public void update(Bullet b){
        super.update(b);

        //撒拖尾的间隔随飞行进度变化：中段最快（fslope 最大）
        if(b.timer.get(0, 3 + b.fslope() * 2f)){
            Effects.effect(trailEffect, backColor, b.x, b.y, b.fslope() * 4f);
        }
    }

    @Override
    public void draw(Bullet b){
        load();

        float baseScale = 0.7f;
        float scale = baseScale + b.fslope() * (1f - baseScale);

        float w = width * (1f - shrinkX * b.fin()) * scale;
        float h = height * (1f - shrinkY * b.fin()) * scale;

        drawRegions(b.x, b.y, w, h, b.rot() - 90f);
    }
}
