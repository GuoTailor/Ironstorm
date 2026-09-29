package com.phoenix.game.world.blocks.production;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.content.Fx;
import com.phoenix.game.content.Liquids;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Strings;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.struct.Array;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.ui.Bar;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.Attribute;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.I18n;

/**
 * 抽液机：从**固体**地板取液并耗电（如抽水机 {@code water-extractor}）。参照 Mindustry
 * mindustry.world.blocks.production.SolidPump 移植。
 * <p>与 {@link Pump}（从液体地板抽）相反，本类要求脚下不是液体，产量按覆盖面里"合格格数占比"
 * （{@link #isValid}）+ 地形属性加成 {@code boost} 缩放；需要电（{@code hasPower = true}）。
 *
 * <p>与原版的差异：
 * <ul>
 *     <li>原版把 {@code update(Tile)} / {@code draw(Tile)} 写在方块上、由 {@code Block.update(Tile)} 调用；
 *         本项目统一写在实体内部类 {@link SolidPumpEntity#update()}（见 {@code Drill}/{@code Pump}）。</li>
 *     <li>原版 {@code Draw.rect(name + "-rotator", ...)} 用的是 arc 的"按名取贴图"重载，
 *         本项目 {@code Draw} 只有 {@code TextureRegion} 版本 → 新增 {@link #rotatorRegion} 字段
 *         （上层贴图直接用 {@code LiquidBlock} 已声明的受保护字段 {@code topRegion}），
 *         在 {@link #load()} 里查图集（与 {@code Drill} 的做法一致）。</li>
 *     <li>原版 {@code tile.getLinkedTilesAs(this, drawTiles)} / {@code getLinkedTiles(tempTiles)}
 *         复用 Block 的缓存数组；本项目 {@code Tile} 没有这两个方法，改为等价的私有
 *         {@link #linkedTiles(Tile, Array)}（与 {@code Pump.linkedTiles} 同一套多格范围算法）。</li>
 *     <li>原版 {@code timers++} 的计时器槽位本项目没有，改为实体自带的 {@code Interval}
 *         （继承自 {@code Pump.PumpEntity}）。</li>
 *     <li>{@code setBars()}（效率条）→ 改写成覆写 {@link #displayBars(Tile, Table)}。</li>
 *     <li>{@code drawPlace(x, y, rotation, valid)}（放置时显示加成百分比）→ 已用
 *         {@code Block.drawPlace/drawPlaceText} 钩子 + 新增的 {@code Block.percentSolid} 接线（见 {@link #drawPlace}）。</li>
 * </ul>
 */
public class SolidPump extends Pump{
    /** 产出的液体。 */
    public Liquid result = Liquids.water;
    public Effects.Effect updateEffect = Fx.none;
    public float updateEffectChance = 0.02f;
    public float rotateSpeed = 1f;
    /** 计算产量时检查的地形属性（对应原版 {@code @Nullable Attribute attribute}）。 */
    public Attribute attribute;

    /** 旋转贴图（name-rotator）。原版用 Draw 的按名取图，本项目改成显式字段。 */
    public TextureRegion rotatorRegion;
    //注意：上层贴图 topRegion 直接复用 LiquidBlock 里已声明的受保护字段（不再重复声明，避免字段隐藏）

    public SolidPump(String name){
        super(name);
        hasPower = true;
        entityType = SolidPumpEntity::new;
    }

    @Override
    public void load(){
        super.load();
        if(Core.atlas == null) return;

        liquidRegion = Core.atlas.findRegion(name + "-liquid");
        rotatorRegion = Core.atlas.findRegion(name + "-rotator");
        topRegion = Core.atlas.findRegion(name + "-top");
    }

    @Override
    public void setStats(){
        super.setStats();

        //父类 Pump 登记的是"液体/秒"的通用产出，这里换成"具体液体 + 数量"
        stats.remove(BlockStat.output);
        stats.add(BlockStat.output, result, 60f * pumpAmount, true);
        if(attribute != null){
            stats.add(BlockStat.affinities, attribute);
        }
    }

    /** 对应原版 {@code setBars()} 里的 efficiency 条（本项目没有 bars，改在这里追加）。 */
    @Override
    public void displayBars(Tile tile, Table table){
        super.displayBars(tile, table);
        if(!(tile.entity instanceof SolidPumpEntity)) return;

        SolidPumpEntity entity = (SolidPumpEntity)tile.entity;
        table.add(new Bar(
            () -> Strings.format(I18n.get("bar.pumpspeed", "Pump speed: {0}/s"),
                Strings.fixed(Time.delta() <= 0f ? 0f : entity.lastPump / Time.delta() * 60f, 1)),
            () -> Pal.ammo,
            () -> entity.warmup)).height(18f).growX().pad(4f);
        table.row();
    }

