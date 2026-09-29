package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.content.Items;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.func.Boolc;
import com.phoenix.game.func.Boolp;
import com.phoenix.game.func.Floatc;
import com.phoenix.game.func.Floatp;
import com.phoenix.game.func.Prov;
import com.phoenix.game.game.Rules;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.struct.Array;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.Block;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * 自定义游戏规则（波次 / 建造 / 单位 / 敌方 … 的一堆开关与数值；含"禁建方块"子对话框）。
 * 参照 Mindustry mindustry.ui.dialogs.CustomRulesDialog。
 *
 * <p><b>与本项目的差异（逐条对应原版写法）</b>
 * <ul>
 *   <li><b>{@link Rules} 字段已按原版回填</b>（T2 批）：{@code respawnTime}/{@code reactorExplosions}/
 *       {@code buildCostMultiplier}/{@code buildSpeedMultiplier}/{@code blockHealthMultiplier}/
 *       {@code playerHealthMultiplier}/{@code playerDamageMultiplier}/{@code unitDrops}/
 *       {@code unitBuildSpeedMultiplier}/{@code attackMode}/{@code enemyCoreBuildRadius}/
 *       {@code solarPowerMultiplier}/{@code lighting}/{@code ambientLight}/{@code bannedBlocks} 均已存在，
 *       本对话框逐条接线。</li>
 *   <li>{@code $rules.infiniteresources}：原版是独立的 {@code rules.infiniteResources}；
 *       本项目 {@code Rules} 只有 {@code sandbox}（{@code Gamemode.sandbox} 就是把它当"无限资源"用的，
 *       见其类注释）→ 这一项接在 {@code rules.sandbox} 上（C 类，等价替代）。</li>
 *   <li>{@code rules.bannedBlocks} 已回填为 {@code ObjectSet<Block>}（T2）→ "禁建方块"子对话框
 *       直接读写 {@code rules.bannedBlocks}；消费方是 {@code world/Build#validPlace()}。</li>
 *   <li><b>{@code rules.loadout} 在本项目是 {@code ItemStack[]}</b>（原版是 {@code Array<ItemStack>}，
 *       且 {@code ItemStack.amount} 可变）→ 用本类的 {@link #loadoutStacks}（{@code struct.Array}）做中转：
 *       打开 {@code LoadoutDialog} 前从数组拷进去、关闭时拷回数组（"重置"直接重建数组）。</li>
 *   <li>{@code Blocks.coreShard.itemCapacity}：本项目没有 {@code coreShard}（核心方块叫
 *       {@code core}/{@code coreFoundation}/{@code coreNucleus}）→ 用 {@code Blocks.core.itemCapacity}。</li>
 *   <li><b>{@code Vars.platform} 在本项目不存在</b>（只有 {@code core/Platform} 接口，没有 {@code Vars} 字段）
 *       → 原版 {@code Vars.platform.addDialog(field, 100)}（移动端软键盘）整段省略。</li>
 *   <li>{@code Strings.parseFloat} / {@code Strings.canParsePositiveFloat}：本项目 {@code core/Strings}
 *       只有 {@code fixed/autoFixed/format} → 就地实现 {@link #parseFloat}；数值框不再做"非法输入变红"
 *       （arc 的 {@code TextField.valid(...)}）。</li>
 *   <li>{@code requestKeyboard()} / {@code requestScroll()}：arc 的 {@code Table} 方法 libgdx 没有
 *       → 降级为 {@code scene.setKeyboardFocus/setScrollFocus}。</li>
 *   <li>{@code 环境光}按钮：<b>已接线</b>——{@code Vars.ui.picker}（{@link com.phoenix.game.ui.dialogs.ColorPicker}）
 *       与 {@code Rules.ambientLight} 都已存在 → 点按钮弹取色器、回调写回 {@code rules.ambientLight}。
 *       预览底色：原版用 {@code Tex.alphaBg}（透明棋盘格）——本项目图集<b>没有该区域</b> → 退化成
 *       {@code Styles.black6}（C 类，刻意降级）。
 *       ⚠<b>如实标注：本项目无光照渲染系统 → {@code ambientLight} 仅存档占位、改它不会让画面变化</b>。</li>
 *   <li>arc 的 {@code Table.margin(...)} / {@code pane(Cons)} / {@code table(Drawable, Cons)} /
 *       {@code addImage(...)} / {@code addImageButton(...)} / {@code addCheck(...)} / {@code Cell.color(...)} /
 *       {@code Cell.update(...)} / {@code Cell.checked(...)} / {@code Cell.disabled(Boolp)} /
 *       {@code ImageButton.resizeImage(int)} / {@code ScrollPane.setScrollYForce(...)} libgdx 都没有 →
 *       逐处换成 {@code pad} / 自建 Table+ScrollPane / {@code add(new Image(...))} / 建好按钮再 add /
 *       {@code Label+CheckBox} / Actor 设色 / 创建时定一次 / {@code setScrollY(...)}。</li>
 *   <li>{@code content.blocks().select(Block::isBuildable)} / {@code each(Boolf, Cons)}：本项目
 *       {@code content.blocks()} 返回 libgdx 的 {@code Array}，lambda 会命中"引用不明确" → 显式下标循环。</li>
 *   <li>{@code Color.lightGray} / {@code Color.gray} / {@code Color.white}（arc 的小写常量）libgdx 没有
 *       → 用 {@code Color.LIGHT_GRAY}/{@code Color.GRAY}/{@code Color.WHITE}。</li>
 * </ul>
 */
public class CustomRulesDialog extends FloatingDialog{
    private Table main;
    private Rules rules;
    private Prov<Rules> resetter;
    private LoadoutDialog loadoutDialog;
    private FloatingDialog banDialog;

    /** "禁建方块"子对话框里的滚动面板（原版每次重建时读它的 scrollY）。 */
    private ScrollPane banPane;
    /** 发射清单的中转（原版直接操作 {@code rules.loadout}，见类注释）。 */
    private final Array<ItemStack> loadoutStacks = new Array<>();

    public CustomRulesDialog(){
        super("$mode.custom");

        loadoutDialog = new LoadoutDialog();
        banDialog = new FloatingDialog("$bannedblocks");
        banDialog.addCloseButton();

        banDialog.shown(this::rebuildBanned);
        banDialog.buttons.add(imageTextButton("$addall", Icon.add, null, () -> {
            //原版 rules.bannedBlocks.addAll(content.blocks().select(Block::isBuildable))
            //  —— libgdx 的 Array.select 收 Predicate、lambda 会"引用不明确" → 显式下标循环
            com.badlogic.gdx.utils.Array<Block> blocks = Vars.content.blocks();
            for(int i = 0; i < blocks.size; i++){
                Block block = blocks.get(i);
                if(block.isBuildable()) rules.bannedBlocks.add(block);
            }
            rebuildBanned();
        })).size(Scl.scl(180f), Scl.scl(64f));

        banDialog.buttons.add(imageTextButton("$clear", Icon.trash, null, () -> {
            rules.bannedBlocks.clear();
            rebuildBanned();
        })).size(Scl.scl(180f), Scl.scl(64f));

        setFillParent(true);
        shown(this::setup);
        addCloseButton();
    }

    private void rebuildBanned(){
        //原版 ((ScrollPane)banDialog.cont.getChildren().first()).getScrollY()（记住滚动位置）
        float previousScroll = banPane == null ? 0f : banPane.getScrollY();
        banDialog.cont.clear();

        //原版 banDialog.cont.pane(t -> {...}).get().setScrollYForce(previousScroll)
        //  —— libgdx 没有 pane(Cons)/setScrollYForce → 自建 Table + ScrollPane，滚动位置用 setScrollY
        Table list = new Table();
        list.pad(Scl.scl(10f));

        if(rules.bannedBlocks.isEmpty()){
            list.add(new Label(bundle("$empty"), Styles.defaultLabel));
        }

        Array<Block> array = new Array<>();
        for(Block block : rules.bannedBlocks){
            array.add(block);
        }
        //原版 array.sort()：本项目 Block 未实现 Comparable → 按 id 排序（语义等价）
        array.sortComparing(block -> (int)block.id);

        int cols = Vars.mobile && isPortrait() ? 1 : Vars.mobile ? 2 : 3;
        int i = 0;

        for(Block block : array){
            //原版 t.table(Tex.underline, b -> {...})：libgdx 没有"带背景 + 回调"的重载 → 自建
            Table row = new Table();
            row.setBackground(Tex.underline);
            row.left();
            row.pad(Scl.scl(4f));

            row.add(new Image(block.icon(Cicon.medium))).size(Scl.scl(Cicon.medium.size)).padRight(Scl.scl(3f));
            Label name = new Label(block.localizedName, Styles.defaultLabel);
            name.setColor(Color.LIGHT_GRAY);
            row.add(name).padLeft(Scl.scl(3f)).growX().left();

            row.add(stopIconButton(Icon.cancel, Styles.clearPartiali, () -> {
                rules.bannedBlocks.remove(block);
                rebuildBanned();
            })).size(Scl.scl(70f)).pad(Scl.scl(-4f)).padLeft(0f);

            list.add(row).size(Scl.scl(300f), Scl.scl(70f)).padRight(Scl.scl(5f));

            if(++i % cols == 0){
                list.row();
            }
        }

        banPane = new ScrollPane(list, Styles.defaultPane);
        banPane.setFadeScrollBars(false);
        banPane.setScrollingDisabled(true, false);
        banPane.setScrollY(previousScroll);

        banDialog.cont.add(banPane).grow();
        banDialog.cont.row();
        banDialog.cont.add(imageTextButton("$add", Icon.add, null, this::showAddBlockDialog)).size(Scl.scl(300f), Scl.scl(64f));
    }

    /** 原版 {@code banDialog.cont.addImageTextButton("$add", ...)} 里内联的那个"挑方块"对话框。 */
    private void showAddBlockDialog(){
        FloatingDialog dialog = new FloatingDialog("$add");

        Table list = new Table();
        list.left();
        list.pad(Scl.scl(14f));

        com.badlogic.gdx.utils.Array<Block> blocks = Vars.content.blocks();
        int i = 0;
        for(int idx = 0; idx < blocks.size; idx++){
            Block block = blocks.get(idx);
            //原版 each(b -> !rules.bannedBlocks.contains(b) && b.isBuildable(), b -> {...})
            if(rules.bannedBlocks.contains(block) || !block.isBuildable()) continue;

            int cols = Vars.mobile && isPortrait() ? 4 : 12;
            Drawable icon = new TextureRegionDrawable(block.icon(Cicon.medium));

            //libgdx 的 ImageButton 没有 (Drawable, style) 构造器 → 手工设 imageUp
            ImageButton.ImageButtonStyle bs = new ImageButton.ImageButtonStyle(Styles.cleari);
            bs.imageUp = icon;
            ImageButton button = new ImageButton(bs);
            button.getImage().setDrawable(icon);
            //原版 .get().resizeImage(Cicon.medium.size)（arc 的 ImageButton.resizeImage）
            //  —— 本项目 UI 是自建替代层、没有该方法 → 只保留 size(60f)，图标按 fit 缩放（刻意降级）
            button.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    rules.bannedBlocks.add(block);
                    rebuildBanned();
                    dialog.hide();
                }
            });

            list.add(button).size(Scl.scl(60f));

            if(++i % cols == 0){
                list.row();
            }
        }

        ScrollPane pane = new ScrollPane(list, Styles.defaultPane);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        dialog.cont.add(pane).grow();

        dialog.addCloseButton();
        dialog.show();
    }

    public void show(Rules rules, Prov<Rules> resetter){
        this.rules = rules;
        this.resetter = resetter;
        show();
    }

    void setup(){
        cont.clear();

        //原版 cont.pane(m -> main = m).get().setScrollingDisabled(true, false)
        main = new Table();
        ScrollPane pane = new ScrollPane(main, Styles.defaultPane);
        pane.setScrollingDisabled(true, false);
        cont.add(pane).grow();

        //原版 main.margin(10f)：libgdx 没有 margin → 用 pad
        main.pad(Scl.scl(10f));
        main.add(textButton("$settings.reset", null, () -> {
            rules = resetter.get();
            setup();
            requestKeyboard();
            requestScroll();
        })).size(Scl.scl(300f), Scl.scl(50f));
        main.left().defaults().fillX().left().pad(Scl.scl(5f));
        main.row();

        title("$rules.title.waves");
        check("$rules.waves", b -> rules.waves = b, () -> rules.waves);
        check("$rules.wavetimer", b -> rules.waveTimer = b, () -> rules.waveTimer);
        check("$rules.waitForWaveToEnd", b -> rules.waitForWaveToEnd = b, () -> rules.waitForWaveToEnd);
        number("$rules.wavespacing", false, f -> rules.waveSpacing = f * 60f, () -> rules.waveSpacing / 60f, () -> true);
        number("$rules.dropzoneradius", false, f -> rules.dropZoneRadius = f * Vars.tilesize, () -> rules.dropZoneRadius / Vars.tilesize, () -> true);

        title("$rules.title.respawns");
        number("$rules.respawntime", f -> rules.respawnTime = f * 60f, () -> rules.respawnTime / 60f);

        title("$rules.title.resourcesbuilding");
        //本项目用 rules.sandbox 顶替原版 rules.infiniteResources（见类注释）
        check("$rules.infiniteresources", b -> rules.sandbox = b, () -> rules.sandbox);
        check("$rules.reactorexplosions", b -> rules.reactorExplosions = b, () -> rules.reactorExplosions);
        number("$rules.buildcostmultiplier", false, f -> rules.buildCostMultiplier = f, () -> rules.buildCostMultiplier, () -> !rules.sandbox);
        number("$rules.buildspeedmultiplier", f -> rules.buildSpeedMultiplier = f, () -> rules.buildSpeedMultiplier);
        number("$rules.deconstructrefundmultiplier", false, f -> rules.deconstructRefundMultiplier = f,
            () -> rules.deconstructRefundMultiplier, () -> !rules.sandbox);
        number("$rules.blockhealthmultiplier", f -> rules.blockHealthMultiplier = f, () -> rules.blockHealthMultiplier);

        //原版 main.addButton("$configure", () -> loadoutDialog.show(Blocks.coreShard.itemCapacity, rules.loadout, ...))
        //  —— 本项目 rules.loadout 是 ItemStack[] → 用 loadoutStacks 中转（见类注释）
        loadoutStacks.clear();
        for(ItemStack stack : rules.loadout){
            loadoutStacks.add(stack);
        }
        Runnable syncLoadout = () -> rules.loadout = loadoutStacks.toArray(ItemStack.class);
        main.add(textButton("$configure", null, () ->
            loadoutDialog.show(Blocks.core.itemCapacity, loadoutStacks,
                //原版重置成"100 铜"：本项目 rules.loadout 是定长数组 → 直接重建
                () -> {
                    loadoutStacks.clear();
                    loadoutStacks.add(new ItemStack(Items.copper, 100));
                    syncLoadout.run();
                },
                syncLoadout, syncLoadout)
        )).left().width(Scl.scl(300f));
        main.row();

        main.add(textButton("$bannedblocks", null, banDialog::show)).left().width(Scl.scl(300f));
        main.row();

        title("$rules.title.player");
        number("$rules.playerhealthmultiplier", f -> rules.playerHealthMultiplier = f, () -> rules.playerHealthMultiplier);
        number("$rules.playerdamagemultiplier", f -> rules.playerDamageMultiplier = f, () -> rules.playerDamageMultiplier);

        title("$rules.title.unit");
        check("$rules.unitdrops", b -> rules.unitDrops = b, () -> rules.unitDrops, () -> true);
        number("$rules.unithealthmultiplier", f -> rules.unitHealthMultiplier = f, () -> rules.unitHealthMultiplier);
        number("$rules.unitdamagemultiplier", f -> rules.unitDamageMultiplier = f, () -> rules.unitDamageMultiplier);
        number("$rules.unitbuildspeedmultiplier", f -> rules.unitBuildSpeedMultiplier = f, () -> rules.unitBuildSpeedMultiplier);

        title("$rules.title.enemy");
        check("$rules.attack", b -> rules.attackMode = b, () -> rules.attackMode);
        check("$rules.enemyCheat", b -> rules.enemyCheat = b, () -> rules.enemyCheat);
        number("$rules.enemycorebuildradius", f -> rules.enemyCoreBuildRadius = f * Vars.tilesize,
            () -> Math.min(rules.enemyCoreBuildRadius / Vars.tilesize, 200f));

        title("$rules.title.experimental");
        number("$rules.solarpowermultiplier", f -> rules.solarPowerMultiplier = f, () -> rules.solarPowerMultiplier);
        check("$rules.lighting", b -> rules.lighting = b, () -> rules.lighting);

        //原版 main.addButton(b -> {b.left(); b.table(Tex.pane, in -> in.stack(new Image(Tex.alphaBg),
        //  new Image(Tex.whiteui){{ update(() -> setColor(rules.ambientLight)); }}).grow()).margin(4).size(50f)...;
        //  b.add("$rules.ambientlight");}, () -> ui.picker.show(rules.ambientLight, rules.ambientLight::set))
        //  —— 本项目 Tex.alphaBg 不在图集 → 预览底色退化成 Styles.black6（刻意降级）；
        //     取色器 Vars.ui.picker 与 Rules.ambientLight 都已存在 → 照原版接线
        TextButton ambient = textButton("$rules.ambientlight", Styles.defaultt, () -> {
            if(Vars.ui != null && Vars.ui.picker != null){
                Vars.ui.picker.show(rules.ambientLight, rules.ambientLight::set);
            }
        });
        Table swatchBox = new Table();
        swatchBox.setBackground(Tex.pane);
        //上层白图每帧跟随 rules.ambientLight 变色（对应原版 Image.update(() -> setColor(...))）
        Image ambientSwatch = new Image(Tex.whiteui){
            @Override
            public void act(float delta){
                super.act(delta);
                setColor(rules.ambientLight);
            }
        };
        swatchBox.stack(new Image(Styles.black6), ambientSwatch).size(Scl.scl(50f));
        ambient.add(swatchBox).pad(Scl.scl(4f)).padRight(Scl.scl(10f));
        //预览色块要排在文字前面（原版先 b.table(...) 再 b.add(text)）
        ambient.getCells().reverse();
        main.add(ambient).left().width(Scl.scl(250f));
        main.row();
        //⚠ 本项目无光照渲染系统：rules.ambientLight 仅存档占位，改它不会有视觉变化
    }

    void number(String text, Floatc cons, Floatp prov){
        number(text, false, cons, prov, () -> true);
    }

    /**
     * 一行"标签 + 数值输入框"（对应原版 {@code number}）。
     * <p>TODO 原版用 {@code Vars.platform.addDialog(field, 100)}（移动端软键盘）——本项目没有 {@code Vars.platform}
     * 字段（{@code core/Platform} 是无实例的死接口），该段省略（B 类）。
     * <p>{@code Strings.parseFloat} / {@code canParsePositiveFloat} / {@code TextField.valid(...)}：
     * 本项目 {@code core/Strings} 只有 {@code fixed/autoFixed/format/stripColors}、libgdx 也没有 {@code valid(...)}
     * → 已用本类 {@link #parseFloat}（{@code Float.parseFloat} + try/catch）+ {@code setTextFieldFilter}
     * 做等价替代（C 类，刻意降级：非法输入不再变红，只做"解析失败就忽略"）。
     */
    void number(String text, boolean integer, Floatc cons, Floatp prov, Boolp condition){
        Table t = new Table();
        t.left();

        //原版 t.add(text).left().padRight(5).update(a -> a.setColor(condition.get() ? Color.white : Color.gray))
        //  —— libgdx 的 Cell 没有 update(...)（每帧回调）→ 创建时定一次颜色
        //  —— 另外 arc 的 add(String) 会解析 "$key"，这里必须自己过一遍 bundle()
        Label label = new Label(bundle(text), Styles.defaultLabel);
        label.setColor(condition.get() ? Color.WHITE : Color.GRAY);
        t.add(label).left().padRight(Scl.scl(5f));

        String initial = integer ? String.valueOf((int)prov.get()) : String.valueOf(prov.get());
        TextField field = new TextField(initial, Styles.defaultField);
        //原版 .update(a -> a.setDisabled(!condition.get()))
        field.setDisabled(!condition.get());
        //原版 .valid(Strings::canParsePositiveFloat) 只允许合法数字 → 用输入过滤器近似（允许数字/小数点/负号）
        field.setTextFieldFilter((f, c) -> Character.isDigit(c) || c == '.' || c == '-');
        field.addListener(new ChangeListener(){
            @Override
            public void changed(ChangeEvent event, Actor actor){
                float value = parseFloat(field.getText());
                if(!Float.isNaN(value)){
                    cons.get(value);
                }
            }
        });

        t.add(field).padRight(Scl.scl(100f)).width(Scl.scl(120f)).left();

        //TODO 原版 Vars.platform.addDialog(t.addField(...).get(), 100)（移动端软键盘）——本项目无 Vars.platform

        main.add(t).padTop(0f);
        main.row();
    }

    void check(String text, Boolc cons, Boolp prov){
        check(text, cons, prov, () -> true);
    }

    /**
     * 一行"标签 + 勾选框"（对应原版 {@code check}）。
     * <p>原版 {@code main.addCheck(text, cons)}（arc 的 Table 方法）——本项目 UI 是自建替代层、没有该方法
     * → 用 {@link CheckBox} + 旁边的 {@link Label}（点击文字不切换勾选，与原版略有差异；刻意降级）。
     */
    void check(String text, Boolc cons, Boolp prov, Boolp condition){
        Table t = new Table();
        t.left();

        //原版 main.addCheck(text, cons)（arc 的 Table 方法，会自动解析 "$key"）libgdx 没有
        //→ 用 {@link CheckBox} + 旁边的 {@link Label}（点击文字不切换勾选，与原版略有差异）
        t.add(new Label(bundle(text), Styles.defaultLabel)).left().padRight(Scl.scl(5f));

        CheckBox box = new CheckBox("", Styles.defaultCheck);
        //原版 .checked(prov.get())
        box.setChecked(prov.get());
        //原版 .update(a -> a.setDisabled(!condition.get()))
        box.setDisabled(!condition.get());
        box.addListener(new ChangeListener(){
            @Override
            public void changed(ChangeEvent event, Actor actor){
                cons.get(box.isChecked());
            }
        });
        t.add(box);

        main.add(t).padRight(Scl.scl(100f)).left();
        main.row();
    }

    void title(String text){
        //原版 main.add(text).color(Pal.accent)...：libgdx 的 Cell 没有 color → 给 Label 设色
        //（arc 的 add(String) 会解析 "$key"，这里自己过一遍 bundle()）
        Label label = new Label(bundle(text), Styles.defaultLabel);
        label.setColor(Pal.accent);
        main.add(label).padTop(Scl.scl(20f)).padRight(Scl.scl(100f)).padBottom(Scl.scl(-3f));
        main.row();

        //原版 main.addImage().color(Pal.accent).height(3f)...：用 whiteui 拼一条分隔线
        Image divider = new Image(Tex.whiteui);
        divider.setColor(Pal.accent);
        main.add(divider).height(Scl.scl(3f)).padRight(Scl.scl(100f)).padBottom(Scl.scl(20f));
        main.row();
    }

    /**
     * 原版 arc 的 {@code Table.requestKeyboard()} / {@code requestScroll()}——本项目 UI 是自建替代层、
     * 没有这两个方法（刻意降级）→ 改为把键盘/滚轮焦点交给本对话框（scene 由启动器注入）。
     */
    void requestKeyboard(){
        if(scene != null) scene.setKeyboardFocus(this);
    }

    void requestScroll(){
        if(scene != null) scene.setScrollFocus(this);
    }

    /** @return 解析后的浮点数；解析失败返回 {@code NaN}（替代本项目没有的 {@code Strings.parseFloat}）。 */
    private static float parseFloat(String text){
        try{
            return Float.parseFloat(text.trim());
        }catch(Exception e){
            return Float.NaN;
        }
    }

    /** @return 是否竖屏（对应 arc 的 {@code Core.graphics.isPortrait()}，libgdx 的 Graphics 没有该方法）。 */
    private static boolean isPortrait(){
        return Core.graphics.getHeight() > Core.graphics.getWidth();
    }

    /**
     * 与 {@code SaveDialog.stopIconButton} 等价：点击后额外 {@code event.stop()}，
     * 阻止事件冒泡到外层行（原版靠 {@code Cell} 的点击目标判定，libgdx 用 stop 代替）。
     */
    private static ImageButton stopIconButton(Drawable icon, ImageButton.ImageButtonStyle style, Runnable action){
        ImageButton.ImageButtonStyle bs = new ImageButton.ImageButtonStyle(style);
        bs.imageUp = icon;
        ImageButton button = new ImageButton(bs);
        button.getImage().setDrawable(icon);
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                event.stop();
                action.run();
            }
        });
        return button;
    }
}
