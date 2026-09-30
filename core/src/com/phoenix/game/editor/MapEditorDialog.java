package com.phoenix.game.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.IntSet;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.core.GameState;
import com.phoenix.game.core.Scl;
import com.phoenix.game.func.Boolp;
import com.phoenix.game.func.Cons;
import com.phoenix.game.game.Rules;
import com.phoenix.game.game.Team;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.input.KeyCode;
import com.phoenix.game.io.MapIO;
import com.phoenix.game.io.SaveIO;
import com.phoenix.game.maps.Map;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.struct.Array;
import com.phoenix.game.struct.StringMap;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.ui.dialogs.FileChooser;
import com.phoenix.game.ui.dialogs.FloatingDialog;
import com.phoenix.game.util.Fi;
import com.phoenix.game.util.Log;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.OverlayFloor;
import com.phoenix.game.world.blocks.Rock;
import com.phoenix.game.world.blocks.storage.CoreBlock;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

import java.io.File;
import java.io.IOException;

/**
 * 地图编辑器主对话框（全屏：左侧工具栏、中间画布 {@link MapView}、右侧方块选择栏 + 弹出菜单）。
 * 参照 Mindustry {@code mindustry.editor.MapEditorDialog} 移植。
 *
 * <p><b>它持有</b>：{@link #editor}（模型，同时写入 {@code Vars.editor}）、{@link MapView}、
 * {@link MapInfoDialog}、{@link MapLoadDialog}、{@link MapResizeDialog}、{@link MapGenerateDialog}，
 * 以及一个 {@code $menu} 弹出菜单（存档/地图信息/生成器/改尺寸/导入/导出/试玩/退出）。
 *
 * <p><b>构造时机（重要）</b>：{@link MapEditor} 的字段初始化会读 {@code Blocks.stone} 与
 * {@code Core.atlas}（{@code MapRenderer} 构造器里取 {@code clear-editor}），
 * {@link WaveInfoDialog} 的字段初始化会读 {@code UnitTypes.dagger}
 * → 本对话框**必须**在 {@code Vars.content.load()} 之后构造。
 * 本项目由 {@code UI.init()} 统一 new（{@code Vars.ui = new UI()} 的接线在 ClientLauncher 里位于
 * content.load() 之后），所以只要走 {@code Vars.ui.editor} 就满足该前提。
 *
 * <p><b>对外 public API</b>：{@link #editor}（模型）、{@link #getView()}、{@link #getGenerateDialog()}、
 * {@link #save()}、{@link #resetSaved()}、{@link #hasPane()}、{@link #build()}、{@link #resumeEditing()}、
 * {@link #beginEditMap(Fi)}、{@link #dispose()}。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li><b>基类</b>：原版 {@code extends Dialog}；本项目改继承 {@link FloatingDialog}
 *       （为了复用本项目对话框的 {@code cont/buttons/show()/shown/hidden/addCloseButton}）。
 *       {@link #build()} 里会 {@code clearChildren()} 掉基类的标题栏与内容表，所以外观不受影响；
 *       唯一残留差异是 {@code FloatingDialog} 的 hidden 回调会播一次 {@code Sounds.back}。</li>
 *   <li>TODO <b>试玩（$editor.ingame）整体降级（B 类）</b>：原版 {@code playtest()} 依赖
 *       {@code Gamemode.editor}（本项目<b>有</b>，见 {@code game/Gamemode.java}）、{@code Rules.editor}
 *       （也<b>有</b>）、{@code World.setMap/endMapLoad/rawTile}、{@code Player.reset/set/setDead/dead}、
 *       {@code Vars.logic}（仿真实例）—— 后三者本项目都缺 → 只提示 + 日志（见 {@link #playtest()}）。</li>
 *   <li>TODO <b>{@code Vars.platform} 不存在</b>：原版用它做文件选择器 / 软键盘 / 强制横屏 / Steam 发布
 *       → 文件选择改用本项目已移植的 {@link FileChooser}；软键盘、{@code beginForceLandscape/endForceLandscape}、
 *       {@code updateRPC}、Steam 发布整段省略。</li>
 *   <li><b>{@code MapIO.isImage}/{@code MapIO.createMap} 已存在</b>（{@code io/MapIO} T19 追加）→ 导入地图
 *       已改调这两个方法（"导入文件"按钮与 {@link #beginEditMap(Fi)}），不再手写 {@code .msav} 解析。
 *       {@code MapIO.writeMap} 仍缺 → 导出用 {@code SaveIO.save(file, tags)}
 *       （并补上 {@code Maps.exportCurrent} 会写的 spawns/teams 统计，
 *       否则导出的图在 {@code MapPlayDialog} 里所有模式都不可用），见 {@link #saveMap(StringMap, Fi)}。</li>
 *   <li>TODO <b>{@code Maps.saveMap} / {@code Maps.tryCatchMapError} 都没有</b> → 存档走
 *       {@link #saveMap(StringMap, Fi)} 自实现；导入的异常处理退化成 {@code ui.showException}。</li>
 *   <li>TODO(B) <b>{@code tags["rules"]}</b>：原版保存时用 {@code JsonIO.write(state.rules)} 序列化规则。
 *       ⚠ 实测本项目的 {@code JsonIO} <b>写得出、读不回</b>——{@code DefaultWaves} 用匿名子类
 *       {@code new SpawnGroup(type){{...}}} 构造，libgdx Json 无法实例化（{@code DefaultWaves$1} 无可用无参构造）
 *       → 读回抛 {@code SerializationException}，{@code Map.rules()} 会 catch 后退回默认规则。
 *       故**不写**该标签（写了也是静默丢失）。修法方向：匿名子类改具名子类 / 注册 serializer。
 *       临时置 {@code state.rules.editor = false} 再还原仍照原版保留。</li>
 *   <li><b>输入层</b>：{@code Core.input.keyTap/ctrl()} 本项目没有 → 用 {@code Gdx.input.isKeyPressed} +
 *       自己维护"上一帧按下的键"（{@link #lastKeys}）模拟 keyTap；{@code Core.scene} → {@link FloatingDialog#scene}。</li>
 *   <li>arc 的 {@code Table.addImageTextButton/addImageButton/addButton/table(Drawable,Cons)/margin}、
 *       {@code Cell.update/checked/disabled(Boolp)}、{@code button.clicked(Cons,ClickListener)}、
 *       {@code ImageButton.resizeImage}、{@code ImageButtonStyle.imageUpColor}、{@code labelWrap(Prov)}、
 *       {@code ScrollPane.hasScroll} libgdx 都没有 → 逐处手搓（"每帧回调"改成覆写 {@code act()}）。</li>
 *   <li>刻意降级：{@code Icon.download/upload/upOpen/downOpen} 本项目生成包 {@code Icon} 里没有
 *       （已查生成的 {@code Icon.java}）→ 分别用 {@code Icon.loadImage}/{@code Icon.saveImage}/
 *       {@code Icon.arrowUp}/{@code Icon.arrowDown}。</li>
 *   <li><b>判方块用 {@code blockRaw()}</b>：本项目 {@code Tile.block()} 会解析多格链接（原版不会）
 *       → ctrl+T"清装饰"等处一律用 {@code blockRaw()}，否则会拆散多格建筑（见 转换.md 地图批 D1c）。</li>
 *   <li>刻意降级：{@code Shaders}/{@code IndexedRenderer} 相关（原版画布用静态网格）已整体降级为
 *       逐帧即时绘制，见 {@link MapRenderer} 的类注释。</li>
 * </ul>
 */
