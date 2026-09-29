package com.phoenix.game.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.content.UnitTypes;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Fill;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Time;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.core.World;
import com.phoenix.game.func.Floatc2;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.Simplex;
import com.phoenix.game.math.noise.RidgedPerlin;
import com.phoenix.game.type.UnitType;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.util.Structs;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.CachedTile;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.OreFloor;

import static com.phoenix.game.Vars.tilesize;

/**
 * 主菜单背景：随机生成一张地形缩略图铺满屏幕，上面有沿 45° 方向循环飞行的单位，最后整体压暗。
 * 参照原版 {@code mindustry.graphics.MenuRenderer} 移植（原版 304 行）。
 *
 * <p><b>本批核心偏差：用两张 FrameBuffer 烘焙代替 arc 的 {@code CacheBatch}/{@code SpriteCache}</b>
 * <ul>
 *   <li>原版 {@code cache()} 把 {@code Core.batch} 临时换成 {@code CacheBatch}（{@code SpriteCache} 的
 *       子类）分两趟缓存顶点流：地板 + 覆盖层 → {@code cacheFloor}，方块 → {@code cacheWall}，
 *       {@code render()} 里用 {@code batch.drawCache(id)} 回放。</li>
 *   <li><b>libgdx 的 {@code SpriteBatch} 没有可覆写的顶点截获点</b>：arc 的 {@code CacheBatch} 靠覆写
 *       {@code protected void draw(Texture, float[], int, int)} 截获顶点流，而 libgdx 1.14.2 的
 *       {@code flush()} 直接 {@code mesh.render(...)}（{@code SpriteBatch.java:966-996}），顶点流截不住 →
 *       {@code CacheBatch}/{@code SpriteCache} 无法移植。</li>
 *   <li>替代方案：把同样的两趟内容烘焙进两张 {@link FrameBuffer}（地板 + 覆盖层一张、方块一张），
 *       绘制时当整图贴回。视觉结果等价，且复用本项目既有做法
 *       （{@code graphics/BlockRenderer.bakeAll} 就是这么烘焙影子/变暗层的）。</li>
 * </ul>
 *
 * <p>其他必须偏离原版的地方（均已在对应代码处注明原因）：
 * <ul>
 *   <li>用本类自己的 {@link World} 实例 {@link #menuWorld}，<b>不用 {@code Vars.world}</b> ——
 *       本项目 {@code Vars.world} 只在开局/编辑器时才被赋值，菜单期恒为 {@code null}。
 *       但 {@code Floor.drawEdges → Tile.getNearby}、{@code StaticWall.eq} 内部读的是 {@code Vars.world}
 *       → 烘焙期间临时挂上 {@code menuWorld}（用完还原）。</li>
 *   <li>本项目 {@code World.rawTile} 读一维数组 → 二维 {@code grid[x][y]} 塞完 {@link CachedTile} 后
 *       必须调 {@link World#endMapLoad(Tile[][])} 展平，否则 {@code rawTile} 拿到的还是空瓦片。</li>
 *   <li>原版 {@code render()} 依赖 {@code CacheBatch.setProjection} + 负高度贴图做翻转；
 *       本项目 {@code render()} 自己 {@code batch.begin()/end()}，贴 FBO 纹理用
 *       {@link TextureRegion#setRegion(float, float, float, float) v/v2 对调}做翻转
 *       （负高度在本项目会让矩形整体错位，见 {@code 转换.md}）。</li>
 * </ul>
 *
 * <p><b>调用约定</b>：由 {@code ClientLauncher} 的菜单分支在 {@code stage.draw()} <b>之前</b>调用
 * （那时 batch 不在绘制状态）→ 本类的 {@code render()} 自己开关批次，并在结束时把投影恢复原样。
 */
public class MenuRenderer implements Disposable{
    /** 整体压暗强度（对应原版 {@code MenuRenderer.darkness}）。 */
    private static final float darkness = 0.3f;

