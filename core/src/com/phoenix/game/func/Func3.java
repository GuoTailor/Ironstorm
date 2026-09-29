package com.phoenix.game.func;

/**
 * 三参数有返回值函数。参照 {@code arc.func.Func3} 移植。
 */
public interface Func3<P1, P2, P3, R>{
    R get(P1 param1, P2 param2, P3 param3);
}
