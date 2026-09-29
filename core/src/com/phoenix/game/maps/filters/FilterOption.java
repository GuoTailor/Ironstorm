package com.phoenix.game.maps.filters;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.func.Boolf;
import com.phoenix.game.func.Cons;
import com.phoenix.game.func.Floatc;
import com.phoenix.game.func.Floatp;
import com.phoenix.game.func.Prov;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.ui.dialogs.FloatingDialog;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.blocks.OverlayFloor;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Icon;

/**
 * 地图生成滤镜的配置项基类。参照 Mindustry {@code mindustry.maps.filters.FilterOption} 移植。
 * {@link GenerateFilter#options} 里存的就是它；每个滤镜通过 {@link #build(Table)} 在编辑器里渲染自己的配置控件。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li>原版 7 个 {@code Boolf} 过滤器里都有 {@code Core.atlas.isFound(b.icon(Cicon.full))}；
 *       libgdx 的 {@code TextureAtlas} 没有 {@code isFound}，且本项目 {@code Block.icon()} 缺图会回退到
 *       {@code region}（几乎不为 null）→ 刻意降级为 {@link #iconFound} 的判空近似（见方法）。</li>
 *   <li><b>{@code Blocks.air} 在本项目是 {@code Floor}</b>（原版是独立的 AirBlock），所以
 *       {@code floorsOnly} 必须显式排除 air，否则空气会被当成可选地板。</li>
 *   <li>原版 UI 依赖 arc 的 {@code Table.add(String)}（解析 {@code $key}）、{@code addSlider}、
 *       {@code addButton(Cons,Runnable)}、{@code addImage}、{@code Cell.update/margin} —— libgdx 全都没有
 *       → 刻意降级为手搓（自建 Label/Slider/Button/Image + 监听器），见两个内部类。</li>
 *   <li>原版用 {@code Icon.block.getRegion()} 作为空气的占位图标；本项目生成包 {@code io.anuke.mindustry.gen.Icon}
 *       里没有 {@code block} → 用 {@code Icon.terrain} 占位（TODO）。</li>
 * </ul>
 */
public abstract class FilterOption{
    public static final Boolf<Block> floorsOnly = b -> (b instanceof Floor && !(b instanceof OverlayFloor) && b != Blocks.air) && !Vars.headless && iconFound(b);
    public static final Boolf<Block> wallsOnly = b -> (!b.synthetic() && !(b instanceof Floor)) && !Vars.headless && iconFound(b);
    public static final Boolf<Block> floorsOptional = b -> b == Blocks.air || ((b instanceof Floor && !(b instanceof OverlayFloor)) && !Vars.headless && iconFound(b));
    public static final Boolf<Block> wallsOptional = b -> b == Blocks.air || ((!b.synthetic() && !(b instanceof Floor)) && !Vars.headless && iconFound(b));
    public static final Boolf<Block> wallsOresOptional = b -> b == Blocks.air || (((!b.synthetic() && !(b instanceof Floor)) || (b instanceof OverlayFloor)) && !Vars.headless && iconFound(b));
    public static final Boolf<Block> oresOnly = b -> b instanceof OverlayFloor && !Vars.headless && iconFound(b);
    public static final Boolf<Block> anyOptional = b -> floorsOnly.get(b) || wallsOnly.get(b) || oresOnly.get(b) || b == Blocks.air;

    public abstract void build(Table table);

    public Runnable changed = () -> {};

    /**
     * 对应原版 {@code Core.atlas.isFound(b.icon(Cicon.full))}。
     * <p>刻意降级：libgdx 的 {@code TextureAtlas} 没有 {@code isFound}，且本项目 {@code Block.icon()} 缺图会回退到
     * {@code region} → 只能退化成「图集存在 + 图标非 null」，无法真正过滤掉"图集里没有图标"的方块。
     */
    private static boolean iconFound(Block b){
        return Core.atlas != null && b.icon(Cicon.full) != null;
    }

    static class SliderOption extends FilterOption{
        final String name;
        final Floatp getter;
        final Floatc setter;
        final float min, max, step;

