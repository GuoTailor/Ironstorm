package com.phoenix.game.entities.type.base;

import com.phoenix.game.ai.Pathfinder.PathTarget;
import com.phoenix.game.core.Time;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.units.UnitState;
import com.phoenix.game.math.Mathf;

/**
 * 飞行单位。参照 Mindustry 飞行单位移动逻辑最小移植。
 * <p>飞行单位不使用地面网格碰撞、不受液体减速，直接按速度积分穿越地图。
 * <p>⚠ 本项目让本类继承 {@link GroundUnit}（原版是直接继承 {@code BaseUnit}）以复用移动代码，
 * 因此也继承了它的状态机 —— 攻击状态在本类里单独覆写（见 {@link #flyAttack}）。
 * <p>本批把原版 {@code FlyingUnit} 的 {@link #circle(float)}/{@link #moveTo(float)} 两个移动辅助
 * 收敛到本类（原先散落在 {@code BaseDrone}/{@code MinerDrone}/{@code HoverUnit} 的重复副本已删）。
 * <p><b>刻意未移植（B 类，已核实）</b>：原版 {@code FlyingUnit} 还有 {@code updateRotation()}（在
 * {@code update()} 里把 rotation 设为 velocity.angle()）、{@code drawWeapons()}（在 {@code draw()} 里画武器）、
 * {@code attack(float)}、{@code onCommand(...)}。本项目**没有**这些 update/draw/命令钩子：
 * 瞄准由 {@code GroundUnit.behavior()} 的 {@code rotate(angleTo(target))} 完成，武器由
 * {@code GroundUnit.draw()} 内联绘制，命令走 {@code CommandAI}。若照原版把 {@code updateRotation()}
 * 接进 {@code update()}，会覆盖 flare/wraith/ghoul/飞船等**所有**飞行单位的 rotation（连带影响面大、
 * 且本环境无法实机回归）→ 故本批只补不影响既有行为的移动辅助，那几个钩子保持现状。
 */
public class FlyingUnit extends GroundUnit{

    /**
     * 创建飞行单位。
     * 具体类型参数由 {@link com.phoenix.game.type.UnitType} 注入。
     */
    public FlyingUnit(){
        super();
    }

    /**
     * 飞行单位自己的攻击状态。
     * <p><b>刻意偏离</b>：原版 {@code FlyingUnit} 有独立的"绕目标盘旋骚扰"攻击逻辑
     * （{@code circle(...)} + {@code targetClosestEnemyFlag(producer/turret)}），本项目没移植那套。
     * 而 {@link GroundUnit#attack} 已改成"冲敌方核心"（照原版地面单位）—— 若不覆写，
     * flare/wraith/ghoul/飞船/悬浮单位会被一起改掉。所以这里保留本类原来的行为：**追击最近的敌方单位**。
     * <p>刻意偏离：原版飞行单位是绕目标盘旋并优先打 producer/turret（{@code targetClosestEnemyFlag}）。
     * 本类不采用那套：{@code targetClosestEnemyFlag} 未移植（本项目只有 {@code getClosest(BlockFlag)} 取单个最近方块），
     * 且本类保留了"追击最近的敌方单位"的既有行为。{@code circle(...)} 本批已补齐，若日后要对齐可在此基础上改。
     */
    protected final UnitState flyAttack = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            if(target == null || !target.isValid() || retarget()){
                target = Units.closestTarget(team, x, y, Math.max(type.attackLength, 700f),
                    u -> type.targetAir || !u.isFlying());
            }

            if(target != null){
                if(dst(target) > range() * 0.75f){
                    moveTo(target.getX(), target.getY());
                }else{
                    velocity.scl(0.9f);
                }
            }else{
                moveTo(PathTarget.enemyCores);
            }
        }
    };

    @Override
    public UnitState getStartState(){
        return flyAttack;
    }

    @Override
    public String stateName(){
        UnitState current = state.current();
        if(current == flyAttack) return "attack(fly)";
        if(current == rally) return "rally";
        if(current == retreat) return "retreat";
        return "none";
    }

    /**
     * 飞行单位朝静态目标直线移动，不查询地面流场。
     * @param path 目标类型
     */
    @Override
    public void moveTo(PathTarget path){
        moveToCore(path);
    }

    /**
     * 飞行单位直接积分位移，绕过地面建筑碰撞。
     * <p>【F1 已删除本覆写】原版所有单位共用 {@code Unit.updateVelocityStatus}（飞行/地面分支都在其中），
     * 本类此前的独立覆写（limit → moveBy → 阻尼）与 {@link BaseUnit#updateVelocity()} 的飞行分支完全等价，
     * 却缺少原版对飞行单位同样生效的出界即死 / 空投区击退 / 出界推回三项 → 删除，统一走基类实现。
     */

    // ---- 飞行移动辅助（对应原版 FlyingUnit 的 circle/moveTo；本批从各子类收敛到这里） ----

    /**
     * 绕当前目标盘旋（对应原版 {@code FlyingUnit.circle(float)}）：目标比 {@code circleLength} 近时
     * 把速度方向偏转，形成环绕；否则直冲目标。由各状态机调用。
     */
    protected void circle(float circleLength){
        circle(circleLength, type.speed);
    }

    /** 绕目标盘旋（可指定速度）。语义照原版 {@code FlyingUnit.circle(float, float)}。 */
    protected void circle(float circleLength, float speed){
        if(target == null) return;

        Tmp.v1.set(target.getX() - x, target.getY() - y);

        if(Tmp.v1.len() < circleLength){
            Tmp.v1.rotate((circleLength - Tmp.v1.len()) / circleLength * 180f);
        }

        Tmp.v1.setLength(speed * Time.delta());

        velocity.add(Tmp.v1);
    }

    /**
     * 朝当前目标保持 {@code circleLength} 的距离（对应原版 {@code FlyingUnit.moveTo(float)}）：
     * 太远就加速靠近、太近就减速甚至倒退。由各状态机调用（如 {@code MinerDrone.mine}）。
     */
    protected void moveTo(float circleLength){
        if(target == null) return;

        Tmp.v1.set(target.getX() - x, target.getY() - y);

        float length = circleLength <= 0.001f ? 1f : Mathf.clamp((dst(target) - circleLength) / 100f, -1f, 1f);

        Tmp.v1.setLength(type.speed * Time.delta() * length);
        if(length < -0.5f){
            Tmp.v1.rotate(180f);
        }else if(length < 0){
            Tmp.v1.setZero();
        }

        velocity.add(Tmp.v1);
    }
}
