package com.phoenix.game.entities.type;

import com.phoenix.game.Vars;
import com.phoenix.game.core.Time;
import com.phoenix.game.game.Team;
import com.phoenix.game.world.Tile;

/**
 * 最小实现：玩家。参照 Mindustry mindustry.entities.type.Player 移植。
 * 原版 Player 自身就是一个 Unit（并带整套 Mech 装备系统），这里简化为“持有一个被控制的单位引用”，
 * 只移植与操作相关的部分：位置、瞄准点、射击/加速状态、死亡后重生。
 */
public class Player{
    /** 死亡后重生等待时间（tick，与原版一致为 5 秒） */
    public static final float respawnTime = 60f * 5f;

    /** 升级所需经验基数（每级 +200%）。 */
    public static final int baseXpNeed = 3;
    /** 最大等级。 */
    public static final int maxLevel = 10;

    public String name = "noname";
    public Team team = Team.sharded;
    /** 当前经验。 */
    public int xp;
    /** 当前等级（1 起）。 */
    public int level = 1;

    /** 被控制的单位（死亡后仍指向该单位，重生时替换） */
    public BaseUnit unit;
    /** 当前玩家单位类型（重生/切换时据此创建单位）。 */
    public com.phoenix.game.type.UnitType selectedType = com.phoenix.game.content.UnitTypes.dagger;
    /** 冲刺冷却（tick）。 */
    public float dashCooldown;

    /** 鼠标世界坐标（对应原版 pointerX/pointerY） */
    public float pointerX, pointerY;
    public boolean isShooting, isBoosting;

    /** 当前位置（死亡后保留最后位置，用于查询最近核心） */
    public float x, y;
    /** 已死亡等待时间（tick） */
    public float deadTime;

    public boolean isDead(){
        return unit == null || unit.isDead();
    }

    public BaseUnit unit(){
        return unit;
    }

    /** 接管一个单位（对应原版 Player 自身就是单位，这里改为引用）。 */
    public void unit(BaseUnit unit){
        this.unit = unit;

        if(unit != null){
            unit.isPlayer = true;
            //小地图给其他玩家显示名字用（原版 Player 就是单位，名字直接挂在单位上）
            unit.playerName = name;
            unit.setTeam(team);
            x = unit.x;
            y = unit.y;
            //重生/接管时应用等级加成
            unit.health(unit.maxHealth() * healthMultiplier());
        }

        deadTime = 0f;
        isShooting = isBoosting = false;
    }

    public Team getTeam(){
        return team;
    }

    public float getX(){
        return x;
    }

    public float getY(){
        return y;
    }

    public Tile getClosestCore(){
        return Vars.state.teams == null ? null : Vars.state.teams.closestCore(x, y, team);
    }

    // ---- 建造队列（真正的队列挂在被控制的单位上，见 BaseUnit#updateBuilding） ----

    /** 追加一条建造/拆除请求。玩家死亡（没有单位）时无法建造。 */
    public void addBuildRequest(com.phoenix.game.world.BuildRequest req){
        if(unit != null) unit.addBuildRequest(req);
    }

    public void clearBuilding(){
        if(unit != null) unit.clearBuilding();
    }

    public boolean isBuilding(){
        return unit != null && unit.isBuilding();
    }

    /** 每帧更新：同步位置 + 死亡后在最近核心处重生（对应原版 updateRespawning）。 */
    public void update(){
        if(!isDead()){
            x = unit.x;
            y = unit.y;
            deadTime = 0f;
            return;
        }

        //没有核心就无法复活（与原版一致）
        Tile core = getClosestCore();
        if(core == null) return;

        deadTime += Time.delta();

        if(deadTime >= respawnTime && Vars.control != null){
            Vars.control.spawnPlayerUnit(team);
        }
    }

    /** 增加经验；满级不再积累。 */
    public void addXp(int amount){
        if(level >= maxLevel) return;
        xp += amount;
        while(level < maxLevel && xp >= xpNeed()){
            xp -= xpNeed();
            level++;
        }
    }

    /** @return 升到下一级所需经验。 */
    public int xpNeed(){
        return (int)(baseXpNeed * Math.pow(2, level - 1));
    }

    /** @return 玩家命中加成倍率（每级 +10%）。 */
    public float damageMultiplier(){
        return 1f + (level - 1) * 0.1f;
    }

    /** @return 玩家最大生命倍率（每级 +10%）。 */
    public float healthMultiplier(){
        return 1f + (level - 1) * 0.1f;
    }
}
