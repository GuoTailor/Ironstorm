package com.phoenix.game.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Scl;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.input.KeyCode;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Bresenham2;
import com.phoenix.game.ui.GridImage;
import io.anuke.mindustry.gen.Tex;

/**
 * 地图编辑器的画布控件（把地图显示出来 + 处理画笔交互）。参照 Mindustry {@code mindustry.editor.MapView} 移植。
 *
 * <p>它是 Scene2D 的一个 {@code Actor}：{@link MapEditor} 负责数据，{@link MapRenderer} 负责画格子，
 * 本类负责「坐标换算（project/unproject）+ 平移缩放 + 把鼠标事件翻译成 {@link EditorTool} 调用」。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li>{@code Element} → libgdx {@link Actor}。</li>
 *   <li><b>手势层整体缺失</b>：原版实现 arc 的 {@code GestureDetector.GestureListener} 并在构造器里
 *       {@code Core.input.getInputProcessors().insert(0, new GestureDetector(...))}；本项目没有
 *       {@code GestureDetector}/{@code GestureListener}（{@code ClientLauncher} 里的那个是 libgdx 的
 *       触摸手势器，只给移动端游戏输入用）→ <b>不实现该接口</b>（桌面端刻意不接），
 *       {@link #pan}/{@link #zoom}/{@link #pinch}/{@link #pinchStop} 保留同名方法（当前无调用点）。
 *       桌面端缩放改由滚轮（{@link #scrolled}）与 {@link #act} 完成。</li>
 *   <li>{@code Core.scene} 本项目没有全局 Stage → 用 {@link #getStage()}；
 *       {@code requestScroll()}（arc 的"把滚轮焦点抢过来"）→ {@code getStage().setScrollFocus(this)}。</li>
 *   <li>{@code Core.input.axis(Binding.move_x/move_y)} / {@code keyTap/keyRelease(SHIFT_LEFT)} /
 *       {@code axis(SCROLL)} 本项目没有 arc 输入层 → 分别用 {@code Gdx.input.isKeyPressed} 手搓轴、
 *       用「上一帧 Shift 状态」模拟 tap/release、用 {@link #scrolled} 累积滚轮量。</li>
 *   <li><b>鼠标键码</b>：原版 {@code touchDown(..., KeyCode button)} 里的 button 是 arc 的
 *       {@code KeyCode.MOUSE_LEFT/MIDDLE}；libgdx 的 {@code InputListener} 传的是
 *       {@code Input.Buttons.*}（与 arc 的 KeyCode 不是一套编号）→ 必须换成 {@code Input.Buttons.LEFT/MIDDLE}。</li>
 *   <li>{@code Tmp.g1}（arc 的全局复用 Point2）本项目 {@code Tmp} 没有 → 本类自带一个
 *       {@code GridPoint2} 字段 {@link #tmpGrid}（原版也是复用同一个实例，语义一致）。</li>
 *   <li><b>在 Stage 里画几何会错位</b>：{@code Lines}/{@code Fill} 底层的 ShapeRenderer 投影固定为
 *       世界相机（见 转换.md「在 Stage 里画几何会错位」）→ 外框/笔刷预览全部改成用 batch 铺白条贴图
 *       （与 {@code ui/BorderImage}、{@code ui/GridImage}、{@code DeployDialog} 同一招）。
 *       {@code Lines.poly(Vec2[], x, y, scale)} 本项目也没有 → 见 {@link #drawPoly}。</li>
 *   <li>{@code Geometry.pixelCircle(size, ...)} 本项目没有 → {@link #pixelCircle(float)} 用等分圆近似
 *       （视觉上与原版的"像素圆"略有差异，但笔刷范围一致）。</li>
 *   <li>{@code ui.editor.resetSaved()}（"标记画布已改动"）：{@code Vars.ui.editor} 已就位
 *       （{@code UI.java}）→ 已接线（{@link #resetSaved()}，带 null 守卫）。</li>
 *   <li>{@code ScissorStack}：原版是 arc 的（内部自己做投影）；libgdx 的需要先用
 *       {@code calculateScissors(camera, batchTransform, area, scissor)} 把「batch 坐标系里的矩形」
 *       换算成屏幕矩形再 push。</li>
 *   <li>{@code Draw.color(Color.gray)} → {@code Color.GRAY}（libgdx 没有小写颜色常量）。</li>
 * </ul>
 */
