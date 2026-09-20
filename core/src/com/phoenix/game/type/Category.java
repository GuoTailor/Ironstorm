package com.phoenix.game.type;

/** 方块建造分类，顺序与 Mindustry 126.2 保持一致。 */
public enum Category{
    turret, production, distribution, liquid, power, defense, crafting, units, upgrade, effect;

    public static final Category[] all = values();

    public Category prev(){
        return all[(ordinal() - 1 + all.length) % all.length];
    }

    public Category next(){
        return all[(ordinal() + 1) % all.length];
    }
}
