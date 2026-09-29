package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.GameState;
import com.phoenix.game.core.Scl;
import com.phoenix.game.game.EventType;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.input.KeyCode;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.util.Log;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Sounds;
import io.anuke.mindustry.gen.Tex;

/**
 * 浮动对话框基类，参照 Mindustry mindustry.ui.dialogs.FloatingDialog。
 *
 * <p><b>与原版的结构差异</b>（arc 的 {@code Dialog} 继承 {@code Table}，libgdx 的继承 {@code Window}）：
 * <ul>
 *   <li>原版 {@code Dialog.cont/buttons/title/titleTable} 是 arc Dialog 的公开字段；
 *       libgdx 用 {@code getContentTable()/getButtonTable()/getTitleLabel()/getTitleTable()} 取，
 *       本类在构造器里把它们挂成同名的 protected 字段，子类写法可以照抄原版。</li>
 *   <li>libgdx 的 Dialog <b>没有</b> {@code DialogStyle}/{@code stageBackground}：
 *       窗口样式走 {@code Window.WindowStyle}（字段与 {@code Styles.DialogStyle} 一一对应），
 *       全屏遮罩则自己铺一个 {@link Image}（做法见 {@code ContentInfoDialog} 的 shade）。</li>
 *   <li>libgdx 的 Dialog 没有 {@code shown/hidden(Runnable)} 钩子，本类自己收集并在 show/hide 里执行。</li>
 *   <li>libgdx 的 Dialog 没有 {@code keyDown}，ESC/BACK 关闭改用 Dialog 自带的 {@code key(int, Object)}。</li>
 * </ul>
 *
 * <p><b>遮罩（stageBackground）怎么落地的</b>：{@link #shade} 是一张 {@code setFillParent(true)} 的
 * {@link Image}，取 {@code Styles.DialogStyle.stageBackground}（默认 {@code Styles.black9}）。
 * 它在 {@link #show(Stage)} 里被加到 stage 上、在 {@link #hide()} 里隐藏。只添加一次 ——
 * libgdx 的 {@code addActor} 会把它提到最上层，重复添加会盖住对话框本身。
 * 与原版一致，<b>点遮罩不关闭</b>（原版 Dialog 也不关），关闭只能走 {@link #addCloseButton()} 的按钮或 ESC。
 *
 * <p><b>暂停行为（shouldPause / wasPaused）</b>：照搬原版 FloatingDialog 的 shown/hidden 回调 ——
 * 打开时若 {@code shouldPause} 且不在主菜单，就记住当前是否已暂停（{@link #wasPaused}）并把状态切成
 * {@code paused}；关闭时恢复成 {@code playing}（原本就是暂停的、或者联机中则不恢复，对应原版的
 * {@code !wasPaused || net.active()}）。原版这段逻辑写在构造器的 {@code shown(...)}/{@code hidden(...)} 里，
 * 本类同样如此，子类只需置 {@link #shouldPause}。
 *
 * <p><b>Stage 从哪来</b>：原版用全局的 {@code Core.scene}，本项目没有全局 Stage 引用
 * （见 {@code OverlayFragment} 的说明），所以这里留了一个 {@link #scene} 静态字段由启动器注入；
 * {@link #show()} 走它，拿不到时只记一条警告。<b>需要主会话在 ClientLauncher 里补一行
 * {@code FloatingDialog.scene = stage;}</b>。
 */
public class FloatingDialog extends Dialog{
    /** 对应原版 {@code Core.scene}：全局 Stage。本项目没有全局引用，由启动器注入。 */
    public static Stage scene;

    /** 标题下方分隔线高度（原版写死 3f）。 */
    private static final float LINE_HEIGHT = 3f;
    /** 关闭按钮尺寸（对应原版 addCloseButton 里的 210x64）。 */
    private static final float CLOSE_WIDTH = 210f, CLOSE_HEIGHT = 64f;

    private final Array<Runnable> shownActions = new Array<>();
    private final Array<Runnable> hiddenActions = new Array<>();
    /** 是否铺满整个 stage（对应原版构造器里的 setFillParent(true)）。 */
    private boolean fillStage;

