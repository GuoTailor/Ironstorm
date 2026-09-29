package com.phoenix.game.mod;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.SerializationException;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.ReflectionException;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Bullets;
import com.phoenix.game.content.Fx;
import com.phoenix.game.content.Loadouts;
import com.phoenix.game.content.StatusEffects;
import com.phoenix.game.content.TechTree;
import com.phoenix.game.content.TechTree.TechNode;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.MockSound;
import com.phoenix.game.ctype.Content;
import com.phoenix.game.ctype.ContentType;
import com.phoenix.game.ctype.MappableContent;
import com.phoenix.game.ctype.UnlockableContent;
import com.phoenix.game.entities.Effects.Effect;
import com.phoenix.game.entities.bullet.BasicBulletType;
import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.func.Func;
import com.phoenix.game.game.Objective;
import com.phoenix.game.game.Objectives;
import com.phoenix.game.game.Schematic;
import com.phoenix.game.game.Schematics;
import com.phoenix.game.mod.Mods.LoadedMod;
import com.phoenix.game.struct.Array;
import com.phoenix.game.struct.StringMap;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.type.LiquidStack;
import com.phoenix.game.type.Mech;
import com.phoenix.game.type.StatusEffect;
import com.phoenix.game.type.UnitType;
import com.phoenix.game.type.Weapon;
import com.phoenix.game.type.Zone;
import com.phoenix.game.util.ArcAnnotate.NonNull;
import com.phoenix.game.util.Fi;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.consumers.Consume;
import com.phoenix.game.world.meta.BuildVisibility;
import com.phoenix.game.world.consumers.ConsumeItems;
import com.phoenix.game.world.consumers.ConsumeLiquid;
import com.phoenix.game.world.consumers.ConsumePower;
import io.anuke.mindustry.gen.Sounds;

import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

