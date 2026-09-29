package com.phoenix.game.world.blocks.defense.turrets;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.OrderedMap;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Predict;
import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.entities.effect.Fire;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.consumers.ConsumeLiquidFilter;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.values.AmmoListValue;
import io.anuke.mindustry.gen.Sounds;

import static com.phoenix.game.Vars.tilesize;

/**
 * 液体炮塔：弹药是液体，装进不同液体打出不同子弹。参照 Mindustry
 * mindustry.world.blocks.defense.turrets.LiquidTurret 移植。
 * <p>当前液体决定弹药类型；灭火类液体（{@code canExtinguish}）还会优先锁定着火点。
 * <p>刻意偏离（本项目结构）：原版把 {@code validateTarget(Tile)} / {@code findTarget(Tile)} /
 * {@code effects(Tile)} / {@code hasAmmo(Tile)} / {@code useAmmo(Tile)} 写在方块上，
 * 本项目统一写在实体内部类里（见 {@code Turret.TurretEntity}）。
 * <p>已接线：{@code Tile.isEnemyCheat()} 的等价实现（{@link Turret#isEnemyCheat(Tile)}，作弊时 {@code useAmmo}
 * 不扣液体）；原版 {@code drawLayer} 里液体贴图跟随 {@code tr2}（后坐力偏移）的绘制也已补上。
 * <p>注意：{@code ConsumeLiquidFilter} / {@code AmmoListValue} / {@code Block.reg(String, int)} 已补齐。
 */
public class LiquidTurret extends Turret{
    public ObjectMap<Liquid, BulletType> ammo = new ObjectMap<>();
    public int liquidRegion;

    public LiquidTurret(String name){
        super(name);
        hasLiquids = true;
        shootSound = Sounds.spray;
        liquidRegion = reg("-liquid");

        entityType = LiquidTurretEntity::new;
    }

    /** Initializes accepted ammo map. Format: [liquid1, bullet1, liquid2, bullet2...] */
    protected void ammo(Object... objects){
        //刻意偏离：原版用 arc 的 OrderedMap.of(objects)；libgdx 的 OrderedMap 没有该静态方法，这里手写成对插入
        ammo = new OrderedMap<>();
        for(int i = 0; i + 1 < objects.length; i += 2){
            ammo.put((Liquid)objects[i], (BulletType)objects[i + 1]);
        }
    }

    @Override
    public void drawLayer(Tile tile){
        super.drawLayer(tile);
        TileEntity entity = tile.entity;

        //libgdx 的 TextureAtlas 没有 isFound（arc 才有）→ findRegion 缺图返回 null，等价判空
        if(reg(liquidRegion) != null){
            TurretEntity turret = (TurretEntity)entity;
            //原版 tr2.trns(entity.rotation, -entity.recoil)：液体层跟随后坐力偏移（与炮管同一位移）
            float x = centerX(tile) - Angles.trnsx(turret.rotation, turret.recoil);
            float y = centerY(tile) - Angles.trnsy(turret.rotation, turret.recoil);
            Draw.color(entity.liquids.current().color);
            Draw.alpha(entity.liquids.total() / liquidCapacity);
            Draw.rect(reg(liquidRegion), x, y, turret.rotation - 90);
            Draw.color();
        }
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(BlockStat.ammo, new AmmoListValue<Liquid>(ammo));
        //液体弹药过滤器：只接受弹药表里登记过的液体
        consumes.add(new ConsumeLiquidFilter(i -> ammo.containsKey(i), 1f){
            @Override
            public boolean valid(TileEntity entity){
                return !((LiquidTurretEntity)entity).ammoIsEmpty();
            }

            @Override
            public void display(com.phoenix.game.world.meta.BlockStats stats){

            }
        });
    }

    /** @return 是否应该播放工作音效（对应原版 LiquidTurret.shouldActiveSound）。 */
    public boolean shouldActiveSound(Tile tile){
        LiquidTurretEntity entity = (LiquidTurretEntity)tile.entity;
        return entity.target != null && entity.hasAmmo();
    }

