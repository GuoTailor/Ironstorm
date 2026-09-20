package com.phoenix.game.world.consumers;

/**
 * 消耗类型。参照 Mindustry mindustry.world.consumers.ConsumeType 移植。
 * <p>一个方块每种类型最多有一个消耗器（由 {@link Consumers} 按下标存放）。
 */
public enum ConsumeType{
    item, liquid, power
}
