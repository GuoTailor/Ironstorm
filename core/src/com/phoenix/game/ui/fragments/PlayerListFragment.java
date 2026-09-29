package com.phoenix.game.ui.fragments;

import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Interval;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Strings;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.net.Administration;
import com.phoenix.game.net.Packets;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.ui.dialogs.BansDialog;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

import static com.phoenix.game.Vars.*;

/**
 * 玩家列表（Tab 打开）。参照 Mindustry mindustry.ui.fragments.PlayerListFragment 移植。
 * <p>结构：整屏遮罩表 → 一张 {@code buttonTrans} 底的面板（标题 + 搜索框 + 可滚动玩家列表 + 底部按钮行）。
 * 面板每 20 帧重建一次（{@code timer.get(20)}），只有联机（{@code net.active()}）且非菜单时可见。
 *
 * <p><b>F13b 已接的玩家行</b>：遍历 {@code playerGroup} 画每一行（单位图标 + 彩色名字 + 管理员标记 + 行分隔线），
 * 搜索过滤、无结果提示、服务端"未连接远程玩家不显示"判据都照原版。
 * <ul>
 *   <li>依赖字段已补齐：{@code Player.con/uuid/usid/isAdmin/isLocal/color}（服务端在 {@code NetServer} 连接处理里从
 *       {@code ConnectPacket} 填，本机玩家在 {@code Control}/{@code NetClient} 创建时标 {@code isLocal}）+
 *       {@code Player.getIconRegion()}（取被控单位类型的 full 图标）。</li>
 *   <li>F13e/F13f 已接：管理员操作按钮 —— ban（{@code Icon.ban} 替代本项目图集没有的 {@code Icon.hammer}）、
 *       kick、加/撤管理员（toggle）、trace（{@code Icon.zoom}，S8 补：服务端组 {@code TraceInfo} 回传给发起者，
 *       对应原版 {@code Call.onTraceInfo}）。**服务端**（房主）直接调服务端 API（{@code admins.banIP}/{@code con.kick}/
 *       {@code setPlayerAdmin}）；**客户端管理员**走 RPC（{@code NetClient.sendAdminRequest} → 服务端
 *       {@code onAdminRequest} 鉴权后执行，对照原版 {@code Call.onAdminRequest}）。加/撤管理员按钮照原版在客户端禁用。</li>
 *   <li>刻意降级：行框线（原版 {@code Lines.rect} 描边，本项目 {@code Lines} 只有 {@code stroke}）→ 分隔线改用纯色 Image。</li>
 *   <li>TODO（B 类）仍未接：votekick（原版 {@code Call.sendChatMessage("/votekick ...")}，服务端没有该命令处理）。</li>
 *   <li>搜索的"去色名字"匹配已补：{@code core/Strings.stripColors}（照 arc 原版实现）+ 本类 {@code rebuild()} 接线。</li>
 * </ul>
 *
 * <p><b>其它 arc→libgdx 差异</b>：
 * <ul>
 *   <li>{@code parent.fill(cont -> {...})} → 自建 {@code setFillParent(true)} 的 Table；
 *       {@code cont.visible(Boolp)} / {@code cont.update(Runnable)} 这两个 arc 的每帧回调 → 用
 *       {@link MenuFragment.Act} 统一驱动；</li>
 *   <li>{@code cont.table(Tex.buttonTrans, pane -> {...})} → 自建 Table + {@code setBackground}；</li>
 *   <li>{@code Table.margin(...)}（外间距）libgdx 没有 → 外间距挪到 Cell 的 {@code pad}、内间距用 {@code pad}；</li>
 *   <li>{@code pane.addField(null, Cons)} → libgdx 没有 → {@code new TextField} + {@code setTextFieldListener}；</li>
 *   <li>{@code pane.pane(content)} → libgdx 没有 → 显式 {@code new ScrollPane(content, Styles.defaultPane)}；</li>
 *   <li>{@code addButton("$key", ...)} → libgdx 没有 → {@code new TextButton(I18n.get(...), Styles.defaultt)}
 *       （原版 {@code $key} 前缀由 arc 自动解析，这里走 {@link I18n}）；</li>
 *   <li>{@code Cell.disabled(...)} / {@code Cell.update(...)} 不存在 → 直接对 Actor 调 {@code setDisabled}；</li>
 *   <li>{@code Core.scene.setKeyboardFocus(null)}（arc 的 Scene）→ 从 Actor 取 {@code getStage()}；</li>
 *   <li>{@code sField.clearText()} → libgdx 是 {@code setText("")}；</li>
 *   <li>{@code Lines.rect(...)}（原版行框线）本项目 {@code Lines} 没有 rect → 该用法随玩家行一起注释保留。</li>
 * </ul>
 */
