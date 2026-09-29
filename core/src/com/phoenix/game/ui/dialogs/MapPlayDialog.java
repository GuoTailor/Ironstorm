package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Strings;
import com.phoenix.game.game.Gamemode;
import com.phoenix.game.game.Rules;
import com.phoenix.game.maps.Map;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * 地图开局对话框（显示地图名 → 选游戏模式 → 自定义规则 → 开局）。
 * 参照 Mindustry mindustry.ui.dialogs.MapPlayDialog。
 *
 * <p><b>与本项目的差异（逐条对应原版写法）</b>
 * <ul>
 *   <li>原版 {@code control.playMap(map, rules)} <b>已落实</b>：{@code Control.playMap(Map, Rules)}
 *       已按原版补齐（加载地图 → 套规则 → 重建队伍/玩家/相机 → 进入 playing），开局按钮直接调它。</li>
 *   <li>原版 {@code map.applyRules(mode)} <b>已落实</b>：{@link Map#applyRules} 现在存在，
 *       语义与原版一致（{@code mode.apply(out)} 之后再 {@link Map#rules(Rules)} 套地图覆盖）。
 *       {@code Map.rules(Rules)} 内部也已接通 {@code io/JsonIO}（T8b）→ 能从地图元数据
 *       {@code tags["rules"]} 反序列化 rules 覆盖（不再是"只保留默认波次"的桩）。</li>
 *   <li>原版 {@code map.getHightScore()} <b>已落实</b>：走 {@code Core.settings}（键 {@code hiscore<地图名>}），
 *       生存模式下的"最高分"一行已恢复。</li>
 *   <li><b>地图预览纹理：半落实</b>。{@code map.safeTexture()} 现在存在，但本项目
 *       {@link Map#texture} 恒为 null（尚未加载每张地图的预览 PNG，见 {@code Maps} 类注释的 TODO）
 *       → 见 {@link #previewOf}：没有预览纹理时退回 {@link Tex#nomap} 占位。
 *       另外原版用 {@code BorderImage}（描边），本项目保留 {@code Image}（描边走 batch 铺白条，
 *       这里没有需要描边的真实预览图）。</li>
 *   <li>{@code ui.custom}：<b>已接线</b>——{@code Vars.ui.custom} 存在（{@link CustomGameDialog}），
 *       {@link #play(Map)} 里开局后按原版 {@code ui.custom.hide()} 收起本框（判空后调用）。</li>
 *   <li>arc 的 {@code Table.add(String)} / {@code addButton} / {@code addImageTextButton} /
 *       {@code label(Prov)} / {@code Cell.update(Boolp)} libgdx 都没有 →
 *       {@code add(String)} 用 {@link Label}；{@code addButton} 用 {@link #textButton}；
 *       {@code addImageTextButton} 用 {@link #imageTextButton}；
 *       {@code Cell.update(b -> b.setChecked(...))}（每帧同步选中态）改成"点击时手工刷新所有按钮的
 *       checked"（见 {@link #show(Map)}）。</li>
 *   <li>{@code Core.bundle.get("mode.help.title")} → 走 {@link I18n#get}（本项目 bundle 缺键返回
 *       {@code ???key???}，不能直接用 bundle 的 fallback）。</li>
 *   <li>{@code Core.graphics.isPortrait()}：本项目没有该方法 → 就地判宽高（等价替代，刻意降级）。</li>
 * </ul>
 */
public class MapPlayDialog extends FloatingDialog{
    /** 自定义规则对话框（原版是字段初始化，同一实例反复复用）。 */
    CustomRulesDialog dialog = new CustomRulesDialog();
    /** 当前这套规则（选模式 / 自定义后会被替换）。 */
    Rules rules;
    /** 当前选中的模式（原版标了 {@code @NonNull}，默认生存）。 */
    Gamemode selectedGamemode = Gamemode.survival;
    /** 上次显示的地图（窗口尺寸变化时用它重排）。 */
    Map lastMap;

    public MapPlayDialog(){
        super("");
        setFillParent(false);

        //原版 onResize 里保留 rules 再重排（重排会重新 applyRules 覆盖掉用户的自定义）
        onResize(() -> {
            if(lastMap != null){
                Rules rules = this.rules;
                show(lastMap);
                this.rules = rules;
            }
        });
    }

    public void show(Map map){
        this.lastMap = map;
        //原版 title.setText(map.name())：本项目 title 是 libgdx 原生 Label（不解析 arc markup）
        title.setText(map.name);
        cont.clearChildren();

        //切到"进攻"等模式后若当前模式对新地图无效，回退到第一个有效模式（原版 Structs.find）
        if(!selectedGamemode.valid(map)){
            selectedGamemode = null;
            for(Gamemode mode : Gamemode.all){
                if(mode.valid(map)){
                    selectedGamemode = mode;
                    break;
                }
            }
            if(selectedGamemode == null){
                selectedGamemode = Gamemode.survival;
            }
        }

        rules = map.applyRules(selectedGamemode);

        Table selmode = new Table();
        //原版 selmode.add("$level.mode").colspan(4)：arc 的 add(String) 会解析 $ → 这里用 Label
        selmode.add(new Label(bundle("$level.mode"), Styles.defaultLabel)).colspan(4);
        selmode.row();

        Table modes = new Table();

        //原版 Cell.update(b -> b.setChecked(selectedGamemode == mode))（每帧回调）libgdx 没有
        //→ 记住每个按钮与它对应的模式，点击时手工刷新一遍 checked
        Array<TextButton> modeButtons = new Array<>();
        Array<Gamemode> modeValues = new Array<>();

        int i = 0;
        for(Gamemode mode : Gamemode.all){
            if(mode.hidden) continue;

            TextButton button = textButton(mode.toString(), Styles.togglet, () -> {
                selectedGamemode = mode;
                rules = map.applyRules(mode);
                for(int k = 0; k < modeValues.size; k++){
                    modeButtons.get(k).setChecked(modeValues.get(k) == selectedGamemode);
                }
            });
            button.setChecked(selectedGamemode == mode);
            //原版 .disabled(!mode.valid(map))：libgdx 的 Cell 没有动态禁用 → 创建时定一次
            button.setDisabled(!mode.valid(map));

            modes.add(button).size(Scl.scl(140f), Scl.scl(54f));
            modeButtons.add(button);
            modeValues.add(mode);

            if(i++ % 2 == 1){
                modes.row();
            }
        }
        selmode.add(modes);
        selmode.add(textButton("?", null, this::displayGameModeHelp)).width(Scl.scl(50f)).fillY().padLeft(Scl.scl(18f));

        cont.add(selmode);
        cont.row();
        //原版 dialog.show(rules, () -> rules = map.applyRules(selectedGamemode))
        cont.add(imageTextButton("$customize", Icon.settings, null,
            () -> dialog.show(rules, () -> rules = map.applyRules(selectedGamemode)))).width(Scl.scl(230f));
        cont.row();

        //原版 cont.add(new BorderImage(map.safeTexture(), 3f))：本项目没有真实预览纹理 → previewOf 退回 nomap
        Image preview = new Image(previewOf(map));
        preview.setScaling(Scaling.fit);
        cont.add(preview).size(Scl.scl(Vars.mobile && !isPortrait() ? 150f : 250f));

        //原版：only maps with survival are valid for high scores
        if(Gamemode.survival.valid(map)){
            cont.row();
            //原版 Core.bundle.format("level.highscore", map.getHightScore())；本项目 bundle 缺键会得到
            //???key???{0} → 走 Strings.format(I18n.get(key,key), args)
            cont.add(new Label(Strings.format(I18n.get("level.highscore", "level.highscore"), map.getHightScore()),
                Styles.defaultLabel)).pad(Scl.scl(3f));
        }

        buttons.clearChildren();
        addCloseButton();

        buttons.add(imageTextButton("$play", Icon.play, null, () -> play(map))).size(Scl.scl(210f), Scl.scl(64f));

        show();
    }

    /**
     * 原版：{@code control.playMap(map, rules); hide(); ui.custom.hide();}
     * <p>本项目 {@link com.phoenix.game.core.Control#playMap} 已实现（加载地图 + 套规则 +
     * 重建队伍/玩家/相机 + 进入 playing），所以这里按原版直接开局并关掉本框与自定义游戏框。
     */
    private void play(Map map){
        Vars.control.playMap(map, rules);
        hide();
        //原版 ui.custom.hide()：本框可能是从 CustomGameDialog 弹出来的，开局后要把它也收掉
        if(Vars.ui != null && Vars.ui.custom != null){
            Vars.ui.custom.hide();
        }
    }

    /**
     * @return 地图预览的 Drawable：有预览纹理就用它，否则退回 {@link Tex#nomap} 占位。
     * <p>不能无条件用 {@code map.safeTexture()}：它在没有预览纹理时会退回**整张图集**里 nomap 那一页，
     * 而 libgdx 的 {@code Texture} 本身不带区域信息（原版 arc 的 {@code Texture} 就是整张图，
     * 所以原版 {@code new BorderImage(map.safeTexture(), 3f)} 直接可用）。
     */
    private static Drawable previewOf(Map map){
        if(map.texture == null) return Tex.nomap;
        Texture texture = map.safeTexture();
        return texture == null ? Tex.nomap : new TextureRegionDrawable(new TextureRegion(texture));
    }

    /** 游戏模式说明（对应原版 {@code displayGameModeHelp}）。 */
    private void displayGameModeHelp(){
        FloatingDialog d = new FloatingDialog(I18n.get("mode.help.title", "mode.help.title"));
        d.setFillParent(false);

        Table table = new Table();
        table.defaults().pad(Scl.scl(1f));

        //原版 new ScrollPane(table)（无样式）→ 本项目统一用 Styles.defaultPane
        ScrollPane pane = new ScrollPane(table, Styles.defaultPane);
        pane.setFadeScrollBars(false);
        table.row();

        for(Gamemode mode : Gamemode.all){
            if(mode.hidden) continue;
            //原版 table.labelWrap("[accent]" + mode + ":[] [lightgray]" + mode.description()).width(400f)
            Label label = new Label("[accent]" + mode + ":[] [lightgray]" + mode.description(), Styles.defaultLabel);
            label.setWrap(true);
            table.add(label).width(Scl.scl(400f));
            table.row();
        }

        d.cont.add(pane);
        d.buttons.add(textButton("$ok", null, d::hide)).size(Scl.scl(110f), Scl.scl(50f)).pad(Scl.scl(10f));
        d.show();
    }

    /** @return 是否竖屏（对应 arc 的 {@code Core.graphics.isPortrait()}，libgdx 的 Graphics 没有该方法）。 */
    private static boolean isPortrait(){
        return Core.graphics.getHeight() > Core.graphics.getWidth();
    }
}
