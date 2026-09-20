package com.phoenix.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGeneratorLoader;
import com.badlogic.gdx.graphics.g2d.freetype.FreetypeFontLoader;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.content.UnitTypes;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.core.Constants;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Fonts;
import com.phoenix.game.core.Logic;
import com.phoenix.game.core.Renderer;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Time;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.ui.fragments.ChatFragment;
import com.phoenix.game.ui.fragments.HudFragment;
import com.phoenix.game.ui.fragments.MenuFragment;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

import java.util.Locale;

/**
 * 应用入口适配器（对应 Mindustry 的 mindustry.ClientLauncher）。
 * create by GYH on 2024/7/30
 */
public class ClientLauncher extends ApplicationAdapter {
    private Stage stage;
    private boolean finished = false;
    private MenuFragment menufrag;
    private HudFragment hudfrag;
    /** 联机聊天界面（Enter 呼出）。 */
    private ChatFragment chatfrag;
    /** 聊天回调是否已挂接（NetClient 惰性创建后挂一次）。 */
    private boolean chatHooked;
    /** 世界渲染器（战役模式使用） */
    public final Renderer renderer = new Renderer();

    OrthographicCamera camera;
    Viewport viewport;
    /** HUD（Scene2D）专用 viewport，与世界相机分开，避免随世界缩放放大 */
    Viewport stageViewport;
    SpriteBatch batch;
    @Override
    public void create() {
        FileHandle handle = Gdx.files.internal("i18n/bundle");
        Locale locale = Locale.getDefault();
        Locale.setDefault(locale);
        Core.bundle = I18NBundle.createBundle(handle, locale);

        Core.assets = new AssetManager();
        FileHandleResolver resolver = new InternalFileHandleResolver();
        Core.assets.setLoader(FreeTypeFontGenerator.class, new FreeTypeFontGeneratorLoader(resolver));
        Core.assets.setLoader(BitmapFont.class, null, new FreetypeFontLoader(resolver) {
            @Override
            public BitmapFont loadSync(AssetManager manager, String fileName, FileHandle file, FreeTypeFontLoaderParameter parameter) {
                if (fileName.equals("outline")) {
                    parameter.fontParameters.borderWidth = Scl.scl(2f);
                    parameter.fontParameters.spaceX -= parameter.fontParameters.borderWidth;
                }
                parameter.fontParameters.magFilter = Texture.TextureFilter.Linear;
                parameter.fontParameters.minFilter = Texture.TextureFilter.Linear;
                parameter.fontParameters.size = fontParameter().size;
                return super.loadSync(manager, fileName, file, parameter);
            }
        });

        Core.camera = camera = new OrthographicCamera();
        viewport = new ScreenViewport(camera);
        Core.batch = batch = new SpriteBatch();

        //HUD 必须用独立相机：Stage 若复用世界相机，滚轮缩放世界时 HUD 会跟着一起放大
        stageViewport = new ScreenViewport(new OrthographicCamera());
        stage = new Stage(stageViewport, batch);
        Table table = new Table();
        table.setFillParent(true);
        stage.addActor(table);

        table.setDebug(false);
        menufrag = new MenuFragment();

        if (!finished) {
            finished = true;
            Core.app = Gdx.app;
            Core.graphics = Gdx.graphics;
            Core.files = Gdx.files;
            Core.atlas = new TextureAtlas("sprites/sprites.atlas");
            Core.skin = new Skin(Core.atlas);
            //加载内容（需要在 atlas 就绪之后）
            Blocks.load();
            Vars.control.loadAsync();
            Vars.renderer = renderer;
            new UnitTypes().load();
//            Musics.load();
//            Sounds.load();
//            Musics.update();
//            Sounds.update();
            Tex.load();
            Icon.load();
            setupFonts();
            Vars.init();
            Core.assets.finishLoading();
            Fonts.outline.getData().markupEnabled = true;
            Fonts.def.getData().markupEnabled = true;
            Fonts.def.setOwnsTexture(false);
            Styles.load();
            menufrag.build(table);
            hudfrag = new HudFragment();
            Vars.hud = hudfrag;
            hudfrag.build(stage.getRoot());
            chatfrag = new ChatFragment();
            chatfrag.build(stage.getRoot());
        }

        //输入路由：Stage（菜单 + HUD 按钮）优先，其次游戏输入处理器（相机/放置）
        Gdx.input.setInputProcessor(new InputMultiplexer(stage, Vars.control.input));

        //调试用：设置环境变量 PHOENIX_AUTOPLAY=1 可跳过菜单直接进入战役模式
        String joinTarget = System.getenv("PHOENIX_JOIN");
        if(System.getenv("PHOENIX_HOST") != null){
            //调试用：PHOENIX_HOST=1 直接开服务器并进入战役（供自动化联机验证）
            if(Vars.netServer == null) Vars.netServer = new com.phoenix.game.core.NetServer();
            Vars.netServer.init();
            Vars.netServer.openServer(Vars.port);
            menufrag.hide();
            Vars.control.play();
        }else if(System.getenv("PHOENIX_AUTOPLAY") != null){
            menufrag.hide();
            Vars.control.play();
        }else if(joinTarget != null && !joinTarget.trim().isEmpty()){
            //调试用：PHOENIX_JOIN=ip[:port] 跳过菜单直接加入服务器（供自动化联机验证）
            String target = joinTarget.trim();
            int sep = target.lastIndexOf(':');
            String ip = sep > 0 ? target.substring(0, sep) : target;
            int port = Vars.port;
            if(sep > 0){
                try{
                    port = Integer.parseInt(target.substring(sep + 1).trim());
                }catch(NumberFormatException e){
                    System.out.println("[客户端] PHOENIX_JOIN 端口无效，回退到默认端口 " + port);
                }
            }
            menufrag.joinServer(ip, port, "player");
        }
    }

    public void setupFonts() {
        String fontName = "fonts/font.ttf";

        FreeTypeFontGenerator.FreeTypeFontParameter param = fontParameter();
        Core.assets.load("chat", BitmapFont.class, new FreeTypeFontLoaderParameter(fontName, param)
                .setLoadedCallback((assetManager, fileName, type) -> Fonts.chat = assetManager.get(fileName)));
        Core.assets.load("default", BitmapFont.class, new FreeTypeFontLoaderParameter(fontName, param)
                .setLoadedCallback((assetManager, fileName, type) -> Fonts.def = assetManager.get(fileName)));
        FreeTypeFontGenerator.FreeTypeFontParameter fontParameters = new FreeTypeFontGenerator.FreeTypeFontParameter() {{
            borderColor = Color.DARK_GRAY;
            incremental = true;
        }};
        Core.assets.load("outline", BitmapFont.class, new FreeTypeFontLoaderParameter(fontName, fontParameters)
                .setLoadedCallback((assetManager, fileName, type) -> Fonts.outline = assetManager.get(fileName)));

    }

    public static class FreeTypeFontLoaderParameter extends FreetypeFontLoader.FreeTypeFontLoaderParameter {

        public FreeTypeFontLoaderParameter() {
        }

        public FreeTypeFontLoaderParameter(String fontFileName, FreeTypeFontGenerator.FreeTypeFontParameter fontParameters) {
            this.fontFileName = fontFileName;
            this.fontParameters = fontParameters;
        }

        public FreeTypeFontLoaderParameter setLoadedCallback(LoadedCallback loadedCallback) {
            this.loadedCallback = loadedCallback;
            return this;
        }
    }

    static FreeTypeFontGenerator.FreeTypeFontParameter fontParameter() {
        return new FreeTypeFontGenerator.FreeTypeFontParameter() {{
            size = (int) (Scl.scl(18f));
            shadowColor = Color.DARK_GRAY;
            shadowOffsetY = 2;
            incremental = true;
        }};
    }

    //DEBUG-SHOT-START
    /** 抓帧计数（PHOENIX_SHOT = 第几帧抓）。 */
    private int shotFrames = 1;
    //DEBUG-SHOT-END
    //DEBUG-LOGTEST-START
    /** 物流链路自动化验证的帧计数 / 结束帧 / 模式 / 是否已搭建 / 链路瓦片 / 容器瓦片。 */
    private int logTestFrame, logTestEnd = 400, logTestMode = 1;
    private boolean logTestBuilt;
    private com.phoenix.game.world.Tile[] logTestTiles;
    private com.phoenix.game.world.Tile[] logTestStores;
    /** 模式 19 用：RTS 命令验证生成的友军单位。 */
    private final com.badlogic.gdx.utils.Array<com.phoenix.game.entities.type.BaseUnit> rtsTestUnits = new com.badlogic.gdx.utils.Array<>();
    /** 模式 18 用：待粘贴的蓝图。 */
    private com.phoenix.game.game.Schematic logTestSchematic;
    private int[] logTestStoreBase;
    /** 模式 23 用：存档往返测试的临时文件与"存档前"状态指纹。 */
    private java.io.File logTestSaveFile;
    private String logTestFingerprint;
    /** 模式 26 用：Fx 里所有特效（反射收集），逐个在网格上重放。 */
    private java.util.List<com.phoenix.game.entities.Effects.Effect> galleryEffects;
    private java.util.List<String> galleryNames;
    /** 模式 28 用：站在火里验证燃烧状态的单位。 */
    private com.phoenix.game.entities.type.BaseUnit logTestFireUnit;
    /** 看门狗线程（死循环定位用）。 */
    private Thread watchdog;
    //DEBUG-LOGTEST-END

    @Override
    public void render() {
        //服务端（主机）模式：每帧 drain 入站包 + 周期性同步单位/建筑状态。
        //必须无条件驱动 —— 之前漏了这一句，主机根本收不到任何入站包（表现为客户端握手超时）。
        if(Vars.netServer != null && Vars.netServer.isHosting()){
            Vars.netServer.update();
        }

        //联机模式：客户端网络管线每帧驱动（drain 入站 + 上报输入 + 心跳/自动重连）
        //注意：必须无条件调用 update()——未连接时的自动重连逻辑就在它内部，
        //若用 isConnected() 守卫会导致断线后再也进不来（重连永远不触发）。
        if(Vars.netClient != null){
            //惰性挂接回调（NetClient 由 MenuFragment 创建）
            if(!chatHooked && chatfrag != null){
                chatHooked = true;
                Vars.netClient.onChat = (name, msg) -> chatfrag.addMessage(name, msg);
                Vars.netClient.onKick = reason -> {
                    chatfrag.addSystem("被踢出: " + reason);
                    Vars.control.menu();
                    menufrag.show();
                };
                Vars.netClient.onDisconnect = reason -> {
                    chatfrag.addSystem("连接断开: " + reason);
                    Vars.control.menu();
                    menufrag.show();
                };
            }
            Vars.netClient.update();
            if(Vars.netClient.isConnected() && chatfrag != null) chatfrag.update();
        }

        //战役模式：输入 -> 逻辑 -> 渲染（暂停时只渲染不更新，与 Mindustry 一致）
        if(!Vars.state.isMenu()){
            if(Vars.control.input != null) Vars.control.input.update();

            if(Vars.state.isPlaying()) Logic.update();
            if(Vars.renderer != null) Vars.renderer.update();




            ScreenUtils.clear(0.09f, 0.10f, 0.12f, 1f);
            renderer.draw();

            //绘制游戏内 HUD（Scene2D 叠加在世界之上）
            if(hudfrag != null){
                hudfrag.show();
                stage.act(Gdx.graphics.getDeltaTime());
                stage.draw();
            }

            //DEBUG-LOGTEST-START
            if(System.getenv("PHOENIX_LOGTEST") != null) debugLogTest();
            //DEBUG-LOGTEST-END

            //DEBUG-SHOT-START
            String shot = System.getenv("PHOENIX_SHOT");
            if(shot != null && shotFrames++ == Integer.parseInt(shot)){
                int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
                com.badlogic.gdx.graphics.Pixmap src = com.badlogic.gdx.graphics.Pixmap.createFromFrameBuffer(0, 0, w, h);
                //帧缓冲是上下翻转的：这里直接翻正再存，省掉外部后处理（Add-Type 之类在受限环境下不可用）
                com.badlogic.gdx.graphics.Pixmap dst = new com.badlogic.gdx.graphics.Pixmap(w, h, src.getFormat());
                java.nio.ByteBuffer sb = src.getPixels(), db = dst.getPixels();
                int stride = w * 4;
                byte[] row = new byte[stride];
                for(int y = 0; y < h; y++){
                    sb.position(y * stride);
                    sb.get(row);
                    db.position((h - 1 - y) * stride);
                    db.put(row);
                }
                com.badlogic.gdx.graphics.PixmapIO.writePNG(
                    Gdx.files.absolute("d:/IdeaProjects/phoenix/shot.png"), dst);
                src.dispose();
                dst.dispose();
                Gdx.app.exit();
            }
            //DEBUG-SHOT-END

            return;
        }

        //菜单：只渲染 Scene2D 界面
        if(hudfrag != null) hudfrag.hide();
        ScreenUtils.clear(0.57f, 0.77f, 0.85f, 1);
        stage.act(Gdx.graphics.getDeltaTime());
        Time.update();
        stage.draw();

        //DEBUG-LOGTEST-START
        //菜单下也要驱动 logtest：联机加入失败时会停在菜单，若只在上面的"非菜单"分支推进帧计数，
        //测试进程就永远不会到达结束帧（表现为挂死）。debugLogTest 内部对 world/player 为 null 已有守卫。
        if(System.getenv("PHOENIX_LOGTEST") != null) debugLogTest();
        //DEBUG-LOGTEST-END
    }

