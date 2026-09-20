package com.phoenix.game.ui.fragments;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.game.Team;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.input.InputHandler;
import com.phoenix.game.type.Category;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Build;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.production.Drill;
import io.anuke.mindustry.gen.Tex;

/**
 * 右侧建造菜单，参照 Mindustry PlacementFragment 使用 libgdx Scene2D 重写。
 * <p>自上而下四段（与原版 build 方法的结构一一对应）：
 * <ol>
 *   <li>顶部详情区（原版 Tex.buttonEdge2 的 topTable）：选中配方或悬停建筑；</li>
 *   <li>分隔条（原版 Pal.gray 的 4px 线）；</li>
 *   <li>方块网格（原版 Tex.pane2 + ScrollPane，4 列）与右侧独立分类列；</li>
 *   <li>底部放置控件（取消建造 / 拆除）。</li>
 * </ol>
 * 详情区在未选中任何方块且未悬停建筑时完全隐藏（对应原版 visible 条件）。
 */
public class PlacementFragment extends Fragment{
    /** 每行方块列数，对应原版 PlacementFragment.rowWidth。 */
    private static final int ROW_WIDTH = 4;
    /** 面板总宽度 = 网格 232 + 分类列 106 + body 左右内边距 10。 */
    private static final float PANEL_WIDTH = 348f;
    /** 方块按钮尺寸，对应原版 .size(46f)。 */
    private static final float SLOT_SIZE = 46f;
    /** 网格外框宽度：4 列方块 + 间距 + 滚动条。 */
    private static final float GRID_FRAME_WIDTH = 232f;
    /** 分类列宽度：2 列 × 50 + 间距。 */
    private static final float CATEGORY_COLUMN_WIDTH = 106f;
    /** 详情区高度（原版按内容自适应，这里给固定高度避免面板抖动）。 */
    private static final float DETAIL_HEIGHT = 96f;
    /** 网格区高度，对应原版 pane(...).height(194f)。 */
    private static final float GRID_HEIGHT = 194f;
    /** 分类按钮尺寸，对应原版 categories.defaults().size(50f)。 */
    private static final float CATEGORY_SIZE = 50f;
    /** 详情区图标尺寸，对应原版 8 * 4。 */
    private static final float DETAIL_ICON_SIZE = 32f;
    /** 状态条区域最多显示的高度（避免面板被撑开）。 */
    private static final float BARS_MAX_HEIGHT = 84f;

    private final Array<Block> blocks = new Array<>();
    private final Array<ImageButton> buttons = new Array<>();

    private Table root;
    /** 详情区容器（对应原版 topTable）。 */
    private Table detail;
    /** 详情外框（未选中时整块隐藏）。 */
    private Table detailRoot;
    /** 详情外框的布局单元（用于动态收放高度）。 */
    private com.badlogic.gdx.scenes.scene2d.ui.Cell<Table> detailCell;
    /** 网格面板（对应原版 blocksSelect）。 */
    private Table grid;
    private ScrollPane blockPane;
    /** 蓝图按钮容器（横向滚动）。 */
    private Table schematicList;
    /** 上次构建蓝图列表时的蓝图数量，用于检测"新存了一张"。 */
    private int schematicCount = -1;
    /** 分类按钮列（对应原版 categories），每帧刷新选中态。 */
    private Table categories;

    // ---- RTS 命令面板（对应原版 commandTable：命令模式下替换建造网格显示） ----
    /** 建造内容（网格 + 分类 + 蓝图行），命令模式下隐藏。 */
    private Table mainContent;
    /** 命令模式面板。 */
    private Table commandTable;
    /** 命令面板动态内容区（选中单位列表 + 命令按钮）。 */
    private Table commandList;
    /** 上次重建时的选择签名（类型计数 + 姿态位），变化才重建。 */
    private String lastCommandSig = "";

    private boolean shown = true;
    /** 原版默认打开分配类。 */
    private Category currentCategory = Category.distribution;

    /** 上一帧悬停的瓦片与方块，避免详情区每帧重建（对应原版 lastHover/lastDisplay/lastGround）。 */
    private Tile lastHover;
    private Block lastDisplay;
    /** 上一帧悬停的建筑方块（null 表示未悬停建筑）。 */
    private Block lastGroundBlock;

