package com.phoenix.game.type;

import com.phoenix.game.ctype.ContentType;
import com.phoenix.game.ctype.MappableContent;
import com.phoenix.game.entities.traits.TypeTrait;
import com.phoenix.game.func.Prov;

/**
 * 可同步实体的类型注册项。参照 Mindustry {@code mindustry.type.TypeID} 移植。
 * <p>原版用它给"需要跨端同步的实体"（火/水洼/玩家）分配一个稳定 id，
 * 内容加载时通过 {@code content/TypeIDs} 注册。
 */
public class TypeID extends MappableContent{
    public final Prov<? extends TypeTrait> constructor;

    public TypeID(String name, Prov<? extends TypeTrait> constructor){
        super(name);
        this.constructor = constructor;
    }

    @Override
    public ContentType getContentType(){
        return ContentType.typeid;
    }
}
