package com.phoenix.game.content;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.ItemType;

/**
 * 物品定义集合。参照 Mindustry mindustry.content.Items 移植。
 * <p>id 顺序 = 本类的创建顺序（由 {@code ContentLoader} 注册分配），与原版加载顺序一致。
 */
public class Items implements com.phoenix.game.ctype.ContentList{
    /** 全部物品，按 id 顺序（指向内容加载器的物品列表）。 */
    public static Array<Item> all;

    public static Item copper, lead, metaglass, graphite, sand, coal, titanium, thorium,
        scrap, silicon, plastanium, phasefabric, surgealloy, sporePod, blastCompound, pyratite;

    @Override
    public void load(){
        if(all != null) return;
        all = com.phoenix.game.Vars.content.items();

        copper = item("copper", "d99d73");
        //原版 Items.java:18 copper 带 alwaysUnlocked = true（字段已补在 Item.java:32）
        copper.alwaysUnlocked = true;
        copper.type = ItemType.material;
        copper.hardness = 1;
        copper.cost = 0.5f;

        lead = item("lead", "8c7fa9");
        lead.type = ItemType.material;
        lead.hardness = 1;
        lead.cost = 0.7f;

        metaglass = item("metaglass", "ebeef5");
        metaglass.type = ItemType.material;
        metaglass.cost = 1.5f;

        graphite = item("graphite", "b2c6d2");
        graphite.type = ItemType.material;
        graphite.cost = 1f;

        sand = item("sand", "f7cba4");

        coal = item("coal", "272727");
        coal.hardness = 2;
        coal.flammability = 1f;
        coal.explosiveness = 0.2f;

        titanium = item("titanium", "8da1e3");
        titanium.type = ItemType.material;
        titanium.hardness = 3;
        titanium.cost = 1f;

        thorium = item("thorium", "f9a3c7");
        thorium.type = ItemType.material;
        thorium.hardness = 4;
        thorium.radioactivity = 1f;
        thorium.explosiveness = 0.2f;
        thorium.cost = 1.1f;

        scrap = item("scrap", "777777");

        silicon = item("silicon", "53565c");
        silicon.type = ItemType.material;
        silicon.cost = 0.8f;

        plastanium = item("plastanium", "cbd97f");
        plastanium.type = ItemType.material;
        plastanium.flammability = 0.1f;
        plastanium.explosiveness = 0.2f;
        plastanium.cost = 1.3f;

        phasefabric = item("phase-fabric", "f4ba6e");
        phasefabric.type = ItemType.material;
        phasefabric.radioactivity = 0.6f;
        phasefabric.cost = 1.3f;

        surgealloy = item("surge-alloy", "f3e979");
        surgealloy.type = ItemType.material;

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
        //id 由 Content 构造函数按注册顺序分配，这里不再手工编号
        return new Item(name, Color.valueOf(colorHex));
    }

    /** @return 按 id 取物品；越界返回 null。 */
    public static Item get(int id){
        return com.phoenix.game.Vars.content.item(id);
    }
}