/**
 * Mod 内容解析器：把 mod 目录里的 {@code .json}/hjson 内容定义反序列化成 {@link Content} 实例。
 * 对应原版 {@code mindustry.mod.ContentParser}（613 行）。
 *
 * <p><b>与原版的差异 + TODO（本类是本批降级最重的一个）</b>：
 * <ol>
 *   <li><b>JSON 库 arc → libgdx</b>：{@code arc.util.serialization.Json} 换成
 *       {@code com.badlogic.gdx.utils.Json}（后者是前者的上游，{@code readValue}/{@code getFields}/
 *       {@code setIgnoreUnknownFields} 同名同签名，见 {@code io/JsonIO.java} 的对照表）。
 *       <br><b>但 1.14.2 有两处 API 被收窄，必须绕开</b>：
 *       <br>① {@code Json.getFields(Class)} 与 {@code Json.FieldMetadata} 都是 <b>private</b>
 *       （arc 的 fork 是 public）→ 原版 {@code readFields}/{@code checkNullFields} 里那套
 *       {@code parser.getFields(...)} + {@code FieldMetadata} 写法<b>无法编译</b>。
 *       替代：{@code readFields} 直接复用 libgdx <b>public</b> 的 {@code Json.readFields(Object, JsonValue)}
 *       （它内部会回调我们覆写的 readValue）；{@code checkNullFields} 改用
 *       {@code com.badlogic.gdx.utils.reflect.ClassReflection} 自行遍历。
 *       <br>② libgdx 的 {@code readValue} <b>没有</b> {@code (Class, Class, JsonValue, Class keyType)} 4 参重载，
 *       只有 3 参 {@code (Class, Class, JsonValue)} → 覆写点从 4 参改成 3 参
 *       （libgdx 的 readFields/fromJson 内部调用的正是 3 参那个，拦截效果一致）。</li>
 *   <li><b>没有 {@code Jval}</b>（arc 的容错 JSON 解析器）→ 原版
 *       {@code parser.fromJson(null, Jval.read(json).toString(Jformat.plain))} 里"先用 Jval 归一化、
 *       再把 {@code #} 转义成 {@code \#}"的两步都去掉，直接 {@code new JsonReader().parse(json)}。
 *       代价：hjson 风格（无引号键/单引号/注释）与"未加引号的 {@code #}"不再被容忍。</li>
 *   <li><b>没有 {@code Vars.tree}（mod 文件树）</b> → 非 base64 的 {@code Schematic} 解析退化成从
 *       {@code local/schematics/<name>.msch} 读；base64 内联蓝图分支已由 T5 补上（{@code Schematics.readBase64}）。</li>
 *   <li><b>没有 {@code ModContentInfo.mod}/{@code sourceFile}</b>（{@code Content.ModContentInfo} 只有
 *       {@code error}/{@code baseError}）、{@code Mods.LoadedMod} 也没有 {@code erroredContent}
 *       → {@code parse}/{@code markError} 里这几行省略。</li>
 *   <li><b>{@code Mods.LoadedMod} 没有 {@code name} 字段</b>（只有 {@code file/mainClass/main/hidden}）
 *       → 原版到处用的 {@code currentMod.name}/{@code mod.name}（内容名前缀）退化成
 *       {@link #modName(LoadedMod)}＝"jar/zip 文件名去掉扩展名"。要恢复原版语义得给
 *       {@code LoadedMod} 补一个从 {@code mod.json} 读出的 name（属改既有文件，本批未做）。</li>
 *   <li><b>没有 {@code Strings.parseInt/capitalize/parseException/getCauses}</b> →
 *       {@code capitalize} 内联；错误串只打异常类名 + 消息（不打因果链与完整堆栈）。</li>
 *   <li><b>没有 {@code JsonParseException}</b>（arc）→ {@code makeError} 不再区分 JSON 解析错误。</li>
 *   <li>{@code Block.buildVisibility} 已由 T2 批补齐 → 原版"有 requirements 且未显式设可见性就自动改成 shown"
 *       （{@code ContentParser:270-273}）已恢复，不再是缺口。</li>
 *   <li>{@code Objectives.ZoneWave}（依赖 {@code ZoneObjective}）已由 T3 启用 → {@code Objective} 解析器的
 *       默认类型恢复原版 {@code Objectives.ZoneWave}。</li>
 *   <li><b>libgdx {@code I18NBundle} 没有 {@code getProperties()}/{@code getParent()}</b> →
 *       {@code readBundle} 无法把 name/description 注入 bundle。退化成"直接写回内容对象"，
 *       且只对 {@link UnlockableContent} 生效；<b>全新内容的 localizedName 只能是内部名</b>。</li>
 *   <li><b>libgdx {@code AssetDescriptor} 没有 {@code loaded}/{@code errored} 回调字段</b>（arc 有）
 *       → {@code Sound} 解析器无法回填 {@code ModLoadingSound.sound}，退化成返回一个空代理；
 *       且本项目音频系统尚未启用（{@code Sounds.load()} 是注释状态）。</li>
 *   <li><b>libgdx {@code JsonValue} 没有 {@code asArray()}/{@code trace()}</b> →
 *       改用 {@code child}/{@code next} 链与 {@code name}；{@code addTrace(child.trace())} 换成字段名。</li>
 *   <li><b>libgdx {@code ObjectMap} 没有 {@code getThrow}</b> → 手写 null 判断后抛异常。</li>
 *   <li>{@code Item} 的单参构造 {@code Item(String)}（默认黑色）已由 T5 补齐 → 与原版一致。</li>
 *   <li><b>{@code UnitType} 的第二参是 {@code java.util.function.Supplier} 而非 arc 的 {@code Prov}</b>
 *       → {@code supply(...)} 的返回类型相应改成 {@code Supplier}。</li>
 * </ol>
 */
