package com.phoenix.game.core;

import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Base64Coder;
import com.phoenix.game.func.Cons;
import com.phoenix.game.net.Net;
import com.phoenix.game.net.PhoenixNetProvider;
import com.phoenix.game.struct.Array;
import com.phoenix.game.type.Publishable;
import com.phoenix.game.util.Fi;

import static com.phoenix.game.Vars.mobile;

/**
 * 平台相关的可选能力（Steam 工坊、脚本、移动端软键盘、文件选择框…）。
 * 参照 Mindustry mindustry.core.Platform 移植。
 * <p>桌面端不需要覆写任何方法：所有方法都有默认实现。
 *
 * <p><b>与本项目的差异（刻意降级；本接口无实例，方法均无调用方）</b>：本项目<b>没有 {@code Vars.platform} 字段</b>，
 * 也没有任何类 {@code implements Platform} → 这些 default 方法当前不会被调用，保留是为了对齐原版形状。逐项：
 * <ul>
 *   <li>刻意降级 {@code createScripts()} / {@code getScriptContext()}：{@code com.phoenix.game.mod.Scripts}
 *       类<b>存在</b>（但是降级版：无 Rhino 引擎，构造即置 {@code errored = true}），
 *       而 rhino 的 {@code org.mozilla.javascript.Context} 未移植（{@code build.gradle} 无 JS 引擎依赖）
 *       → {@code getScriptContext()} 无法实现，故两方法整段注释保留。</li>
 *   <li>刻意降级 {@code addDialog(...)}：原版 {@code TextInput}（移动端软键盘）与 arc 的
 *       {@code TextField.tapped(...)} 均未移植 → 只保留 mobile 判定。</li>
 *   <li>刻意降级 {@code showFileChooser(...)}：{@code ui/dialogs/FileChooser} <b>已移植</b>，且该 default 实现
 *       已被搬成 {@link com.phoenix.game.ui.dialogs.FileChooser#showFileChooser(boolean, String, Cons)} 静态方法
 *       （T15c 收敛：所有调用点都委托给它）；但本接口无实例 → 这里不接（接了也是死代码）。</li>
 * </ul>
 * 另外原版 {@code arc.math.Rand} 未移植（本项目无 {@code Rand} 类）→ {@link #getUUID()} 用
 * {@code java.util.Random} 取随机字节（语义等价）。
 */
public interface Platform{

    /** Steam: Update lobby visibility.*/
    default void updateLobby(){}

    /** Steam: Show multiplayer friend invite dialog.*/
    default void inviteFriends(){}

    /** Steam: Share a map on the workshop.*/
    default void publish(Publishable pub){}

    /** Steam: View a listing on the workshop.*/
    default void viewListing(Publishable pub){}

    /** Steam: View a listing on the workshop by an ID.*/
    default void viewListingID(String mapid){}

    /** Steam: Return external workshop maps to be loaded.*/
    default Array<Fi> getWorkshopContent(Class<? extends Publishable> type){
        return new Array<>(0);
    }

    /** Steam: Open workshop for maps.*/
    default void openWorkshop(){}

    /** Get the networking implementation.*/
    default Net.NetProvider getNet(){
        //原版是 new ArcNetProvider()（arc 的 socket 实现）；本项目是等价的 PhoenixNetProvider
        return new PhoenixNetProvider();
    }

    //刻意降级（缺依赖）原版：
    //  /** Gets the scripting implementation. */
    //  default Scripts createScripts(){
    //      return new Scripts();
    //  }
    //
    //  default Context getScriptContext(){
    //      Context c = Context.enter();
    //      c.setOptimizationLevel(9);
    //      return c;
    //  }
    // —— com.phoenix.game.mod.Scripts 存在（降级版，无 Rhino）；org.mozilla.javascript.Context 未移植
    //    （build.gradle 无 JS 引擎依赖）→ getScriptContext() 无法实现；且本接口无实例，两方法都无调用方。

    /** Add a text input dialog that should show up after the field is tapped. */
    default void addDialog(TextField field){
        addDialog(field, 16);
    }

    /** See addDialog(). */
    default void addDialog(TextField field, int maxLength){
        if(!mobile) return; //this is mobile only, desktop doesn't need dialogs

        //TODO 原版：field.tapped(() -> { TextInput input = new TextInput(); ... Core.input.getTextInput(input); })
        // —— arc 的 TextField.tapped(...) 与 Net.TextInput（移动端软键盘）均未移植，
        //    本项目的输入框走 libgdx 的 TextField + 系统键盘，等移动端适配时再补。
    }

    /** Update discord RPC. */
    default void updateRPC(){
    }

    /** Must be a base64 string 8 bytes in length. */
    default String getUUID(){
        return uuid();
    }

    /**
     * 持久化 uuid 的**静态**入口（语义等同原版 {@code Vars.platform.getUUID()}）。
     * <p>本项目没有 {@code Vars.platform} 字段（见类注释），但玩家 uuid 需要跨局稳定（管理员/封禁按 uuid 记录）
     * → 提供不需要实例的静态入口，{@link #getUUID()} 也委托到这里。
     */
    static String uuid(){
        String uuid = Core.settings.getString("uuid", "");
        if(uuid.isEmpty()){
            byte[] result = new byte[8];
            //等价替代：原版是 new Rand().nextBytes(result)（arc.math.Rand）；本项目无 Rand 类 → 用 java.util.Random
            new java.util.Random().nextBytes(result);
            uuid = new String(Base64Coder.encode(result));
            Core.settings.put("uuid", uuid);
            Core.settings.save();
        }
        return uuid;
    }

    /** Only used for iOS or android: open the share menu for a map or save. */
    default void shareFile(Fi file){
    }

    /**
     * Show a file chooser.
     * @param cons Selection listener
     * @param open Whether to open or save files
     * @param extension File extension to filter
     */
    default void showFileChooser(boolean open, String extension, Cons<Fi> cons){
        //刻意降级（无调用方）：原版这段 default 实现（含"保存模式强制补扩展名"）已**逐字搬**到
        //  {@link com.phoenix.game.ui.dialogs.FileChooser#showFileChooser(boolean, String, Cons)}（T15c），
        //  全部调用点（MapsDialog / MapEditorDialog / ModsDialog / SchematicsDialog / SaveDialog）都委托给它。
        //  本接口无实例（全工程无 implements Platform 的类、无 Vars.platform）→ 这里保持空实现（接上是死代码）。
    }

    /** Hide the app. Android only. */
    default void hide(){
    }

    /** Forces the app into landscape mode.*/
    default void beginForceLandscape(){
    }

    /** Stops forcing the app into landscape orientation.*/
    default void endForceLandscape(){
    }
}
