package com.phoenix.game.game;

/**
 * 游戏规则。参照 Mindustry mindustry.game.Rules 移植。
 * <p>字段已按原版回填（T2 批）；少数依赖本项目尚无的系统（光照 / 太阳能 / 标签 / 默认阵营）
 * 的字段只做"声明 + 存档占位"，消费点见各字段注释。
 */
public class Rules{
    /**
     * 是否开启波次（对应原版 {@code rules.waves}，原版默认 <b>false</b>）。
     * <p>原版默认关，由 {@code Gamemode.survival} / {@code zone.rules} / 地图 rules 置 true。
     * <p>⚠ 本项目 {@code Control.play()} / {@code Control.playZone()} 不套 Gamemode，且区域已换成
     * 程序生成器、没有"内置地图 rules 自带 waves=true"的兜底 → 这两条路径各自显式补
     * {@code rules.waves = true}（见对应方法），以保证战役仍有波次。
     */
    public boolean waves = false;
    /** 波次是否自动计时（false 时只能手动开波） */
    public boolean waveTimer = true;
    /** 每波间隔（tick，默认 120 秒，对应原版 {@code 60 * 60 * 2}） */
    public float waveSpacing = 60f * 60f * 2f;
    /** 是否要等场上敌人清空后才继续计时 */
    public boolean waitForWaveToEnd = false;
    /**
     * 开局赠予玩家的材料（对应原版 {@code rules.loadout}）。
     * <p><b>刻意偏离 + 原因</b>：原版默认 {@code Array.with(ItemStack.with(Items.copper, 100))}，本项目保留空数组。
     * 原因是本项目的 {@code Items.xxx} 是在 {@code Items.load()} 里才赋值的（原版是字段内联 new），而
     * {@code Vars.state = new GameState()}（→ {@code new Rules()}）发生在 Vars 类静态初始化时、早于
     * {@code Vars.content.load()} → 写成原版内联默认会捕获到 {@code Items.copper == null}。
     * <p>等价补偿：{@code Control.playMap()} 在 loadout 为空时补 {@code copper×100}（与原版默认等价）；
     * {@code Control.play()} 显式赋自己的开局材料；区域局用 {@code zone.getStartingItems()}（不读本字段）。
     */
    public com.phoenix.game.type.ItemStack[] loadout = {};
    /** 拆除返还比例 */
    public float deconstructRefundMultiplier = 0.5f;
    /** 波次编成 */
    public com.badlogic.gdx.utils.Array<com.phoenix.game.game.SpawnGroup> spawns = new com.badlogic.gdx.utils.Array<>();
    /** 敌方波次阵营 */
    public Team waveTeam = Team.crux;
    /** 出生点半径（对应原版 {@code rules.dropZoneRadius}，原版默认 300f） */
    public float dropZoneRadius = 300f;
    /** 单位生命倍率 */
    public float unitHealthMultiplier = 1f;
    /** 单位伤害倍率 */
    public float unitDamageMultiplier = 1f;
    /** 敌方是否作弊 */
    public boolean enemyCheat = false;
    /**
     * 沙盒模式：打开后建造菜单里会出现无限资源类方块（电力源/物品源/虚空等）。
     * <p>消费点：{@code BuildVisibility.sandboxOnly.visible()}（= 本字段）；对应方块把
     * {@code buildVisibility} 设为 {@code BuildVisibility.sandboxOnly}，建造栏经 {@code Block.isVisible()} 过滤。
     */
    public boolean sandbox = false;
    /**
     * 游戏目标是否为 PVP（对应原版 {@code rules.pvp}，默认 false）。
     * <p>{@code GameOverDialog} 的 PVP 分支读它（显示获胜队伍、只给"菜单"按钮）；
     * 原版注释还说它会让主机自动开服，本项目没有那条链路。
     */
    public boolean pvp = false;
    /**
     * 是否为编辑器模式（对应原版 {@code rules.editor}，默认 false）。
     * <p>{@code GameState.isEditor()} 就是读它；原版 {@code Gamemode.editor} 会把它置 true。
     */
    public boolean editor = false;
    /**
     * 是否开启教程（对应原版 {@code rules.tutorial}，默认 false）。
     * <p>置 true 时 {@code ClientLauncher} 每帧调 {@code Vars.control.tutorial.update()}。
     */
    public boolean tutorial = false;
    /**
     * 本局对应的战役区域（对应原版 {@code rules.zone}，默认 null）。
     * <p>{@link com.phoenix.game.core.World#getZone()}/{@code isZone()} 读的就是它。
     * 原版由 {@code Control.play()} 里的 {@code state.rules.zone = zone} 赋值；
     * 本项目战役生成器尚未接线 → 恒为 null（非 zone 分支）。
     * <p>注意本字段会进存档（{@code SaveVersion} 用 {@code JsonIO} 写整个 rules）：
     * {@code JsonIO} 已注册 {@code Zone} 序列化器（写 name / 按 name 反查），null 走 JSON null。
     */
    public com.phoenix.game.type.Zone zone;

