package com.phoenix.game.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.ObjectMap;
import com.phoenix.game.core.Core;
import com.phoenix.game.input.KeyBinds.*;
import com.phoenix.game.input.InputDevice.DeviceType;

/**
 * 键位绑定。参照 {@code arc.KeyBinds} 移植。
 * <p><b>与 arc 的差异</b>：
 * <ul>
 *   <li>{@code arc.input.KeyCode} 是枚举、本项目是 int 常量，所以 {@link Axis} 里的键位也用 int。</li>
 *   <li>arc 的 {@code KeyBinds} 是实例（{@code Core.keybinds}），支持多 Section（多人分坐）与手柄
 *       DeviceType；本项目只有单玩家键盘 → 改成<b>静态工具类</b>，Section/DeviceType 固定为
 *       {@code default/keyboard}（只影响 settings 键名，格式与原版保持一致以便日后扩展）。</li>
 *   <li>arc 的 {@code setDefaults(defs, sections...)} 建默认缓存；本项目默认表就是
 *       {@link Binding} 枚举自身的 {@code defaultValue}（枚举即全量表），无需缓存 →
 *       覆盖表 {@link #overrides} 只存"用户改过的"绑定，查询 miss 时回默认值。</li>
 *   <li>arc 的 {@code KeyCode.byOrdinal(UNSET)} 判"没存过"；int 键码没有 UNSET，
 *       用 {@link #UNSET} 哨兵（合法键码：键盘 ≥0、鼠标 -1..-6，不可能撞上）。</li>
 * </ul>
 */
public class KeyBinds{

    /** 一个可绑定的操作。 */
    public interface KeyBind{
        /** @return 操作名（枚举的 {@code name()} 即可）。 */
        String name();

        /** @return 该操作在指定设备上的默认键位。 */
        KeybindValue defaultValue(InputDevice.DeviceType type);

        /** @return 该操作所属分类（可为 null）。 */
        default String category(){
            return null;
        }
    }

    /** 键位取值：单键或一对"轴"键（对应原版 {@code KeybindValue}）。 */
    public interface KeybindValue{
    }

    /**
     * 单键绑定值。
     * <p>arc 里 {@code KeyCode} 枚举**自身**就实现了 {@code KeybindValue}，所以原版写 {@code key(KeyCode.Q)}；
     * 本项目 {@code KeyCode} 是 int 常量，不能实现接口 → 用本类包一层（{@link Binding} 里提供了
     * 接受 int 的构造器，所以原版的常量表写法不用改）。
     */
    public static class KeyValue implements KeybindValue{
        public int key;

        public KeyValue(int key){
            this.key = key;
        }

        @Override
        public String toString(){
            return String.valueOf(key);
        }
    }

    /** 单键 / 双键轴。 */
    public static class Axis implements KeybindValue{
        public int min, max, key;

        public Axis(int key){
            this.key = key;
        }

        public Axis(int min, int max){
            this.min = min;
            this.max = max;
        }

        @Override
        public String toString(){
            //本项目 KeyCode 是 int 常量，没有名字反查（原版打印 KeyCode 枚举名）
            return (min != 0 || max != 0) ? (min + " " + max) : String.valueOf(key);
        }
    }

    // ---- 绑定存取（对应 arc KeyBinds 的 setDefaults/save/load/get/resetToDefault(s)）----

    /** settings 键前缀里的 section/device 名；格式与 arc 保持一致（{@code keybind-default-keyboard-...}）。 */
    public static final String defaultSection = "default";
    private static final String deviceName = DeviceType.keyboard.name();

    /** 用户重绑过的绑定（查询 miss 时回 {@link Binding} 默认值，等价 arc 的 defaultCache fallback）。 */
    private static final ObjectMap<Binding, Axis> overrides = new ObjectMap<>();
    /** settings 里"没存过"的哨兵（合法键码键盘 ≥0、鼠标 -1..-6，不会撞上）。 */
    private static final int UNSET = -1000000;

    /** 初始化：默认表即 {@link Binding} 枚举自身，这里只负责把已保存的重绑读进来（对应原版 setDefaults+load 的调用序列）。 */
    public static void init(){
        load();
    }

    /** @return 该绑定当前生效的键位（重绑优先，未重绑回默认值）。 */
    public static Axis get(Binding binding){
        Axis override = overrides.get(binding);
        if(override != null) return override;

        KeybindValue value = binding.defaultValue(DeviceType.keyboard);
        if(value instanceof KeyValue){
            return new Axis(((KeyValue)value).key);
        }
        return (Axis)value;
    }

