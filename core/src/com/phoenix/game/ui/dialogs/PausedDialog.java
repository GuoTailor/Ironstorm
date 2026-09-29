package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.func.Boolp;
import com.phoenix.game.func.Prov;
import com.phoenix.game.input.KeyCode;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.util.Log;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Icon;

/**
 * 暂停菜单（游戏内按 ESC 弹出的那个）：返回游戏 / 科技树或数据库 / 设置 / 存档 / 读档 / 开服 / 退出。
 * 参照 Mindustry mindustry.ui.dialogs.PausedDialog 移植。
 *
 * <p><b>与原版的差异 + TODO（逐条）</b>
 * <ul>
 *   <li>刻意降级 <b>联机（{@code Vars.net} 那一族）全部退化为"单机语义"</b>：本项目 {@code Vars.net}
 *       <b>从头到尾没有任何地方赋值</b>（见 {@code ScriptConsoleFragment} 的注释）→ 恒为 {@code null}。
 *       所以原版的
 *       {@code net.active()} / {@code net.server()} / {@code net.client()} 一律写成
 *       {@link #netActive()}/{@link #netServer()}/{@link #netClient()}（{@code Vars.net != null && ...}，恒 false）。
 *       具体落到三个点：①"读档/载入"按钮不再因联机而禁用；
 *       ②"开服"按钮的文案恒为 {@code $hostserver}（不会变成 {@code $invitefriends}）、且不再被禁用；
 *       ③退出确认里的 {@code wasClient} 恒 false（不会走"客户端静默断开"分支）。
 *       {@code Vars.netClient} 是有可能在 {@code MenuFragment} 里被赋值的（加入服务器时），
 *       所以退出时仍按原版调用它的断开方法，只是名字叫 {@code disconnect()}（本项目没有 {@code disconnectQuietly()}）。</li>
 *   <li>刻意降级 {@code steam}：本项目 {@code Vars.steam} 是 boolean 字段（永远 false），保留原版的三元判断形状。</li>
 *   <li>TODO {@code platform.inviteFriends()}：本项目<b>没有 {@code Vars.platform}</b> → 该分支降级为日志。</li>
 *   <li>⚠ {@code state.rules.tutorial} / {@code world.isZone()} / {@code state.isEditor()}：<b>F5 已回填</b>
 *       （{@link com.phoenix.game.game.Rules} 补了 {@code tutorial}/{@code editor}/{@code zone}，
 *       {@code World.isZone()} 与 {@code GameState.isEditor()} 也补了）→ 教程局的存档/开服行会隐藏、
 *       zone 局显示"科技树"、编辑器局隐藏存档/读档。
 *       <b>T14 复核</b>：{@code showQuitConfirm}/{@code runExitSave} 里原先硬编码的 {@code tutorial=false}
 *       与"编辑器分支不可达"注释均已过时，已改为真实读值。
 *       本项目没有战役 zone 赋值链路 → {@code isZone()} 实际仍恒 false（字段通了、数据源还没接）。</li>
 *   <li>已接线 {@code control.saves.getCurrent().isAutosave()}：<b>T19 已给</b>
 *       {@link com.phoenix.game.game.Saves.SaveSlot} 补上逐槽开关（{@code isAutosave()}/{@code setAutosave()}，
 *       存 {@code Core.settings} 的 {@code save-<index>-autosave} 键）→ 退出文案与退出保存判据都按原版读它。
 *       另外本项目还有一个原版没有的**全局**总开关 {@code Vars.saves.autosave}（HUD 上的勾选框）→ 两者都开才自动存档。
 *       本项目是 {@code Vars.saves.getCurrent()}（原版 {@code control.saves}）。</li>
 *   <li>{@code logic.reset()}：本项目 {@code Logic} 没有该方法 → 用 {@link com.phoenix.game.core.Control#menu()}
 *       （它就是"回菜单 + 清场"的项目等价物，见 {@code GameOverDialog}）。</li>
 *   <li>{@code Core.bundle.get("savefail")} → {@code I18n.get(key, key)}（本项目 bundle 裁剪过、缺键会得到
 *       {@code ???key???}，见 转换.md）。</li>
 *   <li>arc 的 {@code Table.addImageTextButton} / {@code Cell.disabled(Boolp)} / {@code Cell.update(Cons)} /
 *       {@code Table.addRowImageTextButton} / {@code Cell.getLabelCell().wrap()} libgdx 都没有
 *       → 统一走本类的 {@link #dynamic}(...)：按钮自带图标 + 项目 {@link Label}，
 *       并在 {@code act()} 里每帧同步"禁用态"与"文案"（对应 arc 的两个按需求值回调）。
 *       另 {@code Cell.wrap()} 用 {@code Label.setWrap(true)} 代替。</li>
 *   <li>arc 的 {@code Table.update(Runnable)}（每帧回调）→ 覆写 {@link #act(float)}（与 {@code SaveDialog} 同做法）。</li>
 *   <li>原版 {@code keyDown(key -> ...)} libgdx 没有 → {@code key(KeyCode.ESCAPE, null)} / {@code key(KeyCode.BACK, null)}
 *       （libgdx 的 {@code Dialog.key} 内部已用 {@code postRunnable} 延后一帧再 hide）。</li>
 *   <li>{@code Icon.book}（数据库）本项目生成包没有 → 用 {@code Icon.database}；
 *       {@code Icon.left} → {@code Icon.arrowLeft}；{@code Icon.upload}/{@code Icon.download}（读档）
 *       → {@code Icon.loadImage}。</li>
 * </ul>
 */
