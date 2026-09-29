package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Strings;
import com.phoenix.game.core.Time;
import com.phoenix.game.game.Saves.SaveSlot;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * 存档对话框（列出全部槽位，可覆盖保存 / 改名 / 删除 / 新建）。
 * 参照 Mindustry mindustry.ui.dialogs.SaveDialog。
 *
 * <p><b>与原版 {@code SaveDialog extends LoadDialog} 的关系</b>：
 * <ul>
 *   <li>原版 {@code SaveDialog} 继承 {@code LoadDialog} 复用槽位列表；本项目 <b>{@link LoadDialog} 已存在</b>
 *       （后续批次补的，见其类注释），但 {@code SaveDialog} 早已独立成 {@code extends FloatingDialog}，
 *       并把 {@code pane}/{@code slots}/{@code setup()} 内联了进来（读档按钮/导入导出等属于 LoadDialog 的内容不搬）。
 *       两者<b>刻意暂不建立继承关系</b>（{@code LoadDialog} 类注释写明"统一留到后续批次决定是否收敛"）
 *       → 本批不重构，仅更正"本项目没有 LoadDialog"这条已过时的描述。</li>
 *   <li>槽位成员核对（T17 已核实）：{@code slot.isHidden()}（隐藏槽位）、{@code slot.getZone()}、
 *       {@code slot.previewFile()}/{@code hasPreview()} <b>都已存在</b>（{@link SaveSlot}，存档落库批补上；
 *       本文件已在用 {@code isHidden()} 过滤区域槽）。<b>T19 又补上了逐槽自动存档开关</b>
 *       {@code slot.isAutosave()}/{@code setAutosave()}（存 {@code Core.settings} 的
 *       {@code save-<index>-autosave} 键，与存档 meta 无关）→ 卡片右侧的开关按钮已按原版接回。
 *       仍缺的只有 {@code slot.setName(...)}（改名并写回；本项目只有 public {@code name} 字段）、
 *       {@code slot.mode()}（原版 {@code Gamemode.bestFit(meta.rules)}，本项目 meta 是结构化 RulesMeta）、
 *       {@code slot.previewTexture()}、{@code control.saves.importSave(...)}（导入属 LoadDialog 功能，本类不搬）
 *       → 逐项降级（见下方各处的降级说明）。<b>不要为 UI 好看而硬加这些字段</b>，那会改存档槽语义。</li>
 *   <li><b>"导出存档"已接线</b>：原版走 {@code platform.showFileChooser(false, saveExtension, file -> slot.exportFile(file))}，
 *       本项目没有 {@code Vars.platform} 门面、{@link SaveSlot} 也没有 {@code exportFile} → 改用已移植的
 *       {@link FileChooser} + {@code Files.copy(slot.file, dest)}（{@link SaveSlot#file} 是 public final）。</li>
 *   <li>缩略图：原版用 {@code slot.previewTexture()} 动态换图；本项目只有 {@code previewFile()} →
 *       打开时 {@code new Texture(...)} 加载，并在 {@link #hide()} 里统一 dispose（与
 *       {@code HudFragment.showLoadDialog} 的做法一致，否则每开一次对话框就漏一张 GL 纹理）。</li>
 *   <li>刻意降级 arc 的 {@code Cell.margin(...)}（外间距）libgdx 没有 → 用额外的 {@code pad} 顶替。</li>
 *   <li>刻意降级 arc 的 {@code button.update(Runnable)} / {@code Cell.update(...)}（每帧回调）libgdx 没有
 *       → 缩略图不再做"纹理失效后回退 nomap"的动态检查，改成加载失败直接用 {@link Tex#nomap}。</li>
 *   <li>{@code Core.app.post(...)} → {@code Gdx.app.postRunnable(...)}；{@code Core.scene} → {@link FloatingDialog#scene}；
 *       {@code ui.loadAnd/loadfrag/showTextInput/showConfirm/showException} 本项目的 {@code Vars.ui} 都有。</li>
 * </ul>
 */
public class SaveDialog extends FloatingDialog{
    ScrollPane pane;
    Table slots;

    /** 本对话框加载的缩略图纹理，关闭时统一释放。 */
    private final Array<Texture> textures = new Array<>();

    public SaveDialog(){
        super("$savegame");

        setup();

        shown(() -> {
            setup();
            Time.run(2f, () -> {
                if(scene != null) scene.setScrollFocus(pane);
            });
        });

        addCloseButton();
    }

    /**
     * 原版 {@code update(() -> { if(state.is(State.menu) && isShown()) hide(); })}：
     * libgdx 的 Dialog 没有每帧回调 → 覆写 {@code act()}。{@code act()} 只在对话框还挂在 stage 上时被调用，
     * 所以不需要再判 arc 的 {@code isShown()}。
     */
    @Override
    public void act(float delta){
        super.act(delta);
        if(Vars.state.isMenu()){
            hide();
        }
    }

    @Override
    public void hide(){
        super.hide();
        for(Texture texture : textures){
            texture.dispose();
        }
        textures.clear();
    }

    protected void setup(){
        cont.clear();

        slots = new Table();
        pane = new ScrollPane(slots, Styles.defaultPane);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);

        //原版 slots.marginRight(24)：libgdx 没有 margin（外间距）→ 用 pad
        slots.padRight(Scl.scl(24f));

        //原版 Time.runTask(2f, () -> Core.scene.setScrollFocus(pane))
        Time.run(2f, () -> {
            if(scene != null) scene.setScrollFocus(pane);
        });

        //原版 control.saves.getSaveSlots() 后按时间戳倒序；本项目是 Vars.saves.getSlots()
        Array<SaveSlot> array = new Array<>(Vars.saves.getSlots());
        array.sort((slot, other) -> -Long.compare(slot.dateMillis(), other.dateMillis()));

        for(SaveSlot slot : array){
            //区域存档槽（saves/-1.msav）在列表里隐藏：它是自动维护的"继续战役"进度，不是玩家手动存档
            if(slot.isHidden()) continue;

            TextButton button = new TextButton("", Styles.cleart);
            button.getLabel().remove();
            button.clearChildren();

            button.defaults().left();

            //标题行：名字 + 右侧操作按钮（原版 button.table(title -> {...}).growX().colspan(2)）
            Table title = new Table();

            Label nameLabel = new Label("[accent]" + slot.name, Styles.defaultLabel);
            nameLabel.setWrap(true);
            title.add(nameLabel).left().growX().width(Scl.scl(230f));

            Table actions = new Table();
            actions.right();

            //已接线 原版"自动存档开关"（Icon.save + Styles.emptytogglei + slot.setAutosave/isAutosave）：
            //  T19 已给 SaveSlot 补上 per-slot 的 isAutosave()/setAutosave()（存 Core.settings 的 save-<index>-autosave 键）
            actions.add(autosaveButton(slot)).right();

            actions.add(stopIconButton(Icon.trash, Styles.emptyi, () -> {
                if(Vars.ui != null){
                    Vars.ui.showConfirm("$confirm", "$save.delete.confirm", () -> {
                        Vars.saves.delete(slot);
                        setup();
                    });
                }
            })).right();

            actions.add(stopIconButton(Icon.pencil, Styles.emptyi, () -> {
                if(Vars.ui != null){
                    Vars.ui.showTextInput("$save.rename", "$save.rename.text", slot.name, text -> {
                        //刻意降级（部分）原版 slot.setName(text)：本项目 SaveSlot 只有 public name 字段，
                        //  改名只在内存里生效，要等下次写盘（slot.save() 会把 name 写进 meta 的 name tag）才持久化
                        slot.name = text;
                        setup();
                    });
                }
            })).right();

            //已接线：原版 t.addImageButton(Icon.save, Styles.emptyi, () -> platform.showFileChooser(false, saveExtension,
            //     file -> slot.exportFile(file))) —— 本项目没有 Vars.platform 门面，改用已移植的 FileChooser；
            //     slot.exportFile(file) 等价于把 slot.file 复制到目标路径（SaveSlot 有 public final File file）。
            //     ⚠ 保存模式会由 FileChooser.showFileChooser 强制补上 ".msav" 扩展名（原版 Platform.showFileChooser 的 default 行为）
            actions.add(stopIconButton(Icon.save, Styles.emptyi, () -> {
                FileChooser.showFileChooser(false, Vars.saveExtension, dest -> {
                    try{
                        java.nio.file.Files.copy(slot.file.toPath(), dest.file().toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        setup();
                    }catch(Throwable e){
                        //对应原版 ui.showException("save.export.fail", e)
                        if(Vars.ui != null) Vars.ui.showException("save.export.fail", e);
                        else e.printStackTrace();
                    }
                });
            })).right();

            title.add(actions).padRight(Scl.scl(-10f)).growX();

            button.add(title).growX().colspan(2);
            button.row();

            //缩略图（原版 BorderImage + slot.previewTexture()）
            if(slot.hasPreview()){
                try{
                    Texture texture = new Texture(Gdx.files.absolute(slot.previewFile().getAbsolutePath()));
                    textures.add(texture);
                    Image preview = new Image(texture);
                    preview.setScaling(Scaling.fit);
                    button.add(preview).left().size(Scl.scl(160f)).padRight(Scl.scl(6f));
                }catch(Exception e){
                    addNoPreview(button);
                }
            }else{
                addNoPreview(button);
            }

            //元数据列（原版 button.table(meta -> {...}).left().growX().width(250f)）
            Table meta = new Table();
            meta.left().top();
            meta.defaults().padBottom(Scl.scl(-2f)).left().width(Scl.scl(290f));
            meta.row();

            String color = "[lightgray]";
            //原版 Core.bundle.format("save.map", ...)：本项目 bundle 缺键多 → 走 I18n.get 带 fallback
            meta.add(new Label(format("save.map", color + (slot.meta == null ? I18n.get("unknown", "unknown") : slot.meta.mapname)),
                Styles.defaultLabel)).left();
            meta.row();
            //TODO 原版是 slot.mode().toString() + " /" + color + " " + format("save.wave", slot.getWave())；
            //  SaveSlot 没有 mode()（原版是 Gamemode.bestFit(meta.rules)，而本项目 meta.rules 是结构化
            //  RulesMeta、不是 Rules 对象，喂不进 Gamemode.bestFit）→ 只保留波次
            meta.add(new Label(color + format("save.wave", slot.wave()), Styles.defaultLabel)).left();
            meta.row();
            //已接线 原版按 slot.isAutosave() 显示逐槽开关（T19 给 SaveSlot 补了该字段）
            meta.add(new Label(format("save.autosave", color + I18n.get(slot.isAutosave() ? "on" : "off", slot.isAutosave() ? "on" : "off")),
                Styles.defaultLabel)).left();
            meta.row();
            meta.add(new Label(format("save.playtime", color + slot.playtimeText()), Styles.defaultLabel)).left();
            meta.row();
            meta.add(new Label(color + slot.dateText(), Styles.defaultLabel)).left();
            meta.row();

            button.add(meta).left().growX().width(Scl.scl(250f));

            modifyButton(button, slot);

            //原版 .pad(4).padRight(-4).margin(10f)：margin 用额外的 pad 顶替
            slots.add(button).uniformX().fillX().pad(Scl.scl(4f)).padRight(Scl.scl(-4f));
            slots.row();
        }

        cont.add(pane);

        addSetup();
    }

    /** 原版 {@code SaveDialog.addSetup()}（覆写 LoadDialog 的同名方法）：只加"新建存档"按钮。 */
    public void addSetup(){
        slots.row();
        slots.add(imageTextButton("$save.new", Icon.add, null, () -> {
            if(Vars.ui == null) return;
            Vars.ui.showTextInput("$save", "$save.newslot", 30, "", text -> {
                Vars.ui.loadAnd("$saving", () -> {
                    try{
                        Vars.saves.addSave(text);
                    }catch(Exception e){
                        Vars.ui.showException(e);
                    }
                    //原版 Core.app.post(() -> Core.app.post(this::setup))：连续两次 post 保证写盘/刷新之后再重排
                    Gdx.app.postRunnable(() -> Gdx.app.postRunnable(this::setup));
                });
            });
        })).fillX().pad(Scl.scl(10f)).minWidth(Scl.scl(300f)).height(Scl.scl(70f)).pad(Scl.scl(4f)).padRight(Scl.scl(-4f));
    }

    /**
     * 原版 {@code modifyButton}：点整张卡片 → 确认覆盖保存。
     * <p>原版用 {@code button.childrenPressed()} 判断"点的是不是内部子按钮"；libgdx 的 Button 没有该方法
     * → 改为让子按钮的 ClickListener 调 {@code event.stop()} 阻止冒泡（见 {@link #stopIconButton}），
     * 事件就不会传到这里。
     */
    public void modifyButton(TextButton button, SaveSlot slot){
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                if(Vars.ui != null){
                    Vars.ui.showConfirm("$overwrite", "$save.overwrite", () -> save(slot));
                }
            }
        });
    }

    void save(SaveSlot slot){
        if(Vars.ui != null && Vars.ui.loadfrag != null){
            Vars.ui.loadfrag.show("$saveload");
        }

        Time.run(5f, () -> {
            hide();
            if(Vars.ui != null && Vars.ui.loadfrag != null){
                Vars.ui.loadfrag.hide();
            }
            try{
                slot.save();
                //覆盖保存也要把它设为"当前槽"：否则之后的自动存档会继续写回上一局的槽位
                //（原版是在 SaveSlot.save() 内部做 current = this；本项目 SaveSlot 是 static 嵌套类，
                //  拿不到外层 Saves 实例，所以由调用方显式设置）
                Vars.saves.setCurrent(slot);
            }catch(Throwable e){
                e.printStackTrace();
                if(Vars.ui != null){
                    Vars.ui.showException("[accent]" + I18n.get("savefail", "savefail"), e);
                }
            }
        });
    }

    /** 加一个 nomap 占位缩略图（原版无缩略图时也用 nomap）。 */
    private void addNoPreview(Table target){
        Image image = new Image(Tex.nomap);
        image.setScaling(Scaling.fit);
        target.add(image).left().size(Scl.scl(160f)).padRight(Scl.scl(6f));
    }

    /**
     * 原版 {@code t.addImageButton(Icon.save, Styles.emptytogglei, () -> slot.setAutosave(!slot.isAutosave()))
     * .checked(slot.isAutosave())}。
     * <p>libgdx 的 {@code ImageButtonStyle} 没有 {@code imageUpColor}/{@code imageCheckedColor}（arc 的
     * {@code emptytogglei} 正是靠它们区分开关态）→ 这里手工给图标设色：未开 = 灰、已开 = 白。
     * <p>点击后额外 {@code event.stop()} 阻止事件冒泡到外层卡片（替代原版 {@code childrenPressed()}）。
     */
    private static ImageButton autosaveButton(SaveSlot slot){
        ImageButton.ImageButtonStyle bs = new ImageButton.ImageButtonStyle(Styles.emptytogglei);
        bs.imageUp = Icon.save;
        ImageButton button = new ImageButton(bs);
        button.getImage().setDrawable(Icon.save);
        //先设 checked、再挂监听：libgdx 的 setChecked 会触发 ChangeEvent，避免建按钮时白写一次设置
        button.setChecked(slot.isAutosave());
        button.getImage().setColor(slot.isAutosave() ? Color.WHITE : Color.GRAY);
        button.addListener(new ChangeListener(){
            @Override
            public void changed(ChangeEvent event, Actor actor){
                slot.setAutosave(button.isChecked());
                button.getImage().setColor(button.isChecked() ? Color.WHITE : Color.GRAY);
            }
        });
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                event.stop();
            }
        });
        return button;
    }

    /** 原版 {@code Core.bundle.format(key, args)}；本项目 bundle 缺键多 → 走 {@link I18n#get} 带 fallback。 */
    private static String format(String key, Object... args){
        return Strings.format(I18n.get(key, key), args);
    }

    /**
     * 与 {@link BansDialog#iconButton} 等价，但点击后额外 {@code event.stop()}：
     * 阻止事件冒泡到外层卡片（替代原版 {@code button.childrenPressed()} 的判定）。
     */
    private static ImageButton stopIconButton(Drawable icon, ImageButton.ImageButtonStyle style, Runnable action){
        ImageButton.ImageButtonStyle bs = new ImageButton.ImageButtonStyle(style);
        bs.imageUp = icon;
        ImageButton button = new ImageButton(bs);
        button.getImage().setDrawable(icon);
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                event.stop();
                action.run();
            }
        });
        return button;
    }
}
