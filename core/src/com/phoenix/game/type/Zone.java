package com.phoenix.game.type;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.content.Loadouts;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Events;
import com.phoenix.game.ctype.ContentType;
import com.phoenix.game.ctype.UnlockableContent;
import com.phoenix.game.func.Cons;
import com.phoenix.game.game.EventType;
import com.phoenix.game.game.Objective;
import com.phoenix.game.game.Objectives;
import com.phoenix.game.game.Objectives.ZoneWave;
import com.phoenix.game.game.Rules;
import com.phoenix.game.game.Schematic;
import com.phoenix.game.maps.Map;
import com.phoenix.game.maps.generators.Generator;
import com.phoenix.game.maps.generators.MapGenerator;
import com.phoenix.game.struct.Array;
import com.phoenix.game.util.ArcAnnotate.*;

import static com.phoenix.game.Vars.*;

/**
 * 战役区域（一张有条件的战役地图）。参照 Mindustry mindustry.type.Zone 移植。
 * <p>一个 Zone = 一张地图 + 解锁条件（requirements）+ 发射消耗（launchCost）+ 起始物品（startingItems）。
 * <p>内容集合见 {@code content/Zones}。
 *
 * <p><b>与本项目的差异（逐条对应原版写法）</b>：Zone 是原版战役系统里依赖面最广的类型。
 * <ul>
 *   <li>{@code generator} / 带 generator 的构造函数 / {@link #getRules()} 的 {@code MapGenerator} 分支
 *       / {@code init()} 里的 {@code generator.init(loadout)} <b>已回填</b>（{@code Generator}/{@code MapGenerator}
 *       都在 {@code maps/generators/}）。唯一区别：本项目地图按名字扫目录，{@code getMap()} 可能为 null
 *       → {@link #getRules()} 多一层 null 守卫退回手写 rules。</li>
 *   <li>{@code Objectives.ZoneObjective}/{@code ZoneWave}/{@code Launched} <b>已启用</b>
 *       （它们依赖本类，但 Java 允许同包循环引用）→ {@link #configureObjective} 默认 {@code new ZoneWave(this, 15)}。</li>
 *   <li>{@code content.zones()}（ContentLoader 没有 zones() 访问器，本批不许改它）、
 *       {@code EventType.ZoneRequireCompleteEvent}/{@code ZoneConfigureCompleteEvent}（未移植）
 *       → {@link #updateObjectives} 仍只保留 {@code closure.run()}（缺事件系统，扫描循环接不上）。</li>
 *   <li>{@code data.modified()} <b>已回填</b>（GlobalData 补了 {@code modified()}）；
 *       {@code data.isUnlocked(this)} 已在 S4a 批接线 → {@link #canUnlock()} 照原版
 *       （{@code Vars.data} 为 null 时退化为只看 requirements）。</li>
 *   <li>已接线（S2 批次）<b>起始物品的持久化</b>：原版用 {@code Core.settings.getObject/putObject}
 *       （arc 的 TypeSerializer + UBJson 二进制）；本项目 {@code Settings} 已补 {@code getObject/putObject}
 *       （走 {@code io/JsonIO} 的 JSON 字符串），但 {@code ItemStack} 字段是 {@code final} 且无无参构造器
 *       → libgdx Json 无法往返，故改用"名字:数量,名字:数量"编码（同 {@code GlobalData.unlocks} 的处置）。</li>
 *   <li>{@code state.rules.attackMode} <b>已接线</b>（T2 批给 Rules 补了字段）→ {@link #metCondition()} 照原版。</li>
 *   <li>{@code minfo.mod}（本项目的 {@code Content.ModContentInfo} 没有 mod）→ init() 里的前缀处理 TODO。</li>
 *   <li>{@code ItemStack.amount} 在本项目是 {@code final}（原版可变）→ 累加/清零改成重建 ItemStack（等价替代）；
 *       另外 {@code ItemStack} 未实现 Comparable → 排序改用 {@code sortComparing}（等价替代）。</li>
 * </ul>
 */
public class Zone extends UnlockableContent{
    public @NonNull Generator generator;
    public @NonNull Objective configureObjective = new ZoneWave(this, 15);
    public Array<Objective> requirements = new Array<>();
    //TODO autogenerate
    public Array<Item> resources = new Array<>();

    public Cons<Rules> rules = rules -> {};
    public boolean alwaysUnlocked;
    public int conditionWave = Integer.MAX_VALUE;
    public int launchPeriod = 10;
    public Schematic loadout = Loadouts.basicShard;
    public TextureRegion preview;

    protected Array<ItemStack> baseLaunchCost = new Array<>();
    protected Array<ItemStack> startingItems = new Array<>();
    protected Array<ItemStack> launchCost;