public class PausedDialog extends FloatingDialog{
    private final SaveDialog save = new SaveDialog();
    private final LoadDialog load = new LoadDialog();
    private boolean wasClient = false;

    public PausedDialog(){
        super("$menu");
        shouldPause = true;

        shown(this::rebuild);

        //原版 keyDown(key -> { if(key == KeyCode.ESCAPE || key == KeyCode.BACK) hide(); })
        key(KeyCode.ESCAPE, null);
        key(KeyCode.BACK, null);
    }

    /**
     * 原版 {@code cont} 上的 {@code update(() -> { if(state.is(State.menu) && isShown()) hide(); })}：
     * libgdx 的 Table 没有每帧回调 → 覆写 {@code act()}（对话框只挂在 stage 上时才会被调用，
     * 所以不需要再判 arc 的 {@code isShown()}）。
     */
    @Override
    public void act(float delta){
        super.act(delta);
        if(Vars.state.isMenu()){
            hide();
        }
    }

    void rebuild(){
        cont.clear();

        //原版 state.rules.tutorial（F5 已补该字段）：教程局不显示存档/联机那几行
        final boolean tutorial = Vars.state.rules.tutorial;
        //原版 world.isZone() / state.isEditor()（F5 已补这两个方法）
        final boolean zone = isZone();
        final boolean editor = isEditor();

        if(!Vars.mobile){
            float dw = 220f;
            cont.defaults().width(Scl.scl(dw)).height(Scl.scl(55f)).pad(Scl.scl(5f));

            cont.add(dynamic("$back", Icon.arrowLeft, null, null, this::hide)).colspan(2).width(Scl.scl(dw * 2f + 20f));

            cont.row();
            if(zone){
                cont.add(dynamic("$techtree", Icon.tree, null, null, () -> Vars.ui.tech.show()));
            }else{
                cont.add(dynamic("$database", Icon.database, null, null, () -> Vars.ui.database.show()));
            }
            cont.add(dynamic("$settings", Icon.settings, null, null, () -> Vars.ui.settings.show()));

            if(!tutorial){
                if(!zone && !editor){
                    cont.row();
                    cont.add(dynamic("$savegame", Icon.save, null, null, () -> save.show()));
                    //原版 .disabled(b -> net.active()) → 每帧同步禁用态
                    cont.add(dynamic("$loadgame", Icon.loadImage, PausedDialog::netActive, null, () -> load.show()));
                }

                cont.row();

                cont.add(dynamic("$hostserver", Icon.host, () -> !((Vars.steam && netServer()) || !netActive()),
                    () -> netServer() && Vars.steam ? "$invitefriends" : "$hostserver",
                    this::runHost)).colspan(2).width(Scl.scl(dw * 2f + 20f));
            }

            cont.row();

            //原版 update(s -> s.setText(control.saves.getCurrent() != null && ...isAutosave() ? "$save.quit" : "$quit"))
            cont.add(dynamic("$quit", Icon.exit, null, PausedDialog::quitText, this::showQuitConfirm))
                .colspan(2).width(Scl.scl(dw + 20f));

        }else{
            cont.defaults().size(Scl.scl(130f)).pad(Scl.scl(5f));

            cont.add(dynamic("$back", Icon.play, null, null, this::hide)).row();
            cont.add(dynamic("$settings", Icon.settings, null, null, () -> Vars.ui.settings.show())).row();

            if(!zone && !editor){
                cont.add(dynamic("$save", Icon.save, null, null, () -> save.show())).row();

                cont.add(dynamic("$load", Icon.loadImage, PausedDialog::netActive, null, () -> load.show())).row();
            }else{
                cont.row();
            }

            cont.add(dynamic("$hostserver.mobile", Icon.host, PausedDialog::netActive, null, this::runHost)).row();

            //原版 s.getLabelCell().growX().wrap() → libgdx 的 Cell 没有 wrap() → 设 Label.setWrap(true)
            cont.add(dynamic("$quit", Icon.exit, null, PausedDialog::quitText, this::showQuitConfirm, true));
        }
    }

