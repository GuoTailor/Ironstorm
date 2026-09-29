package com.phoenix.game.net;

import com.phoenix.game.Vars;
import com.phoenix.game.game.Gamemode;

/**
 * 服务器列表/发现结果的一条记录（名字/地址/地图/波次/人数/版本/模式）。
 * <p>对应原版 {@code mindustry.net.Host}，逐字移植（{@code Vars.port} 与
 * {@code com.phoenix.game.game.Gamemode} 均已存在）。
 *
 * <p><b>本类目前没有生产/消费方</b>：原版由 {@code Net.pingHost}/{@code Net.discoverServers}
 * 填充、由 {@code JoinDialog} 渲染；本项目 {@code Net} 还没有这两个方法
 * → 属"只新增文件"，等 {@code Net.pingHost} 与 {@code JoinDialog} 回填批次接线。
 */
public class Host{
    public final String name;
    public final String address;
    public final String mapname, description;
    public final int wave;
    public final int players, playerLimit;
    public final int version;
    public final String versionType;
    public final Gamemode mode;
    public int ping, port = Vars.port;

    public Host(String name, String address, String mapname, int wave, int players, int version, String versionType, Gamemode mode, int playerLimit, String description){
        this.name = name;
        this.address = address;
        this.players = players;
        this.mapname = mapname;
        this.wave = wave;
        this.version = version;
        this.versionType = versionType;
        this.playerLimit = playerLimit;
        this.mode = mode;
        this.description = description;
    }
}
