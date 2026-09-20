package com.phoenix.game.mod;

/**
 * 模组/插件基类。对应原版 Mindustry 的 mindustry.mod.Mod（精简版）。
 * <p>插件可覆写 {@link #init()} 与 {@link #registerServerCommands}，由 {@link Mods} 在服务器启动时调用。
 */
public class Mod {
    /** 所有插件实例化、命令注册完成后调用一次。 */
    public void init(){
    }

    /** 注册服务端控制台命令。 */
    public void registerServerCommands(CommandHandler handler){
    }

    /** 注册客户端游戏内聊天命令。 */
    public void registerClientCommands(CommandHandler handler){
    }
}