package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ObjectSet;
import com.phoenix.game.Vars;
import com.phoenix.game.content.TechTree;
import com.phoenix.game.content.TechTree.TechNode;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Lines;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Strings;
import com.phoenix.game.game.EventType.ResearchEvent;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.struct.Array;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.ui.ElementGestureListener;
import com.phoenix.game.ui.ItemsDisplay;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.ui.layout.BranchTreeLayout;
import com.phoenix.game.ui.layout.TreeLayout;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Sounds;
import io.anuke.mindustry.gen.Tex;

import java.util.Arrays;

/**
 * 科技树对话框（把 {@link TechTree} 的节点铺成两棵"上下对称"的树，可缩放/拖拽/解锁）。
 * 参照 Mindustry mindustry.ui.dialogs.TechTreeDialog。
 *
 * <p><b>前置条件</b>：{@code TechTree.root} 必须已经由 {@code Vars.content.load()} 建好
 * （构造器会立刻把整棵树递归展开成 {@link TechTreeNode}）。所以 {@code Vars.ui = new UI();}
 * 必须放在内容加载之后 —— 与 {@code Vars.hud} 的时机要求一致。
 *
 * <p><b>与本项目的差异（逐条对应原版写法，A=已接线 / B=缺依赖只能降级 / C=等价替代或刻意降级）</b>
 * <ul>
 *   <li><b>A 已接线</b> {@code hidden(ui.deploy::setup)}：{@code DeployDialog} 与 {@code Vars.ui.deploy}
 *       均已存在 → 已接上（因本类先于 deploy 构造，用 lambda 延迟取并判空）。</li>
 *   <li><b>A 已接线</b> {@code Events.fire(new ResearchEvent(node.block))}：{@code EventType.ResearchEvent}
 *       已按原版追加，{@code unlock()} 里照原版位置 fire。⚠ 本项目当前<b>没有监听者</b>，
 *       属"提供 API / 保真原版形状"，不是接通现成链路（见 {@code EventType.ResearchEvent} 注释）。</li>
 *   <li><b>A 已接线</b> {@code data.unlockContent(node.block)}：{@code GlobalData} 的解锁系统已在 S4a 批落地
 *       （{@code isUnlocked}/{@code unlockContent} + Preferences 持久化）→ 照原版位置（{@code removeItems} 之前）调用。</li>
 *   <li><b>B 缺依赖</b> {@code Events.on(ContentReloadEvent.class, ...)}：{@code EventType} 里没有
 *       {@code ContentReloadEvent}，且 {@code ContentLoader} 没有 reload 路径（加了也永不 fire）→
 *       整段去掉。热重载后树不会自动重建。</li>
 *   <li><b>C 等价替代</b> 图标：本项目图集没有 {@code Icon.book}/{@code Icon.lock}/{@code Icon.ok}
 *       （已核对生成的 {@code gen/Icon.java}）→ 分别用 {@code Icon.database}/{@code Icon.locked}/{@code Icon.check}。</li>
 *   <li><b>C 刻意降级</b> arc 的 {@code Table.margin(...)} / {@code stack(...)} / {@code addImageButton(...)} /
 *       {@code Cell.update(...)} / {@code Cell.disabled(...)} / {@code Element.visible(Boolp)} /
 *       {@code Element.clicked/hovered/exited/released/tapped} / {@code view.requestScroll()} libgdx 都没有 →
 *       {@code margin} 用 {@code pad}；{@code stack} 用 libgdx {@code Stack}；
 *       每帧回调（按钮定位/颜色、infoTable 跟随、toast 定位）统一改成覆写 {@code act(float)}；
 *       {@code disabled(Boolp)} 改成创建时 {@code setDisabled(boolean)}；
 *       {@code visible(Boolp)} 改成在 act() 里 {@code setVisible}；
 *       {@code clicked/hovered/exited} 用 {@code ClickListener}（{@code released} 用 {@code InputListener.touchUp}）。</li>
 *   <li><b>C 等价替代</b> {@code Mathf.equal(a, b, margin)} / {@code Mathf.dstm(x, y, x2, y2)}：
 *       本项目 {@code Mathf} 没有这两个方法 → 分别写成 {@code Math.abs(a - b) <= margin} 与 {@code Mathf.dst(...)}。</li>
 *   <li><b>C 等价替代</b> {@code Draw.reset()} 不存在 → 手动 {@code Draw.color(WHITE)} + {@code Draw.alpha(1f)}；
 *       {@code Lines.stroke(width, color)} 本项目只有单参重载 → 先 {@code Lines.stroke(w)} 再 {@code Draw.color(...)}
 *       （见 {@code core/Lines} 的说明）。</li>
 *   <li><b>C 刻意降级</b> 原版 {@code infoTable.update(() -> ...)}（每帧贴到悬停按钮右上角）与
 *       {@code infoTable.exited(...)}：前者改成覆写 {@code act()}，后者<b>只在初始化时挂一次</b>
 *       （原版写在 {@code rebuild()} 里，每次重建都会再挂一个监听器，是泄漏）。
 *       另外原版判"鼠标是否在 infoTable / 按钮上"用的是 arc 的 {@code Element.hasMouse()}，
 *       libgdx 的 {@code Actor} 没有该能力 → 用 {@link #hasMouse(Actor)}
 *       （{@code Stage.screenToStageCoordinates} + {@code Actor.hit}）现场判定。</li>
 *   <li><b>C 刻意降级</b> libgdx 的 {@code Actor.x/y/width/height} 是<b>包内可见</b>（不是 public），
 *       在跨包的子类里读不到 → 一律改用 {@code getWidth()/getHeight()/getX()/getY()}
 *       （原版直接读字段）。注意 {@code TreeLayout.TreeNode} 的 {@code x/y/width/height} 是本项目的
 *       public 字段，那里可以照抄原版。</li>
 *   <li><b>C 刻意降级</b> 原版 {@code desc...label(() -> ...).update(l -> ...)}（动态数量文本 + 每帧改色）
 *       libgdx 没有 → 改成 {@code rebuild()} 时算一次静态文本与颜色。</li>
 *   <li><b>C 刻意降级</b> 原版移动端把 infoTable 推出屏幕时用 arc 的 {@code RelativeTemporalAction}
 *       平滑平移 → libgdx 没有该类，退化成一次性平移。</li>
 *   <li><b>刻意改进（与原版不同，已确认无害）</b>：节点按钮用<b>每个按钮一份</b>的
 *       {@code ImageButtonStyle} 副本，而不是共享 {@code Styles.nodei}。原版直接改
 *       {@code getStyle().up}，改的是全局共享样式对象，所有节点按钮会互相覆盖背景（每帧只有最后一个生效）。</li>
 *   <li><b>刻意改进</b>：libgdx 的 {@code ImageButton} <b>没有</b> {@code (Drawable, ImageButtonStyle)}
 *       构造器（只有 {@code (ImageButtonStyle)} / {@code (Drawable)} / {@code (Drawable, Drawable)} …）→
 *       先 {@code new ImageButton(style)} 再 {@code getImage().setDrawable(drawable)}；
 *       并且<b>必须</b>给 style 的 {@code imageUp} 赋值（见 {@code ImageButton.updateImage()}：按钮
 *       hover/按下/禁用状态一变就把内部 Image 的 drawable 重置成 {@code style.imageUp}，为 null 图标会整块消失）。
 *       每帧改的是我们自己持有的那个 {@code TextureRegionDrawable}（{@code setRegion}，不产生每帧分配），
 *       而不是原版的 {@code (TextureRegionDrawable)getStyle().imageUp}（在 libgdx 里 {@code style.imageUp} 可能是 null）。</li>
 * </ul>
 */
