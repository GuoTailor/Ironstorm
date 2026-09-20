package com.phoenix.game.core;

import com.badlogic.gdx.utils.Array;

import java.util.HashMap;
import java.util.function.Consumer;

/**
 * create by GYH on 2024/10/31
 * 最小实现：事件总线（参照 Mindustry mindustry.core.Events 移植）。
 * 支持两种注册方式：按事件类型 on(Class, Consumer) 与 按事件对象 on(Object, Runnable)（如 Trigger 枚举单例）。
 */
public class Events {
    private static final HashMap<Object, Array<Consumer<?>>> events = new HashMap<>();

    public Events() {
    }

    public static <T> void on(Class<T> type, Consumer<T> listener) {
        events.computeIfAbsent(type, k -> new Array<>()).add(listener);
    }

    public static void on(Object type, Runnable listener) {
        events.computeIfAbsent(type, k -> new Array<>()).add((e) -> listener.run());
    }

    public static <T> void fire(T type) {
        fire(type.getClass(), type);
    }

    /** 触发事件：先派发给按“事件对象”注册的监听器，再派发给按“事件类型”注册的监听器。 */
    public static <T> void fire(Class<?> ctype, T type) {
        Array<Consumer<?>> byInstance = events.get(type);
        dispatch(byInstance, type);

        Array<Consumer<?>> byClass = events.get(ctype);
        if(byClass != byInstance){
            dispatch(byClass, type);
        }
    }

    /** 下标遍历，避免 libgdx Array 迭代器嵌套（监听器里可能再次触发事件）。 */
    @SuppressWarnings("unchecked")
    private static <T> void dispatch(Array<Consumer<?>> consumers, T type) {
        if(consumers == null) return;

        for(int i = 0; i < consumers.size; i++) {
            ((Consumer<T>)consumers.get(i)).accept(type);
        }
    }

    public static void dispose() {
        events.clear();
    }
}
