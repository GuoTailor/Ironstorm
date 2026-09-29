package com.phoenix.game.core;

import com.badlogic.gdx.audio.Sound;

/**
 * 空实现的 {@link Sound}，对应 libgdx 的 {@code com.badlogic.gdx.backends.lwjgl3.audio.mock.MockSound}。
 * <p>原版 Mindustry 用的是 arc 自己的 {@code arc.audio.Sound}，注解处理器生成的
 * {@code io.anuke.mindustry.gen.Sounds} 需要一个"还没有真正加载音频时的占位对象"。
 * <p>这里不复用 libgdx 的那个 mock：它位于 gdx-backend-lwjgl3 里，把它拖进 core 会让
 * Android / iOS 包必须带上整个桌面后端（且运行时 {@code Sounds.<clinit>} 会直接
 * {@code NoClassDefFoundError}）。放在 core 里则三端通用。
 * <p>刻意降级：音频系统尚未启用（{@code Sounds.load()} 在 ClientLauncher 里是注释状态），
 * 目前所有音效都是这个空实现，游戏是静音的。
 */
public class MockSound implements Sound{
    @Override
    public long play(){
        return 0;
    }

    @Override
    public long play(float volume){
        return 0;
    }

    @Override
    public long play(float volume, float pitch, float pan){
        return 0;
    }

    @Override
    public long loop(){
        return 0;
    }

    @Override
    public long loop(float volume){
        return 0;
    }

    @Override
    public long loop(float volume, float pitch, float pan){
        return 0;
    }

    @Override
    public void stop(){
    }

    @Override
    public void pause(){
    }

    @Override
    public void resume(){
    }

    @Override
    public void dispose(){
    }

    @Override
    public void stop(long soundId){
    }

    @Override
    public void pause(long soundId){
    }

    @Override
    public void resume(long soundId){
    }

    @Override
    public void setLooping(long soundId, boolean looping){
    }

    @Override
    public void setPitch(long soundId, float pitch){
    }

    @Override
    public void setVolume(long soundId, float volume){
    }

    @Override
    public void setPan(long soundId, float pan, float volume){
    }
}
