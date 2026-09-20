package com.phoenix.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.phoenix.game.core.Core;
import com.phoenix.game.input.DesktopInput;
import com.phoenix.game.ClientLauncher;

// Please note that on macOS your application needs to be started with the -XstartOnFirstThread JVM argument
public class DesktopLauncher {
	public static void main (String[] arg) {
//        System.out.println(MathUtils.atan2Deg360(-6.597307f, 0.18853895f));
//        System.out.println(MathUtils.atan2Deg(-6.597307f, 0.18853895f));
//        System.out.println(MathUtils.lerpAngleDeg(101.12189f, 271.63702f, 3.3000004f));
//        System.out.println(Math.atan2(-6.597307f, 0.18853895f));
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setForegroundFPS(60);
		config.setTitle("phoenix");
		//窗口尺寸与 Mindustry 桌面端一致：默认 900x700，启动时最大化
		config.setWindowedMode(DesktopInput.defaultWidth, DesktopInput.defaultHeight);
		config.setMaximized(true);
		config.setResizable(true);
		//Mindustry 的窗口配置里 depth / stencil 都是 0
		config.setBackBufferConfig(8, 8, 8, 8, 0, 0, 0);
		Core.app = new Lwjgl3Application(new ClientLauncher(), config);
		Core.graphics = Core.app.getGraphics();
        Core.files = Gdx.files;
		System.out.println("初始化完成>>>>>>>>>>");
	}
}
