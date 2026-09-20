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
        if(System.getenv("PHOENIX_AUTOPLAY") != null){
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
    private int[] logTestStoreBase;
    /** 看门狗线程（死循环定位用）。 */
    private Thread watchdog;
    //DEBUG-LOGTEST-END

    @Override
    public void render() {
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

            if(logTestFrame > 10 && logTestFrame % 30 == 0){
                printLogTestStats();
            }
        }

        if(logTestFrame >= logTestEnd){
            System.out.println("DBG logtest end frame=" + logTestFrame
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
        System.out.println("DBG chain built at " + bx + "," + by);
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
        System.out.println("DBG router built at " + bx + "," + by);
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
        System.out.println("DBG sorter built at " + bx + "," + by);
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
        System.out.println("DBG junction built at " + bx + "," + by);
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
        System.out.println("DBG unloader built at " + bx + "," + by
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
        System.out.println("DBG corelink built at " + bx + "," + by);
    }

    private void grantTestItems(){
        //给全部物品各一批：新方块的材料要求五花八门（钛/钍/硅…），少一样就会静默放置失败
        com.phoenix.game.world.modules.ItemModule inv = Vars.state.teams.items(Vars.player.getTeam());
        for(int i = 0; i < com.phoenix.game.content.Items.all.size; i++){
            inv.add(com.phoenix.game.content.Items.all.get(i), 3000);
        }
    }

    private void clearArea(int bx, int by, int w, int h){
        for(int dx = 0; dx < w; dx++){
            for(int dy = 0; dy < h; dy++){
                com.phoenix.game.world.Tile t = Vars.world.tile(bx + dx, by + dy);
                if(t != null) t.setBlock(com.phoenix.game.content.Blocks.air);
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
                if(t == null || t.entity == null || t.entity.items == null){
                    sb.append(" | store").append(i).append("=-");
                    continue;
                }
                int cu = t.entity.items.get(com.phoenix.game.content.Items.copper);
                int le = t.entity.items.get(com.phoenix.game.content.Items.lead);
                sb.append(" | store").append(i).append(" cu").append(cu).append(" pb").append(le);
            }
        }

        int copper = Vars.state.teams.items(Vars.player.getTeam()).get(com.phoenix.game.content.Items.copper);
        sb.append(" | teamCu=").append(copper);
        System.out.println(sb);
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
        batch.dispose();
        Core.atlas.dispose();
    }
}
