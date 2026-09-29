package com.phoenix.game.ai;

import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Time;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.entities.Damage;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.game.EventType;
import com.phoenix.game.game.SpawnGroup;
import com.phoenix.game.func.Floatc2;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;

/**
 * 波次生成器。参照 Mindustry mindustry.ai.WaveSpawner 移植。
 * <p>出生点来自地图上的 spawn overlay（WorldLoadEvent → reset 扫描）；没有出生点时
 * 飞行/地面单位不会从 overlay 刷出，但 attack 模式仍会从敌方核心附近增援。
 * <p>开波三段（均照原版）：
 * <ol>
 *   <li>地面单位延迟分批进场（{@code Time.run(min(i*5, 120))}，先 unitSpawn 特效再 30 帧后入组）；
 *       飞行单位立即出生；</li>
 *   <li>每个地面出生点在开波 20 帧后播放 {@link Fx#spawnShockwave}、40 帧后
 *       {@link Damage#damage(Team, float, float, float, float, boolean)} 清空投区
 *       （{@code 99999999f} 摧毁范围内敌方单位与建筑，防止玩家堵门建塔）；</li>
 *   <li>{@code rules.attackMode} 时从敌方核心附近取额外出生点（地面点带朝玩家核心的限长偏移，
 *       不清场；飞行点直接在核心上空）。</li>
 * </ol>
 */
public class WaveSpawner{
    /** 飞行单位在地图外的余量 / 核心增援点离敌方核心的最短距离（原版 coreMargin = tilesize * 3） */
    private static final float margin = 40f;
    private static final float coreMargin = Vars.tilesize * 3;

    private final Array<FlyerSpawn> flySpawns = new Array<>();
    private final Array<Tile> groundSpawns = new Array<>();
    private boolean spawning;

    /** 地面出生点消费者（原版私有接口 SpawnConsumer：x, y, 是否播放清场冲击波）。 */
    private interface SpawnConsumer{
        void accept(float x, float y, boolean shockwave);
    }

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

    /** 生成一波敌人（对应原版 spawnEnemies，WaveSpawner.java:45-86）。 */
    public void spawnEnemies(){
        if(Vars.world == null) return;

        spawning = true;

        for(SpawnGroup group : Vars.state.rules.spawns){
            int spawned = group.getUnitsSpawned(Vars.state.wave - 1);
            if(spawned <= 0) continue;

            if(group.type.flying){
                float spread = margin / 1.5f;

                //飞行单位立即出生（原版 create → set → add，无延迟）
                eachFlyerSpawn((spawnX, spawnY) -> {
                    for(int i = 0; i < spawned; i++){
                        BaseUnit unit = group.createUnit(Vars.state.rules.waveTeam);
                        unit.set(spawnX + Mathf.range(spread), spawnY + Mathf.range(spread));
                    }
                });
            }else{
                float spread = Vars.tilesize * 2f;

                //地面单位延迟分批进场（原版 WaveSpawner.java:66-73）
                eachGroundSpawn((spawnX, spawnY, doShockwave) -> {
                    for(int i = 0; i < spawned; i++){
                        //等价 arc 的 Tmp.v1.rnd(spread)：随机方向、固定长度的散布
                        Tmp.v1.setToRandomDirection().scl(spread);

                        BaseUnit unit = group.createUnit(Vars.state.rules.waveTeam);
                        unit.set(spawnX + Tmp.v1.x, spawnY + Tmp.v1.y);

                        //⚠ 时机差异：本项目 type.create() 内部已 Units.add（原版 create 未入组、
                        //add() 才入组）。为等价原版"延迟期间不可见不可交互"，create 后先摘出，
                        //到延迟点由 spawnEffect → unit.add() 重新入组。
                        Units.remove(unit);

                        Time.run(Math.min(i * 5, 60 * 2), () -> spawnEffect(unit));
                    }
                });
            }
        }

        //开波清场（原版 WaveSpawner.java:78-83）：20 帧后冲击波特效、40 帧后摧毁空投区内
        //敌方（= 玩家方）单位与建筑 —— 原版防"堵门建塔"的设计。attack 核心增援点 doShockwave=false 不清。
        eachGroundSpawn((spawnX, spawnY, doShockwave) -> {
            if(doShockwave){
                Time.run(20f, () -> Effects.effect(Fx.spawnShockwave, spawnX, spawnY, Vars.state.rules.dropZoneRadius));
                Time.run(40f, () -> Damage.damage(Vars.state.rules.waveTeam, spawnX, spawnY, Vars.state.rules.dropZoneRadius, 99999999f, true));
            }
        });

        //约 2 秒后视为生成结束（对应原版 Time.runTask(121f, ...)）
        Time.run(121f, () -> spawning = false);
    }

