package com.phoenix.game.world.blocks.defense;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.world.Block;

/**
 * 最小实现：墙体。参照 Mindustry mindustry.world.blocks.defense.Wall 移植。
 * <p>墙体是实心的、有血量（可被子弹摧毁），但没有每帧逻辑（update = false，实体只承载血量）。
 */
public class Wall extends Block{
    public Wall(String name){
        super(name);

        solid = true;
        breakable = true;
        destructible = true;
        entityType = WallEntity::new;
    }

    /** 墙体实体：只承载血量，无每帧逻辑。 */
    public class WallEntity extends TileEntity{
    }
}
