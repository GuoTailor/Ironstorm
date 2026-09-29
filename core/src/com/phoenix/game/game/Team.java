package com.phoenix.game.game;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.I18n;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;

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

    //原版 Team.java:84-86 public Array<CoreEntity> cores(){ return state.teams.cores(this); }
    //（原版 TeamData 上的 hasCore/noCores/core 也一并搬到本队便捷方法上，见 Teams 同名方法）
    /** @return 本队的核心锚点瓦片列表（对应原版 {@code Team.cores()}）。 */
    public Array<Tile> cores(){
        //⚠ 本项目 Vars.state / state.teams 都可能为 null（原版是 final 非空）→ 返回空数组而不是 NPE
        if(Vars.state == null || Vars.state.teams == null) return new Array<>();
        return Vars.state.teams.cores(this);
    }

    /** @return 本队是否还有核心（对应原版 {@code TeamData.hasCore()}）。 */
    public boolean hasCore(){
        return Vars.state != null && Vars.state.teams != null && Vars.state.teams.hasCore(this);
    }

    /** @return 本队是否已无核心（对应原版 {@code TeamData.noCores()}）。 */
    public boolean noCores(){
        return !hasCore();
    }

    /** @return 本队的第一个核心瓦片（原版 {@code TeamData.core()}）；无核心返回 null。 */
    public Tile core(){
        return Vars.state == null || Vars.state.teams == null ? null : Vars.state.teams.core(this);
    }

    public String localized(){
        //原版 Team.java:90 = Core.bundle.get("team." + name + ".name", name)
        //本项目 bundle 缺键返回 ???key???（不能用 get 的 fallback）→ 走 I18n.get 的双形态判定，
        //取不到时回退到原名字（占位队伍 team#6..team#255 没有对应键）
        return I18n.get("team." + name + ".name", name);
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
