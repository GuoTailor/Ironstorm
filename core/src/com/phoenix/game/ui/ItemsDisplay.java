package com.phoenix.game.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Scl;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.ItemType;
import com.phoenix.game.world.meta.I18n;
import com.phoenix.game.world.modules.ItemModule;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

import static com.phoenix.game.Vars.content;
import static com.phoenix.game.Vars.data;
import static com.phoenix.game.Vars.state;

/**
 * 已发射物品清单（"全局库存里现在有多少东西"）。参照 Mindustry mindustry.ui.ItemsDisplay 移植。
 * <p>结构：一个按钮（"已发射物品 / 发射信息"，随游戏状态换文案）→ 展开后是一张
 * "数量 + 图标 + 名称" 的物品清单。
 *
 * <p><b>与本项目的差异（逐条对应原版写法）</b>：
 * <ul>
 *   <li>刻意降级：原版 {@code table(Tex.button, c -> ...)} / {@code addImage(...)} / {@code add(String)}
 *       都要全局 skin；本项目没有 skin → 自建 {@code Table.setBackground(Tex.button)}、
 *       {@code Image} 与 {@link Label}（与 {@code ui/ItemDisplay} 的做法一致）。</li>
 *   <li>等价替代：原版 {@code base.pane(...)} 是 arc 的"加一个 ScrollPane"便捷方法 → 本项目显式 new ScrollPane。</li>
 *   <li>刻意降级：原版 {@code addImageTextButton(...).update(t -> ...)} 用 arc 的 Cell 每帧回调；
 *       libgdx 的 Table 没有 addImageTextButton、Cell 也没有 {@code update(...)} → 这里用
 *       TextButton + 旁边的箭头 Image 拼出来，按钮文案与箭头状态在 {@code rebuild()} 与点击时各同步一次。
 *       另外原版的 {@code Icon.downOpen}/{@code Icon.upOpen} 图标本项目图集里没有 → 用
 *       {@code Icon.arrowDown}/{@code Icon.arrowUp} 代替。</li>
 *   <li>等价替代：数字格式化改调 {@code Vars.ui.formatAmount(int)}（签名一致，T11c 接线）。</li>
 *   <li>已补齐（T11c/S4b）：原版判 {@code item.type == ItemType.material && data.isUnlocked(item)}；
 *       本项目 {@code Item.type} 与 {@code Vars.data.isUnlocked} 都在 → 按 material + 解锁状态过滤
 *       （此前用 {@code item.unlocked()} 替代的降级说明已过时）。</li>
 *   <li>已补齐（T11c）：原版还会追加"核心库存里未发射的那部分"
 *       （{@code player.getTeam().data().hasCore()} / {@code player.getTeam().core().items}）。
 *       本项目核心库存就是队伍共享库存（{@code CoreBlock} 把 {@code entity.items} 指向
 *       {@code state.teams.items(team)}）→ 直接用 {@code state.teams.items(team)}；
 *       原版的 {@code hasCore()} 守卫被 {@code > 0} 判定自然覆盖（无核心时该库存恒为空）。</li>
 * </ul>
 */
public class ItemsDisplay extends Table{
    private StringBuilder builder = new StringBuilder();

    public ItemsDisplay(){
        rebuild();
    }

