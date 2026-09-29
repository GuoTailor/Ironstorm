package com.phoenix.game.input;

import com.badlogic.gdx.Application.ApplicationType;
import com.phoenix.game.core.Core;
import com.phoenix.game.input.KeyBinds.*;
import com.phoenix.game.input.InputDevice.DeviceType;

public enum Binding implements KeyBind{
    move_x(new Axis(KeyCode.A, KeyCode.D), "general"),
    move_y(new Axis(KeyCode.S, KeyCode.W)),
    mouse_move(KeyCode.MOUSE_BACK),
    dash(KeyCode.SHIFT_LEFT),
    select(KeyCode.MOUSE_LEFT),
    deselect(KeyCode.MOUSE_RIGHT),
    break_block(KeyCode.MOUSE_RIGHT),
    clear_building(KeyCode.Q),
    pause_building(KeyCode.E),
    rotate(new Axis(KeyCode.SCROLL)),
    rotateplaced(KeyCode.R),
    diagonal_placement(KeyCode.CONTROL_LEFT),
    pick(KeyCode.MOUSE_MIDDLE),
    schematic_select(KeyCode.F),
    schematic_flip_x(KeyCode.Z),
    schematic_flip_y(KeyCode.X),
    schematic_menu(KeyCode.T),
    category_prev(KeyCode.COMMA),
    category_next(KeyCode.PERIOD),
    block_select_left(KeyCode.LEFT),
    block_select_right(KeyCode.RIGHT),
    block_select_up(KeyCode.UP),
    block_select_down(KeyCode.DOWN),
    block_select_01(KeyCode.NUM_1),
    block_select_02(KeyCode.NUM_2),
    block_select_03(KeyCode.NUM_3),
    block_select_04(KeyCode.NUM_4),
    block_select_05(KeyCode.NUM_5),
    block_select_06(KeyCode.NUM_6),
    block_select_07(KeyCode.NUM_7),
    block_select_08(KeyCode.NUM_8),
    block_select_09(KeyCode.NUM_9),
    block_select_10(KeyCode.NUM_0),
    zoom(new Axis(KeyCode.SCROLL), "view"),
    menu(defaultMenuKey()),
    fullscreen(KeyCode.F11),
    pause(KeyCode.SPACE),
    minimap(KeyCode.M),
    toggle_menus(KeyCode.C),
    screenshot(KeyCode.P),
    toggle_power_lines(KeyCode.F7),
    player_list(KeyCode.TAB, "multiplayer"),
    chat(KeyCode.ENTER),
    chat_history_prev(KeyCode.UP),
    chat_history_next(KeyCode.DOWN),
    chat_scroll(new Axis(KeyCode.SCROLL)),
    console(KeyCode.F8),
    ;

    private final KeybindValue defaultValue;
    private final String category;

    Binding(KeybindValue defaultValue, String category){
        this.defaultValue = defaultValue;
        this.category = category;
    }

    Binding(KeybindValue defaultValue){
        this(defaultValue, null);
    }

    //arc 的 KeyCode 枚举自身实现了 KeybindValue，所以原版单键直接写 Binding(KeyCode.Q)；
    //本项目 KeyCode 是 int，故补两个 int 重载包成 KeyValue —— 这样上面的常量表与原版完全一致。
    Binding(int key, String category){
        this(new KeyValue(key), category);
    }

    Binding(int key){
        this(new KeyValue(key), null);
    }

    /**
     * 原版直接写 {@code Core.app.getType() == ApplicationType.Android ? BACK : ESCAPE}，
     * 但枚举常量的参数是在**类初始化阶段**求值的：本项目 Core.app 由 ClientLauncher 赋值，
     * 若 Binding 在 Core.app 就绪前被触发会 NPE（同类问题见 Scl.scl 的懒初始化）→ 这里加空值兜底。
     */
    private static int defaultMenuKey(){
        return Core.app != null && Core.app.getType() == ApplicationType.Android ? KeyCode.BACK : KeyCode.ESCAPE;
    }

    @Override
    public KeybindValue defaultValue(DeviceType type){
        return defaultValue;
    }

    @Override
    public String category(){
        return category;
    }
}
