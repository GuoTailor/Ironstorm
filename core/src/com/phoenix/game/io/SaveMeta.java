package com.phoenix.game.io;

import java.util.HashMap;

/**
 * 存档元数据。本项目**自有格式**（承担原版 {@code mindustry.io.SaveMeta} 的角色，但字段集不同）。
 * <p>存在存档文件的 meta 区（JSON），**读取时不需要解压/解析整张地图**，
 * 所以存档列表、地图列表可以只读它来显示名字/波次/时长。
 * <p><b>与原版 {@code SaveMeta} 的差异</b>：原版还带 version/build/mods/stats 与 {@code Map} 对象；
 * 本项目只存这里的标量 + {@link RulesMeta} 白名单（**非反射**，新增 Rules 字段必须手动加进白名单才落盘）。
 *
 * <p>⚠⚠ <b>白名单是手工维护的</b>：{@code Rules} 加字段**不会**自动落盘。加了新字段要同时改三处：
 * {@link RulesMeta} 加字段 → {@link SaveIO#writeMeta} 写 → {@link SaveIO#applyMeta} 读。
 * 漏掉的表现是"读档后该字段回到默认值"，不会报错（见 转换.md）。
 */
public class SaveMeta{
    /** 地图名。 */
    public String mapname = "unknown";
    /** 当前波次。 */
    public int wave = 1;
    /** 距下一波的剩余 tick。 */
    public float wavetime;
    /** 本局累计游玩时长（秒）。 */
    public long playtime;
    /** 存档写入时间（毫秒时间戳）。 */
    public long saved;
    /** 地图尺寸。 */
    public int width, height;
    /** 是否已"发射"（战役进度标记，对应原版 {@code state.launched}）。 */
    public boolean launched;
    /** 规则快照。 */
    public RulesMeta rules = new RulesMeta();
    /** 本局统计快照（对应原版 {@code state.stats}）。 */
    public StatsMeta stats = new StatsMeta();
    /** 自定义标签（供后续扩展：mod、挑战标记等）。 */
    public HashMap<String, String> tags = new HashMap<>();

    /**
     * 规则快照。
     * <p>只存**标量**（+ zone 名 / 阵营 id / 禁建方块名）：波次编成（spawns）、开局材料（loadout）、
     * 阵营对象在开局时由代码重建，不入档（对应原版 spawns 由 DefaultWaves 重建的语义）。
     * <p>⚠ 与 {@code game/Rules} 逐字段对齐；{@code Rules} 新增字段必须同步加到这里 + {@link SaveIO} 的读写。
     */
    public static class RulesMeta{
        // ---- 原有 11 项 ----
        public boolean waves = false;
        public boolean waveTimer = true;
        public boolean waitForWaveToEnd = false;
        public boolean enemyCheat = false;
        public boolean sandbox = false;
        public float waveSpacing = 60f * 60f * 2f;
        public float deconstructRefundMultiplier = 0.5f;
        public float dropZoneRadius = 300f;
        public float unitHealthMultiplier = 1f;
        public float unitDamageMultiplier = 1f;
        /** 敌方波次阵营 id。⚠ {@code -1} = 未记录（保持 {@code Rules} 自身默认），
         *  不能用 0 —— {@code Team.get(0)} 是 {@code derelict}（无阵营），波次会刷不出敌人。 */
        public int waveTeamId = -1;

        // ---- 补充：玩法开关（boolean）----
        public boolean pvp = false;
        public boolean editor = false;
        public boolean tutorial = false;
        public boolean unitDrops = true;
        public boolean reactorExplosions = true;
        public boolean limitedRespawns = false;
        public boolean attackMode = false;
        public boolean canGameOver = true;
        /** ⚠ 本项目无光照渲染系统（B 类），仅作存档占位。 */
        public boolean lighting = false;

        // ---- 补充：倍率/数值（float）----
        public float unitBuildSpeedMultiplier = 1f;
        public float playerHealthMultiplier = 1f;
        public float blockHealthMultiplier = 1f;
        public float playerDamageMultiplier = 1f;
        public float buildCostMultiplier = 1f;
        public float buildSpeedMultiplier = 1f;
        public float enemyCoreBuildRadius = 400f;
        public float respawnTime = 60f * 4f;
        public float bossWaveMultiplier = 3f;
        public float launchWaveMultiplier = 2f;
        /** ⚠ 依赖 lighting（本项目未消费），仅作存档占位。 */
        public float solarPowerMultiplier = -1f;

        // ---- 补充：整数 ----
        public int respawns = 5;
        /** 默认玩家阵营（对应 {@code Rules.defaultTeam}，存 id）。
         *  ⚠ {@code -1} = 未记录（保持 {@code Rules} 自身默认 sharded），不能用 0 —— 0 是 {@code derelict}。 */
        public int defaultTeamId = -1;

        // ---- 补充：非标量（按名字持久化，避免内容 id 变动后错位）----
        /** 本局所属战役区域名（null = 非区域局）。读回时按 name 反查 {@code Vars.content}。 */
        public String zoneName;
        /** 禁建方块名列表（对应 {@code Rules.bannedBlocks}）。 */
        public String[] bannedBlockNames;
    }

    /**
     * 本局统计快照（对应 {@code game/Stats}）。
     * <p>{@code itemsDelivered} 是 {@code ObjectIntMap<Item>}，libgdx Json 对泛型元素类型推断不稳
     * → 拆成两个**并行数组**（名字 + 数量），读回时按名字反查物品。
     */
    public static class StatsMeta{
        public int enemyUnitsDestroyed;
        public int wavesLasted;
        public long timeLasted;
        public int buildingsBuilt;
        public int buildingsDeconstructed;
        public int buildingsDestroyed;
        /** 已送达物品名（与 {@link #deliveredAmounts} 一一对应）。 */
        public String[] deliveredItems;
        /** 已送达数量。 */
        public int[] deliveredAmounts;
    }
}
