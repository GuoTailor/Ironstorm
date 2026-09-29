package com.phoenix.game.entities.traits;

import com.phoenix.game.net.Interpolator;

import java.io.*;

/**
 * 参照原版 mindustry.entities.traits.SyncTrait 移植。
 * <p>**刻意偏离**：原版 {@code Interpolator} 用 {@code target/last/pos} 三个 Vec2 + {@code update()}；
 * 本项目 {@link Interpolator} 的字段是 {@code lastX/lastY/targetX/targetY} + {@code alpha()/x(a)/y(a)}，
 * 所以 {@link #setNet} / {@link #interpolate()} 改为直接读写这些字段。
 * <p>注意：{@link TypeTrait} 由本批的另一个任务负责移植，本文件依赖它存在。
 */
public interface SyncTrait extends Entity, TypeTrait{

    /** Sets the position of this entity and updated the interpolator. */
    default void setNet(float x, float y){
        set(x, y);

        if(getInterpolator() != null){
            Interpolator interp = getInterpolator();
            //对应原版 target.set/last.set/pos.set(0,0)/updateSpacing=16/lastUpdated=0
            interp.targetX = interp.lastX = x;
            interp.targetY = interp.lastY = y;
            interp.updateSpacing = 16;
            interp.lastUpdated = 0;
        }
    }

    /** Interpolate entity position only. Override if you need to interpolate rotations or other values. */
    default void interpolate(){
        if(getInterpolator() == null){
            throw new RuntimeException("This entity must have an interpolator to interpolate()!");
        }

        Interpolator interp = getInterpolator();
        float a = interp.alpha();

        setX(interp.x(a));
        setY(interp.y(a));
    }

    /** Return the interpolator used for smoothing the position. Optional. */
    default Interpolator getInterpolator(){
        return null;
    }

    /** Whether syncing is enabled for this entity; true by default. */
    default boolean isSyncing(){
        return true;
    }

    //Read and write sync data, usually position
    void write(DataOutput data) throws IOException;

    void read(DataInput data) throws IOException;
}
