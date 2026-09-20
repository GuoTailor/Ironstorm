package com.phoenix.game.entities.effect;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.game.Team;
import com.phoenix.game.entities.Damage;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;

import static com.phoenix.game.Vars.tilesize;

/**
 * 最小实现：闪电特效。参照 Mindustry mindustry.entities.effect.Lightning 移植。
 */
public class Lightning{
    private static long lastSeed;

    /** 折线闪电特效。data 里存的是段数（Integer）。 */
    public static final Effects.Effect lightning = new Effects.Effect(18f, 64f, e -> {
        int length = e.data instanceof Integer ? (Integer)e.data : 5;
        float fin = e.fin();

        Draw.color(e.color);
        Draw.alpha(1f - fin);

        float px = e.x, py = e.y;
        for(int i = 0; i < length; i++){
            float ang = e.rotation + Mathf.random(-16f, 16f);
            float nx = px + Angles.trnsx(ang, tilesize);
            float ny = py + Angles.trnsy(ang, tilesize);

            TextureRegion region = region();
            if(region != null){
                Draw.rect(region, (px + nx) / 2f, (py + ny) / 2f, tilesize * 1.5f, 2.5f, ang);
            }

            px = nx;
            py = ny;
        }

        Draw.color();
    });

    /** @return a new seed for a lightning effect. */
    public static long nextSeed(){
        return ++lastSeed;
    }

    /** 创建一道闪电，并对落点造成伤害。 */
    public static void createLighting(long seed, Team team, Color color, float damage, float x, float y, float angle, int length){
        Effects.effect(lightning, color, x, y, angle, length);

        if(team != null && damage > 0f){
            float ex = x + Angles.trnsx(angle, length * tilesize);
            float ey = y + Angles.trnsy(angle, length * tilesize);
            Damage.damage(team, ex, ey, tilesize * 1.5f, damage);
        }
    }

    private static TextureRegion region(){
        if(Core.atlas == null) return null;
        TextureRegion region = Core.atlas.findRegion("white");
        if(region == null) region = Core.atlas.findRegion("blank");
        return region;
    }
}
