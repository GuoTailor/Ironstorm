package com.phoenix.game.world.blocks;

import com.phoenix.game.core.Draw;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;

/**
 * 双层叠加地表（如 pebbles 碎石）。参照 Mindustry mindustry.world.blocks.DoubleOverlayFloor 移植。
 * <p>同一张贴图按同一个随机种子画两遍：先压暗 40% 并下移 0.75 单位画一层"影子"，
 * 再用原色在原点画一层，得到轻微的立体感。
 * <p>刻意偏离：本项目 {@link OverlayFloor} 构造器把 {@code variants} 置 0（出生点标记没有贴图），
 * 而原版的 OverlayFloor 继承 Floor 的默认 {@code variants = 3}，所以这里显式补回 {@code variants = 3}，
 * 否则 {@code variantRegions} 为空、{@link #draw} 会数组越界。
 */
public class DoubleOverlayFloor extends OverlayFloor{

    public DoubleOverlayFloor(String name){
        super(name);
        variants = 3;
    }

    @Override
    public void draw(Tile tile){
        Draw.colorl(0.4f);
        Draw.rect(variantRegions[Mathf.randomSeed(tile.pos(), 0, Math.max(0, variantRegions.length - 1))], tile.worldx(), tile.worldy() - 0.75f);
        Draw.color();
        Draw.rect(variantRegions[Mathf.randomSeed(tile.pos(), 0, Math.max(0, variantRegions.length - 1))], tile.worldx(), tile.worldy());
    }
}
