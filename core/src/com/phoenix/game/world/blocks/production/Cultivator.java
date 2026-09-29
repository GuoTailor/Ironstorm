package com.phoenix.game.world.blocks.production;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.RandomXS128;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Lines;
import com.phoenix.game.core.Time;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.Attribute;
import com.phoenix.game.world.meta.BlockStat;

/**
 * 培育机。参照 Mindustry mindustry.world.blocks.production.Cultivator 移植。
 * <p>把孢子类地形（{@link Attribute#spores}）的加成折进产量：{@code boost} 由放置时扫描周围地形得到，
 * 产物推进速度乘 {@code 1 + boost}；{@code warmup} 驱动作物生长动画。
 * <p>刻意偏离：
 * <ul>
 *     <li>原版 {@code Rand random = new Rand(0)}（arc 类型，映射表未覆盖）→ 改用 libgdx 的
 *         {@code RandomXS128}，同样是"可播种、按瓦片坐标确定性取随机数"；</li>
 *     <li>原版靠覆写 {@code Block.getProgressIncrease(entity, baseTime)} 乘 {@code 1 + boost}，
 *         本项目 {@code GenericCrafter} 没有该方法（进度直接在实体里推进），故在实体里展开；</li>
 *     <li>原版 {@code setBars()}（效率条）→ 本项目没有 {@code setBars()}，改写成覆写 {@link #displayBars}；</li>
 *     <li>原版 {@code drawPlace()}（放置时显示加成百分比）→ 本项目已补 {@code Block.drawPlace/drawPlaceText} 钩子并接线；</li>
 * </ul>
 * <p>注意：{@code BlockStats.add(BlockStat, Attribute)}、{@code Block.sumAttribute(Attribute, x, y)}、
 * {@code Floor.attributes}（{@code sporeMoss} 已设 {@code Attribute.spores=0.3}）都已就绪，
 * 所以孢子加成真实生效。{@code GenericCrafter} 没有 {@code craftEffect}（原版设 {@code Fx.none} 即无特效，行为等价）。
 */
public class Cultivator extends GenericCrafter{
    public Color plantColor = Color.valueOf("5541b1");
    public Color plantColorLight = Color.valueOf("7457ce");
    public Color bottomColor = Color.valueOf("474747");

    public TextureRegion middleRegion, topRegion;
    /** 按瓦片坐标确定性地取随机数（对应原版 {@code Rand random = new Rand(0)}）。 */
    public RandomXS128 random = new RandomXS128(0);
    public float recurrence = 6f;
    public Attribute attribute = Attribute.spores;

    public Cultivator(String name){
        super(name);
        //原版 craftEffect = Fx.none（即不播合成特效）；本项目 GenericCrafter 无 craftEffect 字段，行为等价 → 刻意降级
        entityType = CultivatorEntity::new;
    }

    @Override
    public void load(){
        super.load();

        if(Core.atlas == null) return;
        middleRegion = Core.atlas.findRegion(name + "-middle");
        topRegion = Core.atlas.findRegion(name + "-top");
    }

    @Override
    public void setStats(){
        super.setStats();

        //BlockStats.add(BlockStat, Attribute) 已存在
        stats.add(BlockStat.affinities, attribute);
    }

    /** 对应原版 {@code setBars()} 里的 efficiency 条（本项目没有 bars，改在这里追加）。 */
    @Override
    public void displayBars(Tile tile, com.badlogic.gdx.scenes.scene2d.ui.Table table){
        super.displayBars(tile, table);
        if(!(tile.entity instanceof CultivatorEntity)) return;

        CultivatorEntity entity = (CultivatorEntity)tile.entity;
        table.add(new com.phoenix.game.ui.Bar(
            () -> com.phoenix.game.core.Strings.format(
                com.phoenix.game.world.meta.I18n.get("bar.efficiency", "Efficiency: {0}%"),
                com.phoenix.game.core.Strings.fixed((entity.boost + 1f) * entity.warmup * 100f, 1)),
            () -> com.phoenix.game.graphics.Pal.ammo,
            () -> entity.warmup)).height(18f).growX().pad(4f);
        table.row();
    }

