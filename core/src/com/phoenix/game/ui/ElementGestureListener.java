package com.phoenix.game.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener;

/**
 * 元素手势监听器。参照 {@code arc.scene.event.ElementGestureListener} 移植。
 * <p>arc 的 ElementGestureListener 本身就是 libgdx {@code ActorGestureListener} 的 fork，
 * 回调签名几乎完全一致（{@code touchDown/touchUp/tap/fling/pan/zoom/pinch}），
 * 所以这里直接继承 libgdx 的版本，只补 arc 独有的两个成员：
 * <ul>
 *   <li>{@code getTouchDownTarget()} —— 按下时命中的元素（原版用于拖拽判定）</li>
 *   <li>{@code longPress(Element, float, float)} 返回 boolean 的重载（libgdx 的版本返回 void）</li>
 * </ul>
 * <p><b>移植提示</b>：原版覆写里写的 {@code KeyCode button} 参数在本项目应写成 {@code int button}
 * （本项目的 {@code KeyCode} 就是 int 常量，见 {@code com.phoenix.game.input.KeyCode}）。
 */
public class ElementGestureListener extends ActorGestureListener{

    private Actor touchDownTarget;

    public ElementGestureListener(){
        super();
    }

    /** 对应 arc 的 {@code ElementGestureListener(deadzone, tapSquareSize, tapCountInterval, longPressDuration)}。 */
    public ElementGestureListener(float deadzone, float tapSquareSize, float tapCountInterval, float longPressDuration){
        super(deadzone, tapSquareSize, tapCountInterval, longPressDuration);
    }

    @Override
    public void touchDown(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y, int pointer, int button){
        touchDownTarget = event.getTarget();
        super.touchDown(event, x, y, pointer, button);
    }

    /** @return 最近一次按下命中的元素（对应 arc {@code getTouchDownTarget}）。 */
    public Actor getTouchDownTarget(){
        return touchDownTarget;
    }
}
