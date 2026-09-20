package com.phoenix.game.ai;

import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.game.EventType;
import com.phoenix.game.game.SpawnGroup;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;

/**
 * 最小实现：波次生成器。参照 Mindustry mindustry.ai.WaveSpawner 移植。
 * <p>出生点来自地图上的 spawn overlay（World.createMap 在四角生成），与原版一致；
 * 没有出生点则不会生成敌人。飞行单位按「地图中心 → 出生点」的方向在地图外出生。
 */
public class WaveSpawner{
    /** 飞行单位在地图外的余量 */
    private static final float margin = 40f;

    private final Array<FlyerSpawn> flySpawns = new Array<>();
    private final Array<Tile> groundSpawns = new Array<>();
    private boolean spawning;

    public WaveSpawner(){
        Events.on(EventType.WorldLoadEvent.class, e -> reset());
    }

    /** 重新扫描地图出生点（对应原版 reset）。 */
    public void reset(){
        flySpawns.clear();
        groundSpawns.clear();

        if(Vars.world == null) return;

        for(Tile tile : Vars.world.tiles){
            if(tile != null && tile.overlay() == Blocks.spawn){
                addSpawns(tile.x, tile.y);
            }
        }
    }

    private void addSpawns(int x, int y){
        Tile tile = Vars.world.tile(x, y);
        if(tile == null) return;

        groundSpawns.add(tile);

        FlyerSpawn flyer = new FlyerSpawn();
        flyer.angle = Angles.angle(Vars.world.width() / 2f, Vars.world.height() / 2f, x, y);
        flySpawns.add(flyer);
    }

    /** 生成一波敌人（对应原版 spawnEnemies）。 */
    public void spawnEnemies(){
        if(Vars.world == null || groundSpawns.size == 0) return;

        spawning = true;

        for(SpawnGroup group : Vars.state.rules.spawns){
            int spawned = group.getUnitsSpawned(Vars.state.wave - 1);
            if(spawned <= 0) continue;

            if(group.type.flying){
                float spread = margin / 1.5f;
                float trns = (Vars.world.width() + Vars.world.height()) * Vars.tilesize;

                for(FlyerSpawn spawn : flySpawns){
                    //飞行单位从地图外沿射线方向进入
                    float spawnX = Mathf.clamp(Vars.world.unitWidth() / 2f + Angles.trnsx(spawn.angle, trns), -margin, Vars.world.unitWidth() + margin);
                    float spawnY = Mathf.clamp(Vars.world.unitHeight() / 2f + Angles.trnsy(spawn.angle, trns), -margin, Vars.world.unitHeight() + margin);

                    for(int i = 0; i < spawned; i++){
                        BaseUnit unit = group.createUnit(Vars.state.rules.waveTeam);
                        unit.set(spawnX + Mathf.range(spread), spawnY + Mathf.range(spread));
                    }
                }
            }else{
                float spread = Vars.tilesize * 2f;

                for(Tile spawn : groundSpawns){
                    for(int i = 0; i < spawned; i++){
                        BaseUnit unit = group.createUnit(Vars.state.rules.waveTeam);
                        unit.set(spawn.getX() + Mathf.range(spread), spawn.getY() + Mathf.range(spread));
                    }
                }
            }
        }

        //约 2 秒后视为生成结束（对应原版 Time.runTask(121f, ...)）
        Time.run(121f, () -> spawning = false);
    }

    /** @return 是否正在生成敌人 */
    public boolean isSpawning(){
        return spawning;
    }

    /** @return 地面出生点数量 */
    public int countSpawns(){
        return groundSpawns.size;
    }

    public Array<Tile> getGroundSpawns(){
        return groundSpawns;
    }

    /** @return 离指定位置最近的出生点（对应原版 BaseUnit.getClosestSpawner）。 */
    public Tile getClosestSpawner(float x, float y){
        Tile best = null;
        float bestDst = Float.MAX_VALUE;

        for(Tile tile : groundSpawns){
            float dst = Mathf.dst2(x, y, tile.getX(), tile.getY());
            if(dst < bestDst){
                bestDst = dst;
                best = tile;
            }
        }

        return best;
    }

    /** 飞行单位出生方向（只存角度，与原版一致）。 */
    private static class FlyerSpawn{
        float angle;
    }
}
