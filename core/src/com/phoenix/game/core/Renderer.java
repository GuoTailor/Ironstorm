package com.phoenix.game.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Units;
import com.phoenix.game.game.Teams;
import com.phoenix.game.graphics.FboUtil;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.graphics.Shaders;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.world.Block;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.defense.ForceProjector;
import com.phoenix.game.core.World;

import static com.phoenix.game.Vars.tilesize;

/**
 * 最小实现：世界渲染器。参照 Mindustry mindustry.core.Renderer 移植。
 * 绘制顺序：地板/方块 -> 单位 -> 子弹 -> 特效。
 * 相机缩放沿用原版模型：targetscale / camerascale + 逐帧插值，范围 [1.5, 6] * Scl.scl(1)。
 */
public class Renderer {
    /**
     * 灯光收集范围的额外外扩格数（对应原版 {@code BlockRenderer.expandr = 9}，见 {@code BlockRenderer.java:23}）。
     * <p>原版把这个外扩量同时用于方块层与灯光层：灯光层**必须**外扩，因为光斑会溢出屏幕 ——
     * 灯在屏幕外一点点时，它照进可见区的那部分仍要画（方块不会溢出，所以方块层是单独用 {@code expanded} 判据筛的）。
     * <p>⚠ <b>已知的边界</b>（原版同样如此，照抄）：本项目最大的光半径是 {@code ImpactReactor}
     * （{@code (110 + absin(5,5)) * fract} ≈ 14.4 格）、{@code NuclearReactor} ≈ 11.9 格、
     * {@code CoreBlock} {@code 30 * size} = 11.25 格，都**大于** 9 → 这类大光源在屏幕外 9~14 格时会被裁掉。
     * 实测把相机从核心往外扫（7 个位置、含实际发生裁剪的位置）画面**逐像素一致**，肉眼看不出差异
     * （{@code circle-shadow} 是径向渐变，半径 80% 处已经很淡）。若要彻底消除该边界，把这里调大即可
     * （只需多扫一条窄带，代价可忽略）。
     */
    private static final int lightExpandr = 9;

    /** 目标缩放（对应原版 targetscale，默认 Scl.scl(4)） */
    private float targetscale = -1f;
    /** 当前缩放，逐帧插值逼近 targetscale */
    private float camerascale = -1f;

    /** 开局降落动画的当前缩放（对应原版 {@code Renderer.landscale}）。 */
    private float landscale = 0f;
    /** 开局降落动画剩余时间（对应原版 {@code Renderer.landTime}）；{@code > 0} 时动画生效。 */
    private float landTime = 0f;
    /**
     * 降落动画的最小缩放（对应原版 {@code Renderer.minZoomScl = Scl.scl(0.01f)}）。
     * <p>⚠ <b>必须懒初始化</b>：{@code Scl.scl()} 依赖 {@code Core.app}，不能在类初始化阶段调用
     * （那时 {@code Core.app} 为 null）→ 见 {@link #minZoomScl()}。
     */
    private float minZoomScl = -1f;

    /** 是否绘制格子边框（调试用） */
    public boolean drawGrid = false;

    /** "重建幽灵轮廓"的淡入淡出系数（对应原版 {@code BlockRenderer.brokenFade}）。 */
    private float brokenFade = 0f;

    /** 屏幕外单位指示箭头的绘制半径（对应原版 {@code OverlayRenderer.indicatorLength}）。 */
    private static final float indicatorLength = 14f;
    /** 空投区警告圈的额外外扩距离（对应原版 {@code OverlayRenderer.spawnerMargin = tilesize * 11f}）。 */
    private static final float spawnerMargin = tilesize * 11f;
    /** 敌核禁建圈的淡入系数（对应原版 {@code OverlayRenderer.buildFadeTime}）。 */
    private float buildFadeTime = 0f;

    /**
     * 小地图渲染器（对应原版 {@code Vars.renderer.minimap}）。
     * <p>只在客户端创建（Renderer 由 ClientLauncher new），headless 服务端不会挂上它的事件监听。
     */
    public final com.phoenix.game.graphics.MinimapRenderer minimap = new com.phoenix.game.graphics.MinimapRenderer();

    /**
     * 像素化后处理器（对应原版 {@code Renderer.pixelator}）：设置项 {@code pixelate} 打开时，
     * 世界先渲进一张低分辨率 FrameBuffer 再放大贴回屏幕。由 {@code ClientLauncher} 在
     * {@code renderer.draw()} 的位置二选一调用（与原版 {@code Renderer.update()} 里的分支一致）。
     */
    public final com.phoenix.game.graphics.Pixelator pixelator = new com.phoenix.game.graphics.Pixelator();

    /**
     * 方块层的两张"全图烘焙"缓冲：方块落影（shadows）与地图变暗（fog）。
     * <p>对应原版 {@code Renderer.blocks}；本项目只搬了原版 {@code BlockRenderer} 里的这两个子系统
     * （方块请求队列/分缓存层批量绘制由 {@code drawTiles} 直接实现），详见该类注释。
     */
    public final com.phoenix.game.graphics.BlockRenderer blocks = new com.phoenix.game.graphics.BlockRenderer();

    /** 光源收集器（对应原版 {@code Renderer.lights}）。FBO 懒建 → 可以安全地在构造期 new。 */
    public final com.phoenix.game.graphics.LightRenderer lights = new com.phoenix.game.graphics.LightRenderer();

    /**
     * 护盾绘制缓冲（对应原版 {@code Renderer.shieldBuffer}）："动画护盾"模式下先把各护盾的六边形
     * 填充画进它，再用 {@code Shaders.shield} 贴回屏幕。懒建（本类是 {@code ClientLauncher} 的字段，
     * 构造早于 GL 上下文就绪）、窗口尺寸变化时 dispose 重建（libgdx 1.14.2 没有 {@code resize}）。
     */
    private FrameBuffer shieldBuffer;

    /** 本帧收集到的"有可见护盾"的方块瓦片（对应原版 {@code shieldGroup} 的可见集，见 {@link #drawShields}）。 */
    private final Array<Tile> shieldTiles = new Array<>();

