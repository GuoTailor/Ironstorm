package com.phoenix.game.ui.fragments;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Align;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Fonts;
import com.phoenix.game.core.Scl;
import com.phoenix.game.ui.Styles;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 联机聊天界面：左下消息列表 + Enter 呼出输入框。
 * <p>单机（未连服务器）时 Enter 无反应。消息上限 {@link #maxMessages} 条，超出滚动丢弃。
 * 发送经 {@code Vars.netClient.sendChat}；接收经 NetClient 的 onChat 回调 → {@link #addMessage}。
 */
public class ChatFragment extends Fragment {
    /** 消息列表最大保留条数。 */
    private static final int maxMessages = 8;
    /** 输入框最大长度。 */
    private static final int maxTextLength = 150;

    private Table root;
    private Table list;
    private TextField field;
    private boolean open;
    /** 消息文本（渲染用，直接重建 label 列表，条数少无需复用）。 */
    private final Deque<String> messages = new ArrayDeque<>();

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
    }

    /** 每帧由 ClientLauncher 调用：Enter 呼出/收起，Esc 收起。 */
    public void update(){
        if(Vars.netClient == null || !Vars.netClient.isConnected()){
            if(open) close();
            return;
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
    }

    /** 打开输入框（暂停本地射击输入）。 */
    private void open(){
        open = true;
        root.setVisible(true);
        field.setText("");
        //输入事件由 stage 分发，Enter 键处理时 field 必已在 stage 上，直接抓焦点即可
        if(field.getStage() != null){
            field.getStage().setKeyboardFocus(field);
        }
    }

    /** 关闭输入框并归还键盘焦点。 */
    private void close(){
        open = false;
        root.setVisible(false);
        if(field.getStage() != null && field.getStage().getKeyboardFocus() == field){
            field.getStage().setKeyboardFocus(null);
        }
    }

    /** 发送当前输入。以 "/" 开头视为客户端命令（本地执行，不发服务器）。 */
    private void send(){
        String text = field.getText().trim();
        close();
        if(text.isEmpty()) return;

        if(text.startsWith("/")){
            handleCommand(text.substring(1));
            return;
        }

        Vars.netClient.sendChat(text);
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
                //非内置命令：交给插件注册的客户端命令
                if(!dispatchPluginCommand(cmd, parts)){
                    addSystem("未知命令: /" + cmd + "（/help 查看）");
                }
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
        rebuildList();
    }

    /** 本地提示（连接/断开等系统消息）。 */
    public void addSystem(String text){
        addMessage("*", text);
    }

    /** 重建消息列表。 */
    private void rebuildList(){
        list.clear();
        for(String msg : messages){
            Label label = new Label(msg, Styles.defaultLabel);
            label.setAlignment(Align.left);
            list.add(label).left().row();
        }
    }
}