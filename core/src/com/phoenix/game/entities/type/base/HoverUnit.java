package com.phoenix.game.entities.type.base;

import com.phoenix.game.core.Draw;
import com.phoenix.game.entities.Units;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;

/**
 * 参照原版 mindustry.entities.type.base.HoverUnit 移植。
 * <p>**刻意偏离（B 类，已核实）**：原版这三个方法都覆盖 {@code FlyingUnit} 的同名钩子
 * （{@code drawWeapons()} / {@code attack(float)} / {@code updateRotation()}），由 {@code FlyingUnit}
 * 的 {@code draw()}/{@code attack} 状态/{@code update()} 调用。本项目 {@code FlyingUnit} 是
 * {@code GroundUnit} 的子类，**没有**这三个钩子（瞄准在 {@code GroundUnit.behavior()}、武器在
 * {@code GroundUnit.draw()} 内联绘制），所以这里**暂不加 @Override、当前不会被调用**。
 * 详见 {@link FlyingUnit} 类注释的接线风险说明。
 * <p>{@code weaponAngles} 是原版 {@code FlyingUnit} 的字段，本项目仅 {@link #drawWeapons()} 使用，
 * 故仍留在本类（若日后 FlyingUnit 补齐 drawWeapons 钩子再上移）。
 */
public class HoverUnit extends FlyingUnit{

    protected float[] weaponAngles = {0, 0};

    //TODO 原版是 @Override FlyingUnit.drawWeapons；本项目 FlyingUnit 无该钩子 → 当前无调用点（B 类）。
    public void drawWeapons(){
        for(int i : Mathf.signs){
            float tra = rotation - 90, trY = -getWeapon().getRecoil(this, i > 0) + type.weaponOffsetY;
            float w = i > 0 ? -12 : 12;
            float wx = x + Angles.trnsx(tra, getWeapon().width * i, trY), wy = y + Angles.trnsy(tra, getWeapon().width * i, trY);
            int wi = (i + 1) / 2;
            Draw.rect(getWeapon().region, wx, wy, w, 12, weaponAngles[wi] - 90);
        }
    }

    //TODO 原版是 @Override FlyingUnit.attack(float)；本项目 FlyingUnit 无该钩子 → 当前无调用点（B 类）。
    protected void attack(float circleLength){
        moveTo(circleLength);
    }

    //TODO 原版是 @Override FlyingUnit.updateRotation；本项目 FlyingUnit 无该钩子 → 当前无调用点（B 类）。
    protected void updateRotation(){
        if(!Units.invalidateTarget(target, this)){
            rotation = Mathf.slerpDelta(rotation, angleTo(target), type.rotatespeed);
        }else{
            rotation = Mathf.slerpDelta(rotation, velocity.angle(), type.baseRotateSpeed);
        }
    }
}
