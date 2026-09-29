package com.phoenix.game.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.graphics.Colors;

/**
 * 支持 arc 颜色标记的 Label，对应 arc.scene.ui.Label 的 markup 行为。
 * <p>libgdx 的 Label 不认 {@code [accent]文本[]} 这类标记，会把标记本身当字面量画出来
 * （bundle 里大量文案带标记，如 {@code info.title = [accent]详情}、需求行 {@code [white]200/12}）。
 * <p>做法：{@link #setText} 时解析标记，只把剥掉标记的纯文本交给父类；父类完成换行/对齐后，
 * 再按字符区间把颜色落到对应的字形上。标记本身不产生字形，所以字符索引要跳过换行符再映射。
 * <p>⚠ 上色必须放在 {@link #draw} 里、且**在 {@code cache.tint()} 之后**：libgdx 的
 * {@code Label.draw} 会调 {@code cache.tint(color)}，而 {@code BitmapFontCache.tint()} 在 tint 变化时
 * 会**从 {@code GlyphLayout.colors} 重建全部顶点色**，把 {@code cache.setColors(...)} 写进去的
 * 逐字形颜色整体覆盖掉（表现为所有标记文案永远是白色）。arc 靠把颜色写进
 * {@code GlyphLayout.colors} 来规避，本项目没改 GlyphLayout，改为覆写 draw 在 tint 之后再上色。
 */
public class Label extends com.badlogic.gdx.scenes.scene2d.ui.Label{
    /** 一段同色文本：纯文本里的 [start, end) 区间；color 为 null 表示用样式默认色。 */
    private static class Run{
        final int start, end;
        final Color color;

        Run(int start, int end, Color color){
            this.start = start;
            this.end = end;
            this.color = color;
        }
    }

    private final Array<Run> runs = new Array<>();
    /** 剥掉颜色标记后的纯文本（父类持有的就是它）。 */
    private String plain = "";
    /** 上一次设置的原始文本（含标记），用于跳过内容未变的重复解析。 */
    private String rawText = "";
    /** 文本里是否出现过颜色标记；没有就完全走父类默认着色。 */
    private boolean markup;
    /** 字符索引 → 字形索引的映射，复用避免每次布局都分配。 */
    private int[] glyphOffsets = new int[0];
    /** draw 期间的临时色，避免每帧分配。 */
    private final Color drawColor = new Color();
    private final Color runColor = new Color();

    public Label(CharSequence text, LabelStyle style){
        //父类构造器会回调被覆写的 setText，那时字段还没初始化，所以先传空串、构造完再设
        super("", style);
        setText(text);
    }

    @Override
    public void setText(CharSequence newText){
        String text = newText == null ? "" : newText.toString();
        //HUD 每帧都会 setText（内容多半没变），内容相同就不重复解析与重排
        if(text.equals(rawText)) return;
        rawText = text;

        parse(text);
        super.setText(plain);
    }

    @Override
    public void draw(Batch batch, float parentAlpha){
        //不能直接调 super.draw()：它会在 cache.draw() 之前调 cache.tint(color)，
        //而 tint() 会从 GlyphLayout.colors 重建顶点色、覆盖掉我们的标记颜色。
        //这里复制 Label.draw 的逻辑（validate → 背景 → fontColor → tint），把上色挪到 tint 之后。
        validate();

        Color color = drawColor.set(getColor());
        color.a *= parentAlpha;

        LabelStyle style = getStyle();
        if(style.background != null){
            batch.setColor(color.r, color.g, color.b, color.a);
            style.background.draw(batch, getX(), getY(), getWidth(), getHeight());
        }
        if(style.fontColor != null) color.mul(style.fontColor);

        BitmapFontCache cache = getBitmapFontCache();
        if(cache == null) return;

        cache.tint(color);
        if(markup) applyColors(color);
        cache.setPosition(getX(), getY());
        cache.draw(batch);
    }

    /**
     * 解析颜色标记：{@code [name]} / {@code [#rrggbb]} 设色，{@code []} 复位为样式默认色；
     * 认不出的标记（未注册的名字、没有右括号）原样当普通文本。
     */
    private void parse(String text){
        runs.clear();
        markup = false;

        StringBuilder out = new StringBuilder(text.length());
        Color current = null;
        int runStart = 0;

        for(int i = 0; i < text.length();){
            if(text.charAt(i) == '['){
                int close = text.indexOf(']', i + 1);
                if(close > i){
                    String name = text.substring(i + 1, close);
                    Color color = name.isEmpty() ? null : Colors.get(name);
                    if(color == null && !name.isEmpty() && name.charAt(0) == '#'){
                        color = hex(name.substring(1));
                    }

                    if(name.isEmpty() || color != null){
                        if(out.length() > runStart){
                            runs.add(new Run(runStart, out.length(), current));
                        }
                        current = color;
                        runStart = out.length();
                        markup = true;
                        i = close + 1;
                        continue;
                    }
                }
            }

            out.append(text.charAt(i));
            i++;
        }

        if(out.length() > runStart){
            runs.add(new Run(runStart, out.length(), current));
        }

        plain = out.toString();
    }

    /**
     * 把颜色区间落到字形上：换行符不产生字形，字符索引要先映射成字形索引。
     * <p>{@code base} 是本次 draw 的有效底色（已含 getColor × parentAlpha × fontColor）——
     * 与 arc 一致，标记颜色要**乘上**它（这样整体变灰/淡入淡出时标记文案也一起变），
     * 所以这里用 {@code runColor.set(run).mul(base)}，未标记段直接用 base。
     */
    private void applyColors(Color base){
        BitmapFontCache cache = getBitmapFontCache();
        if(cache == null) return;

        int length = plain.length();
        if(glyphOffsets.length < length + 1){
            glyphOffsets = new int[length + 1];
        }
        int glyph = 0;
        for(int i = 0; i < length; i++){
            glyphOffsets[i] = glyph;
            char c = plain.charAt(i);
            if(c != '\n' && c != '\r') glyph++;
        }
        glyphOffsets[length] = glyph;

        for(int i = 0; i < runs.size; i++){
            Run run = runs.get(i);
            int start = glyphOffsets[run.start];
            //省略号（setEllipsis）会把尾部换成"..."，字形数可能少于字符数，越界就截断
            int end = Math.min(glyphOffsets[run.end], glyph);
            if(end > start){
                if(run.color != null){
                    cache.setColors(runColor.set(run.color).mul(base).toFloatBits(), start, end);
                }else{
                    cache.setColors(base.toFloatBits(), start, end);
                }
            }
        }
    }

    /** @return {@code #rrggbb} / {@code #rrggbbaa} 形式的颜色，格式非法返回 null。 */
    private static Color hex(String value){
        try{
            return Color.valueOf(value);
        }catch(Exception e){
            return null;
        }
    }
}
