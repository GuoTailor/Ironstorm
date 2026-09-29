package com.phoenix.game.core;

import java.util.Locale;

/**
 * 字符串工具。参照 Mindustry 的 {@code arc.util.Strings} 移植（只取本项目用到的部分）。
 * <p>arc 的 Strings 在 libgdx 里没有对应物，内容信息对话框的数值/单位格式化依赖它。
 */
public class Strings{

    /** @return 固定保留 precision 位小数（用 ROOT locale，避免某些语言把小数点写成逗号）。 */
    public static String fixed(double value, int precision){
        return String.format(Locale.ROOT, "%." + precision + "f", value);
    }

    /** @return 整数不带小数点，非整数保留 precision 位（对应原版 Strings.autoFixed）。 */
    public static String autoFixed(double value, int precision){
        if(value == (long)value) return Long.toString((long)value);
        return fixed(value, precision);
    }

    /** 把文本里的 {@code {0}}、{@code {1}}… 依次替换成 args（对应原版 Strings.format）。 */
    public static String format(String text, Object... args){
        if(text == null) return "";
        for(int i = 0; i < args.length; i++){
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return text;
    }

    /**
     * 去掉文本里的颜色标记（对应 arc {@code Strings.stripColors}，正则逐字照搬）。
     * <p>颜色标记形如 {@code [red]} / {@code [#rrggbb]} / {@code []}；玩家名可自带颜色标记
     * （{@code HostDialog}/{@code JoinDialog} 直接赋给 {@code Player.name}，不做过滤），
     * 所以按名字搜索时要去色后再比对。
     * <p>消费点：{@code ui/fragments/PlayerListFragment.rebuild()} 的搜索过滤。
     */
    public static String stripColors(String text){
        return text.replaceAll("/*\\[[^]]*]", "");
    }
}