    /**
     * 背景地图尺寸（格）：桌面 100x50、移动端 60x40（对应原版 {@code !mobile ? 100 : 60}）。
     * <p>⚠ 这里用 {@link Vars#mobile} 而不是 {@link Vars#testMobile}：{@code Vars.mobile} 才是原版
     * {@code mobile} 的等价物，且它在 {@code Vars.init()} 里已经"或"进了 {@code testMobile}
     * （{@code mobile = android || ios || testMobile}）→ 单用 testMobile 会让真机 Android 也走 100x50。
     */
    private final int width = !Vars.mobile ? 100 : 60, height = !Vars.mobile ? 50 : 40;

    /**
     * 本类私有的"菜单世界"（对应原版直接用全局 {@code world}）。
     * <p>⚠ 不能用 {@code Vars.world}：本项目它只在开局/编辑器时才被赋值，菜单期恒为 null。
     */
    private final World menuWorld = new World();

    /** 地板 + 覆盖层烘焙缓冲（{@code width*tilesize × height*tilesize} 像素）。 */
    private FrameBuffer floorBuffer;
    /** 方块层烘焙缓冲（同上尺寸）。 */
    private FrameBuffer wallBuffer;
    /** 方块落影缓冲（1 像素 1 格，对应原版 {@code shadows}）。 */
    private FrameBuffer shadows;

    /** 菜单相机（对应原版 arc 的 {@code Camera}；libgdx 侧用正交相机）。 */
    private final OrthographicCamera camera = new OrthographicCamera();
    /** 飞行单位贴图的圆形落影（原版写 {@code Draw.rect("circle-shadow", ...)}，本项目要自己取区域）。 */
    private TextureRegion circleShadow;

    /** 动画计时（对应原版 {@code time}）。 */
    private float time = 0f;
    /** 飞行方向（对应原版 {@code flyerRot}）。 */
    private float flyerRot = 45f;
    /** 飞行单位数量（对应原版 {@code flyers}）。 */
    private int flyers = Mathf.chance(0.2f) ? Mathf.random(35) : Mathf.random(15);
    /** 飞行单位种类（对应原版 {@code flyerType}）。 */
    private UnitType flyerType = Structs.select(UnitTypes.wraith, UnitTypes.wraith, UnitTypes.ghoul,
        UnitTypes.phantom, UnitTypes.phantom, UnitTypes.revenant);

    public MenuRenderer(){
        long start = System.nanoTime();

        //原版写 Draw.rect("circle-shadow", ...)（arc 的 Draw 会自己查图集）；本项目要显式取区域
        circleShadow = Core.atlas == null ? null : Core.atlas.findRegion("circle-shadow");

        generate();
        cache();

        System.out.println("Menu background generated in " + ((System.nanoTime() - start) / 1000000L) + " ms");
    }

