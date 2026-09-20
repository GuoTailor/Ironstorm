package com.phoenix.game.content;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;

/**
 * 最小实现：特效定义集合。参照 Mindustry mindustry.content.Fx 移植。
 * 使用 sprites.atlas 中的 circle / white 贴图做简化绘制。
 */
public class Fx{
    public static final Effects.Effect none = new Effects.Effect(0f, 28f, e -> {});

    /** 爆炸 */
    public static final Effects.Effect explosion = new Effects.Effect(28f, 48f, e -> {
        float f = e.fin();
        Draw.color(e.color, Color.WHITE, f);
        Draw.alpha(1f - f);
        rect("circle", e.x, e.y, 48f * f + 4f, 48f * f + 4f, 0f);
        Draw.color();
    });

    /** 命中 */
    public static final Effects.Effect hit = new Effects.Effect(10f, 10f, e -> {
        float f = e.fin();
        Draw.color(e.color, Color.WHITE, f);
        Draw.alpha(1f - f);
        rect("circle", e.x, e.y, 10f * (1f - f) + 3f, 10f * (1f - f) + 3f, e.rotation);
        Draw.color();
    });

    /** 烟雾 */
    public static final Effects.Effect smoke = new Effects.Effect(45f, 30f, e -> {
        float f = e.fin();
        Draw.color(e.color, Color.DARK_GRAY, f);
        Draw.alpha((1f - f) * 0.65f);
        rect("circle", e.x, e.y + f * 6f, 12f * f + 5f, 12f * f + 5f, e.rotation);
        Draw.color();
    });

    /** 火焰 */
    public static final Effects.Effect fire = new Effects.Effect(30f, 20f, e -> {
        float f = e.fin();
        Draw.color(Color.valueOf("ffdd55"), Color.valueOf("db401c"), f);
        Draw.alpha(1f - f);
        rect("circle", e.x, e.y + f * 4f, 14f * (1f - f * 0.5f), 20f * (1f - f * 0.5f), e.rotation);
        Draw.color();
    });

    /** 火花 */
    public static final Effects.Effect spark = new Effects.Effect(18f, 10f, e -> {
        float f = e.fin();
        Draw.color(e.color, Color.WHITE, f);
        Draw.alpha(1f - f);
        rect("white", e.x, e.y, 12f * (1f - f) + 2f, 3f, e.rotation);
        Draw.color();
    });

    /** 水面涟漪 */
    public static final Effects.Effect ripple = new Effects.Effect(35f, 20f, e -> {
        float f = e.fin();
        Draw.color(e.color);
        Draw.alpha((1f - f) * 0.6f);
        rect("circle", e.x, e.y, 20f * f + 4f, 20f * f + 4f, 0f);
        Draw.color();
    });

    /** 气泡（溺水） */
    public static final Effects.Effect bubble = new Effects.Effect(20f, 8f, e -> {
        float f = e.fin();
        Draw.color(e.color);
        Draw.alpha((1f - f) * 0.7f);
        rect("circle", e.x + Mathf.range(3f), e.y + f * 8f, 5f, 5f, 0f);
        Draw.color();
    });

    /** 炮口闪光 */
    public static final Effects.Effect shootSmall = new Effects.Effect(8f, 12f, e -> {
        float f = e.fin();
        Draw.color(Color.valueOf("ffeec9"), e.color, f);
        Draw.alpha(1f - f);
        rect("white", e.x, e.y, 14f * (1f - f) + 3f, 4f, e.rotation);
        Draw.color();
    });

    private static void rect(String sprite, float x, float y, float w, float h, float rotation){
        TextureRegion region = region(sprite);
        if(region != null){
            Draw.rect(region, x, y, w, h, rotation);
        }
    }

    private static TextureRegion region(String name){
        if(Core.atlas == null) return null;
        TextureRegion region = Core.atlas.findRegion(name);
        if(region == null) region = Core.atlas.findRegion("blank");
        return region;
    }
}
