package com.phoenix.game.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.phoenix.game.core.Scl;
import com.phoenix.game.func.Boolp;
import com.phoenix.game.graphics.Pal;
import io.anuke.mindustry.gen.Tex;

/**
 * 需求图标：条件不满足时在图标上盖一道斜杠。参照 Mindustry mindustry.ui.ReqImage 移植。
 *
 * <p>两个与原版的差异：
 * <ul>
 *   <li>原版用 arc 的动态可见性 {@code visible(() -> !valid.get())}；libgdx 没有该 API，
 *       改为覆写 {@link Actor#isVisible()}（{@code Group.drawChildren} 会跳过不可见的子 Actor）。</li>
 *   <li>原版用 {@code Lines.line} 画斜杠；本项目 UI 阶段的几何投影矩阵不对（见 {@link BorderImage}），
 *       改为用一根旋转的白色贴图拼出线段。</li>
 * </ul>
 */
public class ReqImage extends Stack{
    private final Boolp valid;

    public ReqImage(Actor image, Boolp valid){
        this.valid = valid;
        add(image);
        add(new Actor(){
            @Override
            public boolean isVisible(){
                return super.isVisible() && !valid.get();
            }

            @Override
            public void draw(Batch batch, float parentAlpha){
                float x = getX(), y = getY(), w = getWidth(), h = getHeight();
                float thick = Scl.scl(2f);

                //原版：Lines.stroke(Scl.scl(2f), Pal.removeBack);
                //      Lines.line(x, y - 2f + height, x + width, y - 2f);
                //      Draw.color(Pal.remove); Lines.line(x, y + height, x + width, y);
                diagonal(batch, x, y + h - 2f, x + w, y - 2f, thick, Pal.removeBack, parentAlpha);
                diagonal(batch, x, y + h, x + w, y, thick, Pal.remove, parentAlpha);

                //原版 Draw.reset()
                batch.setColor(Color.WHITE);
            }
        });
    }

    public ReqImage(TextureRegion region, Boolp valid){
        this(new Image(region), valid);
    }

    public boolean valid(){
        return valid.get();
    }

    /** 用一根旋转的细长白色贴图拼出线段（对应 arc 的 {@code Lines.line}）。 */
    private static void diagonal(Batch batch, float x1, float y1, float x2, float y2, float thick, Color color, float alpha){
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float)Math.sqrt(dx * dx + dy * dy);
        if(len <= 0f) return;

        batch.setColor(color.r, color.g, color.b, color.a * alpha);
        //以 (x1,y1) 为旋转原点、线宽为高度的矩形
        batch.draw(Tex.whiteui.getRegion(), x1, y1 - thick / 2f, 0f, thick / 2f, len, thick, 1f, 1f,
            MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees);
    }
}