public class TechTreeDialog extends FloatingDialog{
    private final float nodeSize = Scl.scl(60f);
    private final ObjectSet<TechTreeNode> nodes = new ObjectSet<>();
    private final TechTreeNode root = new TechTreeNode(TechTree.root, null);
    private Rectangle bounds = new Rectangle();
    private ItemsDisplay items;
    private View view;

    public TechTreeDialog(){
        super("");

        //原版：titleTable.remove()（科技树没有标题，整条标题栏连分隔线一起去掉）
        titleTable.remove();
        //原版 margin(0f).marginBottom(8)：libgdx 的 Table 没有 margin → 用 pad
        cont.pad(0f);
        cont.padBottom(Scl.scl(8f));

        Stack stack = new Stack(view = new View(), items = new ItemsDisplay());
        cont.add(stack).grow();

        //刻意降级（B）：原版：Events.on(ContentReloadEvent.class, e -> { nodes.clear(); root = new TechTreeNode(TechTree.root, null);
        //     checkNodes(root); treeLayout(); stack.getChildren().get(0).remove(); stack.addChildAt(0, view = new View()); })
        //  —— EventType 没有 ContentReloadEvent，且 ContentLoader 没有 reload 路径（加了也永不 fire）→ 整段去掉

        shown(() -> {
            checkNodes(root);
            treeLayout();
        });

        //原版：hidden(ui.deploy::setup)（关闭科技树后刷新部署对话框的"已发射物品"列表）。
        //⚠ 本对话框在 UI.init() 里**先于** Vars.ui.deploy 构造，且构造期 Vars.ui 尚未赋值
        //   （Vars.ui = new UI() 整个构造期间 Vars.ui 还是 null）→ 不能用方法引用（会立刻 NPE），
        //   必须用 lambda 延迟到 hide 时取，并判空。
        hidden(() -> {
            if(Vars.ui != null && Vars.ui.deploy != null){
                Vars.ui.deploy.setup();
            }
        });

        addCloseButton();

        //原版：buttons.addImageTextButton("$database", Icon.book, () -> { hide(); ui.database.show(); }).size(210f, 64f)
        buttons.add(imageTextButton("$database", Icon.database, null, () -> {
            hide();
            Vars.ui.database.show();
        })).size(Scl.scl(210f), Scl.scl(64f));

        //缩放 / 拖拽输入
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
                //刻意降级（C）：原版：view.requestScroll()（arc 的 Element 方法）libgdx 没有对应物 → 去掉
                return super.mouseMoved(event, x, y);
            }