    /** 每帧调用：缩放插值、相机跟随玩家，并同步到相机。 */
    public void update(){
        initScale();

        camerascale = Mathf.lerpDelta(camerascale, targetscale, 0.1f);

        //开局降落动画（对应原版 Renderer.update 的 landTime 分支）：
        //landTime 递减，landscale 从 minZoomScl 插值放大到 Scl.scl(4)，并**直接覆盖** camerascale
        //（镜头从极小瞬间拉远到正常缩放，配合 drawLanding 的落点表现）。
        if(landTime > 0f){
            landTime -= Time.delta();
            landscale = Interpolation.pow5In.apply(minZoomScl(), Scl.scl(4f),
                1f - landTime / com.phoenix.game.content.Fx.coreLand.lifetime);
            camerascale = landscale;
        }
        //菜单态强制结束降落动画（对应原版 `if(state.is(State.menu)) landTime = 0f;`）
        if(Vars.state != null && Vars.state.isMenu()){
            landTime = 0f;
        }

        //相机跟随玩家（对应原版 Renderer.draw 里的 camera.position.lerpDelta(position, 0.08f)）。
        //原版这里有一道 `control.input instanceof DesktopInput` 的判断：移动端不自动跟随，
        //镜头完全由玩家拖动，否则每帧的跟随 lerp 会把刚拖出去的镜头又拉回玩家身上。
        if(Vars.player != null && !Vars.player.isDead() && !Vars.state.isPaused() && Core.camera != null
            && Vars.control != null && Vars.control.input instanceof com.phoenix.game.input.DesktopInput){
            Core.camera.position.lerp(Tmp.v31.set(Vars.player.getX(), Vars.player.getY(), 0f), 0.08f);
        }

        applyScale();
    }

    /** 缩放相机（对应原版 Renderer.scaleCamera）。 */
    public void scaleCamera(float amount){
        initScale();
        targetscale += amount;
        clampScale();
    }

    public void clampScale(){
        //先确保已经初始化，否则会把 -1 夹到下限
        initScale();

        float s = Scl.scl(1f);
        targetscale = Mathf.clamp(targetscale, s * 1.5f, Math.round(s * 6));
    }

    public float getScale(){
        return targetscale;
    }

    public void setScale(float scl){
        targetscale = scl;
        clampScale();
    }

    /**
     * 开始"开局降落"镜头动画（对应原版 {@code Renderer.zoomIn}）：把缩放瞬间压到最小，
     * 随后在 duration 秒内拉远到正常缩放。
     * <p>由 {@code Control} 监听 {@code Trigger.newGame} 时调用，duration 传 {@code Fx.coreLand.lifetime}。
     */
    public void zoomIn(float duration){
        landscale = minZoomScl();
        landTime = duration;
    }

    /** @return 降落动画期间的缩放；动画结束后恒为 1f（对应原版 {@code Renderer.landScale}）。 */
    public float landScale(){
        return landTime > 0f ? landscale : 1f;
    }

    /**
     * @return 降落动画的最小缩放（对应原版 {@code Renderer.minZoomScl}）。
     * <p>懒初始化：{@code Scl.scl()} 不能在类初始化阶段调用（{@code Core.app} 为 null）。
     */
    private float minZoomScl(){
        if(minZoomScl < 0f) minZoomScl = Scl.scl(0.01f);
        return minZoomScl;
    }

    /** @return 当前实际缩放（插值结果） */
    public float cameraScale(){
        initScale();
        return camerascale;
    }

    private void initScale(){
        if(targetscale < 0f){
            targetscale = Scl.scl(4f);
            camerascale = targetscale;
        }
    }

    private void applyScale(){
        if(Core.camera instanceof OrthographicCamera){
            //原版的 camerascale 是放大倍数（1 个世界单位 = camerascale 像素，camera.width = 屏幕宽 / camerascale），
            //libgdx 的 OrthographicCamera.zoom 是缩小倍数（可视世界宽 = 屏幕宽 * zoom），两者互为倒数。
            ((OrthographicCamera)Core.camera).zoom = 1f / camerascale;
            Core.camera.update();
        }
    }

    /** @return 当前可视区域的世界宽度（{@code Pixelator} 也要用 → public）。 */
    public float viewWidth(){
        return Core.camera.viewportWidth / camerascale;
    }

    /** @return 当前可视区域的世界高度（{@code Pixelator} 也要用 → public）。 */
    public float viewHeight(){
        return Core.camera.viewportHeight / camerascale;
    }

    public void draw(){
        World world = Vars.world;
        if(world == null || world.tiles.length == 0) return;

        initScale();

        float ox = 0f, oy = 0f;
        if(Effects.shakeTime > 0f){
            //震屏强度乘设置 screenshake（滑块 0~8，默认 4 → 设置值 4 即 1x）：对应原版 Renderer.updateShake 的
            //`intensity = shakeIntensity * (Core.settings.getInt("screenshake", 4) / 4f) * scale`
            //（原版在 draw() 里以固定 scale = 0.75f 调用 updateShake，这里照抄该常量）
            float intensity = Effects.shakeIntensity * (Core.settings.getInt("screenshake", 4) / 4f) * 0.75f;
            ox = Mathf.random(-1f, 1f) * intensity;
            oy = Mathf.random(-1f, 1f) * intensity;
        }

        Core.camera.position.add(ox, oy, 0f);
        applyScale();

        Core.batch.setProjectionMatrix(Core.camera.combined);
        Core.batch.begin();
        //⚠ 必须把 batch 颜色复位成白色：HUD 的 Scene2D 与 Core.batch **共享同一个 SpriteBatch**
        //（ClientLauncher 里 `stage = new Stage(stageViewport, batch)`），而 Scene2D 画背景时会
        //`batch.setColor(颜色 * parentAlpha)`。像 HudFragment 的 $nearpoint 全屏提示表把 color.a 淡到 0 后
        //仍 visible=true（原版也是这个写法）→ 会把共享 batch 残留成 alpha=0，使**下一帧**的
        //drawTiles（地板 + 方块层）整体用 alpha=0 绘制 → 世界地形全黑，而之后 drawGroundShadows 的
        //Draw.color() 复位又让单位/子弹/特效正常显示。世界层不该继承 UI 的颜色状态，这里做硬性复位。
        Core.batch.setColor(1f, 1f, 1f, 1f);

        drawTiles(world);
        //光源收集：对应原版 BlockRenderer 里"为 Layer.lights 建请求"的那一支（原版判据见 drawLights）
        drawLights(world);
        //"被摧毁方块"的重建幽灵轮廓（对应原版 Renderer 里 `blocks.drawDestroyed()`，排在方块层之后）
        drawDestroyed();
        //地面单位的落地阴影（对应原版 Renderer.draw 里的 `drawGroundShadows()`）
        drawGroundShadows();
        //液体水洼：地面层，画在地板之后、单位之前（对应原版 puddleGroup 绘制）
        com.phoenix.game.entities.effect.Puddle.drawAll();
        //飞行单位的投影（对应原版 Renderer.draw 里的 `drawFlyerShadows()`）
        drawFlyerShadows();
        drawUnits();
        drawBullets();
        //特效实体组（对应原版 Renderer.draw 里的 `bulletGroup.draw(); effectGroup.draw();`）：
        //"物品飞向目标"的传输动画（ItemTransfer）就画在这里
        if(Vars.effectGroup != null){
            Vars.effectGroup.draw();
        }
        drawPowerLinks(world);
        drawHealthBars(world);

        //输入层的世界层绘制（对应原版 OverlayRenderer 调 input.drawBottom / drawTop）：
        //建造预览在 drawTop，移动端的选中框/准星/蓝图框选在 drawBottom
        if(Vars.control != null && Vars.control.input != null && !Vars.state.isMenu()){
            Vars.control.input.drawBottom();

            //建造队列幽灵预览（对应原版 Renderer 里 `playerGroup.draw(p -> p.isLocal, Player::drawBuildRequests)`，
            //原版排在 overlays.drawBottom() 与 overlays.drawTop() 之间）：
            //本项目 Player 不是单位基类、没有 isLocal 概念 → 直接对本地玩家单位调一次。
            if(Vars.player != null && Vars.player.unit() != null){
                Vars.player.unit().drawBuildRequests();
            }

            Vars.control.input.drawTop();

            //RTS 命令系统：选中单位指示（圈/连线/目的标记）与框选矩形
            Vars.control.input.drawCommanded();
            Vars.control.input.drawUnitSelection();
        }

        //护盾绘制通道（对应原版 Renderer.draw 里的 shieldGroup 分支，原版 Renderer.java:288-305）：
        //原版排在 `playerGroup.draw(p -> p.isLocal, Player::drawBuildRequests)` 之后、`overlays.drawTop()` 之前
        drawShields(world);

        //对应原版 OverlayRenderer.drawTop 里本项目还缺的三项叠加层
        //（屏幕外单位指示箭头 / 敌核禁建圈 / 空投区警告圈）
        drawOverlay();

        //世界内玩家名（对应原版 Renderer.draw 里 `playerGroup.draw(p -> !p.isDead(), Player::drawName)`，
        //原版排在 overlays.drawTop() 之后、lights/drawLanding 之前）
        drawNames();

        //灯光叠加层（对应原版 Renderer.draw 的 `if(state.rules.lighting){ lights.draw(); }`，
        //原版排在 overlays.drawTop() / playerGroup.drawName 之后、drawLanding() 之前）
        if(lights.enabled()){
            lights.draw();
        }

        Effects.render();

        //开局降落动画（对应原版 Renderer.draw 末尾调用的 drawLanding()）
        drawLanding();

        Core.batch.end();

        Core.camera.position.sub(ox, oy, 0f);
        Core.camera.update();
    }

