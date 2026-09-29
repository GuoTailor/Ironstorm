package com.phoenix.game.world.blocks;

import com.phoenix.game.core.Draw;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

/**
 * 树木（静态装饰方块）。参照 Mindustry mindustry.world.blocks.TreeBlock 移植。
 * <p>本体不绘制（{@code draw} 空实现），只在 {@code drawLayer} 里按瓦片坐标的确定性随机角度画一次贴图，
 * 这样同一棵树每次绘制朝向都一致、整片树林又各不相同。
 * <p>TODO（B 类）原版构造器还写 {@code layer = Layer.power} 与 {@code expanded = true}：
 * {@code graphics/Layer} 枚举**已存在**，但本项目 {@code Block} 没有 layer/expanded 字段，
 * {@code Renderer} 也不按层排序（方块本体与叠层分两遍统一绘制），故这两个字段无处设置。
 */
public class TreeBlock extends Block{

    public TreeBlock(String name){
        super(name);
        solid = true;
    }

    @Override
    public void draw(Tile tile){
    }

    /**
     * ⚠ <b>坐标口径修正</b>：原版 {@code Draw.rect(region, tile.drawx(), tile.drawy(), ...)} 里
     * {@code drawx()} 在原版坐标口径下**就是方块中心**；本项目 {@code Tile.drawx()} 少半格
     * （= {@code centerX(tile) - tilesize/2f}），而 {@code Draw.rect} 是中心语义 → 直接用会整体偏左下 4。
     * 故改用 {@code centerX/centerY(tile)}（树贴图 320×320 远大于一格，但中心点换算不变）。
     */
    @Override
    public void drawLayer(Tile tile){
        Draw.rect(region, centerX(tile), centerY(tile), Mathf.randomSeed(tile.pos(), 0, 4) * 90);
    }
}
