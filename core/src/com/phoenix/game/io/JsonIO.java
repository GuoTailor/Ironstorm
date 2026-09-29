package com.phoenix.game.io;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Items;
import com.phoenix.game.ctype.ContentType;
import com.phoenix.game.ctype.MappableContent;
import com.phoenix.game.game.Rules;
import com.phoenix.game.game.SpawnGroup;
import com.phoenix.game.game.Team;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.type.Zone;
import com.phoenix.game.world.Block;

import java.io.IOException;

/**
 * 全局 JSON 门面。参照 Mindustry {@code mindustry.io.JsonIO} 移植。
 *
 * <p><b>本类最关键的一处替换：JSON 库从 arc 换成 libgdx</b>。
 * 原版用的是 {@code arc.util.serialization.Json}（它本身就是 libgdx {@code com.badlogic.gdx.utils.Json}
 * 的 fork），API 高度相似但包路径不同。逐条对照结论见下（这也是本批最容易踩坑的地方）：
 *
 * <table border="1">
 *   <caption>arc Json → libgdx Json 对照</caption>
 *   <tr><th>原版用法</th><th>libgdx 1.14.2</th><th>处理</th></tr>
 *   <tr><td>{@code new Json()}</td><td>存在</td><td>直接用</td></tr>
 *   <tr><td>{@code writeValue(Object, Class, Class)} 覆写</td><td>存在（public，可覆写）</td><td>照抄</td></tr>
 *   <tr><td>{@code getWriter().value(...)}</td><td>存在（{@code getWriter()} 返回 {@code JsonWriter}）</td><td>照抄，仍需 catch {@code IOException}</td></tr>
 *   <tr><td>{@code convertToString(Object)} 覆写</td><td><b>没有</b>：libgdx 里它是 <b>private</b>，不可覆写</td>
 *       <td>整段删除。影响仅限"以内容对象作 map key"的场景：libgdx 走
 *           {@code String.valueOf(object)} → {@code toString()}，而本项目
 *           {@code MappableContent.toString()} 恰好返回 {@code name} → <b>行为等价</b>，无需降级</td></tr>
 *   <tr><td>{@code setIgnoreUnknownFields}</td><td>存在</td><td>照抄</td></tr>
 *   <tr><td>{@code setElementType(Class, String, Class)}</td><td>存在（字段不存在会抛 {@code SerializationException}）</td><td>照抄</td></tr>
 *   <tr><td>{@code setSerializer(Class, Serializer)}</td><td>存在，{@code Json.Serializer<T>} 同名同签名</td><td>照抄</td></tr>
 *   <tr><td>{@code getSerializer(Class).read(...)}</td><td>存在</td><td>照抄</td></tr>
 *   <tr><td>{@code toJson(obj, Class)} / {@code fromJson(Class, String)} / {@code prettyPrint(String)}</td><td>都存在</td><td>照抄</td></tr>
 *   <tr><td>{@code readValue(Class, Class, JsonValue)} / {@code protected newInstance(Class)}</td><td>都存在</td><td>照抄</td></tr>
 *   <tr><td>{@code new JsonReader().parse(String)} / {@code JsonValue.asString()/asInt()/get()/getInt()}</td><td>都存在</td><td>照抄</td></tr>
 *   <tr><td>{@code setUsePrototypes}</td><td>存在（本类原版没用到）</td><td>不需要</td></tr>
 * </table>
 *
 * <p><b>与原版的差异 + 刻意降级</b>
 * <ul>
 *   <li>{@code read(Class, String)} 里原版做 {@code string.replace("io.anuke.", "")} 来修老存档里的类名。
 *       本项目类名是 {@code com.phoenix.game.*}，而老存档里写的是 {@code io.anuke.mindustry.*}
 *       或 {@code mindustry.*} → 这里按顺序把这两个前缀替换成 {@code com.phoenix.game.}（尽力而为）。
 *       TODO 真正的类名映射表（尤其是注解处理器生成的 {@code io.anuke.mindustry.gen.Serialization}
 *       在本项目根本不存在）没有落地，所以跨版本的类标签反序列化仍然会失败。
 *       （已 grep 确认：本项目 gen 包只有 {@code Icon/Tex/Sounds/Musics}，无 {@code Serialization}。）</li>
 *   <li>Zone 的 read 里原版直接 {@code getByName(ContentType.zone, jsonData.asString())}，
 *       JSON 里是 null 时会把 null 当 key 传给 ObjectMap（libgdx 会 NPE）→ 这里加了 null 守卫（返回 null）。
 *       其它三个序列化器（Item/Block/Team）原版本来就安全，照抄。</li>
 *   <li><b>调用点已接</b>：{@code maps/Map.rules()}（{@code Map.java:186}）与
 *       {@code maps/Maps.readFilters()}（{@code Maps.java:175}）已在用本类的 {@code JsonIO.read}，
 *       {@code io/SaveVersion} 也用 {@code JsonIO.write/read} 读写 rules/stats。原注释
 *       "本类**没有调用点**"**已过时**（grep 已证伪）。{@code SaveIO} 自身的 meta 走自有 JSON 快照，
 *       不经过本类（那部分另算）。</li>
 * </ul>
 */
