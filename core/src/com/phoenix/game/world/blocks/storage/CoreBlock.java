package com.phoenix.game.world.blocks.storage;

import com.phoenix.game.Vars;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.meta.BlockFlag;

import java.util.EnumSet;

/**
 * 核心建筑。参照 Mindustry mindustry.world.blocks.storage.CoreBlock 最小移植。
 * <p>作为双方可被摧毁的寻路目标，并接收物品：核心库存即本队共享库存（原版语义）。
 * 原版的单位重生/近邻容量/物品拆分等依赖 SpawnerTrait 与 proximity 系统，尚未移植。
 */
public class CoreBlock extends Block{
    /** 核心物品容量上限。 */
    public int coreItemCapacity = 4000;

    public CoreBlock(String name){
        super(name);

        solid = true;
        update = true;
        destructible = true;
        health = 2000;
        size = 3;
        flags = EnumSet.of(BlockFlag.core);
        hasItems = true;
        itemCapacity = coreItemCapacity;
        entityType = CoreEntity::new;
    }

    /** 核心实体：库存指向本队共享模块，使全队核心共用同一份物品。 */
    public class CoreEntity extends TileEntity{
        @Override
        public TileEntity init(com.phoenix.game.world.Tile tile, boolean update){
            super.init(tile, update);
            //核心库存 = 本队共享库存
            if(Vars.state != null && Vars.state.teams != null){
                this.items = Vars.state.teams.items(tile.getTeam());
            }
            return this;
        }
    }

    /**
     * 核心邻接表刷新时，把紧邻的仓库链接到自己（对应原版 {@code CoreBlock.onProximityUpdate}）。
     * <p>链接后仓库收到的物品直接进核心的共享库存（容量叠加），这是原版"容器贴着核心扩容"的行为。
     */
    @Override
    public void onProximityUpdate(com.phoenix.game.world.Tile tile){
        if(!(tile.entity instanceof CoreEntity)) return;
        CoreEntity e = (CoreEntity)tile.entity;

        for(int i = 0; i < e.proximity.size; i++){
            com.phoenix.game.world.Tile other = e.proximity.get(i);
            if(other.block() instanceof StorageBlock && other.entity instanceof StorageBlock.StorageEntity){
                //关键：容器的 items **直接指向**核心的共享库存（引用共享，不是拷贝），
                //这样物品"存进容器"就等于存进核心，容量也随核心一起算（对应原版 CoreBlock.onProximityUpdate）
                other.entity.items = e.items;
                ((StorageBlock.StorageEntity)other.entity).linkedCore = tile;
            }
        }
    }

    /** 核心被拆/摧毁时解除仓库链接，否则仓库会一直往一个不存在的核心塞物品。 */
    @Override
    public void onProximityRemoved(com.phoenix.game.world.Tile tile){
        if(Vars.world == null) return;

        for(com.badlogic.gdx.math.GridPoint2 p : com.phoenix.game.world.Edges.getEdges(size)){
            com.phoenix.game.world.Tile other = Vars.world.ltile(tile.x + p.x, tile.y + p.y);
            if(other != null && other.entity instanceof StorageBlock.StorageEntity){
                StorageBlock.StorageEntity se = (StorageBlock.StorageEntity)other.entity;
                if(se.linkedCore == tile) se.linkedCore = null;
            }
        }
    }
}
