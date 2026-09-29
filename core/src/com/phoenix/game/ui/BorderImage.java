package com.phoenix.game.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.phoenix.game.core.Scl;
import com.phoenix.game.graphics.Pal;
import io.anuke.mindustry.gen.Tex;

/**
 * 带描边的图片。参照 Mindustry mindustry.ui.BorderImage 移植。
 *
 * <p>原版 {@code draw()} 是 {@code Lines.stroke(Scl.scl(thickness)); Lines.rect(x + imageX, y + imageY,
 * imageWidth * scaleX, imageHeight * scaleY)}。
 *
 * <p><b>本项目的偏离</b>：{@code Lines}/{@code Fill} 底层是 {@code Drawf} 的 ShapeRenderer 会话，
 * 它的投影矩阵固定为**世界相机**（{@code Core.camera.combined}），而 HUD 的 Stage 用的是独立的
 * {@code stageViewport} 相机，在 Stage 里画几何会整体错位。所以这里改为用 batch 铺 4 条白色贴图
 * （与已移植的 {@code ui.Bar}/{@code MinimapFragment} 同一做法）。
 * 另：arc 的 {@code Lines.rect} 在本项目的替代层里没有，也需要自行拼边。
 */
public class BorderImage extends Image{
    public float thickness = 4f;
    public Color borderColor = Pal.gray;

    public BorderImage(){

    }

    public BorderImage(Texture texture){
        super(texture);
    }

    public BorderImage(Texture texture, float thick){
        super(texture);
        thickness = thick;
    }

    public BorderImage(TextureRegion region, float thick){
        super(region);
        thickness = thick;
    }

    public BorderImage border(Color color){
        this.borderColor = color;
        return this;
    }

    @Override
    public void draw(Batch batch, float parentAlpha){
        super.draw(batch, parentAlpha);

        float scaleX = getScaleX();
        float scaleY = getScaleY();

        float x = getX() + getImageX(), y = getY() + getImageY();
        float w = getImageWidth() * scaleX, h = getImageHeight() * scaleY;
        float t = Scl.scl(thickness);

        //对应原版 Draw.color(borderColor); Draw.alpha(parentAlpha)
        batch.setColor(borderColor.r, borderColor.g, borderColor.b, borderColor.a * parentAlpha);

        TextureRegion white = Tex.whiteui.getRegion();
        batch.draw(white, x, y, w, t);                      //下边
        batch.draw(white, x, y + h - t, w, t);              //上边
        batch.draw(white, x, y + t, t, h - t * 2f);         //左边
        batch.draw(white, x + w - t, y + t, t, h - t * 2f); //右边

        //对应原版 Draw.reset()
        batch.setColor(Color.WHITE);
    }
}
