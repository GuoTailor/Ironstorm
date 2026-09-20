package com.phoenix.game.ui.fragments;
import com.phoenix.game.game.EventType;
import com.phoenix.game.ui.Styles;


import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Disableable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.*;
import io.anuke.mindustry.gen.Icon;

public class MenuFragment extends Fragment {
    private Table container, submenu;
    private Button currentMenu;
    private WidgetGroup group;

    public MenuFragment() {
        Core.assets.load("sprites/logo.png", Texture.class);
        Core.assets.finishLoading();
    }

    /** 隐藏整个菜单（进入游戏时调用）。 */
    public void hide(){
        if(group != null){
            group.setVisible(false);
        }
    }

    /** 显示菜单。 */
    public void show(){
        if(group != null){
            group.setVisible(true);
        }
    }

    @Override
    public void build(Group parent) {

        group = new WidgetGroup();
        group.setFillParent(true);
        group.setVisible(true);
        parent.addActor(group);

        //菜单显隐由游戏状态驱动（对应原版 UI 里 menuGroup.visible(() -> state.is(State.menu))）：
        //否则「胜负判定 → Control.menu()」只改状态、没人调 show()，会停在空白画面
        group.addActor(new Act(() -> group.setVisible(Vars.state.isMenu())));

        parent = group;
        Table table = new Table();
        table.setFillParent(true);
        parent.addActor(table);

        container = table;

        buildDesktop();
        Events.on(EventType.ResizeEvent.class, event -> buildDesktop());

        String versionText = "[#fc8140aa]custom build 0";

        Actor actor = new Actor() {
            @Override
            public void draw(Batch batch, float parentAlpha) {
                Texture logo = Core.assets.get("sprites/logo.png");
                float logoscl = Scl.scl(1);
                float logow = Math.min(logo.getWidth() * logoscl, Core.graphics.getWidth() - Scl.scl(20));
                float logoh = logow * (float) logo.getHeight() / logo.getWidth();

                float fx = (int) (Core.graphics.getWidth() / 2f);
                float fy = (int) (Core.graphics.getHeight() - 6 - logoh) + logoh / 2 - (Core.graphics.getWidth() < Core.graphics.getHeight() ? Scl.scl(30f) : 0f);
                Core.batch.setPackedColor(Color.WHITE_FLOAT_BITS);
                Draw.color();
                Draw.rect(Draw.wrap(logo), fx, fy, logow, logoh);

                Fonts.def.setColor(Color.WHITE);
                Fonts.def.draw(Core.batch, versionText, fx, fy - logoh / 2f, 0.0F, Align.center, false);
            }
        };
        parent.addActor(actor);
        actor.setTouchable(Touchable.disabled);
    }

    private void buildDesktop() {
        container.clear();
        container.setSize(Core.graphics.getWidth(), Core.graphics.getHeight());

        float width = 200f;
        Drawable background = Styles.black6;

        container.left();
        container.add().width(Core.graphics.getWidth() / 10f);
        Table table = new Table();
        table.setBackground(background);
        table.align(1);
        Cell<Table> add = container.add(table);
        table.defaults().width(width).height(60f);

        buttons(table,
                new Buttoni("play", Icon.play2,
                        new Buttoni("campaign", Icon.play2, () -> {
                            // 隐藏菜单并加载地图，进入战役模式
                            hide();
                            Vars.control.play();
                        }),
                        new Buttoni("joingame", Icon.add, this::showJoinDialog),
                        new Buttoni("customgame", Icon.editor, () -> {}),
                        new Buttoni("loadgame", Icon.load, this::showLoadDialog)
                ),
                new Buttoni("editor", Icon.editor, () -> {}),
                new Buttoni("settings", Icon.tools, () -> {}),
                new Buttoni("about.button", Icon.info, () -> {
                    System.out.println("about.button===========");
                }),
                new Buttoni("quit", Icon.exit, Core.app::exit)
        );

        add.width(width).growY();
        Table table2 = new Table();
        table2.setBackground(background);
        table2.align(1);
        Cell<Table> add2 = container.add(table2);
        submenu = table2;
        table2.getColor().a = 0f;
        table2.top();
        table2.defaults().width(width).height(70f);
        table2.addActor(new Act(() -> table2.setVisible(!table2.getChildren().isEmpty())));
//        table2.setVisible(true);
        add2.width(width).growY();
    }

