package com.phoenix.game.entities.bullet;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Fill;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.effect.Fire;
import com.phoenix.game.entities.effect.Puddle;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.util.ArcAnnotate.NonNull;
import com.phoenix.game.util.ArcAnnotate.Nullable;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.*;

public class LiquidBulletType extends BulletType{
    public @NonNull Liquid liquid;
    public float puddleSize = 5f;

    public LiquidBulletType(@Nullable Liquid liquid){
        super(3.5f, 0);

        if(liquid != null){
            this.liquid = liquid;
            this.status = liquid.effect;
        }

        lifetime = 74f;
        statusDuration = 90f;
        despawnEffect = Fx.none;
        hitEffect = Fx.hitLiquid;
        smokeEffect = Fx.none;
        shootEffect = Fx.none;
        drag = 0.009f;
        knockback = 0.55f;
    }

    public LiquidBulletType(){
        this(null);
    }

    @Override
    public float range(){
        return speed * lifetime / 2f;
    }

    @Override
    public void update(Bullet b){
        super.update(b);

        if(liquid.canExtinguish()){
            Tile tile = world.tileWorld(b.x, b.y);
            if(tile != null && Fire.has(tile.x, tile.y)){
                Fire.extinguish(tile, 100f);
                b.remove();
                hit(b);
            }
        }
    }

    @Override
    public void draw(Bullet b){
        Draw.color(liquid.color, Color.WHITE, b.fout() / 100f);

        Fill.circle(b.x, b.y, 0.5f + b.fout() * 2.5f);
    }

    @Override
    public void hit(Bullet b, float hitx, float hity){
        Effects.effect(hitEffect, liquid.color, hitx, hity);
        Puddle.deposit(world.tileWorld(hitx, hity), liquid, puddleSize);

        if(liquid.temperature <= 0.5f && liquid.flammability < 0.3f){
            float intensity = 400f;
            Fire.extinguish(world.tileWorld(hitx, hity), intensity);
            //注意：本项目 Geometry.d4 元素顺序与原版相反，这里只是遍历四邻，顺序无影响
            for(GridPoint2 p : Geometry.d4){
                Fire.extinguish(world.tileWorld(hitx + p.x * tilesize, hity + p.y * tilesize), intensity);
            }
        }
    }
}
