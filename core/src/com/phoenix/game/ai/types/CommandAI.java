package com.phoenix.game.ai.types;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Bits;
import com.phoenix.game.Vars;
import com.phoenix.game.ai.ControlPathfinder;
import com.phoenix.game.ai.UnitCommand;
import com.phoenix.game.ai.UnitGroup;
import com.phoenix.game.ai.UnitStance;
import com.phoenix.game.core.Interval;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.traits.TargetTrait;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.math.Mathf;

/**
 * RTS 命令 AI：被指挥单位的控制器。参照 Mindustry v7/v8
 * {@code mindustry.ai.types.CommandAI} 移植（arc → libgdx）。
 *
 * <p>接到命令后接管单位的移动决策：目的地（{@link #targetPos}）走
 * {@link ControlPathfinder} 流场 + 编队偏移（{@link UnitGroup}），攻击目标（{@link #attackTarget}）
 * 直奔射程内站定开火；命令队列（{@link #commandQueue}）逐段执行。
 *
 * <p>接入方式与原版不同（原版整个控制器体系 {@code AIController/UnitController} 未移植）：
 * {@link BaseUnit#update()} 在 {@code commandAI != null} 时调用 {@link #updateUnit()} 代替
 * 旧的状态机（{@code UnitState}）分支；瞄准/开火仍由 {@code BaseUnit.behavior()} 完成，
 * 本类只负责“把 {@code unit.target} 指向谁”和“往哪走”。
 *
 * <p>与原版的刻意偏离（对应未移植子系统）：
 * <ul>
 *   <li>{@code commandController}（RepairAI 等子控制器）恒为 null，全部走 defaultBehavior。</li>
 *   <li>payload 系列（enterPayload/loadUnits/unloadPayload/loopPayload）未移植，相关分支删除。</li>
 *   <li> {@code hit()} 受击反击、读档恢复（{@code afterRead}）、boost 起降未移植，TODO。</li>
 *   <li>避让同目标单位（blockingUnit）依赖 {@code Units.nearbyCheck} 与障碍查询，未移植，TODO。</li>
 * </ul>
 */
public class CommandAI{
    protected static final int maxCommandQueueSize = 50;
    /** 复用向量（对应原版静态 moveTarget/offsetedDestination/currentDestination）。 */
    protected static final Vector2 moveTarget = new Vector2(), offsetedDestination = new Vector2(), currentDestination = new Vector2();

    private static final int timerTarget = 0;

    /** 命令队列（只存位置；原版还存 Teamc 目标，攻击目标本工程走 {@link #attackTarget}）。 */
    public final Array<Vector2> commandQueue = new Array<>(5);
    /** 当前移动目的地；null 表示空闲。 */
    public Vector2 targetPos;
    /** 当前攻击目标（单位；建筑目标未移植 —— 原版为 Teamc）。 */
    public TargetTrait attackTarget;
    /** 同批命令的编队（对应原版 @Nullable UnitGroup group）。 */
    public UnitGroup group;
    /** 本单位在编队槽位数组中的下标。 */
    public int groupIndex = 0;
    /** 当前命令（对应原版 UnitCommand command）。 */
    public UnitCommand command;
    /** 姿态位集（对应原版 Bits stances）。 */
    public final Bits stances = new Bits(UnitStance.all.size);

    /** 上一次的命令（检测切换用；子控制器未移植，仅保留字段对齐原版）。 */
    protected UnitCommand lastCommand;
    /** true = 到达攻击目标射程后停止追击（防御性命令）。 */
    protected boolean stopAtTarget, stopWhenInRange;
    protected Vector2 lastTargetPos;
    /** 所属单位。 */
    protected final BaseUnit unit;
    /** 重定向计时（原版在 AIController.timer 上）。 */
    protected final Interval timer = new Interval(4);

    public CommandAI(BaseUnit unit){
        this.unit = unit;
        if(command == null){
            command = UnitCommand.moveCommand;
        }
    }

    public UnitCommand currentCommand(){
        return command == null ? UnitCommand.moveCommand : command;
    }

    /** 尝试指派命令（对应原版 {@code command(UnitCommand)}）。 */
    public void command(UnitCommand command){
        if(command == null) return;
        unit.clearBuilding();
        this.command = command;
    }

    public boolean hasStance(UnitStance stance){
        return stance != null && stances.get(stance.id);
    }

