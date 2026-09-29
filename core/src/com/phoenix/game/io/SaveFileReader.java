package com.phoenix.game.io;

import com.badlogic.gdx.utils.ObjectMap;
import com.phoenix.game.struct.StringMap;
import com.phoenix.game.util.io.CounterInputStream;
import com.phoenix.game.util.io.ReusableByteOutStream;
import com.phoenix.game.world.WorldContext;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 存档"分区"读写基类。参照 Mindustry {@code mindustry.io.SaveFileReader} 移植。
 *
 * <p>MSAV 文件由若干**带长度前缀的区块**串起来（meta / content / map / entities），
 * 本类只负责区块的封装与解包，具体内容由子类（{@link SaveVersion} 与 {@code versions/SaveN}）实现。
 *
 * <p><b>与原版的差异 + 刻意降级</b>
 * <ul>
 *   <li>{@code arc.util.io.ReusableByteOutStream} / {@code arc.util.io.CounterInputStream} 本项目没有
 *       → 已按最小实现补到 {@code com.phoenix.game.util.io}（包路径按映射规则落地）。
 *       {@code ReusableByteOutStream} 只是把 {@code ByteArrayOutputStream} 的内部缓冲区暴露出来
 *       （{@code getBytes()} 不拷贝），{@code CounterInputStream} 统计经过它的字节数，
 *       用于 {@link #region(String, DataInput, CounterInputStream, IORunner)} 的长度自检。</li>
 *   <li>原版 {@code fallback = ObjectMap.of()}（arc 的静态工厂），libgdx 的 {@code ObjectMap} 没有
 *       {@code of()} → 改成 {@code new ObjectMap<>()}（语义等价）。</li>
 *   <li>{@code writeStringMap} 的形参类型保持原版的 {@code ObjectMap<String, String>}
 *       （不改成项目自有的 {@code struct.StringMap}），这样 {@code SaveVersion.writeMeta} 可以
 *       把 {@code StringMap} 直接传进来（它是 {@code ObjectMap} 的子类）。</li>
 *   <li>本类**没有调用点**：本项目 {@link SaveIO} 是"整体 zlib + 自有 chunk 布局"的重写版，
 *       区块逻辑内联在里面（见 {@code SaveIO.writeChunk/readChunk}），不走本类。
 *       刻意保留：若以后要读原版 MSAV，需要一个 {@code SaveIO} 的兼容分支来驱动本类 + {@link SaveVersion}。</li>
 * </ul>
 */
public abstract class SaveFileReader{
    protected final ReusableByteOutStream byteOutput = new ReusableByteOutStream();
    protected final DataOutputStream dataBytes = new DataOutputStream(byteOutput);
    protected final ReusableByteOutStream byteOutputSmall = new ReusableByteOutStream();
    protected final DataOutputStream dataBytesSmall = new DataOutputStream(byteOutputSmall);
    /** 老存档内容名的回退映射（原名 → 当前名）；原版由 {@code SaveIO} 在读到未知名字时填充。 */
    protected final ObjectMap<String, String> fallback = new ObjectMap<>();

    protected void region(String name, DataInput stream, CounterInputStream counter, IORunner<DataInput> cons) throws IOException{
        counter.resetCount();
        int length;
        try{
            length = readChunk(stream, cons);
        }catch(Throwable e){
            throw new IOException("Error reading region \"" + name + "\".", e);
        }

        //-4 = 减掉 length 自己的 4 字节（区块长度是 int 前缀）
        if(length != counter.count() - 4){
            throw new IOException("Error reading region \"" + name + "\": read length mismatch. Expected: " + length + "; Actual: " + (counter.count() - 4));
        }
    }

    protected void region(String name, DataOutput stream, IORunner<DataOutput> cons) throws IOException{
        try{
            writeChunk(stream, cons);
        }catch(Throwable e){
            throw new IOException("Error writing region \"" + name + "\".", e);
        }
    }

    public void writeChunk(DataOutput output, IORunner<DataOutput> runner) throws IOException{
        writeChunk(output, false, runner);
    }

    /** Write a chunk of input to the stream. An integer of some length is written first, followed by the data. */
    public void writeChunk(DataOutput output, boolean isByte, IORunner<DataOutput> runner) throws IOException{
        ReusableByteOutStream dout = isByte ? byteOutputSmall : byteOutput;
        //reset output position
        dout.reset();
        //write the needed info
        runner.accept(isByte ? dataBytesSmall : dataBytes);
        int length = dout.size();
        //write length (either int or byte) followed by the output bytes
        if(!isByte){
            output.writeInt(length);
        }else{
            if(length > Short.MAX_VALUE){
                throw new IOException("Byte write length exceeded: " + length + " > " + Short.MAX_VALUE);
            }
            output.writeShort(length);
        }
        output.write(dout.getBytes(), 0, length);
    }

    public int readChunk(DataInput input, IORunner<DataInput> runner) throws IOException{
        return readChunk(input, false, runner);
    }

    /** Reads a chunk of some length. Use the runner for reading to catch more descriptive errors. */
    public int readChunk(DataInput input, boolean isByte, IORunner<DataInput> runner) throws IOException{
        int length = isByte ? input.readUnsignedShort() : input.readInt();
        runner.accept(input);
        return length;
    }

    public void skipRegion(DataInput input) throws IOException{
        skipRegion(input, false);
    }

    /** Skip a region completely. */
    public void skipRegion(DataInput input, boolean isByte) throws IOException{
        int length = readChunk(input, isByte, t -> {});
        int skipped = input.skipBytes(length);
        if(length != skipped){
            throw new IOException("Could not skip bytes. Expected length: " + length + "; Actual length: " + skipped);
        }
    }

    public void writeStringMap(DataOutput stream, ObjectMap<String, String> map) throws IOException{
        stream.writeShort(map.size);
        for(ObjectMap.Entry<String, String> entry : map.entries()){
            stream.writeUTF(entry.key);
            stream.writeUTF(entry.value);
        }
    }

    public StringMap readStringMap(DataInput stream) throws IOException{
        StringMap map = new StringMap();
        short size = stream.readShort();
        for(int i = 0; i < size; i++){
            map.put(stream.readUTF(), stream.readUTF());
        }
        return map;
    }

    public abstract void read(java.io.DataInputStream stream, CounterInputStream counter, WorldContext context) throws IOException;

    public abstract void write(DataOutputStream stream) throws IOException;

    protected interface IORunner<T>{
        void accept(T stream) throws IOException;
    }
}
