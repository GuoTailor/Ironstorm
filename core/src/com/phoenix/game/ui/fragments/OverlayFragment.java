package com.phoenix.game.ui.fragments;

import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;

/**
 * 覆盖层容器（建筑库存面板 + 建筑配置面板的宿主）。参照 Mindustry mindustry.ui.fragments.OverlayFragment 移植。
 * <p>本身只是一个 {@link WidgetGroup}：{@code touchable(childrenOnly)} 让空白处的点击穿透到下面的游戏输入，
 * 只有两个子面板自己能吃到事件。
 *
 * <p><b>刻意降级（挂载点）</b>：原版 {@code add()} 写的是
 * <pre>Vars.ui.hudGroup.addChildBefore(Core.scene.find("overlaymarker"), group)</pre>
 * 本项目<b>没有 {@code menuGroup}/{@code hudGroup} 这两层容器，HUD 里也没有 {@code "overlaymarker"} 标记</b>
 * （HUD/菜单由 {@code ClientLauncher} 直接 build 到 stage 上，见 {@code core/UI.java} 的类注释），
 * 且 {@code HudFragment.group} 是 private、无法从外部插到它里面。
 * 所以这里把 {@code add()} 改成 {@code add(Group parent)}：由调用方传入目标父组（HudFragment 构建时拿到的那个
 * {@code stage.getRoot()} 或 HUD 自己的 group），其余行为与原版一致。
 * <p>另注：{@code Vars.ui}（{@code core/UI.java}）与 {@code Core.scene} 的等价物（{@code Stage}）<b>都已存在</b>，
 * 缺的只是 {@code hudGroup}/{@code overlaymarker} 这两级挂载点；HUD 侧目前由 {@code HudFragment} 直接构建
 * {@code BlockConfigFragment}，因此本类<b>暂无调用方</b>（与 {@code world/BlockStorage}、{@code io/SaveVersion} 同属
 * "刻意保留但未接线的兼容层"，不必再当缺陷去接）。
 */
public class OverlayFragment{
    public final BlockInventoryFragment inv;
    public final BlockConfigFragment config;

    private WidgetGroup group = new WidgetGroup();

    public OverlayFragment(){
        group.setTouchable(Touchable.childrenOnly);
        inv = new BlockInventoryFragment();
        config = new BlockConfigFragment();
    }

    /** 把覆盖层挂到 parent 上并构建两个子面板（对应原版无参 {@code add()}，见类注释）。 */
    public void add(Group parent){
        group.setFillParent(true);
        parent.addActor(group);

        inv.build(group);
        config.build(group);
    }

    public void remove(){
        group.remove();
    }
}