    // ==================== T2 批：按原版回填的字段 ====================

    /** 敌方单位死亡是否掉落随机物品（对应原版 {@code unitDrops}）。消费点：{@code entities/units/UnitDrops}。 */
    public boolean unitDrops = true;
    /** 反应堆能否爆炸（对应原版 {@code reactorExplosions}）。消费点：{@code NuclearReactor}/{@code ImpactReactor}。 */
    public boolean reactorExplosions = true;
    /** 单位工厂生产速度倍率（对应原版 {@code unitBuildSpeedMultiplier}）。消费点：{@code UnitFactory}。 */
    public float unitBuildSpeedMultiplier = 1f;
    /** 玩家生命倍率（对应原版 {@code playerHealthMultiplier}）。消费点：{@code Player#healthMultiplier()}。 */
    public float playerHealthMultiplier = 1f;
    /** 建筑生命倍率（对应原版 {@code blockHealthMultiplier}）。消费点：{@code TileEntity#handleDamage()}。 */
    public float blockHealthMultiplier = 1f;
    /** 玩家伤害倍率（对应原版 {@code playerDamageMultiplier}）。消费点：{@code Player#damageMultiplier()}。 */
    public float playerDamageMultiplier = 1f;
    /** 玩家建造花费倍率（对应原版 {@code buildCostMultiplier}）。消费点：{@code Build#costMultiplier}（由 Control 赋值）。 */
    public float buildCostMultiplier = 1f;
    /** 建造速度倍率（对应原版 {@code buildSpeedMultiplier}）。消费点：{@code BuildBlock.BuildEntity#progressStep()}。 */
    public float buildSpeedMultiplier = 1f;
    /** 敌方核心周围禁建半径（世界单位，对应原版 {@code enemyCoreBuildRadius}）。消费点：{@code Build#validPlace}（全图最近敌核距离 &lt; 本值 + 方块半边长 → 拒绝放置）。⚠ 战役测试图（{@code Control#play()}）双方核心间距 60 格 = 480 世界单位，必须 &gt; 2×本值。 */
    public float enemyCoreBuildRadius = 400f;
    /** 玩家死亡后重生等待时间（tick，对应原版 {@code respawnTime}）。消费点：{@code Player#update()}。 */
    public float respawnTime = 60f * 4f;
    /** BOSS 波间隔倍率（对应原版 {@code bossWaveMultiplier}）。⚠ 本项目波次生成器尚未消费。 */
    public float bossWaveMultiplier = 3f;
    /** 发射波间隔倍率（对应原版 {@code launchWaveMultiplier}）。⚠ 本项目波次生成器尚未消费。 */
    public float launchWaveMultiplier = 2f;
    /** 是否限制重生次数（对应原版 {@code limitedRespawns}）。消费点：{@code Player#update()}。 */
    public boolean limitedRespawns = false;
    /** 每波可重生次数（对应原版 {@code respawns}）。消费点：{@code Player#respawns}。 */
    public int respawns = 5;
    /** 攻击模式（对应原版 {@code attackMode}）。消费点：{@code Gamemode#bestFit()}。 */
    public boolean attackMode = false;
    /** 是否允许判定游戏结束（对应原版 {@code canGameOver}）。消费点：{@code Logic#checkGameOver()}。 */
    public boolean canGameOver = true;
    /**
     * 禁建方块集合（对应原版 {@code bannedBlocks}）。消费点：{@code Build#validPlace()}。
     * <p>原版是 {@code ObjectSet<Block>}；{@code JsonIO} 已补元素类型注册（libgdx Json 原生支持 ObjectSet）。
     */
    public com.badlogic.gdx.utils.ObjectSet<com.phoenix.game.world.Block> bannedBlocks = new com.badlogic.gdx.utils.ObjectSet<>();
    /** 是否开启全局光照（对应原版 {@code lighting}）。⚠ B 类：本项目无光照渲染系统，仅存档占位。 */
    public boolean lighting = false;
    /** 环境光颜色（对应原版 {@code ambientLight}）。⚠ B 类：本项目无光照渲染系统，仅存档占位。 */
    public com.badlogic.gdx.graphics.Color ambientLight = new com.badlogic.gdx.graphics.Color(0.01f, 0.01f, 0.04f, 0.99f);
    /** 太阳能发电倍率（对应原版 {@code solarPowerMultiplier}，负数=按环境光）。⚠ B 类：依赖 lighting，本项目未消费。 */
    public float solarPowerMultiplier = -1f;
    /** 默认玩家阵营（对应原版 {@code defaultTeam}）。已消费：{@code MapGenerator.generate}（认核心）与 {@code Schematics.placeLoadout}（放置开局蓝图）。 */
    public Team defaultTeam = Team.sharded;
    /** 附加标签（对应原版 {@code tags}）。⚠ B 类：本项目尚无消费方，仅存档占位。 */
    public com.phoenix.game.struct.StringMap tags = new com.phoenix.game.struct.StringMap();
}
