package com.phoenix.game.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Scaling;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Strings;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.ItemType;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.type.Mech;
import com.phoenix.game.type.UnitType;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.blocks.production.Drill;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Tex;

/**
 * 内容信息对话框的内容渲染，参照 Mindustry mindustry.ui.ContentDisplay。
 * <p>结构：标题（图标 + 名称）→ 分隔线 → 描述 → 分隔线 → 统计项。
 * 方块的统计项由 {@link Block#stats} 提供（分类标题、项名、值的具体渲染见
 * {@code world.meta.BlockStats.display}）；物品/液体/单位/机甲按原版是固定几行属性。
 * <p>原版用 {@code Core.bundle.format(...)} 直接产出带 {@code [lightgray]} 标记的一整行文本；
 * 本项目 bundle 是裁剪过的（缺键返回 ???key???）→ 统一走 {@link I18n#format}，fallback 硬编码
 * 原版 bundle.properties 里的英文原文（项目 bundle 缺哪些键见各方法内注释）。
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

    /** 显示物品信息（对应原版 {@code ContentDisplay.displayItem}，原版 ContentDisplay.java:65-97）。 */
    public static void displayItem(Table table, Item item){
        table.top().left();

        Table title = new Table();
        title.left();
        title.add(new Image(iconOrBlank(item.icon(Cicon.xlarge)))).size(Scl.scl(48f));
        Label name = new Label(item.localizedName(), Styles.defaultLabel);
        name.setColor(Pal.accent);
        title.add(name).padLeft(Scl.scl(5f));
        table.add(title).left().row();

        dividerRow(table, 15f);

        addDescription(table, item);

        table.left();

        //item.corestorable 键在本项目 bundle 缺失（原版 bundle.properties:844）→ fallback 用原文
        //对应原版：Core.bundle.format("item.corestorable", item.type == ItemType.material ? "$yes" : "$no")
        table.add(new Label(I18n.format("item.corestorable", "[lightgray]Storable in Core: {0}",
            item.type == ItemType.material ? I18n.get("yes", "Yes") : I18n.get("no", "No")), Styles.defaultLabel)).left().row();
        statLine(table, "item.explosiveness", "[lightgray]Explosiveness: {0}%", (int)(item.explosiveness * 100));
        statLine(table, "item.flammability", "[lightgray]Flammability: {0}%", (int)(item.flammability * 100));
        statLine(table, "item.radioactivity", "[lightgray]Radioactivity: {0}%", (int)(item.radioactivity * 100));
    }

    /** 显示液体信息（对应原版 {@code ContentDisplay.displayLiquid}，原版 ContentDisplay.java:99-132）。 */
    public static void displayLiquid(Table table, Liquid liquid){
        table.top().left();

        Table title = new Table();
        title.left();
        title.add(new Image(iconOrBlank(liquid.icon(Cicon.xlarge)))).size(Scl.scl(48f));
        Label name = new Label(liquid.localizedName, Styles.defaultLabel);
        name.setColor(Pal.accent);
        title.add(name).padLeft(Scl.scl(5f));
        table.add(title).left().row();

        dividerRow(table, 15f);

        addDescription(table, liquid);

        table.left();

        statLine(table, "item.explosiveness", "[lightgray]Explosiveness: {0}%", (int)(liquid.explosiveness * 100));
        statLine(table, "item.flammability", "[lightgray]Flammability: {0}%", (int)(liquid.flammability * 100));
        statLine(table, "liquid.heatcapacity", "[lightgray]Heat Capacity: {0}", (int)(liquid.heatCapacity * 100));
        statLine(table, "liquid.temperature", "[lightgray]Temperature: {0}", (int)(liquid.temperature * 100));
        statLine(table, "liquid.viscosity", "[lightgray]Viscosity: {0}", (int)(liquid.viscosity * 100));
    }

    /** 显示单位信息（对应原版 {@code ContentDisplay.displayUnit}，原版 ContentDisplay.java:182-209）。 */
    public static void displayUnit(Table table, UnitType unit){
        table.top().left();

        Table title = new Table();
        title.left();
        title.add(new Image(iconOrBlank(unit.icon(Cicon.xlarge)))).size(Scl.scl(48f));
        Label name = new Label(unit.localizedName, Styles.defaultLabel);
        name.setColor(Pal.accent);
        title.add(name).padLeft(Scl.scl(5f));
        table.add(title).left().row();

        dividerRow(table, 15f);

        addDescription(table, unit);

        table.left();

        statLine(table, "unit.health", "[lightgray]Health: {0}", (int)unit.health);
        statLine(table, "unit.speed", "[lightgray]Speed: {0}", Strings.fixed(unit.speed, 1));
    }

    /** 显示机甲信息（对应原版 {@code ContentDisplay.displayMech}，原版 ContentDisplay.java:134-180）。 */
    public static void displayMech(Table table, Mech mech){
        table.top().left();

        Table title = new Table();
        title.left();
        title.add(new Image(iconOrBlank(mech.icon(Cicon.xlarge)))).size(Scl.scl(48f));
        Label name = new Label(mech.localizedName, Styles.defaultLabel);
        name.setColor(Pal.accent);
        title.add(name).padLeft(Scl.scl(5f));
        table.add(title).left().row();

        dividerRow(table, 15f);

        addDescription(table, mech);

        table.left();

        //原版：Core.bundle.has("mech.<name>.weapon") 才显示该行；I18n.get(key, null) 判缺失
        String weapon = I18n.get("mech." + mech.name + ".weapon", null);
        if(weapon != null){
            table.add(new Label(I18n.format("mech.weapon", "[lightgray]Weapon: {0}", weapon), Styles.defaultLabel)).left().row();
        }
        String ability = I18n.get("mech." + mech.name + ".ability", null);
        if(ability != null){
            table.add(new Label(I18n.format("mech.ability", "[lightgray]Ability: {0}", ability), Styles.defaultLabel)).left().row();
        }

        statLine(table, "mech.buildspeed", "[lightgray]Building Speed: {0}%", (int)(mech.buildPower * 100f));
        statLine(table, "mech.health", "[lightgray]Health: {0}", (int)mech.health);
        statLine(table, "mech.itemcapacity", "[lightgray]Item Capacity: {0}", mech.itemCapacity);

        if(mech.drillPower > 0){
            statLine(table, "mech.minespeed", "[lightgray]Mining Speed: {0}%", (int)(mech.mineSpeed * 100f));
            statLine(table, "mech.minepower", "[lightgray]Mining Power: {0}", mech.drillPower);
        }
    }

    /** @return 找不到时的兜底图标（对应本项目 {@code DatabaseDialog} 的 blank 约定）；两个都缺才返回 null。 */
    private static TextureRegion iconOrBlank(TextureRegion region){
        if(region == null && Core.atlas != null) return Core.atlas.findRegion("blank");
        return region;
    }

    /** 描述段落（有描述才画：文本 + 分隔线，对应原版 {@code if(x.description != null)} 分支）。 */
    private static void addDescription(Table table, com.phoenix.game.ctype.UnlockableContent content){
        String description = content.displayDescription();
        if(!description.isEmpty()){
            Label desc = new Label(description, Styles.defaultLabel);
            desc.setWrap(true);
            table.add(desc).width(Scl.scl(400f)).padLeft(Scl.scl(5f)).padRight(Scl.scl(5f)).fillX().left().row();

            dividerRow(table, 15f);
        }
    }

    /** 液体版的描述段落（Liquid 不是 UnlockableContent，只有可能为 null 的 description 字段）。 */
    private static void addDescription(Table table, Liquid liquid){
        if(liquid.description == null) return;
        Label desc = new Label(liquid.description, Styles.defaultLabel);
        desc.setWrap(true);
        table.add(desc).width(Scl.scl(400f)).padLeft(Scl.scl(5f)).padRight(Scl.scl(5f)).fillX().left().row();

        dividerRow(table, 15f);
    }

    /** 一行 "{键}: {值}" 属性文本（键缺失时用 fallback 原文；对应原版的一组 table.add(bundle.format(...)).row()）。 */
    private static void statLine(Table table, String key, String fallback, Object arg){
        table.add(new Label(I18n.format(key, fallback, arg), Styles.defaultLabel)).left().row();
    }

    /** 水平分隔线 + 换行（displayBlock 用 add(...).height(3).pad(8) 的展开形式，pad 按原版 item/liquid/unit 的 15f）。 */
    private static void dividerRow(Table table, float pad){
        table.add(divider()).height(Scl.scl(3f)).pad(Scl.scl(pad)).padLeft(0f).padRight(0f).fillX().row();
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
