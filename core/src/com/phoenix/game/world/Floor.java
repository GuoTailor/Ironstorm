package com.phoenix.game.world;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.content.Fx;
import com.phoenix.game.content.StatusEffects;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.StatusEffect;

/**
 * 最小实现：地板。参照 Mindustry mindustry.world.blocks.Floor 移植。
 */
public class Floor extends Block {
    /**
     * 地表直接掉落的物品（对应原版 Floor.itemDrop）。
     * <p>原版只有 sand / darksand 掉沙，其余资源全部来自矿脉 overlay（OreFloor）；
     * 钻头产出去 {@link Tile#drop()} 查（矿脉优先）。
     */
    public Item itemDrop = null;
    /** 变体贴图数量 */
    public int variants = 3;
    /** 单位速度倍率 */
    public float speedMultiplier = 1f;
    /** 单位阻力倍率 */
    public float dragMultiplier = 1f;
    /** 每 tick 受到的伤害 */
    public float damageTaken = 0f;
    /**
     * 地热强度（对应原版 {@code Attribute.heat}）：供 {@code ThermalGenerator} 按邻接地板累加发电。
     * <p>原版的热源地板（basalt / hotrock / char / magmarock）本工程尚未移植贴图，
     * 暂时只给 shale 标了热度当热源（TODO：补 hotrock 系列地板后改回）。
     */
    public float heat = 0f;
    /** 淹没所需 tick */
    public float drownTime = 0f;
    /** 是否为液体 */
    public boolean isLiquid;
    /**
     * 地板自身的可燃性（对应原版液体地板的 {@code liquidDrop.flammability}）。
     * <p>火系统用它算蔓延与熄灭：水上（0）没有燃料，火会以 8 倍速烧完，等于被浇灭。
     * <p>刻意偏离：原版每个液体地板挂一个 {@code Liquid} 内容并读它的 flammability，
     * 本工程没有液体地板内容，直接把可燃性标在地板上。
     */
    public float flammability = 0f;
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
        //矿脉（OreFloor）的方块名是 ore-<物品>，贴图却是物品源图（copper1..3），走 textureName 指向
        variantRegions = loadRegions(textureName != null ? textureName : name, variants);
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
