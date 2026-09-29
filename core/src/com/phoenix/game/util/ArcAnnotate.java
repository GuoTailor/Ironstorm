package com.phoenix.game.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 空值/内部使用标记注解的容器。参照 {@code arc.util.ArcAnnotate} 移植。
 * <p>原版 23 个文件写 {@code import arc.util.ArcAnnotate.*;} 后用 {@code @Nullable}/{@code @NonNull}，
 * 保留同名嵌套注解后这些文件只需改 import 路径，不必逐个删注解。
 * <p>这些注解只供 IDE/静态分析使用，不影响运行期行为。
 */
public class ArcAnnotate{

    /** 标记字段/参数/返回值可能为 null。 */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
    public @interface Nullable{}

    /** 标记字段/参数/返回值不应为 null。 */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
    public @interface NonNull{}

    /** 标记仅供内部使用、外部不应调用的 API。 */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.TYPE})
    public @interface Internal{}
}
