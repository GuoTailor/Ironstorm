package com.phoenix.game.ui.dialogs;

import com.phoenix.game.core.Scl;
import com.phoenix.game.input.KeyCode;
import io.anuke.mindustry.gen.Icon;

/**
 * 操作键位对话框，参照 Mindustry mindustry.ui.dialogs.ControlsDialog。
 *
 * <p><b>TODO 原版 {@code extends KeybindDialog}（arc 的 {@code arc.scene.ui.KeybindDialog}，
 * 键位绑定列表）—— 该类型在本项目没有替代层</b>，按约定不自行造替代类，所以这里先继承
 * {@link FloatingDialog} 落地"外壳"：标题居中 + 标题下分隔线 + 返回按钮。
 * <p>原版构造器只做标题样式（不调 addCloseButton，由 KeybindDialog 的构造器调）；
 * 本项目没有 KeybindDialog，所以显式调一次 {@link #addCloseButton()}。
 * 键位表（{@code Styles.defaultKeybindDialog} 已经备好）等 KeybindDialog 移植后再补进 cont。
 */
public class ControlsDialog extends FloatingDialog{
    /** 返回按钮尺寸（原版写死 230x64）。 */
    private static final float CLOSE_WIDTH = 230f, CLOSE_HEIGHT = 64f;

    public ControlsDialog(){
        super("$settings.controls");
        addCloseButton();
    }

    @Override
    public void addCloseButton(){
        buttons.add(imageTextButton("$back", Icon.arrowLeft, null, this::hide))
            .size(Scl.scl(CLOSE_WIDTH), Scl.scl(CLOSE_HEIGHT));

        key(KeyCode.ESCAPE, null);
        key(KeyCode.BACK, null);
    }
}
