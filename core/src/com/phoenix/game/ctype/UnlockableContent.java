package com.phoenix.game.ctype;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.world.meta.I18n;

/**
 * 可解锁内容的基类（物品/方块/单位/液体/状态效果/Mech/天气/区域…）。
 * 参照 Mindustry {@code mindustry.ctype.UnlockableContent} 移植。
 * <p><b>与本项目的差异（刻意）</b>：
 * <ul>
 *   <li>原版用 {@code Core.bundle.get(key, default)} 取名字/描述；本项目的 I18NBundle 关掉了缺键抛异常，
 *       缺键返回的是 {@code ???key???} 而不是键本身，fallback 不生效 —— 这里统一走 {@link I18n} 的双形态判定。</li>
 *   <li>原版有 {@code createIcons(MultiPacker)}（打包期合成图标）；本项目没有打包管线，
 *       改为运行期取图集区域 + {@code Block.loadMinimapColor} 采样小地图色。</li>
 *   <li>原版的解锁状态来自 {@code Vars.data.isUnlocked(this)}（{@code GlobalData} 的内容解锁系统）；
 *       本项目已按原版接线（{@link #unlocked()}），{@code Vars.data} 为 null 时按"已解锁"处理。</li>
 * </ul>
 */
public abstract class UnlockableContent extends MappableContent{
    /** 本地化正式名，永不为 null；bundle 缺键时退回内部名。 */
    public String localizedName;
    /** 本地化描述，可能为 null。 */
    public String description;
    /** 按 Cicon 尺寸缓存的图标。 */
    protected TextureRegion[] cicons = new TextureRegion[Cicon.values().length];

    public UnlockableContent(String name){
        super(name);

        String prefix = getContentType().name() + "." + this.name;
        this.localizedName = I18n.get(prefix + ".name", this.name);
        this.description = I18n.get(prefix + ".description", null);
    }

    public String displayDescription(){
        return description == null ? "" : description;
    }

    /**
     * @return 指定尺寸的内容图标；找不到时按
     * {@code <type>-<name>-<icon>} → {@code <type>-<name>-full} → {@code <type>-<name>} → {@code <name>} → {@code <name>1} 回退。
     */
    public TextureRegion icon(Cicon icon){
        int index = icon.ordinal();
        if(cicons[index] == null){
            TextureRegion found = null;
            if(Core.atlas != null){
                String prefix = getContentType().name() + "-" + name;
                found = Core.atlas.findRegion(prefix + "-" + icon.name());
                if(found == null) found = Core.atlas.findRegion(prefix + "-full");
                if(found == null) found = Core.atlas.findRegion(prefix);
                if(found == null) found = Core.atlas.findRegion(name);
                if(found == null) found = Core.atlas.findRegion(name + "1");
            }
            cicons[index] = found;
        }
        return cicons[index];
    }

    /**
     * 把本内容的详情渲染进表格（对应原版 {@code UnlockableContent.displayInfo}）。
     * <p>本项目 Block 已覆写为 {@code ContentDisplay.displayBlock}；其余类型暂留空实现（TODO 逐个补齐）。
     */
    public void displayInfo(Table table){
        //TODO 各内容类型自己的详情面板尚未移植（原版 Item/UnitType/Liquid/StatusEffect 各有 displayInfo）
    }

    /** 内容被解锁时调用（用于联动解锁其它内容）。 */
    public void onUnlock(){
    }

    /** @return 是否在内容信息对话框里始终隐藏。 */
    public boolean isHidden(){
        return false;
    }

    /** 覆写此方法可让内容始终解锁。 */
    public boolean alwaysUnlocked(){
        return false;
    }

    /** @return 是否已解锁。 */
    public final boolean unlocked(){
        //原版：Vars.data.isUnlocked(this)（GlobalData 的内容解锁系统，S4a 批已落地）。
        //Vars.data 为 null（菜单/初始化早期）时按"已解锁"处理，避免启动期 NPE 或把内容全锁上。
        return Vars.data == null || Vars.data.isUnlocked(this);
    }

    /** @return 是否已解锁，或当前处于自定义游戏。 */
    public final boolean unlockedCur(){
        //原版：Vars.data.isUnlocked(this) || !Vars.world.isZone()。
        //注意 Vars.world 在菜单里是 null（原版世界对象常驻），所以带 null 守卫。
        return unlocked() || !(Vars.world != null && Vars.world.isZone());
    }

    public final boolean locked(){
        return !unlocked();
    }
}
