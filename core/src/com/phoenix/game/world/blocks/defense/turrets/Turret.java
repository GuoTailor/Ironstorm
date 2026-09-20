package com.phoenix.game.world.blocks.defense.turrets;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Interval;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Predict;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.entities.traits.TargetTrait;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockFlag;
import io.anuke.mindustry.gen.Sounds;

import java.util.EnumSet;

import static com.phoenix.game.Vars.tilesize;

/**
 * 最小实现：炮塔。参照 Mindustry mindustry.world.blocks.defense.turrets.Turret 移植。
 * <p>简化点：只有一种弹药（ammoItem，为 null 时无限弹药）、不做液体冷却、不做双管特化绘制、
 * 敌方判定依赖 Bullet 的队伍字段（友方子弹会穿过友方单位与建筑）。
 */
public class Turret extends Block{
    /** 索敌计时器槽位 */
    public static final int timerTarget = 0;

    /** 重新索敌间隔（tick） */
    public int targetInterval = 20;
    /** 射程（世界单位） */
    public float range = 50f;
    /** 装填时间（tick） */
    public float reload = 10f;
    /** 炮管转速（度/tick） */
    public float rotatespeed = 5f;
    /** 开火所需瞄准夹角（度） */
    public float shootCone = 8f;
    public float inaccuracy = 0f;
    /** 每次射击的弹数 */
    public int shots = 1;
    /** 多发的角度间隔 */
    public float spread = 4f;
    /** 后坐力 */
    public float recoil = 1f;
    public float cooldown = 0.02f, restitution = 0.02f;
    public boolean targetAir = true, targetGround = true;
    /** 弹药物品；null 表示不消耗弹药 */
    public Item ammoItem;
    public int ammoPerShot = 1;
    /** 发射的子弹 */
    public BulletType bullet;
    public Sound shootSound = Sounds.shoot;
    public Effects.Effect shootEffect = Fx.none;
    /** 开火烟（对应原版 Turret.smokeEffect） */
    public Effects.Effect smokeEffect = Fx.none;

    /** 炮管贴图（按角度旋转绘制） */
    public TextureRegion barrelRegion;

    public Turret(String name){
        super(name);

        update = true;
        solid = true;
        destructible = true;
        health = 250;
        hasItems = true;
        itemCapacity = 20;
        flags = EnumSet.of(BlockFlag.turret);
        entityType = TurretEntity::new;
    }

    @Override
    public void load(){
        super.load();

        //原版 region=炮管、baseRegion=block-<size>；这里相反：region 给 Renderer 平铺底座，炮管在 drawLayer 旋转绘制
        barrelRegion = region;
        region = Core.atlas.findRegion("block-" + size);
    }

    @Override
    public void drawLayer(Tile tile){
        if(barrelRegion == null || !(tile.entity instanceof TurretEntity)) return;

        TurretEntity entity = (TurretEntity)tile.entity;

        //后坐力：沿炮管反方向偏移
        float x = tile.worldx() + tilesize / 2f - Angles.trnsx(entity.rotation, entity.recoil);
        float y = tile.worldy() + tilesize / 2f - Angles.trnsy(entity.rotation, entity.recoil);

        Draw.rect(barrelRegion, x, y, entity.rotation - 90f);
    }

    /** 只接收消耗器声明过的弹药（对应原版 ItemTurret.acceptItem）。 */
    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        //没声明物品消耗器的炮塔（纯耗电/激光类）不收物品
        if(!consumes.has(com.phoenix.game.world.consumers.ConsumeType.item)) return false;

        com.phoenix.game.world.consumers.ConsumeItems ci =
            consumes.get(com.phoenix.game.world.consumers.ConsumeType.item);
        if(!ci.hasItem(item)) return false;

