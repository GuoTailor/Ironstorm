package com.phoenix.game.world.blocks.production;

import com.badlogic.gdx.graphics.Color;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Fill;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Effects.Effect;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

/**
 * 焚化炉。参照 Mindustry mindustry.world.blocks.production.Incinerator 移植。
 * <p>通电即升温（{@code heat}），温度过 0.5 才开始接收物品/液体并直接销毁掉（收下不存）。
 * <p>刻意偏离（本项目结构）：原版逻辑写在 {@code Block.update(Tile)} 里，
 * 本项目统一写在实体内部类 {@link IncineratorEntity#update()} 里（见 {@code Drill}/{@code GenericCrafter}）。
 * <p>说明：{@code Block.handleLiquid/acceptLiquid} 钩子已就绪并已接线（见下方覆写）。
 */
public class Incinerator extends Block{
    public Effect effect = Fx.fuelburn;
    public Color flameColor = Color.valueOf("ffad9d");

    public Incinerator(String name){
        super(name);
        hasPower = true;
        hasLiquids = true;
        update = true;
        solid = true;
        entityType = IncineratorEntity::new;
    }

    @Override
    public void draw(Tile tile){
        super.draw(tile);

        IncineratorEntity entity = (IncineratorEntity)tile.entity;

        if(entity.heat > 0f){
            float g = 0.3f;
            float r = 0.06f;

            Draw.alpha(((1f - g) + Mathf.absin(Time.time, 8f, g) + Mathf.random(r) - r) * entity.heat);

            Draw.tint(flameColor);
            Fill.circle(centerX(tile), centerY(tile), 2f);
            Draw.color(1f, 1f, 1f, entity.heat);
            Fill.circle(centerX(tile), centerY(tile), 1f);

            Draw.color();
        }
    }

    /** 收到物品直接销毁（只放一点火花特效）。 */
    @Override
    public void handleItem(Item item, Tile tile, Tile source){
        if(Mathf.chance(0.3f)){
            Effects.effect(effect, centerX(tile), centerY(tile));
        }
    }

    /** 温度不够时不收物品。 */
    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        IncineratorEntity entity = (IncineratorEntity)tile.entity;
        return entity.heat > 0.5f;
    }

    /** 收到液体直接销毁（对应原版 Incinerator.handleLiquid）。 */
    @Override
    public void handleLiquid(Tile tile, Tile source, Liquid liquid, float amount){
        if(Mathf.chance(0.02f)){
            Effects.effect(effect, centerX(tile), centerY(tile));
        }
    }

    /** 温度不够时不收液体（对应原版 Incinerator.acceptLiquid，覆写以绕开 liquidfilters 判据）。 */
    @Override
    public boolean acceptLiquid(Tile tile, Tile source, Liquid liquid, float amount){
        IncineratorEntity entity = (IncineratorEntity)tile.entity;
        return entity.heat > 0.5f;
    }

    /** 焚化炉实体：只有温度（其余状态都是每帧重算的）。 */
    public class IncineratorEntity extends TileEntity{
        public float heat;

        @Override
        public void update(){
            if(cons.valid()){
                heat = Mathf.lerpDelta(heat, 1f, 0.04f);
            }else{
                heat = Mathf.lerpDelta(heat, 0f, 0.02f);
            }
        }
    }
}
