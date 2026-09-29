package com.phoenix.game.core;
import com.phoenix.game.game.Rules;
import com.phoenix.game.game.Teams;
import com.phoenix.game.game.Team;
import com.phoenix.game.game.DefaultWaves;
import com.phoenix.game.game.EventType;
import com.phoenix.game.game.Tutorial;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.input.DesktopInput;
import com.phoenix.game.input.InputHandler;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.content.UnitTypes;
import com.phoenix.game.entities.Units;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.content.Items;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.ui.dialogs.FloatingDialog;
import com.phoenix.game.ui.fragments.MenuFragment;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Build;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.BuildBlock;
import com.phoenix.game.world.meta.I18n;

/**
 * 最小实现：游戏控制（开始战役、加载地图）。参照 Mindustry mindustry.core.Control 移植。
 */
public class Control{
    /** 战役地图尺寸（瓦片） */
    public static final int mapWidth = 120, mapHeight = 120;

    /** 当前输入处理器（对应原版 control.input）。由 {@link Vars#init()} 按平台切换。 */
    public InputHandler input = new DesktopInput();

    /**
     * 教程状态机（对应原版 {@code control.tutorial}，原版也在 Control 构造器里 new）。
     * <p>由 {@code ClientLauncher} 每帧在 {@code state.rules.tutorial} 为真时调 {@code update()}。
     */
    public Tutorial tutorial;

    /** 本局是否刷新了当前地图的波次记录（对应原版 {@code Control.hiscore}，结算框的"新纪录"行读它）。 */
    private boolean hiscore = false;

    public Control(){
        tutorial = new Tutorial();

        //高分记录（对应原版 Control 构造器里的两个监听器）：
        //  ResetEvent → 清标记；WaveEvent → 当前波次超过本地记录就记新纪录
        Events.on(EventType.ResetEvent.class, event -> hiscore = false);
        Events.on(EventType.WaveEvent.class, event -> {
            //原版是 world.getMap().getHightScore()（原版 Control.java:94）；见 highScoreKey()
            if(Vars.state.wave > Core.settings.getInt(highScoreKey(), 0)){
                hiscore = true;
                Core.settings.put(highScoreKey(), Vars.state.wave);
                Core.settings.save();
            }
        });

        //本局统计（对应原版 Control 构造器里的三个监听器，结算框的后四行读它们）。
        //⚠ Vars.player 在服务端/菜单态可能为 null，而本项目 Events.fire 是**同步**的
        //  （原版经 Core.app.post 延后一帧）→ 必须加 null 守卫，否则服务端一死单位就 NPE。
        Events.on(EventType.BlockBuildEndEvent.class, e -> {
            if(Vars.player == null || e.team != Vars.player.getTeam()) return;
            if(e.breaking){
                Vars.state.stats.buildingsDeconstructed++;
            }else{
                Vars.state.stats.buildingsBuilt++;
            }
        });

        Events.on(EventType.BlockDestroyEvent.class, e -> {
            if(Vars.player == null || e.tile == null || e.tile.getTeam() != Vars.player.getTeam()) return;
            Vars.state.stats.buildingsDestroyed++;
        });

        //"被摧毁方块"重建队列（对应原版 core.Logic 构造器里的两个监听器：BlockDestroyEvent 入队 /
        //BlockBuildEndEvent 出队）。原版挂在 state.teams.get(team).brokenBlocks，本项目见 Teams.brokenBlocks(Team)。
        //消费方是 Renderer.drawDestroyed()（放置/拆除时淡入的"重建幽灵轮廓"）。
        Events.on(EventType.BlockDestroyEvent.class, e -> {
            if(Vars.state == null || Vars.state.teams == null || e.tile == null) return;
            //客机不能改这些请求（对应原版 net.client() 守卫；原版还判 tile.block().rebuildable，
            //本项目 Block 没有该字段 → 不做该过滤，见类/方法注释）
            if(Vars.isClient()) return;

            Tile tile = e.tile;
            if(tile.entity == null) return;

            Block block = tile.block();
            if(block == null || block == Blocks.air) return;

            if(block instanceof BuildBlock){
                //在建/拆除中的格子：只有"正在建造的合成方块"才值得重建（对应原版 BuildBlock 分支）
                if(!(tile.entity instanceof BuildBlock.BuildEntity)) return;
                Block cblock = ((BuildBlock.BuildEntity)tile.entity).cblock;
                if(cblock == null || !cblock.synthetic()) return;
                block = cblock;
            }

            Array<Teams.BrokenBlock> broken = Vars.state.teams.brokenBlocks(tile.getTeam());
            //同格已有记录先删掉（原版同样先 O(n) 找同格再 addFirst）
            for(int i = 0; i < broken.size; i++){
                Teams.BrokenBlock b = broken.get(i);
                if(b.x == tile.x && b.y == tile.y){
                    broken.removeIndex(i);
                    break;
                }
            }
            broken.insert(0, new Teams.BrokenBlock(tile.x, tile.y, tile.rotation(), (short)block.id, tile.entity.config()));
        });

        Events.on(EventType.BlockBuildEndEvent.class, e -> {
            //只有"建好了东西"才需要把该处从重建队列里剔除（拆掉不算）
            if(e.breaking || Vars.state == null || Vars.state.teams == null || e.tile == null) return;

            Array<Teams.BrokenBlock> broken = Vars.state.teams.brokenBlocks(e.team);
            for(int i = broken.size - 1; i >= 0; i--){
                Teams.BrokenBlock b = broken.get(i);
                Tile t = Vars.world == null ? null : Vars.world.tile(b.x, b.y);
                //该位置已经被重新建上东西 → 出队。原版用 block.bounds 重叠判定（支持多格），
                //本项目 Block 没有 bounds → 退化为"该格已不再是空气"（多格方块的所有格都会被铺上，判据等价）
                if(t == null || t.block() != Blocks.air){
                    broken.removeIndex(i);
                }
            }
        });

        Events.on(EventType.UnitDestroyEvent.class, e -> {
            //原版 Control.java:179-183：在区域（战役地图）里摧毁单位即解锁该单位类型
            //（放在下面的玩家判空之前：该分支不依赖 Vars.player）
            if(e.unit instanceof BaseUnit && Vars.world != null && Vars.world.isZone()){
                Vars.data.unlockContent(((BaseUnit)e.unit).getType());
            }

            if(Vars.player == null || e.unit == null || e.unit.getTeam() == null) return;
            if(e.unit.getTeam() != Vars.player.getTeam()){
                Vars.state.stats.enemyUnitsDestroyed++;
            }
        });

        //解锁提示（对应原版 Control 构造器里的 `Events.on(UnlockEvent.class, e -> ui.hudfrag.showUnlock(e.content))`，
        //Control.java:129）：新内容解锁时在 HUD 顶部弹"图标 + 已解锁"toast。
        //⚠ 本项目 Events.fire 是同步的（unlockContent 可能在 Logic.update 中途触发）→ 动 stage 用
        //  Gdx.app.postRunnable 延后一帧，与 Trigger.newGame 监听器的处理一致。
        Events.on(EventType.UnlockEvent.class, e -> {
            if(Gdx.app != null) Gdx.app.postRunnable(() -> {
                if(Vars.hud != null) Vars.hud.showUnlock(e.content);
            });
        });

        //UI 缩放确认框（对应原版 Control.init() 里的 uiscalechanged 分支）。
        //原版用 arc 的 Core.app.post(() -> Core.app.post(...)) 延后两帧（等 stage/UI 就绪）；
        //本项目 Control 的构造发生在 Vars 类初始化（ClientLauncher.create() 内）、此时 stage 还没建，
        //所以同样用 Gdx.app.postRunnable 双重延后 —— 与 ClientLauncher 里 playTutorial 的处理一致。
        //⚠ 不需要主会话在 ClientLauncher 补行：延迟到第 3 帧时 FloatingDialog.scene 与 Vars.ui 都已就绪。
        if(Gdx.app != null){
            Gdx.app.postRunnable(() -> Gdx.app.postRunnable(this::checkUIScaleChanged));
        }

        //开局降落（对应原版 Control 构造器里的 `Events.on(Trigger.newGame, ...)` 监听器）：
        //镜头 zoomIn + 全屏白闪 + 核心着陆特效；落地后再放一圈 launch 特效并震屏。
        //⚠ 本项目 Events.fire 是**同步**的（原版经 Core.app.post 延后一帧）→ 动 UI（stage）与特效池的调用
        //   统一用 Gdx.app.postRunnable 延后一帧，避免在 enterWorld 中途就操作 stage/特效。
        //⚠ 与"最近核心"相关的取值：本项目 Player 没有 getClosestCore()，且 Tile.worldx()/worldy() 是左下角
        //   → 用 state.teams.closestCore(...) 取 Tile，中心用 block.centerX/centerY。
        Events.on(EventType.Trigger.newGame, () -> {
            Tile core = Vars.state == null || Vars.state.teams == null || Vars.player == null ? null
                : Vars.state.teams.closestCore(Vars.player.getX(), Vars.player.getY(), Vars.player.getTeam());
            if(core == null) return;

            if(Gdx.app != null) Gdx.app.postRunnable(() -> { if(Vars.hud != null) Vars.hud.showLand(); });
            if(Vars.renderer != null) Vars.renderer.zoomIn(com.phoenix.game.content.Fx.coreLand.lifetime);

            float cx = core.block().centerX(core), cy = core.block().centerY(core);
            //原版是 Effects.effect(Fx.coreLand, core.x, core.y, 0, core.block)；本项目没有
            //(Effect,float,float,float,Object) 重载 → 用 (Effect,float,float,float) 版本，data 不传
            //（coreLand 是空特效，data 不影响表现）
            if(Gdx.app != null) Gdx.app.postRunnable(() ->
                Effects.effect(com.phoenix.game.content.Fx.coreLand, cx, cy, 0f));

            //落地后：发射特效 + 震屏（对应原版 `Effects.effect(Fx.launch, core)` / `Effects.shake(5f, 5f, core)`；
            //本项目 shake 用 (float,float,float,float) 重载更稳，launch 用显式中心坐标）
            Time.run(com.phoenix.game.content.Fx.coreLand.lifetime, () -> {
                Effects.effect(com.phoenix.game.content.Fx.launch, cx, cy);
                Effects.shake(5f, 5f, cx, cy);
            });
        });
    }

