package com.phoenix.game.world.blocks;

import com.phoenix.game.world.Block;
import com.phoenix.game.world.meta.BlockGroup;

/**
 * 电力方块基类。参照 Mindustry mindustry.world.blocks.PowerBlock 移植。
 * <p>只负责声明"要接电网、要每帧更新"；发电/耗电由子类写 {@code powerProduction} /
 * {@code consumes.power(...)}。
 */
public abstract class PowerBlock extends Block{

    public PowerBlock(String name){
        super(name);
        update = true;
        solid = true;
        hasPower = true;
        group = BlockGroup.power;
    }
}
