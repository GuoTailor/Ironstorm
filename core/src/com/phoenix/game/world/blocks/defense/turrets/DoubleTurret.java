package com.phoenix.game.world.blocks.defense.turrets;

import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.StatUnit;

import static com.phoenix.game.Vars.tilesize;

/**
 * 双管炮塔：两根炮管左右交替发射（每发横移 {@link #shotWidth}）。参照 Mindustry
 * mindustry.world.blocks.defense.turrets.DoubleTurret 移植。
 * <p>与原版的差异：原版把 {@code shoot(Tile, BulletType)} 写在方块上、由 {@code Block.update(Tile)} 调用；
 * 本项目写在实体内部类（见 {@link DoubleTurretEntity}）。
 */
public class DoubleTurret extends ItemTurret{
    public float shotWidth = 2f;

    public DoubleTurret(String name){
        super(name);
        shots = 2;
        entityType = DoubleTurretEntity::new;
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.remove(BlockStat.reload);
        stats.add(BlockStat.reload, 60f / reload, StatUnit.none);
    }

    /** 炮塔实体：按射击计数左右交替（原版 {@code DoubleTurret.shoot}）。 */
    public class DoubleTurretEntity extends ItemTurretEntity{

        @Override
        protected void shoot(){
            BulletType type = peekAmmo();
            if(type == null) return;

            //注意：这里的 shots 是实体自己的射击计数（对应原版 TurretEntity.shots），
            //不是外层的 Turret.shots（每次装填发几发）；同名遮蔽是原版就有的写法
            shots++;

            int i = Mathf.signs[shots % 2];

            //原版 tr.trns(entity.rotation - 90, shotWidth * i, size * tilesize / 2)：先横移再前推
            float ox = Angles.trnsx(rotation - 90f, shotWidth * i, size * tilesize / 2f);
            float oy = Angles.trnsy(rotation - 90f, shotWidth * i, size * tilesize / 2f);

            Bullet.create(type, this, getTeam(), cx() + ox, cy() + oy, rotation + Mathf.range(inaccuracy));

            effects(cx() + ox, cy() + oy);
            useAmmo();
        }
    }
}
