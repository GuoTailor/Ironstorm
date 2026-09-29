package com.phoenix.game.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.content.StatusEffects;
import com.phoenix.game.content.UnitTypes;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Strings;
import com.phoenix.game.ctype.ContentType;
import com.phoenix.game.func.Cons;
import com.phoenix.game.game.DefaultWaves;
import com.phoenix.game.game.SpawnGroup;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.input.KeyCode;
import com.phoenix.game.type.UnitType;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.ui.dialogs.FloatingDialog;
import com.phoenix.game.util.Log;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Tex;

/**
 * 波次编成对话框（编辑 {@code rules.spawns} 的每一组，并预览前若干波的出兵构成）。
 * 参照 Mindustry {@code mindustry.editor.WaveInfoDialog} 移植。
 *
 * <p>左侧是 {@link SpawnGroup} 列表（类型 / 起止波 / 间隔 / 数量 / 每波递增），右侧是波次预览。
 * 关闭时把编辑结果写回 {@code Vars.state.rules.spawns}。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li><b>{@code JsonIO.copy} 的等价替代</b>：原版用 {@code JsonIO.copy(...)} 深拷贝波次列表
 *       → 本类自带 {@link #copy(Array)} 做等价深拷贝（逐字段搬运，不经过 JSON，行为一致）。</li>
 *   <li><b>TODO {@code Maps.writeWaves/readWaves} 缺失（B 类）</b>：原版"复制/载入"按钮靠它们与剪贴板
 *       互转波次文本 → 两个按钮保留但降级：复制写入一段说明性占位文本、载入直接报 {@code $waves.invalid}
 *       （见 {@link #showEditDialog()} 内的 TODO）。"重置为默认波次"按钮**完整可用**。</li>
 *   <li>{@code StatusEffects.boss} 本项目已具备（F1e2 补齐 11 个状态效果）→ 原版那个"Boss 波"
 *       勾选框（{@code group.effect = StatusEffects.boss}）已照原版接线。</li>
 *   <li>{@code Vars.defaultWaves} 本项目没有 → 用 {@code new DefaultWaves()}（见 {@code Map.rules}）。</li>
 *   <li>{@code Strings.canParsePostiveInt/canParsePositiveFloat/parseInt/parseFloat} 本项目
 *       {@code core/Strings} 只有 {@code fixed/autoFixed/format} → 就地实现
 *       {@link #canParsePositiveInt}/{@link #canParsePositiveFloat}/{@link #parseInt}/{@link #parseFloat}。</li>
 *   <li>{@code TextFieldFilter.digitsOnly/floatsOnly}：libgdx 只有 {@code DigitsOnlyFilter}，
 *       浮点过滤用 lambda 自写（允许数字 / 小数点 / 负号）。</li>
 *   <li>arc 的 {@code addButton(Cons,Runnable)} / {@code addCheck} / {@code add(String)} / {@code pane(Cons)} /
 *       {@code table(Drawable,Cons)} / {@code margin} / {@code Cell.update} / {@code label(Prov)} /
 *       {@code Button.getClickListener()} libgdx 都没有 → 逐处手搓（自建 Button/Image/Label/ScrollPane，
 *       每帧回调改成 {@code act()} 覆写或点击即刷新）。</li>
 *   <li><b>长按加减自动重复</b>：原版用 {@code update(t -> if(t.getClickListener().isPressed()) ...)}
 *       每帧推进；libgdx 没有该回调 → 刻意降级为"点一次走一步"。</li>
 *   <li>{@code Core.app.setClipboardText/getClipboardText} → {@code Gdx.app.getClipboard()}。</li>
 *   <li>{@code state.rules.spawns} 是 libgdx {@code Array}（本项目的 {@code Rules}），
 *       所以 {@link #groups} 也用 libgdx {@code Array} 以免来回转换。</li>
 *   <li>{@code addButton(...).disabled(b -> ...)}（arc 的 Cell 动态禁用）libgdx 没有 → 在动作里加守卫。</li>
 *   <li>{@code unit.type.localizedName} 是 {@code UnlockableContent} 的公开字段，本项目一致。</li>
 * </ul>
 */
