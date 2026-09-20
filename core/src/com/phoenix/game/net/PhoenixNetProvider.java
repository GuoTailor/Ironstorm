package com.phoenix.game.net;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.phoenix.game.net.Net.SendMode;
import com.phoenix.game.net.Packets.Connect;
import com.phoenix.game.net.Packets.Disconnect;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Java 阻塞 socket 的网络传输实现。对应原版 ArcNetProvider 的 arc.net 传输层，但替换为纯 JDK。
 * <p>客户端先建 TCP 连接，随后绑定同一本地端口的 UDP；服务端 accept 线程 + 每连接 TCP 读线程，
 * 并按 (源IP,源端口) 把 UDP 数据报匹配到对应连接。
 * <p>帧格式：TCP 为「4 字节大端长度 + 一帧包」，UDP 为一个数据报一帧包。
 * 收到的包统一投递到入站线程安全队列，由主循环顺次派发，保证游戏状态只被单线程修改。
 */
public class PhoenixNetProvider implements Net.NetProvider {
    private ServerSocket serverSocket;
    private DatagramSocket udpSocket;
    private Socket clientSocket;
    private DatagramSocket clientUdp;
    private final BlockingQueue<InboundFrame> inbound = new LinkedBlockingQueue<>();
    private final ObjectMap<String, ServerNetConnection> connections = new ObjectMap<>();
    private final ObjectMap<InetSocketAddress, ServerNetConnection> udpMap = new ObjectMap<>();

    private String bindAddress = "0.0.0.0";
    private volatile boolean closed;
    private int connectionCounter;

    @Override
    public void connectClient(String ip, int port, Runnable success) throws IOException {
        try{
            clientSocket = new Socket();
            clientSocket.setTcpNoDelay(true);
            clientSocket.connect(new InetSocketAddress(ip, port), 5000);

            ServerNetConnection con = new ServerNetConnection(clientSocket.getInetAddress().getHostAddress(), "client", clientSocket);
            new Thread(() -> tcpReadLoop(con, clientSocket, true), "Client Net TCP").start();

            //UDP 绑定到与 TCP 相同本地端口，服务端据此匹配
            try{
                clientUdp = new DatagramSocket(clientSocket.getLocalPort());
                new Thread(this::clientUdpReadLoop, "Client Net UDP").start();
            }catch(IOException e){
                clientUdp = null;
                System.out.println("UDP 绑定失败，降级为纯 TCP: " + e);
            }

            success.run();
        }catch(IOException e){
            cleanup();
            throw e;
        }
    }

    @Override
    public void sendClient(Object object, SendMode mode){
        //已断开（读线程 cleanup 关闭了 socket）时直接丢弃：否则到超时判定前每帧/每秒都抛 SocketException 刷屏
        if(clientSocket == null || clientSocket.isClosed()) return;
        byte[] frame = serialize(object);
        try{
            if(mode == SendMode.udp && clientUdp != null && !clientUdp.isClosed()){
                sendUdp(clientUdp, frame, clientSocket.getInetAddress(), clientSocket.getPort());
            }else{
                writeTcp(clientSocket, frame);
            }
        }catch(IOException e){
            System.out.println("Client send(" + mode + ") failed: " + e);
        }
    }

    @Override
    public void disconnectClient(){
        cleanup();
    }

