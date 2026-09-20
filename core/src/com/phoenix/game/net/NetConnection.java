package com.phoenix.game.net;

import com.badlogic.gdx.utils.Array;
import com.phoenix.game.net.Net.SendMode;
import com.phoenix.game.net.Packets.KickReason;

import java.io.IOException;

/**
 * 单条连接抽象。对应原版 Mindustry 的 mindustry.net.NetConnection。
 * <p>阶段 2 精简版：保留地址、视角窗口、发送/关闭/流式发送等与协议传输相关字段；
 * 玩家身份、封禁、踢出计数等游戏层字段在阶段 3（NetServer）接入。
 */
public abstract class NetConnection {
    public final String address;
    public boolean mobile, modclient;

    /** 客户端视角窗口（阶段 3 接入实体同步时使用）。 */
    public float viewWidth, viewHeight, viewX, viewY;
    public boolean hasConnected, hasBegunConnecting, hasDisconnected;

    public NetConnection(String address){
        this.address = address;
    }

    /** 踢出连接（阶段 2 仅记录并延迟关闭；Call.onKick / 封禁统计在阶段 3 接入）。 */
    public void kick(KickReason reason){
        System.out.println("Kicking connection " + address + "; Reason: " + reason.name());
        com.phoenix.game.core.Time.run(2f, this::close);
    }

    /** 以任意文本原因踢出。 */
    public void kick(String reason){
        System.out.println("Kicking connection " + address + "; Reason: " + reason.replace("\n", " "));
        com.phoenix.game.core.Time.run(2f, this::close);
    }

    public boolean isConnected(){
        return true;
    }

    /** 分块发送一个流式包：先发 StreamBegin，再按 512B 分块发 StreamChunk。 */
    public void sendStream(Streamable stream){
        try{
            Packets.StreamBegin begin = new Packets.StreamBegin();
            begin.total = stream.stream.available();
            begin.type = Registrator.getID(stream.getClass());
            send(begin, SendMode.tcp);
            int cid = begin.id;

            while(stream.stream.available() > 0){
                byte[] bytes = new byte[Math.min(512, stream.stream.available())];
                stream.stream.read(bytes);

                Packets.StreamChunk chunk = new Packets.StreamChunk();
                chunk.id = cid;
                chunk.data = bytes;
                send(chunk, SendMode.tcp);
            }
        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    /** 发送一个对象。实现由具体传输提供（TCP 可靠 / UDP 尽力）。 */
    public abstract void send(Object object, SendMode mode);

    /** 关闭连接。 */
    public abstract void close();
}