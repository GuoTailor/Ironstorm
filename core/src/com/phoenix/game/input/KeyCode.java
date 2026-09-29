package com.phoenix.game.input;

import com.badlogic.gdx.Input;

/**
 * 按键/鼠标键码表。参照 {@code arc.input.KeyCode} 移植。
 * <p><b>与 arc 的差异（刻意）</b>：arc 的 KeyCode 是**枚举**，键盘键与鼠标键在同一个枚举里；
 * 本项目直接用 libgdx 的 {@code Input.Keys} 整数键码，避免多一层包装（现有 DesktopInput/InputHandler 已经这么用）。
 * 原版源码里 {@code KeyCode.X} 的用法（比较、{@code keyTap(...)}、{@code KeyCode.valueOf(...)}）在整数常量下全部成立。
 * <p><b>鼠标键为什么要挪到负数</b>：libgdx 的鼠标键在 {@code Input.Buttons} 里，与 {@code Input.Keys} 是两套编号且会撞车
 * （{@code Buttons.LEFT == 0 == Keys.UNKNOWN}、{@code Buttons.BACK == 3 == Keys.BACK}），
 * 所以鼠标键/滚轮统一用负数，保证 {@code KeyCode.MOUSE_*} 不会等于任何键盘键码。
 */
public class KeyCode{

    // ---- 键盘：直接复用 libgdx 的键码 ----
    public static final int A = Input.Keys.A;
    public static final int B = Input.Keys.B;
    public static final int C = Input.Keys.C;
    public static final int D = Input.Keys.D;
    public static final int E = Input.Keys.E;
    public static final int F = Input.Keys.F;
    public static final int G = Input.Keys.G;
    public static final int H = Input.Keys.H;
    public static final int I = Input.Keys.I;
    public static final int J = Input.Keys.J;
    public static final int K = Input.Keys.K;
    public static final int L = Input.Keys.L;
    public static final int M = Input.Keys.M;
    public static final int N = Input.Keys.N;
    public static final int O = Input.Keys.O;
    public static final int P = Input.Keys.P;
    public static final int Q = Input.Keys.Q;
    public static final int R = Input.Keys.R;
    public static final int S = Input.Keys.S;
    public static final int T = Input.Keys.T;
    public static final int U = Input.Keys.U;
    public static final int V = Input.Keys.V;
    public static final int W = Input.Keys.W;
    public static final int X = Input.Keys.X;
    public static final int Y = Input.Keys.Y;
    public static final int Z = Input.Keys.Z;

    public static final int NUM_0 = Input.Keys.NUM_0;
    public static final int NUM_1 = Input.Keys.NUM_1;
    public static final int NUM_2 = Input.Keys.NUM_2;
    public static final int NUM_3 = Input.Keys.NUM_3;
    public static final int NUM_4 = Input.Keys.NUM_4;
    public static final int NUM_5 = Input.Keys.NUM_5;
    public static final int NUM_6 = Input.Keys.NUM_6;
    public static final int NUM_7 = Input.Keys.NUM_7;
    public static final int NUM_8 = Input.Keys.NUM_8;
    public static final int NUM_9 = Input.Keys.NUM_9;

    public static final int BACK = Input.Keys.BACK;
    public static final int COMMA = Input.Keys.COMMA;
    public static final int CONTROL_LEFT = Input.Keys.CONTROL_LEFT;
    public static final int CONTROL_RIGHT = Input.Keys.CONTROL_RIGHT;
    public static final int DOWN = Input.Keys.DOWN;
    public static final int UP = Input.Keys.UP;
    public static final int LEFT = Input.Keys.LEFT;
    public static final int RIGHT = Input.Keys.RIGHT;
    public static final int ENTER = Input.Keys.ENTER;
    public static final int ESCAPE = Input.Keys.ESCAPE;
    public static final int PERIOD = Input.Keys.PERIOD;
    public static final int SHIFT_LEFT = Input.Keys.SHIFT_LEFT;
    public static final int SHIFT_RIGHT = Input.Keys.SHIFT_RIGHT;
    public static final int SPACE = Input.Keys.SPACE;
    public static final int TAB = Input.Keys.TAB;