    /** 随机铺地形（对应原版 {@code MenuRenderer.generate()}）。 */
    private void generate(){
        Tile[][] grid = menuWorld.createTiles(width, height);

        //矿脉候选：原版是 content.blocks().select(b -> b instanceof OreBlock)。
        //⚠ 本项目实际注册的矿脉类是 OreFloor（OreBlock 只是照原版类名补的档、没有任何实例），
        //   照抄 instanceof OreBlock 会得到空数组 → 按 OreFloor 过滤。
        //⚠ 必须收集到**新数组**：content.blocks() 返回的是内容加载器内部那个共享 Array，
        //   原版 arc 的 select 会新建数组、后续 ores.remove(...) 才安全。
        Array<Block> ores = new Array<>();
        Array<Block> all = Vars.content.blocks();
        for(int i = 0; i < all.size; i++){
            if(all.get(i) instanceof OreFloor) ores.add(all.get(i));
        }

        shadows = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        //arc 的 FrameBuffer 默认就是 Linear（原版落影是柔和的黑斑）；libgdx 默认 Nearest → 显式对齐
        shadows.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        int offset = Mathf.random(100000);
        Simplex s1 = new Simplex(offset);
        Simplex s2 = new Simplex(offset + 1);
        Simplex s3 = new Simplex(offset + 2);
        RidgedPerlin rid = new RidgedPerlin(1 + offset, 1);

        //地表 / 岩壁配对（对应原版 selected / selected2）
        Block[] selected = Structs.select(
            new Block[]{Blocks.sand, Blocks.sandRocks},
            new Block[]{Blocks.shale, Blocks.shaleRocks},
            new Block[]{Blocks.ice, Blocks.icerocks},
            new Block[]{Blocks.sand, Blocks.sandRocks},
            new Block[]{Blocks.shale, Blocks.shaleRocks},
            new Block[]{Blocks.ice, Blocks.icerocks},
            new Block[]{Blocks.moss, Blocks.sporePine}
        );
        Block[] selected2 = Structs.select(
            new Block[]{Blocks.ignarock, Blocks.duneRocks},
            new Block[]{Blocks.ignarock, Blocks.duneRocks},
            new Block[]{Blocks.stone, Blocks.rocks},
            new Block[]{Blocks.stone, Blocks.rocks},
            new Block[]{Blocks.moss, Blocks.sporerocks},
            new Block[]{Blocks.salt, Blocks.saltRocks}
        );

        Block ore1 = ores.random();
        ores.removeValue(ore1, true);
        Block ore2 = ores.random();

        float tr1 = Mathf.random(0.65f, 0.85f);
        float tr2 = Mathf.random(0.65f, 0.85f);
        boolean doheat = Mathf.chance(0.25f);
        boolean tendrils = Mathf.chance(0.25f);
        boolean tech = Mathf.chance(0.25f);
        int secSize = 10;

        Block floord = selected[0], walld = selected[1];
        Block floord2 = selected2[0], walld2 = selected2[1];

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                Block floor = floord;
                Block ore = Blocks.air;
                Block wall = Blocks.air;

                if(s1.octaveNoise2D(3, 0.5f, 1 / 20f, x, y) > 0.5f){
                    wall = walld;
                }

                if(s3.octaveNoise2D(3, 0.5f, 1 / 20f, x, y) > 0.5f){
                    floor = floord2;
                    if(wall != Blocks.air){
                        wall = walld2;
                    }
                }

                if(s2.octaveNoise2D(3, 0.3f, 1 / 30f, x, y) > tr1){
                    ore = ore1;
                }

                if(s2.octaveNoise2D(2, 0.2f, 1 / 15f, x, y + 99999) > tr2){
                    ore = ore2;
                }

                if(doheat){
                    float heat = s3.octaveNoise2D(4, 0.6f, 1 / 50f, x, y + 9999);
                    float base = 0.65f;

                    if(heat > base){
                        ore = Blocks.air;
                        wall = Blocks.air;
                        floor = Blocks.ignarock;

                        if(heat > base + 0.1f){
                            floor = Blocks.hotrock;

                            if(heat > base + 0.15f){
                                floor = Blocks.magmarock;
                            }
                        }
                    }
                }

                if(tech){
                    int mx = x % secSize, my = y % secSize;
                    int sclx = x / secSize, scly = y / secSize;
                    if(s1.octaveNoise2D(2, 1f / 10f, 0.5f, sclx, scly) > 0.4f
                        && (mx == 0 || my == 0 || mx == secSize - 1 || my == secSize - 1)){
                        floor = Blocks.darkPanel3;
                        if(Mathf.dst(mx, my, secSize / 2, secSize / 2) > secSize / 2f + 1){
                            floor = Blocks.darkPanel4;
                        }

                        if(wall != Blocks.air && Mathf.chance(0.7f)){
                            wall = Blocks.darkMetal;
                        }
                    }
                }

                if(tendrils){
                    if(rid.getValue(x, y, 1f / 17f) > 0f){
                        floor = Mathf.chance(0.2f) ? Blocks.sporeMoss : Blocks.moss;

                        if(wall != Blocks.air){
                            wall = Blocks.sporerocks;
                        }
                    }
                }

                //瓦片用 CachedTile（对应原版 `tiles[x][y] = (tile = new CachedTile())`）
                CachedTile tile = new CachedTile();
                tile.x = (short)x;
                tile.y = (short)y;
                grid[x][y] = tile;

                tile.setFloor((Floor)floor);
                tile.setBlock(wall);
                tile.setOverlay(ore);
            }
        }

        //⚠ 必须展平回一维：本项目 World.rawTile(x,y) 读的是 tiles[y*width+x]，
        //  不调 endMapLoad 的话 rawTile 拿到的还是 createTiles 建的空瓦片。
        menuWorld.endMapLoad(grid);
    }

    /**
     * 烘焙三张缓冲（对应原版 {@code MenuRenderer.cache()}）。
     * <p>烘焙放在构造器里做完（原版也是），那时不在任何 batch 中间 → 可以自由切投影。
     */
    private void cache(){
        //⚠ 烘焙期间临时把 Vars.world 指向菜单世界：Floor.drawEdges → Tile.getNearby 与
        //   StaticWall.eq 内部读的都是 Vars.world（菜单期恒为 null），不挂的话
        //   地板边缘过渡与 2×2 大岩壁都不会画出来。用完还原。
        World prevWorld = Vars.world;
        Vars.world = menuWorld;
        try{
            bakeShadows();
            bakeTiles();
        }finally{
            Vars.world = prevWorld;
        }
    }

    /** 往 FBO 里用几何（{@link Fill}）画东西之前显式打开标准 alpha 混合（理由同 {@code BlockRenderer.beginBlend}）。 */
    private static void beginBlend(){
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** 清一张 FBO 到"全透明"（⚠ 不能把 alpha 清成 1，否则墙层整块盖住地板）。 */
    private static void clearTransparent(){
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    /** ① 落影缓冲（1 像素 1 格，对应原版 cache() 里画 shadows 的那一段）。 */
    private void bakeShadows(){
        Matrix4 saved = new Matrix4(Core.batch.getProjectionMatrix());

        Draw.flush();
        shadows.begin();
        clearTransparent();
        beginBlend();
        Draw.proj().setToOrtho2D(0, 0, width, height);
        Draw.color(Color.BLACK);
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                if(menuWorld.rawTile(x, y).block() != Blocks.air){
                    Fill.rect(x + 0.5f, y + 0.5f, 1, 1);
                }
            }
        }
        Draw.flush();
        Draw.color();
        shadows.end();
        Draw.proj(saved);
    }

    /**
     * ② 分两趟烘焙地板（+ 覆盖层）与方块层（对应原版 cache() 里两段 {@code beginCache/endCache}）。
     * <p>两趟都要写进 FBO 的"1 单位 = 1 像素"正交投影里 —— 瓦片按世界坐标绘制，
     * 于是缓冲内容与地图坐标一一对应。
     */
    private void bakeTiles(){
        int bufW = width * tilesize, bufH = height * tilesize;

        floorBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, bufW, bufH, false);
        wallBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, bufW, bufH, false);
        //两张图会被放大到屏幕上（菜单缩放通常 = Scl.scl(4f)）→ Linear 至少不会出现 4×4 的硬块
        floorBuffer.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        wallBuffer.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        //第一趟：地板 + 覆盖层（矿脉斑点）
        bakePass(floorBuffer, bufW, bufH, true);
        //第二趟：方块（自然岩壁）
        bakePass(wallBuffer, bufW, bufH, false);
    }

    private void bakePass(FrameBuffer buffer, int bufW, int bufH, boolean floorPass){
        Matrix4 saved = new Matrix4(Core.batch.getProjectionMatrix());

        //libgdx 的 FrameBuffer.begin/end 不会 Draw.flush（arc 会）→ 进出 FBO 前后各 flush 一次
        Draw.flush();
        buffer.begin();
        clearTransparent();

        //"1 单位 = 1 像素"正交投影（对应原版 CacheBatch 用的世界投影口径）
        Draw.proj().setToOrtho2D(0, 0, bufW, bufH);

        Core.batch.begin();
        Core.batch.setColor(1f, 1f, 1f, 1f);

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                Tile tile = menuWorld.rawTile(x, y);
                float dx = x * tilesize, dy = y * tilesize;

                if(floorPass){
                    //地板：变体贴图 + 边缘过渡（本项目没有 Floor.draw，逻辑与 Renderer.drawTiles 的第一趟一致）
                    Floor floor = tile.floor();
                    if(floor != null && floor != Blocks.air){
                        TextureRegion region = floor.variant(x, y);
                        if(region != null){
                            Core.batch.draw(region, dx, dy, tilesize, tilesize);
                            floor.drawEdges(tile);
                        }
                    }

                    //覆盖层（矿脉）
                    Floor overlay = tile.overlay();
                    if(overlay != null && overlay != Blocks.air){
                        TextureRegion oRegion = overlay.variant(x, y);
                        if(oRegion != null){
                            Core.batch.draw(oRegion, dx, dy, tilesize, tilesize);
                        }
                    }
                }else{
                    Block block = tile.block();
                    if(block != null && block != Blocks.air){
                        block.draw(tile);
                    }
                }
            }
        }

        Core.batch.end();
        Draw.flush();
        buffer.end();
        Draw.proj(saved);
    }

    /**
     * 绘制整个菜单背景。调用约定：菜单分支、{@code stage.draw()} 之前（batch 未 begin）。
     * <p>本方法自己开关批次，并在结束时把投影恢复成进来时的样子。
     */
    public void render(){
        time += Time.delta();

        float gw = Core.graphics.getWidth(), gh = Core.graphics.getHeight();

        //让整张图铺满屏幕：取"至少 4 倍缩放"与"屏幕 / 地图可视野"中的较大者
        //（对应原版 scaling = Math.max(Scl.scl(4f), Math.max(gw / ((width-1f)*tilesize), gh / ((height-1f)*tilesize)))）
        float scaling = Math.max(Scl.scl(4f),
            Math.max(gw / ((width - 1f) * tilesize), gh / ((height - 1f) * tilesize)));

        //原版用 arc 的 camera.position.set + camera.resize(...)；libgdx 侧等价于 setToOrtho + 挪位置
        camera.setToOrtho(false, gw / scaling, gh / scaling);
        camera.position.set(width * tilesize / 2f, height * tilesize / 2f, 0f);
        camera.update();

        Matrix4 saved = new Matrix4(Core.batch.getProjectionMatrix());

        Core.batch.setProjectionMatrix(camera.combined);
        Core.batch.begin();
        //⚠ 与 Renderer.draw 同理：HUD 的 Scene2D 与本 batch 共享同一个 SpriteBatch，
        //   残留的 alpha=0 会让整张背景透明不可见 → 硬性复位
        Core.batch.setColor(1f, 1f, 1f, 1f);

        float mapW = width * tilesize, mapH = height * tilesize;

        //地板层（对应原版 batch.drawCache(cacheFloor)）
        drawBuffer(floorBuffer, mapW / 2f, mapH / 2f, mapW, mapH);

        //落影层（对应原版 Draw.rect(Draw.wrap(shadows.getTexture()), cx - 4, cy - 4, w, -h)）
        //⚠ 原版用负高度做上下翻转，本项目负高度会让矩形整体错位 → 改用 v/v2 对调（见 drawBuffer）。
        //   原版那个 -tilesize/2 的整体偏移照抄保留。
        Draw.color();
        drawBuffer(shadows, mapW / 2f - tilesize / 2f, mapH / 2f - tilesize / 2f, mapW, mapH);

        Draw.flush();

        //方块层（对应原版 batch.drawCache(cacheWall)）
        drawBuffer(wallBuffer, mapW / 2f, mapH / 2f, mapW, mapH);

        drawFlyers();

        //全屏压暗（对应原版 Draw.proj(mat) + Draw.color(0,0,0,darkness) + Fill.crect(...)）：
        //原版把投影恢复成"屏幕投影"后再画整屏矩形，这里就地切到屏幕正交投影
        //（Drawf.begin 只在"会话未开"时现读 Core.batch.getProjectionMatrix()，
        // 所以先 flush 收尾几何会话，保证下面这一笔用的是新投影）
        Draw.flush();
        Draw.proj().setToOrtho2D(0, 0, gw, gh);
        Draw.color(0f, 0f, 0f, darkness);
        Fill.rect(gw / 2f, gh / 2f, gw, gh);
        Draw.color();
        Draw.flush();

        Core.batch.end();
        Core.batch.setProjectionMatrix(saved);
    }

    /**
     * 把一张 FBO 按"整图"贴到屏幕上。
     * <p>⚠ <b>必须把 v/v2 对调</b>：libgdx 的 {@code SpriteBatch.draw(region, x, y, w, h)} 把
     * {@code region.v2} 放在底边，而 FBO 纹理的 {@code v=0} 是 GL 底行（= 图的下边）→
     * 不对调整层会上下翻转。做法与 {@code BlockRenderer.drawBuffer} 一致。
     */
    private void drawBuffer(FrameBuffer buffer, float cx, float cy, float w, float h){
        Tmp.tr1.setRegion(buffer.getColorBufferTexture());
        //v/v2 对调（见方法注释）
        Tmp.tr1.setRegion(0f, 1f, 1f, 0f);
        Draw.rect(Tmp.tr1, cx, cy, w, h);
    }

    /** 绘制飞行单位（对应原版 {@code MenuRenderer.drawFlyers()}）。 */
    private void drawFlyers(){
        Draw.color(0f, 0f, 0f, 0.4f);

        TextureRegion icon = flyerType.icon(Cicon.full);
        //⚠ arc 的 TextureRegion.getWidth()/getHeight() 在 libgdx 里叫 getRegionWidth()/getRegionHeight()
        float size = Math.max(icon.getRegionWidth(), icon.getRegionHeight()) * Draw.scl * 1.6f;

        //半透明黑影 + 圆形落影
        flyers((x, y) -> Draw.rect(flyerType.region, x - 12f, y - 13f, flyerRot - 90f));

        if(circleShadow != null){
            flyers((x, y) -> Draw.rect(circleShadow, x, y, size, size));
        }
        Draw.color();

        //本体 + 引擎火焰
        flyers((x, y) -> {
            float engineOffset = flyerType.engineOffset, engineSize = flyerType.engineSize, rotation = flyerRot;

            Draw.color(Pal.engine);
            Fill.circle(x + Angles.trnsx(rotation + 180f, engineOffset), y + Angles.trnsy(rotation + 180f, engineOffset),
                engineSize + Mathf.absin(Time.time(), 2f, engineSize / 4f));

            Draw.color(Color.WHITE);
            Fill.circle(x + Angles.trnsx(rotation + 180f, engineOffset - 1f), y + Angles.trnsy(rotation + 180f, engineOffset - 1f),
                (engineSize + Mathf.absin(Time.time(), 2f, engineSize / 4f)) / 2f);
            Draw.color();

            Draw.rect(flyerType.region, x, y, flyerRot - 90f);
        });
    }

    /**
     * 沿 {@code flyerRot} 方向循环平铺 {@code flyers} 个位置（对应原版 {@code MenuRenderer.flyers(Floatc2)}）。
     * <p>⚠ 原版用 arc {@code Tmp.v1.trns(angle, len)}；本项目 {@code Tmp.v1} 是 libgdx 的 {@code Vector2}
     * （没有 trns）→ 直接用 {@link Angles#trnsx}/{@link Angles#trnsy} 算。
     */
    private void flyers(Floatc2 cons){
        float tw = width * tilesize + tilesize;
        float th = height * tilesize + tilesize;
        float range = 500f;
        float offset = -100f;

        for(int i = 0; i < flyers; i++){
            float len = time * (2f + flyerType.speed);
            float vx = Angles.trnsx(flyerRot, len), vy = Angles.trnsy(flyerRot, len);

            cons.get((Mathf.randomSeedRange(i, range) + vx
                    + Mathf.absin(time + Mathf.randomSeedRange(i + 2, 500), 10f, 3.4f) + offset)
                    % (tw + Mathf.randomSeed(i + 5, 0, 500)),
                (Mathf.randomSeedRange(i + 1, range) + vy
                    + Mathf.absin(time + Mathf.randomSeedRange(i + 3, 500), 10f, 3.4f) + offset) % th);
        }
    }

    /** 释放三张缓冲（对应原版 {@code MenuRenderer.dispose()}）。 */
    @Override
    public void dispose(){
        if(floorBuffer != null) floorBuffer.dispose();
        if(wallBuffer != null) wallBuffer.dispose();
        if(shadows != null) shadows.dispose();
        floorBuffer = wallBuffer = shadows = null;
    }
}
