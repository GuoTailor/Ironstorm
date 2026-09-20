package com.phoenix.game.net;

import java.nio.ByteBuffer;

/**
 * 网络包基接口。对应原版 Mindustry 的 mindustry.net.Packet。
 * <p>序列化是每类手工 {@link #read}/{@link #write}（写进共享的 ByteBuffer），
 * 外层由 {@link Registrator} 的 1 字节注册表 ID 标识类型，无反射/注解生成。
 * <p>对象由 {@link com.badlogic.gdx.utils.Pools} 管理，收包处理后由 {@link Net} 归还。
 */
public interface Packet {
    /** 从 buffer 读入自身字段。 */
    default void read(ByteBuffer buffer){
    }

    /** 把自身字段写入 buffer。 */
    default void write(ByteBuffer buffer){
    }

    /** 归还对象池前重置字段。 */
    default void reset(){
    }

    /** 重要包：客户端未加载完也会立即处理（用于握手/踢出）。 */
    default boolean isImportant(){
        return false;
    }

    /** 不重要包：客户端未加载完会直接丢弃。 */
    default boolean isUnimportant(){
        return false;
    }
}
