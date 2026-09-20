package com.phoenix.game.entities.type.base;

import com.phoenix.game.ai.Pathfinder.PathTarget;
import com.phoenix.game.core.Time;

/**
 * 飞行单位。参照 Mindustry 飞行单位移动逻辑最小移植。
 * <p>飞行单位不使用地面网格碰撞、不受液体减速，直接按速度积分穿越地图。
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
     * 飞行单位朝静态目标直线移动，不查询地面流场。
     * @param path 目标类型
     */
    @Override
    public void moveTo(PathTarget path){
        moveToCore(path);
    }

    /**
     * 飞行单位直接积分位移，绕过地面建筑碰撞。
     */
    @Override
    public void updateVelocity(){
        velocity.scl(1f - drag() * Time.delta());
        moveBy(velocity.x * Time.delta(), velocity.y * Time.delta());
    }
}
