package com.phoenix.game.world.blocks.power;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.consumers.ConsumeItemFilter;
import com.phoenix.game.world.consumers.ConsumeLiquidFilter;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.StatUnit;

/**
 * 可用物品、液体或两者同时作为燃料的发电机。参照 Mindustry
 * mindustry.world.blocks.power.ItemLiquidGenerator 移植。
 * <p>工作机制（与原版一致）：<b>液体优先于物品</b> —— 只要库存里有"效率达标"的液体就按
 * {@link #maxLiquidGenerate} 逐帧消耗液体发电；没有液体时才取一个物品进入 {@link #itemDuration} 帧的燃烧期。
 * 子类只需实现 {@link #getItemEfficiency(Item)} / {@link #getLiquidEfficiency(Liquid)}
 * （如燃烧发电机用可燃性、衰变发电机用放射性）。
 *
 * <p>继承链已收敛回原版：{@code BurnerGenerator}/{@code DecayGenerator}/{@code SingleTypeGenerator}
 * 都继承本类（见 {@link PowerGenerator} 类注释）。原先还有一个历史遗留的 {@code ItemGenerator}
 * （= 本类砍掉液体分支后的简化版），已随 **R2 批**删除（`ItemGenerator` 零引用的死类）。
 *
 * <p>与原版的差异（未消化的以 {@code //TODO} 标注）：
 * <ul>
 *     <li>原版把 {@code update(Tile)} / {@code draw(Tile)} 写在方块上、由 {@code Block.update(Tile)} 调用；
 *         本项目统一写在实体内部类 {@link ItemLiquidGeneratorEntity#update()}（见 {@code Drill}/{@code Pump}）。</li>
 *     <li>{@code drawLight(Tile)}（原版 {@code renderer.lights.add(...)}）：<b>已接线（S5a 批次）</b>，
 *         见 {@link #drawLight(Tile)}；{@code Block.drawLiquidLight} 也已由同批补上实现。</li>
 *     <li>{@code state.rules.reactorExplosions}（随机爆炸的开关）：T2 批已补该字段，本类已按原版消费。</li>
 *     <li>{@code entity.power.graph.getUsageFraction()}：本项目 {@code PowerGraph} 没有该方法；
 *         原版该方法**恒返回 1f**（见原版注释 "enable it later"），故这里直接按 1 处理（等价）。</li>
 *     <li>{@code entity.damage(float)} → 本项目实体伤害入口是 {@code handleDamage(float)}。</li>
 *     <li>{@code productionValid(Tile)}：本项目 {@code Block.productionValid} 钩子本批已补齐，
 *         本类的覆写（见下方）现在真的会被 {@code ConsumeModule} 调用。</li>
 * </ul>
 */
public class ItemLiquidGenerator extends PowerGenerator{
    public float minItemEfficiency = 0.2f;
    /** 单个物品能发电的帧数。 */
    public float itemDuration = 70f;

    public float minLiquidEfficiency = 0.2f;
    /** 每帧最多消耗的液体量。 */
    public float maxLiquidGenerate = 0.4f;

    public Effects.Effect generateEffect = Fx.generatespark;
    public Effects.Effect explodeEffect = Fx.generatespark;
    public Color heatColor = Color.valueOf("ff9b59");
    public TextureRegion topRegion, liquidRegion;
    public boolean randomlyExplode = true;
    public boolean defaults = false;

    public ItemLiquidGenerator(boolean hasItems, boolean hasLiquids, String name){
        this(name);
        this.hasItems = hasItems;
        this.hasLiquids = hasLiquids;
        setDefaults();
    }

    public ItemLiquidGenerator(String name){
        super(name);
        this.entityType = ItemLiquidGeneratorEntity::new;
    }

    protected void setDefaults(){
        if(hasItems){
            consumes.add(new ConsumeItemFilter(item -> getItemEfficiency(item) >= minItemEfficiency))
                .update(false).optional(true, false);
        }

        if(hasLiquids){
            consumes.add(new ConsumeLiquidFilter(liquid -> getLiquidEfficiency(liquid) >= minLiquidEfficiency, maxLiquidGenerate))
                .update(false).optional(true, false);
        }

        defaults = true;
    }

    @Override
    public void init(){
        if(!defaults){
            setDefaults();
        }
        super.init();
    }

    @Override
    public void load(){
        super.load();
        if(Core.atlas == null) return;

        //原版是 Core.atlas.find(...)（arc 的 Atlas 缺图返回 error 区域）；本项目用 findRegion（缺图返回 null）
        if(hasItems){
            topRegion = Core.atlas.findRegion(name + "-top");
        }
        liquidRegion = Core.atlas.findRegion(name + "-liquid");
    }

    @Override
    public void setStats(){
        super.setStats();

        if(hasItems){
            stats.add(BlockStat.productionTime, itemDuration / 60f, StatUnit.seconds);
        }
    }

    /** 对应原版 {@code productionValid(Tile)}：正在燃烧物品时视为"在生产"。 */
    @Override
    public boolean productionValid(Tile tile){
        return tile != null && tile.entity instanceof ItemLiquidGeneratorEntity
            && ((ItemLiquidGeneratorEntity)tile.entity).generateTime > 0;
    }

