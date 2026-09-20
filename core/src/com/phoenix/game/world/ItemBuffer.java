package com.phoenix.game.world;

import com.phoenix.game.content.Items;
import com.phoenix.game.core.Time;
import com.phoenix.game.type.Item;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * 单队列物品缓冲。参照 Mindustry mindustry.world.ItemBuffer 移植。
 * <p>每个元素用 long 打包「入队时间(float) + 物品 id(16 位) + 附加数据(16 位)」，
 * 出队必须等 {@code speed} 帧之后（模拟运输耗时）；时间回绕（Time.time 重置）时立即放行。
 * <p>原版靠 {@code @Struct} 注解处理器生成 BufferItem 结构体，本项目直接位运算打包。
 */
public class ItemBuffer{
    private final float speed;
    private final long[] buffer;
    private int index;

    public ItemBuffer(int capacity, float speed){
        this.buffer = new long[capacity];
        this.speed = speed;
    }

    /** @return 是否还能再塞一个。 */
    public boolean accepts(){
        return index < buffer.length;
    }

    public void accept(Item item, short data){
        if(index >= buffer.length) return;
        buffer[index++] = pack(item.id, data, Time.time);
    }

    public void accept(Item item){
        accept(item, (short)-1);
    }

    /** @return 队首物品（等待时间已满）；未满或空返回 null。 */
    public Item poll(){
        if(index > 0){
            long l = buffer[0];
            float time = time(l);

            if(Time.time >= time + speed || Time.time < time){
                return item(itemId(l));
            }
        }
        return null;
    }

    /** @return 队首物品的附加数据；无则 -1。 */
    public short pollData(){
        if(index > 0){
            long l = buffer[0];
            float time = time(l);

            if(Time.time >= time + speed || Time.time < time){
                return data(l);
            }
        }
        return -1;
    }

    /** 弹出队首（与 {@link #poll()} 成对使用）。 */
    public void remove(){
        if(index <= 0) return;
        System.arraycopy(buffer, 1, buffer, 0, index - 1);
        index--;
    }

    /** @return 当前排队数量。 */
    public int size(){
        return index;
    }

    public void write(DataOutput stream) throws IOException{
        stream.writeByte((byte)index);
        stream.writeByte((byte)buffer.length);
        for(long l : buffer){
            stream.writeLong(l);
        }
    }

    public void read(DataInput stream) throws IOException{
        index = stream.readByte();
        byte length = stream.readByte();
        for(int i = 0; i < length; i++){
            long l = stream.readLong();
            if(i < buffer.length){
                buffer[i] = l;
            }
        }
        index = Math.min(index, buffer.length);
    }

    // ---- long 打包：高 32 位 = 时间(float 位模式)，低 32 位 = 物品 id(低 16) | 数据(高 16) ----

    /** 打包一条缓冲记录。 */
    public static long pack(short itemId, short data, float time){
        long high = Float.floatToIntBits(time) & 0xFFFFFFFFL;
        long low = ((long)(itemId & 0xFFFF)) | (((long)(data & 0xFFFF)) << 16);
        return (high << 32) | low;
    }

    public static float time(long l){
        return Float.intBitsToFloat((int)(l >>> 32));
    }

    public static int itemId(long l){
        return (int)(l & 0xFFFF);
    }

    public static short data(long l){
        return (short)((l >>> 16) & 0xFFFF);
    }

    /** 按 id 取物品；越界返回 null（存档损坏时不至于崩）。 */
    public static Item item(int id){
        return id >= 0 && id < Items.all.size ? Items.all.get(id) : null;
    }
}
