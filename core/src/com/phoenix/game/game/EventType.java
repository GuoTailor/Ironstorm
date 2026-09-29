package com.phoenix.game.game;


import com.badlogic.gdx.ai.fsm.State;

import com.phoenix.game.ctype.UnlockableContent;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Player;
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
        openWiki,
        teamCoreDamage
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

    /**
     * 建造或拆除**完成**时派发（对应原版 {@code EventType.BlockBuildEndEvent}），
     * 结算框的"建造数/拆除数"由监听它的 {@code Control} 累加。
     * <p>⚠ 原版第 3 个参数是 {@code playerGroup.getByID(builderID)}；本项目
     * {@code BuildBlock.constructed}/{@code deconstructFinish} 没有 {@code builderID} 形参
     * （建造者身份没往下传），所以固定传 {@code null}；监听方只用 {@code e.team}，不影响统计。
     */
    public static class BlockBuildEndEvent{
        public final Tile tile;
        public final Team team;
        public final Player player;
        public final boolean breaking;

        public BlockBuildEndEvent(Tile tile, Player player, Team team, boolean breaking){
            this.tile = tile;
            this.team = team;
            this.player = player;
            this.breaking = breaking;
        }
    }

    /**
     * 方块被摧毁**之前**派发（对应原版 {@code EventType.BlockDestroyEvent}）。
     * <p>原版注释：派发时该瓦片的实体一定不为 null。
     */
    public static class BlockDestroyEvent{
        public final Tile tile;

        public BlockDestroyEvent(Tile tile){
            this.tile = tile;
        }
    }

    /** 单位死亡时派发（对应原版 {@code EventType.UnitDestroyEvent}）。 */
    public static class UnitDestroyEvent{
        public final BaseUnit unit;

        public UnitDestroyEvent(BaseUnit unit){
            this.unit = unit;
        }
    }

    /**
     * 在科技树里研究（解锁）一项内容时派发（对应原版 {@code EventType.ResearchEvent}，定义逐字一致）。
     * <p>当前派发点：{@code ui/dialogs/TechTreeDialog.unlock()}。
     * <p><b>注意</b>：本项目目前<b>没有任何 {@code ResearchEvent} 监听者</b>（原版由
     * {@code Control}/{@code Achievements} 等消费）→ 这里 fire 属"提供 API / 保真原版形状"，
     * 不是接通某条现成链路。
     */
    public static class ResearchEvent{
        public final UnlockableContent content;

        public ResearchEvent(UnlockableContent content){
            this.content = content;
        }
    }

    /**
     * 内容被解锁时派发（对应原版 {@code EventType.UnlockEvent}，定义逐字一致，原版 :233-239）。
     * <p>当前派发点：{@code GlobalData.unlockContent()}。
     */
    public static class UnlockEvent{
        public final UnlockableContent content;

        public UnlockEvent(UnlockableContent content){
            this.content = content;
        }
    }

}

