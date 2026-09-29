package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Scl;
import com.phoenix.game.net.Administration.PlayerInfo;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * 服务端封禁列表（列出被封禁的玩家，可逐个解封）。参照 Mindustry mindustry.ui.dialogs.BansDialog。
 *
 * <p><b>与本项目的差异</b>：
 * <ul>
 *   <li>{@code Vars.netServer} 在本项目里<b>只在开启服务端时才创建</b>（{@code ClientLauncher} 里懒建），
 *       单机时是 null → 全部读取处都加了 null 守卫，列表为空并提示。</li>
 *   <li>原版 {@code netServer.admins.unbanPlayerID(uuid)} → 本项目 {@link com.phoenix.game.net.Administration}
 *       只有 {@code ban(uuid, boolean)} → 用 {@code ban(info.id, false)} 等价实现。</li>
 *   <li>arc 的 {@code res.labelWrap(...)} libgdx 没有 → 自建 {@link Label} + {@code setWrap(true)}。</li>
 *   <li>arc 的 {@code Table.addImageButton(icon, action)} libgdx 没有 → 见 {@link #iconButton}（ImageButtonStyle 两步设置）。</li>
 *   <li>arc 的 {@code new Table(Tex.button)} libgdx 没有（构造器只接 skin/style）→ 自建 Table 后 {@code setBackground}。</li>
 *   <li>arc 的 {@code res.margin(14f)} libgdx 没有 → 用 {@code pad} 代替。</li>
 * </ul>
 */
public class BansDialog extends FloatingDialog{

    public BansDialog(){
        super("$server.bans");

        addCloseButton();

        setup();

        shown(this::setup);
    }

    private void setup(){
        cont.clear();

        float w = 400f, h = 80f;

        Table table = new Table();

        //原版 new ScrollPane(table)（默认样式）；本项目统一走 Styles.defaultPane
        ScrollPane pane = new ScrollPane(table, Styles.defaultPane);
        pane.setFadeScrollBars(false);

        //原版直接 netServer.admins：本项目 netServer 可能为 null（未开服务端）→ 守卫
        if(Vars.netServer == null || Vars.netServer.admins.getBanned().size == 0){
            table.add(new Label(bundle("$server.bans.none"), Styles.defaultLabel));
        }

        if(Vars.netServer != null){
            for(PlayerInfo info : Vars.netServer.admins.getBanned()){
                Table res = new Table();
                res.setBackground(Tex.button);
                //原版 res.margin(14f)：libgdx 没有 margin → 用 pad
                res.pad(Scl.scl(14f));

                Label label = new Label("IP: [LIGHT_GRAY]" + info.lastIP + "\n[]Name: [LIGHT_GRAY]" + info.lastName,
                    Styles.defaultLabel);
                label.setWrap(true);
                res.add(label).width(Scl.scl(w - h - 24f));
                res.add().growX();

                res.add(iconButton(Icon.cancel, Styles.defaulti, () -> {
                    if(Vars.ui != null){
                        Vars.ui.showConfirm("$confirm", "$confirmunban", () -> {
                            //原版 unbanPlayerID(info.id)：本项目 Administration 只有 ban(uuid, boolean)
                            Vars.netServer.admins.ban(info.id, false);
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

    /**
     * 构造一个纯图标按钮。
     * <p><b>libgdx 的 {@code ImageButton} 没有 {@code (Drawable, ImageButtonStyle)} 构造器</b>，必须两步：
     * {@code bs.imageUp = icon}（不设的话 hover/按下状态一变 {@code updateImage()} 会把 drawable 重置为 null）
     * + 构造后 {@code getImage().setDrawable(icon)}（构造器不会把 imageUp 应用到内部 Image）。
     */
    /** 用图标 + 样式建一个 ImageButton（F13e 起供 PlayerListFragment 的管理员操作按钮复用）。 */
    public static ImageButton iconButton(Drawable icon, ImageButton.ImageButtonStyle style, Runnable action){
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
}
