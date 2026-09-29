package com.phoenix.game.editor;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Scl;
import com.phoenix.game.func.Cons;
import com.phoenix.game.maps.Map;
import com.phoenix.game.ui.BorderImage;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.ui.dialogs.FloatingDialog;
import io.anuke.mindustry.gen.Tex;

/**
 * 载入地图对话框（列出 {@code maps/} 下的全部 {@code .msav}，选一张送进编辑器）。
 * 参照 Mindustry {@code mindustry.editor.MapLoadDialog} 移植。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li><b>地图缩略图</b>：原版用 {@code map.safeTexture()} 给每张地图画预览。本项目
 *       {@link Map#texture} 恒为 null（尚未加载每张地图的预览 PNG，见 {@code Maps} 类注释的 TODO），
 *       且 {@code safeTexture()} 退回的是 {@code nomap} 所在的**整张图集纹理**（libgdx 的 Texture 不带区域信息，
 *       原版 arc 的 Texture 就是单张图）→ 直接用会把整页图集画出来。
 *       所以这里退化成 {@link Tex#nomap} 的**区域**包进 {@link BorderImage}（占位图，非真实预览）。</li>
 *   <li>原版 {@code maps.all().size} 是 arc 的 {@code Seq.size} 字段；本项目 {@code Maps.all()} 返回 libgdx
 *       {@code Array}（同样有 {@code size} 字段），但为了拿 `first()` 之外的语义统一改用下标循环。</li>
 *   <li>arc 的 {@code button.clicked(...)} / {@code add(String)} / {@code table.margin(...)} libgdx 没有
 *       → {@code ClickListener} / {@link Label} / {@code pad}。</li>
 *   <li>原版 {@code button.setDisabled(Boolp)}（动态禁用）→ 这里在重建时手工同步。</li>
 * </ul>
 */
public class MapLoadDialog extends FloatingDialog{
    private Map selected = null;
    /** "载入"按钮（原版用 {@code setDisabled(Boolp)} 动态判定）。 */
    private TextButton loadButton;

    public MapLoadDialog(Cons<Map> loader){
        super("$editor.loadmap");

        shown(this::rebuild);

        loadButton = textButton("$load", null, () -> {
            if(selected != null){
                loader.get(selected);
                hide();
            }
        });
        loadButton.setDisabled(true);

        buttons.defaults().size(Scl.scl(200f), Scl.scl(50f));
        buttons.add(textButton("$cancel", null, this::hide));
        buttons.add(loadButton);
    }

    public void rebuild(){
        cont.clear();
        Array<Map> all = Vars.maps.all();
        if(all.size > 0){
            selected = all.first();
        }else{
            selected = null;
        }
        if(loadButton != null){
            loadButton.setDisabled(selected == null);
        }

        ButtonGroup<TextButton> group = new ButtonGroup<>();

        int maxcol = 3;
        int i = 0;

        Table table = new Table();
        table.defaults().size(Scl.scl(200f), Scl.scl(90f)).pad(Scl.scl(4f));
        //原版 table.margin(10f)：libgdx 没有 margin（外间距）→ 用 pad
        table.pad(Scl.scl(10f));

        ScrollPane pane = new ScrollPane(table, Styles.horizontalPane);
        pane.setFadeScrollBars(false);

        for(int k = 0; k < all.size; k++){
            final Map map = all.get(k);

            TextButton button = new TextButton(map.name, Styles.togglet);
            //原版 = button.add(new BorderImage(map.safeTexture(), 2f).setScaling(Scaling.fit)).size(16 * 4f)
            BorderImage preview = new BorderImage(nomapRegion(), 2f);
            preview.setScaling(Scaling.fit);
            button.add(preview).size(Scl.scl(16 * 4f));
            //原版 button.getCells().reverse()：把预览图与文字的顺序调换成「图在左、字在右」
            button.getCells().reverse();
            button.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    selected = map;
                    if(loadButton != null) loadButton.setDisabled(false);
                }
            });
            button.getLabelCell().grow().left().padLeft(Scl.scl(5f));
            group.add(button);
            table.add(button);
            if(++i % maxcol == 0) table.row();
        }

        if(all.size == 0){
            table.add(new Label(bundle("$maps.none"), Styles.defaultLabel)).center();
        }else{
            cont.add(new Label(bundle("$editor.loadmap"), Styles.defaultLabel));
        }

        cont.row();
        cont.add(pane);
    }

    /**
     * @return {@code nomap} 占位图的区域。
     * <p>本项目生成的 {@code Tex.nomap} 是 {@link TextureRegionDrawable}（已验证），
     * 而 {@code BorderImage} 只接受 {@code TextureRegion}，所以这里取出区域再包一层。
     */
    private static TextureRegion nomapRegion(){
        return ((TextureRegionDrawable)Tex.nomap).getRegion();
    }
}
