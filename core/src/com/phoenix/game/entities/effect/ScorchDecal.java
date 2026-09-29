package com.phoenix.game.entities.effect;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;

/**
 * 爆炸/激光在地面留下的焦痕贴花。参照 Mindustry
 * {@code mindustry.entities.effect.ScorchDecal} 移植。
 *
 * <p>行为：从图集取 {@code scorch1..scorch5}（atlas 里都有），每次绘制叠 3 个随机变体、
 * 围绕自身随机偏移 + 随机旋转，靠父类的淡出收尾。
 *
 * <p><b>与原版的差异</b>：
 * <ul>
 *   <li>原版"纹理被释放就重新取图"的判据是 {@code regions[0].getTexture().isDisposed()}，
 *       <b>libgdx 的 {@code Texture} 没有 {@code isDisposed()}</b> → 去掉该判定，只判 {@code regions[0] == null}；
 *       另外多了 {@code Core.atlas == null} 与取到 {@code null} 的守卫（libgdx 的 {@code findRegion} 找不到返回 null）。</li>
 *   <li>⚠<b>落点判据刻意加强</b>：原版只跳过"地板带液体产出（{@code tile.floor().liquidDrop != null}）"的瓦片。
 *       本项目 {@code Floor.liquidDrop} **确实存在且已配置**（deepwater/taintedWater/sandWater/…=water，tar=oil，
 *       见 {@code content/Blocks.java:205/246}），但 {@code water} 地板（{@code Blocks.java:185}）只设了
 *       {@code isLiquid} 未设 {@code liquidDrop} → 这里额外判 {@code tile.floor().isLiquid}，
 *       以免焦痕落在水面上（补数据缺口，语义与原版一致）。</li>
 *   <li>原版 {@code TextureRegion.getWidth()/getHeight()} → 本项目 {@code getRegionWidth()/getRegionHeight()}。</li>
 *   <li>原版 {@code decal.add()}（登记进 {@code Vars.groundEffectGroup}）→ 改调 {@link Decal#register()}
 *       登记进 {@link Decal#all} 静态列表，原因同 {@link RubbleDecal}。</li>
 * </ul>
 */
public class ScorchDecal extends Decal{
    private static final int scorches = 5;
    private static final TextureRegion[] regions = new TextureRegion[scorches];

    public static void create(float x, float y){
        if(Vars.headless) return;

        //libgdx 的 Texture 没有 arc 的 isDisposed() → 只判空
        if(regions[0] == null){
            for(int i = 0; i < regions.length; i++){
                regions[i] = Core.atlas == null ? null : Core.atlas.findRegion("scorch" + (i + 1));
            }
        }

        Tile tile = Vars.world == null ? null : Vars.world.tileWorld(x, y);

        //原版只判 liquidDrop；本项目 water 地板未配 liquidDrop → 额外判 isLiquid（见类注释）
        if(tile == null || tile.floor().isLiquid || tile.floor().liquidDrop != null) return;

        ScorchDecal decal = new ScorchDecal();
        decal.set(x, y);
        //原版 decal.add()（登记进 Vars.groundEffectGroup）；本项目没有该实体组 → 登记进静态列表
        decal.register();
    }

    @Override
    void drawDecal(){
        for(int i = 0; i < 3; i++){
            TextureRegion region = regions[Mathf.randomSeed(id - i, 0, scorches - 1)];
            //libgdx 取不到贴图时返回 null（原版是 error 贴图，永远非 null）
            if(region == null) continue;

            float rotation = Mathf.randomSeed(id + i, 0, 360);
            float space = 1.5f + Mathf.randomSeed(id + i + 1, 0, 20) / 10f;
            Draw.rect(region,
            x + Angles.trnsx(rotation, space),
            y + Angles.trnsy(rotation, space) + region.getRegionHeight() / 2f * Draw.scl,
            region.getRegionWidth() * Draw.scl,
            region.getRegionHeight() * Draw.scl,
            region.getRegionWidth() / 2f * Draw.scl, 0, rotation - 90);
        }
    }
}