    @Override
    public void draw(Tile tile){
        super.draw(tile);

        if(!(tile.entity instanceof ItemLiquidGeneratorEntity)) return;
        ItemLiquidGeneratorEntity entity = (ItemLiquidGeneratorEntity)tile.entity;

        if(hasItems && topRegion != null){
            Draw.color(heatColor);
            Draw.alpha(entity.heat * 0.4f + Mathf.absin(Time.time(), 8f, 0.6f) * entity.heat);
            Draw.rect(topRegion, centerX(tile), centerY(tile));
            //本项目 Draw 没有 reset()，用 color() 复位颜色（等价）
            Draw.color();
        }

        if(hasLiquids && liquidRegion != null && entity.liquids != null){
            Liquid current = entity.liquids.current();
            Draw.color(current.color);
            Draw.alpha(entity.liquids.currentAmount() / liquidCapacity);
            Draw.rect(liquidRegion, centerX(tile), centerY(tile));
            Draw.color();
        }
    }

    /** 发电机燃烧发光（对应原版 {@code ItemLiquidGenerator.drawLight}，见原版 ItemLiquidGenerator.java:176-181）。 */
    @Override
    public void drawLight(Tile tile){
        if(!(tile.entity instanceof ItemLiquidGeneratorEntity)) return;
        if(Vars.renderer == null) return;

        ItemLiquidGeneratorEntity entity = (ItemLiquidGeneratorEntity)tile.entity;
        //原版用 tile.drawx()/drawy()（方块中心）；本项目 worldx()/worldy() 是瓦片角点 → 用 centerX/centerY
        Vars.renderer.lights.add(centerX(tile), centerY(tile),
            (60f + Mathf.absin(10f, 5f)) * entity.productionEfficiency * size, Color.ORANGE, 0.5f);
    }

    protected float getItemEfficiency(Item item){
        return 0.0f;
    }

    protected float getLiquidEfficiency(Liquid liquid){
        return 0.0f;
    }

    /** 物品/液体发电机实体（原版 {@code ItemLiquidGeneratorEntity}）。 */
    public class ItemLiquidGeneratorEntity extends GeneratorEntity{
        public float explosiveness;
        public float heat;

        /** 对应原版 {@code ItemLiquidGenerator.update(Tile)}（本项目统一写在实体里）。 */
        @Override
        public void update(){
            //注意：算发电量/效率时不要用这个 delta（PowerGraph 已经按 delta 折算过发电量），只有消耗资源才用
            float calculationDelta = delta();

            if(!cons.valid()){
                productionEfficiency = 0.0f;
                return;
            }

            Liquid liquid = null;
            for(int i = 0; i < Vars.content.liquids().size; i++){
                Liquid other = Vars.content.liquids().get(i);
                if(hasLiquids && liquids.get(other) >= 0.001f && getLiquidEfficiency(other) >= minLiquidEfficiency){
                    liquid = other;
                    break;
                }
            }

            heat = Mathf.lerpDelta(heat, generateTime >= 0.001f ? 1f : 0f, 0.05f);

            //液体优先于固体
            if(hasLiquids && liquid != null && liquids.get(liquid) >= 0.001f){
                float baseLiquidEfficiency = getLiquidEfficiency(liquid);
                float maximumPossible = maxLiquidGenerate * calculationDelta;
                float used = Math.min(liquids.get(liquid) * calculationDelta, maximumPossible);

                //原版：liquids.remove(liquid, used * power.graph.getUsageFraction())；原版 getUsageFraction() 恒为 1f
                liquids.remove(liquid, used);
                productionEfficiency = maximumPossible <= 0f ? 0f : baseLiquidEfficiency * used / maximumPossible;

                if(used > 0.001f && Mathf.chance(0.05f * delta())){
                    Effects.effect(generateEffect, block.centerX(tile) + Mathf.range(3f), block.centerY(tile) + Mathf.range(3f));
                }
            }else if(hasItems){
                //没有液体可烧时改用物品
                if(generateTime <= 0f && items != null && items.total() > 0){
                    Effects.effect(generateEffect, block.centerX(tile) + Mathf.range(3f), block.centerY(tile) + Mathf.range(3f));
                    Item item = items.take();
                    productionEfficiency = getItemEfficiency(item);
                    explosiveness = item.explosiveness;
                    generateTime = 1f;
                }

                if(generateTime > 0f){
                    //原版乘 power.graph.getUsageFraction()（恒 1f）
                    generateTime -= Math.min(1f / itemDuration * delta(), generateTime);

                    //原版还判 state.rules.reactorExplosions（T2 批已补该字段，这里按原版消费）
                    if(randomlyExplode && Vars.state.rules.reactorExplosions
                        && Mathf.chance(delta() * 0.06f * Mathf.clamp(explosiveness - 0.5f))){
                        //放到帧末执行：方块可能在本帧被摧毁，此时实体已不属于该瓦片
                        com.badlogic.gdx.Gdx.app.postRunnable(() -> {
                            if(tile == null || tile.entity != this) return;
                            handleDamage(Mathf.random(11f));
                            float half = block.size * Vars.tilesize / 2f;
                            Effects.effect(explodeEffect, block.centerX(tile) + Mathf.range(half), block.centerY(tile) + Mathf.range(half));
                        });
                    }
                }else{
                    productionEfficiency = 0.0f;
                }
            }
        }
    }
}
