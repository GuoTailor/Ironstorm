package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Strings;
import com.phoenix.game.game.Schematic;
import com.phoenix.game.game.Schematics;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.util.Log;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * 蓝图管理对话框（列出本地 .msch，导入/导出，点一下就能用）。参照 Mindustry mindustry.ui.dialogs.SchematicsDialog。
 *
 * <p><b>与本项目的差异（逐条对应原版写法）</b>：
 * <ul>
 *   <li>原版构造器里 {@code Core.assets.load("sprites/schematic-background.png", Texture.class).loaded = ...}：
 *       libgdx 的 {@code AssetManager.load()} 返回 void（没有 {@code .loaded} 字段），且本项目 assets 里没有该贴图
 *       → 整段去掉，{@link SchematicImage} 的底图也不画（刻意降级）。</li>
 *   <li>arc 的 {@code Table.margin(...) / addImage(...) / addButton(Cons, Runnable) / pane(Cons) / table(Cons) /
 *       label(Prov) / Cell.wrap() / Cell.color()} libgdx 都没有 → 分别用 {@code pad}、{@code add(new Image(...))}、
 *       自建 Button、自建 ScrollPane/Table、Label+setWrap、颜色设在 Actor 上。</li>
 *   <li>{@code ui.showTextInput / showInfo / showConfirm / showErrorMessage / showInfoFade}：本项目<b>已存在</b>
 *       （{@code core/UI.java}）→ 重命名 / 删除确认 / 导入提示 / 复制提示均已接线（不再是日志占位）。</li>
 *   <li>{@code Schematics.readBase64 / writeBase64 / remove}：三者<b>均已存在</b>
 *       （readBase64 早先已补，writeBase64/remove 本批追加）→ "从剪贴板导入" / "复制到剪贴板" / "删除蓝图" 均已接线。</li>
 *   <li><b>文件导入 / 导出已接线</b>：原版走 {@code platform.showFileChooser(...)}，本项目没有
 *       {@code Vars.platform} 门面（只有 {@code core/Platform} 接口、无实现类）→ 改用已移植的
 *       {@link FileChooser}（等价替代）。</li>
 *   <li>刻意降级：{@code platform.openWorkshop / publish / shareFile / viewListing} 与 {@code steam} 分支
 *       （{@code Vars.steam} 有，但没有 {@code Vars.platform} 门面）→ 创意工坊入口仍只记日志。</li>
 *   <li>{@code s.mod}：本项目蓝图不关联 mod（无 {@code LoadedMod} 关联）→ 删除分支直接走 {@code showConfirm}
 *       （原版 {@code s.mod != null} 分支恒不成立）。{@code s.hasSteamID() / s.removeSteamID() / s.save()}
 *       本批已补（见 {@link com.phoenix.game.game.Schematic}）。</li>
 *   <li>{@code control.input.useSchematic(s)}：本项目没有该方法，但有等价字段
 *       {@code InputHandler.pasteSchematic} → 已改走它（{@code Vars.control.input.pasteSchematic = sch}）。</li>
 *   <li>刻意降级：{@code Icon.download / Icon.export / Icon.book / Icon.ok / Icon.left / Icon.upOpen / Icon.downOpen}
 *       本项目图集里没有 → 分别用 {@code Icon.arrowDown / Icon.arrowUp / Icon.database / Icon.check / Icon.arrowLeft} 代替。</li>
 * </ul>
 */
public class SchematicsDialog extends FloatingDialog{
    private SchematicInfoDialog info = new SchematicInfoDialog();
    private String search = "";

    public SchematicsDialog(){
        super("$schematics");

        //刻意降级：原版：Core.assets.load("sprites/schematic-background.png", Texture.class).loaded = t -> ((Texture)t).setWrap(TextureWrap.Repeat);
        //  —— libgdx 的 AssetManager.load() 返回 void；本项目也没有这张贴图 → 去掉

        shouldPause = true;
        addCloseButton();
        buttons.add(imageTextButton("$schematic.import", Icon.arrowDown, null, this::showImport));
        shown(this::setup);
        onResize(this::setup);
    }