        return super.acceptItem(item, tile, source);
    }

    /** 炮塔实体：索敌、转向、开火。 */
    public class TurretEntity extends TileEntity{
        public final Interval timer = new Interval(1);
        /** 装填进度（tick） */
        public float reload;
        /** 炮管角度 */
        public float rotation = 90f;
        public float recoil;
        public float heat;
        public TargetTrait target;

        /** 炮塔中心（TileEntity 的 x,y 是瓦片左下角）。 */
        private float cx(){
            return x + tilesize / 2f;
        }

        private float cy(){
            return y + tilesize / 2f;
        }

        @Override
        public void update(){
            recoil = Mathf.lerpDelta(recoil, 0f, restitution);
            heat = Mathf.lerpDelta(heat, 0f, cooldown);

            if(bullet == null || isDead()) return;

            if(!validateTarget()) target = null;
            if(!hasAmmo()) return;

            if(timer.get(timerTarget, targetInterval)){
                findTarget();
            }

            if(!validateTarget()) return;

            //提前量瞄准（对应原版 Turret.update 的 Predict.intercept）
            float speed = bullet.speed < 0.1f ? 9999999f : bullet.speed;
            Vector2 aim = Predict.intercept(cx(), cy(), target.getX(), target.getY(),
                    target.getTargetVelocityX(), target.getTargetVelocityY(), speed);
            float targetRot = Angles.angle(cx(), cy(), aim.x, aim.y);

            rotation = Angles.moveToward(rotation, targetRot, rotatespeed * Time.delta());

            if(Angles.angleDist(rotation, targetRot) < shootCone){
                updateShooting();
            }
        }

        protected boolean validateTarget(){
            return !Units.invalidateTarget(target, cx(), cy(), range);
        }

        protected void findTarget(){
            if(targetAir && !targetGround){
                target = Units.closestTarget(getTeam(), cx(), cy(), range, u -> !u.isDead() && u.isFlying());
            }else{
                target = Units.closestTarget(getTeam(), cx(), cy(), range,
                        u -> !u.isDead() && (!u.isFlying() || targetAir) && (u.isFlying() || targetGround));
            }
        }

        /** @return 能否开火：弹药（或电力）由消耗器统一判定。 */
        public boolean hasAmmo(){
            return cons.valid();
        }

        /** 开火后扣料（ConsumeItems 扣物品，ConsumePower 的 trigger 是空实现）。 */
        protected void useAmmo(){
            cons.trigger();
        }

        protected void updateShooting(){
            if(reload >= Turret.this.reload){
                shoot();
                reload = 0f;
            }else{
                reload += Time.delta() * (bullet == null ? 1f : bullet.reloadMultiplier);
            }
        }

        protected void shoot(){
            recoil = Turret.this.recoil;
            heat = 1f;

            float angle = rotation + Mathf.range(inaccuracy + bullet.inaccuracy);

            //炮口位置：沿炮管方向偏移半个方块
            float ox = Angles.trnsx(angle, size * tilesize / 2f);
            float oy = Angles.trnsy(angle, size * tilesize / 2f);

            for(int i = 0; i < shots; i++){
                float shotAngle = angle + (i - shots / 2) * spread;
                Bullet.create(bullet, this, getTeam(), cx() + ox, cy() + oy, shotAngle);
            }

            useAmmo();

            if(shootSound != null) shootSound.play(1f);
            effects(cx() + ox, cy() + oy);
        }

        /** 播放炮口特效：炮塔自身为 {@link Fx#none} 时退回弹药自带的特效（对应原版 Turret.effects）。 */
        protected void effects(float x, float y){
            Effects.Effect shoot = Turret.this.shootEffect == Fx.none ? bullet.shootEffect : Turret.this.shootEffect;
            Effects.Effect smoke = Turret.this.smokeEffect == Fx.none ? bullet.smokeEffect : Turret.this.smokeEffect;

            Effects.effect(shoot, x, y, rotation);
            Effects.effect(smoke, x, y, rotation);
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeFloat(rotation);
            out.writeFloat(reload);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            rotation = in.readFloat();
            reload = in.readFloat();
            //目标不入档：读档后下一帧重新索敌（recoil/heat 是纯视觉量，不存）
            target = null;
        }
    }
}