public class MapEditorDialog extends FloatingDialog{
    public final MapEditor editor;

    private final MapView view;
    private final MapInfoDialog infoDialog;
    //注意：这两个字段不能加 final —— 它们在构造器靠后的位置才赋值，而前面构建 menuTable 的 lambda 已引用它们。
    //lambda 只在点击时执行（那时字段早已就绪），但 final 会让 javac 的 definite-assignment 检查
    //报"可能尚未初始化变量"。
    private MapLoadDialog loadDialog;
    private MapResizeDialog resizeDialog;
    private final MapGenerateDialog generateDialog;
    private ScrollPane pane;
    private final FloatingDialog menu;
    private boolean saved = false;
    private boolean shownWithMap = false;
    private final Array<Block> blocksOut = new Array<>();

    /** 当前弹出的"工具备用模式"浮层（原版用 {@code Table[] lastTable} 记录，这里换成字段便于 hide 时清理）。 */
    private Table altTable;

    /** 上一帧处于按下状态的键（用来模拟 arc 的 {@code Core.input.keyTap}）。 */
    private final IntSet lastKeys = new IntSet();

    public MapEditorDialog(){
        super("");

        setBackground(Styles.black);

        //注意：MapEditor 的字段初始化会读 Blocks.stone / Core.atlas，构造时机必须在 Vars.content.load() 之后
        editor = new MapEditor();
        //对应原版 EditorTile 里的 ui.editor.editor；D2a 只声明了 Vars.editor，这里补上赋值
        Vars.editor = editor;

        view = new MapView(editor);
        infoDialog = new MapInfoDialog(editor);
        generateDialog = new MapGenerateDialog(editor, true);

        menu = new FloatingDialog("$menu");
        menu.addCloseButton();

        float swidth = 180f;

        Table mc = menu.getContentTable();
        Table menuTable = new Table();
        menuTable.defaults().size(Scl.scl(swidth), Scl.scl(60f)).padBottom(Scl.scl(5f)).padRight(Scl.scl(5f)).padLeft(Scl.scl(5f));

        menuTable.add(imageTextButton("$editor.savemap", Icon.save, null, this::save));

        menuTable.add(imageTextButton("$editor.mapinfo", Icon.pencil, null, () -> {
            infoDialog.show();
            menu.hide();
        }));

        menuTable.row();

        menuTable.add(imageTextButton("$editor.generate", Icon.terrain, null, () -> {
            generateDialog.show(generateDialog::applyToEditor);
            menu.hide();
        }));

        menuTable.add(imageTextButton("$editor.resize", Icon.resize, null, () -> {
            resizeDialog.show();
            menu.hide();
        }));

        menuTable.row();

        //刻意降级：原版用 Icon.download/Icon.upload；本项目生成包 Icon 里没有 → 用 loadImage/saveImage
        menuTable.add(imageTextButton("$editor.import", Icon.loadImage, null, () ->
            createDialog("$editor.import",
                //注意：这里必须用 lambda 而不是 loadDialog::show —— 方法引用会在构造期就绑定 loadDialog 的当前值（null）
                "$editor.importmap", "$editor.importmap.description", Icon.loadMap, (Runnable)() -> loadDialog.show(),
                "$editor.importfile", "$editor.importfile.description", Icon.file, (Runnable)() ->
                    showFileChooser(true, Vars.mapExtension, file -> {
                        if(Vars.ui == null) return;
                        Vars.ui.loadAnd(() -> {
                            try{
                                //原版 MapEditorDialog.java:96-99 =
                                //if(MapIO.isImage(file)) ui.showInfo("$editor.errorimage"); else editor.beginEdit(MapIO.createMap(file, true));
                                if(MapIO.isImage(file.file())){
                                    Vars.ui.showInfo("$editor.errorimage");
                                }else{
                                    editor.beginEdit(MapIO.createMap(file.file(), true));
                                }
                            }catch(Exception e){
                                Vars.ui.showException("$editor.errorload", e);
                                Log.err(e);
                            }
                        });
                    }),

                "$editor.importimage", "$editor.importimage.description", Icon.fileImage, (Runnable)() ->
                    showFileChooser(true, "png", file -> {
                        if(Vars.ui == null) return;
                        Vars.ui.loadAnd(() -> {
                            try{
                                //原版 = new Pixmap(file)；libgdx 的 Pixmap 要 FileHandle
                                Pixmap pixmap = new Pixmap(file.handle());
                                editor.beginEdit(pixmap);
                                pixmap.dispose();
                            }catch(Exception e){
                                Vars.ui.showException("$editor.errorload", e);
                                Log.err(e);
                            }
                        });
                    })
            )));

        menuTable.add(imageTextButton("$editor.export", Icon.saveImage, null, () -> {
            //原版用 !ios 分支 + platform.showFileChooser(false, mapExtension, ...)
            showFileChooser(false, Vars.mapExtension, file -> {
                if(Vars.ui == null) return;
                Vars.ui.loadAnd(() -> {
                    try{
                        if(!editor.getTags().containsKey("name")){
                            editor.getTags().put("name", file.nameWithoutExtension());
                        }
                        saveMap(editor.getTags(), file);
                    }catch(Exception e){
                        Vars.ui.showException("$editor.errorsave", e);
                        Log.err(e);
                    }
                });
            });
        }));

        mc.add(menuTable);
        mc.row();

        //TODO 原版 steam 分支（$editor.publish.workshop）：Vars.steam 恒 false + 没有 Vars.platform → 整段省略

        mc.add(imageTextButton("$editor.ingame", Icon.arrowRight, null, this::playtest))
            .padTop(Scl.scl(-3f)).size(Scl.scl(swidth * 2f + 10f), Scl.scl(60f));
        mc.row();

        mc.add(imageTextButton("$quit", Icon.exit, null, () -> {
            tryExit();
            menu.hide();
        })).size(Scl.scl(swidth * 2f + 10f), Scl.scl(60f));

        resizeDialog = new MapResizeDialog(editor, (x, y) -> {
            if(!(editor.width() == x && editor.height() == y)){
                if(Vars.ui != null){
                    Vars.ui.loadAnd(() -> editor.resize(x, y));
                }else{
                    editor.resize(x, y);
                }
            }
        });

        loadDialog = new MapLoadDialog(map -> {
            if(Vars.ui == null) return;
            Vars.ui.loadAnd(() -> {
                try{
                    editor.beginEdit(map);
                }catch(Exception e){
                    Vars.ui.showException("$editor.errorload", e);
                    Log.err(e);
                }
            });
        });

        setFillParent(true);

        clearChildren();
        //libgdx 的 Window.drawBackground 会直接 titleTable.draw(...)，光靠 clearChildren() 摘不掉标题栏
        //（FloatingDialog 在标题表里塞了标题 Label + 一条 accent 分隔线）→ 显式清空它的子节点
        titleTable.clearChildren();
        //原版 margin(0)：libgdx 没有 margin（外间距）→ 用 pad
        pad(0f);

        shown(() -> {
            saved = true;
            //TODO 原版这里调 platform.beginForceLandscape()（移动端强制横屏）——本项目没有 Vars.platform
            editor.clearOp();
            if(scene != null) scene.setScrollFocus(view);
            if(!shownWithMap){
                //clear units, rules and other unnecessary stuff
                //TODO 原版 = logic.reset() + state.rules = new Rules()；本项目没有 Vars.logic（仿真在 Control/Logic 里）
                Vars.state.rules = new Rules();
                editor.beginEdit(200, 200);
            }
            shownWithMap = false;

            //TODO 原版 = Time.runTask(10f, platform::updateRPC)（把编辑器内容同步给 RPC）——无 Vars.platform
        });

        hidden(() -> {
            editor.clearOp();
            //TODO 原版 = platform.updateRPC() + platform.endForceLandscape() —— 无 Vars.platform
            clearAltTable();
        });

        shown(this::build);
    }

