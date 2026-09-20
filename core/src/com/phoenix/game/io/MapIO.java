package com.phoenix.game.io;

import com.badlogic.gdx.graphics.Color;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.game.Team;
import com.phoenix.game.world.Block;

/**
 * 最小实现：地图 IO 工具。参照 Mindustry mindustry.io.MapIO 移植，目前只搬了小地图上色用到的
 * {@link #colorFor(Block, Block, Block, Team)}（地图文件的读写由 {@link SaveIO} 负责）。
 */
public class MapIO{

    /**
     * 取瓦片在小地图/地图预览上的颜色。对应原版 {@code MapIO.colorFor}：
     * <ul>
     *   <li>人造方块（{@link Block#synthetic()}，即 {@code update || destructible}）→ 阵营色；</li>
     *   <li>否则实心方块 → 方块自身颜色（由 {@code Block.loadMinimapColor} 从图标中心像素采样）；</li>
     *   <li>否则有覆盖层（矿物/出生点）→ 覆盖层颜色；</li>
     *   <li>否则 → 地板颜色。</li>
     * </ul>
     * @param floor 地板
     * @param wall 方块（可为 air）
     * @param ore 覆盖层（可为 air）
     * @param team 阵营
     * @return RGBA8888 颜色
     */
    public static int colorFor(Block floor, Block wall, Block ore, Team team){
        if(wall.synthetic()){
            return Color.rgba8888(team.color);
        }
        return Color.rgba8888(wall.solid ? wall.color : ore == Blocks.air ? floor.color : ore.color);
    }
}
