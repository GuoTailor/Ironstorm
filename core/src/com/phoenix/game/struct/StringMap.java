package com.phoenix.game.struct;

import com.badlogic.gdx.utils.ObjectMap;

/**
 * 字符串到字符串的映射。参照 {@code arc.struct.StringMap} 移植。
 * <p>原版用它承载 mod.json / 存档 meta / 设置项这类纯文本键值对，并提供带类型的读取便捷方法。
 * libgdx 的 {@code ObjectMap<String,String>} 已有 put/get/keys/values/entries，这里只补类型化读取与静态工厂。
 */
public class StringMap extends ObjectMap<String, String>{

    public StringMap(){
        super();
    }

    public StringMap(int initialCapacity){
        super(initialCapacity);
    }

    public StringMap(StringMap other){
        super(other);
    }

    /** 由 {@code key, value, key, value…} 交替的序列构造（对应 arc {@code StringMap.of}）。 */
    public static StringMap of(Object... values){
        StringMap map = new StringMap();
        for(int i = 0; i < values.length / 2; i++){
            map.put(String.valueOf(values[i * 2]), String.valueOf(values[i * 2 + 1]));
        }
        return map;
    }

    public boolean getBool(String key){
        return Boolean.parseBoolean(get(key));
    }

    public int getInt(String key){
        return Integer.parseInt(get(key));
    }

    public float getFloat(String key){
        return Float.parseFloat(get(key));
    }

    public long getLong(String key){
        return Long.parseLong(get(key));
    }

    public int getInt(String key, int defaultValue){
        String value = get(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    public float getFloat(String key, float defaultValue){
        String value = get(key);
        return value == null ? defaultValue : Float.parseFloat(value);
    }

    public long getLong(String key, long defaultValue){
        String value = get(key);
        return value == null ? defaultValue : Long.parseLong(value);
    }
}
