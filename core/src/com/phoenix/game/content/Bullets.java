package com.phoenix.game.content;

import com.badlogic.gdx.utils.Array;
import com.phoenix.game.entities.bullet.BasicBulletType;
import com.phoenix.game.entities.bullet.BulletType;

/**
 * 最小实现：子弹定义集合。参照 Mindustry mindustry.content.Bullets 移植。
 * <p>{@link #all} 按声明顺序登记全部子弹类型，供网络同步按 ID 引用（Bullets 静态字段声明序）。
 */
public class Bullets{
    public static final Array<BulletType> all = new Array<>();
    private static boolean registered;

    private static <T extends BulletType> T reg(T type){
        all.add(type);
        return type;
    }

    public static final BulletType basicBullet = reg(new BasicBulletType(2.5f, 10f){{
        lifetime = 45f;
        width = 7f;
        height = 9f;
    }});

    /** 炮塔标准弹药（对应原版 Bullets.standardCopper）。 */
    public static final BulletType standardCopper = reg(new BasicBulletType(2.5f, 9f){{
        lifetime = 60f;
        width = 7f;
        height = 9f;
        ammoMultiplier = 2f;
        trail = Fx.spark;
    }});

    public static final BulletType flakBullet = reg(new BasicBulletType(3.2f, 8f){{
        lifetime = 38f;
        width = 5f;
        height = 7f;
    }});

    public static final BulletType artilleryBullet = reg(new BasicBulletType(1.9f, 30f){{
        lifetime = 70f;
        width = 11f;
        height = 13f;
        splashDamageRadius = 26f;
        splashDamage = 22f;
        hitEffect = Fx.explosion;
        despawnEffect = Fx.explosion;
        hitShake = 2f;
    }});

    public static final BulletType flameBullet = reg(new BasicBulletType(2f, 6f){{
        lifetime = 24f;
        width = 9f;
        height = 9f;
        incendChance = 0.3f;
        incendAmount = 2;
        hitEffect = Fx.fire;
        despawnEffect = Fx.fire;
    }});

    public static final BulletType chaosBullet = reg(new BasicBulletType(2.4f, 22f){{
        lifetime = 55f;
        width = 9f;
        height = 11f;
        splashDamageRadius = 18f;
        splashDamage = 14f;
    }});

    public static final BulletType eradicationBullet = reg(new BasicBulletType(2.2f, 60f){{
        lifetime = 75f;
        width = 13f;
        height = 16f;
        splashDamageRadius = 34f;
        splashDamage = 40f;
        hitEffect = Fx.explosion;
        despawnEffect = Fx.explosion;
        hitShake = 3f;
    }});

    /** 飞行单位 flare 的子弹。 */
    public static final BulletType flareShot = reg(new BasicBulletType(3f, 8f){{
        lifetime = 40f;
        width = 4f;
        height = 6f;
        trail = Fx.spark;
    }});

    /** 飞行单位 ghoul 的炸弹（范围伤害）。 */
    public static final BulletType bombExplosive = reg(new BasicBulletType(1.5f, 20f){{
        lifetime = 30f;
        width = 9f;
        height = 9f;
        splashDamageRadius = 24f;
        splashDamage = 16f;
        hitEffect = Fx.explosion;
        despawnEffect = Fx.explosion;
        hitShake = 2f;
    }});
}