    /**
     * 世界内玩家名（对应原版 {@code playerGroup.draw(p -> !p.isDead(), Player::drawName)}）。
     * <p>独立成方法是为了让 {@code Pixelator} 能在"像素化贴回屏幕之后"再画一遍 —— 原版
     * {@code Pixelator.drawPixelate} 就是这么做的，玩家名因此不参与像素化（文字保持清晰）。
     * <p>调用方负责已 {@code Core.batch.begin()} 且投影矩阵为世界相机。
     */
    public void drawNames(){
        if(Vars.playerGroup == null) return;
        Array<Player> players = Vars.playerGroup.all();
        for(int i = 0; i < players.size; i++){
            Player p = players.get(i);
            if(!p.isDead()) p.drawName();
        }
    }

    /**
     * 叠加层绘制（对应原版 {@code OverlayRenderer.drawTop} 里尚未移植的三项）：
     * 屏幕外单位指示箭头、敌核禁建圈、空投区警告圈。
     * <p>原版把这三项分散在 {@code input.drawTop()} 前后，本项目按任务约定统一排在输入层绘制之后、{@code Effects.render()} 之前。
     */
    private void drawOverlay(){
        //菜单/服务端 Vars.player 可能为 null（原版 OverlayRenderer 开头是 `if(player.isDead()) return;`）
        if(Vars.player == null) return;

        //原版 indicators 分支排在 `if(player.isDead()) return;` **之前** → 玩家死亡时指示箭头照画
        drawIndicators();

        if(Vars.player.isDead()) return;
        drawEnemyCoreRadius();
        drawDropZones();
    }

    /**
     * 屏幕外单位指示箭头（对应原版 {@code OverlayRenderer.drawTop} 开头的 indicators 分支）：
     * 同队队友 / 敌方单位跑到相机视口 90% 矩形之外时，在镜头中心朝它所在方向画一条短线提示方位。
     * 受设置 {@code indicators} 控制。
     */
    private void drawIndicators(){
        if(Vars.playerGroup == null || !Core.settings.getBool("indicators")) return;

        //视口 90% 矩形（原版 rect.setSize(camera.width * 0.9f, camera.height * 0.9f).setCenter(...)）
        float hw = viewWidth() * 0.9f / 2f, hh = viewHeight() * 0.9f / 2f;
        float camX = Core.camera.position.x, camY = Core.camera.position.y;

        //队友（原版 playerGroup.all()：排除自己、要求同队）
        Array<Player> players = Vars.playerGroup.all();
        for(int i = 0; i < players.size; i++){
            Player other = players.get(i);
            if(other == Vars.player || other.getTeam() != Vars.player.getTeam()) continue;
            if(Math.abs(other.x - camX) <= hw && Math.abs(other.y - camY) <= hh) continue;

            Tmp.v1.set(other.x, other.y).sub(camX, camY).setLength(indicatorLength);
            Lines.stroke(2f, other.getTeam().color);
            Lines.lineAngle(camX + Tmp.v1.x, camY + Tmp.v1.y, Tmp.v1.angle(), 4f);
            Draw.color();
        }

        //敌方单位（原版 Units.all(...)：排除自己、不同队）
        for(int i = 0; i < Units.units.size; i++){
            BaseUnit unit = Units.units.get(i);
            if(unit == Vars.player.unit() || unit.getTeam() == Vars.player.getTeam()) continue;
            if(Math.abs(unit.x - camX) <= hw && Math.abs(unit.y - camY) <= hh) continue;

            Tmp.v1.set(unit.x, unit.y).sub(camX, camY).setLength(indicatorLength);
            Lines.stroke(1f, unit.getTeam().color);
            Lines.lineAngle(camX + Tmp.v1.x, camY + Tmp.v1.y, Tmp.v1.angle(), 3f);
            Draw.color();
        }

        //TODO 原版：Category.upgrade 时给屏幕外 MechPad 画指示箭头（本项目无建造分类状态，降级跳过）
    }