public class WaveInfoDialog extends FloatingDialog{
    private final static int displayed = 20;
    private Array<SpawnGroup> groups = new Array<>();

    private Table table, preview;
    private int start = 0;
    private UnitType lastType = UnitTypes.dagger;

    public WaveInfoDialog(MapEditor editor){
        super("$waves.title");

        shown(this::setup);
        hidden(() -> Vars.state.rules.spawns = groups);

        //原版额外用 keyDown 监听 ESC/BACK；addCloseButton 已注册同样两个键
        addCloseButton();
        buttons.add(textButton("$waves.edit", null, this::showEditDialog)).size(Scl.scl(270f), Scl.scl(64f));
    }

    /** 原版内联在 {@code buttons.addButton("$waves.edit", ...)} 里的子对话框。 */
    private void showEditDialog(){
        FloatingDialog dialog = new FloatingDialog("$waves.edit");
        dialog.addCloseButton();
        dialog.setFillParent(false);

        Table c = dialog.getContentTable();
        c.defaults().size(Scl.scl(210f), Scl.scl(64f));

        c.add(textButton("$waves.copy", null, () -> {
            if(Vars.ui != null) Vars.ui.showInfoFade("$waves.copied");
            //TODO 原版 = Core.app.setClipboardText(maps.writeWaves(groups))
            //  —— 本项目 Maps 没有 writeWaves（波次文本序列化依赖未移植的 JsonIO）→ 只写一段占位说明
            Gdx.app.getClipboard().setContents("# phoenix: wave serialization is not ported (maps.writeWaves)");
            dialog.hide();
        }));
        c.row();

        c.add(textButton("$waves.load", null, () -> {
            //TODO 原版 = groups = maps.readWaves(Core.app.getClipboardText())
            //  —— 本项目 Maps 没有 readWaves → 直接报"内容非法"
            Log.warn("TODO maps.readWaves: wave import from clipboard is not ported.");
            if(Vars.ui != null) Vars.ui.showErrorMessage("$waves.invalid");
            dialog.hide();
        }));
        c.row();

        c.add(textButton("$settings.reset", null, () -> {
            if(Vars.ui != null){
                Vars.ui.showConfirm("$confirm", "$settings.clear.confirm", () -> {
                    //原版 = JsonIO.copy(defaultWaves.get())
                    groups = copy(new DefaultWaves().get());
                    buildGroups();
                    dialog.hide();
                });
            }
        }));

        dialog.show();
    }

    void setup(){
        //原版 = JsonIO.copy(state.rules.spawns.isEmpty() ? defaultWaves.get() : state.rules.spawns)
        Array<SpawnGroup> source = Vars.state.rules.spawns;
        groups = copy(source == null || source.isEmpty() ? new DefaultWaves().get() : source);

        cont.clear();

        //原版 cont.stack(new Table(Tex.clear, main -> {...}), new Label("$waves.none"){{...}})
        Table main = new Table();
        main.setBackground(Tex.clear);

        table = new Table();
        ScrollPane groupPane = new ScrollPane(table, Styles.defaultPane);
        groupPane.setScrollingDisabled(true, false);
        main.add(groupPane).growX().growY().padRight(Scl.scl(8f));
        main.row();
        main.add(textButton("$add", null, () -> {
            if(groups == null) groups = new Array<>();
            groups.add(new SpawnGroup(lastType));
            buildGroups();
        })).growX().height(Scl.scl(70f));

        //原版那个"没有波次"的提示 Label：visible(() -> groups.isEmpty()) → 覆写 act 每帧同步
        Label none = new Label(bundle("$waves.none"), Styles.defaultLabel){
            @Override
            public void act(float delta){
                super.act(delta);
                setVisible(groups != null && groups.isEmpty());
            }
        };
        none.setTouchable(Touchable.disabled);
        none.setWrap(true);
        none.setAlignment(Align.center, Align.center);

        Stack stack = new Stack();
        stack.add(main);
        stack.add(none);
        cont.add(stack).width(Scl.scl(390f)).growY();

        //右侧预览列
        Table right = new Table();
        right.setBackground(Tex.clear);

        Label previewTitle = new Label(bundle("$waves.preview"), Styles.defaultLabel);
        previewTitle.setColor(Color.LIGHT_GRAY);
        right.add(previewTitle).growX().center();
        right.row();
        //刻意降级：原版用 Cell.update + getClickListener().isPressed() 做"长按连减" → 单击一步
        right.add(textButton("-", null, () -> {
            start = Math.max(start - 1, 0);
            updateWaves();
        })).growX().height(Scl.scl(70f));
        right.row();

        preview = new Table();
        ScrollPane previewPane = new ScrollPane(preview, Styles.defaultPane);
        previewPane.setScrollingDisabled(true, true);
        right.add(previewPane).grow();
        right.row();
        //刻意降级：同上的"长按连加"
        right.add(textButton("+", null, () -> {
            start++;
            updateWaves();
        })).growX().height(Scl.scl(70f));

        cont.add(right).growY().width(Scl.scl(180f));

        buildGroups();
    }

