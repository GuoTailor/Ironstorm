package com.phoenix.game.entities.type;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Effects.Effect;
import com.phoenix.game.entities.traits.DrawTrait;
import com.phoenix.game.entities.traits.Entity;

/**
 * 参照原版 mindustry.entities.type.EffectEntity 移植（"每个特效一个实体"的模型）。
 * <p>**刻意偏离 / 未接线（本项目特效系统当前是另一套）**：
 * <ul>
 *     <li>原版 {@code draw()} 走 {@code Effects.renderEffect(id, effect, color, time, rotation, x, y, data)}；
 *         本项目的 {@link Effects} 自己维护一份 {@code EffectState} 列表、没有这个方法，
 *         这里改用 Effects 的公开 API（{@link Effects.EffectContainer} + {@link Effects.Effect#draw}）就地绘制，效果等价。</li>
 *     <li>原版 {@code targetGroup()} 返回 {@code Vars.effectGroup}；本项目 {@code Vars} 没有 effectGroup
 *         （F6 只接了 player/puddle/fire 三个组），所以这里**仍省略该覆盖**，本类目前不会被自动登记/遍历。</li>
 *     <li>原版由 {@code Effects.provider} 通过 {@code Pools.obtain(EffectEntity.class, ...)} 创建；
 *         本项目 Effects 不创建实体，因此本类暂时处于"可编译、未接线"状态。</li>
 * </ul>
 */
public class EffectEntity extends TimedEntity implements Pool.Poolable, DrawTrait{
    /** 绘制用的容器（复用一份，避免每帧分配；对应原版 Effects 里的静态 container）。 */
    private static final Effects.EffectContainer container = new Effects.EffectContainer();

    public Effect effect;
    public Color color = new Color(Color.WHITE);
    public Object data;
    public float rotation = 0f;

    public Entity parent;
    public float poffsetx, poffsety;

    /** For pooling use only! */
    public EffectEntity(){
    }

    public void setParent(Entity parent){
        this.parent = parent;
        this.poffsetx = x - parent.getX();
        this.poffsety = y - parent.getY();
    }

    @Override
    public float lifetime(){
        return effect.lifetime;
    }

    @Override
    public float drawSize(){
        return effect.size;
    }

    @Override
    public void update(){
        if(effect == null){
            remove();
            return;
        }

        super.update();
        if(parent != null){
            x = parent.getX() + poffsetx;
            y = parent.getY() + poffsety;
        }
    }

    @Override
    public void reset(){
        effect = null;
        color.set(Color.WHITE);
        rotation = time = poffsetx = poffsety = 0f;
        parent = null;
        data = null;
    }

    @Override
    public void draw(){
        container.set(x, y, time, effect.lifetime, rotation, color, data);
        container.id = id;
        effect.draw.render(container);
    }

    @Override
    public void removed(){
        Pools.free(this);
    }
}
