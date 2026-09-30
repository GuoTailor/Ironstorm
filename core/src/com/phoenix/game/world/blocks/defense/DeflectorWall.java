package com.phoenix.game.world.blocks.defense;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 反射墙（相织物墙）。参照 Mindustry mindustry.world.blocks.defense.DeflectorWall 移植。
 * <p>被"弱子弹"（伤害 ≤ {@link #maxDamageDeflect}）命中时把子弹弹回去
 * （沿速度射线把位置修正到墙外 + 按侵入较浅的轴翻转速度分量 + 改换归属为反射墙 + 标记已偏转），
 * 强子弹照常被吸收。
 */
public class DeflectorWall extends Wall{
    /** 受击闪光持续时间（帧）。 */
    public static final float hitTime = 10f;

    /** 可被反射的最大子弹伤害；更强的子弹直接吸收。 */
    public float maxDamageDeflect = 10f;

    /** 射线投射用的临时矩形（对应原版 {@code rect}/{@code rect2}）。 */
    protected Rectangle rect = new Rectangle();
    protected Rectangle rect2 = new Rectangle();

    public DeflectorWall(String name){
        super(name);
        entityType = DeflectorEntity::new;
    }

    @Override
    public void handleBulletHit(TileEntity entity, Bullet bullet){
        super.handleBulletHit(entity, bullet);

        //强子弹不反射；已经偏转过的也不再处理（避免来回弹）
        if(bullet == null || bullet.damage() > maxDamageDeflect || bullet.isDeflected()) return;

        float penX = Math.abs(entity.x - bullet.x), penY = Math.abs(entity.y - bullet.y);

        bullet.hitbox(rect2);

        //沿子弹速度射线把位置修正到墙外（对应原版 Geometry.raycastRect(...) + bullet.set）
        Vector2 position = Geometry.raycastRect(
            bullet.x - bullet.velocity().x * Time.delta(), bullet.y - bullet.velocity().y * Time.delta(),
            bullet.x + bullet.velocity().x * Time.delta(), bullet.y + bullet.velocity().y * Time.delta(),
            rect.setSize(size * tilesize + rect2.width * 2 + rect2.height * 2).setCenter(entity.x, entity.y));

        if(position != null){
            bullet.set(position.x, position.y);
        }

        //按侵入较浅的轴翻转（对应原版比较 penX / penY）
        if(penX > penY){
            bullet.velocity().x *= -1f;
        }else{
            bullet.velocity().y *= -1f;
        }

        //改换归属（owner + team）：弹回的子弹属于反射墙 —— BaseUnit.onHit 的 DeflectorEntity 分支据此生效
        bullet.resetOwner(entity, entity.getTeam());
        bullet.scaleTime(1f);
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