    /** 液体炮塔不接受物品输入（只吃液体）。 */
    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        return false;
    }

    /**
     * 接受液体：必须是弹药表里的液体，且当前储量不能超过"一发弹药"的量。
     * <p>照原版覆写 {@code Block.acceptLiquid}（原版 {@code BlockStorage.acceptLiquid} 还带
     * {@code consumes.liquidfilters} 判据，本项目该判据见 {@code Block.acceptLiquid} 的说明）。
     */
    public boolean acceptLiquid(Tile tile, Tile source, Liquid liquid, float amount){
        Liquid current = tile.entity.liquids.current();
        return ammo.get(liquid) != null
        && (current == liquid || (ammo.containsKey(current) && tile.entity.liquids.get(current) <= ammo.get(current).ammoMultiplier + 0.001f));
    }

    /** 炮塔实体：液体弹药版的索敌/开火。 */
    public class LiquidTurretEntity extends TurretEntity{

        /** @return 弹药表里是否有该液体对应的子弹。 */
        public BulletType peekAmmo(){
            return ammo.get(liquids.current());
        }

        public boolean ammoIsEmpty(){
            BulletType type = peekAmmo();
            return type == null || liquids.total() < type.ammoMultiplier;
        }

        @Override
        public boolean hasAmmo(){
            BulletType type = peekAmmo();
            return type != null && liquids.total() >= type.ammoMultiplier;
        }

        @Override
        protected void useAmmo(){
            //原版：if(tile.isEnemyCheat()) return ammo.get(...)；作弊炮塔不扣弹药
            if(isEnemyCheat(tile)) return;
            BulletType type = peekAmmo();
            if(type == null) return;
            liquids.remove(liquids.current(), type.ammoMultiplier);
        }

        /**
         * 覆写父类的每帧逻辑：父类靠 {@code Turret.bullet != null} 决定能否工作，而本方块没有该字段
         * （弹药来自 {@link #peekAmmo()}），否则炮塔永远不会工作。其余步骤与父类一致。
         */
        @Override
        public void update(){
            recoil = Mathf.lerpDelta(recoil, 0f, restitution);
            heat = Mathf.lerpDelta(heat, 0f, cooldown);

            BulletType type = peekAmmo();
            if(type == null || isDead()) return;

            if(!validateTarget()) target = null;
            if(!hasAmmo()) return;

            if(timer.get(timerTarget, targetInterval)){
                findTarget();
            }

            if(!validateTarget()) return;

            float speed = type.speed < 0.1f ? 9999999f : type.speed;
            Vector2 aim = Predict.intercept(cx(), cy(), target.getX(), target.getY(),
                    target.getTargetVelocityX(), target.getTargetVelocityY(), speed);
            float targetRot = Angles.angle(cx(), cy(), aim.x, aim.y);

            rotation = Angles.moveToward(rotation, targetRot, rotatespeed * Time.delta());

            if(Angles.angleDist(rotation, targetRot) < shootCone){
                updateShooting();
            }
        }

        /** 覆写父类开火逻辑：父类用 {@code Turret.bullet}，这里换成 {@link #peekAmmo()}。 */
        @Override
        protected void shoot(){
            BulletType type = peekAmmo();
            if(type == null) return;

            recoil = LiquidTurret.this.recoil;
            heat = 1f;

            //炮口横向随机抖动（对应原版 tr.trns(rotation, size * tilesize / 2, Mathf.range(xRand))）
            float xr = Mathf.range(xRand);
            float ox = Angles.trnsx(rotation, size * tilesize / 2f, xr);
            float oy = Angles.trnsy(rotation, size * tilesize / 2f, xr);

            for(int i = 0; i < LiquidTurret.this.shots; i++){
                float angle = rotation + Mathf.range(inaccuracy + type.inaccuracy) + (i - LiquidTurret.this.shots / 2) * spread;
                Bullet.create(type, this, getTeam(), cx() + ox, cy() + oy, angle);
            }

            effects(cx() + ox, cy() + oy);
            useAmmo();
        }

        /** 灭火液体优先锁定着火点，否则退回父类的普通索敌。 */
        @Override
        protected boolean validateTarget(){
            if(liquids.current().canExtinguish() && target instanceof Tile){
                return Fire.has(((Tile)target).x, ((Tile)target).y);
            }
            return super.validateTarget();
        }

        /** 灭火液体：先在射程内找火，找不到再走父类索敌。 */
        @Override
        protected void findTarget(){
            if(liquids.current().canExtinguish()){
                int tr = (int)(range / tilesize);
                for(int x = -tr; x <= tr; x++){
                    for(int y = -tr; y <= tr; y++){
                        if(Fire.has(x + tile.x, y + tile.y)){
                            target = Vars.world.tile(x + tile.x, y + tile.y);
                            return;
                        }
                    }
                }
            }

            super.findTarget();
        }

        /** 炮口特效用当前液体的颜色（对应原版 LiquidTurret.effects）。 */
        @Override
        protected void effects(float x, float y){
            BulletType type = peekAmmo();

            Effects.effect(type.shootEffect, liquids.current().color, x, y, rotation);
            Effects.effect(type.smokeEffect, liquids.current().color, x, y, rotation);
            //原版这里按 shootShake 震屏（wave 的 shootShake 为 0，所以实际不震）
            if(shootShake > 0f){
                Effects.shake(shootShake, shootShake, cx(), cy());
            }

            recoil = LiquidTurret.this.recoil;

            scheduleEject();
        }
    }
}