    /**
     * 敌核禁建圈（对应原版 {@code OverlayRenderer.drawTop} 的 buildFadeTime 分支）：
     * 玩家在放置方块时淡入，在离玩家足够近的敌方核心周围画一圈禁建范围提示。
     */
    private void drawEnemyCoreRadius(){
        boolean placing = Vars.control != null && Vars.control.input != null && Vars.control.input.isPlacing();
        buildFadeTime = Mathf.lerpDelta(buildFadeTime, placing ? 1f : 0f, 0.06f);

        Draw.color();
        Lines.stroke(buildFadeTime * 2f);

        if(buildFadeTime <= 0.005f || Vars.state == null || Vars.state.teams == null) return;

        float radius = Vars.state.rules.enemyCoreBuildRadius;
        float px = Vars.player.getX(), py = Vars.player.getY();

        Vars.state.teams.eachEnemyCore(Vars.player.getTeam(), core -> {
            //本项目 tile.worldx() 是左下角，方块中心要取 centerX/centerY（原版 worldx() 本身就是中心）
            float cx = core.block().centerX(core), cy = core.block().centerY(core);
            if(Mathf.dst(cx, cy, px, py) < radius * 2.2f){
                Draw.color(Color.DARK_GRAY);
                Lines.circle(cx, cy - 2f, radius);
                Draw.color(Pal.accent, core.getTeam().color, 0.5f + Mathf.absin(Time.time(), 10f, 0.5f));
                Lines.circle(cx, cy, radius);
            }
        });
    }

    /**
     * 空投区（敌方波次地面出生点）警告圈（对应原版 {@code OverlayRenderer.drawTop} 末尾的 spawner 分支）：
     * 玩家靠近出生点时画一圈随距离淡出的虚线圆，提示"这里会来敌人"。
     */
    private void drawDropZones(){
        if(Vars.spawner == null || Vars.state == null) return;
        Array<Tile> spawns = Vars.spawner.getGroundSpawns();
        if(spawns == null || spawns.size == 0) return;

        float radius = Vars.state.rules.dropZoneRadius;
        float px = Vars.player.getX(), py = Vars.player.getY();

        Lines.stroke(2f);
        Draw.color(Color.GRAY, Color.LIGHT_GRAY, Mathf.absin(Time.time(), 8f, 1f));

        for(int i = 0; i < spawns.size; i++){
            Tile tile = spawns.get(i);
            if(tile == null) continue;

            //本项目 tile.worldx() 是左下角，方块中心要取 centerX/centerY（原版 worldx() 本身就是中心）
            float cx = tile.block().centerX(tile), cy = tile.block().centerY(tile);
            float dst = Mathf.dst(cx, cy, px, py);
            if(dst >= radius + spawnerMargin) continue;

            Draw.alpha(Mathf.clamp(1f - (dst - radius) / spawnerMargin));
            Lines.dashCircle(cx, cy, radius);
        }
    }

    /**
     * 护盾绘制通道（对应原版 {@code Renderer.draw} 里的 {@code shieldGroup} 分支，{@code Renderer.java:288-305}）。
     *
     * <p>原版把护盾挂在一个 {@code ShieldEntity} 实体组上；本项目没有该组（也不为本批新增实体类）→
     * 改成"可见瓦片再遍历一遍"的既有套路（同 {@link #drawLights}/{@link #drawHealthBars}），
     * 收集条件等价于原版的 {@code shieldGroup.countInBounds() > 0} + {@code ShieldEntity::drawSimple}
     * 的 {@code realRadius < 0.5f} 提前返回。
     *
     * <p>两种画法（判据照原版 {@code settings.getBool("animatedshields") && Shaders.shield != null}）：
     * <ul>
     *   <li>开：「动画护盾」→ {@link #drawAnimatedShields()}，两趟画进 FrameBuffer 再用 {@link Shaders#shield} 贴回；</li>
     *   <li>关：{@link #drawSimpleShields()}，直接画到屏幕。</li>
     * </ul>
     */
    private void drawShields(World world){
        //收集本帧有护盾的方块（视口剔除，对应原版 shieldGroup.draw 的相机范围判定；多留一个半径的余量）
        shieldTiles.clear();
        float hw = viewWidth() / 2f, hh = viewHeight() / 2f;
        float camX = Core.camera.position.x, camY = Core.camera.position.y;

        for(int i = 0; i < world.tiles.length; i++){
            Tile tile = world.tiles[i];
            //卫星瓦片不单独处理（tile.entity 为 null，但显式跳过更清楚）
            if(tile == null || tile.isLinked()) continue;

            Block block = tile.block();
            if(!(block instanceof ForceProjector)) continue;

            ForceProjector projector = (ForceProjector)block;
            if(!projector.hasShield(tile)) continue;

            float cx = projector.centerX(tile), cy = projector.centerY(tile);
            float rad = projector.realRadius(tile.entity);
            if(Math.abs(cx - camX) > hw + rad || Math.abs(cy - camY) > hh + rad) continue;

            shieldTiles.add(tile);
        }
        if(shieldTiles.size == 0) return;

        //本项目 Shaders 是懒初始化（原版在 Renderer 构造器里建好）→ 这里补一次幂等初始化
        Shaders.init();

        if(Core.settings.getBool("animatedshields", !Vars.mobile) && Shaders.shield != null){
            drawAnimatedShields();
        }else{
            drawSimpleShields();
        }
    }

