package com.phoenix.game.world.blocks.sandbox;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.util.ArcAnnotate.*;
import com.phoenix.game.util.Eachable;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.BuildRequest;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.content;

/**
 * 液体源（沙盒）。参照 Mindustry mindustry.world.blocks.sandbox.LiquidSource 移植。
 * <p>配置一种液体后，每帧把自身储量填满并尝试输出给邻居（邻居收不下就丢掉，不会堆积）。
 * <p>刻意偏离：
 * <ul>
 *     <li>原版逻辑写在 {@code Block.update(Tile)} 里，本项目统一写在实体内部类（见 {@code Drill}/{@code GenericCrafter}）；</li>
 *     <li>配置界面原版用 {@code ItemSelection.buildTable(table, content.liquids(), ...)}，
 *         {@code ItemSelection} 已存在但要求 {@code T extends UnlockableContent}，而本项目 {@code Liquid}
 *         继承 {@code MappableContent}（见 {@code 转换.md} 继承链结论）→ 配置界面待液体批次补；</li>
 *     <li>{@code Draw.rect("center", x, y)}（按区域名取图）在 libgdx 没有，改为先查图集再画；</li>
 *     <li>{@code setBars()} 里 {@code bars.remove("liquid")} → 本项目改用 {@code Block.displayLiquidBar = false}
 *         （效果等价：沙盒液体源不显示液体条）；{@code drawRequestConfig(...)} 依赖
 *         {@code Block.drawRequestConfigCenter}（未移植）→ 刻意降级。</li>
 * </ul>
 */
public class LiquidSource extends Block{
    public static Liquid lastLiquid;

    public LiquidSource(String name){
        super(name);
        update = true;
        solid = true;
        hasLiquids = true;
        liquidCapacity = 100f;
        configurable = true;
        outputsLiquid = true;
        //对应原版 setBars() 里 bars.remove("liquid")：沙盒液体源不显示液体条
        displayLiquidBar = false;
        entityType = LiquidSourceEntity::new;
    }

    @Override
    public void playerPlaced(Tile tile){
        if(lastLiquid != null){
            //libgdx 的 Application 是 postRunnable(Runnable)（arc 叫 post）
            Core.app.postRunnable(() -> tile.configure(lastLiquid.id));
        }
    }

    //刻意降级：原版 drawRequestConfig(BuildRequest, Eachable<BuildRequest>) 用 drawRequestConfigCenter
    //在建造预览上画液体图标；本项目 Block 没有 drawRequestConfigCenter 钩子，未移植

    @Override
    public void draw(Tile tile){
        super.draw(tile);

        LiquidSourceEntity entity = (LiquidSourceEntity)tile.entity;

        if(entity.source != null){
            Draw.color(entity.source.color);
            //libgdx 没有"按区域名画图"的 Draw.rect(String, x, y)，先查图集
            com.badlogic.gdx.graphics.g2d.TextureRegion center = Core.atlas == null ? null : Core.atlas.findRegion("center");
            if(center != null) Draw.rect(center, tile.worldx(), tile.worldy());
            Draw.color();
        }
    }

    @Override
    public void buildConfiguration(Tile tile, Table table){
        //刻意降级：原版 ItemSelection.buildTable(table, content.liquids(), () -> entity.source, liquid -> {...})；
        //ItemSelection 已存在，但其泛型约束 T extends UnlockableContent 不满足（本项目 Liquid 继承 MappableContent），
        //配置界面待液体批次补
    }

    @Override
    public void configured(Tile tile, Player player, int value){
        if(tile.entity instanceof LiquidSourceEntity){
            //Vars.content.liquid(value) 已存在
            ((LiquidSourceEntity)tile.entity).source = value == -1 ? null : content.liquid(value);
        }
    }

    public class LiquidSourceEntity extends TileEntity{
        public @Nullable Liquid source = null;

        @Override
        public void update(){
            if(source == null){
                liquids.clear();
            }else{
                //注意：与原版一致，这里每帧无条件加 liquidCapacity（模块不按容量截断），
                //     总量会超过 capacity（沙盒方块，只求"无限液体"效果）
                liquids.add(source, liquidCapacity);
                tryDumpLiquid(tile, source);
            }
        }

        @Override
        public int config(){
            return source == null ? -1 : source.id;
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeByte(source == null ? -1 : source.id);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            byte id = in.readByte();
            //Vars.content.liquid(id) 已存在
            source = id == -1 ? null : content.liquid(id);
        }
    }
}
