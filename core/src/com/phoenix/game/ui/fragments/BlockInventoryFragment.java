package com.phoenix.game.ui.fragments;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.IntSet;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.effect.ItemTransfer;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.func.Boolp;
import com.phoenix.game.func.Prov;
import com.phoenix.game.game.EventType.WithdrawEvent;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Tex;

import static com.phoenix.game.Vars.*;

/**
 * 建筑库存面板（鼠标悬停/点击可存取方块时弹出的物品列表，长按可连续取出）。
 * 参照 Mindustry mindustry.ui.fragments.BlockInventoryFragment 移植。
 * <p>面板贴在方块世界坐标投影到屏幕的位置上，按 3 列排布该方块现有的物品；按住图标超过
 * {@link #holdWithdraw} 帧会持续取出。
 *
 * <p><b>未移植 / 临时绕过（逐条对应原版写法）</b>：
 * <ul>
 *   <li>{@code @Remote requestItem}：本项目 {@code @Remote} 必须保持注释状态（注解处理器会因缺
 *       {@code @WriteClass} 中断编译），且 {@code Call}（RPC 生成器）、
 *       {@code netServer.admins.allowAction}/{@code ActionType} 未移植 → 方法体只保留"从方块取货 +
 *       触发事件 + 播取出特效"，去掉服务端校验；
 *       （注：{@code Units.canInteract} 与 {@code ValidateException} 其实都已存在，
 *       真正缺的只有 {@code Administration.allowAction}/{@code ActionType} 这两个白名单动作钩子）</li>
 *   <li>玩家背包<b>已移植</b>：{@code Player} 有 {@code getItemCapacity()/acceptsItem/maxAccepted/addItem}
 *       （原版这些在 {@code Unit} 上；本项目 Player 不是 Unit → 背包数据放在 {@code Player} 上，
 *       见该类"背包"字段注释）→ 取出数量按容量夹取、取出的物品真正进背包（此前是直接丢弃）；</li>
 *   <li>{@code Block.isAccessible()} 已补（{@code world/Block.java}，等价原版 {@code hasItems && itemCapacity > 0}）；</li>
 *   <li>刻意降级：{@code ItemModule.has(Item)} 单参版本不存在（本项目只有 {@code has(Item, int)}）→ 用 {@code has(item, 1)}；</li>
 *   <li>{@code HandCursorListener}（arc 的"手型光标"监听器）未移植 → 只保留 {@code canPick} 判定；</li>
 *   <li>{@code Element.update(Runnable)} / {@code Cell.update(...)}（每帧回调）libgdx 没有 →
 *       面板级的每帧刷新改用 {@link MenuFragment.Act} 子 Actor，物品数量角标同理；</li>
 *   <li>{@code Table.margin(...)}（外间距）libgdx 没有 → 用 {@code pad}；{@code Table.addImage} 也没有 →
 *       直接 {@code add(new Image(...))}；</li>
 *   <li>{@code Core.input.mouseScreen(x, y)}（世界→屏幕）本项目没有 → 用 {@code Core.camera.project}；</li>
 *   <li>刻意降级：{@code Core.bundle.getOrNull(...)} → 本项目走 {@link I18n#get(String, String)}
 *       （{@code unit.thousands}/{@code unit.millions} 两个键已补，T10）；</li>
 *   <li>{@code net.client()} / {@code net.server()}：原版的 {@code Vars.net} 在本项目实际不会被赋值
 *       （{@code NetServer}/{@code NetClient} 各自持有自己的 {@code Net}）→ 客机判定改用 {@link Vars#isClient()}；</li>
 *   <li>{@code Fill} 不涉及；尺寸统一走 {@code Scl.scl}。</li>
 * </ul>
 */
public class BlockInventoryFragment extends Fragment{
    private final static float holdWithdraw = 20f;

    private Table table = new Table();
    private Tile tile;
    private float holdTime = 0f;
    private boolean holding;
    private Item lastItem;
    /** 上一次构建时面板里出现过的物品下标集合（用于检测"物品集合变了"并重建）。 */
    private final IntSet container = new IntSet();
    /** 世界→屏幕投影用的临时向量（原版 Core.input.mouseScreen 内部自己分配）。 */
    private final Vector3 tmp = new Vector3();

