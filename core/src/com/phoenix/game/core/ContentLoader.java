package com.phoenix.game.core;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.phoenix.game.content.Liquids;
import com.phoenix.game.content.Mechs;
import com.phoenix.game.content.Loadouts;
import com.phoenix.game.content.TechTree;
import com.phoenix.game.content.Zones;
import com.phoenix.game.content.TypeIDs;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.content.Bullets;
import com.phoenix.game.content.Items;
import com.phoenix.game.content.StatusEffects;
import com.phoenix.game.content.UnitTypes;
import com.phoenix.game.ctype.Content;
import com.phoenix.game.ctype.ContentList;
import com.phoenix.game.ctype.ContentType;
import com.phoenix.game.ctype.MappableContent;
import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.func.Cons;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.type.UnitType;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.LegacyColorMapper;

/**
 * 全部游戏内容的加载器。参照 Mindustry {@code mindustry.core.ContentLoader} 移植。
 * <p>用之前必须先调用 {@link #createBaseContent()}；内容 id 就是各类型列表里的下标。
 * <p><b>与本项目的差异（刻意）</b>：
 * <ul>
 *   <li>原版的内容集合是字段初始化（{@code private ContentList[] content = {new Fx(), ...}}）。
 *       本项目改为 {@link #lists()} 延迟构建 —— 因为 {@code Vars.content} 必须先于任何内容类存在，
 *       而字段初始化会在 {@code new ContentLoader()} 期间就把内容造出来（那时 {@code Vars.content} 还是 null）。</li>
 *   <li>原版还有 mod 内容、{@code loadColors()}（读 sprites/block_colors.png）与
 *       {@code LegacyColorMapper}；本项目没有打包管线与 mod 内容加载，相应省略。</li>
 *   <li>{@code Fx}/{@code Liquids}/{@code Mechs}/{@code Loadouts}/{@code TechTree}/{@code Zones}/{@code TypeIDs}
 *       尚未移植，等对应批次搬进来后追加到 {@link #lists()}（顺序必须与原版一致）。</li>
 * </ul>
 */
@SuppressWarnings("unchecked")
public class ContentLoader{
    private ObjectMap<String, MappableContent>[] contentNameMap = new ObjectMap[ContentType.values().length];
    private Array<Content>[] contentMap = new Array[ContentType.values().length];
    private MappableContent[][] temporaryMapper;
    private Content lastAdded;
    private ObjectSet<Cons<Content>> initialization = new ObjectSet<>();

    public ContentLoader(){
        clear();
    }

    /** 清空全部已注册内容。 */
    public void clear(){
        contentNameMap = new ObjectMap[ContentType.values().length];
        contentMap = new Array[ContentType.values().length];
        initialization = new ObjectSet<>();

        for(ContentType type : ContentType.values()){
            contentMap[type.ordinal()] = new Array<>();
            contentNameMap[type.ordinal()] = new ObjectMap<>();
        }
    }

    /**
     * 内容集合，顺序必须与原版 {@code ContentLoader.content} 一致（id 由它决定）。
     * <p>注意：{@code new XXX()} 本身就会触发该类的静态初始化，所以必须在 {@code Vars.content} 就绪后才调用。
     */
    private ContentList[] lists(){
        return new ContentList[]{
            new Items(),
            new StatusEffects(),
            new Liquids(),
            new Bullets(),
            new Mechs(),
            new UnitTypes(),
            new Blocks(),
            new Loadouts(),
            new TechTree(),
            new Zones(),
            new TypeIDs(),

            //这些其实不是内容类，但原版也放在这里初始化（见原版 ContentLoader 的注释）
            new LegacyColorMapper(),
        };
    }

    /** 创建全部基础内容。 */
    public void createBaseContent(){
        for(ContentList list : lists()){
            list.load();
        }
    }

    /** 对全部内容调用 {@link Content#init()}。只在所有模块创建完成后调用。 */
    public void init(){
        initialize(Content::init);
    }

