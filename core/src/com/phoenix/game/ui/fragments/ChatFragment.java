package com.phoenix.game.ui.fragments;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Fonts;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Time;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 联机聊天界面：左下消息列表 + Enter 呼出输入框。
 * <p>单机（未连服务器）时 Enter 无反应。消息上限 {@link #maxMessages} 条，超出滚动丢弃。
 * 发送经 {@code Vars.netClient.sendChat}；接收经 NetClient 的 onChat 回调 → {@link #addMessage}。
 * <p><b>结构偏离原版</b>：原版 {@code ChatFragment} 是 {@code Table} 子类、消息用 {@code font.getCache()}
 * 直接绘制；本项目是 {@code Fragment} + 一个 {@code root} Table，消息用 Scene2D {@code Label} 渲染。
 * 这里只搬原版的<b>行为</b>：新消息自动显示、逐条淡出、历史导航。
 */
public class ChatFragment extends Fragment {
    /** 消息列表最大保留条数。 */
    private static final int maxMessages = 8;
    /** 同时可见并参与逐条淡出的消息条数（与原版 ChatFragment.messagesShown 同名同值）。 */
    private static final int messagesShown = 10;
    /** 输入框最大长度。 */
    private static final int maxTextLength = 150;

    private Table root;
    private Table list;
    private TextField field;
    private boolean open;
    /** 消息文本（渲染用，直接重建 label 列表，条数少无需复用）。顺序：旧 → 新。 */
    private final Deque<String> messages = new ArrayDeque<>();
    /** 与 {@link #messages} 同序（旧 → 新）的 label，每帧刷新淡出 alpha 用。 */
    private final Array<Label> labels = new Array<>();
    /** 已发送消息历史（新 → 旧；index 0 恒为空串）。对应原版 {@code history}。 */
    private final Array<String> history = new Array<>();
    /** 历史导航位置（0 = 正在编辑新消息）。对应原版 {@code historyPos}。 */
    private int historyPos = 0;
    /** 消息淡出计时：>0 时消息列表保持可见并逐条淡出。对应原版 {@code fadetime}。 */
    private float fadetime = 0f;

    @Override
    public void build(Group parent){
        root = new Table();
        root.setFillParent(true);
        root.bottom().left();
        root.setVisible(false);
        parent.addActor(root);

        list = new Table();
        list.top().left().pad(Scl.scl(4f));

        com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle tfStyle =
            new TextField.TextFieldStyle(Fonts.def, Color.WHITE,
                Styles.flatDown, null, Styles.black6);
        field = new TextField("", tfStyle);
        field.setMaxLength(maxTextLength);
        field.setTextFieldListener((textField, c) -> {
            if(c == '\r' || c == '\n'){
                send();
            }
        });

        root.add(list).bottom().left().row();
        root.add(field).width(Scl.scl(420f)).height(Scl.scl(30f)).pad(Scl.scl(4f)).bottom().left();

        //历史 index 0 恒为空串（对应原版构造器里的 history.insert(0, "")）——
        //clearChatInput() 会 history.set(0, "")，所以这一条必须在任何输入前就存在
        history.insert(0, "");
    }

    /** 每帧由 ClientLauncher 调用：Enter 呼出/收起，Esc 收起，并刷新消息可见性与淡出。 */
    public void update(){
        //消息淡出衰减与可见性刷新**必须每帧跑**（对应原版由 stage 每帧调 act()）：
        //单机 / 断线后仍要能显示系统消息（被踢、连接断开）并按 fadetime 淡出。
        //⚠ 不能把这段放在"未连接就 return"之后 —— 否则单机下消息永远不可见（S4b 批修正）。
        if(fadetime > 0f && !open){
            fadetime -= Time.delta() / 180f;
        }

        boolean connected = Vars.netClient != null && Vars.netClient.isConnected();
        if(!connected){
            if(open) close();
            refresh();
            return;
        }

        //聊天历史导航（对应原版 update() 里 input.keyTap(Binding.chat_history_prev/next)，
        //原版这两个 Binding 分别绑在 UP/DOWN 键上）。
        //⚠ 偏离原版：本项目 DesktopInput 里没有 chat_history_prev/next 这两个 Binding，也没有 arc 的
        //  input.keyTap(...)；这里退化成"仅在聊天框已打开时"直接轮询 Gdx.input，
        //  这样既不用动 DesktopInput，也不会抢走相机移动键（上下键只在 open 时被消费）。
        if(open){
            if(com.badlogic.gdx.Gdx.input.isKeyJustPressed(Input.Keys.UP) && historyPos < history.size - 1){
                if(historyPos == 0) history.set(0, field.getText());
                historyPos++;
                updateChat();
            }
            if(com.badlogic.gdx.Gdx.input.isKeyJustPressed(Input.Keys.DOWN) && historyPos > 0){
                historyPos--;
                updateChat();
            }
        }

        if(com.badlogic.gdx.Gdx.input.isKeyJustPressed(Input.Keys.ENTER)){
            if(open){
                send();
            }else{
                open();
            }
        }else if(open && com.badlogic.gdx.Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)){
            close();
        }

        refresh();
    }

    /** 打开输入框（暂停本地射击输入）。 */
    private void open(){
        open = true;
        root.setVisible(true);
        field.setVisible(true);
        clearChatInput();
        //输入事件由 stage 分发，Enter 键处理时 field 必已在 stage 上，直接抓焦点即可
        if(field.getStage() != null){
            field.getStage().setKeyboardFocus(field);
        }
    }

    /** 关闭输入框并归还键盘焦点。 */
    private void close(){
        open = false;
        clearChatInput();
        if(field.getStage() != null && field.getStage().getKeyboardFocus() == field){
            field.getStage().setKeyboardFocus(null);
        }
        //先收起；随后同一帧的 refresh() 会按 open || fadetime > 0 重新点亮消息列表（关掉输入框后消息还要继续淡出）
        root.setVisible(false);
        field.setVisible(false);
    }

    /** 发送当前输入。以 "/" 开头视为客户端命令（本地执行，不发服务器）。 */
    private void send(){
        String text = field.getText().trim();
        close();
        if(text.isEmpty()) return;

        //把发出的消息插进历史（对应原版 sendMessage 里的 history.insert(1, message)）
        history.insert(1, text);
        clearChatInput();

        if(text.startsWith("/")){
            handleCommand(text.substring(1));
            return;
        }

        Vars.netClient.sendChat(text);
    }

    /** 清空输入框并复位历史导航位置（对应原版 clearChatInput）。 */
    private void clearChatInput(){
        historyPos = 0;
        history.set(0, "");
        field.setText("");
    }

    /** 把历史里的第 {@link #historyPos} 条填回输入框并把光标移到末尾（对应原版 updateChat）。 */
    private void updateChat(){
        field.setText(history.get(historyPos));
        field.setCursorPosition(field.getText().length());
    }

    /** 客户端命令处理（对应原版 registerClientCommands）。 */
    private void handleCommand(String line){
        String[] parts = line.trim().split("\\s+");
        if(parts.length == 0 || parts[0].isEmpty()) return;
        String cmd = parts[0].toLowerCase();

        switch(cmd){
            case "help": {
                addSystem("客户端命令：");
                addSystem("  /help          显示帮助");
                addSystem("  /ping          显示延迟");
                addSystem("  /disconnect    断开连接");
                addSystem("  /reconnect     重连");
                addSystem("服务端命令（需联机）：");
                addSystem("  /t <消息>      仅发给同队玩家");
                addSystem("  /sync          重新同步世界");
                break;
            }
            case "ping": {
                int p = Vars.netClient != null ? Vars.netClient.getPing() : -1;
                addSystem(p < 0 ? "延迟未知（等待心跳）" : "延迟 " + p + " ms");
                break;
            }
            case "disconnect": {
                if(Vars.netClient != null){
                    Vars.netClient.autoReconnect = false;
                    Vars.netClient.disconnect();
                }
                addSystem("已断开连接");
                break;
            }
            case "reconnect": {
                if(Vars.netClient != null && Vars.netClient.reconnect()){
                    addSystem("正在重连…");
                }else{
                    addSystem("无法重连（未连接过）");
                }
                break;
            }
            default: {
                //非内置命令：先交给插件注册的客户端命令
                if(dispatchPluginCommand(cmd, parts)) break;
                //再交给服务端命令（对应原版：客户端把所有 "/" 开头的消息发给服务端，
                //由 NetServer 的命令分发处理 /t、/sync 等；本项目 NetServer.handleCommand）
                if(Vars.netClient != null && Vars.netClient.isConnected()){
                    Vars.netClient.sendChat("/" + line.trim());
                    break;
                }
                addSystem("未知命令: /" + cmd + "（/help 查看）");
            }
        }
    }

    /** 交给插件注册的客户端命令处理。@return 是否已处理 */
    private boolean dispatchPluginCommand(String cmd, String[] parts){
        if(Vars.mods == null) return false;
        com.phoenix.game.mod.CommandHandler handler = new com.phoenix.game.mod.CommandHandler("/");
        Vars.mods.eachClass(mod -> mod.registerClientCommands(handler));
        com.phoenix.game.mod.CommandHandler.Command c = handler.getCommand(cmd);
        if(c == null) return false;

        String[] args = java.util.Arrays.copyOfRange(parts, 1, parts.length);
        c.runner.run(args, this::addSystem);
        return true;
    }

    /** 收到一条消息（由 NetClient.onChat 调用）。 */
    public void addMessage(String name, String message){
        messages.addLast("[" + name + "] " + message);
        while(messages.size() > maxMessages){
            messages.pollFirst();
        }

        //新消息把淡出计时顶起来 → 消息列表自动可见（对应原版 addMessage）
        fadetime += 1f;
        fadetime = Math.min(fadetime, messagesShown) + 1f;

        rebuildList();
    }

    /** 本地提示（连接/断开等系统消息）。 */
    public void addSystem(String text){
        addMessage("*", text);
    }

    /** 重建消息列表。 */
    private void rebuildList(){
        list.clear();
        labels.clear();
        for(String msg : messages){
            Label label = new Label(msg, Styles.defaultLabel);
            label.setAlignment(Align.left);
            list.add(label).left().row();
            labels.add(label);
        }
    }

    /**
     * 每帧刷新可见性与逐条淡出 alpha。
     * <p>对应原版 {@code draw()}：输入框那一行单独受 {@code shown} 控制（{@code chatfield.visible(shown)}），
     * 消息列表在 {@code (i < fadetime || shown)} 时画；第 i 条（0 = 最新）在
     * {@code !shown && fadetime - i < 1f && fadetime - i >= 0f} 时 alpha = {@code (fadetime - i) * opacity}，
     * 否则 alpha = {@code opacity}。
     * <p>原版是 {@code font.getCache().setAlphas(...)}；本项目对 Label 用 {@code getColor().a}（libgdx 的
     * {@code Label.draw} 会把它作为 tint 乘到字形上）。
     */
    private void refresh(){
        float opacity = Core.settings.getInt("chatopacity", 100) / 100f;

        //输入框那一行与消息列表分开控制（原版 chatfield.visible(shown) + 消息按 (i < fadetime || shown) 画）
        root.setVisible(open || fadetime > 0f);
        list.setVisible(open || fadetime > 0f);
        field.setVisible(open);

        int count = labels.size;
        for(int j = 0; j < count; j++){
            //原版索引 i 从 0 = 最新；本项目 messages/labels 是"旧 → 新"，所以要倒过来
            int i = count - 1 - j;
            float alpha;
            if(!open && fadetime - i < 1f && fadetime - i >= 0f){
                alpha = (fadetime - i) * opacity;
            }else{
                alpha = opacity;
            }
            labels.get(j).getColor().a = Math.max(alpha, 0f);
        }
    }
}
