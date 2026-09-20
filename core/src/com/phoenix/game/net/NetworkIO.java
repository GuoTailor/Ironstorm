package com.phoenix.game.net;

import com.phoenix.game.io.SaveIO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
}