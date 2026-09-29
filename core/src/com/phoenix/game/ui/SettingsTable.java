package com.phoenix.game.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.func.Boolc;
import com.phoenix.game.func.Cons;
import com.phoenix.game.world.meta.I18n;

/**
 * 设置项列表（一行一个复选框 / 滑块 + 末尾"恢复默认"），对应 arc
 * {@code arc.scene.ui.SettingsDialog.SettingsTable} 的 libgdx 版（替代层）。
 * <p>原版的 {@code SettingsDialog} 本身没移植（设置菜单用的是 {@code ui/dialogs/SettingsMenuDialog}，
 * 它直接 {@code extends FloatingDialog}），这里只把内部那个静态类 {@code SettingsTable} 单独落成一个文件，
 * 供设置菜单的三个分类页使用（对应原版 {@code SettingsMenuDialog} 里的 {@code game/graphics/sound} 字段）。
 *
 * <p><b>与 arc 版的适配差异（逐条对应原版写法）</b>
 * <ul>
 *   <li>{@code CheckBox.changed(Runnable)}（arc）→ libgdx 只有 {@link ChangeListener} →
 *       改用 {@code addListener(new ChangeListener(){ changed(...) })}。</li>
 *   <li>刻意降级：{@code box.update(() -> box.setChecked(settings.getBool(name)))}（arc 每帧轮询）→
 *       <b>去掉轮询</b>：构造时初始化一次，之后只由 ChangeListener 写回
 *       （外部改 settings 时 UI 不会自动同步；若要补轮询需覆写 {@code act(float)} 并避免每帧
 *       {@code setChecked} 触发 ChangeListener 反复 {@code settings.save()}）。</li>
 *   <li>{@code slider.changed(() -> {...})} + {@code slider.change()} → libgdx 用
 *       {@link ChangeListener} + {@code slider.fire(new ChangeListener.ChangeEvent())}（触发一次 changed）。</li>
 *   <li>{@code settings.defaults(...)} / {@code settings.getDefault(...)} / {@code settings.getInt(name)}：
 *       arc 的 {@code Settings} 有这套默认值体系，本项目的 {@code core/Settings} 已补齐同签名方法。</li>
 *   <li>{@code bundle.get(k, def)} → {@link I18n#get(String, String)}（本项目 bundle 裁剪过，
 *       缺键时 {@code Core.bundle.get} 返回 {@code ???key???}，fallback 不生效）。</li>
 *   <li>{@code addButton(text, action)} 是 arc Element 的便捷方法，libgdx 没有 → 自建
 *       {@link TextButton}（内部换成项目 {@link Label}，arc 的颜色标记才认）。</li>
 *   <li>{@code table.table(t -> {...})} 是 arc Table 的便捷方法，libgdx 没有 → 自建内层 {@link Table} 再 add。</li>
 *   <li>尺寸一律过 {@link Scl#scl(float)}（本项目 UI 缩放约定）。</li>
 * </ul>
 */
public class SettingsTable extends Table{
    /** 滑块右侧文本（对应 arc {@code SettingsDialog.StringProcessor}，原版是外部类的嵌套接口）。 */
    public interface StringProcessor{
        String get(int i);
    }

    /** 已登记的设置项（重建时按顺序重放）。 */
    protected Array<Setting> list = new Array<>();
    /** 重建完成后的回调（对应 arc 的 {@code Cons<SettingsTable> rebuilt}）。 */
    protected Cons<SettingsTable> rebuilt;

    public SettingsTable(){
        left();
    }

    public SettingsTable(Cons<SettingsTable> rebuilt){
        this.rebuilt = rebuilt;
        left();
    }

    public Array<Setting> getSettings(){
        return list;
    }

    public void pref(Setting setting){
        list.add(setting);
        rebuild();
    }

    public void screenshakePref(){
        sliderPref("screenshake", I18n.get("setting.screenshake.name", "Screen Shake"), 4, 0, 8, i -> (i / 4f) + "x");
    }

    public SliderSetting sliderPref(String name, String title, int def, int min, int max, StringProcessor s){
        return sliderPref(name, title, def, min, max, 1, s);
    }

    public SliderSetting sliderPref(String name, String title, int def, int min, int max, int step, StringProcessor s){
        SliderSetting res;
        list.add(res = new SliderSetting(name, title, def, min, max, step, s));
        Core.settings.defaults(name, def);
        rebuild();
        return res;
    }

    public SliderSetting sliderPref(String name, int def, int min, int max, StringProcessor s){
        return sliderPref(name, def, min, max, 1, s);
    }

    /** 标题取自 bundle {@code setting.<name>.name}；缺键时退化成内部名（arc 的 {@code bundle.get(key)} 也是这个行为）。 */
    public SliderSetting sliderPref(String name, int def, int min, int max, int step, StringProcessor s){
        SliderSetting res;
        list.add(res = new SliderSetting(name, I18n.get("setting." + name + ".name", name), def, min, max, step, s));
        Core.settings.defaults(name, def);
        rebuild();
        return res;
    }

    public void checkPref(String name, String title, boolean def){
        list.add(new CheckSetting(name, title, def, null));
        Core.settings.defaults(name, def);
        rebuild();
    }

    public void checkPref(String name, String title, boolean def, Boolc changed){
        list.add(new CheckSetting(name, title, def, changed));
        Core.settings.defaults(name, def);
        rebuild();
    }

