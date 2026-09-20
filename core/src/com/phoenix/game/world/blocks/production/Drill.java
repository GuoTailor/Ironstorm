package com.phoenix.game.world.blocks.production;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

/**
 * 采集钻头。参照 Mindustry mindustry.world.blocks.production.Drill 移植。
 * <p>每 drillTime 帧产 1 个 result 存入自身库存，随后尝试交给相邻建筑（传送带/核心/仓库）。
 * 速度受供电满足率与液体加成影响（对应原版 warmup / liquidBoostIntensity）。
 * 未移植：矿层依赖（任何位置都能采）、dominantItem 按矿脉选择。
 */
public class Drill extends Block{
    /** 采出的物品。 */
    public Item result;
    /** 每采集 1 个所需的基础帧数。 */
    public float drillTime = 60f;
    /** 液体加速倍率上限（对应原版 liquidBoostIntensity）；<=1 表示无加速。 */
    public float liquidBoostIntensity = 1.6f;
    /** 启动速度：每帧 warmup 增长量（对应原版 warmupSpeed）。 */
    public float warmupSpeed = 0.02f;
    /** 钻头旋转速度（度/帧），对应原版 rotateSpeed。 */
    public float rotateSpeed = 2f;
    /** 是否绘制发热外圈（对应原版 drawRim）。 */
    public boolean drawRim = false;
    /** 外圈发热颜色。 */
    public Color heatColor = Color.valueOf("ff5512");

    /** 外圈贴图（name-rim）。 */
    public TextureRegion rimRegion;
    /** 旋转钻头贴图（name-rotator）。 */
    public TextureRegion rotatorRegion;
    /** 上层贴图（name-top）。 */
    public TextureRegion topRegion;

    public Drill(String name){
        super(name);
        update = true;
        solid = true;
        health = 80;
        hasItems = true;
        itemCapacity = 10;
        hasLiquids = true;
        liquidCapacity = 5f;
        hasPower = true;
        powerConsumption = 0.05f;
        entityType = DrillEntity::new;
    }

    @Override
    public void load(){
        super.load();
        if(Core.atlas == null) return;

        rimRegion = Core.atlas.findRegion(name + "-rim");
        rotatorRegion = Core.atlas.findRegion(name + "-rotator");
        topRegion = Core.atlas.findRegion(name + "-top");
    }

    /**
     * 图标分层：底座 + 旋转钻头 + 上层，对应原版 {@code Drill.generateIcons}。
     * <p>原版在打包期合成成一张图；这里保留分层，由 UI 侧按序叠加。
     */
    @Override
    protected TextureRegion[] generateIcons(){
        return new TextureRegion[]{
            region,
            Core.atlas == null ? null : Core.atlas.findRegion(name + "-rotator"),
            Core.atlas == null ? null : Core.atlas.findRegion(name + "-top")
        };
    }

    /**
     * 钻头专属状态条：在通用条之后追加“挖掘速度”条（对应原版 Drill.setBars 的 drillspeed）。
     * <p>数值取实体每帧实测值 {@code lastDrillSpeed}，与实体内部计算保持单一数据源。
     */
    @Override
    public void displayBars(Tile tile, com.badlogic.gdx.scenes.scene2d.ui.Table table){
        super.displayBars(tile, table);
        if(!(tile.entity instanceof DrillEntity)) return;

        DrillEntity entity = (DrillEntity)tile.entity;
        table.add(new com.phoenix.game.ui.Bar(
            () -> String.format("挖掘速度：%.2f/秒", entity.lastDrillSpeed),
            () -> com.phoenix.game.graphics.Pal.ammo,
            () -> entity.warmup))
            .height(18f).growX().pad(4f);
        table.row();
    }

