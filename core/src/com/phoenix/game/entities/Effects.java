package com.phoenix.game.entities;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Time;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.Position;

/**
 * 最小实现：特效系统。参照 Mindustry mindustry.entities.Effects 移植。
 * 特效以数据形式记录下来，由 Renderer 统一绘制；支持屏幕震动。
 */
public class Effects{

    private static final Array<EffectState> active = new Array<>();
    private static final EffectContainer container = new EffectContainer();
    /** 同时存在的特效上限 */
    public static final int maxEffects = 512;

    /** 当前震动强度 */
    public static float shakeIntensity;
    /** 剩余震动时间（tick） */
    public static float shakeTime;

    /** A single effect definition. */
    public static class Effect{
        public final float lifetime;
        /** Clip size. */
        public float size;
        public final EffectRenderer draw;

        public Effect(float life, float clipsize, EffectRenderer draw){
            this.lifetime = life;
            this.size = clipsize;
            this.draw = draw;
        }

        public Effect(float life, EffectRenderer draw){
            this(life, 28f, draw);
        }
    }

    public static class EffectContainer{
        public float x, y, time, lifetime, rotation;
        public Color color;
        public Object data;

        public void set(float x, float y, float time, float lifetime, float rotation, Color color, Object data){
            this.x = x;
            this.y = y;
            this.time = time;
            this.lifetime = lifetime;
            this.rotation = rotation;
            this.color = color;
            this.data = data;
        }

        public float fin(){
            return lifetime == 0f ? 1f : time / lifetime;
        }
    }

    public interface EffectRenderer{
        void render(EffectContainer effect);
    }

    public static void effect(Effect effect, float x, float y){
        effect(effect, Color.WHITE, x, y, 0f, null);
    }

    public static void effect(Effect effect, float x, float y, float rotation){
        effect(effect, Color.WHITE, x, y, rotation, null);
    }

    public static void effect(Effect effect, Position loc){
        effect(effect, Color.WHITE, loc.getX(), loc.getY(), 0f, null);
    }

    public static void effect(Effect effect, Color color, float x, float y){
        effect(effect, color, x, y, 0f, null);
    }

    public static void effect(Effect effect, Color color, float x, float y, float rotation){
        effect(effect, color, x, y, rotation, null);
    }

    public static void effect(Effect effect, Color color, float x, float y, float rotation, Object data){
        if(effect == null || effect.lifetime <= 0f) return;

        if(active.size >= maxEffects){
            active.removeIndex(0);
        }

        EffectState state = new EffectState();
        state.effect = effect;
        state.color = color == null ? Color.WHITE : color;
        state.x = x;
        state.y = y;
        state.rotation = rotation;
        state.data = data;
        active.add(state);
    }

    /** 推进所有特效，移除过期特效。 */
    public static void update(){
        for(int i = 0; i < active.size; i++){
            EffectState state = active.get(i);
            state.time += Time.delta();

            if(state.time >= state.effect.lifetime){
                active.removeIndex(i);
                i--;
            }
        }

        if(shakeTime > 0f){
            shakeTime -= Time.delta();
            if(shakeTime <= 0f){
                shakeTime = 0f;
                shakeIntensity = 0f;
            }
        }
    }

    /** 绘制所有特效（需要在 batch.begin() 之后调用）。 */
    public static void render(){
        for(int i = 0; i < active.size; i++){
            EffectState state = active.get(i);
            container.set(state.x, state.y, state.time, state.effect.lifetime, state.rotation, state.color, state.data);
            state.effect.draw.render(container);
        }
    }

    public static void clear(){
        active.clear();
        shakeTime = shakeIntensity = 0f;
    }

    public static void shake(float intensity, float duration){
        shake(intensity, duration, Core.camera == null ? 0f : Core.camera.position.x, Core.camera == null ? 0f : Core.camera.position.y);
    }

    /** Default value is 1000. Higher numbers mean more powerful shake (less falloff). */
    public static void shake(float intensity, float duration, float x, float y){
        if(intensity <= 0.001f || duration <= 0.001f) return;

        float distance = Core.camera == null ? 1f : Core.camera.position.dst(x, y, 0f);
        if(distance < 1f) distance = 1f;

        shakeIntensity = Math.max(shakeIntensity, intensity * Mathf.clamp(1f - distance / 1000f));
        shakeTime = Math.max(shakeTime, duration);
    }

    public static void shake(float intensity, float duration, Position loc){
        shake(intensity, duration, loc.getX(), loc.getY());
    }

    private static class EffectState{
        Effect effect;
        Color color = Color.WHITE;
        float x, y, rotation, time;
        Object data;
    }
}
