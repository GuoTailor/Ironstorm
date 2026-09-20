package com.phoenix.game.mod;

/**
 * 特殊模组类型：总是隐藏。对应原版 Mindustry 的 mindustry.plugin.Plugin。
 * <p>服务端插件以本类为基类，通过 {@link Mods} 加载并注册服务端命令。
 */
public abstract class Plugin extends Mod {
}