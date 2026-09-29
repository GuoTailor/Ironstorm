package com.phoenix.game.world;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.game.Team;

/**
 * 不触发变更事件、且实体类型被缓存的瓦片（加载预览图时避免产生垃圾）。
 * 参照原版 mindustry.world.CachedTile 移植。
 * <p>本类当前**未被引用**（{@code editor/MapGenerateDialog} 自带了一个语义不同的私有嵌套 CachedTile：
 * 那个是"完全不产生实体"，本类是"产生实体但不触发邻接变更"）。
 * <p>与 Tile 的差异：
 * <ul>
 *     <li>TODO（B 类，缺依赖）原版覆写 {@code preChanged()}（本项目 Tile 无此钩子）；这里靠覆写 {@link #changed()}
 *         跳过 {@code removeFromProximity} / {@code updateProximity}，达到"不触发邻接变更"的同等效果。</li>
 *     <li>（C 类，等价替代）原版 {@code getTeam()} 走 {@code getTeamID()}（本项目 Tile 无此方法）；
 *         本项目基类 {@link Tile#getTeam()} 本身就读 {@code team} 字段，覆写与基类完全等价。</li>
 * </ul>
 */
public class CachedTile extends Tile{

    public CachedTile(){
        super(0, 0);
    }

    @Override
    public Team getTeam(){
        return Team.get(team);
    }

    @Override
    protected void changed(){
        //对应原版注释：这段覆写了旧的瓦片逻辑，不会从邻接表里摘除自己
        entity = null;

        //用 blockRaw()：原版 Tile.block() 返回裸字段，而本项目 block() 会解析多格链接
        Block block = blockRaw();

        if(block.hasEntity()){
            TileEntity n = block.newEntity();
            if(n != null){
                //说明（C 类）：原版在这里逐个 new ItemModule/LiquidModule/PowerModule + new ConsumeModule(entity)
                //     （原版传的 entity 当时恒为 null）；本项目 TileEntity.init 已内联同一套分配，故直接走 init
                n.init(this, block.update);
            }
            entity = n;
        }
    }
}
