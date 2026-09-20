package com.phoenix.game.server;

import com.badlogic.gdx.backends.headless.HeadlessApplication;

/**
 * 服务端启动入口（headless）。对应原版 Mindustry 的 mindustry.server.ServerLauncher。
 * <p>不创建任何渲染窗口：用 libgdx 的 HeadlessApplication 驱动游戏主循环（世界仿真），
 * 控制台命令通过 ServerControl 在标准输入读取。工作目录固定为 {@code <repo>/assets}。
 */
public class ServerLauncher {

    /** 服务端入口。所有参数透传给 {@link ServerControl}。 */
    public static void main(String[] args){
        //headless 后端不渲染，World/Logic/单位/子弹等纯逻辑照常 tick
        new HeadlessApplication(new ServerControl(args));
    }
}