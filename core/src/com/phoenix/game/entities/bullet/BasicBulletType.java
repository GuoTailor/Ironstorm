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
    /** 子弹贴图尺寸（对应原版 {@code bulletWidth/bulletHeight}，本项目字段名沿用 width/height）。 */
    public float width = 5f, height = 7f;
    /** 飞行末段保留的高度比例（对应原版 {@code bulletShrink}）：高度按 {@code (1-shrinkY)+shrinkY*fout()} 收缩，宽度恒定。 */
    public float shrinkY = 0.5f;
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

    @Override
    public void load(){
        //atlas 可能在构造后才就绪，所以懒加载；换过 bulletSprite 要重新取
        if(loaded && bulletSprite.equals(loadedSprite)) return;
        loaded = true;
        loadedSprite = bulletSprite;

        if(Core.atlas != null){
            backRegion = Core.atlas.findRegion(bulletSprite + "-back");
            frontRegion = Core.atlas.findRegion(bulletSprite);
        }
    }

    /**
     * 供子类复用的贴图绘制（宽高与旋转由子类算好传入）。
     * <p>照原版 {@code BasicBulletType.draw}：back/front **同尺寸**叠画（back 在底、front 在上），
     * 不再给 back 额外放大。
     */
    protected void drawRegions(float x, float y, float w, float h, float rotation){
        if(backRegion != null){
            Draw.color(backColor);
            Draw.rect(backRegion, x, y, w, h, rotation);
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

        //照原版：宽恒定，高按 fout() 从 height 收缩到 height*(1-shrinkY)
        float h = height * ((1f - shrinkY) + shrinkY * b.fout());

        drawRegions(b.x, b.y, width, h, b.rot() - 90f);
    }
}