    private Array<ItemStack> defaultStartingItems = new Array<>();

    public Zone(String name, Generator generator){
        super(name);
        this.generator = generator;
    }

    public Zone(String name){
        this(name, new MapGenerator(name));
    }

    @Override
    public void load(){
        //原版是 Core.atlas.find("zone-" + name, Core.atlas.find(name + "-zone"))（arc 的两级回退）；
        //本项目用 findRegion（缺图返回 null，见 world/blocks/LiquidBlock 的说明）→ 手写回退
        if(Core.atlas == null) return;
        preview = Core.atlas.findRegion("zone-" + name);
        if(preview == null) preview = Core.atlas.findRegion(name + "-zone");
    }

    public Rules getRules(){
        if(generator instanceof MapGenerator){
            //原版直接 return ((MapGenerator)generator).getMap().rules()（内置地图一定存在）；
            //本项目地图按名字扫目录，查不到时 getMap() 为 null → 退回手写 rules，避免 NPE
            Map map = ((MapGenerator)generator).getMap();
            if(map != null){
                return map.rules();
            }
        }
        Rules rules = new Rules();
        this.rules.get(rules);
        return rules;
    }

    public boolean isLaunchWave(int wave){
        return metCondition() && wave % launchPeriod == 0;
    }

    public boolean canUnlock(){
        //原版 Zone.java:68 data.isUnlocked(this) || !requirements.contains(r -> !r.complete())；
        //本项目 Vars.data 为 null（菜单/初始化早期）时退化为只看 requirements（等价于原版形状）
        return (data != null && data.isUnlocked(this)) || !requirements.contains(r -> !r.complete());
    }

    public Array<ItemStack> getLaunchCost(){
        if(launchCost == null){
            updateLaunchCost();
        }
        return launchCost;
    }

    public Array<ItemStack> getStartingItems(){
        return startingItems;
    }

    public void resetStartingItems(){
        startingItems.clear();
        defaultStartingItems.each(stack -> startingItems.add(new ItemStack(stack.item, stack.amount)));
    }

    public boolean hasLaunched(){
        return Core.settings.getBool(name + "-launched", false);
    }

    public void setLaunched(){
        updateObjectives(() -> {
            Core.settings.put(name + "-launched", true);
            data.modified();
        });
    }

    public void updateWave(int wave){
        int value = Core.settings.getInt(name + "-wave", 0);

        if(value < wave){
            updateObjectives(() -> {
                Core.settings.put(name + "-wave", wave);
                data.modified();
            });
        }
    }

    public void updateObjectives(Runnable closure){
        //原版 Zone.java:113-127：先扫描"与本区域相关、尚未完成"的 ZoneObjective，
        //执行 closure（把 launched/wave 等状态写进去），再把刚达成的目标逐个 fire 事件出去。
        //F-LOW 批：ZoneRequireCompleteEvent/ZoneConfigureCompleteEvent 已补进 EventType → 按原版接线。
        //arc 的 map/flatten/select 链没有对应物 → 手写双层循环（加载/结算期调用，非热路径）。
        //⚠ ContentLoader.getBy 返回的是 libgdx 的 Array（本项目 struct.Array 是另一套）→ 用全限定名接
        com.badlogic.gdx.utils.Array<Zone> zones = content.getBy(ContentType.zone);
        Array<Objectives.ZoneObjective> incomplete = new Array<>();
        for(int i = 0; i < zones.size; i++){
            Zone z = zones.get(i);
            for(int j = 0; j < z.requirements.size; j++){
                Objective o = z.requirements.get(j);
                if(o instanceof Objectives.ZoneObjective && ((Objectives.ZoneObjective)o).zone == this && !o.complete()){
                    incomplete.add((Objectives.ZoneObjective)o);
                }
            }
        }

        boolean wasConfig = configureObjective.complete();

        closure.run();

        for(int i = 0; i < incomplete.size; i++){
            Objectives.ZoneObjective objective = incomplete.get(i);
            if(objective.complete()){
                //原版 content.zones().find(z -> z.requirements.contains(objective))：找出"哪个区域的需求包含该目标"
                Zone zoneForMet = null;
                for(int j = 0; j < zones.size; j++){
                    if(zones.get(j).requirements.contains(objective::equals)){
                        zoneForMet = zones.get(j);
                        break;
                    }
                }
                Events.fire(new EventType.ZoneRequireCompleteEvent(objective.zone, zoneForMet, objective));
            }
        }

        if(!wasConfig && configureObjective.complete()){
            Events.fire(new EventType.ZoneConfigureCompleteEvent(this));
        }
    }

