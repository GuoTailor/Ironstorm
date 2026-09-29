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
		//调试钩子：PHOENIX_MOBILE_PORTRAIT=1 时以手机竖屏窗口启动（480x800、不最大化），
		//配合 PHOENIX_MOBILE=1 验证移动端竖屏布局。窗口尺寸必须在 GL 初始化前定好 —— 所以放在这里而不是 ClientLauncher。
		boolean portrait = System.getenv("PHOENIX_MOBILE_PORTRAIT") != null;
		config.setWindowedMode(portrait ? 480 : DesktopInput.defaultWidth, portrait ? 800 : DesktopInput.defaultHeight);
		config.setMaximized(!portrait);
		config.setResizable(true);
		//Mindustry 的窗口配置里 depth / stencil 都是 0
		config.setBackBufferConfig(8, 8, 8, 8, 0, 0, 0);
		Core.app = new Lwjgl3Application(new ClientLauncher(), config);
		Core.graphics = Core.app.getGraphics();
        Core.files = Gdx.files;
		System.out.println("初始化完成>>>>>>>>>>");
	}
}
