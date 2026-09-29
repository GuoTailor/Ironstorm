package com.phoenix.game.entities.type.base;

import com.phoenix.game.Vars;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.entities.units.UnitState;
import com.phoenix.game.game.Team;
import com.phoenix.game.world.Pos;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.BuildBlock;

import java.io.*;

import static com.phoenix.game.Vars.world;

/**
 * 维修无人机。参照 Mindustry mindustry.entities.type.base.RepairDrone 移植。
 * <p>默认状态是 {@code repair}：在射程内找**受损**的友方建筑，飞过去用武器"修"它；
 * 没有受损建筑时绕着出生点转圈。
 * <p>受损建筑从 {@code Vars.indexer} 的索引里取（对应原版 {@code Units.findDamagedTile}）；
 * {@code indexer} 不可用时（headless 未接线等）退回就地全瓦片扫描。
 */
public class RepairDrone extends BaseDrone{
    public final UnitState repair = new UnitState(){

        public void entered(){
            target = null;
        }

        public void update(){

            if(retarget()){
                //原版：Units.findDamagedTile(team, x, y)（内部走 indexer.getDamaged(team)）
                target = findDamagedTile(team, x, y);
            }

            if(target instanceof TileEntity && ((TileEntity)target).block instanceof BuildBlock){
                target = null;
            }

            if(target != null){
                if(target.dst(RepairDrone.this) > type.range){
                    circle(type.range * 0.9f);
                }else{
                    getWeapon().update(RepairDrone.this, target.getX(), target.getY());
                }
            }else{
                //circle spawner if there's nothing to repair
                //原版用 getSpawner()（返回自己的出生点实体）；本项目 BaseUnit.getSpawner() 返回的是 int 坐标，
                //所以改用 getClosestSpawner()（最近出生点瓦片），行为等价（刻意替代）
                Tile spawner = getClosestSpawner();
                if(spawner != null){
                    target = spawner;
                    circle(type.range * 1.5f, type.speed/2f);
                    target = null;
                }
            }
        }
    };

    @Override
    public boolean shouldRotate(){
        return target != null;
    }

    @Override
    public UnitState getStartState(){
        return repair;
    }

    /**
     * 找最近的受损友方建筑（对应原版 {@code Units.findDamagedTile(Team, float, float)}）。
     * <p>原版实现是 {@code Geometry.findClosest(x, y, indexer.getDamaged(team))}；本项目
     * {@code Geometry} 没有 {@code findClosest} → 就地线性扫最近（与 {@code BlockIndexer.findClosestTile} 同法）。
     * {@code getDamaged} 会顺手把"已修复/已毁/施工中"的条目从索引里剔除，所以拿到的是活的受损集合。
     * <p>{@code Vars.indexer} 为 null 时（headless 未接线 / 编辑器等）退回全瓦片扫描。
     */
    private static TileEntity findDamagedTile(Team team, float x, float y){
        if(Vars.indexer != null){
            TileEntity best = null;
            float bestDst = Float.POSITIVE_INFINITY;

            for(Tile tile : Vars.indexer.getDamaged(team)){
                TileEntity entity = tile.entity;
                if(entity == null || entity.getTeam() != team || entity.healthf() >= 1f) continue;

                float dst = entity.dst(x, y);
                if(dst < bestDst){
                    bestDst = dst;
                    best = entity;
                }
            }

            return best;
        }

        if(world == null) return null;

        TileEntity best = null;
        float bestDst = Float.POSITIVE_INFINITY;

        for(int i = 0; i < world.tiles.length; i++){
            Tile tile = world.tiles[i];
            if(tile == null || tile.entity == null || tile.getTeam() != team) continue;

            TileEntity entity = tile.entity;
            if(entity.healthf() >= 1f) continue;

            float dst = entity.dst(x, y);
            if(dst < bestDst){
                bestDst = dst;
                best = entity;
            }
        }

        return best;
    }

    @Override
    public void write(DataOutput data) throws IOException{
        super.write(data);
        data.writeInt(state.is(repair) && target instanceof TileEntity ? ((TileEntity)target).tile.pos() : Pos.invalid);
    }

    @Override
    public void read(DataInput data) throws IOException{
        super.read(data);
        Tile repairing = world.tile(data.readInt());

        if(repairing != null){
            target = repairing.entity;
        }
    }
}
