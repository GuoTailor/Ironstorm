package com.phoenix.game.core;

import com.badlogic.gdx.graphics.g2d.BitmapFont;

public class Fonts {
    public static BitmapFont def;
    public static BitmapFont outline;
    public static BitmapFont chat;

    /**
     * 内容名 → 字体图标 unicode 码点（对应原版 {@code Fonts.unicodeIcons}，原版是 arc 的
     * {@code ObjectIntMap<String>}，这里用 libgdx 同名类）。
     * <p>数据源是 {@code icons/icons.properties}（每行 {@code 码点=内容名|贴图名}），
     * 由 {@link #loadContentIcons()} 在启动时解析填充；未加载或查不到时 {@link #getUnicode} 返回 0。
     */
    private static final com.badlogic.gdx.utils.ObjectIntMap<String> unicodeIcons =
        new com.badlogic.gdx.utils.ObjectIntMap<>();

    /**
     * 内容名的字体图标码点（对应原版 {@code Fonts.getUnicode(String)}，原版 Fonts.java:36-38）。
     * <p>消费点：数据库界面 SHIFT+点击把图标字符复制到剪贴板（{@code DatabaseDialog}）。
     * @return 该内容的图标 unicode 码点；未知内容返回 0（原版语义：调用方判 {@code != 0}）
     */
    public static int getUnicode(String content){
        return unicodeIcons.get(content, 0);
    }

    /**
     * 读 {@code icons/icons.properties} 填充 {@link #unicodeIcons}（对应原版
     * {@code Fonts.loadContentIcons()}，原版 Fonts.java:63；调用点在原版 {@code UI} 初始化里、
     * 本项目对应 {@code ClientLauncher} 的 {@code Styles.load()} 之后）。
     *
     * <p><b>刻意降级</b>：原版除了填 {@code unicodeIcons}，还会给 {@code Fonts.icon} 字体
     * （{@code fonts/icon.ttf}，本项目没有该字体）逐个塞 {@code Glyph} 并校验贴图区域在 UI 图集里 ——
     * 这是"字体图标渲染"那半边（聊天/文字混排画图标用）。本项目没有 icon 字体、图标体系是图集制
     * （注解处理器生成的 {@code io.anuke.mindustry.gen.Icon}），所以只移植"名字 → 码点"映射这半边，
     * 够 {@code DatabaseDialog} 的复制彩蛋用；字体渲染那半边不移植（缺字体文件，原样抄会 NPE）。
     * <p>格式：每行 {@code 码点=内容名|贴图名}（如 {@code 63743=spawn|block-spawn-medium}），
     * 只取 {@code =} 左侧码点与右侧 {@code |} 前的名字；解析失败的行跳过（原版直接抛异常，
     * 这里放宽：该文件只是彩蛋数据源，坏一行不该挡启动）。
     */
    public static void loadContentIcons(){
        if(!com.badlogic.gdx.Gdx.files.internal("icons/icons.properties").exists()){
            return;
        }
        String text = com.badlogic.gdx.Gdx.files.internal("icons/icons.properties").readString();
        for(String line : text.split("\n")){
            line = line.trim();
            if(line.isEmpty()) continue;
            int eq = line.indexOf('='), pipe = line.indexOf('|');
            if(eq <= 0 || pipe <= eq) continue;
            try{
                unicodeIcons.put(line.substring(eq + 1, pipe), Integer.parseInt(line.substring(0, eq)));
            }catch(NumberFormatException ignored){
                //码点不是数字 → 坏行，跳过（见方法注释的放宽说明）
            }
        }
    }
}