    /** 原版"开服"按钮的动作（{@code ui.host.runHost()} / {@code ui.host.show()} / {@code platform.inviteFriends()}）。 */
    private void runHost(){
        if(netServer() && Vars.steam){
            //TODO 原版 platform.inviteFriends()：本项目没有 Vars.platform → 降级为日志
            Log.warn("TODO inviteFriends: Vars.platform is not available in phoenix.");
        }else if(Vars.steam){
            //原版 ui.host.runHost()（steam 下直接开服）；本项目 HostDialog 有 runHost()，T14 复核确认已在调用
            Vars.ui.host.runHost();
        }else{
            Vars.ui.host.show();
        }
    }

    void showQuitConfirm(){
        //原版 state.rules.tutorial（Rules.tutorial 已就位，T14 接线；判空防菜单期调用）
        final boolean tutorial = Vars.state != null && Vars.state.rules.tutorial;

        Vars.ui.showConfirm("$confirm", tutorial ? "$quit.confirm.tutorial" : "$quit.confirm", () -> {
            if(tutorial){
                Core.settings.put("playedtutorial", true);
                Core.settings.save();
            }
            //原版 wasClient = net.client(); if(net.client()) netClient.disconnectQuietly();
            //本项目 Vars.net 恒 null → wasClient 恒 false；Vars.netClient 可能非 null（加入服务器时创建）
            wasClient = netClient();
            if(netClient() && Vars.netClient != null){
                //本项目 NetClient 没有 disconnectQuietly() → 用 disconnect()（主动断开，不触发自动重连）
                Vars.netClient.disconnect();
            }
            runExitSave();
            hide();
        });
    }

    public void runExitSave(){
        //原版 state.isEditor()：GameState.isEditor() 已就位（T14 复核，本分支已可达）
        if(isEditor() && !wasClient){
            Vars.ui.editor.resumeEditing();
            return;
        }

        //原版 control.saves.getCurrent().isAutosave()（T19 已给 SaveSlot 补上逐槽开关）
        //  → 再叠一层本项目特有的全局总开关 Vars.saves.autosave（HUD 上的勾选框），两者都开才自动存档
        //原版条件里还有 state.rules.tutorial（Rules.tutorial 已就位，T14 接线）
        if(Vars.saves == null || Vars.saves.getCurrent() == null || !Vars.saves.autosave
            || !Vars.saves.getCurrent().isAutosave() || wasClient
            || (Vars.state != null && Vars.state.rules.tutorial)){
            //原版 state.set(State.menu) + logic.reset()：本项目等价物是 Control.menu()
            Vars.control.menu();
            return;
        }

        Vars.ui.loadAnd("$saveload", () -> {
            try{
                Vars.saves.getCurrent().save();
            }catch(Throwable e){
                e.printStackTrace();
                //原版 Core.bundle.get("savefail")；本项目 bundle 缺键 → I18n.get 带 fallback
                Vars.ui.showException("[accent]" + I18n.get("savefail", "savefail"), e);
            }
            Vars.control.menu();
        });
    }

