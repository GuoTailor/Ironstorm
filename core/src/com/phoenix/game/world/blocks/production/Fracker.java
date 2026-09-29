package com.phoenix.game.world.blocks.production;

import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.StatUnit;

/**
 * 压裂机（原版方块名 {@code oil-extractor}）。参照 Mindustry
 * mindustry.world.blocks.production.Fracker 移植。
 * <p>在 {@link SolidPump} 的基础上多了一路**物品消耗**：每 {@link #itemUseTime} 帧扣一次物品
 * （如沙），扣料期间照常产液，所以断料就停机（{@code cons.valid()} 不通过 → 只把积压的液体往外送）。
 *
 * <p>与原版的差异：
 * <ul>
 *     <li>原版把 {@code update(Tile)} 写在方块上；本项目写在实体 {@link FrackerEntity#update()}，
 *         其中"扣物品 → 跑父类产液逻辑 → 累加计时"的顺序照抄原版（父类逻辑用 {@code super.update()} 调用）。</li>
 *     <li>原版 Fracker **重新声明**了 {@code liquidRegion}/{@code rotatorRegion}/{@code topRegion} 三个字段
 *         （字段隐藏）；本项目直接复用父类已声明的同名贴图字段（{@code liquidRegion}/{@code topRegion}
 *         来自 {@code LiquidBlock}，{@code rotatorRegion} 来自 {@link SolidPump}），不再重复声明。</li>
 *     <li>{@code drawCracks(Tile)}（原版空覆写以屏蔽裂纹绘制）：本项目 {@code Block} 没有裂纹绘制，
 *         未移植。</li>
 *     <li>{@code shouldConsume(Tile)}（储液罐快满时不再耗物品）：本项目 {@code Block.shouldConsume} 钩子
 *         本批已补齐，本类已按原版补上覆写（见下方 {@link #shouldConsume}）。</li>
 *     <li>{@code generateIcons()} 与父类 {@code SolidPump} 完全一致，故不再覆写。</li>
 * </ul>
 */
public class Fracker extends SolidPump{
    /** 消耗一个物品所需帧数。 */
    public float itemUseTime = 100f;

    public Fracker(String name){
        super(name);
        hasItems = true;
        entityType = FrackerEntity::new;
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(BlockStat.productionTime, itemUseTime / 60f, StatUnit.seconds);
    }

    @Override
    public void load(){
        super.load();
        if(Core.atlas == null) return;

        rotatorRegion = Core.atlas.findRegion(name + "-rotator");
        topRegion = Core.atlas.findRegion(name + "-top");
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    /**
     * 空覆写以屏蔽基类的裂纹绘制（对应原版 {@code Fracker.drawCracks(Tile){}}）：
     * 裂纹由 {@link #draw(Tile)} 自己调 {@code super.drawCracks(tile)}，夹在本体与液体条之间。
     */
    @Override
    public void drawCracks(Tile tile){
    }

    /**
     * 绘制本体 + 液体存量 + 旋转头/上层。
     * <p>⚠ <b>坐标口径修正</b>：原版用 {@code tile.drawx()/drawy()}（原版口径下即方块中心）；
     * 本项目 {@code Tile.drawx()} 少半格，而 {@code Draw.rect} 是中心语义 → 改用
     * {@code Block.centerX/centerY(tile)}（= {@code drawx() + tilesize/2f}），否则整体偏左下 4。
     */
    @Override
    public void draw(Tile tile){
        FrackerEntity entity = tile.entity instanceof FrackerEntity ? (FrackerEntity)tile.entity : null;

        if(region != null) Draw.rect(region, centerX(tile), centerY(tile));
        //裂纹画在本体之上（对应原版此处调 super.drawCracks(tile)）
        super.drawCracks(tile);

        if(entity != null && entity.liquids != null){
            Draw.color(result.color);
            Draw.alpha(entity.liquids.get(result) / liquidCapacity);
            if(liquidRegion != null) Draw.rect(liquidRegion, centerX(tile), centerY(tile));
            Draw.color();
        }

        if(entity != null){
            if(rotatorRegion != null) Draw.rect(rotatorRegion, centerX(tile), centerY(tile), entity.pumpTime);
            if(topRegion != null) Draw.rect(topRegion, centerX(tile), centerY(tile));
        }
    }

    /**
     * @return 储液罐没满才消耗输入物品（对应原版 {@code Fracker.shouldConsume}）。
     * <p>{@code Block.shouldConsume} 钩子本批已补齐，{@code ConsumeModule} 会调用它：
     * 罐满时不再扣物品、{@code cons.valid()} 转 false → 实体走 {@code tryDumpLiquid} 排液。
     */
    @Override
    public boolean shouldConsume(Tile tile){
        return tile.entity.liquids.get(result) < liquidCapacity - 0.01f;
    }

    /** @return 当前库存量（原版 Fracker 只统计目标液体）。 */
    @Override
    public float typeLiquid(Tile tile){
        return tile.entity == null || tile.entity.liquids == null ? 0f : tile.entity.liquids.get(result);
    }

    /** 压裂机实体（原版 {@code FrackerEntity}）。 */
    public class FrackerEntity extends SolidPumpEntity{
        /** 距下次扣物品的累计时间（帧）。 */
        public float accumulator;

        /** 对应原版 {@code Fracker.update(Tile)}（本项目统一写在实体里）。 */
        @Override
        public void update(){
            if(cons.valid()){
                if(accumulator >= itemUseTime){
                    cons.trigger();
                    accumulator -= itemUseTime;
                }

                //父类 = SolidPump 的产液逻辑
                super.update();
                accumulator += delta() * efficiency();
            }else{
                tryDumpLiquid(tile, result);
            }
        }
    }
}
