package com.phoenix.game.type;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Bullet;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Time;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import io.anuke.mindustry.gen.Sounds;

/**
 * create by GYH on 2024/12/25
 */
public class Weapon {
    public String name;

    /** minimum cursor distance from player, fixes 'cross-eyed' shooting. */
    protected static float minPlayerDist = 20f;
    protected static int sequenceNum = 0;
    /** bullet shot */
    public BulletType bullet;
    /** shell ejection effect */
    public Effects.Effect ejectEffect = Fx.none;
    /** 武器装弹 */
    public float reload;
    /** amount of shots per fire */
    public int shots = 1;
    /** spacing in degrees between multiple shots, if applicable */
    public float spacing = 12f;
    /** inaccuracy of degrees of each shot */
    public float inaccuracy = 0f;
    /** intensity and duration of each shot's screen shake */
    public float shake = 0f;
    /** visual weapon knockback. */
    public float recoil = 1.5f;
    /** shoot barrel y offset */
    public float length = 3f;
    /** shoot barrel x offset. */
    public float width = 4f;
    /** fraction of velocity that is random */
    public float velocityRnd = 0f;
    /** whether to shoot the weapons in different arms one after another, rather than all at once */
    public boolean alternate = false;
    /** randomization of shot length */
    public float lengthRand = 0f;
    /** delay in ticks between shots */
    public float shotDelay = 0;
    /** whether shooter rotation is ignored when shooting. */
    public boolean ignoreRotation = false;
    /** if turnCursor is false for a mech, how far away will the weapon target. */
    public float targetDistance = 1f;

    public Sound shootSound = Sounds.pew;

    public TextureRegion region;

    protected Weapon(String name){
        this.name = name;
    }

    public Weapon(){
        //no region
        this.name = "";
    }

    public float getRecoil(BaseUnit player, boolean left){
        return (1f - MathUtils.clamp(player.getTimer().getTime(player.getShootTimer(left)) / reload, 0.0F, 1.0F)) * recoil;
    }

    public void load(){
        region = Core.atlas.findRegion(name + "-equip");
        if (region == null) {
            region = Core.atlas.findRegion(name);
            if (region == null) {
                region = Core.atlas.findRegion("clear");
            }
        }
    }

    /** 依据指向位置更新武器（射击目标点）。 */
    public void update(BaseUnit shooter, float pointerX, float pointerY){
        for(boolean left : Mathf.booleans){
            Tmp.v1.set(pointerX, pointerY).sub(shooter.getX(), shooter.getY());
            if(Tmp.v1.len() < minPlayerDist) Tmp.v1.setLength(minPlayerDist);

            float cx = Tmp.v1.x + shooter.getX(), cy = Tmp.v1.y + shooter.getY();

            float ang = Tmp.v1.angle();
            Tmp.v1.set(width * Mathf.sign(left), length + Mathf.range(lengthRand)).rotateDeg(ang - 90f);

            update(shooter, shooter.getX() + Tmp.v1.x, shooter.getY() + Tmp.v1.y, Angles.angle(shooter.getX() + Tmp.v1.x, shooter.getY() + Tmp.v1.y, cx, cy), left);
        }
    }

    /** 依据挂载点位置更新武器。 */
    public void update(BaseUnit shooter, float mountX, float mountY, float angle, boolean left){
        if(shooter.getTimer().get(shooter.getShootTimer(left), reload)){
            if(alternate){
                shooter.getTimer().reset(shooter.getShootTimer(!left), reload / 2f);
            }

            shoot(shooter, mountX - shooter.getX(), mountY - shooter.getY(), angle, left);
        }
    }

    /**
     * 开火入口。对应原版 {@code Weapon.shoot}：
     * <p>服务端先广播开火事件（客户端各自本地生成子弹），然后自己也生成一份（权威伤害）；
     * 客户端不广播（自己的开火事件会从服务端回来，接收端会跳过射手自己）。
     */
    public void shoot(BaseUnit shooter, float x, float y, float angle, boolean left){
        if(com.phoenix.game.Vars.isServer() && shooter != null){
            com.phoenix.game.net.Packets.ShootWeapon sw = new com.phoenix.game.net.Packets.ShootWeapon();
            sw.shooterId = shooter.getID();
            sw.x = x;
            sw.y = y;
            sw.angle = angle;
            sw.left = left;
            com.phoenix.game.Vars.netServer.net.send(sw, com.phoenix.game.net.Net.SendMode.udp);
        }

        shootDirect(shooter, x, y, angle, left);
    }

    public void shootDirect(BaseUnit shooter, float offsetX, float offsetY, float rotation, boolean left){
        float x = shooter.getX() + offsetX;
        float y = shooter.getY() + offsetY;
        float baseX = shooter.getX(), baseY = shooter.getY();

        if(shootSound != null) shootSound.play(1f);

        sequenceNum = 0;
        if(shotDelay > 0.01f){
            Angles.shotgun(shots, spacing, rotation, f -> {
                float ang = f;
                int num = sequenceNum++;
                Time.run(num * shotDelay, () -> bullet(shooter, x + shooter.getX() - baseX, y + shooter.getY() - baseY, ang + Mathf.range(inaccuracy)));
            });
        }else{
            Angles.shotgun(shots, spacing, rotation, f -> bullet(shooter, x, y, f + Mathf.range(inaccuracy)));
        }

        if(bullet != null){
            Tmp.v1.set(bullet.recoil, 0f).rotateDeg(rotation + 180f);
            shooter.velocity().add(Tmp.v1);
        }

        Effects.shake(shake, shake, x, y);
        Effects.effect(ejectEffect, x, y, rotation * -Mathf.sign(left));

        //reset timer for remote players
        shooter.getTimer().get(shooter.getShootTimer(left), reload);
    }

    void bullet(BaseUnit owner, float x, float y, float angle){
        if(owner == null || bullet == null) return;

        Tmp.v1.set(3f, 0f).rotateDeg(angle);
        Bullet.create(bullet, owner, x + Tmp.v1.x, y + Tmp.v1.y, angle, (1f - velocityRnd) + Mathf.random(velocityRnd));
    }
}
