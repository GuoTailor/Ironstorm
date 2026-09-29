package com.phoenix.game.ui;

import com.phoenix.game.core.Strings;
import com.phoenix.game.func.Func;
import com.phoenix.game.world.meta.I18n;

/**
 * A low-garbage way to format bundle strings.
 * <p>参照 Mindustry mindustry.ui.IntFormat 移植：同一个 IntFormat 实例反复调用 {@link #get(int)}
 * 只在数值变化时重建字符串，避免 HUD 每帧分配。
 * <p>原版走 {@code Core.bundle.format(text, ...)}；本项目 bundle 是从原版裁剪的（缺键返回
 * {@code ???key???} 而不是键本身，fallback 不生效），所以先按 {@link I18n} 的双形态判定取文本，
 * 再做 {@code {0}} 替换。
 */
public class IntFormat{
    private final StringBuilder builder = new StringBuilder();
    private final String text;
    private int lastValue = Integer.MIN_VALUE;
    private Func<Integer, String> converter = String::valueOf;

    public IntFormat(String text){
        this.text = text;
    }

    public IntFormat(String text, Func<Integer, String> converter){
        this.text = text;
        this.converter = converter;
    }

    public CharSequence get(int value){
        if(lastValue != value){
            builder.setLength(0);
            builder.append(Strings.format(I18n.get(text, text), converter.get(value)));
        }
        lastValue = value;
        return builder;
    }
}
