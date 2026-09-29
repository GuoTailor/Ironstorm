package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Scl;
import com.phoenix.game.func.Cons;
import com.phoenix.game.input.KeyCode;
import com.phoenix.game.ui.Styles;
import io.anuke.mindustry.gen.Tex;

/**
 * 玩家颜色选择盘（16 个颜色格）。参照 Mindustry mindustry.ui.dialogs.PaletteDialog。
 *
 * <p><b>与本项目的差异</b>：
 * <ul>
 *   <li>原版继承 arc 的 {@code Dialog}；本项目没有可用的 arc Dialog，改为继承 {@link FloatingDialog}
 *       （标题为空串，行为与原来一致：无标题、无关闭按钮）。</li>
 *   <li>原版 {@code table.addImageButton(Tex.whiteui, style, 34, action)} 是 arc 的便捷方法 →
 *       libgdx 的 Table 没有 addImageButton，改成"先 new ImageButton 再 add"。</li>
 *   <li>原版 {@code button.getStyle().imageUpColor = color}：libgdx 的 {@code ImageButtonStyle}
 *       <b>没有</b> {@code imageUpColor} 字段（本项目 UI 自建替代层）→ 改成给按钮内部的 {@code Image} 设色
 *       （视觉等价，刻意降级）。</li>
 *   <li>原版 {@code button.setChecked(player.color.equals(color))}：本项目 {@code Player.color} 已存在
 *       （{@code entities/type/Player.java:43}）→ {@code Vars.player} 非空时用它判选中。</li>
 *   <li>原版 {@code keyDown(key -> ...)}（arc 的按键回调）libgdx 没有 → 用 {@code Dialog.key(int, Object)}。</li>
 * </ul>
 */
public class PaletteDialog extends FloatingDialog{
    private Cons<Color> cons;

    public PaletteDialog(){
        super("");
        build();
    }

    private void build(){
        Table table = new Table();
        cont.add(table);

        for(int i = 0; i < Vars.playerColors.length; i++){
            Color color = Vars.playerColors[i];

            //原版：table.addImageButton(Tex.whiteui, Styles.clearTogglei, 34, () -> {...}).size(48).get()
            //libgdx 的 Table 没有 addImageButton → 建好按钮再 add；样式用拷贝构造，避免改到全局静态样式
            //libgdx 的 ImageButton 也没有 (Drawable, ImageButtonStyle) 构造器 → 手工设 imageUp
            ImageButton.ImageButtonStyle bs = new ImageButton.ImageButtonStyle(Styles.clearTogglei);
            bs.imageUp = Tex.whiteui;
            ImageButton button = new ImageButton(bs);
            button.getImage().setDrawable(Tex.whiteui);
            button.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    cons.get(color);
                    hide();
                }
            });
            table.add(button).size(Scl.scl(48f));

            //原版：button.setChecked(player.color.equals(color))
            //  —— Player.color 已存在（entities/type/Player.java:43）→ 菜单期 Vars.player 为 null 时回退读设置
            Color current = Vars.player != null ? Vars.player.color : JoinDialog.playerColor();
            button.setChecked(current.equals(color));
            //原版：button.getStyle().imageUpColor = color
            //  —— libgdx 的 ImageButtonStyle 没有 imageUpColor → 给按钮内的 Image 设色（等价替代，刻意降级）
            button.getImage().setColor(color);

            if(i % 4 == 3){
                table.row();
            }
        }

        //原版 keyDown(key -> { if(key == KeyCode.ESCAPE || key == KeyCode.BACK) hide(); })
        //libgdx 的 Dialog 没有 keyDown → 用 key(int, Object)（与 FloatingDialog.addCloseButton 同一做法）
        key(KeyCode.ESCAPE, null);
        key(KeyCode.BACK, null);
    }

    public void show(Cons<Color> cons){
        this.cons = cons;
        show();
    }
}
