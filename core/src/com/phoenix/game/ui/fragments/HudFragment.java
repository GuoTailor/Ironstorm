package com.phoenix.game.ui.fragments;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Items;
import com.phoenix.game.core.Scl;
import com.phoenix.game.game.Team;
import com.phoenix.game.input.InputHandler;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.ui.dialogs.FloatingDialog;
import com.phoenix.game.world.meta.BlockFlag;
import com.phoenix.game.world.Tile;
import io.anuke.mindustry.gen.Icon;

/**
 * 最小实现：游戏内 HUD。参照 Mindustry mindustry.ui.fragments.HudFragment/PlacementFragment 仅取其核心。
 * <p>顶部：玩家/核心状态、操作提示、波次倒计时；底部：建造栏。
 * 不移植资源数量、科技树、命令等。
 */
public class HudFragment extends Fragment{
    /** 放置朝向的文字（索引对应 rotation：0=上/1=右/2=下/3=左，与 Geometry.d4 一致） */
    private static final String[] FACING = {"上", "右", "下", "左"};

    private WidgetGroup group;
    private Label statusLabel;
    private Label waveLabel;
    /** 自动存档开关。 */
    private com.badlogic.gdx.scenes.scene2d.ui.CheckBox autosaveCheck;
    /** 教程引导文字面板（对应原版 HudFragment 的 "tutorial text" 块）；非教程局隐藏。 */
    private Table tutorialTable;
    private Label tutorialLabel;
    private Button tutorialPrev, tutorialNext;
    /** BOSS 血条所在的外层表（显隐每帧刷新；对应原版 `.visible(() -> state.rules.waves && state.boss() != null)`）。 */
    private Table bossTable;
    /** 右侧建造菜单。 */
    private final PlacementFragment placementFragment = new PlacementFragment();
    /** 建筑配置面板（点分拣器/指挥中心/物品源等可配置方块时弹出）。 */
    private final com.phoenix.game.ui.fragments.BlockConfigFragment configFragment = new com.phoenix.game.ui.fragments.BlockConfigFragment();
    /**
     * 建筑库存面板（点核心/容器等有物品的可存取方块时弹出，对应原版 {@code OverlayFragment.inv}）。
     * <p>public 供输入层调用（{@code InputHandler.tileTapped} 里的 {@code Vars.hud.inv.showFor/hide}）。
     */
    public final BlockInventoryFragment inv = new BlockInventoryFragment();
    private final MinimapFragment minimapFragment = new MinimapFragment();
    private boolean menusShown = true;

    // ---- 解锁提示（对应原版 HudFragment.lastUnlockTable/lastUnlockLayout/lastToast）----

    /** 上一次解锁提示的外层容器；非 null 表示当前有一条解锁 toast 还在展示。 */
    private Table lastUnlockTable;
    /** 上一次解锁提示的图标叠放区（连续解锁把图标堆进同一个 toast）。 */
    private Table lastUnlockLayout;
    /** 上一次 toast 弹出的时间戳（scheduleToast 排队用）。 */
    private long lastToast;

    public HudFragment(){
    }

