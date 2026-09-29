package com.phoenix.game.graphics;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * 命名颜色表，参照 arc.graphics.Colors。
 * <p>arc 的 Label 支持 {@code [name]} 形式的颜色标记，靠这张表把名字解析成颜色；
 * libgdx 没有等价物，这里照搬同样的名字集合。查找时忽略大小写与下划线
 * （所以 {@code [lightgray]}、{@code [LIGHT_GRAY]} 都能命中）。
 */
public class Colors{
    private static final ObjectMap<String, Color> colors = new ObjectMap<>();

    static{
        put("clear", Color.CLEAR);
        put("white", Color.WHITE);
        put("lightgray", Color.LIGHT_GRAY);
        put("gray", Color.GRAY);
        put("darkgray", Color.DARK_GRAY);
        put("black", Color.BLACK);

        put("blue", Color.BLUE);
        put("navy", Color.NAVY);
        put("royal", Color.ROYAL);
        put("slate", Color.SLATE);
        put("sky", Color.SKY);
        put("cyan", Color.CYAN);
        put("teal", Color.TEAL);

        put("green", Color.GREEN);
        put("chartreuse", Color.CHARTREUSE);
        put("lime", Color.LIME);
        put("forest", Color.FOREST);
        put("olive", Color.OLIVE);

        put("yellow", Color.YELLOW);
        put("gold", Color.GOLD);
        put("goldenrod", Color.GOLDENROD);
        put("orange", Color.ORANGE);
        put("brown", Color.BROWN);
        put("tan", Color.TAN);

        put("firebrick", Color.FIREBRICK);
        put("red", Color.RED);
        put("scarlet", Color.SCARLET);
        put("coral", Color.CORAL);
        put("salmon", Color.SALMON);
        put("pink", Color.PINK);
        put("magenta", Color.MAGENTA);
        put("purple", Color.PURPLE);
        put("violet", Color.VIOLET);
        put("maroon", Color.MAROON);
        //⚠ R2 批补漏：arc 的 `Colors.java` 有三条本项目漏抄的名字，而它们的**目标颜色名与 libgdx 不同名**，
        //  所以不能靠"忽略大小写"命中（`[crimson]` 在 bundle 里出现 7 次，此前一直原样显示成字面量）：
        //   `arc/Colors.java:114` acid   → arc `Color.acid`   = 0x7fff00ff = libgdx `Color.CHARTREUSE`
        //   `arc/Colors.java:126` brick  → arc `Color.brick`  = 0xb22222ff = libgdx `Color.FIREBRICK`
        //   `arc/Colors.java:138` crimson→ arc `Color.scarlet`（**刻意不是 libgdx 的 CRIMSON**）
        put("acid", Color.CHARTREUSE);
        put("brick", Color.FIREBRICK);
        put("crimson", Color.SCARLET);
    }

    /** 注册一个命名颜色（对应 arc 的 {@code Colors.put}）；同名覆盖。 */
    public static void put(String name, Color color){
        colors.put(key(name), color);
    }

    /** @return 名字对应的颜色，未注册返回 null（对应 arc 的 {@code Colors.get}）。 */
    public static Color get(String name){
        return colors.get(key(name));
    }

    /** @return 规范化后的查找键：小写并去掉下划线。 */
    private static String key(String name){
        return name.toLowerCase().replace("_", "");
    }
}
