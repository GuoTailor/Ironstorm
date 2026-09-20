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
    /** 建筑配置面板（点分拣器/指挥中心/物品源等可配置方块时弹出）。 */
    private final com.phoenix.game.ui.fragments.BlockConfigFragment configFragment = new com.phoenix.game.ui.fragments.BlockConfigFragment();
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

        top.add(waveTable).left().padTop(Scl.scl(6f));

        //右上角小地图（对应原版 HudFragment 里的 parent.fill(t -> { t.add(new Minimap()); ... })）
        Table minimapTable = new Table();
        minimapTable.setFillParent(true);
        minimapTable.top().right();
        minimapTable.add(new com.phoenix.game.ui.Minimap());
        group.addActor(minimapTable);

        //右侧建造菜单独立于世界相机，按 Mindustry PlacementFragment 组织
        placementFragment.build(group);
        //建筑配置面板（点 configurable 方块时弹出）
        configFragment.build(group);
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
