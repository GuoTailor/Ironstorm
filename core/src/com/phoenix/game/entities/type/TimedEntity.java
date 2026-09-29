package com.phoenix.game.entities.type;

import com.badlogic.gdx.utils.Pool;
import com.phoenix.game.entities.traits.TimeTrait;

/**
 * 参照原版 mindustry.entities.type.TimedEntity 移植。
 * <p>{@code arc.util.pooling.Pool.Poolable} 按映射表替换为 libgdx 的 {@link Pool.Poolable}（同名同签名 reset()）。
 */
public abstract class TimedEntity extends BaseEntity implements TimeTrait, Pool.Poolable{
    public float time;

    @Override
    public void time(float time){
        this.time = time;
    }

    @Override
    public float time(){
        return time;
    }

    @Override
    public void update(){
        updateTime();
    }

    @Override
    public void reset(){
        time = 0f;
    }

    @Override
    public float fin(){
        return time() / lifetime();
    }
}