    @Override
    public void hostServer(int port) throws IOException {
        try{
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(bindAddress, port));
            udpSocket = new DatagramSocket(port);

            Thread accept = new Thread(this::acceptLoop, "Server Accept");
            accept.setDaemon(true);
            accept.start();
            Thread udp = new Thread(this::serverUdpReadLoop, "Server UDP");
            udp.setDaemon(true);
            udp.start();
        }catch(BindException e){
            throw new IOException("Port " + port + " is already in use.");
        }
    }

    @Override
    public Iterable<? extends NetConnection> getConnections(){
        Array<ServerNetConnection> arr = new Array<>();
        synchronized(connections){
            for(ServerNetConnection con : connections.values()){
                arr.add(con);
            }
        }
        return arr;
    }

    @Override
    public void closeServer(){
        closed = true;
        cleanup();
    }

    /** 阻塞取一个入站帧。 */
    public InboundFrame pollInbound() throws InterruptedException {
        return inbound.take();
    }

    /** 是否有挂起入站帧。 */
    public boolean hasInbound(){
        return !inbound.isEmpty();
    }

    // ---- 内部 ----

    private void cleanup(){
        if(clientSocket != null) try{ clientSocket.close(); }catch(IOException ignored){}
        if(serverSocket != null) try{ serverSocket.close(); }catch(IOException ignored){}
        if(udpSocket != null) udpSocket.close();
        if(clientUdp != null) clientUdp.close();
        inbound.clear();
        synchronized(connections){ connections.clear(); }
    }

    private void acceptLoop(){
        while(!closed){
            try{
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                final String address = socket.getInetAddress().getHostAddress();
                final int tcpPort = socket.getPort();

                ServerNetConnection con;
                synchronized(connections){
                    con = new ServerNetConnection(address, String.valueOf(connectionCounter++), socket);
                    connections.put(con.id, con);
                }
                //UDP 匹配：客户端 UDP 源端口 == TCP 源端口
                udpMap.put(new InetSocketAddress(socket.getInetAddress(), tcpPort), con);

                Connect c = new Connect();
                c.addressTCP = address;
                post(null, c);

                Thread t = new Thread(() -> tcpReadLoop(con, socket, false), "Conn " + con.id + " TCP");
                t.setDaemon(true);
                t.start();
            }catch(Exception e){
                if(closed) break;
                System.out.println("Server accept error: " + e);
            }
        }
    }

    /** 单条连接 TCP 读循环：读 4 字节长度 + 一帧。 */
    private void tcpReadLoop(ServerNetConnection con, Socket socket, boolean clientSide){
        try{
            InputStream in = socket.getInputStream();
            while(!closed && !socket.isClosed()){
                byte[] lenBytes = readFully(in, 4);
                if(lenBytes == null) break;
                int len = ((lenBytes[0] & 0xFF) << 24) | ((lenBytes[1] & 0xFF) << 16)
                        | ((lenBytes[2] & 0xFF) << 8) | (lenBytes[3] & 0xFF);
                if(len > PacketSerializer.MAX_FRAME_SIZE) throw new IOException("Frame too large: " + len);
                byte[] body = readFully(in, len);
                if(body == null) break;
                Object packet = PacketSerializer.read(ByteBuffer.wrap(body));
                post(clientSide ? null : con, packet);
            }
        }catch(Exception e){
            if(!closed) System.out.println((clientSide ? "Client" : "Conn " + con.id) + " TCP read error: " + e);
        }finally{
            if(clientSide){
                cleanup();
            }else{
                synchronized(connections){ connections.remove(con.id); }
                udpMap.remove(new InetSocketAddress(socket.getInetAddress(), socket.getPort()));
                Disconnect d = new Disconnect();
                d.reason = "closed";
                post(con, d);
            }
        }
    }

    private void serverUdpReadLoop(){
        byte[] buf = new byte[PacketSerializer.MAX_FRAME_SIZE];
        while(!closed){
            try{
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                udpSocket.receive(packet);
                InetSocketAddress addr = new InetSocketAddress(packet.getAddress(), packet.getPort());
                ServerNetConnection con = udpMap.get(addr);
                if(con == null) continue; //未注册 UDP 来源，忽略（阶段 3 做注册握手）
                Object obj = PacketSerializer.read(ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength()));
                post(con, obj);
            }catch(Exception e){
                if(udpSocket.isClosed()) break;
                if(!closed) System.out.println("Server UDP read error: " + e);
            }
        }
    }

    private void clientUdpReadLoop(){
        byte[] buf = new byte[PacketSerializer.MAX_FRAME_SIZE];
        while(!closed && !clientUdp.isClosed()){
            try{
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                clientUdp.receive(packet);
                Object obj = PacketSerializer.read(ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength()));
                post(null, obj);
            }catch(Exception e){
                if(clientUdp.isClosed()) break;
                if(!closed) System.out.println("Client UDP read error: " + e);
            }
        }
    }

    private void sendUdp(DatagramSocket socket, byte[] frame, java.net.InetAddress addr, int port) throws IOException {
        socket.send(new DatagramPacket(frame, frame.length, addr, port));
    }

    private void writeTcp(Socket socket, byte[] frame) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write((frame.length >>> 24) & 0xFF);
        out.write((frame.length >>> 16) & 0xFF);
        out.write((frame.length >>> 8) & 0xFF);
        out.write(frame.length & 0xFF);
        out.write(frame);
        out.flush();
    }

    private static byte[] readFully(InputStream in, int n) throws IOException {
        byte[] data = new byte[n];
        int got = 0;
        while(got < n){
            int r = in.read(data, got, n - got);
            if(r < 0) return null;
            got += r;
        }
        return data;
    }

    private static byte[] serialize(Object object){
        ByteBuffer buf = ByteBuffer.allocate(PacketSerializer.MAX_FRAME_SIZE);
        PacketSerializer.write(buf, object);
        byte[] out = new byte[buf.position()];
        System.arraycopy(buf.array(), 0, out, 0, buf.position());
        return out;
    }

    private void post(ServerNetConnection con, Object packet){
        try{
            inbound.put(new InboundFrame(con, packet));
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }

    /** 服务端侧具体连接。 */
    class ServerNetConnection extends NetConnection {
        final String id;
        final Socket tcp;

        ServerNetConnection(String address, String id, Socket tcp){
            super(address);
            this.id = id;
            this.tcp = tcp;
        }

        @Override
        public void send(Object object, SendMode mode){
            byte[] frame = serialize(object);
            try{
                if(mode == SendMode.udp && udpSocket != null && !tcp.isClosed()){
                    sendUdp(udpSocket, frame, tcp.getInetAddress(), tcp.getPort());
                }else if(!tcp.isClosed()){
                    synchronized(tcp){
                        writeTcp(tcp, frame);
                    }
                }
            }catch(IOException e){
                System.out.println("Conn " + id + " send(" + mode + ") failed: " + e);
            }
        }

        @Override
        public void close(){
            try{ tcp.close(); }catch(IOException ignored){}
        }
    }

    /** 入站帧：连接 + 已反序列化对象。 */
    public static class InboundFrame {
        public final NetConnection connection;
        public final Object packet;

        public InboundFrame(NetConnection connection, Object packet){
            this.connection = connection;
            this.packet = packet;
        }
    }
}