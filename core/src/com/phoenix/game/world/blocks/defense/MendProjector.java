package com.phoenix.game.world.blocks.defense;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Core;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 修复投影器。参照 Mindustry mindustry.world.blocks.defense.MendProjector 移植。
 * <p>每 {@link #reload} 帧把范围内所有**受伤的友方建筑**按 {@link #healPercent}% 最大血量治疗一次。
 * <p>简化：原版的"相织物加成"（可选消耗器提升范围/治疗量）与颜色渐变未移植；
 * 没电时充能停住（{@code cons.valid()} 已包含电力判定）。
 */
public class MendProjector extends Block{
    /** 治疗间隔（帧）。 */
    public float reload = 250f;
    /** 作用半径（世界单位）。 */
    public float range = 60f;
    /** 每次治疗占最大血量的百分比。 */
    public float healPercent = 12f;

    /** 顶盖贴图（区域名 = 名称-top），有电时按热度淡入。 */
    public TextureRegion topRegion;

    public MendProjector(String name){
        super(name);
        update = true;
        solid = true;
        health = 80;
        hasPower = true;
        entityType = MendEntity::new;
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

    /** 绘制顶盖：有电（heat > 0）时淡入。 */
    @Override
    public void drawLayer(Tile tile){
        if(topRegion == null || !(tile.entity instanceof MendEntity)) return;
        MendEntity e = (MendEntity)tile.entity;
        if(e.heat <= 0.01f) return;

        float size = this.size * tilesize;
        Core.batch.setColor(1f, 1f, 1f, Math.min(e.heat, 1f));
        Core.batch.draw(topRegion,
            tile.worldx() + (tilesize - size) / 2f + offset(),
            tile.worldy() + (tilesize - size) / 2f + offset(), size, size);
        Core.batch.setColor(Color.WHITE);
    }

    public class MendEntity extends TileEntity{
        /** 充能进度（帧），满 {@link #reload} 触发一次治疗。 */
        public float charge;
        /** 启动热度（0~1），有电时上涨。 */
        public float heat;

        @Override
        public void update(){
            heat = Mathf.lerpDelta(heat, cons.valid() ? 1f : 0f, 0.08f);
            charge += heat * delta();

            if(charge < reload) return;
            charge = 0f;

            healArea(tile, range, healPercent / 100f);
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

    /** 治疗以该瓦片为中心、半径 range 内所有受伤的友方建筑。 */
    protected void healArea(Tile tile, float range, float percent){
        if(Vars.world == null) return;

        float cx = tile.worldx() + tilesize / 2f, cy = tile.worldy() + tilesize / 2f;
        //治疗波（对应原版 Fx.healWave）
        Effects.effect(com.phoenix.game.content.Fx.healWave, cx, cy);
        int tileRange = (int)(range / tilesize + 1);

        for(int x = -tileRange + tile.x; x <= tileRange + tile.x; x++){
            for(int y = -tileRange + tile.y; y <= tileRange + tile.y; y++){
                //圆形范围（对应原版 Mathf.within）
                if(!Mathf.within(x * tilesize, y * tilesize, cx, cy, range)) continue;

                //ltile 解析多格链接，避免对卫星瓦片重复治疗
                Tile other = Vars.world.ltile(x, y);
                if(other == null || other.entity == null) continue;
                if(other.getTeam() != tile.getTeam()) continue;
                if(other.entity.health() >= other.entity.maxHealth()) continue;

                other.entity.healBy(other.entity.maxHealth() * percent);
                Effects.effect(Fx.spark, other.drawx(), other.drawy(), 0f);
            }
        }
    }
}