    void setup(){
        search = "";
        Runnable[] rebuildPane = {null};

        cont.top();
        cont.clear();

        //原版 cont.table(s -> { s.left(); s.addImage(Icon.zoom); s.addField(search, res -> {...}).growX(); }).fillX().padBottom(4)
        Table s = new Table();
        s.left();
        s.add(new Image(Icon.zoom)).padRight(Scl.scl(4f));

        //arc 的 addField(text, cons) → libgdx 用 TextField + ChangeListener
        TextField field = new TextField(search, Styles.defaultField);
        field.addListener(new ChangeListener(){
            @Override
            public void changed(ChangeEvent event, Actor actor){
                search = field.getText();
                if(rebuildPane[0] != null) rebuildPane[0].run();
            }
        });
        s.add(field).growX();

        cont.add(s).fillX().padBottom(Scl.scl(4f));

        cont.row();

        //原版 cont.pane(t -> {...}).get().setScrollingDisabled(true, false)：libgdx 没有 pane(Cons) → 自建
        Table t = new Table();
        t.top();
        //刻意降级：原版 t.margin(20f)（arc 的外间距）→ libgdx 没有 margin，用 pad 代替
        t.pad(Scl.scl(20f));
        ScrollPane pane = new ScrollPane(t, Styles.defaultPane);
        cont.add(pane).grow();

        rebuildPane[0] = () -> {
            t.clear();
            int i = 0;

            //原版 Vars.schematics.all().contains(sch -> ...)：libgdx 的 Array 没有 contains(Boolf)
            //（只有 contains(T, boolean)，lambda 套不上）→ 用显式下标循环
            boolean any = false;
            for(int k = 0; k < Vars.schematics.all().size; k++){
                Schematic sch = Vars.schematics.all().get(k);
                if(search.isEmpty() || sch.name().toLowerCase().contains(search.toLowerCase())){
                    any = true;
                    break;
                }
            }

            if(!any){
                t.add(new Label(bundle("$none"), Styles.defaultLabel));
            }

            for(int k = 0; k < Vars.schematics.all().size; k++){
                Schematic sch = Vars.schematics.all().get(k);
                if(!search.isEmpty() && !sch.name().toLowerCase().contains(search.toLowerCase())) continue;

                Button button = buildSchematicButton(sch, rebuildPane);
                //原版 t.addButton(...).pad(4).style(Styles.cleari).get()：libgdx 没有 addButton(Cons, Runnable)
                t.add(button).pad(Scl.scl(4f));
                //原版 sel[0].getStyle().up = Tex.pane（样式已在 buildSchematicButton 里做了拷贝，不会污染全局样式）
                button.getStyle().up = Tex.pane;

                if(++i % (Vars.mobile ? (isPortrait() ? 2 : 3) : 4) == 0){
                    t.row();
                }
            }
        };

        rebuildPane[0].run();
        pane.setScrollingDisabled(true, false);
    }

