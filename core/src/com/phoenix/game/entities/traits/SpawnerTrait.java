package com.phoenix.game.entities.traits;

import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.math.Position;
import com.phoenix.game.world.Tile;

/**
 * 参照原版 mindustry.entities.traits.SpawnerTrait 移植。
 * <p>原版 {@code hasUnit(Unit)} 的参数是本项目的 {@link BaseUnit}（本项目没有 Unit 基类，
 * 单位基类为 BaseUnit）。
 */
public interface SpawnerTrait extends TargetTrait, Position{
    Tile getTile();

    void updateSpawning(Player unit);

    boolean hasUnit(BaseUnit unit);

    @Override
    default boolean isValid(){
        return getTile().entity instanceof SpawnerTrait;
    }
}
