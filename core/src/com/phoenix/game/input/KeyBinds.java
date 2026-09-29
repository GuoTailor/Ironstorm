package com.phoenix.game.input;

/**
 * 键位绑定。参照 {@code arc.KeyBinds} 最小移植（只补 {@link Binding} 用到的嵌套类型）。
 * <p><b>与 arc 的差异</b>：
 * <ul>
 *   <li>{@code arc.input.KeyCode} 是枚举、本项目是 int 常量，所以 {@link Axis} 里的键位也用 int。</li>
 *   <li>arc 的 {@code KeyBinds} 本体负责"按键表 + 存档 + 按 Section/DeviceType 查询"，
 *       本项目还没做键位设置界面与键位持久化，故只保留类型定义，
 *       查询/保存逻辑见 {@code TODO 原版依赖 arc.KeyBinds 的 setDefaults/save/load，尚未移植}。</li>
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
}