    /**
     * 启动检查：若上次改过 UI 缩放却没点"确定"（{@code uiscalechanged == true}），弹框让玩家确认。
     * 对应原版 {@code Control.init()} 里的 {@code uiscalechanged} 分支。
     * <p>原版流程：{@code FloatingDialog("$confirm")} + 一个**动态**倒计时标签（每帧按 {@code Time.delta()} 递减，
     * 归零就"重置 uiscale = 100 → 存盘 → 退出"），按钮为 {@code $uiscale.cancel}（= 取消并退出）与 {@code $ok}。
     * <p>为什么必须有它：{@code SettingsMenuDialog} 的 uiscale 滑块只写 {@code uiscalechanged} 标记并立刻生效，
     * 若缩放调得过大导致建造菜单超屏，玩家就再也点不到设置按钮 → 只能在启动时靠这个弹框（或手改设置文件）恢复。
     * <p>⚠ 倒计时秒数照抄原版源码的 {@code 60 * 11}（≈11 秒）—— 任务描述里写的"15 秒"与原版源码不符。
     * <p>本项目差异：原版是 arc 的每帧回调 {@code cont.label(() -> ...)}，libgdx 的 Label 是静态文本
     * → 用 {@code MenuFragment.Act}（每帧 run 一次）刷新文本；按钮文字用 {@code I18n.get} 取（本项目 bundle 缺键多，
     * 一律带 fallback）。
     */
    private void checkUIScaleChanged(){
        if(Vars.headless) return;
        if(!Core.settings.getBool("uiscalechanged", false)) return;
        //stage 还没注入时直接放弃（宁可这次不弹，也不要在渲染线程上 NPE）
        if(FloatingDialog.scene == null) return;

        FloatingDialog dialog = new FloatingDialog("$confirm");

        float[] countdown = {60 * 11};
        Runnable exit = () -> {
            Core.settings.put("uiscale", 100);
            Core.settings.put("uiscalechanged", false);
            Core.settings.save();
            dialog.hide();
            Core.app.exit();
        };

        //倒计时标签（原版是 cont.label(Prov) 每帧求值；本项目用 Act 每帧刷新 Label 文本）。
        //⚠ FloatingDialog 的 cont/buttons 是 protected、本包不可见 → 用 libgdx 的 getContentTable()/getButtonTable()
        Label label = new Label("", Styles.defaultLabel);
        dialog.getContentTable().add(label).pad(Scl.scl(10f)).expand().center();
        dialog.getContentTable().addActor(new MenuFragment.Act(() -> {
            if(countdown[0] <= 0f){
                exit.run();
                return;
            }
            label.setText(Strings.format(
                I18n.get("uiscale.reset", "UI scale has been changed.\nPress \"OK\" to confirm this scale."),
                (int)((countdown[0] -= Time.delta()) / 60f)));
        }));

        //按钮：原版 buttons.defaults().size(200f, 60f)（本项目按项目惯例乘 Scl.scl）
        dialog.getButtonTable().defaults().size(Scl.scl(200f), Scl.scl(60f));
        dialog.getButtonTable().add(textButton(I18n.get("uiscale.cancel", "Cancel & Exit"), exit))
            .size(Scl.scl(200f), Scl.scl(60f));
        dialog.getButtonTable().add(textButton(I18n.get("ok", "OK"), () -> {
            Core.settings.put("uiscalechanged", false);
            Core.settings.save();
            dialog.hide();
        })).size(Scl.scl(200f), Scl.scl(60f));

        dialog.show();
    }

