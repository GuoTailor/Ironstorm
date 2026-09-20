package com.phoenix.game.game;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.world.Block;

/**
 * 蓝图（存档结构）。参照 Mindustry mindustry.game.Schematic 移植。
 * <p>只存"相对坐标 + 方块 + 配置 + 朝向"，不存地板/血量/库存，所以蓝图可以贴到任意位置。
 * <p>{@link #tags} 是键值元数据（名称、描述等），会随 .msch 一起读写。
 * <p>未移植：预览图生成、创意工坊发布相关字段。
 */
public class Schematic{
    /** 蓝图内的方块（坐标为蓝图局部坐标，原点在左上角）。 */
    public final Array<Stile> tiles;
    /** 元数据（name / description …）。 */
    public ObjectMap<String, String> tags;
    /** 蓝图尺寸（格）。 */
    public int width, height;
    /** 来源文件；由 {@link Schematics#read} 填充，新建的蓝图为 null。 */
    public java.io.File file;

    public Schematic(Array<Stile> tiles, ObjectMap<String, String> tags, int width, int height){
        this.tiles = tiles;
        this.tags = tags;
        this.width = width;
        this.height = height;
    }

    /** @return 蓝图名（取自 tags，缺失时为 "unknown"）。 */
    public String name(){
        return tags.get("name", "unknown");
    }

    /** @return 建整张蓝图需要的材料总量（各材料累加后按 id 排序）。 */
    public Array<ItemStack> requirements(){
        int[] amounts = new int[com.phoenix.game.content.Items.all.size];

        for(int i = 0; i < tiles.size; i++){
            for(ItemStack stack : tiles.get(i).block.requirements){
                if(stack.item != null) amounts[stack.item.id] += stack.amount;
            }
        }

        Array<ItemStack> stacks = new Array<>();
        for(int id = 0; id < amounts.length; id++){
            if(amounts[id] > 0){
                stacks.add(new ItemStack(com.phoenix.game.content.Items.all.get(id), amounts[id]));
            }
        }
        return stacks;
    }

    /** @return 蓝图里是否含核心（原版用于"带核心的蓝图"特殊处理）。 */
    public boolean hasCore(){
        for(int i = 0; i < tiles.size; i++){
            if(tiles.get(i).block instanceof com.phoenix.game.world.blocks.storage.CoreBlock) return true;
        }
        return false;
    }

    /** 蓝图里的一格方块。参照 Mindustry {@code Schematic.Stile}。 */
    public static class Stile{
        public final Block block;
        public final short x, y;
        /** 配置值（如电力节点的连接目标、物品桥的朝向目标）；-1 表示无。 */
        public final int config;
        public final byte rotation;

        public Stile(Block block, int x, int y, int config, byte rotation){
            this.block = block;
            this.x = (short)x;
            this.y = (short)y;
            this.config = config;
            this.rotation = rotation;
        }
    }
}
