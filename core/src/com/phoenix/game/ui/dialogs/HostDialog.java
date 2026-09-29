package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Time;
import com.phoenix.game.core.Version;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.util.Log;
import io.anuke.mindustry.gen.Tex;

/**
 * 开服对话框（玩家名 + 颜色 + 开服按钮）。参照 Mindustry mindustry.ui.dialogs.HostDialog。
 *
 * <p><b>与本项目的差异</b>：
 * <ul>
 *   <li>原版 {@code t.addField(text, Cons)}（arc 的"加一个带文本变化回调的输入框"）libgdx 没有
 *       → 自建 {@link TextField} + {@code setTextFieldListener}。</li>
 *   <li>原版 {@code net.host(Vars.port)} → 本项目走 {@code Vars.netServer.openServer(Vars.port)}
 *       （{@code NetServer.openServer} 内部就是 {@code net.host} + 启动 LAN 发现）。
 *       注意本项目 {@code Vars.netServer} <b>是懒建的</b>，可能为 null → 加守卫。</li>
 *   <li><b>{@code player.isAdmin = true} 已接线</b>：本项目 {@code Player.isAdmin} 存在
 *       （{@code entities/type/Player.java:39}）→ 开服时写 {@code Vars.player.isAdmin}
 *       （消费点：{@code NetServer} 的玩家快照会把它同步给客户端）。
 *       ⚠ 菜单期 {@code Vars.player} 可能为 null → 判空后再写。</li>
 *   <li><b>{@code player.color.set(color)} 已接线</b>：本项目 {@code Player.color} 存在
 *       （{@code entities/type/Player.java:43}）→ 选色时写回 {@code Vars.player.color}
 *       （消费点：{@code NetServer.java:571} 的颜色快照）。仍保留一个本地 {@code color} 字段给按钮显示
 *       （菜单期没有 player）。</li>
 *   <li>刻意降级 arc 的 {@code button.update(Runnable)}（每帧回调，原版用它把按钮色跟随玩家色）libgdx 没有
 *       → 改成选色后直接给按钮上色（不再每帧刷新）。</li>
 *   <li>刻意降级 {@code Core.settings.getBoolOnce(key, Runnable)}：本项目 {@link com.phoenix.game.core.Settings}
 *       没有该方法 → 用 {@code getBool} + {@code put} 手动实现"只弹一次"。</li>
 *   <li>刻意降级原版 steam 分支（{@code putSave("publichost")} + {@code platform.updateLobby()}）：
 *       {@code Vars.steam} 恒 false → 分支不可达；{@code Settings} 没有 {@code putSave}（已降级为 {@code put + save}）。</li>
 *   <li>TODO {@code platform.updateLobby()}：本项目没有 {@code Vars.platform}
 *       （{@code core/Platform} 是无实例的接口）→ 无法接线。</li>
 *   <li><b>{@code Version} 已接线</b>：{@code Version.modifier.contains("beta")} 分支已补回判定
 *       （本项目 {@code modifier = ""} → 恒不进入）。其中 {@code putSave} 降级为 {@code put + save}、
 *       {@code platform.updateLobby()} 仍缺（没有 {@code Vars.platform}）、
 *       {@code getBoolOnce} 降级为 {@code getBool + put} 手搓。</li>
 * </ul>
 */
public class HostDialog extends FloatingDialog{
    float w = 300;

    /**
     * 当前选择的玩家颜色（对应原版 {@code player.color}）。本项目 {@code Vars.player.color} 已接线，
     * 但菜单期 {@code Vars.player} 可能为 null → 本地保留一份给按钮显示，选色时再写回真字段。
     */
    private final Color color = Vars.player != null ? new Color(Vars.player.color) : new Color(Vars.playerColors[0]);

