package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.ObjectMap;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Strings;
import com.phoenix.game.game.EventType.Trigger;
import com.phoenix.game.graphics.Shaders;
import com.phoenix.game.input.KeyCode;
import com.phoenix.game.ui.SettingsTable;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.util.Fi;
import com.phoenix.game.util.Log;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * 设置菜单对话框，参照 Mindustry mindustry.ui.dialogs.SettingsMenuDialog。
 *
 * <p><b>与原版的对应关系</b>：{@link #addSettings()} 已按原版逐项落地，三个分类页是
 * {@link SettingsTable}（arc {@code arc.scene.ui.SettingsDialog.SettingsTable} 的 libgdx 版替代层，
 * 见其类注释里的适配差异）。{@code com.phoenix.game.core.Settings} 也已补齐
 * {@code defaults/getDefault/getInt(name)…}（原版 {@code Core.settings} 的那套）。
 *
 * <p><b>仍未接的部分（TODO，逐条对应原版写法）</b>
 * <ul>
 *   <li>TODO {@code data.exportData/importData}：本项目 {@code game/GlobalData} 只有物品统计
 *       （{@code getItem/addItem/has/load/save}…），<b>没有 exportData/importData</b>；且本项目
 *       {@code Core.settings.getDataDirectory()} 是应用根目录（运行期 = {@code assets}，资源全在里面），
 *       整体打包/覆盖有删光资源的风险 → 导出/导入两个按钮只记日志。</li>
 *   <li>已接线 原版 {@code Core.app.openFolder(...)}：libgdx 的 {@code Application} 没有该方法 →
 *       等价替代 {@code Gdx.net.openURI(file URI)}（见 {@link #buildDataDialog()}）。</li>
 *   <li>F18 已接线 {@code renderer.toggleBloom(val)}：{@code core/Renderer} 已补
 *       {@code bloom}/{@code toggleBloom} 与帧内 capture/render 挂接；⚠ 管线本体是 no-op 降级
 *       （{@code graphics/Bloom} 类注释）→ 开关链路通了、画面暂不变化。</li>
 *   <li>已接线 原版教程重试按钮（{@code "$tutorial.retake"} → {@code control.playTutorial()}）：{@code Zones}/
 *       {@code Generator} 与 {@link com.phoenix.game.core.Control#playTutorial()} 均已就绪（T17），
 *       按钮在 {@link #addSettings()} 里注册（bundle 键 {@code tutorial.retake} 中英均已存在）。</li>
 *   <li>TODO mobile 专属项（autotarget/keyboard + {@code control.setInput(new DesktopInput()/MobileInput())}）：
 *       ⚠ <b>T14 纠正</b>：{@code Control.setInput(InputHandler)}（{@code Control.java:106}）、
 *       {@code DesktopInput}/{@code MobileInput} <b>其实都存在</b>；真实阻塞是**输入路由不在 setInput 里**——
 *       {@code ClientLauncher} 启动时用 {@code new InputMultiplexer(scrollGate(), stage, Vars.control.input, detector)}
 *       **一次性捕获**了当时的处理器引用，而 {@code InputHandler.add()/remove()} 是空实现
 *       → 切换 {@code Vars.control.input} 不会改变实际路由。修它要改 {@code ClientLauncher}（主会话独占）
 *       → 本批跳过（desktop 下 {@code Vars.mobile} 恒 false，不影响）。</li>
 *   <li>TODO steam 专属项（playerlimit/publichost + {@code platform.updateLobby()}）：{@code Vars.steam} 恒 false → 跳过。</li>
 *   <li>已接线 原版 {@code if(Shaders.shield != null)} 才注册的 animatedshields（N2c2）：{@code graphics/Shaders.shield}
 *       与 {@code core/Renderer.drawShields} 的护盾通道均已就绪（设置值真的被 Renderer 读到）。</li>
 *   <li>原版构造器里的 {@code cont.remove(); buttons.remove();} + 直接 {@code add(pane)}/{@code add(buttons)}
 *       （arc 的 Dialog 本身就是 Table）→ libgdx 的 Dialog 有独立的 content/button 表，
 *       这里改成把 pane 加进 {@code cont}、按钮留在 {@code buttons}。</li>
 * </ul>
 */
public class SettingsMenuDialog extends FloatingDialog{
    /** 菜单按钮尺寸（原版写死 300x60）。 */
    private static final float MENU_WIDTH = 300f, MENU_HEIGHT = 60f;
    /** 数据对话框按钮尺寸（原版写死 270x60）。 */
    private static final float DATA_WIDTH = 270f, DATA_HEIGHT = 60f;
    /** 返回按钮尺寸（原版写死 230x64）。 */
    private static final float CLOSE_WIDTH = 230f, CLOSE_HEIGHT = 64f;

    /** 三个分类页（原版是 arc 的 SettingsTable；本项目见 {@link SettingsTable}）。 */
    private SettingsTable graphics, game, sound;

    private Table prefs;
    private Table menu;
    private FloatingDialog dataDialog;

    public SettingsMenuDialog(){
        super("$settings");

        //原版是 SettingsDialog 自己在 shown/hidden 里暂停与恢复（不带 shouldPause 判定）
        shouldPause = true;

        //原版：cont.clearChildren(); cont.remove(); buttons.remove();（见类注释的差异说明）
        cont.clearChildren();
        buttons.clearChildren();

        menu = new Table();
        menu.setBackground(Tex.button);

        game = new SettingsTable();
        graphics = new SettingsTable();
        sound = new SettingsTable();

        prefs = new Table();
        prefs.top();
        prefs.pad(Scl.scl(14f));

        rebuildMenu();

        prefs.clearChildren();
        prefs.add(menu);

        dataDialog = new FloatingDialog("$settings.data");
        dataDialog.addCloseButton();
        buildDataDialog();

        ScrollPane pane = new ScrollPane(prefs, Styles.defaultPane);
        pane.addCaptureListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button){
                //拖动滑块时不要让 ScrollPane 抢走手势（照搬原版）
                Actor actor = pane.hit(x, y, true);
                if(actor instanceof Slider){
                    pane.setFlickScroll(false);
                    return true;
                }

                return super.touchDown(event, x, y, pointer, button);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button){
                pane.setFlickScroll(true);
                super.touchUp(event, x, y, pointer, button);
            }
        });
        pane.setFadeScrollBars(false);

        cont.add(pane).grow().top();

        addSettings();
    }

    void rebuildMenu(){
        menu.clearChildren();

        menu.defaults().size(Scl.scl(MENU_WIDTH), Scl.scl(MENU_HEIGHT));

        menu.add(textButton("$settings.game", Styles.cleart, () -> visible(0))).row();
        menu.add(textButton("$settings.graphics", Styles.cleart, () -> visible(1))).row();
        menu.add(textButton("$settings.sound", Styles.cleart, () -> visible(2))).row();
        menu.add(textButton("$settings.language", Styles.cleart, this::showLanguage)).row();
        if(!Vars.mobile || Core.settings.getBool("keyboard")){
            menu.add(textButton("$settings.controls", Styles.cleart, this::showControls)).row();
        }

        menu.add(textButton("$settings.data", Styles.cleart, () -> dataDialog.show())).row();
    }

    /** 设置项（对应原版 {@code SettingsMenuDialog.addSettings()}，逐项照抄）。 */
    void addSettings(){
        sound.sliderPref("musicvol", I18n.get("setting.musicvol.name", "Music Volume"), 100, 0, 100, 1, i -> i + "%");
        //TODO 无消费者：原版核心代码里 sfxvol 也只有注册、没有读取点（音效音量未接线）→ 只存值
        sound.sliderPref("sfxvol", I18n.get("setting.sfxvol.name", "SFX Volume"), 100, 0, 100, 1, i -> i + "%");
        sound.sliderPref("ambientvol", I18n.get("setting.ambientvol.name", "Ambient Volume"), 100, 0, 100, 1, i -> i + "%");

        game.screenshakePref();
        //TODO 原版 mobile 专属（autotarget / keyboard + control.setInput(new DesktopInput()/MobileInput())）：
        //     ⚠ T14 纠正：Control.setInput 与 DesktopInput/MobileInput 都存在，但真实输入路由由
        //     ClientLauncher 的 InputMultiplexer 启动时一次性捕获，InputHandler.add/remove 是空实现
        //     → setInput 切换不生效；修它需改 ClientLauncher（主会话独占）→ 本批跳过。desktop 下 Vars.mobile 恒 false，不影响
        game.sliderPref("saveinterval", 60, 10, 5 * 120, 10, i -> Strings.format(I18n.get("setting.seconds", "{0} Seconds"), i));

        if(!Vars.mobile){
            //原版 Core.bundle.format("setting.milliseconds", i)：本项目 bundle 缺该键 → fallback
            //已接线：PlacementFragment 的键盘连击选择（连续按同一数字键在分类内跳选）用它做超时判据
            //     （对应原版 PlacementFragment 的同类逻辑）→ PlacementFragment.java:916 读它
            game.sliderPref("blockselecttimeout", 750, 0, 2000, 50, i -> Strings.format(I18n.get("setting.milliseconds", "{0} Milliseconds"), i));

            game.checkPref("crashreport", true);
        }

        game.checkPref("savecreate", true);
        //已接线：InputHandler.updateLine 末尾用它门控 block.getReplacement（拖拽连线时拐角自动换成交叉器，
        //     对应原版 InputHandler.java:547）→ InputHandler.java:698 附近读它
        game.checkPref("blockreplace", true);
        game.checkPref("conveyorpathfinding", true);
        //F18 已接线 原版：Schematics.create 保存蓝图时用它决定是否把核心写进蓝图（Schematics.java 的
        //     收集判据 `isVisible() || (CoreBlock && coreselect)`）→ 只存值 → 真实消费
        game.checkPref("coreselect", false);
        //消费方不存在：原版 DesktopInput 的放置提示层未移植（用它显隐操作提示文字）
        game.checkPref("hints", true);
        if(!Vars.mobile){
            //消费方不存在：原版 DesktopInput 的建造自动暂停未移植（玩家开始建造时自动暂停游戏）
            game.checkPref("buildautopause", false);
        }

        //TODO 原版 steam 专属（playerlimit / publichost + platform.updateLobby()）：
        //     Vars.steam 恒 false 且 Vars.platform 未移植 → 整段跳过

        //已接线 原版教程重试按钮（原版 game.pref(new Setting(){ table.addButton("$tutorial.retake", ...);
        //     control.playTutorial(); })）。Zones/Generator 与 Control.playTutorial() 均已就绪（T17）。
        //     ⚠ 原版 Setting.add 末尾还有一句多余的 hide()（会把整个设置界面关掉）→ 这里不照抄该笔误。
        game.pref(new SettingsTable.Setting(){
            @Override
            public void add(SettingsTable table){
                table.add(textButton("$tutorial.retake", null, () -> {
                    hide();
                    if(Vars.control != null) Vars.control.playTutorial();
                })).size(Scl.scl(220f), Scl.scl(60f)).pad(Scl.scl(6f)).left();
                table.add();
                table.row();
            }
        });

        graphics.sliderPref("uiscale", 100, 25, 300, 25, s -> {
            //原版守卫 `if(ui.settings != null)` 是必需的：arc 的 SliderSetting.add() 会主动 fire 一次
            //change 事件刷标签，若不加守卫，设置菜单一被构造（本项目在 UI.init() 里构造，即每次启动）
            //就会把 uiscalechanged 置 true，导致"UI 缩放已改变"弹框每次启动都出现。
            //本项目 Vars.ui = new UI() 时 init() 在构造器内跑，此刻 Vars.ui 仍为 null（settings 也未赋值）
            //→ 必须连 Vars.ui 一起判，否则 NPE。
            if(Vars.ui != null && Vars.ui.settings != null) Core.settings.put("uiscalechanged", true);
            return s + "%";
        });
        graphics.sliderPref("fpscap", 240, 15, 245, 5, s -> {
            //帧率上限即时生效：libgdx 的 Graphics 有同名方法（原版是 Core.graphics.setForegroundFPS）。
            //原版语义：s > 240 表示"无限制"（文案 setting.fpscap.none）→ libgdx 传 0 即不限帧
            Gdx.graphics.setForegroundFPS(s > 240 ? 0 : s);
            return s > 240 ? I18n.get("setting.fpscap.none", "None") : Strings.format(I18n.get("setting.fpscap.text", "{0} FPS"), s);
        });
        //已接线：ChatFragment 用 getInt("chatopacity")/100f 作聊天面板背景不透明度 → ChatFragment.java:282 读它
        graphics.sliderPref("chatopacity", 100, 0, 100, 5, s -> s + "%");
        graphics.sliderPref("lasersopacity", 100, 0, 100, 5, s -> {
            //同上：守卫跳过构造期的首次 fire（否则启动时会把 preferredlaseropacity 重置为滑块值）
            if(Vars.ui != null && Vars.ui.settings != null) Core.settings.put("preferredlaseropacity", s);
            return s + "%";
        });
        graphics.sliderPref("bridgeopacity", 75, 0, 100, 5, s -> s + "%");

        if(!Vars.mobile){
            graphics.checkPref("vsync", true, b -> Core.graphics.setVSync(b));
            graphics.checkPref("fullscreen", false, b -> {
                if(b){
                    Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
                }else{
                    Gdx.graphics.setWindowedMode(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                }
            });

            graphics.checkPref("borderlesswindow", false, b -> Gdx.graphics.setUndecorated(b));

            Core.graphics.setVSync(Core.settings.getBool("vsync"));
            if(Core.settings.getBool("fullscreen")){
                //原版 Core.app.post(...)（arc 的 Application.post）；libgdx 的 Application 没有 post → postRunnable
                Gdx.app.postRunnable(() -> Core.graphics.setFullscreenMode(Core.graphics.getDisplayMode()));
            }

            if(Core.settings.getBool("borderlesswindow")){
                Gdx.app.postRunnable(() -> Core.graphics.setUndecorated(true));
            }
        }
        //TODO 原版 else if(!ios){ landscape + platform.beginForceLandscape()/endForceLandscape() }：
        //     本项目没有 platform 的横屏接口 → 跳过（desktop 走的是上面的 if(!mobile) 分支，不受影响）

        //已接线：原版 Renderer 的 effectProvider 用它决定是否生成特效实体（Renderer.java:58）；
        //     本项目没有 effectProvider，等价守卫放在特效生成入口 Effects.effect(...) → Effects.java:139 读它
        graphics.checkPref("effects", true);
        //已接线：Renderer.drawDestroyed() 画"被摧毁方块的重建幽灵轮廓"前判它 → Renderer.java:520 读它
        graphics.checkPref("destroyedblocks", true);
        //已接线：Player.draw 用它显隐玩家头顶聊天文字 → Player.java:186 读它
        graphics.checkPref("playerchat", true);
        //已接线：HudFragment 用它 + !rules.tutorial 控制右上角小地图显隐（照原版 HudFragment.java:257）
        //     → HudFragment.java:196 附近读它
        graphics.checkPref("minimap", !Vars.mobile);
        //已接线：HudFragment 用它显隐光标世界坐标（并判 !rules.tutorial）→ HudFragment.java:378 读它
        graphics.checkPref("position", false);
        //已接线：HudFragment 的 FPS / ping 计数器用它显隐 → HudFragment.java:360 读它
        graphics.checkPref("fps", false);
        if(!Vars.mobile){
            //已接线：PlacementFragment 的数字快捷键（本项目降级为纯数字键）用它启用键盘辅助
            //     → PlacementFragment.java:849 / :907 读它
            graphics.checkPref("blockselectkeys", true);
        }
        graphics.checkPref("indicators", true);
        graphics.checkPref("animatedwater", !Vars.mobile);
        //对应原版 SettingsMenuDialog 的 `if(Shaders.shield != null){ graphics.checkPref("animatedshields", !Vars.mobile); }`
        //（原版没有这个判空也会注册，判空是为了"护盾 shader 加载失败时不暴露一个没效果的设置项"）
        //⚠ 本项目 Shaders 是懒初始化（第一次真正要画时才建）→ 这里先幂等 init() 一次，
        //   否则设置界面构造时 shield 恒为 null、这一项又会被静默跳过。GL 上下文此时已就绪
        //   （UI 在 ClientLauncher.create 里、Core.assets.finishLoading 之后构造）。
        Shaders.init();
        if(Shaders.shield != null){
            //已接线：core/Renderer.drawShields 每帧读它（Core.settings.getBool("animatedshields", !Vars.mobile)）
            //→ Renderer.java:drawShields
            graphics.checkPref("animatedshields", !Vars.mobile);
        }
        if(!Vars.ios){
            graphics.checkPref("bloom", !Vars.mobile, val -> {
                //F18 已接线：调 Renderer.toggleBloom 建/弃 bloom 实例（对应原版 renderer.toggleBloom(val)）。
                //⚠ 管线本体当前是 no-op 降级（graphics/Bloom 类注释：libgdx 无现成等价物、完整 shader
                //  管线未移植）→ 开关链路已通、画面暂不变化
                if(Vars.renderer != null) Vars.renderer.toggleBloom(val);
            });
        }else{
            Core.settings.put("bloom", false);
        }

        //像素化后处理（渲染进低分辨率缓冲再放大）由 graphics.Pixelator 每帧直接读这个设置项决定，
        //所以这里只存值即可。原版还会 fire Trigger.enablePixelation 让监听者重建缓冲，本项目不需要。
        graphics.checkPref("pixelate", false, val -> {
            if(val){
                Events.fire(Trigger.enablePixelation);
            }
        });

        graphics.checkPref("linear", !Vars.mobile, b -> {
            for(Texture tex : Core.atlas.getTextures()){
                Texture.TextureFilter filter = b ? Texture.TextureFilter.Linear : Texture.TextureFilter.Nearest;
                tex.setFilter(filter, filter);
            }
        });

        if(Core.settings.getBool("linear")){
            for(Texture tex : Core.atlas.getTextures()){
                Texture.TextureFilter filter = Texture.TextureFilter.Linear;
                tex.setFilter(filter, filter);
            }
        }

        if(!Vars.mobile){
            Core.settings.put("swapdiagonal", false);
        }
    }

    /** 数据对话框（对应原版构造器里的 {@code dataDialog.cont.table(Tex.button, t -> {...})}）。 */
    private void buildDataDialog(){
        Table t = new Table();
        t.setBackground(Tex.button);
        t.defaults().size(Scl.scl(DATA_WIDTH), Scl.scl(DATA_HEIGHT)).left();

        //原版：ui.showConfirm("$confirm", "$settings.clearall.confirm", () -> {...})（UI.showConfirm 自己会查 bundle）
        //⚠ 偏离原版（安全修复）：原版 getDataDirectory() 返回游戏自己的数据目录，可以整体清空；
        //  本项目它返回的是应用 local 根目录（运行期 = <repo>/assets，sprites/i18n/fonts 全在里面），
        //  原版那样"遍历根目录整体删除"会把资源删光、游戏直接报废 —— 所以改为只删玩家数据子目录。
        t.add(imageTextButton("$settings.cleardata", Icon.trash, Styles.cleart, () ->
            Vars.ui.showConfirm("$confirm", "$settings.clearall.confirm", () -> {
                ObjectMap<String, Object> map = new ObjectMap<>();
                for(String value : Core.settings.keys()){
                    if(value.contains("usid") || value.contains("uuid")){
                        map.put(value, Core.settings.getString(value));
                    }
                }
                Core.settings.clear();
                Core.settings.putAll(map);
                Core.settings.save();

                //只清玩家数据（存档 / 蓝图 / 崩溃日志），绝不触碰 sprites/i18n/fonts 等资源目录
                Log.warn("Clearing player data (saves/schematics/crashes) under {0}", Core.settings.getDataDirectory().absolutePath());
                for(String dir : new String[]{"saves", "schematics", "crashes"}){
                    Fi file = Core.settings.getDataDirectory().child(dir);
                    if(file.exists()){
                        file.deleteDirectory();
                    }
                }

                //原版 Core.app.exit()（arc）→ libgdx Gdx.app.exit()
                Gdx.app.exit();
            }))).row();

        //TODO 原版：data.exportData(file) / platform.showFileChooser(false, "zip", ...)
        //  —— 本项目 game/GlobalData 没有 exportData、且没有 Vars.platform；另外 getDataDirectory() 是 assets 根，整体导出有风险
        t.add(imageTextButton("$data.export", Icon.arrowDown, Styles.cleart, () ->
            Log.warn("Data export is not implemented (GlobalData.exportData missing)."))).row();

        //TODO 原版：data.importData(file) / platform.showFileChooser(true, "zip", ...)
        //  —— 同上：GlobalData 没有 importData、没有 Vars.platform
        t.add(imageTextButton("$data.import", Icon.arrowDown, Styles.cleart, () ->
            Log.warn("Data import is not implemented (GlobalData.importData missing)."))).row();

        if(!Vars.mobile){
            //已接线：原版 Core.app.openFolder(Core.settings.getDataDirectory().absolutePath())
            //  —— libgdx 的 Application 没有 openFolder → 等价替代 Gdx.net.openURI(file URI)
            t.add(imageTextButton("$data.openfolder", Icon.folder, Styles.cleart, () ->
                Gdx.net.openURI(Core.settings.getDataDirectory().file().toURI().toString()))).row();
        }

        dataDialog.cont.add(t).pad(Scl.scl(6f));
    }

    private void back(){
        rebuildMenu();
        prefs.clearChildren();
        prefs.add(menu);
    }

    private void visible(int index){
        prefs.clearChildren();
        prefs.add(new Table[]{game, graphics, sound}[index]);
    }

    /** 原版：{@code menu.addButton("$settings.language", style, ui.language::show)}。 */
    private void showLanguage(){
        if(Vars.ui != null && Vars.ui.language != null){
            Vars.ui.language.show();
        }
    }

    /** 原版：{@code menu.addButton("$settings.controls", style, ui.controls::show)}。 */
    private void showControls(){
        if(Vars.ui != null && Vars.ui.controls != null){
            Vars.ui.controls.show();
        }
    }

    @Override
    public void addCloseButton(){
        buttons.add(imageTextButton("$back", Icon.arrowLeft, null, () -> {
            if(prefs.getChildren().size == 0 || prefs.getChildren().first() != menu){
                back();
            }else{
                hide();
            }
        })).size(Scl.scl(CLOSE_WIDTH), Scl.scl(CLOSE_HEIGHT));

        //原版的 keyDown：在分类页里按 ESC 是"返回上级"，在菜单页才是关闭（libgdx 的 Dialog.key 只会关）
        addListener(new InputListener(){
            @Override
            public boolean keyDown(InputEvent event, int keycode){
                if(keycode == KeyCode.ESCAPE || keycode == KeyCode.BACK){
                    if(prefs.getChildren().size == 0 || prefs.getChildren().first() != menu){
                        back();
                    }else{
                        hide();
                    }
                }
                return false;
            }
        });
    }
}
