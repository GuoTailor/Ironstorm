package com.phoenix.game.world.blocks.distribution;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.core.Core;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Tile;

/**
 * 装甲传送带。参照 Mindustry mindustry.world.blocks.distribution.ArmoredConveyor 移植。
 * <p>与普通传送带的区别：**只接受正后方输入**（侧向一律拒绝），因此不会被侧向并线塞满，血量也更高。
 * <p>TODO 图集里没有 {@code armored-conveyor} 贴图（只有 conveyor / titanium-conveyor），
 * 暂用普通传送带贴图占位，等补美术资源后改回。
 */
public class ArmoredConveyor extends Conveyor{
    public ArmoredConveyor(String name){
        super(name);
        speed = 0.03f;
        health = 180;
    }

    @Override
    public void load(){
        for(int i = 0; i < regions.length; i++){
            for(int j = 0; j < regions[i].length; j++){
                regions[i][j] = Core.atlas == null ? null : Core.atlas.findRegion("conveyor-" + i + "-" + j);
            }
        }
        region = regions[0][0];
        variantRegions = new TextureRegion[]{ region };
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        //只收正后方输入：方向索引差必须为 0
        int direction = source == null ? 0 : Math.abs(source.relativeTo(tile.x, tile.y) - tile.rotation());
        return direction == 0 && super.acceptItem(item, tile, source);
    }
}
