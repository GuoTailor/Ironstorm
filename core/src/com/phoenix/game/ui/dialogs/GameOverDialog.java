package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Items;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Strings;
import com.phoenix.game.game.EventType;
import com.phoenix.game.game.Team;
import com.phoenix.game.type.Item;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.meta.I18n;

/**
 * 胜负结算对话框（显示战绩，点"菜单"回主菜单）。参照 Mindustry mindustry.ui.dialogs.GameOverDialog。
 *
 * <p><b>本批（F5）回填的降级点</b>：
 * <ul>
 *   <li>{@code state.stats}：{@link com.phoenix.game.core.GameState} 已补该字段 →
 *       消灭敌人数/建造数/摧毁数/拆除数四行已照原版接上，{@code stat.wave} 改用
 *       {@code state.stats.wavesLasted}（原版口径，由 {@code Logic.checkGameOver} 填）。
 *       F7 批已补上累加点：{@code EventType} 新增 {@code BlockBuildEndEvent}/{@code BlockDestroyEvent}/
 *       {@code UnitDestroyEvent}，由 {@code BuildBlock}/{@code TileEntity.kill}/{@code BaseUnit.onDeath}
 *       派发、{@code Control} 的三个监听器累加 → 四项均为真实值。</li>
 *   <li>{@code state.launched}：标题已按它切 {@code $launch.title}（本项目还没有发射流程 → 恒 false）。</li>
 *   <li>{@code state.rules.pvp}：PVP 分支已接（显示获胜队伍 + 只给"菜单"按钮）。</li>
 *   <li>{@code control.isHighScore()}：已接，"新纪录"行会在刷新记录时显示。</li>
 *   <li>{@code world.isZone()}：已接（{@link com.phoenix.game.core.World#isZone()}）。</li>
 * </ul>
 *
 * <p><b>仍未接（依赖缺失）</b>：
 * <ul>
 *   <li>{@code control.saves.getCurrent()}：本项目走 {@code Vars.saves.getCurrent()}（字段名不同）。</li>
 *   <li>{@code logic.reset()}：本项目 {@code Logic} 没有该方法 → 用 {@link com.phoenix.game.core.Control#menu()}
 *       （它就是"回菜单 + 清场"的项目等价物）。</li>
 *   <li>arc 的 {@code Cell.margin(...)} / {@code cont.pane(Cons)} / {@code add(String)} / {@code addImage(...)}
 *       libgdx 都没有 → 用 {@code pad}、自建 {@link ScrollPane}、{@link Label}、{@code add(new Image(...))}。</li>
 * </ul>
 *
 * <p><b>F13c 已接（zone 专属三段全通）</b>：
 * <ul>
 *   <li>{@code stat.delivered}：交付资源清单（数量 + {@code item.icon(Cicon.small)} 图标；
 *       图标用 libgdx 的 {@code Image(TextureRegionDrawable)} 直接加进表，绕开缺失的 {@code addImage}）。</li>
 *   <li>{@code stat.rank}：{@code Stats.calculateRank(Vars.world.getZone(), state.launched)} →
 *       纯文本显示（{@code stat.rank} 键本来就带 {@code [accent]} markup，不需要 rank 图标）。</li>
 *   <li>{@code $continue} 按钮：zone 下回菜单并打开 {@code Vars.ui.deploy}（DeployDialog 已移植）。</li>
 * </ul>
 */
public class GameOverDialog extends FloatingDialog{
    private Team winner;

    public GameOverDialog(){
        super("$gameover");
        setFillParent(true);
        shown(this::rebuild);
    }

    public void show(Team winner){
        this.winner = winner;
        show();
        //原版用 player.getTeam()：本项目 Vars.player 可能在菜单态为 null → 守卫
        if(Vars.player != null && winner == Vars.player.getTeam()){
            Events.fire(new EventType.WinEvent());
        }else{
            Events.fire(new EventType.LoseEvent());
        }
    }

