package com.phoenix.game.world.blocks;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockGroup;
import com.phoenix.game.world.modules.LiquidModule;

/**
 * 液体容器/罐体。参照 Mindustry mindustry.world.blocks.LiquidBlock 移植。
 * <p>贴图分三层：{@code <name>-bottom}（底座）、{@code <name>-liquid}（液体填充，按储量透明度叠加）、
 * {@code <name>-top}（顶盖）。
 * <p>刻意偏离：原版 {@code draw} 里的 rotation 用 {@code tile.rotation() * 90}（原版 rotation 0 = +x）；
 * 本项目 rotation 0 = +y，角度换算见 {@code 转换.md}，这里写 {@code (1 - rotation) * 90}。
 */
public class LiquidBlock extends Block{
    protected TextureRegion liquidRegion, bottomRegion, topRegion;

    public LiquidBlock(String name){
        super(name);
        update = true;
        solid = true;
        hasLiquids = true;
        group = BlockGroup.liquids;
        outputsLiquid = true;
    }

    @Override
    public void load(){
        super.load();

        if(Core.atlas == null) return;

        //libgdx 的 TextureAtlas 只有 findRegion（没有 arc 的 find）
        liquidRegion = Core.atlas.findRegion(name + "-liquid");
        topRegion = Core.atlas.findRegion(name + "-top");
        bottomRegion = Core.atlas.findRegion(name + "-bottom");
    }

    @Override
    public TextureRegion[] generateIcons(){
        if(Core.atlas == null) return super.generateIcons();
        return new TextureRegion[]{Core.atlas.findRegion(name + "-bottom"), Core.atlas.findRegion(name + "-top")};
    }

    /**
     * ⚠ <b>坐标口径修正</b>：原版三层都用 {@code tile.drawx()/drawy()}（原版口径下即方块中心）；
     * 本项目 {@code Tile.drawx()} 少半格，而 {@code Draw.rect} 是中心语义 → 改用
     * {@code centerX/centerY(tile)}（= {@code drawx() + tilesize/2f}），否则整体偏左下 4。
     */
    @Override
    public void draw(Tile tile){
        LiquidModule mod = tile.entity.liquids;

        int rotation = rotate ? (1 - tile.rotation()) * 90 : 0;

        Draw.rect(bottomRegion, centerX(tile), centerY(tile), rotation);

        if(mod.total() > 0.001f){
            Draw.color(mod.current().color);
            Draw.alpha(mod.total() / liquidCapacity);
            Draw.rect(liquidRegion, centerX(tile), centerY(tile), rotation);
            Draw.color();
        }

        Draw.rect(topRegion, centerX(tile), centerY(tile), rotation);
    }
}
