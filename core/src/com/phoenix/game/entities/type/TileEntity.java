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
}
