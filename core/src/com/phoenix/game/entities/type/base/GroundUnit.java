package com.phoenix.game.entities.type.base;
import com.phoenix.game.Vars;
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
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Tile;

/**
 * create by GYH on 2024/12/25
 * 最小实现：地面单位。参照 Mindustry mindustry.entities.type.base.GroundUnit 移植。
 * <p>{@code attack} 状态照原版：**移动目标永远是敌方核心**（走寻路流场绕墙），
 * 只有进到射程附近才把核心本身设为攻击目标；打谁另由 {@code updateTargeting()} 决定。
 * <p>⚠ 本项目让 {@link FlyingUnit} 继承本类（原版 {@code FlyingUnit} 直接继承 {@code BaseUnit}），
 * 所以本类的状态机会被飞行单位继承 —— 飞行单位在自己类里覆写了 {@code getStartState()} 保住原行为。
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
            //照原版 GroundUnit.attack：**移动目标始终是敌方核心**（走流场绕墙），而不是"最近的敌方单位"。
            //打谁由 updateTargeting()/targetClosest() 决定（会挑最近的敌方单位或建筑），
            //与"往哪走"是两件事 —— 之前把两者混在一起，导致所有敌人一起扑向同一个最近单位、挤成一团。
            Tile core = getClosestEnemyCore();

            if(core == null){
                //没有敌方核心：朝敌核流场推进；但已经贴近敌方出生点（空投区）时就别往里钻
                Tile closestSpawn = getClosestSpawner();
                if(closestSpawn == null || !within(closestSpawn, Vars.state.rules.dropZoneRadius + 85f)){
                    moveTo(PathTarget.enemyCores);
                }
            }else{
                float dst = dst(core);

                if(dst < range() / 1.1f){
                    target = core;
                }

                if(dst > range() * 0.5f){
                    moveTo(PathTarget.enemyCores);
                }
                //已在射程内就不给速度 —— 靠 drag 自然刹停（本项目 Time.delta() ≈ 1，drag 每帧乘 (1-drag)）

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

        //脚下是液体时，腿部按液体色淡染一半（对应原版 GroundUnit.draw 的 floor.isLiquid 分支）
        Floor floor = getFloorOn();
        if(floor.isLiquid){
            Draw.color(Color.WHITE, floor.color, 0.5f);
        }

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

        //本体着色：液体上按液体色淡染，且染色深度随溺水进度加深（对应原版 GroundUnit.draw）
        if(floor.isLiquid){
            Draw.color(Color.WHITE, floor.color, drownTime * 0.4f);
        }else{
            Draw.color(Color.WHITE);
        }

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

        //F12c：单位附加绘制钩子（对应原版 Mech.draw(Player)；如 javelin-ship 的高速护盾）
        type.drawOverlay(this);
        Draw.color();
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
