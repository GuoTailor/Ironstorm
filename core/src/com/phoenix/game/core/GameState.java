package com.phoenix.game.core;
import com.phoenix.game.Vars;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.game.EventType.StateChangeEvent;
import com.phoenix.game.game.Rules;
import com.phoenix.game.game.Stats;
import com.phoenix.game.game.Teams;

/**
 * 最小实现：游戏状态。参照 Mindustry mindustry.core.GameState 移植。
 */
public class GameState{
    private State state = State.menu;
    public Rules rules = new Rules();
    public Teams teams = new Teams();
    /** 是否已判定游戏结束（防止重复触发胜负）。 */
    public boolean gameOver;
    /**
     * 本局统计（对应原版 {@code state.stats}）：消灭敌人数/建造数/拆除数/摧毁数，结算框显示。
     * <p>⚠ 本项目目前<b>没有任何地方累加它</b>（原版在 {@code Control} 的 BlockBuildEnd/BlockDestroy/
     * UnitDestroy 监听器里累加，而 {@code EventType} 里那几个事件尚未移植）→ 结算框里这几项恒为 0，
     * 属于"字段已接、累加点待补"。只有 {@code wavesLasted} 由 {@code Logic.checkGameOver} 填。
     */
    public Stats stats = new Stats();
    /**
     * 本局是否以"发射（launch）"结束（对应原版 {@code state.launched}）。
     * <p>原版在 {@code Logic.launch()} 的延时任务里置 true，随后 {@code GameOverDialog} 把标题
     * 从 {@code $gameover} 换成 {@code $launch.title}。本项目尚无发射流程（LaunchPad 的发射未接线）
     * → 恒 false，但字段与 UI 分支已就位。
     */
    public boolean launched = false;
    /** 当前波次，从 1 开始。 */
    public int wave = 1;
    /** 距离下一波的剩余 tick。 */
    public float wavetime;
    /** 场上敌方（波次阵营）单位数量。 */
    public int enemies;
    /** 当前地图名（存档元数据用；程序生成图默认 "campaign"）。 */
    public String mapName = "campaign";
    /** 本局累计游玩时长（秒），存档元数据用（由 Logic 累加）。 */
    public float playtime;

    /** 当前是否为编辑器模式（对应原版 {@code GameState.isEditor()}，实现就是读 rules.editor）。 */
    public boolean isEditor(){
        return rules.editor;
    }

    /**
     * 当前的 BOSS 单位（对应原版 {@code GameState.boss()}：{@code unitGroup.find(u -> u.isBoss() && u.getTeam() == rules.waveTeam)}）。
     * <p>⚠ 本项目 {@code Units.units} 是 libgdx {@code Array}（没有 {@code find}）→ 下标循环取第一个匹配。
     * <p>⚠ 判定用 {@code BaseUnit.isBoss()}（= 带 {@code StatusEffects.boss}），**不是** {@code getType().boss}。
     * @return 场上第一个"波次阵营的 BOSS"单位；没有则为 null（HUD 的 BOSS 血条据此显隐）。
     */
    public BaseUnit boss(){
        for(int i = 0; i < Units.units.size; i++){
            BaseUnit unit = Units.units.get(i);
            if(unit.isBoss() && unit.getTeam() == rules.waveTeam){
                return unit;
            }
        }
        return null;
    }

    public boolean isPlaying(){
        return state == State.playing;
    }

    /**
     * 当前是否处于"暂停"（对应原版 {@code GameState.isPaused()}，原版 GameState.java:41-43）：
     * {@code (is(State.paused) && !net.active()) || (gameOver && !net.active())}。
     * <p>本项目没有 {@code net.active()} → 等价物是"网络已激活" = {@code Vars.isClient() || Vars.isServer()}。
     * <p>⚠ 补上 gameOver 分支后，{@code Logic.update()} 里的 {@code !isPaused()} 守卫才能在
     * "已判胜负、但 state 仍是 playing"期间冻结仿真（原版即如此；本项目 state 在 gameOver 时仍是 playing）。
     */
    public boolean isPaused(){
        boolean netActive = Vars.isClient() || Vars.isServer();
        return (state == State.paused && !netActive) || (gameOver && !netActive);
    }

    public boolean isMenu(){
        return state == State.menu;
    }

    public State getState(){
        return state;
    }

    /**
     * 切换游戏状态（对应原版 {@code GameState.set(State astate)}，原版 GameState.java:32-35）。
     * <p>原版顺序是**先 fire 再赋值**：{@code Events.fire(new StateChangeEvent(state, astate)); state = astate;}
     * —— 监听器里读 {@code getState()} 拿到的是**旧**状态（本项目 Events.fire 同步，行为与原版一致）。
     * <p>消费方（原版）：{@code Control} 构造器里的 StateChangeEvent 监听器（切状态时停掉所有音效 + updateRPC）。
     * 本项目该监听器尚未移植（音频未启用），此处只保证事件形状与派发点与原版一致。
     */
    public void set(State astate){
        Events.fire(new StateChangeEvent(this.state, astate));
        this.state = astate;
    }

    public enum State{
        menu, playing, paused
    }
}
