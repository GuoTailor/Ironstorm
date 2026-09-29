package com.phoenix.game.world.blocks.defense.turrets;

import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Effects.Effect;
import com.phoenix.game.game.EventType.Trigger;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.consumers.ConsumeLiquidBase;
import com.phoenix.game.world.consumers.ConsumeLiquidFilter;
import com.phoenix.game.world.consumers.ConsumeType;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.values.BoosterListValue;

import static com.phoenix.game.Vars.tilesize;

/**
 * 带液体冷却的炮塔。参照 Mindustry mindustry.world.blocks.defense.turrets.CooledTurret 移植。
 * <p>冷却液的热容越高、消耗越快，装填速度加成越大；冷却液耗尽时不再加速。
 * <p>刻意偏离（本项目结构）：原版把 {@code updateShooting(Tile)} / {@code handleLiquid(...)} 写在方块上，
 * 本项目统一写在实体内部类里（见 {@code Drill}/{@code GenericCrafter}），所以冷却逻辑落在
 * {@link CooledTurretEntity#updateCoolant()}。
 * <p>已接线：{@code Block.handleLiquid(Tile, Tile, Liquid, float)} 钩子（{@code Block.java}，由液体流动路径
 * {@code tryMoveLiquid} 调用）、{@code Trigger.turretCool} 事件、{@code Turret.baseReloadSpeed} 接进冷却装填推进。
 * <p>注意：{@code ConsumeLiquidFilter} / {@code Consumers.liquidfilters} / {@code BoosterListValue} 已补齐。
 */
public class CooledTurret extends Turret{
    /** How much reload is lowered by for each unit of liquid of heat capacity. */
    public float coolantMultiplier = 5f;
    public Effect coolEffect = Fx.fuelburn;

    public CooledTurret(String name){
        super(name);
        hasLiquids = true;
        liquidCapacity = 20f;

        //冷却液过滤器：低温且不可燃的液体才能当冷却液（对应原版 CooledTurret 构造函数）
        consumes.add(new ConsumeLiquidFilter(liquid -> liquid.temperature <= 0.5f && liquid.flammability < 0.1f, 0.2f)).update(false).boost();

        entityType = CooledTurretEntity::new;
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(BlockStat.booster, new BoosterListValue(reload, consumes.<ConsumeLiquidBase>get(ConsumeType.liquid).amount, coolantMultiplier, true, l -> consumes.liquidfilters.get(l.id)));
    }

    /**
     * 收到冷却液时触发"开始冷却"事件（对应原版 CooledTurret.handleLiquid）。
     * <p>接线点：{@code Block.tryMoveLiquid} → {@code next.block().handleLiquid(...)}（液体流动路径）。
     * 必须调用 {@code super.handleLiquid} 把液体真正存进本方块，否则冷却液会被丢弃。
     */
    public void handleLiquid(Tile tile, Tile source, Liquid liquid, float amount){
        if(tile.entity.liquids.currentAmount() <= 0.001f){
            Events.fire(Trigger.turretCool);
        }

        super.handleLiquid(tile, source, liquid, amount);
    }

    /** 炮塔实体：在父类开火逻辑之后按冷却液热容补装填进度。 */
    public class CooledTurretEntity extends TurretEntity{

        @Override
        protected void updateShooting(){
            super.updateShooting();
            updateCoolant();
        }

        /**
         * 冷却液消耗与装填加速（对应原版 {@code CooledTurret.updateShooting} 的后半段）。
         * <p>接线点：{@link #updateShooting()}，以及 {@code ItemTurret.ItemTurretEntity#updateShooting()}
         * （后者完全覆写了父类装填推进，必须自己再调一次，否则物品炮塔拿不到冷却加成）。
         */
        protected void updateCoolant(){
            if(!consumes.has(ConsumeType.liquid)) return;

            float maxUsed = consumes.<ConsumeLiquidBase>get(ConsumeType.liquid).amount;

            Liquid liquid = liquids.current();

            //注意：CooledTurretEntity 继承的 TurretEntity 里也有个 reload 字段（实体自己的装填进度），
            //     这里要的是方块上的装填时间，必须写成 CooledTurret.this.reload（原版方块/实体分离，没这个坑）
            float used = Math.min(Math.min(liquids.get(liquid), maxUsed * Time.delta()), Math.max(0, ((CooledTurret.this.reload - reload) / coolantMultiplier) / liquid.heatCapacity)) * baseReloadSpeed();
            reload += used * liquid.heatCapacity * coolantMultiplier;
            liquids.remove(liquid, used);

            if(Mathf.chance(0.06f * used)){
                //原版用 tile.drawx()/drawy()（= 方块几何中心），故这里用 cx()/cy() 而不是实体左下角 + 半格
                Effects.effect(coolEffect, cx() + Mathf.range(size * tilesize / 2f), cy() + Mathf.range(size * tilesize / 2f));
            }
        }
    }
}
