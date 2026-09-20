package com.phoenix.game.content;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.type.Item;

/**
 * 物品定义集合。参照 Mindustry mindustry.content.Items 移植。
 * <p>id 顺序与原版 ContentLoader 的加载顺序**完全一致**（共 16 种），
 * 存档与网络同步都按 id 传输，不能随意调整。
 */
public class Items{
    /** 全部物品，按 id 顺序。 */
    public static final Array<Item> all = new Array<>();

    public static Item copper, lead, metaglass, graphite, sand, coal, titanium, thorium,
        scrap, silicon, plastanium, phasefabric, surgealloy, sporePod, blastCompound, pyratite;

    public static void load(){
        if(all.size > 0) return;

        copper = item("copper", "d99d73");
        copper.hardness = 1;
        copper.cost = 0.5f;

        lead = item("lead", "8c7fa9");
        lead.hardness = 1;
        lead.cost = 0.7f;

        metaglass = item("metaglass", "ebeef5");
        metaglass.cost = 1.5f;

        graphite = item("graphite", "b2c6d2");
        graphite.cost = 1f;

        sand = item("sand", "f7cba4");

        coal = item("coal", "272727");
        coal.hardness = 2;
        coal.flammability = 1f;
        coal.explosiveness = 0.2f;

        titanium = item("titanium", "8da1e3");
        titanium.hardness = 3;
        titanium.cost = 1f;

        thorium = item("thorium", "f9a3c7");
        thorium.hardness = 4;
        thorium.radioactivity = 1f;
        thorium.explosiveness = 0.2f;
        thorium.cost = 1.1f;

        scrap = item("scrap", "777777");

        silicon = item("silicon", "53565c");
        silicon.cost = 0.8f;

        plastanium = item("plastanium", "cbd97f");
        plastanium.flammability = 0.1f;
        plastanium.explosiveness = 0.2f;
        plastanium.cost = 1.3f;

        phasefabric = item("phase-fabric", "f4ba6e");
        phasefabric.radioactivity = 0.6f;
        phasefabric.cost = 1.3f;

        surgealloy = item("surge-alloy", "f3e979");

        sporePod = item("spore-pod", "7457ce");
        sporePod.flammability = 1.15f;

        blastCompound = item("blast-compound", "ff795e");
        blastCompound.flammability = 0.4f;
        blastCompound.explosiveness = 1.2f;

        pyratite = item("pyratite", "ffaa5f");
        pyratite.flammability = 1.4f;
        pyratite.explosiveness = 0.4f;
    }

    private static Item item(String name, String colorHex){
        Item item = new Item(all.size, name, Color.valueOf(colorHex));
        all.add(item);
        return item;
    }

    /** @return 按 id 取物品；越界返回 null。 */
    public static Item get(int id){
        return id >= 0 && id < all.size ? all.get(id) : null;
    }
}
