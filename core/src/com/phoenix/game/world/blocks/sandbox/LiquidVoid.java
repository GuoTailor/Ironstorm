package com.phoenix.game.world.blocks.sandbox;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

/**
 * 液体虚空（沙盒）。参照 Mindustry mindustry.world.blocks.sandbox.LiquidVoid 移植。
 * <p>什么液体都收、收下就丢（不占储量），用来消化多余的液体产出。
 * <p>刻意偏离：原版 {@code update = true} 即自动获得实体，本项目 {@code hasEntity()} 还要求
 * {@code entityType != null}（见 {@code Block.hasEntity} 与同包的 {@code ItemVoid}），故补一个空实体类型。
 * <p>刻意降级：{@code setBars()} 里 {@code bars.remove("liquid")} → 本项目改用
 * {@code Block.displayLiquidBar = false}（效果等价：虚空不显示液体条）。
 * {@code Block.acceptLiquid/handleLiquid} 钩子已就绪并已接线。
 */
public class LiquidVoid extends Block{

    public LiquidVoid(String name){
        super(name);
        hasLiquids = true;
        solid = true;
        update = true;
        //对应原版 setBars() 里 bars.remove("liquid")：虚空不显示液体条
        displayLiquidBar = false;
        entityType = LiquidVoidEntity::new;
    }

    /** 无状态实体（虚空不需要储量，收下即丢）。 */
    public class LiquidVoidEntity extends TileEntity{
    }

    /** 永远收得下（覆写以绕开 {@code Block.acceptLiquid} 的 liquidfilters 判据）。 */
    @Override
    public boolean acceptLiquid(Tile tile, Tile source, Liquid liquid, float amount){
        return true;
    }

    /** 收到就丢（覆写 {@code Block.handleLiquid}）。 */
    @Override
    public void handleLiquid(Tile tile, Tile source, Liquid liquid, float amount){}
}
