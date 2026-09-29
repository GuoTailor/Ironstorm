package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.Scaling;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Zones;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.ctype.ContentType;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.struct.Array;
import com.phoenix.game.type.Zone;
import com.phoenix.game.ui.ElementGestureListener;
import com.phoenix.game.ui.ItemsDisplay;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.ui.layout.BranchTreeLayout;
import com.phoenix.game.ui.layout.TreeLayout;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * 部署 / 战役区域树（全屏对话框：背景星空 + 区域节点树 + 左下角的已发射物品）。
 * 参照 Mindustry mindustry.ui.dialogs.DeployDialog。
 *
 * <p><b>与本项目的差异（逐条对应原版写法，A=已接线 / B=缺依赖只能降级 / C=等价替代或刻意降级）</b>
 * <ul>
 *   <li><b>A 已接线</b> 区域树父子关系：原版靠 {@code other.requirements.first().zone() == zone}
 *       判断父子关系。T3 起 {@code Objective.zone()} 与 {@code Objectives.ZoneObjective}/{@code ZoneWave}/{@code Launched}
 *       已启用，且 {@code Zone} 是内容类（{@code ContentType.zone}）→ {@link ZoneNode} 的 {@code arr} 用
 *       {@code Vars.content.getBy(ContentType.zone)}（对应原版 {@code content.zones()}）真正建树，
 *       {@code setup()} 里也照原版补上了"间接子节点"（{@code allChildren}）。
 *       <b>不再只画根节点</b>。</li>
 *   <li><b>A 已接线</b> {@code hidden(Zone)}：{@code zone.requirements.contains(o -> o.zone() != null && o.zone().locked())}
 *       —— {@code Objective.zone()} 已存在 → 表达式照抄；但 {@code UnlockableContent.unlocked()} 恒为 true
 *       （{@code Zone.locked()} 恒 false）→ 结果恒为 false（整条解锁持久化链未移植）。</li>
 *   <li><b>B 缺依赖</b> {@code control.saves.getZoneSlot()}（战役区域存档槽的"继续"卡片）：{@code Saves} 没有该方法，
 *       {@code SaveSlot} 也没有 {@code getZone()/previewTexture()} → 该分支整段省略，恒走 {@link View}。</li>
 *   <li><b>B 缺依赖</b> 背景两张贴图 {@code sprites/backgrounds/stars.png} / {@code planet-zero.png}：
 *       本项目 {@code assets/sprites} 下<b>没有</b> backgrounds 目录 → 省略（纯装饰）。
 *       {@code TextureFilter.Linear}、{@code Color.grays(0.3f)}、{@code Mathf.sin} 的漂移动画一并省去。</li>
 *   <li><b>B 缺依赖</b> {@code Events.on(ContentReloadEvent.class, e -> treeLayout())}：本项目
 *       {@code EventType} 没有 {@code ContentReloadEvent}，且 {@code ContentLoader} 没有 reload 路径
 *       → 只保留构造期的 treeLayout()。</li>
 *   <li><b>B 缺依赖</b> {@code platform.updateRPC()}：本项目没有 {@code Vars.platform} 字段
 *       （{@code core/Platform} 是无实例的死接口）→ 省略。</li>
 *   <li><b>C 等价替代</b> {@code Lines.stroke(...)} + {@code Lines.line(...)}（节点连线）：本项目的几何绘制
 *       （{@code Drawf}/ShapeRenderer）固定用<b>世界相机</b>投影，在 Stage 里会整体错位
 *       （见 {@code ui/BorderImage} 的说明）→ 改为用 {@code batch} 画细长的白色贴图，视觉等价。</li>
 *   <li><b>C 刻意降级</b> arc 的 {@code Group.addChild(...)} / {@code Element.update(Runnable)}（每帧回调）/
 *       {@code released(Runnable)} / {@code setDisabled(Prov)} / {@code Cell.update(...)} libgdx 都没有 →
 *       分别换成 {@code addActor} / 覆写 {@code act(float)} / {@code ClickListener.touchUp} /
 *       {@code setDisabled(boolean)} 定一次 / 去掉闪烁动画。</li>
 *   <li><b>C 等价替代</b> {@code Icon.lock}：本项目只有 {@code Icon.locked}（已核对生成的 {@code gen/Icon.java}）。</li>
 *   <li><b>C 等价替代</b> arc 的 {@code Table.margin(...)} / {@code table(Drawable, Cons)} libgdx 没有 →
 *       {@code margin} 用 {@code pad}、带背景的表自建后 {@code setBackground(...)}。</li>
 *   <li><b>C 刻意降级</b> {@code Stage.scrolled} 只发给 {@code scrollFocus}（本项目已知差异）：原版靠"光标下的元素"分发，
 *       这里在 {@link #setup()} 里显式把滚轮焦点交给本对话框，否则缩放完全收不到事件。</li>
 *   <li>{@code Vars.ui.tech.show()} / {@code Vars.ui} 的 {@code showXxx} 本项目的门面都有。</li>
 * </ul>
 */
public class DeployDialog extends FloatingDialog{
    /** 每个区域节点的边长（原版 {@code Scl.scl(230f)}）。 */
    private final float nodeSize = Scl.scl(230f);
    /** 全部区域节点（原版 {@code ObjectSet<ZoneNode>}）。 */
    private ObjectSet<ZoneNode> nodes = new ObjectSet<>();
    /** 点节点后弹出的区域详情。 */
    private ZoneInfoDialog info = new ZoneInfoDialog();
    /** 整棵树的包围盒（原版 arc 的 {@code Rect} → libgdx {@code Rectangle}）。 */
    private Rectangle bounds = new Rectangle();
    /** 可平移/缩放的节点容器。 */
    private View view = new View();

    public DeployDialog(){
        super("", Styles.fullDialog);

        treeLayout();
        //刻意降级（B）：原版 Events.on(ContentReloadEvent.class, e -> treeLayout())——EventType 没有该事件

        addCloseButton();
        buttons.add(imageTextButton("$techtree", Icon.tree, null, () -> {
            if(Vars.ui != null) Vars.ui.tech.show();
        })).size(Scl.scl(230f), Scl.scl(64f));

        shown(this::setup);

        //滚轮缩放（对应原版 view input）
        addListener(new InputListener(){
            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY){
                view.setScale(Mathf.clamp(view.getScaleX() - amountY / 40f, 0.25f, 1f));
                view.setOrigin(Align.center);
                view.setTransform(true);
                return true;
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y){
                //刻意降级（C）：原版 view.requestScroll()（arc 的"请求滚轮焦点"）；libgdx 没有 → 焦点在 setup() 里设定
                return super.mouseMoved(event, x, y);
            }
        });

        //双指缩放 + 拖拽平移（原版用 arc 的 ElementGestureListener，本项目已有同名替代）
        addListener(new ElementGestureListener(){
            @Override
            public void zoom(InputEvent event, float initialDistance, float distance){
                if(view.lastZoom < 0){
                    view.lastZoom = view.getScaleX();
                }

                view.setScale(Mathf.clamp(distance / initialDistance * view.lastZoom, 0.25f, 1f));
                view.setOrigin(Align.center);
                view.setTransform(true);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button){
                view.lastZoom = view.getScaleX();
            }

            @Override
            public void pan(InputEvent event, float x, float y, float deltaX, float deltaY){
                view.panX += deltaX / view.getScaleX();
                view.panY += deltaY / view.getScaleY();
                view.moved = true;
                view.clamp();
            }
        });
    }

    /** 重新布局区域树（原版 {@code treeLayout}）。 */
    void treeLayout(){
        nodes.clear();
        ZoneNode root = new ZoneNode(Zones.groundZero, null);

        BranchTreeLayout layout = new BranchTreeLayout();
        layout.gapBetweenLevels = layout.gapBetweenNodes = Scl.scl(60f);
        layout.gapBetweenNodes = Scl.scl(120f);
        layout.layout(root);
        //原版 bounds.set(layout.getBounds())（arc 的 Rect.set(Rect)）
        bounds.set(layout.getBounds());
        bounds.y += nodeSize * 0.4f;
    }

    public void setup(){
        //刻意降级（B）：原版 platform.updateRPC()（Discord RPC）；本项目没有 Vars.platform 字段

        cont.clear();
        titleTable.remove();
        //原版 margin(0f).marginBottom(8)：libgdx 的 Table 没有 margin（外间距）→ 用 pad
        pad(0f);
        padBottom(Scl.scl(8f));

        Stack stack = new Stack();

        //刻意降级（B）：原版 stack.add(new Image(new Texture("sprites/backgrounds/stars.png"))...) 与
        //     planet-zero.png（带 Mathf.sin/cos 漂移）—— 本项目 assets 里没有 backgrounds 目录 → 省略

        //刻意降级（B）：原版 if(control.saves.getZoneSlot() != null){ ... "继续战役"卡片 ... }else{ stack.add(view = new View()); }
        //  —— Saves 没有 getZoneSlot()、SaveSlot 没有 getZone()/previewTexture() → 恒走下面这条
        stack.add(view = new View());

        stack.add(new ItemsDisplay());

        cont.add(stack).grow();

        //原版把"直接/间接子节点"都算进 allChildren。
        //⚠ libgdx ObjectSet 的 for-each 迭代器是复用的 → 对**同一个** ObjectSet 嵌套 for-each 会拿到错乱元素
        //   （硬约束：libgdx 集合迭代器不能嵌套）→ 先物化成 Array 再用下标循环。
        Array<ZoneNode> nodeArr = new Array<>();
        for(ZoneNode n : nodes){
            nodeArr.add(n);
        }

        for(int i = 0; i < nodeArr.size; i++){
            ZoneNode node = nodeArr.get(i);
            node.allChildren.clear();
            node.allChildren.addAll(node.children);
            //原版：其它节点的任一 requirement 引用了本节点区域 → 视为间接子节点
            for(int j = 0; j < nodeArr.size; j++){
                ZoneNode other = nodeArr.get(j);
                if(other.zone.requirements.contains(req -> req.zone() == node.zone)){
                    node.allChildren.add(other);
                }
            }
        }

        view.setOrigin(Align.center);
        view.setTransform(true);

        //刻意降级（C）：本项目已知差异——Stage.scrolled 只发给 scrollFocus（不做 hit 判定）→ 不显式设置的话
        //     上面的滚轮缩放永远收不到事件
        if(scene != null){
            scene.setScrollFocus(this);
        }
    }

    /**
     * 原版 {@code zone.requirements.contains(o -> o.zone() != null && o.zone().locked())}。
     * <p>{@code Objective.zone()} 已存在（T3 启用 {@code Objectives.ZoneObjective}）→ 表达式可照抄。
     * 但 {@code Zone.locked()} = {@code UnlockableContent.locked()} = {@code !unlocked()}，
     * 而本项目 {@code unlocked()} 恒为 true → 结果恒为 false（整条解锁持久化链未移植）。
     */
    boolean hidden(Zone zone){
        return zone.requirements.contains(o -> o.zone() != null && o.zone().locked());
    }

    /** 建一个区域节点按钮（原版 {@code buildButton}）。 */
    void buildButton(Zone zone, Button button){
        //原版 button.setDisabled(() -> hidden(zone))（动态）；libgdx 只能创建时定一次
        button.setDisabled(hidden(zone));
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                if(!view.moved){
                    info.show(zone);
                }
            }
        });

        if(zone.unlocked() && !hidden(zone)){
            //原版 button.labelWrap(zone.localizedName).style(Styles.outlineLabel).width(140).growX()
            //     .get().setAlignment(Align.center)
            Label label = new Label(zone.localizedName, Styles.outlineLabel);
            label.setWrap(true);
            label.setAlignment(Align.center);
            button.add(label).width(Scl.scl(140f)).growX();
        }else{
            //刻意降级（C）：原版在 zone.canUnlock() && !hidden(zone) 时用 arc 的 Element.update(...)
            //     让图标/文字呼吸闪烁（Mathf.absin）——libgdx 没有每帧回调 → 去掉闪烁。
            //     ⚠ 本分支实际不可达：zone.unlocked() 恒为 true（解锁持久化链未移植）→ 恒走上面那条。
            button.add(new Image(Icon.locked)).size(Scl.scl(32f));
            button.row();
            button.add(new Label(bundle("$locked"), Styles.defaultLabel));
        }
    }

    /** 可平移/缩放的节点容器（原版 {@code class View extends Group}）。 */
    class View extends Group{
        float panX = 0, panY = -200, lastZoom = -1;
        boolean moved = false;

        /** 与 {@link #getChildren()} 同序的节点表（用于每帧重新定位）。 */
        private final Array<ZoneNode> ordered = new Array<>();

        {
            for(ZoneNode node : nodes){
                Stack stack = new Stack();

                float w = node.width, h = node.height;
                if(node.zone.preview != null){
                    //原版 Tmp.v1.set(Scaling.fit.apply(preview.w, preview.h, node.width, node.height))
                    Vector2 fitted = Scaling.fit.apply(node.zone.preview.getRegionWidth(),
                        node.zone.preview.getRegionHeight(), node.width, node.height);
                    w = fitted.x;
                    h = fitted.y;
                }

                stack.setSize(w, h);

                //原版 stack.add(new Table(t -> t.margin(4f).add(new Image(node.zone.preview)
                //     .setScaling(Scaling.stretch)).color(...).grow()))
                Table previewTable = new Table();
                previewTable.pad(Scl.scl(4f));
                Image preview = node.zone.preview != null ? new Image(node.zone.preview) : new Image(Tex.nomap);
                preview.setScaling(Scaling.stretch);
                //原版 .color(node.zone.unlocked() ? Color.darkGray : Color.grays(0.2f))
                //  —— libgdx 的 Color 没有 grays(float) → 手写同值
                preview.setColor(node.zone.unlocked() ? Color.DARK_GRAY : new Color(0.2f, 0.2f, 0.2f, 1f));
                previewTable.add(preview).grow();
                stack.add(previewTable);

                Button button = new Button(Styles.squaret);
                buildButton(node.zone, button);
                stack.add(button);

                addActor(stack);
                ordered.add(node);
            }

            //原版 released(() -> moved = false)
            addListener(new ClickListener(){
                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button){
                    moved = false;
                    super.touchUp(event, x, y, pointer, button);
                }
            });
        }

        /**
         * 每帧定位子节点 + 收拢视野（对应原版 {@code stack.update(() -> setPosition(...))}
         * 与 {@code drawChildren()} 开头的 {@code clamp()}）。
         */
        @Override
        public void act(float delta){
            clamp();

            float offsetX = panX + getWidth() / 2f, offsetY = panY + getHeight() / 2f;
            for(int i = 0; i < ordered.size; i++){
                ZoneNode node = ordered.get(i);
                Actor child = getChildren().get(i);
                child.setPosition(node.x + offsetX, node.y + offsetY, Align.center);
            }

            super.act(delta);
        }

        /** 把整棵树夹在屏幕内（原版 {@code clamp()}）。 */
        void clamp(){
            float pad = nodeSize;

            float ox = getWidth() / 2f, oy = getHeight() / 2f;
            float rx = bounds.x + panX + ox, ry = panY + oy + bounds.y;
            float rw = bounds.width, rh = bounds.height;
            rx = Mathf.clamp(rx, -rw + pad, Core.graphics.getWidth() - pad);
            ry = Mathf.clamp(ry, pad, Core.graphics.getHeight() - rh - pad);
            panX = rx - bounds.x - ox;
            panY = ry - bounds.y - oy;
        }

        /**
         * 画节点连线。原版是 {@code Lines.stroke(...) + Lines.line(...)}；
         * 刻意降级（C）：本项目几何绘制的投影固定为世界相机，在 Stage 里会错位 → 改用 batch 画白色细条。
         */
        @Override
        public void draw(Batch batch, float parentAlpha){
            float offsetX = panX + getWidth() / 2f, offsetY = panY + getHeight() / 2f;
            TextureRegion white = Tex.whiteui.getRegion();
            float thickness = Scl.scl(4f);

            //对应原版 Draw.alpha(parentAlpha)
            batch.setColor(Pal.gray.r, Pal.gray.g, Pal.gray.b, Pal.gray.a * parentAlpha);
            for(ZoneNode node : nodes){
                for(ZoneNode child : node.allChildren){
                    line(batch, white, node.x + offsetX, node.y + offsetY,
                        child.x + offsetX, child.y + offsetY, thickness);
                }
            }
            //对应原版 Draw.reset()
            batch.setColor(Color.WHITE);

            super.draw(batch, parentAlpha);
        }

        /** 用一张白色贴图旋转出线段（替代本项目不可用的 {@code Lines.line}）。 */
        private void line(Batch batch, TextureRegion white, float x1, float y1, float x2, float y2, float thickness){
            float dx = x2 - x1, dy = y2 - y1;
            float len = (float)Math.sqrt(dx * dx + dy * dy);
            if(len <= 0.0001f) return;
            float angle = (float)Math.toDegrees(Math.atan2(dy, dx));
            batch.draw(white, x1, y1 - thickness / 2f, 0f, thickness / 2f, len, thickness, 1f, 1f, angle);
        }
    }

    /** 区域树节点（原版 {@code class ZoneNode extends TreeNode<ZoneNode>}）。 */
    class ZoneNode extends TreeLayout.TreeNode<ZoneNode>{
        final Array<Zone> arr = new Array<>();
        final Array<ZoneNode> allChildren = new Array<>();
        final Zone zone;

        ZoneNode(Zone zone, ZoneNode parent){
            this.zone = zone;
            this.parent = parent;
            this.width = this.height = nodeSize;
            nodes.add(this);

            //原版：arr.selectFrom(content.zones(), other -> other.requirements.size > 0
            //     && other.requirements.first().zone() == zone);
            //  —— T3 起 Objective.zone() 与 Objectives.ZoneObjective/ZoneWave/Launched 已启用，
            //     且 Zone 是内容类（ContentType.zone，Zones 已在 ContentLoader 注册）→
            //     用 Vars.content.getBy(ContentType.zone) 取全部区域（对应原版 content.zones()）。
            //⚠ 不能照抄 arr.selectFrom(...)：本项目 struct.Array.selectFrom 与原版 arc **语义相反**
            //   （arc 是"清空自己、从参数 base 筛进来"，本项目实现是"从自己筛、塞进参数 out"）
            //   且参数类型是 struct.Array（这里 getBy 返回 libgdx Array）→ 直接循环筛选。
            for(Zone other : Vars.content.<Zone>getBy(ContentType.zone)){
                if(other.requirements.size > 0 && other.requirements.first().zone() == zone){
                    arr.add(other);
                }
            }

            children = new ZoneNode[arr.size];
            for(int i = 0; i < children.length; i++){
                children[i] = new ZoneNode(arr.get(i), this);
            }
        }
    }
}
