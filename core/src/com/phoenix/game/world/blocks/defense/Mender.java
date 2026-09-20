package com.phoenix.game.world.blocks.defense;

import com.phoenix.game.content.Fx;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 修复器。参照 Mindustry mindustry.world.blocks.defense.Mender 移植。
 * <p>与 {@link MendProjector} 的分工：投影器治疗**建筑**，修复器治疗**单位**。
 * 每 {@link #reload} 帧把范围内所有受伤的友方单位按 {@link #healPercent}% 最大血量治疗一次。
 */
public class Mender extends Block{
    /** 治疗间隔（帧）。 */
    public float reload = 200f;
    /** 作用半径（世界单位）。 */
    public float range = 50f;
    /** 每次治疗占最大血量的百分比。 */
    public float healPercent = 10f;

    public Mender(String name){
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

    public class MendEntity extends TileEntity{
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

            healUnits(tile, range, healPercent / 100f);
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

    /** 治疗范围内所有受伤的友方单位。 */
    protected void healUnits(Tile tile, float range, float percent){
        float cx = tile.worldx() + tilesize / 2f, cy = tile.worldy() + tilesize / 2f;
        //治疗波（对应原版 Fx.healWave）：每个治疗周期放一次，不会刷屏
        Effects.effect(com.phoenix.game.content.Fx.healWave, cx, cy);

        //下标遍历：libgdx Array 的迭代器不能嵌套
        for(int i = 0; i < Units.units.size; i++){
            BaseUnit unit = Units.units.get(i);
            if(unit.isDead() || unit.getTeam() != tile.getTeam()) continue;
            if(Mathf.dst(unit.x, unit.y, cx, cy) > range) continue;
            if(unit.health() >= unit.maxHealth()) continue;

            unit.healBy(unit.maxHealth() * percent);
            Effects.effect(Fx.spark, unit.x, unit.y, 0f);
        }
    }
}
