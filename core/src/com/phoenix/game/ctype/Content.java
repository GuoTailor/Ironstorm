package com.phoenix.game.ctype;

import com.phoenix.game.Vars;

/**
 * 所有游戏内容的基类。参照 Mindustry {@code mindustry.ctype.Content} 移植。
 * <p>构造时向 {@link com.phoenix.game.core.ContentLoader} 注册，并按下标分配 id ——
 * id 就是"该类型内的注册顺序"，与原版一致（存档/网络按名字传输，见 {@code SaveIO}）。
 * <p><b>注意</b>：任何内容类都必须在 {@code Vars.content} 就绪之后才能创建，
 * 所以内容集合只能由 {@code ContentLoader.createBaseContent()} 触发加载。
 */
public abstract class Content implements Comparable<Content>{
    public final short id;
    /** 该内容的来源信息（原版还有 mod / sourceFile，mod 系统尚未移植）。 */
    public ModContentInfo minfo = new ModContentInfo();

    public Content(){
        this.id = (short)Vars.content.getBy(getContentType()).size;
        Vars.content.handleContent(this);
    }

    /** @return 本内容的类型；同一类型的全部实例必须返回同一个值。 */
    public abstract ContentType getContentType();

    /** 全部内容与模块创建完成后调用。不要在这里加载贴图！ */
    public void init(){
    }

    /** 全部内容创建完成后调用，仅在非 headless 端。用于加载贴图等图像数据。 */
    public void load(){
    }

    /** @return 加载期是否出过错。 */
    public boolean hasErrored(){
        return minfo.error != null;
    }

    @Override
    public int compareTo(Content other){
        return Integer.compare(id, other.id);
    }

    @Override
    public String toString(){
        return getContentType().name() + "#" + id;
    }

    public static class ModContentInfo{
        //TODO 原版有 mod（LoadedMod）与 sourceFile（Fi），mod 系统尚未移植
        /** 加载期发生的错误，没有则为 null。 */
        public String error;
        /** 导致错误的底层异常。 */
        public Throwable baseError;
    }
}
