package com.phoenix.game.entities.bullet;

import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.math.Mathf;

/**
 * 防空散弹。参照 Mindustry mindustry.entities.bullet.FlakBulletType 移植。
 * <p>飞行中周期检测 {@link #explodeRange} 内是否有敌方单位；一旦发现就标记为"已引爆"，
 * 5 tick 后强制到期（走 {@code despawned} → {@code hit} → 范围伤害），即"近炸引信"。
 * <p>用子弹的 {@code data} 当状态位：{@code Integer} 表示已引爆（对应原版同样的做法）。
 */
public class FlakBulletType extends BasicBulletType{
    /** 近炸检测半径（世界单位）。 */
    protected float explodeRange = 30f;

    public FlakBulletType(float speed, float damage){
        this(speed, damage, "shell");
    }

    public FlakBulletType(float speed, float damage, String bulletSprite){
        super(speed, damage, bulletSprite);

        splashDamage = 15f;
        splashDamageRadius = 34f;
        hitEffect = Fx.flakExplosionBig;
        width = 8f;
        height = 10f;
    }

    @Override
    public void update(Bullet b){
        super.update(b);

        //已经触发过近炸就不再检测（data 已被置为 Integer）
        if(b.getData() instanceof Integer) return;

        if(b.timer.get(2, 6)){
            Units.nearbyEnemies(b.getTeam(), b.x - explodeRange, b.y - explodeRange, explodeRange * 2f, explodeRange * 2f, unit -> {
                if(b.getData() instanceof Integer) return;

                if(Mathf.dst(b.x, b.y, unit.getX(), unit.getY()) < explodeRange){
                    b.setData(0);
                    //延迟几 tick 再爆，让弹体先飞进目标一点，避免"隔空炸"
                    Time.run(5f, () -> {
                        if(b.getData() instanceof Integer){
                            b.time(b.lifetime());
                        }
                    });
                }
            });
        }
    }
}
