package com.phoenix.game.entities.type;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.traits.SolidTrait;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Edges;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.modules.ConsumeModule;
import com.phoenix.game.world.modules.ItemModule;
import com.phoenix.game.world.modules.LiquidModule;
import com.phoenix.game.world.modules.PowerModule;
import com.phoenix.game.world.blocks.power.PowerGraph;

import static com.phoenix.game.Vars.tilesize;

/**
 * 最小实现：瓦片实体（建筑）。参照 Mindustry mindustry.entities.type.TileEntity 移植。
 * 目前只保留血量与碰撞接口，建筑逻辑待后续移植。
 */
public abstract class TileEntity extends SolidEntity{
    public Tile tile;
    public Block block;
    protected float health = 1f;
    public byte rotation;
    /** 本地物品库存；仅在 block.hasItems 时分配。 */
    public ItemModule items;
    /** 本地液体储量；仅在 block.hasLiquids 时分配。 */
    public LiquidModule liquids;
    /** 本地电力模块；仅在 block.hasPower 时分配。 */
    public PowerModule power;
    /** 所属电网（由 PowerGraph 分配）。 */
    public PowerGraph graph;
    /** 消耗状态模块（对应原版 {@code TileEntity.cons}）：每帧刷新，方块逻辑判 {@code cons.valid()}。 */
    public ConsumeModule cons;

    /**
     * 邻接表：外围一圈"有实体且可互通（同队）"的瓦片，物流/电力都靠它找目标
     * （对应原版 {@code TileEntity.proximity}）。多格建筑只记录外围一圈，不含内部卫星格。
     */
    public final Array<Tile> proximity = new Array<>(8);

    /** 超频倍率（OverclockProjector 设置），影响 {@link #delta()}。 */
    public float timeScale = 1f;
    /** 超频剩余时间；归零则 {@link #timeScale} 复位为 1。 */
    public float timeScaleDuration;

    public TileEntity init(Tile tile, boolean update){
        this.tile = tile;
        this.block = tile.block();
        this.health = block.health;
        //队伍跟随瓦片：建筑发射的子弹（炮塔）与友方判定都依赖它
        this.team = tile.getTeam();
        if(block.hasItems){
            this.items = new ItemModule();
        }
        if(block.hasLiquids){
            this.liquids = new LiquidModule();
            this.liquids.setCapacity(block.liquidCapacity);
        }
        if(block.hasPower){
            this.power = new PowerModule();
        }
        //消耗状态模块：所有实体都有（没有消耗器的方块它的 valid 恒为 true）
        this.cons = new ConsumeModule(this);
        set(tile.worldx(), tile.worldy());
        return this;
    }

    /** @return 受超频影响的时间步长（对应原版 {@code TileEntity.delta()}）。 */
    public float delta(){
        return Time.delta() * timeScale;
    }

    /** 每帧衰减超频计时（由 World.updateTiles 统一调用，对应原版 TileEntity.update 开头）。 */
    public void updateTimeScale(){
        timeScaleDuration -= Time.delta();
        if(timeScaleDuration <= 0f || !block.canOverdrive){
            timeScale = 1f;
        }
    }

    /**
     * 重建邻接表（对应原版 {@code TileEntity.updateProximity}）：
     * 取外围一圈格，筛掉无实体/非友方的，双向登记，并触发方块的邻接钩子。
     */
    public void updateProximity(){
        proximity.clear();
        if(Vars.world == null || tile == null) return;

        for(GridPoint2 point : Edges.getEdges(block.size)){
            Tile other = Vars.world.ltile(tile.x + point.x, tile.y + point.y);
            if(other == null || other == tile) continue;
            if(other.entity == null || !other.interactable(tile.getTeam())) continue;

            //双向登记：邻居的邻接表里也要有自己（否则它收不到本建筑的变化通知）
            if(!other.entity.proximity.contains(tile, true)){
                other.entity.proximity.add(tile);
            }
            if(!proximity.contains(other, true)){
                proximity.add(other);
            }
        }

        block.onProximityAdded(tile);
        block.onProximityUpdate(tile);

        for(int i = 0; i < proximity.size; i++){
            Tile other = proximity.get(i);
            other.block().onProximityUpdate(other);
        }
    }

    /**
     * 从邻居的邻接表里摘掉自己（对应原版 {@code TileEntity.removeFromProximity}）。
     * <p>必须在实体被移除**之前**调用，否则邻居会一直记着一个已销毁的瓦片。
     */
    public void removeFromProximity(){
        if(Vars.world == null || tile == null) return;

        block.onProximityRemoved(tile);

        for(GridPoint2 point : Edges.getEdges(block.size)){
            Tile other = Vars.world.ltile(tile.x + point.x, tile.y + point.y);
            if(other != null){
                other.block().onProximityUpdate(other);
                if(other.entity != null){
                    other.entity.proximity.removeValue(tile, true);
                }
            }
        }
    }

    /**
     * 本建筑的配置值（对应原版 {@code TileEntity.config}）：-1 表示没有配置。
     * <p>本工程把配置存在各方块实体自己的字段里（如 {@code ItemBridgeEntity.link}），
     * 这个方法只用于把配置**导出**给蓝图；可配置方块需要覆写它。
     */
    public int config(){
        return -1;
    }

    public Block block(){
        return block;
    }

    public Tile tile(){
        return tile;
    }

    public float health(){
        return health;
    }

    /** 设置建筑当前血量，用于读档/同步。 */
    public void health(float value){
        this.health = Math.max(0f, Math.min(value, maxHealth()));
    }

    public float maxHealth(){
        return block.health;
    }

