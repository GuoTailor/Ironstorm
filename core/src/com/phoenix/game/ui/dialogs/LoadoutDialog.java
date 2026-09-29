package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.input.KeyCode;
import com.phoenix.game.struct.Array;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.type.ItemType;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * 发射清单配置（"这次带多少东西过去"）。参照 Mindustry mindustry.ui.dialogs.LoadoutDialog。
 *
 * <p><b>与本项目的差异（重要）</b>：
 * <ul>
 *   <li><b>{@code ItemStack.amount} 在本项目是 {@code final}</b>（原版可变）→ 不能像原版那样直接改
 *       传入列表里的 {@code stack.amount}。这里引入内部可变副本 {@link Slot}，
 *       编辑时改副本、关闭时（{@code hidden}）由 {@link #flush()} 重建 {@code ItemStack} 写回调用方的列表，
 *       外部契约（"传进来的是活的列表，关掉后能读到新数量"）保持不变。</li>
 *   <li>{@code reseed()} 里筛 {@code i.type == ItemType.material}：<b>已接线</b>——本项目
 *       {@link ItemType} 存在（{@code type/ItemType.java}），且 {@code content/Items.java} 已在给
 *       material 类物品设 {@code item.type = ItemType.material} → 照原版过滤。</li>
 *   <li>{@code t.addImageButton(Icon.pencil, ..., () -> ui.showTextInput(...))}：<b>已接线</b>——
 *       {@code UI.showTextInput(String, String, int, String, boolean, Cons)} 已存在（{@code core/UI.java:353}）
 *       → 铅笔按钮弹真实输入框（原版 {@code Strings.canParsePostiveInt/parseInt} 本项目没有 →
 *       用 {@link #parseIntOr} 等价替代）。</li>
 *   <li>arc 的 {@code Table.margin(...)} / {@code label(Prov)}（动态文本）/ {@code keyDown(...)} libgdx 都没有
 *       → {@code margin} 用 {@code pad}；动态数量 Label 改为在增减时手动 {@code setText}；
 *       ESC/BACK 关闭改用 {@code Dialog.key(int, Object)}。</li>
 *   <li>{@code ItemStack} 未实现 Comparable → 排序改用 {@code sortComparing}（按 {@code item.id}）。</li>
 * </ul>
 */
public class LoadoutDialog extends FloatingDialog{
    private Runnable hider;
    private Runnable resetter;
    private Runnable updater;
    /** 调用方传入的"活"清单（本项目里 {@code ItemStack} 不可变，见类注释）。 */
    private Array<ItemStack> originalStacks = new Array<>();
    /** 内部可变副本。 */
    private Array<Slot> slots = new Array<>();
    private Table items;
    private int capacity;

    /** 可变数量的一格：对应原版可变的 {@code ItemStack.amount}。 */
    private static class Slot{
        final Item item;
        int amount;

        Slot(Item item, int amount){
            this.item = item;
            this.amount = amount;
        }
    }

    public LoadoutDialog(){
        super("$configure");
        setFillParent(true);

        //原版 keyDown(key -> { if(key == ESCAPE || key == BACK) Core.app.post(this::hide); })
        //libgdx 的 Dialog 没有 keyDown → 用 key(int, Object)
        key(KeyCode.ESCAPE, null);
        key(KeyCode.BACK, null);

        //原版 cont.pane(t -> items = t.margin(10f)).left()：libgdx 没有 pane(Cons)
        items = new Table();
        items.pad(Scl.scl(10f));
        cont.add(new ScrollPane(items, Styles.defaultPane)).left().grow();

        shown(this::setup);
        hidden(() -> {
            //原版：originalStacks.selectFrom(stacks, s -> s.amount > 0)
            //本项目 ItemStack 不可变 → 由 flush() 重建整个列表（只保留 amount > 0 的）
            flush();
            updater.run();
            if(hider != null){
                hider.run();
            }
        });

        buttons.add(imageTextButton("$back", Icon.arrowLeft, null, this::hide)).size(Scl.scl(210f), Scl.scl(64f));

        buttons.add(imageTextButton("$settings.reset", Icon.refresh, null, () -> {
            resetter.run();
            reseed();
            updater.run();
            setup();
        })).size(Scl.scl(210f), Scl.scl(64f));
    }

    public void show(int capacity, Array<ItemStack> stacks, Runnable reseter, Runnable updater, Runnable hider){
        this.originalStacks = stacks;
        reseed();
        this.resetter = reseter;
        this.updater = updater;
        this.capacity = capacity;
        this.hider = hider;
        //this.filter = filter;
        show();
    }

    void setup(){
        items.clearChildren();
        items.left();
        float bsize = Scl.scl(40f);

        int i = 0;

        for(Slot slot : slots){
            //原版 items.table(Tex.pane, t -> {...})：libgdx 没有"带背景 + 回调"的重载 → 自建
            Table t = new Table();
            t.setBackground(Tex.pane);
            //原版 t.margin(4).marginRight(8)（外间距）→ 本项目 UI 自建替代层没有 margin，用 pad 代替（刻意降级）
            t.pad(Scl.scl(4f)).padRight(Scl.scl(8f));
            t.left();

            //原版 t.label(() -> stack.amount + "")（arc 的动态文本）libgdx 没有 → 手动刷新
            Label amountLabel = new Label(String.valueOf(slot.amount), Styles.defaultLabel);
            amountLabel.setColor(Color.LIGHT_GRAY);

            t.add(textButton("-", Styles.cleart, () -> {
                slot.amount = Math.max(slot.amount - step(slot.amount), 0);
                amountLabel.setText(String.valueOf(slot.amount));
                updater.run();
            })).size(bsize);

            t.add(textButton("+", Styles.cleart, () -> {
                slot.amount = Math.min(slot.amount + step(slot.amount), capacity);
                amountLabel.setText(String.valueOf(slot.amount));
                updater.run();
            })).size(bsize);

            //原版 t.addImageButton(Icon.pencil, Styles.cleari, () -> ui.showTextInput("$configure", stack.item.localizedName,
            //    10, stack.amount + "", true, str -> {...}))：UI.showTextInput 的 6 参重载已存在（core/UI.java:353）
            //libgdx 的 ImageButton 没有 (Drawable, ImageButtonStyle) 构造器 → 手工设 imageUp
            ImageButton.ImageButtonStyle pencilStyle = new ImageButton.ImageButtonStyle(Styles.cleari);
            pencilStyle.imageUp = Icon.pencil;
            ImageButton pencil = new ImageButton(pencilStyle);
            pencil.getImage().setDrawable(Icon.pencil);
            pencil.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    if(Vars.ui == null) return;
                    Vars.ui.showTextInput("$configure", slot.item.localizedName, 10, String.valueOf(slot.amount), true, str -> {
                        //原版 Strings.canParsePostiveInt/parseInt → 本项目 core/Strings 没有 → 就地解析
                        int amount = parseIntOr(str, -1);
                        if(amount >= 0 && amount <= capacity){
                            slot.amount = amount;
                            amountLabel.setText(String.valueOf(slot.amount));
                            updater.run();
                        }else{
                            Vars.ui.showInfo(Core.bundle.format("configure.invalid", capacity));
                        }
                    });
                }
            });
            t.add(pencil).size(bsize);

            if(slot.item.icon(Cicon.small) != null){
                t.add(new Image(slot.item.icon(Cicon.small))).size(Scl.scl(8 * 3)).padRight(Scl.scl(4f)).padLeft(Scl.scl(4f));
            }
            t.add(amountLabel).left().width(Scl.scl(90f));

            items.add(t).pad(Scl.scl(2f)).left().fillX();

            if(++i % 2 == 0 || (Vars.mobile && isPortrait())){
                items.row();
            }
        }
    }

    private void reseed(){
        slots.clear();

        for(int i = 0; i < originalStacks.size; i++){
            ItemStack stack = originalStacks.get(i);
            slots.add(new Slot(stack.item, stack.amount));
        }

        //⚠ libgdx 的 Array 的 for-each 迭代器不能嵌套 → 外层用下标循环（内层 contains() 也在遍历）
        //⚠ Vars.content.items() 返回的是 libgdx 的 Array（ContentLoader 没改用 struct.Array）→ 这里用父类型声明
        com.badlogic.gdx.utils.Array<Item> allItems = Vars.content.items();
        for(int i = 0; i < allItems.size; i++){
            Item item = allItems.get(i);
            //原版：i.type == ItemType.material && !stacks.contains(stack -> stack.item == i)
            //  —— ItemType 已存在（type/ItemType.java；content/Items.java 在设 material）→ 照原版过滤
            if(item.type == ItemType.material && !contains(item)){
                slots.add(new Slot(item, 0));
            }
        }

        //原版 this.stacks.sort(Structs.comparingInt(s -> s.item.id))（ItemStack 实现了 Comparable）
        slots.sortComparing(s -> (int)s.item.id);
    }

    /** @return 传入的清单里是否已有该物品。 */
    private boolean contains(Item item){
        for(int i = 0; i < originalStacks.size; i++){
            if(originalStacks.get(i).item == item) return true;
        }
        return false;
    }

    /** 把可变副本写回调用方的列表（原版直接改 {@code ItemStack.amount}；本项目 amount 是 final，只能重建）。 */
    private void flush(){
        originalStacks.clear();
        for(Slot slot : slots){
            if(slot.amount > 0){
                originalStacks.add(new ItemStack(slot.item, slot.amount));
            }
        }
    }

    private int step(int amount){
        if(amount < 1000){
            return 100;
        }else if(amount < 2000){
            return 200;
        }else if(amount < 5000){
            return 500;
        }else{
            return 1000;
        }
    }

    /**
     * @return 解析后的非负整数；解析失败或非数字返回 {@code fallback}。
     * <p>等价替代原版 {@code Strings.canParsePostiveInt} + {@code Strings.parseInt}
     * （本项目 {@code core/Strings} 只有 {@code fixed/autoFixed/format/stripColors}）。
     */
    private static int parseIntOr(String text, int fallback){
        if(text == null) return fallback;
        try{
            return Integer.parseInt(text.trim());
        }catch(NumberFormatException e){
            return fallback;
        }
    }

    /** @return 是否竖屏（对应 arc 的 {@code Core.graphics.isPortrait()}，libgdx 的 Graphics 没有该方法）。 */
    private static boolean isPortrait(){
        return Core.graphics.getHeight() > Core.graphics.getWidth();
    }
}
