package com.phoenix.game.world.blocks.power;

import com.badlogic.gdx.graphics.Color;
import com.phoenix.game.content.Items;
import com.phoenix.game.core.Time;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.entities.Damage;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 冲击反应堆。参照 Mindustry mindustry.world.blocks.power.ImpactReactor 移植。
 * <p>同时**耗电与发电**（原版 {@code outputsPower = consumesPower = true}）：需要 25/帧的启动电力
 * 与脉冲燃料；供电满足率到 1 才会预热（{@link #warmupSpeed}），预热后发电效率为 {@code warmup^5}
 * （赋给 {@link FusionReactorEntity#productionEfficiency}，由 {@link PowerGenerator#getPowerProduction} 折算），
 * 所以从点火到满功率有明显爬坡。被打爆时会剧烈爆炸。
 * <p>刻意偏离：原版还要消耗低温流体（{@code consumes.liquid(cryofluid, 0.25f)}），
 * 本工程 ImpactReactor 历来只烧脉冲燃料、不接液体（避免改动既有玩法与调试图），故保留该偏离。
 * 另：原版 {@code draw} 的等离子体层（{@code -bottom}/{@code -plasma-N} 旋转加色绘制）未移植，
 * 仅保留 {@link FusionReactorEntity#update()} 里间歇放出的淡蓝闪焰；爆炸特效链也做了简化。
 */
public class ImpactReactor extends PowerGenerator{
    /** 预热速度（每帧向 1 逼近的比例）。 */
    public float warmupSpeed = 0.001f;
    /** 消耗一个燃料所需帧数。 */
    public float itemDuration = 140f;
    /** 爆炸半径（格数；调用处乘 {@code tilesize} 换算成世界单位，原版 {@code explosionRadius = 50}）。 */
    public float explosionRadius = 50f;
    /** 爆炸基础伤害（原版按 ×4 施加）。 */
    public float explosionDamage = 2000f;
    /** 预热低于该值时被摧毁不爆炸。 */
    public float explosionMinWarmup = 0.4f;
    /** 等离子体配色（对应原版 {@code ImpactReactor.plasma1/plasma2}；本批随 drawLight 一并补上）。 */
    public Color plasma1 = Color.valueOf("ffd06b"), plasma2 = Color.valueOf("ff361b");

    public ImpactReactor(String name){
        super(name);
        //刻意降级（未引入）：原版构造器还有 hasLiquids = true; liquidCapacity = 30f（并在 Blocks 里
        //     consumes.liquid(cryofluid, 0.25f)）：本项目 ImpactReactor 历来只烧脉冲燃料、不接液体，
        //     本批只收敛继承链，不改它的玩法（改动会连带影响 buildGeneratorTest）
        //TODO（B 类）原版 outputsPower = consumesPower = true：本项目 Block 没有 outputsPower 字段，
        //     且 PowerGraph 不做"产电/耗电"分类（只按 getPowerProduction/getPowerNeeded 求和）→ 补了也无消费方。
        //     耗电由 Blocks 里的 consumes.power(25f) 表达（见 Block.init 把耗电同步回 hasPower/powerConsumption），
        //     发电由 PowerGenerator.getPowerProduction 表达，两者并存即等价于"既耗又产"
        size = 4;
        health = 900;
        hasItems = true;
        itemCapacity = 10;
        consumes.items(new ItemStack(Items.blastCompound, 1));
        entityType = FusionReactorEntity::new;
    }

    /**
     * 等离子体发光（对应原版 {@code ImpactReactor.drawLight}，见原版 ImpactReactor.java:119-123）。
     * <p>已接线（S5b 批次）：本项目已移植灯光管线（{@code graphics/LightRenderer}），
     * 本方法由 {@code core/Renderer.drawLights} 逐格调用。
     * <p>⚠ 坐标口径：原版 {@code tile.drawx()/drawy()} 是方块中心；本项目 {@code worldx()/worldy()} 是瓦片角点
     * → 用 {@link com.phoenix.game.world.Block#centerX}/{@link com.phoenix.game.world.Block#centerY}。
     * <p>⚠ API 差异：原版 {@code Mathf.absin(5, 5f)} 是 arc 的 {@code absin(scl, mag)}（时间取 Time.time）；
     * 本项目 {@code Mathf.absin} 的两参重载是 {@code (time, speed)} 语义不同 → 改写成三参 {@code (Time.time(), scl, mag)}。
     */
    @Override
    public void drawLight(Tile tile){
        if(!(tile.entity instanceof FusionReactorEntity)) return;
        if(com.phoenix.game.Vars.renderer == null) return;

        float warmup = ((FusionReactorEntity)tile.entity).warmup;
        com.phoenix.game.Vars.renderer.lights.add(centerX(tile), centerY(tile),
            (110f + Mathf.absin(Time.time(), 5f, 5f)) * warmup,
            Tmp.c1.set(plasma2).lerp(plasma1, Mathf.absin(Time.time(), 7f, 0.2f)),
            0.8f * warmup);
    }

    /** 冲击反应堆实体（原版 {@code ImpactReactor.FusionReactorEntity}）。 */
    public class FusionReactorEntity extends GeneratorEntity{
        /** 预热进度（0~1）。 */
        public float warmup;
        /** 距下次消耗燃料的计时（帧）。 */
        public float fuelTimer;
        /** 防止爆炸伤害把同一座堆反复引爆。 */
        private boolean exploded;

        @Override
        public void update(){
            //原版判据：消耗器满足 且 供电满足率 >= 0.99（没有启动电力就点不着）
            boolean canRun = cons.valid() && (power == null || power.status >= 0.99f);

            if(canRun){
                warmup = Mathf.lerpDelta(warmup, 1f, warmupSpeed);

                fuelTimer += delta();
                if(fuelTimer >= itemDuration){
                    fuelTimer = 0f;
                    cons.trigger();
                }
            }else{
                warmup = Mathf.lerpDelta(warmup, 0f, 0.01f);
            }

            //发电效率 = warmup^5（原版曲线）：PowerGenerator.getPowerProduction 按它折算发电量
            productionEfficiency = Mathf.pow(warmup, 5f);

            //等离子体：预热后间歇放出淡蓝色闪焰（对应原版冲击反应堆的等离子体效果）
            if(warmup > 0.3f && Mathf.chance(0.25f * warmup * delta())){
                com.phoenix.game.entities.Effects.effect(com.phoenix.game.content.Fx.explosionSmall,
                    com.phoenix.game.graphics.Pal.lancerLaser,
                    block.centerX(tile) + Mathf.range(8f), block.centerY(tile) + Mathf.range(8f), 0f);
            }
        }

        @Override
        public void kill(){
            if(!exploded && tile != null){
                exploded = true;

                //原版：warmup < 0.4f 或 !state.rules.reactorExplosions 时不爆（T2 批已补该 rules 字段）
                if(warmup >= explosionMinWarmup && com.phoenix.game.Vars.state.rules.reactorExplosions){
                    float cx = block.centerX(tile), cy = block.centerY(tile);
                    //核爆特效（对应原版 Fx.reactorExplosion）
                    Effects.effect(com.phoenix.game.content.Fx.reactorExplosion, cx, cy);
                    Effects.shake(6f, 16f, cx, cy);
                    //原版 Damage.damage(tile.worldx(), tile.worldy(), explosionRadius * tilesize, explosionDamage * 4)
                    //（Damage.java:170 的 4 参重载 → damage(null, ..., false)：不分敌我、距离衰减、tileDamage 伤建筑）
                    Damage.damage(cx, cy, explosionRadius * tilesize, explosionDamage * 4f);
                }
            }
            super.kill();
        }

        //注意：productionEfficiency 由基类 GeneratorEntity 负责存档（write/read），本类不要再写一遍（会遮蔽）

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeFloat(warmup);
            out.writeFloat(fuelTimer);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            warmup = in.readFloat();
            fuelTimer = in.readFloat();
            //productionEfficiency = warmup^5，下一帧重算；exploded 不入档
        }
    }
}
