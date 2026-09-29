package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Strings;
import com.phoenix.game.game.Objective;
import com.phoenix.game.game.Objectives.Unlock;
import com.phoenix.game.game.Rules;
import com.phoenix.game.game.Schematic;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.struct.Array;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.type.Zone;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Sounds;
import io.anuke.mindustry.gen.Tex;

/**
 * 区域信息对话框（战役里点一个 {@link Zone} 看解锁条件/资源/目标，并可配置发射清单、发射）。
 * 参照 Mindustry mindustry.ui.dialogs.ZoneInfoDialog。
 *
 * <p><b>与本项目的差异（逐条对应原版写法）</b>
 * <ul>
 *   <li><b>区域发射链路已接线</b>：原版"LAUNCH/UNCOVER"按钮走
 *       {@code data.isUnlocked(zone)} / {@code data.unlockContent(zone)} / {@code ui.deploy.setup()} /
 *       {@code ui.deploy.hide()} / {@code control.playZone(zone)}。
 *       本项目 {@code ui.deploy.hide()}/{@code ui.deploy.setup()}（T14）、{@code control.playZone(zone)}（区域链路批）
 *       与 {@code GlobalData.isUnlocked}/{@code unlockContent}（S4a 批）<b>都已接线</b> → 按钮回调照原版分支。
 *       （{@code ui.deploy} 可能为 null，沿用本类既有的判空写法。）</li>
 *   <li>{@code zone.loadout.findCore().itemCapacity} <b>已落实</b>：本项目 {@code Schematic} 已补
 *       {@code findCore()}（见 {@link #coreCapacity(Zone)}；蓝图里没有核心时退回 {@link #DEFAULT_CORE_CAPACITY}）。</li>
 *   <li>{@code rules.attackMode}（决定 {@code zone.objective} 用"生存"还是"进攻"文案）：
 *       <b>T14 已接线</b> → 走 {@code zone.getRules().attackMode}（原版写法；与 {@code game/Stats.java:44} 同源）。</li>
 *   <li>刻意降级：arc 的 {@code Table.margin(...)} / {@code table(Drawable, Cons)} / {@code pane(Cons)} /
 *       {@code addImage(...)} / {@code addImage(icon, color)} / {@code Cell.color(...)} /
 *       {@code Cell.disabled(Boolp)} / {@code Cell.get()} libgdx 都没有 →
 *       {@code margin} 用 {@code pad}；带回调的表改成自建 {@code Table}；
 *       {@code pane(Cons)} 改成自建内容表 + {@link ScrollPane}；
 *       {@code addImage} 改成 {@code add(new Image(...))}；图标颜色改成给 {@code Image} 设色；
 *       {@code disabled(Boolp)} 改成创建时 {@code setDisabled(boolean)}。</li>
 *   <li>刻意降级：{@code Icon.lock}/{@code Icon.ok} 本项目生成包里没有（查过生成的 {@code Icon.java}）
 *       → 用 {@code Icon.locked}/{@code Icon.check}。</li>
 *   <li>刻意降级：{@code Core.bundle.format(...)} 本项目 bundle 是裁剪过的（缺键返回 {@code ???key???}）
 *       → 走 {@link #format(String, String, Object...)}（{@code I18n.get} 双形态判定 + {@code Strings.format}）。</li>
 *   <li>刻意降级：{@code Core.graphics.isPortrait()} libgdx 的 {@code Graphics} 没有该方法 → 就地判宽高。</li>
 *   <li>刻意降级：{@code ItemStack.amount} 在本项目是 {@code final}：本类只读不写，无影响；
 *       {@code LoadoutDialog} 已经内部做了可变副本（见其类注释）。</li>
 *   <li>刻意降级：{@code zone.configureObjective} 加 null 保护（本项目 {@link Zone} 默认值是
 *       {@code new ZoneWave(this, 15)}，非 null；保留守卫是为防外部把它置空）。</li>
 * </ul>
 */
