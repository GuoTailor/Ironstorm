package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Time;
import com.phoenix.game.core.Version;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.input.KeyCode;
import com.phoenix.game.net.Host;
import com.phoenix.game.net.Packets.KickReason;
import com.phoenix.game.struct.Array;
import com.phoenix.game.ui.Collapser;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * 加入多人游戏（本机名字/颜色 + 三个服务器分区：局域网 / 手动添加 / 官方列表 + 连接）。
 * 参照 Mindustry mindustry.ui.dialogs.JoinDialog。
 *
 * <p><b>与本项目的差异（逐条对应原版写法；标 TODO 的是缺依赖的降级项，其余是等价替代）</b>
 * <ul>
 *   <li><b>{@code Host} 类型已接线</b>（{@code net/Host.java} 逐字移植，字段/构造器与原版一致）：
 *       {@code Server.lastHost} 字段、{@link #setupServer}、{@link #buildServer} 已按原版补齐，
 *       服务器卡片的信息区（名字/版本/描述/人数/地图）可正常渲染。</li>
 *   <li><b>已接线（S6 批次）{@code Net.pingHost(...)}</b>：{@code Net.NetProvider} 与
 *       {@code PhoenixNetProvider} 已补 {@code pingHost}/{@code discoverServers} →
 *       {@link #refreshServer} 用 {@code pingHost} 填服务器信息，{@link #refreshLocal} 用
 *       {@code discoverServers} 填充局域网列表（{@link #addLocalHost}/{@link #finishLocalHosts}/
 *       {@link #addGlobalHost} 已按原版补出）。</li>
 *   <li>已知差异 <b>没有 {@code Strings.animated}</b>（"..." 循环动画）→ {@code refreshServer}/
 *       {@code refreshLocal} 的"刷新中/正在发现…"退化为静态文本（键 {@code server.refreshing}/
 *       {@code hosts.discovering.any} 均存在）。{@code Strings.parseInt} 不存在 → {@link #refreshGlobal}
 *       的官方列表解析无法照抄（且列表来源缺失，见下条）。</li>
 *   <li><b>{@code Version} 已接线</b>：{@link #safeConnect} 已补回 {@code Version.build} 与服务器版本的比对，
 *       并照原版用 {@code KickReason.clientOutdated/serverOutdated} 弹提示
 *       （本项目已在 {@code Packets.KickReason} 里补上 {@code toString()} 覆写，取 {@code server.kicked.*} 文本）。
 *       {@link #buildServer} 里的 outdated/custombuild 分支本批已按原版补齐（消费 {@code Host.version/versionType}）。</li>
 *   <li>已接线（S2 批次）<b>{@code Settings.getObject/putObject}</b>（arc 的对象序列化）→
 *       {@link #loadServers}/{@link #saveServers} 已持久化到 {@code server-list} 键。
 *       ⚠ <b>刻意偏离</b>：arc 用 {@code TypeSerializer} 注册表 + UBJson 二进制，本项目改用
 *       {@code io/JsonIO} 做 JSON 字符串序列化（设置文件是自有的 {@code phoenix_settings}，无兼容问题）；
 *       因 libgdx Json 读 {@code struct.Array} 时拿不到元素类型，用 {@code Server[]} 中转。</li>
 *   <li>TODO <b>{@code Core.net.httpGet(...)} + {@code Jval}</b>（拉取官方服务器 JSON）未移植 →
 *       只保留 {@code Vars.defaultServers}（本项目里是空列表）。</li>
 *   <li><b>{@code player.color} 已接线</b>：本项目 {@code Player} 有 {@code color} 字段
 *       （{@code entities/type/Player.java:43}）→ 选色写 {@code color-0}（int/rgba8888，与 {@code HostDialog} 同口径），
 *       {@code Vars.player} 非空时同步回 {@code player.color}。</li>
 *   <li>已知差异（等价替代）<b>{@code Vars.player} 在菜单里是 null</b>（本项目只在战役里创建 Player）→
 *       名字读写统一走 {@link #playerName()}/{@link #setPlayerName(String)}（player 非空时同步到 player，否则只写设置）。</li>
 *   <li>TODO <b>{@code Vars.platform} 不存在</b>（只有 {@code core/Platform} 接口，没有 Vars 字段）→
 *       原版 {@code platform.addDialog(field, 100)}（移动端软键盘）整段省略。</li>
 *   <li>已接线 原版 {@code Core.settings.getBoolOnce("joininfo", ...)}（{@code Settings} 没有 {@code getBoolOnce}）
 *       → 用 {@code getBool} + {@code put} 手搓"只弹一次"（见 {@link #JoinDialog()} 的 {@code shown} 回调，写法同 {@code HostDialog}）。</li>
 *   <li>TODO {@code net.reset()} / {@code netClient.beginConnecting()} / {@code netClient.disconnectQuietly()} /
 *       {@code logic.reset()} / {@code ui.paused.hide()}：本项目都没有（{@code NetClient.connect} 内部自己
 *       标记 connecting，断开用 {@code disconnect()}）→ 逐处降级。</li>
 *   <li>等价替代 {@code Icon.upOpen}/{@code Icon.downOpen}：本项目图集里没有 → 用 {@code Icon.arrowUp}/{@code Icon.arrowDown}。</li>
 *   <li>等价替代 {@code button.childrenPressed()}：libgdx 的 Button 没有该方法 → 子按钮的 ClickListener 调
 *       {@code event.stop()} 阻止冒泡（与 {@code SaveDialog} 同做法）。</li>
 *   <li>等价替代 arc 的 {@code Table.addButton/addImageButton/addField/add(String)/margin/table(Drawable, Cons)} /
 *       {@code Cell.update(...)} / {@code ImageButtonStyle.imageUpColor}：libgdx 都没有 →
 *       逐处换成"先建再 add" / {@code TextField} / {@link Label} / {@code pad} / 自建表 / 创建时定一次 /
 *       给 {@code Image} 设色。</li>
 *   <li>已知差异 {@code Stage.scrolled} 只发给 {@code scrollFocus}：本对话框的服务器列表
 *       ScrollPane 要吃到滚轮，需要显式 {@code scene.setScrollFocus(pane)}。</li>
 * </ul>
 */
public class JoinDialog extends FloatingDialog{
    Array<Server> servers = new Array<>();
    FloatingDialog add;
    Server renaming;
    Table local = new Table();
    Table remote = new Table();
    Table global = new Table();
    Table hosts = new Table();
    int totalHosts;

    public JoinDialog(){
        super("$joingame");

        loadServers();

        if(!Vars.steam) buttons.add().width(Scl.scl(60f));
        buttons.add().growX().width(-1);

        addCloseButton();

        buttons.add().growX().width(-1);
        if(!Vars.steam){
            buttons.add(textButton("?", null, () -> {
                if(Vars.ui != null) Vars.ui.showInfo("$join.info");
            })).size(Scl.scl(60f), Scl.scl(64f)).width(-1);
        }

        add = new FloatingDialog("$joingame.title");
        add.cont.add(new Label(bundle("$joingame.ip"), Styles.defaultLabel)).padRight(Scl.scl(5f)).left();

        //原版 add.cont.addField(Core.settings.getString("ip"), text -> {...}).size(320f, 54f).get()
        //  —— libgdx 的 Table 没有 addField → 自建 TextField
        final TextField field = new TextField(Core.settings.getString("ip", ""), Styles.defaultField);
        field.setMaxLength(100);
        field.addListener(new ChangeListener(){
            @Override
            public void changed(ChangeEvent event, Actor actor){
                Core.settings.put("ip", field.getText());
                Core.settings.save();
            }
        });
        add.cont.add(field).size(Scl.scl(320f), Scl.scl(54f));
        //TODO 原版 platform.addDialog(field, 100)（移动端软键盘）——本项目没有 Vars.platform

        add.cont.row();
        add.buttons.defaults().size(Scl.scl(140f), Scl.scl(60f)).pad(Scl.scl(4f));
        add.buttons.add(textButton("$cancel", null, add::hide));

        TextButton ok = textButton("$ok", null, () -> {
            if(renaming == null){
                Server server = new Server();
                server.setIP(Core.settings.getString("ip", ""));
                servers.add(server);
                saveServers();
                setupRemote();
                refreshRemote();
            }else{
                renaming.setIP(Core.settings.getString("ip", ""));
                saveServers();
                setupRemote();
                refreshRemote();
            }
            add.hide();
        });
        //原版 .disabled(b -> Core.settings.getString("ip").isEmpty() || net.active())
        //  —— libgdx 的 Cell 没有动态禁用 → 手动同步
        ok.setDisabled(Core.settings.getString("ip", "").isEmpty() || (Vars.net != null && Vars.net.active()));
        field.addListener(new ChangeListener(){
            @Override
            public void changed(ChangeEvent event, Actor actor){
                ok.setDisabled(field.getText().isEmpty() || (Vars.net != null && Vars.net.active()));
            }
        });
        add.buttons.add(ok);

        add.shown(() -> {
            //原版 add.title.setText(renaming != null ? "$server.edit" : "$server.add")
            //  —— 本项目 title 是 libgdx 原生 Label（不解析 $ 前缀）→ 走 bundle 文本
            add.title.setText(bundle(renaming != null ? "$server.edit" : "$server.add"));
            if(renaming != null){
                field.setText(renaming.displayIP());
            }
        });

        //原版 keyDown(KeyCode.F5, this::refreshAll)（arc 的按键回调）→ libgdx 用 InputListener
        addListener(new InputListener(){
            @Override
            public boolean keyDown(InputEvent event, int keycode){
                if(keycode == KeyCode.F5){
                    refreshAll();
                    return true;
                }
                return false;
            }
        });

        shown(() -> {
            setup();
            refreshAll();

            if(!Vars.steam){
                //已接线 原版 Core.app.post(() -> Core.settings.getBoolOnce("joininfo", () -> ui.showInfo("$join.info")))
                //  —— Settings 没有 getBoolOnce → getBool + put 手搓"只弹一次"（写法同 HostDialog）
                Gdx.app.postRunnable(() -> {
                    if(!Core.settings.getBool("joininfo", false)){
                        Core.settings.put("joininfo", true);
                        Core.settings.save();
                        if(Vars.ui != null) Vars.ui.showInfo("$join.info");
                    }
                });
            }
        });

        onResize(() -> {
            setup();
            refreshAll();
        });
    }

    void refreshAll(){
        refreshLocal();
        refreshRemote();
        refreshGlobal();
    }

    void setupRemote(){
        remote.clear();

        for(Server server : servers){
            //原版：remote.addButton("[accent]" + server.displayIP(), Styles.cleart, action) 之后把内置 Label
            //挪进内层表、再挂一排图标按钮。本项目 textButton 会清空并重排子节点 → 这里直接自建按钮 + 内层表
            TextButton button = new TextButton("", Styles.cleart);
            button.getLabel().remove();
            button.clearChildren();

            Table inner = new Table();

            Label ipLabel = new Label("[accent]" + server.displayIP(), Styles.defaultLabel);
            ipLabel.setWrap(true);
            inner.add(ipLabel).growX();

            //原版 Icon.upOpen/downOpen → 本项目图集里没有 → 用 arrowUp/arrowDown
            inner.add(stopIconButton(Icon.arrowUp, Styles.emptyi, () -> moveRemote(server, -1)))
                .pad(Scl.scl(3f)).padTop(Scl.scl(6f)).top().right();
            inner.add(stopIconButton(Icon.arrowDown, Styles.emptyi, () -> moveRemote(server, +1)))
                .pad(Scl.scl(3f)).padTop(Scl.scl(6f)).top().right();
            inner.add(stopIconButton(Icon.refresh, Styles.emptyi, () -> refreshServer(server)))
                .pad(Scl.scl(3f)).padTop(Scl.scl(6f)).top().right();
            inner.add(stopIconButton(Icon.pencil, Styles.emptyi, () -> {
                renaming = server;
                add.show();
            })).pad(Scl.scl(3f)).padTop(Scl.scl(6f)).top().right();
            inner.add(stopIconButton(Icon.trash, Styles.emptyi, () -> {
                if(Vars.ui != null){
                    Vars.ui.showConfirm("$confirm", "$server.delete", () -> {
                        servers.removeValue(server, true);
                        saveServers();
                        setupRemote();
                        refreshRemote();
                    });
                }
            })).pad(Scl.scl(3f)).pad(Scl.scl(6f)).top().right();

            button.add(inner).growX();

            //原版用 button.childrenPressed() 区分"点卡片"与"点卡片里的按钮"；libgdx 靠子按钮 event.stop()
            button.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    //对应原版：server.lastHost != null ? safeConnect(ip, port, lastHost.version) : connect(ip, port)
                    if(server.lastHost != null){
                        safeConnect(server.ip, server.port, server.lastHost.version);
                    }else{
                        connect(server.ip, server.port);
                    }
                }
            });

            button.row();

            //原版 server.content = button.table(t -> {}).grow().get()（arc 的 table(Cons)）→ 自建
            server.content = new Table();
            button.add(server.content).grow();

            remote.add(button).width(targetWidth()).pad(Scl.scl(4f));
            remote.row();
        }
    }

    void moveRemote(Server server, int sign){
        int index = servers.indexOf(server, true);

        if(index + sign < 0) return;
        if(index + sign > servers.size - 1) return;

        servers.removeIndex(index);
        servers.insert(index + sign, server);

        saveServers();
        setupRemote();
        for(Server other : servers){
            //对应原版：other.lastHost != null ? setupServer(other, other.lastHost) : refreshServer(other)
            if(other.lastHost != null){
                setupServer(other, other.lastHost);
            }else{
                refreshServer(other);
            }
        }
    }

    void refreshRemote(){
        for(Server server : servers){
            refreshServer(server);
        }
    }

    /**
     * 对应原版 {@code JoinDialog.refreshServer}（原版 JoinDialog.java:194-202）：
     * 先显示"刷新中"，再 {@code net.pingHost(...)}；成功走 {@link #setupServer}，失败显示 {@code $host.invalid}。
     * <p>⚠ 原版用 {@code Strings.animated(Time.time(), ...)} 做 "..." 循环动画 → 本项目没有 {@code Strings.animated}
     * → 退化为静态文本（{@code $server.refreshing} 键存在）。
     */
    void refreshServer(Server server){
        server.content.clear();
        server.content.add(new Label(bundle("$server.refreshing"), Styles.defaultLabel)).padBottom(Scl.scl(4f));

        if(Vars.net == null) return;

        Vars.net.pingHost(server.ip, server.port, host -> setupServer(server, host), e -> {
            server.content.clear();
            server.content.add(new Label(bundle("$host.invalid"), Styles.defaultLabel)).padBottom(Scl.scl(4f));
        });
    }

    /** 对应原版 {@code setupServer(Server, Host)}：记下 Host 并渲染信息区（pingHost 成功后由回调调用）。 */
    void setupServer(Server server, Host host){
        server.lastHost = host;
        server.content.clear();
        buildServer(host, server.content);
    }

    /** 对应原版 {@code buildServer(Host, Table)}：铺服务器名 / 版本串 / 描述 / 人数 / 地图。 */
    void buildServer(Host host, Table content){
        String versionString;

        if(host.version == -1){
            versionString = Core.bundle.format("server.version", Core.bundle.get("server.custombuild"), "");
        }else if(host.version == 0){
            versionString = Core.bundle.get("server.outdated");
        }else if(host.version < Version.build && Version.build != -1){
            versionString = Core.bundle.get("server.outdated") + "\n" +
                Core.bundle.format("server.version", host.version, "");
        }else if(host.version > Version.build && Version.build != -1){
            versionString = Core.bundle.get("server.outdated.client") + "\n" +
                Core.bundle.format("server.version", host.version, "");
        }else if(host.version == Version.build && Version.type.equals(host.versionType)){
            //not important
            versionString = "";
        }else{
            versionString = Core.bundle.format("server.version", host.version, host.versionType);
        }

        Table t = new Table();

        //原版 t.add("[lightgray]" + host.name + "   " + versionString).width(...).left().get().setEllipsis(true)
        Label nameLabel = new Label("[lightgray]" + host.name + "   " + versionString, Styles.defaultLabel);
        nameLabel.setEllipsis(true);
        t.add(nameLabel).width(Scl.scl(targetWidth() - 10f)).left();
        t.row();

        if(!host.description.isEmpty()){
            //原版 t.add("[gray]" + host.description).width(...).left().wrap()
            Label desc = new Label("[gray]" + host.description, Styles.defaultLabel);
            desc.setWrap(true);
            t.add(desc).width(Scl.scl(targetWidth() - 10f)).left();
            t.row();
        }

        //原版 t.add("[lightgray]" + Core.bundle.format("players" + (single ? ".single" : ""), 人数文本))
        String playerText = Core.bundle.format("players" + (host.players == 1 && host.playerLimit <= 0 ? ".single" : ""),
            (host.players == 0 ? "[lightgray]" : "[accent]") + host.players
                + (host.playerLimit > 0 ? "[lightgray]/[accent]" + host.playerLimit : "") + "[lightgray]");
        t.add(new Label("[lightgray]" + playerText, Styles.defaultLabel)).left();
        t.row();

        //原版 t.add("[lightgray]" + Core.bundle.format("save.map", host.mapname) + "[lightgray] / " + host.mode)
        Label mapLabel = new Label("[lightgray]" + Core.bundle.format("save.map", host.mapname)
            + "[lightgray] / " + host.mode, Styles.defaultLabel);
        mapLabel.setEllipsis(true);
        t.add(mapLabel).width(Scl.scl(targetWidth() - 10f)).left();

        content.add(t).expand().left().bottom().padLeft(Scl.scl(12f)).padBottom(Scl.scl(8f));
    }

    void setup(){
        local.clear();
        remote.clear();
        global.clear();
        float w = targetWidth();

        hosts.clear();

        section("$servers.local", local);
        section("$servers.remote", remote);
        section("$servers.global", global);

        ScrollPane pane = new ScrollPane(hosts, Styles.defaultPane);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);

        setupRemote();

        cont.clear();

        //名字 + 颜色行（原版 cont.table(t -> {...}).width(w).height(70f).pad(4)）
        Table nameRow = new Table();
        nameRow.add(new Label(bundle("$name"), Styles.defaultLabel)).padRight(Scl.scl(10f));
        if(!Vars.steam){
            //原版 t.addField(Core.settings.getString("name"), text -> {...}).grow().pad(8).get().setMaxLength(maxNameLength)
            TextField nameField = new TextField(playerName(), Styles.defaultField);
            nameField.setMaxLength(Vars.maxNameLength);
            nameField.addListener(new ChangeListener(){
                @Override
                public void changed(ChangeEvent event, Actor actor){
                    setPlayerName(nameField.getText());
                }
            });
            nameRow.add(nameField).grow().pad(Scl.scl(8f));
        }else{
            nameRow.add(new Label(playerName(), Styles.defaultLabel)).grow().pad(Scl.scl(8f));
        }

        //原版 t.addImageButton(Tex.whiteui, Styles.clearFulli, 40, () -> new PaletteDialog().show(...))
        //  —— libgdx 的 ImageButton 没有 (Drawable, style) 构造器 → 手工设 imageUp
        ImageButton.ImageButtonStyle bs = new ImageButton.ImageButtonStyle(Styles.clearFulli);
        bs.imageUp = Tex.whiteui;
        ImageButton colorButton = new ImageButton(bs);
        colorButton.getImage().setDrawable(Tex.whiteui);
        //原版 button.update(() -> button.getStyle().imageUpColor = player.color)（每帧跟随玩家颜色）
        //  —— libgdx 的 ImageButtonStyle 没有 imageUpColor、且菜单期 Vars.player 为 null
        //     → 用设置里的颜色定一次（Player.color 本身已存在，见 putPlayerColor）
        colorButton.getImage().setColor(playerColor());
        colorButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                new PaletteDialog().show(color -> {
                    //原版 player.color.set(color) + Core.settings.put("color-0", color.rgba())
                    putPlayerColor(color);
                    colorButton.getImage().setColor(color);
                });
            }
        });
        nameRow.add(colorButton).size(Scl.scl(54f));

        cont.add(nameRow).width(w).height(Scl.scl(70f)).pad(Scl.scl(4f));
        cont.row();
        cont.add(pane).width(w + Scl.scl(38f)).pad(0f);
        cont.row();

        //原版 cont.addCenteredImageTextButton("$server.add", ...).marginLeft(10).width(w).height(80f)
        //     .update(button -> { 根据滚动条宽度微调 cell 宽度 })
        //  —— libgdx 的 Cell 没有 update(...) → 那段自适应宽度省略
        cont.add(imageTextButton("$server.add", Icon.add, null, () -> {
            renaming = null;
            add.show();
        })).padLeft(Scl.scl(10f)).width(w).height(Scl.scl(80f)).center();

        //已知差异：Stage.scrolled 只发给 scrollFocus → 服务器列表要吃到滚轮必须显式设定
        if(scene != null){
            scene.setScrollFocus(pane);
        }
    }

    void section(String label, Table servers){
        Collapser coll = new Collapser(servers, Core.settings.getBool("collapsed-" + label, false));
        coll.setDuration(0.1f);

        Table name = new Table();
        //原版 name.add(label)（arc 的 add(String) 会解析 "$key"）→ 这里自己过一遍 bundle()
        Label title = new Label(bundle(label), Styles.defaultLabel);
        title.setColor(Pal.accent);
        name.add(title).pad(Scl.scl(10f)).growX().left();

        //原版用 .update(i -> i.getStyle().imageUp = !coll.isCollapsed() ? Icon.upOpen : Icon.downOpen) 同步箭头
        //  —— 等价替代：libgdx 的 Cell 没有 update(...) 每帧回调 → 只在创建时按当前折叠态定一次（收起后箭头不翻转）
        ImageButton toggle = iconButton(coll.isCollapsed() ? Icon.arrowDown : Icon.arrowUp, Styles.emptyi, () -> {
            coll.toggle(false);
            //等价替代 原版 Core.settings.putSave(key, value)（arc 的"只写不立即 flush"）→ 用 put + save
            Core.settings.put("collapsed-" + label, coll.isCollapsed());
            Core.settings.save();
        });
        name.add(toggle).size(Scl.scl(40f)).right().padRight(Scl.scl(10f));

        hosts.add(name).growX();
        hosts.row();

        //原版 hosts.addImage().growX().pad(5).padLeft(10).padRight(10).height(3).color(Pal.accent)
        Image divider = new Image(Tex.whiteui);
        divider.setColor(Pal.accent);
        hosts.add(divider).growX().pad(Scl.scl(5f)).padLeft(Scl.scl(10f)).padRight(Scl.scl(10f)).height(Scl.scl(3f));
        hosts.row();
        hosts.add(coll).width(targetWidth());
        hosts.row();
    }

    /**
     * 对应原版 {@code JoinDialog.refreshLocal}（原版 JoinDialog.java:325-332）：
     * 先铺"正在发现…"的 box，再由 {@code net.discoverServers(this::addLocalHost, this::finishLocalHosts)} 填充。
     * <p>⚠ 原版用 {@code Strings.animated} 做 "..." 动画 → 本项目没有，退化为静态文本。
     */
    void refreshLocal(){
        totalHosts = 0;

        local.clear();
        local.setBackground((Drawable)null);

        //原版 local.table(Tex.button, t -> t.label(...)) → libgdx 没有 table(Drawable, Cons)/label(Prov) → 自建 box
        Table box = new Table();
        box.setBackground(Tex.button);
        box.add(new Label("[accent]" + bundle("$hosts.discovering.any"), Styles.defaultLabel)).pad(Scl.scl(10f));
        local.add(box).growX();

        if(Vars.net != null){
            Vars.net.discoverServers(this::addLocalHost, this::finishLocalHosts);
        }
    }

    /**
     * TODO 原版 {@code refreshGlobal()}（原版 JoinDialog.java:334-345）：对 {@code defaultServers} 逐个
     * {@code net.pingHost} 并列出。本项目 {@code defaultServers} 的来源是 {@code Core.net.httpGet} + {@code Jval}
     * （拉官方服务器 JSON）——这两者均未移植（见 {@link #loadServers}），列表恒为空 → 保持占位。
     */
    void refreshGlobal(){
        global.clear();
        global.setBackground((Drawable)null);

        Table box = new Table();
        box.setBackground(Tex.button);
        box.add(new Label(bundle("$hosts.none"), Styles.defaultLabel)).pad(Scl.scl(10f));
        global.add(box).growX();
    }

    /** 对应原版 {@code JoinDialog.finishLocalHosts}（原版 JoinDialog.java:347-357）：发现结束，无结果则铺"未发现" + 刷新按钮。 */
    void finishLocalHosts(){
        if(totalHosts == 0){
            local.clear();
            local.setBackground(Tex.button);
            local.add(new Label(bundle("$hosts.none"), Styles.defaultLabel)).pad(Scl.scl(10f));
            local.add().growX();
            local.add(iconButton(Icon.refresh, Styles.emptyi, this::refreshLocal)).pad(Scl.scl(-12f)).padLeft(0f).size(Scl.scl(70f));
        }else{
            local.setBackground((Drawable)null);
        }
    }

    /** 对应原版 {@code JoinDialog.addLocalHost}（原版 JoinDialog.java:359-373）：发现一台局域网服务器后追加卡片。 */
    void addLocalHost(Host host){
        if(totalHosts == 0){
            local.clear();
        }
        local.setBackground((Drawable)null);
        totalHosts++;
        float w = targetWidth();

        local.row();

        //原版 local.addButton("", Styles.cleart, action) → libgdx 自建 TextButton + ClickListener
        TextButton button = new TextButton("", Styles.cleart);
        button.getLabel().remove();
        button.clearChildren();
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                safeConnect(host.address, host.port, host.version);
            }
        });
        local.add(button).width(w).pad(Scl.scl(5f));
        buildServer(host, button);
    }

    /** 对应原版 {@code JoinDialog.addGlobalHost}（原版 JoinDialog.java:375-385）：官方服务器列表里追加卡片。 */
    void addGlobalHost(Host host){
        global.setBackground((Drawable)null);
        float w = targetWidth();

        global.row();

        TextButton button = new TextButton("", Styles.cleart);
        button.getLabel().remove();
        button.clearChildren();
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                safeConnect(host.address, host.port, host.version);
            }
        });
        global.add(button).width(w).pad(Scl.scl(5f));
        buildServer(host, button);
    }

    public void connect(String ip, int port){
        if(playerName().trim().isEmpty()){
            if(Vars.ui != null) Vars.ui.showInfo("$noname");
            return;
        }

        if(Vars.ui != null && Vars.ui.loadfrag != null){
            Vars.ui.loadfrag.show(bundle("$connecting"));

            Vars.ui.loadfrag.setButton(() -> {
                Vars.ui.loadfrag.hide();
                //TODO 原版 netClient.disconnectQuietly()（本项目是 NetClient.disconnect()）
                if(Vars.netClient != null) Vars.netClient.disconnect();
            });
        }

        Time.run(2f, () -> {
            //TODO 原版 logic.reset(); net.reset(); Vars.netClient.beginConnecting();
            //  —— Logic 没有 reset()、Vars.net 没有 reset()、NetClient 没有 beginConnecting()
            //     （本项目 NetClient.connect 内部自己标记 connecting）
            if(Vars.netClient != null){
                Vars.netClient.connect(ip, port, playerName());
            }
            //TODO 原版 net.connect(ip, port, () -> { hide(); add.hide(); }) 是"连上后"回调；
            //  —— NetClient.connect 没有成功回调 → 这里直接收起对话框（连上后由 onWorldLoaded 关加载框）
            hide();
            add.hide();
        });
    }

    /**
     * 版本比对后连接（对应原版 {@code safeConnect(ip, port, version)}）。
     * <p>原版弹 {@code KickReason.clientOutdated/serverOutdated}（它的 {@code toString()} 取
     * {@code server.kicked.*} 的本地化文本，本项目已在 {@code Packets.KickReason} 里补上该覆写）。
     * <p>⚠ {@code UI.showInfo(String)} 只对"整串以 {@code $} 开头"的文本做 bundle 解析，
     * 所以这里把原因文本先取出来再拼；{@code server.versions} 由 {@code Core.bundle.format} 单独格式化。
     */
    void safeConnect(String ip, int port, int version){
        if(version != Version.build && Version.build != -1 && version != -1){
            KickReason reason = version > Version.build ? KickReason.clientOutdated : KickReason.serverOutdated;
            if(Vars.ui != null){
                Vars.ui.showInfo("[scarlet]" + reason + "\n[]" + Core.bundle.format("server.versions", Version.build, version));
            }
        }else{
            connect(ip, port);
        }
    }

    float targetWidth(){
        return Math.min(Core.graphics.getWidth() / Scl.scl() * 0.9f, 500f);
    }

    @SuppressWarnings("unchecked")
    private void loadServers(){
        //原版 JoinDialog.java:426 servers = Core.settings.getObject("server-list", Array.class, Array::new)
        //已接线（S2 批次）：本项目 Settings 补了 getObject（用 io/JsonIO 做 JSON 字符串序列化，替代 arc 的 UBJson）。
        //⚠ 本项目 servers 是 struct.Array（extends libgdx Array）；libgdx Json 读回时拿不到元素类型
        //  → 用 Server[] 中转（libgdx Json 原生支持对象数组），再走 struct.Array(T[]) 构造器
        Server[] saved = Core.settings.getObject("server-list", Server[].class, null);
        servers = saved == null ? new Array<>() : new Array<>(saved);

        //TODO 原版 Core.net.httpGet(becontrol.active() ? serverJsonBeURL : serverJsonURL, result -> {
        //     Jval val = Jval.read(result.getResultAsString()); defaultServers.clear(); val.asArray()... })
        //  —— Core.net.httpGet 需要 libgdx 的 HttpRequest 且 Jval 未移植 → 官方服务器列表不拉取，
        //     只用 Vars.defaultServers（本项目里是空列表）
    }

    private void saveServers(){
        //原版 JoinDialog.java:444 Core.settings.putObject("server-list", servers); Core.settings.save();
        //已接线（S2 批次）：putObject 内部走 io/JsonIO（⚠ 刻意偏离 arc 的 UBJson，见 Settings#putObject）
        //⚠ servers 是 struct.Array；用 toArray(Class)（libgdx Array 的方法）转成 Server[] 再序列化
        Core.settings.putObject("server-list", servers.toArray(Server.class));
        Core.settings.save();
    }

    // ------------------------------------------------------------------
    // 玩家名字 / 颜色（本项目 Vars.player 在菜单里为 null，见类注释）
    // ------------------------------------------------------------------

    /** @return 玩家名：{@code Vars.player} 存在时取它，否则退回 {@code Core.settings} 的 {@code name} 键。 */
    static String playerName(){
        if(Vars.player != null) return Vars.player.name;
        return Core.settings.getString("name", "noname");
    }

    /** 写玩家名：同步到 {@code Vars.player}（若存在）并持久化到设置。 */
    static void setPlayerName(String name){
        if(Vars.player != null) Vars.player.name = name;
        Core.settings.put("name", name);
        Core.settings.save();
    }

    /** @return 玩家颜色：菜单期 {@code Vars.player} 为 null → 读设置里的 {@code color-0}（与原版同为 int/rgba8888 编码）。 */
    static Color playerColor(){
        return new Color(Core.settings.getInt("color-0",
            com.badlogic.gdx.graphics.Color.rgba8888(Vars.playerColors[8])));
    }

    /** 写玩家颜色：原版写 {@code player.color} + {@code settings.put("color-0", color.rgba())}（int）。 */
    static void putPlayerColor(Color color){
        Core.settings.put("color-0", com.badlogic.gdx.graphics.Color.rgba8888(color));
        if(Vars.player != null) Vars.player.color.set(color);
        Core.settings.save();
    }

    /** 一个纯图标按钮（对应原版 {@code addImageButton(icon, style, action)}）。 */
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

    /**
     * 与 {@code SaveDialog.stopIconButton} 等价：点击后额外 {@code event.stop()}，
     * 阻止事件冒泡到外层服务器卡片（替代原版 {@code button.childrenPressed()}）。
     */
    private static ImageButton stopIconButton(Drawable icon, ImageButton.ImageButtonStyle style, Runnable action){
        ImageButton.ImageButtonStyle bs = new ImageButton.ImageButtonStyle(style);
        bs.imageUp = icon;
        ImageButton button = new ImageButton(bs);
        button.getImage().setDrawable(icon);
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                event.stop();
                action.run();
            }
        });
        return button;
    }

    /** 服务器条目。原版标了 {@code @Serialize}（网络/设置序列化），本项目序列化框架未移植 → 保持注释。 */
    //@Serialize
    public static class Server{
        public String ip;
        public int port;

        transient Table content;
        /** 最近一次 ping 到的服务器信息（对应原版 {@code transient Host lastHost}）；由 {@link #setupServer} 填充。 */
        transient Host lastHost;

        void setIP(String ip){
            //parse ip:port, if unsuccessful, use default values
            if(ip.lastIndexOf(':') != -1 && ip.lastIndexOf(':') != ip.length() - 1){
                try{
                    int idx = ip.lastIndexOf(':');
                    this.ip = ip.substring(0, idx);
                    this.port = Integer.parseInt(ip.substring(idx + 1));
                }catch(Exception e){
                    this.ip = ip;
                    this.port = Vars.port;
                }
            }else{
                this.ip = ip;
                this.port = Vars.port;
            }
        }

        String displayIP(){
            return ip + (port != Vars.port ? ":" + port : "");
        }

        public Server(){
        }
    }
}