    /** 标题取自 bundle {@code setting.<name>.name}；缺键时退化成内部名（arc 的 {@code bundle.get(key)} 也是这个行为）。 */
    public void checkPref(String name, boolean def){
        list.add(new CheckSetting(name, I18n.get("setting." + name + ".name", name), def, null));
        Core.settings.defaults(name, def);
        rebuild();
    }

    /** 标题取自 bundle {@code setting.<name>.name}；缺键时退化成内部名（arc 的 {@code bundle.get(key)} 也是这个行为）。 */
    public void checkPref(String name, boolean def, Boolc changed){
        list.add(new CheckSetting(name, I18n.get("setting." + name + ".name", name), def, changed));
        Core.settings.defaults(name, def);
        rebuild();
    }

    void rebuild(){
        clearChildren();

        for(int i = 0; i < list.size; i++){
            list.get(i).add(this);
        }

        //原版：addButton(bundle.get("settings.reset", "Reset to Defaults"), () -> {...}).margin(14).width(240f).pad(6)
        //  —— libgdx 的 Table 没有 addButton → 自建按钮（内部换成项目 Label）
        TextButton reset = new TextButton("", Styles.defaultt);
        reset.clearChildren();
        reset.add(new Label(I18n.get("settings.reset", "Reset to Defaults"), Styles.defaultLabel));
        reset.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                for(int i = 0; i < list.size; i++){
                    Setting setting = list.get(i);
                    if(setting.name == null || setting.title == null) continue;
                    Core.settings.put(setting.name, Core.settings.getDefault(setting.name));
                    Core.settings.save();
                }
                rebuild();
            }
        });
        //原版 Cell.margin(14) 作用在内层 Table 上（libgdx 的 Cell 没有 margin）→ 折进 pad
        add(reset).width(Scl.scl(240f)).pad(Scl.scl(14f));

        if(rebuilt != null) rebuilt.get(this);
    }

    /** libgdx 的 CheckBox 内部用原生 Label（不认 arc 的颜色标记）→ 换成项目 {@link Label}（同 FloatingDialog 的做法）。 */
    private static class MarkupCheckBox extends CheckBox{
        MarkupCheckBox(String text, CheckBoxStyle style){
            super(text, style);
        }

        @Override
        protected com.badlogic.gdx.scenes.scene2d.ui.Label newLabel(String text, com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle style){
            return new Label(text, style);
        }
    }

    /** 一个设置项的基类（对应 arc {@code SettingsDialog.SettingsTable.Setting}）。 */
    public abstract static class Setting{
        public String name;
        public String title;

        public abstract void add(SettingsTable table);
    }

    /** 复选框设置项（对应 arc 的 {@code CheckSetting}）。 */
    public class CheckSetting extends Setting{
        boolean def;
        Boolc changed;

        CheckSetting(String name, String title, boolean def, Boolc changed){
            this.name = name;
            this.title = title;
            this.def = def;
            this.changed = changed;
        }

        @Override
        public void add(SettingsTable table){
            CheckBox box = new MarkupCheckBox(title, Styles.defaultCheck);

            //刻意降级：arc 的 update() 每帧轮询未移植（外部改 settings 时 UI 不会自动同步）
            //  （原版：box.update(() -> box.setChecked(settings.getBool(name)));）
            box.setChecked(Core.settings.getBool(name));

            box.addListener(new ChangeListener(){
                @Override
                public void changed(ChangeEvent event, Actor actor){
                    Core.settings.put(name, box.isChecked());
                    Core.settings.save();
                    if(changed != null){
                        changed.get(box.isChecked());
                    }
                }
            });

            box.left();
            table.add(box).left().padTop(Scl.scl(3f));
            table.row();
        }
    }

    /** 滑块设置项（对应 arc 的 {@code SliderSetting}）。 */
    public class SliderSetting extends Setting{
        int def;
        int min;
        int max;
        int step;
        StringProcessor sp;
        float[] values = null;

        SliderSetting(String name, String title, int def, int min, int max, int step, StringProcessor s){
            this.name = name;
            this.title = title;
            this.def = def;
            this.min = min;
            this.max = max;
            this.step = step;
            this.sp = s;
        }

        @Override
        public void add(SettingsTable table){
            Slider slider = new Slider(min, max, step, false, Styles.defaultSlider);

            slider.setValue(Core.settings.getInt(name));
            if(values != null){
                slider.setSnapToValues(values, 1f);
            }

            Label label = new Label(title, Styles.defaultLabel);
            slider.addListener(new ChangeListener(){
                @Override
                public void changed(ChangeEvent event, Actor actor){
                    Core.settings.put(name, (int)slider.getValue());
                    Core.settings.save();
                    label.setText(title + ": " + sp.get((int)slider.getValue()));
                }
            });

            //原版 slider.change()（arc 触发一次 changed，用来刷初值文本）
            //  → libgdx 的 Slider 没有 change()，手动 fire 一次变更事件
            slider.fire(new ChangeListener.ChangeEvent());

            //原版 table.table(t -> {...})：libgdx 的 Table 没有带回调的 table(...) → 自建内层表
            Table inner = new Table();
            inner.left().defaults().left();
            inner.add(label).minWidth(label.getPrefWidth() / Scl.scl(1f) + 50);
            inner.add(slider).width(Scl.scl(180f));
            table.add(inner).left().padTop(Scl.scl(3f));

            table.row();
        }
    }
}