@SuppressWarnings("unchecked")
public class ContentParser{
    private static final boolean ignoreUnknownFields = true;
    private ObjectMap<Class<?>, ContentType> contentTypes = new ObjectMap<>();
    private StringMap legacyUnitMap = StringMap.of(
    "Dagger", "GroundUnit",
    "Eruptor", "GroundUnit",
    "Titan", "GroundUnit",
    "Fortress", "GroundUnit",
    "Crawler", "GroundUnit",
    "Revenant", "HoverUnit",
    "Draug", "MinerDrone",
    "Phantom", "BuilderDrone",
    "Spirit", "RepairDrone",
    "Wraith", "FlyingUnit",
    "Ghoul", "FlyingUnit"
    );
    private LoadedMod currentMod;
    private Content currentContent;
    private ObjectMap<Class<?>, FieldParser> classParsers = new ObjectMap<Class<?>, FieldParser>(){{
        put(Effect.class, (type, data) -> field(Fx.class, data));
        put(Schematic.class, (type, data) -> {
            Object result = fieldOpt(Loadouts.class, data);
            if(result != null){
                return result;
            }
            String str = data.asString();
            //T5：Schematics 已补 base64Header/readBase64 → 恢复原版的"内联 base64 蓝图"分支。
            //仍缺 Vars.tree（mod 文件树）→ 非 base64 时只能从本地 schematics/ 目录读。
            if(str.startsWith(Schematics.base64Header)){
                return Schematics.readBase64(str);
            }
            return Schematics.read(new FileInputStream(Core.files.local("schematics/" + str + "." + Vars.schematicExtension).file()));
        });
        put(StatusEffect.class, (type, data) -> {
            Object result = fieldOpt(StatusEffects.class, data);
            if(result != null){
                return result;
            }
            StatusEffect effect = new StatusEffect(modName(currentMod) + "-" + data.getString("name"));
            readFields(effect, data);
            return effect;
        });
        put(Color.class, (type, data) -> Color.valueOf(data.asString()));
        put(BulletType.class, (type, data) -> {
            if(data.isString()){
                return field(Bullets.class, data);
            }
            Class<? extends BulletType> bc = data.has("type") ? resolve(data.getString("type"), "com.phoenix.game.entities.bullet") : BasicBulletType.class;
            data.remove("type");
            BulletType result = make(bc);
            readFields(result, data);
            return result;
        });
        put(Sound.class, (type, data) -> {
            Object vanilla = fieldOpt(Sounds.class, data);
            if(vanilla != null) return vanilla;
            if(Vars.headless) return new MockSound();

            //TODO 原版：算出 ogg/mp3 路径后 Core.assets.load(path, Sound.class)，并把
            //     AssetDescriptor.loaded/errored 回调挂上去，加载完成后回填 ModLoadingSound.sound。
            //     libgdx 的 AssetDescriptor 没有这两个回调字段 → 这里只能返回一个内部仍是 MockSound 的代理。
            //     另外本项目音频系统尚未启用（Sounds.load() 在 ClientLauncher 里是注释状态）。
            return new ModLoadingSound();
        });
        put(Objective.class, (type, data) -> {
            //原版默认类型是 ZoneWave.class（T3 批已启用 Objectives.ZoneWave/ZoneObjective → 恢复原版默认）。
            Class<? extends Objective> oc = data.has("type") ? resolve(data.getString("type"), "com.phoenix.game.game.Objectives") : Objectives.ZoneWave.class;
            data.remove("type");
            Objective obj = make(oc);
            readFields(obj, data);
            return obj;
        });
        put(Weapon.class, (type, data) -> {
            Weapon weapon = new Weapon();
            readFields(weapon, data);
            weapon.name = modName(currentMod) + "-" + weapon.name;
            return weapon;
        });
    }};
    /** Stores things that need to be parsed fully, e.g. reading fields of content.
     * This is done to accomodate binding of content names first.*/
    private Array<Runnable> reads = new Array<>();
    private Array<Runnable> postreads = new Array<>();
    private ObjectSet<Object> toBeParsed = new ObjectSet<>();