    void buildGroups(){
        table.clear();
        table.top();
        table.pad(Scl.scl(10f));

        if(groups != null){
            for(int gi = 0; gi < groups.size; gi++){
                final SpawnGroup group = groups.get(gi);

                Table box = new Table();
                box.setBackground(Tex.button);
                box.pad(Scl.scl(0f));
                box.defaults().pad(Scl.scl(3f)).padLeft(Scl.scl(5f)).growX().left();

                //类型按钮（原版 = t.addButton(b -> { b.left(); b.addImage(...); b.add(name).color(...); }, ...)）
                Button typeButton = new Button(Styles.defaultb);
                typeButton.left();
                typeButton.add(new Image(iconOf(group.type))).size(Scl.scl(32f)).padRight(Scl.scl(3f));
                Label typeLabel = new Label(group.type.localizedName, Styles.defaultLabel);
                typeLabel.setColor(Pal.accent);
                typeButton.add(typeLabel);
                typeButton.addListener(new ClickListener(){
                    @Override
                    public void clicked(InputEvent event, float x, float y){
                        showUpdate(group);
                    }
                });
                box.add(typeButton).pad(Scl.scl(-6f)).padBottom(0f);
                box.row();

                //起止波
                Table range = new Table();
                range.add(field("" + (group.begin + 1), digitsOnly(), text -> {
                    if(canParsePositiveInt(text)){
                        group.begin = parseInt(text) - 1;
                        updateWaves();
                    }
                })).width(Scl.scl(100f));
                range.add(new Label(bundle("$waves.to"), Styles.defaultLabel)).padLeft(Scl.scl(4f)).padRight(Scl.scl(4f));
                TextField endField = field(group.end == SpawnGroup.never ? "" : (group.end + 1) + "", digitsOnly(), text -> {
                    if(canParsePositiveInt(text)){
                        group.end = parseInt(text) - 1;
                        updateWaves();
                    }else if(text.isEmpty()){
                        group.end = SpawnGroup.never;
                        updateWaves();
                    }
                });
                //原版 = Core.bundle.get("waves.never")；本项目 bundle 缺键多 → 走 I18n 双形态判定
                endField.setMessageText(I18n.get("waves.never", "waves.never"));
                range.add(endField).width(Scl.scl(100f));
                box.add(range);
                box.row();

                //间隔
                Table spacing = new Table();
                spacing.add(new Label(bundle("$waves.every"), Styles.defaultLabel)).padRight(Scl.scl(4f));
                spacing.add(field(group.spacing + "", digitsOnly(), text -> {
                    if(canParsePositiveInt(text) && parseInt(text) > 0){
                        group.spacing = parseInt(text);
                        updateWaves();
                    }
                })).width(Scl.scl(100f));
                spacing.add(new Label(bundle("$waves.waves"), Styles.defaultLabel)).padLeft(Scl.scl(4f));
                box.add(spacing);
                box.row();

                //数量 + 每波递增
                Table amount = new Table();
                amount.add(field(group.unitAmount + "", digitsOnly(), text -> {
                    if(canParsePositiveInt(text)){
                        group.unitAmount = parseInt(text);
                        updateWaves();
                    }
                })).width(Scl.scl(80f));
                amount.add(new Label(" + ", Styles.defaultLabel));
                //原版 = Strings.fixed(Math.max((Mathf.zero(unitScaling) ? 0 : 1f / unitScaling), 0), 2)
                //本项目没有 Mathf.zero → 用 |x| < 1e-4 代替
                amount.add(field(Strings.fixed(Math.max((Math.abs(group.unitScaling) < 0.0001f ? 0f : 1f / group.unitScaling), 0f), 2),
                    floatsOnly(), text -> {
                        if(canParsePositiveFloat(text)){
                            group.unitScaling = 1f / parseFloat(text);
                            updateWaves();
                        }
                    })).width(Scl.scl(80f));
                amount.add(new Label(bundle("$waves.perspawn"), Styles.defaultLabel)).padLeft(Scl.scl(4f));
                box.add(amount);
                box.row();

                //原版 = t.addCheck("$waves.boss", b -> group.effect = b ? StatusEffects.boss : null)
                //  .update(b -> b.setChecked(group.effect == StatusEffects.boss))
                //本项目 UI 无 addCheck → 用 CheckBox + Label（同 CustomRulesDialog.check）
                Table bossRow = new Table();
                bossRow.left();
                bossRow.add(new Label(bundle("$waves.boss"), Styles.defaultLabel)).left().padRight(Scl.scl(5f));
                CheckBox bossBox = new CheckBox("", Styles.defaultCheck);
                bossBox.setChecked(group.effect == StatusEffects.boss);
                bossBox.addListener(new ChangeListener(){
                    @Override
                    public void changed(ChangeEvent event, Actor actor){
                        group.effect = bossBox.isChecked() ? StatusEffects.boss : null;
                    }
                });
                bossRow.add(bossBox);
                box.add(bossRow).left();
                box.row();

                box.add(textButton("$waves.remove", null, () -> {
                    groups.removeValue(group, true);
                    box.remove();
                    updateWaves();
                })).growX().pad(Scl.scl(-6f)).padTop(Scl.scl(5f));

                table.add(box).width(Scl.scl(340f)).pad(Scl.scl(16f));
                table.row();
            }
        }else{
            table.add(new Label(bundle("$editor.default"), Styles.defaultLabel));
        }

        updateWaves();
    }

