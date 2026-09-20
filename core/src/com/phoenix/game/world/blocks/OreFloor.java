package com.phoenix.game.world.blocks;

import com.phoenix.game.type.Item;
import com.phoenix.game.world.Tile;

/**
 * 矿脉覆盖层：叠在地表上的矿物斑点，钻头采它产矿。
 * 参照 Mindustry mindustry.world.blocks.OreBlock 移植。
 * <p>原版在打包期把物品源图加阴影生成 {@code ore-<物品>1..3}；phoenix 没有打包管线，
 * 图集里已有无阴影的源图（copper1..3 等），故方块名仍按原版 {@code ore-<物品>}，
 * 贴图名通过 {@code textureName} 指向物品源图。
 */
public class OreFloor extends OverlayFloor{

    public OreFloor(Item ore){
        super("ore-" + ore.name);
        textureName = ore.name;
        //itemDrop 用 Floor 的继承字段：Tile.drop() 按 Floor 静态类型取值，
        //这里若重新声明字段会遮蔽父类字段、导致 drop() 永远查不到产出
        itemDrop = ore;
        variants = 3;
        //小地图颜色 = 物品颜色（对应原版 OreBlock 构造里的 color.set(ore.color)）
        color.set(ore.color);
    }

    @Override
    public String getDisplayName(Tile tile){
        return itemDrop.toString();
    }
}