    /**
     * 绘制本体 + 液体存量 + 旋转头/上层。
     * <p>⚠ <b>坐标口径修正</b>：原版用 {@code tile.drawx()/drawy()}（原版口径下即方块中心）；
     * 本项目 {@code Tile.drawx()} 少半格，而 {@code Draw.rect} 是中心语义 → 改用
     * {@code Block.centerX/centerY(tile)}（= {@code drawx() + tilesize/2f}），否则整体偏左下 4。
     */
    @Override
    public void draw(Tile tile){
        SolidPumpEntity entity = tile.entity instanceof SolidPumpEntity ? (SolidPumpEntity)tile.entity : null;

        if(region != null) Draw.rect(region, centerX(tile), centerY(tile));

        if(entity != null && entity.liquids != null){
            Liquid current = entity.liquids.current();
            Draw.color(current.color);
            Draw.alpha(entity.liquids.total() / liquidCapacity);
            if(liquidRegion != null) Draw.rect(liquidRegion, centerX(tile), centerY(tile));
            Draw.color();
        }

        if(entity != null){
            if(rotatorRegion != null) Draw.rect(rotatorRegion, centerX(tile), centerY(tile), entity.pumpTime * rotateSpeed);
            if(topRegion != null) Draw.rect(topRegion, centerX(tile), centerY(tile));
        }
    }

    /** 图标分层：底座 + 旋转头 + 上层（对应原版 {@code SolidPump.generateIcons}）。 */
    @Override
    public TextureRegion[] generateIcons(){
        return new TextureRegion[]{region, rotatorRegion, topRegion};
    }

    @Override
    public boolean canPlaceOn(Tile tile){
        if(isMultiblock()){
            for(Tile other : linkedTiles(tile, drawTiles)){
                if(SolidPump.this.isValid(other)) return true;
            }
            return false;
        }else{
            return SolidPump.this.isValid(tile);
        }
    }

    /** @return 该格是否可抽液（对应原版 {@code SolidPump.isValid}：非液体地板即可）。 */
    @Override
    protected boolean isValid(Tile tile){
        return tile != null && !tile.floor().isLiquid;
    }

    /** 放置预览：显示"地形属性加成 × 非液体格占比"的产量百分比（对应原版 {@code SolidPump.drawPlace}）。 */
    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        if(attribute != null){
            drawPlaceText(com.phoenix.game.core.Strings.format(
                com.phoenix.game.world.meta.I18n.get("bar.efficiency", "Efficiency: {0}%"),
                com.phoenix.game.core.Strings.fixed((sumAttribute(attribute, x, y) + 1f) * 100f * percentSolid(x, y), 1)),
                x, y, valid);
        }
    }

    @Override
    public void onProximityAdded(Tile tile){
        super.onProximityAdded(tile);

        if(attribute != null && tile.entity instanceof SolidPumpEntity){
            ((SolidPumpEntity)tile.entity).boost = sumAttribute(attribute, tile.x, tile.y);
        }
    }

    /** @return 当前库存量（对应原版 {@code SolidPump.typeLiquid}）。 */
    public float typeLiquid(Tile tile){
        return tile.entity == null || tile.entity.liquids == null ? 0f : tile.entity.liquids.total();
    }

    /** 覆盖面瓦片（与 {@code Tile.setBlock} 的多格铺开范围一致，支持偶数尺寸）。 */
    private Array<Tile> linkedTiles(Tile tile, Array<Tile> out){
        out.clear();
        int offset = -(size - 1) / 2;
        for(int dx = 0; dx < size; dx++){
            for(int dy = 0; dy < size; dy++){
                Tile other = tile.getNearby(dx + offset, dy + offset);
                if(other != null) out.add(other);
            }
        }
        return out;
    }

    /** 抽液机实体（原版 {@code SolidPumpEntity}）。 */
    public class SolidPumpEntity extends PumpEntity{
        /** 启动进度（0~1），驱动机头旋转与效率条。 */
        public float warmup;
        /** 机头旋转相位。 */
        public float pumpTime;
        /** 地形属性加成（放置时由 {@code onProximityAdded} 算出）。 */
        public float boost;
        /** 上一帧实际抽到的液量（效率条用）。 */
        public float lastPump;

        /** 对应原版 {@code SolidPump.update(Tile)}（本项目统一写在实体里）。 */
        @Override
        public void update(){
            float fraction = 0f;

            if(isMultiblock()){
                for(Tile other : linkedTiles(tile, updateTiles)){
                    if(SolidPump.this.isValid(other)){
                        fraction += 1f / (size * size);
                    }
                }
            }else{
                if(SolidPump.this.isValid(tile)) fraction = 1f;
            }

            fraction += boost;

            if(cons.valid() && typeLiquid(tile) < liquidCapacity - 0.001f){
                float maxPump = Math.min(liquidCapacity - typeLiquid(tile), pumpAmount * delta() * fraction * efficiency());
                liquids.add(result, maxPump);
                lastPump = maxPump;
                warmup = Mathf.lerpDelta(warmup, 1f, 0.02f);
                if(timer.get(timerContentCheck, 10)) useContent(tile, result);
                if(Mathf.chance(delta() * updateEffectChance)){
                    Effects.effect(updateEffect, block.centerX(tile) + Mathf.range(size * 2f), block.centerY(tile) + Mathf.range(size * 2f));
                }
            }else{
                warmup = Mathf.lerpDelta(warmup, 0f, 0.02f);
                lastPump = 0f;
            }

            pumpTime += warmup * delta();

            tryDumpLiquid(tile, result);
        }
    }
}
