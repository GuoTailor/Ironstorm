package com.phoenix.game.net;

import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Version;
import com.phoenix.game.func.Cons;
import com.phoenix.game.util.Fi;
import com.phoenix.game.util.Log;
import com.phoenix.game.util.OS;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 崩溃报告：写本地崩溃日志 + 上报到 {@code Vars.crashReportURL}，然后退出进程。
 * 对应原版 {@code mindustry.net.CrashSender}。
 *
 * <p><b>与原版的差异 + TODO</b>：
 * <ul>
 *   <li><b>{@code Version} 已接线</b>：补回了原版两条"不上报"判定 ——
 *       {@code Version.build == -1}（自定义构建）与 {@code Version.number == 0}（版本号解析失败）；
 *       上报 JSON 里的 {@code versionType/versionNumber/versionModifier/build/revision} 五个字段也已补上。
 *       仍<b>未补</b>的是原版"从 {@code /version.properties} 补读版本号"整段
 *       （本项目没有该资源文件，`Version` 字段是硬编码的）。</li>
 *   <li><b>没有 {@code Strings.parseException/getCauses}</b> → 本地日志与 {@code trace} 字段
 *       统一用本类自己的 {@link #parseException(Throwable)}（printStackTrace 到 StringWriter，与原版一致）。</li>
 *   <li>{@code players} 字段：F6 起 {@code Vars.playerGroup} 已建并接线，上报的是组内玩家数
 *       （组未建时按 0 兜底）。</li>
 *   <li><b>没有 arc 的 {@code NetJavaImpl}/{@code HttpRequest}/{@code HttpResponse}</b> →
 *       {@link #httpPost} 用 java.net.{@code HttpURLConnection} <b>同步</b> POST（原版是异步回调 + 主线程自旋等待）。
 *       签名里的 {@code Cons<HttpResponse>} 相应降级成 {@code Cons<String>}（回传 "HTTP &lt;code&gt;"）。</li>
 *   <li><b>{@code Vars.net} 全工程从未赋值（恒 null）</b> → {@code net.active()/server()/dispose()} 全部加 null 守卫。</li>
 *   <li>JSON 库从 arc 换成 libgdx：{@code new JsonValue(ValueType.object)} →
 *       {@code new JsonValue(JsonValue.ValueType.object)}；{@code value.toJson(OutputType.json)} →
 *       {@code value.toJson(JsonWriter.OutputType.json)}。{@code addChild(String, JsonValue)} 同名同签名。</li>
 * </ul>
 */
public class CrashSender{

    public static void log(Throwable exception){
        try{
            Core.settings.getDataDirectory().child("crashes").child("crash_" + System.currentTimeMillis() + ".txt").writeString(parseException(exception));
        }catch(Throwable ignored){
        }

        if(exception instanceof RuntimeException){
            throw (RuntimeException)exception;
        }
        throw new RuntimeException(exception);
    }

    public static void send(Throwable exception, Cons<File> writeListener){

        try{
            //原版：自定义构建（build == -1）不生成崩溃日志，直接退出（本项目 build 恒 126，不会进入）
            if(Version.build == -1){
                ret();
                return;
            }
            //TODO 原版还有"开发者本机"判定（System.getProperty("user.name") 与 Version.modifier）
            //     —— 本项目没有对应设施，只做 build == -1 这一条。

            exception.printStackTrace();

            try{
                File file = new File(OS.getAppDataDirectoryString(Vars.appName), "crashes/crash-report-" + new SimpleDateFormat("MM_dd_yyyy_HH_mm_ss").format(new Date()) + ".txt");
                new Fi(OS.getAppDataDirectoryString(Vars.appName)).child("crashes").mkdirs();
                new Fi(file).writeString(parseException(exception));
                if(writeListener != null) writeListener.get(file);
            }catch(Throwable e){
                Log.err("Failed to save local crash report.", e);
            }

            try{
                //check crash report setting
                if(!Core.settings.getBool("crashreport", true)){
                    ret();
                }
            }catch(Throwable ignored){
                //if there's no settings init we don't know what the user wants but chances are it's an important crash, so send it anyway
            }

            try{
                //check any mods - if there are any, don't send reports
                if(Vars.mods != null && Vars.mods.list().size > 0){
                    ret();
                }
            }catch(Throwable ignored){
            }

            //do not send exceptions that occur for versions that can't be parsed
            if(Version.number == 0){
                ret();
                return;
            }

            boolean netActive = false, netServer = false;

            //attempt to close connections, if applicable
            try{
                //本项目 Vars.net 恒为 null（传输层实例由 netServer/netClient 各自持有）→ 必须守卫
                if(Vars.net != null){
                    netActive = Vars.net.active();
                    netServer = Vars.net.server();
                    Vars.net.dispose();
                }
            }catch(Throwable ignored){
            }

            JsonValue value = new JsonValue(JsonValue.ValueType.object);

            boolean fn = netActive, fs = netServer;

            //add all relevant info, ignoring exceptions
            ex(() -> value.addChild("versionType", new JsonValue(Version.type)));
            ex(() -> value.addChild("versionNumber", new JsonValue(Version.number)));
            ex(() -> value.addChild("versionModifier", new JsonValue(Version.modifier)));
            ex(() -> value.addChild("build", new JsonValue(Version.build)));
            ex(() -> value.addChild("revision", new JsonValue(Version.revision)));
            ex(() -> value.addChild("net", new JsonValue(fn)));
            ex(() -> value.addChild("server", new JsonValue(fs)));
            //玩家数（对应原版 Vars.playerGroup.size()）；playerGroup 未建时按 0 兜底
            ex(() -> value.addChild("players", new JsonValue(Vars.playerGroup == null ? 0 : Vars.playerGroup.size())));
            ex(() -> value.addChild("state", new JsonValue(Vars.state.getState().name())));
            ex(() -> value.addChild("os", new JsonValue(System.getProperty("os.name") + "x" + (OS.is64Bit ? "64" : "32"))));
            ex(() -> value.addChild("trace", new JsonValue(parseException(exception))));
            ex(() -> value.addChild("javaVersion", new JsonValue(System.getProperty("java.version"))));
            ex(() -> value.addChild("javaArch", new JsonValue(System.getProperty("sun.arch.data.model"))));

            boolean[] sent = {false};

            Log.info("Sending crash report.");
            //post to crash report URL
            httpPost(Vars.crashReportURL, value.toJson(JsonWriter.OutputType.json), r -> {
                Log.info("Crash sent successfully.");
                sent[0] = true;
                System.exit(1);
            }, t -> {
                t.printStackTrace();
                sent[0] = true;
                System.exit(1);
            });

            //sleep until report is sent
            try{
                while(!sent[0]){
                    Thread.sleep(30);
                }
            }catch(InterruptedException ignored){}
        }catch(Throwable death){
            death.printStackTrace();
        }

        ret();
    }

    private static void ret(){
        System.exit(1);
    }

    /** 同步 POST。原版走 arc 的 {@code NetJavaImpl.http(...)}（异步 + HttpResponse），本项目用 java.net 直接实现。 */
    private static void httpPost(String url, String content, Cons<String> success, Cons<Throwable> failure){
        try{
            HttpURLConnection con = (HttpURLConnection)new URL(url).openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");
            byte[] body = content.getBytes(Vars.charset);
            con.setFixedLengthStreamingMode(body.length);
            con.getOutputStream().write(body);
            con.getOutputStream().flush();
            success.get("HTTP " + con.getResponseCode());
        }catch(Throwable t){
            failure.get(t);
        }
    }

    private static String parseException(Throwable e){
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    private static void ex(Runnable r){
        try{
            r.run();
        }catch(Throwable ignored){
        }
    }
}