    //DEBUG-LOGTEST-START
    /**
     * 物流链路自动化验证（环境变量 {@code PHOENIX_LOGTEST=<结束帧>}）：
     * 在玩家附近搭一条「传送带 ×4 → 核心」的链路，往首格塞 3 个铜，
     * 每 60 帧打印各格物品数与核心库存增量，到结束帧打印并退出。
     */
    private void debugLogTest(){
        //DEBUG-WATCHDOG-START
        //看门狗：主循环 3 秒没推进就 dump 全部线程栈，用于定位死循环（stderr 无缓冲，卡死前也能看到）
        if(watchdog == null){
            watchdog = new Thread(() -> {
                int last = -1;
                while(true){
                    try{
                        Thread.sleep(1500);
                    }catch(Exception e){
                        return;
                    }
                    int cur = logTestFrame;
                    if(cur == last && cur > 0){
                        System.err.println("DBG WATCHDOG STUCK at frame " + cur);
                        for(java.util.Map.Entry<Thread, StackTraceElement[]> en : Thread.getAllStackTraces().entrySet()){
                            System.err.println("DBG THREAD " + en.getKey().getName());
                            for(StackTraceElement s : en.getValue()){
                                System.err.println("DBG   at " + s);
                            }
                        }
                        return;
                    }
                    last = cur;
                }
            });
            watchdog.setDaemon(true);
            watchdog.start();
        }
        //DEBUG-WATCHDOG-END

        //先解析参数并推进帧计数：即使世界/玩家未就绪也要能按帧数退出，否则会静默卡住
        String env = System.getenv("PHOENIX_LOGTEST");
        if(env != null){
            try{
                int c = env.indexOf(':');
                if(c > 0){
                    logTestEnd = Integer.parseInt(env.substring(0, c));
                    logTestMode = Integer.parseInt(env.substring(c + 1));
                }else{
                    logTestEnd = Integer.parseInt(env);
                }
            }catch(Exception ignored){
            }
        }

        logTestFrame++;

        if(Vars.world != null && Vars.player != null){
            if(!logTestBuilt && logTestFrame > 5){
                logTestBuilt = true;
                //放大相机，截图时能看清贴图细节
                if(Vars.renderer != null) Vars.renderer.setScale(3f);
                switch(logTestMode){
                    case 2: buildRouterTest(); break;
                    case 3: buildSorterTest(); break;
                    case 4: buildJunctionTest(); break;
                case 5: buildUnloaderTest(); break;
                case 6: buildCoreLinkTest(); break;
                case 7: buildBridgeTest(); break;
                case 8: buildMassDriverTest(); break;
                case 9: buildLaunchPadTest(); break;
                case 10: buildDrillTest(); break;
                case 11: buildWallTest(); break;
                case 12: buildTurretTest(); break;
                case 13: buildSupportTest(); break;
                case 14: buildPowerTest(); break;
                case 15: buildNodeDiodeTest(); break;
                case 16: buildGeneratorTest(); break;
                case 17: buildQueueTest(); break;
                case 18: buildSchematicTest(); break;
                case 19: buildUnitSupportTest(); break;
                case 20: buildUnitProduceTest(); break;
                case 21: buildSandboxConfigTest(); break;
                case 22: buildSchematicPreviewTest(); break;
                case 23: buildSaveLoadTest(); break;
                case 24: buildEffectTest(); break;
                case 25: break; // 联机建造队列验证：布局由帧驱动部分处理
                case 26: buildEffectGalleryTest(); break;
                case 27: buildMuzzleTest(); break;
                case 28: buildFireTest(); break;
                default: buildConveyorTest(); break;
                }
                printLogTestStats();
            }

            //模式 2：周期性往路由器塞物品，观察分发
            if(logTestMode == 2 && logTestFrame > 10 && logTestFrame < 250 && logTestFrame % 30 == 0){
                com.phoenix.game.world.Tile c = logTestTiles[0];
                if(c != null && c.entity != null){
                    c.block().handleItem(com.phoenix.game.content.Items.copper, c, c);
                }
            }

            //模式 14：第 200 帧才接入耗电 3.0 的力场投影器（3x3，锚点即中心），观察电池由"充"转"放"。
            //锚点取电池右数第 2 格：3x3 占满 x+2..x+4，左列正好与电池相邻，且不会覆盖电池本身
            if(logTestMode == 14 && logTestFrame == 200 && logTestTiles != null && logTestTiles[0] != null){
                com.phoenix.game.world.Tile proj =
                    put(logTestTiles[0].x + 2, logTestTiles[0].y, com.phoenix.game.content.Blocks.forceProjector, 0);
                System.err.println("DBG power added forceProjector frame=" + logTestFrame
                    + " placed=" + (proj != null && proj.entity != null));
            }

            //模式 16：第 300 帧打爆钍反应堆，验证核爆回调不会把主循环带崩
            if(logTestMode == 16 && logTestFrame == 300 && logTestTiles != null && logTestTiles[2] != null){
                com.phoenix.game.world.Tile reactor = logTestTiles[2];
                if(reactor.entity != null){
                    reactor.entity.health(1f);
                    reactor.entity.handleDamage(9999f);
                }
                System.err.println("DBG reactor killed, blockNow="
                    + (reactor.blockRaw() == null ? "null" : reactor.blockRaw().name));
            }

            //模式 17：第 300 帧塞一条拆除请求（拆已建好的燃烧发电机，材料多、返还看得清）
            if(logTestMode == 17 && logTestFrame == 300 && logTestTiles != null && logTestTiles[1] != null){
                Vars.player.addBuildRequest(new com.phoenix.game.world.BuildRequest(
                    logTestTiles[1].x, logTestTiles[1].y));
                System.err.println("DBG queue enqueued break at " + logTestTiles[1].x + "," + logTestTiles[1].y);
            }

            //模式 18：第 60 帧走**玩家真实输入路径**粘贴（input.pasteSchematic + placeWorld）
            if(logTestMode == 18 && logTestFrame == 60 && logTestSchematic != null
                && logTestTiles != null && logTestTiles[0] != null && Vars.player != null
                && Vars.control != null && Vars.control.input != null){
                int ox = logTestTiles[0].x + logTestSchematic.width / 2;
                com.phoenix.game.input.InputHandler input = Vars.control.input;
                input.pasteSchematic = logTestSchematic;
                ((com.phoenix.game.input.DesktopInput)input).placeWorld(ox, logTestTiles[0].y);
                input.pasteSchematic = null;
                System.err.println("DBG schem pasted via placeWorld at " + ox + "," + logTestTiles[0].y
                    + " queue=" + Vars.player.unit().buildQueue.size);
            }

            //模式 19：第 200 帧生成 3 个友军 → 框选 → 下达移动命令（RTS 命令系统验证）
            if(logTestMode == 19 && logTestFrame == 200 && logTestTiles != null && logTestTiles[0] != null
                && Vars.player != null && Vars.control != null && Vars.control.input != null){
                com.phoenix.game.input.InputHandler input = Vars.control.input;

                if(Vars.isClient()){
                    //联机客户端：选中 3 个本队已有单位（服务器权威），命令走 UnitCommandPacket
                    int picked = 0;
                    for(int i = 0; i < com.phoenix.game.entities.Units.units.size && picked < 3; i++){
                        BaseUnit unit = com.phoenix.game.entities.Units.units.get(i);
                        if(input.canCommand(unit)){
                            input.selectedUnits.add(unit);
                            rtsTestUnits.add(unit);
                            picked++;
                        }
                    }
                    float tx = Vars.player.getX() + 96f, ty = Vars.player.getY() + 96f;
                    input.commandTap(tx, ty, false);
                    System.err.println("DBG rts(client) commanded=" + picked + " target=" + (int)tx + "," + (int)ty);
                }else{
                    BaseUnit playerUnit = Vars.player.unit();

                    for(int i = 0; i < 3; i++){
                        com.phoenix.game.type.UnitType type = com.phoenix.game.content.UnitTypes.dagger;
                        //UnitType.create() 内部已 init（含 Units.add），无需重复加入
                        BaseUnit unit = type.create();
                        unit.setTeam(Vars.player.getTeam());
                        unit.set(playerUnit.getX() + 24f + i * 16f, playerUnit.getY() + 24f);
                        input.selectedUnits.add(unit);
                        rtsTestUnits.add(unit);
                    }

                    //通过真实命令入口下达移动命令（单机/主机 = 权威应用）
                    float tx = playerUnit.getX() + 96f, ty = playerUnit.getY() + 96f;
                    input.commandTap(tx, ty, false);
                    System.err.println("DBG rts commanded=" + input.selectedUnits.size
                        + " target=" + (int)tx + "," + (int)ty);
                }
            }

            //模式 20：第 120 帧点机甲平台换机甲（等世界跑起来、电网充好电）
            if(logTestMode == 20 && logTestFrame == 120 && logTestTiles != null && logTestTiles[0] != null
                && Vars.player != null && logTestTiles[0].entity != null){
                com.phoenix.game.world.Tile pt = logTestTiles[0];
                float pcx = pt.block().centerX(pt), pcy = pt.block().centerY(pt);
                System.err.println("DBG mechpad precheck sameTeam=" + (pt.getTeam() == Vars.player.getTeam())
                    + " cons=" + pt.entity.cons.valid()
                    + " pw=" + (pt.entity.power == null ? -1f : pt.entity.power.status)
                    + " dx=" + Math.abs(Vars.player.getX() - pcx) + " dy=" + Math.abs(Vars.player.getY() - pcy)
                    + " limit=" + (pt.block().size * 8));

                boolean handled = pt.block().tapped(pt, Vars.player);
                System.err.println("DBG mechpad tapped handled=" + handled
                    + " unitType=" + (Vars.player.unit() == null ? "none" : Vars.player.unit().getType().name));
            }

            //模式 21：第 150 帧加电力虚空（看电池被抽干），第 200 帧打开分拣器的配置面板
            if(logTestMode == 21 && logTestTiles != null){
                if(logTestFrame == 150 && logTestTiles[0] != null){
                    com.phoenix.game.world.Tile voidTile = put(logTestTiles[0].x + 1, logTestTiles[0].y,
                        com.phoenix.game.content.Blocks.powerVoid, 0);
                    logTestTiles[1] = voidTile;
                    System.err.println("DBG power void placed=" + (voidTile != null && voidTile.entity != null));
                }
                if(logTestFrame == 200 && logTestTiles[4] != null && Vars.control != null && Vars.control.input != null){
                    Vars.control.input.configTile = logTestTiles[4];
                    System.err.println("DBG config panel opened on "
                        + logTestTiles[4].blockRaw().name + " configurable=" + logTestTiles[4].block().configurable);
                }
            }

            //模式 23：存档往返 —— 120 帧记指纹并存盘，130 帧破坏世界，140 帧读档比对
            if(logTestMode == 23 && logTestSaveFile != null){
                if(logTestFrame == 120){
                    logTestFingerprint = saveFingerprint();
                    try{
                        //走 Saves 的正常路径（SaveIO + 元数据 + 缩略图），而不是只调 SaveIO
                        com.phoenix.game.game.Saves.SaveSlot slot = Vars.saves.addSave("saveload-test");
                        logTestSaveFile = slot.file;
                        java.io.File png = slot.previewFile();
                        System.err.println("DBG saveload saved file=" + slot.file.getName()
                            + " bytes=" + slot.file.length()
                            + " fpLen=" + logTestFingerprint.length()
                            + " meta=" + describeMeta(slot.file)
                            + " slotWave=" + slot.wave() + " playtime=" + slot.playtimeText()
                            + " preview=" + slot.hasPreview() + " png=" + (png.exists() ? png.length() : -1));
                    }catch(Exception e){
                        System.err.println("DBG saveload SAVE FAILED: " + e);
                    }
                }

                if(logTestFrame == 130){
                    damageWorldForSaveTest();
                    System.err.println("DBG saveload damaged, blocksNow=" + countBlocks()
                        + " shardedItems=" + Vars.state.teams.items(com.phoenix.game.game.Team.sharded).total());
                }

                if(logTestFrame == 140){
                    try{
                        com.phoenix.game.io.SaveIO.load(logTestSaveFile);
                        rebuildPlayerAfterLoad();
                        String after = saveFingerprint();
                        boolean same = after.equals(logTestFingerprint);
                        System.err.println("DBG saveload loaded blocksNow=" + countBlocks()
                            + " shardedItems=" + Vars.state.teams.items(com.phoenix.game.game.Team.sharded).total()
                            + " same=" + same);
                        if(!same){
                            System.err.println("DBG saveload DIFF at " + firstDifference(logTestFingerprint, after));
                        }
                    }catch(Exception e){
                        System.err.println("DBG saveload LOAD FAILED: " + e);
                        e.printStackTrace();
                    }
                }

                //200 帧弹出读档对话框，供截图确认缩略图/信息卡渲染
                if(logTestFrame == 200 && stage != null){
                    com.phoenix.game.ui.fragments.HudFragment.showLoadDialog(stage, null);
                    System.err.println("DBG saveload dialog shown slots=" + Vars.saves.getSlots().size
                        + " firstHasPreview=" + (Vars.saves.getSlots().size > 0 && Vars.saves.getSlots().first().hasPreview()));
                }
            }

            //模式 25：联机建造队列验证 —— 客机发建造请求，检查能否从 BlockState 看到"在建"方块与进度
            if(logTestMode == 25 && Vars.isClient() && Vars.world != null && Vars.player != null){
                if(logTestFrame == 150 && Vars.netClient != null && Vars.netClient.isConnected()){
                    int bx = Vars.world.toTile(Vars.player.getX()) + 3;
                    int by = Vars.world.toTile(Vars.player.getY());
                    com.phoenix.game.world.Tile t = Vars.world.tile(bx, by);
                    logTestTiles = new com.phoenix.game.world.Tile[]{t};
                    Vars.netClient.sendBuild(bx, by, com.phoenix.game.content.Blocks.combustionGenerator, 0);
                    System.err.println("DBG mp client sent build at " + bx + "," + by
                        + " wasAir=" + (t != null && t.block() == com.phoenix.game.content.Blocks.air));
                }

                if(logTestFrame > 170 && logTestFrame % 20 == 0 && logTestTiles != null && logTestTiles[0] != null){
                    com.phoenix.game.world.Tile t = logTestTiles[0];
                    String extra = "";
                    if(t.entity instanceof com.phoenix.game.world.blocks.BuildBlock.BuildEntity){
                        com.phoenix.game.world.blocks.BuildBlock.BuildEntity be =
                            (com.phoenix.game.world.blocks.BuildBlock.BuildEntity)t.entity;
                        extra = " cblock=" + (be.cblock == null ? "null" : be.cblock.name)
                            + " progress=" + String.format("%.3f", be.progress);
                    }
                    System.err.println("DBG mp client tile=" + t.blockRaw().name + extra);
                }
            }

            //模式 24：特效验证 —— 持续放置/拆除建筑、炮塔开火、单位死亡
            if(logTestMode == 24){
                int bx = Vars.world.toTile(Vars.player.getX()) + 3;
                int by = Vars.world.toTile(Vars.player.getY());

                //持续放置：每 15 帧放一个，抓帧时总能看到 placeBlock 粒子
                if(logTestFrame > 30 && logTestFrame % 15 == 0){
                    com.phoenix.game.world.Block[] palette = {
                        com.phoenix.game.content.Blocks.conveyor,
                        com.phoenix.game.content.Blocks.router,
                        com.phoenix.game.content.Blocks.junction,
                        com.phoenix.game.content.Blocks.sorter,
                        com.phoenix.game.content.Blocks.battery,
                        com.phoenix.game.content.Blocks.powerNode,
                    };
                    int n = logTestFrame / 15;
                    put(bx + (n % 8), by - 2 - (n / 8), palette[n % palette.length], 0);
                }

                //周期性拆除：产生 breakBlock 粒子
                if(logTestFrame > 60 && logTestFrame % 40 == 0){
                    com.phoenix.game.world.Tile t = Vars.world.tile(bx + (logTestFrame / 40 % 8), by - 2);
                    if(t != null && t.block() != com.phoenix.game.content.Blocks.air){
                        com.phoenix.game.world.Build.deconstruct(t, Vars.player.getTeam());
                    }
                }

                //敌方单位周期性进入炮塔射程：验证炮口闪光 / 命中 / 死亡碎片
                if(logTestFrame > 40 && logTestFrame % 30 == 0){
                    com.phoenix.game.entities.type.BaseUnit enemy = com.phoenix.game.content.UnitTypes.dagger.create();
                    enemy.setTeam(com.phoenix.game.game.Team.crux);
                    enemy.set(Vars.player.getX() + 50f + (logTestFrame / 30 % 3) * 14f, Vars.player.getY() + 12f);
                    enemy.health(20f);
                    com.phoenix.game.entities.Units.add(enemy);
                    if(logTestFrame == 40){
                        System.err.println("DBG effect enemy spawned hp=" + enemy.health());
                    }
                }
            }

            //模式 26：特效画廊 —— 把 Fx 里所有特效铺成网格反复重放，抓一帧就能看全
            if(logTestMode == 26 && galleryEffects != null){
                if(logTestFrame > 20 && logTestFrame % 25 == 0){
                    playGallery();
                    if(logTestFrame == 20 + 25){
                        System.err.println("DBG gallery played count=" + galleryEffects.size());
                    }
                }
            }

            //模式 27：周期在激光炮塔正前方放敌人，让整排炮塔（含激光炮塔）持续开火
            if(logTestMode == 27){
                int bx2 = Vars.world.toTile(Vars.player.getX()) + 3;
                int by2 = Vars.world.toTile(Vars.player.getY());

                //meltdown 耗电 1.5/帧，一块太阳能板（0.06）根本供不起；测试里直接灌电
                com.phoenix.game.world.Tile mt = Vars.world.tile(bx2 + 22, by2);
                if(mt != null && mt.entity != null && mt.entity.power != null){
                    mt.entity.power.stored = 1000f;
                    mt.entity.power.status = 1f;
                }

                if(logTestFrame > 40 && logTestFrame % 20 == 0){
                    //以 lancer 实体的实际位置为参考点放敌人：炮塔格坐标与实际实体坐标差得很远，
                    //按"玩家位置 + 偏移"来估会以为在射程内、实际差了几百单位
                    com.phoenix.game.world.Tile lt = Vars.world.tile(bx2 + 18, by2);
                    float ax = lt != null && lt.entity != null ? lt.entity.x + 110f : Vars.player.getX() + 300f;
                    float ay = lt != null && lt.entity != null ? lt.entity.y : Vars.player.getY();

                    for(int k = 0; k < 3; k++){
                        com.phoenix.game.entities.type.BaseUnit enemy = com.phoenix.game.content.UnitTypes.dagger.create();
                        enemy.setTeam(com.phoenix.game.game.Team.crux);
                        enemy.set(ax + k * 40f, ay + (k - 1) * 40f);
                        enemy.health(120f);
                        com.phoenix.game.entities.Units.add(enemy);
                    }

                    if(logTestFrame == 60){
                        System.err.println("DBG muzzle enemies spawned");
                    }
                }

                //meltdown 靠电网供电，测试场景里一块太阳能板（0.06）供不起它的 1.5 → cons.valid() 不通过、
                //炮塔根本不开火。这里直接创建它的激光弹来验证光束绘制（炮塔侧接线与 lancer 同源，已由 lancer 验证）
                if(logTestFrame > 40 && logTestFrame % 16 == 0 && mt != null && mt.entity != null){
                    com.phoenix.game.entities.type.Bullet.create(
                        com.phoenix.game.content.Bullets.meltdownLaser,
                        (com.phoenix.game.entities.traits.Entity)null,
                        Vars.player.getTeam(), mt.entity.x, mt.entity.y - 80f, 0f);
                }

                //统计激光弹数量：0 说明激光炮塔没开火，>0 说明光束确实在场上（配合抓帧看绘制）
                if(logTestFrame > 60 && logTestFrame % 60 == 0){
                    int lasers = 0;
                    for(int i = 0; i < com.phoenix.game.entities.type.Bullet.all.size; i++){
                        com.phoenix.game.entities.bullet.BulletType bt =
                            com.phoenix.game.entities.type.Bullet.all.get(i).getBulletType();
                        if(bt == com.phoenix.game.content.Bullets.lancerLaser
                            || bt == com.phoenix.game.content.Bullets.meltdownLaser) lasers++;
                    }
                    System.err.println("DBG muzzle bullets=" + com.phoenix.game.entities.type.Bullet.all.size
                        + " lasers=" + lasers);
                }
            }

            //模式 28：观察火势蔓延 / 熄灭，并验证火会给地面单位挂燃烧状态
            if(logTestMode == 28){
                //第 30 帧把一个地面单位丢进容器火里：火应周期性给它挂 burning 并扣血
                if(logTestFrame == 30 && logTestTiles != null && logTestTiles[0] != null
                    && logTestTiles[0].entity != null){
                    com.phoenix.game.entities.type.BaseUnit unit = com.phoenix.game.content.UnitTypes.dagger.create();
                    unit.setTeam(Vars.player.getTeam());
                    unit.set(logTestTiles[0].entity.x, logTestTiles[0].entity.y);
                    unit.health(500f);
                    com.phoenix.game.entities.Units.add(unit);
                    logTestFireUnit = unit;
                }

                if(logTestFrame > 60 && logTestFrame % 60 == 0 && logTestTiles != null
                    && logTestTiles[3] != null && logTestTiles[4] != null){
                    boolean burning = logTestFireUnit != null && logTestFireUnit.status() != null
                        && logTestFireUnit.status().hasEffect(com.phoenix.game.content.StatusEffects.burning);
                    System.err.println("DBG fire count=" + com.phoenix.game.entities.effect.Fire.count()
                        + " water=" + com.phoenix.game.entities.effect.Fire.has(logTestTiles[3].x, logTestTiles[3].y)
                        + " grass=" + com.phoenix.game.entities.effect.Fire.has(logTestTiles[4].x, logTestTiles[4].y)
                        + " unitBurning=" + burning
                        + " unitHp=" + (logTestFireUnit == null ? -1f : logTestFireUnit.health())
                        + " waterFloor=" + logTestTiles[3].floor().name
                        + " grassFloor=" + logTestTiles[4].floor().name);
                }
            }

            if(logTestFrame > 10 && logTestFrame % 30 == 0){
                printLogTestStats();
            }
        }

        if(logTestFrame >= logTestEnd){
            System.err.println("DBG logtest end frame=" + logTestFrame
                + " world=" + (Vars.world != null) + " player=" + (Vars.player != null));
            Gdx.app.exit();
        }
    }