    public void setStance(UnitStance stance, boolean enabled){
        if(enabled){
            setStance(stance);
        }else{
            disableStance(stance);
        }
    }

    public void setStance(UnitStance stance){
        //stop 不是真正的姿态：选中它 = 取消全部命令（对应原版语义）
        if(stance == UnitStance.stop) return;

        if(stance.incompatibleStanceBits != null){
            stances.andNot(stance.incompatibleStanceBits);
        }
        stances.set(stance.id);
    }

    public void disableStance(UnitStance stance){
        stances.clear(stance.id);
    }

    public boolean isAttacking(){
        return unit.target() != null && unit.dst(unit.target()) < unit.range() + 10f;
    }

    /** 每帧调用（由 {@link BaseUnit#update()} 在被指挥时驱动，对应原版 {@code updateUnit()}）。 */
    public void updateUnit(){
        //pursueTarget 姿态：空闲时主动追击自动搜索到的目标
        if(hasStance(UnitStance.pursueTarget) && unit.target() != null && attackTarget == null && targetPos == null){
            commandTarget(unit.target(), false);
        }

        //assign defaults
        if(command == null){
            command = UnitCommand.moveCommand;
        }
        lastCommand = command;

        defaultBehavior();
    }

    /** 取消全部命令（对应原版 {@code clearCommands}；UI 的“取消命令”按钮走这里）。 */
    public void clearCommands(){
        commandQueue.clear();
        targetPos = null;
        attackTarget = null;
        group = null;
    }

    /** @return 是否有进行中的移动命令（对应原版 {@code hasCommand()}）。 */
    public boolean hasCommand(){
        return targetPos != null;
    }

    /** 核心行为（对应原版 {@code defaultBehavior()}，payload 分支已裁剪）。 */
    public void defaultBehavior(){
        // ---- 目标选择：攻击目标 > 停火 > 自动搜索 ----
        if(hasStance(UnitStance.holdFire)){
            unit.setTarget(null);
        }else if(attackTarget != null){
            unit.setTarget(attackTarget);
        }else{
            unit.updateTargeting();
        }

        if(attackTarget != null && invalid(attackTarget)){
            attackTarget = null;
            targetPos = null;
        }

        //move on to the next target
        if(attackTarget == null && targetPos == null){
            finishPath();
        }

        if(attackTarget != null){
            if(targetPos == null){
                targetPos = new Vector2();
                lastTargetPos = targetPos;
            }
            targetPos.set(attackTarget.getX(), attackTarget.getY());
            //原版对“实心建筑目标”做 findClosestEdge 修正；建筑目标未移植，跳过
        }

        boolean alwaysArrive = false;

        float engageRange = unit.range() - 10f;
        boolean withinAttackRange = attackTarget != null && unit.dst(attackTarget) < engageRange;

        if(targetPos != null){
            boolean move = true, isFinalPoint = commandQueue.size == 0;
            //实际要到达的点
            currentDestination.set(targetPos);
            //直线目标点（含寻路子路点）
            moveTarget.set(currentDestination);
            //目的地 + 编队偏移（对应原版 offsetedDestination）
            offsetedDestination.set(currentDestination);
            if(group != null && group.valid && groupIndex * 2 + 1 < (group.positions == null ? -1 : group.positions.length)){
                offsetedDestination.add(group.positions[groupIndex * 2], group.positions[groupIndex * 2 + 1]);
            }

            if(unit.isFlying()){
                //飞行单位直线飞向偏移目的地
                moveTarget.set(offsetedDestination);
            }else{
                if(withinAttackRange){
                    //已在射程内：不再问寻路器，直接朝目标微调
                    moveTarget.set(offsetedDestination);
                }else{
                    ControlPathfinder.PathfindResult result = Vars.controlPath.getPathPosition(unit, offsetedDestination, currentDestination);

                    move &= result.move;
                    if(result.move){
                        moveTarget.set(result.dest);
                    }

                    if(result.unreachable){
                        //路彻底不通：放弃当前命令（对应原版记录 unreachable 后 finishPath）
                        attackTarget = null;
                        finishPath();
                        return;
                    }
                }

                //最终点判定：流场已把我们送到目的地附近
                isFinalPoint &= offsetedDestination.dst(moveTarget) < 4.1f;
            }

            if(move){
                moveTo(moveTarget, isFinalPoint || alwaysArrive);
            }

            //stopAtTarget：到达射程后放弃攻击目标（防御性命令，对应原版语义）
            if(attackTarget != null && stopAtTarget && unit.dst(attackTarget) < engageRange - 1f){
                attackTarget = null;
            }

            //朝向：没有攻击目标时面向移动方向（有目标时 behavior() 自己会转身瞄准）
            if(attackTarget == null && !unit.velocity().isZero(0.01f)){
                unit.rotation = Mathf.slerpDelta(unit.rotation, unit.velocity().angle(), 0.13f);
            }

            //到达目的地 → 结束本段路径
            if(attackTarget == null && unit.dst(offsetedDestination.x, offsetedDestination.y) < Math.max(5f, unit.getSize() / 2f)){
                finishPath();
            }

            //停在射程内的命令
            if(stopWhenInRange && targetPos != null && unit.dst(offsetedDestination.x, offsetedDestination.y) < engageRange * 0.9f){
                finishPath();
                stopWhenInRange = false;
            }
        }else if(unit.target() != null){
            //原地转向目标（开火由 behavior() 处理）
            unit.rotation = Mathf.slerpDelta(unit.rotation, unit.angleTo(unit.target()), 0.2f);
        }
    }

