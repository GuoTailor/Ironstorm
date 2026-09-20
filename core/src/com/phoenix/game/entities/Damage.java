package com.phoenix.game.entities;
import com.phoenix.game.entities.type.BaseUnit;

import com.badlogic.gdx.graphics.Color;
import com.phoenix.game.content.Fx;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.Mathf;

/**
 * 最小实现：伤害/爆炸工具类。参照 Mindustry mindustry.entities.Damage 移植。
 * 只做单位范围伤害，瓦片伤害待建筑系统完善后补充。
 */
public class Damage{

    /** 对指定区域内的敌方单位造成伤害。 */
    public static void damage(Team team, float x, float y, float radius, float damage){
        if(damage <= 0f) return;

        //注意：用下标遍历，避免 libgdx Array 迭代器嵌套（伤害可能触发单位死亡→再遍历单位列表）
        for(int i = 0; i < Units.units.size; i++){
            BaseUnit unit = Units.units.get(i);
            if(unit.isDead() || !unit.withinDst(x, y, radius)) continue;
            if(team != null && !team.isEnemy(unit.getTeam())) continue;

            unit.damage(damage);
        }
    }

    /** 对区域内所有单位造成伤害（不分敌我）。 */
    public static void damageAll(float x, float y, float radius, float damage){
        if(damage <= 0f) return;

        for(int i = 0; i < Units.units.size; i++){
            BaseUnit unit = Units.units.get(i);
            if(unit.isDead() || !unit.withinDst(x, y, radius)) continue;
            unit.damage(damage);
        }
    }

    /** 点燃指定位置。 */
    public static void createIncend(float x, float y, float spread, int amount){
        if(amount <= 0) return;

        for(int i = 0; i < amount; i++){
            Effects.effect(Fx.fire, x + Mathf.range(spread), y + Mathf.range(spread), Mathf.random(360f));
        }
    }

    /** 动态爆炸（带可燃/爆炸物属性）。 */
    public static void dynamicExplosion(float x, float y, float flammability, float explosiveness, float power, float radius, Color color){
        Effects.effect(Fx.explosion, color, x, y, 0f);
        Effects.shake(explosiveness + power, explosiveness + power, x, y);

        damageAll(x, y, radius, 4f * (explosiveness + power + 1f));

        if(flammability > 0f){
            createIncend(x, y, radius, (int)Math.min(flammability, 8f));
        }
    }
}
