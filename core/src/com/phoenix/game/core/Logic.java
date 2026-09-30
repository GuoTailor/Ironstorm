package com.phoenix.game.core;

import com.phoenix.game.Vars;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Units;
import com.phoenix.game.content.Fx;
import com.phoenix.game.game.EventType;
import com.phoenix.game.game.Team;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Tile;

/**
 * 最小实现：游戏逻辑主循环。参照 Mindustry mindustry.core.Logic 移植。
 * 相机操作已移到 input.DesktopInput（与 Mindustry 一致），暂停时不会调用本类。
 */
public class Logic {

    /**
     * 发射收尾延时任务的剩余 tick（对应原版 {@code Logic.launchZone()} 里的
     * {@code Time.runTask(30f, ...)}，mindustry/core/Logic.java:186）。
     * <p>⚠ <b>刻意偏离（原因）</b>：arc 的 {@code Time.runTask} 走 {@code java.util.Timer}（按**真实秒**计，
     * 不受暂停影响）；本项目的 {@code Time.run} 是 tick 队列、只在 {@code Time.update()} 内推进，
     * 而 {@code Time.update()} 被 {@code !state.isPaused()} 守卫包住（见本类 {@code update()}）——
     * 发射时 {@code state.gameOver} 已置 true（即"暂停"）→ 若直接用 {@code Time.run(30f, ...)}，
     * 任务会**永不执行**（白闪后卡死、核心不消失、结算框不弹）。
     * 故这里把延时改到 {@code update()} 的暂停守卫**之外**按 {@link Time#delta()} 递减，行为等价
     * （30 tick ≈ 0.5s；原版 {@code runTask(30f)} 也是 30/60 = 0.5 秒）。
     */
    private static float launchTimer = -1f;
    /** {@link #launchTimer} 到点后要执行的回调；{@code launchTimer < 0} 表示当前没有待执行任务。 */
    private static Runnable launchTimerTask;

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
        //对应原版 Logic.java:211 —— update() **最开头**每帧派发 Trigger.update（mod/成就/脚本挂接点）。
        //⚠ 刻意说明：本项目 Logic.update() 由 ClientLauncher 在 `state.isPlaying()` 时调用（原版 Logic 是
        //  ApplicationListener 模块、每帧无条件 update）→ 本事件只在游玩中派发，菜单/暂停态不派发。
        Events.fire(EventType.Trigger.update);

        //敌兵统计：原版在暂停守卫**之外**（原版 Logic.java:215，每帧都数）→ HUD 敌人数不因暂停/gameOver 僵住。
        if(!Vars.isClient()){
            countEnemies();
        }

        //⚠ G4-5：原版 Logic.update 用 `if(!state.isPaused())` 包住整段仿真（原版 Logic.java:218-260）——
        //   Time / 波次计时 / 特效 / 实体 / 碰撞全在守卫内，且 gameOver 也算暂停（见 GameState.isPaused）→
        //   胜负判定后冻结仿真。本项目 state 在 gameOver 时仍是 playing（ClientLauncher 只按 isPlaying() 调
        //   update）→ 必须靠这里冻结，否则结算框弹出后世界仍在跑。
        if(!Vars.state.isPaused()){
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
            //波次生成 / 自动存档。
            if(!Vars.isClient()){
                //自动存档计时
                if(Vars.saves != null){
                    Vars.saves.update();
                }

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
        }

        //发射收尾延时任务（见 launchTimer 的字段注释）：必须放在 isPaused 守卫**之外**——
        //发射时 gameOver 已置 true，仿真被冻结，但发射序列仍须继续推进（原版靠 java.util.Timer 做到）。
        if(launchTimer >= 0f){
            launchTimer -= Time.delta();
            if(launchTimer <= 0f){
                launchTimer = -1f;
                Runnable task = launchTimerTask;
                launchTimerTask = null;
                if(task != null) task.run();
            }
        }

        //规则可关闭胜负判定（对应原版 !net.client() && ... && state.rules.canGameOver）。
        //原版 checkGameOver 在暂停守卫**之外**（原版 Logic.java:262-264）→ 保持同样位置。
        if(!Vars.isClient() && Vars.state.rules.canGameOver){
            checkGameOver();
        }
    }

