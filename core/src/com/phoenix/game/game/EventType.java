package com.phoenix.game.game;


import com.badlogic.gdx.ai.fsm.State;

import com.phoenix.game.world.Tile;

public class EventType {

    //events that occur very often
    public enum Trigger{
        shock,
        phaseDeflectHit,
        impactPower,
        thoriumReactorOverheat,
        itemLaunch,
        fireExtinguish,
        newGame,
        tutorialComplete,
        flameAmmo,
        turretCool,
        enablePixelation,
        drown,
        exclusionDeath,
        suicideBomb,
        openWiki
    }

    public static class WinEvent{}

    public static class LoseEvent{}

    public static class LaunchEvent{}

    public static class MapMakeEvent{}

    public static class MapPublishEvent{}

    /** Called when the client game is first loaded. */
    public static class ClientLoadEvent{

    }

    public static class DisposeEvent{

    }

    public static class PlayEvent{

    }

    public static class ResetEvent{

    }

    public static class WaveEvent{

    }

    /** Called when the player places a line, mobile or desktop.*/
    public static class LineConfirmEvent{

    }

    /** Called when a turret recieves ammo, but only when the tutorial is active! */
    public static class TurretAmmoDeliverEvent{

    }

    /** Called when a core recieves ammo, but only when the tutorial is active! */
    public static class CoreItemDeliverEvent{

    }

    /** Called when the player opens info for a specific block.*/
    public static class BlockInfoEvent{

    }

    /** Called when a player withdraws items from a block. Tutorial only.*/
    public static class WithdrawEvent{

    }

    /** Called when a player deposits items to a block.*/
    public static class DepositEvent{

    }


    /** Called when a game begins and the world is loaded. */
    public static class WorldLoadEvent{

    }

    public static class StateChangeEvent{
        public final State from, to;

        public StateChangeEvent(State from, State to){
            this.from = from;
            this.to = to;
        }
    }


    public static class ResizeEvent{

    }

    /**
     * 瓦片内容发生变化（放/拆建筑、改地板）。由 {@code World.notifyChanged} 派发。
     * <p>原版注释「Called from the logic thread. Do not access graphics here!」在 phoenix 不成立：
     * 逻辑与渲染同线程，监听方（MinimapRenderer）只写 CPU 侧 Pixmap，纹理上传另在绘制前 flush()。
     */
    public static class TileChangeEvent{
        public final Tile tile;

        public TileChangeEvent(Tile tile){
            this.tile = tile;
        }
    }

}

