package com.phoenix.game.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Items;
import com.phoenix.game.core.Events;
import com.phoenix.game.ctype.ContentType;
import com.phoenix.game.ctype.UnlockableContent;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.ItemStack;

/**
 * 全局库存（战役跨区运输）+ 内容解锁系统。参照 Mindustry mindustry.game.GlobalData 移植。
 * <p>原版把物品数与解锁表都写进 settings；本项目用 libgdx 的 {@link Preferences} 持久化到本地
 * （物品一个键一个物品，解锁表按 {@link ContentType} 分键）。
 * <p>已含解锁系统：{@link #isUnlocked}/{@link #unlockContent} 供 {@link UnlockableContent#unlocked()} 查询，
 * 解锁来源为 {@code addItem}（获得物品即解锁）、科技树研究、区域解锁、区域内摧毁单位（见 {@code Control}）。
 * <p>只在客户端存在（原版注释：Clientside only）——{@code LaunchPad} 发射的物品会进这里，
 * 供后续对局取用。
 */
public class GlobalData{
    private static final String PREF_NAME = "phoenix_global";

    /** 各物品的全局库存（按 item.id 索引）。 */
    private final int[] items = new int[Items.all.size];
    /** 各内容类型下已解锁内容的名字集合（对应原版 {@code GlobalData.unlocked}）。 */
    private ObjectMap<ContentType, ObjectSet<String>> unlocked = new ObjectMap<>();
    private boolean modified;

    /** 标记数据已改动（对应原版 {@code GlobalData.modified()}）：下次 {@link #checkSave()} 时落盘。 */
    public void modified(){
        modified = true;
    }

    public int getItem(Item item){
        return item == null ? 0 : items[item.id];
    }

    public void addItem(Item item, int amount){
        if(item == null || amount <= 0) return;

        //原版 GlobalData.java:85-87：获得物品（amount > 0）即解锁该物品
        unlockContent(item);

        modified = true;
        items[item.id] = (int)Math.min((long)items[item.id] + amount, Integer.MAX_VALUE);

        //原版 GlobalData.java:92-96：同时累计"已送达物品"统计（结算框 / 区域评级读它），并做溢出钳制。
        //⚠ 本项目 items 是 int[]（原版是 ObjectIntMap），溢出已在上面用 long 钳死；itemsDelivered 是
        //   ObjectIntMap<Item> → 照原版 getAndIncrement + 负数钳制。
        //⚠ 本项目 Vars.state / state.stats 可能为 null（菜单态/初始化早期也会走 addItem，如 load() 的启动资金）
        //   → 加 null 守卫（原版这两个对象恒非空，不需要）。
        if(Vars.state != null && Vars.state.stats != null){
            Vars.state.stats.itemsDelivered.getAndIncrement(item, 0, amount);
            if(Vars.state.stats.itemsDelivered.get(item, 0) < 0){
                Vars.state.stats.itemsDelivered.put(item, Integer.MAX_VALUE);
            }
        }
    }

    public boolean has(Item item, int amount){
        return getItem(item) >= amount;
    }

    public boolean hasItems(ItemStack[] stacks){
        for(ItemStack stack : stacks){
            if(!has(stack.item, stack.amount)) return false;
        }
        return true;
    }

    public void removeItems(ItemStack[] stacks){
        for(ItemStack stack : stacks){
            if(stack.item == null) continue;
            items[stack.item.id] = Math.max(0, items[stack.item.id] - stack.amount);
        }
        modified = true;
    }

    /** @return 全局库存总量（所有物品求和）。 */
    public int total(){
        int sum = 0;
        for(int value : items){
            sum += value;
        }
        return sum;
    }

    /**
     * @return 该内容是否已解锁（对应原版 {@code GlobalData.isUnlocked}，原版 :136-138）。
     * <p>原版用 {@code unlocked.getOr(type, ObjectSet::new)}；libgdx 的 {@code ObjectMap} 没有该重载
     * → 取不到时按"空集合"处理（等价）。
     */
    public boolean isUnlocked(UnlockableContent content){
        ObjectSet<String> set = unlocked.get(content.getContentType());
        return content.alwaysUnlocked() || (set != null && set.contains(content.name));
    }

    /**
     * 把该内容标记为已解锁（对应原版 {@code GlobalData.unlockContent}，原版 :140-154）。
     * <p>结果要到 {@link #save()}（或 {@link #checkSave()}）才落盘。
     */
    public void unlockContent(UnlockableContent content){
        if(content.alwaysUnlocked()) return;

        ObjectSet<String> set = unlocked.get(content.getContentType());
        if(set == null){
            set = new ObjectSet<>();
            unlocked.put(content.getContentType(), set);
        }

        //原版 :149-153：首次解锁才置脏、回调、派发事件
        if(set.add(content.name)){
            modified = true;
            content.onUnlock();
            Events.fire(new EventType.UnlockEvent(content));
        }
    }

    /** 清空所有已解锁内容并落盘（对应原版 {@code GlobalData.reset}，原版 :157-159 实现就只是 save()）。 */
    public void reset(){
        save();
    }

    /** 从本地读取；首次运行给一点启动资金（对应原版默认给 50 铜）。 */
    public void load(){
        if(Gdx.app == null) return;

        Preferences prefs = Gdx.app.getPreferences(PREF_NAME);

        //原版 load() :171 unlocked = Core.settings.getObject("unlocks", ObjectMap.class, ObjectMap::new)
        //本项目没有 Settings.getObject → 按 ContentType 分键读回（键 "unlocks-<type>"，值 = 名字逗号连接）
        unlocked.clear();
        for(ContentType type : ContentType.all){
            String raw = prefs.getString("unlocks-" + type.name(), "");
            if(raw.isEmpty()) continue;

            ObjectSet<String> set = new ObjectSet<>();
            for(String name : raw.split(",")){
                if(!name.isEmpty()) set.add(name);
            }
            unlocked.put(type, set);
        }

        for(int i = 0; i < Items.all.size; i++){
            items[i] = Math.max(0, prefs.getInteger("item-" + Items.all.get(i).name, 0));
        }

        if(!prefs.contains("item-" + Items.copper.name)){
            addItem(Items.copper, 50);
            save();
        }

        modified = false;
    }

    public void save(){
        if(Gdx.app == null) return;

        Preferences prefs = Gdx.app.getPreferences(PREF_NAME);
        for(int i = 0; i < Items.all.size; i++){
            prefs.putInteger("item-" + Items.all.get(i).name, items[i]);
        }

        //原版 save() :183 Core.settings.putObject("unlocks", unlocked)
        //本项目 → Preferences：每个 ContentType 一个键，值为该类型下所有已解锁名字用 ',' 连接
        //（内容名里不含逗号，安全；顺序按 ObjectSet 迭代顺序）
        for(ContentType type : ContentType.all){
            ObjectSet<String> set = unlocked.get(type);
            StringBuilder sb = new StringBuilder();
            if(set != null){
                for(String name : set){
                    if(sb.length() > 0) sb.append(',');
                    sb.append(name);
                }
            }
            prefs.putString("unlocks-" + type.name(), sb.toString());
        }
        prefs.flush();
    }

    /** 有改动才落盘（对应原版 {@code checkSave}），由主循环定期调用。 */
    public void checkSave(){
        if(modified){
            save();
            modified = false;
        }
    }
}
