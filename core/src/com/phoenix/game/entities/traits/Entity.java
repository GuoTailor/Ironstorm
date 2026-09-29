package com.phoenix.game.entities.traits;

import com.phoenix.game.entities.EntityGroup;

/**
 * 实体接口。参照原版 mindustry.entities.traits.Entity 移植。
 *
 * <p><b>刻意偏离（本项目 removed() 是同步调用的）</b>：原版 {@code removed()} 由
 * {@link EntityGroup#updateEvents()} 在"下一帧"延迟调用（配合池化实体的回收），本项目
 * {@code BaseEntity.remove()} 是同步 {@code removed()} —— 所以 {@link EntityGroup} 只维护成员数组、
 * **不再重复回调** {@code added()}/{@code removed()}（否则池化实体被 {@code Pools.free} 后会重复清理）。
 * 详见 {@link EntityGroup} 的类注释与 {@code BaseEntity.remove()}。
 */
public interface Entity extends MoveTrait{

    int getID();

    void resetID(int id);

    default void update(){}

    default void removed(){}

    default void added(){}

    /** @return 本实体归属的组（对应原版 {@code Entity.targetGroup()}）；没有归属时返回 null。 */
    default EntityGroup targetGroup(){
        return null;
    }

    /** 把本实体登记进 {@link #targetGroup()}（对应原版 {@code Entity.add()}）。 */
    @SuppressWarnings("unchecked")
    default void add(){
        if(targetGroup() != null){
            targetGroup().add(this);
        }
    }

    /**
     * 从当前所属组解绑（对应原版 {@code Entity.remove()}）。
     * <p>注意：{@code BaseEntity} 覆写了它（额外置 dead 并同步回调 {@code removed()}），
     * 所以本默认实现只对"不继承 BaseEntity 的轻量实体"（{@code Player}/{@code Fire}）生效。
     */
    @SuppressWarnings("unchecked")
    default void remove(){
        if(getGroup() != null){
            getGroup().remove(this);
        }
        setGroup(null);
    }

    /** @return 本实体当前所属的组；未加入任何组时为 null（对应原版 {@code Entity.getGroup()}）。 */
    EntityGroup getGroup();

    /** 设置本实体所属的组（对应原版 {@code Entity.setGroup(EntityGroup)}）。 */
    void setGroup(EntityGroup group);

    /** @return 本实体是否已登记进某个组（对应原版 {@code Entity.isAdded()}）。 */
    default boolean isAdded(){
        return getGroup() != null;
    }
}
