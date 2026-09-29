package com.phoenix.game.ui.fragments;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Align;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Fill;
import com.phoenix.game.core.Fonts;
import com.phoenix.game.core.Scl;
import com.phoenix.game.input.Binding;
import com.phoenix.game.input.KeyBinds;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.struct.Array;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;

/**
 * 脚本控制台（开发者用，F8 开关）。参照 Mindustry mindustry.ui.fragments.ScriptConsoleFragment 移植。
 * <p>左下角一个输入框 + 上方最多 {@link #messagesShown} 行历史输出；回车执行一行脚本
 * （原版把脚本交给 rhino 的 {@code Scripts.runConsole}），上下方向键翻历史。
 *
 * <p><b>临时绕过 / 未移植</b>：
 * <ul>
 *   <li>{@code mods.getScripts().runConsole(message)}：原版链路是 {@code Mods.getScripts()} →
 *       {@code Vars.platform.createScripts()} → rhino。本项目 {@code mod/Scripts} <b>存在但已大幅降级</b>
 *       （rhino 的 {@code org.mozilla.javascript.*} 未移植，{@code core/build.gradle} 无 JS 引擎依赖），
 *       且 <b>{@code Vars.platform} 不存在</b>（{@code core/Platform} 是无实例的死接口）→ 拿不到
 *       {@code Mods.getScripts()}（{@code Mods} 里也没有该访问器）→ {@link #runConsole(String)} 返回一句提示；</li>
 *   <li>移动端软键盘（{@code Net.TextInput} + {@code Core.input.getTextInput}）未移植 → 刻意降级：
 *       libgdx 的 {@code TextField} 被 {@code Stage.setKeyboardFocus} 聚焦后，Android 上会自动弹出
 *       系统软键盘，原版那段 {@code TextInput} 包装在本项目里是多余的（{@link #toggle()} 移动端分支因此为空）；</li>
 *   <li>{@code chatfield.fireClick()}：libgdx 的 TextField 没有该方法 → 用 {@code Stage.setKeyboardFocus}（等价：都是让输入框获得焦点）；</li>
 *   <li>{@code scene.getStyle(TextField.TextFieldStyle.class)}：arc 的 Scene 按类型取样式，本项目没有
 *       Scene → 直接用项目的样式表 {@code Styles.defaultField}；</li>
 *   <li>{@code input.keyTap(Binding.xxx)}：本项目 {@code Core.input} 是 libgdx 的 Input，没有按 Binding
 *       查询的接口（{@code KeyBinds} 只保留了类型定义，无"Binding → 当前键码"表）→ 按键走
 *       {@link #keyTap(Binding)}，读 {@link Binding#defaultValue} 的默认键码。因为项目里没有键位重绑定
 *       界面/持久化，默认键码就是实际生效键码，行为等价；</li>
 *   <li>{@code input.axis(Binding.chat_scroll)}：滚轮轴在本项目里由 {@code InputHandler.scrolled} 累积、
 *       由 {@code DesktopInput.update()} 每帧用 {@code scroll()} <b>取走并清零</b>（比 {@code act()} 早），
 *       且 {@code ClientLauncher.scrollGate()} 只把滚轮派发给"鼠标下的 ScrollPane"（本 Fragment 不是
 *       ScrollPane）→ 控制台拿不到滚轮，历史消息只能靠上下方向键翻；</li>
 *   <li>{@code Element.visible(Boolp)} / {@code Element.update(Runnable)} 这两个 arc 的每帧回调 libgdx 没有
 *       → 统一放进 {@link #act(float)}；</li>
 *   <li>{@code Core.scene}（arc 的 Scene）没有全局引用 → 一律通过 {@code getStage()} 取；</li>
 *   <li>{@code Table.margin(...)}（外间距）libgdx 没有 → 用 {@code pad}；{@code getMarginBottom()} → {@code getPadBottom()}；</li>
 *   <li><b>{@code Fill.crect(x, y, w, h)}</b>的 (x, y) 是左下角，本项目 {@code Fill.rect} 是中心 → 两处调用都换算过。</li>
 * </ul>
 * <p>另外原文件同时 {@code import static arc.Core.*} 与 {@code import static mindustry.Vars.*}，两边都有 {@code net}
 * 成员；本项目为了避免同名静态导入的歧义，全部写成 {@code Core.xxx} / {@code Vars.xxx} 限定形式。
 */
public class ScriptConsoleFragment extends Table{
    private final static int messagesShown = 30;
    private Array<String> messages = new Array<>();
    private boolean open = false, shown;
    private TextField chatfield;
    private Label fieldlabel = new Label(">", Styles.defaultLabel);
    private BitmapFont font;
    private GlyphLayout layout = new GlyphLayout();
    private float offsetx = Scl.scl(4), offsety = Scl.scl(4), fontoffsetx = Scl.scl(2), chatspace = Scl.scl(50);
    private Color shadowColor = new Color(0, 0, 0, 0.4f);
    private float textspacing = Scl.scl(10);
    private Array<String> history = new Array<>();
    private int historyPos = 0;
    private int scrollPos = 0;
    private Fragment container = new Fragment(){
        @Override
        public void build(Group parent){
            //TODO 原版 scene.add(ScriptConsoleFragment.this)（arc 的 Scene.add）；本项目没有 Core.scene
            //     → 从传入的父组取 stage 再挂上去
            if(parent.getStage() != null){
                parent.getStage().addActor(ScriptConsoleFragment.this);
            }
        }
    };