    /**
     * 请求从方块里取出物品（对应原版 {@code @Remote(called = Loc.server, targets = Loc.both, forward = true) requestItem}）。
     * <p>TODO（B 类）原版是网络方法（服务端执行 + 校验后转发）；本项目 {@code @Remote} 未移植、{@code Call} 未生成、
     *     {@code Administration.allowAction}/{@code ActionType} 也没有 → 这里退化为本地直接取货。
     */
    public static void requestItem(Player player, Tile tile, Item item, int amount){
        if(player == null || tile == null || !tile.interactable(player.getTeam())) return;
        //原版 amount = Mathf.clamp(amount, 0, player.getItemCapacity()); int fa = amount;
        //（fa 只给下面的服务端校验回调用，这里不声明以免未使用告警）
        amount = Mathf.clamp(amount, 0, player.getItemCapacity());

        //TODO（B 类）原版服务端校验：
        //  if(net.server() && (!Units.canInteract(player, tile) ||
        //      !netServer.admins.allowAction(player, ActionType.withdrawItem, tile, action -> {
        //          action.item = item;
        //          action.itemAmount = fa;
        //      }))) throw new ValidateException(player, "Player cannot request items.");
        //  —— 真正缺的只有 Administration.allowAction / ActionType（白名单动作钩子）；
        //     Units.canInteract（entities/Units.java）与 ValidateException（net/ValidateException.java）均已存在，
        //     但 Vars.net 在本项目恒为 null（见类注释）→ 该 net.server() 分支不可达，故仍不接线

        int removed = tile.block().removeStack(tile, item, amount);

        //原版 player.addItem(item, removed)：取出的物品进玩家背包
        player.addItem(item, removed);
        Events.fire(new WithdrawEvent());
        for(int j = 0; j < Mathf.clamp(removed / 3, 1, 8); j++){
            //原版直接写 Time.run(j * 3f, () -> ...) 捕获循环变量 j；Java 要求被捕获的局部变量是
            //effectively final，所以这里先把延迟提成 final 局部量（语义完全一致）
            final float delay = j * 3f;
            Time.run(delay, () -> ItemTransfer.transferItemEffect(item, tile.block().centerX(tile), tile.block().centerY(tile), player.unit()));
        }
    }

    @Override
    public void build(Group parent){
        table.setName("inventory");
        table.setTransform(true);
        parent.setTransform(true);
        parent.addActor(table);

        //刻意降级：原版 table.update(() -> {...})（arc 的 Element.update(Runnable) 每帧回调）；
        //     libgdx 的 Actor/Table 都没有该 API → 挂一个 Act 子 Actor 跑同一段逻辑
        parent.addActor(new MenuFragment.Act(this::update));
    }

    public void showFor(Tile t){
        if(this.tile == t){
            hide();
            return;
        }
        this.tile = t;
        //对应原版 tile.block().isAccessible()（Block.isAccessible 已补，见 world/Block.java）
        if(tile == null || tile.entity == null || !tile.block().isAccessible() || tile.entity.items.total() == 0)
            return;
        rebuild(true);
    }

    public void hide(){
        if(table == null) return;

        //刻意降级：原版 table.actions(a, b, c) 是 arc 的"按顺序追加多个 action"；libgdx 要用 Actions.sequence
        table.addAction(Actions.sequence(
            Actions.scaleTo(0f, 1f, 0.06f, Interpolation.pow3Out),
            Actions.run(() -> {
                table.clearChildren();
                table.clearListeners();
                //刻意降级：原版 table.update(null) 注销每帧回调；libgdx 没有 update 回调可清
            }),
            Actions.visible(false)
        ));
        table.setTouchable(Touchable.disabled);
        tile = null;
    }

    /** 每帧刷新（原版写在 {@code table.update(...)} 里，见 {@link #build}）。 */
    private void update(){
        if(state.isMenu() || tile == null || tile.entity == null || !tile.block().isAccessible() || tile.entity.items.total() == 0){
            //刻意降级：原版 hide() 里的 table.update(null) 会把本回调注销，所以只收一次；
            //     本项目的 Act 是常驻的 → 用可见性守卫避免每帧重复追加收起动作
            if(table.isVisible()) hide();
        }else{
            if(holding && lastItem != null){
                holdTime += Time.delta();

                if(holdTime >= holdWithdraw){
                    //原版 Math.min(tile.entity.items.get(lastItem), player.maxAccepted(lastItem))
                    int amount = Math.min(tile.entity.items.get(lastItem), player.maxAccepted(lastItem));
                    requestItem(player, tile, lastItem, amount);
                    holding = false;
                    holdTime = 0f;

                    //刻意降级：原版 net.client()（本项目 Vars.net 实际不会被赋值，客机判定走 Vars.isClient()）
                    if(Vars.isClient()) Events.fire(new WithdrawEvent());
                }
            }

            updateTablePosition();
            if(tile.block().hasItems){
                for(int i = 0; i < content.items().size; i++){
                    //刻意降级：原版 tile.entity.items.has(item)（单参）；本项目 ItemModule 只有 has(Item, int) → has(item, 1)
                    boolean has = tile.entity.items.has(content.item(i), 1);
                    if(has != container.contains(i)){
                        rebuild(false);
                    }
                }
            }
        }
    }