    /**
     * 从游戏里回到编辑器（保留上次的瓦片）。
     * <p>TODO 原版会恢复 {@code lastSavedRules}（试玩前的 rules）；本项目没有可用的试玩 → 该字段删除。
     */
    public void resumeEditing(){
        Vars.state.set(GameState.State.menu);
        shownWithMap = true;
        show();
        editor.renderer().updateAll();
    }

    /**
     * 试玩。
     * <p>TODO 原版：{@code lastSavedRules = state.rules; hide(); state.teams = new Teams(); player.reset();
     * state.rules = Gamemode.editor.apply(lastSavedRules.copy()); state.rules.zone = null;
     * world.setMap(new Map(...)); world.endMapLoad(); 逐格 add() 实体; player.set(...);
     * player.setDead(false); logic.play();}
     * <p>本项目 {@code World.setMap/endMapLoad/rawTile} <b>都已存在</b>（S1 批次）；真正缺的是
     * {@code Player.reset,set,setDead,dead} 与 {@code Vars.logic} → 整段降级为提示 + 日志（B 类）。
     * <p>注：{@code Gamemode.editor}（{@code game/Gamemode.java}）与 {@code Rules.editor} 本项目<b>都有</b>，
     * 但上面其余依赖仍缺，所以试玩依旧不可用。
     */
    private void playtest(){
        menu.hide();
        Log.warn("TODO playtest: editor playtesting is not available in phoenix yet "
            + "(missing Player.reset/set/setDead/dead, Vars.logic).");
        if(Vars.ui != null){
            Vars.ui.showInfoText(bundle("$editor.ingame"),
                "Editor playtesting is not implemented yet in phoenix "
                    + "(missing Player.reset/set/setDead/dead, Vars.logic).");
        }
    }