    /** 治疗建筑（对应原版 TileEntity.healBy）；不会超过最大血量。 */
    public void healBy(float amount){
        if(amount <= 0f) return;
        health = Math.min(health + amount, maxHealth());
    }

    public float healthf(){
        return health / maxHealth();
    }

    /**
     * 建筑伤害入口。对应原版 {@code TileEntity.damage()}：
     * <p>**客机上不本地扣血** —— 原版这里调 {@code Call.onTileDamage}，而它在客机是 no-op
     * （{@code called = Loc.server}），所以建筑血量在客户端完全由服务端广播驱动。
     * 服务端扣血后广播 {@link com.phoenix.game.net.Packets.TileDamage} 让客机覆盖本地值；
     * 血量归零则广播 {@link com.phoenix.game.net.Packets.BlockRemove} 再移除
     * （对应原版 {@code Call.onTileDestroyed}）。
     */
    public void handleDamage(float amount){
        if(com.phoenix.game.Vars.isClient()) return;

        health = Math.max(0f, health - amount);

        if(health <= 0f){
            //打爆：先广播移除，再本地销毁。客机不做本地销毁，只认这个包
            if(com.phoenix.game.Vars.isServer() && tile != null){
                com.phoenix.game.Vars.netServer.broadcastBlockRemove(tile);
            }
            kill();
            return;
        }

        if(com.phoenix.game.Vars.isServer() && tile != null){
            com.phoenix.game.net.Packets.TileDamage td = new com.phoenix.game.net.Packets.TileDamage();
            td.x = tile.x;
            td.y = tile.y;
            td.health = health;
            com.phoenix.game.Vars.netServer.net.send(td, com.phoenix.game.net.Net.SendMode.tcp);
        }
    }

    public void kill(){
        if(tile != null){
            tile.remove();
        }
        setDead(true);
    }

    /** @return 是否与子弹碰撞 */
    public boolean collide(SolidTrait other){
        return true;
    }

    public void collision(SolidTrait other){
    }

    public void update(){
    }

    public void onDeath(){
    }

    @Override
    public void hitbox(Rectangle rect){
        //实体坐标是锚点瓦片的左下角，方块的几何中心要加上 offset 与半格
        rect.setSize(block.size * tilesize).setCenter(x + block.offset() + tilesize / 2f, y + block.offset() + tilesize / 2f);
    }

    @Override
    public void hitboxTile(Rectangle rect){
        rect.setSize(block.size * tilesize).setCenter(x, y);
    }

    @Override
    public float mass(){
        return 10f;
    }

    // ==================== 存档序列化 ====================

    /**
     * 实体数据版本（对应原版 {@code TileEntity.version()}）：实体字段结构变化时递增，
     * 读档时传给 {@link #read(DataInputStream, byte)} 用于兼容旧数据。
     */
    public byte revision(){
        return 1;
    }

    /**
     * 序列化本实体状态（对应原版 {@code TileEntity.write}）。
     * <p>默认写：血量、本地物品库存、电力储量与满足率。
     * 子类覆盖时**必须先调用 {@code super.write(out)}**，再写自己的字段。
     * <p>不写每帧可重算的派生量（proximity / cons / graph / produced / needed）。
     */
    public void write(java.io.DataOutputStream out) throws java.io.IOException{
        out.writeFloat(health);

        if(items == null){
            out.writeBoolean(false);
        }else{
            out.writeBoolean(true);
            for(int i = 0; i < com.phoenix.game.content.Items.all.size; i++){
                out.writeInt(items.get(com.phoenix.game.content.Items.all.get(i)));
            }
        }

        if(power == null){
            out.writeBoolean(false);
        }else{
            out.writeBoolean(true);
            //储量必须存（电池累积，不是每帧重算）；满足率只在没有电网重算时才用得上
            out.writeFloat(power.stored);
            out.writeFloat(power.status);
        }
    }

    /**
     * 反序列化本实体状态（对应原版 {@code TileEntity.read}）。
     * <p>读之前实体已由 {@code tile.setBlock} 按方块类型创建并 {@code init} 过。
     * 子类覆盖时**必须先调用 {@code super.read(in, revision)}**。
     */
    public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
        health = in.readFloat();

        //即使本实体没有 items 模块也必须把字节消费掉，否则后续字段全部错位
        if(in.readBoolean()){
            for(int i = 0; i < com.phoenix.game.content.Items.all.size; i++){
                int amount = in.readInt();
                if(items != null) items.set(com.phoenix.game.content.Items.all.get(i), amount);
            }
        }

        if(in.readBoolean()){
            float stored = in.readFloat();
            float status = in.readFloat();
            if(power != null){
                power.stored = stored;
                power.status = status;
            }
        }
    }

    /**
     * 读档完成后的回调：此时**整张地图已经建好、邻接表也重建完毕**，
     * 可以解析那些跨越瓦片的引用（如 Router 的"上一件物品来自哪"）。
     * <p>需要它的实体覆写即可；默认什么都不做。
     */
    public void afterRead(){
    }

    /** 写一个 float 数组（长度前缀）。null 记为长度 -1。 */
    protected static void writeFloats(java.io.DataOutputStream out, float[] array) throws java.io.IOException{
        if(array == null){
            out.writeShort(-1);
            return;
        }
        out.writeShort(array.length);
        for(float value : array){
            out.writeFloat(value);
        }
    }

    /** 读一个 float 数组（长度前缀）。 */
    protected static float[] readFloats(java.io.DataInputStream in) throws java.io.IOException{
        int length = in.readShort();
        if(length < 0) return null;
        float[] array = new float[length];
        for(int i = 0; i < length; i++){
            array[i] = in.readFloat();
        }
        return array;
    }
}