    /** 对全部内容调用 {@link Content#load()}。只在客户端的模块创建完成后调用。 */
    public void load(){
        initialize(Content::load);
    }

    private void initialize(Cons<Content> callable){
        if(initialization.contains(callable)) return;

        for(ContentType type : ContentType.values()){
            for(Content content : contentMap[type.ordinal()]){
                callable.get(content);
            }
        }

        initialization.add(callable);
    }

    /** @return 最后创建的内容（出错时定位用）。 */
    public Content getLastAdded(){
        return lastAdded;
    }

    /** 出错时回滚最后创建的内容。 */
    public void removeLast(){
        if(lastAdded != null && contentMap[lastAdded.getContentType().ordinal()].peek() == lastAdded){
            contentMap[lastAdded.getContentType().ordinal()].pop();
            if(lastAdded instanceof MappableContent){
                contentNameMap[lastAdded.getContentType().ordinal()].remove(((MappableContent)lastAdded).name);
            }
        }
    }

    public void handleContent(Content content){
        this.lastAdded = content;
        contentMap[content.getContentType().ordinal()].add(content);
    }

    /** @return mod 前缀转换后的名字（无 mod 时原样返回）。 */
    public String transformName(String name){
        //TODO 原版还有 currentMod 前缀；mod 内容加载尚未移植
        return name;
    }

    public void handleMappableContent(MappableContent content){
        if(contentNameMap[content.getContentType().ordinal()].containsKey(content.name)){
            throw new IllegalArgumentException("Two content objects cannot have the same name! (issue: '" + content.name + "')");
        }
        contentNameMap[content.getContentType().ordinal()].put(content.name, content);
    }

    public void setTemporaryMapper(MappableContent[][] temporaryMapper){
        this.temporaryMapper = temporaryMapper;
    }

    public Array<Content>[] getContentMap(){
        return contentMap;
    }

    public <T extends MappableContent> T getByName(ContentType type, String name){
        if(contentNameMap[type.ordinal()] == null){
            return null;
        }
        return (T)contentNameMap[type.ordinal()].get(name);
    }

    public <T extends Content> T getByID(ContentType type, int id){
        if(temporaryMapper != null && temporaryMapper[type.ordinal()] != null && temporaryMapper[type.ordinal()].length != 0){
            //-1 = 无效内容
            if(id < 0){
                return null;
            }
            if(temporaryMapper[type.ordinal()].length <= id || temporaryMapper[type.ordinal()][id] == null){
                return (T)contentMap[type.ordinal()].get(0); //默认值永远是 id 0
            }
            return (T)temporaryMapper[type.ordinal()][id];
        }

        if(id >= contentMap[type.ordinal()].size || id < 0){
            return null;
        }
        return (T)contentMap[type.ordinal()].get(id);
    }

    public <T extends Content> Array<T> getBy(ContentType type){
        return (Array<T>)contentMap[type.ordinal()];
    }

    // ---- 便捷访问器（与原版同名）----

    public Array<Block> blocks(){
        return getBy(ContentType.block);
    }

    public Block block(int id){
        return (Block)getByID(ContentType.block, id);
    }

    public Array<Item> items(){
        return getBy(ContentType.item);
    }

    public Item item(int id){
        return (Item)getByID(ContentType.item, id);
    }

    public Array<Liquid> liquids(){
        return getBy(ContentType.liquid);
    }

    public Liquid liquid(int id){
        return (Liquid)getByID(ContentType.liquid, id);
    }

    public Array<BulletType> bullets(){
        return getBy(ContentType.bullet);
    }

    public BulletType bullet(int id){
        return (BulletType)getByID(ContentType.bullet, id);
    }

    public Array<UnitType> units(){
        return getBy(ContentType.unit);
    }

    public UnitType unit(int id){
        return (UnitType)getByID(ContentType.unit, id);
    }
}
