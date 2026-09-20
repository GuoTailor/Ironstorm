package com.phoenix.game.ui.fragments;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Items;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

/**
 * 建筑配置面板。参照 Mindustry 里 {@code Block.buildConfiguration} 的弹窗用法移植。
 * <p>点击一个 {@code configurable} 的方块时，输入层把它的瓦片记到
 * {@link com.phoenix.game.input.InputHandler#configTile}，本面板据此显示该方块自己的配置表
 * （分拣器选物品、指挥中心选指令、物品源选产物……）。
 * <p>与原版的差异：原版配置界面走 {@code ui.showConfig} 的独立窗口体系，这里是一个常驻的右上角面板，
 * 切换方块时整体重建。
 * <p>未移植：数字键快捷选择、搜索框。
 */
public class BlockConfigFragment extends Fragment{
    /** 面板宽度。 */
    private static final float PANEL_WIDTH = 260f;
    /** 物品选择网格的列数。 */
    private static final int PICKER_COLUMNS = 6;
    /** 单个物品按钮尺寸。 */
    private static final float PICKER_SLOT = 34f;

    private Table root;
    private Table content;
    /** 当前显示的瓦片；变了才重建，避免每帧重建 UI。 */
    private Tile shownTile;

    @Override
    public void build(Group parent){
        root = new Table();
        root.setFillParent(true);
        root.top().right();
        root.setVisible(false);
        root.padTop(Scl.scl(70f)).padRight(Scl.scl(8f));
        parent.addActor(root);

        content = new Table();
        content.setBackground(Styles.black9);
        content.top().left();
        content.defaults().pad(Scl.scl(3f));
        root.add(content).width(Scl.scl(PANEL_WIDTH));

        root.addActor(new MenuFragment.Act(this::act));
    }

    /** 每帧同步：配置目标变了就重建面板。 */
    private void act(){
        if(Vars.control == null || Vars.control.input == null){
            root.setVisible(false);
            return;
        }

        Tile tile = Vars.control.input.configTile;
        if(tile == null || tile.entity == null){
            if(shownTile != null){
                shownTile = null;
                root.setVisible(false);
                content.clearChildren();
            }
            return;
        }

        if(tile != shownTile){
            shownTile = tile;
            rebuild(tile);
        }
        root.setVisible(true);
    }

    /** 按方块的 buildConfiguration 重建内容。 */
    private void rebuild(Tile tile){
        content.clearChildren();

        Block block = tile.block();

        //标题行：方块名 + 关闭
        Table header = new Table();
        header.add(new Label(block.getDisplayName(tile), Styles.outlineLabel)).left().growX();

        Button close = new Button(Styles.defaultb);
        close.add(new Label("关闭", Styles.defaultLabel)).pad(Scl.scl(3f));
        close.addListener(new ClickListener(){
            @Override public void clicked(InputEvent event, float x, float y){
                if(Vars.control != null && Vars.control.input != null){
                    Vars.control.input.configTile = null;
                }
            }
        });
        header.add(close).right();
        content.add(header).growX().row();

        //方块自己的配置表
        Table body = new Table();
        body.top().left();
        block.buildConfiguration(tile, body);
        content.add(body).growX().row();

        content.pack();
    }

    /**
     * 物品选择表（对应原版 {@code ItemSelection.buildTable}）：网格排列全部物品，点一下选中。
     * @param table 输出到的表格
     * @param selected 当前选中的物品（可空）
     * @param onSelect 选中回调；参数为 null 表示"清除配置"
     */
    public static void itemPicker(Table table, Item selected, java.util.function.Consumer<Item> onSelect){
        Table grid = new Table();
        grid.top().left();

        //"无"按钮：清除配置（分拣器/物品源都支持未配置状态）
        grid.add(itemButton(null, selected == null, () -> onSelect.accept(null))).size(Scl.scl(PICKER_SLOT));
        for(int i = 0; i < Items.all.size; i++){
            Item item = Items.all.get(i);
            if(i % PICKER_COLUMNS == PICKER_COLUMNS - 1) grid.row();
            grid.add(itemButton(item, item == selected, () -> onSelect.accept(item))).size(Scl.scl(PICKER_SLOT));
        }

        ScrollPane pane = new ScrollPane(grid, Styles.defaultPane);
        pane.setScrollingDisabled(true, false);
        table.add(pane).width(Scl.scl(PANEL_WIDTH - 16f)).height(Scl.scl(120f)).left().row();

        if(selected != null){
            table.add(new Label(itemName(selected), Styles.defaultLabel)).left().padTop(Scl.scl(2f)).row();
        }
    }

    /** 单个物品按钮；item 为 null 时画一个"无"字。 */
    private static Button itemButton(Item item, boolean checked, Runnable onClick){
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle(Styles.selecti);
        TextureRegion region = itemIcon(item);
        if(region != null){
            style.imageUp = new TextureRegionDrawable(region);
        }

        ImageButton button = new ImageButton(style);
        button.setChecked(checked);
        button.addListener(new ClickListener(){
            @Override public void clicked(InputEvent event, float x, float y){
                onClick.run();
            }
        });

        if(item == null){
            //"无"：没有图标，塞一个占位 Image 保证按钮有尺寸
            button.getImageCell().setActor(new Image(itemIcon(null)));
            button.getImage().setColor(Color.DARK_GRAY);
        }
        return button;
    }

    /** @return 物品图标；null 表示"无"，返回白色占位。 */
    private static TextureRegion itemIcon(Item item){
        if(Core.atlas == null) return null;
        if(item == null) return Core.atlas.findRegion("white");
        TextureRegion region = Core.atlas.findRegion("item-" + item.name);
        return region != null ? region : Core.atlas.findRegion("white");
    }

    private static String itemName(Item item){
        if(item == null || Core.bundle == null) return "";
        String key = "item." + item.name + ".name";
        String value = Core.bundle.get(key);
        //I18NBundle 缺键返回 "???key???"（不是键本身），两种形态都视为缺失
        return (value.equals(key) || value.equals("???" + key + "???")) ? item.name : value;
    }
}