    /** 绘制钻头：底座 + 发热外圈 + 旋转钻头 + 上层（对应原版 Drill.draw）。 */
    @Override
    public void draw(Tile tile){
        if(region == null) return;

        DrillEntity entity = tile.entity instanceof DrillEntity ? (DrillEntity)tile.entity : null;
        float s = 0.3f, ts = 0.6f;

        drawBase(tile);

        if(drawRim && rimRegion != null && entity != null){
            Draw.color(heatColor);
            Draw.alpha(entity.warmup * ts * (1f - s + Mathf.absin(Time.time, 3f, s)));
            //加色混合模拟发热外圈（对应原版 Draw.blend(Blending.additive)）
            Core.batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE);
            drawRegionCentered(rimRegion, tile);
            Core.batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
            Draw.color();
        }

        if(rotatorRegion != null && entity != null){
            float rotation = entity.progress * rotateSpeed;
            drawRegionRotated(rotatorRegion, tile, rotation);
        }
    }

    /** 上层贴图单独一层，避免被后续绘制的地板/建筑覆盖。 */
    @Override
    public void drawLayer(Tile tile){
        if(topRegion != null){
            drawRegionCentered(topRegion, tile);
        }
    }

    /** 绘制 size×size 的底座（与 Block.draw 一致，含偶数尺寸的半格偏移）。 */
    private void drawBase(Tile tile){
        float s = size * com.phoenix.game.Vars.tilesize;
        Core.batch.draw(region, tile.worldx() + (com.phoenix.game.Vars.tilesize - s) / 2f + offset(),
            tile.worldy() + (com.phoenix.game.Vars.tilesize - s) / 2f + offset(), s, s);
    }

    /** 以瓦片几何中心绘制整张贴图。 */
    private void drawRegionCentered(TextureRegion reg, Tile tile){
        float s = size * com.phoenix.game.Vars.tilesize;
        Core.batch.draw(reg, centerX(tile) - s / 2f, centerY(tile) - s / 2f, s, s);
    }

    /** 以瓦片几何中心旋转绘制贴图（用于钻头自转）。 */
    private void drawRegionRotated(TextureRegion reg, Tile tile, float rotation){
        float s = size * com.phoenix.game.Vars.tilesize;
        Core.batch.draw(reg, centerX(tile) - s / 2f, centerY(tile) - s / 2f, s / 2f, s / 2f, s, s, 1f, 1f, rotation);
    }

    public class DrillEntity extends TileEntity{
        /** 采集进度（帧）；同时作为钻头旋转相位，对应原版 entity.drillTime。 */
        public float progress;
        /** 启动进度（0~1），对应原版 warmup。 */
        public float warmup;
        /** 最后一次计算的采集速度（每秒），用于详情条。 */
        public float lastDrillSpeed;
        /** 产出的物品种类（详情面板显示用）。 */
        public Item dominantItem;

        @Override
        public void update(){
            if(result == null) return;

            float speed = speedf();

            //启动进度：供电满足时上涨到 speed，否则回落到 0（对应原版 warmup）
            warmup = Mathf.lerpDelta(warmup, speed, warmupSpeed);

            if(speed <= 0.001f || items.total() >= itemCapacity){
                lastDrillSpeed = 0f;
                return;
            }

            //倍率折算：基础速度 × 液体加成（对应原版 optionalValid → liquidBoostIntensity）
            float boost = liquidBoostIntensity > 1f && liquids != null && !liquids.isEmpty() ? liquidBoostIntensity : 1f;
            float rate = speed * boost * warmup;

            lastDrillSpeed = rate / drillTime * 60f;
            dominantItem = result;
            progress += Time.delta() * rate;

            if(progress >= drillTime){
                progress = 0f;
                //先尝试直接交给相邻建筑，无处可去才留在自身库存
                offloadNear(tile, result);
            }

            //把积压的库存持续往外送
            tryDump(tile);
        }

        /** @return 供电满足率（0~1）；无电力模块时视为满供。 */
        private float speedf(){
            return power == null ? 1f : power.status;
        }
    }
}
