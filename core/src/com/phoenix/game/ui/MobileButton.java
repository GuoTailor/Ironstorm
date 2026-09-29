package com.phoenix.game.ui;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;

/**
 * 移动端图标按钮（图标在上、文字在下）。参照 Mindustry mindustry.ui.MobileButton 移植。
 *
 * <p>与原版的两处差异：
 * <ul>
 *   <li>arc 的 {@code Button.clicked(Runnable)} 在 libgdx 里没有 → 挂一个 {@link ClickListener}。</li>
 *   <li>arc 的 {@code Table.add(text)} 需要全局 skin，本项目没有 → 自建 {@link Label}。</li>
 * </ul>
 */
public class MobileButton extends ImageButton{

    public MobileButton(Drawable icon, String text, Runnable listener){
        super(icon);
        addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                listener.run();
            }
        });
        row();
        //原版是 add(text).growX().wrap().center().get().setAlignment(...)：libgdx 的 Cell 没有 wrap()（arc 独有）
        Label label = new Label(text, Styles.defaultLabel);
        label.setWrap(true);
        label.setAlignment(Align.center, Align.center);
        add(label).growX().center();
    }
}