            //原版：released(() -> moved = false)（arc 的 Element.released）
            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button){
                view.moved = false;
            }
        });

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

            //原版参数是 arc 的 KeyCode，本项目 KeyCode 是 int 常量 → 写成 int
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

    /** 把树算成上下两半分别布局（对应原版 {@code treeLayout()}）。 */
    void treeLayout(){
        float spacing = 20f;
        LayoutNode node = new LayoutNode(root, null);
        LayoutNode[] children = node.children;
        //等价替代（C）：原版 Mathf.ceil 返回 int；本项目 Mathf.ceil 返回 float → 要强转
        int half = (int)Mathf.ceil(node.children.length / 2f);
        LayoutNode[] leftHalf = Arrays.copyOfRange(node.children, 0, half);
        LayoutNode[] rightHalf = Arrays.copyOfRange(node.children, half, node.children.length);
        node.children = leftHalf;

        BranchTreeLayout upper = new BranchTreeLayout();
        upper.gapBetweenLevels = spacing;
        upper.gapBetweenNodes = spacing;
        upper.rootLocation = BranchTreeLayout.TreeLocation.top;
        upper.layout(node);

        node.children = rightHalf;

        BranchTreeLayout lower = new BranchTreeLayout();
        lower.gapBetweenLevels = spacing;
        lower.gapBetweenNodes = spacing;
        lower.rootLocation = BranchTreeLayout.TreeLocation.bottom;
        lower.layout(node);

        node.children = children;

        float minx = 0f, miny = 0f, maxx = 0f, maxy = 0f;
        copyInfo(node);

        for(TechTreeNode n : nodes){
            if(!n.visible) continue;
            minx = Math.min(n.x - n.width / 2f, minx);
            maxx = Math.max(n.x + n.width / 2f, maxx);
            miny = Math.min(n.y - n.height / 2f, miny);
            maxy = Math.max(n.y + n.height / 2f, maxy);
        }
        bounds = new Rectangle(minx, miny, maxx - minx, maxy - miny);
        bounds.y += nodeSize * 1.5f;
    }

    void copyInfo(LayoutNode node){
        node.node.x = node.x;
        node.node.y = node.y;
        if(node.children != null){
            for(LayoutNode child : node.children){
                copyInfo(child);
            }
        }
    }

    void checkNodes(TechTreeNode node){
        boolean locked = locked(node.node);
        if(!locked) node.visible = true;
        for(TechTreeNode l : node.children){
            l.visible = !locked;
            checkNodes(l);
        }

        items.rebuild();
    }

    void showToast(String info){
        if(FloatingDialog.scene == null) return;

        //刻意降级（C）：原版用 table.update(() -> { table.toFront(); table.setPosition(w/2, h-21, Align.top); })
        //     （arc 的每帧回调）→ 改成覆写 act()
        Table table = new Table(){
            @Override
            public void act(float delta){
                super.act(delta);
                if(getStage() != null) toFront();
                setPosition(Core.graphics.getWidth() / 2f, Core.graphics.getHeight() - Scl.scl(21f), Align.top);
            }
        };
        table.addAction(Actions.sequence(Actions.fadeOut(0.5f, Interpolation.fade), Actions.removeActor()));
        table.top();
        table.add(new Label(info, Styles.outlineLabel));
        table.setName("toast");
        table.pack();
        FloatingDialog.scene.addActor(table);
    }

    boolean locked(TechNode node){
        return node.block.locked();
    }

    /** 布局用的节点（只关心坐标，不参与绘制）。 */
    class LayoutNode extends TreeLayout.TreeNode<LayoutNode>{
        final TechTreeNode node;

        LayoutNode(TechTreeNode node, LayoutNode parent){
            this.node = node;
            this.parent = parent;
            this.width = this.height = nodeSize;
            if(node.children != null){
                children = Array.with(node.children).map(t -> new LayoutNode(t, this)).toArray(LayoutNode.class);
            }
        }
    }

    /** 树节点（带"是否可见"状态）。 */
    class TechTreeNode extends TreeLayout.TreeNode<TechTreeNode>{
        final TechNode node;
        boolean visible = true;

        TechTreeNode(TechNode node, TechTreeNode parent){
            this.node = node;
            this.parent = parent;
            this.width = this.height = nodeSize;
            nodes.add(this);
            if(node.children != null){
                children = new TechTreeNode[node.children.size];
                for(int i = 0; i < children.length; i++){
                    children[i] = new TechTreeNode(node.children.get(i), this);
                }
            }
        }
    }

    /** 可缩放/拖拽的节点视图。 */
    class View extends Group{
        float panX = 0, panY = -200, lastZoom = -1;
        boolean moved = false;
        ImageButton hoverNode;

        /** 悬停节点的信息面板；每帧贴到悬停按钮的右上角（对应原版 infoTable.update(...)）。 */
        Table infoTable = new Table(){
            @Override
            public void act(float delta){
                super.act(delta);
                if(hoverNode != null && getStage() != null){
                    setPosition(hoverNode.getX() + hoverNode.getWidth(),
                                hoverNode.getY() + hoverNode.getHeight(), Align.topLeft);
                }
            }
        };

        {
            infoTable.setTouchable(Touchable.enabled);
            //刻意改进（C）：原版把 exited(...) 写在 rebuild() 里（每重建一次就多挂一个监听器）→ 这里只挂一次
            infoTable.addListener(new ClickListener(){
                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Actor toActor){
                    if(hoverNode != null && !hasMouse(infoTable) && !hasMouse(hoverNode)){
                        hoverNode = null;
                        rebuild();
                    }
                }
            });

            for(TechTreeNode node : nodes){
                //libgdx 的 ImageButton 没有 (TextureRegion, style) 构造器 → 先建按钮再把图塞进内部 Image。
                //另外要给每个按钮一份**独立的** ImageButtonStyle 副本：Styles.nodei 是全局共享的，
                //原版直接改 getStyle().up 会让所有节点按钮互相覆盖背景（每帧只有最后一个生效）
                ImageButton.ImageButtonStyle nodeStyle = new ImageButton.ImageButtonStyle(Styles.nodei);
                final TextureRegionDrawable nodeImage = new TextureRegionDrawable(node.node.block.icon(Cicon.medium));
                //⚠ 必须设 style.imageUp：libgdx 的 ImageButton.updateImage() 在按钮状态变化（hover/按下/禁用）时
                //   会把内部 Image 的 drawable 重置成 style.imageUp —— 若为 null 图标会整块消失；
                //   而 ImageButton(style) 构造器本身不会把 style.imageUp 应用到内部 Image（要手动设一次）
                nodeStyle.imageUp = nodeImage;
                ImageButton button = new ImageButton(nodeStyle){
                    @Override
                    public void act(float delta){
                        super.act(delta);

                        //⚠ 原版这里用的 width/height 是外层 View 的宽高（与 drawChildren 的连线原点一致）；
                        //  在匿名 ImageButton 子类里必须显式写 View.this.getWidth()，否则会拿到按钮自身的尺寸、连线错位
                        float offset = (Core.graphics.getHeight() % 2) / 2f;
                        setPosition(node.x + View.this.panX + View.this.getWidth() / 2f,
                                    node.y + View.this.panY + View.this.getHeight() / 2f + offset, Align.center);

                        getStyle().up = !locked(node.node) ? Tex.buttonOver
                            : !Vars.data.hasItems(node.node.requirements) ? Tex.buttonRed : Tex.button;
                        //原版改的是 (TextureRegionDrawable)getStyle().imageUp；libgdx 的 ImageButton 把图放在内部
                        //Image actor 里（style.imageUp 恒为 null）→ 直接改我们持有的那个 drawable
                        nodeImage.setRegion(node.visible ? node.node.block.icon(Cicon.medium) : Icon.locked.getRegion());
                        getImage().setColor(!locked(node.node) ? Color.WHITE : Color.GRAY);

                        //刻意降级（C）：原版 button.visible(() -> node.visible) / button.touchable(() -> ...)（arc 的动态属性）
                        //     libgdx 没有 → 在每帧回调里同步
                        setVisible(node.visible);
                        setTouchable(node.visible ? Touchable.enabled : Touchable.disabled);
                    }
                };
                button.getImage().setDrawable(nodeImage);
                button.addListener(new ClickListener(){
                    @Override
                    public void clicked(InputEvent event, float x, float y){
                        if(moved) return;

                        if(Vars.mobile){
                            hoverNode = button;
                            rebuild();
                            float right = infoTable.getRight();
                            if(right > Core.graphics.getWidth()){
                                float moveBy = right - Core.graphics.getWidth();
                                //原版用 arc 的 RelativeTemporalAction 平滑平移；libgdx 没有该类
                                //→ 退化成一次性平移（刻意降级 C：需要平滑可换成 Actions.moveBy）
                                View.this.panX -= moveBy;
                            }
                        }else if(Vars.data.hasItems(node.node.requirements) && locked(node.node)){
                            unlock(node.node);
                        }
                    }

                    @Override
                    public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor){
                        if(!Vars.mobile && hoverNode != button && node.visible){
                            hoverNode = button;
                            rebuild();
                        }
                    }

                    @Override
                    public void exit(InputEvent event, float x, float y, int pointer, Actor toActor){
                        if(!Vars.mobile && hoverNode == button && !hasMouse(infoTable) && !hasMouse(hoverNode)){
                            hoverNode = null;
                            rebuild();
                        }
                    }
                });
                button.setUserObject(node.node);
                button.setSize(nodeSize, nodeSize);
                addActor(button);
            }

            if(Vars.mobile){
                //刻意降级（C）：原版用 tapped(() -> { Element e = Core.scene.hit(...); if(e == this){...} })（arc 的 Element.tapped）
                //     libgdx 的 Group 没有 tapped → 用 ClickListener，用 event.getTarget() 代替 hit 判定
                addListener(new ClickListener(){
                    @Override
                    public void clicked(InputEvent event, float x, float y){
                        if(event.getTarget() == View.this){
                            hoverNode = null;
                            rebuild();
                        }
                    }
                });
            }

            setOrigin(Align.center);
            setTransform(true);
        }

        void clamp(){
            float pad = nodeSize;

            float ox = getWidth() / 2f, oy = getHeight() / 2f;
            float rx = bounds.x + panX + ox, ry = panY + oy + bounds.y;
            float rw = bounds.width, rh = bounds.height;
            rx = Mathf.clamp(rx, -rw + pad, Core.graphics.getWidth() - pad);
            ry = Mathf.clamp(ry, -rh + pad, Core.graphics.getHeight() - pad);
            panX = rx - bounds.x - ox;
            panY = ry - bounds.y - oy;
        }

        void unlock(TechNode node){
            //原版 TechTreeDialog.java:308 data.unlockContent(node.block)（在 removeItems 之前）
            Vars.data.unlockContent(node.block);
            Vars.data.removeItems(node.requirements);
            showToast(format("researched", "{0} researched.", node.block.localizedName));
            checkNodes(root);
            hoverNode = null;
            treeLayout();
            rebuild();
            if(FloatingDialog.scene != null) FloatingDialog.scene.act();
            Sounds.unlock.play();
            //原版：Events.fire(new ResearchEvent(node.block))。
            //⚠ 本项目当前没有 ResearchEvent 监听者 → 这里只是提供 API/保真原版形状（见 EventType.ResearchEvent 注释）
            Events.fire(new ResearchEvent(node.block));
        }

        void rebuild(){
            ImageButton button = hoverNode;

            infoTable.remove();
            infoTable.clear();

            if(button == null) return;

            TechNode node = (TechNode)button.getUserObject();

            infoTable.left();
            infoTable.setBackground(Tex.button);
            //等价替代（C）：原版 infoTable.background(Tex.button).margin(8f)：libgdx 没有 margin → 用 pad
            infoTable.pad(Scl.scl(8f));

            //原版 infoTable.table(b -> {...})：libgdx 没有带回调的 table(...) → 自建内层表
            Table b = new Table();
            //等价替代（C）：原版 b.margin(0).left().defaults().left()：libgdx 没有 margin → 用 pad
            b.pad(0f);
            b.left();
            b.defaults().left();

            //原版 b.addImageButton(Icon.info, Styles.cleari, () -> ui.content.show(node.block)).growY().width(50f)
            //libgdx 的 ImageButton 没有 (Drawable, style) 构造器 → 先建按钮再塞图；
            //并且要给 style 一份副本再设 imageUp（见上面节点按钮处的说明）
            ImageButton.ImageButtonStyle infoStyle = new ImageButton.ImageButtonStyle(Styles.cleari);
            infoStyle.imageUp = Icon.info;
            ImageButton infoButton = new ImageButton(infoStyle);
            infoButton.getImage().setDrawable(Icon.info);
            infoButton.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    //原版：ui.content.show(node.block)。
                    //本项目 ContentInfoDialog 不是 FloatingDialog，需要显式传 Stage（用本 actor 所在的 stage）
                    if(Vars.ui != null && Vars.ui.content != null){
                        Stage stage = getStage();
                        if(stage != null){
                            Vars.ui.content.show(node.block, stage);
                        }
                    }
                }
            });
            b.add(infoButton).growY().width(Scl.scl(50f));
            b.add().grow();

            Table desc = new Table();
            desc.left();
            desc.defaults().left();
            desc.add(new Label(node.block.localizedName, Styles.defaultLabel));
            desc.row();

            if(locked(node)){
                Table t = new Table();
                t.left();
                for(ItemStack req : node.requirements){
                    Table list = new Table();
                    list.left();
                    list.add(new Image(req.item.icon(Cicon.small))).size(Scl.scl(8 * 3)).padRight(Scl.scl(3f));
                    Label reqName = new Label(req.item.localizedName, Styles.defaultLabel);
                    reqName.setColor(Color.LIGHT_GRAY);
                    list.add(reqName);

                    //刻意降级（C）：原版是 list.label(() -> ...).update(l -> l.setColor(...))（动态文本 + 每帧改色）
                    //     libgdx 没有 → rebuild() 时算一次静态文本与颜色
                    Label amount = new Label(" " + Math.min(Vars.data.getItem(req.item), req.amount) + " / " + req.amount,
                        Styles.defaultLabel);
                    amount.setColor(Vars.data.has(req.item, req.amount) ? Color.LIGHT_GRAY : Color.SCARLET);
                    list.add(amount);

                    t.add(list).fillX().left();
                    t.row();
                }
                desc.add(t);
            }else{
                desc.add(new Label(bundle("$completed"), Styles.defaultLabel));
            }
            b.add(desc).pad(Scl.scl(9f));

            if(Vars.mobile && locked(node)){
                b.row();
                TextButton research = imageTextButton("$research", Icon.check, Styles.nodet, () -> unlock(node));
                //刻意降级（C）：原版 .disabled(i -> !data.hasItems(...))（arc 的 Cell 动态禁用）→ 创建时定一次
                research.setDisabled(!Vars.data.hasItems(node.requirements));
                b.add(research).growX().height(Scl.scl(44f)).colspan(3);
            }

            infoTable.add(b);
            infoTable.row();

            if(node.block.description != null){
                //原版 infoTable.table(t -> t.margin(3f).left().labelWrap(node.block.displayDescription()).color(Color.lightGray)...)
                //libgdx 没有 table(Cons) / labelWrap / Cell.color → 自建表 + 自建 Label
                Table t = new Table();
                t.pad(Scl.scl(3f));
                t.left();
                Label description = new Label(node.block.displayDescription(), Styles.defaultLabel);
                description.setWrap(true);
                description.setColor(Color.LIGHT_GRAY);
                t.add(description).growX();
                infoTable.add(t).fillX();
            }

            addActor(infoTable);
            infoTable.pack();
            infoTable.act(Core.graphics.getDeltaTime());
        }

        @Override
        protected void drawChildren(Batch batch, float parentAlpha){
            clamp();
            float offsetX = panX + getWidth() / 2f, offsetY = panY + getHeight() / 2f;

            for(TechTreeNode node : nodes){
                if(!node.visible) continue;
                for(TechTreeNode child : node.children){
                    if(!child.visible) continue;

                    //原版 Lines.stroke(Scl.scl(4f), color)：本项目 Lines.stroke 只有单参重载 → 颜色走 Draw.color
                    Lines.stroke(Scl.scl(4f));
                    Draw.color(locked(node.node) || locked(child.node) ? Pal.gray : Pal.accent);
                    Draw.alpha(parentAlpha);

                    //等价替代（C）：原版 Mathf.equal(a, b, 1f) / Mathf.dstm(...)：本项目 Mathf 没有 → 就地实现
                    if(Math.abs(Math.abs(node.y - child.y) - Math.abs(node.x - child.x)) <= 1f
                        && Mathf.dst(node.x, node.y, child.x, child.y) <= node.width * 3){
                        Lines.line(node.x + offsetX, node.y + offsetY, child.x + offsetX, child.y + offsetY);
                    }else{
                        Lines.line(node.x + offsetX, node.y + offsetY, child.x + offsetX, node.y + offsetY);
                        Lines.line(child.x + offsetX, node.y + offsetY, child.x + offsetX, child.y + offsetY);
                    }
                }
            }

            //原版 Draw.reset()：本项目 Draw 没有该方法 → 手动还原
            Draw.color(Color.WHITE);
            Draw.alpha(1f);
            super.drawChildren(batch, parentAlpha);
        }
    }

    /** @return 取 bundle 文本并用 {@code Strings.format} 填占位符（原版 {@code Core.bundle.format}）。 */
    private static String format(String key, String fallback, Object... args){
        return Strings.format(I18n.get(key, fallback), args);
    }

    /**
     * 现场判断鼠标是否悬停在某个 actor 上（对应 arc 的 {@code Element.hasMouse()}）。
     * <p>等价替代（C）：arc 的 {@code Element.hasMouse()} libgdx 没有对应物（{@code Actor} 既没有
     * {@code hasMouseOver()} 也没有 {@code isOver()}，只有 {@code Button.isOver()} 且它由 ClickListener 维护、
     * 时机不可靠）→ 这里用 {@code Stage.screenToStageCoordinates} + {@code Actor.hit} 做一次实时判定。
     */
    private static boolean hasMouse(Actor actor){
        Stage stage = actor.getStage();
        if(stage == null || Gdx.input == null) return false;

        Vector2 v = new Vector2(Gdx.input.getX(), Gdx.input.getY());
        stage.screenToStageCoordinates(v);
        return actor.hit(v.x, v.y, true) != null;
    }
}