    public ScriptConsoleFragment(){

        setFillParent(true);
        font = Fonts.def;

        //TODO 原版这里写的是 visible(() -> {...})（arc 的每帧可见性回调）；libgdx 没有 → 见 act(float)

        history.insert(0, "");
        setup();
    }

    /**
     * 每帧驱动（原版拆在 {@code visible(Boolp)} 与 {@code update(Runnable)} 两个回调里）。
     */
    @Override
    public void act(float delta){
        super.act(delta);

        // ---- 原版 visible(() -> {...}) ----
        if(keyTap(Binding.console) && (keyboardFocus() == chatfield || keyboardFocus() == null)){
            shown = !shown;
            if(shown && !open && Vars.enableConsole){
                toggle();
            }
            clearChatInput();
        }
        //TODO 原版 return shown && !Vars.net.active()（Vars.net 在本项目实际不会被赋值）
        //     → 用 Vars.isClient()/isServer() 判联机
        setVisible(shown && !(Vars.isClient() || Vars.isServer()));

        // ---- 原版 update(() -> {...}) ----
        if(keyTap(Binding.chat) && Vars.enableConsole && (keyboardFocus() == chatfield || keyboardFocus() == null)){
            toggle();
        }

        if(open){
            if(keyTap(Binding.chat_history_prev) && historyPos < history.size - 1){
                if(historyPos == 0) history.set(0, chatfield.getText());
                historyPos++;
                updateChat();
            }
            if(keyTap(Binding.chat_history_next) && historyPos > 0){
                historyPos--;
                updateChat();
            }
        }

        //原版 scrollPos = (int)Mathf.clamp(scrollPos + input.axis(Binding.chat_scroll), 0,
        //          Math.max(0, messages.size - messagesShown));
        // —— 滚轮拿不到：项目里滚轮由 InputHandler.scrolled 累积，DesktopInput.update()（早于本类的 act()）
        //    每帧用 scroll() 取走并清零；且 ClientLauncher.scrollGate() 只把滚轮派发给鼠标下的 ScrollPane，
        //    本 Fragment 不是 ScrollPane → 保持 scrollPos 不变，翻历史只能靠 chat_history_prev/next（上/下方向键）
        scrollPos = (int)Mathf.clamp(scrollPos, 0, Math.max(0, messages.size - messagesShown));
    }

    /**
     * 按键判定（对应原版 {@code input.keyTap(Binding)}）。
     * <p>F15 起经 {@link KeyBinds} 查生效键码（默认值 == 未重绑时的生效值）；
     * 鼠标键/滚轮在本项目用负数编码（见 KeyCode 类注释），libgdx 的 isKeyJustPressed 只认键盘键码。
     */
    private static boolean keyTap(Binding binding){
        int key = KeyBinds.key(binding);
        return key >= 0 && Gdx.input.isKeyJustPressed(key);
    }

    /** @return 当前舞台的键盘焦点（对应原版 {@code scene.getKeyboardFocus()}）。 */
    private com.badlogic.gdx.scenes.scene2d.Actor keyboardFocus(){
        return getStage() == null ? null : getStage().getKeyboardFocus();
    }

    public Fragment container(){
        return container;
    }

    public void clearMessages(){
        messages.clear();
        history.clear();
        history.insert(0, "");
    }

    private void setup(){
        fieldlabel.setStyle(new Label.LabelStyle(fieldlabel.getStyle()));
        fieldlabel.getStyle().font = font;
        fieldlabel.setStyle(fieldlabel.getStyle());

        //TODO 原版 new TextField("", new TextField.TextFieldStyle(scene.getStyle(TextField.TextFieldStyle.class)))
        //  —— arc 的 Scene.getStyle(Class) 本项目没有 → 用样式表里的 Styles.defaultField
        chatfield = new TextField("", new TextField.TextFieldStyle(Styles.defaultField));
        chatfield.setMaxLength(Vars.maxTextLength);
        chatfield.getStyle().background = null;
        chatfield.getStyle().font = Fonts.chat;
        chatfield.getStyle().fontColor = Color.WHITE;
        chatfield.setStyle(chatfield.getStyle());

        //TODO 原版 bottom().left().marginBottom(offsety).marginLeft(offsetx * 2)（margin 是外间距）→ 用 pad
        bottom().left().padBottom(offsety).padLeft(offsetx * 2).add(fieldlabel).padBottom(Scl.scl(6f));

        add(chatfield).padBottom(offsety).padLeft(offsetx).growX().padRight(offsetx).height(Scl.scl(28f));
    }

