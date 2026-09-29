package com.phoenix.game.util.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * 统计已读取字节数的输入流包装。参照 {@code arc.util.io.CounterInputStream} 移植。
 *
 * <p>用途：{@code SaveFileReader.region(...)} 先读 4 字节的区块长度，再读区块数据，
 * 读完后校验 {@code 长度 == count() - 4}（减掉的正是那 4 字节长度前缀），
 * 用来发现"存档被截断/长度字段与实际数据不符"的损坏文件。
 * 所以这里必须把**经过本流的所有字节**都计入 {@link #count()}。
 *
 * <p>注意：调用方要把 {@code DataInputStream} 套在**本流外面**
 * （{@code new DataInputStream(new CounterInputStream(raw))}），否则读到的计数恒为 0。
 *
 * <p>本项目没有引入 arc，所以这里补最小实现；包路径按 {@code arc.util.io.*} →
 * {@code com.phoenix.game.util.io.*} 的映射规则落地。
 */
public class CounterInputStream extends InputStream{
    private final InputStream input;
    private long count;

    public CounterInputStream(InputStream input){
        this.input = input;
    }

    /** 把计数器归零（每读一个区块前调用）。 */
    public void resetCount(){
        count = 0;
    }

    /** @return 自上次 {@link #resetCount()} 以来读出的字节数。 */
    public long count(){
        return count;
    }

    @Override
    public int read() throws IOException{
        int i = input.read();
        if(i != -1) count++;
        return i;
    }

    @Override
    public int read(byte[] b) throws IOException{
        int i = input.read(b);
        if(i != -1) count += i;
        return i;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException{
        int i = input.read(b, off, len);
        if(i != -1) count += i;
        return i;
    }

    @Override
    public long skip(long n) throws IOException{
        long skipped = input.skip(n);
        count += skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException{
        return input.available();
    }

    @Override
    public void close() throws IOException{
        input.close();
    }

    @Override
    public synchronized void mark(int readlimit){
        input.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException{
        input.reset();
    }

    @Override
    public boolean markSupported(){
        return input.markSupported();
    }
}
