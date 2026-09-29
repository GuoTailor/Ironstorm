package com.phoenix.game.world.blocks.power;

import com.badlogic.gdx.graphics.Color;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Strings;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.Attribute;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.I18n;

/**
 * 地热发电机。参照 Mindustry mindustry.world.blocks.power.ThermalGenerator 移植。
 * <p>发电量 = 覆盖范围内地板热度之和 × {@link #powerProduction}：压在热源地板上的格子越多、热度越高，
 * 发电越多（原版注释：这里的 {@code productionEfficiency} 表示"总热度"，可以大于 1.0）。
 * <p>简化：原版还要求"所在格有热度"（{@code canPlaceOn}）与边缘格判定，本项目沿用既有实现，只做范围累加。
 */
public class ThermalGenerator extends PowerGenerator{
    public Effects.Effect generateEffect = Fx.none;
    public Attribute attribute = Attribute.heat;

    public ThermalGenerator(String name){
        super(name);
        entityType = ThermalEntity::new;
    }

    @Override
    public void setStats(){
        super.setStats();

        //原版如此：把"哪些地板提供该属性"列进详情（BlockStats 会逐个列出带该属性的 Floor）
        stats.add(BlockStat.tiles, attribute);
    }

    /**
     * 放置预览：显示覆盖范围内的地热效率百分比（对应原版 {@code ThermalGenerator.drawPlace}）。
     * <p><b>与原版的刻意偏离</b>：原版用 {@code sumAttribute(attribute, x, y)}（读 {@code Floor.attributes}），
     * 本项目地热强度标在 {@code Floor.heat} 上（见 {@link #sumHeat}），故这里也用 {@code sumHeat}，
     * 保证预览数值与实体实际的 {@code productionEfficiency} 是同一个来源（否则会一直显示 0%）。
     */
    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        Tile tile = Vars.world == null ? null : Vars.world.tile(x, y);
        if(tile == null) return;

        drawPlaceText(Strings.format(I18n.get("bar.efficiency", "Efficiency: {0}%"),
            Strings.fixed(sumHeat(tile) * 100f, 1)), x, y, valid);
    }

    /**
     * 地热发光（对应原版 {@code ThermalGenerator.drawLight}，见原版 ThermalGenerator.java:43-47）。
     * <p>已接线（S5b 批次）：本项目已移植灯光管线（{@code graphics/LightRenderer}），
     * 本方法由 {@code core/Renderer.drawLights} 逐格调用。
     * <p>⚠ 坐标口径：原版 {@code tile.drawx()/drawy()} 是方块中心；本项目 {@code worldx()/worldy()} 是瓦片角点
     * → 用 {@link com.phoenix.game.world.Block#centerX}/{@link com.phoenix.game.world.Block#centerY}。
     * <p>⚠ API 差异：原版 {@code Mathf.absin(10f, 5f)} 是 arc 的 {@code absin(scl, mag)}（时间取 Time.time）；
     * 本项目两参重载是 {@code (time, speed)} 语义不同 → 改写成三参 {@code (Time.time(), scl, mag)}。
     */
    @Override
    public void drawLight(Tile tile){
        if(!(tile.entity instanceof ThermalEntity)) return;
        if(Vars.renderer == null) return;

        ThermalEntity e = (ThermalEntity)tile.entity;
        //原版 Color.scarlet（arc 0xff341c）；本项目用 libgdx 的 Color.SCARLET（0xff2400，见 graphics/Colors 的映射）
        Vars.renderer.lights.add(centerX(tile), centerY(tile),
            (40f + Mathf.absin(Time.time(), 10f, 5f)) * e.productionEfficiency * size, Color.SCARLET, 0.4f);
    }

    /**
     * @return 本建筑覆盖范围内所有地板的热度之和。
     * <p>⚠ 刻意偏离：原版是 {@code sumAttribute(attribute, tile.x, tile.y)}（读 {@code Floor.attributes}），
     * 本项目地热强度一直标在 {@code Floor.heat} 字段上（见 {@code Blocks.java} 里 hotrock/magmarock 的赋值），
     * 两处口径不同，这里沿用既有的 {@code Floor.heat}，避免把地热发电机改成读 attributes 后在地图上取不到热度。
     */
    public float sumHeat(Tile tile){
        int offset = -(size - 1) / 2;
        float sum = 0f;

        for(int dx = 0; dx < size; dx++){
            for(int dy = 0; dy < size; dy++){
                Tile other = com.phoenix.game.Vars.world.tile(tile.x + dx + offset, tile.y + dy + offset);
                if(other != null && other.floor() != null){
                    sum += other.floor().heat;
                }
            }
        }
        return sum;
    }

    //刻意降级（未引入）：原版 canPlaceOn(Tile) 要求所在位置热度 > 0.01f（否则不许建造）。
    //  依赖其实已就位（Block.canPlaceOn 已在 Build.validPlace 接线、Floor.heat 存在），
    //  但本项目一直没做该限制，接入会改变可建造性、并影响调试图 buildGeneratorTest（先放置后改地板），
    //  故沿用上批决定暂不引入；若要恢复：覆写 canPlaceOn 返回 sumHeat(tile) > 0.01f。

    /** 地热发电机实体。原版在 {@code onProximityAdded} 里算一次效率；本项目改成每帧重算（见 {@link #update()}）。 */
    public class ThermalEntity extends GeneratorEntity{
        /** 对应原版 {@code ThermalGenerator.update(Tile)}（冒火星的部分）。 */
        @Override
        public void update(){
            //原版在 onProximityAdded(Tile) 里算一次（热源地板不会变）；本项目地板可以在运行期被改
            //（如测试里"先放机器再铺热源地板"），所以改成每帧按地形重算 —— 地板不变时结果与原版一致
            productionEfficiency = sumHeat(tile);

            if(productionEfficiency > 0.1f && Mathf.chance(0.05f * delta())){
                Effects.effect(generateEffect, block.centerX(tile) + Mathf.range(3f), block.centerY(tile) + Mathf.range(3f));
            }
        }
    }
}