    public void rebuild(){
        clear();
        top().left();
        //刻意降级：原版 margin(0)；libgdx 的 Table 没有 margin → 用 pad(0f)
        pad(0f);

        //原版 table(Tex.button, c -> {...})（libgdx 的 Table 没有带背景 + 回调的重载）→ 自建
        //刻意降级：原版用的是 arc 的 c.margin(...)（外间距）；libgdx 的 Table/Cell 都没有 margin 概念 → 用 pad 代替
        Table c = new Table();
        c.setBackground(Tex.button);
        c.pad(Scl.scl(10f)).padLeft(Scl.scl(12f)).padTop(Scl.scl(15f));
        c.padRight(Scl.scl(12f));
        c.left();

        Collapser col = new Collapser(base -> {
            Table t = new Table();
            //刻意降级：原版 t.marginRight(30f)（外间距）；libgdx 没有 margin → 用 padRight 代替
            t.padRight(Scl.scl(30f));
            t.left();

            for(Item item : content.items()){
                //原版 ui/ItemsDisplay.java:34：if(item.type == ItemType.material && data.isUnlocked(item)){...}
                //（在这里等价改写成"不满足就 continue"）
                if(item.type != ItemType.material || !data.isUnlocked(item)) continue;
                t.add(new Label(format(item), Styles.defaultLabel)).left();
                if(item.icon() != null){
                    t.add(new Image(item.icon())).size(Scl.scl(8 * 3)).padLeft(Scl.scl(4f)).padRight(Scl.scl(4f));
                }
                Label itemName = new Label(item.localizedName(), Styles.defaultLabel);
                //libgdx 的 Cell 没有 arc 的 .color(Color) → 直接给 Actor 设色
                itemName.setColor(Color.LIGHT_GRAY);
                t.add(itemName).left();
                t.row();
            }

            ScrollPane pane = new ScrollPane(t, Styles.defaultPane);
            pane.setScrollingDisabled(true, false);
            base.add(pane).grow();
        }, false).setDuration(0.3f);

        //刻意降级：原版 c.addImageTextButton("$launcheditems", Icon.downOpen, Styles.clearTogglet, col::toggle)
        //  —— libgdx 没有 addImageTextButton / Cell.update；图标也用 arrowDown/arrowUp 代替 downOpen/upOpen
        Image arrow = new Image(col.isCollapsed() ? Icon.arrowUp : Icon.arrowDown);
        TextButton button = new TextButton(buttonText(), Styles.clearTogglet);
        button.setProgrammaticChangeEvents(false);
        button.setChecked(col.isCollapsed());
        button.addListener(new ChangeListener(){
            @Override
            public void changed(ChangeEvent event, Actor actor){
                col.toggle();
                //刻意降级：原版是在 .update(...) 里每帧同步文案/箭头；这里改为点击时同步一次
                button.setText(buttonText());
                arrow.setDrawable(col.isCollapsed() ? Icon.arrowUp : Icon.arrowDown);
            }
        });

        c.add(arrow).padRight(Scl.scl(4f)).left();
        //刻意降级：原版是 .padBottom(4).left().fillX().margin(12f).minWidth(200f)（margin 是外间距，libgdx 没有）→ 用 pad 代替
        c.add(button).pad(Scl.scl(12f)).padBottom(Scl.scl(4f)).left().fillX().expandX().minWidth(Scl.scl(200f));
        c.row();
        c.add(col).colspan(2);

        //刻意降级：原版把 margin 写在 Table 上（c.margin(...)）；libgdx 没有 margin → 这里挪到外层 Cell 上
        add(c).left().padTop(Scl.scl(15f)).padLeft(Scl.scl(12f)).padRight(Scl.scl(12f)).padBottom(Scl.scl(10f));
    }

    /** 按钮文案：菜单里叫"已发射物品"，进游戏后叫"发射信息"（对应原版 {@code state.is(State.menu)} 分支）。 */
    private String buttonText(){
        //等价替代：原版是 "$launcheditems" / "$launchinfo"（arc 会自动解析 $ 前缀的 bundle 键）；本项目走 I18n
        return state.isMenu()
            ? I18n.get("launcheditems", "Launched Items")
            : I18n.get("launchinfo", "Launch Info");
    }

    private String format(Item item){
        builder.setLength(0);
        builder.append(formatAmount(data.getItem(item)));
        //原版还会追加"核心库存里尚未发射的"部分：
        //  if(!state.is(State.menu) && player.getTeam().data().hasCore() && player.getTeam().core().items.get(item) > 0){...}
        //本项目核心库存 = 队伍共享库存（CoreBlock 把 entity.items 指向 state.teams.items(team)），
        //原版的 hasCore() 守卫被 "> 0" 判定自然覆盖（无核心时该库存恒为空）。
        if(!state.isMenu() && Vars.player != null){
            ItemModule core = state.teams.items(Vars.player.getTeam());
            int coreAmount = core.get(item);
            if(coreAmount > 0){
                builder.append(" [unlaunched]+ ");
                builder.append(formatAmount(coreAmount));
            }
        }
        return builder.toString();
    }

    /**
     * 数字格式化（带 k/M 后缀），对应原版 {@code ui.formatAmount(int)}（T11c 接线，签名一致）。
     * <p>注意：{@code Vars.ui} 在 {@code UI.init()} 内部构造本组件时仍为 null（赋值尚未完成），
     * 此时退化成原样数字；{@code rebuild()} 之后即走真实格式化。
     */
    private static String formatAmount(int number){
        return Vars.ui == null ? String.valueOf(number) : Vars.ui.formatAmount(number);
    }
}
