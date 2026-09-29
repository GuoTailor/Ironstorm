package com.phoenix.game.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import io.anuke.mindustry.gen.Tex;

/**
 * 网格背景（画 imageWidth × imageHeight 条分隔线）。参照 Mindustry mindustry.ui.GridImage 移植。
 *
 * <p>原版 {@code draw()} 用 {@code Fill.crect(...)} 画线。{@code Fill} 底层是 {@code Drawf} 的
 * ShapeRenderer 会话，投影矩阵固定为**世界相机**，在 HUD 的 Stage（独立相机）里会整体错位 →
 * 这里改为用 batch 铺白色贴图（原因同 {@link BorderImage}）。
 */
public class GridImage extends Actor{
    private int imageWidth, imageHeight;

    public GridImage(int w, int h){
        this.imageWidth = w;
        this.imageHeight = h;
    }

    @Override
    public void draw(Batch batch, float parentAlpha){
        float xspace = (getWidth() / imageWidth);
        float yspace = (getHeight() / imageHeight);
        float s = 1f;

        int minspace = 10;

        int jumpx = (int)(Math.max(minspace, xspace) / xspace);
        int jumpy = (int)(Math.max(minspace, yspace) / yspace);

        Color color = getColor();
        batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
        TextureRegion white = Tex.whiteui.getRegion();

        for(int x = 0; x <= imageWidth; x += jumpx){
            crect(batch, white, (int)(getX() + xspace * x - s), getY() - s, 2, getHeight() + (x == imageWidth ? 1 : 0));
        }

        for(int y = 0; y <= imageHeight; y += jumpy){
            crect(batch, white, getX() - s, (int)(getY() + y * yspace - s), getWidth(), 2);
        }

        batch.setColor(Color.WHITE);
    }

    /** 以 (x,y) 为**中心**铺一块矩形（对应 arc 的 {@code Fill.crect}，libgdx 没有对应原语）。 */
    private static void crect(Batch batch, TextureRegion region, float x, float y, float width, float height){
        batch.draw(region, x - width / 2f, y - height / 2f, width, height);
    }

    public void setImageSize(int w, int h){
        this.imageWidth = w;
        this.imageHeight = h;
    }
}
