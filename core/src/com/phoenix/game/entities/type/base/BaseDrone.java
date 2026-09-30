package com.phoenix.game.entities.type.base;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.ai.UnitCommand;
import com.phoenix.game.entities.units.UnitState;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockFlag;

/**
 * 参照原版 mindustry.entities.type.base.BaseDrone 移植。
 * <p>**刻意偏离（已逐条 grep 核实）**：
 * <ul>
 *     <li>{@code indexer.getAllied(team, BlockFlag.repair)} + {@code Geometry.findClosest(...)}
 *         → 本项目改用 {@code BaseUnit.getClosest(BlockFlag.repair)}（等价的 O(瓦片数) 扫描）。
 *         ⚠ 原注释称"本项目没有 indexer / getAllied 会返回 null"**已过时**：
 *         {@code Vars.indexer}（{@code ai.BlockIndexer}）已有 {@code getAllied}，且 {@code flagMap}
 *         内层 {@code ObjectSet} 在字段初始化时就用 {@code newFlagMap()} 建好了（不会返回 null）。
 *         这里保持 {@code getClosest} 只是等价替代（本类不需要"取集合再取最近"两步）。</li>
 *     <li>{@code targetHasFlag} 已上移到 {@link com.phoenix.game.entities.type.BaseUnit}（照原版），
 *         本类只覆写；{@code circle(...)} 已上移到 {@link FlyingUnit}（照原版）。</li>
 *     <li>{@code onCommand} / {@code updateRotation} 在原版覆盖 FlyingUnit 的同名方法；
 *         本项目 FlyingUnit 没有这两个钩子（见 {@link FlyingUnit} 类注释），因此**暂不加 @Override，
 *         也暂时不会被调用**（B 类：接线会覆盖所有飞行单位的共享 update/draw 行为）。</li>
 * </ul>
 */
public abstract class BaseDrone extends FlyingUnit{
    public final UnitState retreat = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            if(health >= maxHealth()){
                state.set(getStartState());
            }else if(!targetHasFlag(BlockFlag.repair)){
                if(retarget()){
                    //原版 Geometry.findClosest(x, y, indexer.getAllied(team, BlockFlag.repair))；
                    //本项目用 getClosest（按距离取最近的同阵营 repair 方块），等价替代
                    Tile repairPoint = getClosest(BlockFlag.repair);
                    if(repairPoint != null){
                        target = repairPoint;
                    }else{
                        setState(getStartState());
                    }
                }
            }else{
                circle(40f);
            }
        }
    };

    @Override
    public boolean countsAsEnemy(){
        return false;
    }

    //TODO 原版是 @Override FlyingUnit.onCommand（由命令系统调用）；本项目 FlyingUnit 无该钩子，
    // 命令走 ai.types.CommandAI → 本方法当前无调用点（B 类，保留以对齐原版结构）。
    public void onCommand(UnitCommand command){
        //do nothing, normal commands are not applicable here
    }

    //TODO 原版是 @Override FlyingUnit.updateRotation（由 FlyingUnit.update() 调用）；
    // 本项目 FlyingUnit 无该钩子、瞄准由 GroundUnit.behavior() 完成 → 本方法当前无调用点（B 类）。
    protected void updateRotation(){
        if(target != null && shouldRotate() && target.dst(this) < type.range){
            rotation = Mathf.slerpDelta(rotation, angleTo(target), 0.3f);
        }else{
            rotation = Mathf.slerpDelta(rotation, velocity.angle(), 0.3f);
        }
    }

    @Override
    public void behavior(){
        //原版 indexer.getAllied(team, BlockFlag.repair) → 本项目改用 getClosest(BlockFlag.repair)（等价）
        if(health <= maxHealth() * type.retreatPercent && !state.is(retreat) && getClosest(BlockFlag.repair) != null){
            setState(retreat);
        }
    }

    public boolean shouldRotate(){
        return state.is(getStartState());
    }

    @Override
    public abstract UnitState getStartState();

    /** 对应原版 {@code BaseUnit.targetHasFlag}；现已上移到 {@link com.phoenix.game.entities.type.BaseUnit}，此处仅覆写。 */
    @Override
    public boolean targetHasFlag(BlockFlag flag){
        return (target instanceof TileEntity && ((TileEntity)target).tile.block().flags.contains(flag))
            || (target instanceof Tile && ((Tile)target).block().flags.contains(flag));
    }
}
