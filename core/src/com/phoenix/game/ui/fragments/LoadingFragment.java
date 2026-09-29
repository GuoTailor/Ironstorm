package com.phoenix.game.ui.fragments;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.phoenix.game.core.Scl;
import com.phoenix.game.func.Floatp;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.ui.Bar;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Tex;

/**
 * 加载遮罩。参照 Mindustry mindustry.ui.fragments.LoadingFragment 移植。
 * <p>整屏半透明黑幕 + 上下两条强调色横线 + 居中的提示文字（名字标签 "namelabel"），
 * 下面可以挂一个进度条（{@link #setProgress}）与一个取消按钮（{@link #setButton}）。
 *
 * <p><b>与 arc 的差异（临时绕过）</b>：
 * <ul>
 *   <li>{@code parent.fill(Drawable, Cons<Table>)}：arc 的"加一个铺满父级的带背景表"，libgdx 的 Group
 *       没有 → 显式建一个 {@code setFillParent(true)} 的 Table；</li>
 *   <li>{@code t.addImage()} / {@code t.addButton(...)} / {@code t.add(String)}：libgdx 的 Table 都没有
 *       （它们依赖全局 skin）→ 分别换成 {@code new Image(Tex.whiteui)}、{@code new TextButton(...)}、
 *       {@link Label}；原版 arc 的 {@code Label.setText} 会自动展开 {@code $key} 前缀，本项目 {@link Label}
 *       只解析颜色标记 → 文案统一过 {@link #resolve(String)}；</li>
 *   <li>{@code bar = t.add(new Bar())}：本项目 {@link Bar} 没有无参构造，也没有 {@code reset(float)} /
 *       {@code set(name, fraction, color)} → 用三个 Supplier 直接接上本类的进度字段（Bar 每帧自行求值）；</li>
 *   <li>{@code Cell.visible(boolean)} 不存在 → 直接对 Actor 调 {@code setVisible}；</li>
 *   <li>{@code button.getListeners().remove(size - 1)} + {@code button.clicked(Runnable)} 是 arc 的链式 API
 *       → libgdx 用 {@code clearListeners()} + {@code ClickListener}；</li>
 *   <li>{@code table.&lt;Label&gt;find("namelabel")}（按名字查找）→ libgdx 是 {@code findActor(String)}；</li>
 *   <li>{@code table.actions(a, b)} 是 arc 的"顺序执行多个 action" → libgdx 要显式 {@code Actions.sequence}。</li>
 * </ul>
 */
public class LoadingFragment extends Fragment{
    private Table table;
    private TextButton button;
    private Bar bar;
    /** 进度提供者（原版是 Bar 自己持有的 fraction 供应商）。 */
    private Floatp progress;

    @Override
    public void build(Group parent){
        //TODO 原版 parent.fill(Styles.black8, t -> {...})；libgdx 的 Group 没有 fill(...) 便捷方法
        table = new Table();
        table.setFillParent(true);
        table.setBackground(Styles.black8);
        table.setVisible(false);
        table.setTouchable(Touchable.enabled);
        parent.addActor(table);

        table.add().height(Scl.scl(133f)).row();
        table.add(line()).growX().height(Scl.scl(3f)).pad(Scl.scl(4f)).row();
        //TODO 原版 t.add("$loading").name("namelabel")：libgdx 的 Cell 没有 name(...)（名字只能设在 Actor 上）
        Label nameLabel = new Label(I18n.get("loading", "Loading"), Styles.defaultLabel);
        nameLabel.setName("namelabel");
        table.add(nameLabel).pad(Scl.scl(10f)).row();
        table.add(line()).growX().height(Scl.scl(3f)).pad(Scl.scl(4f)).row();

        //TODO 原版 bar = t.add(new Bar()).pad(3).size(500f, 40f).visible(false).get()；
        //     本项目 Bar 没有无参构造 → 名称/颜色/充满度都接本类字段（Bar.act 每帧重新求值）
        bar = new Bar(() -> progress == null ? "" : ((int)(progress.get() * 100) + "%"),
                      () -> Pal.accent,
                      () -> progress == null ? 0f : progress.get());
        table.add(bar).pad(Scl.scl(3f)).size(Scl.scl(500f), Scl.scl(40f));
        bar.setVisible(false);
        table.row();

        //TODO 原版 t.addButton("$cancel", () -> {})（依赖 skin 的按钮样式 + $key 文案）
        button = new TextButton(I18n.get("cancel", "Cancel"), Styles.defaultt);
        table.add(button).pad(Scl.scl(20f)).size(Scl.scl(250f), Scl.scl(70f));
        button.setVisible(false);
    }

    /** 一条强调色横线（对应原版 {@code t.addImage().growX().height(3f)...get().setColor(Pal.accent)}）。 */
    private Image line(){
        Image image = new Image(Tex.whiteui);
        image.setColor(Pal.accent);
        return image;
    }

    public void setProgress(Floatp progress){
        //TODO 原版还有 bar.reset(0f)；本项目 Bar 没有 reset/set → 进度完全由 Supplier 每帧读取
        this.progress = progress;
        bar.setVisible(true);
    }

    public void setButton(Runnable listener){
        button.setVisible(true);
        //TODO 原版 button.getListeners().remove(button.getListeners().size - 1) + button.clicked(listener)
        //  —— arc 的监听器列表与 clicked(Runnable) 便捷方法；libgdx 用 clearListeners + ClickListener
        button.clearListeners();
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                listener.run();
            }
        });
    }

    public void setText(String text){
        //TODO 原版 table.<Label>find("namelabel")（arc 的泛型按名查找）→ libgdx 的 findActor
        Label label = table.findActor("namelabel");
        if(label == null) return;
        label.setText(resolve(text));
        label.setColor(Pal.accent);
    }

    /**
     * 解析 {@code $key} 文案（对应原版 arc {@code Label.setText} 对 {@code $} 前缀的自动展开）。
     * <p>本项目 {@link Label} 只解析 {@code [color]} 标记、不认 {@code $key}，而调用方
     * （{@code HostDialog} 的 {@code "$hosting"}、{@code SaveDialog} 的 {@code "$saveload"}）直接传原始键名，
     * 不展开的话遮罩上会原样显示 {@code $hosting}。
     * <p>缺键回退成键名本身，与 {@code core/UI.bundle(String)} 的策略一致。
     */
    private static String resolve(String text){
        if(text == null) return "";
        return text.startsWith("$") ? I18n.get(text.substring(1), text.substring(1)) : text;
    }

    public void show(){
        show(I18n.get("loading", "Loading"));
    }

    public void show(String text){
        Label label = table.findActor("namelabel");
        if(label != null){
            label.setColor(Color.WHITE);
            label.setText(resolve(text));
        }
        bar.setVisible(false);
        table.clearActions();
        table.setTouchable(Touchable.enabled);
        table.setVisible(true);
        table.getColor().a = 1f;
        table.toFront();
    }

    public void hide(){
        table.clearActions();
        table.toFront();
        table.setTouchable(Touchable.disabled);
        //TODO 原版 table.actions(Actions.fadeOut(0.5f), Actions.visible(false)) 是"顺序执行" → 这里用 sequence
        table.addAction(Actions.sequence(Actions.fadeOut(0.5f), Actions.visible(false)));
    }
}