public class ZoneInfoDialog extends FloatingDialog{
    /**
     * 取不到 {@code zone.loadout.findCore().itemCapacity} 时的兜底容量
     * （蓝图里没有核心时 {@link Schematic#findCore()} 会抛异常，见 {@link #coreCapacity(Zone)}）。
     */
    private static final int DEFAULT_CORE_CAPACITY = 4000;

    private LoadoutDialog loadout = new LoadoutDialog();

    public ZoneInfoDialog(){
        super("");

        //原版：titleTable.remove()（区域对话框没有标题栏）
        titleTable.remove();
        addCloseButton();
    }

    public void show(Zone zone){
        setup(zone);
        show();
    }

    private void setup(Zone zone){
        cont.clear();

        //发射清单（每次重建，对应原版的 rebuildItems）
        Table iteminfo = new Table();
        Runnable rebuildItems = () -> {
            int i = 0;
            iteminfo.clear();

            if(!zone.unlocked()) return;

            for(ItemStack stack : zone.getLaunchCost()){
                if(stack.amount == 0) continue;

                if(i++ % 2 == 0){
                    iteminfo.row();
                }
                //原版 addImage(...)：libgdx 没有 → 自建 Image
                iteminfo.add(new Image(stack.item.icon(Cicon.small))).size(Scl.scl(8 * 3)).padRight(Scl.scl(1f));
                //原版 add(...).color(Color.lightGray)：libgdx 的 Cell 没有 color → 给 Label 设色
                Label amount = new Label(stack.amount + "", Styles.defaultLabel);
                amount.setColor(Color.LIGHT_GRAY);
                iteminfo.add(amount).padRight(Scl.scl(5f));
            }
        };

        rebuildItems.run();

        //原版 cont.pane(cont -> {...})：libgdx 没有 pane(Cons) → 自建内容表 + ScrollPane
        Table content = new Table();
        content.top().left();

        if(zone.locked()){
            content.add(new Image(Icon.locked));
            content.row();
            content.add(new Label(bundle("$locked"), Styles.defaultLabel)).padBottom(Scl.scl(6f));
            content.row();

            Table req = new Table();
            req.left();
            req.defaults().left();

            //非 Unlock 的目标（波次/发射等）与需要研究的方块（Unlock 目标）分两组。
            //⚠ 不能写 zone.requirements.select(o -> ...)：本项目 struct.Array 的 select(Boolf) 与
            //  libgdx Array 的 select(Predicate) 都匹配这个 lambda，javac 会报"对select的引用不明确"
            //  → 这里改成显式下标循环分组
            Array<Objective> zones = new Array<>();
            Array<Unlock> blocks = new Array<>();
            for(int i = 0; i < zone.requirements.size; i++){
                Objective o = zone.requirements.get(i);
                if(o instanceof Unlock){
                    blocks.add((Unlock)o);
                }else{
                    zones.add(o);
                }
            }

            if(!zones.isEmpty()){
                Table r = new Table();
                r.left();
                r.add(new Label(bundle("$complete"), Styles.defaultLabel)).colspan(2).left();
                r.row();
                for(Objective o : zones){
                    r.add(new Image(Icon.terrain)).padRight(Scl.scl(4f));
                    Label display = new Label(o.display(), Styles.defaultLabel);
                    display.setColor(Color.LIGHT_GRAY);
                    r.add(display);
                    Image mark = new Image(o.complete() ? Icon.check : Icon.cancel);
                    mark.setColor(o.complete() ? Color.LIGHT_GRAY : Color.SCARLET);
                    r.add(mark).padLeft(Scl.scl(3f));
                    r.row();
                }
                req.add(r);
            }

            req.row();

            if(!blocks.isEmpty()){
                Table r = new Table();
                r.left();
                r.add(new Label(bundle("$research.list"), Styles.defaultLabel)).colspan(2).left();
                r.row();
                for(Unlock blocko : blocks){
                    r.add(new Image(blocko.block.icon(Cicon.small))).size(Scl.scl(8 * 3)).padRight(Scl.scl(5f));
                    Label name = new Label(blocko.block.localizedName, Styles.defaultLabel);
                    name.setColor(Color.LIGHT_GRAY);
                    r.add(name).left();
                    Image mark = new Image(blocko.block.unlocked() ? Icon.check : Icon.cancel);
                    mark.setColor(blocko.block.unlocked() ? Color.LIGHT_GRAY : Color.SCARLET);
                    r.add(mark).padLeft(Scl.scl(3f));
                    r.row();
                }
                req.add(r).padTop(Scl.scl(10f));
            }

            content.add(req).growX();
        }else{
            content.add(new Label(zone.localizedName, Styles.defaultLabel)).growX().center();
            content.row();

            //原版 addImage().color(Pal.accent).height(3).pad(6).growX()
            Image divider = new Image(Tex.whiteui);
            divider.setColor(Pal.accent);
            content.add(divider).height(Scl.scl(3f)).pad(Scl.scl(6f)).growX();
            content.row();

            Table desc = new Table();
            desc.left();
            desc.defaults().left().width(Scl.scl(isPortrait() ? 350f : 500f));

            //原版 desc.pane(t -> t.marginRight(12f).add(zone.description).wrap().growX())
            //     .fillX().maxHeight(...).pad(2).padBottom(8f).get().setScrollingDisabled(true, false)
            Table descText = new Table();
            descText.padRight(Scl.scl(12f));
            Label description = new Label(zone.description, Styles.defaultLabel);
            description.setWrap(true);
            descText.add(description).growX();

            ScrollPane descPane = new ScrollPane(descText, Styles.defaultPane);
            descPane.setScrollingDisabled(true, false);
            desc.add(descPane).fillX().maxHeight(Scl.scl(Vars.mobile ? 300f : 450f))
                .pad(Scl.scl(2f)).padBottom(Scl.scl(8f));
            desc.row();

            Table resources = new Table();
            resources.left();
            resources.add(new Label(bundle("$zone.resources"), Styles.defaultLabel)).padRight(Scl.scl(6f));

            if(zone.resources.size > 0){
                Table r = new Table();
                r.left();
                int i = 0;
                for(Item item : zone.resources){
                    r.add(new Image(item.icon(Cicon.small))).size(Scl.scl(8 * 3));
                    if(++i % 4 == 0){
                        r.row();
                    }
                }
                resources.add(r);
            }else{
                resources.add(new Label(bundle("$none"), Styles.defaultLabel));
            }
            desc.add(resources);
            desc.row();

            //原版：Rules rules = zone.getRules();
            //     Core.bundle.get(!rules.attackMode ? "zone.objective.survival" : "zone.objective.attack")
            //T14：Rules.attackMode 已就位（Zone.getRules() 与 game/Stats.java:44 同一读法）→ 按攻击/生存取文案
            Rules zoneRules = zone.getRules();
            String objective = zoneRules != null && zoneRules.attackMode
                ? I18n.get("zone.objective.attack", "Destroy Enemy Core")
                : I18n.get("zone.objective.survival", "Survive");
            desc.add(new Label(format("zone.objective", "[lightgray]Objective: [accent]{0}", objective), Styles.defaultLabel));

            if(zone.bestWave() > 0){
                desc.row();
                desc.add(new Label(format("bestwave", "[lightgray]Best Wave: {0}", zone.bestWave()), Styles.defaultLabel));
            }

            content.add(desc);
            content.row();
        }

        //原版 cont.marginRight(12f)（写在 pane 的 lambda 里，作用在内层内容表上）
        content.padRight(Scl.scl(12f));

        ScrollPane contentPane = new ScrollPane(content, Styles.defaultPane);
        contentPane.setScrollingDisabled(true, false);
        cont.add(contentPane).grow();
        cont.row();

        //原版 cont.addButton(zone.canConfigure() ? "$configure"
        //     : Core.bundle.format("configure.locked", zone.configureObjective.display()), ...)
        //     .fillX().pad(3).disabled(b -> !zone.canConfigure())
        String configureText = zone.canConfigure()
            ? "$configure"
            : format("configure.locked", "[lightgray]Unlock configuring loadout: Wave {0}.",
                zone.configureObjective == null ? "?" : zone.configureObjective.display());

        TextButton configure = textButton(configureText, null, () ->
            //原版 loadout.show(zone.loadout.findCore().itemCapacity, ...)
            loadout.show(coreCapacity(zone), zone.getStartingItems(),
                zone::resetStartingItems, zone::updateLaunchCost, rebuildItems));
        //原版 .disabled(b -> !zone.canConfigure())（arc 的 Cell 动态禁用）→ libgdx 只能创建时定一次
        configure.setDisabled(!zone.canConfigure());
        cont.add(configure).fillX().pad(Scl.scl(3f));
        cont.row();

        //原版 cont.addButton(zone.locked() ? "$uncover" : "$launch", () -> {...})
        //     .minWidth(200f).margin(13f).padTop(5).disabled(b -> ...).uniformY()
        final TextButton button = textButton(zone.locked() ? "$uncover" : "$launch", null, () -> {
            //原版 ZoneInfoDialog.java:151-163
            if(!Vars.data.isUnlocked(zone)){
                //未解锁 → 播解锁音效、记录解锁、刷新部署对话框的"已发射物品"列表、重建本对话框（按钮变 LAUNCH）
                Sounds.unlock.play();
                Vars.data.unlockContent(zone);
                if(Vars.ui != null && Vars.ui.deploy != null){
                    Vars.ui.deploy.setup();
                }
                setup(zone);
            }else{
                //已解锁 → 扣发射消耗并进入区域（原版 else 分支；全部已接线：T14 接 hide、区域链路批接 playZone）
                ItemStack[] cost = zone.getLaunchCost().toArray(ItemStack.class);
                if(!Vars.data.hasItems(cost)) return;

                if(Vars.ui != null && Vars.ui.deploy != null){
                    Vars.ui.deploy.hide();
                }
                Vars.data.removeItems(cost);
                hide();
                //进入区域（对应原版 control.playZone(zone)）；内部走 World.loadGenerator → Control.enterWorld
                Vars.control.playZone(zone);
            }
        });

        ItemStack[] cost = zone.getLaunchCost().toArray(ItemStack.class);
        //原版 .disabled(b -> zone.locked() ? !zone.canUnlock() : !data.hasItems(zone.getLaunchCost()))
        button.setDisabled(zone.locked() ? !zone.canUnlock() : !Vars.data.hasItems(cost));
        cont.add(button).minWidth(Scl.scl(200f)).pad(Scl.scl(13f)).padTop(Scl.scl(5f)).uniformY();
        cont.row();

        //原版 button.row(); button.add(iteminfo)（把发射清单塞进按钮里）
        button.row();
        button.add(iteminfo);
    }

    /** @return 是否竖屏（对应 arc 的 {@code Core.graphics.isPortrait()}，libgdx 的 Graphics 没有该方法）。 */
    private static boolean isPortrait(){
        return Core.graphics.getHeight() > Core.graphics.getWidth();
    }

    /**
     * @return 该区域开局蓝图里核心的物品容量（原版 {@code zone.loadout.findCore().itemCapacity}）。
     * <p>本项目多一层兜底：蓝图里没有核心时 {@link Schematic#findCore()} 会抛
     * {@code IllegalArgumentException}（与原版一致），这里先判 {@link Schematic#hasCore()} 再取。
     */
    private static int coreCapacity(Zone zone){
        if(zone.loadout == null || !zone.loadout.hasCore()) return DEFAULT_CORE_CAPACITY;
        return zone.loadout.findCore().itemCapacity;
    }

    /**
     * 取 bundle 文本并填占位符（对应原版 {@code Core.bundle.format(key, args)}）。
     * <p>本项目 bundle 是裁剪过的、缺键返回 {@code ???key???} → 走 {@code I18n.get} 的双形态判定 + fallback。
     */
    private static String format(String key, String fallback, Object... args){
        return Strings.format(I18n.get(key, fallback), args);
    }
}
