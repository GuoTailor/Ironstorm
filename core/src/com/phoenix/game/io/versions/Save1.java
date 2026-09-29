package com.phoenix.game.io.versions;

import com.phoenix.game.entities.traits.SaveTrait;
import com.phoenix.game.func.Prov;

import java.io.DataInput;
import java.io.IOException;

/**
 * 存档格式 v1 的实体读取。参照 Mindustry {@code mindustry.io.versions.Save1} 移植。
 *
 * <p>v1 与 v2 的唯一区别就是**实体类型用旧下标表**：v1 写的是
 * {@link LegacyTypeTable} 里的字节下标（随 build 变化），v2 起改成 {@code TypeID} 的内容 id。
 * 其余全部继承 {@link Save2}（也就是 {@link com.phoenix.game.io.SaveVersion}）。
 *
 * <p><b>与原版的差异 + 刻意保留</b>
 * <ul>
 *   <li>刻意保留：原版对 {@code table[typeid]} 不做任何越界检查（表是编译期常量，正常存档不会越界）。
 *       这里加了 {@code typeid} 的范围守卫 —— 坏存档只会丢掉那一个实体，不会直接抛
 *       {@code ArrayIndexOutOfBoundsException} 让整个存档读不进来。</li>
 *   <li>刻意保留：本类**刻意不接线** —— 本项目 {@link com.phoenix.game.io.SaveIO} 是重写版
 *       （自有 PHNX 头 + 名字表 content 区），不读原版 MSAV（见 {@code SaveIO.readHeader}），
 *       所以本类只是"齐文件"的兼容层。</li>
 * </ul>
 */
public class Save1 extends Save2{

    public Save1(){
        version = 1;
    }

    @Override
    public void readEntities(DataInput stream) throws IOException{
        Prov[] table = LegacyTypeTable.getTable(lastReadBuild);

        byte groups = stream.readByte();

        for(int i = 0; i < groups; i++){
            int amount = stream.readInt();
            for(int j = 0; j < amount; j++){
                readChunk(stream, true, in -> {
                    byte typeid = in.readByte();
                    byte version = in.readByte();
                    //刻意保留：原版无守卫（坏存档会直接越界崩）；这里加范围检查，坏项只丢一个实体
                    if(typeid < 0 || typeid >= table.length) return;
                    SaveTrait trait = (SaveTrait)table[typeid].get();
                    trait.readSave(in, version);
                });
            }
        }
    }
}
