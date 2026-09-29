package com.phoenix.game.world;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.world.blocks.StaticWall;

import static com.phoenix.game.Vars.tilesize;

/**
 * 树木类静态岩壁（4 邻同种时把贴图裁掉相邻那条边，避免重叠）。参照原版 mindustry.world.StaticTree 移植。
 * <p>方向索引换算：原版 arc 的 d4 是 {+x,+y,-x,-y}，本项目是 {+y,+x,-y,-x}，
 * 同一方向在本项目的下标 = 原版下标 ^ 1（见 {@link com.phoenix.game.math.geom.Geometry#d4}）。
 */
public class StaticTree extends StaticWall{

    public StaticTree(String name){
        super(name);
    }

    @Override
    public void draw(Tile tile){
        TextureRegion r = Tmp.tr1;
        //libgdx 的 TextureRegion.setRegion 只复制 uv 坐标（不复制宽高）→ 补上宽高，等价 arc 的 set(TextureRegion)
        r.setRegion(region);
        r.setRegionWidth(region.getRegionWidth());
        r.setRegionHeight(region.getRegionHeight());
        //arc 的 TextureRegion 用 getWidth/setX/setY；libgdx 对应 getRegionWidth/setRegionX/setRegionY
        int crop = (region.getRegionWidth() - tilesize*4) / 2;
        float ox = 0;
        float oy = 0;

        for(int i = 0; i < 4; i++){
            //i 沿用原版的方向编号（0=+x,1=+y,2=-x,3=-y），取邻居时换算成本项目的下标
            Tile nearby = tile.getNearby(i ^ 1);
            if(nearby != null && nearby.block() instanceof StaticWall){

                if(i == 0){
                    r.setRegionWidth(r.getRegionWidth() - crop);
                    ox -= crop /2f;
                }else if(i == 1){
                    r.setRegionY(r.getRegionY() + crop);
                    oy -= crop /2f;
                }else if(i == 2){
                    r.setRegionX(r.getRegionX() + crop);
                    ox += crop /2f;
                }else{
                    r.setRegionHeight(r.getRegionHeight() - crop);
                    oy += crop /2f;
                }
            }
        }
        Draw.rect(r, centerX(tile) + ox * Draw.scl, centerY(tile) + oy * Draw.scl);
    }
}
