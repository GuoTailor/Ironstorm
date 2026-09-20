package com.phoenix.game.io;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ArrayMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.content.Items;
import com.phoenix.game.content.UnitTypes;
import com.phoenix.game.core.World;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.game.Team;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.UnitType;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockFlag;
import com.phoenix.game.world.modules.ItemModule;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** 存档文件读写。参照 Mindustry mindustry.io.SaveIO 移植的最小版（不含元数据/缩略图）。 */
public class SaveIO{
    public static final int version = 1;

    // 物品 ID → 建筑方块 ID 映射，实际用 Blocks.all / Items.all 下标
    private static final Array<Item> itemIds = new Array<>();

    // ItemModule 是按 Items.all 下标索引的，这里直接用下标即可

    /** 写存档到文件。需要先调用 {@link #itemIndex()} 建立物品索引。 */
    public static void save(java.io.File file) throws IOException{
        try(DataOutputStream out = new DataOutputStream(new java.io.FileOutputStream(file))){
            write(out);
        }
    }

    /** 读存档（替换当前世界与状态）。 */
    public static void load(java.io.File file) throws IOException{
        try(DataInputStream in = new DataInputStream(new java.io.FileInputStream(file))){
            read(in);
        }
    }

    /** 把当前世界序列化到输出流（网络传输与存档共用格式）。 */
    public static void write(DataOutputStream out) throws IOException{
        World world = Vars.world;
        int width = world.width(), height = world.height();

        out.writeInt(version);
        out.writeInt(width);
        out.writeInt(height);

        // ---- tile data ----
        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){
                Tile tile = world.tile(x, y);
                byte fid = (byte)blockId(tile.floor());
                //用原始存储方块（blockRaw）而非 block()：block() 会把卫星瓦片解析成中心方块，
                //导致卫星写成 core、读取时重建出多余实体，造成字节错位
                byte bid = (byte)blockId(tile.blockRaw());
                byte oid = (byte)(tile.overlay() != null ? blockId(tile.overlay()) : 0);
                out.writeByte(bid);
                out.writeByte((byte)tile.getTeam().id);
                out.writeByte(tile.rotation());
                out.writeByte(fid);
                out.writeByte(oid);
                //实体建筑：血量 + 本地库存 + 电力状态
                if(tile.entity != null){
                    out.writeFloat(tile.entity.health());
                    writeItems(out, tile, tile.entity.items);
                    //电力状态（生产/需求每帧重算，只需存满足率）
                    out.writeBoolean(tile.entity.power != null);
                    if(tile.entity.power != null){
                        out.writeFloat(tile.entity.power.status);
                    }
                }
            }
        }
        //网格线后的空行（读时用于同步）
        out.writeInt(-1);

        // ---- wave / time ----
        out.writeInt(Vars.state.wave);
        out.writeFloat(Vars.state.wavetime);
        out.writeInt(Vars.state.enemies);

        // ---- team inventory ----
        int baseTeams = Team.base().length;
        Team[] teams = Team.base();
        out.writeInt(baseTeams);
        for(Team team : teams){
            if(team == null) continue;
            out.writeByte((byte)team.id);
            ItemModule inv = Vars.state.teams.items(team);
            for(Item item : Items.all){
                out.writeInt(inv.get(item));
            }
        }

        // ---- units ----
        out.writeInt(Units.units.size);
        for(BaseUnit u : Units.units){
            out.writeByte((byte)UnitTypes.all.indexOf(u.getType(), true));
            out.writeByte((byte)u.getTeam().id);
            out.writeFloat(u.x);
            out.writeFloat(u.y);
            out.writeFloat(u.health());
        }
    }

    private static void writeItems(DataOutputStream out, Tile tile, ItemModule items) throws IOException{
        if(items == null){
            out.writeBoolean(false);
            return;
        }
        out.writeBoolean(true);
        //核心库存与队伍共享模块别名，避免重复写
        boolean isCore = tile.block().flags.contains(BlockFlag.core);
        for(Item item : Items.all){
            if(isCore){
                //核心库存由队伍库存统一恢复，但仍写入占位值保持格式对齐
                out.writeInt(0);
            }else{
                out.writeInt(items.get(item));
            }
        }
    }

    /** 从输入流读入，替换当前世界与状态（网络传输与存档共用格式）。 */
    public static void read(DataInputStream in) throws IOException{
        int ver = in.readInt();
        if(ver != version){
            throw new IOException("Unsupported save version: " + ver);
        }
        int width = in.readInt(), height = in.readInt();

        World world = new World();
        world.width = width;
        world.height = height;
        world.tiles = new Tile[width * height];
        Vars.state.teams = new com.phoenix.game.game.Teams();
        //注意：不能在这里就赋值 Vars.world —— 瓦片数组此时还是空的，
        //若读档过程中（如另一线程的渲染帧/寻路）观察到 Vars.world，会见到空瓦片而 NPE。
        //等全部瓦片填充完、read() 结束时再统一替换。

        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){
                Tile tile = new Tile(x, y);

                byte bid = in.readByte();
                byte teamId = in.readByte();
                byte rot = in.readByte();
                byte fid = in.readByte();
                byte oid = in.readByte();

                // 先假定所有瓦片是 air（无实体），由 loadRestore 决定
                tile.loadRestore(blockOf(bid), rot, Team.get(teamId), floorOf(fid), overlayOf(oid));

                //实体建筑：恢复血量 + 本地库存 + 电力状态
                if(tile.entity != null){
                    tile.entity.health(in.readFloat());
                    readItems(in, tile);
                    if(in.readBoolean() && tile.entity.power != null){
                        tile.entity.power.status = in.readFloat();
                    }
                }

                world.tiles[y * width + x] = tile;
            }
        }
        //所有瓦片填充完成，此刻才把新世界挂到 Vars.world（避免读档中途被观察到空瓦片）
        Vars.world = world;
        //网格线结束标记同步
        int endmark = in.readInt();
        if(endmark != -1){
            throw new IOException("Corrupt save: tile section not terminated (endmark=" + endmark + ")");
        }

        // ---- wave ----
        Vars.state.wave = in.readInt();
        Vars.state.wavetime = in.readFloat();
        Vars.state.enemies = in.readInt();

        // ---- team inventory ----
        int baseTeams = in.readInt();
        for(int t = 0; t < baseTeams; t++){
            byte teamId = in.readByte();
            ItemModule inv = Vars.state.teams.items(Team.get(teamId));
            for(Item item : Items.all){
                inv.add(item, in.readInt());
            }
        }

        // ---- units ----
        try{
            int count = in.readInt();
            Units.units.clear();
            for(int i = 0; i < count; i++){
                int typeId = in.readByte() & 0xFF;
                byte teamId = in.readByte();
                float ux = in.readFloat(), uy = in.readFloat();
                float uh = in.readFloat();

                UnitType type = typeOf(typeId);
                if(type == null) continue;
                BaseUnit unit = type.create();
                unit.setTeam(Team.get(teamId));
                unit.set(ux, uy);
                unit.health(uh);
                Units.add(unit);
            }
        }catch(java.io.EOFException e){
            //非 fatal：单位段可能缺失（旧存档）
        }
    }

    private static void readItems(DataInputStream in, Tile tile) throws IOException{
        boolean has = in.readBoolean();
        if(!has) return;
        boolean isCore = tile.block().flags.contains(BlockFlag.core);
        ItemModule items = tile.entity.items;
        if(items == null) return;
        for(Item item : Items.all){
            int amount = in.readInt();
            if(!isCore && amount > 0) items.add(item, amount);
        }
    }

    // ---- ID 映射 ----
    private static int blockId(com.phoenix.game.world.Block block){
        int idx = Blocks.all.indexOf(block, true);
        return idx < 0 ? 0 : idx;
    }

    private static com.phoenix.game.world.Block blockOf(int id){
        if(id < 0 || id >= Blocks.all.size) return Blocks.air;
        return Blocks.all.get(id);
    }

    private static Floor floorOf(int id){
        com.phoenix.game.world.Block b = blockOf(id);
        return b instanceof Floor ? (Floor)b : Blocks.air;
    }

    private static Floor overlayOf(int id){
        com.phoenix.game.world.Block b = blockOf(id);
        if(b instanceof Floor && b == Blocks.spawn) return (Floor)b;
        if(b instanceof Floor && b != Blocks.air) return (Floor)b;
        return Blocks.air;
    }

    private static UnitType typeOf(int id){
        if(id < 0 || id >= UnitTypes.all.size) return null;
        return UnitTypes.all.get(id);
    }

}