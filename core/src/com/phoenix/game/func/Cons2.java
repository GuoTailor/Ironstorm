package com.phoenix.game.func;

/**
 * 双参数无返回值消费器。参照 {@code arc.func.Cons2} 移植。
 */
public interface Cons2<T, N>{
    void get(T value, N value2);

    /** @return 先执行本消费器、再执行 after 的组合消费器。 */
    default Cons2<T, N> with(Cons2<T, N> after){
        return (value, value2) -> {
            get(value, value2);
            after.get(value, value2);
        };
    }
}