    /** 拼出一张蓝图卡片（顶部 4 个操作按钮 + 预览图 + 名称条）。 */
    private Button buildSchematicButton(Schematic sch, Runnable[] rebuildPane){
        //样式必须拷贝：原版 sel[0].getStyle().up = Tex.pane 是改实例样式，直接用 Styles.cleari 会污染全局
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle(Styles.clearPartiali);
        Button button = new Button(new ImageButton.ImageButtonStyle(Styles.cleari));
        button.top();
        //刻意降级：原版 b.margin(0f)（arc 的外间距）→ libgdx 没有 margin，用 pad
        button.pad(0f);

        Table buttons = new Table();
        buttons.left();
        buttons.defaults().size(Scl.scl(50f));

        //原版 buttons.addImageButton(Icon.info, style, () -> showInfo(s))
        buttons.add(iconButton(Icon.info, style, () -> showInfo(sch)));

        //刻意降级：原版 Icon.download（本项目图集没有，已查生成的 Icon.java）→ 用 Icon.arrowDown 代替
        buttons.add(iconButton(Icon.arrowDown, style, () -> showExport(sch)));

        //重命名：对应原版 ui.showTextInput("$schematic.rename", "$name", s.name(), res -> {...})
        //（重名不允许 —— 无法确定 tags 该如何合并、该删哪一个）
        buttons.add(iconButton(Icon.pencil, style, () -> {
            if(Vars.ui == null) return;
            Vars.ui.showTextInput("$schematic.rename", "$name", sch.name(), res -> {
                for(int i = 0; i < Vars.schematics.all().size; i++){
                    Schematic other = Vars.schematics.all().get(i);
                    if(other != sch && other.name().equals(res)){
                        Vars.ui.showErrorMessage("$schematic.exists");
                        return;
                    }
                }

                sch.tags.put("name", res);
                sch.save();
                rebuildPane[0].run();
            });
        }));

        //删除：对应原版 if(s.hasSteamID()){ platform.viewListing(s) } else { if(s.mod != null) ui.showInfo(...) else ui.showConfirm(...) }
        //  —— 本项目无 Steam 发布路径、蓝图也不关联 mod → 直接走 showConfirm 分支
        buttons.add(iconButton(Icon.trash, style, () -> {
            if(Vars.ui == null) return;
            Vars.ui.showConfirm("$confirm", "$schematic.delete.confirm", () -> {
                Vars.schematics.remove(sch);
                rebuildPane[0].run();
            });
        }));

        button.add(buttons).growX().height(Scl.scl(50f));
        button.row();

        //原版 b.stack(new SchematicImage(s).setScaling(Scaling.fit), new Table(n -> {...})).size(200f)
        Table overlay = new Table();
        overlay.top();
        Table box = new Table();
        box.setBackground(Styles.black3);
        Label label = new Label(sch.name(), Styles.outlineLabel);
        label.setColor(Color.WHITE);
        label.setEllipsis(true);
        label.setAlignment(Align.center);
        box.add(label).top().growX().maxWidth(Scl.scl(200f - 8f));
        //刻意降级：原版 .growX().margin(1).pad(4).maxWidth(...).padBottom(0)（margin 是 arc 的外间距，libgdx 没有）
        overlay.add(box).growX().pad(Scl.scl(1f)).pad(Scl.scl(4f)).maxWidth(Scl.scl(200f - 8f)).padBottom(0f);

        button.stack(new SchematicImage(sch), overlay).size(Scl.scl(200f));

        //原版整块按钮的点击：先判 childrenPressed()（避免点小按钮时也触发），再按状态决定打开详情还是贴蓝图
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                if(childrenPressed(button)) return;
                if(Vars.state.isMenu()){
                    showInfo(sch);
                }else{
                    //对应原版 control.input.useSchematic(s)：本项目没有该方法，但 InputHandler.pasteSchematic
                    //就是它的等价状态（DesktopInput 会据它逐 Stile 放置）
                    Vars.control.input.pasteSchematic = sch;
                    hide();
                }
            }
        });

        return button;
    }

    /** 对应 arc 的 {@code Element.childrenPressed()}：是否有子元素吃掉了这次按下。 */
    private static boolean childrenPressed(Actor actor){
        if(!(actor instanceof Group)) return false;
        Group group = (Group)actor;
        for(int i = 0; i < group.getChildren().size; i++){
            Actor child = group.getChildren().get(i);
            if(child instanceof Button && ((Button)child).isPressed()) return true;
            if(childrenPressed(child)) return true;
        }
        return false;
    }

    /**
     * 图标按钮。对应 arc 的 {@code Table.addImageButton(icon, style, action)}。
     * <p>注意：回调里 {@code event.stop()} 阻止冒泡 —— 否则点小按钮会连带触发外层"整块蓝图"的点击
     * （libgdx 的 {@code ClickListener} 不会自动停事件，这正是原版 {@code childrenPressed()} 要解决的问题）。
     */
    private static ImageButton iconButton(com.badlogic.gdx.scenes.scene2d.utils.Drawable icon,
                                          ImageButton.ImageButtonStyle style, Runnable action){
        //libgdx 的 ImageButton 没有 (Drawable, ImageButtonStyle) 构造器 → 手工设 imageUp
        //（必须设 imageUp：否则 hover/按下状态一变，updateImage() 会把 drawable 重置为 null）
        ImageButton.ImageButtonStyle bs = new ImageButton.ImageButtonStyle(style);
        bs.imageUp = icon;
        ImageButton button = new ImageButton(bs);
        //ImageButton(style) 构造器不会把 imageUp 应用到内部 Image，要手动设一次
        button.getImage().setDrawable(icon);
        button.addListener(new ClickListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button1){
                boolean result = super.touchDown(event, x, y, pointer, button1);
                event.stop();
                return result;
            }

            @Override
            public void clicked(InputEvent event, float x, float y){
                action.run();
            }
        });
        return button;
    }

    public void showInfo(Schematic schematic){
        info.show(schematic);
    }

    public void showImport(){
        FloatingDialog dialog = new FloatingDialog("$editor.export");

        //原版 dialog.cont.pane(p -> {...})：libgdx 没有 pane(Cons)
        Table p = new Table();
        //刻意降级：原版 p.margin(10f)（arc 的外间距）→ libgdx 没有 margin，用 pad 代替
        p.pad(Scl.scl(10f));
        Table t = new Table();
        t.setBackground(Tex.button);
        t.defaults().size(Scl.scl(280f), Scl.scl(60f)).left();
        t.row();

        //从剪贴板导入：对应原版 Schematics.readBase64(Core.app.getClipboardText()) → removeSteamID → add → setup → showInfoFade → showInfo
        //  —— libgdx 的剪贴板走 Gdx.app.getClipboard()
        String clipboard = Gdx.app.getClipboard().getContents();
        TextButton copy = imageTextButton("$schematic.copy.import", Icon.copy, Styles.cleart, () -> {
            dialog.hide();
            try{
                Schematic s = Schematics.readBase64(Gdx.app.getClipboard().getContents());
                s.removeSteamID();
                Vars.schematics.add(s);
                setup();
                if(Vars.ui != null) Vars.ui.showInfoFade("$schematic.saved");
                showInfo(s);
            }catch(Throwable e){
                if(Vars.ui != null) Vars.ui.showException(e);
                else Log.err(e);
            }
        });
        //刻意降级：原版 .disabled(b -> clipboard 为空或不以 schematicBaseStart 开头)（arc 的 Cell 动态禁用）
        //  —— libgdx 的 Cell 没有动态禁用 → 按打开对话框时的剪贴板内容定一次
        copy.setDisabled(clipboard == null || !clipboard.startsWith(Schematics.base64Header));
        t.add(copy).padLeft(Scl.scl(12f));
        t.row();

        //已接线：原版 platform.showFileChooser(true, schematicExtension, file -> {...})
        //  —— 本项目没有 Vars.platform 门面（只有 core/Platform 接口、无实现类），改用已移植的
        //     {@link FileChooser}（等价的本项目替代；Schematics.read(File) 本身是有的）
        TextButton fileImport = imageTextButton("$schematic.importfile", Icon.arrowDown, Styles.cleart, () -> {
            dialog.hide();
            FileChooser.showFileChooser(true, Schematics.schematicExtension, file -> {
                try{
                    //Schematics.read(File) 是实例方法（与 InputStream 的静态重载不同）→ 走 Vars.schematics
                    Schematic s = Vars.schematics.read(file.file());
                    s.removeSteamID();
                    Vars.schematics.add(s);
                    setup();
                    showInfo(s);
                }catch(Throwable e){
                    if(Vars.ui != null) Vars.ui.showException(e);
                    else Log.err(e);
                }
            });
        });
        t.add(fileImport).padLeft(Scl.scl(12f));
        t.row();

        if(Vars.steam){
            //刻意降级：原版 t.addImageTextButton("$schematic.browseworkshop", Icon.book, style, () -> { dialog.hide(); platform.openWorkshop(); })
            //  —— Vars.platform 不存在 → 禁用占位（Icon.book 本项目图集也没有）
            TextButton workshop = imageTextButton("$schematic.browseworkshop", Icon.database, Styles.cleart, () -> {
                dialog.hide();
                Log.info("TODO open workshop (Vars.platform.openWorkshop 不存在)");
            });
            workshop.setDisabled(true);
            t.add(workshop).padLeft(Scl.scl(12f));
        }

        p.add(t);
        dialog.cont.add(new ScrollPane(p, Styles.defaultPane)).grow();
        dialog.addCloseButton();
        dialog.show();
    }

    public void showExport(Schematic s){
        FloatingDialog dialog = new FloatingDialog("$editor.export");

        Table p = new Table();
        //刻意降级：原版 p.margin(10f)（arc 的外间距）→ libgdx 没有 margin，用 pad 代替
        p.pad(Scl.scl(10f));
        Table t = new Table();
        t.setBackground(Tex.button);
        t.defaults().size(Scl.scl(280f), Scl.scl(60f)).left();

        //对应原版条件 steam && !s.hasSteamID()（hasSteamID 本批已补，见 Schematic）
        if(Vars.steam && !s.hasSteamID()){
            //刻意降级：原版 t.addImageTextButton("$schematic.shareworkshop", Icon.book, style, () -> platform.publish(s))
            //  —— Vars.platform 不存在 → 禁用占位（Icon.book 本项目图集也没有）
            TextButton share = imageTextButton("$schematic.shareworkshop", Icon.database, Styles.cleart, () -> {
                Log.info("TODO publish schematic to workshop (Vars.platform.publish 不存在)");
            });
            share.setDisabled(true);
            t.add(share).padLeft(Scl.scl(12f));
            t.row();
            dialog.hide();
        }

        //复制到剪贴板：对应原版 Core.app.setClipboardText(schematics.writeBase64(s)) + ui.showInfoFade("$copied")
        //  —— libgdx 的剪贴板走 Gdx.app.getClipboard()
        TextButton copy = imageTextButton("$schematic.copy", Icon.copy, Styles.cleart, () -> {
            dialog.hide();
            if(Vars.ui != null) Vars.ui.showInfoFade("$copied");
            Gdx.app.getClipboard().setContents(Schematics.writeBase64(s));
        });
        t.add(copy).padLeft(Scl.scl(12f));
        t.row();

        //已接线：原版非 ios 走 platform.showFileChooser(false, schematicExtension, file -> Schematics.write(s, file))；
        //  —— 本项目没有 Vars.platform 门面，改用已移植的 {@link FileChooser}（等价替代；Schematics.write 本身是有的）。
        //  ⚠ 保存模式会由 FileChooser.showFileChooser 强制补上 ".msch" 扩展名（原版 Platform.showFileChooser 的 default 行为）。
        //  原版的 ios 分支（写 local + platform.shareFile）归 B 类：本项目没有 shareFile。
        TextButton export = imageTextButton("$schematic.exportfile", Icon.arrowUp, Styles.cleart, () -> {
            dialog.hide();
            FileChooser.showFileChooser(false, Schematics.schematicExtension, file -> {
                try{
                    Schematics.write(s, file.file());
                }catch(Throwable e){
                    //对应原版 ui.showException(e)
                    if(Vars.ui != null) Vars.ui.showException(e);
                    else Log.err(e);
                }
            });
        });
        t.add(export).padLeft(Scl.scl(12f));

        p.add(t);
        dialog.cont.add(new ScrollPane(p, Styles.defaultPane)).grow();
        dialog.addCloseButton();
        dialog.show();
    }

    /** @return 是否竖屏（对应 arc 的 {@code Core.graphics.isPortrait()}，libgdx 的 Graphics 没有该方法）。 */
    private static boolean isPortrait(){
        return Core.graphics.getHeight() > Core.graphics.getWidth();
    }

    /**
     * 蓝图预览图 + 描边。参照 Mindustry {@code SchematicsDialog.SchematicImage}。
     *
     * <p><b>本项目的偏离</b>：
     * <ul>
     *   <li>刻意降级：原版底图是平铺的 {@code sprites/schematic-background.png}（{@code Draw.wrap} + 改 u2/v2 +
     *       {@code Draw.rect}）—— 本项目没有这张贴图，且 Stage 内的绘制不能借用世界相机的 {@code Draw}
     *       （见 {@code ui/BorderImage} 的说明）→ 底图整段跳过。</li>
     *   <li>原版 {@code Lines.rect(...)} 画描边同理不可用 → 用 batch 铺 4 条白边（与 {@code ui/BorderImage} 一致）。</li>
     *   <li>原版 {@code Core.app.post(this::setPreview)} → 用 libgdx 的 {@code Core.app.postRunnable}。</li>
     * </ul>
     */
    public static class SchematicImage extends Image{
        public float scaling = 16f;
        public float thickness = 4f;
        public Color borderColor = Pal.gray;

        private Schematic schematic;
        boolean set;

        public SchematicImage(Schematic s){
            super(Tex.clear);
            setScaling(Scaling.fit);
            schematic = s;

            if(Vars.schematics.hasPreview(s)){
                setPreview();
                set = true;
            }
        }

        @Override
        public void draw(Batch batch, float parentAlpha){
            boolean checked = getParent() != null && getParent().getParent() instanceof Button
                && ((Button)getParent().getParent()).isOver();

            boolean wasSet = set;
            if(!set){
                Core.app.postRunnable(this::setPreview);
                set = true;
            }

            if(wasSet){
                super.draw(batch, parentAlpha);
            }else{
                //原版：Draw.rect(Icon.refresh.getRegion(), x + width/2f, y + height/2f, width/4f, height/4f)
                //Stage 子 Actor 的 draw 用的是父 Group 局部坐标 → 自己加上 getX()/getY()
                batch.setColor(1f, 1f, 1f, parentAlpha);
                batch.draw(Icon.refresh.getRegion(),
                    getX() + getWidth() / 2f - getWidth() / 8f,
                    getY() + getHeight() / 2f - getHeight() / 8f,
                    getWidth() / 4f, getHeight() / 4f);
            }

            //原版：Draw.color(checked ? Pal.accent : borderColor); Draw.alpha(parentAlpha);
            //      Lines.stroke(Scl.scl(thickness)); Lines.rect(x, y, width, height); Draw.reset();
            //  —— Lines 在 Stage 里会错位 → 用 batch 铺 4 条白边
            Color color = checked ? Pal.accent : borderColor;
            float t = Scl.scl(thickness);
            float x = getX(), y = getY(), w = getWidth(), h = getHeight();
            TextureRegion white = Tex.whiteui.getRegion();
            batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
            batch.draw(white, x, y, w, t);
            batch.draw(white, x, y + h - t, w, t);
            batch.draw(white, x, y + t, t, h - t * 2f);
            batch.draw(white, x + w - t, y + t, t, h - t * 2f);
            batch.setColor(Color.WHITE);
        }

        private void setPreview(){
            //本项目 getPreview 直接返回 Texture（原版返回 TextureRegion）
            Texture preview = Vars.schematics.getPreview(schematic);
            if(preview == null) return;
            setDrawable(new TextureRegionDrawable(new TextureRegion(preview)));
            setScaling(Scaling.fit);
        }
    }

    /** 蓝图详情（尺寸、预览大图、材料清单）。参照 Mindustry {@code SchematicsDialog.SchematicInfoDialog}。 */
    public static class SchematicInfoDialog extends FloatingDialog{

        SchematicInfoDialog(){
            super("");
            setFillParent(true);
            addCloseButton();
        }

        public void show(Schematic schem){
            cont.clear();
            //原版 title.setText("[[" + Core.bundle.get("schematic") + "] " + schem.name())：
            //  "[[...]" 是 arc 的转义写法；本项目标题是原生 Label（不解析 markup），用 resolve 剥掉标记
            title.setText("[" + resolve(bundle("$schematic")) + "] " + schem.name());

            //原版 Core.bundle.format("schematic.info", ...)（缺键会抛）→ 走 I18n + Strings.format
            Label infoLabel = new Label(Strings.format(I18n.get("schematic.info", "schematic.info"),
                schem.width, schem.height, schem.tiles.size), Styles.defaultLabel);
            infoLabel.setColor(Color.LIGHT_GRAY);
            cont.add(infoLabel);
            cont.row();
            cont.add(new SchematicImage(schem)).maxSize(Scl.scl(800f));
            cont.row();

            Table r = new Table();
            ItemStack[] arr = schem.requirements().toArray(ItemStack.class);
            int i = 0;
            for(ItemStack s : arr){
                if(s.item.icon(Cicon.small) != null){
                    r.add(new Image(s.item.icon(Cicon.small))).left();
                }
                Label amount = new Label(s.amount + "", Styles.defaultLabel);
                amount.setColor(Color.LIGHT_GRAY);
                r.add(amount).padLeft(Scl.scl(2f)).left().padRight(Scl.scl(4f));

                if(++i % 4 == 0){
                    r.row();
                }
            }
            cont.add(r);

            show();
        }
    }
}
