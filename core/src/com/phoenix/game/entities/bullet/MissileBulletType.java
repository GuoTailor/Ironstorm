package com.phoenix.game.entities.bullet;

import com.badlogic.gdx.graphics.Color;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Mathf;
import io.anuke.mindustry.gen.Sounds;

/**
 * 导弹。参照 Mindustry mindustry.entities.bullet.MissileBulletType 移植。
 * <p>默认带追踪（{@code homingPower}），飞行中随机撒导弹拖尾；{@code weaveMag > 0} 时叠加蛇形摆动。
 */
public class MissileBulletType extends BasicBulletType{
    protected Color trailColor = Pal.missileYellowBack;

    protected float weaveScale = 0f;
    protected float weaveMag = -1f;

    public MissileBulletType(float speed, float damage){
        this(speed, damage, "missile");
    }

    public MissileBulletType(float speed, float damage, String bulletSprite){
        super(speed, damage, bulletSprite);

        backColor = Pal.missileYellowBack;
        frontColor = Pal.missileYellow;
        homingPower = 7f;
        hitSound = Sounds.explosion;
    }

    @Override
    public void update(Bullet b){
        super.update(b);

        if(Mathf.chance(Time.delta() * 0.2f)){
            Effects.effect(Fx.missileTrail, trailColor, b.x, b.y, 2f);
        }

        if(weaveMag > 0){
            b.velocity().rotate(Mathf.sin(Time.time + b.id * 4422, weaveScale, weaveMag) * Time.delta());
        }
    }
}
