package com.phoenix.game.entities.type;

import com.phoenix.game.game.Team;
import com.phoenix.game.entities.EntityGroup;
import com.phoenix.game.entities.traits.Entity;

public abstract class BaseEntity implements Entity {
    private static int lastid;
    /** Do not modify. Used for network operations and mapping. */
    public int id;
    public float x, y;
    /** 所属阵营（最小实现，对应原版 TeamTrait）。 */
    public Team team = Team.sharded;
    protected boolean dead;
    /**
     * 所属实体组（对应原版 {@code Entity.group}）：未加入任何组时为 null。
     * <p>只由 {@link EntityGroup#add}/{@link EntityGroup#remove} 读写，别在别处直接改。
     */
    private transient EntityGroup group;

    public BaseEntity(){
        id = lastid++;
    }

    public Team getTeam(){
        return team;
    }

    public void setTeam(Team team){
        this.team = team;
    }

    public boolean isDead(){
        return dead;
    }

    public void setDead(boolean dead){
        this.dead = dead;
    }

    /**
     * 将实体从场景中移除：先从所属实体组解绑（对应原版 {@code Entity.remove()} 里的
     * {@code getGroup().remove(this)}），再置 dead 并**同步**回调 {@code removed()}。
     * <p>⚠ 本项目 {@code removed()} 是同步的（原版由 {@link EntityGroup#updateEvents()} 延迟调用）——
     * 所以 {@link EntityGroup} 只维护成员数组、不再重复回调，池化实体（{@code Bullet}）才能不被串。
     */
    @SuppressWarnings("unchecked")
    public void remove(){
        if(group != null) group.remove(this);
        dead = true;
        removed();
    }

    @Override
    public EntityGroup getGroup(){
        return group;
    }

    @Override
    public void setGroup(EntityGroup group){
        this.group = group;
    }

    @Override
    public int getID(){
        return id;
    }

    @Override
    public void resetID(int id){
        this.id = id;
    }

    @Override
    public float getX(){
        return x;
    }

    @Override
    public void setX(float x){
        this.x = x;
    }

    @Override
    public float getY(){
        return y;
    }

    @Override
    public void setY(float y){
        this.y = y;
    }

    @Override
    public String toString(){
        return getClass() + " " + id;
    }

    /** Increments this entity's ID. Used for pooled entities.*/
    public void incrementID(){
        id = lastid++;
    }
}
