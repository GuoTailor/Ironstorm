package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Strings;
import com.phoenix.game.func.Cons;
import com.phoenix.game.game.EventType;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.input.KeyCode;
import com.phoenix.game.io.MapIO;
import com.phoenix.game.maps.Map;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.ui.BorderImage;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.util.Fi;
import com.phoenix.game.util.Log;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

import java.io.File;

/**
 * 地图列表（把 {@code Vars.maps.all()} 铺成缩略图卡片网格；可新建/导入地图，点开看详情/进编辑器/删除）。
 * 参照 Mindustry mindustry.ui.dialogs.MapsDialog 移植。
 *
 * <p><b>与原版的差异 + TODO（逐条）</b>
 * <ul>
 *   <li><b>文件选择器已接线</b>：原版 {@code platform.showFileChooser(true, mapExtension, ...)}，本项目
 *       <b>没有 {@code Vars.platform}</b> → 用已移植的 {@link FileChooser}（与 {@code MapEditorDialog} 同一招，
 *       见 {@link #showFileChooser}）。</li>
 *   <li><b>已接线</b> {@code MapIO.isImage(file)} / {@code MapIO.createMap(file, true)}：<b>T19 已补</b>
 *       （{@link com.phoenix.game.io.MapIO#isImage}/{@link com.phoenix.game.io.MapIO#createMap}）——
 *       isImage 按 PNG 魔数判（不再用扩展名），createMap 读 {@code SaveIO.readMeta} 的 tags/宽高构造
 *       {@code Map}（原版从 meta region 读 tags，本项目 meta 是结构化 {@code SaveMeta} → 用它的
 *       {@code tags}/{@code width}/{@code height}）。</li>
 *   <li>等价替代 {@code maps.tryCatchMapError(...)}：本项目 {@code Maps} 没有该方法、也没有 {@code UnsafeRunnable}
 *       → 改成普通 {@code try/catch} + {@code ui.showException}。</li>
 *   <li>{@code maps.importMap(file)} / {@code maps.removeMap(map)}：<b>T14 已补</b>（见
 *       {@link com.phoenix.game.maps.Maps#importMap}/{@link com.phoenix.game.maps.Maps#removeMap}）
 *       → 本对话框的导入/删除已改为调用它们（不再走文件级复制 + {@code Vars.maps.load()} 重扫）。</li>
 *   <li>等价替代 {@code map.tags.getOr("name", ...)}：本项目 {@link com.phoenix.game.struct.StringMap}
 *       （继承 libgdx {@code ObjectMap}）没有 {@code getOr} → 手工判 {@code containsKey} + 后续 while 循环生成
 *       唯一名（原版 getOr 的 supplier 副作用就是生成唯一名）；另外把"空串"也当作缺名处理。</li>
 *   <li>TODO {@code platform.viewListing(map)}（工坊地图的"查看页面"）：本项目没有 {@code Vars.platform}
 *       （只有 {@code core/Platform} 接口、无实现类）→ 只记日志。</li>
 *   <li>TODO {@code map.mod.meta.displayName()}：本项目 {@code Mods.LoadedMod} 只有
 *       {@code file/mainClass/main/hidden} 字段，没有 {@code meta} → 用 {@code mod.toString()}。</li>
 *   <li>TODO <b>缩略图（整条地图预览链未移植）</b>：原版 {@code map.safeTexture()} 直接可用（arc 的 Texture 就是整张图）。
 *       本项目缺 {@code MapIO.generatePreview} / {@code mapPreviewDirectory} / {@code Map.texture} 赋值路径
 *       （{@link com.phoenix.game.maps.MapPreviewLoader} 尚未注册进 {@code Core.assets}、{@code Maps.loadPreviews()}
 *       不存在、{@code Maps.queueNewPreview} 只打日志）→ {@code map.texture} <b>恒为 null</b>；
 *       而 {@code Map.safeTexture()} 在没有预览纹理时会退回**整张图集纹理**（见 {@code Map} 类注释），
 *       直接塞进 {@code Image} 会把整页图集画出来 → 只认 {@code map.texture != null}，否则退回 {@link Tex#nomap}
 *       （与 {@code CustomGameDialog.previewOf} 同做法，见 {@link #previewImage}/{@link #previewBorder}）。</li>
 *   <li>{@code Core.graphics.isPortrait()} → 本项目 {@code Graphics} 没有该方法 → 用
 *       {@code getHeight() > getWidth()}（见 {@link #isPortrait()}）。</li>
 *   <li>arc 的 {@code Table.margin(...)} / {@code addImage()} / {@code add(String)} / {@code Cell.color(...)} /
 *       {@code table(Drawable, Cons)} / {@code Cell.wrap()} / {@code Cell.disabled(Boolp)} / {@code addButton} /
 *       {@code addImageTextButton} libgdx 都没有 → 用 {@code pad}、{@code add(new Image(...))}、{@link Label}、
 *       Actor 设色、自建内层 Table、{@code Label.setWrap(true)}、{@code button.setDisabled(...)}、
 *       {@link FloatingDialog#textButton}/{@link FloatingDialog#imageTextButton}。</li>
 *   <li>原版 {@code keyDown(key -> Core.app.post(this::hide))}：libgdx 的 {@code Dialog.key(int, Object)}
 *       内部已用 {@code postRunnable} 延后一帧再 hide → 直接 {@code key(KeyCode.ESCAPE, null)} 即等价。</li>
 *   <li>{@code Icon.left} → {@code Icon.arrowLeft}；{@code Icon.upload}（导入）→ {@code Icon.loadImage}；
 *       {@code Icon.export}（在编辑器中打开）→ {@code Icon.pencil}（本项目生成包里没有 export）。</li>
 *   <li>{@code Vars.ui.editor.beginEditMap(map.file)}：<b>T14 已接线</b>（原版 {@code file} 是 {@code Fi}，
 *       本项目 {@link Map#file} 是 {@code java.io.File} → 用 {@link Map#fi()} 转换）。</li>
 * </ul>
 */
