package com.phoenix.game.util.io;

import java.io.ByteArrayOutputStream;

/**
 * 可重复使用的字节输出流。参照 {@code arc.util.io.ReusableByteOutStream} 移植。
 *
 * <p>它唯一的增量就是 {@link #getBytes()}：直接暴露内部缓冲区（**不拷贝**），
 * 于是"写完立刻拿字节数组"这种高频操作不会每次都复制一遍。
 * {@code size()} / {@code reset()} 直接沿用 {@link ByteArrayOutputStream} 的语义
 * （{@code size()} 返回已写入字节数，{@code reset()} 只把写指针归零、缓冲区保留），
 * 与 arc 的实现一致，所以调用点（{@code SaveFileReader.writeChunk}）可以照抄原版写法。
 *
 * <p>本项目没有引入 arc，所以这里补最小实现；包路径按 {@code arc.util.io.*} →
 * {@code com.phoenix.game.util.io.*} 的映射规则落地。
 */
public class ReusableByteOutStream extends ByteArrayOutputStream{

    public ReusableByteOutStream(){
        super();
    }

    public ReusableByteOutStream(int size){
        super(size);
    }

    /** @return 内部缓冲区本身（**不拷贝**，只保证前 {@link #size()} 个字节有效）。 */
    public byte[] getBytes(){
        return buf;
    }
}
