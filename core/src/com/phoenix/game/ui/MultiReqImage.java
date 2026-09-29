package com.phoenix.game.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.phoenix.game.core.Time;
import com.phoenix.game.struct.Array;

/**
 * 一组需求图标。参照 Mindustry mindustry.ui.MultiReqImage 移植。
 *
 * <p>行为：同一时刻只显示一个 —— 优先显示第一个满足条件的；全都未满足时按时间轮换闪烁，
 * 提示玩家当前缺的是哪一项。
 */
public class MultiReqImage extends Stack{
    private Array<ReqImage> displays = new Array<>();
    private float time;

    public void add(ReqImage display){
        displays.add(display);
        super.add(display);
    }

    @Override
    public void act(float delta){
        super.act(delta);

        //原版 Time.delta() 是 tick 数（60/s），这里换成秒
        time += Time.delta() / 60f;

        displays.each(req -> req.setVisible(false));

        ReqImage valid = displays.find(ReqImage::valid);
        if(valid != null){
            valid.setVisible(true);
        }else{
            if(displays.size > 0){
                displays.get((int)(time) % displays.size).setVisible(true);
            }
        }
    }
}
