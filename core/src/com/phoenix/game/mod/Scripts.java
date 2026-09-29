package com.phoenix.game.mod;

import com.badlogic.gdx.utils.Disposable;
import com.phoenix.game.mod.Mods.LoadedMod;
import com.phoenix.game.struct.Array;
import com.phoenix.game.util.Fi;
import com.phoenix.game.util.Log;
import com.phoenix.game.util.Log.LogLevel;

/**
 * Mod 的 JavaScript 脚本宿主。对应原版 {@code mindustry.mod.Scripts}。
 *
 * <p><b>本项目是"大幅降级"版本（保留 API 形状 + TODO）</b>。
 * 原版依赖 <b>Mozilla Rhino</b>：{@code org.mozilla.javascript.*}
 * （{@code Context}/{@code Scriptable}/{@code ImporterTopLevel}/{@code NativeJavaObject}/{@code Undefined}）
 * 与 {@code org.mozilla.javascript.commonjs.module.*}（CommonJS 模块加载），
 * 并靠 {@code Vars.platform.getScriptContext()} 拿引擎上下文。
 * <p>已确认 <b>{@code core/build.gradle} 里没有任何 JS 引擎依赖</b>（全工程 gradle 文件里
 * grep {@code rhino|nashorn|mozilla|javascript} 无命中），按约定<b>不去改 build.gradle 加依赖</b>
 * → 全部方法走 TODO 降级：
 * <ul>
 *   <li>{@link #Scripts()} 不建引擎、不执行 {@code scripts/global.js}，直接置 {@code errored = true}
 *       （调用方应据此禁用脚本控制台 / 跳过 mod 脚本加载）。</li>
 *   <li>{@link #runConsole(String)} 不执行，返回固定的"脚本引擎未移植"提示串。</li>
 *   <li>{@link #run(LoadedMod, Fi)} 不执行脚本，只记一条 warn。</li>
 *   <li>{@link #hasErrored()} / {@link #log(String, String)} / {@link #log(LogLevel, String, String)}
 *       <b>可用</b>（纯日志，无引擎依赖），与原版逐字一致。</li>
 *   <li>{@code blacklist}/{@code whitelist} 保留（原版用它给 rhino 的 ClassShutter 做白/黑名单过滤，
 *       引擎缺失时无用，但保留字段以对齐原版形状）。</li>
 *   <li>原版的内部类 {@code ScriptModuleProvider extends UrlModuleSourceProvider}
 *       （CommonJS 模块解析）<b>整段删除</b> —— 父类就不存在。</li>
 *   <li>{@code Vars.platform} 在本项目<b>不存在</b>（只有 {@code core/Platform} 接口，无字段），
 *       原版 {@code Vars.platform.getScriptContext()} 无处降级。</li>
 * </ul>
 */
public class Scripts implements Disposable{
    private final Array<String> blacklist = Array.with("net", "files", "reflect", "javax", "rhino", "file", "channels", "jdk",
        "runtime", "util.os", "rmi", "security", "org.", "sun.", "beans", "sql", "http", "exec", "compiler", "process", "system",
        ".awt", "socket", "classloader", "oracle", "invoke");
    private final Array<String> whitelist = Array.with("mindustry.net");
    private boolean errored;
    private LoadedMod currentMod = null;

    public Scripts(){
        //TODO 原版：
        //  Time.mark();
        //  context = Vars.platform.getScriptContext();
        //  context.setClassShutter(type -> !blacklist.contains(...) || whitelist.contains(...));
        //  context.getWrapFactory().setJavaPrimitiveWrap(false);
        //  scope = new ImporterTopLevel(context);
        //  new RequireBuilder().setModuleScriptProvider(...).setSandboxed(true).createRequire(context, scope).install(scope);
        //  if(!run(Core.files.internal("scripts/global.js").readString(), "global.js")) errored = true;
        //  Log.debug("Time to load script engine: {0}", Time.elapsed());
        // —— rhino（Context/Scriptable/ImporterTopLevel/RequireBuilder）与 Vars.platform 均未移植。
        errored = true;
        Log.debug("Script engine is not available: rhino was not ported into this project.");
    }

    public boolean hasErrored(){
        return errored;
    }

    /** 在控制台执行一行脚本并返回结果串。
     * <p>TODO 原版：{@code context.evaluateString(scope, text, "console.js", 1, null)}，并把
     * {@code NativeJavaObject} 解包、{@code Undefined} 显示成 "undefined"。
     * 无引擎 → 返回固定提示。 */
    public String runConsole(String text){
        return "Script engine is not available in this build (rhino not ported).";
    }

    private String getError(Throwable t){
        t.printStackTrace();
        return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
    }

    public void log(String source, String message){
        log(LogLevel.info, source, message);
    }

    public void log(LogLevel level, String source, String message){
        Log.log(level, "[{0}]: {1}", source, message);
    }

    /** 执行某个 mod 的脚本文件。
     * <p>TODO 原版：{@code run(file.readString(), file.name())}（交给 rhino）。无引擎 → 只记 warn。 */
    public void run(LoadedMod mod, Fi file){
        currentMod = mod;
        Log.warn("Ignoring script {0}: script engine not ported.", file == null ? "?" : file.name());
        currentMod = null;
    }

    @Override
    public void dispose(){
        //TODO 原版：Context.exit()（rhino）。无引擎 → 空实现。
    }
}
