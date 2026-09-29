package com.phoenix.game.world.meta;

/**
 * 统计项的分类（对应原版 mindustry.world.meta.StatCategory）。
 * <p>内容信息对话框按这个分类分组显示方块属性，组顺序即本枚举的声明顺序。
 */
public enum StatCategory{
    general,
    power,
    liquids,
    items,
    crafting,
    shooting,
    optional;

    public String localized(){
        return I18n.get("category." + name(), name());
    }
}
