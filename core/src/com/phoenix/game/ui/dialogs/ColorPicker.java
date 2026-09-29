package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.phoenix.game.core.Scl;
import com.phoenix.game.func.Cons;
import com.phoenix.game.func.Floatc;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * 颜色选择对话框，参照 Mindustry mindustry.ui.dialogs.ColorPicker。
 *
 * <p><b>与本项目的差异</b>：
 * <ul>
 *   <li>arc 的 {@code Table.addSlider(min, max, step, value, Floatc)} / {@code Cell.color} /
 *       {@code Image.update(Runnable)} libgdx 都没有 → 分别改成"手搓 Slider + ChangeListener"、
 *       "给 Label 设颜色"、"在匿名 Image 的 act() 里刷新颜色"（见 {@link #addSlider}）。</li>
 *   <li>{@code Tex.alphaBg}（透明棋盘格贴图）本项目图集里没有，预览底色退化成
 *       {@code Styles.black6}（刻意降级；等补了棋盘格再换回去）。</li>
 * </ul>
 */
public class ColorPicker extends FloatingDialog{
    /** 滑块长度（原版写死 150f）。 */
    private static final float SLIDER_WIDTH = 150f;
    /** 色块预览边长（原版写死 200f）。 */
    private static final float PREVIEW_SIZE = 200f;

    private Cons<Color> cons = c -> {};
    private final Color current = new Color();

    public ColorPicker(){
        super("$pickcolor");
    }

    public void show(Color color, Cons<Color> consumer){
        show(color, true, consumer);
    }

    public void show(Color color, boolean alpha, Cons<Color> consumer){
        current.set(color);
        cons = consumer;
        show();

        cont.clear();

        Table content = new Table();
        ScrollPane pane = new ScrollPane(content, Styles.defaultPane);
        pane.setFadeScrollBars(false);
        cont.add(pane).grow();

        //色块预览：底下垫一层底色（原版是透明棋盘格 Tex.alphaBg），上面是随 current 实时变色的白图
        Image backdrop = new Image(Styles.black6);
        Image swatch = new Image(Tex.whiteui){
            @Override
            public void act(float delta){
                super.act(delta);
                setColor(current);
            }
        };

        Table preview = new Table();
        preview.setBackground(Tex.pane);
        preview.stack(backdrop, swatch).size(Scl.scl(PREVIEW_SIZE));
        content.add(preview).colspan(2).padBottom(Scl.scl(5f));

        content.row();

        content.defaults().padBottom(Scl.scl(4f));
        addSlider(content, "R", Pal.remove, current.r, value -> current.r = value).width(Scl.scl(SLIDER_WIDTH));
        content.row();
        addSlider(content, "G", Color.LIME, current.g, value -> current.g = value).width(Scl.scl(SLIDER_WIDTH));
        content.row();
        addSlider(content, "B", Color.ROYAL, current.b, value -> current.b = value).width(Scl.scl(SLIDER_WIDTH));
        content.row();
        if(alpha){
            addSlider(content, "A", null, current.a, value -> current.a = value).width(Scl.scl(SLIDER_WIDTH));
            content.row();
        }

        buttons.clear();
        addCloseButton();
        buttons.add(imageTextButton("$ok", Icon.check, null, () -> {
            cons.get(current);
            hide();
        })).size(Scl.scl(210f), Scl.scl(64f));
    }

    /** 一行"名字 + 滑块"。对应 arc 的 {@code Table.addSlider(...)}（libgdx 没有）。 */
    private Cell<Slider> addSlider(Table table, String name, Color nameColor, float value, Floatc setter){
        Label label = new Label(name, Styles.defaultLabel);
        if(nameColor != null) label.setColor(nameColor);
        table.add(label);

        Slider slider = new Slider(0f, 1f, 0.01f, false, Styles.defaultSlider);
        slider.setValue(value);
        slider.addListener(new ChangeListener(){
            @Override
            public void changed(ChangeEvent event, Actor actor){
                setter.get(slider.getValue());
            }
        });
        return table.add(slider);
    }
}
