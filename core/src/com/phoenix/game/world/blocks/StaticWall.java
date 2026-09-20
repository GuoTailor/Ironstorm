package com.phoenix.game.world.blocks;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Pos;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 最小实现：不可破坏的自然岩壁。参照 Mindustry mindustry.world.blocks.StaticWall 移植。
 * <p>原版地图生成器用它铺自然地形（{@code rocks} / {@code sandrocks} / {@code darkMetal} 等）：
 * {@code solid = true} 挡路、{@code breakable = alwaysReplace = false} 不可拆，
 * 且 {@code destructible = false} → 小地图画图标采样色而不是阵营色。
 * <p>4 格同种岩壁相邻时画一张 2x2 的 {@code -large} 大贴图（对应原版同名逻辑）。
 */
public class StaticWall extends Rock{
    private TextureRegion large;
    private TextureRegion[][] split;

    public StaticWall(String name){
        super(name);
        breakable = alwaysReplace = false;
        solid = true;
        variants = 2;
    }

    @Override
    public void draw(Tile tile){
        int rx = tile.x / 2 * 2;
        int ry = tile.y / 2 * 2;

        if(large != null && split != null && eq(rx, ry) && Mathf.randomSeed(Pos.get(rx, ry)) < 0.5f){
            //split[row][col]（row 0 = 图像顶行）。世界 y 向上，所以「左下那格」要取图像的左下象限：
            //row = 1 - y%2、col = x%2。
            //注意：原版写的是 split[x%2][1-y%2] —— arc 的 TextureRegion.split 下标顺序与 libgdx 相反，
            //照抄会在 libgdx 下把四象限摆错（已用离线拼图逐张验证：只有下面这个映射能复原原图）。
            TextureRegion region = split[1 - tile.y % 2][tile.x % 2];
            if(region != null){
                Core.batch.draw(region, tile.worldx(), tile.worldy(), tilesize, tilesize);
                return;
            }
        }
        super.draw(tile);
    }

    @Override
    public void load(){
        super.load();

        large = Core.atlas == null ? null : Core.atlas.findRegion(name + "-large");
        //-large 是 2x2 格的大贴图，切成 2x2 块
        split = large == null ? null : large.split(large.getRegionWidth() / 2, large.getRegionHeight() / 2);
    }

    /** @return 以 (rx,ry) 为左下角的 2x2 四格是否全是本方块（对应原版同名方法）。 */
    private boolean eq(int rx, int ry){
        if(Vars.world == null) return false;
        if(rx >= Vars.world.width - 1 || ry >= Vars.world.height - 1) return false;

        return Vars.world.tile(rx + 1, ry).block() == this
            && Vars.world.tile(rx, ry + 1).block() == this
            && Vars.world.tile(rx, ry).block() == this
            && Vars.world.tile(rx + 1, ry + 1).block() == this;
    }
}
