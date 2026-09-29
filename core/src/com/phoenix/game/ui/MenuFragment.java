package com.phoenix.game.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;

/**
 * 最小实现：原版 MenuFragment 里被解锁 toast / 建造提示条共用的 {@code Act} 辅助
 * （一个每帧执行回调的 Actor，承担"菜单态自删""每帧刷文本/显隐"这类逻辑）。
 * 原版挂在 arc MenuFragment 内部；本工程没有菜单 Fragment 的对应移植面，按原版类名落成独立类。
 */
public class MenuFragment{

    /** 每帧执行 {@code updater} 的透明 Actor（对应原版 {@code MenuFragment.Act}）。 */
    public static class Act extends Actor{
        private final Runnable updater;

        public Act(Runnable updater){
            this.updater = updater;
        }

        @Override
        public void act(float delta){
            super.act(delta);
            updater.run();
        }
    }
}
