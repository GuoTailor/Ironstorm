package com.phoenix.game.core;

import com.phoenix.game.Vars;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Units;
import com.phoenix.game.game.EventType;
import com.phoenix.game.game.Team;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockFlag;

/**
 * 最小实现：游戏逻辑主循环。参照 Mindustry mindustry.core.Logic 移植。
 * 相机操作已移到 input.DesktopInput（与 Mindustry 一致），暂停时不会调用本类。
 */
public class Logic {

    /** 每帧更新一次游戏逻辑。 */
    public static void update(){
        Time.update();
        Effects.update();

        //规则级逻辑只在服务端跑（对应原版 Logic.update 里的 net.client() 守卫）：
        //波次生成 / 敌兵统计 / 胜负判定 / 自动存档。
        if(!Vars.isClient()){
            //自动存档计时
            if(Vars.saves != null){
                Vars.saves.update();
            }

            countEnemies();
            updateWaves();
        }

        if(Vars.world != null){
            Vars.world.updateTiles();
        }

        //玩家：同步位置 + 重生计时（原版玩家本身是单位，随单位一起更新）
        if(Vars.player != null){
            Vars.player.update();
        }

        //单位：倒序遍历，允许更新过程中移除自身
        for(int i = Units.units.size - 1; i >= 0; i--){
            Units.units.get(i).update();
        }

        //子弹
        for(int i = Bullet.all.size - 1; i >= 0; i--){
            Bullet bullet = Bullet.all.get(i);
            if(!bullet.isDead()){
                bullet.update();
            }
        }

        //实体碰撞：单位 vs 子弹（原版由 collideGroups 处理；单位在外层，避免处理已回收的子弹）
        if(Vars.collisions != null && Bullet.all.size > 0 && Units.units.size > 0){
            Vars.collisions.collideGroups(Units.units, Bullet.all);
        }

        //清理死亡单位
        for(int i = Units.units.size - 1; i >= 0; i--){
            if(Units.units.get(i).isDead()){
                Units.units.removeIndex(i);
            }
        }

        if(!Vars.isClient()){
            checkGameOver();
        }
    }

    /** 统计场上敌方单位数量（对应原版 Logic.update 的 state.enemies）。 */
    private static void countEnemies(){
        int enemies = 0;
        Team waveTeam = Vars.state.rules.waveTeam;

        for(int i = 0; i < Units.units.size; i++){
            if(Units.units.get(i).getTeam() == waveTeam){
                enemies++;
            }
        }

        Vars.state.enemies = enemies;
    }

    /** 波次计时与开波（对应原版 Logic.update 的波次部分）。 */
    private static void updateWaves(){
        if(Vars.state.rules == null || !Vars.state.rules.waves || Vars.state.gameOver) return;

        if(Vars.state.rules.waveTimer && (!Vars.state.rules.waitForWaveToEnd || Vars.state.enemies == 0)){
            Vars.state.wavetime = Math.max(Vars.state.wavetime - Time.delta(), 0f);
        }

        if(Vars.state.wavetime <= 0f && Vars.spawner != null){
            runWave();
        }
    }

    /** 开一波：生成敌人、波次 +1、重置计时（对应原版 Logic.runWave）。 */
    public static void runWave(){
        if(Vars.spawner != null){
            Vars.spawner.spawnEnemies();
        }

        Vars.state.wave++;
        Vars.state.wavetime = Vars.state.rules.waveSpacing;

        System.out.println("第 " + Vars.state.wave + " 波开始");
        Events.fire(new EventType.WaveEvent());
    }

    /**
     * 胜负判定：任一队伍核心全毁且仍存活的队伍只剩一支，则该队胜。
     * 仅当还存活两支及以上队伍时检查；胜出后回菜单。
     */
    private static void checkGameOver(){
        if(Vars.state.gameOver || Vars.world == null) return;

        int alive = 0;
        Team winner = null;
        for(Team team : Team.base()){
            if(hasCore(team)){
                alive++;
                winner = team;
            }
        }

        if(alive == 1 && winner != null){
            Vars.state.gameOver = true;
            System.out.println("胜负判定：" + winner.name + " 获胜（核心被全部摧毁）");
            Events.fire(new EventType.WinEvent());
            Vars.control.menu();
        }
    }

    /** @return 该队伍是否还有存活核心（多格核心只统计有实体的中心瓦片）。 */
    private static boolean hasCore(Team team){
        for(Tile tile : Vars.world.tiles){
            if(tile != null && tile.block().flags.contains(BlockFlag.core) && tile.getTeam() == team && tile.entity != null){
                return true;
            }
        }
        return false;
    }
}
