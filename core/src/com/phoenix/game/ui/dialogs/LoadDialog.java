package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Strings;
import com.phoenix.game.core.Time;
import com.phoenix.game.game.Saves.SaveSlot;
import com.phoenix.game.ui.BorderImage;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.util.Log;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * 读档对话框（列出全部存档槽，点卡片读档；支持删除 / 改名 / 导入）。
 * 参照 Mindustry mindustry.ui.dialogs.LoadDialog。
 *
 * <p><b>与原版 {@code SaveDialog extends LoadDialog} 的关系</b>：原版 {@code SaveDialog} 继承本类复用槽位列表；
 * 本项目的 {@code SaveDialog} 已经做成了独立的 {@code extends FloatingDialog}（上一批），
 * <b>本类同样独立实现</b>，两者暂不建立继承关系（统一留到后续批次决定是否收敛）。
 * 因此本类只搬 LoadDialog 自己的部分（读档按钮 / 导入），不搬 SaveDialog 的"新建存档"。
 *
 * <p><b>与本项目的差异（逐条对应原版写法，A=已接线 / B=缺依赖只能降级 / C=等价替代或刻意降级）</b>
 * <ul>
 *   <li><b>A 已接线</b> {@code ui.paused.hide()}（{@code Vars.ui.paused} 存在）、
 *       {@code state.rules.editor = false} / {@code state.rules.zone = null}
 *       （{@code Rules.editor}/{@code Rules.zone} 两个字段都存在）→ 都在 {@link #runLoadSave} 里接上（判空）。</li>
 *   <li><b>B 缺依赖</b> {@code net.reset()}：{@code net/Net} 没有 {@code reset()}，且 <b>{@code Vars.net} 恒为 null</b>
 *       （传输层实例由 {@code netServer}/{@code netClient} 各自持有，见 {@code net/CrashSender} 的注释）
 *       → 门面级 reset() 无意义，不接线。</li>
 *   <li><b>C 等价替代</b> {@code control.saves.getSaveSlots()}：本项目是 {@code Vars.saves.getSlots()}（同义），
 *       但 <b>没有 {@code getZoneSlot()}</b>（战役区域槽，B）→ 本类用不到，{@code DeployDialog} 里已降级。</li>
 *   <li><b>B 缺依赖</b> {@code slot.isHidden()}（隐藏槽位）：{@link SaveSlot} 没有该成员 → 不跳过任何槽位。</li>
 *   <li><b>B 缺依赖</b> {@code slot.setAutosave/isAutosave()}（逐槽自动存档开关）：{@link SaveSlot} 没有该成员 →
 *       卡片标题右侧的"自动存档"切换按钮整段去掉（全局开关在 {@code Vars.saves.autosave}）。</li>
 *   <li><b>B 缺依赖</b> {@code slot.setName(text)}（改名并写回）：本项目只有 public {@code name} 字段，
 *       改名只在内存里生效，要等下次写盘才随 meta 的 name tag 持久化（与 {@code SaveDialog} 同）。</li>
 *   <li><b>C 等价替代</b> {@code slot.previewTexture()} / {@code slot.getZone()} / {@code slot.mode()} /
 *       {@code slot.getTimestamp()} / {@code slot.getPlayTime()} / {@code slot.getDate()} /
 *       {@code slot.getWave()} / {@code slot.getMap()}：本项目 {@link SaveSlot} 对应的是
 *       {@code previewFile()/dateMillis()/playtimeText()/dateText()/wave()/meta.mapname} →
 *       逐处替换；没有 mode() 与 getZone()（B）→ 对应行降级。</li>
 *   <li><b>B 缺依赖</b> {@code slot.exportFile(file)} / {@code control.saves.importSave(file)} /
 *       {@code SaveIO.isSaveValid(file)} / {@code platform.showFileChooser(...)} / {@code platform.shareFile(...)}：
 *       本项目没有 {@code Vars.platform}（{@code core/Platform} 是无实例的死接口）、
 *       {@code Saves} 没有 {@code importSave}、{@code SaveIO} 里叫 {@code isValid(File)} →
 *       导出/导入两个按钮降级为"日志"。</li>
 *   <li><b>B 缺依赖</b> {@code slot.cautiousLoad(...)}（先试读，损坏则回退备份）：{@link SaveSlot} 没有该方法 →
 *       直接走 {@code Vars.ui.loadAnd} + {@code Vars.control.load(slot)}（后者自带异常捕获与打印）。</li>
 *   <li><b>B 缺依赖</b> {@code SaveException}（原版 {@code mindustry.io.SaveIO.SaveException}）未移植 →
 *       原版 {@code catch(SaveException)} → {@code ui.showErrorMessage("$save.corrupted")} 那一段，
 *       本批（R2）改由 {@code Vars.control.load} 的**宽 catch** 承担：失败时 {@code menu()} + 同一条
 *       {@code $save.corrupted} 提示（这样用户点了旧档/坏档会看到提示，而不是"点了没反应"）。</li>
 *   <li><b>C 刻意降级</b> 缩略图：原版用 {@code slot.previewTexture()} 动态换图 + {@code Cell.update(...)}
 *       检查纹理是否被释放；本项目只有 {@code previewFile()} → 打开时 {@code new Texture(...)} 加载，
 *       并在 {@link #hide()} 里统一 dispose（与 {@code SaveDialog} 同做法，否则每开一次对话框就漏一张 GL 纹理）。
 *       动态回退 nomap 的那段 {@code update(...)} 去掉（libgdx 无每帧回调）。</li>
 *   <li><b>C 等价替代</b> arc 的 {@code Cell.margin(...)}（外间距）libgdx 没有 → 用额外的 {@code pad} 顶替；
 *       {@code Core.app.post(...)} → {@code Gdx.app.postRunnable(...)}；{@code Core.scene} → {@link FloatingDialog#scene}；
 *       {@code ui.showConfirm/showTextInput/showException/loadAnd} 本项目的 {@code Vars.ui} 都有。</li>
 * </ul>
 */
public class LoadDialog extends FloatingDialog{
    ScrollPane pane;
    Table slots;

    /** 本对话框加载的缩略图纹理，关闭时统一释放。 */
    private final Array<Texture> textures = new Array<>();

    public LoadDialog(){
        this("$loadgame");
    }

    public LoadDialog(String title){
        super(title);
        setup();

        shown(() -> {
            setup();
            //原版 Time.runTask(2f, () -> Core.scene.setScrollFocus(pane))
            Time.run(2f, () -> {
                if(scene != null) scene.setScrollFocus(pane);
            });
        });

        addCloseButton();
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
            //刻意降级（B）：原版 if(slot.isHidden()) continue;（SaveSlot 没有 isHidden() → 不跳过）

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

            //刻意降级（B）：原版"自动存档开关"（Icon.save + Styles.emptytogglei + slot.setAutosave/isAutosave）：
            //  SaveSlot 没有逐槽开关 → 去掉该按钮

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
                        //刻意降级（B）：原版 slot.setName(text)：本项目 SaveSlot 只有 public name 字段，
                        //  改名只在内存里生效，要等下次写盘才随 meta 的 name tag 持久化
                        slot.name = text;
                        setup();
                    });
                }
            })).right();

            //刻意降级（B）：原版"导出存档"按钮（Icon.save + platform.showFileChooser + slot.exportFile）：
            //  platform 的文件选择器未移植、SaveSlot 没有 exportFile → 去掉该按钮

            title.add(actions).padRight(Scl.scl(-10f)).growX();

            button.add(title).growX().colspan(2);
            button.row();

            //缩略图（原版 BorderImage + slot.previewTexture()）
            button.add(buildPreview(slot)).left().size(Scl.scl(160f)).padRight(Scl.scl(6f));

            //元数据列（原版 button.table(meta -> {...}).left().growX().width(250f)）
            Table meta = new Table();
            meta.left().top();
            meta.defaults().padBottom(Scl.scl(-2f)).left().width(Scl.scl(290f));
            meta.row();

            String color = "[lightgray]";
            //原版 Core.bundle.format("save.map", ...)：本项目 bundle 缺键多 → 走 I18n.get 带 fallback
            Label mapLabel = new Label(format("save.map", color + (slot.meta == null ? I18n.get("unknown", "unknown") : slot.meta.mapname)),
                Styles.defaultLabel);
            mapLabel.setWrap(true);
            meta.add(mapLabel).left();
            meta.row();
            //刻意降级（B）：原版是 slot.mode().toString() + " /" + color + " " + format("save.wave", slot.getWave())；
            //  SaveSlot 没有 mode() → 只保留波次
            Label waveLabel = new Label(color + format("save.wave", slot.wave()), Styles.defaultLabel);
            waveLabel.setWrap(true);
            meta.add(waveLabel).left();
            meta.row();
            //刻意降级（B）：原版按 slot.isAutosave() 显示逐槽开关；本项目只有全局 Vars.saves.autosave → 用它
            Label autosaveLabel = new Label(format("save.autosave",
                color + I18n.get(Vars.saves.autosave ? "on" : "off", Vars.saves.autosave ? "on" : "off")), Styles.defaultLabel);
            autosaveLabel.setWrap(true);
            meta.add(autosaveLabel).left();
            meta.row();
            Label playtimeLabel = new Label(format("save.playtime", color + slot.playtimeText()), Styles.defaultLabel);
            playtimeLabel.setWrap(true);
            meta.add(playtimeLabel).left();
            meta.row();
            Label dateLabel = new Label(color + slot.dateText(), Styles.defaultLabel);
            dateLabel.setWrap(true);
            meta.add(dateLabel).left();
            meta.row();

            button.add(meta).left().growX().width(Scl.scl(250f));

            modifyButton(button, slot);

            //原版 .pad(4).padRight(-4).margin(10f)：margin 用额外的 pad 顶替（与 SaveDialog 同口径）
            slots.add(button).uniformX().fillX().pad(Scl.scl(4f)).padRight(Scl.scl(-4f));
            slots.row();
        }

        cont.add(pane);

        addSetup();
    }

    /** 原版 {@code LoadDialog.addSetup()}：空列表占位 + "导入存档"按钮。 */
    public void addSetup(){
        //刻意降级（B）：原版 for(SaveSlot slot : control.saves.getSaveSlots()) if(!slot.isHidden()) valids = true;
        //  —— SaveSlot 没有 isHidden() → 有槽位就算有效
        boolean valids = Vars.saves.getSlots().size > 0;

        if(!valids){
            slots.row();
            //原版 slots.addButton("$save.none", () -> {}).disabled(true)：libgdx 的 Cell 没有 disabled(Boolp)
            //→ 建按钮后 setDisabled(true)
            TextButton none = textButton("$save.none", null, () -> {
            });
            none.setDisabled(true);
            slots.add(none).fillX().pad(Scl.scl(20f)).minWidth(Scl.scl(340f)).height(Scl.scl(80f)).pad(Scl.scl(4f));
        }

        slots.row();

        slots.add(imageTextButton("$save.import", Icon.add, null, this::importSave))
            .fillX().pad(Scl.scl(10f)).minWidth(Scl.scl(300f)).height(Scl.scl(70f)).pad(Scl.scl(4f)).padRight(Scl.scl(-4f));
    }

    /**
     * 刻意降级（B）：原版：{@code platform.showFileChooser(true, saveExtension, file -> {
     *     if(SaveIO.isSaveValid(file)) control.saves.importSave(file); else ui.showErrorMessage("$save.import.invalid"); });}
     * <p>本项目没有 {@code Vars.platform}（{@code core/Platform} 是无实例的死接口）、
     * {@code Saves} 也没有 {@code importSave(file)}、{@code SaveIO} 里叫 {@code isValid(File)}
     * → 按钮只记一条日志。
     */
    private void importSave(){
        Log.warn("import save not ported (B): Platform.showFileChooser / Saves.importSave missing.");
    }

    /**
     * 读档（对应原版 {@code runLoadSave}）。
     * <p>刻意降级（B）：原版 {@code slot.cautiousLoad(...)}（先试读，损坏时回退备份）——{@link SaveSlot} 没有该方法
     * → 直接走 {@code Vars.ui.loadAnd} + {@code Vars.control.load(slot)}。
     */
    public void runLoadSave(SaveSlot slot){
        if(Vars.ui == null){
            Vars.control.load(slot);
            return;
        }
        Vars.ui.loadAnd(() -> {
            hide();
            //原版 ui.paused.hide()（本项目 Vars.ui.paused 存在 → 已接线；判空）
            if(Vars.ui != null && Vars.ui.paused != null){
                Vars.ui.paused.hide();
            }
            //刻意降级（B）：原版 net.reset()（closeServer + 客户端断开）。本项目 net/Net 没有 reset()，
            //  且 Vars.net 恒为 null（传输层实例由 netServer/netClient 各自持有，见 net/CrashSender 的注释）
            //  → 门面级 reset() 无意义，不接线。
            Vars.control.load(slot);
            //原版 state.rules.editor = false / state.rules.zone = null（Rules 两个字段都存在 → 已接线；判空）
            if(Vars.state != null){
                Vars.state.rules.editor = false;
                Vars.state.rules.zone = null;
            }
        });
    }

    /**
     * 原版 {@code modifyButton}：点整张卡片 → 读档。
     * <p>原版用 {@code button.childrenPressed()} 判断"点的是不是内部子按钮"；libgdx 的 Button 没有该方法
     * → 改为让子按钮的 ClickListener 调 {@code event.stop()} 阻止冒泡（见 {@link #stopIconButton}）。
     */
    public void modifyButton(TextButton button, SaveSlot slot){
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                runLoadSave(slot);
            }
        });
    }

    /** 缩略图：有预览图就加载纹理（关闭时统一 dispose），否则用 nomap 占位。 */
    private BorderImage buildPreview(SaveSlot slot){
        if(slot.hasPreview()){
            try{
                Texture texture = new Texture(Gdx.files.absolute(slot.previewFile().getAbsolutePath()));
                textures.add(texture);
                BorderImage image = new BorderImage(texture, 4f);
                image.setScaling(Scaling.fit);
                return image;
            }catch(Exception ignored){
                //加载失败 → 落到下面的 nomap
            }
        }
        BorderImage image = new BorderImage(Tex.nomap.getRegion(), 4f);
        image.setScaling(Scaling.fit);
        return image;
    }

    /** 原版 {@code Core.bundle.format(key, args)}；本项目 bundle 缺键多 → 走 {@link I18n#get} 带 fallback。 */
    private static String format(String key, Object... args){
        return Strings.format(I18n.get(key, key), args);
    }

    /**
     * 与 {@code SaveDialog.stopIconButton} 等价：点击后额外 {@code event.stop()}，
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