    void rebuild(){
        //原版 title.setText(state.launched ? "$launch.title" : "$gameover")
        //  （libgdx 的 Label 不解析 $ 前缀 → 走 resolve）
        title.setText(resolve(Vars.state.launched ? "$launch.title" : "$gameover"));
        buttons.clear();
        cont.clear();

        //原版 buttons.margin(10)：libgdx 没有 margin → 用 pad
        buttons.pad(Scl.scl(10f));
        buttons.defaults().size(Scl.scl(130f), Scl.scl(60f));

        if(Vars.state.rules.pvp){
            //PVP：只显示获胜队伍 + "菜单"按钮（原版这两个分支互斥，PVP 下没有统计面板）
            cont.add(new Label(format("gameover.pvp", winner.localized()), Styles.defaultLabel)).pad(Scl.scl(6f));
            cont.row();
            buttons.add(textButton("$menu", null, () -> {
                hide();
                //原版 state.set(State.menu) + logic.reset()：本项目等价物是 Control.menu()
                Vars.control.menu();
            }));
            return;
        }

        //原版 if(control.isHighScore()){ cont.add("$highscore"); cont.row(); }
        if(Vars.control.isHighScore()){
            cont.add(new Label(resolve("$highscore"), Styles.defaultLabel)).pad(Scl.scl(6f));
            cont.row();
        }

        //原版 cont.pane(t -> {...})：libgdx 没有带回调的 pane(...) → 自建 Table + ScrollPane
        Table stats = new Table();
        stats.left().top();
        stats.defaults().left();
        //原版 t.margin(13f) → pad
        stats.pad(Scl.scl(13f));

        //原版这五项都读 state.stats（F7 批已补齐累加点：EventType 三事件 + Control 三监听器 → 均为真实值）
        stats.add(new Label(format("stat.wave", Vars.state.stats.wavesLasted), Styles.defaultLabel));
        stats.row();
        stats.add(new Label(format("stat.enemiesDestroyed", Vars.state.stats.enemyUnitsDestroyed), Styles.defaultLabel));
        stats.row();
        stats.add(new Label(format("stat.built", Vars.state.stats.buildingsBuilt), Styles.defaultLabel));
        stats.row();
        stats.add(new Label(format("stat.destroyed", Vars.state.stats.buildingsDestroyed), Styles.defaultLabel));
        stats.row();
        stats.add(new Label(format("stat.deconstructed", Vars.state.stats.buildingsDeconstructed), Styles.defaultLabel));
        stats.row();
        //原版 stat.playtime 取 control.saves.getCurrent().getPlayTime()：本项目对应 Vars.saves.getCurrent().playtimeText()
        if(Vars.saves != null && Vars.saves.getCurrent() != null){
            stats.add(new Label(format("stat.playtime", Vars.saves.getCurrent().playtimeText()), Styles.defaultLabel));
            stats.row();
        }

        //zone 专属：已交付资源清单（原版 world.isZone() && !itemsDelivered.isEmpty()）
        //原版用 addImage(item.icon(Cicon.small))：libgdx 没有 addImage → 直接加 Image(TextureRegionDrawable)
        if(Vars.world != null && Vars.world.isZone() && !Vars.state.stats.itemsDelivered.isEmpty()){
            stats.add(new Label(resolve("$stat.delivered"), Styles.defaultLabel));
            stats.row();
            for(int i = 0; i < Items.all.size; i++){
                Item item = Items.all.get(i);
                int delivered = Vars.state.stats.itemsDelivered.get(item, 0);
                if(delivered <= 0) continue;

                Table row = new Table();
                row.add(new Label("    [LIGHT_GRAY]" + delivered, Styles.defaultLabel));
                com.badlogic.gdx.graphics.g2d.TextureRegion region =
                    item.icon(com.phoenix.game.ui.Cicon.small);
                if(region != null){
                    row.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(
                        new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(region)))
                        .size(Scl.scl(24f)).pad(Scl.scl(4f));
                }
                stats.add(row).left();
                stats.row();
            }
        }

        //zone 专属：最终评级（原版 world.isZone() → stat.rank；键值形如 "Final Rank: [accent]{0}"）
        if(Vars.world != null && Vars.world.isZone()){
            com.phoenix.game.game.Stats.RankResult result =
                Vars.state.stats.calculateRank(Vars.world.getZone(), Vars.state.launched);
            stats.add(new Label(format("stat.rank", result.rank + result.modifier), Styles.defaultLabel));
            stats.row();
        }

        ScrollPane pane = new ScrollPane(stats, Styles.defaultPane);
        pane.setFadeScrollBars(false);
        cont.add(pane).pad(Scl.scl(12f));

        //原版 if(world.isZone()){ "$continue" → 回菜单 + ui.deploy.show() } else { "$menu" }
        //本项目 Vars.ui.deploy 字段已存在（DeployDialog 已移植）→ 照原版双分支
        if(Vars.world != null && Vars.world.isZone()){
            buttons.add(textButton("$continue", null, () -> {
                hide();
                //原版 state.set(State.menu) + logic.reset()：本项目等价物是 Control.menu()
                Vars.control.menu();
                if(Vars.ui != null && Vars.ui.deploy != null){
                    Vars.ui.deploy.show();
                }
            }));
        }else{
            buttons.add(textButton("$menu", null, () -> {
                hide();
                //原版 state.set(State.menu) + logic.reset()：本项目等价物是 Control.menu()
                Vars.control.menu();
            }));
        }
    }

    /** 原版 {@code Core.bundle.format(key, args)}；本项目 bundle 缺键多 → 走 {@link I18n#get} 带 fallback。 */
    private static String format(String key, Object... args){
        return Strings.format(I18n.get(key, key), args);
    }
}
