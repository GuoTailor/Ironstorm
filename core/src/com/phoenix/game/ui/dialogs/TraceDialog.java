package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.net.Administration;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import io.anuke.mindustry.gen.Tex;

/**
 * 玩家追踪信息对话框，参照 Mindustry mindustry.ui.dialogs.TraceDialog。
 */
public class TraceDialog extends FloatingDialog{

    public TraceDialog(){
        super("$trace");

        addCloseButton();
        setFillParent(false);
    }

    /** 显示某个玩家的追踪信息（对应原版 {@code show(Player, TraceInfo)}）。 */
    public void show(Player player, Administration.TraceInfo info){
        cont.clear();

        //原版 new Table(Tex.clear)：无边框的纯色底板
        Table table = new Table();
        table.setBackground(Tex.clear);
        table.pad(Scl.scl(14f));
        table.defaults().pad(Scl.scl(1f));

        table.defaults().left();
        table.add(label(Core.bundle.format("trace.playername", player.name)));
        table.row();
        table.add(label(Core.bundle.format("trace.ip", info.ip)));
        table.row();
        table.add(label(Core.bundle.format("trace.id", info.uuid)));
        table.row();
        table.add(label(Core.bundle.format("trace.modclient", info.modded)));
        table.row();
        table.add(label(Core.bundle.format("trace.mobile", info.mobile)));
        table.row();

        table.add().pad(Scl.scl(5f));
        table.row();

        cont.add(table);

        show();
    }

    /** 追踪信息里的文字带 {@code [accent]} 标记，必须用项目的 Label（会解析 markup）。 */
    private static Label label(String text){
        return new Label(text, Styles.defaultLabel);
    }
}
