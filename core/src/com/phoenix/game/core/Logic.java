package com.phoenix.game.core;

import com.phoenix.game.Vars;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Units;
import com.phoenix.game.game.EventType;
import com.phoenix.game.game.Team;

/**
 * 最小实现：游戏逻辑主循环。参照 Mindustry mindustry.core.Logic 移植。
 * 相机操作已移到 input.DesktopInput（与 Mindustry 一致），暂停时不会调用本类。
 */
public class Logic {

    //对应原版 Logic() 构造器里注册的 WaveEvent 监听器（mindustry/core/Logic.java:34-43）：
    //每波开始给每个玩家重置剩余重生次数；区域图（战役 Zone）推进 Zone 的波次进度（更新可解锁/发射波标记）。
    //⚠ 本项目 Logic 是纯静态类、从不实例化，所以用静态初始化块替代原版构造器。
    static{
        Events.on(EventType.WaveEvent.class, event -> {
            if(Vars.playerGroup != null){
                for(com.phoenix.game.entities.type.Player p : Vars.playerGroup.all()){
                    p.respawns = Vars.state.rules.respawns;
                }
            }

            if(Vars.world != null && Vars.world.isZone()){
                Vars.world.getZone().updateWave(Vars.state.wave);
            }
        });
    }

    /** 每帧更新一次游戏逻辑。 */
    public static void update(){
        Time.update();
        Effects.update();

        //实体组的延迟 add/remove 队列（对应原版 Logic.update 里各 group.update() 开头的 updateEvents）。
        //⚠ 本项目仿真仍以 Units.units / Bullet.all / Puddle.all 等静态数组为准，所以这里**只 flush 队列**、
        //   不调用 group.update()（否则实体会被重复 update 一遍）。
        if(Vars.entities != null){
            for(int i = 0; i < Vars.entities.all().size; i++){
                Vars.entities.all().get(i).updateEvents();
            }
        }

        //特效实体组（对应原版 Logic.update 里的 effectGroup.update()，排在 unitGroup 之前）：
        //⚠ 这是唯一**必须**调 group.update() 的组 —— ItemTransfer 这类特效实体不在任何静态数组里，
        //   只能靠组每帧 update（生命周期结束时 remove() → removed() 里执行"物品真正入库"的回调）。
        //   其余组仍只 flush 队列（上面那段），仿真以 Units.units / Bullet.all 等静态数组为准。
        if(Vars.effectGroup != null){
            Vars.effectGroup.update();
        }

        //游玩时长（存档元数据用）：只在真正游玩时累加，回菜单后停止
        if(!Vars.state.isMenu()){
            Vars.state.playtime += Time.delta();
        }

        //全局库存有改动就落盘（对应原版 GlobalData.checkSave）
        if(Vars.data != null){
            Vars.data.checkSave();
        }

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

        //燃烧（对应原版 Logic.update 里 tileGroup 之后的 fireGroup.update）
        com.phoenix.game.entities.effect.Fire.updateAll();

        //液体水洼（对应原版 Logic.update 里 puddleGroup.update）
        com.phoenix.game.entities.effect.Puddle.updateAll();
        //地面残骸贴花（对应原版 Logic.update 里 groundEffectGroup.update 的 BelowLiquidTrait 那部分）
        com.phoenix.game.entities.effect.Decal.updateAll();

        //玩家：同步位置 + 重生计时（原版玩家本身是单位，随单位一起更新）
        if(Vars.player != null){
            Vars.player.update();
        }

        //远端玩家头顶发言气泡的计时衰减：原版每个 Player 都是实体、随 playerGroup 一起 update()，
        //本项目只 update 本地玩家（上面那行），远端玩家不跑 Player.update() → 气泡会一直挂着。
        //这里只补 textFadeTime 的衰减（远端玩家的重生逻辑仍由服务端权威，绝不能在这里调 Player.update()）。
        if(Vars.playerGroup != null){
            com.badlogic.gdx.utils.Array<com.phoenix.game.entities.type.Player> players = Vars.playerGroup.all();
            for(int i = 0; i < players.size; i++){
                com.phoenix.game.entities.type.Player p = players.get(i);
                if(p != null && p != Vars.player && p.textFadeTime > 0f){
                    p.textFadeTime -= Time.delta() / (60 * 5);
                }
            }
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

        //实体碰撞：子弹 vs 单位（对应原版 collideGroups(bulletGroup, unitGroup)，原版子弹组在第一个参数）。
        //⚠ 本项目刻意对调实参（单位在外层）：checkCollide 是 a.collision → b.collision 顺序回调，
        //若子弹在外层，子弹 collision 里的 remove() 会先回收自身（清空 type），随后单位侧
        //BaseUnit.collision 再调 ((DamageTrait)other).getShieldDamage() 就会拿到已回收的子弹（NPE 风险）。
        //双向 collides 判定与两次回调都执行，与原版语义等价。
        if(Vars.collisions != null && Bullet.all.size > 0 && Units.units.size > 0){
            Vars.collisions.collideGroups(Units.units, Bullet.all);
        }

        //清理死亡单位
        for(int i = Units.units.size - 1; i >= 0; i--){
            if(Units.units.get(i).isDead()){
                Units.units.removeIndex(i);
            }
        }

        //规则可关闭胜负判定（对应原版 !net.client() && ... && state.rules.canGameOver）
        if(!Vars.isClient() && Vars.state.rules.canGameOver){
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
        //照原版 Logic.runWave（mindustry/core/Logic.java:139）：区域图的发射波（isLaunchWave）波次间隔乘 launchWaveMultiplier
        Vars.state.wavetime = Vars.world.isZone() && Vars.world.getZone().isLaunchWave(Vars.state.wave)
                ? Vars.state.rules.waveSpacing * Vars.state.rules.launchWaveMultiplier
                : Vars.state.rules.waveSpacing;

        //玩家 respawns 的每波重置在静态块注册的 WaveEvent 监听器里做（对应原版结构），此处不再重复
        System.out.println("第 " + Vars.state.wave + " 波开始");
        Events.fire(new EventType.WaveEvent());
    }

    /**
     * 胜负判定（对应原版 {@code Logic.checkGameOver}，按原版分两支）。
     * <ul>
     *   <li><b>非 attack 模式</b>：只有"**玩家核心全毁**"才 gameOver（判负，胜方记为波次阵营）。
     *       区域图（survival，如 {@code groundZero}）走这一支 —— 地图里只有玩家核心，敌人从 spawn 点刷出。</li>
     *   <li><b>attack 模式</b>：遍历各队，只剩一支有核心 → 该队胜。</li>
     * </ul>
     * ⚠ 本项目此前实现成"只剩一支队伍有核心即判胜"（**不分模式**）→ 在只有玩家核心的区域图里
     * **每帧立刻误判玩家获胜**（`playZone` 后直接弹结算框，见区域链路批的实测）。
     * 原版非 attack 分支根本不看"有几支队伍有核心"，故已按原版重写。
     */
    private static void checkGameOver(){
        if(Vars.state.gameOver || Vars.world == null || Vars.state.rules == null) return;

        if(!Vars.state.rules.attackMode){
            //非 attack：玩家核心全毁 = 失败（对应原版 state.teams.playerCores().size == 0）
            if(hasCore(Vars.state.rules.defaultTeam)) return;

            Vars.state.gameOver = true;
            //结算统计（对应原版 Logic.onGameOver 里的 state.stats.wavesLasted = state.wave）
            Vars.state.stats.wavesLasted = Vars.state.wave;
            System.out.println("胜负判定：玩家核心被全部摧毁");

            //原版：Events.fire(new GameOverEvent(waveTeam)) → Control 的监听器调 Logic.onGameOver
            //      → ui.restart.show(winner)（GameOverDialog 内部按胜负发 Win/LoseEvent，
            //        点它的"菜单"按钮才回菜单）。F-LOW 批已补 GameOverEvent → 按原版先 fire；
            //        等价消费逻辑（结算统计/弹结算框）原本就内联在本方法里，新监听者可再挂接。
            Events.fire(new EventType.GameOverEvent(Vars.state.rules.waveTeam));
            if(Vars.ui != null && Vars.ui.restart != null){
                Vars.ui.restart.show(Vars.state.rules.waveTeam);
            }else{
                Events.fire(new EventType.WinEvent());
                Vars.control.menu();
            }
            return;
        }

        //attack 模式：只剩一支队伍有核心 → 该队胜
        Team alive = null;
        for(Team team : Team.base()){
            if(hasCore(team)){
                if(alive != null) return;   //两支及以上还活着 → 继续打
                alive = team;
            }
        }

        if(alive != null && !Vars.state.gameOver){
            Vars.state.gameOver = true;
            Vars.state.stats.wavesLasted = Vars.state.wave;
            System.out.println("胜负判定：" + alive.name + " 获胜（其余队伍核心被摧毁）");

            //原版：区域图里若玩家队获胜，等价于"发射成功"（Call.launchZone）；
            //本项目区域发射/存档/解锁链未移植 → 统一按普通胜利弹结算框。
            //F-LOW 批：同上，按原版先 fire GameOverEvent（原版 Logic.java:165）。
            Events.fire(new EventType.GameOverEvent(alive));
            if(Vars.ui != null && Vars.ui.restart != null){
                Vars.ui.restart.show(alive);
            }else{
                Events.fire(new EventType.WinEvent());
                Vars.control.menu();
            }
        }
    }

    /** @return 该队伍是否还有存活核心（已接线 S3：直接查 {@code Teams} 的核心列表）。 */
    //原版 Logic.java:110/152 team.hasCore()；本项目 Teams 已有核心列表（S3 批次）→ 直接用
    private static boolean hasCore(Team team){
        return Vars.state != null && Vars.state.teams != null && Vars.state.teams.hasCore(team);
    }

}
