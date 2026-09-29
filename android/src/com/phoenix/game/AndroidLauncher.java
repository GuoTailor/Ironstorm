package com.phoenix.game;

import android.content.Context;
import android.os.Bundle;
import android.telephony.TelephonyManager;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.phoenix.game.core.Scl;

/**
 * Android 端入口。参照 Mindustry {@code mindustry.android.AndroidLauncher} 移植。
 * <p>本轮只做「能启动」：直接跑 {@link ClientLauncher}，不接文件选择器/分享/存档 intent/脚本上下文
 * （这些依赖原版 ClientLauncher 上的可覆写钩子，phoenix 的 ClientLauncher 目前没有）。
 */
public class AndroidLauncher extends AndroidApplication{
    /** 平板（无电话模块）上把 UI 整体放大一档，对应原版 doubleScaleTablets。 */
    boolean doubleScaleTablets = true;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        if(doubleScaleTablets && isTablet(this.getContext())){
            Scl.setAddition(0.5f);
        }

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        //隐藏状态栏/导航栏（gdx 1.12 已移除 hideStatusBar，useImmersiveMode 即为此用）
        config.useImmersiveMode = true;
        config.depth = 0;
        config.useAccelerometer = false;
        config.useCompass = false;

        initialize(new ClientLauncher(), config);
    }

    /** @return 是否是无电话模块的设备（原版用它近似判断平板）。 */
    private boolean isTablet(Context context){
        TelephonyManager manager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        return manager != null && manager.getPhoneType() == TelephonyManager.PHONE_TYPE_NONE;
    }
}
