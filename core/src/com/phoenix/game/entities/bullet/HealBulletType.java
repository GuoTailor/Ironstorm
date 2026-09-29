package com.phoenix.game.entities.bullet;

import com.badlogic.gdx.graphics.Color;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Lines;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.BuildBlock;

/**
 * 治疗弹。参照 Mindustry mindustry.entities.bullet.HealBulletType 移植。
 * <p><b>与 arc/原版的差异</b>：原版瓦片命中回调是 {@code collides(Bullet, Tile)} / {@code hitTile(Bullet, Tile)}
 * （带 Tile 参数），本项目 {@code BulletType} 已补 {@code collides(Bullet, Tile)}（默认 true，由
 * {@code Bullet.update} 的瓦片射线回调调用）；{@code hitTile(Bullet)} 仍不带 Tile，
 * 由子弹坐标反查瓦片（见下）。
 */
public class HealBulletType extends BulletType{
    protected float healPercent = 3f;

    public HealBulletType(float speed, float damage){
        super(speed, damage);

        shootEffect = Fx.shootHeal;
        smokeEffect = Fx.hitLaser;
        hitEffect = Fx.hitLaser;
        despawnEffect = Fx.hitLaser;
        collidesTeam = true;
    }

    public HealBulletType(){
        this(1f, 1f);
    }

    /** 友方满血建筑不拦截治疗弹（对应原版 {@code collides(Bullet, Tile)}）。 */
    @Override
    public boolean collides(Bullet b, Tile tile){
        return tile.getTeam() != b.getTeam() || tile.entity == null || tile.entity.healthf() < 1f;
    }

    @Override
    public void draw(Bullet b){
        Draw.color(Pal.heal);
        Lines.stroke(2f);
        Lines.lineAngleCenter(b.x, b.y, b.rot(), 7f);
        Draw.color(Color.WHITE);
        Lines.lineAngleCenter(b.x, b.y, b.rot(), 3f);
        //原版是 Draw.reset()；本项目没有该重载，用 Draw.color() 复位到白色（见 ItemTransfer 的同类处理）
        Draw.color();
    }

    @Override
    public void hitTile(Bullet b){
        super.hit(b);

        Tile tile = Vars.world == null ? null : Vars.world.tileWorld(b.x, b.y);
        if(tile == null) return;

        tile = tile.link();

        if(tile.entity != null && tile.getTeam() == b.getTeam() && !(tile.block() instanceof BuildBlock)){
            Effects.effect(Fx.healBlockFull, Pal.heal, tile.block().centerX(tile), tile.block().centerY(tile), tile.block().size);
            tile.entity.healBy(healPercent / 100f * tile.entity.maxHealth());
        }
    }
}
