package com.phoenix.game.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Scaling;
import com.phoenix.game.core.Scl;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.blocks.production.Drill;
import io.anuke.mindustry.gen.Tex;

/**
 * 内容信息对话框的内容渲染，参照 Mindustry mindustry.ui.ContentDisplay。
 * <p>结构：标题（图标 + 名称）→ 分隔线 → 描述 → 分隔线 → 按分类分组的统计项。
 * 统计项由 {@link Block#stats} 提供（分类标题、项名、值的具体渲染见
 * {@code world.meta.BlockStats.display}）。
 */
public class ContentDisplay{

    /** 显示方块信息（对应原版 {@code ContentDisplay.displayBlock}）。 */
    public static void displayBlock(Table table, Block block){
        table.top().left();

        //标题：图标 + 名称（对应原版 addImage(block.icon(Cicon.xlarge)) + "[accent]" + localizedName）
        Table title = new Table();
        title.left();
        title.add(iconStack(block.getGeneratedIcons())).size(Scl.scl(48f));
        Label name = new Label(block.localizedName(), Styles.defaultLabel);
        name.setColor(Pal.accent);
        title.add(name).padLeft(Scl.scl(5f));
        table.add(title).left().row();

        table.add(divider()).height(Scl.scl(3f)).pad(Scl.scl(8f)).padLeft(0f).padRight(0f).fillX().row();

        String description = block.displayDescription();
        if(!description.isEmpty()){
            Label desc = new Label(description, Styles.defaultLabel);
            desc.setWrap(true);
            table.add(desc).width(Scl.scl(400f)).padLeft(Scl.scl(5f)).padRight(Scl.scl(5f)).fillX().left().row();

            table.add(divider()).height(Scl.scl(3f)).pad(Scl.scl(8f)).padLeft(0f).padRight(0f).fillX().row();
        }

        //统计项：按 StatCategory 分组显示（对应原版对 block.stats.toMap() 的双层遍历）
        block.stats.display(table);
    }

    /**
     * 按层叠加方块图标（对应原版打包期合成的 {@code block-<name>-full}）。
     * <p>图集里只有 base/rotator/top 等分层贴图，直接取单张只会拿到最底层，钻头之类会缺一半。
     */
    public static Stack iconStack(TextureRegion[] layers){
        Stack icon = new Stack();
        for(TextureRegion layer : layers){
            if(layer == null) continue;
            Image image = new Image(layer);
            image.setScaling(Scaling.fit);
            icon.add(image);
        }
        return icon;
    }

    /** @return 钻头采集速度（个/秒），非钻头返回 0（对应原版 {@code BlockStat.drillSpeed}）。 */
    public static float drillSpeed(Block block){
        if(!(block instanceof Drill)) return 0f;
        Drill drill = (Drill)block;
        if(drill.drillTime <= 0f) return 0f;
        return 60f / drill.drillTime * drill.size * drill.size;
    }

    /** 内容之间的浅灰分隔线（对应原版 {@code addImage().height(3).color(Color.lightGray)}）。 */
    private static Image divider(){
        Image image = new Image(Tex.whiteui);
        image.setColor(Color.LIGHT_GRAY);
        return image;
    }
}