    /**
     * 「动画护盾」：先把所有护盾的六边形填充画进一张屏幕分辨率的 FBO，再用 {@code Shaders.shield}
     * 把这张图贴回屏幕（shader 负责把纯色填充变成"描边 + 流动条纹"，对应原版 shieldBuffer 那一段）。
     *
     * <p>⚠ 三个已知坑（{@code 转换.md} 的 S5a/S7a 两节都写过）：
     * ① libgdx 的 {@code FrameBuffer.begin()/end()} <b>不会</b> {@code Draw.flush()} → 进出前后各 flush 一次；
     * ② FBO 纹理贴回<b>必须对调 v/v2</b>（本项目约定；原版那句负高度翻转在本项目会让矩形错位）；
     * ③ 几何会话（{@code Fill} = ShapeRenderer）会污染 SpriteBatch 的 program —— {@code Drawf.end()} 已全局兜底，
     *    但这里仍让"画 → flush → end"顺序显式成立（{@code Draw.flush()} 内部就会先收尾几何会话）。
     */
    private void drawAnimatedShields(){
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        if(w <= 0 || h <= 0) return;

        //libgdx 1.14.2 的 GLFrameBuffer 没有 resize → 尺寸不符就 dispose 后重建（同 LightRenderer）
        if(shieldBuffer == null || shieldBuffer.getWidth() != w || shieldBuffer.getHeight() != h){
            if(shieldBuffer != null) shieldBuffer.dispose();
            shieldBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        }

        //⚠ arc 的 FrameBuffer.begin() 会 Draw.flush()，libgdx 的不会 → 先把排队的精灵落到默认帧缓冲
        Draw.flush();

        //⚠⚠ libgdx 的 FrameBuffer.end() 固定 glBindFramebuffer(GL_FRAMEBUFFER, 0) **并把视口重置成整屏**，
        //不是恢复到上一个 FBO → 若外层还开着 FBO（Pixelator 的像素化缓冲），这里一 end() 就会把后续世界绘制
        //打回默认帧缓冲。所以进出前存取/恢复当前绑定与视口（S10；见 FboUtil 的类注释）。
        int[] prevFbo = FboUtil.save();

        shieldBuffer.begin();
        //libgdx 没有 begin(Color) 重载（arc 有）→ 手动清成透明黑（原版 graphics.clear(Color.clear)）
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        //⚠ Fill 走的是 GL 混合，而批次未必处在标准 alpha 混合下（可能刚被切成加色混合）
        //→ 显式打开标准 alpha 混合（同 BlockRenderer.beginBlend 的说明）
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        //第一趟：护盾本体（对应原版 shieldGroup.draw() → ShieldEntity.draw：accent 色实心六边形）
        for(int i = 0; i < shieldTiles.size; i++){
            Tile tile = shieldTiles.get(i);
            ForceProjector projector = (ForceProjector)tile.block();
            Draw.color(Pal.accent);
            Fill.poly(projector.centerX(tile), projector.centerY(tile), 6, projector.realRadius(tile.entity));
        }

        //第二趟：命中白闪（对应原版 shieldGroup.draw(shield -> true, ShieldEntity::drawOver)）
        for(int i = 0; i < shieldTiles.size; i++){
            Tile tile = shieldTiles.get(i);
            ForceProjector projector = (ForceProjector)tile.block();
            float hit = projector.shieldHit(tile.entity);
            if(hit <= 0f) continue;

            Draw.color(Color.WHITE);
            Draw.alpha(hit);
            Fill.poly(projector.centerX(tile), projector.centerY(tile), 6, projector.realRadius(tile.entity));
        }
        //本项目没有 Draw.reset() → 用 color() 复位
        Draw.color();
        //⚠ 出 FBO 前必须先落盘（同进入前，libgdx 的 end() 不会 flush）
        Draw.flush();
        shieldBuffer.end();
        //恢复进入前的绑定（否则会把外层的像素化 FBO 踢掉）
        FboUtil.restore(prevFbo);

        //把护盾图用护盾 shader 贴回屏幕（对应原版 `Draw.shader(Shaders.shield); Draw.rect(Draw.wrap(...), ...)`）
        float viewW = viewWidth(), viewH = viewHeight();
        Draw.shader(Shaders.shield);
        //⚠ libgdx 没有 arc 的 Shader.apply() 钩子 → 显式灌 uniform
        //（u_texsize 也不是 SpriteBatch 自动喂的，见 ShieldShader.applyUniforms 的说明）
        Shaders.shield.applyUniforms(viewW, viewH);
        Draw.color(Pal.accent);
        //⚠⚠ 必须把 v/v2 对调：libgdx 的 SpriteBatch 把 region.v2 放在底边、FBO 纹理的 v=0 是 GL 底行
        //（详见 LightRenderer.draw / Pixelator.drawPixelate 的同一处说明）
        Tmp.tr1.setRegion(shieldBuffer.getColorBufferTexture());
        Tmp.tr1.setRegion(0f, 1f, 1f, 0f);
        Draw.rect(Tmp.tr1, Core.camera.position.x, Core.camera.position.y, viewW, viewH);
        Draw.color();
        Draw.shader();
    }

    /**
     * 「简易护盾」：直接画到屏幕（对应原版 {@code shieldGroup.draw(..., ShieldEntity::drawSimple)}）。
     * <p>原版结尾是 {@code Draw.reset()}（复位颜色与描边），本项目没有该重载 → 分别复位。
     */
    private void drawSimpleShields(){
        for(int i = 0; i < shieldTiles.size; i++){
            Tile tile = shieldTiles.get(i);
            ForceProjector projector = (ForceProjector)tile.block();
            float cx = projector.centerX(tile), cy = projector.centerY(tile);
            float rad = projector.realRadius(tile.entity);

            Draw.color(Pal.accent);
            Lines.stroke(1.5f);
            Draw.alpha(0.09f + 0.08f * projector.shieldHit(tile.entity));
            Fill.poly(cx, cy, 6, rad);
            Draw.alpha(1f);
            Lines.poly(cx, cy, 6, rad);
        }
        Draw.color();
        Lines.stroke(1f);
    }

