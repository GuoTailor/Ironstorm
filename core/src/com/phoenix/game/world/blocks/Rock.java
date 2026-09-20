package com.phoenix.game.world.blocks;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.core.Core;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 最小实现：自然岩石。参照 Mindustry mindustry.world.blocks.Rock 移植。
 * <p>与建筑不同，岩石**可破坏但不挡路**（原版 {@code breakable = true; alwaysReplace = true}），
 * 且 {@code destructible = false} —— 因此 {@link Block#synthetic()} 为假，
 * 小地图会走 {@code MapIO.colorFor} 的「实心方块用自身颜色」分支（即图标采样色）。
 */
public class Rock extends Block{
    /** 变体贴图数量（对应原版 Rock.variants）。 */
    protected int variants;

    public Rock(String name){
        super(name);
        breakable = true;
        alwaysReplace = true;
    }

    @Override
    public void draw(Tile tile){
        if(variants > 0 && variantRegions.length > 0){
            //按瓦片坐标确定性地挑一个变体（对应原版 Mathf.randomSeed(tile.pos(), ...)）
            int index = Mathf.randomSeed(tile.pos(), 0, variantRegions.length - 1);
            TextureRegion region = variantRegions[index];
            if(region != null){
                Core.batch.draw(region, tile.worldx(), tile.worldy(), tilesize, tilesize);
                return;
            }
        }
        super.draw(tile);
    }

    @Override
    public TextureRegion[] generateIcons(){
        //变体命名是 name1..nameN（图集里没有裸 name），所以图标取第一号变体（对应原版同名方法）
        if(variants > 0 && Core.atlas != null){
            TextureRegion first = Core.atlas.findRegion(name + "1");
            if(first != null) return new TextureRegion[]{first};
        }
        return super.generateIcons();
    }

    @Override
    public void load(){
        super.load();

        if(variants > 0 && Core.atlas != null){
            variantRegions = new TextureRegion[variants];
            for(int i = 0; i < variants; i++){
                TextureRegion region = Core.atlas.findRegion(name + (i + 1));
                //缺号时回退到本体贴图，避免 draw 里出现 null
                variantRegions[i] = region != null ? region : this.region;
            }
        }
    }
}
