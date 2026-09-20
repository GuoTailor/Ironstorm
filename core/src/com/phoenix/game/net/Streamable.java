package com.phoenix.game.net;

import com.phoenix.game.net.Packets.StreamBegin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 可分块传输的流式包。对应原版 Mindustry 的 mindustry.net.Streamable。
 * <p>大文件（如世界数据）拆成 {@link StreamBegin} + 若干 {@link StreamChunk}（每块 512B）发送，
 * 接收方用 {@link StreamBuilder} 累积后重建出原始 {@link Streamable} 子类。
 */
public abstract class Streamable implements Packet {
    /** 累积出的完整数据流（transient：不直接序列化，由分块重建填充）。 */
    public transient ByteArrayInputStream stream;

    @Override
    public boolean isImportant(){
        return true;
    }

    /** 接收端累积器：按 id 接收各 Chunk，收满 total 后 build() 出完整包。 */
    public static class StreamBuilder {
        public final int id;
        public final byte type;
        public final int total;
        public final ByteArrayOutputStream stream = new ByteArrayOutputStream();

        public StreamBuilder(StreamBegin begin){
            this.id = begin.id;
            this.type = begin.type;
            this.total = begin.total;
        }

        /** 接收进度 0~1。 */
        public float progress(){
            return (float)stream.size() / total;
        }

        public void add(byte[] bytes){
            try{
                stream.write(bytes);
            }catch(IOException e){
                throw new RuntimeException(e);
            }
        }

        /** 重建出完整 Streamable，并把累积数据塞进它的 stream。 */
        public Streamable build(){
            Streamable s = (Streamable)Registrator.getByID(type).constructor.get();
            s.stream = new ByteArrayInputStream(stream.toByteArray());
            return s;
        }

        public boolean isDone(){
            return stream.size() >= total;
        }
    }
}