    @Override
    public void draw(Batch batch, float parentAlpha){
        float opacity = 1f;
        float textWidth = Core.graphics.getWidth() - offsetx*2f;

        Draw.color(shadowColor);

        if(open){
            //TODO 原版 Fill.crect(offsetx, chatfield.getY(), w, h)（左下角）；本项目 Fill.rect 是中心
            float w = chatfield.getWidth() + 15f, h = chatfield.getHeight() - 1;
            Fill.rect(offsetx + w / 2f, chatfield.getY() + h / 2f, w, h);
        }

        super.draw(batch, parentAlpha);

        float spacing = chatspace;

        chatfield.setVisible(open);
        fieldlabel.setVisible(open);

        Draw.color(shadowColor);
        Draw.alpha(shadowColor.a * opacity);

        //TODO 原版 getMarginBottom()（arc 的 Table.margin）→ libgdx 是 getPadBottom()
        float theight = offsety + spacing + getPadBottom();
        for(int i = scrollPos; i < messages.size && i < messagesShown + scrollPos; i++){

            layout.setText(font, messages.get(i), Color.WHITE, textWidth, Align.bottomLeft, true);
            theight += layout.height + textspacing;
            if(i - scrollPos == 0) theight -= textspacing + 1;

            font.getCache().clear();
            //TODO 原版 font.getCache().draw() 无参版本（用缓存自己的 batch）；libgdx 的 BitmapFontCache
            //     只有 draw(Batch)/draw(Batch, int, int)/draw(Batch, float) → 这里显式传 Core.batch
            font.getCache().addText(messages.get(i), fontoffsetx + offsetx, offsety + theight, textWidth, Align.bottomLeft, true);

            if(!open){
                font.getCache().setAlphas(opacity);
                Draw.color(0, 0, 0, shadowColor.a * opacity);
            }else{
                font.getCache().setAlphas(opacity);
            }

            //TODO Fill.crect → Fill.rect（中心坐标）
            float bw = textWidth + Scl.scl(4f), bh = layout.height + textspacing;
            Fill.rect(offsetx + bw / 2f, theight - layout.height - 2 + bh / 2f, bw, bh);
            Draw.color(shadowColor);
            Draw.alpha(opacity * shadowColor.a);

            font.getCache().draw(Core.batch);
        }

        Draw.color();
    }

    private void sendMessage(){
        String message = chatfield.getText();
        clearChatInput();

        if(message.replaceAll(" ", "").isEmpty()) return;

        history.insert(1, message);

        addMessage("[lightgray]> " + message.replace("[", "[["));
        addMessage(runConsole(message).replace("[", "[["));
    }

    /**
     * 执行一行控制台脚本（对应原版 {@code mods.getScripts().runConsole(message)}）。
     * <p>原版链路 {@code Mods.getScripts()} → {@code Vars.platform.createScripts()} → rhino。
     *     {@code com.phoenix.game.mod.Scripts} <b>已移植但为降级版</b>（{@code runConsole} 恒返回固定提示、
     *     构造器直接置 {@code errored = true}），而 {@code Vars.platform} 不存在、{@code Mods} 也没有
     *     {@code getScripts()} 访问器 → 这里就地返回提示，不去接 {@code Scripts}（接了也只是换一句同样的话）。
     */
    private String runConsole(String message){
        return "console unavailable (Scripts not ported)";
    }

    public void toggle(){

        if(!open){
            //TODO 原版 scene.setKeyboardFocus(chatfield)（本项目没有 Core.scene）→ 从 Actor 取 stage
            if(getStage() != null) getStage().setKeyboardFocus(chatfield);
            open = !open;
            if(Vars.mobile){
                //刻意降级：原版：TextInput input = new TextInput(); ... Core.input.getTextInput(input)
                //  —— mindustry.net.Net.TextInput 与 Core.input.getTextInput 均未移植（见 core/Platform.java），
                //     但 libgdx 的 TextField 在 Android 上被 setKeyboardFocus 聚焦后会自动弹系统软键盘，
                //     所以这里不需要额外做软键盘包装。
            }else{
                //原版 chatfield.fireClick()（arc 的 TextField 方法，libgdx 的 TextField 没有）→ 无需等价物：
                //  上面 setKeyboardFocus(chatfield) 已经让输入框获得焦点，fireClick 在原版也是同一效果。
            }
        }else{
            //TODO 原版 scene.setKeyboardFocus(null) → 从 Actor 取 stage
            if(getStage() != null) getStage().setKeyboardFocus(null);
            open = !open;
            scrollPos = 0;
            sendMessage();
        }
    }

    public void hide(){
        //TODO 原版 scene.setKeyboardFocus(null) → 从 Actor 取 stage
        if(getStage() != null) getStage().setKeyboardFocus(null);
        open = false;
        clearChatInput();
    }

    public void updateChat(){
        chatfield.setText(history.get(historyPos));
        chatfield.setCursorPosition(chatfield.getText().length());
    }

    public void clearChatInput(){
        historyPos = 0;
        history.set(0, "");
        chatfield.setText("");
    }

    public boolean open(){
        return open;
    }

    public void addMessage(String message){
        messages.insert(0, message);
    }
}
