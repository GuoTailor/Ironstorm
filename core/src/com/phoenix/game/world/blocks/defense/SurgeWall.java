package com.phoenix.game.world.blocks.defense;

import com.phoenix.game.entities.effect.Lightning;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;

/**
 * 合金墙（浪涌墙）。参照 Mindustry mindustry.world.blocks.defense.SurgeWall 移植。
 * <p>被子弹命中时按 {@link #lightningChance} 的概率释放一道闪电反击。
 */
public class SurgeWall extends Wall{
    /** 每次受击释放闪电的概率。 */
    public float lightningChance = 0.05f;
    /** 闪电伤害。 */
    public float lightningDamage = 15f;
    /** 闪电长度（像素）。 */
    public int lightningLength = 17;

    public SurgeWall(String name){
        super(name);
    }

    @Override
    public void handleBulletHit(TileEntity entity, Bullet bullet){
        if(bullet == null || !Mathf.chance(lightningChance)) return;

        //朝子弹来的方向放电（对应原版 bullet.rot() + 180f）；seed 用系统计时保证每次形状不同
        Lightning.createLighting(
            com.phoenix.game.core.Time.nanos(), entity.getTeam(), com.phoenix.game.graphics.Pal.surge,
            lightningDamage, bullet.x, bullet.y, bullet.rot() + 180f, lightningLength);
    }
}
