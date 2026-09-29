package com.phoenix.game.world.blocks.defense.turrets;

import com.badlogic.gdx.math.Vector2;
import com.phoenix.game.entities.Predict;
import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;

import static com.phoenix.game.Vars.tilesize;

/**
 * 火炮：按抛物线落点反推初速，只打地面目标。参照 Mindustry
 * mindustry.world.blocks.defense.turrets.ArtilleryTurret 移植。
 * <p>与原版的差异：原版把 {@code shoot(Tile, BulletType)} 写在方块上、由 {@code Block.update(Tile)} 调用；
 * 本项目写在实体内部类（见 {@link ArtilleryTurretEntity}）。
 */
public class ArtilleryTurret extends ItemTurret{
    public float velocityInaccuracy = 0f;

    public ArtilleryTurret(String name){
        super(name);
        targetAir = false;
        entityType = ArtilleryTurretEntity::new;
    }

    /** 炮塔实体：按抛物线落点算初速（原版 {@code ArtilleryTurret.shoot}）。 */
    public class ArtilleryTurretEntity extends ItemTurretEntity{

        @Override
        protected void shoot(){
            BulletType type = peekAmmo();
            if(type == null) return;

            recoil = ArtilleryTurret.this.recoil;
            heat = 1f;

            float ox = Angles.trnsx(rotation, size * tilesize / 2f);
            float oy = Angles.trnsy(rotation, size * tilesize / 2f);

            //原版 Predict.intercept(tile, entity.target, type.speed)：预测落点
            Vector2 predict = Predict.intercept(cx(), cy(), target.getX(), target.getY(),
                target.getTargetVelocityX(), target.getTargetVelocityY(), type.speed);

            float dst = Mathf.dst(cx(), cy(), predict.x, predict.y);
            float maxTraveled = type.lifetime * type.speed;

            for(int i = 0; i < ArtilleryTurret.this.shots; i++){
                //原版 8 参 create（velocityScl, lifetimeScl）——本项目已补该重载
                Bullet.create(type, this, getTeam(), cx() + ox, cy() + oy,
                    rotation + Mathf.range(inaccuracy + type.inaccuracy), 1f + Mathf.range(velocityInaccuracy), dst / maxTraveled);
            }

            effects(cx() + ox, cy() + oy);
            useAmmo();
        }
    }
}
