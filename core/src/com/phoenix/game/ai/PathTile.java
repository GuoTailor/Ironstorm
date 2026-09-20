package com.phoenix.game.ai;

/**
 * 瓦片寻路数据，打包进一个 int。参照 Mindustry ai/Pathfinder 的 PathTileStruct @Struct 移植。
 * <p>位布局（自低到高）：cost(低 8 位) | team(8 位) | type(8 位，备留) | passable(第 24 位)。
 * 打包/解包必须与 Pathfinder 内部保持一致。
 */
final class PathTile{
    /** 不可通行标记对应的成本值。 */
    static final int impassable = -1;

    private PathTile(){
    }

    /** 打包一个瓦片的寻路数据。 */
    static int get(int cost, int team, int type, boolean passable){
        return (cost & 0xFF)
                | ((team & 0xFF) << 8)
                | ((type & 0xFF) << 16)
                | (passable ? 1 << 24 : 0);
    }

    /** 该瓦片是否可通行。 */
    static boolean passable(int tile){
        return (tile & (1 << 24)) != 0;
    }

    /** 该瓦片上实体所属队伍 ID。 */
    static int team(int tile){
        return (tile >>> 8) & 0xFF;
    }
}