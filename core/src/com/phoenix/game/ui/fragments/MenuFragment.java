package com.phoenix.game.ui.fragments;
import com.phoenix.game.game.EventType;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.MobileButton;
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
    /** 主菜单背景渲染器（懒创建，见 {@link #renderer()}）。 */
    private com.phoenix.game.graphics.MenuRenderer menuRenderer;

    public MenuFragment() {
        Core.assets.load("sprites/logo.png", Texture.class);
        Core.assets.finishLoading();
    }

    /**
     * 主菜单背景渲染器（对应原版 {@code MenuFragment} 里 `parent.fill((x,y,w,h) -> renderer.render())` 用的
     * {@code renderer} 字段）。
     * <p>⚠ <b>必须懒创建</b>：本 Fragment 的构造发生在**内容加载之前**
     * （{@code ClientLauncher} 里 {@code new MenuFragment()} 早于 {@code Vars.content.load()}），
     * 而 {@code MenuRenderer} 的构造要用 {@code Blocks}/{@code UnitTypes}（那时还是 null）→ 会崩。
     */
    public com.phoenix.game.graphics.MenuRenderer renderer(){
        if(menuRenderer == null) menuRenderer = new com.phoenix.game.graphics.MenuRenderer();
        return menuRenderer;
    }

    /** 释放菜单背景占用的 FBO（对应原版 {@code MenuFragment.dispose()}）。 */
    public void dispose(){
        if(menuRenderer != null){
            menuRenderer.dispose();
            menuRenderer = null;
        }
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

        //桌面/移动端布局二选一（原版是 if(!mobile) buildDesktop() else buildMobile()）：
        //Vars.mobile 由 Vars.init() 按平台算好，启动后不再变
        if(!Vars.mobile){
            buildDesktop();
            Events.on(EventType.ResizeEvent.class, event -> buildDesktop());
        }else{
            buildMobile();
            Events.on(EventType.ResizeEvent.class, event -> buildMobile());
        }

        //移动端专属角落按钮（对应原版 build() 里的 if(mobile) 段）：
        //  parent.fill(c -> c.bottom().left().addButton("", Styles.infot, ui.about::show).size(84, 45))
        //本项目没有 arc 的 Parent.fill(Cons)/addButton → 自建整屏 Table + TextButton + ClickListener。
        //原版紧随其后的 else if(becontrol.active()) 更新提示按钮：本项目没有 becontrol → 不移植。
        if(Vars.mobile){
            addCornerButton(Styles.infot, Align.bottomLeft, () -> ui(u -> u.about.show()));
            addCornerButton(Styles.discordt, Align.bottomRight, () -> ui(u -> u.discord.show()));
        }

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

    /**
     * 移动端菜单布局（对应原版 MenuFragment.buildMobile()）。
     * 120f 方形图标按钮：横屏两行（4 + 4，第二行嵌套 Table、colspan(4)），竖屏 2 列 × 4 行（末尾嵌套 Table 2 个、colspan(2)）。
     */
    private void buildMobile(){
        container.clear();
        container.setSize(Core.graphics.getWidth(), Core.graphics.getHeight());

        float size = 120f;
        container.defaults().size(size).pad(5).padTop(4f);

        //文案必须显式 Core.bundle.get(键)：原版 MobileButton 收 "$campaign" 这类串、靠 arc 的 Table.add(String)
        //自动解析 bundle，本项目的 MobileButton 是 new Label(text, ...)、一个字都不解析（传 "$campaign" 会显示 ???campaign???）。
        //图标沿用桌面端已确立的替代约定（原版的 icon font 管线未移植，见 buildDesktop 里 mods 的注释）：
        //  rightOpenOut(自定义游戏)→Icon.terrain、download(载入游戏)→Icon.load、book(模组)→Icon.folder。
        //原版的 checkPlay(...) 包裹（mod 内容出错时拦截）本项目没有 → 直接调动作。
        MobileButton
            play   = new MobileButton(Icon.play,     Core.bundle.get("campaign"),   () -> { hide(); ui(u -> u.deploy.show()); }),
            custom = new MobileButton(Icon.terrain,  Core.bundle.get("customgame"), () -> ui(u -> u.custom.show())),
            maps   = new MobileButton(Icon.load,     Core.bundle.get("loadgame"),   this::showLoadDialog),
            join   = new MobileButton(Icon.add,      Core.bundle.get("joingame"),   this::showJoinDialog),
            editor = new MobileButton(Icon.terrain,  Core.bundle.get("editor"),     () -> ui(u -> u.maps.show())),
            tools  = new MobileButton(Icon.settings, Core.bundle.get("settings"),   () -> ui(u -> u.settings.show())),
            mods   = new MobileButton(Icon.folder,   Core.bundle.get("mods"),       () -> ui(u -> u.mods.show())),
            exit   = new MobileButton(Icon.exit,     Core.bundle.get("quit"),       Core.app::exit);
        //注：原版的 donate(website) 按钮在 126.2 里创建了却从没被 add（那行被注释掉）→ 不建，避免死变量

        if(!isPortrait()){
            //⚠ 适配：arc 的 Table.marginTop(N) 是"把整块内容下移 N"；libgdx 的 Table **没有 margin**，
            //  改用 padTop(N)——居中布局下它把内容区上边收 N，内容实际下移 N/2（这里 30）。
            //  视觉作用相同（给顶部 logo 让位），不再精算那 30px。
            container.padTop(60f);
            container.add(play);
            container.add(join);
            container.add(custom);
            container.add(maps);
            container.row();

            //原版是 container.table(table -> { table.defaults().set(container.defaults()); ... }).colspan(4)：
            //  arc 的 Table.table(Cons) 本项目没有 → 显式 new Table()；arc 的 Cell.set(Cell) 在 libgdx 里是
            //  包级私有（Cell.java:934 void set(Cell)）→ 在嵌套表上把同样的 defaults 显式重写一遍
            Table inner = new Table();
            inner.defaults().size(size).pad(5).padTop(4f);
            inner.add(editor);
            inner.add(tools);
            inner.add(mods);
            inner.add(exit);
            container.add(inner).colspan(4);
        }else{
            //原版这里写 marginTop(0f)（竖屏本来就有 4 行、不需要额外让位）→ 本项目不设 padTop 即为 0
            container.add(play);
            container.add(maps);
            container.row();
            container.add(custom);
            container.add(join);
            container.row();
            container.add(editor);
            container.add(tools);
            container.row();

            Table inner = new Table();
            inner.defaults().size(size).pad(5).padTop(4f);
            inner.add(mods);
            inner.add(exit);
            container.add(inner).colspan(2);
        }
    }

    /**
     * 移动端角落按钮：整屏 {@link Table}（{@code setFillParent(true)}）+ 单个按钮，按 {@code align} 贴角。
     * 对应原版 {@code parent.fill(c -> c.bottom().left().addButton("", style, action).size(84, 45))}
     * （{@code Align.bottomLeft} 即 libgdx 的 {@code table.bottom().left()}）。
     */
    private void addCornerButton(TextButton.TextButtonStyle style, int align, Runnable action){
        Table corner = new Table();
        corner.setFillParent(true);
        corner.align(align);
        TextButton button = new TextButton("", style);
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                action.run();
            }
        });
        corner.add(button).size(84f, 45f);
        group.addActor(corner);
    }

    /** @return 是否竖屏（对应 arc 的 {@code Core.graphics.isPortrait()}，libgdx 的 Graphics 没有该方法）。 */
    private static boolean isPortrait(){
        return Core.graphics.getHeight() > Core.graphics.getWidth();
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
                    //原版主菜单的"战役"打开区域选择界面（DeployDialog），而不是直接进程序生成图；
                    //本项目的程序生成图入口保留为调试用（PHOENIX_AUTOPLAY 走 Vars.control.play()）
                    hide();
                    ui(u -> u.deploy.show());
                }),
                        new Buttoni("joingame", Icon.add, this::showJoinDialog),
                        new Buttoni("customgame", Icon.terrain, () -> ui(u -> u.custom.show())),
                        new Buttoni("loadgame", Icon.load, this::showLoadDialog),
                        //教程入口（对应原版 new Buttoni("$tutorial", Icon.info, () -> checkPlay(control::playTutorial))；
                        //原版 checkPlay 是 mod 内容出错时拦截，本项目 mods 无内容校验 → 直接进）
                        new Buttoni("tutorial", Icon.info, () -> {
                            hide();
                            if(Vars.control != null) Vars.control.playTutorial();
                        })
                ),
                new Buttoni("editor", Icon.terrain, () -> ui(u -> u.maps.show())),
                //刻意降级：原版 mods 用的是 Icon.bookOpen（移动端还有 Icon.book），这两个图标来自
                //  **icon font** —— 原版 core/assets-raw/fontgen/icons/book.svg 由 iconfont 生成字形，
                //  再用 Fonts.getGlyph(Fonts.icon, ...) 取（见原版生成的 Icon.java）。
                //  本项目整条 fontgen 图标字体管线未移植（连 core/assets-raw/fontgen/ 目录都没有），
                //  Icon 只由 assets-raw/sprites/ui/icons/*.png 生成，没有 book/bookOpen → 用语义最近的 Icon.folder
                new Buttoni("mods", Icon.folder, () -> ui(u -> u.mods.show())),
                new Buttoni("settings", Icon.settings, () -> ui(u -> u.settings.show())),
                new Buttoni("about.button", Icon.info, () -> ui(u -> u.about.show())),
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

    /**
     * 打开 {@code Vars.ui} 上的某个对话框（对应原版菜单按钮里的 {@code ui.settings::show} 等）。
     * <p>{@code Vars.ui} 由启动器在 {@code content.load()} 之后构造；这里加一层空守卫，
     * 免得将来有人在 UI 就绪前点到菜单按钮时 NPE。
     */
    private static void ui(com.phoenix.game.func.Cons<com.phoenix.game.core.UI> action){
        if(Vars.ui != null) action.get(Vars.ui);
    }

    /** 弹出读档对话框（与 HUD 共用同一实现；读档前先把菜单隐藏掉）。 */
    private void showLoadDialog(){
        if(group == null || group.getStage() == null) return;
        HudFragment.showLoadDialog(group.getStage(), this::hide);
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
