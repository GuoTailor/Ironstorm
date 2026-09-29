package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.mod.Mods;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import io.anuke.mindustry.gen.Icon;

/**
 * 模组管理对话框。参照 Mindustry mindustry.ui.dialogs.ModsDialog。
 *
 * <p><b>本项目 {@code mod/Mods} 是 headless 精简版</b>（只扫 {@code mods/} 下的 jar/zip 并实例化入口类），
 * 原版那一整套模组元数据 / 启用停用 / 依赖检查 / 创意工坊都没移植，所以本对话框只能
 * "列出 / 导入 / 移除已加载的模组"（没有启用停用与元数据显示）。逐条对应：
 * <ul>
 *   <li>已接线 原版 {@code Core.app.openFolder(modDirectory.absolutePath())}：libgdx 的 {@code Application}
 *       没有该方法 → 等价替代 {@code Gdx.net.openURI(file URI)}（原版桌面端就是 {@code java.awt.Desktop.open}；
 *       本项目也没有 {@code Vars.modDirectory}，路径由 {@link #modsDirectory()} 提供）。</li>
 *   <li>已接线 原版 {@code shown(() -> Core.app.post(() -> Core.settings.getBoolOnce("modsalpha", ...)))}
 *       （{@code Settings} 没有 {@code getBoolOnce} → 用 {@code getBool} + {@code put} 手搓"只弹一次"，
 *       写法同 {@code HostDialog}；{@code ui.showText} 存在）→ 弹出 {@code $mods.alphainfo}。</li>
 *   <li>已接线 原版 {@code platform.showFileChooser(true, "zip", ...)}（"导入"按钮）：本项目没有
 *       {@code Vars.platform} → 改用 {@code ui/dialogs/FileChooser} + {@code Vars.mods.loadMod(File)}。</li>
 *   <li>TODO 原版 {@code mods.requiresReload()} / {@code hidden()} 里的 {@code ui.loadAnd("$reloading", ...)} /
 *       {@code mods.eachEnabled(...)} / {@code mods.reloadContent()} / {@code ui.showErrorMessage(...)}
 *       （{@code Mods} 只有 {@code load/loadMod/list/listMods/eachClass/hasContentErrors}，<b>缺
 *       {@code requiresReload()} / {@code reloadContent()} / {@code eachEnabled()}</b>；
 *       {@code Vars.ui.loadAnd} 本身是有的，缺的是 {@code mods.requiresReload()} 这个触发条件）→ 整段去掉。</li>
 *   <li>TODO 原版"从 GitHub 导入"：{@code ui.showTextInput} / {@code ui.loadfrag} / {@code ui.showErrorMessage}
 *       / {@code mods.importMod} <b>都有</b>；真正缺的是 {@code Core.net.httpGet}（本项目 {@code Core.net}
 *       是 libgdx {@code Net}，<b>无 httpGet</b>）与 {@code mods.reloadContent} → 禁用占位。</li>
 *   <li><b>已接线</b> 原版模组卡片上的"移除"按钮（{@code mods.removeMod} + {@code ui.showConfirm}）：
 *       <b>T19 已给 {@code mod/Mods} 补上 {@code removeMod(LoadedMod)}</b>（删文件 + 从列表移除）→
 *       卡片标题右侧的 {@code Icon.trash} 按钮已接回。仍缺的是 启用/禁用（{@code mods.setEnabled} /
 *       {@code mod.enabled()}）与 Steam 发布（{@code platform.publish}）——{@code LoadedMod} 只有
 *       {@code file/mainClass/main/hidden} 字段、没有 {@code enabled}/{@code meta}，且没有 {@code Vars.platform}。</li>
 *   <li>等价替代 arc 的 {@code Table.margin(...)} / {@code table(Drawable, Cons)} / {@code labelWrap(...)} /
 *       {@code Cell.wrap()}：libgdx 都没有 → 分别用 {@code pad} / 自建 Table / Label+setWrap / 只设 setWrap。</li>
 *   <li><b>T14 补 bundle 键</b>：{@code mods.openfolder}/{@code mods.guide}/{@code mods.none}/{@code mod.import}/
 *       {@code mod.import.github} 此前在本项目 bundle 里整体缺失（按钮一直显示裸键）→ 已按原版补齐中英。</li>
 * </ul>
 */
