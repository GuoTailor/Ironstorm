package com.phoenix.game.world.blocks.defense;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 反射墙（相织物墙）。参照 Mindustry mindustry.world.blocks.defense.DeflectorWall 移植。
 * <p>被"弱子弹"（伤害 ≤ {@link #maxDamageDeflect}）命中时把子弹弹回去
 * （按侵入较浅的轴翻转速度分量 + 标记已偏转），强子弹照常被吸收。
 * <p>简化：原版会沿射线把子弹位置修正到墙外、并改换子弹归属（{@code resetOwner}），
 * 本项目只做翻转 + 标记（子弹仍属原队伍）。
 */
public class DeflectorWall extends Wall{
    /** 受击闪光持续时间（帧）。 */
    public static final float hitTime = 10f;

    /** 可被反射的最大子弹伤害；更强的子弹直接吸收。 */
    public float maxDamageDeflect = 10f;

    public DeflectorWall(String name){
        super(name);
        entityType = DeflectorEntity::new;
    }

    @Override
    public void handleBulletHit(TileEntity entity, Bullet bullet){
        //强子弹不反射；已经偏转过的也不再处理（避免来回弹）
        if(bullet == null || bullet.damage() > maxDamageDeflect || bullet.isDeflected()) return;

        //按侵入较浅的轴翻转（对应原版比较 penX / penY）
        float penX = Math.abs(entity.x - bullet.x);
        float penY = Math.abs(entity.y - bullet.y);

        if(penX > penY){
            bullet.velocity().x *= -1f;
        }else{
            bullet.velocity().y *= -1f;
        }

        bullet.deflect();

        if(entity instanceof DeflectorEntity){
            ((DeflectorEntity)entity).hit = 1f;
        }
    }

    /** 受击闪光：命中后按 {@link #hitTime} 帧淡出。 */
    @Override
    public void drawLayer(Tile tile){
        if(!(tile.entity instanceof DeflectorEntity)) return;
        DeflectorEntity e = (DeflectorEntity)tile.entity;
        if(e.hit < 0.0001f) return;

        TextureRegion white = Core.atlas == null ? null : Core.atlas.findRegion("white");
        if(white == null) return;

        float size = this.size * tilesize;
        Core.batch.setColor(1f, 1f, 1f, e.hit * 0.5f);
        Core.batch.draw(white,
            tile.worldx() + (tilesize - size) / 2f + offset(),
            tile.worldy() + (tilesize - size) / 2f + offset(), size, size);
        Core.batch.setColor(Color.WHITE);

        e.hit = Mathf.clamp(e.hit - Time.delta() / hitTime);
    }

    public class DeflectorEntity extends WallEntity{
        /** 受击闪光强度（0~1）。 */
        public float hit;
    }
}
