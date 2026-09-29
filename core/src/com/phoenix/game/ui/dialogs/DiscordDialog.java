package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * Discord 邀请对话框（展示横幅 + 复制/打开链接）。参照 Mindustry mindustry.ui.dialogs.DiscordDialog。
 *
 * <p><b>与本项目的差异</b>：
 * <ul>
 *   <li>arc 的 {@code Table.margin(...)} libgdx 没有（Table/Cell 都只有 pad 内间距）→ 用 {@code pad} 代替。</li>
 *   <li>arc 的 {@code Table.table(Cons)} / {@code addImage()} / {@code add(String)} 便捷方法 libgdx 没有
 *       → 自建内层 {@link Table}，图标/文字用 {@code add(new Image(...))} / {@link Label}。</li>
 *   <li>arc 的 {@code Cell.color(...)} libgdx 没有 → 颜色直接设在 Actor 上（{@code Image.setColor} / {@code Label.setColor}）。</li>
 *   <li>原版 {@code Core.app.setClipboardText(...)} → libgdx 用 {@code Gdx.app.getClipboard().setContents(...)}。</li>
 *   <li>{@code Core.net.openURI}：本项目的 {@code Core.net} 就是 libgdx 的 {@code com.badlogic.gdx.Net}，
 *       它有 {@code openURI(String)}（语义一致）→ <b>无需降级</b>。</li>
 * </ul>
 */
public class DiscordDialog extends FloatingDialog{

    public DiscordDialog(){
        super("");

        float h = 70f;

        //原版 cont.margin(12f)：libgdx 的 Table/Cell 没有 margin（外间距）→ 用 pad 代替
        cont.pad(Scl.scl(12f));

        Color color = Color.valueOf("7289da");

        //原版 cont.table(t -> {...})：libgdx 没有带回调的 table(...) → 自建内层 Table
        Table banner = new Table();
        banner.setBackground(Tex.button);
        banner.pad(0f);

        //左侧竖条：上段原色 + 下段 0.8 倍暗色（对应原版两次 addImage）
        Table stripes = new Table();
        Image top = new Image(Tex.whiteui);
        top.setColor(color);
        stripes.add(top).height(Scl.scl(h - 5f)).width(Scl.scl(40f));
        stripes.row();
        Image bottom = new Image(Tex.whiteui);
        //原版 color.cpy().mul(0.8f, 0.8f, 0.8f, 1f)
        bottom.setColor(color.r * 0.8f, color.g * 0.8f, color.b * 0.8f, 1f);
        stripes.add(bottom).height(Scl.scl(5f)).width(Scl.scl(40f));
        banner.add(stripes).expandY();

        Table iconBox = new Table();
        iconBox.setBackground(Tex.button);
        iconBox.add(new Image(Icon.discord));
        banner.add(iconBox).size(Scl.scl(h)).left();

        Label name = new Label(bundle("$discord"), Styles.defaultLabel);
        name.setColor(Pal.accent);
        banner.add(name).growX().padLeft(Scl.scl(10f));

        cont.add(banner).size(Scl.scl(440f), Scl.scl(h)).pad(Scl.scl(10f));

        buttons.defaults().size(Scl.scl(150f), Scl.scl(50f));

        buttons.add(textButton("$back", null, this::hide));
        buttons.add(textButton("$copylink", null, () -> {
            //Core.app 没有 setClipboardText → 用 libgdx 的剪贴板
            Gdx.app.getClipboard().setContents(Vars.discordURL);
        }));
        buttons.add(textButton("$openlink", null, () -> {
            if(!Core.net.openURI(Vars.discordURL)){
                if(Vars.ui != null){
                    Vars.ui.showErrorMessage("$linkfail");
                }
                Gdx.app.getClipboard().setContents(Vars.discordURL);
            }
        }));
    }
}
