package com.phoenix.game.ui.fragments;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Fill;
import com.phoenix.game.math.Mathf;

/**
 * 黑幕淡出。参照 Mindustry mindustry.ui.fragments.FadeInFragment 移植。
 * <p>在父组里塞一个铺满屏幕、不吃触摸的 Actor，用不透明黑盖住整屏，然后每帧把 alpha 递减，
 * 约 {@link #duration} 帧后自行 {@code remove()}。
 *
 * <p><b>与 arc 的差异</b>：
 * <ul>
 *   <li>{@code arc.scene.Element} → libgdx {@code Actor}，绘制入口从无参 {@code draw()} 变成
 *       {@code draw(Batch, float parentAlpha)}；{@code touchable(Touchable)} 是 arc 的 setter，
 *       libgdx 里叫 {@code setTouchable}；{@code parent.addChild(...)} → {@code addActor}。</li>
 *   <li>{@code Fill.crect(x, y, w, h)} 的 (x, y) 是<b>左下角</b>；本项目 {@code Fill.rect} 对应的是 arc 的
 *       {@code Fill.rect}，(x, y) 是<b>中心</b> → 传 (w/2, h/2)。</li>
 * </ul>
 */
public class FadeInFragment extends Fragment{
    private static final float duration = 40f;
    float time = 0f;

    @Override
    public void build(Group parent){
        parent.addActor(new Actor(){
            {
                //TODO 原版 setFillParent(true) 是 arc 的 Element API；libgdx 里只有 Widget/WidgetGroup 有
                //     setFillParent，Actor 没有。这里其实不需要尺寸（draw 直接读 Core.graphics 的宽高），
                //     所以只保留"不吃触摸"这一项。
                setTouchable(Touchable.disabled);
            }

            @Override
            public void draw(Batch batch, float parentAlpha){
                Draw.color(0f, 0f, 0f, Mathf.clamp(1f - time));
                //TODO 原版 Fill.crect(0, 0, w, h)（左下角 + 宽高）；本项目 Fill.rect 是中心 + 宽高
                float w = Core.graphics.getWidth(), h = Core.graphics.getHeight();
                Fill.rect(w / 2f, h / 2f, w, h);
                Draw.color();
            }

            @Override
            public void act(float delta){
                super.act(delta);
                time += 1f / duration;
                if(time > 1){
                    remove();
                }
            }
        });
    }
}
