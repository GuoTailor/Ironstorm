package com.phoenix.game.content;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Fill;
import com.phoenix.game.core.Lines;
import com.phoenix.game.core.Time;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.entities.Damage;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.bullet.ArtilleryBulletType;
import com.phoenix.game.entities.bullet.BasicBulletType;
import com.phoenix.game.entities.bullet.BombBulletType;
import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.entities.bullet.FlakBulletType;
import com.phoenix.game.entities.bullet.MassDriverBolt;
import com.phoenix.game.entities.bullet.MissileBulletType;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;

/**
 * 最小实现：子弹定义集合。参照 Mindustry mindustry.content.Bullets 移植。
 * <p>{@link #all} 按声明顺序登记全部子弹类型，供网络同步按 ID 引用（Bullets 静态字段声明序）。
 * <p>炮口特效（{@code shootEffect}/{@code smokeEffect}）按原版对应弹药配置：炮塔自身这两个字段为
 * {@link Fx#none} 时会退回这里（见 {@code Turret.effects}）。原版用默认值的项不再重复写出。
 */
public class Bullets{
    public static final Array<BulletType> all = new Array<>();
    private static boolean registered;

    private static <T extends BulletType> T reg(T type){
        all.add(type);
        return type;
    }

    /** 质量驱动器的炮弹：携带 DriverBulletData，命中对端驱动器后投递物品。 */
    public static final BulletType driverBolt = reg(new MassDriverBolt());

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
        //原版 standardCopper：小口径炮口闪光 + 轻烟
        shootEffect = Fx.shootSmall;
        smokeEffect = Fx.shootSmallSmoke;
    }});

    /** 防空散弹（对应原版 Bullets.flakLead）：带近炸引信，靠近敌机就提前起爆。 */
    public static final BulletType flakBullet = reg(new FlakBulletType(3.2f, 8f){{
        lifetime = 38f;
        width = 5f;
        height = 7f;
        shootEffect = Fx.shootSmall;
    }});

    /** 迫击炮弹（对应原版 Bullets.artilleryDense）：飞行途中周期性撒拖尾。 */
    public static final BulletType artilleryBullet = reg(new ArtilleryBulletType(1.9f, 30f){{
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
        //原版 basicFlame：喷火器专用炮口特效
        shootEffect = Fx.shootSmallFlame;
    }});

    /**
     * 火球（对应原版 Bullets.fireball）：由燃烧的瓦片随机甩出。
     * <p>不参与任何碰撞、可穿透，靠飞行途中不断点火来引燃路径；初速随机（原版在 {@code init} 里重设速度长度）。
     */
    public static final BulletType fireball = reg(new BulletType(1f, 4f){
        {
            pierce = true;
            hitTiles = false;
            collides = false;
            collidesTiles = false;
            drag = 0.03f;
            hitEffect = Fx.none;
            despawnEffect = Fx.none;
        }

        @Override
        public void init(Bullet b){
            b.velocity().setLength(0.6f + Mathf.random(2f));
        }

        @Override
        public void draw(Bullet b){
            Draw.color(Pal.lightFlame, Pal.darkFlame, Color.GRAY, b.fin());
            Fill.circle(b.x, b.y, 3f * b.fout());
            Draw.color();
        }

        @Override
        public void update(Bullet b){
            if(Mathf.chance(0.04f * Time.delta())){
                com.phoenix.game.world.Tile tile = Vars.world == null ? null : Vars.world.tileWorld(b.x, b.y);
                if(tile != null){
                    com.phoenix.game.entities.effect.Fire.create(tile);
                }
            }

            if(Mathf.chance(0.1f * Time.delta())){
                Effects.effect(Fx.fireballsmoke, b.x, b.y);
            }

            if(Mathf.chance(0.1f * Time.delta())){
                Effects.effect(Fx.ballfire, b.x, b.y);
            }
        }
    });

    public static final BulletType chaosBullet = reg(new BasicBulletType(2.4f, 22f){{
        lifetime = 55f;
        width = 9f;
        height = 11f;
        splashDamageRadius = 18f;
        splashDamage = 14f;
    }});

    /** 追踪导弹（对应原版 Bullets.missileExplosive）：默认带追踪与导弹拖尾。 */
    public static final BulletType missileBullet = reg(new MissileBulletType(2.4f, 22f){{
        lifetime = 55f;
        width = 9f;
        height = 11f;
        splashDamageRadius = 18f;
        splashDamage = 14f;
        hitEffect = Fx.explosion;
        despawnEffect = Fx.explosion;
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
        //大口径弹药：原版 standardThorium/standardDenseBig 一类都用 shootBig
        shootEffect = Fx.shootBig;
        smokeEffect = Fx.shootBigSmoke;
    }});

    /**
     * 长矛的激光束（对应原版 Bullets.lancerLaser）。
     * <p>瞬时命中：在 {@code init} 里沿光束做一次 {@link Damage#collideLine}；绘制是 3 层不同颜色、
     * 每层 4 档粗细的叠加线段，长度在前 20% 生命周期内由 0 伸长到全长。
     */
    public static final BulletType lancerLaser = reg(new BulletType(0.001f, 140f){
        final Color[] colors = {Pal.lancerLaser.cpy().mul(1f, 1f, 1f, 0.4f), Pal.lancerLaser, Color.WHITE};
        final float[] tscales = {1f, 0.7f, 0.5f, 0.2f};
        final float[] lenscales = {1f, 1.1f, 1.13f, 1.14f};
        final float length = 160f;

        {
            hitEffect = Fx.hitLancer;
            despawnEffect = Fx.none;
            hitSize = 4f;
            lifetime = 16f;
            pierce = true;
        }

        @Override
        public float range(){
            return length;
        }

        @Override
        public void init(Bullet b){
            Damage.collideLine(b, b.getTeam(), hitEffect, b.x, b.y, b.rot(), length);
        }

        @Override
        public void draw(Bullet b){
            float f = Mathf.curve(b.fin(), 0f, 0.2f);
            float baseLen = length * f;

            Lines.lineAngle(b.x, b.y, b.rot(), baseLen);
            for(int s = 0; s < 3; s++){
                Draw.color(colors[s]);
                for(int i = 0; i < tscales.length; i++){
                    Lines.stroke(7f * b.fout() * (s == 0 ? 1.5f : s == 1 ? 1f : 0.3f) * tscales[i]);
                    Lines.lineAngle(b.x, b.y, b.rot(), baseLen * lenscales[i]);
                }
            }
            Draw.color();
        }
    });

    /**
     * 熔毁的激光束（对应原版 Bullets.meltdownLaser）。
     * <p>与长矛的区别：持续判定（每 5 tick 沿光束再打一次，且带加宽的四邻检测）+ 每帧震屏；
     * 绘制是 4 层颜色、逐层外推并带脉动粗细的粗光束。
     */
    public static final BulletType meltdownLaser = reg(new BulletType(0.001f, 70f){
        final Color tmpColor = new Color();
        final Color[] colors = {Color.valueOf("ec745855"), Color.valueOf("ec7458aa"), Color.valueOf("ff9c5a"), Color.WHITE};
        final float[] tscales = {1f, 0.7f, 0.5f, 0.2f};
        final float[] strokes = {2f, 1.5f, 1f, 0.3f};
        final float[] lenscales = {1f, 1.12f, 1.15f, 1.17f};
        final float length = 220f;

        {
            hitEffect = Fx.hitMeltdown;
            despawnEffect = Fx.none;
            hitSize = 4f;
            drawSize = 420f;
            lifetime = 16f;
            pierce = true;
        }

        @Override
        public void update(Bullet b){
            if(b.timer.get(1, 5f)){
                Damage.collideLine(b, b.getTeam(), hitEffect, b.x, b.y, b.rot(), length, true);
            }

            Effects.shake(1f, 1f, b.x, b.y);
        }

        @Override
        public void hit(Bullet b, float hitx, float hity){
            Effects.effect(hitEffect, colors[2], hitx, hity);
            //原版：熔毁光束有 40% 概率在命中点附近点火
            if(Mathf.chance(0.4f) && Vars.world != null){
                com.phoenix.game.entities.effect.Fire.create(
                    Vars.world.tileWorld(hitx + Mathf.range(5f), hity + Mathf.range(5f)));
            }
        }

        @Override
        public void draw(Bullet b){
            float baseLen = length * b.fout();

            Lines.lineAngle(b.x, b.y, b.rot(), baseLen);
            for(int s = 0; s < colors.length; s++){
                Draw.color(tmpColor.set(colors[s]).mul(1f + Mathf.absin(Time.time, 1f, 0.1f)));
                for(int i = 0; i < tscales.length; i++){
                    float offset = (lenscales[i] - 1f) * 35f;
                    Tmp.v1.set(Angles.trnsx(b.rot() + 180f, offset), Angles.trnsy(b.rot() + 180f, offset));
                    Lines.stroke((9f + Mathf.absin(Time.time, 0.8f, 1.5f)) * b.fout() * strokes[s] * tscales[i]);
                    Lines.lineAngle(b.x + Tmp.v1.x, b.y + Tmp.v1.y, b.rot(), baseLen * lenscales[i]);
                }
            }
            Draw.color();
        }
    });

    /** 飞行单位 flare 的子弹。 */
    public static final BulletType flareShot = reg(new BasicBulletType(3f, 8f){{
        lifetime = 40f;
        width = 4f;
        height = 6f;
        trail = Fx.spark;
    }});

    /** 飞行单位 ghoul 的炸弹（抛体，靠 lifetime 落地后范围爆炸）。 */
    public static final BulletType bombExplosive = reg(new BombBulletType(20f, 24f){{
        //保持 phoenix 原有的投弹初速，否则轰炸机丢出的炸弹几乎原地落下
        speed = 1.5f;
        width = 9f;
        height = 9f;
        hitEffect = Fx.explosion;
        despawnEffect = Fx.explosion;
        hitShake = 2f;
        //原版 bombExplosive：炸弹由机腹投下，不播炮口特效
        shootEffect = Fx.none;
        smokeEffect = Fx.none;
    }});
}