    // ------------------------------------------------------------------
    // 联机判定（对应原版 Vars.net 的那几个查询）
    // ------------------------------------------------------------------

    /**
     * 对应原版 {@code net.active()}。
     * <p>刻意降级：本项目 {@code Vars.net} <b>没有任何地方赋值</b>（见 {@code ScriptConsoleFragment} 注释）→ 恒 false。
     */
    private static boolean netActive(){
        return Vars.net != null && Vars.net.active();
    }

    /** 对应原版 {@code net.server()}（同上，本项目恒 false）。 */
    private static boolean netServer(){
        return Vars.net != null && Vars.net.server();
    }

    /** 对应原版 {@code net.client()}（同上，本项目恒 false）。 */
    private static boolean netClient(){
        return Vars.net != null && Vars.net.client();
    }

    // ------------------------------------------------------------------
    // 降级判定
    // ------------------------------------------------------------------

    /** 原版 {@code world.isZone()}：F5 已补该方法（{@code state.rules.zone != null}）。 */
    private static boolean isZone(){
        return Vars.world != null && Vars.world.isZone();
    }

    /** 原版 {@code state.isEditor()}：F5 已补该方法（{@code rules.editor}）。 */
    private static boolean isEditor(){
        return Vars.state.isEditor();
    }

    /**
     * 原版退出按钮的文案：
     * {@code control.saves.getCurrent() != null && control.saves.getCurrent().isAutosave() ? "$save.quit" : "$quit"}。
     * <p>已接线：逐槽 {@code isAutosave()}（T19 补）+ 本项目特有的全局 {@code Vars.saves.autosave}
     * —— 与 {@link #runExitSave()} 的判据保持一致，避免"显示会保存、实际不保存"。
     */
    private static String quitText(){
        boolean auto = Vars.saves != null && Vars.saves.autosave
            && Vars.saves.getCurrent() != null && Vars.saves.getCurrent().isAutosave();
        return auto ? "$save.quit" : "$quit";
    }

    // ------------------------------------------------------------------
    // 按钮工具
    // ------------------------------------------------------------------

    /**
     * 建一个"图标 + 文字"按钮，并支持每帧同步禁用态/文案（替代 arc 的
     * {@code Table.addImageTextButton} + {@code Cell.disabled(Boolp)} + {@code Cell.update(Cons)}）。
     *
     * @param disabled 非 null 时每帧同步禁用态（点击回调里也会复查，因为自建的 ClickListener 不会自动跳过禁用按钮）
     * @param text     非 null 时每帧同步文案，返回值是 {@code "$key"} 形态的 bundle key
     */
    private static TextButton dynamic(String label, Drawable icon, Boolp disabled, Prov<String> text, Runnable action){
        return dynamic(label, icon, disabled, text, action, false);
    }

    /** @param wrap 是否给文字设 {@code setWrap(true)}（对应原版 mobile 分支的 {@code getLabelCell().wrap()}）。 */
    private static TextButton dynamic(String label, Drawable icon, Boolp disabled, Prov<String> text, Runnable action, boolean wrap){
        final Label textLabel = new Label(resolve(label), Styles.defaultLabel);
        textLabel.setWrap(wrap);

        TextButton button = new TextButton("", Styles.defaultt){
            @Override
            public void act(float delta){
                super.act(delta);
                if(disabled != null){
                    setDisabled(disabled.get());
                }
                if(text != null){
                    String next = resolve(text.get());
                    if(!next.contentEquals(textLabel.getText())){
                        textLabel.setText(next);
                    }
                }
            }
        };
        button.clearChildren();
        if(icon != null){
            button.add(new Image(icon)).size(Scl.scl(26f)).padLeft(Scl.scl(8f));
        }
        button.add(textLabel).pad(Scl.scl(5f));

        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                //libgdx 的 Button 只在自己的内置 ClickListener 里检查 isDisabled，自建的不会 → 这里复查
                if(button.isDisabled()) return;
                action.run();
            }
        });
        return button;
    }
}
