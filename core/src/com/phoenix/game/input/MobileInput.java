package com.phoenix.game.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.content.Fx;
import com.phoenix.game.game.Team;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Build;
import com.phoenix.game.world.BuildRequest;
import com.phoenix.game.world.Tile;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * 移动端触摸输入。参照 Mindustry {@code mindustry.input.MobileInput}（Build 126.2）移植。
 *
 * <p><b>与原版的差异（刻意偏离，因为 phoenix 的输入层是一次重构而非逐行移植）</b>：
 * <ul>
 *   <li>原版用 {@code PlaceMode mode}（none/placing/breaking/schematicSelect）表示状态，
 *       phoenix 的基类用 {@code buildBlock != null} / {@code breaking} / {@code pasteSchematic} 三个布尔，
 *       这里沿用基类语义，额外加一个 {@link #schematicSelect} 表示"正在框选蓝图区域"。</li>
 *   <li>原版 {@code block} / {@code rotation} → phoenix 的 {@code buildBlock} / {@code buildRotation}。</li>
 *   <li>原版在 {@code update()} 里读 {@code Core.input.mouseX/Y}，phoenix 用 libgdx 的 {@code Gdx.input.getX/getY}
 *       —— 在 Android 上它就是当前触摸点，所以基类的 {@link #updateTarget()} 直接可用。</li>
 *   <li>原版用 {@code Core.scene.hasMouse()} 判断"这一下点到 UI 了没"；phoenix 靠 InputMultiplexer 的顺序
 *       （stage 在前）天然实现：点中按钮时 Stage 消费掉事件，GestureDetector 收不到，因此不需要这个判断。</li>
 *   <li>未移植：瞄准目标（{@code checkTargets}，phoenix 没有 target 系统）、
 *       蓝图另存（{@code showSchematicSave}，phoenix 没有文本输入框）。</li>
 *   <li>采矿（{@code tryBeginMine}）已接：点非 UI 的非可存取瓦片时尝试开采（见 {@code tap}）。
 *       丢物品（{@code tryDropItems}）也已接。</li>
 *   <li>图标替代：原版的 {@code hammer/right/ok/flipX/flipY} 在 phoenix 的图标集里没有，
 *       分别用 {@code break/arrowRight/check/rotateLeft/rotateRight} 代替。</li>
 * </ul>
 *
 * <p><b>操作方式</b>：单指拖动平移镜头（拖到屏幕边缘会自动继续平移）、双指捏合缩放、
 * 点按把方块放进待确认队列（绿色半透明预览）、长按进入连线模式、底部按钮切换拆除/旋转/确认。
 */
public class MobileInput extends InputHandler implements GestureDetector.GestureListener{
    /** 边缘自动平移的最大速度（世界单位/帧，对应原版 maxPanSpeed）。 */
    private static final float maxPanSpeed = 1.3f;
    /** 距离屏幕边缘多少像素开始自动平移。Scl.scl 不能在字段初始化时调用（Core.app 还是 null），故懒算。 */
    private float edgePan = -1f;

    private final Vector2 vector = new Vector2();
    /** 上一次的缩放值（捏合开始时记一次，之后按距离比例缩放）。 */
    private float lastZoom = -1f;

    /** 连线模式的起点与上一帧光标（瓦片坐标）。 */
    private int lastLineX, lastLineY;

    /** 平移待确认请求时的累计位移（不足一格时先攒着，对应原版 shiftDeltaX/Y）。 */
    private float shiftDeltaX, shiftDeltaY;

    /** 已确认、正在淡出的请求（对应原版 removals）。 */
    private final Array<BuildRequest> removals = new Array<>();
    /** 待确认的建造/拆除请求（移动端"先摆后确认"队列，对应原版 selectRequests）。 */
    public final Array<BuildRequest> selectRequests = new Array<>();

    /** 是否正在拖动整批待确认请求。 */
    private boolean selecting;
    /** 是否处于蓝图粘贴模式（原版 schematicMode）。 */
    private boolean schematicMode;
    /** 是否正在框选蓝图区域（原版 PlaceMode.schematicSelect）。 */
    private boolean schematicSelect;
    /** 切换到拆除模式前的方块（原版用它判断"选了新方块就自动切回建造"）。 */
    private Block lastBlock;
    /** 手指是否按下（pan 期间用它过滤掉 UI 上的拖动）。 */
    private boolean down;

    /** 碰撞检测复用的矩形（对应原版 InputHandler 的 r1/r2）。 */
    private final Rectangle r1 = new Rectangle(), r2 = new Rectangle();

    //UI 按钮引用：libgdx 的 Cell 没有 arc 那种 update(Cons) 回调，
    //所以把按钮存下来，在 update() 里统一刷新它们的选中态/图标。
    private ImageButton breakButton, diagonalButton, rotateButton, confirmButton;
    private Table schemPanel;

    // ---- 工具方法 ----

    private float edgePan(){
        if(edgePan < 0f){
            edgePan = Scl.scl(60f);
        }
        return edgePan;
    }

    /** @return 屏幕坐标对应的瓦片；越界返回 null。 */
    private Tile tileAt(float screenX, float screenY){
        if(Vars.world == null) return null;
        return Vars.world.tile(Vars.world.toTile(mouseWorldX(screenX)), Vars.world.toTile(mouseWorldY(screenY)));
    }

    /** @return 这条瓦片是否已经在待确认队列里（或与其中某条请求重叠）。 */
    private boolean hasRequest(Tile tile){
        return getRequest(tile) != null;
    }

    /** @return 与这条瓦片重叠的待确认请求；没有返回 null。 */
    private BuildRequest getRequest(Tile tile){
        if(tile == null) return null;

        r2.setSize(Vars.tilesize);
        r2.setCenter(tile.worldx(), tile.worldy());

        for(int i = 0; i < selectRequests.size; i++){
            BuildRequest req = selectRequests.get(i);
            Tile other = req.tile();
            if(other == null) continue;

            if(!req.breaking){
                r1.setSize(req.block.size * Vars.tilesize);
                r1.setCenter(other.worldx() + req.block.offset(), other.worldy() + req.block.offset());
            }else{
                r1.setSize(other.block().size * Vars.tilesize);
                r1.setCenter(other.worldx() + other.block().offset(), other.worldy() + other.block().offset());
            }

            if(r2.overlaps(r1)) return req;
        }
        return null;
    }

    /** @return 在这里放 block 是否会与队列里已有的请求重叠（多格建筑互相压住）。 */
    private boolean checkOverlapPlacement(int x, int y, Block block){
        r2.setSize(block.size * Vars.tilesize);
        r2.setCenter(x * Vars.tilesize + block.offset(), y * Vars.tilesize + block.offset());

        for(int i = 0; i < selectRequests.size; i++){
            BuildRequest req = selectRequests.get(i);
            Tile other = req.tile();
            if(other == null || req.breaking) continue;

            r1.setSize(req.block.size * Vars.tilesize);
            r1.setCenter(other.worldx() + req.block.offset(), other.worldy() + req.block.offset());

            if(r2.overlaps(r1)) return true;
        }
        return false;
    }

    /** 从队列里摘掉一条请求（建造请求进淡出列表）。 */
    private void removeRequest(BuildRequest request){
        selectRequests.removeValue(request, true);
        if(!request.breaking){
            removals.add(request);
        }
    }

    /** @return 这一格能不能拆（自己的、可拆的方块）。 */
    private boolean validBreak(int x, int y){
        if(Vars.world == null) return false;
        return Build.canDeconstruct(Vars.world.tile(x, y), playerTeam());
    }

    // ---- 挂载点：UI ----

    @Override
    public void buildPlacementUI(Table table){
        table.add().height(4f).colspan(4).growX();
        table.row();
        table.left().defaults().size(48f);

        //拆除模式开关
        breakButton = iconButton(Icon.breaki, Styles.clearTogglePartiali, () -> {
            breaking = !breaking;
            if(breaking){
                lastBlock = buildBlock;
                buildBlock = null;
                pasteSchematic = null;
            }else{
                buildBlock = lastBlock;
            }
        });
        table.add(breakButton);

        //斜向放置开关（原版 swapdiagonal，持久化到 settings）
        diagonalButton = iconButton(Icon.diagonal, Styles.clearTogglePartiali, () -> {
            Core.settings.put("swapdiagonal", !Core.settings.getBool("swapdiagonal"));
            Core.settings.save();
        });
        table.add(diagonalButton);

        //旋转 / 蓝图模式切换
        rotateButton = iconButton(Icon.arrowRight, Styles.clearTogglePartiali, () -> {
            if(buildBlock != null && buildBlock.rotate){
                buildRotation = (buildRotation + 1) & 3;
            }else{
                schematicMode = !schematicMode;
                if(schematicMode){
                    buildBlock = null;
                    breaking = false;
                    pasteSchematic = null;
                }
            }
        });
        table.add(rotateButton);

        //确认：把待确认队列真正提交（建造入玩家建造队列、拆除直接拆）
        confirmButton = iconButton(Icon.check, Styles.clearPartiali, this::confirmRequests);
        table.add(confirmButton);
    }

    @Override
    public void buildUI(Table table){
        //左下角"取消"：清掉玩家单位的建造队列 + 待确认队列 + 当前选中
        ImageButton cancel = iconButton(Icon.cancel, Styles.clearPartiali, () -> {
            if(Vars.player != null) Vars.player.clearBuilding();
            selectRequests.clear();
            clearBuild();
            schematicMode = false;
            schematicSelect = false;
        });
        table.add(cancel).size(48f).padLeft(12f).padBottom(12f);

        //蓝图面板（旋转/翻转/取消）：只在"选中了蓝图且有待确认请求"时出现
        schemPanel = new Table();
        schemPanel.setBackground(Tex.pane);
        schemPanel.defaults().size(50f);
        schemPanel.add(iconButton(Icon.rotateLeft, Styles.clearPartiali, () -> flipRequests(true)));
        schemPanel.add(iconButton(Icon.rotateRight, Styles.clearPartiali, () -> flipRequests(false)));
        schemPanel.row();
        schemPanel.add(iconButton(Icon.rotate, Styles.clearPartiali, () -> rotateRequests()));
        schemPanel.add(iconButton(Icon.cancel, Styles.clearPartiali, selectRequests::clear));
        table.add(schemPanel).padLeft(8f).padBottom(12f);
    }

    /** 造一个图标按钮（libgdx 没有 Table.addImageButton，得自己 new + add）。 */
    private static ImageButton iconButton(com.badlogic.gdx.scenes.scene2d.utils.Drawable icon, ImageButton.ImageButtonStyle base, Runnable action){
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle(base);
        style.imageUp = icon;
        ImageButton button = new ImageButton(style);
        //关掉"程序化 setChecked 也发 ChangeEvent"：否则 refreshButtons() 刷新选中态会反过来再触发一次动作
        button.setProgrammaticChangeEvents(false);
        button.addListener(new ChangeListener(){
            @Override
            public void changed(ChangeEvent event, Actor actor){
                action.run();
            }
        });
        return button;
    }

    /** 提交待确认队列：建造请求入玩家建造队列，拆除请求直接拆。 */
    private void confirmRequests(){
        for(int i = 0; i < selectRequests.size; i++){
            BuildRequest request = selectRequests.get(i);
            Tile tile = request.tile();
            if(tile == null) continue;

            if(!request.breaking){
                if(Build.validPlace(tile, request.block, request.rotation, playerTeam())){
                    if(Vars.player != null) Vars.player.addBuildRequest(request.copy());
                }
                buildRotation = request.rotation;
            }else{
                breakTile(tile.x, tile.y);
            }
        }

        //建造请求留在场上淡出，拆除请求直接消失
        for(int i = selectRequests.size - 1; i >= 0; i--){
            BuildRequest req = selectRequests.get(i);
            if(!req.breaking) removals.add(req);
        }
        selectRequests.clear();
        selecting = false;
    }

    /** 把待确认队列整体平移/旋转（原版 flipRequests / rotateRequests 的简化版）。 */
    private void flipRequests(boolean xAxis){
        if(selectRequests.size == 0) return;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for(int i = 0; i < selectRequests.size; i++){
            BuildRequest r = selectRequests.get(i);
            minX = Math.min(minX, r.x); maxX = Math.max(maxX, r.x);
            minY = Math.min(minY, r.y); maxY = Math.max(maxY, r.y);
        }

        for(int i = 0; i < selectRequests.size; i++){
            BuildRequest r = selectRequests.get(i);
            if(xAxis){
                r.x = minX + maxX - r.x;
            }else{
                r.y = minY + maxY - r.y;
            }
        }
    }

    /** 把待确认队列整体顺时针转 90°（坐标绕包围盒中心旋转，朝向同步 +1）。 */
    private void rotateRequests(){
        if(selectRequests.size == 0) return;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for(int i = 0; i < selectRequests.size; i++){
            BuildRequest r = selectRequests.get(i);
            minX = Math.min(minX, r.x); maxX = Math.max(maxX, r.x);
            minY = Math.min(minY, r.y); maxY = Math.max(maxY, r.y);
        }

        for(int i = 0; i < selectRequests.size; i++){
            BuildRequest r = selectRequests.get(i);
            int nx = minX + (maxY - r.y);
            int ny = minY + (r.x - minX);
            r.x = nx;
            r.y = ny;
            r.rotation = (r.rotation + 1) & 3;
        }
    }

    @Override
    public void updateState(){
        selectRequests.clear();
        removals.clear();
        clearBuild();
        schematicMode = false;
        schematicSelect = false;
    }

    @Override
    public boolean isBreaking(){
        return breaking;
    }

    // ---- 世界层绘制 ----

    @Override
    public void drawBottom(){
        if(Vars.world == null || Vars.state.isMenu()) return;

        TextureRegion white = whiteRegion();
        if(white == null) return;

        //已确认、正在淡出的请求
        for(int i = removals.size - 1; i >= 0; i--){
            BuildRequest request = removals.get(i);
            Tile tile = request.tile();
            if(tile == null){
                removals.removeIndex(i);
                continue;
            }

            request.animScale = com.phoenix.game.math.Mathf.lerpDelta(request.animScale, 0f, 0.2f);
            if(request.animScale <= 0.0001f){
                removals.removeIndex(i);
                continue;
            }

            if(request.breaking){
                drawGhost(tile, tile.block(), 0, false, white);
            }else{
                drawGhost(tile, request.block, request.rotation, true, white);
            }
        }

        //待确认队列：合法绿、非法偏红
        for(int i = 0; i < selectRequests.size; i++){
            BuildRequest request = selectRequests.get(i);
            Tile tile = request.tile();
            if(tile == null) continue;

            boolean ok = request.breaking
                ? validBreak(tile.x, tile.y)
                : Build.validPlace(tile, request.block, request.rotation, playerTeam()) && Build.canAfford(playerTeam(), request.block);

            request.animScale = com.phoenix.game.math.Mathf.lerpDelta(request.animScale, ok ? 1f : 0.6f, 0.2f);

            if(request.breaking){
                drawGhost(tile, tile.block(), 0, false, white);
            }else{
                drawGhost(tile, request.block, request.rotation, ok, white);
                if(request.block.rotate){
                    drawArrow(tile.x, tile.y, request.rotation, ok, request.block, white);
                }
            }
        }

        //框选蓝图区域：把范围描出来
        if(schematicSelect){
            drawSelectionRect(white);
        }

        Core.batch.setColor(1f, 1f, 1f, 1f);
    }

    /** 画蓝图框选的矩形范围（用格子描边）。 */
    private void drawSelectionRect(TextureRegion white){
        int x1 = Math.min(lineStartX, lastLineX), x2 = Math.max(lineStartX, lastLineX);
        int y1 = Math.min(lineStartY, lastLineY), y2 = Math.max(lineStartY, lastLineY);

        //框太大就不画了，免得糊满屏幕
        if(x2 - x1 > Vars.maxSchematicSize
            || y2 - y1 > Vars.maxSchematicSize) return;

        Core.batch.setColor(Pal.accent.r, Pal.accent.g, Pal.accent.b, 0.35f);
        for(int x = x1; x <= x2; x++){
            for(int y = y1; y <= y2; y++){
                if(x != x1 && x != x2 && y != y1 && y != y2) continue;
                Core.batch.draw(white, x * Vars.tilesize, y * Vars.tilesize, Vars.tilesize, Vars.tilesize);
            }
        }
    }

    // ---- 输入事件（GestureListener）----

    @Override
    public boolean touchDown(float x, float y, int pointer, int button){
        if(Vars.state.isMenu()) return false;
        down = true;

        //按在已有请求上 → 之后拖动是"整批挪位"而不是平移相机（对应原版 selecting = hasRequest(cursor)）
        selecting = hasRequest(tileAt(x, y));

        //蓝图框选模式：这一下按下就是框选起点
        if(schematicMode && buildBlock == null && !breaking){
            beginSchematicSelect(x, y);
        }

        return false;
    }

    /**
     * 手指抬起。这里处理"连线模式收尾"和"蓝图框选收尾"。
     * <p>之所以放在 InputProcessor 的 touchUp 而不是 GestureListener 的 panStop：
     * 长按之后如果没移动，GestureDetector 不会触发 pan/panStop。
     * <p>返回 false 让事件继续传给 GestureDetector（它负责判定 tap/fling）。
     */
    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button){
        if(pointer != 0) return false;
        down = false;
        selecting = false;

        if(Vars.state.isMenu()) return false;

        if(lineMode){
            flushMobileLine();
            lineMode = false;
        }else if(schematicSelect){
            finishSchematicSelect();
        }

        return false;
    }

    /**
     * 长按：进入连线模式（对应原版 MobileInput.longPress）。
     * <p>返回 true 很重要：libgdx 的 GestureDetector 会把返回值记进 {@code longPressFired}，
     * 之后的拖动就不会再走 {@code pan}（否则长按刚进入连线模式，镜头又被拖走了）。
     */
    @Override
    public boolean longPress(float x, float y){
        if(Vars.state.isMenu() || Vars.player == null || Vars.player.isDead()) return false;
        if(!isPlacing() && !breaking) return false;

        Tile cursor = tileAt(x, y);
        if(cursor == null) return false;

        if(breaking && buildBlock == null){
            //拆除：长按只是记起点，拖动时沿路径连续拆（在 update() 里做）
            lineStartX = cursor.x;
            lineStartY = cursor.y;
            lastLineX = cursor.x;
            lastLineY = cursor.y;
            lineMode = true;
            Effects.effect(Fx.tapBlock, cursor.worldx(), cursor.worldy(), 1f);
            return true;
        }

        if(buildBlock != null){
            beginLine(cursor.worldx(), cursor.worldy());
            lastLineX = cursor.x;
            lastLineY = cursor.y;
            Effects.effect(Fx.tapBlock, cursor.worldx() + buildBlock.offset(), cursor.worldy() + buildBlock.offset(), buildBlock.size);
            return true;
        }
        return false;
    }

    /** 点按：把方块放进待确认队列（对应原版 MobileInput.tap）。 */
    @Override
    public boolean tap(float x, float y, int count, int button){
        if(Vars.state.isMenu() || lineMode || Vars.player == null || Vars.player.isDead()) return false;

        Tile cursor = tileAt(x, y);
        if(cursor == null) return false;

        //点中队列里的请求 → 摘掉它（相当于撤销）
        if(hasRequest(cursor)){
            removeRequest(getRequest(cursor));
            return false;
        }

        //蓝图粘贴：整张蓝图进队列
        if(pasteSchematic != null){
            Array<BuildRequest> reqs = Vars.schematics.toRequests(pasteSchematic, cursor.x, cursor.y);
            for(int i = 0; i < reqs.size; i++){
                BuildRequest req = reqs.get(i);
                if(req.block != null) selectRequests.add(req);
            }
            return false;
        }

        if(breaking){
            Tile link = cursor.link() != null ? cursor.link() : cursor;
            if(validBreak(link.x, link.y) && !hasRequest(link)){
                selectRequests.add(new BuildRequest(link.x, link.y));
            }
            return false;
        }

        if(buildBlock != null){
            if(Build.validPlace(cursor, buildBlock, buildRotation, playerTeam()) && !checkOverlapPlacement(cursor.x, cursor.y, buildBlock)){
                selectRequests.add(new BuildRequest(cursor.x, cursor.y, buildRotation, buildBlock));
            }
            return false;
        }

        //玩家背包投递（对应原版 MobileInput 里的 `!tryTapPlayer(worldx, worldy)` 与
        //`tryDropItems(tile.link(), ...)` 两处）：点自己 → 进入投递状态；点其它位置 → 结算投递
        //（目标方块能收就存进去，不能收就丢弃）
        float dropWx = mouseWorldX(x), dropWy = mouseWorldY(y);
        if(tryTapPlayer(dropWx, dropWy)){
            return false;
        }
        tryDropItems(cursor.link() != null ? cursor.link() : cursor, dropWx, dropWy);

        //不在建造模式：点方块本体（可配置方块开配置面板；其余交给方块自己的 tapped）
        if(cursor.block() != Blocks.air){
            if(cursor.entity != null && cursor.block().configurable){
                //原版对非库存分支一律收起库存面板（tileTapped 末尾的 inv.hide），这里提前返回前同样处理
                if(Vars.hud != null) Vars.hud.inv.hide();
                configTile = configTile == cursor ? null : cursor;
                return false;
            }
            configTile = null;
            cursor.block().tapped(cursor, Vars.player);
        }

        //库存面板（对应原版 InputHandler.tileTapped 的 inv 分支）：
        //点核心/容器等有物品的可存取方块 → 弹出物品列表；点其他方块/空地 → 收起
        //没弹出面板时尝试采矿（对应原版 MobileInput 的 `else if(!canTapPlayer(...) && !tileTapped(...)) tryBeginMine(cursor)`）
        if(!tileTapped(cursor)){
            tryBeginMine(cursor);
        }

        return false;
    }

    @Override
    public boolean fling(float velocityX, float velocityY, int button){
        return false;
    }

    /** 拖动：平移相机；若正在拖整批待确认请求则改为整体位移（对应原版 MobileInput.pan）。 */
    @Override
    public boolean pan(float x, float y, float deltaX, float deltaY){
        if(!down) return false;
        if(lineMode || schematicSelect || pasteSchematic != null) return false;

        //1 个世界单位 = camerascale 像素，所以屏幕位移除以 camerascale 才是世界位移
        float scale = 1f / Math.max(0.0001f, Vars.renderer == null ? 1f : Vars.renderer.cameraScale());
        deltaX *= scale;
        deltaY *= scale;

        if(selecting){
            //拖整批待确认请求：累计到满一格才真正移动
            shiftDeltaX += deltaX;
            shiftDeltaY += deltaY;

            int shiftedX = (int)(shiftDeltaX / Vars.tilesize);
            int shiftedY = (int)(shiftDeltaY / Vars.tilesize);

            if(shiftedX != 0 || shiftedY != 0){
                for(int i = 0; i < selectRequests.size; i++){
                    BuildRequest req = selectRequests.get(i);
                    if(req.breaking) continue;
                    req.x += shiftedX;
                    req.y += shiftedY;
                }
                shiftDeltaX -= shiftedX * Vars.tilesize;
                shiftDeltaY -= shiftedY * Vars.tilesize;
            }
        }else{
            //平移相机（手指往右拖，画面跟着往右走 → 相机往左移）
            Core.camera.position.x -= deltaX;
            Core.camera.position.y -= deltaY;
        }
        return false;
    }

    @Override
    public boolean panStop(float x, float y, int pointer, int button){
        shiftDeltaX = shiftDeltaY = 0f;
        return false;
    }

    /** 双指捏合缩放（对应原版 MobileInput.zoom）。 */
    @Override
    public boolean zoom(float initialDistance, float distance){
        if(Vars.renderer == null || initialDistance <= 0f) return false;

        if(lastZoom < 0f){
            lastZoom = Vars.renderer.getScale();
        }

        Vars.renderer.setScale(distance / initialDistance * lastZoom);
        return true;
    }

    @Override
    public boolean pinch(Vector2 initialPointer1, Vector2 initialPointer2, Vector2 pointer1, Vector2 pointer2){
        return false;
    }

    @Override
    public void pinchStop(){
        //捏合结束：下次捏合重新取当前缩放作基准
        lastZoom = -1f;
    }

    // ---- 每帧逻辑 ----

    @Override
    public void update(){
        if(Vars.state.isMenu()){
            if(selectRequests.size > 0 || removals.size > 0 || isPlacing()){
                updateState();
            }
            refreshButtons();
            return;
        }

        if(Vars.player == null || Vars.player.isDead()){
            if(breaking || buildBlock != null){
                buildBlock = null;
                breaking = false;
                clearLine();
            }
        }

        //长按拖动时：连线模式按当前手指位置刷新路径 / 拆除模式沿路径连续拆
        if(lineMode && Vars.world != null && down){
            updateLineToCursor();

            if(breaking && buildBlock == null){
                int tx = Vars.world.toTile(targetX), ty = Vars.world.toTile(targetY);
                if(tx != lastLineX || ty != lastLineY){
                    int count = buildPath(lastLineX, lastLineY, tx, ty);
                    for(int i = 0; i < count; i++){
                        breakTile(pathX[i], pathY[i]);
                    }
                    lastLineX = tx;
                    lastLineY = ty;
                }
            }
        }

        //框选蓝图时跟随手指 + 边缘自动平移
        if(schematicSelect){
            lastLineX = Vars.world.toTile(targetX);
            lastLineY = Vars.world.toTile(targetY);
            autoPan();
        }

        //连线模式下拖到屏幕边缘自动平移（否则够不到远处）
        if(lineMode && down){
            autoPan();
        }

        //松开手指后清掉捏合基准，下次捏合重新取当前缩放
        if(!Gdx.input.isTouched()){
            lastZoom = -1f;
        }

        refreshButtons();
    }

    /** 按当前手指位置刷新连线路径。 */
    private void updateLineToCursor(){
        if(Vars.world == null) return;

        int lx = Vars.world.toTile(targetX), ly = Vars.world.toTile(targetY);
        if((lastLineX != lx || lastLineY != ly) && buildBlock != null){
            lastLineX = lx;
            lastLineY = ly;
            updateLine(lineStartX, lineStartY, lx, ly);
        }
    }

    /** 收尾连线：建造模式把路径整批放进待确认队列，拆除模式直接拆（对应原版 flushSelectRequests / removeSelection）。 */
    private void flushMobileLine(){
        if(Vars.world == null) return;

        if(buildBlock != null){
            for(int i = 0; i < lineSize; i++){
                LineRequest req = lines[i];
                Tile tile = Vars.world.tile(req.x, req.y);
                if(tile == null || !Build.validPlace(tile, buildBlock, req.rotation, playerTeam())) continue;
                selectRequests.add(new BuildRequest(req.x, req.y, req.rotation, buildBlock));
            }
            Effects.effect(Fx.placeBlock, targetX, targetY, 0f);
        }

        clearLine();
    }

    /** 收尾蓝图框选：把矩形范围存成蓝图并进入粘贴模式（对应原版 touchUp 的 schematicSelect 分支）。 */
    private void finishSchematicSelect(){
        int x1 = Math.min(lineStartX, lastLineX), x2 = Math.max(lineStartX, lastLineX);
        int y1 = Math.min(lineStartY, lastLineY), y2 = Math.max(lineStartY, lastLineY);

        schematicSelect = false;
        schematicMode = false;

        if(Vars.schematics == null) return;

        com.phoenix.game.game.Schematic schem = Vars.schematics.create(x1, y1, x2, y2);
        if(schem != null && !schem.tiles.isEmpty()){
            selectRequests.clear();
            pasteSchematic = schem;
        }
    }

    /** 拆一格（走和桌面端同一条路：联机发请求 / 单机入玩家拆除队列）。 */
    private void breakTile(int x, int y){
        if(Vars.world == null) return;

        if(Vars.netClient != null && Vars.netClient.isConnected()){
            Vars.netClient.sendDeconstruct(x, y);
            return;
        }
        if(Build.useBuildQueue() && Vars.player != null){
            Vars.player.addBuildRequest(new BuildRequest(x, y));
            return;
        }
        Build.deconstruct(Vars.world.tile(x, y), playerTeam());
    }

    /** 拖到屏幕边缘时继续平移相机（对应原版 MobileInput.autoPan）。 */
    private void autoPan(){
        float screenX = mouseX(), screenY = mouseY();
        float edge = edgePan();

        float panX = 0f, panY = 0f;

        if(screenX <= edge){
            panX = -(edge - screenX);
        }else if(screenX >= Core.graphics.getWidth() - edge){
            panX = screenX - Core.graphics.getWidth() + edge;
        }

        if(screenY <= edge){
            panY = -(edge - screenY);
        }else if(screenY >= Core.graphics.getHeight() - edge){
            panY = screenY - Core.graphics.getHeight() + edge;
        }

        if(panX == 0f && panY == 0f) return;

        vector.set(panX, panY).scl(1f / Math.max(0.0001f, Vars.renderer == null ? 1f : Vars.renderer.cameraScale()));
        if(vector.len() > maxPanSpeed){
            vector.setLength(maxPanSpeed);
        }
        Core.camera.position.x += vector.x;
        Core.camera.position.y += vector.y;
    }

    /** 把按钮的选中态/图标刷成当前状态（libgdx 没有 arc 的 Cell.update 回调）。 */
    private void refreshButtons(){
        if(breakButton != null) breakButton.setChecked(breaking);
        if(diagonalButton != null) diagonalButton.setChecked(Core.settings.getBool("swapdiagonal"));

        if(rotateButton != null){
            boolean arrow = buildBlock != null && buildBlock.rotate;
            rotateButton.getStyle().imageUp = arrow ? Icon.arrowRight : Icon.paste;
            rotateButton.getImage().setOrigin(Align.center);
            //图标基准朝右，rotation 0=上/1=右/2=下/3=左，故 (1 - rotation) * 90（同 InputHandler.spriteAngle）
            rotateButton.getImage().setRotation(arrow ? (1 - (buildRotation & 3)) * 90f : 0f);
            rotateButton.setChecked(!arrow && schematicMode);
        }

        if(confirmButton != null) confirmButton.setVisible(selectRequests.size > 0);
        if(schemPanel != null) schemPanel.setVisible(selectRequests.size > 0 && pasteSchematic != null);
    }

    /** 开始框选蓝图区域（由蓝图菜单触发，对应原版选中 paste 图标后的行为）。 */
    public void beginSchematicSelect(float screenX, float screenY){
        if(Vars.world == null) return;
        int tx = Vars.world.toTile(mouseWorldX(screenX)), ty = Vars.world.toTile(mouseWorldY(screenY));
        schematicSelect = true;
        lineStartX = tx;
        lineStartY = ty;
        lastLineX = tx;
        lastLineY = ty;
    }
}
