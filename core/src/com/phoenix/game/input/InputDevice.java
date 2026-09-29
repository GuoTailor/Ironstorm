package com.phoenix.game.input;

/**
 * 输入设备类型。参照 {@code arc.input.InputDevice} 最小移植。
 * <p>原版 {@code InputDevice} 是一个接口（含键盘/手柄实现），本项目尚未做多设备抽象，
 * 只保留 {@link DeviceType} —— 它是 {@link KeyBinds.KeyBind#defaultValue(DeviceType)} 的入参。
 */
public interface InputDevice{

    enum DeviceType{
        keyboard, controller
    }
}