    public HostDialog(){
        super("$hostserver");

        addCloseButton();

        Table row = new Table();
        row.add(new Label(bundle("$name"), Styles.defaultLabel)).padRight(Scl.scl(10f));

        TextField nameField = new TextField(Core.settings.getString("name", "player"), Styles.defaultField);
        //原版 .get().setMaxLength(40)
        nameField.setMaxLength(Vars.maxNameLength);
        nameField.setTextFieldListener((field, c) -> {
            String text = field.getText();
            if(Vars.player != null){
                Vars.player.name = text;
            }
            Core.settings.put("name", text);
            Core.settings.save();
            if(Vars.ui != null && Vars.ui.listfrag != null){
                Vars.ui.listfrag.rebuild();
            }
        });
        row.add(nameField).grow().pad(Scl.scl(8f));

        //原版 t.addImageButton(Tex.whiteui, Styles.clearFulli, 40, ...)：
        //libgdx 的 ImageButton 没有 (Drawable, style) 构造器 → 两步设置（imageUp + getImage().setDrawable）
        ImageButton.ImageButtonStyle bs = new ImageButton.ImageButtonStyle(Styles.clearFulli);
        bs.imageUp = Tex.whiteui;
        //刻意降级 原版 bs.imageUpColor = color：libgdx 的 ImageButtonStyle 没有颜色字段 → 只给内部 Image 设色（下一行）
        ImageButton colorButton = new ImageButton(bs);
        colorButton.getImage().setDrawable(Tex.whiteui);
        colorButton.getImage().setColor(color);
        //原版 imagesize = 40
        colorButton.getImageCell().size(Scl.scl(40f));
        colorButton.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener(){
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                new PaletteDialog().show(picked -> {
                    //原版 player.color.set(color)：本项目 Player.color 存在 → 写回真字段（消费点 NetServer 颜色快照）
                    color.set(picked);
                    if(Vars.player != null){
                        Vars.player.color.set(picked);
                    }
                    colorButton.getImage().setColor(color);
                    //libgdx 的 Color 没有 arc 的 rgba() → 用静态 rgba8888（与原版 color.rgba() 同为 int 格式）
                    Core.settings.put("color-0", com.badlogic.gdx.graphics.Color.rgba8888(picked));
                    Core.settings.save();
                });
            }
        });
        row.add(colorButton).size(Scl.scl(54f));

        cont.add(row).width(Scl.scl(w)).height(Scl.scl(70f)).pad(Scl.scl(4f)).colspan(3);

        cont.row();

        cont.add().width(Scl.scl(65f));

        cont.add(textButton("$host", null, this::runHost)).width(Scl.scl(w)).height(Scl.scl(70f));

        cont.add(textButton("?", null, () -> {
            if(Vars.ui != null) Vars.ui.showInfo("$host.info");
        })).size(Scl.scl(65f), Scl.scl(70f)).padLeft(Scl.scl(6f));

        shown(() -> {
            if(!Vars.steam){
                //原版 Core.app.post(() -> Core.settings.getBoolOnce("hostinfo", () -> ui.showInfo("$host.info")))
                //  —— Settings 没有 getBoolOnce → 用 getBool + put 实现"只弹一次"
                Gdx.app.postRunnable(() -> {
                    if(!Core.settings.getBool("hostinfo", false)){
                        Core.settings.put("hostinfo", true);
                        Core.settings.save();
                        if(Vars.ui != null) Vars.ui.showInfo("$host.info");
                    }
                });
            }
        });
    }

    public void runHost(){
        if(Vars.ui != null && Vars.ui.loadfrag != null){
            Vars.ui.loadfrag.show("$hosting");
        }

        Time.run(5f, () -> {
            try{
                //原版 net.host(Vars.port)：本项目 NetServer.openServer 内部即 net.host + LAN 发现
                if(Vars.netServer != null){
                    Vars.netServer.openServer(Vars.port);
                }else{
                    Log.warn("HostDialog.runHost: Vars.netServer is null, cannot host.");
                }
                //原版 player.isAdmin = true：本项目 Player.isAdmin 存在 → 写真字段（消费点 NetServer 玩家快照）
                if(Vars.player != null){
                    Vars.player.isAdmin = true;
                }

                //刻意降级 原版 steam 分支：Core.settings.putSave("publichost", ...) + platform.updateLobby()
                //  —— Vars.steam 恒 false → 分支不可达；Settings 没有 putSave、也没有 Vars.platform → 整段省略

                //原版 Version.modifier.contains("beta") 分支（本项目 modifier = "" → 恒不进入）
                if(Version.modifier.contains("beta")){
                    //刻意降级 原版 Core.settings.putSave("publichost", false)（Settings 没有 putSave）→ put + save
                    Core.settings.put("publichost", false);
                    Core.settings.save();
                    //TODO 原版 platform.updateLobby()（本项目没有 Vars.platform）
                    //刻意降级 原版 Core.settings.getBoolOnce("betapublic", ...)（Settings 没有 getBoolOnce）→ 手搓
                    if(!Core.settings.getBool("betapublic", false)){
                        Core.settings.put("betapublic", true);
                        Core.settings.save();
                        if(Vars.ui != null) Vars.ui.showInfo("$public.beta");
                    }
                }
            }catch(Throwable e){
                if(Vars.ui != null) Vars.ui.showException("$server.error", e);
            }
            if(Vars.ui != null && Vars.ui.loadfrag != null){
                Vars.ui.loadfrag.hide();
            }
            hide();
        });
    }
}
