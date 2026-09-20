package com.phoenix.game.entities.type;

import com.phoenix.game.game.Team;
import com.phoenix.game.entities.traits.Entity;

public abstract class BaseEntity implements Entity {
    private static int lastid;
    /** Do not modify. Used for network operations and mapping. */
    public int id;
    public float x, y;
    /** 所属阵营（最小实现，对应原版 TeamTrait）。 */
    public Team team = Team.sharded;
    protected boolean dead;

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

    /** 最小实现：将实体从场景中移除。原版还会从 EntityGroup 中移除。 */
    public void remove(){
        dead = true;
        removed();
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
