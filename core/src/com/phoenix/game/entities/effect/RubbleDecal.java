package com.phoenix.game.entities.effect;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.math.Mathf;

/**
 * 方块被摧毁后留下的碎石残骸（长寿命地面贴花）。参照 Mindustry
 * {@code mindustry.entities.effect.RubbleDecal} 移植。
 *
 * <p>行为：从图集取 {@code rubble-<尺寸>-<0|1>}（尺寸 1~4 的两张变体，atlas 里都存在），
 * 随机朝向绘制，寿命 8200 tick，最后一小段淡出（淡出在父类 {@link Decal#draw()} 里做）。
 *
 * <p><b>与原版的差异</b>：
 * <ul>
 *   <li>原版用 {@code Core.atlas.find(...)} + {@code Core.atlas.isFound(region)}（arc 的 atlas 找不到时
 *       返回 error 贴图，再靠 {@code isFound} 判定）。libgdx 的 {@code findRegion} 找不到直接返回
 *       {@code null} → 这里改成判 {@code region == null} 提前 return。</li>
 *   <li>原版 {@code TextureRegion.getWidth()} → 本项目 {@code getRegionWidth()}（libgdx 没有 getWidth）。</li>
 *   <li>原版 {@code decal.add()} 把实体登记进 {@code Vars.groundEffectGroup}；本项目 {@code Vars}
 *       没有该实体组（F6 只接了 player/puddle/fire，且 {@code Vars.java} 本批不可改）→ 改调
 *       {@link Decal#register()} 登记进 {@link Decal#all} 静态列表，
 *       由 {@code Decal.updateAll()}/{@code Decal.drawAll()} 统一更新与绘制（与 {@code Puddle} 同口径）。</li>
 * </ul>
 */
public class RubbleDecal extends Decal{
    private TextureRegion region;

    /** Creates a rubble effect at a position. Provide a block size to use. */
    public static void create(float x, float y, int size){
        if(Vars.headless) return;

        RubbleDecal decal = new RubbleDecal();
        decal.region = Core.atlas == null ? null
            : Core.atlas.findRegion("rubble-" + size + "-" + Mathf.randomSeed(decal.id, 0, 1));

        //原版是 Core.atlas.isFound(region)：libgdx 找不到即 null
        if(decal.region == null){
            return;
        }

        decal.set(x, y);
        //原版 decal.add()（登记进 Vars.groundEffectGroup）；本项目没有该实体组 → 登记进静态列表
        decal.register();
    }

    @Override
    public float lifetime(){
        return 8200f;
    }

    @Override
    void drawDecal(){
        if(region == null){
            remove();
            return;
        }
        Draw.rect(region, x, y, Mathf.randomSeed(id, 0, 4) * 90);
    }

    @Override
    public float drawSize(){
        //原版 region.getWidth()；libgdx 的 TextureRegion 没有 getWidth()
        return region == null ? 20f : region.getRegionWidth() * 3f;
    }
}
