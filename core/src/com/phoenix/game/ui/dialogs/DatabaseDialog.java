package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Fonts;
import com.phoenix.game.core.Scl;
import com.phoenix.game.ctype.Content;
import com.phoenix.game.ctype.ContentType;
import com.phoenix.game.ctype.UnlockableContent;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.input.KeyCode;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * 内容数据库（把全部 {@link UnlockableContent} 按类型铺成图标网格，点开看详情）。
 * 参照 Mindustry mindustry.ui.dialogs.DatabaseDialog。
 *
 * <p><b>与本项目的差异</b>：
 * <ul>
 *   <li>arc 的 {@code Table.margin(...)} / {@code addImage()} / {@code add(String)} 等便捷方法 libgdx 没有
 *       → {@code margin} 用 {@code pad}、{@code addImage} 用 {@code add(new Image(...))}、
 *       {@code add(String)} 用 {@link Label}（需要 skin，本项目没有）。</li>
 *   <li>{@code Cell.color(...)} libgdx 的 Cell 没有 → 颜色直接设在 Actor 上。</li>
 *   <li>刻意降级 {@code Icon.lockOpen}：本项目图集里没有该图标（只有 {@code Icon.locked}）→ 用 {@code Icon.locked} 代替。</li>
 *   <li>刻意降级 {@code image.update(Runnable)}（arc 的每帧回调）libgdx 没有 → 悬停高亮直接去掉。</li>
 *   <li>刻意降级 {@code new HandCursorListener()} / {@code new Tooltip(...)}（arc 的鼠标手型与提示气泡）未移植 → 跳过。</li>
 *   <li><b>F18 已补 {@code Fonts.getUnicode}</b>（{@code icons/icons.properties} →
 *       {@code Fonts.unicodeIcons}）→ SHIFT+点击复制图标字符的分支已照原版接线；
 *       注意本项目没有 icon 字体（原版 {@code fonts/icon.ttf}），复制出的是码点字符本身、
 *       聊天框里不会渲染成图标 —— 数据与复制行为与原版一致，字体渲染那半边不在本项目。</li>
 *   <li><b>{@code Vars.ui} 门面已接线</b>：{@code Vars.ui.content}（{@code ContentInfoDialog}）与
 *       {@code UI.showInfoFade} 都存在 → {@code $copied} 提示与内容详情都走真实门面
 *       （{@code ContentInfoDialog.show} 多一个 Stage 参数，从 {@code getStage()} 取）。</li>
 *   <li>⚠ {@code Vars.world.isZone()}：<b>F5 已回填</b>（{@link com.phoenix.game.core.World#isZone()}），
 *       {@link #unlocked} 现在带上了"zone 局只看已解锁内容"这一项。</li>
 * </ul>
 */
public class DatabaseDialog extends FloatingDialog{

    public DatabaseDialog(){
        super("$database");

        shouldPause = true;
        addCloseButton();
        shown(this::rebuild);
        onResize(this::rebuild);
    }

    void rebuild(){
        cont.clear();

        Table table = new Table();
        //刻意降级 原版 table.margin(20)：libgdx 的 Table/Cell 都没有 margin（外间距）→ 用 pad 代替
        table.pad(Scl.scl(20f));
        ScrollPane pane = new ScrollPane(table, Styles.defaultPane);

        Array<Content>[] allContent = Vars.content.getContentMap();

        for(int j = 0; j < allContent.length; j++){
            ContentType type = ContentType.values()[j];

            //原版 allContent[j].select(c -> c instanceof UnlockableContent && !((UnlockableContent)c).isHidden())：
            //libgdx 的 Array 的 select 收 Predicate，这里改用显式下标循环（避免重载歧义）。
            //⚠ 原版的 `isHidden()` 对 Block 的实现是 `!buildVisibility.visible()`（= "建造栏不可见"），
            //  而本项目 `Block.isHidden()` 是"**不绘制**"语义（`BlockPart` 覆写返回 true，`Renderer.drawTiles` 用它跳过绘制）
            //  → 直接照抄会把树/地板/矿石等全列进内容数据库。Block 要改走 `buildVisibility.visible()`（T2b）。
            Array<Content> array = new Array<>();
            for(int i = 0; i < allContent[j].size; i++){
                Content c = allContent[j].get(i);
                if(c instanceof UnlockableContent && dbVisible(c)){
                    array.add(c);
                }
            }
            if(array.size == 0) continue;

            //原版 table.add("$content.X.name")：arc 会自动解析 $ 前缀 → 这里走 FloatingDialog.bundle
            Label header = new Label(bundle("$content." + type.name() + ".name"), Styles.defaultLabel);
            header.setColor(Pal.accent);
            table.add(header).growX().left();
            table.row();

            //原版 table.addImage().growX().pad(5).padLeft(0).padRight(0).height(3).color(Pal.accent)
            //libgdx 没有 addImage / Cell.color → 建 Image 再设色
            Image divider = new Image(Tex.whiteui);
            divider.setColor(Pal.accent);
            table.add(divider).growX().pad(Scl.scl(5f)).padLeft(0f).padRight(0f).height(Scl.scl(3f));
            table.row();

            //原版 table.table(list -> {...})：libgdx 没有带回调的 table(...) → 自建内层 Table
            Table list = new Table();
            list.left();

            int maxWidth = isPortrait() ? 7 : 13;
            int count = 0;

            for(int i = 0; i < array.size; i++){
                UnlockableContent unlock = (UnlockableContent)array.get(i);

                //原版：new Image(Icon.lockOpen, Pal.gray)；libgdx 的 Image 没有 (Drawable, Color) 构造器
                //⚠ 本项目 Content.icon(Cicon) 缺图时**返回 null**（本项目 `findRegion` 缺图返回 null，见 `type/Item.java`；
                //  原版 arc 的 `icon()` 会回退到 error 区域、永不为 null）→ 直接把 null 交给 `new Image(...)` 会在绘制时
                //  NPE（`SpriteBatch.draw` 读 `region.texture`）。这里照 `Renderer.region` 的既有约定回退到图集里的 `blank`。
                //  ⚠ 实测（2026-09-29 T2b）：**只有 11 个 StatusEffect 走这条回退**（burning/freezing/wet/…）
                //   —— 原版图集同样没有 `burning`/`status-*` 区域（`grep -c '^status' Mindustry 的 sprites.atlas` = 0，
                //   且 `UnlockableContent.icon()` 的查找链就是 `status-<name>-medium` → `-full` → `status-<name>` → `<name>` → `<name>1`），
                //   所以原版显示的是 `error` 粉色占位图、本项目显示 `blank` 白块 —— **同属"无图标"，不算移植缺口**。
                TextureRegion dbIcon = unlocked(unlock) ? unlock.icon(Cicon.medium) : null;
                if(unlocked(unlock) && dbIcon == null && Core.atlas != null){
                    dbIcon = Core.atlas.findRegion("blank");
                }
                if(unlocked(unlock) && dbIcon == null){
                    continue; //图集都没有 blank（理论上不会）→ 跳过，别把 null 塞进 Image
                }
                Image image = unlocked(unlock) ? new Image(dbIcon) : new Image(Icon.locked);
                if(!unlocked(unlock)){
                    image.setColor(Pal.gray);
                }
                list.add(image).size(Scl.scl(8 * 4)).pad(Scl.scl(3f));
                ClickListener listener = new ClickListener();
                image.addListener(listener);
                if(!Vars.mobile && unlocked(unlock)){
                    //刻意降级 原版：image.addListener(new HandCursorListener())（arc 的鼠标手型未移植）
                    //刻意降级 原版：image.update(() -> image.getColor().lerp(!listener.isOver() ? Color.lightGray : Color.white, 0.4f * Time.delta()))
                    //  —— arc 的 Element.update(Runnable)（每帧回调）libgdx 没有 → 悬停高亮整段去掉
                    image.setColor(com.badlogic.gdx.graphics.Color.LIGHT_GRAY);
                }

                if(unlocked(unlock)){
                    image.addListener(new ClickListener(){
                        @Override
                        public void clicked(InputEvent event, float x, float y){
                            //原版：if(Core.input.keyDown(KeyCode.SHIFT_LEFT) && Fonts.getUnicode(unlock.name) != 0)
                            //      → 复制图标字符并提示 "$copied"（F18 已补 Fonts.getUnicode：
                            //        icons/icons.properties → Fonts.unicodeIcons）
                            //Core.input 没有 keyDown(int) → 用 libgdx 的 Gdx.input.isKeyPressed
                            //（KeyCode.SHIFT_LEFT 就等于 Input.Keys.SHIFT_LEFT，见 KeyCode 类注释）
                            if(Gdx.input.isKeyPressed(KeyCode.SHIFT_LEFT) && Fonts.getUnicode(unlock.name) != 0){
                                //libgdx 的 Application 没有 setClipboardText → 用 Gdx.app.getClipboard()
                                Gdx.app.getClipboard().setContents((char)Fonts.getUnicode(unlock.name) + "");
                                //原版 ui.showInfoFade("$copied")：Vars.ui 门面已存在（判空）
                                if(Vars.ui != null){
                                    Vars.ui.showInfoFade("$copied");
                                }
                            }else{
                                //原版 Vars.ui.content.show(unlock)：改走共享门面实例（判空）
                                openInfo(unlock);
                            }
                        }
                    });
                    //刻意降级 原版：image.addListener(new Tooltip(t -> t.background(Tex.button).add(unlock.localizedName)))
                    //  —— arc 的 Tooltip 未移植 → 悬停提示先不做
                }

                if((++count) % maxWidth == 0){
                    list.row();
                }
            }

            table.add(list).growX().left().padBottom(Scl.scl(10f));
            table.row();
        }

        cont.add(pane);
    }

    /**
     * 原版走 {@code Vars.ui.content.show(unlock)}；本项目改调共享门面 {@code Vars.ui.content}
     * （不再每次 {@code new ContentInfoDialog()}）。本项目 {@code ContentInfoDialog.show} 需要 Stage
     * （见其类注释），从本 actor 所在的 {@code getStage()} 取（与 {@code TechTreeDialog} 同口径）。
     * <p>保留"非 Block 就 return"的守卫：本项目 {@code ContentInfoDialog} 只支持 Block。
     */
    private void openInfo(UnlockableContent unlock){
        if(!(unlock instanceof com.phoenix.game.world.Block)) return;
        if(Vars.ui == null || Vars.ui.content == null) return;

        Stage stage = getStage();
        if(stage == null) return;

        Vars.ui.content.show((com.phoenix.game.world.Block)unlock, stage);
    }

    boolean unlocked(UnlockableContent content){
        //原版：(!Vars.world.isZone() && !Vars.state.is(State.menu)) || content.unlocked()
        //  —— F5 已补 World.isZone()；GameState 没有 is(State)，用 isMenu() 代替。
        //  注意 Vars.world 在菜单里是 null，所以要带 null 守卫（原版世界对象常驻）。
        return (!(Vars.world != null && Vars.world.isZone()) && !Vars.state.isMenu()) || content.unlocked();
    }

    /** @return 是否竖屏（对应 arc 的 {@code Core.graphics.isPortrait()}，libgdx 的 Graphics 没有该方法）。 */
    private static boolean isPortrait(){
        return Core.graphics.getHeight() > Core.graphics.getWidth();
    }

    /**
     * 内容数据库的"是否列出"判据（对应原版 {@code !((UnlockableContent)c).isHidden()}）。
     * <p>⚠ 本项目 {@code Block.isHidden()} 是"**不绘制**"语义（`BlockPart` 覆写返回 true，
     * {@code Renderer.drawTiles} 用它跳过绘制），而原版 {@code Block.isHidden()} 的实现是
     * {@code !buildVisibility.visible()}（= "建造栏不可见"，见 `转换.md` T2 批）→
     * 直接照抄原式会把树/地板/矿石等非建造方块全列进数据库。所以 Block 单独走 {@code buildVisibility.visible()}。
     * <p>非 Block 内容（物品/液体/单位）沿用 {@code isHidden()}，语义两边一致。
     */
    private static boolean dbVisible(Content c){
        if(c instanceof com.phoenix.game.world.Block){
            return ((com.phoenix.game.world.Block)c).buildVisibility.visible();
        }
        return !((UnlockableContent)c).isHidden();
    }
}
