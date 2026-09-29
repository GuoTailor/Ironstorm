package com.phoenix.game.world.blocks.units;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.meta.BlockFlag;

import java.util.EnumSet;

/**
 * 集结点。参照 Mindustry mindustry.world.blocks.units.RallyPoint 移植。
 * <p>带 {@link BlockFlag#rally} 标记的方块：单位编队/移动指令会把它当成集结点查询
 * （原版靠 {@code world.tile(...).block().flags.contains(BlockFlag.rally)} 找目标）。
 * <p>刻意偏离：原版 {@code update = solid = true} 时由 {@code hasEntity()} 自动获得实体，
 * 本项目 {@code hasEntity()} 还要求 {@code entityType != null}（见 {@code Block.hasEntity}），
 * 所以这里补一个空实体类型，否则这一格"有方块、没实体"。
 */
public class RallyPoint extends Block{

    public RallyPoint(String name){
        super(name);
        update = solid = true;
        flags = EnumSet.of(BlockFlag.rally);
        entityType = RallyPointEntity::new;
    }

    /** 无状态实体（集结点本身不需要库存/逻辑，标记语义全在 {@link #flags} 上）。 */
    public class RallyPointEntity extends TileEntity{
    }
}
