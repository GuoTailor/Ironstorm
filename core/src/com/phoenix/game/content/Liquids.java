package com.phoenix.game.content;

import com.badlogic.gdx.graphics.Color;
import com.phoenix.game.type.Liquid;

/**
 * 液体定义集合。参照 Mindustry mindustry.content.Liquids 移植。
 * <p>id 顺序 = 本类的创建顺序（由 {@code ContentLoader} 注册分配），与原版加载顺序一致。
 * <p>slag 挂 {@code StatusEffects.melting}、oil 挂 {@code StatusEffects.tarred}（与原版一致）。
 * 消费点：{@code Puddle#update} 里 {@code unit.applyEffect(liquid.effect, ...)}——水洼覆盖的单位会中对应状态。
 */
public class Liquids implements com.phoenix.game.ctype.ContentList{
    public static Liquid water, slag, oil, cryofluid;

    @Override
    public void load(){
        water = new Liquid("water", Color.valueOf("596ab8")){{
            heatCapacity = 0.4f;
            effect = StatusEffects.wet;
        }};

        slag = new Liquid("slag", Color.valueOf("ffa166")){{
            temperature = 1f;
            viscosity = 0.8f;
            effect = StatusEffects.melting;
            //libgdx 的 Color 没有 arc 的 .a(float)（返回 Color）→ 先取色再改 alpha
            lightColor = Color.valueOf("f0511d");
            lightColor.a = 0.4f;
        }};

        oil = new Liquid("oil", Color.valueOf("313131")){{
            viscosity = 0.7f;
            flammability = 1.2f;
            explosiveness = 1.2f;
            heatCapacity = 0.7f;
            barColor = Color.valueOf("6b675f");
            effect = StatusEffects.tarred;
        }};

        cryofluid = new Liquid("cryofluid", Color.valueOf("6ecdec")){{
            heatCapacity = 0.9f;
            temperature = 0.25f;
            effect = StatusEffects.freezing;
            //libgdx 的 Color 没有 arc 的 .a(float)（返回 Color）→ 先取色再改 alpha
            lightColor = Color.valueOf("0097f5");
            lightColor.a = 0.2f;
        }};
    }
}