    /**
     * 对应 arc 的 {@code Table.addButton(text, action)}。
     * <p>libgdx 的 Table 没有该方法，而 {@code FloatingDialog.textButton} 是 {@code protected}、
     * 本包不可见（{@code UI} 里也是就地实现了一份等价的）→ 这里同样就地实现一份。
     */
    private static TextButton textButton(String text, Runnable action){
        TextButton button = new TextButton(text, Styles.defaultt);
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                action.run();
            }
        });
        return button;
    }

    public boolean isHighScore(){
        return hiscore;
    }

    /**
     * 高分记录的 {@code Core.settings} 键（对应原版 {@code "hiscore" + map.file.nameWithoutExtension()}，
     * 与 {@link com.phoenix.game.maps.Map#getHightScore()} 同源，所以 {@code MapPlayDialog} 显示的记录能对上）。
     * <p>没有 Map 对象时（{@link #play()} 的程序生成图）退化成按 {@code state.mapName} 存键。
     * <p><b>⚠ 合成 Map 的例外</b>：原版 {@code RandomGenerator} 会 {@code world.setMap(new Map(new StringMap()))}
     * —— 一张"无元数据的空 Map"，其 file 名派生为 {@code unknown}。原版这条路径只用于**自造随机图**，
     * 而本项目把大部分区域生成器也做成了 {@code RandomGenerator} 子类（原版是加载内置 .msav 的
     * {@code MapGenerator}，见 {@code Zones.java} 类注释）→ 若照它算键，**所有区域会共用 {@code hiscoreunknown}**，
     * 丢掉区域间的区分度。故空 tags 的合成 Map 视同"没有 Map"，退回 {@code state.mapName}（= 区域名），
     * 得到与原版 {@code MapGenerator} 加载内置图时**同效**的键。
     */
    private String highScoreKey(){
        //原版 Control.java:94 world.getMap().getHightScore()；键格式见 Map.java:66/82 "hiscore" + file.nameWithoutExtension()
        com.phoenix.game.maps.Map map = Vars.world == null ? null : Vars.world.getMap();
        //合成 Map 判据：tags 为空（RandomGenerator 塞的 new Map(new StringMap())；真地图经 Maps.readStats 填过 tags）
        if(map != null && map.tags != null && map.tags.size > 0) return "hiscore" + map.nameWithoutExtension();
        return "hiscore" + (Vars.state.mapName == null ? "campaign" : Vars.state.mapName);
    }

    /** 切换输入处理器（对应原版 Control.setInput）。移动端切到 MobileInput，桌面端切回 DesktopInput。 */
    public void setInput(InputHandler handler){
        if(input != null){
            input.remove();
        }
        input = handler;
        if(input != null){
            input.add();
        }
    }

    /**
     * 加载内容相关初始化。对应原版 Control.loadAsync()，必须在 Core.atlas 就绪后调用一次。
     * 原版的世界贴图是按 4 倍分辨率导出的，scale_marker 的宽度即为缩放基准（4 -> Draw.scl = 0.25），
     * 不设置的话单位等按贴图原始尺寸绘制的对象会大 4 倍。
     */
    public void loadAsync(){
        TextureRegion marker = Core.atlas == null ? null : Core.atlas.findRegion("scale_marker");
        if(marker != null){
            Draw.scl = 1f / marker.getRegionWidth();
        }
    }

    /** 开始战役：生成地图、生成双方单位并进入游戏。 */
    public void play(){
        Vars.world = new World();
        //程序生成图没有 Map 对象（原版这条路径 world.getMap() 也是 null）→ 高分记录按 state.mapName 存键
        Vars.world.setMap(null);
        Vars.world.createMap(mapWidth, mapHeight);

        //队伍与规则必须在放置核心前重置：核心实体初始化时会挂到本队共享库存上
        Vars.state.rules = new Rules();
        Vars.state.teams = new Teams();
        Vars.state.gameOver = false;
        //新局重置本局统计与"发射"标记（对应原版 Logic.reset 里的 state.gameOver = state.launched = false;
        //state.stats = new Stats();）。⚠ 不能放进 enterWorld()：读档路径是先 saves.load()（含 stats）
        //再 enterWorld()，那样会把刚读出来的统计清掉。
        Vars.state.launched = false;
        Vars.state.stats = new com.phoenix.game.game.Stats();

        //波次：默认编成 + 开局宽限（对应原版 Logic.play 的 waveSpacing * 2）
        Vars.state.wave = 1;
        Vars.state.enemies = 0;
        //存档元数据：地图名与游玩时长（新局从零开始计时）
        Vars.state.mapName = "campaign";
        Vars.state.playtime = 0f;
        Vars.state.rules.spawns = new DefaultWaves().get();
        Vars.state.wavetime = Vars.state.rules.waveSpacing * 2f;

        //建造材料返还比例 + 开局材料（对应原版 Logic.play 的 starting items）
        Build.refundMultiplier = Vars.state.rules.deconstructRefundMultiplier;
        Build.costMultiplier = Vars.state.rules.buildCostMultiplier;
        Vars.state.rules.loadout = new ItemStack[]{
            new ItemStack(Items.copper, 200), new ItemStack(Items.lead, 100), new ItemStack(Items.silicon, 60)
        };
        com.phoenix.game.world.modules.ItemModule startItems = Vars.state.teams.items(Team.sharded);
        for(ItemStack stack : Vars.state.rules.loadout){
            startItems.add(stack.item, stack.amount);
        }

        //双方核心：当前为单瓦片实体，Block.size=3 仅用于绘制尺寸；建筑系统完善后再接多格链接。
        //⚠ 间距必须 > 2×rules.enemyCoreBuildRadius（战役默认 400 世界单位 = 50 格），否则敌核禁建圈会
        //罩住玩家自己基地（见 Build.validPlace）。0.25/0.75 → 30/90 格 = 60 格 = 480 世界单位。
        placeCore(mapWidth / 2, (int)(mapHeight * 0.25f), Team.crux);
        placeCore(mapWidth / 2, (int)(mapHeight * 0.75f), Team.sharded);

        //开局放一个太阳能发电机（演示电网起点）。⚠ 必须落在 placeCore 铺出的补偿陆地上（核心 ±3 格内）
        com.phoenix.game.world.Tile solarTile = Vars.world.tile(mapWidth / 2 + 2, (int)(mapHeight * 0.75f));
        if(solarTile != null && solarTile.block() == Blocks.air){
            solarTile.setBlock(Blocks.solarPanel, Team.sharded, 0);
        }

        enterWorld();

        //新战役不绑定已有槽位（独立于自动存档）
        Vars.saves.resetCurrent();

        System.out.println("战役模式：已加载地图 " + mapWidth + "x" + mapHeight);
    }

    /**
     * 进入一个区域（对应原版 {@code Control.playZone(Zone)}）。
     * <p>原版顺序：{@code logic.reset()} → {@code net.reset()} → {@code world.loadGenerator(zone.generator)}
     * → {@code zone.rules.get(state.rules)} → {@code state.rules.zone = zone} → 给玩家核心发
     * {@code zone.getStartingItems()} → {@code state.set(playing)} → {@code wavetime} → {@code saves.zoneSave()}
     * → {@code logic.play()} → {@code Events.fire(Trigger.newGame)}。
     *
     * <p><b>与原版的差异（逐条）</b>
     * <ul>
     *   <li>刻意降级 {@code net.reset()}：本项目 {@code Vars.net} 恒为 null。</li>
     *   <li>{@code saves.zoneSave()} <b>已接线</b>（存档落库批补上：{@link com.phoenix.game.game.Saves} 现在有
     *       区域槽 {@code saves/-1.msav} 与 {@code getZoneSlot()}）。</li>
     *   <li>等价替代 {@code logic.reset()}/{@code logic.play()}：本项目把"清场 + 重建寻路/出生点 + 切 playing
     *       + 重建玩家与相机 + 接管输入"抽到 {@link #enterWorld()}（与 {@link #play()}/{@link #playMap} 共用）。</li>
     *   <li>⚠ <b>刻意新增"放核心"</b>：原版区域图**自带玩家核心**（原版 {@code MapGenerator.generate}
     *       从内置地图里找 {@code CoreBlock}、移除后换成 {@code zone.loadout} 蓝图）；而本项目没有内置地图、
     *       地图是程序生成的（见 {@link World#generateTerrain}），里面**没有核心** → 这里补放一个，
     *       否则玩家没有核心、无法建造也无法收材料。attack 模式下同理补放敌方核心。</li>
     *   <li>{@code Trigger.newGame} <b>已接线</b>：{@link EventType.Trigger} 枚举里有 {@code newGame}，
     *       {@code enterWorld()} 末尾会 fire（{@link EventType.WorldLoadEvent} 仍照发，寻路器/波次生成器/小地图挂在它上面）。</li>
     * </ul>
     */
    public void playZone(com.phoenix.game.type.Zone zone){
        playZone(zone, true);
    }

    /**
     * @param saveProgress 是否把本局写进**区域存档槽**（{@code saves/-1.msav}）。
     *   <p>正常进区域传 {@code true}；{@link #playTutorial()} 传 {@code false} ——
     *   教程复用 {@code groundZero} 的生成器，但它**不是玩家的战役进度**，
     *   若也写区域槽会把玩家真正的 groundZero 存档覆盖掉（"重试教程"不该毁档）。
     */
    public void playZone(com.phoenix.game.type.Zone zone, boolean saveProgress){
        if(zone == null || zone.generator == null) return;

        //1) 用生成器建一张新图（对应原版 world.loadGenerator(zone.generator)）
        Vars.world = new World();
        //新世界的 currentMap 先清空（World 构造即 null，这里显式写一遍）：
        //若区域生成器是 MapGenerator，它会在 generate() 里把区域地图 setMap 回来 → 高分记录就挂在区域地图上（与原版一致）
        Vars.world.setMap(null);
        Vars.world.loadGenerator(zone.generator);

        //2) 队伍与规则必须在放核心前重置：核心实体初始化时会挂到本队共享库存上
        Vars.state.rules = new Rules();
        Vars.state.teams = new Teams();
        Vars.state.gameOver = false;
        Vars.state.launched = false;
        Vars.state.stats = new com.phoenix.game.game.Stats();

        //3) 区域规则就地覆盖默认（对应原版 zone.rules.get(state.rules)），并标记当前区域
        zone.rules.get(Vars.state.rules);
        Vars.state.rules.zone = zone;

        //4) 波次与存档元数据
        Vars.state.wave = 1;
        Vars.state.enemies = 0;
        Vars.state.mapName = zone.name;
        Vars.state.playtime = 0f;
        if(Vars.state.rules.spawns == null) Vars.state.rules.spawns = new DefaultWaves().get();
        Vars.state.wavetime = Vars.state.rules.waveSpacing * 2f;

        //5) 建造倍率
        Build.refundMultiplier = Vars.state.rules.deconstructRefundMultiplier;
        Build.costMultiplier = Vars.state.rules.buildCostMultiplier;

        //6) 放核心（本项目程序生成图没有核心，见方法注释）
        Team playerTeam = Vars.state.rules.defaultTeam;
        placeCore(Vars.world.width() / 2, (int)(Vars.world.height() * 0.4f), playerTeam);
        if(Vars.state.rules.attackMode){
            placeCore(Vars.world.width() / 2, (int)(Vars.world.height() * 0.75f), Vars.state.rules.waveTeam);
        }

        //7) 发区域起始物品到本队共享库存。
        //原版 Control.java:259 是 for(TileEntity core : state.teams.playerCores()){ core.items.add(...) }。
        //本项目 Teams.playerCores() 已存在（S3 批次），但**所有核心共享同一个 ItemModule 引用**
        //（CoreEntity.init 里 this.items = teams.items(...)）→ 加一次即等价，故按共享库存写法保留，不改成遍历。
        com.phoenix.game.world.modules.ItemModule inv = Vars.state.teams.items(playerTeam);
        for(ItemStack stack : zone.getStartingItems()){
            inv.add(stack.item, stack.amount);
        }

        //8) 进入世界（与 play()/playMap() 共用）
        enterWorld();

        //9) 区域进度落盘（对应原版 zoneSave()）：写进固定的区域槽 saves/-1.msav，
        //   meta 里带上 rules.zone，之后就能靠 Saves.getZoneSlot() 找回"继续战役"的进度。
        //   ⚠ 必须在 enterWorld() 之后：SaveSlot.save() 内部会 refreshMeta()，而 meta 里的 zoneName
        //     只有在写盘时才由 rules.zone 生成 —— 顺序反了 getZoneSlot() 会恒为 null。
        if(saveProgress) Vars.saves.zoneSave();

        System.out.println("区域模式：已进入 " + zone.name + "（" + Vars.world.width() + "x" + Vars.world.height() + "）");
    }

    /**
     * 开始教程（对应原版 {@code Control.playTutorial}，原版见 Mindustry-126.2 Control.java:272）。
     * <p>原版顺序：{@code Zones.groundZero} → {@code logic.reset()} → {@code net.reset()} →
     * {@code world.beginMapLoad/createTiles/generate} → 找 {@code CoreBlock} → 清掉核心 10 格内同队非核心方块、
     * 5 格内 {@code clearOverlay} → {@code zone.rules.get} → 发 startingItems → {@code core.items.clear()} →
     * {@code logic.play()} → {@code rules.waveTimer = false / waveSpacing = 60*30 / buildCostMultiplier = 0.3f /
     * tutorial = true}。
     *
     * <p><b>本项目的等价实现</b>：直接复用已接线的 {@link #playZone(com.phoenix.game.type.Zone)}
     * （它已做 {@code world.loadGenerator} + 规则初始化 + 放核心 + {@code enterWorld}），再补教程专属处理：
     * <ul>
     *   <li>原版 {@code world.createTiles/generate/endMapLoad} 三连 → 本项目 {@code World.loadGenerator} 一次完成。</li>
     *   <li>原版"找 CoreBlock 再清周围方块"：本项目地图是程序生成的、<b>没有</b>预置核心与蓝图 →
     *       由 {@link #playZone} 补放的玩家核心代替，无需清场（{@code Geometry.circle} 本项目也未移植）。</li>
     *   <li>原版 {@code ui.loadAnd(...)}（异步遮罩）本项目没有 → 同步执行（与 {@code play()}/{@code playZone} 一致）。</li>
     *   <li>{@code net.reset()} 刻意降级（本项目 {@code Vars.net} 恒 null）。</li>
     *   <li>⚠ 与 {@link #playZone} 共用的副作用：会写区域槽 {@code saves/-1.msav}（原版 playTutorial 不写）。</li>
     * </ul>
     */
    public void playTutorial(){
        //1) 复用区域链路进入 groundZero（生成图 + 区域规则 + 放核心 + 进世界）
        //   ⚠ 传 false：教程不是玩家的战役进度，别覆盖区域存档槽（见 playZone(Zone, boolean)）
        com.phoenix.game.type.Zone zone = com.phoenix.game.content.Zones.groundZero;
        if(zone == null || zone.generator == null) return;
        playZone(zone, false);

        if(Vars.state == null || Vars.state.rules == null) return;

        //2) 教程专属规则（对应原版 playTutorial 尾部）。ClientLauncher 每帧读 rules.tutorial，
        //   所以放在 enterWorld（playZone 内）之后设置同样生效。
        Vars.state.rules.tutorial = true;
        Vars.state.rules.waveTimer = false;
        Vars.state.rules.waveSpacing = 60f * 30f;
        Vars.state.rules.buildCostMultiplier = 0.3f;
        //建造花费随 rules 走（playZone 已按旧值设过 Build.costMultiplier，这里按新值补一次）
        Build.costMultiplier = Vars.state.rules.buildCostMultiplier;

        //3) 清空核心库存（原版 core.items.clear()）：教程从零材料开始，让玩家按引导去挖铜。
        //   本项目核心库存 = 队伍共享库存
        if(Vars.state.teams != null){
            Vars.state.teams.items(Vars.state.rules.defaultTeam).clear();
        }

        //4) 教程状态机复位到第一阶段（原版靠 logic.reset() 的 ResetEvent → tutorial.reset()）
        if(Vars.control != null && Vars.control.tutorial != null){
            Vars.control.tutorial.reset();
        }
    }

    /**
     * 用一张地图 + 一套规则开局（对应原版 {@code Control.playMap(Map, Rules)}）。
     * <p>原版语义：{@code logic.reset()} → {@code world.loadMap(map, rules)} → {@code state.rules = rules}
     * → {@code logic.play()}（进入 playing + 发开局材料）→ {@code Events.fire(Trigger.newGame)}。
     * 本项目把"清场 + 重建寻路/出生点 + 切 playing + 重建玩家与相机 + 接管输入"抽到
     * {@link #enterWorld()}（与 {@link #play()} / {@link #load} 共用），这里只负责"加载地图 + 套规则"。
     *
     * <p><b>与原版的差异（刻意降级 / 等价替代）</b>
     * <ul>
     *   <li>⚠ 原版 {@code state.rules.zone = null} / {@code state.rules.editor = false}：本批已回填
     *       （{@link Rules} 现在有 {@code zone}/{@code editor} 字段，见方法体第 2 步）。</li>
     *   <li>{@code Events.fire(Trigger.newGame)} <b>已接线</b>：{@link EventType.Trigger} 枚举里本就有
     *       {@code newGame}（原注释说"本项目没有 Trigger 事件类"已过时）。现在由 {@code enterWorld()}
     *       在玩家单位创建之后 fire，监听方在 {@link Control} 构造器里（镜头降落动画 + 核心着陆特效）；
     *       {@link EventType.WorldLoadEvent} 仍照发，寻路器/波次生成器/小地图都挂在它上面。</li>
     *   <li>刻意降级 {@code settings.getBool("savecreate") && !world.isInvalidMap()} 时自动新建存档：
     *       本项目 {@link #play()} 的口径是"新局不绑定已有槽位"（{@code Vars.saves.resetCurrent()}），
     *       这里保持一致，不自动建槽（{@code World.isInvalidMap()} 亦不存在）。</li>
     *   <li>等价替代 原版 {@code world.loadMap(map, rules)} 会把 rules 直接喂给地图加载器；本项目
     *       {@link com.phoenix.game.maps.Maps#loadMap} 走 {@code SaveIO.load}，规则来自地图文件里的 meta，
     *       所以这里在加载**之后**用传入的 rules 覆盖（顺序等价，结果一致）。</li>
     *   <li>等价替代 原版 {@code logic.play()} 会 {@code entity.items.clear()} 后按 {@code rules.loadout}
     *       发开局材料；本项目核心库存 = 队伍共享库存（{@link com.phoenix.game.game.Teams#items}）→
     *       按同样语义对每个有核心的队伍 clear 后发放。</li>
     *   <li>刻意降级 原版把整段包在 {@code ui.loadAnd(...)}（异步加载遮罩）里；本项目 {@link #play()} /
     *       {@link #load} 都是同步的，这里保持一致（地图加载失败时直接弹错误框，见下）。</li>
     * </ul>
     *
     * @param map   要开局的地图（来自 {@code Vars.maps.all()}）
     * @param rules 模式规则（{@code Map#applyRules(Gamemode)} 的产物）
     */
    public void playMap(com.phoenix.game.maps.Map map, Rules rules){
        if(map == null || rules == null) return;

        //1) 加载地图：SaveIO 会重建 World/Teams，并套上地图 meta 里的 rules
        //   加载失败时 Vars.world 还是上一张图 → 直接放弃开局（避免"开局了但地图是旧的"）
        if(!Vars.maps.loadMap(map)){
            //说明：原版此处用的 bundle 键 "map.loaderror" 在**原版 bundle 里也不存在**
            //（grep Mindustry-126.2/core/assets/bundles/bundle.properties 无命中）→ 本项目直接用
            //字面量英文提示，与"本项目缺键"无关。
            if(Vars.ui != null) Vars.ui.showErrorMessage("Failed to load map (see log).");
            return;
        }
        if(Vars.world == null) return;

        //高分记录挂在当前地图上（对应原版 world.getMap()）。
        //原版 World.loadMap(Map) 里就设了 currentMap（原版 World.java:231）；本项目 World 由 SaveIO 内部重建，
        //所以由调用方在加载成功后补设
        Vars.world.setMap(map);

        //2) 用调用方给的规则覆盖（对应原版 world.loadMap(map, rules) 之后的 state.rules = rules）
        Vars.state.rules = rules;
        //对应原版 playMap 里的 state.rules.zone = null; state.rules.editor = false;
        //（自定义地图/编辑器开局必须清掉上一局的区域与编辑器标记，否则 World.isZone() 会残留为 true）
        rules.zone = null;
        rules.editor = false;
        //新局重置本局统计与"发射"标记（理由同 play()）
        Vars.state.launched = false;
        Vars.state.stats = new com.phoenix.game.game.Stats();

        //3) 波次编成：地图 rules 可能没带 spawns（本项目 rules() 只在为空时填默认波次，这里再兜一次）
        if(rules.spawns == null || rules.spawns.size == 0){
            rules.spawns = new DefaultWaves().get();
        }
        //建造返还比例随 rules 走（与 play()/load() 同口径）
        Build.refundMultiplier = rules.deconstructRefundMultiplier;
        Build.costMultiplier = rules.buildCostMultiplier;

        //4) 开局波次/计时（对应原版 logic.reset() 的 wave=1 + logic.play() 的 wavetime = waveSpacing*2）
        Vars.state.wave = 1;
        Vars.state.enemies = 0;
        Vars.state.wavetime = rules.waveSpacing * 2f;
        Vars.state.mapName = map.name;
        Vars.state.playtime = 0f;

        //5) 开局材料（对应原版 logic.play() 的 loadout 发放）
        //   本项目 Rules.loadout 默认是空数组（原版默认 copper x100）→ 空时补上原版默认值（已在代码里落地），
        //   否则自定义地图开局没有任何材料、什么都造不出来
        if(rules.loadout == null || rules.loadout.length == 0){
            rules.loadout = new ItemStack[]{new ItemStack(Items.copper, 100)};
        }
        for(Team team : Team.base()){
            if(Vars.state.teams.closestCore(0f, 0f, team) == null) continue;
            com.phoenix.game.world.modules.ItemModule inv = Vars.state.teams.items(team);
            inv.clear();
            for(ItemStack stack : rules.loadout){
                inv.add(stack.item, stack.amount);
            }
        }

        //6) 进入世界（与 play()/load() 共用）
        enterWorld();

        //新局不绑定已有槽位（与 play() 同口径）
        Vars.saves.resetCurrent();

        System.out.println("已开局地图 " + map.name + "（" + Vars.world.width() + "x" + Vars.world.height() + "）");
    }

    /**
     * 进入世界的公共收尾（世界/队伍/规则都已就绪后调用）：
     * 清上一局残留 → 重建寻路/出生点 → 切 playing → 重建玩家与相机 → 接管输入。
     * <p>由 {@link #play()} / {@link #load} / {@link #playMap} 共用，
     * 对应原版 {@code logic.reset()} 里的"清场" + {@code logic.play()} 里的"进入 playing + 重建玩家"。
     * <p>顺序说明：{@code WorldLoadEvent} 的监听者（寻路器 / 波次生成器 / 小地图 / 方块索引）
     * 都不读 {@code state}，所以"先发事件再切 playing"与 {@link #play()} 原来的顺序等价。
     * <p>⚠ <b>读档路径必须传 {@code false}</b>（见 {@link #enterWorld(boolean)}）。
     */
    private void enterWorld(){
        enterWorld(true);
    }

    /**
     * @param clearUnits 是否清空 {@link Units#units}。
     *   <p><b>为什么需要这个开关</b>：原版顺序是 {@code logic.reset()}（清场）→ 读档 → {@code logic.play()}，
     *   即**先清后读**；而本项目 {@link #load} 是**先读后清** —— {@code SaveIO.load} 刚把单位重建好，
     *   这里一句 {@code Units.units.clear()} 就把它们全清了，读档后只剩玩家单位。
     *   → 读档路径传 {@code false}；{@link #play()}/{@link #playMap}/{@link #playZone} 仍传 {@code true}。
     *   <p>其余清场（子弹/火焰/水洼/实体组/特效/延迟任务）在读档后**仍然要清**：它们本来就不入档，
     *   留着就是上一局的残留。（{@code Vars.entities.clear()} 只清 EntityGroup 容器，不影响
     *   {@code Units.units} —— {@code Units.add} 只往 {@code Units.units} 里加，见 Units.java。）
     */
    private void enterWorld(boolean clearUnits){
        Vars.state.gameOver = false;
        //"新纪录"标记随本局清空（对应原版 ResetEvent 监听器里的 hiscore = false）。
        //放在这里而不是 play()/playMap()：读档进来也算新的一局。
        hiscore = false;

        if(clearUnits){
            Units.units.clear();
        }
        Bullet.all.clear();
        com.phoenix.game.entities.effect.Fire.clear();
        com.phoenix.game.entities.effect.Puddle.clear();
        //残骸贴花不入档，随上一局一起清掉（对应原版 Logic.reset 里的 groundEffectGroup.clear()）
        com.phoenix.game.entities.effect.Decal.clear();
        //实体组同步清空（解绑组内成员 + 清延迟队列）：对应原版 Logic.reset() 里的 entities.clear()
        if(Vars.entities != null) Vars.entities.clear();
        Effects.clear();
        Time.clear(); //清掉上一局残留的延迟任务（武器连发等）

        //世界加载事件：触发寻路器重建网格、波次生成器扫描出生点
        Events.fire(new EventType.WorldLoadEvent());
        if(Vars.world != null){
            Vars.world.rebuildPowerGraphs();
        }

        Vars.state.set(GameState.State.playing);

        //玩家：原版在地核处出生并接管一个单位（本项目简化为控制一个单位；默认 dart-ship = 原版出生机甲 Mechs.starter）
        Vars.player = new Player();
        //持久化 uuid（对应原版 Vars.platform.getUUID()）：玩家列表快照靠它识别每个玩家，
        //没有它主机自己在客户端列表里那条会被当成"匿名"而丢掉
        Vars.player.uuid = Platform.uuid();
        //原版 Control.createPlayer()：player.name = settings.getString("name"); player.color.set(settings.getInt("color-0"))
        //  —— 本项目原先漏了这两行，导致开服界面选的名字/颜色只落在设置里，
        //     进游戏后玩家名恒为 "noname"、颜色恒为白（玩家列表快照 sync.colors 也随之全白）。
        Vars.player.name = Core.settings.getString("name", Vars.player.name);
        Vars.player.color.set(Core.settings.getInt("color-0",
            com.badlogic.gdx.graphics.Color.rgba8888(Vars.playerColors[8])));
        Vars.player.isLocal = true; //本地玩家（玩家列表据此区分本地/远程，对应原版 Player.isLocal）
        Vars.player.add(); //登记进 playerGroup（对应原版 Player 创建后的 add()）
        resetCamera();
        //玩家单位用 rules.defaultTeam：读档路径下 applyMeta 已先恢复该字段（存档里存了阵营 id），
        //而 play()/playMap() 走的是 new Rules()（默认 sharded）→ 两边行为都与原来一致。
        Team playerTeam = Vars.state.rules == null || Vars.state.rules.defaultTeam == null
            ? Team.sharded : Vars.state.rules.defaultTeam;
        spawnPlayerUnit(playerTeam);

        //切换到游戏输入（对应原版 control.input 接管输入）
        if(input != null){
            input.use();
        }

        //开局事件（对应原版 `logic.play()` 之后的 `Events.fire(Trigger.newGame)`）。
        //⚠ 必须放在**玩家单位已创建之后**：监听器要读 Vars.player 的位置来定位最近核心
        //  （原版顺序也是 logic.play() 里建好玩家才 fire）。若紧跟 WorldLoadEvent 发，Vars.player
        //  还是上一局残留/首次启动时为 null → 降落动画会定位错核心或整个不触发。
        Events.fire(EventType.Trigger.newGame);
    }

    /** 把当前局保存到当前槽（无槽则自动新建）。 */
    public void save(){
        try{
            Vars.saves.saveCurrent();
            System.out.println("已存档 -> " + (Vars.saves.getCurrent() == null ? "?" : Vars.saves.getCurrent().file.getName()));
        }catch(Exception e){
            System.err.println("存档失败: " + e);
        }
    }

    /**
     * 从指定槽读档并接管战场。
     * 读档会整体替换世界/状态/单位，随后重建寻路、出生点与玩家。
     */
    public void load(com.phoenix.game.game.Saves.SaveSlot slot){
        try{
            //走 Saves.load 而不是 slot.load：前者还会把该槽设为"当前槽"，
            //否则读档后自动存档仍然写回上一局的槽位
            Vars.saves.load(slot);
            Vars.state.rules.spawns = new DefaultWaves().get();
            Build.refundMultiplier = Vars.state.rules.deconstructRefundMultiplier;
            Build.costMultiplier = Vars.state.rules.buildCostMultiplier;

            //读档后按地图名找回 Map（高分记录要挂在它上面，与 MapPlayDialog 显示的那条同源）；
            //程序生成图（mapName="campaign"）在 maps 列表里找不到 → 保持 null，退化成按地图名存键
            //⚠ 必须在 Vars.saves.load(slot) 之后：那个调用内部会重建 World（SaveIO.load）
            if(Vars.world != null) Vars.world.setMap(Vars.maps == null ? null : Vars.maps.byName(Vars.state.mapName));

            //清场 → 重建寻路/出生点 → 切 playing → 重建玩家与相机 → 接管输入（与 play()/playMap 共用）
            //（上一局若已判定胜负（gameOver=true）会残留在状态里，enterWorld 里会清掉，
            //  否则 Logic 不会刷波次也不判胜负，读档进来的这一局等于卡死）
            //⚠ 必须传 false：SaveIO.load 刚把单位重建好，enterWorld(true) 会把它们全清掉
            //  （原版是"先 reset 再读档"，本项目是"先读档再 enterWorld"，所以这里要跳过清单位）
            enterWorld(false);

            //读档恢复玩家背包（对应原版 Player.read 里的 Unit.readSave；本项目玩家单位不入档，单独存/取）：
            //SaveIO.read 已把它暂存在静态 pending 字段（读档发生在 enterWorld 重建玩家之前）
            if(Vars.player != null){
                com.phoenix.game.type.Item pitem = com.phoenix.game.io.SaveIO.pendingPlayerItem();
                if(pitem != null){
                    Vars.player.addItem(pitem, com.phoenix.game.io.SaveIO.pendingPlayerAmount());
                }
            }
            com.phoenix.game.io.SaveIO.clearPendingPlayer();

            System.out.println("已读档：第 " + Vars.state.wave + " 波");
        }catch(Exception e){
            //原版 `LoadDialog.runLoadSave` 的 `catch(SaveException e)`：`Log.err` → `state.set(menu)` →
            //`logic.reset()` → `ui.showErrorMessage("$save.corrupted")`。本项目**没有 `SaveException`**
            //（B 缺依赖，见 `LoadDialog` 类注释）→ 用宽 catch；`Logic` 是纯静态类、没有 `reset()`，
            //改用现成的 {@link #menu()}（它做的就是"回菜单 + 清特效/单位/子弹/玩家"）。
            //⚠ 这一点正好覆盖 **FS9c「旧存档不兼容」**：`$save.corrupted` 的文案本身就写着
            //"如果你刚刚升级了游戏，那么这可能是因为存档格式改变了，而不是 bug"，
            //而本项目 `SaveIO` 对版本不符是**硬拒**（`ver != version` 直接抛）→ 旧档（v3/v4）读入必失败，
            //用户会看到这条提示而不是"点了没反应"。
            System.err.println("读档失败: " + e);
            menu();
            if(Vars.ui != null) Vars.ui.showErrorMessage("$save.corrupted");
        }
    }

    /**
     * 在我方核心旁生成玩家控制的单位（对应原版玩家在地核重生）。
     * 开局与死亡重生都走这里；相机立即对准玩家，避免镜头飞过去。
     */
    public void spawnPlayerUnit(Team team){
        if(Vars.world == null) return;

        Tile core = Vars.state.teams.closestCore(Vars.world.unitWidth() / 2f, Vars.world.unitHeight() / 2f, team);
        //出生点放在核心靠己方一侧（敌核在地图另一端，避免一出生就进敌方火力范围）
        float x = core != null ? core.getX() : Vars.world.unitWidth() * 0.5f;
        float y = core != null ? core.getY() + 32f : Vars.world.unitHeight() * 0.5f;

        //用玩家当前选择的单位类型重生（默认 dart-ship = 原版出生机甲 Mechs.starter）
        com.phoenix.game.type.UnitType type = Vars.player != null && Vars.player.selectedType != null
            ? Vars.player.selectedType : UnitTypes.dartShip;

        spawnPlayerUnitAt(team, type, x, y);
    }

    /**
     * 在指定位置用指定单位类型重生玩家（机甲平台 MechPad 也走这里）。
     * <p>同时把该类型记到 {@code player.selectedType}，这样后续死亡重生会沿用同一型号。
     * <p>换装/重生前会先清掉玩家当前单位：否则旧单位仍作为独立单位存活（小地图上多一个玩家名标签、
     * 被敌方当成可打目标、又不再受控制 —— 即"幽灵机"）。这里只置死（{@code setDead(true)}）：
     * 单位下一次 {@code update()} 会自己走 {@code remove()} 退场，与正常死亡的收场路径一致，
     * 且不触发死亡爆炸（换装不该炸伤平台周围的建筑）。
     */
    public BaseUnit spawnPlayerUnitAt(Team team, com.phoenix.game.type.UnitType type, float x, float y){
        if(Vars.world == null || type == null) return null;

        //必须在创建/接管新单位**之前**清旧单位（此时 player.unit() 还是旧的那个，不会误杀新单位）
        if(Vars.player != null && Vars.player.unit() != null){
            Vars.player.unit().setDead(true);
        }

        BaseUnit unit = type.create();
        unit.setTeam(team);
        unit.set(x, y);
        unit.health(unit.maxHealth());
        unit.isPlayer = true;

        if(Vars.player != null){
            Vars.player.team = team;
            Vars.player.selectedType = type;
            Vars.player.unit(unit);
        }

        if(Core.camera != null){
            Core.camera.position.set(x, y, 0f);
            Core.camera.update();
        }
        return unit;
    }

    /** 退出到菜单。 */
    public void menu(){
        Vars.state.set(GameState.State.menu);
        Effects.clear();
        //玩家只在战役中存在（先从 playerGroup 解绑）
        if(Vars.player != null){
            Vars.player.remove();
            Vars.player = null;
        }
        //清理战场残留（单位/子弹/建筑实体）
        Units.units.clear();
        Bullet.all.clear();
        com.phoenix.game.entities.effect.Fire.clear();
        com.phoenix.game.entities.effect.Puddle.clear();
        com.phoenix.game.entities.effect.Decal.clear();
        if(Vars.entities != null) Vars.entities.clear();
        //重置事件：停掉寻路后台线程
        Events.fire(new EventType.ResetEvent());
        Time.clear();
    }

    /** 在指定瓦片放置核心（带队营/旋转）。 */
    private void placeCore(int x, int y, Team team){
        //核心周围铺一小片陆地：本项目的战役图是**程序生成**的（纯噪声地形），核心有概率落在深水上，
        //那样玩家在核心旁什么都建不了（原版由地图作者保证 spawn 点位于陆地，本项目没有作者）。
        //把核心 3×3 外扩两圈（共 7×7）的深水地板换成 stone；不动自然岩壁（可拆，保留更像原版）。
        for(int dx = -3; dx <= 3; dx++){
            for(int dy = -3; dy <= 3; dy++){
                Tile t = Vars.world.tile(x + dx, y + dy);
                if(t == null) continue;
                com.phoenix.game.world.Floor f = t.floor();
                if(f != null && f.isLiquid && f.drownTime > 0f){
                    t.setFloor(Blocks.stone);
                }
            }
        }

        Tile tile = Vars.world.tile(x, y);
        if(tile == null) return;
        tile.setBlock(Blocks.core, team, 0);
    }

    /**
     * 重置相机。原版在进入世界后把相机放到玩家（或其核心）位置，缩放保持 Renderer 的默认值；
     * 这里没有玩家单位，就放在我方出生点附近。
     */
    private void resetCamera(){
        if(Core.camera == null) return;

        Core.camera.position.set(Vars.world.unitWidth() * 0.42f, Vars.world.unitHeight() * 0.5f, 0f);

        if(Vars.renderer != null){
            Vars.renderer.clampScale();
            Vars.renderer.update();
        }

        Core.camera.update();
    }
}
