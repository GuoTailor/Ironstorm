package com.phoenix.game.func;

/**
 * 单参数布尔谓词。参照 {@code arc.func.Boolf} 移植。
 */
public interface Boolf<T>{
    boolean get(T value);

    /** @return 本谓词与 other 的逻辑与。 */
    default Boolf<T> and(Boolf<T> other){
        return value -> get(value) && other.get(value);
    }

    /** @return 本谓词与 other 的逻辑或。 */
    default Boolf<T> or(Boolf<T> other){
        return value -> get(value) || other.get(value);
    }
}
