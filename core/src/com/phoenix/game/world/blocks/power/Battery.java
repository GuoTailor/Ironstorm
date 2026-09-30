package com.phoenix.game.world.blocks.power;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Fill;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 电池。参照 Mindustry mindustry.world.blocks.power.Battery 移植。
 * <p>用 {@code consumes.powerBuffered(capacity)} 声明储能容量；
 * {@link PowerGraph} 会在电量富余时充入、不足时取出，电量记在 {@code entity.power.stored}。
 * <p>大电池只是容量更大的实例（原版 battery-large 走同一个类）。
 * <p>收敛继承链：原版 {@code extends PowerDistributor}（→ {@code PowerBlock}，后者负责 update/solid/hasPower/group）。
 */
public class Battery extends PowerDistributor{
    /** 顶盖贴图下标（对应原版 {@code Battery.topRegion = reg("-top")}）：电量色块之上再叠一层。 */
    public int topRegion = reg("-top");

    /** 空电时的色块颜色（对应原版 {@code Battery.emptyLightColor}）。 */
    public Color emptyLightColor = Color.valueOf("f8c266");
    /** 满电时的色块颜色（对应原版 {@code Battery.fullLightColor}）。 */
    public Color fullLightColor = Color.valueOf("fb9567");

    public Battery(String name, float capacity){
        super(name);
        //原版 Battery.java 构造器不设 health（Blocks.java:1089/1094 也未覆盖）→ 走 Block.init 的 size*size*40 默认
        //（battery size1 = 40、battery-large size3 = 360）；此处不再显式赋值，交给 init() 补默认。
        consumes.powerBuffered(capacity);
        entityType = BatteryEntity::new;
    }

    /**
     * 电量色块 + 顶盖贴图（对应原版 {@code Battery.draw}）：
     * 色块颜色按 {@code entity.power.status} 在空/满色之间插值，再叠一张 {@code -top} 贴图。
     * <p>⚠ <b>本项目必调 {@code super.draw(tile)}</b>：原版的本体贴图由 {@code BlockRenderer} 单独绘制、
     * {@code draw(tile)} 只是叠层；本项目 {@code Block.draw(Tile)} 才是本体绘制点（见其注释），
     * 子类覆写不调 super 会丢掉电池底图（{@code NuclearReactor}/{@code LightBlock} 同样调 super）。
     * <p>⚠ 坐标口径：原版 {@code tile.drawx()/drawy()} 即方块中心；本项目改用 {@code Block.centerX/centerY}。
     * <p>⚠ {@code Fill} 走 GL 混合，而批次可能正处在加色混合等非标准状态 → 显式打开标准 alpha 混合
     * （同 {@code Renderer.drawShields} 的说明）。
     */
    @Override
    public void draw(Tile tile){
        super.draw(tile);

        TileEntity entity = tile.entity;
        if(entity == null || entity.power == null) return;

        Draw.color(emptyLightColor, fullLightColor, entity.power.status);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        //G10 起本项目 Fill.square 已对齐 arc 的**半径**语义 → 直接照原版写 tilesize*size/2f - 1
        Fill.square(centerX(tile), centerY(tile), tilesize * size / 2f - 1f);
        Draw.color();

        TextureRegion top = reg(topRegion);
        if(top != null){
            Draw.rect(top, centerX(tile), centerY(tile));
        }
    }

    /** 电池实体：储量存在 PowerModule.stored，无额外状态。 */
    public class BatteryEntity extends TileEntity{
    }
}