    private Json parser = new Json(){
        {
            //原版靠 {@code ignoreUnknownFields} 常量控制"未知字段是否忽略"；libgdx Json 有同名开关
            //（默认就是 true）→ 这里显式同步，保证常量是真正生效的。
            //注意必须写全限定名：Json 自己也有个 protected 的 {@code ignoreUnknownFields} 字段会遮住外层常量。
            setIgnoreUnknownFields(ContentParser.ignoreUnknownFields);
        }

        //⚠ 覆写点与原版不同：原版覆写的是 arc 的 4 参
        //  {@code readValue(Class, Class, JsonValue, Class keyType)}；libgdx 1.14.2 的 Json
        //  <b>没有</b> keyType 版本，只有 3 参 {@code readValue(Class, Class, JsonValue)}。
        //  好消息：libgdx 自己的 readFields/fromJson 内部调用的正是这个 3 参重载
        //  （{@code field.set(object, readValue(field.getType(), metadata.elementType, child))}），
        //  所以覆写它同样能拦截"所有字段读取"。
        @Override
        public <T> T readValue(Class<T> type, Class elementType, JsonValue jsonData){
            T t = internalRead(type, elementType, jsonData);
            if(t != null) checkNullFields(t);
            return t;
        }

        private <T> T internalRead(Class<T> type, Class elementType, JsonValue jsonData){
            if(type != null){
                if(classParsers.containsKey(type)){
                    try{
                        return (T)classParsers.get(type).parse(type, jsonData);
                    }catch(Exception e){
                        throw new RuntimeException(e);
                    }
                }

                //try to parse "item/amount" syntax
                if(type == ItemStack.class && jsonData.isString() && jsonData.asString().contains("/")){
                    String[] split = jsonData.asString().split("/");

                    return (T)fromJson(ItemStack.class, "{item: " + split[0] + ", amount: " + split[1] + "}");
                }

                //try to parse "liquid/amount" syntax
                if(jsonData.isString() && jsonData.asString().contains("/")){
                    String[] split = jsonData.asString().split("/");
                    if(type == LiquidStack.class){
                        return (T)fromJson(LiquidStack.class, "{liquid: " + split[0] + ", amount: " + split[1] + "}");
                    }else if(type == ConsumeLiquid.class){
                        return (T)fromJson(ConsumeLiquid.class, "{liquid: " + split[0] + ", amount: " + split[1] + "}");
                    }
                }

                if(Content.class.isAssignableFrom(type)){
                    //libgdx 的 ObjectMap 没有 getThrow（arc 有）→ 手写判定
                    ContentType ctype = contentTypes.get(type);
                    if(ctype == null) throw new IllegalArgumentException("No content type for class: " + type.getSimpleName());
                    String prefix = currentMod != null ? modName(currentMod) + "-" : "";
                    T one = (T)Vars.content.getByName(ctype, prefix + jsonData.asString());
                    if(one != null) return one;
                    T two = (T)Vars.content.getByName(ctype, jsonData.asString());

                    if(two != null) return two;
                    throw new IllegalArgumentException("\"" + jsonData.name + "\": No " + ctype + " found with name '" + jsonData.asString() + "'.\nMake sure '" + jsonData.asString() + "' is spelled correctly, and that it really exists!\nThis may also occur because its file failed to parse.");
                }
            }

            return super.readValue(type, elementType, jsonData);
        }
    };

    private ObjectMap<ContentType, TypeParser<?>> parsers = makeParsers();