    @Override
    public void build(com.badlogic.gdx.scenes.scene2d.Group parent){
        group = new WidgetGroup();
        group.setFillParent(true);
        group.setVisible(false);
        parent.addActor(group);

        //顶部信息（独立一张 fillParent 表，避免与底部建造栏抢对齐）
        Table top = new Table();
        top.setFillParent(true);
        top.top().left();
        group.addActor(top);

        Table menu = new Table();
        menu.setBackground(Styles.black6);
        Button menuButton = new Button(Styles.defaultb);
        menuButton.add(new Label("☰", Styles.defaultLabel)).pad(Scl.scl(7f));
        menuButton.addListener(new ClickListener(){
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){ toggleMenus(); }
        });
        menu.add(menuButton).size(Scl.scl(45f));
        Button pauseButton = new Button(Styles.defaultb);
        pauseButton.add(new Label("Ⅱ", Styles.defaultLabel)).pad(Scl.scl(7f));
        pauseButton.addListener(new ClickListener(){
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                if(Vars.state != null) Vars.state.set(Vars.state.isPaused() ? com.phoenix.game.core.GameState.State.playing : com.phoenix.game.core.GameState.State.paused);
            }
        });
        menu.add(pauseButton).size(Scl.scl(45f));
        top.add(menu).left().row();

        //原版 HUD 左上只放波次面板；玩家状态由选中/悬停信息层提供。
        Table waveTable = new Table();
        //教程 outline 按这个名字找波次面板（对应原版 HudFragment.java:624 的 table.setName("waves")）
        waveTable.setName("waves");

        waveLabel = new Label("", Styles.outlineLabel);
        waveTable.add(waveLabel).padRight(Scl.scl(10f));

        Button waveButton = button("下一波");
        waveButton.addListener(new ClickListener(){
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                //计时归零，下一帧 Logic 就会开波
                if(Vars.state != null && Vars.state.isPlaying() && Vars.state.rules.waves){
                    Vars.state.wavetime = 0f;
                }
            }
        });
        waveTable.add(waveButton);

        //存档 / 读档 / 自动存档
        Button saveBtn = button("存档");
        saveBtn.addListener(new ClickListener(){
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                if(Vars.control != null) Vars.control.save();
            }
        });
        waveTable.add(saveBtn).padLeft(Scl.scl(10f));

        Button saveAsBtn = button("另存");
        saveAsBtn.addListener(new ClickListener(){
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                showSaveAsDialog();
            }
        });
        waveTable.add(saveAsBtn).padLeft(Scl.scl(4f));

        Button loadBtn = button("读档");
        loadBtn.addListener(new ClickListener(){
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                showLoadDialog();
            }
        });
        waveTable.add(loadBtn).padLeft(Scl.scl(6f));

        autosaveCheck = new com.badlogic.gdx.scenes.scene2d.ui.CheckBox("自动存", Styles.defaultCheck);
        autosaveCheck.setChecked(Vars.saves == null || Vars.saves.autosave);
        autosaveCheck.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener(){
            @Override
            public void changed(com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor){
                if(Vars.saves != null){
                    Vars.saves.autosave = autosaveCheck.isChecked();
                }
            }
        });
        waveTable.add(autosaveCheck).padLeft(Scl.scl(6f));

        //原版把 BOSS 血条放在 wavesMain（波次按钮那一列）的**第二行**，并用 `fillX` 撑满该列宽度：
        //`wavesMain.table(Tex.button, t -> t.margin(10f).add(new Bar("boss.health", Pal.health,
        //   () -> state.boss() == null ? 0f : state.boss().healthf()).blink(Color.white))
        //   .grow()).fillX().visible(() -> state.rules.waves && state.boss() != null).height(60f)`
        //本项目波次面板在左上角且是一横排 → 同样嵌一层 wavesMain，让血条宽度 = 波次行的宽度。
        //⚠ 判定用 `state.boss()`（= 带 StatusEffects.boss 且属波次阵营），不是 getType().boss。
        bossTable = new Table();
        bossTable.setBackground(io.anuke.mindustry.gen.Tex.button);
        bossTable.add(new com.phoenix.game.ui.Bar(
            com.phoenix.game.world.meta.I18n.get("boss.health", "Boss Health"),
            com.phoenix.game.graphics.Pal.health,
            () -> {
                com.phoenix.game.entities.type.BaseUnit b = Vars.state == null ? null : Vars.state.boss();
                return b == null ? 0f : b.healthf();
            }).blink(Color.WHITE)).grow().pad(Scl.scl(10f));
        bossTable.setVisible(false);

        Table wavesMain = new Table();
        wavesMain.add(waveTable).left().row();
        wavesMain.add(bossTable).fillX().height(Scl.scl(60f)).padTop(Scl.scl(6f));
        top.add(wavesMain).left().padTop(Scl.scl(6f)).row();

        //FPS / ping 显示（对应原版 HudFragment 的 "fps display" 块）。
        //原版用 IntFormat("fps")/IntFormat("ping") 惰性取值；本项目没有 IntFormat，
        //改在 MenuFragment.Act 里每帧 setText（文案取自 bundle，缺键时 I18n.get 回退）。
        Table fpsTable = new Table();
        Label fpsLabel = new Label("", Styles.outlineLabel);
        fpsLabel.setAlignment(Align.left);
        Label pingLabel = new Label("", Styles.outlineLabel);
        pingLabel.setAlignment(Align.left);
        fpsTable.add(fpsLabel).left().row();
        fpsTable.add(pingLabel).left().row();
        fpsTable.setVisible(false);
        top.add(fpsTable).left().padTop(Scl.scl(4f));

        //右上角小地图（对应原版 HudFragment 里的 parent.fill(t -> { t.add(new Minimap()); ... })）
        Table minimapTable = new Table();
        minimapTable.setFillParent(true);
        minimapTable.top().right();
        minimapTable.add(new com.phoenix.game.ui.Minimap());
        //坐标显示（对应原版小地图表里 t.row() 之后的 position label）
        Label positionLabel = new Label("", Styles.outlineLabel);
        minimapTable.row();
        minimapTable.add(positionLabel).right().padTop(Scl.scl(2f));
        group.addActor(minimapTable);

        //右侧建造菜单独立于世界相机，按 Mindustry PlacementFragment 组织
        placementFragment.build(group);
        //建筑配置面板（点 configurable 方块时弹出）
        configFragment.build(group);

        //建筑库存面板（点核心/容器等有物品的方块时弹出）。
        //对应原版 OverlayFragment 的 group：一层 childrenOnly 的全屏容器，空白处点击穿透到世界输入。
        //这里仅内联该容器并挂 inv（不直接用 OverlayFragment.add()：它的 config 实例会与上面
        //configFragment 同时响应 configTile，导致配置面板显示两份）。
        WidgetGroup overlay = new WidgetGroup();
        overlay.setFillParent(true);
        overlay.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly);
        group.addActor(overlay);
        inv.build(overlay);

        //桌面端：底部建造提示条（对应原版 DesktopInput.buildUI 往 uiGroup 塞的第一张 fill 表；
        //本类建好容器、由输入处理器往里填内容 —— 与原版 buildUI(group) 的分工等价）
        if(!Vars.mobile && input() != null){
            Table hintTable = new Table();
            hintTable.setFillParent(true);
            input().buildUI(hintTable);
            group.addActor(hintTable);
        }

        //移动端：左下角的"取消 / 蓝图面板"按钮（对应原版 MobileInput.buildUI）。
        //放在建造菜单之后、全屏小地图之前，这样它盖在世界上但压不住小地图。
        if(Vars.mobile && input() != null){
            Table mobileTable = new Table();
            mobileTable.setFillParent(true);
            mobileTable.bottom().left();
            input().buildUI(mobileTable);
            group.addActor(mobileTable);
        }

        //全屏小地图必须最后 build，才能盖在建造菜单之上
        //（对应原版 UI.java 里 minimapfrag.build 在 hudfrag.build 之后）
        minimapFragment.build(group);

        //教程引导文字（对应原版 HudFragment 的 "tutorial text" 块）：只在 rules.tutorial 为真时显示。
        //原版放**右上角**并靠 !state.rules.tutorial 隐藏小地图；本项目小地图固定在右上角，
        //所以教程文字改放**顶部居中**，避开波次面板（左上）与小地图（右上）。
        tutorialTable = new Table();
        tutorialTable.setFillParent(true);
        tutorialTable.top();
        tutorialTable.setVisible(false);
        group.addActor(tutorialTable);

        tutorialLabel = new Label("", Styles.outlineLabel);
        tutorialLabel.setWrap(true);
        tutorialLabel.setAlignment(Align.center);
        tutorialTable.add(tutorialLabel).width(Scl.scl(420f)).padTop(Scl.scl(64f)).row();

        Table tutorialNav = new Table();
        tutorialPrev = button("◀");
        tutorialPrev.addListener(new ClickListener(){
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                if(Vars.control != null && Vars.control.tutorial != null) Vars.control.tutorial.prevSentence();
            }
        });
        tutorialNext = button("▶");
        tutorialNext.addListener(new ClickListener(){
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                if(Vars.control != null && Vars.control.tutorial != null) Vars.control.tutorial.nextSentence();
            }
        });
        tutorialNav.add(tutorialPrev).size(Scl.scl(38f)).padRight(Scl.scl(6f));
        tutorialNav.add(tutorialNext).size(Scl.scl(38f));
        tutorialTable.add(tutorialNav);

        //暂停提示（对应原版 HudFragment 的 "paused table"：顶部居中的小黑条 "$paused"）。
        //⚠ 本项目教程文字也占顶部居中（见上，从 padTop 64px 起）→ 提示条压在最顶部，两者不重叠
        Table pausedTable = new Table();
        pausedTable.setFillParent(true);
        pausedTable.top();
        pausedTable.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        pausedTable.setVisible(false);
        group.addActor(pausedTable);

        Table pausedInner = new Table();
        pausedInner.setBackground(io.anuke.mindustry.gen.Tex.buttonTrans);
        pausedInner.add(new Label(com.phoenix.game.world.meta.I18n.get("paused", "Paused"), Styles.defaultLabel)).pad(Scl.scl(5f));
        pausedTable.add(pausedInner).padTop(Scl.scl(6f));

        //'saving' 指示器（对应原版 HudFragment 的 "saving indicator"：底部居中 "$saveload" 小字）
        Table savingTable = new Table();
        savingTable.setFillParent(true);
        savingTable.bottom();
        savingTable.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        savingTable.setVisible(false);
        group.addActor(savingTable);

        savingTable.add(new Label(com.phoenix.game.world.meta.I18n.get("saveload", "Saving..."), Styles.outlineLabel));

        //空投区警告（对应原版 HudFragment 的 "spawner warning" 块）：
        //整屏表居中放一个黑底 Label，alpha 每帧 lerp 到 playerNear。
        //⚠ 偏离：原版把背景/内边距挂在 table(Styles.black, ...).margin(6) 上；本项目 Table/Cell 没有 margin()
        //  → 改用 pad(6)。label 颜色在白/猩红间脉动照抄原版。
        Table nearTable = new Table();
        nearTable.setFillParent(true);
        nearTable.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        group.addActor(nearTable);

        Table nearInner = new Table();
        nearInner.setBackground(Styles.black);
        Label nearLabel = new Label(com.phoenix.game.world.meta.I18n.get("nearpoint", "LEAVE DROP POINT IMMEDIATELY"), Styles.defaultLabel);
        nearInner.add(nearLabel).pad(Scl.scl(6f));
        nearTable.add(nearInner);
        nearTable.getColor().a = 0f;

        //等待玩家提示（对应原版 "waiting for players" 块）：仅在服务器等待玩家时可见。
        //⚠ 偏离：原版 fill 表未禁用触摸；本项目整屏表禁用触摸，避免挡住建造/操作输入。
        Table waitingTable = new Table();
        waitingTable.setFillParent(true);
        waitingTable.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        group.addActor(waitingTable);

        Table waitingInner = new Table();
        waitingInner.setBackground(io.anuke.mindustry.gen.Tex.button);
        waitingInner.add(new Label(com.phoenix.game.world.meta.I18n.get("waiting.players", "Waiting for players..."), Styles.defaultLabel)).pad(Scl.scl(5f));
        waitingTable.add(waitingInner);

        //'core is under attack' 提示（对应原版 "'core is under attack' table"）：
        //整屏顶部按钮底 Label，收到 teamCoreDamage 事件后 240 帧内淡入淡出。
        Table coreTable = new Table();
        coreTable.setFillParent(true);
        coreTable.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        coreTable.top();
        group.addActor(coreTable);

        Table coreInner = new Table();
        coreInner.setBackground(io.anuke.mindustry.gen.Tex.button);
        Label coreLabel = new Label(com.phoenix.game.world.meta.I18n.get("coreattack", "< Core is under attack! >"), Styles.defaultLabel);
        coreInner.add(coreLabel).pad(Scl.scl(2f));
        coreTable.add(coreInner).padTop(Scl.scl(30f));

        //收到"核心被攻击"事件后计时 240 帧（对应原版 notifDuration / coreAttackTime）
        final float notifDuration = 240f;
        final float[] coreAttackTime = {0f};
        final float[] coreAttackOpacity = {0f};
        com.phoenix.game.core.Events.on(com.phoenix.game.game.EventType.Trigger.teamCoreDamage,
            () -> coreAttackTime[0] = notifDuration);

        //两者每帧刷新（与 waveLabel 同一模式；暂停判定对应原版 visible(() -> state.isPaused())）
        group.addActor(new MenuFragment.Act(() -> {
            if(pausedTable != null) pausedTable.setVisible(Vars.state != null && Vars.state.isPaused());
            if(savingTable != null) savingTable.setVisible(Vars.saves != null && Vars.saves.isSaving());
        }));

        //每帧刷新顶部状态
        group.addActor(new MenuFragment.Act(() -> {
            if(waveLabel != null){
                waveLabel.setText(waveStatus());
            }
            //BOSS 血条显隐（对应原版 `.visible(() -> state.rules.waves && state.boss() != null)`）
            if(bossTable != null){
                bossTable.setVisible(Vars.state != null && Vars.state.rules != null
                    && Vars.state.rules.waves && Vars.state.boss() != null);
            }
        }));

        //教程文字每帧刷新（对应原版 labelWrap(() -> control.tutorial.stage.text()) 的惰性取值）
        group.addActor(new MenuFragment.Act(() -> {
            if(tutorialTable == null) return;
            boolean active = Vars.state != null && Vars.state.rules != null && Vars.state.rules.tutorial
                && Vars.control != null && Vars.control.tutorial != null;
            tutorialTable.setVisible(active);
            if(active){
                com.phoenix.game.game.Tutorial tut = Vars.control.tutorial;
                tutorialLabel.setText(tut.stage.text()
                    + (tut.canNext() ? "\n\n" + com.phoenix.game.world.meta.I18n.get("tutorial.next", "next") : ""));
                tutorialPrev.setVisible(tut.canPrev());
                tutorialNext.setVisible(tut.canNext());
            }
        }));

        //FPS / ping 每帧刷新（对应原版 info.visible(() -> Core.settings.getBool("fps")) + 惰性文本；
        //ping 只在联机时显示，对应原版 label(...).visible(net::client)）
        group.addActor(new MenuFragment.Act(() -> {
            if(fpsTable == null) return;
            boolean show = com.phoenix.game.core.Core.settings.getBool("fps");
            fpsTable.setVisible(show);
            if(!show) return;

            String fpsFmt = com.phoenix.game.world.meta.I18n.get("fps", "FPS: {0}");
            fpsLabel.setText(fpsFmt.replace("{0}", String.valueOf(com.phoenix.game.core.Core.graphics.getFramesPerSecond())));

            boolean connected = Vars.netClient != null && Vars.netClient.isConnected();
            pingLabel.setVisible(connected);
            if(connected){
                String pingFmt = com.phoenix.game.world.meta.I18n.get("ping", "Ping: {0}ms");
                pingLabel.setText(pingFmt.replace("{0}", String.valueOf(Vars.netClient.getPing())));
            }
        }));

        //坐标显示每帧刷新（对应原版 visible(() -> Core.settings.getBool("position") && !state.rules.tutorial)）
        group.addActor(new MenuFragment.Act(() -> {
            if(positionLabel == null) return;
            boolean show = com.phoenix.game.core.Core.settings.getBool("position")
                && Vars.state != null && Vars.state.rules != null && !Vars.state.rules.tutorial
                && Vars.player != null && Vars.world != null;
            positionLabel.setVisible(show);
            if(show){
                positionLabel.setText(Vars.world.toTile(Vars.player.x) + "," + Vars.world.toTile(Vars.player.y));
            }
        }));

        //空投区警告每帧刷新（对应原版 update：整表 alpha lerp 到 playerNear，label 颜色白↔猩红脉动）。
        //⚠ Mathf.num 本项目不存在 → 用三元表达式取 1f/0f。
        group.addActor(new MenuFragment.Act(() -> {
            if(nearTable == null) return;
            float playerNear = (Vars.spawner != null && Vars.spawner.playerNear()) ? 1f : 0f;
            nearTable.getColor().a = com.phoenix.game.math.Mathf.lerpDelta(nearTable.getColor().a, playerNear, 0.1f);
            nearLabel.getColor().set(Color.WHITE).lerp(Color.SCARLET,
                com.phoenix.game.math.Mathf.absin(com.phoenix.game.core.Time.time(), 10f, 1f));
        }));

        //等待玩家提示每帧刷新（对应原版 visible(() -> netServer.isWaitingForPlayers())）
        group.addActor(new MenuFragment.Act(() -> {
            if(waitingTable == null) return;
            waitingTable.setVisible(Vars.netServer != null && Vars.netServer.isWaitingForPlayers());
        }));

        //核心被攻击提示每帧刷新（对应原版 t.top().visible(...) 里的淡入淡出逻辑）
        group.addActor(new MenuFragment.Act(() -> {
            if(coreTable == null) return;
            if(Vars.state == null || Vars.state.isMenu() || Vars.player == null){
                coreAttackTime[0] = 0f;
                coreTable.setVisible(false);
                return;
            }
            coreTable.getColor().a = coreAttackOpacity[0];
            coreAttackOpacity[0] = com.phoenix.game.math.Mathf.lerpDelta(coreAttackOpacity[0], coreAttackTime[0] > 0 ? 1f : 0f, 0.1f);
            coreAttackTime[0] -= com.phoenix.game.core.Time.delta();
            coreTable.setVisible(coreAttackOpacity[0] > 0f);
            coreLabel.getColor().set(Color.ORANGE).lerp(Color.SCARLET,
                com.phoenix.game.math.Mathf.absin(com.phoenix.game.core.Time.time(), 2f, 1f));
        }));
    }

    /** @return 当前输入处理器 */
    private InputHandler input(){
        return Vars.control == null ? null : Vars.control.input;
    }

    /** @return 建造菜单（DesktopInput 中键吸取用）。 */
    public PlacementFragment placement(){
        return placementFragment;
    }

    /**
     * toast 排队（对应原版 {@code HudFragment.scheduleToast}，HudFragment.java:396-406）：
     * 距上次 toast 足够久（3.5s）就立即弹，否则按剩余间隔排进延迟任务 —— 连续解锁的提示逐条出现、不叠屏。
     */
    private void scheduleToast(Runnable run){
        long duration = (int)(3.5 * 1000);
        long since = com.phoenix.game.core.Time.timeSinceMillis(lastToast);
        if(since > duration){
            lastToast = com.phoenix.game.core.Time.millis();
            run.run();
        }else{
            com.phoenix.game.core.Time.run((duration - since) / 1000f * 60f, run);
            lastToast += duration;
        }
    }

    /**
     * 解锁提示（对应原版 {@code HudFragment.showUnlock}，HudFragment.java:440-526；由
     * {@code Control} 构造器的 UnlockEvent 监听器经 postRunnable 调用）：
     * 第一条弹「内容图标 + $unlocked」toast（顶部滑入、停 2.5s、滑出）；
     * 短时间内连续解锁则把图标堆进同一条 toast（3 列、9 格容量，满了显示加号图标）。
     * <p>⚠ <b>libgdx 适配</b>：原版用 {@code table.update(...)} 每帧回调 + arc 的 actions(...) 并行
     * 动作；libgdx 无每帧回调 → 用 {@code MenuFragment.Act} 做菜单态自删；
     * {@code container.actions(a, b, c)} → {@code Actions.parallel(动作, sequence(delay, run))}。
     * 原版图标 {@code content.icon(Cicon.xlarge/medium)} 可能缺图（图集缺区域）→ null 时跳过该图标。
     */
    public void showUnlock(com.phoenix.game.ctype.UnlockableContent content){
        //菜单态不弹；教程里不弹以免困惑（原版同款守卫）
        if(Vars.state == null || Vars.state.isMenu()
            || (Vars.state.rules != null && Vars.state.rules.tutorial)) return;
        com.badlogic.gdx.scenes.scene2d.Stage stage = FloatingDialog.scene;
        if(stage == null) return;

        io.anuke.mindustry.gen.Sounds.message.play();

        //如果当前没有解锁提示 → 弹一条新 toast（对应原版 lastUnlockTable == null 分支）
        if(lastUnlockTable == null){
            scheduleToast(() -> {
                com.badlogic.gdx.scenes.scene2d.Stage s = FloatingDialog.scene;
                if(s == null) return;

                Table table = new Table();
                table.setBackground(io.anuke.mindustry.gen.Tex.button);
                table.pad(Scl.scl(12f));
                //菜单态自删（对应原版 table.update(...)）
                table.addActor(new com.phoenix.game.ui.MenuFragment.Act(() -> {
                    if(Vars.state != null && Vars.state.isMenu()){
                        table.remove();
                        lastUnlockLayout = null;
                        lastUnlockTable = null;
                    }
                }));

                Table in = new Table();

                //内容图标（xlarge）；缺图时跳过（原版注释：some content may not have icons... yet）
                com.badlogic.gdx.graphics.g2d.TextureRegion icon = content.icon(com.phoenix.game.ui.Cicon.xlarge);
                if(icon != null){
                    Image image = new Image(new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(icon));
                    image.setScaling(com.badlogic.gdx.utils.Scaling.fit);
                    in.add(image).size(Scl.scl(8 * 6)).pad(Scl.scl(2f));
                }

                //add to table
                table.add(in).padRight(Scl.scl(8f));
                table.add(new Label(com.phoenix.game.world.meta.I18n.get("unlocked", "New content unlocked!"),
                    Styles.outlineLabel));
                table.pack();

                //create container table which will align and move
                Table container = new Table();
                container.setFillParent(true);
                container.top();
                container.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly);
                container.add(table);
                s.addActor(container);

                float height = table.getPrefHeight();
                //原版用 arc Element 的 setTranslation(0, height) + translateBy 做滑入/滑出——libgdx 没有
                //该渲染偏移 API → 等价改为淡入/淡出（滑出后自删，lastUnlock 两字段同步清空）
                container.getColor().a = 0f;
                container.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.parallel(
                    com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(1f,
                        com.badlogic.gdx.math.Interpolation.fade),
                    com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(2.5f),
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.run(() -> container.addAction(
                            com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                                com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeOut(1f,
                                    com.badlogic.gdx.math.Interpolation.fade),
                                com.badlogic.gdx.scenes.scene2d.actions.Actions.run(() -> {
                                    lastUnlockTable = null;
                                    lastUnlockLayout = null;
                                }),
                                com.badlogic.gdx.scenes.scene2d.actions.Actions.removeActor()))))));

                lastUnlockTable = container;
                lastUnlockLayout = in;
            });
        }else{
            //堆叠分支：把新图标塞进上一条 toast（对应原版 else 分支）
            //max column size
            int col = 3;
            //max amount of elements minus extra 'plus'
            int cap = col * col - 1;

            //get old elements（libgdx getChildren 返回 SnapshotArray → 拷贝一份再重建）
            com.badlogic.gdx.utils.Array<com.badlogic.gdx.scenes.scene2d.Actor> elements =
                new com.badlogic.gdx.utils.Array<>(lastUnlockLayout.getChildren());
            int esize = elements.size;

            //...if it's already reached the cap, ignore everything
            if(esize > cap) return;

            //get size of each element
            float size = 48f / Math.min(elements.size + 1, col);

            lastUnlockLayout.clearChildren();
            lastUnlockLayout.defaults().size(Scl.scl(size)).pad(Scl.scl(2f));

            for(int i = 0; i < esize; i++){
                lastUnlockLayout.add(elements.get(i));

                if(i % col == col - 1){
                    lastUnlockLayout.row();
                }
            }

            //if there's space, add it
            if(esize < cap){
                com.badlogic.gdx.graphics.g2d.TextureRegion icon = content.icon(com.phoenix.game.ui.Cicon.medium);
                if(icon != null){
                    Image image = new Image(new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(icon));
                    image.setScaling(com.badlogic.gdx.utils.Scaling.fit);
                    lastUnlockLayout.add(image);
                }
            }else{ //else, add a specific icon to denote no more space
                lastUnlockLayout.add(new Image(io.anuke.mindustry.gen.Icon.add));
            }

            lastUnlockLayout.pack();
        }
    }

    /**
     * 开局降落时的全屏白闪（对应原版 {@code HudFragment.showLand}）：
     * 往 HUD 所在 stage 的顶层塞一张铺满屏幕的白色 Image，0.8 秒淡出后自删。
     * <p>⚠ <b>与 libgdx 的差异</b>：原版 {@code new Image()} 用的是 arc 的默认白色 drawable；
     * libgdx 的 {@code Image} 没有默认 drawable（不给就什么都画不出来）→ 这里显式给一个 {@code white} 贴图区域
     * （缺失时回退 {@code blank}，与 {@code Renderer.region(...)} 同口径）。
     * <p>⚠ <b>偏离</b>：原版还在 update 钩子里每帧 {@code image.toFront()} 并在菜单态 {@code image.remove()}；
     * 本项目 {@code showLand()} 只在 {@code Trigger.newGame}（非菜单态）被调用，0.8s 后自动移除，
     * 故省去该 update 钩子。
     */
    public void showLand(){
        if(group == null || group.getStage() == null) return;

        com.badlogic.gdx.graphics.g2d.TextureRegion white = com.phoenix.game.core.Core.atlas == null
            ? null : com.phoenix.game.core.Core.atlas.findRegion("white");
        if(white == null && com.phoenix.game.core.Core.atlas != null){
            white = com.phoenix.game.core.Core.atlas.findRegion("blank");
        }
        if(white == null) return;

        com.badlogic.gdx.scenes.scene2d.ui.Image image = new com.badlogic.gdx.scenes.scene2d.ui.Image(
            new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(white));
        image.getColor().a = 1f;
        image.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        //铺满 stage（fillParent 由 Widget.validate() 在绘制时生效）
        image.setFillParent(true);
        image.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
            com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeOut(0.8f),
            com.badlogic.gdx.scenes.scene2d.actions.Actions.removeActor()));
        group.getStage().addActor(image);
    }

    /** 弹出读档对话框（HUD 用）：共用静态实现。 */
    private void showLoadDialog(){
        if(group != null) showLoadDialog(group.getStage(), null);
    }

    /** 弹出"另存为"对话框：输入名字，新建一个槽位并保存当前局。 */
    private void showSaveAsDialog(){
        if(group == null || group.getStage() == null || Vars.saves == null) return;
        com.badlogic.gdx.scenes.scene2d.Stage stage = group.getStage();

        com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle winStyle =
            new com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle(com.phoenix.game.core.Fonts.def,
                com.phoenix.game.graphics.Pal.accent, Styles.defaultDialog.background);
        com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog =
            new com.badlogic.gdx.scenes.scene2d.ui.Dialog("另存为", winStyle);

        com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle tfStyle =
            new com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle(com.phoenix.game.core.Fonts.def,
                com.badlogic.gdx.graphics.Color.WHITE, Styles.flatDown, null, Styles.black6);
        com.badlogic.gdx.scenes.scene2d.ui.TextField nameField =
            new com.badlogic.gdx.scenes.scene2d.ui.TextField("存档 " + (Vars.saves.getSlots().size + 1), tfStyle);

        Table form = new Table();
        form.add(new Label("存档名", Styles.defaultLabel)).left().row();
        form.add(nameField).width(Scl.scl(240f)).pad(Scl.scl(3f)).row();
        dialog.getContentTable().add(form).pad(Scl.scl(8f));

        Button ok = new Button(Styles.defaultb);
        ok.add(new Label("保存", Styles.defaultLabel)).pad(Scl.scl(5f));
        ok.addListener(new ClickListener(){
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                try{
                    String name = nameField.getText().trim();
                    Vars.saves.addSave(name.isEmpty() ? null : name);
                }catch(Exception e){
                    System.err.println("另存为失败: " + e);
                }
                dialog.hide();
            }
        });
        Button cancel = new Button(Styles.defaultb);
        cancel.add(new Label("取消", Styles.defaultLabel)).pad(Scl.scl(5f));
        cancel.addListener(new ClickListener(){
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                dialog.hide();
            }
        });
        dialog.getButtonTable().add(ok).width(Scl.scl(100f)).height(Scl.scl(36f)).pad(Scl.scl(4f));
        dialog.getButtonTable().add(cancel).width(Scl.scl(100f)).height(Scl.scl(36f)).pad(Scl.scl(4f));
        dialog.show(stage);
        dialog.pack();
        dialog.setPosition((stage.getWidth() - dialog.getWidth()) / 2f,
                           (stage.getHeight() - dialog.getHeight()) / 2f);
    }

    /**
     * 弹出读档对话框：列出全部槽位（缩略图 + 名字 + 波次 + 时长 + 存档时间），可读取或删除。
     * <p>菜单与 HUD 共用。
     * @param onLoad 读档前的额外回调（菜单用它先隐藏自己），可为 null
     */
    public static void showLoadDialog(com.badlogic.gdx.scenes.scene2d.Stage stage, Runnable onLoad){
        if(stage == null || Vars.saves == null) return;

        com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle winStyle =
            new com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle(com.phoenix.game.core.Fonts.def,
                com.phoenix.game.graphics.Pal.accent, Styles.defaultDialog.background);

        //缩略图纹理每张占一个 GL 纹理，对话框关闭时必须释放
        final com.badlogic.gdx.utils.Array<com.badlogic.gdx.graphics.Texture> textures = new com.badlogic.gdx.utils.Array<>();
        com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog =
            new com.badlogic.gdx.scenes.scene2d.ui.Dialog("读档", winStyle){
                @Override
                public void hide(){
                    super.hide();
                    for(com.badlogic.gdx.graphics.Texture texture : textures){
                        texture.dispose();
                    }
                    textures.clear();
                }
            };

        Table list = new Table();
        list.top().left();

        com.badlogic.gdx.utils.Array<com.phoenix.game.game.Saves.SaveSlot> slots = Vars.saves.getSlots();
        if(slots.size == 0){
            list.add(new Label("暂无存档", Styles.defaultLabel)).pad(Scl.scl(10f));
        }else{
            for(com.phoenix.game.game.Saves.SaveSlot slot : slots){
                list.add(buildSlotCard(dialog, slot, stage, onLoad, textures)).growX().pad(Scl.scl(3f)).row();
            }
        }

        dialog.getContentTable().add(list).pad(Scl.scl(6f));
        //注意：Dialog 由 WindowStyle 构造（skin 为 null），不能调用 dialog.button(...)——
        //libgdx 该方法依赖 skin，会抛 IllegalStateException 直接崩掉渲染线程
        Button close = new Button(Styles.defaultb);
        close.add(new Label("关闭", Styles.defaultLabel)).pad(Scl.scl(5f));
        close.addListener(new ClickListener(){
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                dialog.hide();
            }
        });
        dialog.getButtonTable().add(close).width(Scl.scl(120f)).height(Scl.scl(40f)).pad(Scl.scl(4f));
        dialog.show(stage);
        //pack 后重新居中：show() 内部用的是 pack 前的旧尺寸，否则窗口与 hit 区域会错开
        dialog.pack();
        dialog.setPosition((stage.getWidth() - dialog.getWidth()) / 2f,
                           (stage.getHeight() - dialog.getHeight()) / 2f);
    }

    /** 组装一张存档卡片：缩略图 + 信息 + 读取/删除按钮。 */
    private static Table buildSlotCard(com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog,
                                       com.phoenix.game.game.Saves.SaveSlot slot,
                                       com.badlogic.gdx.scenes.scene2d.Stage stage, Runnable onLoad,
                                       com.badlogic.gdx.utils.Array<com.badlogic.gdx.graphics.Texture> textures){
        Table card = new Table();
        card.setBackground(Styles.black6);

        //缩略图（存档时生成的 saves/<n>.msav.png）
        if(slot.hasPreview()){
            try{
                com.badlogic.gdx.graphics.Texture texture = new com.badlogic.gdx.graphics.Texture(
                    com.badlogic.gdx.Gdx.files.absolute(slot.previewFile().getAbsolutePath()));
                textures.add(texture);
                card.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(texture)).size(Scl.scl(64f)).pad(Scl.scl(3f));
            }catch(Exception e){
                card.add(new Label("无图", Styles.defaultLabel)).size(Scl.scl(64f)).pad(Scl.scl(3f));
            }
        }else{
            card.add(new Label("无图", Styles.defaultLabel)).size(Scl.scl(64f)).pad(Scl.scl(3f));
        }

        //信息
        Table info = new Table();
        Label nameLabel = new Label(slot.name, Styles.defaultLabel);
        nameLabel.setAlignment(Align.left);
        info.add(nameLabel).left().row();
        Label detailLabel = new Label("第 " + slot.wave() + " 波 · 时长 " + slot.playtimeText(), Styles.defaultLabel);
        detailLabel.setAlignment(Align.left);
        info.add(detailLabel).left().padTop(Scl.scl(2f)).row();
        Label dateLabel = new Label(slot.dateText(), Styles.defaultLabel);
        dateLabel.setAlignment(Align.left);
        info.add(dateLabel).left().padTop(Scl.scl(2f)).row();
        card.add(info).width(Scl.scl(180f)).padLeft(Scl.scl(6f));

        //操作
        Table actions = new Table();
        Button load = new Button(Styles.defaultb);
        load.add(new Label("读取", Styles.defaultLabel)).pad(Scl.scl(4f));
        load.addListener(new ClickListener(){
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                dialog.hide();
                if(onLoad != null) onLoad.run();
                if(Vars.control != null) Vars.control.load(slot);
            }
        });
        actions.add(load).width(Scl.scl(70f)).height(Scl.scl(32f)).row();

        Button remove = new Button(Styles.defaultb);
        remove.add(new Label("删除", Styles.defaultLabel)).pad(Scl.scl(4f));
        remove.addListener(new ClickListener(){
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                Vars.saves.delete(slot);
                dialog.hide();
                //重建列表，让删除立刻可见
                showLoadDialog(stage, onLoad);
            }
        });
        actions.add(remove).width(Scl.scl(70f)).height(Scl.scl(32f)).padTop(Scl.scl(4f)).row();

        card.add(actions).pad(Scl.scl(4f));
        return card;
    }

    /** 波次信息：第 N 波 · 下一波倒计时 · 场上敌军数。 */
    private String waveStatus(){
        if(Vars.state == null || Vars.state.isMenu() || Vars.state.rules == null || !Vars.state.rules.waves){
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[WHITE]第 ").append(Vars.state.wave).append(" 波[]");
        if(isBossWave()){
            sb.append("[GOLD]  ☠ BOSS[]");
        }

        if(Vars.state.rules.waveTimer){
            float remain = Math.max(Vars.state.wavetime, 0f) / 60f;
            sb.append("[LIGHT_GRAY] · 下一波 ").append((int)(remain / 60f)).append(':')
              .append(String.format("%02d", (int)(remain % 60f))).append("[]");
        }

        sb.append("[LIGHT_GRAY] · 敌军 ").append(Vars.state.enemies).append("[]");
        return sb.toString();
    }

    /** 判断当前显示波次是否包含 Boss 编成。 */
    private boolean isBossWave(){
        if(Vars.state.rules.spawns == null) return false;
        int spawnWave = Vars.state.wave - 1;
        for(int i = 0; i < Vars.state.rules.spawns.size; i++){
            com.phoenix.game.game.SpawnGroup group = Vars.state.rules.spawns.get(i);
            if(group.type != null && group.type.boss && group.getUnitsSpawned(spawnWave) > 0){
                return true;
            }
        }
        return false;
    }

    /** 放置状态提示，显示当前方块朝向。 */
    private String coreStatus(){
        InputHandler build = input();
        if(build == null || build.buildBlock == null || !build.buildBlock.rotate) return "";
        return "放置朝向：" + FACING[build.buildRotation & 3] + "（R 键切换）";
    }

    /** 显示/隐藏右侧建造菜单（对应 Mindustry toggleMenus）。 */
    public void toggleMenus(){
        menusShown = !menusShown;
        placementFragment.setShown(menusShown);
    }

    /** @return 右侧 HUD 菜单是否显示。 */
    public boolean menusShown(){
        return menusShown;
    }

    /** @return 全屏小地图是否打开（DesktopInput 据此让出输入）。 */
    public boolean minimapFullShown(){
        return minimapFragment.shown();
    }

    /** @return 全屏小地图 fragment（对应原版 {@code ui.minimapfrag}）。 */
    public MinimapFragment minimapFragment(){
        return minimapFragment;
    }

    /** 显示 HUD（进入战役时）。 */
    public void show(){
        if(group != null) group.setVisible(true);
    }

    /** 隐藏 HUD（进入菜单时）。 */
    public void hide(){
        //全屏小地图也要一起收掉，否则下次进游戏会直接盖住画面
        if(minimapFragment.shown()) minimapFragment.toggle();
        if(group != null) group.setVisible(false);
    }

    /** 简单文本按钮。 */
    private Button button(String text){
        Button btn = new Button(Styles.defaultb);
        Label label = new Label(text, Styles.defaultLabel);
        label.setAlignment(Align.center);
        btn.add(label).pad(Scl.scl(5f));
        return btn;
    }
}
