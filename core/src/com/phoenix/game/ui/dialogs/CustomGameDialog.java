package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.game.Gamemode;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.maps.Map;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * 自定义游戏（把 {@code Vars.maps.all()} 里的地图铺成卡片网格，点开选模式开局）。
 * 参照 Mindustry mindustry.ui.dialogs.CustomGameDialog。
 *
 * <p><b>与本项目的差异（本类是本批降级最重的）</b>：
 * <ul>
 *   <li>原版 {@code private MapPlayDialog dialog = new MapPlayDialog();}
 *       + {@code image.clicked(() -> dialog.show(map))} <b>已落实</b>：本项目
 *       {@link com.phoenix.game.core.Control#playMap} 已补齐，点卡片改为弹出 {@link MapPlayDialog}
 *       （选模式 → 开局），与 {@code MapPlayDialog.play} 里的 {@code ui.custom.hide()} 成对。</li>
 *   <li><b>地图缩略图：F14 已接线</b>：{@link Map#texture} 由预览链填充
 *       （{@code Maps.loadPreviews} 直载 PNG / {@code Maps.createNewPreview} 离线生成），
 *       预览区出真图；仍为 null 时（v6 旧图未重存）用 {@link Tex#nomap} 占位，见 {@link #previewOf}。</li>
 *   <li>模式小图标：原版走 {@code Vars.ui.getIcon("mode" + Strings.capitalize(name) + "Small")}。
 *       {@code UI.getIcon} 现已支持（会先把 camelCase 还原成 kebab-case 再查图集，见 {@code core/UI.java}
 *       的 {@code getIcon}/{@code camelToKebab}）→ 该写法可用；但这里直接用生成的 {@code Icon.modeXxxSmall}
 *       字段更直接、且能显式表达"没有该图标"。注意 {@code sandbox}/{@code editor} 两个模式确实没有对应图标
 *       → 由 {@link #modeIcon} 返回 null 跳过。</li>
 *   <li>原版 {@code Core.atlas.isFound(icon.getRegion())}（判断图标是否真的存在）libgdx 的 TextureAtlas 没有 →
 *       靠 {@link #modeIcon} 返回 null 表达"没有图标"。</li>
 *   <li>arc 的 {@code Table.margin(...)} / {@code addImage(...)} / {@code add(String)} / {@code Cell.color(...)}
 *       libgdx 都没有 → 用 {@code pad} / {@code add(new Image(...))} / {@link Label} / Actor 设色。</li>
 *   <li>原版卡片是 {@code new ImageButton(map.safeTexture(), Styles.cleari)} 再往里手动排布局
 *       （arc 的 ImageButton 就是 Table）。libgdx 的 ImageButton 也是 Table，但没有 {@code (Drawable, style)}
 *       构造器，且要先 {@code getImage().remove()} 摘掉内置图标（留下一个空 cell）才能排自己的内容
 *       → 改成等价的 {@link Button}（同样是 Table，up/over/down 直接取 {@code Styles.cleari} 的同名字段），
 *       布局代码照抄原版。</li>
 *   <li>{@code BorderImage border = new BorderImage(map.safeTexture(), 3f); image.replaceImage(border);}
 *       —— 依赖地图纹理，且本项目没有 {@code BorderImage}（自建 UI 替代层）→ 整段省略（刻意降级）。</li>
 *   <li>原版 {@code clearChildren(); add(titleTable); row(); stack(cont, buttons).grow(); buttons.bottom();}
 *       是把 cont/buttons 重新排成"标题在上、内容与按钮重叠"的布局；libgdx 的 Dialog 布局由 Window 管理，
 *       这里只清空 {@code cont} 后重填（标题/按钮沿用 {@link FloatingDialog} 的默认位置）。</li>
 * </ul>
 */
public class CustomGameDialog extends FloatingDialog{
    /** 选模式/自定义规则的开局对话框（原版 {@code private MapPlayDialog dialog = new MapPlayDialog();}）。 */
    private final MapPlayDialog dialog = new MapPlayDialog();

    public CustomGameDialog(){
        super("$customgame");
        addCloseButton();
        shown(this::setup);
        onResize(this::setup);
    }

    void setup(){
        cont.clear();

        Table maps = new Table();
        maps.top().left();

        ScrollPane pane = new ScrollPane(maps, Styles.defaultPane);
        pane.setFadeScrollBars(false);

        int maxwidth = Mathf.clamp((int)(Core.graphics.getWidth() / Scl.scl(200f)), 1, 8);
        float images = 146f;

        int i = 0;
        //原版 maps.defaults().width(170).fillY().top().pad(4f)
        maps.defaults().width(Scl.scl(170f)).top().pad(Scl.scl(4f));

        //原版 for(Map map : Vars.maps.all())：本项目 all() 返回 libgdx Array → 显式下标循环
        Array<Map> all = Vars.maps.all();
        for(int idx = 0; idx < all.size; idx++){
            Map map = all.get(idx);

            //原版 if(i % maxwidth == 0) maps.row()（i=0 那次是空行，libgdx 的 Table.row() 允许空行）
            if(i % maxwidth == 0){
                maps.row();
            }

            //原版 new ImageButton(new TextureRegion(map.safeTexture()), Styles.cleari)，然后在按钮上
            //手动排 title/modes/预览图（arc 的 ImageButton 就是 Table）。libgdx 的 ImageButton 也是 Table，
            //但没有 (Drawable, style) 构造器、还得先摘掉内置图标（留一个空 cell）→ 改用等价的 Button，
            //底色沿用 Styles.cleari 的 up/over/down。
            Button.ButtonStyle cardStyle = new Button.ButtonStyle();
            cardStyle.up = Styles.cleari.up;
            cardStyle.over = Styles.cleari.over;
            cardStyle.down = Styles.cleari.down;
            Button card = new Button(cardStyle);
            card.pad(Scl.scl(5f));
            card.top();

            //模式小图标行
            Table modes = new Table();
            modes.left();
            for(Gamemode mode : Gamemode.all){
                if(mode.hidden || !mode.valid(map)) continue;
                Drawable icon = modeIcon(mode);
                if(icon != null){
                    modes.add(new Image(icon)).size(Scl.scl(16f)).pad(Scl.scl(4f));
                }
            }
            card.add(modes).left();
            card.row();

            //地图名（原版 add(map.name()).pad(1f).growX().wrap().left().get().setEllipsis(true)）
            Label name = new Label(map.name, Styles.defaultLabel);
            name.setEllipsis(true);
            card.add(name).pad(Scl.scl(1f)).growX().left();
            card.row();

            //分隔线（原版 addImage(Tex.whiteui, Pal.gray).growX().pad(3).height(4f)）
            Image divider = new Image(Tex.whiteui);
            divider.setColor(Pal.gray);
            card.add(divider).growX().pad(Scl.scl(3f)).height(Scl.scl(4f));
            card.row();

            //预览图：原版 add(img).size(images)（img 是上面从按钮里摘下来的内置 Image，贴 map.safeTexture()）
            //  —— 本项目还没有加载地图预览纹理 → previewOf 退回 nomap 占位
            Image preview = new Image(previewOf(map));
            preview.setScaling(Scaling.fit);
            card.add(preview).size(Scl.scl(images));

            //原版 image.clicked(() -> dialog.show(map))
            card.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    play(map);
                }
            });

            maps.add(card);

            i++;
        }

        if(all.size == 0){
            maps.add(new Label(bundle("$maps.none"), Styles.defaultLabel)).pad(Scl.scl(50f));
        }

        cont.add(pane).uniformX();
    }

    /**
     * 原版：{@code dialog.show(map)}（{@link MapPlayDialog}：选模式 → {@code control.playMap} 开局）。
     * <p>开局本身由 {@link MapPlayDialog#play} 负责（那里会顺手 {@code ui.custom.hide()} 收起本框）。
     */
    private void play(Map map){
        dialog.show(map);
    }

    /**
     * @return 地图预览的 Drawable：有预览纹理就用它，否则退回 {@link Tex#nomap} 占位。
     * <p>不能无条件用 {@code map.safeTexture()}：它在没有预览纹理时会退回**整张图集**里 nomap 那一页，
     * 而 libgdx 的 {@code Texture} 不带区域信息（原版 arc 的 {@code Texture} 就是整张图，故可直接用）。
     */
    private static Drawable previewOf(Map map){
        if(map.texture == null) return Tex.nomap;
        Texture texture = map.safeTexture();
        return texture == null ? Tex.nomap : new TextureRegionDrawable(new TextureRegion(texture));
    }

    /** @return 该模式的 16px 小图标；没有对应图标（sandbox/editor）时返回 null。 */
    private static Drawable modeIcon(Gamemode mode){
        switch(mode){
            case survival: return Icon.modeSurvivalSmall;
            case attack: return Icon.modeAttackSmall;
            case pvp: return Icon.modePvpSmall;
            default: return null;
        }
    }
}
