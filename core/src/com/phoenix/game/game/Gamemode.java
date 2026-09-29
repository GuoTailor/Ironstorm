package com.phoenix.game.game;

import com.phoenix.game.Vars;
import com.phoenix.game.func.Boolf;
import com.phoenix.game.func.Cons;
import com.phoenix.game.maps.Map;
import com.phoenix.game.world.meta.I18n;

/**
 * Defines preset rule sets.
 * <p><b>与本项目的差异（刻意）</b>：
 * <ul>
 *   <li>三个模式的 {@code validator} 已按原版恢复：{@link Map} 现在带
 *       {@code spawns}/{@code teams} 元数据（见 {@link com.phoenix.game.maps.Maps}）。
 *       <b>注意</b>：这两个值只在「导出地图」时统计并写进存档 meta，扫描目录时由
 *       {@code Maps.readStats} 恢复；手写/外部来源的 {@code .msav} 没有这两个 tag，
 *       会得到 {@code spawns=0}/{@code teams=空} —— 与原版「没有缓存文件就是 0」一致。</li>
 *   <li>{@link Rules} 字段已按原版回填（T2 批），各模式的规则集照原版赋值；
 *       本项目没有 {@code rules.infiniteResources}，用 {@code rules.sandbox} 等价表达（见 {@code sandbox} 模式注释）。</li>
 * </ul>
 */
public enum Gamemode{
    survival(rules -> {
        rules.waveTimer = true;
        rules.waves = true;
        rules.unitDrops = true;
    }, map -> map.spawns > 0),
    sandbox(rules -> {
        //本项目用 rules.sandbox 顶替原版 rules.infiniteResources（两者语义在本工程等价）
        rules.sandbox = true;
        rules.waves = true;
        rules.waveTimer = false;
        rules.respawnTime = 0f;
    }),
    attack(rules -> {
        rules.unitDrops = true;
        rules.attackMode = true;
    }, map -> map.teams.contains(Vars.state.rules.waveTeam.id)),
    pvp(rules -> {
        rules.pvp = true;
        rules.enemyCoreBuildRadius = 600f;
        rules.respawnTime = 60 * 10;
        rules.buildCostMultiplier = 1f;
        rules.buildSpeedMultiplier = 1f;
        rules.playerDamageMultiplier = 0.33f;
        rules.playerHealthMultiplier = 0.5f;
        rules.unitBuildSpeedMultiplier = 2f;
        rules.unitHealthMultiplier = 3f;
        rules.attackMode = true;
    }, map -> map.teams.size > 1),
    editor(true, rules -> {
        //infiniteResources → sandbox（本项目等价表达）
        rules.sandbox = true;
        rules.editor = true;
        rules.waves = false;
        rules.enemyCoreBuildRadius = 0f;
        rules.waveTimer = false;
        rules.respawnTime = 0f;
    });

    private final Cons<Rules> rules;
    private final Boolf<Map> validator;

    public final boolean hidden;
    public final static Gamemode[] all = values();

    Gamemode(Cons<Rules> rules){
        this(false, rules);
    }

    Gamemode(boolean hidden, Cons<Rules> rules){
         this(hidden, rules, m -> true);
    }

    Gamemode(Cons<Rules> rules, Boolf<Map> validator){
        this(false, rules, validator);
    }

    Gamemode(boolean hidden, Cons<Rules> rules, Boolf<Map> validator){
        this.rules = rules;
        this.hidden = hidden;
        this.validator = validator;
    }

    public static Gamemode bestFit(Rules rules){
        //照原版顺序：pvp → editor → attackMode → infiniteResources（本项目用 sandbox 等价表达）
        if(rules.pvp){
            return pvp;
        }else if(rules.editor){
            return editor;
        }else if(rules.attackMode){
            return attack;
        }else if(rules.sandbox){
            return sandbox;
        }else{
            return survival;
        }
    }

    /** Applies this preset to this ruleset. */
    public Rules apply(Rules in){
        rules.get(in);
        return in;
    }

    /** @return whether this mode can be played on the specified map. */
    public boolean valid(Map map){
        return validator.get(map);
    }

    public String description(){
        //原版是 Core.bundle.get("mode." + name() + ".description")；本项目 bundle 缺键返回 ???key???，
        //所以统一走 I18n.get 的双形态判定，缺键回退到模式名。
        return I18n.get("mode." + name() + ".description", name());
    }

    @Override
    public String toString(){
        return I18n.get("mode." + name() + ".name", name());
    }
}