@SuppressWarnings("unchecked")
public class JsonIO{
    private static CustomJson jsonBase = new CustomJson();
    private static Json json = new Json(){
        {
            apply(this);
        }

        @Override
        public void writeValue(Object value, Class knownType, Class elementType){
            if(value instanceof MappableContent){
                try{
                    getWriter().value(((MappableContent)value).name);
                }catch(IOException e){
                    throw new RuntimeException(e);
                }
            }else{
                super.writeValue(value, knownType, elementType);
            }
        }

        //刻意降级（等价替代）：原版还覆写了 convertToString(Object) 让 MappableContent 作 map key 时写成 name；
        //     libgdx 1.14.2 里 convertToString(Object) 是 **private**，无法覆写 → 整段删除。
        //     影响面：libgdx 内部走 String.valueOf(object) → toString()，而本项目
        //     MappableContent.toString() 返回 name（已核实），所以序列化结果与原版一致，不需要降级实现。
    };

    public static Json json(){
        return json;
    }

    public static String write(Object object){
        return json.toJson(object, object.getClass());
    }

    public static <T> T copy(T object){
        return read((Class<T>)object.getClass(), write(object));
    }

    public static <T> T read(Class<T> type, String string){
        return json.fromJson(type, fixLegacyClassTags(string));
    }

    public static <T> T read(Class<T> type, T base, String string){
        return jsonBase.fromBaseJson(type, base, fixLegacyClassTags(string));
    }

    public static String print(String in){
        return json.prettyPrint(in);
    }

    /**
     * 修老存档里写死的旧包名。原版是 {@code replace("io.anuke.", "")}。
     * <p>TODO 真正的类名映射表未落地：本项目类名是 {@code com.phoenix.game.*}，
     * 老存档里的 {@code io.anuke.mindustry.gen.Serialization} 之类在本项目并不存在
     * （已 grep 确认无该生成类），这里只做前缀替换（尽力而为），反序列化仍可能因找不到类而失败。
     */
    private static String fixLegacyClassTags(String string){
        if(string == null) return null;
        //顺序不能反：先剥外层 io.anuke.，再把剩下的 mindustry. 换成 com.phoenix.game.
        return string.replace("io.anuke.", "").replace("mindustry.", "com.phoenix.game.");
    }

