package com.phoenix.game.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * 最小实现：持久化配置。对应 arc 的 {@code arc.util.Settings}（原版 {@code Core.settings}）。
 * <p>读写 + 枚举键 / 清空 / 批量写入 / 默认值体系（{@code defaults/getDefault/getXxx(name)}），
 * 设置菜单（{@code ui/SettingsTable}）用的就是这一套。
 * 后端直接用 libgdx 的 {@link Preferences}，落盘位置由各平台决定
 * （桌面是 <code>~/.prefs/</code>，Android 是应用私有目录的 shared_prefs）。
 * <p>注意 {@link com.phoenix.game.game.GlobalData} 用的是另一个 Preferences 名，两者互不干扰。
 */
public class Settings{
    private static final String PREF_NAME = "phoenix_settings";

    private Preferences prefs;

    /** 各设置项的默认值（对应 arc 的 {@code Settings.defaults}）；由 {@link #defaults(Object...)} 成对登记。 */
    private final ObjectMap<String, Object> defaults = new ObjectMap<>();

    private Preferences prefs(){
        if(prefs == null){
            prefs = Gdx.app.getPreferences(PREF_NAME);
        }
        return prefs;
    }

    // ---- 读 ----

    /** @return 该键的布尔值；没写过就取 {@link #defaults(Object...)} 登记的默认值，没有默认值则为 false。 */
    public boolean getBool(String key){
        Object def = defaults.get(key);
        return getBool(key, def instanceof Boolean && (Boolean)def);
    }

    public boolean getBool(String key, boolean def){
        return prefs().getBoolean(key, def);
    }

    public int getInt(String key, int def){
        return prefs().getInteger(key, def);
    }

    public float getFloat(String key, float def){
        return prefs().getFloat(key, def);
    }

    public String getString(String key, String def){
        return prefs().getString(key, def);
    }

    /**
     * 读一个被序列化的对象（对应 arc 的 {@code Settings.getObject(String, Class, Prov)}，
     * 原版 {@code Arc/arc-core/src/arc/Settings.java:286}）。
     * <p>⚠ <b>刻意偏离</b>：arc 用的是「{@code TypeSerializer} 注册表 + UBJson 二进制」
     * （未注册的类型回退 {@code json.toUBJson}，值以字节存进设置），本项目**不移植 UBJson/TypeSerializer**，
     * 改用**已有的** {@link com.phoenix.game.io.JsonIO} 做 JSON 字符串序列化
     * （设置文件是本项目自有的 {@code phoenix_settings}，不存在与原版的格式兼容问题）。
     * @param def 键不存在（或读到 null）时返回它的值（对应 arc 的 {@code Prov} 供应商）
     */
    public <T> T getObject(String key, Class<T> type, java.util.function.Supplier<T> def){
        String str = prefs().getString(key, null);
        if(str == null) return def == null ? null : def.get();
        //⚠ 对应 arc 的 `catch(Exception e){ return def.get(); }`：设置文件里存了损坏/过时的 JSON 时
        //  （例如内容改名后反序列化失败）不能让菜单 UI 在构造期崩掉 → 任何异常都退回默认值。
        try{
            return com.phoenix.game.io.JsonIO.read(type, str);
        }catch(Throwable t){
            System.err.println("读取设置对象失败 " + key + ": " + t);
            return def == null ? null : def.get();
        }
    }

    public boolean has(String key){
        return prefs().contains(key);
    }

    // ---- 写（写完要调一次 save() 才落盘）----

    public void put(String key, boolean value){
        prefs().putBoolean(key, value);
    }

    public void put(String key, int value){
        prefs().putInteger(key, value);
    }

    public void put(String key, float value){
        prefs().putFloat(key, value);
    }

    public void put(String key, String value){
        prefs().putString(key, value);
    }

    /**
     * 写一个被序列化的对象（对应 arc 的 {@code Settings.putObject(String, Object)}，
     * 原版 {@code Arc/arc-core/src/arc/Settings.java:264}）。
     * <p>⚠ 同上（见 {@link #getObject}）：用 {@link com.phoenix.game.io.JsonIO} 而非 arc 的 UBJson 二进制。
     */
    public void putObject(String key, Object object){
        put(key, com.phoenix.game.io.JsonIO.write(object));
    }

    public void remove(String key){
        prefs().remove(key);
    }

    /** 把内存里的改动刷到磁盘（对应 arc 的 Settings.save）。 */
    public void save(){
        prefs().flush();
    }

    // ---- arc Settings 的其余便捷方法（原版 UI/对话框会用到）----