    /** 测试模式 1：传送带 x4 → 核心（基础流动）。 */
    private void buildConveyorTest(){
        com.phoenix.game.game.Team team = Vars.player.getTeam();
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 1, by - 3, 10, 7);

        logTestTiles = new com.phoenix.game.world.Tile[5];

        for(int i = 0; i < 4; i++){
            logTestTiles[i] = put(bx + i, by, com.phoenix.game.content.Blocks.conveyor, 1);
        }
        logTestTiles[4] = put(bx + 5, by, com.phoenix.game.content.Blocks.core, 0);

        fillConveyor(logTestTiles[0], com.phoenix.game.content.Items.copper, 3);
        System.err.println("DBG chain built at " + bx + "," + by);
    }

    /** 测试模式 2：路由器向四个方向分发。 */
    private void buildRouterTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 2, by - 2, 6, 6);

        logTestTiles = new com.phoenix.game.world.Tile[5];
        logTestTiles[0] = put(bx, by, com.phoenix.game.content.Blocks.router, 0);

        //四方向各一条朝外的传送带
        for(int i = 0; i < 4; i++){
            com.badlogic.gdx.math.GridPoint2 d = com.phoenix.game.math.geom.Geometry.d4[i];
            logTestTiles[i + 1] = put(bx + d.x, by + d.y, com.phoenix.game.content.Blocks.conveyor, i);
        }
        System.err.println("DBG router built at " + bx + "," + by);
    }

    /** 测试模式 3：分类器把铜直行、铅分流到侧向容器。 */
    private void buildSorterTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 2, by - 2, 7, 6);

        //输入带（朝右）→ 分类器（配置铜）→ 直行带（朝右）→ 容器A
        //                                    侧向带（朝上）→ 容器B
        com.phoenix.game.world.Tile in = put(bx - 1, by, com.phoenix.game.content.Blocks.conveyor, 1);
        put(bx, by, com.phoenix.game.content.Blocks.sorter, 0);
        put(bx + 1, by, com.phoenix.game.content.Blocks.conveyor, 1);
        com.phoenix.game.world.Tile storeA = put(bx + 2, by, com.phoenix.game.content.Blocks.storage, 0);

        put(bx, by + 1, com.phoenix.game.content.Blocks.conveyor, 0);
        com.phoenix.game.world.Tile storeB = put(bx, by + 2, com.phoenix.game.content.Blocks.storage, 0);

        //配置分类器：直行 = 铜
        com.phoenix.game.world.Tile sorterTile = Vars.world.tile(bx, by);
        sorterTile.configure(com.phoenix.game.content.Items.copper.id);

        fillConveyor(in, com.phoenix.game.content.Items.copper, 2);
        fillConveyor(in, com.phoenix.game.content.Items.lead, 2);

        logTestStores = new com.phoenix.game.world.Tile[]{storeA, storeB};
        logTestStoreBase = new int[]{0, 0};
        System.err.println("DBG sorter built at " + bx + "," + by);
    }

    /** 测试模式 4：交叉器让两条带子直行穿越、互不串线。 */
    private void buildJunctionTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 2, by - 2, 7, 6);

        //水平：带(左,朝右) → 交叉器 → 带(右,朝右) → 容器A
        com.phoenix.game.world.Tile inH = put(bx - 1, by, com.phoenix.game.content.Blocks.conveyor, 1);
        put(bx, by, com.phoenix.game.content.Blocks.junction, 0);
        put(bx + 1, by, com.phoenix.game.content.Blocks.conveyor, 1);
        com.phoenix.game.world.Tile storeA = put(bx + 2, by, com.phoenix.game.content.Blocks.storage, 0);

        //垂直：带(下,朝上) → 交叉器 → 带(上,朝上) → 容器B
        com.phoenix.game.world.Tile inV = put(bx, by - 1, com.phoenix.game.content.Blocks.conveyor, 0);
        put(bx, by + 1, com.phoenix.game.content.Blocks.conveyor, 0);
        com.phoenix.game.world.Tile storeB = put(bx, by + 2, com.phoenix.game.content.Blocks.storage, 0);

        fillConveyor(inH, com.phoenix.game.content.Items.copper, 2);
        fillConveyor(inV, com.phoenix.game.content.Items.lead, 2);

        logTestStores = new com.phoenix.game.world.Tile[]{storeA, storeB};
        logTestStoreBase = new int[]{0, 0};
        System.err.println("DBG junction built at " + bx + "," + by);
    }

    /** 测试模式 5：装卸器把物品从容器 A 搬到容器 B。 */
    private void buildUnloaderTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 3, by - 3, 10, 7);

        //容器A(2x2) → 装卸器 → 传送带(朝右) → 容器B(2x2)
        com.phoenix.game.world.Tile storeA = put(bx - 2, by, com.phoenix.game.content.Blocks.storage, 0);
        put(bx, by, com.phoenix.game.content.Blocks.unloader, 0);
        put(bx + 1, by, com.phoenix.game.content.Blocks.conveyor, 1);
        com.phoenix.game.world.Tile storeB = put(bx + 2, by, com.phoenix.game.content.Blocks.storage, 0);

        //往 A 塞 10 个铜
        if(storeA != null && storeA.entity != null && storeA.entity.items != null){
            storeA.entity.items.add(com.phoenix.game.content.Items.copper, 10);
        }

        logTestStores = new com.phoenix.game.world.Tile[]{storeA, storeB};
        System.err.println("DBG unloader built at " + bx + "," + by
            + " time=" + com.phoenix.game.core.Time.time
            + " prox=" + (Vars.world.tile(bx, by).entity == null ? -1 : Vars.world.tile(bx, by).entity.proximity.size));
    }

    /** 测试模式 6：紧贴核心的容器，收到的物品直接进共享库存。 */
    private void buildCoreLinkTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 3, by - 3, 12, 8);

        //核心 3x3 在 (bx+3, by)，占 [bx+2, bx+4]；容器 2x2 在 (bx, by)，占 [bx, bx+1]
        put(bx + 3, by, com.phoenix.game.content.Blocks.core, 0);
        com.phoenix.game.world.Tile store = put(bx, by, com.phoenix.game.content.Blocks.storage, 0);
        //带子朝右送入容器
        com.phoenix.game.world.Tile in = put(bx - 1, by, com.phoenix.game.content.Blocks.conveyor, 1);

        fillConveyor(in, com.phoenix.game.content.Items.copper, 3);

        logTestStores = new com.phoenix.game.world.Tile[]{store};
        System.err.println("DBG corelink built at " + bx + "," + by);
    }

    /** 测试模式 7：物品桥跨格运输（中间隔空 2 格，桥对自动互联）。 */
    private void buildBridgeTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 3, by - 3, 12, 7);

        //桥A(bx) ... 隔 2 格 ... 桥B(bx+3) → 容器B(bx+4)
        com.phoenix.game.world.Tile bridgeA = put(bx, by, com.phoenix.game.content.Blocks.itemBridge, 0);
        com.phoenix.game.world.Tile bridgeB = put(bx + 3, by, com.phoenix.game.content.Blocks.itemBridge, 0);
        com.phoenix.game.world.Tile storeB = put(bx + 4, by, com.phoenix.game.content.Blocks.storage, 0);

        //往桥A塞 3 个铜
        if(bridgeA != null && bridgeA.entity != null && bridgeA.entity.items != null){
            bridgeA.entity.items.add(com.phoenix.game.content.Items.copper, 3);
        }

        logTestStores = new com.phoenix.game.world.Tile[]{bridgeA, bridgeB, storeB};
        System.err.println("DBG bridge built at " + bx + "," + by
            + " linkA=" + bridgeLink(bridgeA) + " linkB=" + bridgeLink(bridgeB));
    }

    private int bridgeLink(com.phoenix.game.world.Tile t){
        if(t == null || !(t.entity instanceof com.phoenix.game.world.blocks.distribution.ItemBridge.ItemBridgeEntity)) return -999;
        return ((com.phoenix.game.world.blocks.distribution.ItemBridge.ItemBridgeEntity)t.entity).link;
    }

    /** 测试模式 8：两台质量驱动器互相对准并投送物品（需要供电）。 */
    private void buildMassDriverTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 3, by - 3, 16, 7);

        //太阳能板 + 电力节点 + 驱动器A ... 驱动器B + 电力节点 + 太阳能板
        put(bx, by, com.phoenix.game.content.Blocks.solarPanel, 0);
        put(bx + 1, by, com.phoenix.game.content.Blocks.powerNode, 0);
        com.phoenix.game.world.Tile driverA = put(bx + 2, by, com.phoenix.game.content.Blocks.massDriver, 0);
        com.phoenix.game.world.Tile driverB = put(bx + 8, by, com.phoenix.game.content.Blocks.massDriver, 0);
        put(bx + 9, by, com.phoenix.game.content.Blocks.powerNode, 0);
        put(bx + 10, by, com.phoenix.game.content.Blocks.solarPanel, 0);

        //两台互相配对
        if(driverA != null && driverB != null){
            driverA.configure(driverB.pos());
            driverB.configure(driverA.pos());
        }

        //给 A 塞 20 个铜（minDistribute = 10）
        if(driverA != null && driverA.entity != null && driverA.entity.items != null){
            driverA.entity.items.add(com.phoenix.game.content.Items.copper, 20);
        }

        logTestStores = new com.phoenix.game.world.Tile[]{driverA, driverB};
        System.err.println("DBG massdriver built at " + bx + "," + by
            + " A=" + (driverA == null ? -1 : driverA.pos()) + " B=" + (driverB == null ? -1 : driverB.pos())
            + " Aent=" + (driverA != null && driverA.entity != null) + " Bent=" + (driverB != null && driverB.entity != null));
    }

    /** 测试模式 9：发射台把整仓物品送进全局库存。 */
    private void buildLaunchPadTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 3, by - 3, 12, 8);

        //测试用：把发射间隔改短（原版 20 秒）
        if(com.phoenix.game.content.Blocks.launchPad instanceof com.phoenix.game.world.blocks.storage.LaunchPad){
            ((com.phoenix.game.world.blocks.storage.LaunchPad)com.phoenix.game.content.Blocks.launchPad).launchTime = 30f;
        }

        com.phoenix.game.world.Tile pad = put(bx, by, com.phoenix.game.content.Blocks.launchPad, 0);

        //塞满（itemCapacity = 100）
        if(pad != null && pad.entity != null && pad.entity.items != null){
            pad.entity.items.add(com.phoenix.game.content.Items.copper, 100);
        }

        logTestStores = new com.phoenix.game.world.Tile[]{pad};
        System.err.println("DBG launchpad built at " + bx + "," + by
            + " ent=" + (pad != null && pad.entity != null)
            + " globalCu=" + (Vars.data == null ? -1 : Vars.data.getItem(com.phoenix.game.content.Items.copper)));
    }

    /** 测试模式 10：钻头按矿脉产出不同矿（对应原版矿脉机制 + tier 硬度门槛）。 */
    private void buildDrillTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 3, by - 3, 14, 8);

        //三组用例：矿脉 overlay 产铜 / 沙地地板直接产沙 / 钛矿硬度超出机械钻头 tier（应放不上）
        com.phoenix.game.world.Tile t1 = Vars.world.tile(bx, by);
        com.phoenix.game.world.Tile t2 = Vars.world.tile(bx + 3, by);
        com.phoenix.game.world.Tile t3 = Vars.world.tile(bx + 6, by);
        if(t1 != null){
            t1.setFloor(com.phoenix.game.content.Blocks.grass);
            t1.setOverlay(com.phoenix.game.content.Blocks.oreCopper);
        }
        if(t2 != null){
            t2.setFloor(com.phoenix.game.content.Blocks.sand);
        }
        if(t3 != null){
            t3.setFloor(com.phoenix.game.content.Blocks.grass);
            t3.setOverlay(com.phoenix.game.content.Blocks.oreTitanium);
        }

        com.phoenix.game.world.Tile d1 = put(bx, by, com.phoenix.game.content.Blocks.copperDrill, 0);
        com.phoenix.game.world.Tile d2 = put(bx + 3, by, com.phoenix.game.content.Blocks.copperDrill, 0);
        //tier 2 < 钛硬度 3：validPlace 应拒绝
        com.phoenix.game.world.Tile d3 = put(bx + 6, by, com.phoenix.game.content.Blocks.copperDrill, 0);

        logTestStores = new com.phoenix.game.world.Tile[]{d1, d2};
        System.err.println("DBG drill t1 overlay=" + (t1 == null ? "?" : t1.overlay().name)
            + " drop=" + (t1 == null || t1.drop() == null ? "null" : t1.drop().name)
            + " canPlaceOn=" + com.phoenix.game.content.Blocks.copperDrill.canPlaceOn(t1)
            + " d3Valid(expect false)=" + (t3 != null && com.phoenix.game.world.Build.validPlace(t3, com.phoenix.game.content.Blocks.copperDrill)));
        System.err.println("DBG drill built at " + bx + "," + by
            + " d1=" + (d1 != null && d1.entity != null)
            + " d2=" + (d2 != null && d2.entity != null)
            + " d3(tier-gated, expect false)=" + (d3 != null && d3.entity != null)
            + " r1=" + (t1 == null ? "?" : String.valueOf(com.phoenix.game.content.Blocks.copperDrill.resultFor(t1)))
            + " r2=" + (t2 == null ? "?" : String.valueOf(com.phoenix.game.content.Blocks.copperDrill.resultFor(t2)))
            + " r3=" + (t3 == null ? "?" : String.valueOf(com.phoenix.game.content.Blocks.copperDrill.resultFor(t3))));
    }

    /** 测试模式 11：墙系列与门的渲染/血量。 */
    private void buildWallTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 3, by - 3, 16, 8);

        //第一排：1x1 墙 + 门
        put(bx, by, com.phoenix.game.content.Blocks.copperWall, 0);
        put(bx + 1, by, com.phoenix.game.content.Blocks.titaniumWall, 0);
        put(bx + 2, by, com.phoenix.game.content.Blocks.thoriumWall, 0);
        put(bx + 3, by, com.phoenix.game.content.Blocks.plastaniumWall, 0);
        put(bx + 4, by, com.phoenix.game.content.Blocks.phaseWall, 0);
        put(bx + 5, by, com.phoenix.game.content.Blocks.surgeWall, 0);
        put(bx + 6, by, com.phoenix.game.content.Blocks.scrapWall, 0);
        put(bx + 8, by, com.phoenix.game.content.Blocks.door, 0);

        //第二排：2x2 大型墙 + 大门
        put(bx, by + 3, com.phoenix.game.content.Blocks.copperWallLarge, 0);
        put(bx + 3, by + 3, com.phoenix.game.content.Blocks.thoriumWallLarge, 0);
        put(bx + 6, by + 3, com.phoenix.game.content.Blocks.doorLarge, 0);

        System.err.println("DBG walls built at " + bx + "," + by);
    }

    /** 测试模式 12：14 种炮塔的渲染。 */
    private void buildTurretTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 4, by - 6, 24, 14);

        put(bx, by, com.phoenix.game.content.Blocks.duo, 0);
        put(bx + 2, by, com.phoenix.game.content.Blocks.scatter, 0);
        put(bx + 5, by, com.phoenix.game.content.Blocks.scorch, 0);
        put(bx + 7, by, com.phoenix.game.content.Blocks.hail, 0);
        put(bx + 9, by, com.phoenix.game.content.Blocks.lancer, 0);
        put(bx + 12, by, com.phoenix.game.content.Blocks.arc, 0);
        put(bx + 14, by, com.phoenix.game.content.Blocks.swarmer, 0);
        put(bx + 17, by, com.phoenix.game.content.Blocks.salvo, 0);

        put(bx, by + 4, com.phoenix.game.content.Blocks.fuse, 0);
        put(bx + 2, by + 4, com.phoenix.game.content.Blocks.ripple, 0);
        put(bx + 6, by + 4, com.phoenix.game.content.Blocks.cyclone, 0);
        put(bx + 10, by + 4, com.phoenix.game.content.Blocks.spectre, 0);
        put(bx + 15, by + 4, com.phoenix.game.content.Blocks.meltdown, 0);

        System.err.println("DBG turrets built at " + bx + "," + by);
    }

    /** 测试模式 13：修复投影器治疗建筑（把墙打残后看血量回升）。 */
    private void buildSupportTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 3, by - 3, 14, 9);

        //测试用：把治疗间隔改短（原版 250 帧）
        if(com.phoenix.game.content.Blocks.mendProjector instanceof com.phoenix.game.world.blocks.defense.MendProjector){
            ((com.phoenix.game.world.blocks.defense.MendProjector)com.phoenix.game.content.Blocks.mendProjector).reload = 20f;
        }

        //太阳能板 + 电力节点 + 修复投影器 + 受损的墙
        put(bx, by, com.phoenix.game.content.Blocks.solarPanel, 0);
        put(bx + 1, by, com.phoenix.game.content.Blocks.powerNode, 0);
        put(bx + 2, by, com.phoenix.game.content.Blocks.mendProjector, 0);
        com.phoenix.game.world.Tile wall = put(bx + 4, by, com.phoenix.game.content.Blocks.copperWall, 0);

        //把墙打残到 30%
        if(wall != null && wall.entity != null){
            wall.entity.health(wall.entity.maxHealth() * 0.3f);
        }

        logTestStores = new com.phoenix.game.world.Tile[]{wall};
        System.err.println("DBG support built at " + bx + "," + by
            + " wallHp=" + (wall == null || wall.entity == null ? -1f : wall.entity.health()));
    }

    /**
     * 测试模式 14：电网。
     * <p>一排四邻接：太阳能(0.06) - 节点 - 燃烧发电机(烧煤 1.0) - 电池(4000，初始空)。
     * <p>预期时间线：
     * <ul>
     *   <li>f6~f200：只发电不耗电，富余 1.06/帧充入电池（prod 被电池吃掉 → 报 0）；</li>
     *   <li>f200：接入 3x3 力场投影器(3.0)，net = 1.06-3.0 = -1.94 → 电池放电补足，sat 仍 1.0；</li>
     *   <li>f246：2 个煤烧完，发电机停产 → net = 0.06-3.0 = -2.94，放电加快；</li>
     *   <li>f~286：电池耗尽 → sat 掉到 0.06/3.0 = 0.02。</li>
     * </ul>
     * <p>顺带验证两处拓扑 bug：放新建筑后电网仍连通（reflow 前必须清空归属），
     * 以及多格建筑能连上电网（卫星格无实体，连通必须走邻接表 / {@code link()}）。
     */
    private void buildPowerTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 2, by - 2, 12, 6);

        put(bx, by, com.phoenix.game.content.Blocks.solarPanel, 0);
        put(bx + 1, by, com.phoenix.game.content.Blocks.powerNode, 0);
        com.phoenix.game.world.Tile burn = put(bx + 2, by, com.phoenix.game.content.Blocks.combustionGenerator, 0);
        com.phoenix.game.world.Tile bat = put(bx + 3, by, com.phoenix.game.content.Blocks.battery, 0);

        //给燃烧发电机备 2 个煤：每个烧 120 帧，约 240 帧后断料（观察发电量掉回太阳能的 0.06）
        for(int i = 0; i < 2 && burn != null && burn.entity != null; i++){
            burn.block().handleItem(com.phoenix.game.content.Items.coal, burn, burn);
        }

        logTestTiles = new com.phoenix.game.world.Tile[]{bat, burn};
        System.err.println("DBG power built at " + bx + "," + by
            + " bat=" + (bat == null || bat.entity == null ? -1f : bat.entity.power.stored));
    }

    /**
     * 测试模式 15：电力节点连线 + 二极管单向输电。
     * <p>两条互不相邻的链（纵向隔 8 格，超出节点 6 格连线范围）：
     * <ul>
     *   <li>链 A：太阳能 - 电池(300) - 节点1 …… 隔 4 格空档 …… 节点2 - 过载投影器(1.0)。
     *       节点2 与节点1 相隔正好 6 格，只有靠连线才能进同一张电网 —— 连通则 gsize=5、need=1.0；</li>
     *   <li>链 B：太阳能 - 电池L(400) - 二极管(朝右) - 电池R - 过载投影器(1.0)。
     *       二极管自身不带电力模块，把左右隔成两张电网；靠比较两侧电池的充满百分比单向搬电。</li>
     * </ul>
     * <p>预期：链 A 的 gsize=5 且 need=1.0（连线生效）；链 B 左右各 gsize=2 且互不相通，
     * 左侧 need=0、右侧 need=1.0，电池L 的 400 电被二极管搬去右侧供投影器用，
     * 所以右侧 sat 一直 1.0，直到左侧搬空（约 400 帧）后掉到 0。
     */
    private void buildNodeDiodeTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 2, by - 10, 16, 15);

        //链 A：节点 1 → 节点 2 相隔 6 格（中间全空），只能靠节点连线
        put(bx, by, com.phoenix.game.content.Blocks.solarPanel, 0);
        com.phoenix.game.world.Tile batA = put(bx + 1, by, com.phoenix.game.content.Blocks.battery, 0);
        put(bx + 2, by, com.phoenix.game.content.Blocks.powerNode, 0);
        put(bx + 8, by, com.phoenix.game.content.Blocks.powerNode, 0);
        com.phoenix.game.world.Tile odA = put(bx + 9, by, com.phoenix.game.content.Blocks.overdriveProjector, 0);

        //链 B：二极管朝右（rotation 1 = +x），两侧各有一块电池，二极管靠百分比差搬电
        int by2 = by - 8;
        put(bx, by2, com.phoenix.game.content.Blocks.solarPanel, 0);
        com.phoenix.game.world.Tile batL = put(bx + 1, by2, com.phoenix.game.content.Blocks.battery, 0);
        com.phoenix.game.world.Tile diode = put(bx + 2, by2, com.phoenix.game.content.Blocks.diode, 1);
        com.phoenix.game.world.Tile batR = put(bx + 3, by2, com.phoenix.game.content.Blocks.battery, 0);
        com.phoenix.game.world.Tile odB = put(bx + 4, by2, com.phoenix.game.content.Blocks.overdriveProjector, 0);

        //预充电：链 A 电池 300（会被过载投影器耗光）；链 B 只给左侧 400，看它能不能被"搬"到右侧
        if(batA != null && batA.entity != null) batA.entity.power.stored = 300f;
        if(batL != null && batL.entity != null) batL.entity.power.stored = 400f;

        logTestTiles = new com.phoenix.game.world.Tile[]{batA, odA, batL, batR, odB};
        System.err.println("DBG nodediode built at " + bx + "," + by
            + " batA=" + (batA == null || batA.entity == null ? -1f : batA.entity.power.stored)
            + " batL=" + (batL == null || batL.entity == null ? -1f : batL.entity.power.stored)
            + " diodeHasPower=" + (diode != null && diode.entity != null && diode.entity.power != null)
            + " odA=" + (odA != null && odA.entity != null) + " odB=" + (odB != null && odB.entity != null));
    }

    /**
     * 测试模式 16：各类发电机。
     * <p>一字排开（间隔足够，多格建筑互不重叠），各自带燃料/热源：
     * <ul>
     *   <li>地热发电机：把脚下 2x2 的地板改成 shale(heat=0.5) → 预期发电 1.8 × (4×0.5) = 3.6；</li>
     *   <li>衰变发电机：塞钍(radioactivity=1.0) → 预期 3 × 1.0 = 3.0；</li>
     *   <li>钍反应堆：塞 15 钍（容量 30）→ 效率 0.5 → 预期 14 × 0.5 = 7.0；</li>
     *   <li>冲击反应堆：需要 25/帧启动电力才能点火，旁边挂一块预充电池；预热提速到 0.02 便于观察爬坡。</li>
     * </ul>
     * <p>第 300 帧把钍反应堆打爆，验证核爆回调不炸（爆炸只伤单位，本工程尚无爆炸伤建筑）。
     */
    private void buildGeneratorTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 2, by - 3, 32, 9);

        //地热：先把脚下一片地板改成热源
        com.phoenix.game.world.Tile thermal = put(bx, by, com.phoenix.game.content.Blocks.thermalGenerator, 0);
        for(int dx = 0; dx < 2; dx++){
            for(int dy = 0; dy < 2; dy++){
                com.phoenix.game.world.Tile t = Vars.world.tile(bx + dx, by + dy);
                if(t != null) t.setFloor(com.phoenix.game.content.Blocks.shale);
            }
        }

        //衰变发电机：塞钍
        com.phoenix.game.world.Tile rtg = put(bx + 7, by, com.phoenix.game.content.Blocks.rtgGenerator, 0);
        for(int i = 0; i < 2 && rtg != null && rtg.entity != null; i++){
            rtg.block().handleItem(com.phoenix.game.content.Items.thorium, rtg, rtg);
        }

        //钍反应堆：塞 15 钍（容量 30 → 效率 0.5）
        com.phoenix.game.world.Tile reactor = put(bx + 13, by, com.phoenix.game.content.Blocks.thoriumReactor, 0);
        for(int i = 0; i < 15 && reactor != null && reactor.entity != null; i++){
            reactor.block().handleItem(com.phoenix.game.content.Items.thorium, reactor, reactor);
        }

        //冲击反应堆：需要外部供电点火，旁边挂预充电池；并把预热速度调快便于观察
        if(com.phoenix.game.content.Blocks.impactReactor instanceof com.phoenix.game.world.blocks.power.ImpactReactor){
            ((com.phoenix.game.world.blocks.power.ImpactReactor)com.phoenix.game.content.Blocks.impactReactor).warmupSpeed = 0.02f;
        }
        com.phoenix.game.world.Tile impact = put(bx + 20, by, com.phoenix.game.content.Blocks.impactReactor, 0);
        for(int i = 0; i < 3 && impact != null && impact.entity != null; i++){
            impact.block().handleItem(com.phoenix.game.content.Items.blastCompound, impact, impact);
        }
        //4x4 锚点在 bx+20，占 bx+19..bx+22，所以电池要放 bx+23 才贴得上
        com.phoenix.game.world.Tile bat = put(bx + 23, by, com.phoenix.game.content.Blocks.battery, 0);
        if(bat != null && bat.entity != null) bat.entity.power.stored = 4000f;

        logTestTiles = new com.phoenix.game.world.Tile[]{thermal, rtg, reactor, impact};
        System.err.println("DBG generators built at " + bx + "," + by
            + " thermal=" + (thermal != null && thermal.entity != null)
            + " rtg=" + (rtg != null && rtg.entity != null)
            + " reactor=" + (reactor != null && reactor.entity != null)
            + " impact=" + (impact != null && impact.entity != null)
            + " bat=" + (bat != null && bat.entity != null));
    }

    /**
     * 测试模式 17：建造队列。
     * <p>不再直接 {@code Build.placeBlock}，而是往玩家单位的队列里塞两条请求：
     * 传送带（成本 0.5，几乎瞬间）和燃烧发电机（成本 23，约 76 帧），观察
     * <ul>
     *   <li>目标格先变成 {@code build1} 占位方块，随后 progress 从 0 涨到 1；</li>
     *   <li>队伍库存按需求**逐步**减少（不是一次扣光）；</li>
     *   <li>完成后方块变成目标方块，请求自动出队；</li>
     *   <li>第 300 帧再塞一条拆除请求，验证进度从 1 往回退并按比例返还材料。</li>
     * </ul>
     * <p>进度速度 = 1 / buildCost × buildPower(0.3)：成本 23 的发电机约 76 帧，
     * 所以"便宜方块建得飞快"本身就是原版行为，不是 bug。
     */
    private void buildQueueTest(){
        grantTestItems();
        //放大到上限，截图时能看清在建方块（默认的 3f 只有 24px/格）
        if(Vars.renderer != null) Vars.renderer.setScale(6f);

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 2, by - 2, 12, 6);

        //只留铜和铅：这样"材料逐步消耗"看得最清楚
        com.phoenix.game.world.modules.ItemModule inv = Vars.state.teams.items(Vars.player.getTeam());
        for(int i = 0; i < com.phoenix.game.content.Items.all.size; i++){
            com.phoenix.game.type.Item item = com.phoenix.game.content.Items.all.get(i);
            if(item != com.phoenix.game.content.Items.copper && item != com.phoenix.game.content.Items.lead){
                inv.remove(item, inv.get(item));
            }
        }

        logTestTiles = new com.phoenix.game.world.Tile[2];
        logTestTiles[0] = Vars.world.tile(bx, by);
        logTestTiles[1] = Vars.world.tile(bx + 2, by);

        Vars.player.addBuildRequest(new com.phoenix.game.world.BuildRequest(
            bx, by, 1, com.phoenix.game.content.Blocks.conveyor));
        Vars.player.addBuildRequest(new com.phoenix.game.world.BuildRequest(
            bx + 2, by, 0, com.phoenix.game.content.Blocks.combustionGenerator));

        System.err.println("DBG queue built at " + bx + "," + by
            + " cu=" + inv.get(com.phoenix.game.content.Items.copper)
            + " pb=" + inv.get(com.phoenix.game.content.Items.lead)
            + " conveyorCost=" + com.phoenix.game.content.Blocks.conveyor.buildCost
            + " genCost=" + com.phoenix.game.content.Blocks.combustionGenerator.buildCost);
    }

    /**
     * 测试模式 18：蓝图。
     * <p>步骤：
     * <ol>
     *   <li>摆一条「传送带-传送带-分拣器(配置成煤)-路由器」，用 {@code create} 抓成蓝图；</li>
     *   <li>写成 .msch 再读回来，比对瓦片数 / 方块名 / 配置是否一致（格式往返）；</li>
     *   <li>第 60 帧把蓝图转成建造请求入队，贴到另一处，验证粘贴走的是建造队列。</li>
     * </ol>
     * 同时打印文件头字节，确认与原版 .msch 兼容（'m','s','c','h',0）。
     */
    private void buildSchematicTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 3, by - 3, 24, 8);

        //源结构：带一个"配置成煤"的分拣器，用来验证 config 能随蓝图保存
        put(bx, by, com.phoenix.game.content.Blocks.conveyor, 1);
        put(bx + 1, by, com.phoenix.game.content.Blocks.conveyor, 1);
        put(bx + 2, by, com.phoenix.game.content.Blocks.sorter, 1);
        put(bx + 3, by, com.phoenix.game.content.Blocks.router, 0);
        com.phoenix.game.world.Tile sorter = Vars.world.tile(bx + 2, by);
        if(sorter != null) sorter.configure(com.phoenix.game.content.Items.coal.id);

        com.phoenix.game.game.Schematics schematics = Vars.schematics;
        com.phoenix.game.game.Schematic schem = schematics.create(bx, by, bx + 3, by);
        schem.tags.put("name", "phoenix-autotest");

        //往返：写文件 → 读文件
        String roundTrip = "n/a";
        int fileSize = -1;
        try{
            java.io.File file = new java.io.File(schematics.directory(), "phoenix-autotest.msch");
            com.phoenix.game.game.Schematics.write(schem, file);
            fileSize = (int)file.length();

            com.phoenix.game.game.Schematic back = schematics.read(file);
            StringBuilder names = new StringBuilder();
            int cfg = -2;
            for(int i = 0; i < back.tiles.size; i++){
                if(names.length() > 0) names.append(',');
                names.append(back.tiles.get(i).block.name);
                if(back.tiles.get(i).block == com.phoenix.game.content.Blocks.sorter){
                    cfg = back.tiles.get(i).config;
                }
            }
            roundTrip = back.tiles.size + "/" + back.width + "x" + back.height + " [" + names + "] cfg=" + cfg;
        }catch(Exception e){
            roundTrip = "EX " + e;
        }

        logTestTiles = new com.phoenix.game.world.Tile[]{
            Vars.world.tile(bx + 10, by), Vars.world.tile(bx + 11, by),
            Vars.world.tile(bx + 12, by), Vars.world.tile(bx + 13, by)
        };
        logTestSchematic = schem;

        System.err.println("DBG schem created tiles=" + schem.tiles.size
            + " size=" + schem.width + "x" + schem.height
            + " fileBytes=" + fileSize
            + " roundTrip=" + roundTrip
            + " coalId=" + com.phoenix.game.content.Items.coal.id
            + " reqs=" + schem.requirements().size);
    }

    /**
     * 测试模式 19：维修点 + 指挥中心。
     * <p>维修点：把玩家单位打残，旁边放电池(预充 4000)+维修点(耗电 1/帧)，看血量是否回升。
     * <p>指挥中心：第 200 帧放下并切到"集结"(rally)，看它的 config 与在场单位的 AI 状态名。
     */
    private void buildUnitSupportTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 3, by - 3, 16, 8);

        put(bx, by, com.phoenix.game.content.Blocks.battery, 0);
        com.phoenix.game.world.Tile bat = Vars.world.tile(bx, by);
        if(bat != null && bat.entity != null) bat.entity.power.stored = 4000f;

        com.phoenix.game.world.Tile repair = put(bx + 1, by, com.phoenix.game.content.Blocks.repairPoint, 0);

        //把玩家单位打残到 30%，方便观察回血
        if(Vars.player.unit() != null){
            Vars.player.unit().health(Vars.player.unit().maxHealth() * 0.3f);
        }

        logTestTiles = new com.phoenix.game.world.Tile[]{repair, null, null};
        System.err.println("DBG unitsupport built at " + bx + "," + by
            + " repair=" + (repair != null && repair.entity != null)
            + " unitHp=" + (Vars.player.unit() == null ? -1f : Vars.player.unit().health())
            + " unitMax=" + (Vars.player.unit() == null ? -1f : Vars.player.unit().maxHealth()));
    }

    /**
     * 测试模式 20：机甲平台 + 单位工厂。
     * <p>机甲平台：直接调 {@code tapped}（外部点不了鼠标），看进度推进、完成后玩家单位类型是否变了。
     * <p>单位工厂：放一座幽灵工厂并塞料，看是否出厂单位。
     */
    private void buildUnitProduceTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 5, by - 4, 20, 10);

        //机甲平台是 2x2，锚点在玩家脚下那一格（保证 |dx| <= size*tilesize 的贴脸判据成立）
        int px = Vars.world.toTile(Vars.player.getX()), py = Vars.world.toTile(Vars.player.getY());
        com.phoenix.game.world.Tile pad = put(px, py, com.phoenix.game.content.Blocks.mechPadDelta, 0);

        //电池必须放在平台的**外面**：平台占 (px..px+1)，所以放 px+2 才贴得上（放 px+1 会盖到卫星格、静默放置失败）
        put(px + 2, py, com.phoenix.game.content.Blocks.battery, 0);
        com.phoenix.game.world.Tile bat = Vars.world.tile(px + 2, py);
        if(bat != null && bat.entity != null) bat.entity.power.stored = 4000f;

        //幽灵工厂：2x2，塞硅与钛；再单独给它配一块电池（工厂要 0.5 电，没电就停工）
        com.phoenix.game.world.Tile factory = put(px + 4, py, com.phoenix.game.content.Blocks.wraithFactory, 0);
        for(int i = 0; i < 12 && factory != null && factory.entity != null; i++){
            factory.block().handleItem(com.phoenix.game.content.Items.silicon, factory, factory);
            factory.block().handleItem(com.phoenix.game.content.Items.titanium, factory, factory);
        }
        put(px + 6, py, com.phoenix.game.content.Blocks.battery, 0);
        com.phoenix.game.world.Tile facBat = Vars.world.tile(px + 6, py);
        if(facBat != null && facBat.entity != null) facBat.entity.power.stored = 4000f;

        //出厂时间调短，好在一次跑里看到单位真的出来
        if(com.phoenix.game.content.Blocks.wraithFactory != null){
            com.phoenix.game.content.Blocks.wraithFactory.produceTime = 200f;
        }

        logTestTiles = new com.phoenix.game.world.Tile[]{pad, factory, null};
        System.err.println("DBG unitproduce built at " + bx + "," + by
            + " pad=" + (pad != null && pad.entity != null)
            + " factory=" + (factory != null && factory.entity != null)
            + " unitType=" + (Vars.player.unit() == null ? "none" : Vars.player.unit().getType().name));
    }

    /**
     * 测试模式 21：沙盒方块 + 建筑配置面板。
     * <p>沙盒：
     * <ul>
     *   <li>电力源(10000/帧) + 电池：电池瞬间充满到 4000；</li>
     *   <li>第 150 帧加一个电力虚空：电池被抽干（虚空只吃存量，不制造需求）；</li>
     *   <li>物品源(配置成铜) → 传送带：带子上应该出现物品；</li>
     *   <li>物品源 → 传送带 → 物品虚空：物品被吃掉，不会堆在带子上。</li>
     * </ul>
     * <p>配置面板：第 200 帧把 {@code input.configTile} 指到分拣器上，让 BlockConfigFragment 真的渲染一次
     * （渲染异常会打到 stderr），配合截图确认面板内容。
     */
    private void buildSandboxConfigTest(){
        grantTestItems();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 3, by - 3, 24, 8);

        //电力：源 + 电池（虚空第 150 帧再加）
        put(bx, by, com.phoenix.game.content.Blocks.powerSource, 0);
        com.phoenix.game.world.Tile bat = put(bx + 1, by, com.phoenix.game.content.Blocks.battery, 0);

        //物品源 → 传送带（看带子上有没有货）
        com.phoenix.game.world.Tile src = put(bx + 4, by, com.phoenix.game.content.Blocks.itemSource, 0);
        if(src != null) src.configure(com.phoenix.game.content.Items.copper.id);
        com.phoenix.game.world.Tile belt1 = put(bx + 5, by, com.phoenix.game.content.Blocks.conveyor, 1);
        put(bx + 6, by, com.phoenix.game.content.Blocks.conveyor, 1);

        //物品源 → 传送带 → 物品虚空（带子应该一直空着）
        com.phoenix.game.world.Tile src2 = put(bx + 9, by, com.phoenix.game.content.Blocks.itemSource, 0);
        if(src2 != null) src2.configure(com.phoenix.game.content.Items.silicon.id);
        com.phoenix.game.world.Tile belt2 = put(bx + 10, by, com.phoenix.game.content.Blocks.conveyor, 1);
        put(bx + 11, by, com.phoenix.game.content.Blocks.itemVoid, 0);

        //配置面板目标：一个分拣器
        com.phoenix.game.world.Tile sorter = put(bx + 14, by, com.phoenix.game.content.Blocks.sorter, 1);
        if(sorter != null) sorter.configure(com.phoenix.game.content.Items.coal.id);

        //物品虚空：直接断言"收得下 + 收下不留存"（比看传送带上的在途物品数更明确）
        com.phoenix.game.world.Tile itemVoid = Vars.world.tile(bx + 11, by);
        boolean voidAccepts = itemVoid != null && itemVoid.entity != null
            && itemVoid.block().acceptItem(com.phoenix.game.content.Items.copper, itemVoid, null);
        if(itemVoid != null && itemVoid.entity != null){
            itemVoid.block().handleItem(com.phoenix.game.content.Items.copper, itemVoid, null);
        }
        int voidStored = itemVoid == null || itemVoid.entity == null || itemVoid.entity.items == null
            ? -1 : itemVoid.entity.items.total();

        logTestTiles = new com.phoenix.game.world.Tile[]{bat, null, belt1, belt2, sorter};
        System.err.println("DBG sandbox built at " + bx + "," + by
            + " bat=" + (bat != null && bat.entity != null)
            + " src=" + (src != null && src.entity != null)
            + " src2=" + (src2 != null && src2.entity != null)
            + " sorterCfg=" + (sorter == null || sorter.entity == null ? -1 : sorter.entity.config())
            + " powerSourceProd=" + com.phoenix.game.content.Blocks.powerSource.getPowerProduction(
                Vars.world.tile(bx, by))
            + " itemVoidAccepts=" + voidAccepts + " itemVoidStored=" + voidStored
            + " itemVoidTile=" + (itemVoid == null ? "null" : String.valueOf(itemVoid.blockRaw().name))
            + " itemVoidHasEntity=" + (itemVoid != null && itemVoid.entity != null)
            + " blocksItemVoid=" + (com.phoenix.game.content.Blocks.itemVoid == null ? "null" : com.phoenix.game.content.Blocks.itemVoid.name));
    }

    /**
     * 测试模式 22：蓝图预览图。
     * <p>摆一小片建筑 → 抓成蓝图 → 生成预览 FBO → 导出 PNG，检查：
     * <ul>
     *   <li>{@code getPreview} 返回的纹理尺寸 = (宽+padding)×resolution；</li>
     *   <li>导出的 PNG 存在且字节数合理（不是全透明的空图）；</li>
     *   <li>蓝图列表会因"数量变化"自动重建并显示缩略图（截图确认）。</li>
     * </ul>
     */
    private void buildSchematicPreviewTest(){
        grantTestItems();
        //放大一点，截图能看清蓝图列表里的缩略图
        if(Vars.renderer != null) Vars.renderer.setScale(3f);

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 3, by - 4, 16, 10);

        //摆一个"L 形 + 一个 2x2"的小基地，缩略图里能看出形状
        put(bx, by, com.phoenix.game.content.Blocks.conveyor, 1);
        put(bx + 1, by, com.phoenix.game.content.Blocks.conveyor, 1);
        put(bx + 2, by, com.phoenix.game.content.Blocks.router, 0);
        put(bx + 2, by + 1, com.phoenix.game.content.Blocks.conveyor, 0);
        put(bx + 2, by + 2, com.phoenix.game.content.Blocks.sorter, 0);
        put(bx + 4, by, com.phoenix.game.content.Blocks.battery, 0);
        put(bx + 5, by, com.phoenix.game.content.Blocks.powerNode, 0);
        put(bx + 4, by + 2, com.phoenix.game.content.Blocks.solarPanel, 0);

        com.phoenix.game.game.Schematics schematics = Vars.schematics;
        com.phoenix.game.game.Schematic schem = schematics.create(bx, by, bx + 5, by + 2);
        schem.tags.put("name", "phoenix-preview-test");

        //add() 内部会写 .msch 并顺手导出 .png
        schematics.add(schem);

        java.io.File png = schematics.previewFile(schem);
        com.badlogic.gdx.graphics.Texture preview = schematics.getPreview(schem);

        int texW = preview == null ? -1 : preview.getWidth();
        int texH = preview == null ? -1 : preview.getHeight();
        long pngBytes = png.exists() ? png.length() : -1;

        //读回 PNG 数一下不透明像素：防止"文件写出来了但其实是全透明的空图"
        int opaque = -1;
        try{
            com.badlogic.gdx.graphics.Pixmap pm = new com.badlogic.gdx.graphics.Pixmap(
                com.badlogic.gdx.Gdx.files.absolute(png.getAbsolutePath()));
            opaque = 0;
            for(int py = 0; py < pm.getHeight(); py++){
                for(int px = 0; px < pm.getWidth(); px++){
                    if((pm.getPixel(px, py) & 0xff) > 0) opaque++;
                }
            }
            pm.dispose();
        }catch(Exception e){
            opaque = -2;
        }

        logTestSchematic = schem;
        logTestTiles = new com.phoenix.game.world.Tile[]{Vars.world.tile(bx + 2, by + 2), null, null, null, null};

        System.err.println("DBG preview tiles=" + schem.tiles.size
            + " size=" + schem.width + "x" + schem.height
            + " expectTex=" + ((schem.width + com.phoenix.game.game.Schematics.padding) * com.phoenix.game.game.Schematics.resolution)
            + "x" + ((schem.height + com.phoenix.game.game.Schematics.padding) * com.phoenix.game.game.Schematics.resolution)
            + " tex=" + texW + "x" + texH
            + " png=" + png.getName() + " bytes=" + pngBytes + " opaquePx=" + opaque
            + " hasPreview=" + schematics.hasPreview(schem));
    }

    // ---------------- 模式 24：特效验证 ----------------

    /** 测试模式 24：摆一座炮塔 + 两个电力节点（看激光连线）+ 超频投影器，随后由帧驱动持续放置/拆除。 */
    private void buildEffectTest(){
        grantTestItems();
        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 3, by - 5, 16, 12);

        put(bx + 2, by + 2, com.phoenix.game.content.Blocks.duo, 1);
        put(bx + 4, by + 2, com.phoenix.game.content.Blocks.solarPanel, 0);
        put(bx + 6, by + 2, com.phoenix.game.content.Blocks.powerNode, 0);
        put(bx + 10, by + 2, com.phoenix.game.content.Blocks.powerNode, 0);
        put(bx + 12, by + 2, com.phoenix.game.content.Blocks.battery, 0);
        //超频投影器：验证范围波特效（同时给炮塔加时间倍率）
        put(bx + 8, by + 5, com.phoenix.game.content.Blocks.overdriveProjector, 0);

        System.err.println("DBG effect layout built");
    }

    /** 测试模式 27：炮口特效与激光束 —— 一排弹药各异的炮塔，验证「炮塔为 none 就退回弹药特效」与激光弹绘制。 */
    private void buildMuzzleTest(){
        grantTestItems();
        //缩小相机：要一屏容下整排炮塔（switch 里统一设的 3f 太近）
        Vars.renderer.setScale(1.5f);

        //测试用：激光束只存在 16 帧，按原版装填（80~90 帧）很难在抓帧时撞上，这里调短让光束接近常驻
        ((com.phoenix.game.world.blocks.defense.turrets.Turret)com.phoenix.game.content.Blocks.lancer).reload = 4f;
        ((com.phoenix.game.world.blocks.defense.turrets.Turret)com.phoenix.game.content.Blocks.meltdown).reload = 4f;

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 4, by - 6, 34, 16);
        fillLiquidArea(bx - 4, by - 6, 34, 16);

        //(炮塔, 弹药物品, x 偏移)：偏移按方块尺寸留出间距，避免大炮塔互相压盖。
        //物品为 null 表示该炮塔不耗弹药（耗电/纯装饰），跳过塞弹。
        Object[][] list = {
            {com.phoenix.game.content.Blocks.duo, com.phoenix.game.content.Items.copper, 0},
            {com.phoenix.game.content.Blocks.scorch, com.phoenix.game.content.Items.copper, 2},
            {com.phoenix.game.content.Blocks.hail, com.phoenix.game.content.Items.graphite, 4},
            {com.phoenix.game.content.Blocks.fuse, com.phoenix.game.content.Items.titanium, 8},
            {com.phoenix.game.content.Blocks.scatter, com.phoenix.game.content.Items.lead, 12},
            {com.phoenix.game.content.Blocks.swarmer, com.phoenix.game.content.Items.silicon, 15},
            {com.phoenix.game.content.Blocks.lancer, com.phoenix.game.content.Items.copper, 18},
            {com.phoenix.game.content.Blocks.meltdown, null, 22},
            {com.phoenix.game.content.Blocks.solarPanel, null, 26},
        };

        for(int i = 0; i < list.length; i++){
            com.phoenix.game.world.Block block = (com.phoenix.game.world.Block)list[i][0];
            com.phoenix.game.type.Item item = (com.phoenix.game.type.Item)list[i][1];
            int offset = (Integer)list[i][2];

            com.phoenix.game.world.Tile t = put(bx + offset, by, block, 1);
            //塞满弹药：ConsumeItems.valid 要求建筑库存里真的有对应物品
            if(item != null && t != null && t.entity != null && t.entity.items != null){
                t.entity.items.add(item, 500);
            }
            if(t == null || t.block() != block){
                System.err.println("DBG muzzle place FAILED " + block.name + " offset=" + offset);
            }
        }

        System.err.println("DBG muzzle turrets built=" + list.length);
    }

    /**
     * 测试模式 28：火焰系统。
     * <p>布局：一排装满高可燃物品（pyratite，可燃性 1.4）的容器——火会在这排容器上蔓延、烧建筑、偶尔甩火球；
     * 另外两块对照火点（水域 / 草地空地）没有燃料，应当快速熄灭。
     */
    private void buildFireTest(){
        grantTestItems();
        //拉近镜头：火焰粒子很小，远景抓帧看不清（switch 里统一设的 3f 偏远）
        Vars.renderer.setScale(5f);

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 2, by - 4, 16, 10);
        //容器不能建在水上，先把这片区域的液体地板填成陆地
        fillLiquidArea(bx - 2, by - 4, 16, 10);

        logTestTiles = new com.phoenix.game.world.Tile[6];

        //可燃排：3 个 2x2 容器（storage），各装 100 pyratite
        for(int i = 0; i < 3; i++){
            com.phoenix.game.world.Tile t = put(bx + i * 2, by, com.phoenix.game.content.Blocks.storage, 0);
            logTestTiles[i] = t;
            if(t != null && t.entity != null && t.entity.items != null){
                t.entity.items.add(com.phoenix.game.content.Items.pyratite, 100);
            }
            if(t == null || t.block() != com.phoenix.game.content.Blocks.storage){
                System.err.println("DBG fire place FAILED i=" + i);
            }
        }

        //对照 A：水域（无燃料 → 应 8 倍速烧完）
        for(int i = 0; i < 4; i++){
            com.phoenix.game.world.Tile t = Vars.world.tile(bx + 8 + i, by + 4);
            if(t != null) t.setFloor(com.phoenix.game.content.Blocks.water);
        }
        logTestTiles[3] = Vars.world.tile(bx + 9, by + 4);

        //对照 B：草地空地（无燃料）
        logTestTiles[4] = Vars.world.tile(bx + 4, by + 4);

        for(int i = 0; i < 5; i++){
            if(logTestTiles[i] != null) com.phoenix.game.entities.effect.Fire.create(logTestTiles[i]);
        }

        System.err.println("DBG fire built count=" + com.phoenix.game.entities.effect.Fire.count());
    }

    /** 测试模式 26：反射收集 Fx 里所有特效，铺成网格反复重放（特效移植的视觉验证）。 */
    private void buildEffectGalleryTest(){
        Vars.renderer.setScale(1.5f);
        galleryEffects = new java.util.ArrayList<>();
        galleryNames = new java.util.ArrayList<>();

        for(java.lang.reflect.Field f : com.phoenix.game.content.Fx.class.getFields()){
            if(f.getType() != com.phoenix.game.entities.Effects.Effect.class) continue;
            if(!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            try{
                com.phoenix.game.entities.Effects.Effect effect = (com.phoenix.game.entities.Effects.Effect)f.get(null);
                //lifetime<=0 的（Fx.none）永远不会被播放，跳过
                if(effect == null || effect.lifetime <= 0f) continue;
                galleryEffects.add(effect);
                galleryNames.add(f.getName());
            }catch(Exception ignored){
            }
        }

        System.err.println("DBG gallery collected=" + galleryEffects.size());
    }

    /** 把所有特效在玩家周围铺成 12 列的网格重放一遍。 */
    private void playGallery(){
        float px = Vars.player.getX(), py = Vars.player.getY();
        int cols = 12;
        float step = 30f;

        for(int i = 0; i < galleryEffects.size(); i++){
            float x = px + (i % cols - cols / 2f) * step;
            float y = py + (4f - i / cols) * step;
            //统一给一个"中等"旋转量：方块类特效当边长倍数、波类特效当最大半径、拖尾当初始半径
            com.phoenix.game.entities.Effects.effect(galleryEffects.get(i),
                com.phoenix.game.graphics.Pal.accent, x, y, 6f, null);
        }
    }

    // ---------------- 模式 23：存档往返 ----------------

    /** 测试模式 23：搭一片"状态丰富"的建筑（库存/配置/进度/传送带物品）→ 存档 → 破坏世界 → 读档 → 比对。 */
    private void buildSaveLoadTest(){
        grantTestItems();
        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        clearArea(bx - 2, by - 3, 12, 9);

        //传送带链：末格带上一个铜（测 ConveyorEntity 的并行数组）
        put(bx, by, com.phoenix.game.content.Blocks.conveyor, 1);
        put(bx + 1, by, com.phoenix.game.content.Blocks.conveyor, 1);
        com.phoenix.game.world.Tile last = put(bx + 2, by, com.phoenix.game.content.Blocks.conveyor, 1);
        if(last != null && last.entity instanceof com.phoenix.game.world.blocks.distribution.Conveyor.ConveyorEntity){
            com.phoenix.game.world.blocks.distribution.Conveyor.ConveyorEntity ce =
                (com.phoenix.game.world.blocks.distribution.Conveyor.ConveyorEntity)last.entity;
            ce.ids[0] = com.phoenix.game.content.Items.copper;
            ce.xs[0] = 0.25f;
            ce.ys[0] = 0.5f;
            ce.len = 1;
        }

        //分拣器：带配置
        com.phoenix.game.world.Tile sorter = put(bx + 3, by, com.phoenix.game.content.Blocks.sorter, 1);
        if(sorter != null && sorter.entity != null) sorter.configure(com.phoenix.game.content.Items.lead.id);

        //炮塔：带炮管角度与装填
        com.phoenix.game.world.Tile turret = put(bx, by + 2, com.phoenix.game.content.Blocks.duo, 1);
        if(turret != null && turret.entity instanceof com.phoenix.game.world.blocks.defense.turrets.Turret.TurretEntity){
            com.phoenix.game.world.blocks.defense.turrets.Turret.TurretEntity te =
                (com.phoenix.game.world.blocks.defense.turrets.Turret.TurretEntity)turret.entity;
            te.rotation = 137f;
            te.reload = 12.5f;
        }

        //电池：带电量
        put(bx + 1, by + 2, com.phoenix.game.content.Blocks.battery, 0);

        //钻头：带采集进度与启动进度
        com.phoenix.game.world.Tile drill = put(bx + 3, by + 2, com.phoenix.game.content.Blocks.copperDrill, 0);
        if(drill != null && drill.entity instanceof com.phoenix.game.world.blocks.production.Drill.DrillEntity){
            com.phoenix.game.world.blocks.production.Drill.DrillEntity de =
                (com.phoenix.game.world.blocks.production.Drill.DrillEntity)drill.entity;
            de.progress = 33.5f;
            de.warmup = 0.625f;
        }

        //在建方块：BuildEntity 的进度 + 记账数组
        com.phoenix.game.world.Tile build = put(bx + 5, by, com.phoenix.game.world.blocks.BuildBlock.get(1), 0);
        if(build != null && build.entity instanceof com.phoenix.game.world.blocks.BuildBlock.BuildEntity){
            com.phoenix.game.world.blocks.BuildBlock.BuildEntity be =
                (com.phoenix.game.world.blocks.BuildBlock.BuildEntity)build.entity;
            be.setConstruct(com.phoenix.game.content.Blocks.air, com.phoenix.game.content.Blocks.conveyor);
            be.progress = 0.375f;
        }

        //物品源：带配置
        com.phoenix.game.world.Tile source = put(bx + 5, by + 2, com.phoenix.game.content.Blocks.itemSource, 0);
        if(source != null && source.entity != null) source.configure(com.phoenix.game.content.Items.titanium.id);

        logTestSaveFile = com.badlogic.gdx.Gdx.files.local("saves/saveload-test.msav").file();
        System.err.println("DBG saveload layout built blocks=" + countBlocks()
            + " sharded=" + Vars.state.teams.items(com.phoenix.game.game.Team.sharded).total());
    }

    /** 模式 23：把世界改得面目全非，确保后面的读档确实是从文件恢复的。 */
    private void damageWorldForSaveTest(){
        Vars.state.teams.items(com.phoenix.game.game.Team.sharded).clear();

        int bx = Vars.world.toTile(Vars.player.getX()) + 3;
        int by = Vars.world.toTile(Vars.player.getY());
        for(int x = bx - 2; x <= bx + 8; x++){
            for(int y = by - 3; y <= by + 4; y++){
                com.phoenix.game.world.Tile t = Vars.world.tile(x, y);
                if(t != null && t.blockRaw() != com.phoenix.game.content.Blocks.air){
                    t.setBlock(com.phoenix.game.content.Blocks.air);
                }
            }
        }
        Vars.state.wave = 99;
    }

    /** 模式 23：读档后重建玩家（对应 Control.load，避免 player 还引用旧世界的单位）。 */
    private void rebuildPlayerAfterLoad(){
        Vars.player = new com.phoenix.game.entities.type.Player();
        Vars.state.gameOver = false;
        com.phoenix.game.core.Events.fire(new com.phoenix.game.game.EventType.WorldLoadEvent());
        Vars.control.spawnPlayerUnit(com.phoenix.game.game.Team.sharded);
    }

    /** @return 世界里的非空方块数。 */
    private int countBlocks(){
        int count = 0;
        for(com.phoenix.game.world.Tile t : Vars.world.tiles){
            if(t != null && t.blockRaw() != com.phoenix.game.content.Blocks.air) count++;
        }
        return count;
    }

    /** @return 存档 meta 的摘要（用于确认 meta 区本身也写对了）。 */
    private String describeMeta(java.io.File file){
        try{
            com.phoenix.game.io.SaveMeta m = com.phoenix.game.io.SaveIO.readMeta(file);
            return "wave=" + m.wave + " map=" + m.mapname + " size=" + m.width + "x" + m.height
                + " playtime=" + m.playtime + " sandbox=" + m.rules.sandbox;
        }catch(Exception e){
            return "FAILED:" + e;
        }
    }

    /**
     * 模式 23：把"存档应当保存下来的全部状态"编成一个可比对的字符串。
     * <p>刻意**不包含**单位与子弹（读档后会重新生成玩家单位，属于预期差异）。
     */
    private String saveFingerprint(){
        StringBuilder sb = new StringBuilder();
        com.phoenix.game.core.World world = Vars.world;

        for(int y = 0; y < world.height(); y++){
            for(int x = 0; x < world.width(); x++){
                com.phoenix.game.world.Tile t = world.tile(x, y);
                com.phoenix.game.world.Block b = t.blockRaw();
                if(b == com.phoenix.game.content.Blocks.air) continue;

                sb.append(x).append(',').append(y).append(':').append(b.name)
                    .append('/').append(t.getTeam().id).append('/').append(t.rotation());

                com.phoenix.game.entities.type.TileEntity e = t.entity;
                if(e != null){
                    sb.append(" h=").append(Math.round(e.health()));
                    if(e.items != null) sb.append(" i=").append(e.items.total());
                    if(e.power != null) sb.append(" p=").append(Math.round(e.power.stored));
                    sb.append(" cfg=").append(e.config());
                    String extra = entityState(e);
                    if(extra.length() > 0) sb.append(' ').append(extra);
                }
                sb.append(';');
            }
        }

        for(com.phoenix.game.game.Team team : com.phoenix.game.game.Team.base()){
            if(team == null) continue;
            sb.append('T').append(team.id).append('=')
                .append(Vars.state.teams.items(team).total()).append(';');
        }
        sb.append("wave=").append(Vars.state.wave);
        return sb.toString();
    }

    /** @return 各实体类型里"必须存下来"的内部字段（与 TileEntity 子类的 write/read 一一对应）。 */
    private String entityState(com.phoenix.game.entities.type.TileEntity e){
        StringBuilder sb = new StringBuilder();

        if(e instanceof com.phoenix.game.world.blocks.BuildBlock.BuildEntity){
            com.phoenix.game.world.blocks.BuildBlock.BuildEntity b =
                (com.phoenix.game.world.blocks.BuildBlock.BuildEntity)e;
            sb.append("cb=").append(b.cblock == null ? "null" : b.cblock.name)
                .append(",pr=").append(Math.round(b.progress * 1000f))
                .append(",cost=").append(Math.round(b.buildCost * 100f));
        }else if(e instanceof com.phoenix.game.world.blocks.defense.turrets.Turret.TurretEntity){
            com.phoenix.game.world.blocks.defense.turrets.Turret.TurretEntity t =
                (com.phoenix.game.world.blocks.defense.turrets.Turret.TurretEntity)e;
            sb.append("rot=").append(Math.round(t.rotation * 100f))
                .append(",rl=").append(Math.round(t.reload * 100f));
        }else if(e instanceof com.phoenix.game.world.blocks.distribution.Conveyor.ConveyorEntity){
            com.phoenix.game.world.blocks.distribution.Conveyor.ConveyorEntity c =
                (com.phoenix.game.world.blocks.distribution.Conveyor.ConveyorEntity)e;
            sb.append("len=").append(c.len);
            for(int i = 0; i < c.len; i++){
                sb.append(",[").append(c.ids[i] == null ? "null" : c.ids[i].name)
                    .append('@').append(Math.round(c.ys[i] * 100f)).append(']');
            }
        }else if(e instanceof com.phoenix.game.world.blocks.production.Drill.DrillEntity){
            com.phoenix.game.world.blocks.production.Drill.DrillEntity d =
                (com.phoenix.game.world.blocks.production.Drill.DrillEntity)e;
            sb.append("pr=").append(Math.round(d.progress * 100f))
                .append(",wu=").append(Math.round(d.warmup * 1000f));
        }else if(e instanceof com.phoenix.game.world.blocks.production.GenericCrafter.CraftEntity){
            sb.append("pr=").append(Math.round(
                ((com.phoenix.game.world.blocks.production.GenericCrafter.CraftEntity)e).progress * 100f));
        }else if(e instanceof com.phoenix.game.world.blocks.power.ItemGenerator.GeneratorEntity){
            com.phoenix.game.world.blocks.power.ItemGenerator.GeneratorEntity g =
                (com.phoenix.game.world.blocks.power.ItemGenerator.GeneratorEntity)e;
            sb.append("gt=").append(Math.round(g.generateTime * 1000f))
                .append(",pe=").append(Math.round(g.productionEfficiency * 1000f));
        }else if(e instanceof com.phoenix.game.world.blocks.power.ThoriumReactor.ReactorEntity){
            sb.append("ft=").append(Math.round(
                ((com.phoenix.game.world.blocks.power.ThoriumReactor.ReactorEntity)e).fuelTimer * 100f));
        }else if(e instanceof com.phoenix.game.world.blocks.power.ImpactReactor.FusionReactorEntity){
            com.phoenix.game.world.blocks.power.ImpactReactor.FusionReactorEntity f =
                (com.phoenix.game.world.blocks.power.ImpactReactor.FusionReactorEntity)e;
            sb.append("wu=").append(Math.round(f.warmup * 1000f))
                .append(",ft=").append(Math.round(f.fuelTimer * 100f));
        }else if(e instanceof com.phoenix.game.world.blocks.distribution.ItemBridge.ItemBridgeEntity){
            sb.append("link=").append(((com.phoenix.game.world.blocks.distribution.ItemBridge.ItemBridgeEntity)e).link);
        }else if(e instanceof com.phoenix.game.world.blocks.distribution.MassDriver.MassDriverEntity){
            com.phoenix.game.world.blocks.distribution.MassDriver.MassDriverEntity m =
                (com.phoenix.game.world.blocks.distribution.MassDriver.MassDriverEntity)e;
            sb.append("link=").append(m.link)
                .append(",br=").append(Math.round(m.barrelRotation * 100f))
                .append(",rl=").append(Math.round(m.reload * 100f))
                .append(",st=").append(m.state.ordinal());
        }else if(e instanceof com.phoenix.game.world.blocks.distribution.Sorter.SorterEntity){
            com.phoenix.game.world.blocks.distribution.Sorter.SorterEntity s =
                (com.phoenix.game.world.blocks.distribution.Sorter.SorterEntity)e;
            sb.append("item=").append(s.sortItem == null ? "null" : s.sortItem.name);
        }else if(e instanceof com.phoenix.game.world.blocks.units.UnitFactory.UnitFactoryEntity){
            sb.append("bt=").append(Math.round(
                ((com.phoenix.game.world.blocks.units.UnitFactory.UnitFactoryEntity)e).buildTime * 100f));
        }else if(e instanceof com.phoenix.game.world.blocks.units.RepairPoint.RepairPointEntity){
            sb.append("rot=").append(Math.round(
                ((com.phoenix.game.world.blocks.units.RepairPoint.RepairPointEntity)e).rotation * 100f));
        }else if(e instanceof com.phoenix.game.world.blocks.defense.ForceProjector.ShieldEntity){
            com.phoenix.game.world.blocks.defense.ForceProjector.ShieldEntity s =
                (com.phoenix.game.world.blocks.defense.ForceProjector.ShieldEntity)e;
            sb.append("sh=").append(Math.round(s.shield * 100f)).append(",brk=").append(s.broken);
        }else if(e instanceof com.phoenix.game.world.blocks.defense.Mender.MendEntity){
            sb.append("ch=").append(Math.round(
                ((com.phoenix.game.world.blocks.defense.Mender.MendEntity)e).charge * 100f));
        }else if(e instanceof com.phoenix.game.world.blocks.defense.MendProjector.MendEntity){
            sb.append("ch=").append(Math.round(
                ((com.phoenix.game.world.blocks.defense.MendProjector.MendEntity)e).charge * 100f));
        }else if(e instanceof com.phoenix.game.world.blocks.defense.OverdriveProjector.OverdriveEntity){
            sb.append("ch=").append(Math.round(
                ((com.phoenix.game.world.blocks.defense.OverdriveProjector.OverdriveEntity)e).charge * 100f));
        }else if(e instanceof com.phoenix.game.world.blocks.defense.ShockMine.MineEntity){
            sb.append("tm=").append(Math.round(
                ((com.phoenix.game.world.blocks.defense.ShockMine.MineEntity)e).timer * 100f));
        }else if(e instanceof com.phoenix.game.world.blocks.sandbox.ItemSource.ItemSourceEntity){
            com.phoenix.game.world.blocks.sandbox.ItemSource.ItemSourceEntity s =
                (com.phoenix.game.world.blocks.sandbox.ItemSource.ItemSourceEntity)e;
            sb.append("out=").append(s.outputItem == null ? "null" : s.outputItem.name);
        }else if(e instanceof com.phoenix.game.world.blocks.storage.Unloader.UnloaderEntity){
            com.phoenix.game.world.blocks.storage.Unloader.UnloaderEntity u =
                (com.phoenix.game.world.blocks.storage.Unloader.UnloaderEntity)e;
            sb.append("item=").append(u.sortItem == null ? "null" : u.sortItem.name);
        }

        return sb.toString();
    }

    /** @return 两个字符串第一处差异的描述（比对失败时定位用）。 */
    private String firstDifference(String a, String b){
        int limit = Math.min(a.length(), b.length());
        for(int i = 0; i < limit; i++){
            if(a.charAt(i) != b.charAt(i)){
                int from = Math.max(0, i - 40);
                return "index " + i + "\n  A: ..." + a.substring(from, Math.min(a.length(), i + 40))
                    + "\n  B: ..." + b.substring(from, Math.min(b.length(), i + 40));
            }
        }
        if(a.length() != b.length()){
            return "length " + a.length() + " vs " + b.length();
        }
        return "none";
    }

    private void grantTestItems(){
        //给全部物品各一批：新方块的材料要求五花八门（钛/钍/硅…），少一样就会静默放置失败
        com.phoenix.game.world.modules.ItemModule inv = Vars.state.teams.items(Vars.player.getTeam());
        for(int i = 0; i < com.phoenix.game.content.Items.all.size; i++){
            inv.add(com.phoenix.game.content.Items.all.get(i), 3000);
        }
    }

    /**
     * 清出一块空地，**只清自然地形（岩石/墙）**。
     * <p>注意不能无脑清空：地图是随机生成的，玩家核心可能正好落在这块区域里，
     * 拆掉自家核心会触发判负 → 回菜单 → 后续帧钩子不再执行（表现为"卡住"）。
     * 判据用 {@code synthetic()}（有 update/destructible 的是玩家建筑），并排除多格卫星格。
     */
    private void clearArea(int bx, int by, int w, int h){
        for(int dx = 0; dx < w; dx++){
            for(int dy = 0; dy < h; dy++){
                com.phoenix.game.world.Tile t = Vars.world.tile(bx + dx, by + dy);
                if(t == null) continue;

                com.phoenix.game.world.Block b = t.blockRaw();
                if(!b.synthetic() && !(b instanceof com.phoenix.game.world.blocks.BlockPart)){
                    t.setBlock(com.phoenix.game.content.Blocks.air);
                }
            }
        }
    }

    /**
     * 把区域内的液体地板填成陆地。
     * <p>建筑不能建在液体上（{@code placeBlock} 会静默失败），而 {@link #clearArea} 只清方块、不动地板，
     * 所以布局落在水域时会出现「日志说 built=N、场上却只有几个建筑」。
     */
    private void fillLiquidArea(int bx, int by, int w, int h){
        //先找一块现成的陆地地板当填充物，避免依赖具体地板名
        com.phoenix.game.world.Floor land = null;
        for(int dx = 0; dx < w && land == null; dx++){
            for(int dy = 0; dy < h; dy++){
                com.phoenix.game.world.Tile t = Vars.world.tile(bx + dx, by + dy);
                if(t != null && t.floor() != null && !t.floor().isLiquid
                    && t.floor() != com.phoenix.game.content.Blocks.air){
                    land = t.floor();
                    break;
                }
            }
        }

        if(land == null) return;

        for(int dx = 0; dx < w; dx++){
            for(int dy = 0; dy < h; dy++){
                com.phoenix.game.world.Tile t = Vars.world.tile(bx + dx, by + dy);
                if(t != null && t.floor() != null && t.floor().isLiquid){
                    t.setFloor(land);
                }
            }
        }
    }

    private com.phoenix.game.world.Tile put(int x, int y, com.phoenix.game.world.Block block, int rotation){
        com.phoenix.game.world.Tile t = Vars.world.tile(x, y);
        if(t != null){
            com.phoenix.game.world.Build.placeBlock(t, block, Vars.player.getTeam(), rotation);
        }
        return t;
    }

    private void fillConveyor(com.phoenix.game.world.Tile t, com.phoenix.game.type.Item item, int count){
        if(t == null || !(t.entity instanceof com.phoenix.game.world.blocks.distribution.Conveyor.ConveyorEntity)) return;

        com.phoenix.game.world.blocks.distribution.Conveyor.ConveyorEntity e =
            (com.phoenix.game.world.blocks.distribution.Conveyor.ConveyorEntity)t.entity;
        for(int i = 0; i < count; i++){
            e.add(0);
            e.ids[0] = item;
            e.ys[0] = i * 0.4f;
            e.items.add(item, 1);
        }
    }

    /** 往统计串里追加一个瓦片的电网状态：储能 / 网络规模 / 发耗 / 满足率。 */
    private void appendPower(StringBuilder sb, com.phoenix.game.world.Tile t){
        if(t == null || t.entity == null || t.entity.power == null){
            sb.append(" -");
            return;
        }

        com.phoenix.game.world.blocks.power.PowerGraph g = t.entity.power.graph;
        sb.append(" [").append((int)t.entity.power.stored)
          .append(" n=").append(g == null ? -1 : g.size())
          .append(" p=").append(g == null ? -1f : g.getPowerProduced())
          .append(" d=").append(g == null ? -1f : g.getPowerNeeded())
          .append(" s=").append(g == null ? -1f : g.getSatisfaction())
          .append(']');
    }

    /** @return 传送带上的物品数；不是传送带返回 -1。 */
    private int conveyorCount(com.phoenix.game.world.Tile t){
        if(t == null || !(t.entity instanceof com.phoenix.game.world.blocks.distribution.Conveyor.ConveyorEntity)) return -1;
        return ((com.phoenix.game.world.blocks.distribution.Conveyor.ConveyorEntity)t.entity).len;
    }

    private void printLogTestStats(){
        if(Vars.world == null || Vars.player == null) return;

        StringBuilder sb = new StringBuilder("DBG chain");
        if(logTestTiles != null){
            for(com.phoenix.game.world.Tile t : logTestTiles){
                if(t == null || t.entity == null){
                    sb.append(" -");
                }else if(t.entity instanceof com.phoenix.game.world.blocks.distribution.Conveyor.ConveyorEntity){
                    sb.append(' ').append(((com.phoenix.game.world.blocks.distribution.Conveyor.ConveyorEntity)t.entity).len);
                }else{
                    sb.append(" B");
                }
            }
        }

        if(logTestStores != null){
            for(int i = 0; i < logTestStores.length; i++){
                com.phoenix.game.world.Tile t = logTestStores[i];
                //墙没有物品模块（hasItems=false），所以 items 判空要单独处理
                if(t == null || t.entity == null){
                    sb.append(" | store").append(i).append("=-");
                    continue;
                }

                sb.append(" | store").append(i).append(" hp").append((int)t.entity.health());

                if(t.entity.items == null) continue;

                int cu = t.entity.items.get(com.phoenix.game.content.Items.copper);
                int le = t.entity.items.get(com.phoenix.game.content.Items.lead);
                int sd = t.entity.items.get(com.phoenix.game.content.Items.sand);
                sb.append(" cu").append(cu).append(" pb").append(le)
                  .append(" sd").append(sd).append(" tot").append(t.entity.items.total());
            }
        }

        //模式 14：电网统计（电池储量 / 全网发耗 / 满足率 / 连通规模 / 发电机燃烧剩余）
        if(logTestMode == 14 && logTestTiles != null && logTestTiles.length >= 2){
            com.phoenix.game.world.Tile bat = logTestTiles[0];
            com.phoenix.game.world.Tile burn = logTestTiles[1];

            if(bat != null && bat.entity != null && bat.entity.power != null){
                com.phoenix.game.world.blocks.power.PowerGraph g = bat.entity.power.graph;
                sb.append(" | P bat=").append((int)bat.entity.power.stored)
                  .append(" prod=").append(g == null ? -1f : g.getPowerProduced())
                  .append(" need=").append(g == null ? -1f : g.getPowerNeeded())
                  .append(" sat=").append(g == null ? -1f : g.getSatisfaction())
                  .append(" gsize=").append(g == null ? -1 : g.size());
            }

            if(burn != null && burn.entity instanceof com.phoenix.game.world.blocks.power.ItemGenerator.GeneratorEntity){
                com.phoenix.game.world.blocks.power.ItemGenerator.GeneratorEntity be =
                    (com.phoenix.game.world.blocks.power.ItemGenerator.GeneratorEntity)burn.entity;
                sb.append(" eff=").append(be.productionEfficiency)
                  .append(" coal=").append(burn.entity.items == null ? -1 : burn.entity.items.get(com.phoenix.game.content.Items.coal));
            }
        }

        //模式 15：链 A（节点连线）/ 链 B（二极管单向）各节点的电网状态
        if(logTestMode == 15 && logTestTiles != null && logTestTiles.length >= 5){
            sb.append(" | A");
            appendPower(sb, logTestTiles[0]);
            appendPower(sb, logTestTiles[1]);
            sb.append(" | B");
            appendPower(sb, logTestTiles[2]);
            appendPower(sb, logTestTiles[3]);
            appendPower(sb, logTestTiles[4]);
        }

        //模式 16：四台发电机的实际出力
        if(logTestMode == 16 && logTestTiles != null && logTestTiles.length >= 4){
            sb.append(" | G");
            appendPower(sb, logTestTiles[0]);
            appendPower(sb, logTestTiles[1]);
            appendPower(sb, logTestTiles[2]);
            appendPower(sb, logTestTiles[3]);
        }

        //模式 17：建造队列（占位方块 / 进度 / 队列长度 / 材料消耗）
        if(logTestMode == 17 && logTestTiles != null){
            sb.append(" | Q q=").append(Vars.player.unit() == null ? -1 : Vars.player.unit().buildQueue.size);
            for(com.phoenix.game.world.Tile t : logTestTiles){
                if(t == null || t.entity == null){
                    sb.append(" -");
                    continue;
                }
                sb.append(" [").append(t.blockRaw() == null ? "null" : t.blockRaw().name);
                if(t.entity instanceof com.phoenix.game.world.blocks.BuildBlock.BuildEntity){
                    com.phoenix.game.world.blocks.BuildBlock.BuildEntity be =
                        (com.phoenix.game.world.blocks.BuildBlock.BuildEntity)t.entity;
                    sb.append(" ").append((int)(be.progress * 100)).append("%")
                      .append(" -> ").append(be.cblock == null ? "none" : be.cblock.name);
                }
                sb.append(']');
            }
        }

        //模式 18：粘贴结果（方块名 + 分拣器配置是否跟着蓝图过来了）
        if(logTestMode == 18 && logTestTiles != null){
            sb.append(" | S paste");
            for(com.phoenix.game.world.Tile t : logTestTiles){
                if(t == null || t.entity == null || t.blockRaw() == null){
                    sb.append(" -");
                    continue;
                }
                sb.append(" [").append(t.blockRaw().name);
                if(t.entity.config() >= 0) sb.append(" cfg").append(t.entity.config());
                sb.append(']');
            }
        }

        //模式 19：RTS 命令系统 —— 命令单位的位置与命令目标（应能看到它们向目标移动）
        if(logTestMode == 19 && !rtsTestUnits.isEmpty()){
            sb.append(" | RTS n=").append(rtsTestUnits.size);
            for(int i = 0; i < rtsTestUnits.size; i++){
                BaseUnit unit = rtsTestUnits.get(i);
                if(unit.isDead()){
                    sb.append(" [dead]");
                    continue;
                }
                sb.append(" [").append((int)unit.getX()).append(',').append((int)unit.getY());
                if(unit.commandAI != null){
                    if(unit.commandAI.targetPos != null){
                        sb.append(" dst=").append((int)unit.commandAI.targetPos.x).append(',').append((int)unit.commandAI.targetPos.y);
                    }else{
                        sb.append(" arrived");
                    }
                }else{
                    sb.append(" noai");
                }
                sb.append(']');
            }
        }

        //模式 20：机甲平台换装 + 单位工厂产出
        if(logTestMode == 20 && logTestTiles != null){
            com.phoenix.game.world.Tile pad = logTestTiles[0];
            com.phoenix.game.world.Tile factory = logTestTiles.length > 1 ? logTestTiles[1] : null;

            if(pad != null && pad.entity instanceof com.phoenix.game.world.blocks.units.MechPad.MechPadEntity){
                com.phoenix.game.world.blocks.units.MechPad.MechPadEntity pe =
                    (com.phoenix.game.world.blocks.units.MechPad.MechPadEntity)pad.entity;
                sb.append(" | U pad=").append((int)(pe.progress * 100)).append("%")
                  .append(" busy=").append(pe.player != null);
            }else{
                sb.append(" | U pad=-");
            }

            sb.append(" type=").append(Vars.player.unit() == null ? "none" : Vars.player.unit().getType().name);

            if(factory != null && factory.entity instanceof com.phoenix.game.world.blocks.units.UnitFactory.UnitFactoryEntity){
                com.phoenix.game.world.blocks.units.UnitFactory.UnitFactoryEntity fe =
                    (com.phoenix.game.world.blocks.units.UnitFactory.UnitFactoryEntity)factory.entity;
                sb.append(" fac=").append((int)(fe.progress() * 100)).append("%")
                  .append(" alive=").append(fe.aliveCount())
                  .append(" si=").append(factory.entity.items == null ? -1 : factory.entity.items.get(com.phoenix.game.content.Items.silicon));
            }
        }

        //模式 21：沙盒方块（电力源/虚空、物品源/虚空）
        if(logTestMode == 21 && logTestTiles != null){
            com.phoenix.game.world.Tile bat = logTestTiles[0];
            com.phoenix.game.world.Tile belt1 = logTestTiles[2];
            com.phoenix.game.world.Tile belt2 = logTestTiles[3];

            sb.append(" | K bat=");
            if(bat != null && bat.entity != null && bat.entity.power != null){
                sb.append((int)bat.entity.power.stored)
                  .append(" prod=").append(bat.entity.power.graph == null ? -1f : bat.entity.power.graph.getPowerProduced());
            }else{
                sb.append('-');
            }
            sb.append(" void=").append(logTestTiles[1] != null);
            sb.append(" belt1=").append(conveyorCount(belt1));
            sb.append(" belt2=").append(conveyorCount(belt2));
            sb.append(" cfgTile=").append(Vars.control != null && Vars.control.input != null
                ? (Vars.control.input.configTile == null ? "none" : Vars.control.input.configTile.blockRaw().name) : "?");
        }

        //全局库存（跨区运输结果）
        if(Vars.data != null){
            sb.append(" | globalCu=").append(Vars.data.getItem(com.phoenix.game.content.Items.copper));
        }

        com.phoenix.game.world.modules.ItemModule team = Vars.state.teams.items(Vars.player.getTeam());
        sb.append(" | teamCu=").append(team.get(com.phoenix.game.content.Items.copper))
          .append(" teamPb=").append(team.get(com.phoenix.game.content.Items.lead));
        //走 stderr：stdout 在进程被强杀时会被缓冲吞掉
        System.err.println(sb);
    }
    //DEBUG-LOGTEST-END

    @Override
    public void resize(int width, int height) {
        //注意：centerCamera 必须为 false，否则窗口变化时相机位置会被重置到屏幕中心，
        //把 Control.play() 设置好的出生点视角覆盖掉（表现为地图偏移、大量黑边）
        viewport.update(width, height, false);

        //HUD 视口独立更新（centerCamera = true，它只影响 UI 相机）
        if(stageViewport != null){
            stageViewport.update(width, height, true);
        }

        Constants.width = viewport.getWorldWidth();
        Constants.height = viewport.getWorldHeight();
    }

    @Override
    public void dispose() {
        //小地图纹理是自己 new 的（不走 AssetManager），要显式释放
        renderer.minimap.dispose();
        //蓝图预览图是运行时 new 的 FrameBuffer，也要显式释放
        if(Vars.schematics != null) Vars.schematics.disposePreviews();
        batch.dispose();
        Core.atlas.dispose();
    }
}
