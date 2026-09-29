package com.phoenix.game.entities.effect;

import com.phoenix.game.Vars;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Effects.EffectRenderer;
import com.phoenix.game.entities.type.EffectEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;

/**
 * 地面层特效实体：把特效画在"地面层"而不是"顶层"。
 * 参照 Mindustry {@code mindustry.entities.effect.GroundEffectEntity} 移植。
 *
 * <p>与普通 {@link EffectEntity} 的区别在于 {@link GroundEffect#isStatic}：
 * 静态特效播完初次生命周期后会**停在地上**（{@code staticLife} tick），
 * 期间不再推进时间（{@code time} 被钳在 {@code staticLife}），落地时若脚下是液体则立即消失。
 *
 * <p><b>与原版的差异</b>：
 * <ul>
 *   <li>原版 {@code draw()} 调静态的 {@code Effects.renderEffect(id, effect, color, time, rotation, x, y, data)}；
 *       本项目的 {@code Effects} 没有这个方法（它自己维护 {@code EffectState} 列表），
 *       实体侧由 {@link EffectEntity#draw()} 用 {@code EffectContainer} 就地绘制。
 *       为了复用父类那份私有 container，这里用"临时把 {@code time} 换成 {@code lifetime()} 再 super.draw()"
 *       实现原版 {@code once} 分支（语义完全等价：container.time = lifetime() ⇒ fin()==1）。</li>
 *   <li>B 类：原版靠 {@code effectGroup} 承载实体；本项目 {@code Vars} 没有该实体组（F6 只接了 player/puddle/fire，
 *       {@code Vars.java} 本批不可改）→ 本类同样**未被自动登记/遍历**。</li>
 * </ul>
 */
public class GroundEffectEntity extends EffectEntity{
    private boolean once;

    @Override
    public void update(){
        //父类 EffectEntity.update() 有 effect == null 的守卫；本方法在 isStatic 分支不调 super，
        //所以这里要自己补一份，否则空 effect 会 NPE
        if(effect == null){
            remove();
            return;
        }

        GroundEffect effect = (GroundEffect)this.effect;

        if(effect.isStatic){
            time += Time.delta();

            time = Mathf.clamp(time, 0, effect.staticLife);

            if(!once && time >= lifetime()){
                once = true;
                time = 0f;
                Tile tile = Vars.world == null ? null : Vars.world.tileWorld(x, y);
                if(tile != null && tile.floor().isLiquid){
                    remove();
                }
            }else if(once && time >= effect.staticLife){
                remove();
            }
        }else{
            super.update();
        }
    }

    @Override
    public void draw(){
        GroundEffect effect = (GroundEffect)this.effect;

        if(once && effect.isStatic){
            //原版 Effects.renderEffect(..., lifetime(), ...)：把 time 传成 lifetime() ⇒ fin()==1
            float prev = time;
            time = lifetime();
            super.draw();
            time = prev;
        }else{
            super.draw();
        }
    }

    @Override
    public void reset(){
        super.reset();
        once = false;
    }

    /**
     * An effect that is rendered on the ground layer as opposed to the top layer.
     */
    public static class GroundEffect extends Effects.Effect{
        /**
         * How long this effect stays on the ground when static.
         */
        public final float staticLife;
        /**
         * If true, this effect will stop and lie on the ground for a specific duration,
         * after its initial lifetime is over.
         */
        public final boolean isStatic;

        public GroundEffect(float life, float staticLife, EffectRenderer draw){
            super(life, draw);
            this.staticLife = staticLife;
            this.isStatic = true;
        }

        public GroundEffect(boolean isStatic, float life, EffectRenderer draw){
            super(life, draw);
            this.staticLife = 0f;
            this.isStatic = isStatic;
        }

        public GroundEffect(float life, EffectRenderer draw){
            super(life, draw);
            this.staticLife = 0f;
            this.isStatic = false;
        }
    }
}
