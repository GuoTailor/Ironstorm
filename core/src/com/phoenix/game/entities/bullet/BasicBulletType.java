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
    /** 贴图前缀（对应原版 {@code bulletSprite}）：找 {@code "<name>-back"} 与 {@code "<name>"}，如 bullet / shell / missile。 */
    public String bulletSprite = "bullet";

    protected TextureRegion backRegion, frontRegion;
    private boolean loaded;
    private String loadedSprite;

    public BasicBulletType(float speed, float damage){
        this(speed, damage, "bullet");
    }

    public BasicBulletType(float speed, float damage, String bulletSprite){
        super(speed, damage);
        this.bulletSprite = bulletSprite;

        hitEffect = Fx.hit;
        despawnEffect = Fx.hit;
        hitSound = Sounds.pew;
        despawnSound = Sounds.pew;
    }

    protected void load(){
        //atlas 可能在构造后才就绪，所以懒加载；换过 bulletSprite 要重新取
        if(loaded && bulletSprite.equals(loadedSprite)) return;
        loaded = true;
        loadedSprite = bulletSprite;

        if(Core.atlas != null){
            backRegion = Core.atlas.findRegion(bulletSprite + "-back");
            frontRegion = Core.atlas.findRegion(bulletSprite);
        }
    }

    /** 供子类复用的贴图绘制（缩放后的宽高与旋转由子类算好传入）。 */
    protected void drawRegions(float x, float y, float w, float h, float rotation){
        if(backRegion != null){
            Draw.color(backColor);
            Draw.rect(backRegion, x, y, w * 1.1f, h * 1.3f, rotation);
        }

        if(frontRegion != null){
            Draw.color(frontColor);
            Draw.rect(frontRegion, x, y, w, h, rotation);
        }

        Draw.color();
    }

    @Override
    public void draw(Bullet b){
        load();

        float w = width * (1f - shrinkX * b.fin());
        float h = height * (1f - shrinkY * b.fin());

        drawRegions(b.x, b.y, w, h, b.rot() - 90f);
    }
}