        SliderOption(String name, Floatp getter, Floatc setter, float min, float max){
            this(name, getter, setter, min, max, (max - min) / 200);
        }

        SliderOption(String name, Floatp getter, Floatc setter, float min, float max, float step){
            this.name = name;
            this.getter = getter;
            this.setter = setter;
            this.min = min;
            this.max = max;
            this.step = step;
        }

        @Override
        public void build(Table table){
            //原版 = table.add("$filter.option." + name)（arc 的 Table.add(String) 会解析 $key）；
            //刻意降级：libgdx 的 Table.add(String) 不解析 → 自建 Label 走 I18n 双形态取值
            table.add(new Label(I18n.get("filter.option." + name, name), Styles.defaultLabel));
            table.row();
            Slider slider = new Slider(min, max, step, false, Styles.defaultSlider);
            slider.setValue(getter.get());
            //原版按 Vars.updateEditorOnChange 在 slider.changed(changed) / slider.released(changed) 间二选一；
            //libgdx 的 Slider 没有 changed(Runnable)/released(Runnable)，但 ChangeEvent 在拖动中与松开时都会发，
            //且松开时 isDragging() 为 false（见 libgdx Slider.touchUp）→ 用它复刻"拖动中生效 / 仅松开生效"两种模式。
            slider.addListener(new ChangeListener(){
                @Override
                public void changed(ChangeEvent event, Actor actor){
                    setter.get(slider.getValue());
                    if(Vars.updateEditorOnChange || !slider.isDragging()){
                        changed.run();
                    }
                }
            });
            table.add(slider).growX();
        }
    }

    static class BlockOption extends FilterOption{
        final String name;
        final Prov<Block> supplier;
        final Cons<Block> consumer;
        final Boolf<Block> filter;

        BlockOption(String name, Prov<Block> supplier, Cons<Block> consumer, Boolf<Block> filter){
            this.name = name;
            this.supplier = supplier;
            this.consumer = consumer;
            this.filter = filter;
        }

        @Override
        public void build(Table table){
            //原版 = table.addButton(b -> b.addImage(...).update(...), () -> {...}).pad(4).margin(12f)
            //     + table.add("$filter.option." + name)；libgdx 的 Table 没有 addButton(Cons,Runnable)/addImage、
            //     Cell 没有 update/margin → 刻意降级为手搓 Button + Image + ClickListener
            Button button = new Button(Styles.defaultb);
            button.add(new Image(blockDrawable(supplier.get(), Cicon.small))).size(Scl.scl(24f));
            button.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    openPicker();
                }
            });
            table.add(button).pad(4f).padLeft(12f).padRight(12f);
            table.add(new Label(I18n.get("filter.option." + name, name), Styles.defaultLabel));
        }

        /** 对应原版 {@code dialog.cont.addImage(...).size(8*4).pad(3).get().clicked(...)} 的方块选择对话框。 */
        private void openPicker(){
            FloatingDialog dialog = new FloatingDialog("");
            dialog.setFillParent(false);
            int i = 0;
            for(Block block : Vars.content.blocks()){
                if(!filter.get(block)) continue;

                Image image = new Image(blockDrawable(block, Cicon.medium));
                image.addListener(new ClickListener(){
                    @Override
                    public void clicked(InputEvent event, float x, float y){
                        consumer.get(block);
                        dialog.hide();
                        changed.run();
                    }
                });
                dialog.getContentTable().add(image).size(Scl.scl(32f)).pad(3f);
                if(++i % 10 == 0) dialog.getContentTable().row();
            }
            dialog.show();
        }

        /**
         * 对应原版 {@code block == Blocks.air ? Icon.block.getRegion() : block.icon(cicon)}。
         * <p>TODO 本项目生成包 {@code Icon} 里没有 {@code block} → 用 {@code Icon.terrain} 占位。
         */
        private static Drawable blockDrawable(Block block, Cicon cicon){
            TextureRegion region = block == Blocks.air ? Icon.terrain.getRegion() : block.icon(cicon);
            return region == null ? null : new TextureRegionDrawable(region);
        }
    }
}
