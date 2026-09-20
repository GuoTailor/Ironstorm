package com.phoenix.game.entities.bullet;


import com.badlogic.gdx.audio.Sound;
import com.phoenix.game.content.Fx;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.Damage;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.effect.Lightning;
import com.phoenix.game.content.StatusEffects;
import com.phoenix.game.entities.traits.TargetTrait;
import com.phoenix.game.ctype.ContentType;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.StatusEffect;
import io.anuke.mindustry.gen.Sounds;

public abstract class BulletType {
    public float lifetime;
    public float speed;
    public float damage;
    public float hitSize = 4;
    public float drawSize = 40f;
    public float drag = 0f;
    public boolean pierce;
    /** Extra inaccuracy when firing. */
    public float inaccuracy = 0f;
    /** How many bullets get created per ammo item/liquid. */
    public float ammoMultiplier = 2f;
    /** Multiplied by turret reload speed to get final shoot speed. */
    public float reloadMultiplier = 1f;
    /** Recoil from shooter entities. */
    public float recoil;
    /** Whether to kill the shooter when this is shot. For suicide bombers. */
    public boolean killShooter;
    /** Whether to instantly make the bullet disappear. */
    public boolean instantDisappear;
    /** Damage dealt in splash. 0 to disable.*/
    public float splashDamage = 0f;
    /** Knockback in velocity. */
    public float knockback;
    /** Whether this bullet hits tiles. */
    public boolean hitTiles = true;
    /** 命中时施加的状态效果 */
    public StatusEffect status = StatusEffects.none;
    /** Intensity of applied status effect in terms of duration. */
    public float statusDuration = 60 * 10f;
    /** Whether this bullet type collides with tiles. */
    public boolean collidesTiles = true;
    /** Whether this bullet type collides with tiles that are of the same team. */
    public boolean collidesTeam = false;
    /** Whether this bullet type collides with air units. */
    public boolean collidesAir = true;
    /** Whether this bullet types collides with anything at all. */
    public boolean collides = true;
    /** Whether velocity is inherited from the shooter. */
    public boolean keepVelocity = true;

    //visual effects
    /** 命中特效。 */
    public Effects.Effect hitEffect = Fx.none;
    /** 消失特效。 */
    public Effects.Effect despawnEffect = Fx.none;
    /** 命中音效。 */
    public Sound hitSound = Sounds.none;
    /** 消失音效。 */
    public Sound despawnSound = Sounds.none;
    /** 飞行拖尾特效（每帧在弹尾撒一个）；非 none 时启用。 */
    public Effects.Effect trail = Fx.none;

    //additional effects

    public int fragBullets = 9;
    public float fragVelocityMin = 0.2f, fragVelocityMax = 1f;
    public BulletType fragBullet = null;

    /** Use a negative value to disable splash damage. */
    public float splashDamageRadius = -1f;

    public int incendAmount = 0;
    public float incendSpread = 8f;
    public float incendChance = 1f;

    public float homingPower = 0f;
    public float homingRange = 50f;

    public int lightining;
    public int lightningLength = 5;

    public float hitShake = 0f;

    public BulletType(float speed, float damage){
        this.speed = speed;
        this.damage = damage;
        lifetime = 40f;
    }

    /** Returns maximum distance the bullet this bullet type has can travel.
     * 返回此子弹类型的子弹可以移动的最大距离。 */
    public float range(){
        return speed * lifetime * (1f - drag);
    }

    public boolean collides(Bullet bullet){
        return true;
    }

    public void hitTile(Bullet b){
        hit(b);
    }

    public void hit(Bullet b){
        hit(b, b.x, b.y);
    }

    public void hit(Bullet b, float x, float y){
        Effects.effect(hitEffect, x, y, b.rot());
        if(hitSound != null) hitSound.play(1f);

        Effects.shake(hitShake, hitShake, b);

        if(fragBullet != null){
            for(int i = 0; i < fragBullets; i++){
                float len = Mathf.random(1f, 7f);
                float a = Mathf.random(360f);
                Bullet.create(fragBullet, b, x + Angles.trnsx(a, len), y + Angles.trnsy(a, len), a, Mathf.random(fragVelocityMin, fragVelocityMax));
            }
        }

        if(Mathf.chance(incendChance)){
            Damage.createIncend(x, y, incendSpread, incendAmount);
        }

        if(splashDamageRadius > 0){
            Damage.damage(b.getTeam(), x, y, splashDamageRadius, splashDamage * b.damageMultiplier());
        }
    }

    public void despawned(Bullet b){
        Effects.effect(despawnEffect, b.x, b.y, b.rot());
        if(despawnSound != null) despawnSound.play(1f);

        if(fragBullet != null || splashDamageRadius > 0){
            hit(b);
        }

        for(int i = 0; i < lightining; i++){
            Lightning.createLighting(Lightning.nextSeed(), b.getTeam(), Pal.surge, damage, b.x, b.y, Mathf.random(360f), lightningLength);
        }
    }

    public void draw(Bullet b){
    }

    public void init(Bullet b){
        //TODO: 需要 HealthTrait 支持后才能生效
        /*
        if(killShooter && b.getOwner() instanceof HealthTrait){
            ((HealthTrait)b.getOwner()).kill();
        }
        */

        if(instantDisappear){
            b.time(lifetime);
        }
    }

    public void update(Bullet b){
        if(trail != Fx.none){
            Effects.effect(trail, b.x, b.y, b.rot());
        }

        if(homingPower > 0.0001f){
            TargetTrait target = Units.closestTarget(b.getTeam(), b.x, b.y, homingRange, e -> !e.isFlying() || collidesAir);
            if(target != null){
                b.velocity().setAngle(Mathf.slerpDelta(b.velocity().angle(), b.angleTo(target), 0.08f));
            }
        }
    }

    public ContentType getContentType(){
        return ContentType.bullet;
    }
}
