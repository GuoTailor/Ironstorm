package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.phoenix.game.core.Scl;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.input.Binding;
import com.phoenix.game.input.InputDevice.DeviceType;
import com.phoenix.game.input.KeyBinds;
import com.phoenix.game.input.KeyCode;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Tex;

/**
 * 操作键位对话框，参照 Mindustry mindustry.ui.dialogs.ControlsDialog（原版 extends arc 的 KeybindDialog）。
 *
 * <p><b>与原版的差异（F15 落地的"KeybindDialog 替代层"）</b>：
 * <ul>
 *   <li>原版列表/重绑逻辑全部在 {@code arc.scene.ui.KeybindDialog}；本类直接在 FloatingDialog 里实现
 *       等价的键位表（分类分组 + 当前键 + Rebind/Reset + 恢复默认），数据源是
 *       {@link KeyBinds}（对应原版 {@code Core.keybinds}）。</li>
 *   <li>原版支持多 Section / 手柄设备切换；本项目只有默认键盘 section → 不做设备切换栏。</li>
 *   <li>重绑捕获对话框：键盘键/鼠标键/滚轮三种输入都能录（原版同）；双键轴（move_x/move_y）走
 *       原版的两段式（先按负方向键、再按正方向键）。ESC/BACK 取消。</li>
 *   <li>arc 的 markup（{@code [red]/[]}）由项目 {@link Label} 解析。</li>
 * </ul>
 *
 * <p>原版构造器只做标题样式（不调 addCloseButton，由 KeybindDialog 的构造器调）；
 * 本项目显式调 {@link #addCloseButton()}。
 */
public class ControlsDialog extends FloatingDialog{
    /** 返回按钮尺寸（原版写死 230x64）。 */
    private static final float CLOSE_WIDTH = 230f, CLOSE_HEIGHT = 64f;

    /** 正在重绑的绑定；null 表示不在重绑流程。 */
    private Binding rebindKey;
    /** 双键轴两段式状态：true 表示下一条输入记录为 min（负方向）。 */
    private boolean rebindMin;
    /** 两段式第一段记下的 min 键。 */
    private int minKey;
    private Dialog rebindDialog;

    public ControlsDialog(){
        super("$settings.controls");
        addCloseButton();
        setup();
    }

    /** 建键位表（对应原版 KeybindDialog.setup；重绑/恢复默认后整体重建）。 */
    private void setup(){
        cont.clear();

        Table table = new Table();
        String lastCategory = null;

        for(Binding binding : Binding.values()){
            //分类标题（照原版：category() 非 null 且变化时插入一行灰字 + 分隔线）
            if(binding.category() != null && !binding.category().equals(lastCategory)){
                String catName = I18n.get("category." + binding.category() + ".name", capitalize(binding.category()));
                table.add(new Label("[gray]" + catName + "[]", Styles.defaultLabel))
                    .colspan(4).pad(Scl.scl(10f)).padBottom(Scl.scl(4f)).row();
                table.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(Tex.whiteui.tint(Pal.gray)))
                    .fillX().height(Scl.scl(3f))
                    .pad(Scl.scl(6f)).colspan(4).padTop(0f).padBottom(Scl.scl(10f)).row();
                lastCategory = binding.category();
            }

            boolean dualAxis = isDualAxis(binding);
            KeyBinds.Axis axis = KeyBinds.get(binding);

            table.add(new Label(I18n.get("keybind." + binding.name() + ".name", capitalize(binding.name())), Styles.defaultLabel))
                .left().padLeft(Scl.scl(8f)).padRight(Scl.scl(40f));
            table.add(new Label(keyText(axis, dualAxis), Styles.defaultLabel))
                .left().minWidth(Scl.scl(90f)).padRight(Scl.scl(20f));

            table.add(textButton(I18n.get("settings.rebind", "Rebind"), null, () -> openRebind(binding)))
                .width(Scl.scl(130f));
            table.add(textButton(I18n.get("settings.resetKey", "Reset"), null, () -> {
                KeyBinds.resetToDefault(binding);
                setup();
            })).width(Scl.scl(130f));

            table.row();
        }