public class MapsDialog extends FloatingDialog{
    private FloatingDialog dialog;

    public MapsDialog(){
        super("$maps");

        //原版把按钮表从对话框上摘下来，setup() 里再挂到 cont 顶部
        buttons.remove();

        //原版 keyDown(key -> { if(key == KeyCode.ESCAPE || key == KeyCode.BACK) Core.app.post(this::hide); })
        key(KeyCode.ESCAPE, null);
        key(KeyCode.BACK, null);

        shown(this::setup);
        onResize(() -> {
            if(dialog != null){
                dialog.hide();
            }
            setup();
        });
    }

    void setup(){
        buttons.clearChildren();

        if(isPortrait()){
            buttons.add(imageTextButton("$back", Icon.arrowLeft, null, this::hide)).size(Scl.scl(210f * 2f), Scl.scl(64f)).colspan(2);
            buttons.row();
        }else{
            buttons.add(imageTextButton("$back", Icon.arrowLeft, null, this::hide)).size(Scl.scl(210f), Scl.scl(64f));
        }

        buttons.add(imageTextButton("$editor.newmap", Icon.add, null, () -> {
            Vars.ui.showTextInput("$editor.newmap", "$name", "", text -> {
                Runnable show = () -> Vars.ui.loadAnd(() -> {
                    hide();
                    Vars.ui.editor.show();
                    Vars.ui.editor.editor.getTags().put("name", text);
                    Events.fire(new EventType.MapMakeEvent());
                });

                if(Vars.maps.byName(text) != null){
                    Vars.ui.showErrorMessage("$editor.exists");
                }else{
                    show.run();
                }
            });
        })).size(Scl.scl(210f), Scl.scl(64f));

        buttons.add(imageTextButton("$editor.importmap", Icon.loadImage, null, () -> {
            //已接线：原版 platform.showFileChooser(true, mapExtension, file -> {...}) —— 本项目没有 Vars.platform
            //  → 走本类的 showFileChooser helper（FileChooser 对话框）替代
            showFileChooser(true, Vars.mapExtension, file -> Vars.ui.loadAnd(() -> {
                //等价替代：原版 maps.tryCatchMapError(() -> {...}) —— 本项目 Maps 没有该方法 → 普通 try/catch
                try{
                    //已接线 原版 MapIO.isImage(file)：T19 已在 io/MapIO 补上（按 PNG 魔数判，比原来的扩展名判定更准）
                    if(MapIO.isImage(file.file())){
                        Vars.ui.showErrorMessage("$editor.errorimage");
                        return;
                    }

                    //已接线 原版 MapIO.createMap(file, true)：T19 已在 io/MapIO 补上
                    //  （读 SaveIO 的 meta tags/宽高构造 Map；本项目 meta 是结构化字段，见 MapIO#createMap 注释）
                    Map map = MapIO.createMap(file.file(), true);

                    //等价替代：原版 map.tags.getOr("name", () -> {...}) —— 本项目 StringMap（继承 libgdx ObjectMap）
                    //  没有 getOr → 手工判 containsKey + 下面的 while 循环生成唯一名（等价于原版 supplier 的副作用）
                    String name = map.tags.containsKey("name") ? map.tags.get("name") : null;
                    if(name == null || name.trim().isEmpty()){
                        String result = "unknown";
                        int number = 0;
                        while(Vars.maps.byName(result + number++) != null);
                        name = result + number;
                    }

                    //等价替代：原版还有一段 name == null 的兜底（注释说"永远不会触发"）→ 本项目已在上面并入判空

                    Map conflict = null;
                    Array<Map> all = Vars.maps.all();
                    for(int i = 0; i < all.size; i++){
                        Map other = all.get(i);
                        if(other.name.equals(name)){
                            conflict = other;
                            break;
                        }
                    }

                    if(conflict != null && !conflict.custom){
                        Vars.ui.showInfo(format("editor.import.exists", name));
                    }else if(conflict != null){
                        //注意：conflict/name 都在上面被重新赋值过，不是 effectively final → 先落成 final 局部变量再进 lambda
                        final Map conflictMap = conflict;
                        Vars.ui.showConfirm("$confirm", "$editor.overwrite.confirm", () -> {
                            removeMap(conflictMap);
                            importMap(map.file);
                            setup();
                        });
                    }else{
                        importMap(map.file);
                        setup();
                    }
                }catch(Exception e){
                    Log.err(e);
                    Vars.ui.showException(e);
                }
            }));
        })).size(Scl.scl(210f), Scl.scl(64f));

        cont.clear();

        Table maps = new Table();
        //原版 maps.marginRight(24)：libgdx 的 Table 没有 margin（外间距）→ 用 pad
        maps.padRight(Scl.scl(24f));

        ScrollPane pane = new ScrollPane(maps, Styles.defaultPane);
        pane.setFadeScrollBars(false);

        int maxwidth = Mathf.clamp((int)(Core.graphics.getWidth() / Scl.scl(230f)), 1, 8);
        float mapsize = 200f;

        Array<Map> all = Vars.maps == null ? new Array<Map>() : Vars.maps.all();

        int i = 0;
        //原版 for(Map map : Vars.maps.all())：本项目 all() 返回 libgdx Array → 显式下标循环
        for(int idx = 0; idx < all.size; idx++){
            Map map = all.get(idx);

            //原版 if(i % maxwidth == 0) maps.row()（i=0 那次是空行，libgdx 的 Table.row() 允许空行）
            if(i % maxwidth == 0){
                maps.row();
            }

            //原版 maps.addButton("", Styles.cleart, () -> showMapInfo(map)).width(mapsize).pad(8)
            TextButton button = textButton("", Styles.cleart, () -> showMapInfo(map));
            button.clearChildren();
            //原版 button.margin(9)：libgdx 没有 margin → pad
            button.pad(Scl.scl(9f));

            //原版 button.add(map.name()).width(mapsize - 18f).center().get().setEllipsis(true)
            Label nameLabel = new Label(map.name, Styles.defaultLabel);
            nameLabel.setEllipsis(true);
            button.add(nameLabel).width(Scl.scl(mapsize - 18f)).center();
            button.row();

            //原版 button.addImage().growX().pad(4).color(Pal.gray)
            Image divider = new Image(Tex.whiteui);
            divider.setColor(Pal.gray);
            button.add(divider).growX().pad(Scl.scl(4f));
            button.row();

            //原版 button.stack(new Image(map.safeTexture())..., new BorderImage(...)).size(mapsize - 20f)
            button.stack(previewImage(map), previewBorder(map)).size(Scl.scl(mapsize - 20f));
            button.row();

            //原版 button.add(map.custom ? "$custom" : ...).color(Color.gray).padTop(3)
            Label typeLabel = new Label(bundle(mapTypeLabel(map)), Styles.defaultLabel);
            typeLabel.setColor(Color.GRAY);
            button.add(typeLabel).padTop(Scl.scl(3f));

            maps.add(button).width(Scl.scl(mapsize)).pad(Scl.scl(8f));

            i++;
        }

        if(all.size == 0){
            maps.add(new Label(bundle("$maps.none"), Styles.defaultLabel));
        }

        cont.add(buttons).growX();
        cont.row();
        cont.add(pane).uniformX();
    }

