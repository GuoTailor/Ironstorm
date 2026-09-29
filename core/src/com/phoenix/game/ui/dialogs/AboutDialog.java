package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ObjectSet;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Time;
import com.phoenix.game.game.EventType;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.struct.Array;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Links;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.util.Log;
import com.phoenix.game.util.OS;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * 关于对话框（外链 + 贡献者名单），参照 Mindustry mindustry.ui.dialogs.AboutDialog。
 *
 * <p><b>与本项目的差异</b>：
 * <ul>
 *   <li>arc 的 {@code Table.addImage()/addImageButton()/table(Cons)/labelWrap()/margin()}、
 *       {@code Cell.color()} libgdx 都没有 → 改成"建好 Actor 再 add"，颜色设在 Actor 上
 *       （见 {@link #iconButton} 与 setup 里的注释）。</li>
 *   <li>{@code ui.showErrorMessage("$linkfail")}：<b>已接线</b>（{@code UI.showErrorMessage(String)} 见
 *       {@code core/UI.java:567}，内部会 {@code bundle()} 解析裸 {@code $key}）；剪贴板改用
 *       {@code Core.app.getClipboard().setContents(...)}。</li>
 *   <li>{@code assets/contributors} 文件不存在时直接留空（原版无条件读，本项目资源是裁剪过的）。</li>
 *   <li>{@code ObjectSet.with(...)} libgdx 没有 → 静态块里逐条 add。</li>
 * </ul>
 */
public class AboutDialog extends FloatingDialog{
    /** 每个外链条目的高度（原版按竖屏/横屏取值）。 */
    private static final float ROW_HEIGHT_PORTRAIT = 90f, ROW_HEIGHT_LANDSCAPE = 80f;
    /** 每个外链条目的宽度（原版按竖屏/横屏取值）。 */
    private static final float ROW_WIDTH_PORTRAIT = 330f, ROW_WIDTH_LANDSCAPE = 600f;
    /** 左侧色条宽度（原版写死 40f）。 */
    private static final float BAR_WIDTH = 40f;
    /** 关闭/credits 按钮尺寸（原版 210x64 与 200x64）。 */
    private static final float CLOSE_WIDTH = 210f, CLOSE_HEIGHT = 64f, CREDITS_WIDTH = 200f;

    private Array<String> contributors = new Array<>();
    private static final ObjectSet<String> bannedItems = new ObjectSet<>();

    static{
        bannedItems.addAll("google-play", "itch.io", "dev-builds", "f-droid");
    }

    public AboutDialog(){
        super("$about.button");

        shown(() -> {
            //原版直接 readString；本项目 assets 里可能没有 contributors（资源是裁剪过的）
            if(Core.files.internal("contributors").exists()){
                contributors = Array.with(Core.files.internal("contributors").readString("UTF-8").split("\n"));
            }else{
                contributors = new Array<>();
            }
            Core.app.postRunnable(this::setup);
        });

        shown(this::setup);
        onResize(this::setup);
    }

    void setup(){
        cont.clear();
        buttons.clear();

        float h = isPortrait() ? ROW_HEIGHT_PORTRAIT : ROW_HEIGHT_LANDSCAPE;
        float w = isPortrait() ? ROW_WIDTH_PORTRAIT : ROW_WIDTH_LANDSCAPE;

        Table in = new Table();
        ScrollPane pane = new ScrollPane(in, Styles.defaultPane);
        pane.setFadeScrollBars(false);

        for(Links.LinkEntry link : Links.getLinks()){
            if((Vars.ios || OS.isMac || Vars.steam) && bannedItems.contains(link.name)){
                continue;
            }

            Table table = new Table();
            table.setBackground(Tex.underline);
            table.pad(0f);

            //左侧竖色条：上半 40x(h-5)，下半 40x5 稍暗（原版 table.table(img -> {...})）
            Table bar = new Table();
            Image barTop = new Image(Tex.whiteui);
            barTop.setColor(link.color);
            bar.add(barTop).height(Scl.scl(h - 5f)).width(Scl.scl(BAR_WIDTH));
            bar.row();
            Image barBottom = new Image(Tex.whiteui);
            barBottom.setColor(link.color.cpy().mul(0.8f, 0.8f, 0.8f, 1f));
            bar.add(barBottom).height(Scl.scl(5f)).width(Scl.scl(BAR_WIDTH));
            table.add(bar).expandY();

            //图标块（原版 table.table(i -> { i.background(Tex.buttonEdge3); i.addImage(link.icon); })）
            Table icon = new Table();
            icon.setBackground(Tex.buttonEdge3);
            icon.add(new Image(link.icon));
            table.add(icon).size(Scl.scl(h - 5f), Scl.scl(h));

            //标题 + 描述（原版 table.table(inset -> {...})）
            Table inset = new Table();
            inset.add(new Label("[accent]" + link.title, Styles.defaultLabel)).growX().left();
            inset.row();
            Label description = new Label(link.description, Styles.defaultLabel);
            description.setWrap(true);
            description.setColor(Color.LIGHT_GRAY);
            inset.add(description).width(Scl.scl(w - 100f)).growX();
            table.add(inset).padLeft(Scl.scl(8f));

            table.add(iconButton(Icon.link, () -> {
                if(link.name.equals("wiki")) Events.fire(EventType.Trigger.openWiki);

                if(!Core.net.openURI(link.link)){
                    //原版：ui.showErrorMessage("$linkfail")（Vars.ui 门面已存在）
                    Log.warn("Failed to open link: {0}", link.link);
                    if(Vars.ui != null){
                        Vars.ui.showErrorMessage("$linkfail");
                    }
                    Core.app.getClipboard().setContents(link.link);
                }
            })).size(Scl.scl(h - 5f), Scl.scl(h));

            in.add(table).size(Scl.scl(w), Scl.scl(h)).padTop(Scl.scl(5f)).row();
        }

        shown(() -> Time.run(1f, () -> {
            //对应原版 Core.scene.setScrollFocus(pane)：本项目从 getStage() 取
            if(getStage() != null) getStage().setScrollFocus(pane);
        }));

        cont.add(pane).growX().growY();

        addCloseButton();

        buttons.add(textButton("$credits", Styles.cleart, this::showCredits))
            .size(Scl.scl(CREDITS_WIDTH), Scl.scl(CLOSE_HEIGHT));

        if(isPortrait()){
            for(Cell cell : buttons.getCells()){
                cell.width(Scl.scl(140f));
            }
        }
    }

    public void showCredits(){
        FloatingDialog dialog = new FloatingDialog("$credits");
        dialog.addCloseButton();

        Label text = new Label(bundle("$credits.text"), Styles.defaultLabel);
        text.setWrap(true);
        text.setAlignment(Align.center);
        dialog.cont.add(text).fillX().growX().pad(Scl.scl(6f)).row();

        if(!contributors.isEmpty()){
            dialog.cont.add(new Image(Tex.whiteui.tint(Pal.accent)))
                .fillX().height(Scl.scl(3f)).pad(Scl.scl(3f)).row();
            dialog.cont.add(new Label(bundle("$contributors"), Styles.defaultLabel)).row();

            //原版 dialog.cont.pane(new Table(){{ left(); ... 每 3 个换行 }})：libgdx 没有 pane()
            Table names = new Table();
            names.left();
            int i = 0;
            for(String c : contributors){
                names.add(new Label("[lightgray]" + c, Styles.defaultLabel)).left()
                    .pad(Scl.scl(3f)).padLeft(Scl.scl(6f)).padRight(Scl.scl(6f));
                if(++i % 3 == 0){
                    names.row();
                }
            }
            ScrollPane pane = new ScrollPane(names, Styles.defaultPane);
            pane.setFadeScrollBars(false);
            dialog.cont.add(pane).grow();
        }
        dialog.show();
    }

    /** @return 是否竖屏（对应 arc 的 {@code Core.graphics.isPortrait()}，libgdx 的 Graphics 没有该方法）。 */
    private static boolean isPortrait(){
        return Core.graphics.getHeight() > Core.graphics.getWidth();
    }

    /** 用图标的图片按钮（对应 arc 的 {@code Table.addImageButton(icon, action)}）。 */
    private static ImageButton iconButton(Drawable icon, Runnable action){
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle(Styles.defaulti);
        style.imageUp = icon;
        ImageButton button = new ImageButton(style);
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                action.run();
            }
        });
        return button;
    }
}
