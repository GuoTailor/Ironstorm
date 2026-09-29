package com.phoenix.game.func;

/**
 * 单参数无返回值消费器。参照 {@code arc.func.Cons} 移植。
 * <p>libgdx 没有对应类型（{@code java.util.function.Consumer} 名字不同、且原版源码全用 arc 名），
 * 故保留同名接口，让移植文件只需改 import。
 */
public interface Cons<T>{
    void get(T value);

    /** @return 先执行本消费器、再执行 after 的组合消费器。 */
    default Cons<T> with(Cons<T> after){
        return value -> {
            get(value);
            after.get(value);
        };
    }
}