        //恢复全部默认（对应原版表尾的 settings.reset 按钮）
        table.add(textButton(I18n.get("settings.reset", "Reset to Defaults"), null, () -> {
            KeyBinds.resetToDefaults();
            setup();
        })).colspan(4).padTop(Scl.scl(4f)).fill();

        ScrollPane pane = new ScrollPane(table, Styles.defaultPane);
        pane.setFadeScrollBars(false);
        cont.add(pane).grow().colspan(2);
    }

    /** 打开"按一个键"捕获对话框（对应原版 KeybindDialog.openDialog）。 */
    private void openRebind(Binding binding){
        rebindKey = binding;
        rebindMin = isDualAxis(binding);
        showRebindDialog();
    }

    /** 弹出捕获对话框（两段式两段共用；标题按当前段位变化）。 */
    private void showRebindDialog(){
        String prompt = isDualAxis(rebindKey)
            ? I18n.get("keybind.press.axis", "Press an axis or key...") + " (" + (rebindMin ? "1" : "2") + "/2)"
            : I18n.get("keybind.press", "Press a key...");

        Window.WindowStyle window = new Window.WindowStyle(
            Styles.defaultDialog.titleFont, Styles.defaultDialog.titleFontColor, Styles.defaultDialog.background);
        rebindDialog = new Dialog(prompt, window);
        rebindDialog.setModal(true);
        //捕获三条输入路径（对应原版 rebindDialog 的 keyDown/touchDown/scrolled 监听）：
        //ESC/BACK 取消；鼠标键与滚轮照原版也可录为键位
        rebindDialog.addListener(new InputListener(){
            @Override
            public boolean keyDown(InputEvent event, int keycode){
                if(keycode == KeyCode.ESCAPE || keycode == KeyCode.BACK){
                    cancelRebind();
                    return true;
                }
                rebind(keycode);
                return true;
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button){
                //libgdx 的 Buttons 常量（0..4）换算成本项目负数鼠标键码（同 InputHandler.mouseCode）
                rebind(-(button) - 1);
                return true;
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY){
                rebind(KeyCode.SCROLL);
                return true;
            }
        });
        rebindDialog.show(FloatingDialog.scene);
    }

    /** 写入新绑定（对应原版 KeybindDialog.rebind；双键轴第一段只记 min 并重开对话框）。 */
    private void rebind(int newKey){
        if(rebindKey == null) return;
        Binding binding = rebindKey;

        if(isDualAxis(binding)){
            if(rebindMin){
                //第一段：记下负方向键，延后一帧重开对话框录正方向（原版用 Time.runTask 延后一帧，同型）
                minKey = newKey;
                rebindMin = false;
                rebindDialog.hide();
                Gdx.app.postRunnable(this::showRebindDialog);
                return;
            }
            KeyBinds.bind(binding, minKey, newKey);
        }else{
            KeyBinds.bind(binding, newKey);
        }
        finishRebind();
    }

    private void cancelRebind(){
        rebindDialog.hide();
        rebindKey = null;
    }

    private void finishRebind(){
        rebindDialog.hide();
        rebindKey = null;
        rebindMin = false;
        //KeyBinds.bind 内部已 save；这里重建列表刷新显示（对应原版 setup()）
        setup();
    }

    /** @return 是否为双键轴（move_x/move_y 这类 min/max 成对的绑定；单键轴 rotate/zoom 不算）。 */
    private static boolean isDualAxis(Binding binding){
        return binding.defaultValue(DeviceType.keyboard) instanceof KeyBinds.Axis
            && ((KeyBinds.Axis)binding.defaultValue(DeviceType.keyboard)).key == 0;
    }

    /** @return 当前键位显示文本（双键轴显示 "min / max"，其余显示单键名）。 */
    private static String keyText(KeyBinds.Axis axis, boolean dualAxis){
        if(dualAxis){
            return KeyBinds.keyCodeName(axis.min) + " [red]/[] " + KeyBinds.keyCodeName(axis.max);
        }
        return KeyBinds.keyCodeName(axis.key != 0 ? axis.key : axis.min);
    }

    /** 原版 Strings.capitalize：首字母大写。 */
    private static String capitalize(String s){
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
