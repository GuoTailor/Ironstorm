package com.phoenix.game.game;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ObjectMap;
import com.phoenix.game.core.Core;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.Position;

import static com.phoenix.game.Vars.*;

/**
 * 循环音效聚合器：同一帧里对同一个 {@link Sound} 的多次 {@link #play} 请求会被合并成
 * 一个循环实例，按平均声源位置更新音量与声道。
 * <p><b>本项目与 arc 的差异</b>：arc 的 {@code Sound} 自带 {@code calcFalloff(x, y)} /
 * {@code calcPan(x, y)}（距离衰减与左右声道），libgdx 的 {@link Sound} 没有这两个方法 ——
 * 暂时按「不衰减、居中」处理，等音频系统真正启用后再补。
 */
public class LoopControl{
    private ObjectMap<Sound, SoundData> sounds = new ObjectMap<>();

    public void play(Sound sound, Position pos, float volume){
        if(headless) return;

        //TODO 原版 sound.calcFalloff(pos.getX(), pos.getY())（libgdx 的 Sound 没有该方法）→ 暂时不衰减
        float baseVol = 1f;
        float vol = baseVol * volume;

        //libgdx 的 ObjectMap 没有 arc 的 getOr(key, Prov)，手动建默认值
        SoundData data = sounds.get(sound);
        if(data == null){
            data = new SoundData();
            sounds.put(sound, data);
        }
        data.volume += vol;
        data.volume = Mathf.clamp(data.volume, 0f, 1f);
        data.total += baseVol;
        data.sum.add(pos.getX() * baseVol, pos.getY() * baseVol);
    }

    public void update(){
        float avol = Core.settings.getInt("ambientvol", 100) / 100f;

        //libgdx 的 ObjectMap 没有 arc 的 each(Cons2)，用 entries() 遍历；
        //这里不嵌套其它 Array 迭代器，所以 for-each 是安全的。
        for(ObjectMap.Entry<Sound, SoundData> entry : sounds.entries()){
            Sound sound = entry.key;
            SoundData data = entry.value;

            data.curVolume = Mathf.lerpDelta(data.curVolume, data.volume * avol, 0.2f);

            boolean play = data.curVolume > 0.01f;
            //TODO 原版是 Mathf.zero(data.total, 0.0001f) ? 0f : sound.calcPan(sum.x / total, sum.y / total)：
            // ① Mathf 尚未补 zero（若将来补上，判零分支本身就是 0f）② libgdx 的 Sound 没有 calcPan → 声道固定居中。
            float pan = 0f;
            if(data.soundID <= 0){
                if(play){
                    //libgdx 的 loop 返回 long，arc 返回 int
                    data.soundID = (int)sound.loop(data.curVolume, 1f, pan);
                }
            }else{
                if(data.curVolume <= 0.01f){
                    sound.stop();
                    data.soundID = -1;
                    //对应原版 lambda 里的 return：跳过末尾的音量/坐标清零
                    continue;
                }
                sound.setPan(data.soundID, pan, data.curVolume);
            }

            data.volume = 0f;
            data.total = 0f;
            data.sum.setZero();
        }
    }

    private class SoundData{
        float volume;
        float total;
        Vector2 sum = new Vector2();

        int soundID;
        float curVolume;
    }
}
