package com.phoenix.game.entities.traits;

import com.phoenix.game.type.TypeID;

/**
 * 可同步类型（实体/特效/火等）的标记。参照 Mindustry {@code mindustry.entities.traits.TypeTrait} 移植。
 * <p>原版这个接口里还带着一整套 registerType/getTypeByID 的静态注册机制，但那段代码在原版里**整段被注释掉了**
 * （改用 {@code ContentLoader} 的 TypeID 内容），所以这里只保留 {@link #getTypeID()}。
 */
public interface TypeTrait{

    TypeID getTypeID();
}
