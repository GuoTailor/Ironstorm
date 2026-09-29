package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ObjectMap;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.util.Log;

import java.util.Locale;

/**
 * 语言选择对话框，参照 Mindustry mindustry.ui.dialogs.LanguageDialog。
 *
 * <p><b>与本项目的差异</b>：
 * <ul>
 *   <li>arc 的 {@code Table.add(button).group(group).update(...)} libgdx 的 Cell 没有 {@code group}/{@code update}
 *       → 改成直接 {@code group.add(button)} + 建好时就 setChecked（见 setup）。</li>
 *   <li>arc 的 {@code button.clicked(Runnable)} libgdx 没有 → 用 {@link ClickListener}。</li>
 *   <li>arc 的 {@code Strings.capitalize} 本项目 core.Strings 没有 → 内联一个最小实现。</li>
 *   <li>{@code Vars.ui.showInfo(...)}：<b>已接线</b>（{@code UI.showInfo(String)} 见 {@code core/UI.java:551}，
 *       内部会 {@code bundle()} 解析裸 {@code $key}）→ 切换语言后弹"需重启"提示。</li>
 *   <li>滚动的 pane 加了 {@code grow()}：语言有几十个，不给高度会溢出屏幕（原版靠 arc 的布局兜着）。</li>
 * </ul>
 */
public class LanguageDialog extends FloatingDialog{
    /** 语言按钮尺寸（原版写死 400x50）。 */
    private static final float BUTTON_WIDTH = 400f, BUTTON_HEIGHT = 50f;

    private Locale lastLocale;
    /** 语言的自定义显示名（原版用 {@code ObjectMap.of(...)}；libgdx 的 ObjectMap 没有 of → 在构造器里 put）。 */
    private final ObjectMap<Locale, String> displayNames = new ObjectMap<>();

    public LanguageDialog(){
        super("$settings.language");
        displayNames.put(Locale.TRADITIONAL_CHINESE, "正體中文");
        displayNames.put(Locale.SIMPLIFIED_CHINESE, "简体中文");
        addCloseButton();
        setup();
    }

    private void setup(){
        Table langs = new Table();
        langs.padLeft(Scl.scl(24f)).padRight(Scl.scl(24f));
        ScrollPane pane = new ScrollPane(langs, Styles.defaultPane);
        pane.setFadeScrollBars(false);

        ButtonGroup<TextButton> group = new ButtonGroup<>();

        for(Locale loc : Vars.locales){
            TextButton button = new TextButton(capitalize(displayNames.get(loc, loc.getDisplayName(loc))), Styles.clearTogglet);
            //对应原版 Cell.update(t -> t.setChecked(...))：libgdx 的 Cell 没有 update，建好时直接设
            button.setChecked(loc.equals(getLocale()));
            button.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    if(getLocale().equals(loc)) return;
                    Core.settings.put("locale", loc.toString());
                    Core.settings.save();
                    Log.info("Setting locale: {0}", loc.toString());
                    //原版：ui.showInfo("$language.restart")；本对话框在 UI.init() 期间构造（那时 Vars.ui 还是 null），
                    //但本回调只在点击时触发，Vars.ui 已就绪 → 仍加 null 守卫
                    if(Vars.ui != null){
                        Vars.ui.showInfo("$language.restart");
                    }
                }
            });
            group.add(button);
            langs.add(button).size(Scl.scl(BUTTON_WIDTH), Scl.scl(BUTTON_HEIGHT)).row();
        }

        cont.add(pane).grow();
    }

    public Locale getLocale(){
        String loc = Core.settings.getString("locale", "default");

        if(loc.equals("default")){
            findClosestLocale();
        }

        //注意：原版这里 loc 仍是 "default"（findClosestLocale 只改了配置），所以首次调用会返回 Locale("default")；
        //第二次调用读到具体值就恢复正常。照原版保留该行为。
        if(lastLocale == null || !lastLocale.toString().equals(loc)){
            if(loc.contains("_")){
                String[] split = loc.split("_");
                lastLocale = new Locale(split[0], split[1]);
            }else{
                lastLocale = new Locale(loc);
            }
        }

        return lastLocale;
    }

    void findClosestLocale(){
        //check exact locale
        for(Locale l : Vars.locales){
            if(l.equals(Locale.getDefault())){
                Core.settings.put("locale", l.toString());
                return;
            }
        }

        //find by language
        for(Locale l : Vars.locales){
            if(l.getLanguage().equals(Locale.getDefault().getLanguage())){
                Core.settings.put("locale", l.toString());
                return;
            }
        }

        Core.settings.put("locale", new Locale("en").toString());
    }

    /** 首字母大写（对应 arc 的 {@code Strings.capitalize}，本项目 core.Strings 未提供）。 */
    private static String capitalize(String text){
        if(text == null || text.isEmpty()) return "";
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
