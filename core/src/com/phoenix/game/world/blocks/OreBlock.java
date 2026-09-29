package com.phoenix.game.world.blocks;

import com.phoenix.game.type.Item;
import com.phoenix.game.world.Tile;

/**
 * 特定物品的矿脉覆盖层。参照 Mindustry mindustry.world.blocks.OreBlock 移植。
 * <p>原版在打包期用物品源图 + 阴影合成 {@code ore-<物品>1..3}（见原版 {@code createIcons}）；
 * 本项目没有打包管线，图集里只有无阴影的源图（copper1..3 等），因此沿用
 * {@link OreFloor} 的做法，用 {@code textureName} 指向物品源图。
 * <p>刻意偏离：原版 {@code createIcons(MultiPacker)} 依赖 arc 的 {@code PixmapRegion} 与
 * mindustry 的 {@code MultiPacker}（两者都未移植），整个方法已删去 —— 它只影响打包产物，
 * 本项目改由运行期 {@code Blocks.loadMinimapColors()} 取小地图颜色，功能上不需要它。
 * <p>说明：本项目早先已按同一原版类移植过 {@link OreFloor}（名字不同、实现等价），
 * 本文件是照原版类名补的版本，注册接线由后续批次决定用哪一个。
 */
public class OreBlock extends OverlayFloor{

    public OreBlock(Item ore){
        super("ore-" + ore.name);
        //贴图指向物品源图（原版由打包期生成 ore-<物品>N，本项目图集里只有物品源图）
        this.textureName = ore.name;
        //原版写 this.localizedName = ore.localizedName；本项目该字段在 UnlockableContent 里
        //（public String localizedName，见 Block.localizedName()），故照原版赋值。
        //显示名另有覆写 getDisplayName 取 itemDrop.localizedName()，两者一致
        this.localizedName = ore.localizedName;
        this.itemDrop = ore;
        this.variants = 3;
        this.color.set(ore.color);
    }

    /** For mod use only!*/
    public OreBlock(String name){
        super(name);
        variants = 3;
    }

    public void setup(Item ore){
        this.textureName = ore.name;
        //对应原版 setup 里的 this.localizedName = ore.localizedName
        this.localizedName = ore.localizedName;
        this.itemDrop = ore;
        this.color.set(ore.color);
    }

    @Override
    public void init(){
        super.init();

        if(itemDrop != null){
            setup(itemDrop);
        }else{
            throw new IllegalArgumentException(name + " must have an item drop!");
        }
    }

    @Override
    public String getDisplayName(Tile tile){
        return itemDrop.localizedName();
    }
}
