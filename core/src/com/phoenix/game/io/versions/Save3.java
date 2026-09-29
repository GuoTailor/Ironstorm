package com.phoenix.game.io.versions;

import com.phoenix.game.io.SaveVersion;

/**
 * 存档格式 v3。参照 Mindustry {@code mindustry.io.versions.Save3} 移植。
 *
 * <p>v3 相对 v2 没有任何结构变化（只是内容表变了），所以它除了版本号之外什么都不用覆写。
 * 原版 {@code SaveIO.versionArray.peek()} 就是它 —— 也就是说 v3 是**写档时的当前版本**。
 *
 * <p><b>与原版的差异 + 刻意保留</b>
 * <ul>
 *   <li>无差异（9 行的空壳类，逐字照抄）。</li>
 *   <li>刻意保留：本类**刻意不接线** —— 本项目 {@link com.phoenix.game.io.SaveIO} 是重写版
 *       （自有 PHNX 头 + 整体 zlib + 名字表 content 区，{@code version = 2}），
 *       写档走它自己，不经过本类。本类是"齐文件"的兼容层。</li>
 * </ul>
 */
public class Save3 extends SaveVersion{
    public Save3(){
        super(3);
    }
}