    void showMapInfo(Map map){
        dialog = new FloatingDialog("$editor.mapinfo");
        dialog.addCloseButton();

        float mapsize = isPortrait() ? 160f : 300f;
        Table table = dialog.getContentTable();

        table.stack(previewImage(map), previewBorder(map)).size(Scl.scl(mapsize));

        //原版 table.table(Styles.black, desc -> {...}).height(mapsize).width(mapsize)
        //libgdx 没有带回调的 table(...) → 自建内层 Table
        Table desc = new Table();
        desc.setBackground(Styles.black);
        desc.top();

        Table t = new Table();
        //原版 t.margin(6)：libgdx 没有 margin → pad
        t.pad(Scl.scl(6f));

        ScrollPane pane = new ScrollPane(t, Styles.defaultPane);
        desc.add(pane).grow();

        t.top();
        t.defaults().padTop(Scl.scl(10f)).left();

        t.add(grayLabel("$editor.name")).padRight(Scl.scl(10f)).padTop(0f);
        t.row();
        t.add(wrappedLabel(map.name)).growX().padTop(Scl.scl(2f));
        t.row();
        t.add(grayLabel("$editor.author")).padRight(Scl.scl(10f));
        t.row();
        //原版 map.custom && map.author().isEmpty() ? "Anuke" : map.author()
        t.add(wrappedLabel(map.custom && map.author().isEmpty() ? "Anuke" : map.author())).growX().padTop(Scl.scl(2f));
        t.row();
        t.add(grayLabel("$editor.description")).padRight(Scl.scl(10f)).top();
        t.row();
        t.add(wrappedLabel(map.description())).growX().padTop(Scl.scl(2f));

        table.add(desc).height(Scl.scl(mapsize)).width(Scl.scl(mapsize));

        table.row();

        table.add(imageTextButton("$editor.openin", Icon.pencil, null, () -> {
            try{
                //原版 ui.editor.beginEditMap(map.file)（Fi）；本项目 Map.file 是 File → 用 map.fi()（T14 已接线）
                Vars.ui.editor.beginEditMap(map.fi());
                dialog.hide();
                hide();
            }catch(Exception e){
                e.printStackTrace();
                Vars.ui.showErrorMessage("$error.mapnotfound");
            }
        })).fillX().height(Scl.scl(54f)).padLeft(Scl.scl(10f));

        boolean workshop = map.workshop && Vars.steam;

        //原版 .disabled(!map.workshop && !map.custom)：内置地图（非自定义、非工坊）不能删
        TextButton action = imageTextButton(workshop ? "$view.workshop" : "$delete", workshop ? Icon.link : Icon.trash, null, () -> {
            if(workshop){
                //TODO 原版 platform.viewListing(map)：本项目没有 Vars.platform → 降级为日志
                Log.warn("TODO viewListing: Vars.platform is not available in phoenix.");
            }else{
                Vars.ui.showConfirm("$confirm", format("map.delete", map.name), () -> {
                    removeMap(map);
                    dialog.hide();
                    setup();
                });
            }
        });
        action.setDisabled(!map.workshop && !map.custom);

        table.add(action).fillX().height(Scl.scl(54f)).padLeft(Scl.scl(10f));

        dialog.show();
    }

