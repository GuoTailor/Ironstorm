package com.phoenix.game.entities.effect;

import com.badlogic.gdx.utils.Array;
import com.phoenix.game.core.Draw;
import com.phoenix.game.entities.traits.BelowLiquidTrait;
import com.phoenix.game.entities.traits.DrawTrait;
import com.phoenix.game.entities.type.TimedEntity;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Mathf;

/**
 * 地面残骸（方块被摧毁后留下的碎块）。参照 Mindustry mindustry.entities.effect.Decal 移植。
 * <p>生命周期很长（3600 tick），最后一小段才淡出。
 * <p>刻意偏离：原版 {@code targetGroup()} 返回 {@code Vars.groundEffectGroup}，
 * 本项目 {@code Vars} 没有该实体组（F6 只接了 player/puddle/fire 三个组），
 * 所以仍省略该覆盖（与 {@code EffectEntity} 的处理一致）。
 * <p><b>怎么被更新/绘制</b>（对应原版 {@code groundEffectGroup.update()/draw()}）：
 * 本项目没有该实体组，改走与 {@link Puddle} 相同的口径 —— 静态列表 {@link #all} +
 * {@link #updateAll()}/{@link #drawAll()}，分别由 {@code core.Logic.update()} 与
 * {@code core.Renderer} 调用（原版里 decal 属于 BelowLiquidTrait，画在**地板之上、方块之下**）。
 */
public abstract class Decal extends TimedEntity implements BelowLiquidTrait, DrawTrait{
    /**
     * 场上所有贴花。对应原版 {@code Vars.groundEffectGroup} 里 {@code BelowLiquidTrait} 的那部分
     * （原版 {@code groundEffectGroup.draw(e -> e instanceof BelowLiquidTrait)}）。
     */
    public static final Array<Decal> all = new Array<>();

    @Override
    public float lifetime(){
        return 3600;
    }

    @Override
    public void draw(){
        Draw.color(Pal.rubble.r, Pal.rubble.g, Pal.rubble.b, 1f - Mathf.curve(fin(), 0.98f));
        drawDecal();
        Draw.color();
    }

    abstract void drawDecal();

    /** 登记进静态列表（原版是 {@code decal.add()} 进 {@code Vars.groundEffectGroup}；本项目无该组）。 */
    protected void register(){
        all.add(this);
    }

    /** 寿命耗尽被 {@link TimedEntity#update()} 移除时自动出队。 */
    @Override
    public void removed(){
        all.removeValue(this, true);
    }

    /**
     * 每帧更新所有贴花（对应原版 {@code groundEffectGroup.update()}）。由 {@code core.Logic.update()} 调用。
     * <p>⚠ 倒序遍历但**不**额外 {@code removeIndex}：{@code remove()} 是同步回调 {@code removed()}，
     * 里面已把自身从 {@link #all} 摘掉（与 {@link Puddle#updateAll()} 同一注意事项）。
     */
    public static void updateAll(){
        for(int i = all.size - 1; i >= 0; i--){
            all.get(i).update();
        }
    }

    /** 绘制所有贴花。由 {@code core.Renderer} 在地板之后、方块之前调用。 */
    public static void drawAll(){
        for(int i = 0; i < all.size; i++){
            all.get(i).draw();
        }
    }

    /** 清空所有贴花（回菜单/重开一局时用，对应原版 {@code groundEffectGroup.clear()}）。 */
    public static void clear(){
        all.clear();
    }
}