    /**
     * @return 单键绑定的生效键码（供 {@code isKeyJustPressed/isKeyPressed} 轮询）。
     * <p>双键轴（move_x/move_y）没有"单键"语义 → 返回 min（负方向键），调用方不应把双键轴喂给单键判定。
     */
    public static int key(Binding binding){
        Axis axis = get(binding);
        return axis.key != 0 ? axis.key : axis.min;
    }

    /** 单键重绑（含单键轴，如滚轮）。写覆盖表并落盘。 */
    public static void bind(Binding binding, int key){
        overrides.put(binding, new Axis(key));
        save();
    }

    /** 双键轴重绑（如 move_x 的 A/D）。写覆盖表并落盘。 */
    public static void bind(Binding binding, int min, int max){
        overrides.put(binding, new Axis(min, max));
        save();
    }

    /** 恢复单个绑定的默认值并落盘（对应 arc 的 resetToDefault；做法是删覆盖 + 删 settings 键）。 */
    public static void resetToDefault(Binding binding){
        overrides.remove(binding);
        String rname = rname(binding);
        Core.settings.remove(rname + "-single");
        Core.settings.remove(rname + "-key");
        Core.settings.remove(rname + "-min");
        Core.settings.remove(rname + "-max");
        Core.settings.save();
    }

    /** 恢复全部默认值并落盘（对应 arc 的 resetToDefaults）。 */
    public static void resetToDefaults(){
        overrides.clear();
        for(Binding binding : Binding.values()){
            String rname = rname(binding);
            Core.settings.remove(rname + "-single");
            Core.settings.remove(rname + "-key");
            Core.settings.remove(rname + "-min");
            Core.settings.remove(rname + "-max");
        }
        Core.settings.save();
    }

    /** 把覆盖表写进 settings（不主动落盘的版本留给内部；对外一律 save 落盘，与原版语义对齐）。 */
    public static void save(){
        for(ObjectMap.Entry<Binding, Axis> entry : overrides.entries()){
            Axis axis = entry.value;
            String rname = rname(entry.key);
            //格式照 arc：单键轴/单键存 -single/-key；双键轴存 -single=false/-min/-max
            if(axis.key != 0){
                Core.settings.put(rname + "-single", true);
                Core.settings.put(rname + "-key", axis.key);
            }else{
                Core.settings.put(rname + "-single", false);
                Core.settings.put(rname + "-min", axis.min);
                Core.settings.put(rname + "-max", axis.max);
            }
        }
        Core.settings.save();
    }

    /** 从 settings 读取已保存的重绑（覆盖表先清空，避免热重载时残留）。 */
    public static void load(){
        overrides.clear();
        for(Binding binding : Binding.values()){
            String rname = rname(binding);
            Axis loaded = load(rname);
            if(loaded != null){
                overrides.put(binding, loaded);
            }
        }
    }

    private static Axis load(String rname){
        if(Core.settings.getBool(rname + "-single", true)){
            int key = Core.settings.getInt(rname + "-key", UNSET);
            return key == UNSET ? null : new Axis(key);
        }else{
            int min = Core.settings.getInt(rname + "-min", UNSET);
            int max = Core.settings.getInt(rname + "-max", UNSET);
            return (min == UNSET || max == UNSET) ? null : new Axis(min, max);
        }
    }

    /** @return settings 键名（照 arc：{@code keybind-<section>-<device>-<bind>}）。 */
    private static String rname(Binding binding){
        return "keybind-" + defaultSection + "-" + deviceName + "-" + binding.name();
    }

    /**
     * @return 键码的人类可读名（对应 arc 的 {@code KeyCode.toString()}）。
     * <p>本项目 KeyCode 是 int：键盘键直接用 libgdx 的 {@code Keys.toString}；
     * 鼠标/滚轮是负数区间（见 {@link KeyCode} 类注释），libgdx 不认识 → 自己给名字。
     */
    public static String keyCodeName(int key){
        switch(key){
            case KeyCode.MOUSE_LEFT: return "Mouse Left";
            case KeyCode.MOUSE_RIGHT: return "Mouse Right";
            case KeyCode.MOUSE_MIDDLE: return "Mouse Middle";
            case KeyCode.MOUSE_BACK: return "Mouse Back";
            case KeyCode.MOUSE_FORWARD: return "Mouse Forward";
            case KeyCode.SCROLL: return "Scroll";
            default: return Input.Keys.toString(key);
        }
    }
}
