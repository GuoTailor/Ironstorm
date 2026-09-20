package com.phoenix.game.world.blocks.defense;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Core;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 力场投影器。参照 Mindustry mindustry.world.blocks.defense.ForceProjector 移植。
 * <p>护盾值（{@link #breakage}）吸收命中自身的子弹伤害；护盾耗尽后进入"破碎"状态，
 * 回充速度降到 {@link #cooldownBrokenBase}，充满后恢复。
 * <p>简化：原版护盾覆盖**半径内的友方建筑**（需要子弹在飞行途中查询护盾），
 * 本项目改为**只保护自身**；液体冷却与相织物加成未移植。
 */
public class ForceProjector extends Block{
    /** 护盾总量。 */
    public float breakage = 550f;
    /** 正常回充速度（每帧）。 */
    public float cooldownNormal = 1.75f;
    /** 破碎后的回充速度。 */
    public float cooldownBrokenBase = 0.35f;
    /** 护盾半径（世界单位，仅用于绘制）。 */
    public float radius = 101.7f;

    /** 顶盖贴图（区域名 = 名称-top）。 */
    public TextureRegion topRegion;

    public ForceProjector(String name){
        super(name);
        update = true;
        solid = true;
        health = 400;
        hasPower = true;
        entityType = ShieldEntity::new;
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

    /** 用半透明白块近似护盾力场（原版是六边形）。 */
    @Override
    public void drawLayer(Tile tile){
        if(!(tile.entity instanceof ShieldEntity)) return;
        ShieldEntity e = (ShieldEntity)tile.entity;
        if(e.broken || e.shield <= 0.01f) return;

        TextureRegion white = Core.atlas == null ? null : Core.atlas.findRegion("white");
        if(white == null) return;

        float size = this.size * tilesize;
        float alpha = Mathf.clamp(e.shield / breakage) * 0.35f;

        Core.batch.setColor(0.6f, 0.85f, 1f, alpha);
        Core.batch.draw(white,
            tile.worldx() + (tilesize - size) / 2f + offset(),
            tile.worldy() + (tilesize - size) / 2f + offset(), size, size);
        Core.batch.setColor(Color.WHITE);
    }

    /** 护盾吸收命中自身的子弹：护盾没破就不掉血（子弹照常被消耗）。 */
    @Override
    public void handleBulletHit(TileEntity entity, Bullet bullet){
        if(!(entity instanceof ShieldEntity) || bullet == null) return;

        ShieldEntity e = (ShieldEntity)entity;
        if(e.broken || e.shield <= 0f) return;

        e.shield -= bullet.damage();

        if(e.shield <= 0f){
            e.shield = 0f;
            e.broken = true;
            //护盾破碎：以建筑为中心扩散一圈六边形碎环（对应原版 Fx.shieldBreak，半径取护盾半径）
            Effects.effect(com.phoenix.game.content.Fx.shieldBreak,
                centerX(entity.tile()), centerY(entity.tile()), radius);
            Effects.effect(Fx.explosion, bullet.x, bullet.y, 0f);
        }else{
            Effects.effect(com.phoenix.game.content.Fx.hitBulletSmall, bullet.x, bullet.y, bullet.rot());
        }

        //标记"已拦截"：Bullet 的命中回调在扣血之前，这样护盾期间建筑本体不掉血
        bullet.deflect();
    }

    public class ShieldEntity extends TileEntity{
        /** 当前护盾值。 */
        public float shield = breakage;
        /** 护盾是否已破碎（破碎后回充更慢）。 */
        public boolean broken;

        @Override
        public void update(){
            //没电不回充
            if(!cons.valid()) return;

            float rate = broken ? cooldownBrokenBase : cooldownNormal;
            shield = Math.min(shield + rate * delta(), breakage);

            if(broken && shield >= breakage){
                broken = false;
            }
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeFloat(shield);
            out.writeBoolean(broken);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            shield = in.readFloat();
            broken = in.readBoolean();
        }
    }
}