    private void drawTiles(World world){
        float viewW = viewWidth();
        float viewH = viewHeight();
        float cx = Core.camera.position.x, cy = Core.camera.position.y;

        int minx = world.toTile(cx - viewW / 2f) - 1;
        int maxx = world.toTile(cx + viewW / 2f) + 1;
        int miny = world.toTile(cy - viewH / 2f) - 1;
        int maxy = world.toTile(cy + viewH / 2f) + 1;

        //⚠⚠ 必须把遍历范围钳进地图（原版 BlockRenderer.buildRequests 就是这么做的：
        //`int minx = Math.max(avgx - rangex - expandr, 0); ... int maxx = Math.min(world.width() - 1, ...)`）。
        //不钳的后果：开局降落动画的镜头缩放最小到 `Scl.scl(0.01f)`（`Renderer.zoomIn`），
        //可视世界区域 = 屏幕像素 / 0.01 ≈ 192000 × 100800 世界单位 → 循环退化成 23906 × 12564 ≈ **3 亿格/趟**，
        //越界格 `world.tile()` 返回 null 被 continue —— 纯属白跑。实测：scl=0.0100 时单帧 `Renderer.draw()`
        //耗时 **1029 ms（1 fps）**，随动画拉近逐步回落，动画结束（scl≈4）后恢复 110 fps。
        //钳到地图内后上限就是地图格数（本例 61×33 的视口 vs 全图），开销回到 O(可视面积)。
        minx = Math.max(minx, 0);
        miny = Math.max(miny, 0);
        maxx = Math.min(maxx, world.width() - 1);
        maxy = Math.min(maxy, world.height() - 1);

        //第一遍：地板。必须整体先画完，否则多格建筑贴图会被它"上方/右侧"格子的地板盖掉一部分
        //（多格贴图由中心格绘制、会延伸到相邻格，而循环是自下而上/自左向右的）
        for(int y = miny; y <= maxy; y++){
            for(int x = minx; x <= maxx; x++){
                Tile tile = world.tile(x, y);
                if(tile == null) continue;

                Floor floor = tile.floor();
                if(floor != null && floor != Blocks.air){
                    TextureRegion region = floor.variant(tile.x, tile.y);
                    if(region != null){
                        Core.batch.draw(region, tile.x * tilesize, tile.y * tilesize, tilesize, tilesize);
                        //地板边缘过渡（对应原版 Floor.draw 里 `Draw.rect(variant)` 之后紧跟的 `drawEdges(tile)`）：
                        //按 8 邻格挑出"要往本格压边缘"的地板，把对应的边缘小图叠上来。
                        floor.drawEdges(tile);
                    }
                }

                //覆盖层：矿脉斑点、波次出生点标记（对应原版 drawFloor 里随地板绘制的 overlay）
                Floor overlay = tile.overlay();
                if(overlay != null && overlay != Blocks.air){
                    TextureRegion oRegion = overlay.variant(tile.x, tile.y);
                    if(oRegion != null){
                        Core.batch.draw(oRegion, tile.x * tilesize, tile.y * tilesize, tilesize, tilesize);
                    }
                }
            }
        }

        //地面残骸贴花：画在地板之上、方块之下（对应原版 `groundEffectGroup.draw(BelowLiquidTrait)` 的位置，
        //它排在地板之后、方块之前；本项目把 Puddle 放在了方块之后，这里按原版顺序插在地板与方块之间）
        com.phoenix.game.entities.effect.Decal.drawAll();

        //方块落影（对应原版 Renderer 里 `blocks.drawShadows()`，排在地面特效之后、方块层之前）
        blocks.drawShadows();

        //第二遍：方块与叠层（对应原版 Renderer 的 drawFloor/drawBlocks 分离）
        for(int y = miny; y <= maxy; y++){
            for(int x = minx; x <= maxx; x++){
                Tile tile = world.tile(x, y);
                if(tile == null) continue;

                float dx = tile.x * tilesize;
                float dy = tile.y * tilesize;

                //卫星瓦片不单独绘制：整个多格建筑由中心瓦片一次性绘制
                //（注意不能用 block.isHidden() 判断——tile.block() 对卫星瓦片会转发到中心方块）
                Block block = tile.block();
                if(block != null && block != Blocks.air && !tile.isLinked() && !block.isHidden()){
                    //方块本体（默认按 region 铺满；传送带等带朝向/动画的方块自行绘制）
                    block.draw(tile);
                    //受损裂纹（对应原版 BlockRenderer.drawBlocks 里 `draw(tile)` 之后紧跟的
                    //`if(tile.entity != null && tile.entity.damaged()) block.drawCracks(tile);`）
                    if(tile.entity != null && tile.entity.damaged()){
                        block.drawCracks(tile);
                    }
                    //叠层内容（如传送带上的物品）
                    block.drawLayer(tile);
                    //非己方建筑：叠一圈队伍色边框（对应原版 BlockRenderer.drawBlocks 里的
                    //`if(block.synthetic() && request.tile.getTeam() != player.getTeam()) block.drawTeam(tile);`）。
                    //⚠ 本项目没有 player 时的守卫：菜单/服务端 Vars.player 可能为 null
                    if(Vars.player != null && block.synthetic() && tile.getTeam() != Vars.player.getTeam()){
                        block.drawTeam(tile);
                    }
                }

                if(drawGrid){
                    Core.batch.setColor(0f, 0f, 0f, 0.15f);
                    Core.batch.draw(region("white"), dx, dy, tilesize, 1f);
                    Core.batch.draw(region("white"), dx, dy, 1f, tilesize);
                    Core.batch.setColor(1f, 1f, 1f, 1f);
                }
            }
        }

        //地图变暗层（对应原版 `blocks.drawFog()`，排在方块层之后、顶层/放置层之前）
        blocks.drawFog();

        //第三遍：顶层内容（电力节点的激光连线要盖在相邻方块之上）
        for(int y = miny; y <= maxy; y++){
            for(int x = minx; x <= maxx; x++){
                Tile tile = world.tile(x, y);
                if(tile == null || tile.isLinked()) continue;

                Block block = tile.block();
                if(block != null && block != Blocks.air){
                    block.drawTopLayer(tile);
                }
            }
        }
    }

    /**
     * 收集本帧所有发光方块的光源（对应原版 {@code BlockRenderer.java:221-222} 里为 {@code Layer.lights}
     * 建请求的那一支，以及 {@code BlockRenderer.java:281-282} 里调用的 {@code block.drawLight(tile)}）。
     *
     * <p>原版判据：{@code state.rules.lighting && tile.block().synthetic() && !(tile.block() instanceof BlockPart)}。
     * <p>⚠ <b>本项目差异</b>：本项目 {@code Tile.block()} 对多格建筑的卫星瓦片会<b>转发到中心方块</b>
     * （见 {@code Tile.block()} 的注释），所以"是不是 BlockPart"要判 {@code tile.isLinked()}
     * —— 否则一个 3×3 建筑会被登记 9 次光源，且每次用的都是卫星瓦片自己的坐标（光斑散开）。
     *
     * <p>⚠ 遍历用下标循环（热路径规范：libgdx Array 的 for-each 迭代器不能嵌套）。
     * <p>只登记、不绘制；真正的绘制在 {@code Renderer.draw()} 的 {@code lights.draw()} 里（原版顺序）。
     */
    private void drawLights(World world){
        //关灯时直接跳过（原版在 BlockRenderer 的请求阶段就判了 state.rules.lighting）
        if(!lights.enabled()) return;

        //⚠ 按可见范围裁剪（对应原版 {@code BlockRenderer.processBlocks} 里 {@code Layer.lights} 的请求范围）：
        //原版 `int rangex = (int)(camera.width / tilesize / 2) + 3;`，两端再各扩 {@link #lightExpandr} 格
        //（灯光层**比方块层多扩**：光斑会溢出屏幕，只要灯在屏幕外一点点，它照进可见区的那部分仍要画），
        //最后两端都钳到地图内。本项目此前是**全图遍历** —— 每帧白扫 width*height 格（大图上几十万格），
        //且与 {@code drawTiles} 的口径不一致。
        //⚠ 与 {@code drawTiles} 一样：**必须钳**，否则极端缩放下（{@code minZoomScl = Scl.scl(0.01f)}）范围会爆炸。
        float cx = Core.camera.position.x, cy = Core.camera.position.y;
        int rangex = (int)(viewWidth() / tilesize / 2f) + 3;
        int rangey = (int)(viewHeight() / tilesize / 2f) + 3;

        int minx = Math.max(world.toTile(cx) - rangex - lightExpandr, 0);
        int miny = Math.max(world.toTile(cy) - rangey - lightExpandr, 0);
        int maxx = Math.min(world.toTile(cx) + rangex + lightExpandr, world.width() - 1);
        int maxy = Math.min(world.toTile(cy) + rangey + lightExpandr, world.height() - 1);

        for(int y = miny; y <= maxy; y++){
            for(int x = minx; x <= maxx; x++){
                Tile tile = world.tile(x, y);
                if(tile == null || tile.isLinked()) continue;

                Block block = tile.block();
                if(block != null && block != Blocks.air && block.synthetic()){
                    block.drawLight(tile);
                }
            }
        }
    }