public class PlayerListFragment extends Fragment{
    private boolean visible = false;
    //刻意降级：原版 content = new Table().marginRight(13f).marginLeft(13f)（margin 是外间距）→ 用 padLeft/padRight
    private Table content = new Table();
    private Interval timer = new Interval();
    private TextField sField;
    /** 标题（"N 位玩家在线"），在 rebuild() 里按当前人数更新。 */
    private Label countLabel;

    @Override
    public void build(Group parent){
        //刻意降级：原版 parent.fill(cont -> {...})（arc 的"铺满父级 + 回调"）；libgdx 的 Group 没有 → 自建
        Table cont = new Table();
        cont.setFillParent(true);
        cont.setVisible(false);
        parent.addActor(cont);

        //刻意降级：原版 cont.visible(() -> visible) + cont.update(() -> {...})（两个 arc 的每帧回调）
        //     → libgdx 没有对应 API，统一放进一个 Act 子 Actor
        cont.addActor(new MenuFragment.Act(() -> {
            cont.setVisible(visible);
            //刻意降级：原版 net.active()（Vars.net 在本项目实际不会被赋值）→ 用 Vars.isClient()/isServer() 判联机
            if(!((Vars.isClient() || Vars.isServer()) && !state.isMenu())){
                visible = false;
                return;
            }

            if(visible && timer.get(20)){
                rebuild();
                content.pack();
                content.act(Core.graphics.getDeltaTime());
                //刻意降级：原版 Core.scene.act(0f)（arc 的"强制刷新一次 UI"）—— 本项目没有 Core.scene
            }
        }));

        //刻意降级：原版 cont.table(Tex.buttonTrans, pane -> {...})（带背景 + 回调）；libgdx 的 Table 没有这个重载
        Table pane = new Table();
        pane.setBackground(Tex.buttonTrans);
        pane.setTouchable(Touchable.enabled);

        //原版：Core.bundle.format(playerGroup.size() == 1 ? "players.single" : "players", playerGroup.size())
        //  —— bundle 是裁剪过的（缺键返回 ???key???），所以走 I18n.get(key, fallback) + Strings.format
        //  （F13b 修正：标题改在 rebuild() 里每帧更新 —— build 时 playerGroup 还是 null，人数会一直停在 0）
        countLabel = new Label("", Styles.defaultLabel);
        pane.add(countLabel).row();

        //刻意降级：原版 pane.addField(null, text -> rebuild())（arc 的"加一个带文本变化回调的输入框"）
        sField = new TextField("", new TextField.TextFieldStyle(Styles.defaultField));
        sField.setMaxLength(maxNameLength);
        sField.setMessageText(I18n.get("players.search", "Search"));
        sField.setTextFieldListener((field, c) -> rebuild());
        pane.add(sField).grow().pad(Scl.scl(8f)).row();

        //刻意降级：原版 pane.pane(content).grow().get().setScrollingDisabled(true, false)（arc 的 pane(...) 便捷方法）
        ScrollPane scroll = new ScrollPane(content, Styles.defaultPane);
        scroll.setScrollingDisabled(true, false);
        pane.add(scroll).grow().row();

        //刻意降级：原版 pane.table(menu -> {...}).margin(0f).pad(10f).growX()
        Table menu = new Table();
        menu.defaults().growX().height(Scl.scl(50f)).fillY();

        //原版 menu.addButton("$server.bans", ui.bans::show).disabled(b -> net.client())
        //  —— Vars.ui 门面已就绪，直接用 ui.bans/ui.admins；
        //     原版的 net.client() 在本项目恒 false（Vars.net 从未赋值，见 PausedDialog 说明），
        //     这里改用 Vars.netClient 判断"当前是客户端"：客户端不允许改封禁/管理员（服务端权威）
        boolean client = Vars.netClient != null && Vars.netClient.isConnected();

        TextButton bans = menuButton(I18n.get("server.bans", "Bans"), () -> {
            if(Vars.ui != null) Vars.ui.bans.show();
        });
        bans.setDisabled(client);
        menu.add(bans);

        TextButton admins = menuButton(I18n.get("server.admins", "Admins"), () -> {
            if(Vars.ui != null) Vars.ui.admins.show();
        });
        admins.setDisabled(client);
        menu.add(admins);

        TextButton close = new TextButton(I18n.get("close", "Close"), Styles.defaultt);
        close.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                toggle();
            }
        });
        menu.add(close);

        //刻意降级：原版 pane.table(menu -> {...}).margin(0f).pad(10f).growX()：
        //     其中 margin/pad 是 Table 的（libgdx 没有 margin → 用 pad），growX 是外层 Cell 的（见下面的 pane.add）
        menu.pad(Scl.scl(10f));
        pane.add(menu).growX();

        //刻意降级：原版 pane.touchable(Touchable.enabled).margin(14f)：margin 是外间距，libgdx 只能挂到外层 Cell 上
        cont.add(pane).pad(Scl.scl(14f));

        rebuild();
    }

    /**
     * 执行一个管理员动作（F13e/F13f）。
     * <p>服务端（房主）：直接调服务端 API（等价原版服务端收到 {@code AdminRequest} 后的执行）；
     * trace 不需要目标连接，房主本地直接显示追踪信息（对应原版 {@code NetClient.onTraceInfo}）。
     * <p>客户端：发 RPC 给服务端（{@link com.phoenix.game.core.NetClient#sendAdminRequest}，服务端鉴权后执行）。
     */
    private void adminAction(Player user, Packets.AdminAction action){
        if(Vars.isServer()){
            if(Vars.netServer == null) return;
            if(action == Packets.AdminAction.trace){
                //对应原版 NetServer.onAdminRequest 的 trace 分支：房主本人就是发起者（其 con 为 null），
                //原版走 else 分支 NetClient.onTraceInfo(other, info) —— 也就是**本地直接显示**。
                //trace 不需要目标连接（只有 ban/kick 才需要），所以放在下面的 con 判空之前。
                if(Vars.ui == null) return;
                Administration.TraceInfo info = new Administration.TraceInfo(
                    user.con == null ? "" : user.con.address,
                    user.uuid,
                    user.con != null && user.con.modclient,
                    user.con != null && user.con.mobile);
                Vars.ui.traces.show(user, info);
                return;
            }
            //ban/kick 需要目标连接（服务端 API 就是"踢连接 / 封该连接的 IP"）
            if(user.con == null) return;
            if(action == Packets.AdminAction.ban){
                //原版服务端语义：封 IP + 踢
                Vars.netServer.admins.banIP(user.con.address);
                user.con.kick(Packets.KickReason.banned);
            }else if(action == Packets.AdminAction.kick){
                user.con.kick(Packets.KickReason.kick);
            }
        }else if(Vars.netClient != null){
            Vars.netClient.sendAdminRequest(user.uuid, action);
        }
    }

    /** 菜单里的普通按钮（原版 {@code menu.addButton(text, action)}）。 */
    private TextButton menuButton(String text, Runnable action){
        TextButton button = new TextButton(text, Styles.defaultt);
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                action.run();
            }
        });
        return button;
    }

    public void rebuild(){
        content.clearChildren();

        float h = 74f;

        //标题人数（F13b：每帧重建时更新，build 时 playerGroup 还是 null）
        if(countLabel != null){
            int playercount = Vars.playerGroup == null ? 0 : Vars.playerGroup.size();
            countLabel.setText(Strings.format(
                I18n.get(playercount == 1 ? "players.single" : "players", "Players: {0}"), playercount));
        }

        //F13b：玩家行（原版 rebuild() 整段的核心，依赖已补齐：Player.con/uuid/isAdmin/isLocal/color/getIconRegion）
        if(Vars.playerGroup != null){
            com.badlogic.gdx.utils.Array<Player> players = Vars.playerGroup.all();
            //原版 playerGroup.all().sort(Structs.comparing(Unit::getTeam))
            //  （本项目 Team 不是枚举 → 按 Team.id 排序，与 Team.compareTo 同序）
            players.sort((a, b) -> Integer.compare(a.getTeam().id, b.getTeam().id));

            boolean server = Vars.isServer();
            String search = sField.getText() == null ? "" : sField.getText().toLowerCase();
            int shown = 0;

            for(int i = 0; i < players.size; i++){
                Player user = players.get(i);

                //原版：服务端上未连接的远程玩家不显示（本机玩家 con 为 null 但有 isLocal 标记 → 显示）
                if(user.con == null && server && !user.isLocal) continue;
                //搜索过滤（对应原版：原名匹配 + 去色后名字匹配；Strings.stripColors 已补，见 core/Strings.java）
                if(search.length() > 0 && !user.name.toLowerCase().contains(search)
                    && !Strings.stripColors(user.name.toLowerCase()).contains(search)) continue;
                shown++;

                Table button = new Table();
                button.left();
                button.pad(Scl.scl(5f)).padBottom(Scl.scl(10f));

                //单位图标（原版 new Image(user.getIconRegion()).setScaling(Scaling.none)）
                com.badlogic.gdx.graphics.g2d.TextureRegion icon = user.getIconRegion();
                if(icon != null){
                    com.badlogic.gdx.scenes.scene2d.ui.Image image = new com.badlogic.gdx.scenes.scene2d.ui.Image(
                        new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(icon));
                    image.setScaling(com.badlogic.gdx.utils.Scaling.none);
                    button.add(image).size(Scl.scl(h - 10f)).pad(Scl.scl(8f));
                }else{
                    //没有单位（死亡等待重生）：留空位，行高不塌
                    button.add().size(Scl.scl(h - 10f)).pad(Scl.scl(8f));
                }

                //名字（原版 button.labelWrap("[#" + user.color + "]" + user.name).width(170f).pad(10)）
                //  —— 本项目自定义 Label 支持 [#rrggbb] markup（见 ui/Label.java 的 applyColors）
                Label name = new Label("[#" + user.color.toString().toUpperCase() + "]" + user.name, Styles.defaultLabel);
                name.setWrap(true);
                button.add(name).width(Scl.scl(170f)).pad(Scl.scl(10f));
                button.add().grow();

                //管理员标记（原版 addImage(Icon.admin).visible(() -> user.isAdmin && !(!user.isLocal && net.server()))）
                if(user.isAdmin && !(!user.isLocal && server)){
                    button.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(Icon.admin)).size(Scl.scl(24f)).padRight(Scl.scl(5f));
                }

                //F13e/F13f：管理员操作按钮。原版判定：(net.server() || player.isAdmin) && !user.isLocal
                //  && (!user.isAdmin || net.server())；服务端（房主）直接执行、客户端管理员走 RPC。
                boolean canAdminister = server || (Vars.isClient() && Vars.player != null && Vars.player.isAdmin);
                if(canAdminister && !user.isLocal && (!user.isAdmin || server)){
                    float bs = Scl.scl(h / 2f);

                    Table actions = new Table();
                    actions.defaults().size(bs);

                    //封禁（原版用 Icon.hammer，本项目图集没有该图标 → 用语义更直接的 Icon.ban）
                    actions.add(BansDialog.iconButton(Icon.ban, Styles.clearPartiali, () -> {
                        if(Vars.ui == null) return;
                        Vars.ui.showConfirm("$confirm", "$confirmban", () -> adminAction(user, Packets.AdminAction.ban));
                    }));

                    //踢出
                    actions.add(BansDialog.iconButton(Icon.cancel, Styles.clearPartiali, () -> {
                        if(Vars.ui == null) return;
                        Vars.ui.showConfirm("$confirm", "$confirmkick", () -> adminAction(user, Packets.AdminAction.kick));
                    }));

                    actions.row();

                    //加/撤管理员（toggle 样式，checked 状态 = 当前是否管理员）。
                    //原版该按钮在客户端上禁用/无效（`if(net.client()) return;` + `disabled(net.client())`）——
                    //它改的是服务端档案、没有对应的 RPC 动作，照原版只在服务端可用。
                    //TODO 原版 adminPlayer(id, usid)/unAdminPlayer(id) 还会记录 adminUsid（防 uuid 冒用），
                    //  本项目 PlayerInfo 没有该字段 → 用现有的 setAdmin(uuid, bool)
                    ImageButton adminBtn = BansDialog.iconButton(Icon.admin, Styles.clearTogglePartiali, () -> {
                        if(Vars.ui == null || Vars.netServer == null || Vars.isClient()) return;
                        boolean isAdmin = Vars.netServer.admins.isAdmin(user.uuid);
                        Vars.ui.showConfirm("$confirm", isAdmin ? "$confirmunadmin" : "$confirmadmin", () -> {
                            if(Vars.netServer == null) return;
                            //F13g：改档案 + 同步在场玩家对象 + 广播玩家列表（客户端据此刷新列表与按钮）
                            Vars.netServer.setPlayerAdmin(user.uuid, !isAdmin);
                        });
                    });
                    adminBtn.setChecked(user.isAdmin);
                    adminBtn.setDisabled(Vars.isClient()); //原版 .disabled(net.client())
                    actions.add(adminBtn);

                    //追踪（原版第 4 个按钮 Icon.zoom → Call.onAdminRequest(user, AdminAction.trace)）。
                    //注：原版该按钮无确认框，本项目 bundle 也没有 confirmtrace 键 → 直接执行、不加确认框。
                    actions.add(BansDialog.iconButton(Icon.zoom, Styles.clearPartiali,
                        () -> adminAction(user, Packets.AdminAction.trace)));

                    button.add(actions).padRight(Scl.scl(12f));
                }
                //TODO votekick（原版 else 分支：!user.isLocal && !user.isAdmin && net.client() && playerGroup.size() >= 3
                //  && player.getTeam() == user.getTeam() → Call.sendChatMessage("/votekick " + user.name)）：
                //  本项目聊天发送通道已有（NetClient.sendChat → Packets.ChatMessage），
                //  但服务端没有 "/votekick" 命令处理 → 先不显示按钮。

                content.add(button).padBottom(Scl.scl(-6f)).width(Scl.scl(350f)).maxHeight(Scl.scl(h + 14f));
                content.row();

                //行分隔线（原版 content.addImage().height(4f).color(pvp ? teamColor : Pal.gray).growX()）
                com.badlogic.gdx.scenes.scene2d.ui.Image divider = new com.badlogic.gdx.scenes.scene2d.ui.Image(Styles.black);
                divider.setColor(state.rules.pvp ? user.getTeam().color : com.phoenix.game.graphics.Pal.gray);
                content.add(divider).height(Scl.scl(4f)).growX();
                content.row();
            }

            //搜索无结果提示（原版 Core.bundle.format("players.notfound")）
            if(search.length() > 0 && shown == 0){
                content.add(new Label(I18n.get("players.notfound", "No players found"), Styles.defaultLabel)).pad(Scl.scl(6f));
                content.row();
            }
        }

        //刻意降级：原版 content.marginBottom(5)（外间距）→ libgdx 只能当作内间距 padBottom
        content.padBottom(Scl.scl(5f));
        content.padLeft(Scl.scl(13f)).padRight(Scl.scl(13f));
    }

    public void toggle(){
        visible = !visible;
        if(visible){
            rebuild();
        }else{
            //刻意降级：原版 Core.scene.setKeyboardFocus(null)（本项目没有 Core.scene）→ 从 Actor 取 stage
            if(content.getStage() != null){
                content.getStage().setKeyboardFocus(null);
            }
            //刻意降级：原版 sField.clearText()（libgdx 的 TextField 没有该方法）→ setText("")
            sField.setText("");
        }
    }
}
