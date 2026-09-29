package com.phoenix.game.maps.filters;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Scaling;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Lines;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.func.Cons;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.maps.filters.FilterOption.*;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;

public class MirrorFilter extends GenerateFilter{
    //arc.math.geom.Vec2 → libgdx Vector2
    private final Vector2 v1 = new Vector2(), v2 = new Vector2(), v3 = new Vector2();

    int angle = 45;

    {
        options(new SliderOption("angle", () -> angle, f -> angle = (int)f, 0, 360, 45));
    }

    @Override
    protected void apply(){
        //刻意降级：arc 的 Vec2.trnsExact(angle, len) libgdx 没有 → set(len, 0).rotateDeg(angle)（见 转换.md）
        v1.set(1f, 0f).rotateDeg(angle - 90f);
        v2.set(v1).scl(-1f);

        v1.add(in.width/2f - 0.5f, in.height/2f - 0.5f);
        v2.add(in.width/2f - 0.5f, in.height/2f - 0.5f);

        v3.set(in.x, in.y);

        if(!left(v1, v2, v3)){
            mirror(v3, v1.x, v1.y, v2.x, v2.y);
            Tile tile = in.tile(v3.x, v3.y);
            in.floor = tile.floor();
            if(!tile.block().synthetic()){
                in.block = tile.block();
            }
            in.ore = tile.overlay();
        }
    }

    @Override
    public void draw(Image image){
        super.draw(image);

        //arc 的 Scaling.fit.apply(...) → libgdx com.badlogic.gdx.utils.Scaling.fit.apply(...)（同签名，返回 Vector2）
        Vector2 vsize = Scaling.fit.apply(image.getDrawable().getMinWidth(), image.getDrawable().getMinHeight(), image.getWidth(), image.getHeight());
        float imageWidth = Math.max(vsize.x, vsize.y);
        float imageHeight = Math.max(vsize.y, vsize.x);

        float size = Math.max(image.getWidth() * 2, image.getHeight() * 2);
        //libgdx 的 Vector2 只有两参 clamp(min,max)，没有 arc 的四参版本 → 逐轴用 Mathf.clamp
        Cons<Vector2> clamper = v -> {
            v.x = Mathf.clamp(v.x,
                image.getX() + image.getWidth()/2f - imageWidth/2f,
                image.getX() + image.getWidth()/2f + imageWidth/2f);
            v.y = Mathf.clamp(v.y,
                image.getY() + image.getHeight()/2f - imageHeight/2f,
                image.getY() + image.getHeight()/2f + imageHeight/2f);
        };

        clamper.get(Tmp.v1.set(size, 0f).rotateDeg(angle - 90f).add(image.getWidth()/2f + image.getX(), image.getHeight()/2f + image.getY()));
        clamper.get(Tmp.v2.set(Tmp.v1).sub(image.getWidth()/2f + image.getX(), image.getHeight()/2f + image.getY()).rotateDeg(180f).add(image.getWidth()/2f + image.getX(), image.getHeight()/2f + image.getY()));

        //刻意降级：原版 = Lines.stroke(Scl.scl(3f), Pal.accent)（带颜色重载）；本项目 Lines.stroke 只有单参 → 先 Draw.color 设色
        Draw.color(Pal.accent);
        Lines.stroke(Scl.scl(3f));
        Lines.line(Tmp.v1.x, Tmp.v1.y, Tmp.v2.x, Tmp.v2.y);
        //刻意降级：原版 = Draw.reset()；本项目 Draw 没有 reset() → 用 Draw.color() 复位成白色
        Draw.color();
    }

    void mirror(Vector2 p, float x0, float y0, float x1, float y1){
        //special case: uneven map mirrored at 45 degree angle
        if(in.width != in.height && angle % 90 != 0){
            p.x = (p.x - in.width/2f) * -1 + in.width/2f;
            p.y = (p.y - in.height/2f) * -1 + in.height/2f;
        }else{
            float dx = x1 - x0;
            float dy = y1 - y0;

            float a = (dx * dx - dy * dy) / (dx * dx + dy * dy);
            float b = 2 * dx * dy / (dx * dx + dy * dy);

            p.set((a * (p.x - x0) + b * (p.y - y0) + x0), (b * (p.x - x0) - a * (p.y - y0) + y0));
        }
    }

    boolean left(Vector2 a, Vector2 b, Vector2 c){
        return ((b.x - a.x)*(c.y - a.y) - (b.y - a.y)*(c.x - a.x)) > 0;
    }
}
