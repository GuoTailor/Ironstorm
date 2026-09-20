package com.phoenix.game.world.blocks.power;

import com.phoenix.game.content.Items;
import com.phoenix.game.entities.Damage;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

/**
 * 冲击反应堆。参照 Mindustry mindustry.world.blocks.power.ImpactReactor 移植。
 * <p>同时**耗电与发电**（原版 {@code outputsPower = consumesPower = true}）：需要 25/帧的启动电力
 * 与脉冲燃料；供电满足率到 1 才会预热（{@link #warmupSpeed}），预热后发电效率为 {@code warmup^5}，
 * 所以从点火到满功率有明显爬坡。被打爆时会剧烈爆炸。
 * <p>简化：原版还要消耗低温流体，本工程未移植液体，故只烧脉冲燃料；
 * 等离子体绘制、爆炸特效链也未移植。
 */
public class ImpactReactor extends Block{
    /** 预热速度（每帧向 1 逼近的比例）。 */
    public float warmupSpeed = 0.001f;
    /** 消耗一个燃料所需帧数。 */
    public float itemDuration = 140f;
    /** 爆炸半径（像素）。 */
    public float explosionRadius = 50f;
    /** 爆炸基础伤害（原版按 ×4 施加）。 */
    public float explosionDamage = 2000f;
    /** 预热低于该值时被摧毁不爆炸。 */
    public float explosionMinWarmup = 0.4f;

    public ImpactReactor(String name){
        super(name);
        update = true;
        solid = true;
        size = 4;
        health = 900;
        hasItems = true;
        itemCapacity = 10;
        consumes.items(new ItemStack(Items.blastCompound, 1));
        entityType = FusionReactorEntity::new;
    }

    /** 发电量按预热曲线缩放（对应原版 {@code powerProduction * productionEfficiency}）。 */
    @Override
    public float getPowerProduction(Tile tile){
        if(!(tile.entity instanceof FusionReactorEntity)) return 0f;
        return powerProduction * ((FusionReactorEntity)tile.entity).productionEfficiency;
    }

    public class FusionReactorEntity extends TileEntity{
        /** 预热进度（0~1）。 */
        public float warmup;
        /** 发电效率 = warmup^5（原版曲线，让点火后缓慢爬升）。 */
        public float productionEfficiency;
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

                if(warmup >= explosionMinWarmup){
                    float cx = block.centerX(tile), cy = block.centerY(tile);
                    //核爆特效（对应原版 Fx.reactorExplosion）
                    Effects.effect(com.phoenix.game.content.Fx.reactorExplosion, cx, cy);
                    Effects.shake(6f, 16f, cx, cy);
                    Damage.damageAll(cx, cy, explosionRadius, explosionDamage * 4f);
                }
            }
            super.kill();
        }

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