public class ModsDialog extends FloatingDialog{

    public ModsDialog(){
        super("$mods");
        addCloseButton();

        //已接线：原版 buttons.addImageTextButton("$mods.openfolder", Icon.link, () -> Core.app.openFolder(modDirectory.absolutePath())).size(250f, 64f)
        //  —— libgdx 的 Application 没有 openFolder → 等价替代 Gdx.net.openURI(file URI)
        buttons.add(imageTextButton("$mods.openfolder", Icon.link, null,
            () -> Gdx.net.openURI(new java.io.File(modsDirectory()).toURI().toString())))
            .size(Scl.scl(250f), Scl.scl(64f));

        buttons.row();

        //原版：buttons.addImageTextButton("$mods.guide", Icon.link, () -> Core.net.openURI(modGuideURL)).size(210, 64f)
        //  —— ⚠ 本项目 Core.net 从未赋值（恒 null），改用 Gdx.net（libgdx 后端始终可用）
        buttons.add(imageTextButton("$mods.guide", Icon.link, null,
            () -> Gdx.net.openURI(Vars.modGuideURL)))
            .size(Scl.scl(210f), Scl.scl(64f));

        //TODO 原版"从 GitHub 导入"（ui.showTextInput + Core.net.httpGet + mods.importMod + ui.loadfrag）→ 禁用占位
        //  —— 缺 Core.net.httpGet / mods.reloadContent（mods.importMod 本项目已有；见类注释）
        TextButton github = imageTextButton("$mod.import.github", Icon.github, null, () -> {});
        github.setDisabled(true);
        buttons.add(github).size(Scl.scl(250f), Scl.scl(64f));

        shown(this::setup);

        //已接线：原版 shown(() -> Core.app.post(() -> Core.settings.getBoolOnce("modsalpha", ...)))
        //  —— Settings 没有 getBoolOnce → 用 getBool + put 手搓"只弹一次"（写法同 HostDialog）
        shown(() -> Gdx.app.postRunnable(() -> {
            if(!Core.settings.getBool("modsalpha", false)){
                Core.settings.put("modsalpha", true);
                Core.settings.save();
                if(Vars.ui != null) Vars.ui.showText("$mods", "$mods.alphainfo");
            }
        }));

        //TODO 原版 hidden()：if(mods.requiresReload()){ ui.loadAnd("$reloading", ...) }
        //  —— Mods 缺 requiresReload()/reloadContent()/eachEnabled()（见类注释）→ 整段去掉
    }

