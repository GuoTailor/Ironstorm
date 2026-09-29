package com.phoenix.game.editor;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Scl;
import com.phoenix.game.func.Cons;
import com.phoenix.game.maps.Map;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.ui.dialogs.FloatingDialog;

/**
 * 地图另存为对话框（输入地图名；重名且不是自定义图时禁止保存）。
 * 参照 Mindustry {@code mindustry.editor.MapSaveDialog} 移植。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li>TODO 原版 {@code Vars.platform.addDialog(field)}（把输入框登记给移动端软键盘）——
 *       本项目没有 {@code Vars.platform} 字段 → 整段省略。</li>
 *   <li>原版 {@code cont.label(Prov)}（每帧求值的状态提示）libgdx 没有 → 用 {@link Label} +
 *       {@link ChangeListener} 在输入变化时刷新（见 {@link #refreshStatus()}）。</li>
 *   <li>原版 {@code button.setDisabled(this::invalid)}（动态禁用）libgdx 没有 → 每次输入变化时手工
 *       {@code setDisabled(invalid())}。</li>
 *   <li>原版 {@code new TextField()} 无参构造器 libgdx 没有 → 用 {@code new TextField("", Styles.defaultField)}。</li>
 *   <li>arc 的 {@code add(String)} 会解析 {@code "$key"} → 文本一律先过 {@link FloatingDialog#bundle}。</li>
 *   <li>{@code Vars.maps.byName(String)} 本项目存在（{@link com.phoenix.game.maps.Maps#byName}），语义一致。</li>
 *   <li>{@code ui.showErrorMessage} → {@code Vars.ui.showErrorMessage}（{@code Vars.ui} 可能为 null，加守卫）。</li>
 * </ul>
 */
public class MapSaveDialog extends FloatingDialog{
    private final TextField field;
    private final Cons<String> listener;

    /** "保存"按钮（原版用 {@code setDisabled(Boolp)} 动态判定；这里手工同步）。 */
    private TextButton saveButton;
    /** 重名状态提示（原版是 {@code label(Prov)}）。 */
    private Label statusLabel;

    public MapSaveDialog(Cons<String> cons){
        super("$editor.savemap");
        field = new TextField("", Styles.defaultField);
        listener = cons;

        //TODO 原版 Vars.platform.addDialog(field)：本项目没有 Vars.platform（见类注释）

        field.addListener(new ChangeListener(){
            @Override
            public void changed(ChangeEvent event, Actor actor){
                refreshStatus();
            }
        });

        shown(() -> {
            cont.clear();

            statusLabel = new Label("", Styles.defaultLabel);
            cont.add(statusLabel).colspan(2);
            cont.row();
            cont.add(new Label(bundle("$editor.mapname"), Styles.defaultLabel)).padRight(Scl.scl(14f));
            cont.add(field).size(Scl.scl(220f), Scl.scl(48f));

            refreshStatus();
        });

        buttons.defaults().size(Scl.scl(200f), Scl.scl(50f)).pad(Scl.scl(2f));
        buttons.add(textButton("$cancel", null, this::hide));

        saveButton = textButton("$save", null, () -> {
            if(!invalid()){
                cons.get(field.getText());
                hide();
            }
        });
        saveButton.setDisabled(invalid());
        buttons.add(saveButton);
    }

    /** 刷新重名提示与"保存"按钮的可用性（对应原版 {@code label(Prov)} + {@code setDisabled(Boolp)}）。 */
    private void refreshStatus(){
        Map map = Vars.maps == null ? null : Vars.maps.byName(field.getText());
        if(statusLabel != null){
            if(map == null){
                statusLabel.setText("");
            }else{
                statusLabel.setText(bundle(map.custom ? "$editor.overwrite" : "$editor.failoverwrite"));
            }
        }
        if(saveButton != null){
            saveButton.setDisabled(invalid());
        }
    }

    public void save(){
        if(!invalid()){
            listener.get(field.getText());
        }else if(Vars.ui != null){
            Vars.ui.showErrorMessage("$editor.failoverwrite");
        }
    }

    public void setFieldText(String text){
        field.setText(text);
    }

    private boolean invalid(){
        if(field.getText().isEmpty()){
            return true;
        }
        Map map = Vars.maps == null ? null : Vars.maps.byName(field.getText());
        return map != null && !map.custom;
    }
}
