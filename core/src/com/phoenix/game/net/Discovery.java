package com.phoenix.game.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;

/**
 * LAN 服务器发现（phoenix 自有格式）。对应原版 Mindustry 的 multicast 发现，协议自成一套。
 * <p>服务端在 {@link #multicastPort} 上加入 {@link #multicastGroup}，收到探测包
 * （{@link #MAGIC} + 空）后回复一行文本：{@code PHOENIX1\t名称\t端口\t当前人数\t上限\t地图}。
 * <p>客户端发 {@link #MAGIC} 探测包到组播地址，收集 {@link #discoverTimeoutMs} 内的回复。
 */
public final class Discovery {
    /** 组播地址。 */
    public static final String multicastGroup = "227.2.7.7";
    /** 组播端口。 */
    public static final int multicastPort = 20151;
    /** 探测魔术字（识别 phoenix 服务器）。 */
    public static final String MAGIC = "PHOENIX_PROBE";
    /** 回复前缀（带版本，便于后续协议演进）。 */
    public static final String REPLY_PREFIX = "PHOENIX1";
    /** 文本编码。 */
    public static final java.nio.charset.Charset charset = StandardCharsets.UTF_8;
    /** 客户端等待发现回复的时长（毫秒）。 */
    public static final int discoverTimeoutMs = 1200;
    /** 包长上限。 */
    private static final int maxPacket = 512;

    private Discovery(){
    }

    /** 一台被发现的服务器。 */
    public static class ServerInfo {
        public String address;
        public int port;
        public String name;
        public int players, playerLimit;
        public String map;

        @Override
        public String toString(){
            return name + " @" + address + ":" + port + " (" + players + (playerLimit > 0 ? "/" + playerLimit : "") + ") " + map;
        }
    }

    /** 服务端发现响应器：后台线程监听组播探测并回复。 */
    public static class Responder {
        private MulticastSocket socket;
        private Thread thread;
        private volatile boolean running;
        /** 本服务器的展示名。 */
        public String serverName = "Phoenix Server";
        /** 服务器实际监听端口。 */
        public int gamePort = 6567;
        /** 状态提供者：返回 {当前人数, 人数上限, 地图名}。 */
        public java.util.function.Supplier<String> statusSupplier = () -> "0\t0\t-";

        /** 启动响应线程。 */
        public void start(){
            try{
                socket = new MulticastSocket(multicastPort);
                socket.setReuseAddress(true);
                socket.joinGroup(InetAddress.getByName(multicastGroup));
            }catch(IOException e){
                System.err.println("启动 LAN 发现失败: " + e);
                return;
            }
            running = true;
            thread = new Thread(this::loop, "LAN Discovery");
            thread.setDaemon(true);
            thread.start();
        }

        /** 停止响应。 */
        public void stop(){
            running = false;
            if(socket != null) socket.close();
        }

        private void loop(){
            byte[] buf = new byte[maxPacket];
            while(running){
                try{
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), charset).trim();
                    if(!msg.equals(MAGIC)) continue;

                    String reply = REPLY_PREFIX + "\t" + serverName + "\t" + gamePort + "\t" + statusSupplier.get();
                    byte[] out = reply.getBytes(charset);
                    socket.send(new DatagramPacket(out, out.length, packet.getAddress(), packet.getPort()));
                }catch(Exception e){
                    if(running) System.err.println("Discovery 响应出错: " + e);
                }
            }
        }
    }

    /** 客户端发现：阻塞至多 {@link #discoverTimeoutMs}，返回发现的服务器列表。 */
    public static java.util.List<ServerInfo> discover(){
        java.util.List<ServerInfo> found = new java.util.ArrayList<>();
        try(DatagramSocket socket = new DatagramSocket()){
            socket.setSoTimeout(discoverTimeoutMs);
            byte[] probe = MAGIC.getBytes(charset);
            socket.send(new DatagramPacket(probe, probe.length,
                InetAddress.getByName(multicastGroup), multicastPort));

            long deadline = System.currentTimeMillis() + discoverTimeoutMs;
            byte[] buf = new byte[maxPacket];
            while(System.currentTimeMillis() < deadline){
                try{
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), charset);
                    ServerInfo info = parseReply(msg, packet.getAddress().getHostAddress());
                    if(info != null) found.add(info);
                }catch(java.net.SocketTimeoutException e){
                    break;
                }
            }
        }catch(IOException e){
            System.err.println("发现服务器失败: " + e);
        }
        return found;
    }

    /** 解析回复文本为 {@link ServerInfo}；不是 phoenix 格式返回 null。 */
    private static ServerInfo parseReply(String msg, String address){
        String[] parts = msg.split("\t");
        if(parts.length < 6 || !REPLY_PREFIX.equals(parts[0])) return null;
        try{
            ServerInfo info = new ServerInfo();
            info.address = address;
            info.name = parts[1];
            info.port = Integer.parseInt(parts[2]);
            info.players = Integer.parseInt(parts[3]);
            info.playerLimit = Integer.parseInt(parts[4]);
            info.map = parts[5];
            return info;
        }catch(NumberFormatException e){
            return null;
        }
    }
}