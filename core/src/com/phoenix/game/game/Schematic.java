package com.phoenix.game.game;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.phoenix.game.Vars;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.type.Publishable;
import com.phoenix.game.util.Fi;
import com.phoenix.game.world.Block;

/**
 * 蓝图（存档结构）。参照 Mindustry mindustry.game.Schematic 移植。
 * <p>只存"相对坐标 + 方块 + 配置 + 朝向"，不存地板/血量/库存，所以蓝图可以贴到任意位置。
 * <p>{@link #tags} 是键值元数据（名称、描述等），会随 .msch 一起读写。
 * <p>{@link Publishable} 的创意工坊方法已按原版补齐（steamID 存进 {@code tags}），
 * 但本项目无 Steam 发布路径 → 这些方法属"保持 API 完整"，实际不可达（与 {@code maps/Map} 同样处理）。
 */
public class Schematic implements Publishable{
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

    /** 把蓝图写回它自己的文件（对应原版 {@code Schematic.save}）。{@code Vars.schematics} 未就绪时是 no-op。 */
    public void save(){
        if(Vars.schematics != null) Vars.schematics.saveChanges(this);
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

    /**
     * @return 蓝图里的核心方块（取第一个）。
     * <p>对应原版 {@code Schematic.findCore()}（原版用 arc 的 {@code tiles.find(...)}，
     * 本项目 {@code tiles} 是 libgdx 的 {@link Array} → 手写成下标循环，风格同 {@link #hasCore()}）。
     * @throws IllegalArgumentException 蓝图里没有核心时（与原版一致）
     */
    public com.phoenix.game.world.blocks.storage.CoreBlock findCore(){
        com.phoenix.game.world.blocks.storage.CoreBlock block = null;
        for(int i = 0; i < tiles.size; i++){
            if(tiles.get(i).block instanceof com.phoenix.game.world.blocks.storage.CoreBlock){
                block = (com.phoenix.game.world.blocks.storage.CoreBlock)tiles.get(i).block;
                break;
            }
        }
        if(block == null) throw new IllegalArgumentException("Schematic is missing a core!");
        return block;
    }

    // ---- Publishable（Steam 创意工坊；本项目未接线，仅保持 API 完整）----
    // 照 maps/Map 的同接口实现：steamID 存进 tags。与原版的差异：原版写 tmpDirectory，本项目没有该全局目录。

    @Override
    public String getSteamID(){
        return tags.get("steamid");
    }

    @Override
    public void addSteamID(String id){
        tags.put("steamid", id);
        save();
    }

    @Override
    public void removeSteamID(){
        tags.remove("steamid");
        save();
    }

    @Override
    public String steamTitle(){
        return name();
    }

    @Override
    public String steamDescription(){
        return null;
    }

    @Override
    public String steamTag(){
        return "schematic";
    }

    /** 刻意降级：原版 = {@code tmpDirectory.child("schematic_" + id).child("schematic.msch")}；本项目没有 tmpDirectory → 系统临时目录。 */
    @Override
    public Fi createSteamFolder(String id){
        Fi dir = Fi.tempDirectory("schematic_" + id).child("schematic." + Schematics.schematicExtension);
        if(file != null){
            new Fi(file).copyTo(dir);
        }
        return dir;
    }

    /** 刻意降级：原版写到 {@code tmpDirectory}；本项目用系统临时目录 + {@code Schematics.savePreview}。 */
    @Override
    public Fi createSteamPreview(String id){
        Fi preview = new Fi(new java.io.File(System.getProperty("java.io.tmpdir"), "schematic_preview_" + id + ".png"));
        if(Vars.schematics != null){
            Vars.schematics.savePreview(this, preview.file());
        }
        return preview;
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
