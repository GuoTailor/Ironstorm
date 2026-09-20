package com.phoenix.game.game;
import com.phoenix.game.core.Core;
import com.phoenix.game.graphics.Pal;

import com.badlogic.gdx.graphics.Color;

/**
 * 最小实现：阵营。参照 Mindustry mindustry.game.Team 移植。
 * 原版敌对关系由 GameState/Teams 维护，这里先用最简单的规则替代。
 */
public class Team implements Comparable<Team>{
    public final byte id;
    public final Color color;
    public String name;

    /** All 256 registered teams. */
    private static final Team[] all = new Team[256];
    /** The 6 base teams used in the editor. */
    private static final Team[] baseTeams = new Team[6];

    public static final Team
    derelict = new Team(0, "derelict", Color.valueOf("4d4e58")),
    sharded = new Team(1, "sharded", Pal.accent.cpy()),
    crux = new Team(2, "crux", Color.valueOf("e82d2d")),
    green = new Team(3, "green", Color.valueOf("4dd98b")),
    purple = new Team(4, "purple", Color.valueOf("9a4bdf")),
    blue = new Team(5, "blue", Color.ROYAL.cpy());

    static{
        //create the whole 256 placeholder teams
        for(int i = 6; i < all.length; i++){
            new Team(i, "team#" + i, Color.WHITE.cpy());
        }
    }

    public static Team get(int id){
        return all[id & 0xFF];
    }

    /** @return the 6 base team colors. */
    public static Team[] base(){
        return baseTeams;
    }

    /** @return all the teams - do not use this for lookup! */
    public static Team[] all(){
        return all;
    }

    protected Team(int id, String name, Color color){
        this.name = name;
        this.color = color;
        this.id = (byte)id;

        int us = id & 0xFF;
        if(us < 6) baseTeams[us] = this;
        all[us] = this;
    }

    /** 最小实现：非同一阵营即敌对。 */
    public boolean isEnemy(Team other){
        return other != this;
    }

    public String localized(){
        //TODO: 待本地化资源就绪后改为读取 Core.bundle
        return name;
    }

    @Override
    public int compareTo(Team team){
        return Integer.compare(id, team.id);
    }

    @Override
    public String toString(){
        return name;
    }
}