    public static final int F1 = Input.Keys.F1;
    public static final int F2 = Input.Keys.F2;
    public static final int F3 = Input.Keys.F3;
    public static final int F4 = Input.Keys.F4;
    public static final int F5 = Input.Keys.F5;
    public static final int F6 = Input.Keys.F6;
    public static final int F7 = Input.Keys.F7;
    public static final int F8 = Input.Keys.F8;
    public static final int F11 = Input.Keys.F11;

    // ---- 鼠标：负数区间（见类注释）----
    public static final int MOUSE_LEFT = -1;
    public static final int MOUSE_RIGHT = -2;
    public static final int MOUSE_MIDDLE = -3;
    public static final int MOUSE_BACK = -4;
    public static final int MOUSE_FORWARD = -5;
    /** 滚轮（arc 把滚轮也当成一个"键"，libgdx 走 {@code scrolled()} 回调，这里只保留常量做键位绑定）。 */
    public static final int SCROLL = -6;

    private KeyCode(){
    }

    /** @return 名字对应的键码；名字非法返回 -1（对应原版枚举的 {@code KeyCode.valueOf}，MapEditorDialog 用字符串拼名字）。 */
    public static int valueOf(String name){
        if(name == null) return -1;
        switch(name){
            case "A": return A;  case "B": return B;  case "C": return C;  case "D": return D;
            case "E": return E;  case "F": return F;  case "G": return G;  case "H": return H;
            case "I": return I;  case "J": return J;  case "K": return K;  case "L": return L;
            case "M": return M;  case "N": return N;  case "O": return O;  case "P": return P;
            case "Q": return Q;  case "R": return R;  case "S": return S;  case "T": return T;
            case "U": return U;  case "V": return V;  case "W": return W;  case "X": return X;
            case "Y": return Y;  case "Z": return Z;
            case "NUM_0": return NUM_0;  case "NUM_1": return NUM_1;  case "NUM_2": return NUM_2;
            case "NUM_3": return NUM_3;  case "NUM_4": return NUM_4;  case "NUM_5": return NUM_5;
            case "NUM_6": return NUM_6;  case "NUM_7": return NUM_7;  case "NUM_8": return NUM_8;
            case "NUM_9": return NUM_9;
            case "BACK": return BACK;
            case "COMMA": return COMMA;
            case "CONTROL_LEFT": return CONTROL_LEFT;
            case "CONTROL_RIGHT": return CONTROL_RIGHT;
            case "DOWN": return DOWN;
            case "UP": return UP;
            case "LEFT": return LEFT;
            case "RIGHT": return RIGHT;
            case "ENTER": return ENTER;
            case "ESCAPE": return ESCAPE;
            case "PERIOD": return PERIOD;
            case "SHIFT_LEFT": return SHIFT_LEFT;
            case "SHIFT_RIGHT": return SHIFT_RIGHT;
            case "SPACE": return SPACE;
            case "TAB": return TAB;
            case "F1": return F1;  case "F2": return F2;  case "F3": return F3;  case "F4": return F4;
            case "F5": return F5;  case "F6": return F6;  case "F7": return F7;  case "F8": return F8;
            case "F11": return F11;
            case "MOUSE_LEFT": return MOUSE_LEFT;
            case "MOUSE_RIGHT": return MOUSE_RIGHT;
            case "MOUSE_MIDDLE": return MOUSE_MIDDLE;
            case "MOUSE_BACK": return MOUSE_BACK;
            case "MOUSE_FORWARD": return MOUSE_FORWARD;
            case "SCROLL": return SCROLL;
            default: return -1;
        }
    }

    /** @return 键码是否属于鼠标域（负数区间）。 */
    public static boolean isMouse(int keyCode){
        return keyCode < 0;
    }
}