    /**
     * 绘制"被摧毁方块的重建幽灵轮廓"（对应原版 {@code BlockRenderer.drawDestroyed}）：
     * 玩家在放置/拆除时淡入，在被摧毁过的格子上叠一层半透明原方块图标，提示这里原来有什么、可以重建。
     * 受设置 {@code destroyedblocks} 控制（对应原版 {@code Core.settings.getBool("destroyedblocks")}）。
     * <p>数据来源 = {@code state.teams.brokenBlocks(玩家队伍)}，由 {@code core.Control} 的两个监听器维护
     * （对应原版 {@code core.Logic} 里的同款监听器）。
     * <p><b>降级</b>：原版还叠了一层 {@code Draw.mixcol(Color.white, 0.2f + Mathf.absin(...))} 的白色脉动，
     * libgdx 的 {@code SpriteBatch} 没有 arc 的 mix color（额外加色混合）→ 只保留 alpha 淡入淡出。
     */
    private void drawDestroyed(){
        if(!Core.settings.getBool("destroyedblocks")) return;
        if(Vars.state == null || Vars.state.teams == null || Vars.player == null) return;

        boolean acting = Vars.control != null && Vars.control.input != null
            && (Vars.control.input.isPlacing() || Vars.control.input.isBreaking());
        brokenFade = Mathf.lerpDelta(brokenFade, acting ? 1f : 0f, 0.1f);
        if(brokenFade <= 0.001f) return;

        Array<Teams.BrokenBlock> broken = Vars.state.teams.brokenBlocks(Vars.player.getTeam());
        if(broken.size == 0) return;

        //可见范围（原版用 camera.bounds(Tmp.r1).grow(tilesize * 2f) 做视口剔除）
        float hw = viewWidth() / 2f + tilesize * 2f, hh = viewHeight() / 2f + tilesize * 2f;
        float cx = Core.camera.position.x, cy = Core.camera.position.y;

        Draw.color(); //复位到白色（本项目没有 Draw.reset()）
        for(int i = 0; i < broken.size; i++){
            Teams.BrokenBlock bb = broken.get(i);
            Block b = Vars.content.block(bb.block);
            if(b == null || b == Blocks.air) continue;

            //方块几何中心（原版 block.x * tilesize + b.offset() 在原版口径下即中心，本项目要加半格）
            float bx = bb.x * tilesize + b.offset() + tilesize / 2f;
            float by = bb.y * tilesize + b.offset() + tilesize / 2f;
            if(Math.abs(bx - cx) > hw || Math.abs(by - cy) > hh) continue;

            TextureRegion icon = b.icon(Cicon.full);
            if(icon == null) continue;

            Draw.alpha(0.33f * brokenFade);
            Draw.rect(icon, bx, by, b.rotate ? (1 - (bb.rotation & 3)) * 90f : 0f);
        }
        Draw.color();
    }

    /**
     * 地面单位的落地阴影（对应原版 {@code Renderer.drawGroundShadows}）：每个地面单位脚下画一个
     * {@code circle-shadow} 半透明黑圆。
     * <p><b>顺序等价替代</b>：原版把这一趟夹在方块层之间（overlay 层之后、turret 层之前）；本项目
     * {@code drawTiles()} 是一次性画完所有方块层 → 只能排在所有方块之后、单位之前（视觉效果等价：
     * 阴影压在地面/方块上、被单位本体盖住）。
     * <p><b>偏离原版（两处）</b>：
     * <ul>
     *   <li>原版遍历 {@code unitGroup} + {@code playerGroup}（原版 Player 本身就是 Unit）；本项目
     *       {@code Player} 是独立对象、其单位也在 {@code Units.units} 里 → 只遍历 {@code Units.units} 即可
     *       覆盖玩家单位，且不会重复绘制。</li>
     *   <li>原版用 {@code region.getWidth()/getHeight()}（arc 口径）；libgdx 的 {@code TextureRegion}
     *       没有这两个方法 → 改用 {@code getRegionWidth()/getRegionHeight()}（与 {@code drawLanding} 同口径）。</li>
     * </ul>
     */
    private void drawGroundShadows(){
        Draw.color(0f, 0f, 0f, 0.4f);
        float rad = 1.6f;
        TextureRegion shadow = region("circle-shadow");
        if(shadow != null){
            for(int i = 0; i < Units.units.size; i++){
                BaseUnit u = Units.units.get(i);
                if(u.isDead() || u.isFlying()) continue;
                //图标缺失时 getIconRegion() 可能为 null（本项目 findRegion 缺图返回 null）→ 跳过
                TextureRegion icon = u.getIconRegion();
                if(icon == null) continue;
                float size = Math.max(icon.getRegionWidth(), icon.getRegionHeight()) * Draw.scl;
                Draw.rect(shadow, u.x, u.y, size * rad, size * rad);
            }
        }
        Draw.color();
    }

    /**
     * 飞行单位的投影（对应原版 {@code Renderer.drawFlyerShadows}）：把飞行单位的图标按固定偏移
     * 画成半透明黑色剪影（偏移量 -12/-13 照抄原版）。
     * <p><b>顺序等价替代</b>：同 {@link #drawGroundShadows()}，原版夹在方块层之间，本项目统一排在单位之前。
     * <p><b>偏离原版</b>：原版遍历 {@code unitGroup} + {@code playerGroup}，本项目只遍历 {@code Units.units}
     * （玩家单位也在其中，见 {@link #drawGroundShadows()} 的说明）。
     */
    private void drawFlyerShadows(){
        Draw.color(0f, 0f, 0f, 0.22f);
        for(int i = 0; i < Units.units.size; i++){
            BaseUnit u = Units.units.get(i);
            if(u.isDead() || !u.isFlying()) continue;
            u.drawShadow(-12f, -13f);
        }
        Draw.color();
    }