    /** 保存当前地图（原版返回保存出的 {@link Map}，供 Steam 发布复用）。 */
    public Map save(){
        boolean isEditor = Vars.state.rules.editor;
        Vars.state.rules.editor = false;
        String name = editor.getTags().get("name", "").trim();
        //TODO(B) 原版 = editor.getTags().put("rules", JsonIO.write(Vars.state.rules))。
        //  ⚠ 实测（一次性调试钩子验证）：write 能成功（1568 字符），但 read 回来抛
        //  SerializationException: Class cannot be created (missing no-arg constructor):
        //  com.phoenix.game.game.DefaultWaves$1 —— DefaultWaves 用匿名子类
        //  new SpawnGroup(type){{...}} 构造，libgdx Json 无法实例化（匿名类跨包 setAccessible 失败）。
        //  写进去也读不回（Map.rules() 会 catch 后退回默认规则）→ 不如不写，保持 B 类。
        //  修法方向：把匿名子类改成具名子类，或在 JsonIO 里注册该类型的 serializer。
        editor.getTags().remove("width");
        editor.getTags().remove("height");
        //B 类：原版还会 player.dead = true；本项目 Player 没有 dead 字段（isDead() 由被控单位状态推导）→ 省略

        Map returned = null;

        if(name.isEmpty()){
            infoDialog.show();
            Gdx.app.postRunnable(() -> {
                if(Vars.ui != null) Vars.ui.showErrorMessage("$editor.save.noname");
            });
        }else{
            Map map = findMap(name);
            if(map != null && !map.custom){
                handleSaveBuiltin(map);
            }else{
                returned = saveMap(editor.getTags(), null);
                if(Vars.ui != null) Vars.ui.showInfoFade("$editor.saved");
            }
        }

        menu.hide();
        saved = true;
        Vars.state.rules.editor = isEditor;
        return returned;
    }

    /** Called when a built-in map save is attempted. */
    protected void handleSaveBuiltin(Map map){
        if(Vars.ui != null) Vars.ui.showErrorMessage("$editor.save.overwrite");
    }

