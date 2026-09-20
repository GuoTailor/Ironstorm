package com.phoenix.game.ai;

import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.phoenix.game.ai.types.CommandAI;
import com.phoenix.game.type.UnitType;
import io.anuke.mindustry.gen.Icon;

/**
 * RTS 命令系统：一条命令的行为定义。参照 Mindustry v7/v8 {@code mindustry.ai.UnitCommand} 移植
 * （替代 v6 的 {@code entities.units.UnitCommand} 枚举 + 指挥中心广播机制）。
 *
 * <p>与原版的刻意偏离（对应未移植的子系统，见各类 TODO）：
 * <ul>
 *   <li>原版是 {@code MappableContent}（参与内容注册/网络按 id 寻址）；本工程内容系统未移植，
 *       退化为普通类，联机时命令按下标（{@link #id}）同步。</li>
 *   <li>{@code controller}（RepairAI/BuilderAI/MinerAI 等子控制器）未移植 ——
 *       原版为 null 的命令（move 等）走 {@link CommandAI#defaultBehavior}，
 *       本工程所有命令都走 defaultBehavior，因此只保留 move。
 *       repair/rebuild/assist/mine/payload 系列等待对应系统移植后再补（原版定义见 v8 UnitCommand.loadAll）。</li>
 * </ul>
 */
public class UnitCommand{
    /** 所有已注册命令，下标即联机同步用的 id。 */
    public static final com.badlogic.gdx.utils.Array<UnitCommand> all = new com.badlogic.gdx.utils.Array<>();

    public static UnitCommand moveCommand;

    public static void loadAll(){
        //v8: moveCommand = new UnitCommand("move", "right", null){{ drawTarget = true; resetTarget = false; }};
        moveCommand = new UnitCommand("move", "right"){{
            drawTarget = true;
            resetTarget = false;
        }};

        //TODO 原版其余命令依赖未移植的子系统，暂不提供：
        // repairCommand("repair") -> RepairAI（维修系统未移植）
        // rebuildCommand("rebuild") / assistCommand("assist") -> BuilderAI（建造 AI 未移植）
        // mineCommand("mine") -> MinerAI（采矿 AI 未移植）
        // enterPayload/loadUnits/loadBlocks/unloadPayload/loopPayload -> 载具系统未移植
    }

    /** 命令名（对应原版 content name）。 */
    public final String name;
    /** 原版 Icon 图标名（仅用于 {@link #getIcon} 的查表与显示）。 */
    public final String icon;
    /** 联机同步用的下标。 */
    public final int id;
    /** 若为 true，单位被指派位置时自动切回 move 命令（原版语义）。 */
    public boolean switchToMove = true;
    /** 是否绘制移动/攻击目标指示。 */
    public boolean drawTarget = false;
    /** 切入/切出该命令时是否清空目标（原版语义）。 */
    public boolean resetTarget = true;
    /** 目的地是否吸附到友方建筑（原版语义；建筑指挥未移植，暂无效果）。 */
    public boolean snapToBuilding = false;
    /** 是否必须精确到达端点（原版语义）。 */
    public boolean exactArrival = false;

    public UnitCommand(String name, String icon){
        this.name = name;
        this.icon = icon;
        this.id = all.size;
        all.add(this);
    }

    /** @return 界面显示名（本工程无 bundle，直接返回中文，与 v6 枚举的 localized 惯例一致）。 */
    public String localized(){
        switch(name){
            case "move": return "移动";
            default: return name;
        }
    }

    /** @return 命令按钮图标（对应原版 {@code Icon.icons.get(icon, Icon.cancel)}）。 */
    public TextureRegionDrawable getIcon(){
        if("right".equals(icon)){
            return Icon.arrowRight;
        }
        return Icon.cancel;
    }

    @Override
    public String toString(){
        return "UnitCommand:" + name;
    }

    /** @return 单位类型支持的命令（原版 UnitType.commands；本工程全部单位支持 move）。 */
    public static boolean isSupported(UnitType type, UnitCommand command){
        return command == moveCommand;
    }
}
