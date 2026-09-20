package com.phoenix.game.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.phoenix.game.content.Items;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.ItemStack;

/**
 * 全局库存（战役跨区运输）。参照 Mindustry mindustry.game.GlobalData 移植。
 * <p>原版把物品数写进 settings 并附带一整套内容解锁系统；本项目只保留"物品全局库存"部分，
 * 用 libgdx 的 {@link Preferences} 持久化到本地。
 * <p>只在客户端存在（原版注释：Clientside only）——{@code LaunchPad} 发射的物品会进这里，
 * 供后续对局取用。
 */
public class GlobalData{
    private static final String PREF_NAME = "phoenix_global";

    /** 各物品的全局库存（按 item.id 索引）。 */
    private final int[] items = new int[Items.all.size];
    private boolean modified;

    public int getItem(Item item){
        return item == null ? 0 : items[item.id];
    }

    public void addItem(Item item, int amount){
        if(item == null || amount <= 0) return;

        modified = true;
        items[item.id] = (int)Math.min((long)items[item.id] + amount, Integer.MAX_VALUE);
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

    /** 从本地读取；首次运行给一点启动资金（对应原版默认给 50 铜）。 */
    public void load(){
        if(Gdx.app == null) return;

        Preferences prefs = Gdx.app.getPreferences(PREF_NAME);
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
