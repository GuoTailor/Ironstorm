package com.phoenix.game.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.phoenix.game.Vars;
import com.phoenix.game.func.Boolp;
import com.phoenix.game.func.Cons;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.input.KeyCode;
import com.phoenix.game.ui.Collapser;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.editor.MapEditorDialog;
import com.phoenix.game.ui.dialogs.AboutDialog;
import com.phoenix.game.ui.dialogs.AdminsDialog;
import com.phoenix.game.ui.dialogs.BansDialog;
import com.phoenix.game.ui.dialogs.ColorPicker;
import com.phoenix.game.ui.dialogs.ContentInfoDialog;
import com.phoenix.game.ui.dialogs.ControlsDialog;
import com.phoenix.game.ui.dialogs.CustomGameDialog;
import com.phoenix.game.ui.dialogs.DatabaseDialog;
import com.phoenix.game.ui.dialogs.DeployDialog;
import com.phoenix.game.ui.dialogs.DiscordDialog;
import com.phoenix.game.ui.dialogs.FloatingDialog;
import com.phoenix.game.ui.dialogs.GameOverDialog;
import com.phoenix.game.ui.dialogs.HostDialog;
import com.phoenix.game.ui.dialogs.JoinDialog;
import com.phoenix.game.ui.dialogs.LanguageDialog;
import com.phoenix.game.ui.dialogs.LoadDialog;
import com.phoenix.game.ui.dialogs.MapsDialog;
import com.phoenix.game.ui.dialogs.ModsDialog;
import com.phoenix.game.ui.dialogs.PausedDialog;
import com.phoenix.game.ui.dialogs.SchematicsDialog;
import com.phoenix.game.ui.dialogs.SettingsMenuDialog;
import com.phoenix.game.ui.dialogs.TechTreeDialog;
import com.phoenix.game.ui.dialogs.TraceDialog;
import com.phoenix.game.ui.fragments.LoadingFragment;
import com.phoenix.game.ui.fragments.PlayerListFragment;
import com.phoenix.game.ui.fragments.ScriptConsoleFragment;
import com.phoenix.game.util.Log;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * UI 门面（对应原版 {@code Vars.ui}）。参照 Mindustry mindustry.core.UI 移植的<b>最小可用版</b>。
 *
 * <p>原版 UI 是 514 行、实现了 {@code ApplicationListener}/{@code Loadable} 的全量 UI 根
 * （负责建 {@code Core.scene}、加载字体/图集/样式、挂 menuGroup/hudGroup、驱动 Stage、
 * 持有全部对话框与 fragment）。本项目<b>没有</b>那套加载体系，也没有全局 Stage，
 * 所以本类只保留「对话框门面 + 一批 showXxx 便捷弹窗」这一层，其余全部裁掉。
 *
 * <h3>相对原版裁剪了什么（逐项）</h3>
 * <ul>
 *   <li><b>不实现</b> {@code ApplicationListener} / {@code Loadable}；没有
 *       {@code loadAsync/loadSync/getDependencies/update/resize/dispose}。
 *       原版这些方法负责建 Stage、加载 {@code Tex/Icon/Styles}、注册 {@code Dialog.setShowAction}、
 *       新建光标等 —— 本项目这些都在 {@code ClientLauncher} 里做完了。</li>
 *   <li><b>没有 {@code Core.scene}</b>：原版所有"往屏幕上贴个浮动 label"的方法都往 {@code Core.scene} 加 Actor。
 *       本项目用 {@link FloatingDialog#scene}（启动器注入的全局 Stage）；拿不到时记 {@code Log.warn} 并直接返回，
 *       不 NPE。</li>
 *   <li><b>没有 {@code Core.assets}</b>：原版 {@code loadSync} 从 AssetManager 里取字体，本项目字体在
 *       {@code core/Fonts} 里由 {@code ClientLauncher} 直接加载。</li>
 *   <li><b>没有 {@code menuGroup}/{@code hudGroup}/{@code menufrag}/{@code chatfrag}/{@code minimapfrag} 字段</b>：
 *       这几个 fragment 类在本项目<b>都存在</b>（{@code ui/fragments/*}），但实例由 {@code ClientLauncher}
 *       私有持有并直接 build 到 stage 上（HUD 另经 {@code Vars.hud} 暴露），本类不再声明这些字段。</li>
 *   <li><b>没有 {@code drillCursor}/{@code unloadCursor}/{@code packer}</b>（刻意降级）：
 *       libgdx 无 {@code Core.graphics.newCursor} 式的运行时光标设施，本项目也没有运行时图集打包管线
 *       （mod 贴图打包未启用）→ 这三个原版字段不声明。{@code editor}/{@code deploy}/{@code bans}/{@code admins}
 *       等对话框字段则<b>已声明</b>（见下方字段区）。</li>
 *   <li><b>没有 {@code showExceptions(String, String...)}</b>（原版的多段错误列表弹窗）：依赖
 *       {@code Strings.parseException}（本项目 {@code core/Strings} 只有 {@code fixed/autoFixed/format}）
 *       → 刻意不落地。</li>
 * </ul>
 *
 * <h3>为什么 showXxx 不用 {@code FloatingDialog.textButton(...)}</h3>
 * 原版这些方法用 {@code new Dialog(t){{ cont.addButton(...); }}} 的 arc 写法，本项目的对应物是
 * {@link FloatingDialog}。但 {@code FloatingDialog} 的 {@code cont/buttons} 与
 * {@code textButton/imageTextButton/bundle} 都是 {@code protected}，而本类在
 * {@code com.phoenix.game.core} 包、**不是** {@code FloatingDialog} 的子类，
 * 所以它们在本类里<b>不可见</b>。因此这里：
 * <ul>
 *   <li>取表一律用 libgdx 的 {@code dialog.getContentTable()/getButtonTable()}（public）；</li>
 *   <li>按钮用本类自己的 {@link #textButton}/{@link #imageTextButton}（与 {@code FloatingDialog}
 *       里那两个 protected 辅助逐行等价，只是可见性不同）；</li>
 *   <li>取 bundle 文本用本类的 {@link #bundle(String)}。</li>
 * </ul>
 *
 * <h3>已知偏离（刻意降级 / 等价替代，不再是待办）</h3>
 * <ul>
 *   <li>原版字段中<b>未声明</b>的只剩三类（其余同名对话框字段均已声明，见下方字段区）：
 *       {@code menufrag}/{@code chatfrag}/{@code minimapfrag}（类存在、实例由 {@code ClientLauncher} 私有持有）、
 *       {@code menuGroup}/{@code hudGroup}（本项目没有这两层容器）、
 *       {@code drillCursor}/{@code unloadCursor}（libgdx 无运行时光标设施）、{@code packer}（无运行时图集打包）。</li>
 *   <li>刻意降级 {@code hudfrag}：本项目用 {@code Vars.hud}（HudFragment 实例由 ClientLauncher 持有并赋值）。</li>
 *   <li>{@code getIcon(String)}：原版查 {@code Icon.icons} 映射表（键为裸名），本项目生成的 {@code Icon}
 *       只有静态字段、没有映射表 → 改为按 skin 名 {@code "icon-" + name} 查（本批修正了
 *       {@code has(..., Drawable.class)} 恒为 false 的 bug，改用 {@code TextureRegion.class} 判定）。</li>
 *   <li>刻意降级 {@code showTextInput} 的 mobile 分支：原版走 {@code Core.input.getTextInput(TextInput)}
 *       （arc 的软键盘输入框），本项目没有该设施 → 一律走桌面分支（带 TextField 的对话框）。</li>
 *   <li>{@code formatAmount(int)}：原版用 {@code unit.thousands}/{@code unit.millions}；本批已补这两个键
 *       （见 {@link #formatAmount}）。</li>
 *   <li>等价替代 {@code showInfoFade/showInfoToast/showInfoPopup/showLabel} 里原版的
 *       {@code table.update(() -> { if(state.is(State.menu)) table.remove(); })}（arc 的每帧回调）
 *       libgdx 没有 → 用匿名 Table 覆写 {@code act(float)} 达到同样效果。</li>
 *   <li>等价替代 {@code showLabel} 的世界坐标投影：原版 {@code Core.camera.project(worldx, worldy)} 的 y 向上，
 *       libgdx {@code Camera.project(Vector3)} 的 y 向下 → 这里做 {@code stageHeight - y} 翻转。</li>
 *   <li>等价替代 {@code showException}：原版用 {@code Strings.getFinalMesage(exc)} 与
 *       {@code Strings.parseException(exc, true)}（本项目 {@code core/Strings} 没有这两个方法）
 *       → 就地实现 {@link #describe(Throwable)} / {@link #stackTrace(Throwable)}。</li>
 *   <li>刻意降级 {@code showException} 里的 {@code "$details"} 折叠按钮高亮：arc 的 {@code Cell.checked(Boolp)}
 *       能跟着 Collapser 状态高亮，libgdx 没有 → 按钮不高亮。</li>
 *   <li>刻意降级 {@code showSmall}：原版会往 titleTable 再补一条 accent 分隔线；本项目
 *       {@link FloatingDialog} 的构造器已经加过一条，再加会重复 → 去掉。</li>
 * </ul>
 *
 * <p><b>接线</b>：主会话在 {@code ClientLauncher} 里（{@code Styles.load()} 与
 * {@code FloatingDialog.scene = stage;} 之后）加一行 {@code Vars.ui = new UI();} 即可 ——
 * 本类的构造器直接调用 {@link #init()}，构造完所有字段就都是可用的。
 */
public class UI{
    //刻意降级：原版 public static PixmapPacker packer;（本项目无运行时图集打包管线）
    //刻意降级：原版 menufrag/chatfrag/minimapfrag 字段（对应 ui/fragments/{Menu,Chat,Minimap}Fragment，
    //          类都存在，但实例由 ClientLauncher 私有持有并直接 build 到 stage）
    //刻意降级：原版 WidgetGroup menuGroup, hudGroup;（本项目没有这两层容器，HUD/菜单直接挂 stage）
    //注：原版 load/join/deploy/discord/host/bans/admins/editor/paused/maps 等对话框字段在本类下方**已声明**
    //刻意降级：原版 Cursor drillCursor, unloadCursor;（libgdx 无 Core.graphics.newCursor 式设施）
    //刻意降级：原版 public HudFragment hudfrag;（本项目用 Vars.hud 代替）

    /** 加载遮罩（对应原版 {@code loadfrag}）。 */
    public LoadingFragment loadfrag;
    /** 玩家列表（对应原版 {@code listfrag}）。 */
    public PlayerListFragment listfrag;
    /** 脚本控制台（对应原版 {@code scriptfrag}）。 */
    public ScriptConsoleFragment scriptfrag;

    /** 关于对话框。 */
    public AboutDialog about;
    /** 设置菜单。 */
    public SettingsMenuDialog settings;
    /** 按键绑定。 */
    public ControlsDialog controls;
    /** 语言选择。 */
    public LanguageDialog language;
    /** 玩家追踪信息。 */
    public TraceDialog traces;
    /** 内容数据库。 */
    public DatabaseDialog database;
    /** 内容详情（注意：本项目的 ContentInfoDialog 不是 FloatingDialog，见其类注释）。 */
    public ContentInfoDialog content;
    /** 蓝图库。 */
    public SchematicsDialog schematics;
    /** 模组管理。 */
    public ModsDialog mods;
    /** 科技树。 */
    public TechTreeDialog tech;
    /** 取色器。 */
    public ColorPicker picker;
    /** 游戏结束（原版字段名就是 restart）。 */
    public GameOverDialog restart;
    /** 自定义游戏。 */
    public CustomGameDialog custom;
    /** Discord 社区。 */
    public DiscordDialog discord;
    /** 开服。 */
    public HostDialog host;
    /** 封禁管理。 */
    public BansDialog bans;
    /** 管理员管理。 */
    public AdminsDialog admins;
    /** 读档。 */
    public LoadDialog load;
    /** 加入服务器。 */
    public JoinDialog join;
    /** 区域部署（战役）。 */
    public DeployDialog deploy;
    /**
     * 地图编辑器（对应原版 {@code UI.editor}）。
     * <p><b>构造时机</b>：{@link MapEditorDialog} 的构造器会 new {@code MapEditor}（读 {@code Blocks.stone}
     * 与 {@code Core.atlas}）并 new {@code WaveInfoDialog}（读 {@code UnitTypes.dagger}）
     * → 必须在 {@code Vars.content.load()} 之后构造；{@link #init()} 正是那个时机。
     */
    public MapEditorDialog editor;
    /** 暂停菜单（对应原版 {@code UI.paused}）。 */
    public PausedDialog paused;
    /** 地图列表（对应原版 {@code UI.maps}）。 */
    public MapsDialog maps;

    public UI(){
        //原版构造器只做 Fonts.loadFonts()，对话框在 init() 里建（由加载器调用）。
        //本项目没有加载器 → 构造器直接调 init()，这样 Vars.ui = new UI(); 之后字段即可用。
        init();
    }

    /** 构造已移植的对话框/fragment 实例（对应原版 {@code UI.init()} 里建实例的那一段）。 */
    public void init(){
        loadfrag = new LoadingFragment();
        listfrag = new PlayerListFragment();
        scriptfrag = new ScriptConsoleFragment();

        picker = new ColorPicker();
        controls = new ControlsDialog();
        language = new LanguageDialog();
        database = new DatabaseDialog();
        settings = new SettingsMenuDialog();
        about = new AboutDialog();
        traces = new TraceDialog();
        content = new ContentInfoDialog();
        tech = new TechTreeDialog();
        mods = new ModsDialog();
        schematics = new SchematicsDialog();
        restart = new GameOverDialog();
        custom = new CustomGameDialog();
        discord = new DiscordDialog();
        host = new HostDialog();
        bans = new BansDialog();
        admins = new AdminsDialog();
        load = new LoadDialog();
        join = new JoinDialog();
        deploy = new DeployDialog();
        //地图编辑器：构造时会给 Vars.editor 赋值（对应原版 ui.editor.editor）
        editor = new MapEditorDialog();
        //地图列表 / 暂停菜单（都在 content.load() 之后构造，见各自类注释）
        maps = new MapsDialog();
        paused = new PausedDialog();

        //刻意降级：原版 init() 剩下的大段内容都依赖未移植设施，这里整段裁掉：
        //  menuGroup/hudGroup = new WidgetGroup() + setFillParent + touchable(childrenOnly) + visible(...)
        //  Core.scene.add(menuGroup/hudGroup)、hudfrag.build(hudGroup)、menufrag.build(menuGroup)、
        //  chatfrag/minimapfrag/listfrag/scriptfrag.build(hudGroup)、loadfrag.build(group)、
        //  new FadeInFragment().build(group)
        //  —— 本项目 HUD/菜单由 ClientLauncher 直接 build 到 stage 上，没有 menuGroup/hudGroup 这两层容器。
    }

    // ------------------------------------------------------------------
    // 图标 / 文本工具
    // ------------------------------------------------------------------

    /**
     * 取图标（对应原版 {@code UI.getIcon(String)}）。
     * <p>原版查 {@code Icon.icons} 映射表（键是裸名，如 {@code "pencil"}）；本项目生成的 {@code Icon}
     * 只有静态字段、没有映射表 → 改成按 skin 名 {@code "icon-" + kebab(name)} 查：camelCase 先还原成 kebab-case
     * （资源生成器把 {@code icon-mode-attack-small.png} 命名成 {@code Icon.modeAttackSmall}，两者互逆）。
     * <p><b>本批修正的真 bug</b>：本项目 {@code Core.skin = new Skin(Core.atlas)}，libgdx 的
     * {@code Skin(TextureAtlas)} 只把区域注册成 {@code TextureRegion.class}（见 {@code Skin.addRegions}），
     * 所以原先的 {@code has(key, Drawable.class)} <b>恒为 false</b>、本方法永远返回 {@code Icon.missing}。
     * 现改为判定 {@code TextureRegion.class} 再 {@code getDrawable}（它内部会把区域包成 Drawable）。
     * <p>回退用 {@code Icon.missing}（原版回退 {@code Core.atlas.getDrawable("error")}；本项目图集里
     * <b>确有</b> {@code error} 区域，但 {@code icon-missing} 才是本项目既有的"缺图"约定）。
     * @param name 裸图标名（<b>不含</b> {@code icon-} 前缀），如调用方传的 {@code tool.name()} = {@code "pencil"}
     */
    public Drawable getIcon(String name){
        if(Core.skin != null && name != null && !name.isEmpty()){
            //⚠ 生成器把 icon-mode-attack-small.png 映射成 Icon.modeAttackSmall（camelCase），
            //  运行时没有反向映射表 → 直接拼 "icon-" + camelCase 查不到多词图标（T10 只修了 Drawable/TextureRegion 判定，
            //  这个 camel→kebab 是残留缺口）。这里先还原成 kebab-case 再查，失败再回退原样名。
            String kebab = camelToKebab(name);
            String key = "icon-" + kebab;
            if(Core.skin.has(key, TextureRegion.class)){
                return Core.skin.getDrawable(key);
            }
            key = "icon-" + name;
            if(Core.skin.has(key, TextureRegion.class)){
                return Core.skin.getDrawable(key);
            }
        }
        return Icon.missing;
    }

    /** camelCase → kebab-case（{@code "modeAttackSmall"} → {@code "mode-attack-small"}），与资源生成器的命名规则互逆。 */
    private static String camelToKebab(String s){
        StringBuilder sb = new StringBuilder(s.length() + 4);
        for(int i = 0; i < s.length(); i++){
            char c = s.charAt(i);
            if(Character.isUpperCase(c)){
                if(i > 0) sb.append('-');
                sb.append(Character.toLowerCase(c));
            }else{
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 数字格式化（带 k/M 后缀），对应原版 {@code UI.formatAmount(int)}。
     * <p>本批已把 {@code unit.thousands = k} / {@code unit.millions = mil} 补进
     * {@code assets/i18n/bundle.properties} 与 {@code bundle_zh_CN.properties}（取值同原版 bundle），
     * {@link I18n#get(String, String)} 现在取到真实键值，fallback 仅作兜底。
     */
    public String formatAmount(int number){
        if(number >= 1000000){
            return Strings.fixed(number / 1000000f, 1) + "[gray]" + I18n.get("unit.millions", "M") + "[]";
        }else if(number >= 10000){
            return number / 1000 + "[gray]" + I18n.get("unit.thousands", "k") + "[]";
        }else if(number >= 1000){
            return Strings.fixed(number / 1000f, 1) + "[gray]" + I18n.get("unit.thousands", "k") + "[]";
        }else{
            return number + "";
        }
    }

    // ------------------------------------------------------------------
    // 加载遮罩
    // ------------------------------------------------------------------

    public void loadAnd(Runnable call){
        loadAnd("$loading", call);
    }

    /**
     * 显示加载遮罩 → 执行任务 → 收起遮罩（对应原版 {@code UI.loadAnd}）。
     * <p>原版用 {@code Time.runTask(7f, ...)}，本项目对应物是 {@link Time#run(float, Runnable)}。
     */
    public void loadAnd(String text, Runnable call){
        if(loadfrag == null){
            //没有遮罩就直接跑，避免任务被静默丢弃
            call.run();
            return;
        }
        loadfrag.show(bundle(text));
        Time.run(7f, () -> {
            call.run();
            loadfrag.hide();
        });
    }

    // ------------------------------------------------------------------
    // 文本输入
    // ------------------------------------------------------------------

    /**
     * 弹出文本输入框（对应原版 {@code UI.showTextInput}）。
     * <p>刻意降级：原版有 mobile 分支（{@code Core.input.getTextInput(TextInput)} 调系统软键盘），
     * 本项目没有该设施 → 一律走桌面分支（带 TextField 的对话框）。
     */
    public void showTextInput(String titleText, String dtext, int textLength, String def, boolean inumeric, Cons<String> confirmed){
        FloatingDialog dialog = new FloatingDialog(titleText);

        Table content = dialog.getContentTable();
        content.pad(Scl.scl(30f));
        content.add(new Label(bundle(dtext), Styles.defaultLabel)).padRight(Scl.scl(6f));

        //等价替代：原版用 arc 的 TextFieldFilter.digitsOnly 做数字过滤；libgdx 的 TextFieldFilter 也是函数式接口，
        //     但本项目没有 digitsOnly 常量 → 用 lambda 自己判（与原版 (f, c) -> true 的语义等价）
        final TextField field = new TextField(def == null ? "" : def, Styles.defaultField);
        field.setMaxLength(textLength);
        if(inumeric){
            field.setTextFieldFilter((f, c) -> Character.isDigit(c) || c == '.');
        }
        content.add(field).size(Scl.scl(330f), Scl.scl(50f));

        Table btns = dialog.getButtonTable();
        btns.defaults().size(Scl.scl(120f), Scl.scl(54f)).pad(Scl.scl(4f));

        final TextButton ok = textButton("$ok", null, () -> {
            confirmed.get(field.getText());
            dialog.hide();
        });
        //原版：.disabled(b -> field.getText().isEmpty())（arc 的 Cell 动态禁用）
        //libgdx 的 Cell 没有 disabled(Boolp) → 手动同步按钮的禁用态
        ok.setDisabled(field.getText().isEmpty());
        field.addListener(new ChangeListener(){
            @Override
            public void changed(ChangeEvent event, Actor actor){
                ok.setDisabled(field.getText().isEmpty());
            }
        });

        btns.add(ok);
        btns.add(textButton("$cancel", null, dialog::hide));

        //原版用 keyDown(ENTER, ...) 提交、keyDown(ESCAPE/BACK, ...) 关闭；
        //libgdx 的 Dialog.key(int, Object) 只做"按键即关闭"，所以 ENTER 走自建监听器
        dialog.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener(){
            @Override
            public boolean keyDown(InputEvent event, int keycode){
                if(keycode == KeyCode.ENTER){
                    String text = field.getText();
                    if(!text.isEmpty()){
                        confirmed.get(text);
                        dialog.hide();
                    }
                    return true;
                }
                return false;
            }
        });
        dialog.key(KeyCode.ESCAPE, null);
        dialog.key(KeyCode.BACK, null);

        dialog.show();

        //原版：Core.scene.setKeyboardFocus(field) + field.setCursorPosition(def.length())
        if(FloatingDialog.scene != null){
            FloatingDialog.scene.setKeyboardFocus(field);
        }
        field.setCursorPosition(def == null ? 0 : def.length());
    }

    public void showTextInput(String title, String text, String def, Cons<String> confirmed){
        showTextInput(title, text, 32, def, confirmed);
    }

    public void showTextInput(String titleText, String text, int textLength, String def, Cons<String> confirmed){
        showTextInput(titleText, text, textLength, def, false, confirmed);
    }

    /** 便捷重载：只有标题、说明、确认回调（对应原版不存在的额外重载，供调用方少写参数）。 */
    public void showTextInput(String title, String text, Cons<String> confirmed){
        showTextInput(title, text, 32, "", confirmed);
    }

    // ------------------------------------------------------------------
    // 屏幕上的浮动文本
    // ------------------------------------------------------------------

    /** 屏幕顶部淡出提示（对应原版 {@code UI.showInfoFade}）。 */
    public void showInfoFade(String info){
        Stage stage = requireScene("showInfoFade");
        if(stage == null) return;

        Table table = new Table();
        table.setFillParent(true);
        table.setTouchable(Touchable.disabled);
        table.addAction(Actions.sequence(Actions.fadeOut(7f, Interpolation.fade), Actions.removeActor()));
        table.top();
        table.add(new Label(bundle(info), Styles.outlineLabel)).padTop(Scl.scl(10f));
        stage.addActor(table);
    }

    /** 屏幕顶部淡出提示（带时长，对应原版 {@code UI.showInfoToast}）。 */
    public void showInfoToast(String info, float duration){
        Stage stage = requireScene("showInfoToast");
        if(stage == null) return;

        //等价替代：原版用 table.update(() -> { if(state.is(State.menu)) table.remove(); })（arc 的每帧回调）
        //     libgdx 没有 → 覆写 act() 达到同样效果
        Table table = new Table(){
            @Override
            public void act(float delta){
                super.act(delta);
                if(Vars.state.isMenu()) remove();
            }
        };
        table.setFillParent(true);
        table.setTouchable(Touchable.disabled);
        table.addAction(Actions.sequence(
            Actions.delay(duration * 0.9f),
            Actions.fadeOut(duration * 0.1f, Interpolation.fade),
            Actions.removeActor()));
        table.top();

        Table inner = new Table();
        inner.setBackground(Styles.black3);
        inner.pad(Scl.scl(4f));
        inner.add(new Label(bundle(info), Styles.outlineLabel));
        table.add(inner).padTop(Scl.scl(10f));
        stage.addActor(table);
    }

    /**
     * 屏幕上某个位置显示一段时间（不淡出），对应原版 {@code UI.showInfoPopup}。
     * @param align libgdx 的 {@link Align} 常量
     */
    public void showInfoPopup(String info, float duration, int align, int top, int left, int bottom, int right){
        Stage stage = requireScene("showInfoPopup");
        if(stage == null) return;

        Table table = new Table(){
            @Override
            public void act(float delta){
                super.act(delta);
                if(Vars.state.isMenu()) remove();
            }
        };
        table.setFillParent(true);
        table.setTouchable(Touchable.disabled);
        table.addAction(Actions.sequence(Actions.delay(duration), Actions.removeActor()));
        table.align(align);

        Table inner = new Table();
        inner.setBackground(Styles.black3);
        inner.pad(Scl.scl(4f));
        inner.add(new Label(bundle(info), Styles.outlineLabel));
        table.add(inner).pad(top, left, bottom, right);
        stage.addActor(table);
    }

    /**
     * 世界坐标处的标签（不淡出），对应原版 {@code UI.showLabel}。
     * <p>原版把外层 Table 加到 {@code Core.scene.root} 的 0 号位置（"保证在最底层"），
     * 这里照做：{@code stage.getRoot().addActorAt(0, table)}。
     * <p>等价替代：原版 {@code Core.camera.project(worldx, worldy)} 返回 y 向上的屏幕坐标；
     * libgdx 的 {@code Camera.project(Vector3)} 返回的 y 向下 → 这里用
     * {@code stageHeight - y} 翻转。
     */
    public void showLabel(String info, float duration, float worldx, float worldy){
        Stage stage = requireScene("showLabel");
        if(stage == null) return;

        //标签直接挂在 stage 上（不是 Table 的 cell）——这样 setPosition 不会被父表布局覆盖。
        //原版把外层 Table 作为 cell 加进去再 setPosition，在 libgdx 里会被 Table.layout 重置。
        Table table = new Table(){
            @Override
            public void act(float delta){
                super.act(delta);
                if(Vars.state.isMenu()){
                    remove();
                    return;
                }
                //每帧把世界坐标投影成屏幕坐标（对应原版的 table.update(...)）
                if(Core.camera != null){
                    Vector3 v = new Vector3(worldx, worldy, 0f);
                    Core.camera.project(v);
                    setPosition(v.x, Core.graphics.getHeight() - v.y, Align.center);
                }
            }
        };
        table.setTouchable(Touchable.disabled);
        table.setBackground(Styles.black3);
        table.pad(Scl.scl(4f));
        table.add(new Label(bundle(info), Styles.outlineLabel));
        table.pack();
        table.addAction(Actions.sequence(Actions.delay(duration), Actions.removeActor()));

        //放在最底层（对应原版 Core.scene.root.addChildAt(0, table)）
        stage.getRoot().addActorAt(0, table);
    }

    // ------------------------------------------------------------------
    // 信息 / 错误弹窗
    // ------------------------------------------------------------------

    public void showInfo(String info){
        FloatingDialog dialog = new FloatingDialog("");

        Table content = dialog.getContentTable();
        Label label = new Label(bundle(info), Styles.defaultLabel);
        label.setWrap(true);
        label.setAlignment(Align.center);
        content.add(label).width(Scl.scl(400f));

        Table btns = dialog.getButtonTable();
        btns.defaults().size(Scl.scl(90f), Scl.scl(50f)).pad(Scl.scl(4f));
        btns.add(textButton("$ok", null, dialog::hide));

        dialog.show();
    }

    public void showErrorMessage(String text){
        FloatingDialog dialog = new FloatingDialog("");

        Table content = dialog.getContentTable();
        content.pad(Scl.scl(15f));
        content.add(new Label(bundle("$error.title"), Styles.defaultLabel));
        content.row();
        content.add(divider(Color.SCARLET)).width(Scl.scl(300f)).pad(Scl.scl(2f)).height(Scl.scl(4f));
        content.row();

        Label label = new Label(bundle(text), Styles.defaultLabel);
        label.setWrap(true);
        label.setAlignment(Align.center);
        content.add(label).pad(Scl.scl(2f)).growX();
        content.row();
        content.add(textButton("$ok", null, dialog::hide)).size(Scl.scl(120f), Scl.scl(50f)).pad(Scl.scl(4f));

        dialog.show();
    }

    public void showException(Throwable t){
        showException("", t);
    }

    public void showException(String text, Throwable exc){
        if(loadfrag != null) loadfrag.hide();

        FloatingDialog dialog = new FloatingDialog("");

        //等价替代：原版：String message = Strings.getFinalMesage(exc)（core/Strings 没有该方法）→ 就地实现
        String message = describe(exc);

        Table content = dialog.getContentTable();
        content.pad(Scl.scl(15f));
        content.add(new Label(bundle("$error.title"), Styles.defaultLabel)).colspan(2);
        content.row();
        content.add(divider(Color.SCARLET)).width(Scl.scl(300f)).pad(Scl.scl(2f)).colspan(2).height(Scl.scl(4f));
        content.row();

        Label msg = new Label(bundle(text) + (message == null ? "" : "\n[lightgray](" + message + ")"), Styles.defaultLabel);
        msg.setWrap(true);
        msg.setAlignment(Align.center);
        content.add(msg).colspan(2).growX();
        content.row();

        //等价替代：原版：new Collapser(base -> base.pane(t -> t.margin(14f).add(Strings.parseException(exc, true))...))
        //     —— arc 的 pane(Cons) / margin / Strings.parseException 都没有 → 自建 ScrollPane + 手写堆栈文本
        Collapser col = new Collapser(base -> {
            Table t = new Table();
            t.pad(Scl.scl(14f));
            t.left();
            Label trace = new Label(stackTrace(exc), Styles.defaultLabel);
            trace.setColor(Color.LIGHT_GRAY);
            trace.setWrap(true);
            t.add(trace).growX().left();
            ScrollPane pane = new ScrollPane(t, Styles.defaultPane);
            pane.setScrollingDisabled(true, false);
            base.add(pane).grow();
        }, true);

        //刻意降级：原版：cont.addButton("$details", Styles.togglet, col::toggle).checked(b -> !col.isCollapsed())
        //     —— libgdx 的 Cell 没有 checked(Boolp)，按钮不随折叠状态高亮
        content.add(textButton("$details", Styles.togglet, col::toggle)).size(Scl.scl(180f), Scl.scl(50f)).fillX().right();
        content.add(textButton("$ok", null, dialog::hide)).size(Scl.scl(100f), Scl.scl(50f)).fillX().left();
        content.row();
        content.add(col).colspan(2).pad(Scl.scl(2f));

        dialog.show();
    }

    // ------------------------------------------------------------------
    // 确认 / 提示类弹窗
    // ------------------------------------------------------------------

    public void showConfirm(String title, String text, Runnable confirmed){
        showConfirm(title, text, null, confirmed);
    }

    /**
     * 确认框（对应原版 {@code UI.showConfirm}）。
     * @param hide 非 null 时每帧检查，返回 true 就自动关闭（原版 {@code dialog.update(hide)}）
     */
    public void showConfirm(String title, String text, Boolp hide, Runnable confirmed){
        //等价替代：原版 dialog.update(Boolp)：libgdx 的 Dialog 没有每帧回调 → 覆写 act()
        FloatingDialog dialog = new FloatingDialog(title){
            @Override
            public void act(float delta){
                super.act(delta);
                if(hide != null && hide.get()){
                    hide();
                }
            }
        };

        Table content = dialog.getContentTable();
        Label label = new Label(bundle(text), Styles.defaultLabel);
        label.setWrap(true);
        label.setAlignment(Align.center);
        content.add(label).width(Scl.scl(Vars.mobile ? 400f : 500f)).pad(Scl.scl(4f));

        Table btns = dialog.getButtonTable();
        btns.defaults().size(Scl.scl(200f), Scl.scl(54f)).pad(Scl.scl(2f));
        dialog.setFillParent(false);
        btns.add(textButton("$cancel", null, dialog::hide));
        btns.add(textButton("$ok", null, () -> {
            dialog.hide();
            confirmed.run();
        }));

        dialog.key(KeyCode.ESCAPE, null);
        dialog.key(KeyCode.BACK, null);
        dialog.show();
    }

    /** 自定义按钮文案的确认框（对应原版 {@code UI.showCustomConfirm}）。 */
    public void showCustomConfirm(String title, String text, String yes, String no, Runnable confirmed, Runnable denied){
        FloatingDialog dialog = new FloatingDialog(title);

        Table content = dialog.getContentTable();
        Label label = new Label(bundle(text), Styles.defaultLabel);
        label.setWrap(true);
        label.setAlignment(Align.center);
        content.add(label).width(Scl.scl(Vars.mobile ? 400f : 500f)).pad(Scl.scl(4f));

        Table btns = dialog.getButtonTable();
        btns.defaults().size(Scl.scl(200f), Scl.scl(54f)).pad(Scl.scl(2f));
        dialog.setFillParent(false);
        btns.add(textButton(no, null, () -> {
            dialog.hide();
            denied.run();
        }));
        btns.add(textButton(yes, null, () -> {
            dialog.hide();
            confirmed.run();
        }));

        dialog.key(KeyCode.ESCAPE, null);
        dialog.key(KeyCode.BACK, null);
        dialog.show();
    }

    /** 只有"确定"按钮的提示框（对应原版 {@code UI.showOkText}）。 */
    public void showOkText(String title, String text, Runnable confirmed){
        FloatingDialog dialog = new FloatingDialog(title);

        Table content = dialog.getContentTable();
        Label label = new Label(bundle(text), Styles.defaultLabel);
        label.setWrap(true);
        label.setAlignment(Align.center);
        content.add(label).width(Scl.scl(500f)).pad(Scl.scl(4f));

        Table btns = dialog.getButtonTable();
        btns.defaults().size(Scl.scl(200f), Scl.scl(54f)).pad(Scl.scl(2f));
        dialog.setFillParent(false);
        btns.add(textButton("$ok", null, () -> {
            dialog.hide();
            confirmed.run();
        }));

        dialog.show();
    }

    public void showText(String titleText, String text){
        showText(titleText, text, Align.center);
    }

    /** 居中/对齐可配的纯文本提示（对应原版 {@code UI.showText}）。 */
    public void showText(String titleText, String text, int align){
        FloatingDialog dialog = new FloatingDialog(titleText);

        Table content = dialog.getContentTable();
        content.row();
        content.add(divider(Pal.accent)).width(Scl.scl(400f)).pad(Scl.scl(2f)).colspan(2).height(Scl.scl(4f));
        content.row();

        Label label = new Label(bundle(text), Styles.defaultLabel);
        label.setWrap(true);
        label.setAlignment(align);
        content.add(label).width(Scl.scl(400f));
        content.row();

        Table btns = dialog.getButtonTable();
        btns.defaults().size(Scl.scl(90f), Scl.scl(50f)).pad(Scl.scl(4f));
        btns.add(textButton("$ok", null, dialog::hide));

        dialog.show();
    }

    /** 左对齐换行的信息文本（对应原版 {@code UI.showInfoText}）。 */
    public void showInfoText(String titleText, String text){
        FloatingDialog dialog = new FloatingDialog(titleText);

        Table content = dialog.getContentTable();
        content.pad(Scl.scl(15f));
        Label label = new Label(bundle(text), Styles.defaultLabel);
        label.setWrap(true);
        label.setAlignment(Align.left);
        content.add(label).width(Scl.scl(400f)).left();

        Table btns = dialog.getButtonTable();
        btns.defaults().size(Scl.scl(90f), Scl.scl(50f)).pad(Scl.scl(4f));
        btns.add(textButton("$ok", null, dialog::hide));

        dialog.show();
    }

    /** 小提示框（对应原版 {@code UI.showSmall}）。 */
    public void showSmall(String titleText, String text){
        FloatingDialog dialog = new FloatingDialog(titleText);

        Table content = dialog.getContentTable();
        content.pad(Scl.scl(10f));
        content.add(new Label(bundle(text), Styles.defaultLabel));

        //刻意降级：原版这里还会往 titleTable 补一条 accent 分隔线；FloatingDialog 构造器已经加过一条 → 去掉

        Table btns = dialog.getButtonTable();
        btns.defaults().size(Scl.scl(90f), Scl.scl(50f)).pad(Scl.scl(4f));
        btns.add(textButton("$ok", null, dialog::hide));

        dialog.show();
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /** @return 全局 Stage；拿不到时记一条警告并返回 null（对应原版的 {@code Core.scene}）。 */
    private static Stage requireScene(String what){
        Stage stage = FloatingDialog.scene;
        if(stage == null){
            Log.warn("Vars.ui.{0}: FloatingDialog.scene is null, skipped.", what);
        }
        return stage;
    }

    /** 一条分隔线（对应原版 {@code cont.addImage().color(...)}）。 */
    private static Image divider(Color color){
        Image image = new Image(Tex.whiteui);
        image.setColor(color);
        return image;
    }

    /**
     * 对应 arc 的 {@code Table.addButton(text, style, action)}。
     * <p>libgdx 的 Table 没有该方法，且 {@code FloatingDialog} 的同名辅助是 protected、本包不可见
     * → 就地实现一份等价的（按钮文字用项目 {@link Label}，arc 的颜色标记由它解析）。
     */
    private static TextButton textButton(String text, TextButton.TextButtonStyle style, Runnable action){
        return imageTextButton(text, null, style, action);
    }

    /** 对应 arc 的 {@code Table.addImageTextButton(text, icon, style, action)}。 */
    private static TextButton imageTextButton(String text, Drawable icon, TextButton.TextButtonStyle style, Runnable action){
        TextButton button = new TextButton("", style == null ? Styles.defaultt : style);
        button.clearChildren();
        if(icon != null){
            button.add(new Image(icon)).size(Scl.scl(26f)).padLeft(Scl.scl(8f));
        }
        button.add(new Label(bundle(text), Styles.defaultLabel)).pad(Scl.scl(5f));
        if(action != null){
            button.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    action.run();
                }
            });
        }
        return button;
    }

    /**
     * 取 bundle 文本（对应 arc 的 {@code "$key"} 前缀解析 + 项目 {@code I18n.get} 的缺键回退）。
     * <p>本项目的 bundle 是裁剪过的，缺键很多，所以一律带 fallback。
     */
    private static String bundle(String key){
        if(key == null) return "";
        return key.startsWith("$") ? I18n.get(key.substring(1), key.substring(1)) : key;
    }

    /**
     * 异常的"最终消息"（对应原版 {@code Strings.getFinalMesage}，本项目 {@code core/Strings} 没有）。
     * @return 形如 {@code "NullPointerException: xxx"}；没有消息时只返回类名
     */
    private static String describe(Throwable exc){
        if(exc == null) return null;
        String message = exc.getMessage();
        return message == null ? exc.getClass().getSimpleName() : exc.getClass().getSimpleName() + ": " + message;
    }

    /**
     * 异常堆栈文本（对应原版 {@code Strings.parseException(exc, true)}，本项目 {@code core/Strings} 没有）。
     * <p>这里直接拼 {@link Throwable#printStackTrace()} 的等价内容（含 cause 链）。
     */
    private static String stackTrace(Throwable exc){
        if(exc == null) return "";
        StringBuilder builder = new StringBuilder();
        builder.append(exc.getClass().getName());
        if(exc.getMessage() != null){
            builder.append(": ").append(exc.getMessage());
        }
        builder.append('\n');

        for(StackTraceElement element : exc.getStackTrace()){
            builder.append("    at ").append(element).append('\n');
        }

        Throwable cause = exc.getCause();
        while(cause != null){
            builder.append("Caused by: ").append(cause.getClass().getName());
            if(cause.getMessage() != null){
                builder.append(": ").append(cause.getMessage());
            }
            builder.append('\n');
            cause = cause.getCause();
        }
        return builder.toString();
    }
}
