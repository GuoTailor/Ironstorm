package com.phoenix.game.editor;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.func.Cons;
import com.phoenix.game.game.Rules;
import com.phoenix.game.struct.StringMap;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.ui.dialogs.CustomRulesDialog;
import com.phoenix.game.ui.dialogs.FloatingDialog;

/**
 * 地图信息对话框（名称 / 描述 / 作者 / 规则 / 波次 / 生成器）。
 * 参照 Mindustry {@code mindustry.editor.MapInfoDialog} 移植。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li><b>描述框退化</b>：原版用 arc 的 {@code TextArea}（多行）。libgdx 没有 {@code TextArea}，
 *       本项目也没有自建 → 描述退化成单行 {@link TextField}（样式仍用 {@code Styles.areaField}）。
 *       刻意降级：想要多行需要自建 TextArea（当前单行）。</li>
 *   <li>原版 {@code t.addField(text, Cons)} / {@code t.addArea(text, style, Cons)} /
 *       {@code t.addButton(text, action)} / {@code t.pane(Cons)} / {@code t.margin(...)} libgdx 都没有
 *       → 手搓 {@link TextField} + {@link ChangeListener} / {@link FloatingDialog#textButton} /
 *       自建 {@link Table} + {@link ScrollPane} / {@code pad}。</li>
 *   <li>{@code Vars.maps.readFilters(String)} 本项目已具备（T8b）→ "生成器"一行照原版**读** {@code genfilters} 标签。
 *       ⚠ 但**写回**归 B 类：实测 {@code JsonIO.write(filters)} 直接抛 {@code SerializationException}
 *       （{@code Maps.readFilters} 用匿名子类 {@code new ScatterFilter(){{...}}} 构造默认滤镜链，
 *       libgdx Json 无法实例化 {@code Maps$1}）→ 只接读取、关闭时不写回（见 {@link #setup()}）。
 *       地图生成器见 {@link MapGenerateDialog}。</li>
 *   <li>TODO {@code Vars.platform.addDialog(field, ...)}（移动端软键盘）本项目没有 {@code Vars.platform} → 省略。</li>
 *   <li>原版 {@code name.change()/description.change()/author.change()}（把输入框当前值立刻回灌进 tags）
 *       —— 这里在构建时就用 {@code tags.get(...)} 的默认值填进输入框，用户不改就不会写 tags，
 *       语义等价；故省略这三行。</li>
 *   <li>arc 的 {@code add(String)} 会解析 {@code "$key"} → 文本一律先过 {@link FloatingDialog#bundle}。</li>
 *   <li>{@code Core.settings.getString("mapAuthor", "")} / {@code put} / {@code save} 本项目都有，语义一致。</li>
 * </ul>
 */
public class MapInfoDialog extends FloatingDialog{
    private final MapEditor editor;
    private final WaveInfoDialog waveInfo;
    private final MapGenerateDialog generate;
    private final CustomRulesDialog ruleInfo = new CustomRulesDialog();

    public MapInfoDialog(MapEditor editor){
        super("$editor.mapinfo");
        this.editor = editor;
        this.waveInfo = new WaveInfoDialog(editor);
        this.generate = new MapGenerateDialog(editor, false);

        addCloseButton();

        shown(this::setup);
    }

    private void setup(){
        cont.clear();

        StringMap tags = editor.getTags();

        //原版 cont.pane(t -> {...})：libgdx 没有带回调的 pane → 自建 Table + ScrollPane
        Table t = new Table();
        t.top().left();
        ScrollPane pane = new ScrollPane(t, Styles.defaultPane);
        pane.setScrollingDisabled(true, false);

        t.add(new Label(bundle("$editor.name"), Styles.defaultLabel)).padRight(Scl.scl(8f)).left();
        t.defaults().padTop(Scl.scl(15f));

        TextField name = addField(t, tags.get("name", ""), 400f, 55f, text -> tags.put("name", text));
        name.setMessageText(bundle("$unknown"));

        t.row();
        t.add(new Label(bundle("$editor.description"), Styles.defaultLabel)).padRight(Scl.scl(8f)).left();

        //刻意降级：原版 = t.addArea(tags.get("description", ""), Styles.areaField, text -> ...).size(400f, 140f)
        //     —— libgdx/本项目都没有 TextArea（多行输入框）→ 退化成单行 TextField（样式沿用 areaField）
        TextField description = addField(t, tags.get("description", ""), 400f, 140f, text -> tags.put("description", text), Styles.areaField);

        t.row();
        t.add(new Label(bundle("$editor.author"), Styles.defaultLabel)).padRight(Scl.scl(8f)).left();

        TextField author = addField(t, tags.get("author", Core.settings.getString("mapAuthor", "")), 400f, 55f, text -> {
            tags.put("author", text);
            Core.settings.put("mapAuthor", text);
            Core.settings.save();
        });
        author.setMessageText(bundle("$unknown"));

        t.row();
        t.add(new Label(bundle("$editor.rules"), Styles.defaultLabel)).padRight(Scl.scl(8f)).left();
        t.add(textButton("$edit", null, () -> {
            ruleInfo.show(Vars.state.rules, () -> Vars.state.rules = new Rules());
            hide();
        })).left().width(Scl.scl(200f));

        t.row();
        t.add(new Label(bundle("$editor.waves"), Styles.defaultLabel)).padRight(Scl.scl(8f)).left();
        t.add(textButton("$edit", null, () -> {
            waveInfo.show();
            hide();
        })).left().width(Scl.scl(200f));

        t.row();
        t.add(new Label(bundle("$editor.generation"), Styles.defaultLabel)).padRight(Scl.scl(8f)).left();
        t.add(textButton("$edit", null, () -> {
            //原版 = generate.show(Vars.maps.readFilters(tags.get("genfilters", "")),
            //     filters -> tags.put("genfilters", JsonIO.write(filters)))
            //⚠ 读取侧可用；写回侧实测抛 SerializationException（Class cannot be created:
            //  com.phoenix.game.maps.Maps$1）—— Maps.readFilters 用匿名子类 new ScatterFilter(){{...}}
            //  构造默认滤镜链，libgdx Json 无法实例化 → 写回归 B 类，只接读取。
            generate.show(Vars.maps.readFilters(tags.get("genfilters", "")),
                filters -> com.phoenix.game.util.Log.warn(
                    "TODO genfilters write-back: JsonIO cannot serialize anonymous GenerateFilter subclasses."));
            hide();
        })).left().width(Scl.scl(200f));

        //TODO 原版 Vars.platform.addDialog(name, 50) / (author, 50) / (description, 1000)：本项目没有 Vars.platform

        t.pad(Scl.scl(16f));

        cont.add(pane).grow();
    }

    /** 一行输入框（对应 arc 的 {@code t.addField(text, Cons)}）。 */
    private TextField addField(Table table, String value, float width, float height, Cons<String> changed){
        return addField(table, value, width, height, changed, Styles.defaultField);
    }

    /** 一行输入框（可指定样式；对应 arc 的 {@code t.addField/addArea}）。 */
    private TextField addField(Table table, String value, float width, float height, Cons<String> changed,
                               TextField.TextFieldStyle style){
        TextField field = new TextField(value == null ? "" : value, style);
        field.addListener(new ChangeListener(){
            @Override
            public void changed(ChangeEvent event, Actor actor){
                changed.get(field.getText());
            }
        });
        table.add(field).size(Scl.scl(width), Scl.scl(height));
        return field;
    }
}
