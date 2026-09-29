package com.phoenix.game.core;

import com.badlogic.gdx.audio.Music;

/**
 * 空实现的 {@link Music}，对应 libgdx 的 {@code com.badlogic.gdx.backends.lwjgl3.audio.mock.MockMusic}。
 * <p>与 {@link MockSound} 同理：不复用 libgdx 的 mock，避免 core 依赖桌面后端。
 * <p>刻意降级：音频系统尚未启用（{@code Musics.load()} 在 ClientLauncher 里是注释状态）。
 */
public class MockMusic implements Music{
    @Override
    public void play(){
    }

    @Override
    public void pause(){
    }

    @Override
    public void stop(){
    }

    @Override
    public boolean isPlaying(){
        return false;
    }

    @Override
    public void setLooping(boolean isLooping){
    }

    @Override
    public boolean isLooping(){
        return false;
    }

    @Override
    public void setVolume(float volume){
    }

    @Override
    public float getVolume(){
        return 0;
    }

    @Override
    public void setPan(float pan, float volume){
    }

    @Override
    public void setPosition(float position){
    }

    @Override
    public float getPosition(){
        return 0;
    }

    @Override
    public void dispose(){
    }

    @Override
    public void setOnCompletionListener(OnCompletionListener listener){
    }
}
