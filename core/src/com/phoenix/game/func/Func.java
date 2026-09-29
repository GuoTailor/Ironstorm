package com.phoenix.game.func;

/**
 * 单参数有返回值函数。参照 {@code arc.func.Func} 移植。
 */
public interface Func<P, R>{
    R get(P param);
}
