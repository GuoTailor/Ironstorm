package com.phoenix.game.world;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.content.Fx;
import com.phoenix.game.content.StatusEffects;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.type.StatusEffect;

/**
 * 最小实现：地板。参照 Mindustry mindustry.world.blocks.Floor 移植。
 */
public class Floor extends Block {
    /** 变体贴图数量 */
    public int variants = 3;
    /** 单位速度倍率 */
    public float speedMultiplier = 1f;
    /** 单位阻力倍率 */
    public float dragMultiplier = 1f;
    /** 每 tick 受到的伤害 */
    public float damageTaken = 0f;
    /** 淹没所需 tick */
    public float drownTime = 0f;
    /** 是否为液体 */
    public boolean isLiquid;
    /** 行走特效 */
    public Effects.Effect walkEffect = Fx.none;
    /** 溺水特效 */
    public Effects.Effect drownUpdateEffect = Fx.none;
    /** 施加的状态 */
    public StatusEffect status = StatusEffects.none;
    /** 状态持续时间 */
    public float statusDuration = 60f;

    public Floor(String name){
        super(name);
    }

    @Override
    public void load(){
        variantRegions = loadRegions(name, variants);
        if(variantRegions.length > 0) region = variantRegions[0];
    }

    /** 依据坐标确定性地取一个变体贴图。 */
    public TextureRegion variant(int x, int y){
        if(variantRegions.length == 0) return region;
        int hash = x * 31 + y * 17;
        return variantRegions[Math.abs(hash) % variantRegions.length];
    }

    @Override
    public boolean hasEntity(){
        return false;
    }
}
