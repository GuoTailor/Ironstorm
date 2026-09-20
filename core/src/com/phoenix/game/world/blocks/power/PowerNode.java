package com.phoenix.game.world.blocks.power;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.world.Block;

/** 电力分配节点。参照 Mindustry PowerNode 最小移植：四邻域连通电网，自身不产生/消耗电力。 */
public class PowerNode extends Block{
    public PowerNode(String name){
        super(name);
        solid = true;
        update = true;
        health = 40;
        hasPower = true;
        entityType = NodeEntity::new;
    }

    public class NodeEntity extends TileEntity{
    }
}