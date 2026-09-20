package com.phoenix.game.core;

import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;

import java.util.function.Supplier;

/**
 * create by GYH on 2025/3/14
 */
public class Time {
    /** Conversion factors for ticks to other unit values. */
    public static final float toSeconds = 60f, toMinutes = 60f * 60f, toHours = 60f * 60f * 60f;

    /** Global time values. Do not change. */
    public static float time, globalTime;

    public static final long nanosPerMilli = 1000000;

    private static double timeRaw, globalTimeRaw;

    private static final Supplier<Float> deltaimpl = () -> Math.min(Core.graphics.getDeltaTime() * 60f, 3f);

    /** 延迟任务队列（对应原版 Time 的 runs 列表）。 */
    private static final com.badlogic.gdx.utils.Array<DelayRun> runs = new com.badlogic.gdx.utils.Array<>();

    /** Runs a task with a delay of several ticks. If Time.clear() is called, this task will be cancelled. */
    public static void run(float delay, Runnable r){
        DelayRun run = Pools.obtain(DelayRun.class);
        run.finish = r;
        run.delay = delay;
        runs.add(run);
    }

    /** 取消所有待执行任务（切换到新地图时调用）。 */
    public static void clear(){
        for(DelayRun run : runs){
            Pools.free(run);
        }
        runs.clear();
    }

    /**
     * 只推进 globalTime，与 arc 的 Time.updateGlobal() 一致。
     * <p>注意：delta 在 arc 里**不是字段**，只有 {@link #delta()} 方法可取；此处不再保留 delta 字段，
     * 避免误用成恒为 1f 的常量（曾导致游戏速度随帧率变化）。
     */
    public static void updateGlobal(){
        globalTimeRaw += Core.graphics.getDeltaTime()*60f;
        globalTime = (float)globalTimeRaw;
    }

    /** Use normal delta time (e. g. delta * 60) */
    public static void update(){
        float delta = delta();

        timeRaw += delta;

        if(Double.isInfinite(timeRaw) || Double.isNaN(timeRaw)){
            timeRaw = 0;
        }

        time = (float)timeRaw;

        updateRuns(delta);
    }

    /** 推进延迟任务（倒序遍历，任务内部新增任务也不会漏跑）。 */
    private static void updateRuns(float delta){
        for(int i = runs.size - 1; i >= 0; i--){
            DelayRun run = runs.get(i);

            run.delay -= delta;
            if(run.delay <= 0f){
                runs.removeIndex(i);
                Pools.free(run);
                if(run.finish != null) run.finish.run();
            }
        }
    }
    public static float delta() {
        return deltaimpl.get();
    }
    /** @return The current value of the system timer, in nanoseconds. */
    public static long nanos(){
        return System.nanoTime();
    }

    /** @return the difference, measured in milliseconds, between the current time and midnight, January 1, 1970 UTC. */
    public static long millis(){
        return System.currentTimeMillis();
    }

    /**
     * Convert nanoseconds time to milliseconds
     * @param nanos must be nanoseconds
     * @return time value in milliseconds
     */
    public static long nanosToMillis(long nanos){
        return nanos / nanosPerMilli;
    }

    /**
     * Convert milliseconds time to nanoseconds
     * @param millis must be milliseconds
     * @return time value in nanoseconds
     */
    public static long millisToNanos(long millis){
        return millis * nanosPerMilli;
    }

    /**
     * Get the time in nanos passed since a previous time
     * @param prevTime - must be nanoseconds
     * @return - time passed since prevTime in nanoseconds
     */
    public static long timeSinceNanos(long prevTime){
        return nanos() - prevTime;
    }

    /**
     * Get the time in millis passed since a previous time
     * @param prevTime - must be milliseconds
     * @return - time passed since prevTime in milliseconds
     */
    public static long timeSinceMillis(long prevTime){
        return millis() - prevTime;
    }

    public static class DelayRun implements Pool.Poolable {
        float delay;
        Runnable finish;

        @Override
        public void reset(){
            delay = 0;
            finish = null;
        }
    }
}