    /**
     * Argument format:
     * 0) button name
     * 1) description
     * 2) icon name
     * 3) listener
     */
    private void createDialog(String title, Object... arguments){
        FloatingDialog dialog = new FloatingDialog(title);

        Table c = dialog.getContentTable();

        float h = 90f;

        c.defaults().size(Scl.scl(360f), Scl.scl(h)).padBottom(Scl.scl(5f)).padRight(Scl.scl(5f)).padLeft(Scl.scl(5f));

        for(int i = 0; i < arguments.length; i += 4){
            String name = (String)arguments[i];
            String description = (String)arguments[i + 1];
            Drawable iconname = (Drawable)arguments[i + 2];
            final Runnable listenable = (Runnable)arguments[i + 3];

            //原版 = dialog.cont.addButton(name, () -> {...}).left().margin(0).get()
            //  —— libgdx 没有 addButton；这里自建 TextButton 并手工拼内容
            TextButton button = new TextButton("", Styles.cleart);
            button.clearChildren();
            button.add(new Image(iconname)).padLeft(Scl.scl(10f));

            Table content = new Table();
            Label nameLabel = new Label(bundle(name), Styles.defaultLabel);
            nameLabel.setWrap(true);
            content.add(nameLabel).growX();
            content.row();
            Label descLabel = new Label(bundle(description), Styles.defaultLabel);
            descLabel.setColor(Color.GRAY);
            descLabel.setWrap(true);
            content.add(descLabel).growX();
            button.add(content).growX().pad(Scl.scl(10f)).padLeft(Scl.scl(5f));
            button.row();

            button.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    listenable.run();
                    dialog.hide();
                    menu.hide();
                }
            });

            c.add(button).left();
            c.row();
        }

        dialog.addCloseButton();
        dialog.show();
    }

    public void beginEditMap(Fi file){
        if(Vars.ui == null){
            shownWithMap = true;
            //原版 MapEditorDialog.java:388 = editor.beginEdit(MapIO.createMap(file, true))；headless 分支就地兜住异常
            try{
                editor.beginEdit(MapIO.createMap(file.file(), true));
            }catch(Exception e){
                Log.err(e);
            }
            show();
            return;
        }

        Vars.ui.loadAnd(() -> {
            try{
                shownWithMap = true;
                //原版 MapEditorDialog.java:388 = editor.beginEdit(MapIO.createMap(file, true))
                editor.beginEdit(MapIO.createMap(file.file(), true));
                show();
            }catch(Exception e){
                Log.err(e);
                Vars.ui.showException("$editor.errorload", e);
            }
        });
    }

    public MapView getView(){
        return view;
    }

    public MapGenerateDialog getGenerateDialog(){
        return generateDialog;
    }

    public void resetSaved(){
        saved = false;
    }

    public boolean hasPane(){
        //原版 = Core.scene.getScrollFocus() == pane || Core.scene.getKeyboardFocus() != this
        if(scene == null) return false;
        return scene.getScrollFocus() == pane || scene.getKeyboardFocus() != this;
    }

    public void build(){
        float size = 60f;

        clearChildren();
        pad(0f);

        Table root = new Table();
        root.left();

        // ---- 左列：工具栏 ----
        Table mid = new Table();
        mid.top();

        Table tools = new Table();
        tools.top();
        tools.defaults().size(Scl.scl(size), Scl.scl(size));

        ButtonGroup<ImageButton> group = new ButtonGroup<>();

        Cons<EditorTool> addTool = tool -> {
            final ImageButton button = new ImageButton(toggleStyle(Vars.ui.getIcon(tool.name()), Styles.clearTogglei)){
                @Override
                public void act(float delta){
                    super.act(delta);
                    //原版 = button.update(() -> button.setChecked(view.getTool() == tool))
                    setChecked(view.getTool() == tool);
                }
            };
            group.add(button);

            button.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    //原版：桌面端右键 / 移动端双击 弹出该工具的备用模式
                    if(tool.altModes.length > 0 && getButton() == Input.Buttons.RIGHT){
                        showAltModes(button, tool);
                        return;
                    }
                    view.setTool(tool);
                    clearAltTable();
                }
            });

            Label mode = new Label("", Styles.defaultLabel){
                @Override
                public void act(float delta){
                    super.act(delta);
                    //原版 = mode.update(() -> mode.setText(tool.mode == -1 ? "" : "M" + (tool.mode + 1) + " "))
                    setText(tool.mode == -1 ? "" : "M" + (tool.mode + 1) + " ");
                }
            };
            mode.setColor(Pal.remove);
            mode.setAlignment(Align.bottomRight, Align.bottomRight);
            mode.setTouchable(Touchable.disabled);

            Stack stack = new Stack();
            stack.add(button);
            stack.add(mode);
            tools.add(stack);
        };

        tools.add(iconButton(Icon.menu, Styles.cleari, menu::show));

        final ImageButton grid = new ImageButton(toggleStyle(Icon.grid, Styles.clearTogglei)){
            @Override
            public void act(float delta){
                super.act(delta);
                setChecked(view.isGrid());
            }
        };
        grid.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                view.setGrid(!view.isGrid());
            }
        });
        tools.add(grid);

        addTool.get(EditorTool.zoom);

        tools.row();

        tools.add(disabledIconButton(Icon.undo, Styles.cleari, editor::undo, () -> editor.canUndo()));
        tools.add(disabledIconButton(Icon.redo, Styles.cleari, editor::redo, () -> editor.canRedo()));

        addTool.get(EditorTool.pick);

        tools.row();

        addTool.get(EditorTool.line);
        addTool.get(EditorTool.pencil);
        addTool.get(EditorTool.eraser);

        tools.row();

        addTool.get(EditorTool.fill);
        addTool.get(EditorTool.spray);

        //原版 = rotate.getImage().update(() -> {setRotation(editor.rotation * 90); setOrigin(Align.center);})
        //刻意沿用原版公式：本项目 rotation 与原版相差 90°（本项目 0 = +y）；图标基准朝向未定，暂不改动
        final ImageButton rotate = new ImageButton(toggleStyle(Icon.arrowRight, Styles.cleari)){
            @Override
            public void act(float delta){
                super.act(delta);
                getImage().setOrigin(Align.center);
                getImage().setRotation(editor.rotation * 90f);
            }
        };
        rotate.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                editor.rotation = (editor.rotation + 1) % 4;
            }
        });
        tools.add(rotate);

        tools.row();

        Table teamHeader = new Table();
        teamHeader.setBackground(Tex.underline);
        teamHeader.add(new Label(bundle("$editor.teams"), Styles.defaultLabel));
        tools.add(teamHeader).colspan(3).height(Scl.scl(40f)).width(Scl.scl(size * 3f + 3f)).padBottom(Scl.scl(3f));

        tools.row();

        ButtonGroup<ImageButton> teamgroup = new ButtonGroup<>();

        int i = 0;

        for(Team team : Team.base()){
            final Team selected = team;
            ImageButton button = new ImageButton(toggleStyle(Tex.whiteui, Styles.clearTogglePartiali)){
                @Override
                public void act(float delta){
                    super.act(delta);
                    setChecked(editor.drawTeam == selected);
                }
            };
            //原版 = button.margin(4f)：libgdx 没有 margin → 用 pad
            button.pad(Scl.scl(4f));
            button.getImageCell().grow();
            //原版 = button.getStyle().imageUpColor = team.color；libgdx 的 ImageButtonStyle 没有颜色字段
            //  → 直接给内部 Image 设色（Tex.whiteui 是纯白，tint 即阵营色）
            button.getImage().setColor(team.color);
            button.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    editor.drawTeam = selected;
                }
            });
            teamgroup.add(button);
            tools.add(button);

            if(i++ % 3 == 2) tools.row();
        }

        mid.add(tools).top().padBottom(Scl.scl(-6f));

        mid.row();

        Table brushBox = new Table();
        brushBox.setBackground(Tex.underline);
        brushBox.top();
        brushBox.add(new Label(bundle("$editor.brush"), Styles.defaultLabel));
        brushBox.row();
        final Slider slider = new Slider(0, MapEditor.brushSizes.length - 1, 1, false, Styles.defaultSlider);
        //原版 = slider.moved(f -> editor.brushSize = ...)；libgdx 没有 moved → ChangeListener
        slider.addListener(new ChangeListener(){
            @Override
            public void changed(ChangeEvent event, Actor actor){
                editor.brushSize = MapEditor.brushSizes[(int)slider.getValue()];
            }
        });
        for(int j = 0; j < MapEditor.brushSizes.length; j++){
            if(MapEditor.brushSizes[j] == editor.brushSize){
                slider.setValue(j);
            }
        }
        brushBox.add(slider).width(Scl.scl(size * 3f - 20f)).padTop(Scl.scl(4f));

        mid.add(brushBox).padTop(Scl.scl(5f)).growX().top();

        root.add(mid).pad(0f).left().growY();

        // ---- 中列：画布 ----
        root.add(view).grow();

        // ---- 右列：方块选择 ----
        Table rightColumn = new Table();
        addBlockSelection(rightColumn);
        root.add(rightColumn).right().growY();

        add(root).grow();
    }

    /**
     * 弹出某个工具的"备用模式"列表（对应原版 {@code button.clicked(configure, e -> {...})} 里那段）。
     * <p>原版浮层跟随按钮位置（{@code table.update(...)}）；这里覆写 {@code act()} 达到同样效果，
     * 并在对话框 {@code hidden} / 切换工具时统一移除。
     */
    private void showAltModes(final ImageButton button, final EditorTool tool){
        clearAltTable();

        Table table = new Table(){
            private final Vector2 v = new Vector2();

            @Override
            public void act(float delta){
                super.act(delta);
                v.set(0f, 0f);
                button.localToStageCoordinates(v);
                setPosition(v.x, v.y, Align.topLeft);
            }
        };
        table.setBackground(Styles.black9);
        table.defaults().size(Scl.scl(300f), Scl.scl(70f));

        for(int i = 0; i < tool.altModes.length; i++){
            final int mode = i;
            final String name = tool.altModes[i];

            TextButton b = new TextButton("", Styles.clearTogglet);
            b.clearChildren();
            b.left();

            Table inner = new Table();
            inner.left();
            inner.add(new Label(I18n.get("toolmode." + name, name), Styles.defaultLabel)).left();
            inner.row();
            Label desc = new Label(I18n.get("toolmode." + name + ".description", name + ".description"), Styles.defaultLabel);
            desc.setColor(Color.LIGHT_GRAY);
            inner.add(desc).left();

            b.add(inner).left().padLeft(Scl.scl(6f));
            b.setChecked(tool.mode == mode);
            b.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    tool.mode = (tool.mode == mode ? -1 : mode);
                    clearAltTable();
                }
            });

            table.add(b);
            table.row();
        }

        table.pack();
        addActor(table);
        altTable = table;
    }

    private void clearAltTable(){
        if(altTable != null){
            altTable.remove();
            altTable = null;
        }
    }

    private void doInput(){
        boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);

        if(ctrl){
            //alt mode select
            for(int i = 0; i < view.getTool().altModes.length + 1; i++){
                if(tapped(KeyCode.valueOf("NUM_" + (i + 1)))){
                    view.getTool().mode = i - 1;
                }
            }
        }else{
            //tool select
            for(int i = 0; i < EditorTool.values().length; i++){
                if(tapped(KeyCode.valueOf("NUM_" + (i + 1)))){
                    view.setTool(EditorTool.values()[i]);
                    break;
                }
            }
        }

        if(tapped(KeyCode.ESCAPE)){
            //原版 = if(!menu.isShown())；libgdx 的 FloatingDialog.hide() 会 remove() → 用 getStage() 判"是否在显示"
            if(menu.getStage() == null){
                menu.show();
            }
        }

        if(tapped(KeyCode.R)){
            editor.rotation = Mathf.mod(editor.rotation + 1, 4);
        }

        if(tapped(KeyCode.E)){
            editor.rotation = Mathf.mod(editor.rotation - 1, 4);
        }

        //ctrl keys (undo, redo, save)
        if(ctrl){
            if(tapped(KeyCode.Z)){
                editor.undo();
            }

            //more undocumented features, fantastic
            if(tapped(KeyCode.T)){
                //clears all 'decoration' from the map
                for(int x = 0; x < editor.width(); x++){
                    for(int y = 0; y < editor.height(); y++){
                        Tile tile = editor.tile(x, y);
                        if(tile == null) continue;
                        //本项目 block() 会解析多格链接 → 用 blockRaw()（原版 block() 就是瓦片自己存的值）
                        Block block = tile.blockRaw();
                        if(block.breakable && block instanceof Rock){
                            tile.setBlock(Blocks.air);
                            editor.renderer().updatePoint(x, y);
                        }

                        if(tile.overlay() != Blocks.air && tile.overlay() != Blocks.spawn){
                            tile.setOverlay(Blocks.air);
                            editor.renderer().updatePoint(x, y);
                        }
                    }
                }

                editor.flushOp();
            }

            if(tapped(KeyCode.Y)){
                editor.redo();
            }

            if(tapped(KeyCode.S)){
                save();
            }

            if(tapped(KeyCode.G)){
                view.setGrid(!view.isGrid());
            }
        }

        updateKeys();
    }

    private void tryExit(){
        if(!saved && Vars.ui != null){
            Vars.ui.showConfirm("$confirm", "$editor.unsaved", this::hide);
        }else{
            hide();
        }
    }

    private void addBlockSelection(Table table){
        Table content = new Table();
        pane = new ScrollPane(content, Styles.defaultPane);
        pane.setFadeScrollBars(false);
        pane.setOverscroll(true, false);
        //原版 = pane.exited(() -> { if(pane.hasScroll()) Core.scene.setScrollFocus(view); })
        //  —— libgdx 的 ScrollPane 没有 hasScroll() → 用 isScrollY()（近似：竖直滚动条是否可见）
        pane.addListener(new InputListener(){
            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor){
                if(pane.isScrollY() && scene != null){
                    scene.setScrollFocus(view);
                }
            }
        });
        ButtonGroup<ImageButton> group = new ButtonGroup<>();

        int i = 0;

        blocksOut.clear();
        com.badlogic.gdx.utils.Array<Block> blocks = Vars.content.blocks();
        for(int k = 0; k < blocks.size; k++){
            blocksOut.add(blocks.get(k));
        }
        blocksOut.sort((b1, b2) -> {
            int core = -Boolean.compare(b1 instanceof CoreBlock, b2 instanceof CoreBlock);
            if(core != 0) return core;
            int synth = Boolean.compare(b1.synthetic(), b2.synthetic());
            if(synth != 0) return synth;
            int ore = Boolean.compare(b1 instanceof OverlayFloor, b2 instanceof OverlayFloor);
            if(ore != 0) return ore;
            return Integer.compare(b1.id, b2.id);
        });

        for(int k = 0; k < blocksOut.size; k++){
            final Block block = blocksOut.get(k);
            TextureRegion region = block.icon(Cicon.medium);

            //刻意降级：原版 = Core.atlas.isFound(region)；libgdx 没有 isFound → 用判空近似
            if(region == null) continue;

            ImageButton button = new ImageButton(toggleStyle(new TextureRegionDrawable(region), Styles.clearTogglei)){
                @Override
                public void act(float delta){
                    super.act(delta);
                    setChecked(editor.drawBlock == block);
                }
            };
            //刻意降级：原版 = button.resizeImage(8 * 4f)；libgdx 没有该方法 → 用 getImageCell().size(...)
            button.getImageCell().size(Scl.scl(8 * 4f));
            button.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    editor.drawBlock = block;
                }
            });
            group.add(button);
            content.add(button).size(Scl.scl(50f));

            if(++i % 4 == 0){
                content.row();
            }
        }

        //原版 = group.getButtons().get(2).setChecked(true)
        if(group.getButtons().size > 2){
            group.getButtons().get(2).setChecked(true);
        }

        Table extra = new Table();
        extra.setBackground(Tex.underline);
        Label blockName = new Label("", Styles.defaultLabel){
            @Override
            public void act(float delta){
                super.act(delta);
                //原版 = labelWrap(Prov) 每帧求值
                setText(editor.drawBlock.localizedName());
            }
        };
        blockName.setWrap(true);
        blockName.setAlignment(Align.center);
        extra.add(blockName).width(Scl.scl(200f)).center();
        table.add(extra).growX();
        table.row();
        table.add(pane).growY().fillX();
    }

    public void dispose(){
        editor.renderer().dispose();
    }

    /**
     * 覆写每帧回调（原版 {@code update(() -> { if(Core.scene.getKeyboardFocus() instanceof Dialog
     * && != this) return; if(Core.scene.getKeyboardFocus() == this) doInput(); })}）。
     * <p>libgdx 的 Dialog 不会自动拿键盘焦点，所以这里把"焦点为 null"也当作"编辑器接管输入"
     * （全屏编辑器本来就该独占键盘），否则 {@link #doInput()} 永远不会被调用。
     */
    @Override
    public void act(float delta){
        super.act(delta);

        Stage stage = getStage();
        if(stage == null) return;

        if(stage.getKeyboardFocus() != null && stage.getKeyboardFocus() != this){
            return;
        }

        doInput();
    }

    @Override
    public void hide(){
        clearAltTable();
        super.hide();
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /** 复制一份样式并设好 imageUp（libgdx 的 ImageButton 没有 {@code (Drawable, style)} 构造器）。 */
    private static ImageButton.ImageButtonStyle toggleStyle(Drawable image, ImageButton.ImageButtonStyle style){
        ImageButton.ImageButtonStyle bs = new ImageButton.ImageButtonStyle(style);
        //imageUp 必须设：否则 hover/按下状态一变 updateImage() 会把 drawable 重置为 null
        bs.imageUp = image;
        return bs;
    }

    /** 对应 arc 的 {@code Table.addImageButton(icon, style, action)}。 */
    private static ImageButton iconButton(Drawable icon, ImageButton.ImageButtonStyle style, final Runnable action){
        ImageButton button = new ImageButton(toggleStyle(icon, style));
        button.getImage().setDrawable(icon);
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                action.run();
            }
        });
        return button;
    }

    /**
     * 图标按钮，且"可用/禁用 + 图标颜色"每帧同步。
     * <p>替代原版 arc 的 {@code setDisabled(Boolp)} + {@code Cell.update(() -> image.setColor(...))}。
     */
    private static ImageButton disabledIconButton(Drawable icon, ImageButton.ImageButtonStyle style,
                                                  final Runnable action, final Boolp enabled){
        final ImageButton button = new ImageButton(toggleStyle(icon, style)){
            @Override
            public void act(float delta){
                super.act(delta);
                boolean on = enabled.get();
                setDisabled(!on);
                getImage().setColor(on ? Color.WHITE : Color.GRAY);
            }
        };
        button.getImage().setDrawable(icon);
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                if(!button.isDisabled()) action.run();
            }
        });
        return button;
    }

    /** 对应 arc 的 {@code Core.input.keyTap(key)}（libgdx 没有 keyRelease → 自己记上一帧按下的键）。 */
    private boolean tapped(int key){
        return key != -1 && Gdx.input.isKeyPressed(key) && !lastKeys.contains(key);
    }

    /** 记录本帧按下的相关按键（供下一帧的 {@link #tapped(int)} 判断"刚按下"）。 */
    private void updateKeys(){
        lastKeys.clear();

        for(int i = 0; i < EditorTool.values().length + 1; i++){
            int k = KeyCode.valueOf("NUM_" + (i + 1));
            if(k != -1 && Gdx.input.isKeyPressed(k)) lastKeys.add(k);
        }

        int[] others = {KeyCode.ESCAPE, KeyCode.R, KeyCode.E, KeyCode.Z, KeyCode.T, KeyCode.Y, KeyCode.S, KeyCode.G};
        for(int k : others){
            if(Gdx.input.isKeyPressed(k)) lastKeys.add(k);
        }
    }

    /** 按名查地图（{@code Maps.byName} 的等价实现，避免 libgdx Array 的 lambda 重载歧义）。 */
    private static Map findMap(String name){
        com.badlogic.gdx.utils.Array<Map> all = Vars.maps.all();
        for(int i = 0; i < all.size; i++){
            Map map = all.get(i);
            if(map.name.equals(name)) return map;
        }
        return null;
    }

    /**
     * 把编辑器的瓦片落盘成一张地图。对应原版 {@code Maps.saveMap(tags)} / {@code MapIO.writeMap(file, map)}。
     *
     * <p><b>降级说明</b>：本项目 {@code Maps} 没有 {@code saveMap}、{@code MapIO} 没有 {@code writeMap}
     * → 用 {@code SaveIO.save(file, tags)}（本项目唯一的地图写盘路径，它写的正是 {@code Vars.world}，
     * 而 {@code MapEditor} 一直把编辑器瓦片同步在 {@code Vars.world} 里）。
     * 同时补上 {@code Maps.exportCurrent} 会写的 {@code spawns}/{@code teams} 统计 ——
     * 否则导出的地图在 {@code MapPlayDialog} 里所有模式都会因"没有出生点/队伍"被判为不可用。
     *
     * @param tags   地图标签（来自 {@code editor.getTags()}）
     * @param target 目标文件；为 null 时自动选（同名自定义图则覆盖，否则取一个不冲突的文件名）
     * @return 保存出的 {@link Map}；失败返回 null
     */
    private Map saveMap(StringMap tags, Fi target){
        String name = tags.get("name", "").trim();

        Map existing = findMap(name);
        File file = target != null ? target.file()
            : (existing != null ? existing.file : Vars.maps.nextMapFile());

        Map map = editor.createMap(new Fi(file));

        java.util.HashMap<String, String> out = new java.util.HashMap<>();
        for(com.badlogic.gdx.utils.ObjectMap.Entry<String, String> e : tags.entries()){
            out.put(e.key, e.value);
        }
        countTags(out);

        try{
            SaveIO.save(file, out);
        }catch(IOException e){
            Log.err(e);
            if(Vars.ui != null) Vars.ui.showException("$editor.errorsave", e);
            return null;
        }

        if(existing == null && !Vars.maps.all().contains(map, true)){
            Vars.maps.all().add(map);
        }
        //对应原版 Maps.saveMap 的收尾：落盘后立刻生成缩略图（F14；文件刚写出，离线读与内存瓦片等价，
        //编辑器世界此时就是 Vars.world，数据一致）
        Vars.maps.createNewPreview(map, null);
        return map;
    }

    /** 统计出生点/核心阵营并写进 tags（对应 {@code Maps.countWorld} + {@code encodeTeams} 的等价逻辑）。 */
    private void countTags(java.util.HashMap<String, String> out){
        IntSet teams = new IntSet();
        int spawns = 0;

        for(int x = 0; x < editor.width(); x++){
            for(int y = 0; y < editor.height(); y++){
                Tile tile = editor.tile(x, y);
                if(tile == null) continue;
                //本项目 block() 会解析多格链接（核心的卫星格也会命中）→ IntSet 去重，结果与原版一致
                if(tile.block() instanceof CoreBlock) teams.add(tile.getTeam().id);
                if(tile.overlay() == Blocks.spawn) spawns++;
            }
        }

        out.put("spawns", String.valueOf(spawns));

        StringBuilder sb = new StringBuilder();
        IntSet.IntSetIterator it = teams.iterator();
        while(it.hasNext){
            if(sb.length() > 0) sb.append(',');
            sb.append(it.next());
        }
        out.put("teams", sb.toString());
    }

    /**
     * 打开文件选择器。
     * <p>已接线：委托给 {@link FileChooser#showFileChooser(boolean, String, Cons)}，
     * 它就是原版 {@code Platform.showFileChooser(open, extension, cons)} 的 default 实现
     * （含"保存模式强制补扩展名"），本项目只是从"接口 default 方法"改成"静态工具方法"。
     */
    private static void showFileChooser(boolean open, String extension, Cons<Fi> result){
        FileChooser.showFileChooser(open, extension, result);
    }
}