    /** 放置时显示"孢子加成后的效率百分比"（对应原版 {@code Cultivator.drawPlace}）。 */
    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        drawPlaceText(com.phoenix.game.core.Strings.format(
            com.phoenix.game.world.meta.I18n.get("bar.efficiency", "Efficiency: {0}%"),
            com.phoenix.game.core.Strings.fixed((1f + sumAttribute(attribute, x, y)) * 100f, 1)), x, y, valid);
    }

    /**
     * 绘制本体 + 中间层 + 孢子粒子 + 上层。
     * <p>⚠ <b>坐标口径修正</b>：原版用 {@code tile.drawx()/drawy()}（原版口径下即方块中心）；
     * 本项目 {@code Tile.drawx()} 少半格，而 {@code Draw.rect}/{@code Lines.poly} 都是中心语义 →
     * 改用 {@code Block.centerX/centerY(tile)}（= {@code drawx() + tilesize/2f}），否则整体偏左下 4。
     */
    @Override
    public void draw(Tile tile){
        CultivatorEntity entity = (CultivatorEntity)tile.entity;

        Draw.rect(region, centerX(tile), centerY(tile));

        Draw.color(plantColor);
        Draw.alpha(entity.warmup);
        Draw.rect(middleRegion, centerX(tile), centerY(tile));

        Draw.color(bottomColor, plantColorLight, entity.warmup);

        random.setSeed(tile.pos());
        for(int i = 0; i < 12; i++){
            float offset = random.nextFloat() * 999999f;
            //原版是 Rand.range(4f)（区间 [-2,2)）；这里等价展开
            float x = random.nextFloat() * 4f - 2f, y = random.nextFloat() * 4f - 2f;
            float life = 1f - (((Time.time + offset) / 50f) % recurrence);

            if(life > 0){
                Lines.stroke(entity.warmup * (life * 1f + 0.2f));
                Lines.poly(centerX(tile) + x, centerY(tile) + y, 8, (1f - life) * 3f);
            }
        }

        Draw.color();
        Draw.rect(topRegion, centerX(tile), centerY(tile));
    }

    @Override
    public TextureRegion[] generateIcons(){
        if(Core.atlas == null) return new TextureRegion[]{region};
        return new TextureRegion[]{Core.atlas.findRegion(name), Core.atlas.findRegion(name + "-top"),};
    }

    @Override
    public void onProximityAdded(Tile tile){
        super.onProximityAdded(tile);

        if(!(tile.entity instanceof CultivatorEntity)) return;
        CultivatorEntity entity = (CultivatorEntity)tile.entity;
        //Block.sumAttribute(Attribute, x, y) 已存在
        entity.boost = sumAttribute(attribute, tile.x, tile.y);
    }

    /** 培育机实体：在父类的合成推进之上叠加"孢子加成"与生长动画的 warmup。 */
    public class CultivatorEntity extends CraftEntity{
        public float warmup;
        public float boost;

        @Override
        public void update(){
            //对应原版覆写 getProgressIncrease(entity, baseTime) 乘 (1 + boost)；
            //本项目 GenericCrafter 没有该钩子，这里展开父类的推进逻辑并带上加成
            if(!cons.valid()){
                progress = 0f;
            }else{
                float status = power == null ? 1f : power.status;
                progress += Time.delta() * status * (1f + boost);

                if(progress >= craftTime){
                    progress = 0f;

                    cons.trigger();

                    if(outputItem != null){
                        items.add(outputItem.item, outputItem.amount);
                    }
                }
            }

            //产出仍要外送（输入物留在库存里等配方凑齐）
            if(outputItem != null){
                tryDump(tile, outputItem.item);
            }

            //原版 Cultivator.update：生长动画的插值
            warmup = Mathf.lerpDelta(warmup, cons.valid() ? 1f : 0f, 0.015f);
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeFloat(warmup);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            warmup = in.readFloat();
            //boost 是放置时按地形算出来的派生量，读档后由 onProximityAdded 重算
            boost = 0f;
        }
    }
}
