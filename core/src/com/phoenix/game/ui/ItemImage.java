package com.phoenix.game.ui;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;

/**
 * 物品图标（图标 + 右下角数量角标）。参照 Mindustry mindustry.ui.ItemImage 移植。
 * <p>尺寸由使用方通过 {@code .size(...)} 指定（原版也是这样）。
 */
public class ItemImage extends Stack{

    public ItemImage(TextureRegion region, int amount){
        if(region != null){
            Image image = new Image(region);
            image.setScaling(Scaling.fit);
            add(image);
        }

        if(amount > 0){
            Table badge = new Table().left().bottom();
            Label label = new Label(String.valueOf(amount), Styles.outlineLabel);
            label.setAlignment(Align.bottomRight);
            badge.add(label);
            add(badge);
        }
    }

    public ItemImage(TextureRegion region){
        this(region, 0);
    }
}
