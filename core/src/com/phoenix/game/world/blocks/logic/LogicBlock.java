package com.phoenix.game.world.blocks.logic;

import com.phoenix.game.world.Block;

/**
 * 逻辑方块基类（原版用于承载逻辑处理器/开关等）。参照 Mindustry
 * mindustry.world.blocks.logic.LogicBlock 移植。
 * <p>本类目前只是 {@link Block} 的空子类：原版的逻辑系统（LAssembler/逻辑处理器）尚未移植，
 * 保留类是为了让后续逻辑类方块有统一的父类与类名对照点。
 */
public class LogicBlock extends Block{

    public LogicBlock(String name){
        super(name);
    }
}