    @Override
    public void build(Group parent){
        root = new Table();
        root.setFillParent(true);
        root.bottom().right();
        root.setVisible(false);
        parent.addActor(root);

        Table panel = new Table();
        panel.setBackground(Styles.black9);
        panel.right().bottom();
        //固定宽度：保证 growX 的行（详情区/分隔条）有确定基准，
        //数值 = 网格 232 + 分类列 106 + body 左右内边距 10，避免内容溢出屏幕右缘
        panel.setWidth(Scl.scl(PANEL_WIDTH));
        root.add(panel).right().bottom();

        // 1) 顶部详情区：原版用 Tex.buttonEdge2 包裹，未选中时整块隐藏
        Table detailFrame = new Table();
        detailFrame.setBackground(Tex.buttonEdge2);
        detail = new Table();
        detail.top().left().pad(Scl.scl(5f));
        detailFrame.add(detail).grow().pad(Scl.scl(3f));
        //高度动态收缩：无内容时归零，不自留空白（对应原版 visible(false) 整块塌陷）
        detailCell = panel.add(detailFrame).growX().height(0f).padLeft(Scl.scl(5f)).padRight(Scl.scl(5f));
        panel.row();

        //未选中配方且未悬停建筑时整块隐藏
        detailRoot = detailFrame;

        // 2) 分隔条：原版 Pal.gray 的 4px 线
        Table divider = new Table();
        divider.setBackground(Styles.flatOver);
        divider.setColor(Pal.gray);
        panel.add(divider).growX().height(Scl.scl(4f)).row();

        // 3) 网格 + 分类列（原版 blocksSelect 与 categories 并排同一行）
        //    原版两者是 frame 同一行的两个 cell，各自 fillY().bottom()：
        //    分类列 5 行 × 50 比网格 194 更高，行高取二者较大值，网格靠底对齐。
        //    因此这里不给分类列设高度，让它撑开行高。
        Table body = new Table();
        Table gridFrame = new Table();
        gridFrame.setBackground(Tex.pane2);
        gridFrame.pad(Scl.scl(4f));

        grid = new Table();
        grid.top().left().pad(Scl.scl(5f));
        blockPane = new ScrollPane(grid, Styles.defaultPane);
        gridFrame.add(blockPane).growX().height(Scl.scl(GRID_HEIGHT)).row();
        gridFrame.add(actionButtons()).growX();

        body.add(gridFrame).width(Scl.scl(GRID_FRAME_WIDTH)).bottom();
        body.add(categories = buildCategories()).width(Scl.scl(CATEGORY_COLUMN_WIDTH)).bottom();

        // 3b) 命令面板：命令模式下与建造内容互斥显示（对应原版 mainStack 的 blockCatTable/commandTable 切换）
        mainContent = new Table();
        mainContent.add(body).growX().pad(Scl.scl(5f)).row();
        mainContent.add(schematicRow()).growX().padLeft(Scl.scl(5f)).padRight(Scl.scl(5f)).row();

        commandTable = buildCommandTable();
        commandTable.setVisible(false);

        Stack mainStack = new Stack();
        mainStack.add(mainContent);
        mainStack.add(commandTable);
        panel.add(mainStack).growX().row();

        rebuildGrid();
        rebuildCategories();
        root.addActor(new MenuFragment.Act(this::act));
    }

    /**
     * 构建分类按钮列（对应原版 frame.table(categories -> ...)）。
     * <p>空分类显示为黑色占位块，与原版一致；两列排布。
     */
    private Table buildCategories(){
        Table table = new Table();
        table.bottom();
        table.defaults().size(Scl.scl(CATEGORY_SIZE));
        return table;
    }