    private boolean wasPaused;
    /** 打开时是否暂停游戏（对应原版 {@code FloatingDialog.shouldPause}）。 */
    protected boolean shouldPause;

    /** 内容表（对应原版 {@code Dialog.cont}）。 */
    protected Table cont;
    /** 按钮表（对应原版 {@code Dialog.buttons}）。 */
    protected Table buttons;
    /** 标题表（对应原版 {@code Dialog.titleTable}）。 */
    protected Table titleTable;
    /** 标题（libgdx 的 Window 自带；用原生 Label 绘制，不解析 arc 的 markup）。 */
    protected com.badlogic.gdx.scenes.scene2d.ui.Label title;

    /** 全屏遮罩（对应原版 {@code DialogStyle.stageBackground}）。 */
    private Image shade;

    public FloatingDialog(String title, Styles.DialogStyle style){
        super(resolve(title), new Window.WindowStyle(style.titleFont, style.titleFontColor, style.background));

        cont = getContentTable();
        buttons = getButtonTable();
        titleTable = getTitleTable();
        this.title = getTitleLabel();

        //原版 FloatingDialog：标题居中 + 标题下方一条 accent 分隔线
        this.title.setAlignment(Align.center);
        titleTable.row();
        titleTable.add(new Image(Tex.whiteui.tint(Pal.accent)))
            .growX().height(Scl.scl(LINE_HEIGHT)).pad(Scl.scl(4f));

        //原版构造器里的 setFillParent(true)：对话框铺满整个 stage，内容表自身 grow 居中
        setFillParent(true);

        //遮罩：libgdx 的 Dialog 没有 stageBackground，自己铺一层全屏 Image
        shade = new Image(style.stageBackground != null ? style.stageBackground : Styles.black9);
        shade.setFillParent(true);
        shade.setVisible(false);

        hidden(() -> {
            if(shouldPause && !Vars.state.isMenu()){
                if(!wasPaused || (Vars.net != null && Vars.net.active())){
                    Vars.state.set(GameState.State.playing);
                }
            }
            Sounds.back.play();
        });

        shown(() -> {
            if(shouldPause && !Vars.state.isMenu()){
                wasPaused = Vars.state.isPaused();
                Vars.state.set(GameState.State.paused);
            }
        });
    }

    public FloatingDialog(String title){
        this(title, Styles.defaultDialog);
    }

    /**
     * 显示对话框（对应原版 {@code Dialog.show()}）。原版取全局 {@code Core.scene}，
     * 本项目用 {@link #scene}；没注入时只记警告并返回（避免直接 NPE 崩掉渲染线程）。
     */
    public void show(){
        if(scene == null){
            Log.warn("FloatingDialog.scene is null, dialog not shown.");
            return;
        }
        show(scene);
    }

    @Override
    public Dialog show(Stage stage){
        //遮罩只添加一次：libgdx 的 addActor 会把它提到最上层，重复添加会盖住对话框
        if(shade.getStage() == null){
            stage.addActor(shade);
        }
        shade.setVisible(true);

        super.show(stage);

        //先做快照再跑：回调里可能又调 shown(...)（AboutDialog.setup 就是这么写的），
        //直接按 size 遍历会把新加进来的回调也立刻执行，变成死循环
        Array<Runnable> snapshot = new Array<>(shownActions);
        for(int i = 0; i < snapshot.size; i++){
            snapshot.get(i).run();
        }

        //pack 后重新居中：show() 内部用的是 pack 前的旧尺寸，否则窗口与 hit 区域会错开
        pack();
        if(!fillStage){
            setPosition((stage.getWidth() - getWidth()) / 2f, (stage.getHeight() - getHeight()) / 2f);
        }
        return this;
    }

    @Override
    public void hide(){
        if(shade != null) shade.setVisible(false);

        Array<Runnable> snapshot = new Array<>(hiddenActions);
        for(int i = 0; i < snapshot.size; i++){
            snapshot.get(i).run();
        }

        //原版 arc 的 hide 是立即收掉；libgdx 默认带 0.4s 淡出，
        //会让遮罩先消失、窗口还浮在亮背景上，这里跟随原版立即移除
        remove();
    }

