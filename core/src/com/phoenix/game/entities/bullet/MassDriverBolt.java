package com.phoenix.game.entities.bullet;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Core;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.blocks.distribution.MassDriver.DriverBulletData;

/**
 * 质量驱动器的炮弹。参照 Mindustry mindustry.entities.bullet.MassDriverBolt 移植。
 * <p>子弹携带 {@link DriverBulletData}（要投送的物品清单），飞行中判定是否命中目标驱动器：
 * 命中则把物品交给它，未命中则在消失时把物品掉出来。
 */
public class MassDriverBolt extends BulletType{
    /** 判定命中时允许的距离误差。 */
    private static final float hitDst = 7f;

    public MassDriverBolt(){
        super(5.3f, 50f);
        collidesTiles = false;
        lifetime = 200f;
        drag = 0.005f;
    }

    @Override
    public void draw(Bullet b){
        TextureRegion white = Core.atlas == null ? null : Core.atlas.findRegion("white");
        if(white == null) return;

        Core.batch.setColor(1f, 0.83f, 0.5f, 1f);
        Core.batch.draw(white, b.x - 5.5f, b.y - 6.5f, 5.5f, 6.5f, 11f, 13f, 1f, 1f, b.rot() + 90f);
        Core.batch.setColor(Color.WHITE);
    }

    @Override
    public void update(Bullet b){
        //数据不是 DriverBulletData 说明这颗子弹不该走这条路，直接引爆
        if(!(b.getData() instanceof DriverBulletData)){
            hit(b);
            return;
        }

        DriverBulletData data = (DriverBulletData)b.getData();

        //目标或来源没了：让它自然飞完消失
        if(data.to == null || data.from == null || data.to.isDead()) return;

        float baseDst = Vector2.dst(data.from.getX(), data.from.getY(), data.to.getX(), data.to.getY());
        float dst1 = Vector2.dst(b.x, b.y, data.from.getX(), data.from.getY());
        float dst2 = Vector2.dst(b.x, b.y, data.to.getX(), data.to.getY());

        boolean intersect = false;

        //已经飞过了目标点：只有"角度接近"才算擦中（低帧率时把子弹位置拉回目标附近）
        if(dst1 > baseDst){
            float angleTo = Angles.angle(b.x, b.y, data.to.getX(), data.to.getY());
            float baseAngle = Angles.angle(data.to.getX(), data.to.getY(), data.from.getX(), data.from.getY());

            if(Angles.near(angleTo, baseAngle, 2f)){
                intersect = true;
                b.set(data.to.getX() + Angles.trnsx(baseAngle, hitDst),
                      data.to.getY() + Angles.trnsy(baseAngle, hitDst));
            }
        }

        //还在航线上且已进入命中距离
        if(Math.abs(dst1 + dst2 - baseDst) < 4f && dst2 <= hitDst){
            intersect = true;
        }

        if(intersect){
            data.to.handlePayload(b, data);
        }
    }

    @Override
    public void despawned(Bullet b){
        super.despawned(b);

        if(!(b.getData() instanceof DriverBulletData)) return;

        //没送达就随机掉一部分（对应原版按比例掉落）
        DriverBulletData data = (DriverBulletData)b.getData();
        for(int i = 0; i < data.items.length; i++){
            if(data.items[i] > 0 && Mathf.random(0, data.items[i]) > 0){
                Effects.effect(Fx.explosion, b.x, b.y, b.rot());
                break;
            }
        }
    }

    @Override
    public void hit(Bullet b, float x, float y){
        super.hit(b, x, y);
        despawned(b);
    }
}
