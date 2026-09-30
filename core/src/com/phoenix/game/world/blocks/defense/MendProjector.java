package com.phoenix.game.world.blocks.defense;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.IntSet;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Drawf;
import com.phoenix.game.core.Interval;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.StatUnit;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 修复投影器。参照 Mindustry mindustry.world.blocks.defense.MendProjector 移植。
 * <p>每 {@link #reload} 帧把范围内所有**受伤的友方建筑**按 {@link #healPercent}% 最大血量治疗一次。
 * <p>相织物加成（可选消耗器，对应原版）：库存里有相织物时 {@link MendEntity#phaseHeat} 升温 →
 * 范围 +{@link #phaseRangeBoost}、单次治疗量 +{@link #phaseBoost}%，颜色从 {@link #baseColor} 渐变到
 * {@link #phaseColor}；每 {@link #useTime} 帧消耗一次相织物。
 * <p>方块 `mend-projector`（size=2）与 `mender`（size=1、便宜款）共用本类 —— 与原版一致，
 * size / health 由 Blocks.java 显式给，不在构造器里写死。
 */
public class MendProjector extends Block{
    /**
     * 单趟治疗中已处理过的目标瓦片（{@link Tile#pos()}）。
     * <p>去重的必要性：{@code Vars.world.ltile(x, y)} 会把一个多格建筑的每个卫星格都解析回
     * **同一个锚点瓦片**，同一目标会在这一趟里被命中 N 次（2×2 建筑吃 4 倍治疗量），所以照原版用集合挡掉。
     */
    private static final IntSet healed = new IntSet();

    /** 治疗间隔（帧）。 */
    public float reload = 250f;
    /** 作用半径（世界单位）。 */
    public float range = 60f;
    /** 每次治疗占最大血量的百分比。 */
    public float healPercent = 12f;
    /** 相织物加成：额外治疗量（百分点，对应原版 {@code phaseBoost}）。 */
    public float phaseBoost = 12f;
    /** 相织物加成：额外范围（世界单位，对应原版 {@code phaseRangeBoost}）。 */
    public float phaseRangeBoost = 50f;
    /** 相织物单次消耗的间隔（帧，对应原版 {@code useTime}）。 */
    public float useTime = 400f;

    /** 范围指示圈的颜色（对应原版 {@code MendProjector.baseColor}）。 */
    public Color baseColor = Color.valueOf("84f491");
    /** 相织物生效时渐变到的颜色（对应原版 {@code MendProjector.phaseColor}）。 */
    public Color phaseColor = Color.valueOf("ffd59e");

    /** 顶盖贴图（区域名 = 名称-top），有电时按热度淡入。 */
    public TextureRegion topRegion;

    public MendProjector(String name){
        super(name);
        update = true;
        solid = true;
        //size / health 外移：同一类的 mender 是 size=1 / health=80，mend-projector 是 size=2 /
        //health=80*size*size —— 两者都在 Blocks.java 里显式写（对应原版）
        hasPower = true;
        hasItems = true;
        entityType = MendEntity::new;
    }

    @Override
    public void setStats(){
        super.setStats();

        //对应原版 MendProjector.setStats：修满 100% 血量所需秒数 + 作用范围
        if(healPercent > 0f){
            stats.add(BlockStat.repairTime, (int)(100f / healPercent * reload / 60f), StatUnit.seconds);
        }
        stats.add(BlockStat.range, range / com.phoenix.game.Vars.tilesize, StatUnit.blocks);

        //相织物加成的两条统计项（对应原版 MendProjector.setStats 末尾）
        stats.add(BlockStat.boostEffect, phaseRangeBoost / com.phoenix.game.Vars.tilesize, StatUnit.blocks);
        stats.add(BlockStat.boostEffect, (phaseBoost + healPercent) / healPercent, StatUnit.timesSpeed);
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
     * 放置预览：修复范围虚线圈（对应原版 {@code MendProjector.drawPlace}）。
     * <p>⚠ 坐标口径：中心 = 锚点瓦片左下角 + offset() + 半格（原版写 {@code x * tilesize + offset()}，
     * 那里它就是贴图中心），照抄会偏半格。
     */
    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        Drawf.dashCircle(x * tilesize + offset() + tilesize / 2f, y * tilesize + offset() + tilesize / 2f,
            range, Pal.accent);
    }

    /**
     * 悬停时显示修复范围虚线圈（对应原版 {@code MendProjector.drawSelect}）。
     * <p>半径含相织物加成：{@code range + phaseHeat * phaseRangeBoost}（与原版一致）。
     * <p>⚠ 坐标口径同 {@link #drawPlace}；本类方块是 2×2（mender 是 1×1），锚点瓦片只是左下角那一格，
     * 所以先 {@code link()} 解析到锚点瓦片，再用 {@link Block#centerX}/{@link Block#centerY} 取整个建筑的几何中心。
     */
    @Override
    public void drawSelect(Tile tile){
        Tile anchor = tile.link();
        float realRange = range;
        if(tile.entity instanceof MendEntity){
            realRange += ((MendEntity)tile.entity).phaseHeat * phaseRangeBoost;
        }
        Drawf.dashCircle(centerX(anchor), centerY(anchor), realRange, baseColor);
    }

    /** 绘制顶盖：有电（heat > 0）时淡入；颜色随相织物从 baseColor 渐变到 phaseColor。 */
    @Override
    public void drawLayer(Tile tile){
        if(topRegion == null || !(tile.entity instanceof MendEntity)) return;
        MendEntity e = (MendEntity)tile.entity;
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
     * 修复投影器发光（对应原版 {@code MendProjector.drawLight}，见原版 MendProjector.java:135-138）。
     * <p>已接线（S5b 批次）：本项目已移植灯光管线（{@code graphics/LightRenderer}），
     * 本方法由 {@code core/Renderer.drawLights} 逐格调用。
     * <p>⚠ 坐标口径：原版 {@code tile.drawx()/drawy()} 是方块中心；本项目 {@code worldx()/worldy()} 是瓦片角点
     * → 用 {@link Block#centerX}/{@link Block#centerY}。
     */
    @Override
    public void drawLight(Tile tile){
        if(!(tile.entity instanceof MendEntity)) return;
        if(Vars.renderer == null) return;

        MendEntity e = (MendEntity)tile.entity;
        Vars.renderer.lights.add(centerX(tile), centerY(tile), 50f * e.efficiency(), baseColor, 0.7f * e.efficiency());
    }

    public class MendEntity extends TileEntity{
        /** 充能进度（帧），满 {@link #reload} 触发一次治疗。 */
        public float charge;
        /** 启动热度（0~1），有电时上涨。 */
        public float heat;
        /** 相织物热度（0~1），有相织物时上涨（对应原版 {@code phaseHeat}）。 */
        public float phaseHeat;
        /** 相织物消耗计时器（对应原版 {@code entity.timer.get(timerUse, useTime)}；槽位 0 独占）。 */
        public final Interval timer = new Interval(1);

        @Override
        public void update(){
            //照原版：作弊 AI 的敌方投影器即使没通电也按"有效"升温（tile.isEnemyCheat()）
            heat = Mathf.lerpDelta(heat, cons.valid() || tile.isEnemyCheat() ? 1f : 0f, 0.08f);
            charge += heat * delta();

            //相织物升温 + 定时消耗一次（对应原版 update 的前两段；注意先判 optionalValid 再 trigger）
            phaseHeat = Mathf.lerpDelta(phaseHeat, cons.optionalValid() ? 1f : 0f, 0.1f);
            if(cons.optionalValid() && timer.get(0, useTime) && efficiency() > 0f){
                cons.trigger();
            }

            if(charge < reload) return;
            charge = 0f;

            //相织物生效时范围与治疗量都提升（对应原版 realRange / healPercent + phaseHeat * phaseBoost）
            healArea(tile, range + phaseHeat * phaseRangeBoost,
                (healPercent + phaseHeat * phaseBoost) / 100f, phaseHeat);
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

    /** 治疗以该瓦片为中心、半径 range 内所有受伤的友方建筑。{@code phaseHeat} 只用于命中特效配色。 */
    protected void healArea(Tile tile, float range, float percent, float phaseHeat){
        if(Vars.world == null) return;

        float cx = centerX(tile), cy = centerY(tile);
        //供电率：照原版治疗量要乘 entity.efficiency()（与 OverdriveProjector 的写法一致）
        float efficiency = tile.entity == null ? 1f : tile.entity.efficiency();
        //治疗波（本项目扩展：每个治疗周期放一次的波纹）
        Effects.effect(Fx.healWave, cx, cy);
        int tileRange = (int)(range / tilesize + 1);
        //每趟清空去重集合（对应原版 healed.clear()）
        healed.clear();

        for(int x = -tileRange + tile.x; x <= tileRange + tile.x; x++){
            for(int y = -tileRange + tile.y; y <= tileRange + tile.y; y++){
                //圆形范围（对应原版 Mathf.within）。⚠ 坐标口径：本项目 worldx() 是角点，
                //原版那里的 x*tilesize 就是瓦片中心，所以要补半格
                if(!Mathf.within(x * tilesize + tilesize / 2f, y * tilesize + tilesize / 2f, cx, cy, range)) continue;

                Tile other = Vars.world.ltile(x, y);
                if(other == null || other.entity == null) continue;
                if(other.getTeam() != tile.getTeam()) continue;
                if(other.entity.health() >= other.entity.maxHealth()) continue;
                //去重：ltile 把同一多格建筑的卫星格都解析回同一个锚点瓦片，不去重会重复治疗
                if(healed.contains(other.pos())) continue;

                //照原版：治疗量 × 供电率（efficiency）
                other.entity.healBy(other.entity.maxHealth() * percent * efficiency);
                //命中特效（对应原版 Fx.healBlockFull）：颜色随相织物从 baseColor 渐变到 phaseColor，
                //rotation 传 block.size（特效内部 × tilesize，画出整块建筑的方形闪光）
                Effects.effect(Fx.healBlockFull, Tmp.c1.set(baseColor).lerp(phaseColor, phaseHeat),
                    other.block().centerX(other), other.block().centerY(other), other.block().size);
                healed.add(other.pos());
            }
        }
    }
}
