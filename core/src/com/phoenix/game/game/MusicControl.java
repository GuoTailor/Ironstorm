package com.phoenix.game.game;

import com.badlogic.gdx.audio.Music;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Interval;
import com.phoenix.game.core.Time;
import com.phoenix.game.game.EventType.ClientLoadEvent;
import com.phoenix.game.game.EventType.WaveEvent;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.struct.Array;
import com.phoenix.game.util.ArcAnnotate.Nullable;
import com.phoenix.game.world.Tile;
import io.anuke.mindustry.gen.Musics;

import static com.phoenix.game.Vars.*;

/**
 * Controls playback of multiple music tracks.
 * <p><b>本项目与 arc 的差异</b>：arc 的 {@code Music} 换成 libgdx {@link Music}
 * （{@code setCompletionListener} → {@code setOnCompletionListener}）。
 */
public class MusicControl{
    private static final float finTime = 120f, foutTime = 120f, musicInterval = 60 * 60 * 3f, musicChance = 0.6f, musicWaveChance = 0.5f;

    /** normal, ambient music, plays at any time */
    public Array<Music> ambientMusic = Array.with();
    /** darker music, used in times of conflict  */
    public Array<Music> darkMusic = Array.with();
    private Music lastRandomPlayed;
    private Interval timer = new Interval();
    private @Nullable Music current;
    private float fade;
    private boolean silenced;

    public MusicControl(){
        Events.on(ClientLoadEvent.class, e -> reload());

        //only run music 10 seconds after a wave spawns
        Events.on(WaveEvent.class, e -> Time.run(60f * 10f, () -> {
            if(Mathf.chance(musicWaveChance)){
                playRandom();
            }
        }));
    }

    private void reload(){
        current = null;
        fade = 0f;
        ambientMusic = Array.with(Musics.game1, Musics.game3, Musics.game4, Musics.game6);
        darkMusic = Array.with(Musics.game2, Musics.game5, Musics.game7);
    }

    /** Update and play the right music track.*/
    public void update(){
        //TODO 原版依赖 Vars.ui.deploy / Vars.ui.editor 的 isShown()（UI 系统尚未移植），
        // 所以菜单态下只会播 Musics.menu，发射场/编辑器音乐暂时不播。
        if(state.isMenu()){
            silenced = false;
            play(Musics.menu);
        }else{
            //this just fades out the last track to make way for ingame music
            silence();

            //play music at intervals
            if(timer.get(musicInterval)){
                //chance to play it per interval
                if(Mathf.chance(musicChance)){
                    playRandom();
                }
            }
        }
    }

    /** Plays a random track.*/
    private void playRandom(){
        if(isDark()){
            playOnce(darkMusic.random(lastRandomPlayed));
        }else{
            playOnce(ambientMusic.random(lastRandomPlayed));
        }
    }

    /** Whether to play dark music.*/
    private boolean isDark(){
        //原版 MusicControl.java:86 state.teams.get(player.getTeam()).hasCore() && ...core().healthf() < 0.85f
        //（已接线 S3：Teams.cores/Team.hasCore/core 已存在）
        if(player != null && player.getTeam().hasCore()){
            Tile core = player.getTeam().core();
            if(core != null && core.entity != null && core.entity.healthf() < 0.85f){
                //core damaged -> dark
                return true;
            }
        }

        //it may be dark based on wave
        if(Mathf.chance((float)(Math.log10((state.wave - 17f)/19f) + 1) / 4f)){
            return true;
        }

        //dark based on enemies
        return Mathf.chance(state.enemies / 70f + 0.1f);
    }

    /** Plays and fades in a music track. This must be called every frame.
     * If something is already playing, fades out that track and fades in this new music.*/
    private void play(@Nullable Music music){
        if(!shouldPlay()) return;

        //update volume of current track
        if(current != null){
            current.setVolume(fade * Core.settings.getInt("musicvol", 100) / 100f);
        }

        //do not update once the track has faded out completely, just stop
        if(silenced){
            return;
        }

        if(current == null && music != null){
            //begin playing in a new track
            current = music;
            current.setLooping(true);
            current.setVolume(fade = 0f);
            current.play();
            silenced = false;
        }else if(current == music && music != null){
            //fade in the playing track
            fade = Mathf.clamp(fade + Time.delta()/finTime);
        }else if(current != null){
            //fade out the current track
            fade = Mathf.clamp(fade - Time.delta()/foutTime);

            if(fade <= 0.01f){
                //stop current track when it hits 0 volume
                current.stop();
                current = null;
                silenced = true;
                if(music != null){
                    //play newly scheduled track
                    current = music;
                    current.setVolume(fade = 0f);
                    current.setLooping(true);
                    current.play();
                    silenced = false;
                }
            }
        }
    }

    /** Plays a music track once and only once. If something is already playing, does nothing.*/
    private void playOnce(Music music){
        if(current != null || music == null || !shouldPlay()) return; //do not interrupt already-playing tracks

        //save last random track played to prevent duplicates
        lastRandomPlayed = music;

        //set fade to 1 and play it, stopping the current when it's done
        fade = 1f;
        current = music;
        current.setVolume(1f);
        current.setLooping(false);
        current.setOnCompletionListener(m -> {
            if(current == m){
                current = null;
                fade = 0f;
            }
        });
        current.play();
    }

    private boolean shouldPlay(){
        return Core.settings.getInt("musicvol", 100) > 0;
    }

    /** Fades out the current track, unless it has already been silenced. */
    private void silence(){
        play(null);
    }
}
