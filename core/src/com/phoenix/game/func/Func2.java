package com.phoenix.game.func;

/**
 * 双参数有返回值函数。参照 {@code arc.func.Func2} 移植。
 */
public interface Func2<P1, P2, R>{
    R get(P1 param1, P2 param2);
}