    // ------------------------------------------------------------------
    // 地图文件操作（T14：改用 Maps 新补的 importMap/removeMap，不再自己做文件级复制 + 全量重扫）
    // ------------------------------------------------------------------

    /**
     * 导入地图（原版 {@code maps.importMap(map.file)}）。
     * <p>{@link com.phoenix.game.maps.Maps#importMap} 会抛 {@code IOException}，且这里可能从
     * {@code showConfirm} 的回调（脱离外层 try/catch）里调用 → 自带一层 catch + {@code ui.showException}。
     */
    private static void importMap(File source){
        if(Vars.maps == null) return;
        try{
            Vars.maps.importMap(source);
        }catch(Exception e){
            Log.err(e);
            if(Vars.ui != null) Vars.ui.showException(e);
        }
    }

    /** 删除地图（原版 {@code maps.removeMap(map)}）：释放预览纹理 + 从列表移除 + 删文件。 */
    private static void removeMap(Map map){
        if(Vars.maps == null){
            if(map.file.exists()) map.file.delete();
            return;
        }
        Vars.maps.removeMap(map);
    }

    /** 打开文件选择器（对应原版 {@code platform.showFileChooser}；与 {@code MapEditorDialog} 同做法）。 */
    private static void showFileChooser(boolean open, String extension, Cons<Fi> result){
        FileChooser.showFileChooser(open, extension, result);
    }