    /** 朝目标点移动（对应原版 {@code AIController.moveTo} 的速度部分：接近目的地减速）。 */
    protected void moveTo(Vector2 target, boolean arrive){
        float dst = unit.dst(target.x, target.y);
        if(dst < 0.01f){
            return;
        }

        float speed = unit.getType().maxVelocity;
        //最终点减速进站（对应原版 approach 逻辑的最小实现）
        if(arrive){
            speed *= Mathf.clamp(dst / 24f, 0.15f, 1f);
        }

        unit.velocity().set(0, speed).setAngle(unit.angleTo(target.x, target.y));
    }

    /**
     * 结束当前路径段：弹出队列中的下一段，或清掉编队（对应原版 {@code finishPath()}，
     * payload/patrol 分支已裁剪）。
     */
    protected void finishPath(){
        Vector2 prev = targetPos;
        targetPos = null;

        if(commandQueue.size > 0){
            Vector2 next = commandQueue.removeIndex(0);
            commandPosition(next);

            //确保编队槽位可达
            if(group != null){
                group.updateRaycast(groupIndex, next);
            }
        }else{
            group = null;
        }
    }

    /** 单位被移除时清理（对应原版 {@code removed(unit)}）。 */
    public void removed(){
        clearCommands();
    }

    /**
     * 入队一个位置命令（对应原版 {@code commandQueue(Position)}）：
     * 空闲则立刻执行，否则追加（去重、限长）。
     */
    public void commandQueue(Vector2 location){
        if(location == null) return;
        if(targetPos == null && attackTarget == null){
            commandPosition(location);
        }else if(commandQueue.size < maxCommandQueueSize && !containsPos(location)){
            commandQueue.add(new Vector2(location));
        }
    }

    private boolean containsPos(Vector2 pos){
        for(int i = 0; i < commandQueue.size; i++){
            if(commandQueue.get(i).epsilonEquals(pos, 0.01f)) return true;
        }
        return false;
    }

    /**
     * 指派移动目的地（对应原版 {@code commandPosition(pos, stopWhenInRange)}）：
     * 覆盖当前目的地并清空攻击目标。
     */
    public void commandPosition(Vector2 pos){
        commandPosition(pos, false);
    }

    public void commandPosition(Vector2 pos, boolean stopWhenInRange){
        if(pos == null) return;

        //拷贝防外部修改（原版注释同款）
        targetPos = lastTargetPos = new Vector2(pos);
        attackTarget = null;
        this.stopWhenInRange = stopWhenInRange;
    }

    /** 指派攻击目标（对应原版 {@code commandTarget(Teamc, boolean)}）。 */
    public void commandTarget(TargetTrait moveTo){
        commandTarget(moveTo, false);
    }

    public void commandTarget(TargetTrait moveTo, boolean stopAtTarget){
        attackTarget = moveTo;
        this.stopAtTarget = stopAtTarget;
    }

    /** @return 重定向节流（有明确目标时更频繁，对应原版 {@code retarget()}）。 */
    public boolean retarget(){
        return timer.get(timerTarget, attackTarget != null ? 10f : 20f);
    }

    /** @return 目标是否已失效（死亡/移除）。 */
    protected boolean invalid(TargetTrait target){
        return target == null || !target.isValid() || (target instanceof BaseUnit u && u.isDead());
    }
}
