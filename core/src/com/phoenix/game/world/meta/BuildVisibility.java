package com.phoenix.game.world.meta;

import com.phoenix.game.Vars;
import com.phoenix.game.func.Boolp;

/**
 * 建造栏可见性。参照原版 mindustry.world.meta.BuildVisibility 移植。
 * <p>{@code campaignOnly}/{@code lightingOnly} 的依赖（{@code Vars.world.isZone()} / {@code rules.lighting}）
 * 均已在项目内存在，判据已按原版还原；{@code sandboxOnly} 用本项目的 {@code rules.sandbox} 对应原版
 * {@code rules.infiniteResources}（本项目无 infiniteResources 字段，两者语义相同）。
 * <p>T2 批起已有消费点：{@code Block.buildVisibility} 字段持有它，{@code Block.isVisible()}/
 * {@code Block.isBuildable()} 与 {@code PlacementFragment} 的建造栏过滤都按它判定
 * （对应原版 {@code Block.java:139/877} 与 {@code PlacementFragment:424}）。
 */
public enum BuildVisibility{
    hidden(() -> false),
    shown(() -> true),
    debugOnly(() -> false),
    //刻意对应：原版依赖 Vars.state.rules.infiniteResources，本项目对应字段是 rules.sandbox（见 Rules.sandbox 注释）
    sandboxOnly(() -> Vars.state.rules.sandbox),
    //已还原原版判据：Vars.world.isZone()（菜单里 Vars.world 为 null，需守卫）
    campaignOnly(() -> Vars.world != null && Vars.world.isZone()),
    //已还原原版判据：Vars.state.rules.lighting
    lightingOnly(() -> Vars.state.rules.lighting);

    private final Boolp visible;

    public boolean visible(){
        return visible.get();
    }

    BuildVisibility(Boolp visible){
        this.visible = visible;
    }
}