    /** @return 全部已存在的键（对应 arc 的 Settings.keys）。 */
    public Iterable<String> keys(){
        return prefs().get().keySet();
    }

    /** 清空全部设置并落盘（对应 arc 的 Settings.clear）。 */
    public void clear(){
        for(String key : keys()){
            prefs().remove(key);
        }
        save();
    }

    /** 把另一个 Settings 的全部键值拷进来（对应 arc 的 Settings.putAll）。 */
    public void putAll(Settings other){
        for(java.util.Map.Entry<String, ?> entry : other.prefs().get().entrySet()){
            Object value = entry.getValue();
            if(value instanceof Boolean){
                put(entry.getKey(), (Boolean)value);
            }else if(value instanceof Integer){
                put(entry.getKey(), (Integer)value);
            }else if(value instanceof Float){
                put(entry.getKey(), (Float)value);
            }else if(value instanceof Long){
                put(entry.getKey(), ((Long)value).intValue());
            }else if(value != null){
                put(entry.getKey(), value.toString());
            }
        }
    }

    /**
     * @return 设置所在目录（对应 arc 的 Settings.getDataDirectory）。
     * <p><b>偏离</b>：arc 自己管理设置文件；本项目用 libgdx {@link Preferences}，
     * 落盘位置由平台决定（桌面是 <code>~/.prefs/</code>，Android 是应用私有目录），
     * 这里只能返回应用的 local 根目录（设置菜单"清除数据"用它删数据文件；"打开目录"仍未接 {@code Core.app.openFolder}）。
     * <p><b>⚠⚠ 安全警告</b>：桌面运行期该目录 = <code>&lt;repo&gt;/assets</code>，里面混放着资源
     * （sprites/i18n/fonts/locales…）与玩家数据（saves/schematics/crashes）。
     * <b>绝对不要照原版那样遍历本目录整体删除</b>（原版的 getDataDirectory 是纯数据目录，本项目不是）——
     * 要清数据只能按白名单删子目录（见 {@code SettingsMenuDialog.buildDataDialog()}）。
     */
    public com.phoenix.game.util.Fi getDataDirectory(){
        return new com.phoenix.game.util.Fi(Gdx.files.local("").file());
    }

    // ---- 默认值与"单参 getter"（对应 arc Settings 的 defaults/getDefault/getXxx(name)）----

    /**
     * 登记一批默认值，格式：{@code name1, default1, name2, default2, ...}（与 arc 一致，成对传入）。
     * <p>默认值只影响"从未写过该键"时的取值，不会写盘（对应 arc 的 Settings.defaults）。
     */
    public void defaults(Object... objects){
        for(int i = 0; i < objects.length; i += 2){
            defaults.put((String)objects[i], objects[i + 1]);
        }
    }

    /** @return 该键登记的默认值；没登记过返回 null（对应 arc 的 {@code Settings.getDefault}）。 */
    public Object getDefault(String name){
        return defaults.get(name);
    }

    /** @return 该键的整数值；没写过就取登记的默认值，没有默认值则为 0。 */
    public int getInt(String name){
        Object def = defaults.get(name);
        return getInt(name, def instanceof Number ? ((Number)def).intValue() : 0);
    }

    /** @return 该键的字符串值；没写过就取登记的默认值，没有默认值则为 ""。 */
    public String getString(String name){
        Object def = defaults.get(name);
        return getString(name, def instanceof String ? (String)def : "");
    }

    /** @return 该键的浮点值；没写过就取登记的默认值，没有默认值则为 0f。 */
    public float getFloat(String name){
        Object def = defaults.get(name);
        return getFloat(name, def instanceof Number ? ((Number)def).floatValue() : 0f);
    }

    /** 按运行时类型分派写入（对应 arc 的 {@code Settings.put(String, Object)}，主要给"恢复默认值"用）。 */
    public void put(String key, Object value){
        if(value instanceof Boolean){
            put(key, (Boolean)value);
        }else if(value instanceof Integer){
            put(key, (Integer)value);
        }else if(value instanceof Long){
            put(key, ((Long)value).intValue());
        }else if(value instanceof Float){
            put(key, (Float)value);
        }else if(value instanceof Number){
            put(key, ((Number)value).floatValue());
        }else if(value != null){
            put(key, value.toString());
        }
    }

    /** 把一张键值表整体写进来（对应 arc 的 {@code Settings.putAll(ObjectMap)}）。 */
    public void putAll(ObjectMap<String, Object> map){
        for(ObjectMap.Entry<String, Object> entry : map.entries()){
            put(entry.key, entry.value);
        }
    }
}
