package com.phoenix.game.type;

import com.phoenix.game.ctype.Content;
import com.phoenix.game.ctype.ContentType;

/**
 * 表示"解析失败的内容"的占位类型：任何解析出错的内容都会被它替换。
 * 参照 Mindustry {@code mindustry.type.ErrorContent} 移植。
 * <p>与原版逐字一致，无降级。注意：本类与 {@code WeatherEvent} 一样**不会被实例化**，
 * 只是给"内容加载失败"预留一个可注册的 {@link ContentType#error} 类型
 * （原版 {@code ContentLoader} 也不主动创建它）。
 */
public class ErrorContent extends Content{
    @Override
    public ContentType getContentType(){
        return ContentType.error;
    }
}