    public int bestWave(){
        return Core.settings.getInt(name + "-wave", 0);
    }

    /** @return whether initial conditions to launch are met. */
    public boolean isLaunchMet(){
        return bestWave() >= conditionWave;
    }

    public void updateLaunchCost(){
        Array<ItemStack> stacks = new Array<>();

        Cons<ItemStack> adder = stack -> {
            for(int i = 0; i < stacks.size; i++){
                ItemStack other = stacks.get(i);
                if(other.item == stack.item){
                    //刻意降级：原版是 other.amount += stack.amount；本项目 ItemStack.amount 是 final → 换成新对象（等价）
                    stacks.set(i, new ItemStack(other.item, other.amount + stack.amount));
                    return;
                }
            }
            stacks.add(new ItemStack(stack.item, stack.amount));
        };

        for(ItemStack stack : baseLaunchCost) adder.get(stack);
        for(ItemStack stack : startingItems) adder.get(stack);

        for(int i = 0; i < stacks.size; i++){
            ItemStack stack = stacks.get(i);
            //刻意降级：原版是 stack.amount = 0；本项目 amount 是 final → 换成新对象（等价）
            if(stack.amount < 0) stacks.set(i, new ItemStack(stack.item, 0));
        }

        //刻意降级：原版是 stacks.sort()（原版 ItemStack 实现了 Comparable）；本项目 ItemStack 未实现 Comparable
        //     → 按 item.id 排序，语义等价
        stacks.sortComparing(stack -> (int)stack.item.id);
        launchCost = stacks;
        //原版 Zone.java:160 Core.settings.putObject(name + "-starting-items", startingItems);
        //⚠ 刻意偏离：本项目 ItemStack 的 item/amount 是 final 且无无参构造器 → libgdx Json 无法往返；
        //  改用项目既有的"名字:数量,名字:数量"编码（同 GlobalData.unlocks 的处置；设置文件是自有格式，无兼容问题）
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < startingItems.size; i++){
            ItemStack stack = startingItems.get(i);
            if(i > 0) sb.append(',');
            sb.append(stack.item.name).append(':').append(stack.amount);
        }
        Core.settings.put(name + "-starting-items", sb.toString());
        //⚠ 原版此处只写内存（arc Settings 会自行落盘）；本项目 Settings 是 libgdx Preferences → 必须显式 flush
        Core.settings.save();
        data.modified();
    }

    /** Whether this zone has met its condition; if true, the player can leave. */
    public boolean metCondition(){
        //players can't leave in attack mode.
        return state.wave >= conditionWave && !state.rules.attackMode;
    }

    public boolean canConfigure(){
        return configureObjective.complete();
    }

    @Override
    public void init(){
        //TODO 原版：if(generator instanceof MapGenerator && minfo.mod != null){
        //      ((MapGenerator)generator).removePrefix(minfo.mod.name);
        //  }  —— minfo 没有 mod（见 ctype.Content.ModContentInfo）

        generator.init(loadout);
        resources.sort();

        for(ItemStack stack : startingItems){
            defaultStartingItems.add(new ItemStack(stack.item, stack.amount));
        }

        //原版 Zone.java:187-191（顺序一致：先填 defaultStartingItems，再读设置覆盖 startingItems）：
        //  Array<ItemStack> arr = Core.settings.getObject(name + "-starting-items", Array.class, () -> null);
        //  if(arr != null){ startingItems = arr; }
        //⚠ 刻意偏离：同 updateLaunchCost —— ItemStack 字段 final 且无无参构造器，libgdx Json 无法往返
        //  → 改用与写盘对应的"名字:数量"编码解码
        String saved = Core.settings.getString(name + "-starting-items", null);
        if(saved != null && !saved.isEmpty()){
            Array<ItemStack> arr = new Array<>();
            for(String part : saved.split(",")){
                int idx = part.lastIndexOf(':');
                if(idx <= 0) continue;
                Item item = content.getByName(ContentType.item, part.substring(0, idx));
                if(item == null) continue;   //内容表里没有这个名字（老设置/改名）→ 跳过，不要崩
                try{
                    arr.add(new ItemStack(item, Integer.parseInt(part.substring(idx + 1))));
                }catch(NumberFormatException ignored){}
            }
            startingItems = arr;
        }
    }

    @Override
    public boolean alwaysUnlocked(){
        return alwaysUnlocked;
    }

    @Override
    public boolean isHidden(){
        return true;
    }

    //neither of these are implemented, as zones are not displayed in a normal fashion... yet
    @Override
    public void displayInfo(Table table){
    }

    @Override
    public com.phoenix.game.ctype.ContentType getContentType(){
        return com.phoenix.game.ctype.ContentType.zone;
    }
}
