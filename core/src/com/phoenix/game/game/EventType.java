package com.phoenix.game.game;


//⚠ 照原版 EventType.java:4 `import mindustry.core.GameState.State;` —— 这里必须是**游戏状态枚举**
//GameState.State，不是 libgdx 的 ai.fsm.State。此前误写成 ai.fsm.State，导致 StateChangeEvent
//的构造器签名与实际要传的 GameState.State 不兼容（G3-3 接线时暴露）。
import com.phoenix.game.core.GameState.State;

import com.phoenix.game.ai.UnitCommand;
import com.phoenix.game.ctype.UnlockableContent;
import com.phoenix.game.entities.traits.BuilderTrait;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.Mech;
import com.phoenix.game.type.Zone;
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
        teamCoreDamage,
        //F-LOW 批照原版补齐（原版 EventType.java:32-33）：
        //update 在原版 Logic.java:211 每帧 fire；socketConfigChanged 由 Config 的 socketInput 三项 fire（本地 TCP 控制台未移植）
        socketConfigChanged,
        update
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

    //==================== F-LOW 批照原版补齐的事件类 ====================
    //原版定义见 mindustry/game/EventType.java。多数类当前在本项目没有派发点/监听方
    //(原版的核心消费者也是 mod 或未移植子系统)，先按原版形状立好，接线点在各类注释里注明。

    /** 发射台发射物品（原版 :42-48；原版派发点 LaunchPad.java:83 / Logic.java:190 —— 本项目发射链未移植，暂无派发点）。 */
    public static class LaunchItemEvent{
        public final ItemStack stack;

        public LaunchItemEvent(Item item, int amount){
            this.stack = new ItemStack(item, amount);
        }
    }

    /** 指挥中心下达全队命令（原版 :54-62；原版派发点 CommandCenter.java:118 —— 本项目 CommandCenter 已退役为空壳，暂无派发点）。 */
    public static class CommandIssueEvent{
        public final Tile tile;
        public final UnitCommand command;

        public CommandIssueEvent(Tile tile, UnitCommand command){
            this.tile = tile;
            this.command = command;
        }
    }

    /** 收到玩家聊天消息（原版 :64-72；原版派发点 NetClient.java:165 —— 本项目自研网络无该分发点，暂无派发点）。 */
    public static class PlayerChatEvent{
        public final Player player;
        public final String message;

        public PlayerChatEvent(Player player, String message){
            this.player = player;
            this.message = message;
        }
    }

    /** 区域的一个需求目标达成（原版 :75-84；派发点 {@code Zone.updateObjectives}，本项目已接线）。 */
    public static class ZoneRequireCompleteEvent{
        public final Zone zoneMet, zoneForMet;
        public final Objective objective;

        public ZoneRequireCompleteEvent(Zone zoneMet, Zone zoneForMet, Objective objective){
            this.zoneMet = zoneMet;
            this.zoneForMet = zoneForMet;
            this.objective = objective;
        }
    }

    /** 区域的配置目标（默认波次）达成（原版 :87-93；派发点 {@code Zone.updateObjectives}，本项目已接线）。 */
    public static class ZoneConfigureCompleteEvent{
        public final Zone zone;

        public ZoneConfigureCompleteEvent(Zone zone){
            this.zone = zone;
        }
    }

    /** 客户端创建（原版 :95-97；原版派发点 ClientLauncher.java:36 —— 启动早期，本项目暂无派发点）。 */
    public static class ClientCreateEvent{

    }

    /** headless 服务端加载完成（原版 :104-106；本项目无 headless 循环，暂无派发点）。 */
    public static class ServerLoadEvent{

    }

    /** 内容重载（mod 热重载后重取贴图/布局；原版 :108-110，派发点 Mods.java:452 —— 本项目 mod 系统降级，暂无派发点）。
     * <p>原版消费者：MapRenderer 重取 clear-editor 贴图、TechTreeDialog 重排布局（两处本项目都有对应 TODO）。 */
    public static class ContentReloadEvent{

    }

    /** 玩家点了一下方块（原版 :179-187；原版派发点 InputHandler.onTileTapped —— 本项目 tapped 调用点分散在 DesktopInput/MobileInput/ClientLauncher，未接线）。 */
    public static class TapEvent{
        public final Tile tile;
        public final Player player;

        public TapEvent(Tile tile, Player player){
            this.tile = tile;
            this.player = player;
        }
    }

    /** 玩家配置了方块（原版 :190-200；原版派发点 InputHandler.onTileConfig —— 本项目唯一配置入口 {@code Tile.configured} 可接线但当前无消费者，暂未派发）。 */
    public static class TapConfigEvent{
        public final Tile tile;
        public final Player player;
        public final int value;

        public TapConfigEvent(Tile tile, Player player, int value){
            this.tile = tile;
            this.player = player;
            this.value = value;
        }
    }

    /** 游戏结束（原版 :202-208）。派发点 {@code Logic.checkGameOver}（本项目已接线）；
     * 原版消费者 Control 的"弹结算框 + 结算统计"在本项目已内联等价实现，监听本事件的新逻辑可自行挂接。 */
    public static class GameOverEvent{
        public final Team winner;

        public GameOverEvent(Team winner){
            this.winner = winner;
        }
    }

    /** 开始建造/拆除一个方块（占位方块落下时；原版 :253-263，派发点 Build.java:43/64 —— 本项目 Build.beginPlace 未拆分 begin/end 派发，暂无派发点）。 */
    public static class BlockBuildBeginEvent{
        public final Tile tile;
        public final Team team;
        public final boolean breaking;

        public BlockBuildBeginEvent(Tile tile, Team team, boolean breaking){
            this.tile = tile;
            this.team = team;
            this.breaking = breaking;
        }
    }

    /** 单位/玩家开始建造某个占位方块（原版 :284-296；派发点 {@code BuilderTrait.updateBuilding}，本项目已接线，
     * 消费者 BuilderDrone"对方块开始建造时停手"联动已按原版恢复）。 */
    public static class BuildSelectEvent{
        public final Tile tile;
        public final Team team;
        public final BuilderTrait builder;
        public final boolean breaking;

        public BuildSelectEvent(Tile tile, Team team, BuilderTrait builder, boolean breaking){
            this.tile = tile;
            this.team = team;
            this.builder = builder;
            this.breaking = breaking;
        }
    }

    /** 工厂出厂一个单位（原版 :316-322；原版派发点 UnitFactory.java:66 —— 本项目 spawnUnit 可接线但当前无消费者，暂未派发）。 */
    public static class UnitCreateEvent{
        public final BaseUnit unit;

        public UnitCreateEvent(BaseUnit unit){
            this.unit = unit;
        }
    }

    /** 玩家机甲变更（原版 :328-336；原版派发点 MechPad.java:79 —— 本项目 Player 无机甲装备系统（F12 等价路径），暂无派发点）。 */
    public static class MechChangeEvent{
        public final Player player;
        public final Mech mech;

        public MechChangeEvent(Player player, Mech mech){
            this.player = player;
            this.mech = mech;
        }
    }

    /** 玩家连上并拿到世界数据、可以开始玩（原版 :339-345；原版派发点 NetServer.java:649 —— 本项目自研网络，暂无派发点）。 */
    public static class PlayerJoin{
        public final Player player;

        public PlayerJoin(Player player){
            this.player = player;
        }
    }

    /** 玩家已连接但还没加入游戏（原版 :348-354；原版派发点 NetServer.java:245 —— 本项目自研网络，暂无派发点）。 */
    public static class PlayerConnect{
        public final Player player;

        public PlayerConnect(Player player){
            this.player = player;
        }
    }

    /** 玩家离开（原版 :356-362；原版派发点 NetServer.java:487 —— 本项目自研网络，暂无派发点）。 */
    public static class PlayerLeave{
        public final Player player;

        public PlayerLeave(Player player){
            this.player = player;
        }
    }

    /** 玩家被封禁（原版 :364-370；原版派发点 Administration.banPlayerID —— 本项目 Administration.ban 走文件持久化，暂无派发点）。 */
    public static class PlayerBanEvent{
        public final Player player;

        public PlayerBanEvent(Player player){
            this.player = player;
        }
    }

    /** 玩家被解禁（原版 :372-378）。 */
    public static class PlayerUnbanEvent{
        public final Player player;

        public PlayerUnbanEvent(Player player){
            this.player = player;
        }
    }

    /** IP 被封禁（原版 :380-387）。 */
    public static class PlayerIpBanEvent{
        public final String ip;

        public PlayerIpBanEvent(String ip){
            this.ip = ip;
        }
    }

    /** IP 被解禁（原版 :389-396）。 */
    public static class PlayerIpUnbanEvent{
        public final String ip;

        public PlayerIpUnbanEvent(String ip){
            this.ip = ip;
        }
    }

}