    private static void apply(Json json){
        json.setIgnoreUnknownFields(true);
        json.setElementType(Rules.class, "spawns", SpawnGroup.class);
        json.setElementType(Rules.class, "loadout", ItemStack.class);
        //T2：Rules 新增 ObjectSet<Block> bannedBlocks（libgdx Json 原生支持 ObjectSet，这里补元素类型）
        json.setElementType(Rules.class, "bannedBlocks", Block.class);

        //T2：Rules 新增 Color ambientLight。libgdx 的 Json **没有**内建 Color 支持（arc 的 fork 才有），
        //不注册序列化器会走反射、格式不稳定 → 显式按 r/g/b/a 四个 float 写。
        json.setSerializer(com.badlogic.gdx.graphics.Color.class, new Json.Serializer<com.badlogic.gdx.graphics.Color>(){
            @Override
            public void write(Json json, com.badlogic.gdx.graphics.Color object, Class knownType){
                json.writeObjectStart();
                json.writeValue("r", object.r);
                json.writeValue("g", object.g);
                json.writeValue("b", object.b);
                json.writeValue("a", object.a);
                json.writeObjectEnd();
            }

            @Override
            public com.badlogic.gdx.graphics.Color read(Json json, JsonValue jsonData, Class type){
                return new com.badlogic.gdx.graphics.Color(
                    jsonData.getFloat("r"), jsonData.getFloat("g"), jsonData.getFloat("b"), jsonData.getFloat("a"));
            }
        });

        json.setSerializer(Zone.class, new Json.Serializer<Zone>(){
            @Override
            public void write(Json json, Zone object, Class knownType){
                json.writeValue(object.name);
            }

            @Override
            public Zone read(Json json, JsonValue jsonData, Class type){
                String name = jsonData.asString();
                //原版直接把可能为 null 的名字传给 getByName（libgdx 的 ObjectMap 会 NPE）→ 这里加守卫
                return name == null ? null : Vars.content.<Zone>getByName(ContentType.zone, name);
            }
        });

        json.setSerializer(Item.class, new Json.Serializer<Item>(){
            @Override
            public void write(Json json, Item object, Class knownType){
                json.writeValue(object.name);
            }

            @Override
            public Item read(Json json, JsonValue jsonData, Class type){
                if(jsonData.asString() == null) return Items.copper;
                Item i = Vars.content.getByName(ContentType.item, jsonData.asString());
                return i == null ? Items.copper : i;
            }
        });

        json.setSerializer(Team.class, new Json.Serializer<Team>(){
            @Override
            public void write(Json json, Team object, Class knownType){
                json.writeValue(object.id);
            }

            @Override
            public Team read(Json json, JsonValue jsonData, Class type){
                return Team.get(jsonData.asInt());
            }
        });

        json.setSerializer(Block.class, new Json.Serializer<Block>(){
            @Override
            public void write(Json json, Block object, Class knownType){
                json.writeValue(object.name);
            }

            @Override
            public Block read(Json json, JsonValue jsonData, Class type){
                return Vars.content.getByName(ContentType.block, jsonData.asString());
            }
        });

        json.setSerializer(ItemStack.class, new Json.Serializer<ItemStack>(){
            @Override
            public void write(Json json, ItemStack object, Class knownType){
                json.writeObjectStart();
                json.writeValue("item", object.item);
                json.writeValue("amount", object.amount);
                json.writeObjectEnd();
            }

            @Override
            public ItemStack read(Json json, JsonValue jsonData, Class type){
                return new ItemStack(json.getSerializer(Item.class).read(json, jsonData.get("item"), Item.class), jsonData.getInt("amount"));
            }
        });
    }

    static class CustomJson extends Json{
        private Object baseObject;

        {
            apply(this);
        }

        @Override
        public <T> T fromJson(Class<T> type, String json){
            return fromBaseJson(type, null, json);
        }

        public <T> T fromBaseJson(Class<T> type, T base, String json){
            this.baseObject = base;
            return readValue(type, null, new JsonReader().parse(json));
        }

        @Override
        protected Object newInstance(Class type){
            if(baseObject == null || baseObject.getClass() != type){
                return super.newInstance(type);
            }
            return baseObject;
        }
    }
}