    // ------------------------------------------------------------------
    // 缩略图 / 文案 / 布局工具
    // ------------------------------------------------------------------

    /**
     * @return 地图缩略图（原版 {@code new Image(map.safeTexture())}）。
     * <p>不能无条件用 {@code map.safeTexture()}：它在没有预览纹理时会退回**整张图集**里 nomap 那一页，
     * 而 libgdx 的 {@code Texture} 不带区域信息（原版 arc 的 {@code Texture} 就是整张图，故可直接用）
     * → 只认 {@code map.texture != null}，否则退回 {@link Tex#nomap}。
     */
    private static Image previewImage(Map map){
        Image image = new Image(previewDrawable(map));
        image.setScaling(Scaling.fit);
        return image;
    }

    /** 同 {@link #previewImage}，对应原版的 {@code new BorderImage(map.safeTexture())}。 */
    private static BorderImage previewBorder(Map map){
        BorderImage image = map.texture == null
            ? new BorderImage(Tex.nomap.getRegion(), 4f)
            : new BorderImage(map.texture);
        image.setScaling(Scaling.fit);
        return image;
    }

    private static TextureRegionDrawable previewDrawable(Map map){
        if(map.texture == null) return Tex.nomap;
        Texture texture = map.safeTexture();
        return texture == null ? Tex.nomap : new TextureRegionDrawable(new TextureRegion(texture));
    }

    /** 卡片底部的类型文案（原版 {@code map.custom ? "$custom" : map.workshop ? "$workshop" : map.mod != null ? ... : "$builtin"}）。 */
    private static String mapTypeLabel(Map map){
        if(map.custom) return "$custom";
        if(map.workshop) return "$workshop";
        //TODO 原版 map.mod.meta.displayName()：本项目 Mods.LoadedMod 没有 meta → 用 toString()
        if(map.mod != null) return "[lightgray]" + map.mod.toString();
        return "$builtin";
    }

    private static Label grayLabel(String key){
        Label label = new Label(bundle(key), Styles.defaultLabel);
        label.setColor(Color.GRAY);
        return label;
    }

    /** 换行文本（原版 arc 的 {@code Cell.wrap()}；libgdx 要设在 Label 上）。 */
    private static Label wrappedLabel(String text){
        Label label = new Label(text, Styles.defaultLabel);
        label.setWrap(true);
        return label;
    }

    /** @return 是否竖屏（对应 arc 的 {@code Core.graphics.isPortrait()}，libgdx 的 Graphics 没有该方法）。 */
    private static boolean isPortrait(){
        return Core.graphics.getHeight() > Core.graphics.getWidth();
    }

    /** 原版 {@code Core.bundle.format(key, args)}；本项目 bundle 缺键多 → 走 {@link I18n#get} 带 fallback。 */
    private static String format(String key, Object... args){
        return Strings.format(I18n.get(key, key), args);
    }
}