    /** 对应原版 {@code Dialog.shown(Runnable)}。 */
    public void shown(Runnable run){
        shownActions.add(run);
    }

    /** 对应原版 {@code Dialog.hidden(Runnable)}。 */
    public void hidden(Runnable run){
        hiddenActions.add(run);
    }

    /** 窗口是否铺满 stage（对应原版 {@code setFillParent}；默认 true）。 */
    @Override
    public void setFillParent(boolean fillParent){
        this.fillStage = fillParent;
        super.setFillParent(fillParent);
    }

    /** 窗口尺寸变化时重跑布局（对应原版 {@code FloatingDialog.onResize}）。 */
    protected void onResize(Runnable run){
        Events.on(EventType.ResizeEvent.class, event -> {
            if(getStage() == null) return;
            run.run();
            //对应原版的 updateScrollFocus()：libgdx 的 Dialog 没有该方法，手动把滚轮焦点收回对话框
            getStage().setScrollFocus(this);
        });
    }

    /** 对应原版 {@code FloatingDialog.addCloseButton}（"$back" 按钮 + ESC/BACK 关闭）。 */
    public void addCloseButton(){
        buttons.defaults().size(Scl.scl(CLOSE_WIDTH), Scl.scl(CLOSE_HEIGHT));
        buttons.add(imageTextButton("$back", Icon.arrowLeft, null, this::hide))
            .size(Scl.scl(CLOSE_WIDTH), Scl.scl(CLOSE_HEIGHT));

        //原版用 keyDown 监听 KeyCode.ESCAPE/BACK；本项目 KeyCode 就是 int 常量，
        //libgdx 的 Dialog.key(int, Object) 在按键时调 result(object) 再 hide()
        key(KeyCode.ESCAPE, null);
        key(KeyCode.BACK, null);
    }

    /**
     * 对应 arc 的 {@code Table.addImageTextButton(text, icon, style, action)}。
     * libgdx 的 Table <b>没有</b>该方法，改成"先建按钮再 add"。
     * <p>按钮里的文字换成项目的 {@link Label}（arc 的 markup 由它解析；libgdx 原生 Label 不认）。
     */
    protected static TextButton imageTextButton(String text, Drawable icon, TextButton.TextButtonStyle style, Runnable action){
        TextButton button = new TextButton("", style == null ? Styles.defaultt : style);
        //TextButton 自带的 Label 换成支持 markup 的版本
        button.clearChildren();
        if(icon != null){
            button.add(new Image(icon)).size(Scl.scl(26f)).padLeft(Scl.scl(8f));
        }
        button.add(new Label(resolve(text), Styles.defaultLabel)).pad(Scl.scl(5f));
        if(action != null){
            button.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    action.run();
                }
            });
        }
        return button;
    }

    /** 对应 arc 的 {@code Table.addButton(text, action)} / {@code addButton(text, style, action)}。 */
    protected static TextButton textButton(String text, TextButton.TextButtonStyle style, Runnable action){
        return imageTextButton(text, null, style, action);
    }

    /**
     * 把原版的 {@code "$key"} 标题换成 bundle 文本（缺键回退成 key 本身），并剥掉 arc 的颜色标记 ——
     * libgdx 的 Window 标题用原生 Label 绘制，会把 {@code [accent]} 当字面量画出来。
     */
    protected static String resolve(String text){
        if(text == null) return "";
        String value = text.startsWith("$") ? I18n.get(text.substring(1), text.substring(1)) : text;
        return value.replaceAll("\\[[^\\[\\]]*\\]", "");
    }

    /** @return bundle 文本（带 markup，供项目 Label 使用）；{@code "$key"} 形态才查 bundle。 */
    protected static String bundle(String key){
        if(key == null) return "";
        return key.startsWith("$") ? I18n.get(key.substring(1), key.substring(1)) : key;
    }
}
