package com.phoenix.game.world.blocks.production;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.consumers.ConsumeLiquidBase;
import com.phoenix.game.world.consumers.ConsumeType;
import com.phoenix.game.world.meta.BlockStat;

/**
 * 液体转化器。参照 Mindustry mindustry.world.blocks.production.LiquidConverter 移植。
 * <p>把一种液体（由 {@code consumes} 里的 ConsumeLiquid* 声明）按 craftTime 转化成
 * {@code outputLiquid}，并把产物推向相邻建筑。
 * <p>刻意偏离（本项目结构）：原版逻辑写在 {@code Block.update(Tile)} 里、实体复用 {@code GenericCrafterEntity}；
 * 本项目统一把逻辑写进实体内部类（见 {@code Drill}/{@code GenericCrafter}），
 * 且 {@code GenericCrafter.CraftEntity} 的 progress 是"帧数"语义（原版是 0~1 的完成度），
 * 所以这里自定义了等价的 {@link LiquidConverterEntity}。
 * <p>已接线：{@code entity.efficiency()}（超频效率，已乘进 {@code use}）。
 * <p>已接线（S5a）：{@code drawLight(Tile)}（原版 {@code renderer.lights.add(...)}）—— 本项目已移植
 * 灯光管线（{@code graphics/LightRenderer}），见 {@link #drawLight(Tile)}。
 */
public class LiquidConverter extends GenericCrafter{

    public LiquidConverter(String name){
        super(name);
        hasLiquids = true;
        entityType = LiquidConverterEntity::new;
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    @Override
    public void init(){
        ConsumeLiquidBase cl = consumes.get(ConsumeType.liquid);
        cl.update(true);
        outputLiquid.amount = cl.amount;
        super.init();
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.remove(BlockStat.output);
        stats.add(BlockStat.output, outputLiquid.liquid, outputLiquid.amount * craftTime, false);
    }

    /**
     * 液体发光（对应原版 {@code LiquidConverter.drawLight}，见原版 LiquidConverter.java:35-40）。
     * <p>已接线（S5a）：本项目 {@code Renderer} 现已移植灯光管线，本方法由 {@code Renderer.drawLights}
     * 逐格调用；实际加光源的是 {@link com.phoenix.game.world.Block#drawLiquidLight(Tile, com.phoenix.game.type.Liquid, float)}。
     */
    @Override
    public void drawLight(Tile tile){
        //本项目补的守卫：没有实体就没有 liquids 模块（原版此处默认 tile.entity 非空）
        if(tile == null || tile.entity == null) return;
        if(hasLiquids && drawLiquidLight && outputLiquid.liquid.lightColor.a > 0.001f){
            drawLiquidLight(tile, outputLiquid.liquid, tile.entity.liquids.get(outputLiquid.liquid));
        }
    }

    /** 液体转化器实体：把输入液体按 craftTime 转成输出液体。 */
    public class LiquidConverterEntity extends TileEntity{
        /** 完成度 0~1（与原版 GenericCrafterEntity.progress 同义）。 */
        public float progress;

        @Override
        public void update(){
            ConsumeLiquidBase cl = consumes.get(ConsumeType.liquid);

            if(cons.valid()){
                //乘 entity.efficiency()（超频效率），与原版一致
                float use = Math.min(cl.amount * delta(), liquidCapacity - liquids.get(outputLiquid.liquid)) * efficiency();

                useContent(tile, outputLiquid.liquid);
                progress += use / cl.amount / craftTime;
                liquids.add(outputLiquid.liquid, use);
                if(progress >= 1f){
                    cons.trigger();
                    progress = 0f;
                }
            }

            tryDumpLiquid(tile, outputLiquid.liquid);
        }
    }
}
