package com.phoenix.game.editor;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.core.Scl;
import com.phoenix.game.func.Intc2;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.ui.dialogs.FloatingDialog;
import io.anuke.mindustry.gen.Tex;

/**
 * 地图尺寸调整对话框（宽/高各一对 "&lt;" "&gt;" 按钮，步长 50，范围 50~500）。
 * 参照 Mindustry {@code mindustry.editor.MapResizeDialog} 移植。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li>{@code Table.addButton(text, action)} / {@code Table.table(Drawable, Cons)} /
 *       {@code Table.label(Prov)} libgdx 都没有 → 按钮走 {@link FloatingDialog#textButton}，
 *       {@code table(Tex.button, ...)} 自建 {@link Table} + {@code setBackground}，
 *       动态文本用 {@link Label} + 点击后手工刷新（原版是 {@code label(Prov)} 每帧求值）。</li>
 *   <li>arc 的 {@code add(String)} 会解析 {@code "$key"}；libgdx 的 {@code Table.add(String)} 不解析
 *       → 文本一律先过 {@link FloatingDialog#bundle} 再塞进 {@link Label}。</li>
 *   <li>本项目所有 UI 尺寸统一乘 {@link Scl#scl(float)}（原版 arc 的布局单位自带缩放）。</li>
 * </ul>
 */
public class MapResizeDialog extends FloatingDialog{
    private static final int minSize = 50, maxSize = 500, increment = 50;
    int width, height;

    /** 数值框里的两个 Label（原版是 {@code label(Prov)} 每帧求值，这里点击后手工刷新）。 */
    private Label widthLabel, heightLabel;

    public MapResizeDialog(MapEditor editor, Intc2 cons){
        super("$editor.resizemap");
        shown(() -> {
            cont.clear();
            width = editor.width();
            height = editor.height();

            Table table = new Table();

            widthLabel = new Label("", Styles.defaultLabel);
            heightLabel = new Label("", Styles.defaultLabel);

            for(boolean w : Mathf.booleans){
                table.add(new Label(bundle(w ? "$width" : "$height"), Styles.defaultLabel)).padRight(Scl.scl(8f));
                table.defaults().height(Scl.scl(60f)).padTop(Scl.scl(8f));
                table.add(textButton("<", null, () -> {
                    if(w){
                        width = move(width, -1);
                    }else{
                        height = move(height, -1);
                    }
                    refresh();
                })).size(Scl.scl(60f));

                //原版 = table.table(Tex.button, t -> t.label(() -> (w ? width : height) + ""))
                Table box = new Table();
                box.setBackground(Tex.button);
                box.add(w ? widthLabel : heightLabel);
                table.add(box).width(Scl.scl(200f));

                table.add(textButton(">", null, () -> {
                    if(w){
                        width = move(width, 1);
                    }else{
                        height = move(height, 1);
                    }
                    refresh();
                })).size(Scl.scl(60f));
                table.row();
            }
            cont.row();
            cont.add(table);

            refresh();
        });

        buttons.defaults().size(Scl.scl(200f), Scl.scl(50f));
        buttons.add(textButton("$cancel", null, this::hide));
        buttons.add(textButton("$ok", null, () -> {
            cons.get(width, height);
            hide();
        }));
    }

    /** 刷新两个数值 Label（替代原版 arc 的 {@code label(Prov)} 每帧求值）。 */
    private void refresh(){
        if(widthLabel != null) widthLabel.setText(width + "");
        if(heightLabel != null) heightLabel.setText(height + "");
    }

    static int move(int value, int direction){
        return Mathf.clamp((value / increment + direction) * increment, minSize, maxSize);
    }
}
