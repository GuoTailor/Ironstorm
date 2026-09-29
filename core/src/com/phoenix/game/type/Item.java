package com.phoenix.game.type;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.ctype.ContentType;
import com.phoenix.game.ctype.UnlockableContent;
import com.phoenix.game.struct.Array;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.blocks.OreFloor;

/**
 * 物品资源。参照 Mindustry mindustry.type.Item 移植。
 * <p>继承 {@link UnlockableContent}：名字/本地化名/描述来自它，构造时自动注册进 {@code Vars.content}，
 * id = 物品注册顺序（与原版 ContentLoader 的加载顺序一致）。
 */
public class Item extends UnlockableContent{
    public final Color color;

    /** 物品类型：用于分类页签与"核心是否接收"。默认 {@link ItemType#resource}（对应原版 Item.type）。 */
    public ItemType type = ItemType.resource;

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
    /** 是否始终解锁（对应原版 Item.java:32）。 */
    public boolean alwaysUnlocked = false;

    /** 图标区域缓存（图集里的 item-&lt;name&gt;）。 */
    private TextureRegion iconRegion;
    private boolean iconLoaded;

    public Item(String name, Color color){
        super(name);
        this.color = color;
    }

    /** 单参构造（对应原版 {@code Item(String)}）：默认色为**黑色**（原版如此，非白色）。 */
    public Item(String name){
        this(name, new Color(Color.BLACK));
    }

    @Override
    public ContentType getContentType(){
        return ContentType.item;
    }

    /** 对应原版 Item.java:44-46（字段覆写，非匿名子类）。 */
    @Override
    public boolean alwaysUnlocked(){
        return alwaysUnlocked;
    }

    /** @return 该物品的图标区域（对应原版 Item.icon(Cicon)）；图集缺失返回 null。 */
    public TextureRegion icon(){
        if(!iconLoaded){
            iconRegion = Core.atlas == null ? null : Core.atlas.findRegion("item-" + name);
            iconLoaded = true;
        }
        return iconRegion;
    }

    /**
     * 对应原版 {@code Item.icon(Cicon)} 的尺寸参数版本。
     * <p>本项目图集里每种物品只有一张 {@code item-<name>}，没有按尺寸切的多张图，故忽略尺寸参数。
     */
    @Override
    public TextureRegion icon(com.phoenix.game.ui.Cicon cicon){
        return icon();
    }

    /** @return 本地化名称；继承自 UnlockableContent（缺失时回退内部名）。 */
    public String localizedName(){
        return localizedName;
    }

    /**
     * @return 所有能生成矿脉的物品（对应原版 {@code Item.getAllOres()}）。
     * <p>原版 = {@code content.blocks().select(b -> b instanceof OreBlock).map(b -> ((Floor)b).itemDrop)}；
     * 本项目 {@code Vars.content.blocks()} 是 libgdx {@code Array}（没有 {@code select}/{@code map}）
     * → 手写下标循环筛选（加载期调用，非热路径）。
     * <p>⚠ 判据用 {@link OreFloor}（本项目 {@code Blocks} 实际注册的矿脉类，见 {@code content/Blocks} 的
     * {@code oreCopper}/{@code oreLead}/… 区）；{@code world.blocks.OreBlock} 是照原版类名补的**重复类**、
     * 没有任何注册点，用它筛会得到空集。
     */
    public static Array<Item> getAllOres(){
        Array<Item> result = new Array<>();
        com.badlogic.gdx.utils.Array<Block> blocks = Vars.content.blocks();
        for(int i = 0; i < blocks.size; i++){
            Block block = blocks.get(i);
            if(block instanceof OreFloor){
                result.add(((Floor)block).itemDrop);
            }
        }
        return result;
    }

    @Override
    public String toString(){
        return name;
    }
}
