package com.phoenix.game.world.blocks.defense;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 超频投影器。参照 Mindustry mindustry.world.blocks.defense.OverdriveProjector 移植。
 * <p>每 {@link #reload} 帧给范围内所有友方建筑设置 {@code timeScale = speedBoost}
 * （持续 {@code reload + 1} 帧），建筑的 {@code update} 用 {@code delta()} 计时，因此会被加速。
 * <p>简化：原版的"相织物加成"（更高倍率 + 更大范围）未移植。
 */
public class OverdriveProjector extends Block{
    /** 加成刷新间隔（帧）。 */
    public float reload = 60f;
    /** 作用半径（世界单位）。 */
    public float range = 80f;
    /** 速度倍率。 */
    public float speedBoost = 1.5f;

    /** 顶盖贴图（区域名 = 名称-top）。 */
    public TextureRegion topRegion;

    public OverdriveProjector(String name){
        super(name);
        update = true;
        solid = true;
        health = 80;
        hasPower = true;
        //自身不可被超频（避免叠加）
        canOverdrive = false;
        entityType = OverdriveEntity::new;
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

    @Override
    public void drawLayer(Tile tile){
        if(topRegion == null || !(tile.entity instanceof OverdriveEntity)) return;
        OverdriveEntity e = (OverdriveEntity)tile.entity;
        if(e.heat <= 0.01f) return;

        float size = this.size * tilesize;
        Core.batch.setColor(1f, 1f, 1f, Math.min(e.heat, 1f));
        Core.batch.draw(topRegion,
            tile.worldx() + (tilesize - size) / 2f + offset(),
            tile.worldy() + (tilesize - size) / 2f + offset(), size, size);
        Core.batch.setColor(Color.WHITE);
    }

    public class OverdriveEntity extends TileEntity{
        /** 充能进度（帧）。 */
        public float charge;
        /** 启动热度（0~1）。 */
        public float heat;

        @Override
        public void update(){
            heat = Mathf.lerpDelta(heat, cons.valid() ? 1f : 0f, 0.08f);
            charge += heat * delta();

            if(charge < reload) return;
            charge = 0f;

            boostArea(tile, range, speedBoost);
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

    /** 给范围内所有友方建筑（且允许超频的）设置时间倍率。 */
    protected void boostArea(Tile tile, float range, float boost){
        if(Vars.world == null) return;

        float cx = tile.worldx() + tilesize / 2f, cy = tile.worldy() + tilesize / 2f;
        //超频波（对应原版 Fx.overdriveWave）：颜色 + 最大半径（波从中心扩到作用范围）
        com.phoenix.game.entities.Effects.effect(com.phoenix.game.content.Fx.overdriveWave,
            com.phoenix.game.graphics.Pal.accent, cx, cy, range);
        int tileRange = (int)(range / tilesize + 1);

        for(int x = -tileRange + tile.x; x <= tileRange + tile.x; x++){
            for(int y = -tileRange + tile.y; y <= tileRange + tile.y; y++){
                if(!Mathf.within(x * tilesize, y * tilesize, cx, cy, range)) continue;

                Tile other = Vars.world.ltile(x, y);
                if(other == null || other.entity == null) continue;
                if(other.getTeam() != tile.getTeam()) continue;
                if(!other.block().canOverdrive) continue;

                //只提升不降低：多个投影器取最大值
                if(other.entity.timeScale <= boost){
                    other.entity.timeScaleDuration = Math.max(other.entity.timeScaleDuration, reload + 1f);
                    other.entity.timeScale = Math.max(other.entity.timeScale, boost);
                }
            }
        }
    }
}