    /** 构建内容类型 → 解析器映射。
     * <p>原版是字段初始化里的 {@code ObjectMap.of(...)}（libgdx 的 ObjectMap.of 只到 5 对，这里手写 put 更稳）。 */
    private ObjectMap<ContentType, TypeParser<?>> makeParsers(){
        ObjectMap<ContentType, TypeParser<?>> map = new ObjectMap<>();

        map.put(ContentType.block, (TypeParser<Block>)(mod, name, value) -> {
            readBundle(ContentType.block, name, value);

            Block block;

            if(locate(ContentType.block, name) != null){
                block = locate(ContentType.block, name);

                if(value.has("type")){
                    throw new IllegalArgumentException("When defining properties for an existing block, you must not re-declare its type. The original type will be used. Block: " + name);
                }
            }else{
                //TODO generate dynamically instead of doing.. this
                Class<? extends Block> type;

                try{
                    type = resolve(getType(value),
                    "com.phoenix.game.world",
                    "com.phoenix.game.world.blocks",
                    "com.phoenix.game.world.blocks.defense",
                    "com.phoenix.game.world.blocks.defense.turrets",
                    "com.phoenix.game.world.blocks.distribution",
                    "com.phoenix.game.world.blocks.liquid",
                    "com.phoenix.game.world.blocks.logic",
                    "com.phoenix.game.world.blocks.power",
                    "com.phoenix.game.world.blocks.production",
                    "com.phoenix.game.world.blocks.sandbox",
                    "com.phoenix.game.world.blocks.storage",
                    "com.phoenix.game.world.blocks.units"
                    );
                }catch(IllegalArgumentException e){
                    type = Block.class;
                }

                block = make(type, mod + "-" + name);
            }

            currentContent = block;

            String[] research = {null};

            //add research tech node
            if(value.has("research")){
                research[0] = value.get("research").asString();
                value.remove("research");
            }

            read(() -> {
                if(value.has("consumes")){
                    for(JsonValue child : value.get("consumes")){
                        if(child.name.equals("item")){
                            block.consumes.item(find(ContentType.item, child.asString()));
                        }else if(child.name.equals("items")){
                            block.consumes.add((Consume)parser.readValue(ConsumeItems.class, child));
                        }else if(child.name.equals("liquid")){
                            block.consumes.add((Consume)parser.readValue(ConsumeLiquid.class, child));
                        }else if(child.name.equals("power")){
                            if(child.isNumber()){
                                block.consumes.power(child.asFloat());
                            }else{
                                block.consumes.add((Consume)parser.readValue(ConsumePower.class, child));
                            }
                        }else if(child.name.equals("powerBuffered")){
                            block.consumes.powerBuffered(child.asFloat());
                        }else{
                            throw new IllegalArgumentException("Unknown consumption type: '" + child.name + "' for block '" + block.name + "'.");
                        }
                    }
                    value.remove("consumes");
                }

                readFields(block, value, true);

                if(block.size > 8){
                    throw new IllegalArgumentException("Blocks cannot be larger than 8x8.");
                }

                //add research tech node
                if(research[0] != null){
                    Block parent = find(ContentType.block, research[0]);
                    TechNode baseNode = TechTree.create(parent, block);
                    LoadedMod cur = currentMod;

                    postreads.add(() -> {
                        currentContent = block;
                        currentMod = cur;

                        TechNode parnode = TechTree.all.find(t -> t.block == parent);
                        if(parnode == null){
                            throw new IllegalArgumentException("Block '" + parent.name + "' isn't in the tech tree, but '" + block.name + "' requires it to be researched.");
                        }
                        if(!parnode.children.contains(baseNode, true)){
                            parnode.children.add(baseNode);
                        }
                    });
                }

                //make block visible by default if there are requirements and no visibility set
                //（对应原版 ContentParser.java:270-273；T2 批补齐 Block.buildVisibility 后恢复）
                if(value.has("requirements") && block.buildVisibility == BuildVisibility.hidden){
                    block.buildVisibility = BuildVisibility.shown;
                }
            });

            return block;
        });

        map.put(ContentType.unit, (TypeParser<UnitType>)(mod, name, value) -> {
            readBundle(ContentType.unit, name, value);

            UnitType unit;
            if(locate(ContentType.unit, name) == null){
                String typeName = getType(value);
                Class<BaseUnit> type = resolve(legacyUnitMap.get(capitalize(typeName), typeName), "com.phoenix.game.entities.type.base");
                unit = new UnitType(mod + "-" + name, supply(type));
            }else{
                unit = locate(ContentType.unit, name);
            }

            currentContent = unit;
            read(() -> readFields(unit, value, true));

            return unit;
        });

        //T5：已给 Item 补上单参构造 Item(String)（默认黑色，与原版一致）→ 恢复原版写法。
        map.put(ContentType.item, parser(ContentType.item, name -> new Item(name)));
        map.put(ContentType.liquid, parser(ContentType.liquid, Liquid::new));
        map.put(ContentType.mech, parser(ContentType.mech, Mech::new));
        map.put(ContentType.zone, parser(ContentType.zone, Zone::new));

        return map;
    }