    private void rebuild(boolean actions){
        //刻意偏离：原版 update() 与 rebuild() 里各自 new 了一个局部 IntSet，两处互不可见，
        //所以 update 里的 has != container.contains(i) 恒为真（有物品就每帧重建）。
        //这里把 container 提升为字段，实现代码原本想表达的"物品集合变了才重建"。
        container.clear();

        table.clearChildren();
        table.clearActions();
        table.setBackground(Tex.inventory);
        table.setTouchable(Touchable.enabled);

        int cols = 3;
        int row = 0;

        //刻意降级：原版 table.margin(4f)（外间距）；libgdx 的 Table 没有 margin → 用 pad
        table.pad(Scl.scl(4f));
        table.defaults().size(Scl.scl(8 * 5)).pad(Scl.scl(4f));

        if(tile.block().hasItems){
            for(int i = 0; i < content.items().size; i++){
                Item item = content.item(i);
                //刻意降级：原版 tile.entity.items.has(item)（单参）；本项目 ItemModule 只有 has(Item, int) → has(item, 1)
                if(!tile.entity.items.has(item, 1)) continue;

                container.add(i);

                //原版 () -> player.acceptsItem(item) && !state.isPaused()
                Boolp canPick = () -> player.acceptsItem(item) && !state.isPaused();

                //刻意降级：原版 new HandCursorListener() + l.setEnabled(canPick)（HandCursorListener 未移植）
                //HandCursorListener l = new HandCursorListener();
                //l.setEnabled(canPick);

                Actor image = itemImage(item.icon(Cicon.xlarge), () -> {
                    if(tile == null || tile.entity == null){
                        return "";
                    }
                    return round(tile.entity.items.get(item));
                });
                //刻意降级：image.addListener(l);
                image.addListener(new InputListener(){
                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button){
                        if(!canPick.get() || tile == null || tile.entity == null || tile.entity.items == null
                            || !tile.entity.items.has(item, 1)) return false;
                        //原版 Math.min(1, player.maxAccepted(item))
                        int amount = Math.min(1, player.maxAccepted(item));
                        if(amount > 0){
                            requestItem(player, tile, item, amount);
                            lastItem = item;
                            holding = true;
                            holdTime = 0f;
                            //刻意降级：原版 net.client()（本项目 Vars.net 实际不会被赋值，客机判定走 Vars.isClient()）
                            if(Vars.isClient()) Events.fire(new WithdrawEvent());
                        }
                        return true;
                    }

                    @Override
                    public void touchUp(InputEvent event, float x, float y, int pointer, int button){
                        holding = false;
                        lastItem = null;
                    }
                });
                table.add(image);

                if(row++ % cols == cols - 1) table.row();
            }
        }

        if(row == 0){
            table.setSize(0f, 0f);
        }

        updateTablePosition();

        table.setVisible(true);

        if(actions){
            table.setScale(0f, 1f);
            table.addAction(Actions.scaleTo(1f, 1f, 0.07f, Interpolation.pow3Out));
        }else{
            table.setScale(1f, 1f);
        }
    }

    private String round(float f){
        f = (int)f;
        if(f >= 1000000){
            //刻意降级：原版 Core.bundle.getOrNull(...)（缺键返回 null）→ 本项目走 I18n.get(key, fallback)
            //（unit.millions/unit.thousands 两个键已补，T10 后能取到 "mil"/"k"）
            return (int)(f / 1000000f) + "[gray]" + I18n.get("unit.millions", "") + "[]";
        }else if(f >= 1000){
            return (int)(f / 1000) + I18n.get("unit.thousands", "");
        }else{
            return (int)f + "";
        }
    }

    private void updateTablePosition(){
        //刻意降级：原版 Core.input.mouseScreen(worldX, worldY)（本项目 Core.input 是 libgdx 的 Input，没有 mouseScreen）
        //     → 用 Core.camera.project 做世界→屏幕投影（与 unproject 互逆）
        tmp.set(tile.block().centerX(tile) + tile.block().size * tilesize / 2f,
                tile.block().centerY(tile) + tile.block().size * tilesize / 2f, 0f);
        Core.camera.project(tmp);
        table.pack();
        table.setPosition(tmp.x, tmp.y, Align.topLeft);
    }

    private Actor itemImage(TextureRegion region, Prov<CharSequence> text){
        Stack stack = new Stack();

        Table t = new Table();
        t.left().bottom();
        //刻意降级：原版 t.label(text) 是 arc 的"按 Prov 每帧刷新文本"的 Label（靠 Cell.update 实现）；
        //     libgdx 没有 update → 用 MenuFragment.Act 每帧 setText
        Label count = new Label(text.get(), Styles.defaultLabel);
        t.add(count);
        t.addActor(new MenuFragment.Act(() -> count.setText(text.get())));

        stack.add(new Image(region));
        stack.add(t);
        return stack;
    }
}
