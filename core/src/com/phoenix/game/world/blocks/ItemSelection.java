package com.phoenix.game.world.blocks;

import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Scl;
import com.phoenix.game.ctype.UnlockableContent;
import com.phoenix.game.func.Cons;
import com.phoenix.game.func.Prov;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.ui.Styles;
import io.anuke.mindustry.gen.Tex;

/**
 * 内容选择表格（物品/液体等的图标网格）。参照 Mindustry mindustry.world.blocks.ItemSelection 移植。
 * <p>点击图标切换选中项，并把选中的内容（或 null = 清除）交给 {@code consumer}；
 * 每帧把 {@code holder} 的当前值同步到按钮的选中态，所以外部改动也会反映到界面上。
 * <p>刻意偏离（本项目结构，libgdx 与 arc 的 UI 差异）：
 * <ul>
 *     <li>原版用 arc 的 {@code Table.addImageButton(drawable, style, size, runnable)} / {@code addImage(...)}
 *         扩展方法；libgdx 没有，改为自己 {@code new ImageButton(style)} + {@code cont.add(...)}
 *         （同 {@code MobileInput.iconButton} 的写法）。</li>
 *     <li>原版 {@code button.update(Runnable)} 是 arc 的"每帧回调"，libgdx 没有 —— 改为覆写
 *         {@link ImageButton#act(float)}，并关掉程序化选中事件避免回调自激。</li>
 *     <li>原版 {@code button.changed(Runnable)} 改为 libgdx 的 {@link ChangeListener}。</li>
 *     <li>原版 {@code pane.setScrollYForce(...)} / {@code pane.update(...)}：libgdx 的 {@code ScrollPane}
 *         没有 Force 版，改为先 {@code validate()} 再 {@code setScrollY(...)}，滚动位置用覆写 {@code act()} 记录。</li>
 *     <li>{@code Styles.smallPane} 本项目没有，退化为 {@code Styles.defaultPane}。</li>
 * </ul>
 * 依赖（已核实）：
 * <ul>
 *     <li>{@code world.isZone()} **已存在**（F5 批补的 {@code World.isZone()}）；原版过滤条件里的
 *         {@code Vars.data.isUnlocked(item)} 已按原版直写（S4b 批接线，{@code GlobalData.isUnlocked} 于 S4a 落地；
 *         此前用 {@code UnlockableContent.unlocked()} 替代的降级说明已过时）。</li>
 *     <li>{@code control.input.frag.config.hideConfig()}（配置弹窗体系）；本项目等价操作是
 *         {@code Vars.control.input.configTile = null}。</li>
 * </ul>
 */
public class ItemSelection{
    /** 跨次打开记住的滚动位置（原版同名静态字段）。 */
    private static float scrollPos = 0f;

    public static <T extends UnlockableContent> void buildTable(Table table, Array<T> items, Prov<T> holder, Cons<T> consumer){

        ButtonGroup<ImageButton> group = new ButtonGroup<>();
        group.setMinCheckCount(0);
        Table cont = new Table();
        cont.defaults().size(Scl.scl(40f));

        int i = 0;

        for(T item : items){
            //原版 world/blocks/ItemSelection.java:27：if(!data.isUnlocked(item) && world.isZone()) continue;
            //  —— 解锁判定已按原版接线（S4b 批）；Vars.world != null 是本项目的既有防御（原版 world 恒非 null）
            if(!Vars.data.isUnlocked(item) && Vars.world != null && Vars.world.isZone()) continue;

            final T selectedItem = item;

            ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle(Styles.clearToggleTransi);
            style.up = Tex.whiteui;
            ImageButton button = new ImageButton(style){
                @Override
                public void act(float delta){
                    super.act(delta);
                    //原版 button.update(() -> button.setChecked(holder.get() == item))
                    setChecked(holder.get() == selectedItem);
                }
            };
            button.setProgrammaticChangeEvents(false);
            button.addListener(new ChangeListener(){
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor){
                    //原版会顺手收起配置弹窗；本项目等价操作是清掉配置目标
                    if(Vars.control != null && Vars.control.input != null){
                        Vars.control.input.configTile = null;
                    }
                    consumer.get(button.isChecked() ? selectedItem : null);
                }
            });

            TextureRegionDrawable icon = selectedItem.icon(Cicon.small) == null
                ? null : new TextureRegionDrawable(selectedItem.icon(Cicon.small));
            button.getStyle().imageUp = icon;
            group.add(button);

            cont.add(button).size(Scl.scl(40f));

            if(i++ % 4 == 3){
                cont.row();
            }
        }

        //add extra blank spaces so it looks nice
        if(i % 4 != 0){
            int remaining = 4 - (i % 4);
            for(int j = 0; j < remaining; j++){
                cont.add(new Image(Styles.black6));
            }
        }

        ScrollPane pane = new ScrollPane(cont, Styles.defaultPane){
            @Override
            public void act(float delta){
                super.act(delta);
                scrollPos = getScrollY();
            }
        };
        pane.setScrollingDisabled(true, false);
        //原版 setScrollYForce 可以无视布局直接设值；libgdx 的 setScrollY 依赖布局后的尺寸，先 validate
        pane.validate();
        pane.setScrollY(scrollPos);

        pane.setOverscroll(false, false);
        table.add(pane).maxHeight(Scl.scl(40f * 5f));
    }
}
