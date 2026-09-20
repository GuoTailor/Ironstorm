package com.phoenix.game.ai;

import io.anuke.mindustry.gen.Icon;

/**
 * RTS 命令系统：单位姿态。参照 Mindustry v8 {@code mindustry.ai.UnitStance} 移植。
 * <p>姿态是叠加在命令之上的开关（如“停火”“追击目标”），以位集形式存在单位的命令 AI 上。
 *
 * <p>与原版的刻意偏离：原版有 stop/holdFire/pursueTarget/patrol/ram/boost/holdPosition/mineAuto
 * 八种姿态 + 互斥位集 + KeyBind；本工程按已移植的子系统裁剪：
 * <ul>
 *   <li>{@link #holdFire}/{@link #pursueTarget}：在 {@link com.phoenix.game.ai.types.CommandAI} 中生效。</li>
 *   <li>{@link #stop}：不是真正的姿态 —— 选中它表示“取消全部命令”（对应原版 setUnitCommand(stop) 语义）。</li>
 *   <li>patrol/ram/boost/holdPosition/mineAuto：巡逻、撞击、飞行 boost、原地驻守、自动采矿
 *       依赖未移植的子系统，TODO 后补。</li>
 * </ul>
 */
public class UnitStance{
    public static final com.badlogic.gdx.utils.Array<UnitStance> all = new com.badlogic.gdx.utils.Array<>();

    public static UnitStance stop, holdFire, pursueTarget;

    public static void loadAll(){
        //注意：stop 必须最先注册（id=0），与原版 UnitStance.stop 同名
        stop = new UnitStance("stop", "cancel");
        holdFire = new UnitStance("holdFire", "none");
        pursueTarget = new UnitStance("pursueTarget", "modeAttack");

        //互斥位集（对应原版 init() 里的 incompatibleStanceBits；phoenix 精简为 holdFire/pursueTarget 互斥）
        holdFire.incompatibleStanceBits = bitsOf(pursueTarget);
        pursueTarget.incompatibleStanceBits = bitsOf(holdFire);
    }

    private static com.badlogic.gdx.utils.Bits bitsOf(UnitStance... stances){
        com.badlogic.gdx.utils.Bits bits = new com.badlogic.gdx.utils.Bits(all.size);
        for(UnitStance stance : stances){
            bits.set(stance.id);
        }
        return bits;
    }

    /** 联机同步用下标。 */
    public final int id;
    /** 姿态名（对应原版 content name）。 */
    public final String name;
    /** 原版 Icon 图标名。 */
    public final String icon;
    /** 开启本姿态时要关闭的其它姿态位集。 */
    public com.badlogic.gdx.utils.Bits incompatibleStanceBits;

    public UnitStance(String name, String icon){
        this.name = name;
        this.icon = icon;
        this.id = all.size;
        all.add(this);
    }

    /** @return 界面显示名（本工程无 bundle，直接返回中文）。 */
    public String localized(){
        switch(name){
            case "stop": return "取消命令";
            case "holdFire": return "停火";
            case "pursueTarget": return "追击目标";
            default: return name;
        }
    }

    /** @return 姿态按钮图标（原版经 Icon.icons 查表）。 */
    public com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable getIcon(){
        if("cancel".equals(icon)) return Icon.cancel;
        if("modeAttack".equals(icon)) return Icon.modeAttack;
        if("none".equals(icon)) return Icon.none;
        return Icon.cancel;
    }

    @Override
    public String toString(){
        return "UnitStance:" + name;
    }
}
