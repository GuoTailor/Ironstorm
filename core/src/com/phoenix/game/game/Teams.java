package com.phoenix.game.game;

import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.world.meta.BlockFlag;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.modules.ItemModule;

/**
 * 最小实现：阵营关系与核心查询。参照 Mindustry mindustry.game.Teams 移植。
 */
public class Teams{
    /** 各队共享物品库存（演示期无传送带，按队伍统一存取）。 */
    private final ItemModule[] itemModules = new ItemModule[Team.all().length];

    /** @return 某队伍的共享物品库存（惰性创建）。 */
    public ItemModule items(Team team){
        int index = team.id & 0xFF;
        if(itemModules[index] == null){
            itemModules[index] = new ItemModule();
        }
        return itemModules[index];
    }

    /** @return 两个阵营是否敌对（最小实现：不同阵营即敌对）。 */
    public boolean areEnemies(Team a, Team b){
        return a != b;
    }

    /** @return 是否为友军。 */
    public boolean areAllies(Team a, Team b){
        return a == b;
    }

    public boolean isActive(Team team){
        return team != null && team != Team.derelict;
    }

    public boolean canInteract(Team a, Team b){
        return a == b;
    }

    /** @return 与指定阵营敌对的所有阵营。 */
    public Array<Team> enemiesOf(Team team){
        Array<Team> array = new Array<>();
        for(Team other : Team.base()){
            if(other != null && areEnemies(team, other)){
                array.add(other);
            }
        }
        return array;
    }

    /** @return 距离最近的己方核心所在瓦片。 */
    public Tile closestCore(float x, float y, Team team){
        return Vars.world == null ? null : Vars.world.closestTile(x, y, team, BlockFlag.core, false);
    }

    /** @return 距离最近的敌方核心所在瓦片。 */
    public Tile closestEnemyCore(float x, float y, Team team){
        return Vars.world == null ? null : Vars.world.closestTile(x, y, team, BlockFlag.core, true);
    }
}
