package com.phoenix.game.game;

import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.type.StatusEffect;
import com.phoenix.game.type.UnitType;

/**
 * 最小实现：波次编成（一波里某种单位生成多少）。参照 Mindustry mindustry.game.SpawnGroup 移植。
 */
public class SpawnGroup{
    public static final int never = Integer.MAX_VALUE;

    /** 生成的单位类型 */
    public UnitType type;
    /** 结束波次（含） */
    public int end = never;
    /** 起始波次（含） */
    public int begin;
    /** 每隔几波生成一次，2 = 每两波 */
    public int spacing = 1;
    /** 单波生成上限 */
    public int max = 100;
    /** 每多少波数量 +1 */
    public float unitScaling = never;
    /** 初始数量 */
    public int unitAmount = 1;
    /** 施加的状态效果 */
    public StatusEffect effect;
    /** 携带的物品 */
    public ItemStack items;

    public SpawnGroup(UnitType type){
        this.type = type;
    }

    public SpawnGroup(UnitType type, int begin){
        this(type);
        this.begin = begin;
    }

    public SpawnGroup(UnitType type, int begin, int end){
        this(type, begin);
        this.end = end;
    }

    /** @return 第 wave 波应当生成的数量（波次从 0 开始计，对应原版 getUnitsSpawned）。 */
    public int getUnitsSpawned(int wave){
        if(wave < begin || wave > end || (wave - begin) % spacing != 0){
            return 0;
        }
        return Math.min(unitAmount + (int)(((wave - begin) / spacing) / unitScaling), max);
    }

    /** 创建一个单位（已加入场景，位置由调用方设置）。对应原版 createUnit。 */
    public BaseUnit createUnit(Team team){
        BaseUnit unit = type.create();
        unit.setTeam(team);
        unit.health(unit.maxHealth());

        if(effect != null){
            unit.applyEffect(effect, 999999f);
        }

        return unit;
    }
}
