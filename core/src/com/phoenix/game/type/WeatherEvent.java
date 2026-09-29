package com.phoenix.game.type;

import com.phoenix.game.ctype.Content;
import com.phoenix.game.ctype.ContentType;

/**
 * 天气事件。参照 Mindustry {@code mindustry.type.WeatherEvent} 移植。
 * <p>原版注释即 "currently unimplemented, see trello for implementation plans" —— 天气系统尚未实现，
 * 本类只是占住 {@link ContentType#weather} 这个内容槽位。无降级。
 */
public class WeatherEvent extends Content{
    public final String name;

    public WeatherEvent(String name){
        this.name = name;
    }

    @Override
    public ContentType getContentType(){
        return ContentType.weather;
    }
}