    private void drawUnits(){
        //下标遍历，避免 libgdx Array 迭代器嵌套
        for(int i = 0; i < Units.units.size; i++){
            BaseUnit unit = Units.units.get(i);
            if(unit.isDead()) continue;
            unit.draw();
            //单位顶部能量核心覆盖层（对应原版 Unit.drawAll 的 `draw(); drawStats();`）。
            //⚠ 与本项目自加的 drawHealthBars/drawBossHealthBars（世界内血条）无关，那两套保持原样。
            unit.drawStats();
        }

        //对应原版 Renderer 的独立 drawOver 通道（unitGroup.draw(..., Unit::drawOver)）：
        //单位本体画完后统一补画"压在单位之上"的内容（无人机采矿光束/建造预览等）。
        //必须单独一趟，否则会被同批后面绘制的单位盖住。
        for(int i = 0; i < Units.units.size; i++){
            BaseUnit unit = Units.units.get(i);
            if(unit.isDead()) continue;
            unit.drawOver();
        }
    }

    private void drawBullets(){
        for(int i = 0; i < Bullet.all.size; i++){
            Bullet bullet = Bullet.all.get(i);
            if(bullet.isDead()) continue;
            bullet.draw();
        }

        //激光弹这类"纯几何"的子弹会让 Drawf 的 ShapeRenderer 会话一直开着；这里收尾，
        //否则后面的 batch 精灵（电力线/血条）会先于几何落盘，绘制顺序错乱
        com.phoenix.game.core.Drawf.end();
    }

    /** 绘制电网连接线：把相邻的带电建筑连成拓扑（黄色细线）。 */
    private void drawPowerLinks(World world){
        TextureRegion white = region("white");
        if(white == null) return;
        if(world.powerGraphs.size == 0) return;

        for(Tile tile : world.tiles){
            if(tile == null || tile.entity == null || tile.entity.power == null) continue;
            if(tile.link() != tile) continue;

            for(com.badlogic.gdx.math.GridPoint2 dir : com.phoenix.game.math.geom.Geometry.d4){
                Tile next = tile.getNearby(dir.x, dir.y);
                if(next == null || next.entity == null || next.entity.power == null) continue;
                //只画一次（避免重复）：按坐标序比较
                if(next.pos() < tile.pos()) continue;

                float x1 = tile.worldx() + tilesize / 2f, y1 = tile.worldy() + tilesize / 2f;
                float x2 = next.worldx() + tilesize / 2f, y2 = next.worldy() + tilesize / 2f;
                float len = com.badlogic.gdx.math.Vector2.dst(x1, y1, x2, y2);
                float ang = com.phoenix.game.math.Angles.angle(x1, y1, x2, y2);

                Core.batch.setColor(1f, 0.85f, 0.2f, 0.7f); //电力黄
                Core.batch.draw(white, x1, y1, 0f, 0f, len, 2f, 1f, 1f, ang);
                Core.batch.setColor(1f, 1f, 1f, 1f);
            }
        }
    }

    /** 绘制受损建筑的浮空血条。 */
    private void drawHealthBars(World world){
        TextureRegion white = region("white");
        if(white == null) return;

        for(Tile tile : world.tiles){
            if(tile == null || tile.entity == null) continue;
            //卫星瓦片不重复绘制（链接回中心）
            if(tile.link() != tile) continue;

            float hp = tile.entity.healthf();
            if(hp <= 0f || hp >= 0.999f) continue;

            float size = tile.block().size * tilesize;
            float x = tile.worldx() + size / 2f;
            float y = tile.worldy() + size + 3f;
            float bw = size + 2f;
            float bh = 3f;

            //背景
            Core.batch.setColor(0f, 0f, 0f, 0.6f);
            Core.batch.draw(white, x - bw / 2f, y, bw, bh);
            //血条
            Core.batch.setColor(1f, hp, hp, 1f);
            Core.batch.draw(white, x - bw / 2f + 1f, y + 1f, (bw - 2f) * hp, bh - 2f);
            Core.batch.setColor(1f, 1f, 1f, 1f);
        }
    }


    /**
     * 开局降落的落点表现（对应原版 {@code Renderer.drawLanding}）：
     * 在最近己方核心处画一个随 landTime 收缩的阴影圈 + 放射线，并把核心图标旋转着放大"落"下来。
     * <p>⚠ <b>降级</b>：原版此处 {@code Draw.mixcol(Color.white, fract)} 是 arc 的加色混合，
     * libgdx 的 {@code SpriteBatch} 没有 → 改用 {@code Draw.alpha(fract)} 做淡入近似
     * （视觉上少了"发光"，但落点/旋转/尺寸一致）。
     * <p>⚠ <b>偏离</b>：原版取 {@code player.getClosestCore()} 且直接用 {@code entity.x/entity.y}
     * （原版口径下即方块中心）；本项目 {@code Player} 没有该方法、且 {@code Tile.worldx()/worldy()} 是
     * <b>左下角</b> → 用 {@code state.teams.closestCore(...)} 取核心、再用 {@code block.centerX/centerY} 取中心。
     */
    private void drawLanding(){
        if(landTime <= 0f || Vars.state == null || Vars.state.isMenu() || Vars.player == null) return;
        if(Vars.state.teams == null) return;

        Tile core = Vars.state.teams.closestCore(Vars.player.getX(), Vars.player.getY(), Vars.player.getTeam());
        if(core == null) return;

        TextureRegion reg = core.block().icon(Cicon.full);
        if(reg == null) return;

        float fract = landTime / com.phoenix.game.content.Fx.coreLand.lifetime;
        //本项目 tile.worldx() 是左下角，方块中心要取 centerX/centerY（原版 worldx() 本身就是中心）
        float cx = core.block().centerX(core), cy = core.block().centerY(core);
        float scl = Scl.scl(4f) / camerascale;
        float s = reg.getRegionWidth() * Draw.scl * scl * 4f * fract;

        Draw.color(Pal.lightTrail);
        TextureRegion shadow = region("circle-shadow");
        if(shadow != null){
            Draw.rect(shadow, cx, cy, s, s);
        }

        //放射线：随降落推进逐渐缩短、变细
        Angles.randLenVectors(1, (1f - fract), 100, 1000f * scl * (1f - fract), (x, y, fin, fout) -> {
            Lines.stroke(scl * fin);
            Lines.lineAngle(cx + x, cy + y, Mathf.angle(x, y), (fin * 20 + 1f) * scl);
        });

        Draw.color();
        //降级：原版 Draw.mixcol(Color.white, fract)（arc 的加色混合）→ 用 alpha 淡入近似
        Draw.alpha(fract);
        Draw.rect(reg, cx, cy, reg.getRegionWidth() * Draw.scl * scl, reg.getRegionHeight() * Draw.scl * scl, fract * 135f);
        //复位（本项目没有 Draw.reset()）
        Draw.color();
    }

    private static TextureRegion region(String name){
        if(Core.atlas == null) return null;
        TextureRegion region = Core.atlas.findRegion(name);
        if(region == null) region = Core.atlas.findRegion("blank");
        return region;
    }
}
