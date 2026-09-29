package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.ui.ContentDisplay;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.Block;

/**
 * 内容信息对话框，参照 Mindustry mindustry.ui.dialogs.ContentInfoDialog。
 * <p>原版继承 FloatingDialog（标题 + 关闭按钮 + ScrollPane 内容，打开时不暂停游戏）；
 * phoenix 没有 FloatingDialog，用 libgdx 原生 {@link Dialog} 复刻同样的结构。
 * <p>对话框是懒创建的：构造期 {@code Core.bundle} 可能还没加载，标题取不到本地化文本。
 */
public class ContentInfoDialog{
    /** 内容区宽度，对应原版 ScrollPane 内表格的 400px 文本宽度。 */
    private static final float WIDTH = 440f;
    /** 内容区最大高度：装得下就按内容自适应，装不下才滚动（原版也是这个行为）。 */
    private static final float MAX_HEIGHT = 520f;

    private Dialog dialog;
    /** 滚动区里的内容表，每次 show 重建。 */
    private Table content;
    /** 全屏遮罩（对应原版 DialogStyle.stageBackground：打开时整屏变暗）。 */
    private Image shade;

    /** 显示某个方块的信息（对应原版 {@code ContentInfoDialog.show(UnlockableContent)}）。 */
    public void show(Block block, Stage stage){
        if(stage == null) return;
        if(dialog == null) create();

        content.clearChildren();
        ContentDisplay.displayBlock(content, block);

        //遮罩只添加一次：libgdx 的 addActor 会把它提到最上层，重复添加会盖住对话框
        if(shade.getStage() == null){
            stage.addActor(shade);
        }
        shade.setVisible(true);
        dialog.show(stage);
        //pack 后重新居中：show() 内部用的是 pack 前的旧尺寸，否则窗口与 hit 区域会错开
        dialog.pack();
        dialog.setPosition((stage.getWidth() - dialog.getWidth()) / 2f,
                           (stage.getHeight() - dialog.getHeight()) / 2f);
    }

    /** @return 对话框是否正显示（libgdx 的 Dialog 没有 isShown()，离开 stage 即已隐藏）。 */
    public boolean isShown(){
        return dialog != null && dialog.getStage() != null;
    }

    /** 收起对话框（未创建过则无操作）。 */
    public void hide(){
        if(shade != null) shade.setVisible(false);
        if(dialog != null) dialog.hide();
    }

    private void create(){
        //libgdx 的 Dialog 只吃 WindowStyle，没有 arc 那样的 DialogStyle：
        //窗口背景与标题样式取自项目样式表，stageBackground 则要自己铺一层遮罩
        Window.WindowStyle window = new Window.WindowStyle(
            Styles.defaultDialog.titleFont, Styles.defaultDialog.titleFontColor, Styles.defaultDialog.background);
        dialog = new Dialog(plain(Core.bundle.get("info.title")), window){
            @Override
            public void hide(){
                //原版 arc 的 Dialog.hide() 是立即收掉；libgdx 默认带 0.4s 淡出，
                //会让遮罩先消失、窗口还浮在亮背景上，这里跟随原版立即移除
                remove();
            }
        };
        //不设 modal：点空白处关闭交给遮罩处理（modal 会把点击全部吃掉，遮罩收不到）
        dialog.setModal(false);

        shade = new Image(Styles.black9);
        shade.setFillParent(true);
        shade.setVisible(false);
        shade.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                hide();
            }
        });

        content = new Table();
        content.top().left();
        dialog.getContentTable().add(new ScrollPane(content, Styles.defaultPane))
            .width(Scl.scl(WIDTH)).maxHeight(Scl.scl(MAX_HEIGHT)).pad(Scl.scl(6f));

        //关闭按钮（对应原版 FloatingDialog.addCloseButton 的 "$back"）
        Button close = new Button(Styles.defaultb);
        close.add(new Label(Core.bundle.get("back"), Styles.defaultLabel)).pad(Scl.scl(5f));
        close.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                dialog.hide();
            }
        });
        dialog.getButtonTable().add(close).width(Scl.scl(210f)).height(Scl.scl(50f)).pad(Scl.scl(4f));
    }

    /** 去掉 arc 的颜色标记：标题由 libgdx 的 Window 用 titleLabel 绘制，不走本项目解析 markup 的 Label。 */
    private static String plain(String text){
        return text == null ? "" : text.replaceAll("\\[[^\\[\\]]*\\]", "");
    }
}
