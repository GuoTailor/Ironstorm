package com.phoenix.game.entities.type.base;
import com.phoenix.game.entities.Predict;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.type.Weapon;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Time;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.ai.Pathfinder.PathTarget;
import com.phoenix.game.entities.units.UnitState;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.meta.BlockFlag;
import com.phoenix.game.world.Tile;

/**
 * create by GYH on 2024/12/25
 * 最小实现：地面单位。参照 Mindustry mindustry.entities.type.base.GroundUnit 移植。
 * 原版的寻路/核心突袭被简化为“朝最近敌人直线移动”。
 */
public class GroundUnit extends BaseUnit {
    protected float walkTime;
    protected float stuckTime;
    protected float baseRotation = 99;

    public final UnitState

            attack = new UnitState() {
        public void entered() {
            target = null;
        }

        public void update() {
            //最小实现：没有核心建筑系统，直接搜索较远范围内的敌方单位并靠近
            if(target == null || !target.isValid() || retarget()){
                target = Units.closestTarget(team, x, y, Math.max(type.attackLength, 700f), u -> type.targetAir || !u.isFlying());
            }

            if(target != null){
                if(dst(target) > range() * 0.75f){
                    //追击移动目标走直线；被障碍卡住时改用流场绕路，避免一直顶着墙/建筑
                    if(stuckTime > 30f){
                        moveTo(PathTarget.enemyCores);
                    }else{
                        moveTo(target.getX(), target.getY());
                    }
                }else{
                    velocity.scl(0.9f);
                }
            }else{
                //无可见目标时，朝敌核流场流动（绕墙）。原版语义：无目标 -> 逼近敌方核心。
                moveTo(PathTarget.enemyCores);
            }
        }
    },
            rally = new UnitState() {
                public void update() {
                    Tile target = getClosest(BlockFlag.rally);

                    if (target != null && dst(target) > 80f) {
                        moveToCore(PathTarget.rallyPoints);
                    }
                }
            },
            retreat = new UnitState() {
                public void entered() {
                    target = null;
                }

                public void update() {
                    moveAwayFromCore();
                }
            };

    @Override
    public UnitState getStartState(){
        return attack;
    }

    /** @return 当前 AI 状态名（调试/验证用）。 */
    public String stateName(){
        UnitState current = state.current();
        if(current == attack) return "attack";
        if(current == rally) return "rally";
        if(current == retreat) return "retreat";
        return "none";
    }

    /** @return 走路动画相位（调试用）。 */
    public float walkTime(){
        return walkTime;
    }

    public void move(float x, float y) {
        float dst = Mathf.dst(x, y);
        if (dst > 0.01f) {
            baseRotation = MathUtils.lerpAngleDeg(baseRotation, MathUtils.atan2Deg360(y, x), type.baseRotateSpeed * (dst / type.speed));
        }
        super.move(x, y);
    }

    /**
     * 走路动画只在实际移动时推进（对应原版 GroundUnit.update）：
     * 这一帧位置没变就记入 stuckTime，动画随之停下，避免站着不动腿还在乱动。
     */
    @Override
    public void update(){
        super.update();

        stuckTime = !Tmp.v1.set(x, y).sub(lastPosition()).isZero(0.0001f) ? 0f : stuckTime + Time.delta();

        if(!velocity().isZero()){
            baseRotation = Mathf.slerpDelta(baseRotation, velocity().angle(), 0.05f);
        }

        if(stuckTime < 1f){
            walkTime += Time.delta();
        }
    }

    public void draw() {
        float ft = Mathf.sin(walkTime * type.speed * 5f, 6f, 2f + type.hitsize / 15f);

        if(type.legRegion != null){
            for(int i : Mathf.signs){
                Draw.rect(type.legRegion,
                        x + Angles.trnsx(baseRotation, ft * i),
                        y + Angles.trnsy(baseRotation, ft * i),
                        type.legRegion.getRegionWidth() * i * Draw.scl,
                        type.legRegion.getRegionHeight() * Draw.scl - MathUtils.clamp(ft * i, 0, 2),
                        baseRotation - 90);
            }
        }
        Draw.color(Color.WHITE);

        if(type.baseRegion != null){
            Draw.rect(type.baseRegion, x, y, baseRotation - 90);
        }

        if(type.region != null){
            Draw.rect(type.region, x, y, rotation - 90);
        }

        if(type.weapon != null && type.weapon.region != null){
            float tra = rotation - 90, trY = -type.weapon.getRecoil(this, false) + type.weaponOffsetY;
            float w = 1 * type.weapon.region.getRegionWidth() * Draw.scl;
            Draw.rect(type.weapon.region,
                    x + Angles.trnsx(tra, -type.weapon.width, trY),
                    y + Angles.trnsy(tra, -type.weapon.width, trY), w, type.weapon.region.getRegionHeight() * Draw.scl, rotation - 90);

            trY = -type.weapon.getRecoil(this, true) + type.weaponOffsetY;
            w = -w;
            Draw.rect(type.weapon.region,
                    x + Angles.trnsx(tra, type.weapon.width, trY),
                    y + Angles.trnsy(tra, type.weapon.width, trY), w, type.weapon.region.getRegionHeight() * Draw.scl, rotation - 90);
        }
    }

    public void behavior() {

        if (!Units.invalidateTarget(target, this)) {
            if (dst(target) < range()) {

                rotate(angleTo(target));

                if (Angles.near(angleTo(target), rotation, 13f)) {
                    BulletType ammo = getWeapon().bullet;

                    Vector2 to = Predict.intercept(GroundUnit.this, target, ammo.speed);

                    getWeapon().update(GroundUnit.this, to.x, to.y);
                }
            }
        }
    }
}
