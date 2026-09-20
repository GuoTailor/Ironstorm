package com.phoenix.game.type;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.core.Core;

/**
 * 物品资源。参照 Mindustry mindustry.type.Item 移植。
 * <p>不含内容注册框架（phoenix 未启用 Content），用静态 id 顺序索引；
 * id 顺序必须与原版 ContentLoader 的加载顺序一致（存档/网络同步按 id 传输）。
 */
public class Item{
    public final short id;
    public final String name;
    public final Color color;

    /** 硬度：钻头能否采集、采集速度（对应原版 Item.hardness）。 */
    public int hardness;
    /** 可燃性（对应原版 Item.flammability）。 */
    public float flammability;
    /** 爆炸性（对应原版 Item.explosiveness）。 */
    public float explosiveness;
    /** 放射性（对应原版 Item.radioactivity）。 */
    public float radioactivity;
    /** 建造花费权重（统计/UI 用，对应原版 Item.cost）。 */
    public float cost = 1f;

    /** 图标区域缓存（图集里的 item-&lt;name&gt;）。 */
    private TextureRegion iconRegion;
    private boolean iconLoaded;

    public Item(int id, String name, Color color){
        this.id = (short)id;
        this.name = name;
        this.color = color;
    }

    /** @return 该物品的图标区域（对应原版 Item.icon(Cicon)）；图集缺失返回 null。 */
    public TextureRegion icon(){
        if(!iconLoaded){
            iconRegion = Core.atlas == null ? null : Core.atlas.findRegion("item-" + name);
            iconLoaded = true;
        }
        return iconRegion;
    }

    @Override
    public String toString(){
        return name;
    }
}
