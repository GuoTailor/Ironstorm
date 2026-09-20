package com.phoenix.game.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Fonts;
import com.phoenix.game.math.Mathf;
import io.anuke.mindustry.gen.Tex;

import java.util.function.Supplier;

/**
 * 进度条控件。参照 Mindustry mindustry.ui.Bar 使用 libgdx Scene2D 重写。
 * <p>绘制顺序与原版一致：底色（bar）→ 按比例的填充（bar-top）→ 居中标签。
 * 名称与颜色可用 Supplier 动态刷新（如“挖掘速度：0.27/秒”）。
 */
public class Bar extends Actor{
    /** 复用的文本布局，避免每帧分配。 */
    private static final GlyphLayout LAYOUT = new GlyphLayout();

    private Supplier<String> nameProvider;
    private Supplier<Color> colorProvider;
    private Supplier<Float> fractionProvider;

    private String name = "";
    private float value;
    private float lastValue;
    private float blink;
    private final Color blinkColor = new Color();

    /**
     * 创建一个固定名称/颜色的进度条。
     * @param name 条上显示的文字
     * @param color 填充颜色
     * @param fraction 充满度提供者，返回值会被裁剪到 0~1
     */
    public Bar(String name, Color color, Supplier<Float> fraction){
        this.name = name;
        this.colorProvider = () -> color;
        this.fractionProvider = fraction;
        this.lastValue = this.value = clamp(fraction.get());
        setColor(color);
    }

    /** 创建动态条（名称/颜色/充满度每帧重新求值）。 */
    public Bar(Supplier<String> name, Supplier<Color> color, Supplier<Float> fraction){
        this.nameProvider = name;
        this.colorProvider = color;
        this.fractionProvider = fraction;
        this.lastValue = this.value = clamp(fraction.get());
        setColor(color.get());
    }

    private static float clamp(float v){
        return Math.max(0f, Math.min(1f, v));
    }

    @Override
    public void act(float delta){
        super.act(delta);
        if(fractionProvider == null) return;

        if(nameProvider != null){
            String next = nameProvider.get();
            this.name = next == null ? "" : next;
        }
        if(colorProvider != null){
            Color next = colorProvider.get();
            if(next != null){
                blinkColor.set(next);
                setColor(next);
            }
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha){
        if(fractionProvider == null) return;

        float computed = clamp(fractionProvider.get());
        if(Math.abs(lastValue - computed) > 0.0001f){
            blink = 1f;
            lastValue = computed;
        }
        blink = Mathf.lerpDelta(blink, 0f, 0.2f);
        value = Mathf.lerpDelta(value, computed, 0.15f);

        float x = getX(), y = getY(), w = getWidth(), h = getHeight();
        Drawable background = Tex.bar;
        Drawable top = Tex.barTop;

        //底色压暗，保证填充部分对比明显（对应原版 Draw.colorl(0.1f)）
        batch.setColor(0.1f, 0.1f, 0.1f, 1f);
        background.draw(batch, x, y, w, h);

        //填充：颜色在 fillColor 与 blinkColor 之间按闪烁量插值
        Color fill = Tmp.getFillColor(getColor(), blinkColor, blink);
        batch.setColor(fill);
        float topWidth = w * value;
        if(topWidth > 0f){
            top.draw(batch, x, y, topWidth, h);
        }

        batch.setColor(Color.WHITE);
        if(Fonts.outline != null && name != null && !name.isEmpty()){
            LAYOUT.setText(Fonts.outline, name);
            Fonts.outline.setColor(Color.WHITE);
            Fonts.outline.draw(batch, LAYOUT, x + w / 2f - LAYOUT.width / 2f, y + h / 2f + LAYOUT.height / 2f + 1f);
        }
    }

    /** 临时颜色容器（避免在 draw 中分配）。 */
    private static class Tmp{
        private static final Color fill = new Color();

        static Color getFillColor(Color base, Color blinkTo, float amount){
            return fill.set(base).lerp(blinkTo, Math.max(0f, Math.min(1f, amount)));
        }
    }

    /** @return 当前显示文本（调试用）。 */
    public String name(){
        return name;
    }
}
