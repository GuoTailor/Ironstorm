package com.phoenix.game.net;

import com.phoenix.game.Vars;
import com.phoenix.game.core.Version;
import com.phoenix.game.game.Gamemode;
import com.phoenix.game.game.Rules;
import com.phoenix.game.io.SaveIO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * 世界数据传输编解码。对应原版 Mindustry 的 mindustry.io.NetworkIO。
 * <p>服务端把整局世界经 {@link SaveIO} 序列化并 zlib 压缩成 byte[]，放进 {@link Packets.WorldStream}
 * 分块发给客户端；客户端解压后经 {@link SaveIO#read} 重建本地世界。压缩用 java.util.zip（暂不引 LZ4）。
 */
public final class NetworkIO {
    private NetworkIO(){
    }

    /** 把当前世界序列化为压缩字节（头 4 字节为解压后大小，便于客户端预分配）。 */
    public static byte[] writeWorld(){
        try{
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(raw);
            SaveIO.write(out);
            out.flush();
            byte[] data = raw.toByteArray();

            ByteArrayOutputStream comp = new ByteArrayOutputStream();
            Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
            try(DeflaterOutputStream dos = new DeflaterOutputStream(comp, deflater)){
                dos.write(data);
            }
            byte[] compData = comp.toByteArray();

            //4 字节原始大小前缀 + 压缩数据
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            java.io.DataOutputStream rout = new java.io.DataOutputStream(result);
            rout.writeInt(data.length);
            rout.write(compData);
            rout.flush();
            return result.toByteArray();
        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    /** 把压缩世界字节重建为本地世界（Vars.world/teams/state 被替换）。 */
    public static void loadWorld(byte[] bytes){
        try{
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            int rawSize = in.readInt();
            byte[] comp = new byte[in.available()];
            in.readFully(comp);

            java.io.ByteArrayInputStream raw = new java.io.ByteArrayInputStream(comp);
            InflaterInputStream iis = new InflaterInputStream(raw);
            SaveIO.read(new DataInputStream(iis));
        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    /** 供 {@link Packets.WorldStream} 传输用的世界字节（含大小前缀）。 */
    public static byte[] writeWorldBytes(){
        return writeWorld();
    }

    // ------------------------------------------------------------------
    // 服务器信息编解码（对应原版 NetworkIO.writeServerData/readServerData）
    // 逐字节兼容原版线路格式：{name,map,players,wave,version,vertype,gamemode,limit,description}
    // ------------------------------------------------------------------

    /** 字符串编码。原版用 arc.util.Strings.charset（即 UTF-8），这里用等价常量。 */
    private static final Charset charset = StandardCharsets.UTF_8;

    /**
     * 写出服务器信息（对应原版 NetworkIO.writeServerData）：ping 的应答负载。
     * <p>⚠ 与原版的差异：原版取 {@code headless ? Config.name : player.name}、描述取
     * {@code Config.desc}，本项目没有 {@code Config} → 名字回退常量、描述留空。
     */
    public static ByteBuffer writeServerData(){
        //原版 NetworkIO.java:64-84
        String name = Vars.player == null ? "Server" : Vars.player.name;              //⚠ 原版是 headless ? Config.name : player.name
        String description = "";                                                      //⚠ 本项目没有 Config.desc
        String map = Vars.world == null || Vars.world.getMap() == null ? "None" : Vars.world.getMap().name;
        Rules rules = Vars.state == null ? new Rules() : Vars.state.rules;            //⚠ null 守卫：UDP 线程里读，state 可能未就绪

        ByteBuffer buffer = ByteBuffer.allocate(512);

        writeString(buffer, name, 100);
        writeString(buffer, map);

        buffer.putInt(Vars.playerGroup == null ? 0 : Vars.playerGroup.size());
        buffer.putInt(Vars.state == null ? 0 : Vars.state.wave);
        buffer.putInt(Version.build);
        writeString(buffer, Version.type);

        buffer.put((byte)Gamemode.bestFit(rules).ordinal());
        buffer.putInt(Vars.netServer == null ? 0 : Vars.netServer.admins.playerLimit);

        writeString(buffer, description, 100);
        return buffer;
    }

    /** 读回服务器信息（对应原版 NetworkIO.java:86-98）。 */
    public static Host readServerData(String hostAddress, ByteBuffer buffer){
        String host = readString(buffer);
        String map = readString(buffer);
        int players = buffer.getInt();
        int wave = buffer.getInt();
        int version = buffer.getInt();
        String vertype = readString(buffer);
        //原版用 Gamemode.all[buffer.get()]；本项目 Gamemode.all = values() 已存在（Gamemode.java:65）
        Gamemode gamemode = Gamemode.all[buffer.get() & 0xff];
        int limit = buffer.getInt();
        String description = readString(buffer);

        return new Host(host, hostAddress, map, wave, players, version, vertype, gamemode, limit, description);
    }

    /** 写一个「1 字节长度 + UTF-8 字节」的字符串（对应原版 NetworkIO.java:100-109）。 */
    private static void writeString(ByteBuffer buffer, String string, int maxlen){
        byte[] bytes = string.getBytes(charset);
        //todo truncating this way may lead to wierd encoding errors at the ends of strings...
        if(bytes.length > maxlen){
            bytes = Arrays.copyOfRange(bytes, 0, maxlen);
        }

        buffer.put((byte)bytes.length);
        buffer.put(bytes);
    }

    /** 默认最长 32 字节（对应原版 NetworkIO.java:111-113）。 */
    private static void writeString(ByteBuffer buffer, String string){
        writeString(buffer, string, 32);
    }

    /** 读一个「1 字节长度 + UTF-8 字节」的字符串（对应原版 NetworkIO.java:115-120）。 */
    private static String readString(ByteBuffer buffer){
        short length = (short)(buffer.get() & 0xff);
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, charset);
    }
}