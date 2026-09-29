package com.phoenix.game.mod;

import com.badlogic.gdx.audio.Sound;
import com.phoenix.game.core.MockSound;
import com.phoenix.game.util.ArcAnnotate.NonNull;

/**
 * 音频代理：模组音频<b>异步加载</b>完成前先用 {@link MockSound} 占位，
 * 加载完成后把真实 {@link Sound} 回填进 {@link #sound}。
 * 对应原版 {@code mindustry.mod.ModLoadingSound}。
 *
 * <p><b>与原版的差异（arc.audio.Sound → libgdx Sound）</b>：
 * <ul>
 *   <li>原版实现的是 {@code arc.audio.Sound}：多出 {@code at(x,y)}/{@code at(x,y,pitch)}/
 *       {@code calcPan/calcVolume/calcFalloff}/{@code isDisposed()}，且所有 {@code soundId} 是 <b>int</b>、
 *       返回值是 <b>int</b>。libgdx 的 {@link Sound} <b>没有</b>这些方法与概念，
 *       {@code soundId} 与返回值都是 <b>long</b> → 这些重载全部删除。</li>
 *   <li>原版 {@code @NonNull} 来自 {@code arc.util.ArcAnnotate}；本项目改用
 *       {@code com.phoenix.game.util.ArcAnnotate.NonNull}（只是 IDE 提示，CLASS 保留期）。</li>
 * </ul>
 * <p>TODO 音频系统在本项目尚未启用（{@code Sounds.load()} 在 {@code ClientLauncher} 里是注释状态），
 * 所以 {@link #sound} 目前永远是 {@link MockSound}；等音频启用后由 {@code ContentParser} 的
 * {@code Sound} 解析器回填（见 {@code ContentParser} 里对应的 TODO）。
 */
public class ModLoadingSound implements Sound{
    /** 实际音频；加载完成前是 {@link MockSound}。 */
    public @NonNull Sound sound = new MockSound();

    @Override
    public long play(){
        return sound.play();
    }

    @Override
    public long play(float volume){
        return sound.play(volume);
    }

    @Override
    public long play(float volume, float pitch, float pan){
        return sound.play(volume, pitch, pan);
    }

    @Override
    public long loop(){
        return sound.loop();
    }

    @Override
    public long loop(float volume){
        return sound.loop(volume);
    }

    @Override
    public long loop(float volume, float pitch, float pan){
        return sound.loop(volume, pitch, pan);
    }

    @Override
    public void stop(){
        sound.stop();
    }

    @Override
    public void pause(){
        sound.pause();
    }

    @Override
    public void resume(){
        sound.resume();
    }

    @Override
    public void dispose(){
        sound.dispose();
    }

    @Override
    public void stop(long soundId){
        sound.stop(soundId);
    }

    @Override
    public void pause(long soundId){
        sound.pause(soundId);
    }

    @Override
    public void resume(long soundId){
        sound.resume(soundId);
    }

    @Override
    public void setLooping(long soundId, boolean looping){
        sound.setLooping(soundId, looping);
    }

    @Override
    public void setPitch(long soundId, float pitch){
        sound.setPitch(soundId, pitch);
    }

    @Override
    public void setVolume(long soundId, float volume){
        sound.setVolume(soundId, volume);
    }

    @Override
    public void setPan(long soundId, float pan, float volume){
        sound.setPan(soundId, pan, volume);
    }
}