    void setup(){
        cont.clear();
        cont.defaults().width(Scl.scl(Vars.mobile ? 500f : 560f)).pad(Scl.scl(4f));

        //TODO 原版 cont.add("$mod.reloadrequired").visible(mods::requiresReload).center().get().setAlignment(Align.center)
        //  —— Mods 没有 requiresReload()（也没有"重启才生效"的启用/停用）→ 去掉

        Array<Mods.LoadedMod> list = Vars.mods.list();

        if(!list.isEmpty()){
            Table table = new Table();
            //等价替代 原版 table.margin(10f).top()（外间距）→ libgdx 没有 margin，用 pad 代替
            table.pad(Scl.scl(10f)).top();

            for(int i = 0; i < list.size; i++){
                Mods.LoadedMod mod = list.get(i);

                //原版 table.table(Styles.black6, t -> {...})：libgdx 没有"带背景 + 回调"的重载 → 自建
                Table t = new Table();
                t.setBackground(Styles.black6);
                t.defaults().pad(Scl.scl(2f)).left().top();
                //等价替代 原版 t.margin(14f)（外间距）→ 用 pad 代替
                t.pad(Scl.scl(14f)).left();

                //原版 title.add("[accent]" + mod.meta.displayName() + "[lightgray] v" + mod.meta.version ...)
                //  —— LoadedMod 没有 meta（缺 ModMeta 一族）→ 显示文件名 + 入口类
                Table title = new Table();
                Label titleLabel = new Label("[accent]" + mod.file.getName() + "[lightgray]  " + mod.mainClass, Styles.defaultLabel);
                titleLabel.setWrap(true);
                title.add(titleLabel).width(Scl.scl(200f)).left();

                //已接线 原版右侧"移除"按钮（Icon.trash + mods.removeMod + ui.showConfirm）：
                //  T19 已给 Mods 补上 removeMod（删文件 + 从列表移除）。
                //  原版该按钮还带 steam 工坊分支（hasSteamID → platform.viewListing）与 启用/停用 按钮，
                //  这两者仍缺（LoadedMod 没有 enabled/meta、没有 Vars.platform）→ 只落地"移除"
                title.add(iconButton(Icon.trash, Styles.cleari, () -> {
                    if(Vars.ui != null){
                        Vars.ui.showConfirm("$confirm", "$mod.remove.confirm", () -> {
                            if(Vars.mods != null) Vars.mods.removeMod(mod);
                            setup();
                        });
                    }
                })).size(Scl.scl(44f)).padLeft(Scl.scl(6f));

                t.add(title).growX().left();
                t.row();
                Label path = new Label("[lightgray]" + mod.file.getAbsolutePath(), Styles.defaultLabel);
                path.setWrap(true);
                t.add(path).growX();

                table.add(t).width(Scl.scl(Vars.mobile ? 430f : 500f));
                table.row();
            }

            ScrollPane pane = new ScrollPane(table, Styles.defaultPane);
            cont.add(pane).grow();
        }else{
            //原版 cont.table(Styles.black6, t -> t.add("$mods.none")).height(80f)
            Table t = new Table();
            t.setBackground(Styles.black6);
            t.add(new Label(bundle("$mods.none"), Styles.defaultLabel));
            cont.add(t).height(Scl.scl(80f));
        }

        cont.row();

        //已接线：原版 cont.addImageTextButton("$mod.import", Icon.add, () -> platform.showFileChooser(true, "zip", file -> mods.importMod(file)))
        //  —— 本项目没有 Vars.platform → 用 ui/dialogs/FileChooser 等价替代。
        //  注意原版 importMod 只把 jar 拷进 mods/（不立即加载，靠 requiresReload 提示重启）；
        //  本项目没有 reload 机制 → 先 importMod 落盘（持久化），再 loadMod 让它本次会话即可见。
        TextButton importButton = imageTextButton("$mod.import", Icon.add, null, () -> {
            FileChooser.showFileChooser(true, "zip", file -> {
                try{
                    if(Vars.mods != null) Vars.mods.loadMod(Vars.mods.importMod(file.file()));
                    setup();
                }catch(Exception e){
                    if(Vars.ui != null) Vars.ui.showException(e);
                }
            });
        });
        cont.add(importButton).pad(Scl.scl(12f)).width(Scl.scl(400f));
    }

    /** @return 模组目录的绝对路径（对应原版 {@code Vars.modDirectory}，本项目没有该字段）。 */
    private static String modsDirectory(){
        return Core.files.local("mods").file().getAbsolutePath();
    }

    /** 一个纯图标按钮（对应原版 {@code addImageButton(icon, style, action)}，与 {@code JoinDialog} 同做法）。 */
    private static ImageButton iconButton(Drawable icon, ImageButton.ImageButtonStyle style, Runnable action){
        ImageButton.ImageButtonStyle bs = new ImageButton.ImageButtonStyle(style);
        bs.imageUp = icon;
        ImageButton button = new ImageButton(bs);
        button.getImage().setDrawable(icon);
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                action.run();
            }
        });
        return button;
    }
}