    /** 重建分类按钮（分类空与否会随解锁变化）。 */
    private void rebuildCategories(){
        if(categories == null) return;
        categories.clearChildren();

        int column = 0;
        for(Category category : Category.all){
            if(column++ % 2 == 0) categories.row();

            Array<Block> list = blocksOf(category);
            if(list.isEmpty()){
                //空分类：原版放一个黑色占位块，保持网格整齐
                categories.add(new Image(Styles.black6)).size(Scl.scl(CATEGORY_SIZE));
                continue;
            }

            ImageButton button = new ImageButton(new ImageButton.ImageButtonStyle(Styles.clearToggleTransi));
            TextureRegion icon = categoryIcon(category);
            if(icon != null) button.getStyle().imageUp = new TextureRegionDrawable(icon);
            button.setChecked(currentCategory == category);
            button.addListener(new ClickListener(){
                @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                    currentCategory = category;
                    rebuildGrid();
                    rebuildCategories();
                }
            });
            categories.add(button).size(Scl.scl(CATEGORY_SIZE));
        }
    }

    /** @return 分类图标；图集缺失时回退到方块图标。 */
    private TextureRegion categoryIcon(Category category){
        if(Core.atlas == null) return null;
        return Core.atlas.findRegion("icon-" + category.name());
    }

    /** 分类按钮的选中态刷新（分类表重建后仍需逐帧同步）。 */
    private void updateCategoryChecked(){
        if(categories == null) return;
        int index = 0;
        for(Category category : Category.all){
            Array<Block> list = blocksOf(category);
            if(list.isEmpty()) continue;

            com.badlogic.gdx.scenes.scene2d.Actor actor = categories.getChildren().get(index);
            if(actor instanceof ImageButton){
                ((ImageButton)actor).setChecked(currentCategory == category);
            }
            index++;
        }
    }

    /** 底部放置控件：取消建造 / 拆除（对应原版 inputTable 的放置 UI）。 */
    private Table actionButtons(){
        Table table = new Table();
        table.defaults().growX().height(Scl.scl(34f)).pad(Scl.scl(2f));

        Button breaking = new Button(Styles.defaultb);
        breaking.add(new Label("拆除", Styles.defaultLabel)).pad(Scl.scl(4f));
        breaking.addListener(new ClickListener(){
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                InputHandler input = input();
                if(input != null){ input.buildBlock = null; input.breaking = true; }
            }
        });
        table.add(breaking);

        Button clear = new Button(Styles.defaultb);
        clear.add(new Label("取消建造", Styles.defaultLabel)).pad(Scl.scl(4f));
        clear.addListener(new ClickListener(){
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                InputHandler input = input();
                if(input != null) input.clearBuild();
            }
        });
        table.add(clear);
        return table;
    }

    /** 复制选区半径（格）：以玩家脚下为中心取 (2r+1)² 的区域。原版是拖拽选区，这里简化为固定范围。 */
    private static final int SCHEMATIC_COPY_RADIUS = 3;
    /** 蓝图缩略图的显示边长（像素）。 */
    private static final float SCHEMATIC_PREVIEW_SIZE = 56f;

    /**
     * 蓝图行：左边"复制选区"，右边蓝图列表（横向滚动）。
     * <p>点蓝图按钮 → 进入粘贴模式（{@link InputHandler#pasteSchematic}），再点世界即可贴。
     * <p>原版是拖拽框选 + 独立蓝图对话框，这里简化为固定范围复制 + 内嵌列表。
     */
    private Table schematicRow(){
        Table table = new Table();
        table.defaults().pad(Scl.scl(2f));

        Button copy = new Button(Styles.defaultb);
        copy.add(new Label("复制选区", Styles.defaultLabel)).pad(Scl.scl(4f));
        copy.addListener(new ClickListener(){
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                saveSelection();
            }
        });
        table.add(copy).height(Scl.scl(30f)).width(Scl.scl(84f));

        schematicList = new Table();
        ScrollPane pane = new ScrollPane(schematicList, Styles.defaultPane);
        pane.setScrollingDisabled(false, true);
        //高度要能放下缩略图 + 名字（缩略图是懒生成的，没生成出来时这个高度也只是稍微宽松些）
        table.add(pane).height(Scl.scl(SCHEMATIC_PREVIEW_SIZE + 26f)).growX();

        rebuildSchematicList();
        return table;
    }

    /** 把玩家脚下 (2r+1)² 的区域存成蓝图。 */
    private void saveSelection(){
        if(Vars.world == null || Vars.player == null || Vars.schematics == null) return;

        int cx = Vars.world.toTile(Vars.player.getX());
        int cy = Vars.world.toTile(Vars.player.getY());
        com.phoenix.game.game.Schematic schem = Vars.schematics.create(
            cx - SCHEMATIC_COPY_RADIUS, cy - SCHEMATIC_COPY_RADIUS,
            cx + SCHEMATIC_COPY_RADIUS, cy + SCHEMATIC_COPY_RADIUS);

        if(schem.tiles.size == 0){
            System.err.println("DBG 复制选区为空，未保存蓝图");
            return;
        }
        Vars.schematics.add(schem);
        rebuildSchematicList();
        System.err.println("DBG 已保存蓝图 " + schem.name() + " tiles=" + schem.tiles.size);
    }

    /** 重建蓝图按钮列表（点一下进入/退出粘贴模式）。 */
    private void rebuildSchematicList(){
        if(schematicList == null || Vars.schematics == null) return;
        schematicList.clearChildren();
        //记下当前数量，act() 靠它判断"有没有新蓝图需要重建列表"
        schematicCount = Vars.schematics.all().size;

        if(Vars.schematics.all().size == 0){
            schematicList.add(new Label("无蓝图", Styles.defaultLabel)).pad(Scl.scl(4f));
            return;
        }

        for(int i = 0; i < Vars.schematics.all().size; i++){
            com.phoenix.game.game.Schematic schem = Vars.schematics.all().get(i);

            //缩略图：懒生成（首次显示时才渲染到 FBO），GL 未就绪时退化成纯文字按钮
            com.badlogic.gdx.graphics.Texture preview = Vars.schematics.getPreview(schem);

            Table cell = new Table();
            cell.setBackground(Styles.black6);

            if(preview != null){
                Image image = new Image(new TextureRegionDrawable(new TextureRegion(preview)));
                cell.add(image).size(Scl.scl(SCHEMATIC_PREVIEW_SIZE)).pad(Scl.scl(2f)).row();
            }

            Label nameLabel = new Label(schem.name(), Styles.defaultLabel);
            nameLabel.setEllipsis(true);
            cell.add(nameLabel).width(Scl.scl(SCHEMATIC_PREVIEW_SIZE)).pad(Scl.scl(2f)).row();

            cell.addListener(new ClickListener(){
                @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                    InputHandler input = input();
                    if(input == null) return;
                    //再点同一个就取消粘贴
                    input.pasteSchematic = input.pasteSchematic == schem ? null : schem;
                    if(input.pasteSchematic != null){
                        input.buildBlock = null;
                        input.breaking = false;
                    }
                }
            });

            schematicList.add(cell).pad(Scl.scl(2f));
        }
    }

    /** 按当前分类重建 4 列方块网格。 */
    private void rebuildGrid(){
        if(grid == null) return;
        grid.clearChildren();
        blocks.clear();
        buttons.clear();

        Array<Block> list = blocksOf(currentCategory);
        for(int i = 0; i < list.size; i++){
            Block block = list.get(i);
            blocks.add(block);

            ImageButton button = new ImageButton(new ImageButton.ImageButtonStyle(Styles.selecti));
            setIcon(button, block);
            button.addListener(new ClickListener(){
                @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                    InputHandler input = input();
                    if(input != null){
                        input.buildBlock = input.buildBlock == block ? null : block;
                        input.breaking = false;
                        //选了方块就退出蓝图粘贴模式（两者互斥）
                        input.pasteSchematic = null;
                    }
                }
            });
            buttons.add(button);

            if(i % ROW_WIDTH == 0) grid.row();
            grid.add(button).size(Scl.scl(SLOT_SIZE)).pad(Scl.scl(2f));
        }

        //补齐空位使每行等宽（对应原版 blockTable.add().size(46f)）
        int remainder = list.size % ROW_WIDTH;
        if(remainder != 0){
            for(int i = 0; i < ROW_WIDTH - remainder; i++){
                grid.add().size(Scl.scl(SLOT_SIZE));
            }
        }
    }

    /**
     * 按层叠加绘制方块图标（对应原版把 generateIcons 合成后的 block-*-full）。
     * <p>原版在打包期把 base/rotator/top 压成一张图；这里用 Stack 逐层叠加，视觉等价。
     */
    private void setIcon(ImageButton button, Block block){
        Stack icon = new Stack();
        TextureRegion[] layers = block.getGeneratedIcons();
        for(TextureRegion layer : layers){
            if(layer == null) continue;
            Image image = new Image(layer);
            image.setScaling(com.badlogic.gdx.utils.Scaling.fit);
            icon.add(image);
        }
        button.add(icon).size(Scl.scl(SLOT_SIZE * 0.85f));
    }

    private InputHandler input(){
        return Vars.control == null ? null : Vars.control.input;
    }

    /** 每帧刷新：可见性、按钮选中/材料色、分类选中态、详情区。 */
    private void act(){
        if(root == null) return;
        root.setVisible(shown && Vars.state != null && !Vars.state.isMenu());

        for(int i = 0; i < buttons.size; i++){
            ImageButton button = buttons.get(i);
            Block block = blocks.get(i);
            button.setChecked(input() != null && input().buildBlock == block);
            //材料不足时灰显（对应原版 gray）
            button.setColor(Build.canAfford(Team.sharded, block) ? Color.WHITE : Color.GRAY);
        }

        //蓝图数量变了（新存了一张 / 重新扫描目录）就重建列表，否则新存的蓝图要重启才看得到
        if(Vars.schematics != null && Vars.schematics.all().size != schematicCount){
            schematicCount = Vars.schematics.all().size;
            rebuildSchematicList();
        }

        updateCategoryChecked();
        updateDetail();
        updateCommandMode();
    }

    /** 命令面板骨架：标题 + 动态内容区（对应原版 commandTable 的构建）。 */
    private Table buildCommandTable(){
        Table table = new Table();
        table.top().left().pad(Scl.scl(5f));
        table.add(new Label("RTS 命令模式", Styles.defaultLabel)).left().padBottom(Scl.scl(4f)).row();
        commandList = new Table();
        table.add(commandList).growX().row();
        return table;
    }

    /**
     * 命令模式切换与面板刷新（对应原版 PlacementFragment 的 mainStack.update + u.update()）：
     * commandMode 时隐藏建造网格、显示命令面板；选择变化时重建列表。
     */
    private void updateCommandMode(){
        InputHandler input = input();
        boolean cmd = input != null && input.commandMode;

        mainContent.setVisible(!cmd);
        commandTable.setVisible(cmd);

        if(!cmd){
            lastCommandSig = "";
            return;
        }

        if(input.selectedUnits.size == 0){
            //无选中单位：只显示提示（对应原版 commandmode.nounits）
            if(!lastCommandSig.equals("-")){
                rebuildCommandList(null, null);
                lastCommandSig = "-";
            }
            return;
        }

        //选择签名：各类型数量 + 姿态位（变了才重建，避免每帧分配 Actor）
        java.util.LinkedHashMap<com.phoenix.game.type.UnitType, int[]> counts = new java.util.LinkedHashMap<>();
        StringBuilder sig = new StringBuilder();
        for(int i = 0; i < input.selectedUnits.size; i++){
            com.phoenix.game.entities.type.BaseUnit unit = input.selectedUnits.get(i);
            int[] count = counts.get(unit.getType());
            if(count == null){
                count = new int[1];
                counts.put(unit.getType(), count);
            }
            count[0]++;
            if(unit.commandAI != null) sig.append(unit.commandAI.stances.toString()).append(';');
        }
        for(java.util.Map.Entry<com.phoenix.game.type.UnitType, int[]> e : counts.entrySet()){
            sig.append(e.getKey().name).append(':').append(e.getValue()[0]).append('|');
        }

        if(!sig.toString().equals(lastCommandSig)){
            rebuildCommandList(input, counts);
            lastCommandSig = sig.toString();
        }
    }

    /** 重建命令面板内容：选中单位图标行 + 命令/姿态按钮行。 */
    private void rebuildCommandList(InputHandler input, java.util.Map<com.phoenix.game.type.UnitType, int[]> counts){
        commandList.clearChildren();
        commandList.top().left();

        if(input == null || counts == null){
            commandList.add(new Label("选中单位后右键下达命令", Styles.defaultLabel)).left().pad(Scl.scl(4f)).row();
            return;
        }

        //单位类型图标行：左键把该类型移出选择，右键只保留该类型（对应原版 rebuildCommand）
        Table unitsRow = new Table();
        int shown = 0;
        for(java.util.Map.Entry<com.phoenix.game.type.UnitType, int[]> e : counts.entrySet()){
            com.phoenix.game.type.UnitType type = e.getKey();
            if(type.icon(com.phoenix.game.ui.Cicon.medium) == null) continue;

            Table cell = new Table();
            ImageButton.ImageButtonStyle iconStyle = new ImageButton.ImageButtonStyle(Styles.selecti);
            iconStyle.imageUp = new TextureRegionDrawable(type.icon(com.phoenix.game.ui.Cicon.medium));
            ImageButton iconBtn = new ImageButton(iconStyle);
            com.phoenix.game.type.UnitType fType = type;
            iconBtn.addListener(new ClickListener(){
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                    if(event.getButton() == com.badlogic.gdx.Input.Buttons.RIGHT){
                        //右键：只保留该类型
                        for(int i = input.selectedUnits.size - 1; i >= 0; i--){
                            if(input.selectedUnits.get(i).getType() != fType){
                                input.selectedUnits.removeIndex(i);
                            }
                        }
                    }else{
                        //左键：把该类型移出选择
                        for(int i = input.selectedUnits.size - 1; i >= 0; i--){
                            if(input.selectedUnits.get(i).getType() == fType){
                                input.selectedUnits.removeIndex(i);
                            }
                        }
                    }
                }
            });
            cell.add(iconBtn).size(Scl.scl(40f)).row();
            cell.add(new Label("×" + e.getValue()[0], Styles.defaultLabel)).center();
            unitsRow.add(cell).pad(Scl.scl(2f));
            shown++;
        }
        if(shown > 0){
            commandList.add(unitsRow).left().row();
        }

        //命令按钮行（对应原版命令按钮 + 姿态按钮）
        Table cmdRow = new Table();

        //移动命令
        addCommandButton(cmdRow, com.phoenix.game.ai.UnitCommand.moveCommand.getIcon(), "移动",
            allSelectedCommand(input, com.phoenix.game.ai.UnitCommand.moveCommand),
            () -> input.setUnitCommand(com.phoenix.game.ai.UnitCommand.moveCommand));

        //取消命令（对应原版 UnitStance.stop → clearCommands）
        addStanceButton(cmdRow, input, com.phoenix.game.ai.UnitStance.stop);
        //停火 / 追击目标
        addStanceButton(cmdRow, input, com.phoenix.game.ai.UnitStance.holdFire);
        addStanceButton(cmdRow, input, com.phoenix.game.ai.UnitStance.pursueTarget);

        commandList.add(cmdRow).left().pad(Scl.scl(4f)).row();

        commandList.add(new Label("左键框选 · 右键移动/攻击 · 中键追加", Styles.defaultLabel))
            .left().pad(Scl.scl(4f)).row();
    }

    /** @return 是否所有选中单位的当前命令都是该命令。 */
    private boolean allSelectedCommand(InputHandler input, com.phoenix.game.ai.UnitCommand command){
        for(int i = 0; i < input.selectedUnits.size; i++){
            com.phoenix.game.entities.type.BaseUnit unit = input.selectedUnits.get(i);
            if(unit.commandAI == null || unit.commandAI.currentCommand() != command){
                return false;
            }
        }
        return true;
    }

    /** 命令按钮：图标 + 提示文字。 */
    private void addCommandButton(Table row, com.badlogic.gdx.scenes.scene2d.utils.Drawable icon,
                                  String name, boolean checked, Runnable action){
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle(Styles.clearTogglei);
        style.imageUp = icon;
        ImageButton button = new ImageButton(style);
        button.setChecked(checked);
        button.addListener(new ClickListener(){
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                action.run();
            }
        });
        row.add(button).size(Scl.scl(40f)).pad(Scl.scl(2f));
    }

    /** 姿态按钮（toggle；stop = 取消全部命令）。 */
    private void addStanceButton(Table row, InputHandler input, com.phoenix.game.ai.UnitStance stance){
        boolean checked = stance != com.phoenix.game.ai.UnitStance.stop && anySelectedStance(input, stance);
        addCommandButton(row, stance.getIcon(), stance.localized(), checked, () -> input.setUnitStance(stance));
    }

    /** @return 是否有选中单位开启该姿态。 */
    private boolean anySelectedStance(InputHandler input, com.phoenix.game.ai.UnitStance stance){
        for(int i = 0; i < input.selectedUnits.size; i++){
            com.phoenix.game.entities.type.BaseUnit unit = input.selectedUnits.get(i);
            if(unit.commandAI != null && unit.commandAI.hasStance(stance)){
                return true;
            }
        }
        return false;
    }

    /**
     * 刷新详情区。优先级与原版 getSelected() 一致：选中配方 > 悬停的建造菜单按钮 > 悬停的世界建筑。
     * <p>面板每帧重建代价高，这里用 lastDisplay/lastHover/lastGround 做变化检测。
     */
    private void updateDetail(){
        InputHandler input = input();
        Block selected = input == null ? null : input.buildBlock;

        Tile hover = hoveredTile();
        Block groundBlock = hover == null ? null : hover.block();
        if(groundBlock == Blocks.air) groundBlock = null;

        //只在“显示内容身份”变化时重建；条内数值由 Bar 的 Supplier 每帧自行刷新，
        //重建会丢失条的插值动画，也会持续分配 Actor
        boolean sameSelection = selected == lastDisplay
            && groundBlock == lastGroundBlock
            && (groundBlock == null || hover == lastHover);

        lastDisplay = selected;
        lastGroundBlock = groundBlock;
        lastHover = hover;

        if(sameSelection) return;

        detail.clearChildren();
        if(selected != null){
            setDetailVisible(true);
            addRecipeDetail(selected);
        }else if(groundBlock != null){
            setDetailVisible(true);
            addTileDetail(groundBlock, hover);
        }else{
            //未选中配方且未悬停建筑：整块塌陷（对应原版 visible 条件）
            setDetailVisible(false);
        }
    }

    /**
     * 收放详情区。隐藏时把高度压到 0，避免面板顶部残留空白
     * （原版靠 visible(false) 让 Table 塌陷，libgdx 需显式改 Cell 高度）。
     */
    private void setDetailVisible(boolean visible){
        detailRoot.setVisible(visible);
        if(detailCell != null){
            detailCell.height(visible ? Scl.scl(DETAIL_HEIGHT) : 0f);
            detailCell.padTop(visible ? Scl.scl(5f) : 0f);
        }
    }

    /** 显示选中配方：图标 + 名称 + 快捷键 + 材料需求行（对应原版 topTable 的 lastDisplay 分支）。 */
    private void addRecipeDetail(Block block){
        Table header = new Table();
        header.left();
        header.add(new Image(block.icon(Cicon.medium))).size(Scl.scl(DETAIL_ICON_SIZE));
        Label nameLabel = new Label(block.localizedName() + keyHint(block), Styles.outlineLabel);
        nameLabel.setWrap(true);
        header.add(nameLabel).left().width(Scl.scl(190f)).padLeft(Scl.scl(5f));
        header.add().growX();

        Button info = new Button(Styles.clearPartialt);
        info.add(new Label("?", Styles.defaultLabel)).pad(Scl.scl(2f));
        header.add(info).size(Scl.scl(DETAIL_ICON_SIZE + 8f)).right();
        detail.add(header).growX().left().row();

        //材料需求：图标 + 名称 + 持有量/需求量（对应原版 requirements 表）
        for(com.phoenix.game.type.ItemStack stack : block.requirements){
            Table line = new Table();
            line.left();
            TextureRegion itemIcon = itemIcon(stack.item);
            if(itemIcon != null) line.add(new Image(itemIcon)).size(Scl.scl(16f)).padLeft(Scl.scl(2f));
            Label itemLabel = new Label(itemName(stack.item), Styles.defaultLabel);
            itemLabel.setColor(Color.LIGHT_GRAY);
            line.add(itemLabel).left().padLeft(Scl.scl(2f)).width(Scl.scl(80f));
            line.add(new Label(requirementText(stack), Styles.defaultLabel)).left().padLeft(Scl.scl(5f));
            detail.add(line).growX().left().row();
        }

        //钻头类额外显示采集速度（对应原版 BlockStat.drillSpeed 的 items/s）
        String drillRate = drillRateText(block);
        if(!drillRate.isEmpty()){
            detail.add(new Label(drillRate, Styles.outlineLabel)).left().padTop(Scl.scl(2f)).row();
        }
    }

    /** 显示悬停建筑：图标 + 名称，然后是该建筑的运行状态条（对应原版 getDisplayIcon + display）。 */
    private void addTileDetail(Block block, Tile tile){
        Table header = new Table();
        header.left();
        header.add(new Image(block.getDisplayIcon(tile))).size(Scl.scl(DETAIL_ICON_SIZE));
        Label nameLabel = new Label(block.getDisplayName(tile), Styles.outlineLabel);
        nameLabel.setWrap(true);
        header.add(nameLabel).left().width(Scl.scl(190f)).padLeft(Scl.scl(5f));
        detail.add(header).growX().left().row();

        if(tile.entity != null && tile.getTeam() == Team.sharded){
            Table bars = new Table();
            bars.left();
            bars.defaults().growX();
            block.displayBars(tile, bars);
            detail.add(bars).growX().left().height(Scl.scl(BARS_MAX_HEIGHT)).maxHeight(Scl.scl(BARS_MAX_HEIGHT)).row();
        }
    }

    /** @return 材料持有量/需求量文本；材料充足为白色，不足为红色（对应原版需求行着色）。 */
    private String requirementText(com.phoenix.game.type.ItemStack stack){
        com.phoenix.game.world.modules.ItemModule inv = Build.items(Team.sharded);
        int amount = inv == null ? 0 : inv.get(stack.item);
        String color = amount < stack.amount / 2f ? "[red]" : amount < stack.amount ? "[accent]" : "[white]";
        return color + amount + "[]/" + stack.amount;
    }

    /** @return 物品图标区域。 */
    private TextureRegion itemIcon(com.phoenix.game.type.Item item){
        if(Core.atlas == null || item == null) return null;
        TextureRegion region = Core.atlas.findRegion("item-" + item.name);
        return region != null ? region : Core.atlas.findRegion("white");
    }

    private String itemName(com.phoenix.game.type.Item item){
        if(item == null || Core.bundle == null) return "";
        String key = "item." + item.name + ".name";
        String value = Core.bundle.get(key);
        //I18NBundle 缺键返回 "???key???"（不是键本身），两种形态都视为缺失
        return (value.equals(key) || value.equals("???" + key + "???")) ? item.name : value;
    }

    /** @return 钻头每秒采集量文本；非钻头返回空串。 */
    private String drillRateText(Block block){
        if(!(block instanceof Drill)) return "";
        Drill drill = (Drill)block;
        if(drill.drillTime <= 0f) return "";
        //对应原版 stats.add(BlockStat.drillSpeed, 60f / drillTime * size * size)
        float rate = 60f / drill.drillTime * drill.size * drill.size;
        return String.format("挖掘速度：%.2f/秒", rate);
    }

    /** 生成原版 PlacementFragment 风格数字快捷键提示。 */
    private String keyHint(Block block){
        for(int i = 0; i < blocks.size; i++){
            if(blocks.get(i) == block){
                return " [" + (i / 10 + 1) + "," + (i % 10) + "]";
            }
        }
        return "";
    }

    /** @return 该分类下可建造的方块（排除空气）。 */
    private Array<Block> blocksOf(Category category){
        Array<Block> result = new Array<>();
        for(int i = 0; i < Blocks.all.size; i++){
            Block block = Blocks.all.get(i);
            if(block != Blocks.air && block.category == category && buildable(block)) result.add(block);
        }
        return result;
    }

    /**
     * @return 是否出现在建造菜单里。
     * <p>地板与自然岩壁（{@code Rock}/{@code StaticWall}）不可建造。
     * 原版靠 {@code buildVisibility} + 解锁状态过滤（{@code PlacementFragment:424}），phoenix 两者都没有，按类型过滤。
     */
    private boolean buildable(Block block){
        if(block instanceof com.phoenix.game.world.Floor || block instanceof com.phoenix.game.world.blocks.Rock){
            return false;
        }
        //沙盒方块只在沙盒模式下列出（对应原版 BuildVisibility.sandboxOnly）
        if(block.sandboxOnly && !Vars.state.rules.sandbox){
            return false;
        }
        return true;
    }

    private Tile hoveredTile(){
        if(input() == null || Vars.world == null || Core.camera == null) return null;
        float x = input().mouseWorldX(Gdx.input.getX());
        float y = input().mouseWorldY(Gdx.input.getY());
        return Vars.world.tileWorld(x, y);
    }

    public void setShown(boolean value){ shown = value; }
    public boolean isShown(){ return shown; }
    public void show(){ shown = true; }
    public void hide(){ shown = false; }
}
