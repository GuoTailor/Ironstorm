package com.phoenix.game.util;

import com.phoenix.game.func.Cons;

/**
 * 可遍历对象。参照 {@code arc.util.Eachable} 移植（{@code arc.struct.Array} 实现了它）。
 */
public interface Eachable<T>{
    void each(Cons<? super T> consumer);
}
