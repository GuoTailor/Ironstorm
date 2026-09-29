package com.phoenix.game.io.versions;

import com.phoenix.game.ctype.ContentType;
import com.phoenix.game.entities.traits.SaveTrait;
import com.phoenix.game.io.SaveVersion;
import com.phoenix.game.type.TypeID;

import java.io.DataInput;
import java.io.IOException;

import static com.phoenix.game.Vars.content;

/**
 * 存档格式 v2 的实体读取。参照 Mindustry {@code mindustry.io.versions.Save2} 移植。
 *
 * <p>v2 起实体类型不再写"旧下标"，改成写 {@code TypeID} 的内容 id（{@code ContentType.typeid} 那一条），
 * 于是不用再维护 {@link LegacyTypeTable} 那套随 build 变化的表。
 * 本类只覆写 {@code readEntities}（v2 的 entities 区没有队伍数据，直接就是实体分组）。
 *
 * <p><b>与原版的差异 + 刻意保留</b>
 * <ul>
 *   <li>原版 {@code content.<TypeID>getByID(ContentType.typeid, typeid).constructor.get()} 不做 null 检查；
 *       本项目 {@code ContentLoader.getByID} 在"临时映射表缺项"时会返回 null（原版同样会返回 null，
 *       但紧接着就会 NPE 在 {@code .constructor} 上）→ 这里加了 null 守卫，坏项只丢一个实体。</li>
 *   <li>刻意保留：本类**刻意不接线** —— 本项目 {@link com.phoenix.game.io.SaveIO} 是重写版，不读原版 MSAV
 *       （见 {@code SaveIO.readHeader}；原版 {@code SaveIO} 会用 {@code versionArray} 按版本号分派到
 *       {@code Save1/2/3}）。等要读原版存档时，给 {@code SaveIO} 加一条按魔数分派的兼容分支即可把这三个类接上。</li>
 * </ul>
 */
public class Save2 extends SaveVersion{

    public Save2(){
        super(2);
    }

    @Override
    public void readEntities(DataInput stream) throws IOException{
        byte groups = stream.readByte();

        for(int i = 0; i < groups; i++){
            int amount = stream.readInt();
            for(int j = 0; j < amount; j++){
                try{
                    readChunk(stream, true, in -> {
                        byte typeid = in.readByte();
                        byte version = in.readByte();
                        //刻意保留：原版无 null 守卫；本项目 getByID 可能返回 null，坏项只丢一个实体
                        TypeID type = content.getByID(ContentType.typeid, typeid);
                        if(type == null) return;
                        SaveTrait trait = (SaveTrait)type.constructor.get();
                        trait.readSave(in, version);
                    });
                }catch(Exception e){
                    //对应原版 "throw exception on read fail"：坏实体带上序号抛出，便于定位
                    throw new IOException("Failed to read entity " + j + "/" + amount + ".", e);
                }
            }
        }
    }
}
