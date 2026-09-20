package com.phoenix.game.game;

/**
 * 最小实现：游戏规则。参照 Mindustry mindustry.game.Rules 移植（只保留已用到的字段）。
 */
public class Rules{
    /** 是否开启波次 */
    public boolean waves = true;
    /** 波次是否自动计时（false 时只能手动开波） */
    public boolean waveTimer = true;
    /** 每波间隔（tick，默认 60 秒） */
    public float waveSpacing = 60f * 60f;
    /** 是否要等场上敌人清空后才继续计时 */
    public boolean waitForWaveToEnd = false;
    /** 开局赠予玩家的材料（对应原版 rules.loadout，在 Control.play 里按已加载的物品赋值） */
    public com.phoenix.game.type.ItemStack[] loadout = {};
    /** 拆除返还比例 */
    public float deconstructRefundMultiplier = 0.5f;
    /** 波次编成 */
    public com.badlogic.gdx.utils.Array<com.phoenix.game.game.SpawnGroup> spawns = new com.badlogic.gdx.utils.Array<>();
    /** 敌方波次阵营 */
    public Team waveTeam = Team.crux;
    /** 出生点半径 */
    public float dropZoneRadius = 200f;
    /** 单位生命倍率 */
    public float unitHealthMultiplier = 1f;
    /** 单位伤害倍率 */
    public float unitDamageMultiplier = 1f;
    /** 敌方是否作弊 */
    public boolean enemyCheat = false;
    /**
     * 沙盒模式：打开后建造菜单里会出现无限资源类方块（电力源/物品源/虚空等）。
     * <p>对应原版按 {@code BuildVisibility.sandboxOnly} 过滤方块。
     */
    public boolean sandbox = false;
}