    /** 统计场上敌方单位数量（对应原版 Logic.update 的 state.enemies）。 */
    private static void countEnemies(){
        int enemies = 0;
        Team waveTeam = Vars.state.rules.waveTeam;

        for(int i = 0; i < Units.units.size; i++){
            BaseUnit unit = Units.units.get(i);
            //照原版 Logic.update（mindustry/core/Logic.java:215）：还要 countsAsEnemy()，
            //否则敌方 Builder/Miner/Repair 无人机会被计入敌人数（HUD 偏高 + waitForWaveToEnd 卡波）。
            if(unit.getTeam() == waveTeam && unit.countsAsEnemy()){
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
            //照原版 Logic.java:160-168 分两支：区域图 + 玩家队获胜 → 等价于"发射成功"（原版 Call.launchZone()）；
            //否则普通胜利，fire GameOverEvent(alive)。state.gameOver = true 在**两支之后**统一置（原版 :167）。
            //⚠ 本项目没有 Call（网络 RPC 层）→ 单机/主机直接调 Logic.launchZone()。
            //⚠ 纯客机（Vars.isClient()）分支在本项目不可达：checkGameOver 只在 !isClient() 时被调用
            //  （见 update() 末尾），且网络层没有任何"发射/gameOver"同步包（Packets 只有 WorldState，
            //   只带 wave/enemies/wavetime）→ 客户端发射暂不支持，仅主机/单机（见 G7 报告）。
            if(Vars.world != null && Vars.world.isZone() && alive == Vars.state.rules.defaultTeam){
                //in attack maps, a victorious game over is equivalent to a launch
                launchZone();
            }else{
                Vars.state.stats.wavesLasted = Vars.state.wave;
                System.out.println("胜负判定：" + alive.name + " 获胜（其余队伍核心被摧毁）");

                //原版：Events.fire(new GameOverEvent(alive)) → Control 的监听器调 Logic.onGameOver
                //      → ui.restart.show(winner)。F-LOW 批已补 GameOverEvent → 按原版先 fire；
                //      本项目没有该监听器 → 与上面非 attack 分支一致，直接弹结算框。
                Events.fire(new EventType.GameOverEvent(alive));
                if(Vars.ui != null && Vars.ui.restart != null){
                    Vars.ui.restart.show(alive);
                }else{
                    Events.fire(new EventType.WinEvent());
                    Vars.control.menu();
                }
            }

            Vars.state.gameOver = true;
        }
    }

    /**
     * 战役区域"发射到下一区域"（照抄原版 {@code Logic.launchZone()}，mindustry/core/Logic.java:172-200）。
     * <p>流程：白闪提示 → 各玩家核心播放发射特效 → 标记该区域已发射 → 延时 30 tick 后把核心库存
     * 并入全局物品（{@code data}）、逐个移除核心、置 {@code state.launched/gameOver}，并派发
     * {@code LaunchEvent} / {@code GameOverEvent}。
     * <p>⚠ <b>与原版的差异（均在此声明）</b>：
     * <ul>
     *   <li>原版是 {@code @Remote(called = Loc.both)} 的网络 RPC；本项目没有 {@code Call} 层 →
     *       改为普通静态方法，仅主机/单机调用（纯客机见 {@link #checkGameOver} 的说明）。</li>
     *   <li>原版 {@code if(!headless)}；本项目没有 headless 概念 → 用 {@code Vars.hud != null} 守卫。</li>
     *   <li>原版延时用 {@code Time.runTask(30f, ...)}（走 java.util.Timer，不受暂停影响）；本项目
     *       {@code Time.run} 依赖 {@code Time.update()}，发射时仿真已暂停 → 改用
     *       {@link #launchTimer}/{@link #launchTimerTask} 在暂停守卫之外递减（见字段注释，行为等价）。</li>
     *   <li>原版边遍历 {@code state.teams.playerCores()} 边 {@code tile.remove()}（arc 数组无 fail-fast
     *       检查所以能跑）；libgdx 数组的迭代器会抛异常 → 先拷贝一份再遍历（语义等价：每个核心处理一次）。</li>
     *   <li>原版 {@code GameOverEvent} 由 {@code Control} 的监听器消费（置 {@code stats.wavesLasted} +
     *       弹结算框，原版 Control.java:103-114）；本项目没有该监听器 → 与 {@code Logic.update()} 里
     *       既有写法一致，就地补 {@code stats.wavesLasted} 并直接调 {@code Vars.ui.restart.show(...)}。</li>
     * </ul>
     */
    public static void launchZone(){
        //原版：if(!headless) ui.hudfrag.showLaunch(); —— 全屏白闪（本项目 HudFragment.showLaunch）
        if(Vars.hud != null){
            Vars.hud.showLaunch();
        }

        //对每个玩家核心播放发射特效（原版 Effects.effect(Fx.launch, tile)，传的是 TileEntity）。
        //⚠ 传参口径核对：原版 TileEntity.init 里 x/y = tile.drawx()/drawy()（= 方块中心），而本项目
        //  Tile.getX()/getY() 就是 drawx()/drawy()（同样 = 方块中心）→ 直接传 Tile 等价，无需换算。
        //  （playerCores() 存的都是锚点瓦片，drawx() 对锚点才等于中心；卫星瓦片不在此列。）
        com.badlogic.gdx.utils.Array<Tile> cores = Vars.state.teams.playerCores();
        for(int i = 0; i < cores.size; i++){
            Effects.effect(Fx.launch, cores.get(i));
        }

        if(Vars.world.getZone() != null){
            Vars.world.getZone().setLaunched();
        }

        //延时 30 tick 后的收尾（原版 Time.runTask(30f, ...)）；机制见 launchTimer 字段注释
        launchTimer = 30f;
        launchTimerTask = () -> {
            //拷贝一份再遍历：tile.remove() → onProximityRemoved → unregisterCore 会从 cores 里摘掉自己
            com.badlogic.gdx.utils.Array<Tile> coreList =
                new com.badlogic.gdx.utils.Array<>(Vars.state.teams.playerCores());
            for(int i = 0; i < coreList.size; i++){
                Tile tile = coreList.get(i);
                if(tile == null || tile.entity == null || tile.entity.items == null) continue;

                //核心库存并入全局物品（原版 data.addItem + LaunchItemEvent，逐物品派发）
                for(int j = 0; j < Vars.content.items().size; j++){
                    Item item = Vars.content.items().get(j);
                    int amount = tile.entity.items.get(item);
                    if(Vars.data != null) Vars.data.addItem(item, amount);
                    Events.fire(new EventType.LaunchItemEvent(item, amount));
                }

                tile.remove();
            }

            Vars.state.launched = true;
            Vars.state.gameOver = true;
            //原版由 Control 的 GameOverEvent 监听器置 state.stats.wavesLasted = state.wave（Control.java:104）；
            //本项目没有该监听器 → 就地补上，否则结算框"存活波次"会显示 0
            Vars.state.stats.wavesLasted = Vars.state.wave;
            Events.fire(new EventType.LaunchEvent());
            //manually fire game over event now
            Events.fire(new EventType.GameOverEvent(Vars.state.rules.defaultTeam));
            if(Vars.ui != null && Vars.ui.restart != null){
                Vars.ui.restart.show(Vars.state.rules.defaultTeam);
            }
        };
    }

    /**
     * 取消尚未执行的发射收尾任务（对应 {@code Time.clear()} 对延时任务队列的清理）。
     * <p>由 {@code Control.menu()} 在 {@code Time.clear()} 旁调用，避免残留的发射任务被带进下一局
     * （本项目的 {@code launchTimer} 不挂在 {@code Time.runs} 队列里，{@code Time.clear()} 清不到它）。
     */
    public static void cancelLaunchTimer(){
        launchTimer = -1f;
        launchTimerTask = null;
    }

    /** @return 该队伍是否还有存活核心（已接线 S3：直接查 {@code Teams} 的核心列表）。 */
    //原版 Logic.java:110/152 team.hasCore()；本项目 Teams 已有核心列表（S3 批次）→ 直接用
    private static boolean hasCore(Team team){
        return Vars.state != null && Vars.state.teams != null && Vars.state.teams.hasCore(team);
    }

}
