package com.phoenix.game.ctype;

import com.phoenix.game.Vars;

/**
 * 有名字的内容基类。参照 Mindustry {@code mindustry.ctype.MappableContent} 移植。
 * <p>名字在构造时按 mod 前缀转换（无 mod 时原样），并登记进内容名表供 {@code getByName} 查询。
 */
public abstract class MappableContent extends Content{
    public final String name;

    public MappableContent(String name){
        this.name = Vars.content.transformName(name);
        Vars.content.handleMappableContent(this);
    }

    @Override
    public String toString(){
        return name;
    }
}