    void showUpdate(SpawnGroup group){
        FloatingDialog dialog = new FloatingDialog("");
        dialog.setFillParent(true);
        //原版没有关闭按钮（只能选一个单位）；这里补 ESC 以免卡住
        dialog.key(KeyCode.ESCAPE, null);

        Table p = new Table();
        ScrollPane pane = new ScrollPane(p, Styles.defaultPane);
        dialog.getContentTable().add(pane).grow();

        int i = 0;
        Array<UnitType> units = Vars.content.units();
        for(int k = 0; k < units.size; k++){
            final UnitType type = units.get(k);

            Button b = new Button(Styles.defaultb);
            b.left();
            b.add(new Image(iconOf(type))).size(Scl.scl(40f)).padRight(Scl.scl(2f));
            b.add(new Label(type.localizedName, Styles.defaultLabel));
            b.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    lastType = type;
                    group.type = type;
                    dialog.hide();
                    buildGroups();
                }
            });

            p.add(b).pad(Scl.scl(2f)).padLeft(Scl.scl(12f)).fillX();
            if(++i % 3 == 0) p.row();
        }

        dialog.show();
    }

    void updateWaves(){
        preview.clear();
        preview.top();

        for(int i = start; i < displayed + start; i++){
            final int wave = i;

            Table t = new Table();
            t.setBackground(Tex.underline);

            Label num = new Label((wave + 1) + "", Styles.defaultLabel);
            num.setColor(Pal.accent);
            num.setAlignment(Align.center, Align.center);
            t.add(num).center().colspan(2);
            t.row();

            int[] spawned = new int[Vars.content.getBy(ContentType.unit).size];

            if(groups != null){
                for(int g = 0; g < groups.size; g++){
                    SpawnGroup spawn = groups.get(g);
                    //id 正常等于数组下标；这里加一道越界守卫，避免异常数据把预览整块打崩
                    if(spawn.type != null && spawn.type.id >= 0 && spawn.type.id < spawned.length){
                        spawned[spawn.type.id] += spawn.getUnitsSpawned(wave);
                    }
                }
            }

            for(int j = 0; j < spawned.length; j++){
                if(spawned[j] > 0){
                    UnitType type = Vars.content.getByID(ContentType.unit, j);
                    if(type == null) continue;
                    t.add(new Image(iconOf(type))).size(Scl.scl(8f * 4f)).padRight(Scl.scl(4f));
                    Label count = new Label(spawned[j] + "x", Styles.defaultLabel);
                    count.setColor(Color.LIGHT_GRAY);
                    t.add(count).padRight(Scl.scl(6f));
                    t.row();
                }
            }

            if(t.getChildren().size == 1){
                Label none = new Label(bundle("$none"), Styles.defaultLabel);
                none.setColor(Pal.remove);
                t.add(none);
            }

            preview.add(t).width(Scl.scl(110f)).pad(Scl.scl(2f));
            preview.row();
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 深拷贝波次列表（替代原版的 {@code JsonIO.copy}，逐字段搬运）。 */
    private static Array<SpawnGroup> copy(Array<SpawnGroup> src){
        Array<SpawnGroup> out = new Array<>();
        if(src == null) return out;

        for(int i = 0; i < src.size; i++){
            SpawnGroup g = src.get(i);
            SpawnGroup n = new SpawnGroup(g.type);
            n.begin = g.begin;
            n.end = g.end;
            n.spacing = g.spacing;
            n.max = g.max;
            n.unitScaling = g.unitScaling;
            n.unitAmount = g.unitAmount;
            n.effect = g.effect;
            n.items = g.items;
            out.add(n);
        }
        return out;
    }

    /** 单位图标；图集缺图时返回 null（{@code Image} 允许 null drawable）。 */
    private static com.badlogic.gdx.graphics.g2d.TextureRegion iconOf(UnitType type){
        return type == null ? null : type.icon(Cicon.medium);
    }

    /** 一行输入框（对应 arc 的 {@code addField(text, filter, Cons)}）。 */
    private static TextField field(String text, TextField.TextFieldFilter filter, final Cons<String> changed){
        final TextField field = new TextField(text, Styles.defaultField);
        if(filter != null) field.setTextFieldFilter(filter);
        field.addListener(new ChangeListener(){
            @Override
            public void changed(ChangeEvent event, Actor actor){
                changed.get(field.getText());
            }
        });
        return field;
    }

    /** 对应 arc 的 {@code TextFieldFilter.digitsOnly}（libgdx 提供 {@code DigitsOnlyFilter}）。 */
    private static TextField.TextFieldFilter digitsOnly(){
        return new TextField.TextFieldFilter.DigitsOnlyFilter();
    }

    /** 对应 arc 的 {@code TextFieldFilter.floatsOnly}（libgdx 没有 → 允许数字 / 小数点 / 负号）。 */
    private static TextField.TextFieldFilter floatsOnly(){
        return (f, c) -> Character.isDigit(c) || c == '.' || c == '-';
    }

    /** 对应 {@code Strings.canParsePostiveInt}（本项目 {@code core/Strings} 没有）。 */
    private static boolean canParsePositiveInt(String text){
        if(text == null || text.isEmpty()) return false;
        for(int i = 0; i < text.length(); i++){
            if(!Character.isDigit(text.charAt(i))) return false;
        }
        return true;
    }

    /** 对应 {@code Strings.parseInt}（本项目 {@code core/Strings} 没有）。 */
    private static int parseInt(String text){
        try{
            return Integer.parseInt(text.trim());
        }catch(Exception e){
            return 0;
        }
    }

    /** 对应 {@code Strings.canParsePositiveFloat}（本项目 {@code core/Strings} 没有）。 */
    private static boolean canParsePositiveFloat(String text){
        if(text == null || text.isEmpty()) return false;
        try{
            return Float.parseFloat(text.trim()) >= 0f;
        }catch(Exception e){
            return false;
        }
    }

    /** 对应 {@code Strings.parseFloat}（本项目 {@code core/Strings} 没有）。 */
    private static float parseFloat(String text){
        try{
            return Float.parseFloat(text.trim());
        }catch(Exception e){
            return 0f;
        }
    }
}
