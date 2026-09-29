package com.phoenix.game.game;

import com.badlogic.gdx.audio.Sound;
import com.phoenix.game.core.Time;
import com.phoenix.game.math.Mathf;

/**
 * A simple class for playing a looping sound at a position.
 * <p><b>本项目与 arc 的差异</b>：arc 的 {@code Sound} 自带 {@code calcVolume(x, y)}（距离衰减）与
 * {@code calcPan(x, y)}（左右声道），libgdx 的 {@link Sound} 没有这两个方法 —— 暂时按
 * 「不衰减、居中」处理。{@code loop/stop/setPan} 的签名一致，只是 id 从 arc 的 {@code int}
 * 变成 libgdx 的 {@code long}。
 */
public class SoundLoop{
    private static final float fadeSpeed = 0.05f;

    private final Sound sound;
    private int id = -1;
    private float volume, baseVolume;

    public SoundLoop(Sound sound, float baseVolume){
        this.sound = sound;
        this.baseVolume = baseVolume;
    }

    public void update(float x, float y, boolean play){
        if(baseVolume < 0) return;

        //TODO 原版 sound.calcVolume(x, y) / sound.calcPan(x, y)（libgdx 的 Sound 没有这两个方法）
        float calcVolume = 1f, calcPan = 0f;

        if(id < 0){
            if(play){
                //libgdx 的 loop 返回 long，arc 返回 int
                id = (int)sound.loop(calcVolume * volume * baseVolume, 1f, calcPan);
            }
        }else{
            //fade the sound in or out
            if(play){
                volume = Mathf.clamp(volume + fadeSpeed * Time.delta());
            }else{
                volume = Mathf.clamp(volume - fadeSpeed * Time.delta());
                if(volume <= 0.001f){
                    sound.stop(id);
                    id = -1;
                    return;
                }
            }
            sound.setPan(id, calcPan, calcVolume * volume * baseVolume);
        }
    }

    public void stop(){
        if(id != -1){
            sound.stop(id);
            id = -1;
            volume = baseVolume = -1;
        }
    }
}
