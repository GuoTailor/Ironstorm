package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Scl;
import com.phoenix.game.net.Administration.PlayerInfo;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * 服务端管理员列表（列出管理员，可逐个撤销）。参照 Mindustry mindustry.ui.dialogs.AdminsDialog。
 *
 * <p><b>与本项目的差异</b>：
 * <ul>
 *   <li>{@code Vars.netServer} 在本项目里只在开启服务端时才创建，单机时为 null → 全部读取处加了 null 守卫。</li>
 *   <li>原版 {@code netServer.admins.unAdminPlayer(uuid)} → 本项目 {@link com.phoenix.game.net.Administration}
 *       只有 {@code setAdmin(uuid, boolean)} → 用 {@code setAdmin(info.id, false)} 等价实现。</li>
 *   <li>原版撤销管理员后遍历 {@code playerGroup.all()} 把在场玩家的 {@code isAdmin} 清掉：<b>已由既有实现满足</b>
 *       ——{@link com.phoenix.game.entities.type.Player#isAdmin} 字段<b>存在</b>（{@code entities/type/Player.java:39}），
 *       且 {@code Vars.netServer.setPlayerAdmin(uuid, false)} 内部已同步在场玩家对象（{@code core/NetServer.java:606}
 *       → 写 {@code Vars.player.isAdmin} + {@code RemotePlayer.player.isAdmin}）→ 无需在本对话框再遍历一遍。</li>
 *   <li>arc 的 {@code labelWrap} / {@code addImageButton} / {@code new Table(Tex.button)} / {@code margin}
 *       libgdx 都没有 → 分别用 {@link Label}+wrap、{@link BansDialog#iconButton}、{@code setBackground}、{@code pad}。</li>
 * </ul>
 */
public class AdminsDialog extends FloatingDialog{

    public AdminsDialog(){
        super("$server.admins");

        addCloseButton();

        setup();
        shown(this::setup);
    }

    private void setup(){
        cont.clear();

        float w = 400f, h = 80f;

        Table table = new Table();

        ScrollPane pane = new ScrollPane(table, Styles.defaultPane);
        pane.setFadeScrollBars(false);

        if(Vars.netServer == null || Vars.netServer.admins.getAdmins().size == 0){
            table.add(new Label(bundle("$server.admins.none"), Styles.defaultLabel));
        }

        if(Vars.netServer != null){
            for(PlayerInfo info : Vars.netServer.admins.getAdmins()){
                Table res = new Table();
                res.setBackground(Tex.button);
                res.pad(Scl.scl(14f));

                Label label = new Label("[LIGHT_GRAY]" + info.lastName, Styles.defaultLabel);
                label.setWrap(true);
                res.add(label).width(Scl.scl(w - h - 24f));
                res.add().growX();

                res.add(BansDialog.iconButton(Icon.cancel, Styles.defaulti, () -> {
                    if(Vars.ui != null){
                        Vars.ui.showConfirm("$confirm", "$confirmunadmin", () -> {
                            //原版 unAdminPlayer(info.id)：本项目用 setPlayerAdmin（F13g：改档案 + 同步在场玩家 + 广播列表）
                            Vars.netServer.setPlayerAdmin(info.id, false);
                            setup();
                        });
                    }
                })).size(Scl.scl(h)).pad(Scl.scl(-14f));

                table.add(res).width(Scl.scl(w)).height(Scl.scl(h));
                table.row();
            }
        }

        cont.add(pane);
    }
}
