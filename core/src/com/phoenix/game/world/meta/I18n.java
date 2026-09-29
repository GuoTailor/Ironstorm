package com.phoenix.game.world.meta;

import com.phoenix.game.core.Core;

/**
 * meta 包内的 bundle 取值工具：键缺失时回退到给定文本。
 * <p>不能直接用 {@code Core.bundle.get(key, fallback)} —— 项目里 I18NBundle 关掉了"缺键抛异常"
 * （见 UnitType 的静态块），缺键时返回的是 {@code ???key???}，不会走 fallback。
 */
public final class I18n{
    private I18n(){
    }

    /** @return bundle 里 key 对应的文本；键缺失时返回 fallback。 */
    public static String get(String key, String fallback){
        if(Core.bundle == null) return fallback;
        String value = Core.bundle.get(key);
        return (value.equals(key) || value.equals("???" + key + "???")) ? fallback : value;
    }

    /**
     * 格式化 bundle 文本（对应原版 {@code Core.bundle.format(key, args...)}，文本里的 {@code {0}} 等占位符按参数替换）。
     * <p>键缺失时对 fallback 也做占位符替换（{@code MessageFormat} 语义与 libgdx bundle 一致）。
     */
    public static String format(String key, String fallback, Object... args){
        if(Core.bundle == null) return java.text.MessageFormat.format(fallback, args);
        String value = Core.bundle.get(key);
        if(value.equals(key) || value.equals("???" + key + "???")) return java.text.MessageFormat.format(fallback, args);
        return Core.bundle.format(key, args);
    }
}