    /** 遍历所有地面出生点（overlay 出生点 doShockwave=true + attack 模式下敌方核心旁的增援点 doShockwave=false）。 */
    private void eachGroundSpawn(SpawnConsumer cons){
        for(int i = 0; i < groundSpawns.size; i++){
            Tile spawn = groundSpawns.get(i);
            cons.accept(spawn.worldx(), spawn.worldy(), true);
        }

        //attack 模式：从敌方核心附近增援（原版 WaveSpawner.java:93-99）
        if(Vars.state.rules.attackMode && Vars.state.teams.isActive(Vars.state.rules.waveTeam) && !Vars.state.teams.playerCores().isEmpty()){
            Tile firstCore = Vars.state.teams.playerCores().first();
            //原版 firstCore/core 是 TileEntity（x/y = 建筑中心）；本项目核心列表是锚点 Tile，
            //建筑中心用 Block.centerX/centerY（worldx() 是左下角）
            float firstX = firstCore.block().centerX(firstCore), firstY = firstCore.block().centerY(firstCore);
            Array<Tile> cores = Vars.state.teams.cores(Vars.state.rules.waveTeam);
            for(int i = 0; i < cores.size; i++){
                Tile core = cores.get(i);
                float cx = core.block().centerX(core), cy = core.block().centerY(core);
                //增援点在核心到玩家核心方向上偏出 coreMargin + 核心半径，保证不在核心头顶刷出
                Tmp.v1.set(firstX, firstY).sub(cx, cy).limit(coreMargin + core.block().size * Vars.tilesize);
                cons.accept(cx + Tmp.v1.x, cy + Tmp.v1.y, false);
            }
        }
    }

    /** 遍历所有飞行出生点（地图外沿射线 + attack 模式下敌方核心上空）。 */
    private void eachFlyerSpawn(Floatc2 cons){
        for(int i = 0; i < flySpawns.size; i++){
            FlyerSpawn spawn = flySpawns.get(i);
            float trns = (Vars.world.width() + Vars.world.height()) * Vars.tilesize;
            float spawnX = Mathf.clamp(Vars.world.unitWidth() / 2f + Angles.trnsx(spawn.angle, trns), -margin, Vars.world.unitWidth() + margin);
            float spawnY = Mathf.clamp(Vars.world.unitHeight() / 2f + Angles.trnsy(spawn.angle, trns), -margin, Vars.world.unitHeight() + margin);
            cons.get(spawnX, spawnY);
        }

        //attack 模式：飞行单位直接在敌方核心上空出生（原版 WaveSpawner.java:110-114）
        if(Vars.state.rules.attackMode && Vars.state.teams.isActive(Vars.state.rules.waveTeam)){
            Array<Tile> cores = Vars.state.teams.cores(Vars.state.rules.waveTeam);
            for(int i = 0; i < cores.size; i++){
                Tile core = cores.get(i);
                cons.get(core.block().centerX(core), core.block().centerY(core));
            }
        }
    }

    /**
     * 单位出生特效 + 二段延迟入组（对应原版 spawnEffect，WaveSpawner.java:144-150）：
     * 先播放单位图标落下的 unitSpawn 特效，再过 30 帧才真正入组 + 播放 spawn 特效。
     */
    private void spawnEffect(BaseUnit unit){
        Effects.effect(Fx.unitSpawn, unit.getX(), unit.getY(), 0f, unit);
        Time.run(30f, () -> {
            unit.add();
            Effects.effect(Fx.spawn, unit);
        });
    }

    /** @return 是否正在生成敌人（对应原版 {@code spawning && !net.client()}：客机由服务器广播驱动） */
    public boolean isSpawning(){
        return spawning && !Vars.isClient();
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

    /**
     * @return 玩家（非波次阵营）是否站在某个地面出生点的空投区内（对应原版 {@code WaveSpawner.playerNear}）。
     * <p>⚠ 原版用 {@code groundSpawns.contains(pred)}；本项目该数组是 libgdx {@code Array}（无谓词重载），
     * 改为下标循环。坐标口径照抄原版：出生点取格坐标 * tilesize（左下角）。
     */
    public boolean playerNear(){
        if(Vars.player == null || Vars.state == null || Vars.state.rules == null) return false;

        for(int i = 0; i < groundSpawns.size; i++){
            Tile g = groundSpawns.get(i);
            if(Mathf.dst(g.x * Vars.tilesize, g.y * Vars.tilesize, Vars.player.x, Vars.player.y) < Vars.state.rules.dropZoneRadius
                && Vars.player.getTeam() != Vars.state.rules.waveTeam){
                return true;
            }
        }
        return false;
    }

    /** 飞行单位出生方向（只存角度，与原版一致）。 */
    private static class FlyerSpawn{
        float angle;
    }
}