    private void fadeInMenu() {
        submenu.clearActions();
        submenu.addAction(Actions.sequence(Actions.alpha(1f, 0.15f, Interpolation.fade)));
        System.out.println("fadeInMenu");
    }

    /** 弹出读档对话框：列出全部存档槽位，点选即读档进入战场。 */
    private void showLoadDialog(){
        if(Vars.saves == null) return;

        com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle winStyle =
            new com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle(com.phoenix.game.core.Fonts.def,
                com.phoenix.game.graphics.Pal.accent, Styles.defaultDialog.background);
        com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog = new com.badlogic.gdx.scenes.scene2d.ui.Dialog("读档", winStyle);

        Table list = new Table();
        list.top().left();

        com.badlogic.gdx.utils.Array<com.phoenix.game.game.Saves.SaveSlot> slots = Vars.saves.getSlots();
        if(slots.size == 0){
            list.add(new Label("暂无存档", Styles.defaultLabel));
        }else{
            for(com.phoenix.game.game.Saves.SaveSlot slot : slots){
                Button b = new Button(Styles.defaultb);
                Label label = new Label(slot.toString(), Styles.defaultLabel);
                label.setAlignment(Align.left);
                b.add(label).pad(Scl.scl(5f)).growX();
                b.addListener(new ClickListener(){
                    @Override
                    public void clicked(InputEvent event, float x, float y){
                        dialog.hide();
                        if(Vars.control != null){
                            hide();
                            Vars.control.load(slot);
                        }
                    }
                });
                list.add(b).width(Scl.scl(320f)).pad(Scl.scl(2f)).row();
            }
        }

        dialog.getContentTable().add(list).pad(Scl.scl(10f));
        //注意：Dialog 由 WindowStyle 构造（skin 为 null），不能调用 dialog.button(...)——
        //libgdx 该方法依赖 skin，会抛 IllegalStateException 直接崩掉渲染线程
        Button close = new Button(Styles.defaultb);
        close.add(new Label("关闭", Styles.defaultLabel)).pad(Scl.scl(5f));
        close.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                dialog.hide();
            }
        });
        dialog.getButtonTable().add(close).width(Scl.scl(120f)).height(Scl.scl(40f)).pad(Scl.scl(4f));
        if(group != null && group.getStage() != null){
            com.badlogic.gdx.scenes.scene2d.Stage st = group.getStage();
            dialog.show(st);
            //同 showJoinDialog：pack 后重新居中，避免 hit 区域错位
            dialog.pack();
            dialog.setPosition((st.getWidth() - dialog.getWidth()) / 2f,
                               (st.getHeight() - dialog.getHeight()) / 2f);
        }
    }

    /** 弹出加入服务器对话框：输入 IP:端口与玩家名，连接成功进入联机模式。 */
    private void showJoinDialog(){
        com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle winStyle =
            new com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle(com.phoenix.game.core.Fonts.def,
                com.phoenix.game.graphics.Pal.accent, Styles.defaultDialog.background);
        com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog = new com.badlogic.gdx.scenes.scene2d.ui.Dialog("加入服务器", winStyle);

        Table form = new Table();
        form.top().left().pad(Scl.scl(6f));

        com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle tfStyle =
            new com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle(Fonts.def, Color.WHITE,
                Styles.flatDown, null, Styles.black6);

        TextField ipField = new TextField("127.0.0.1:6567", tfStyle);
        TextField nameField = new TextField("player", tfStyle);
        Label status = new Label("", Styles.defaultLabel);

        form.add(new Label("地址(IP:端口)", Styles.defaultLabel)).left().row();
        form.add(ipField).width(Scl.scl(260f)).pad(Scl.scl(2f)).row();
        form.add(new Label("玩家名", Styles.defaultLabel)).left().padTop(Scl.scl(4f)).row();
        form.add(nameField).width(Scl.scl(260f)).pad(Scl.scl(2f)).row();

        //LAN 发现：后台线程扫描，结果回主线程刷新列表
        Table serverList = new Table();
        serverList.top().left();
        Label discoverHint = new Label("正在扫描局域网…", Styles.defaultLabel);
        form.add(discoverHint).left().padTop(Scl.scl(6f)).row();
        form.add(serverList).width(Scl.scl(280f)).left().row();

        new Thread(() -> {
            java.util.List<com.phoenix.game.net.Discovery.ServerInfo> found =
                com.phoenix.game.net.Discovery.discover();
            com.badlogic.gdx.Gdx.app.postRunnable(() -> {
                serverList.clear();
                if(found.isEmpty()){
                    discoverHint.setText("未发现局域网服务器（可手动填地址）");
                    return;
                }
                discoverHint.setText("发现的服务器（点击填入）：");
                for(com.phoenix.game.net.Discovery.ServerInfo info : found){
                    Button b = new Button(Styles.defaultb);
                    b.add(new Label(info.toString(), Styles.defaultLabel)).pad(Scl.scl(4f));
                    b.addListener(new ClickListener(){
                        @Override
                        public void clicked(InputEvent event, float x, float y){
                            ipField.setText(info.address + ":" + info.port);
                        }
                    });
                    serverList.add(b).width(Scl.scl(270f)).pad(Scl.scl(2f)).row();
                }
            });
        }, "LAN Discover").start();

        form.add(status).left().padTop(Scl.scl(4f)).row();

        dialog.getContentTable().add(form);

        //连接回调：成功隐藏菜单，失败显示错误
        Runnable doConnect = () -> {
            String addr = ipField.getText().trim();
            String name = nameField.getText().trim();
            if(addr.isEmpty() || name.isEmpty()){
                status.setText("请填写地址与玩家名");
                return;
            }
            String ip = addr;
            int port = com.phoenix.game.Vars.port;
            int colon = addr.lastIndexOf(':');
            if(colon > 0){
                ip = addr.substring(0, colon);
                try{
                    port = Integer.parseInt(addr.substring(colon + 1));
                }catch(NumberFormatException e){
                    status.setText("端口格式错误");
                    return;
                }
            }
            status.setText("连接中…");
            connectTo(ip, port, name, status, dialog);
        };

        Button ok = new Button(Styles.defaultb);
        ok.add(new Label("连接", Styles.defaultLabel)).pad(Scl.scl(5f));
        ok.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                doConnect.run();
            }
        });
        Button close = new Button(Styles.defaultb);
        close.add(new Label("取消", Styles.defaultLabel)).pad(Scl.scl(5f));
        close.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                dialog.hide();
            }
        });
        dialog.getButtonTable().add(ok).width(Scl.scl(110f)).height(Scl.scl(40f)).pad(Scl.scl(4f));
        dialog.getButtonTable().add(close).width(Scl.scl(110f)).height(Scl.scl(40f)).pad(Scl.scl(4f));
        if(group != null && group.getStage() != null){
            com.badlogic.gdx.scenes.scene2d.Stage st = group.getStage();
            dialog.show(st);
            //show() 内部的居中用的可能是 pack 前的旧尺寸，这里 pack 后重新居中，
            //否则窗口尺寸变化后对话框 hit 区域与渲染位置错开（点不到按钮/点空白即关闭）
            dialog.pack();
            dialog.setPosition((st.getWidth() - dialog.getWidth()) / 2f,
                               (st.getHeight() - dialog.getHeight()) / 2f);
        }
    }

    /** 发起连接（成功进入联机世界，失败在状态标签显示）。status/dialog 为加入对话框的 UI 反馈，自动化加入时传 null。 */
    private void connectTo(String ip, int port, String name, Label status, com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog){
        if(Vars.netClient == null){
            Vars.netClient = new com.phoenix.game.core.NetClient();
        }
        Vars.netClient.onWorldLoaded = () -> {
            if(dialog != null) dialog.hide();
            hide();
        };
        Vars.netClient.onChat = (sender, text) -> {
            if(com.phoenix.game.Vars.hud != null){
                //聊天消息由 ChatFragment 展示（经 ClientLauncher 挂接；这里仅保底打印）
            }
        };
        Vars.netClient.onKick = reason -> {
            //断线/被踢：回菜单（清理世界与本地状态）
            System.out.println("[菜单] 被踢/断开: " + reason);
            com.phoenix.game.Vars.control.menu();
            show();
        };
        Vars.netClient.connect(ip, port, name);
        //简单超时提示（仅加入对话框需要）
        if(status != null){
            com.phoenix.game.core.Time.run(60f * 5f, () -> {
                if(Vars.netClient != null && !Vars.netClient.isConnected()){
                    status.setText("连接超时或失败");
                }
            });
        }
    }

    /** 调试/自动化入口：跳过加入对话框直接连接（对应环境变量 PHOENIX_JOIN=ip[:port]）。 */
    public void joinServer(String ip, int port, String name){
        System.out.println("[菜单] PHOENIX_JOIN 自动加入 " + ip + ":" + port);
        connectTo(ip, port, name, null, null);
    }

    private void fadeOutMenu() {
        //nothing to fade out
        if (submenu.getChildren().isEmpty()) {
            return;
        }

        submenu.clearActions();
        submenu.addAction(Actions.sequence(Actions.alpha(1f),
                Actions.alpha(0f, 0.2f, Interpolation.fade),
                Actions.run(() -> submenu.clearChildren())));
        System.out.println("fadeOutMenu");
    }

    private void buttons(Table t, Buttoni... buttons) {

        for (Buttoni b : buttons) {
            if (b == null) continue;
            TextButton button = new TextButton(Core.bundle.get(b.text), Styles.clearToggleMenut);
            button.add(new Image(b.icon)).size(b.icon.getMinWidth());
            button.getCells().reverse();
            ClickListener listener = new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    System.out.println(event + " " + x + " " + y);
                    if ((!(t instanceof Disableable) || !((Disableable) t).isDisabled())) {
                        if (currentMenu == button) {
                            button.setChecked(false);
                            fadeOutMenu();
                        } else {
                            if (b.submenu != null) {
                                currentMenu = button;
                                button.setChecked(true);
                                submenu.clearChildren();
                                fadeInMenu();
                                //correctly offset the button
                                submenu.add().height((Core.graphics.getHeight() - button.getY(Align.topLeft)) / Scl.scl(1f));
                                submenu.row();
                                buttons(submenu, b.submenu);
                            } else {
                                if (currentMenu != null) {
                                    currentMenu.setChecked(false);
                                }
                                currentMenu = button;
//                                currentMenu = null;
                                button.setChecked(false);
                                fadeOutMenu();
                                b.runnable.run();
                            }
                        }
                    }
                    super.clicked(event, x, y);
                }
            };
            button.addListener(listener);
            listener.setButton(Input.Buttons.LEFT);

            t.add(button);
//            button.addActor(new Act(() -> button.setChecked(currentMenu == button)));
            t.row();
        }
    }

    private class Buttoni {
        final Drawable icon;
        final String text;
        final Runnable runnable;
        final Buttoni[] submenu;

        public Buttoni(String text, Drawable icon, Runnable runnable) {
            this.icon = icon;
            this.text = text;
            this.runnable = runnable;
            this.submenu = null;
        }

        public Buttoni(String text, Drawable icon, Buttoni... buttons) {
            this.icon = icon;
            this.text = text;
            this.runnable = () -> {
            };
            this.submenu = buttons;
        }
    }

    public static class Act extends Actor {
        private final Runnable update;

        public Act(Runnable update) {
            this.update = update;
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            if (update != null) {
                update.run();
            }
        }
    }
}
