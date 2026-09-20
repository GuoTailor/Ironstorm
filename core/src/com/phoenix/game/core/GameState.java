package com.phoenix.game.core;
import com.phoenix.game.game.Rules;
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
    /** 当前波次，从 1 开始。 */
    public int wave = 1;
    /** 距离下一波的剩余 tick。 */
    public float wavetime;
    /** 场上敌方（波次阵营）单位数量。 */
    public int enemies;

    public boolean isPlaying(){
        return state == State.playing;
    }

    public boolean isPaused(){
        return state == State.paused;
    }

    public boolean isMenu(){
        return state == State.menu;
    }

    public State getState(){
        return state;
    }

    public void set(State state){
        this.state = state;
    }

    public enum State{
        menu, playing, paused
    }
}
