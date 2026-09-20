package com.phoenix.game.net;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pools;
import com.phoenix.game.net.Packets.StreamBegin;
import com.phoenix.game.net.Packets.StreamChunk;
import com.phoenix.game.net.Streamable.StreamBuilder;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * 网络门面与传输抽象。对应原版 Mindustry 的 mindustry.net.Net。
 * <p>持有连接/流状态，把收包按类型派发给注册的监听器；{@link NetProvider} 是传输层接口，
 * 具体 socket 实现可替换（阶段 2 用 {@link PhoenixNetProvider}，Java 阻塞 socket）。
 * <p>阶段 2 精简版：快照压缩先用 java.util.zip（协议语义占位，LZ4 在阶段 6 对齐原版线路时再换）。
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class Net {
    private boolean server;
    private boolean active;
    private boolean clientLoaded;
    private StreamBuilder currentStream;

    private final Array<Object> packetQueue = new Array<>();
    private final ObjectMap<Class<?>, Consumer> clientListeners = new ObjectMap<>();
    private final ObjectMap<Class<?>, BiConsumer<NetConnection, Object>> serverListeners = new ObjectMap<>();
    private final IntMap<StreamBuilder> streams = new IntMap<>();

    private final NetProvider provider;

    public Net(NetProvider provider){
        this.provider = provider;
    }

    /**
     * 设置客户端加载状态。加载完成后会立即补处理加载期间排队的包。
     */
    public void setClientLoaded(boolean loaded){
        clientLoaded = loaded;
        if(loaded){
            for(int i = 0; i < packetQueue.size; i++){
                handleClientReceived(packetQueue.get(i));
            }
        }
        packetQueue.clear();
    }

    public void setClientConnected(){
        active = true;
        server = false;
    }

    /** 以客户端身份连接服务器。@return 是否成功发起连接；false 表示未进入 active 状态（连接失败） */
    public boolean connect(String ip, int port, Runnable success){
        try{
            if(!active){
                provider.connectClient(ip, port, success);
                active = true;
                server = false;
                return true;
            }else{
                throw new IOException("alreadyconnected");
            }
        }catch(IOException e){
            System.err.println("Net connect failed: " + e);
            return false;
        }
    }

    /** 以服务端身份在指定端口监听。 */
    public void host(int port) throws IOException {
        provider.hostServer(port);
        active = true;
        server = true;
    }

    /** 关闭服务端。 */
    public void closeServer(){
        provider.closeServer();
        server = false;
        active = false;
    }

    public void disconnect(){
        provider.disconnectClient();
        server = false;
        active = false;
    }

    /** 快照压缩（阶段 2 用 zlib 占位；原版用 LZ4）。 */
    public byte[] compressSnapshot(byte[] input){
        Deflater deflater = new Deflater();
        deflater.setInput(input);
        deflater.finish();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[512];
        while(!deflater.finished()){
            int n = deflater.deflate(buf);
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** 快照解压（与 compressSnapshot 对应）。 */
    public byte[] decompressSnapshot(byte[] input, int size){
        try{
            Inflater inflater = new Inflater();
            inflater.setInput(input);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[512];
            while(!inflater.finished() && inflater.getRemaining() > 0){
                int n = inflater.inflate(buf);
                if(n == 0) break;
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    public Iterable<? extends NetConnection> getConnections(){
        return provider.getConnections();
    }

    /** 发送对象到所有连接（服务端）或到服务器（客户端）。 */
    public void send(Object object, SendMode mode){
        if(server){
            for(NetConnection con : provider.getConnections()){
                con.send(object, mode);
            }
        }else{
            provider.sendClient(object, mode);
        }
    }

    /** 除 except 外的所有连接都发（服务端）。 */
    public void sendExcept(NetConnection except, Object object, SendMode mode){
        for(NetConnection con : getConnections()){
            if(con != except){
                con.send(object, mode);
            }
        }
    }

    public StreamBuilder getCurrentStream(){
        return currentStream;
    }

    /** 注册客户端监听器。 */
    public <T> void handleClient(Class<T> type, Consumer<T> listener){
        clientListeners.put(type, (Consumer)listener);
    }

    /** 注册服务端监听器。 */
    public <T> void handleServer(Class<T> type, BiConsumer<NetConnection, T> listener){
        serverListeners.put(type, (BiConsumer)listener);
    }

    /** 处理一个收包（客户端）。会处理流分块累积，并按类型派发，处理完归还对象池。 */
    public void handleClientReceived(Object object){
        if(object instanceof StreamBegin){
            StreamBegin b = (StreamBegin)object;
            streams.put(b.id, currentStream = new StreamBuilder(b));

        }else if(object instanceof StreamChunk){
            StreamChunk c = (StreamChunk)object;
            StreamBuilder builder = streams.get(c.id);
            if(builder == null){
                throw new RuntimeException("Received stream chunk without a StreamBegin beforehand!");
            }
            builder.add(c.data);
            if(builder.isDone()){
                streams.remove(builder.id);
                handleClientReceived(builder.build());
                currentStream = null;
            }
        }else if(clientListeners.containsKey(object.getClass())){
            if(clientLoaded || (object instanceof Packet && ((Packet)object).isImportant())){
                clientListeners.get(object.getClass()).accept(object);
                Pools.free(object);
            }else if(!(object instanceof Packet && ((Packet)object).isUnimportant())){
                packetQueue.add(object);
            }else{
                Pools.free(object);
            }
        }else{
            System.err.println("Unhandled packet type: " + object);
        }
    }

    /** 处理一个收包（服务端）。 */
    public void handleServerReceived(NetConnection connection, Object object){
        if(serverListeners.containsKey(object.getClass())){
            serverListeners.get(object.getClass()).accept(connection, object);
            Pools.free(object);
        }else{
            System.err.println("Unhandled packet type: " + object.getClass());
        }
    }

    public boolean active(){
        return active;
    }

    public boolean server(){
        return server && active;
    }

    public boolean client(){
        return !server && active;
    }

    public void dispose(){
        provider.dispose();
        server = false;
        active = false;
    }

    /** 传输方式。 */
    public enum SendMode {
        tcp, udp
    }

    /** 传输层接口。实现负责 socket 读写、帧解析、事件投递。 */
    public interface NetProvider {
        /** 以客户端身份连接服务器。成功回调 success。 */
        void connectClient(String ip, int port, Runnable success) throws IOException;

        /** 客户端发送对象到服务器。 */
        void sendClient(Object object, SendMode mode);

        /** 客户端断开。 */
        void disconnectClient();

        /** 服务端监听端口。 */
        void hostServer(int port) throws IOException;

        /** 服务端所有连接。 */
        Iterable<? extends NetConnection> getConnections();

        /** 服务端关闭。 */
        void closeServer();

        /** 关闭所有连接。 */
        default void dispose(){
            disconnectClient();
            closeServer();
        }
    }
}