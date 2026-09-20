package com.phoenix.game.world;

import com.phoenix.game.core.Time;
import com.phoenix.game.type.Item;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * 四方向物品缓冲：每个方向一条独立队列。参照 Mindustry mindustry.world.DirectionalItemBuffer 移植。
 * <p>Junction（交叉器）靠它实现"直行穿越"：物品从哪边进来就排进哪个方向的队列，到点后原样送出。
 * <p>方向索引与 {@link Tile#relativeTo} / {@link Tile#getNearby(int)} 一致（0=+y,1=+x,2=-y,3=-x）。
 */
public class DirectionalItemBuffer{
    /** 每个方向的队列（[方向][位置]，位置 0 为队首）。 */
    public final long[][] buffers;
    /** 每个方向的当前长度；索引 4 未使用（原版如此，保留同样的数组长度）。 */
    public final int[] indexes;
    private final float speed;

    public DirectionalItemBuffer(int capacity, float speed){
        this.buffers = new long[4][capacity];
        this.indexes = new int[5];
        this.speed = speed;
    }

    /** @return 该方向的队列是否还能再收一个。 */
    public boolean accepts(int buffer){
        return buffer >= 0 && buffer < 4 && indexes[buffer] < buffers[buffer].length;
    }

    public void accept(int buffer, Item item){
        if(!accepts(buffer)) return;
        buffers[buffer][indexes[buffer]++] = ItemBuffer.pack(item.id, (short)-1, Time.time);
    }

    /** @return 该方向队首物品（等待时间已满）；未满或空返回 null。 */
    public Item poll(int buffer){
        if(buffer < 0 || buffer >= 4) return null;

        if(indexes[buffer] > 0){
            long l = buffers[buffer][0];
            float time = ItemBuffer.time(l);

            if(Time.time >= time + speed || Time.time < time){
                return ItemBuffer.item(ItemBuffer.itemId(l));
            }
        }
        return null;
    }

    /** 弹出该方向队首（与 {@link #poll(int)} 成对使用）。 */
    public void remove(int buffer){
        if(buffer < 0 || buffer >= 4 || indexes[buffer] <= 0) return;
        System.arraycopy(buffers[buffer], 1, buffers[buffer], 0, indexes[buffer] - 1);
        indexes[buffer]--;
    }

    public void write(DataOutput stream) throws IOException{
        for(int i = 0; i < 4; i++){
            stream.writeByte(indexes[i]);
            stream.writeByte(buffers[i].length);
            for(long l : buffers[i]){
                stream.writeLong(l);
            }
        }
    }

    public void read(DataInput stream) throws IOException{
        for(int i = 0; i < 4; i++){
            indexes[i] = stream.readByte();
            byte length = stream.readByte();
            for(int j = 0; j < length; j++){
                long value = stream.readLong();
                if(j < buffers[i].length){
                    buffers[i][j] = value;
                }
            }
            indexes[i] = Math.min(indexes[i], buffers[i].length);
        }
    }
}
