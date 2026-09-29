package com.phoenix.game.world.blocks.defense;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Drawf;
import com.phoenix.game.core.Interval;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.StatUnit;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 超频投影器。参照 Mindustry mindustry.world.blocks.defense.OverdriveProjector 移植。
 * <p>每 {@link #reload} 帧给范围内所有友方建筑设置 {@code timeScale = speedBoost}
 * （持续 {@code reload + 1} 帧），建筑的 {@code update} 用 {@code delta()} 计时，因此会被加速。
 * <p>相织物加成（可选消耗器，对应原版）：库存里有相织物时 {@link OverdriveEntity#phaseHeat} 升温 →
 * 范围 +{@link #phaseRangeBoost}、倍率 +{@link #speedBoostPhase}，颜色从 {@link #baseColor} 渐变到
 * {@link #phaseColor}；每 {@link #useTime} 帧消耗一次相织物。
 */
public class OverdriveProjector extends Block{
    /** 加成刷新间隔（帧）。 */
    public float reload = 60f;
    /** 作用半径（世界单位）。 */
    public float range = 80f;
    /** 速度倍率。 */
    public float speedBoost = 1.5f;
    /** 相织物加成：额外速度倍率（对应原版 {@code speedBoostPhase}）。 */
    public float speedBoostPhase = 0.75f;
    /** 相织物加成：额外范围（世界单位，对应原版 {@code phaseRangeBoost}）。 */
    public float phaseRangeBoost = 20f;
    /** 相织物单次消耗的间隔（帧，对应原版 {@code useTime}）。 */
    public float useTime = 400f;

    /** 范围指示圈的颜色（对应原版 {@code OverdriveProjector.baseColor}）。 */
    public Color baseColor = Color.valueOf("feb380");
    /** 相织物生效时渐变到的颜色（对应原版 {@code OverdriveProjector.phaseColor}）。 */
    public Color phaseColor = Color.valueOf("ffd59e");

    /** 顶盖贴图（区域名 = 名称-top）。 */
    public TextureRegion topRegion;

    public OverdriveProjector(String name){
        super(name);
        update = true;
        solid = true;
        //2×2；不写 health，走 Block.init() 的默认值 size*size*40 = 160（对应原版没有显式 health）
        size = 2;
        hasPower = true;
        hasItems = true;
        //自身不可被超频（避免叠加）
        canOverdrive = false;
        entityType = OverdriveEntity::new;
    }

    @Override
    public void setStats(){
        super.setStats();

        //对应原版 OverdriveProjector.setStats
        stats.add(BlockStat.speedIncrease, (int)(100f * speedBoost), StatUnit.percent);
        stats.add(BlockStat.range, range / com.phoenix.game.Vars.tilesize, StatUnit.blocks);

        //相织物加成的两条统计项（对应原版 OverdriveProjector.setStats 末尾）
        stats.add(BlockStat.boostEffect, phaseRangeBoost / com.phoenix.game.Vars.tilesize, StatUnit.blocks);
        stats.add(BlockStat.boostEffect, (int)((speedBoost + speedBoostPhase) * 100f), StatUnit.percent);
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    @Override
    public void load(){
        super.load();
        topRegion = Core.atlas == null ? null : Core.atlas.findRegion(name + "-top");
    }

    /**
     * 放置预览：加成范围虚线圈（对应原版 {@code OverdriveProjector.drawPlace}）。
     * <p>⚠ 坐标口径：中心 = 锚点瓦片左下角 + offset() + 半格（原版写 {@code x * tilesize + offset()}，
     * 那里它就是贴图中心），照抄会偏半格。
     */
    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        Drawf.dashCircle(x * tilesize + offset() + tilesize / 2f, y * tilesize + offset() + tilesize / 2f,
            range, Pal.accent);
    }

    /**
     * 悬停时显示加成范围虚线圈（对应原版 {@code OverdriveProjector.drawSelect}）。
     * <p>半径含相织物加成：{@code range + phaseHeat * phaseRangeBoost}（与原版一致）。
     * <p>⚠ 坐标口径同 {@link #drawPlace}；本方块 size=2（2×2），锚点瓦片只是左下角那一格，
     * 所以先 {@code link()} 解析到锚点瓦片，再用 {@link Block#centerX}/{@link Block#centerY} 取整个建筑的几何中心。
     */
    @Override
    public void drawSelect(Tile tile){
        Tile anchor = tile.link();
        float realRange = range;
        if(tile.entity instanceof OverdriveEntity){
            realRange += ((OverdriveEntity)tile.entity).phaseHeat * phaseRangeBoost;
        }
        Drawf.dashCircle(centerX(anchor), centerY(anchor), realRange, baseColor);
    }

    @Override
    public void drawLayer(Tile tile){
        if(topRegion == null || !(tile.entity instanceof OverdriveEntity)) return;
        OverdriveEntity e = (OverdriveEntity)tile.entity;
        if(e.heat <= 0.01f) return;

        float size = this.size * tilesize;
        //颜色渐变对应原版 draw 里的 Draw.color(baseColor, phaseColor, entity.phaseHeat)
        Color c = Tmp.c1.set(baseColor).lerp(phaseColor, e.phaseHeat);
        Core.batch.setColor(c.r, c.g, c.b, Math.min(e.heat, 1f));
        Core.batch.draw(topRegion,
            tile.worldx() + (tilesize - size) / 2f + offset(),
            tile.worldy() + (tilesize - size) / 2f + offset(), size, size);
        Core.batch.setColor(Color.WHITE);
    }

    /**
     * 超频投影器发光（对应原版 {@code OverdriveProjector.drawLight}，见原版 OverdriveProjector.java:70-73）。
     * <p>已接线（S5b 批次）：本项目已移植灯光管线（{@code graphics/LightRenderer}），
     * 本方法由 {@code core/Renderer.drawLights} 逐格调用。
     * <p>⚠ 坐标口径：原版 {@code tile.drawx()/drawy()} 是方块中心；本项目 {@code worldx()/worldy()} 是瓦片角点
     * → 用 {@link Block#centerX}/{@link Block#centerY}。
     */
    @Override
    public void drawLight(Tile tile){
        if(!(tile.entity instanceof OverdriveEntity)) return;
        if(Vars.renderer == null) return;

        OverdriveEntity e = (OverdriveEntity)tile.entity;
        Vars.renderer.lights.add(centerX(tile), centerY(tile), 50f * e.efficiency(), baseColor, 0.7f * e.efficiency());
    }

    public class OverdriveEntity extends TileEntity{
        /** 充能进度（帧）。 */
        public float charge;
        /** 启动热度（0~1）。 */
        public float heat;
        /** 相织物热度（0~1），有相织物时上涨（对应原版 {@code phaseHeat}）。 */
        public float phaseHeat;
        /** 相织物消耗计时器（对应原版 {@code entity.timer.get(timerUse, useTime)}；槽位 0 独占）。 */
        public final Interval timer = new Interval(1);

        @Override
        public void update(){
            heat = Mathf.lerpDelta(heat, cons.valid() ? 1f : 0f, 0.08f);
            charge += heat * delta();

            //相织物升温 + 定时消耗一次（对应原版 update 的前两段；原版这里不判 optionalValid）
            phaseHeat = Mathf.lerpDelta(phaseHeat, cons.optionalValid() ? 1f : 0f, 0.1f);
            if(timer.get(0, useTime) && efficiency() > 0f){
                cons.trigger();
            }

            if(charge < reload) return;
            charge = 0f;

            //相织物生效时范围与倍率都提升（对应原版 realRange / (speedBoost + phaseHeat * speedBoostPhase)）
            boostArea(tile, range + phaseHeat * phaseRangeBoost,
                (speedBoost + phaseHeat * speedBoostPhase) * efficiency());
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeFloat(charge);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            charge = in.readFloat();
            //heat 是每帧向目标插值的视觉量，不存
        }
    }

    /**
     * 给范围内所有友方建筑（且允许超频的）设置时间倍率。
     * <p>参数由调用方算好（含相织物加成与供电率）：{@code realRange = range + phaseHeat * phaseRangeBoost}、
     * {@code realBoost = (speedBoost + phaseHeat * speedBoostPhase) * efficiency()}。
     */
    protected void boostArea(Tile tile, float realRange, float realBoost){
        if(Vars.world == null) return;

        float cx = centerX(tile), cy = centerY(tile);
        //超频波（本项目扩展）：颜色 + 最大半径（波从中心扩到作用范围）
        com.phoenix.game.entities.Effects.effect(com.phoenix.game.content.Fx.overdriveWave,
            com.phoenix.game.graphics.Pal.accent, cx, cy, realRange);
        int tileRange = (int)(realRange / tilesize + 1);

        //原版这里同样有 healed 去重（ltile 会把多格建筑的卫星格解析回同一锚点）；
        //本项目对 timeScale 取 max、本身幂等，重复设置无可观测影响，故省略该集合
        for(int x = -tileRange + tile.x; x <= tileRange + tile.x; x++){
            for(int y = -tileRange + tile.y; y <= tileRange + tile.y; y++){
                //⚠ 坐标口径：本项目 worldx() 是角点，原版那里的 x*tilesize 就是瓦片中心，所以要补半格
                if(!Mathf.within(x * tilesize + tilesize / 2f, y * tilesize + tilesize / 2f, cx, cy, realRange)) continue;

                Tile other = Vars.world.ltile(x, y);
                if(other == null || other.entity == null) continue;
                if(other.getTeam() != tile.getTeam()) continue;
                if(!other.block().canOverdrive) continue;

                //只提升不降低：多个投影器取最大值
                if(other.entity.timeScale <= realBoost){
                    other.entity.timeScaleDuration = Math.max(other.entity.timeScaleDuration, reload + 1f);
                    other.entity.timeScale = Math.max(other.entity.timeScale, realBoost);
                }
            }
        }
    }
}
