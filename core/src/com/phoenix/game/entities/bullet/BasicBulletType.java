package com.phoenix.game.entities.bullet;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.entities.type.Bullet;
import io.anuke.mindustry.gen.Sounds;

/**
 * 最小实现：基础子弹。参照 Mindustry mindustry.entities.bullet.BasicBulletType 移植。
 */
public class BasicBulletType extends BulletType{
    public float width = 5f, height = 8f;
    /** 飞行过程中向内收缩的比例 */
    public float shrinkX = 0.2f, shrinkY = 0.5f;
    public Color backColor = Pal.bulletYellowBack, frontColor = Pal.bulletYellow;

    private TextureRegion backRegion, frontRegion;
    private boolean loaded;

    public BasicBulletType(float speed, float damage){
        super(speed, damage);

        hitEffect = Fx.hit;
        despawnEffect = Fx.hit;
        hitSound = Sounds.pew;
        despawnSound = Sounds.pew;
    }

    private void load(){
        if(loaded) return;
        loaded = true;

        if(Core.atlas != null){
            backRegion = Core.atlas.findRegion("bullet-back");
            frontRegion = Core.atlas.findRegion("bullet");
        }
    }

    @Override
    public void draw(Bullet b){
        load();

        float w = width * (1f - shrinkX * b.fin());
        float h = height * (1f - shrinkY * b.fin());
        float rot = b.rot();

        if(backRegion != null){
            Draw.color(backColor);
            Draw.rect(backRegion, b.x, b.y, w * 1.1f, h * 1.3f, rot - 90f);
        }

        if(frontRegion != null){
            Draw.color(frontColor);
            Draw.rect(frontRegion, b.x, b.y, w, h, rot - 90f);
        }

        Draw.color();
    }
}
