package com.phoenix.game.entities.type;


import com.phoenix.game.entities.traits.DamageTrait;
import com.phoenix.game.entities.traits.HealthTrait;
import com.phoenix.game.entities.traits.SolidTrait;

/**
 * 参照原版 mindustry.entities.type.DestructibleEntity 移植。
 * <p>**刻意偏离**：原版在这里重新声明了 {@code public transient boolean dead;}
 * （因为原版 BaseEntity 没有 dead 字段）；本项目 BaseEntity 已经有 {@code protected boolean dead}，
 * 再声明一次会**隐藏父类字段**（BaseEntity.remove() 写父字段、isDead() 读子字段 → 移除了也不算死），
 * 所以这里直接复用父类的字段。
 */
public abstract class DestructibleEntity extends SolidEntity implements HealthTrait{
    public float health;

    @Override
    public boolean collides(SolidTrait other){
        return other instanceof DamageTrait;
    }

    @Override
    public void collision(SolidTrait other, float x, float y){
        if(other instanceof DamageTrait){
            boolean wasDead = isDead();
            onHit(other);
            damage(((DamageTrait)other).damage());
            if(!wasDead && isDead()){
                ((DamageTrait)other).killed(this);
            }
        }
    }

    @Override
    public void health(float health){
        this.health = health;
    }

    @Override
    public float health(){
        return health;
    }

    @Override
    public boolean isDead(){
        return dead;
    }

    @Override
    public void setDead(boolean dead){
        this.dead = dead;
    }

}