public class MapView extends Actor{
    private final MapEditor editor;
    private EditorTool tool = EditorTool.pencil;
    private float offsetx, offsety;
    private float zoom = 1f;
    private boolean grid = false;
    private final GridImage image = new GridImage(0, 0);
    private final Vector2 vec = new Vector2();
    private final Rectangle rect = new Rectangle();
    private final Rectangle scissor = new Rectangle();
    private final Vector2[][] brushPolygons = new Vector2[MapEditor.brushSizes.length][0];

    private boolean drawing;
    private int lastx, lasty;
    private int startx, starty;
    private float mousex, mousey;
    private EditorTool lastTool;

    /** 复用的坐标实例（对应原版 {@code Tmp.g1}）。 */
    private final GridPoint2 tmpGrid = new GridPoint2();
    /** 上一帧 Shift 是否按下（用来模拟原版的 keyTap/keyRelease）。 */
    private boolean shiftDown;
    /** 本帧累积的滚轮量（对应原版 {@code Core.input.axis(KeyCode.SCROLL)}）。 */
    private float scrollAmount;

    public MapView(MapEditor editor){
        this.editor = editor;

        for(int i = 0; i < MapEditor.brushSizes.length; i++){
            float size = MapEditor.brushSizes[i];
            //刻意降级：原版 = Geometry.pixelCircle(size, (index,x,y) -> Mathf.dst(...) <= index - 0.5f)
            //     本项目 Geometry 没有 pixelCircle → 用等分圆近似（笔刷覆盖范围一致）
            brushPolygons[i] = pixelCircle(size);
        }

        //刻意降级：原版在这里插入 arc 的 GestureDetector 处理移动端平移/捏合缩放；本项目没有该类型
        //     （桌面端的滚轮缩放已由 scrolled() + act() 覆盖）
        setTouchable(Touchable.enabled);

        final GridPoint2 firstTouch = new GridPoint2();

        addListener(new InputListener(){

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y){
                mousex = x;
                mousey = y;
                requestScroll();

                return false;
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor){
                requestScroll();
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button){
                if(pointer != 0){
                    return false;
                }

                //原版比的是 arc 的 KeyCode.MOUSE_*；libgdx 传的是 Input.Buttons.*（两套编号不同）
                if(!Vars.mobile && button != Input.Buttons.LEFT && button != Input.Buttons.MIDDLE){
                    return true;
                }

                if(button == Input.Buttons.MIDDLE){
                    lastTool = tool;
                    tool = EditorTool.zoom;
                }

                mousex = x;
                mousey = y;

                GridPoint2 p = project(x, y);
                lastx = p.x;
                lasty = p.y;
                startx = p.x;
                starty = p.y;
                tool.touched(editor, p.x, p.y);
                firstTouch.set(p.x, p.y);

                if(tool.edit){
                    resetSaved();
                }

                drawing = true;
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button){
                if(!Vars.mobile && button != Input.Buttons.LEFT && button != Input.Buttons.MIDDLE){
                    return;
                }

                drawing = false;

                GridPoint2 p = project(x, y);

                if(tool == EditorTool.line){
                    resetSaved();
                    tool.touchedLine(editor, startx, starty, p.x, p.y);
                }

                editor.flushOp();

                if(button == Input.Buttons.MIDDLE && lastTool != null){
                    tool = lastTool;
                    lastTool = null;
                }
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer){
                mousex = x;
                mousey = y;

                GridPoint2 p = project(x, y);

                if(drawing && tool.draggable && !(p.x == lastx && p.y == lasty)){
                    resetSaved();
                    Bresenham2.line(lastx, lasty, p.x, p.y, (cx, cy) -> tool.touched(editor, cx, cy));
                }

                if(tool == EditorTool.line && tool.mode == 1){
                    if(Math.abs(p.x - firstTouch.x) > Math.abs(p.y - firstTouch.y)){
                        lastx = p.x;
                        lasty = firstTouch.y;
                    }else{
                        lastx = firstTouch.x;
                        lasty = p.y;
                    }
                }else{
                    lastx = p.x;
                    lasty = p.y;
                }
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY){
                //对应原版 act() 里的 Core.input.axis(KeyCode.SCROLL)：Stage 只把滚轮发给 scrollFocus
                scrollAmount += amountY;
                return false;
            }
        });
    }

    public EditorTool getTool(){
        return tool;
    }

    public void setTool(EditorTool tool){
        this.tool = tool;
    }

    public boolean isGrid(){
        return grid;
    }

    public void setGrid(boolean grid){
        this.grid = grid;
    }

    @Override
    public void act(float delta){
        super.act(delta);

        //刻意降级：原版 = Core.scene.getKeyboardFocus() + Core.input.axis(Binding.move_x/move_y)；
        //     本项目没有全局 Stage 与 arc 输入 → 焦点走 getStage()，WASD 直接读 Gdx.input
        if(getStage() != null && (getStage().getKeyboardFocus() == null || !(getStage().getKeyboardFocus() instanceof TextField))
            && !Gdx.input.isKeyPressed(KeyCode.CONTROL_LEFT)){
            float ax = axis(KeyCode.A, KeyCode.D);
            float ay = axis(KeyCode.S, KeyCode.W);
            offsetx -= ax * 15f / zoom;
            offsety -= ay * 15f / zoom;
        }

        //Shift 按住临时切到取色工具（原版 = keyTap/keyRelease(KeyCode.SHIFT_LEFT)；libgdx 没有 keyRelease）
        boolean shift = Gdx.input.isKeyPressed(KeyCode.SHIFT_LEFT);
        if(shift && !shiftDown){
            lastTool = tool;
            tool = EditorTool.pick;
        }else if(!shift && shiftDown && lastTool != null){
            tool = lastTool;
            lastTool = null;
        }
        shiftDown = shift;

        if(getStage() == null || getStage().getScrollFocus() != this) return;

        if(Math.abs(scrollAmount) > 0.0001f){
            zoom += scrollAmount / 10f * zoom;
            scrollAmount = 0f;
        }
        clampZoom();
    }

    private void clampZoom(){
        zoom = Mathf.clamp(zoom, 0.2f, 20f);
    }

    /** 对应 arc 的 {@code Core.input.axis(Binding)}：pos 按下返回 1、neg 按下返回 -1。 */
    private static float axis(int neg, int pos){
        return (Gdx.input.isKeyPressed(pos) ? 1f : 0f) - (Gdx.input.isKeyPressed(neg) ? 1f : 0f);
    }

    private GridPoint2 project(float x, float y){
        if(editor.width() <= 0 || editor.height() <= 0) return tmpGrid.set(0, 0);

        float ratio = 1f / ((float)editor.width() / editor.height());
        float size = Math.min(getWidth(), getHeight());
        float sclwidth = size * zoom;
        float sclheight = size * zoom * ratio;
        x = (x - getWidth() / 2 + sclwidth / 2 - offsetx * zoom) / sclwidth * editor.width();
        y = (y - getHeight() / 2 + sclheight / 2 - offsety * zoom) / sclheight * editor.height();

        if(editor.drawBlock.size % 2 == 0 && tool != EditorTool.eraser){
            return tmpGrid.set((int)(x - 0.5f), (int)(y - 0.5f));
        }else{
            return tmpGrid.set((int)x, (int)y);
        }
    }

    private Vector2 unproject(int x, int y){
        if(editor.width() <= 0 || editor.height() <= 0) return vec.set(0, 0);

        float ratio = 1f / ((float)editor.width() / editor.height());
        float size = Math.min(getWidth(), getHeight());
        float sclwidth = size * zoom;
        float sclheight = size * zoom * ratio;
        float px = ((float)x / editor.width()) * sclwidth + offsetx * zoom - sclwidth / 2 + getWidth() / 2;
        float py = ((float)y / editor.height()) * sclheight + offsety * zoom - sclheight / 2 + getHeight() / 2;
        return vec.set(px, py);
    }

    @Override
    public void draw(Batch batch, float parentAlpha){
        if(editor.width() <= 0 || editor.height() <= 0 || getStage() == null) return;

        float ratio = 1f / ((float)editor.width() / editor.height());
        float size = Math.min(getWidth(), getHeight());
        float sclwidth = size * zoom;
        float sclheight = size * zoom * ratio;
        float centerx = getX() + getWidth() / 2 + offsetx * zoom;
        float centery = getY() + getHeight() / 2 + offsety * zoom;

        image.setImageSize(editor.width(), editor.height());

        //裁剪：原版 arc 的 pushScissors 内部自己做投影；libgdx 要先按 batch 变换算出屏幕矩形
        batch.flush();
        ScissorStack.calculateScissors(getStage().getCamera(), batch.getTransformMatrix(),
            rect.set(getX(), getY(), getWidth(), getHeight()), scissor);
        if(!ScissorStack.pushScissors(scissor)){
            return;
        }

        //外框（原版 = Draw.color(Pal.remove) + Lines.stroke(2f) + Lines.rect(...)）
        setColor(batch, Pal.remove, parentAlpha);
        drawRect(batch, centerx - sclwidth / 2 - 1, centery - sclheight / 2 - 1, sclwidth + 2, sclheight + 2, Scl.scl(2f));

        editor.renderer().draw(centerx - sclwidth / 2, centery - sclheight / 2, sclwidth, sclheight);

        if(grid){
            image.setColor(Color.GRAY);
            image.setBounds(centerx - sclwidth / 2, centery - sclheight / 2, sclwidth, sclheight);
            image.draw(batch, parentAlpha);
        }

        int index = 0;
        for(int i = 0; i < MapEditor.brushSizes.length; i++){
            if(editor.brushSize == MapEditor.brushSizes[i]){
                index = i;
                break;
            }
        }

        float scaling = zoom * Math.min(getWidth(), getHeight()) / editor.width();

        setColor(batch, Pal.accent, parentAlpha);
        float stroke = Scl.scl(2f);

        if((!editor.drawBlock.isMultiblock() || tool == EditorTool.eraser) && tool != EditorTool.fill){
            if(tool == EditorTool.line && drawing){
                //注意 unproject 复用同一个 Vector2（原版同）：先把第一个点存下来再算第二个
                Vector2 v1 = unproject(startx, starty).add(getX(), getY());
                float sx = v1.x, sy = v1.y;
                Vector2 v2 = unproject(lastx, lasty).add(getX(), getY());

                drawPoly(batch, brushPolygons[index], sx, sy, scaling, stroke);
                drawPoly(batch, brushPolygons[index], v2.x, v2.y, scaling, stroke);
            }

            if((tool.edit || (tool == EditorTool.line && !drawing)) && (!Vars.mobile || drawing)){
                GridPoint2 p = project(mousex, mousey);
                Vector2 v = unproject(p.x, p.y).add(getX(), getY());

                //pencil square outline
                if(tool == EditorTool.pencil && tool.mode == 1){
                    drawSquare(batch, v.x + scaling / 2f, v.y + scaling / 2f, scaling * (editor.brushSize + 0.5f), stroke);
                }else{
                    drawPoly(batch, brushPolygons[index], v.x, v.y, scaling, stroke);
                }
            }
        }else{
            if((tool.edit || tool == EditorTool.line) && (!Vars.mobile || drawing)){
                GridPoint2 p = project(mousex, mousey);
                Vector2 v = unproject(p.x, p.y).add(getX(), getY());
                float offset = (editor.drawBlock.size % 2 == 0 ? scaling / 2f : 0f);
                drawSquare(batch, v.x + scaling / 2f + offset, v.y + scaling / 2f + offset,
                    scaling * editor.drawBlock.size / 2f, stroke);
            }
        }

        setColor(batch, Pal.accent, parentAlpha);
        drawRect(batch, getX(), getY(), getWidth(), getHeight(), Scl.scl(3f));
        batch.setColor(Color.WHITE);

        batch.flush();
        ScissorStack.popScissors();
    }

    /** 对应原版 {@code Core.scene.getScrollFocus()} + {@code ui.editor} 可见性判定（本类是否在接管滚轮）。 */
    private boolean active(){
        //注意坐标：libgdx 的 Gdx.input.getY() 以上边缘为原点，Stage 的 hit 用下边缘为原点 → 要翻转
        return getStage() != null
            && getStage().getKeyboardFocus() != null
            && getStage().getScrollFocus() == this
            && isVisible()
            && tool == EditorTool.zoom
            && getStage().hit(Gdx.input.getX(), getStage().getHeight() - Gdx.input.getY(), true) == this;
    }

    //刻意降级：以下四个是原版 GestureListener 的回调（移动端平移/捏合缩放）。本项目没有 GestureDetector
    //     → 保留同名方法以便后续接手势层，当前无调用点。

    public boolean pan(float x, float y, float deltaX, float deltaY){
        if(!active()) return false;
        offsetx += deltaX / zoom;
        offsety += deltaY / zoom;
        return false;
    }

    public boolean zoom(float initialDistance, float distance){
        if(!active()) return false;
        float nzoom = distance - initialDistance;
        zoom += nzoom / 10000f / Scl.scl(1f) * zoom;
        clampZoom();
        return false;
    }

    public boolean pinch(Vector2 initialPointer1, Vector2 initialPointer2, Vector2 pointer1, Vector2 pointer2){
        return false;
    }

    public void pinchStop(){

    }

    private void requestScroll(){
        if(getStage() != null) getStage().setScrollFocus(this);
    }

    /**
     * 对应原版 {@code ui.editor.resetSaved()}：画布被改动 → 标记地图"未保存"。
     * <p>{@code Vars.ui} 在其构造期可能还是 null（MapEditorDialog 由 {@code UI.init()} 建），故判空。
     */
    private static void resetSaved(){
        if(Vars.ui != null && Vars.ui.editor != null){
            Vars.ui.editor.resetSaved();
        }
    }

    /**
     * 等分圆近似（对应原版 {@code Geometry.pixelCircle}）。返回以圆心为原点的多边形顶点。
     * <p>刻意降级：原版返回的是"像素圆"（按整数像素偏移取点），本项目 Geometry 没有该方法
     * → 用等分圆近似，笔刷覆盖范围一致、视觉边缘略有差异。
     */
    private static Vector2[] pixelCircle(float size){
        int segments = Math.max(8, (int)(size * 8));
        Vector2[] out = new Vector2[segments];
        for(int i = 0; i < segments; i++){
            float angle = i * 360f / segments;
            //注意：本项目 Mathf.sin/cos 是**弧度**（arc 是角度）→ 这里用 libgdx 的 cosDeg/sinDeg
            out[i] = new Vector2(MathUtils.cosDeg(angle) * size, MathUtils.sinDeg(angle) * size);
        }
        return out;
    }

    // ---- 用 batch 铺白条贴图实现几何图元（Stage 里用 ShapeRenderer 会错位，见类 javadoc）----

    private static void setColor(Batch batch, Color color, float parentAlpha){
        batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
    }

    private static void drawLine(Batch batch, float x1, float y1, float x2, float y2, float thickness){
        float len = Vector2.dst(x1, y1, x2, y2);
        if(len <= 0.0001f) return;

        TextureRegion white = Tex.whiteui.getRegion();
        float angle = MathUtils.atan2(y2 - y1, x2 - x1) * MathUtils.radiansToDegrees;
        batch.draw(white, x1, y1 - thickness / 2f, 0f, thickness / 2f, len, thickness, 1f, 1f, angle);
    }

    private static void drawRect(Batch batch, float x, float y, float w, float h, float thickness){
        drawLine(batch, x, y, x + w, y, thickness);
        drawLine(batch, x + w, y, x + w, y + h, thickness);
        drawLine(batch, x + w, y + h, x, y + h, thickness);
        drawLine(batch, x, y + h, x, y, thickness);
    }

    private static void drawSquare(Batch batch, float x, float y, float radius, float thickness){
        drawRect(batch, x - radius, y - radius, radius * 2f, radius * 2f, thickness);
    }

    /** 对应 arc 的 {@code Lines.poly(Vec2[] vertices, float x, float y, float scale)}（本项目 Lines 没有该重载）。 */
    private static void drawPoly(Batch batch, Vector2[] points, float x, float y, float scale, float thickness){
        if(points.length == 0) return;
        for(int i = 0; i < points.length; i++){
            Vector2 a = points[i], b = points[(i + 1) % points.length];
            drawLine(batch, x + a.x * scale, y + a.y * scale, x + b.x * scale, y + b.y * scale, thickness);
        }
    }
}
