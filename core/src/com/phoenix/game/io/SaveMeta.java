package com.phoenix.game.io;

import java.util.HashMap;

/**
 * 存档元数据（对应原版 {@code SaveMeta}）。
 * <p>存在存档文件的 meta 区（JSON），**读取时不需要解压/解析整张地图**，
 * 所以存档列表、地图列表可以只读它来显示名字/波次/时长。
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
    /** 规则快照。 */
    public RulesMeta rules = new RulesMeta();
    /** 自定义标签（供后续扩展：mod、挑战标记等）。 */
    public HashMap<String, String> tags = new HashMap<>();

    /**
     * 规则快照。
     * <p>只存**标量**：波次编成（spawns）、开局材料（loadout）、阵营对象在开局时由代码重建，不入档
     * （对应原版 spawns 由 DefaultWaves 重建的语义）。
     */
    public static class RulesMeta{
        public boolean waves = true;
        public boolean waveTimer = true;
        public boolean waitForWaveToEnd = false;
        public boolean enemyCheat = false;
        public boolean sandbox = false;
        public float waveSpacing = 60f * 60f;
        public float deconstructRefundMultiplier = 0.5f;
        public float dropZoneRadius = 200f;
        public float unitHealthMultiplier = 1f;
        public float unitDamageMultiplier = 1f;
        public int waveTeamId;
    }
}