    private String getString(JsonValue value, String key){
        if(value.has(key)){
            return value.getString(key);
        }else{
            throw new IllegalArgumentException("You are missing a \"" + key + "\". It must be added before the file can be parsed.");
        }
    }

    private String getType(JsonValue value){
        return getString(value, "type");
    }

    /** 首字母大写。原版是 {@code Strings.capitalize}，本项目 Strings 没有 → 内联。 */
    private static String capitalize(String s){
        if(s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private <T extends Content> T find(ContentType type, String name){
        Content c = Vars.content.getByName(type, name);
        if(c == null) c = Vars.content.getByName(type, modName(currentMod) + "-" + name);
        if(c == null) throw new IllegalArgumentException("No " + type + " found with name '" + name + "'");
        return (T)c;
    }

    private <T extends Content> TypeParser<T> parser(ContentType type, Func<String, T> constructor){
        return (mod, name, value) -> {
            T item;
            if(Vars.content.getByName(type, name) != null){
                item = (T)Vars.content.getByName(type, name);
                readBundle(type, name, value);
            }else{
                readBundle(type, name, value);
                item = constructor.get(mod + "-" + name);
            }
            currentContent = item;
            read(() -> readFields(item, value));
            return item;
        };
    }

    /**
     * 读取 name/description 并写回内容对象。
     * <p><b>偏离</b>：原版把它们注入最外层 {@code I18NBundle} 的 {@code Properties}，
     * 让新建内容的构造函数也能查到；libgdx 的 {@code I18NBundle} 没有
     * {@code getProperties()}/{@code getParent()} → 无法注入，只能直接写回
     * {@link UnlockableContent} 的 {@code localizedName}/{@code description}。
     * <p>TODO 因此<b>全新 mod 内容的 localizedName 只能是内部名</b>（构造时 bundle 里还没有这条键）；
     * 等自定义 bundle 注入机制补上后再接线。
     */
    private void readBundle(ContentType type, String name, JsonValue value){
        Content located = locate(type, name);
        UnlockableContent cont = located instanceof UnlockableContent ? (UnlockableContent)located : null;

        if(value.has("name")){
            if(cont != null) cont.localizedName = value.getString("name");
            value.remove("name");
        }

        if(value.has("description")){
            if(cont != null) cont.description = value.getString("description");
            value.remove("description");
        }
    }

    /** Call to read a content's extra info later.*/
    private void read(Runnable run){
        Content cont = currentContent;
        LoadedMod mod = currentMod;
        reads.add(() -> {
            this.currentMod = mod;
            this.currentContent = cont;
            run.run();
        });
    }

    private void init(){
        for(ContentType type : ContentType.all){
            com.badlogic.gdx.utils.Array<Content> arr = Vars.content.getBy(type);
            if(!arr.isEmpty()){
                Class<?> c = arr.first().getClass();
                //get base content class, skipping intermediates
                while(!(c.getSuperclass() == Content.class || c.getSuperclass() == UnlockableContent.class || Modifier.isAbstract(c.getSuperclass().getModifiers()))){
                    c = c.getSuperclass();
                }

                contentTypes.put(c, type);
            }
        }
    }

    private void attempt(Runnable run){
        try{
            run.run();
        }catch(Throwable t){
            //don't overwrite double errors
            markError(currentContent, t);
        }
    }

    public void finishParsing(){
        reads.each(this::attempt);
        postreads.each(this::attempt);
        reads.clear();
        postreads.clear();
        toBeParsed.clear();
    }

    /**
     * Parses content from a json file.
     * @param name the name of the file without its extension
     * @param json the json to parse
     * @param type the type of content this is
     * @param file file that this content is being parsed from
     * @return the content that was parsed
     */
    public Content parse(LoadedMod mod, String name, String json, Fi file, ContentType type) throws Exception{
        if(contentTypes.isEmpty()){
            init();
        }

        //TODO 原版先用 arc 的 Jval 归一化 JSON（容忍 hjson 风格），并对 .json 做 json.replace("#", "\\#")
        //     的容错；本项目没有 Jval，且 libgdx 的 JsonReader 不认 \# 转义 → 两步都去掉，直接解析。
        JsonValue value = new JsonReader().parse(json);

        if(!parsers.containsKey(type)){
            throw new SerializationException("No parsers for content type '" + type + "'");
        }

        currentMod = mod;
        Content c = parsers.get(type).parse(modName(mod), name, value);
        //TODO 原版这里还有：c.minfo.sourceFile = file; if(!located) c.minfo.mod = mod;
        //     本项目 Content.ModContentInfo 没有 sourceFile/mod 两个字段 → 省略。
        toBeParsed.add(c);

        return c;
    }

    public void markError(Content content, LoadedMod mod, Fi file, Throwable error){
        //TODO 原版还写 content.minfo.mod / content.minfo.sourceFile，并把内容加进 mod.erroredContent；
        //     本项目 ModContentInfo 只有 error/baseError，Mods.LoadedMod 也没有 erroredContent → 省略。
        content.minfo.error = makeError(error, file);
        content.minfo.baseError = error;
    }

    public void markError(Content content, Throwable error){
        if(content.minfo != null && !content.hasErrored()){
            //TODO 原版传的是 content.minfo.mod / content.minfo.sourceFile；
            //     本项目 ModContentInfo 没有这两个字段 → 只能传 null。
            markError(content, null, null, error);
        }
    }

    private String makeError(Throwable t, Fi file){
        StringBuilder builder = new StringBuilder();
        builder.append("[lightgray]").append("File: ").append(file == null ? "?" : file.name()).append("[]\n\n");

        //TODO 原版区分 JsonParseException（arc），并用 Strings.parseException/getCauses 打完整因果链与堆栈；
        //     本项目没有这些工具 → 只打异常类名 + 消息（剥掉包名前缀）。
        builder.append("[accent][[").append(t.getClass().getSimpleName().replace("Exception", ""))
        .append("][] ")
        .append(t.getMessage() != null ?
        t.getMessage().replace("com.phoenix.game.", "").replace("mindustry.", "").replace("arc.", "") : "").append("\n");
        return builder.toString();
    }

    private <T extends MappableContent> T locate(ContentType type, String name){
        T first = Vars.content.getByName(type, name); //try vanilla replacement
        return first != null ? first : Vars.content.getByName(type, modName(currentMod) + "-" + name);
    }

    /** mod 名（内容名前缀用）。
     * <p><b>偏离</b>：原版用 {@code LoadedMod.name}（mod.json 里的展示名）；本项目
     * {@code Mods.LoadedMod} 只有 {@code file/mainClass/main/hidden}、<b>没有 name</b>
     * → 退化成"jar/zip 文件名去掉扩展名"。
     * <p>TODO 想恢复原版语义，需要给 {@code Mods.LoadedMod} 补一个从 {@code mod.json} 读出的
     * {@code name} 字段（属改既有文件，本批未做）。 */
    private static String modName(LoadedMod mod){
        if(mod == null) return "mod";
        String n = mod.file == null ? null : mod.file.getName();
        if(n == null || n.isEmpty()) return "mod";
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private <T> T make(Class<T> type){
        try{
            Constructor<T> cons = type.getDeclaredConstructor();
            cons.setAccessible(true);
            return cons.newInstance();
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    private <T> T make(Class<T> type, String name){
        try{
            Constructor<T> cons = type.getDeclaredConstructor(String.class);
            cons.setAccessible(true);
            return cons.newInstance(name);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    /** 无参构造供给器。原版返回 arc 的 {@code Prov<T>}，本项目 {@code UnitType} 的第二参是
     * {@code java.util.function.Supplier} → 这里相应返回 {@code Supplier}。 */
    private <T> java.util.function.Supplier<T> supply(Class<T> type){
        try{
            Constructor<T> cons = type.getDeclaredConstructor();
            return () -> {
                try{
                    return cons.newInstance();
                }catch(Exception e){
                    throw new RuntimeException(e);
                }
            };
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    private Object field(Class<?> type, JsonValue value){
        return field(type, value.asString());
    }

    /** Gets a field from a static class by name, throwing a descriptive exception if not found. */
    private Object field(Class<?> type, String name){
        try{
            Object b = type.getField(name).get(null);
            if(b == null) throw new IllegalArgumentException(type.getSimpleName() + ": not found: '" + name + "'");
            return b;
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    private Object fieldOpt(Class<?> type, JsonValue value){
        try{
            return type.getField(value.asString()).get(null);
        }catch(Exception e){
            return null;
        }
    }

    private void checkNullFields(Object object){
        if(object instanceof Number || object instanceof String || toBeParsed.contains(object)) return;

        //⚠ 原版用 {@code parser.getFields(Class)} + {@code Json.FieldMetadata}；
        //   libgdx 1.14.2 里这两个都是 <b>private</b>（arc 的 fork 是 public）→
        //   改用 {@code com.badlogic.gdx.utils.reflect.ClassReflection} 自己遍历类层次的声明字段，
        //   跳过 static/transient/synthetic（与 libgdx Json 内部 getFields 的口径一致）。
        for(Class<?> c = object.getClass(); c != null && c != Object.class; c = c.getSuperclass()){
            for(com.badlogic.gdx.utils.reflect.Field field : ClassReflection.getDeclaredFields(c)){
                if(field.isStatic() || field.isTransient() || field.isSynthetic()) continue;
                if(field.getType().isPrimitive()) continue;
                if(!field.isAccessible()) field.setAccessible(true);

                //注：本项目 ArcAnnotate.NonNull 是 CLASS 保留期（与原版 arc 一致），
                //    所以这个判定运行期恒为 false —— 与原版行为相同，保留只为对齐代码形状。
                //    （原版遇 primitive 字段是 return，这里改成 continue：同样恒不生效，但语义更合理。）
                //    另：libgdx 的 ReflectionException 是<b>受检</b>异常（arc 的是 RuntimeException）→ 必须捕获。
                try{
                    if(field.isAnnotationPresent(NonNull.class) && field.get(object) == null){
                        throw new RuntimeException("'" + field.getName() + "' in " + object.getClass().getSimpleName() + " is missing!");
                    }
                }catch(ReflectionException e){
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void readFields(Object object, JsonValue jsonMap, boolean stripType){
        if(stripType) jsonMap.remove("type");
        readFields(object, jsonMap);
    }

    private void readFields(Object object, JsonValue jsonMap){
        toBeParsed.remove(object);
        //⚠ 原版自己用 parser.getFields(...)+FieldMetadata 遍历字段；libgdx 里那两个是 private，
        //   但 libgdx 的 {@code Json.readFields(Object, JsonValue)} 是 <b>public</b> 的，
        //   且内部会调用我们覆写的 3 参 readValue → 直接复用（未知字段忽略、异常加 trace 的行为一致）。
        parser.readFields(object, jsonMap);
    }

    /** Tries to resolve a class from a list of potential class names. */
    private <T> Class<T> resolve(String base, String... potentials){
        if(!base.isEmpty() && Character.isLowerCase(base.charAt(0))) base = capitalize(base);

        for(String type : potentials){
            try{
                return (Class<T>)Class.forName(type + '.' + base);
            }catch(Exception ignored){
                try{
                    return (Class<T>)Class.forName(type + '$' + base);
                }catch(Exception ignored2){
                }
            }
        }
        throw new IllegalArgumentException("Types not found: " + base + "." + potentials[0]);
    }

    private interface FieldParser{
        Object parse(Class<?> type, JsonValue value) throws Exception;
    }

    private interface TypeParser<T extends Content>{
        T parse(String mod, String name, JsonValue value) throws Exception;
    }
}
