package com.phoenix.game.ui.fragments;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Items;
import com.phoenix.game.core.Scl;
import com.phoenix.game.game.Team;
import com.phoenix.game.input.InputHandler;
import com.phoenix.game.ui.Styles;
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
    /** 右侧建造菜单。 */
    private final PlacementFragment placementFragment = new PlacementFragment();
    private final MinimapFragment minimapFragment = new MinimapFragment();
    private boolean menusShown = true;

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

        top.add(waveTable).left().padTop(Scl.scl(6f));

        //右上角小地图（对应原版 HudFragment 里的 parent.fill(t -> { t.add(new Minimap()); ... })）
        Table minimapTable = new Table();
        minimapTable.setFillParent(true);
        minimapTable.top().right();
        minimapTable.add(new com.phoenix.game.ui.Minimap());
        group.addActor(minimapTable);

        //右侧建造菜单独立于世界相机，按 Mindustry PlacementFragment 组织
        placementFragment.build(group);
        //全屏小地图必须最后 build，才能盖在建造菜单之上
        //（对应原版 UI.java 里 minimapfrag.build 在 hudfrag.build 之后）
        minimapFragment.build(group);

        //每帧刷新顶部状态
        group.addActor(new MenuFragment.Act(() -> {
            if(waveLabel != null){
                waveLabel.setText(waveStatus());
            }
        }));
    }

    /** @return 当前输入处理器 */
    private InputHandler input(){
        return Vars.control == null ? null : Vars.control.input;
    }

    /** 弹出读档对话框（HUD 用）：共用静态实现。 */
    private void showLoadDialog(){
        if(group != null) showLoadDialog(group.getStage());
    }

    /** 弹出读档对话框：列出全部槽位，点选即读档（菜单与 HUD 共用）。 */
    public static void showLoadDialog(com.badlogic.gdx.scenes.scene2d.Stage stage){
        if(stage == null || Vars.saves == null) return;

        com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle winStyle =
            new com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle(com.phoenix.game.core.Fonts.def, com.phoenix.game.graphics.Pal.accent, Styles.defaultDialog.background);
        com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog = new com.badlogic.gdx.scenes.scene2d.ui.Dialog("读档", winStyle);
        Table list = new Table();
        list.top().left();

        com.badlogic.gdx.utils.Array<com.phoenix.game.game.Saves.SaveSlot> slots = Vars.saves.getSlots();
        if(slots.size == 0){
            list.add(new Label("暂无存档", Styles.defaultLabel));
        }else{
            for(com.phoenix.game.game.Saves.SaveSlot slot : slots){
                Button b = new Button(Styles.defaultb);
                Label label = new Label(slot.toString(), Styles.defaultLabel);
                label.setAlignment(Align.center);
                b.add(label).pad(Scl.scl(5f));
                b.addListener(new ClickListener(){
                    @Override
                    public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                        dialog.hide();
                        if(Vars.control != null) Vars.control.load(slot);
                    }
                });
                list.add(b).width(Scl.scl(260f)).pad(Scl.scl(2f)).row();
            }
        }

        dialog.getContentTable().add(list);
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
