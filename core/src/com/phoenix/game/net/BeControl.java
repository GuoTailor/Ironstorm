package com.phoenix.game.net;

import com.phoenix.game.core.Core;
import com.phoenix.game.core.Version;
import com.phoenix.game.func.Boolc;
import com.phoenix.game.func.Boolp;
import com.phoenix.game.func.Cons;
import com.phoenix.game.func.Floatc;
import com.phoenix.game.func.Intc;
import com.phoenix.game.util.Fi;
import com.phoenix.game.util.Log;
import com.phoenix.game.util.Threads;

import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Handles control of bleeding edge builds.
 *
 * <p><b>本项目是"大幅降级"版本</b>。原版的 BE 自动更新依赖四样本项目都没有的东西：
 * <ol>
 *   <li>{@code Core.net.httpGet(url, success, error)}（arc 的 Net）—— 本项目 {@code Core.net} 是 libgdx 的
 *       {@code com.badlogic.gdx.Net}，没有 {@code httpGet}；</li>
 *   <li>{@code arc.util.serialization.Jval}（容忍 hjson 的 JSON 解析）—— 未移植；</li>
 *   <li>{@code arc.util.Timer} / {@code arc.util.async.AsyncExecutor} —— 未移植。</li>
 * </ol>
 * 降级口径：
 * <ul>
 *   <li>{@link #active()} 已接 {@code Version.type}（本项目 {@code type = "official"} → 仍为 false）。</li>
 *   <li>构造器不排任何定时任务（原版用 {@code Timer.schedule} 每 {@code updateInterval} 秒自检）。</li>
 *   <li>{@link #checkUpdate(Boolc)} 直接回调 {@code false}（"没有新版本"）。</li>
 *   <li>{@link #showUpdateDialog()} 只打一条日志（原版的下载进度框 + 重启逻辑依赖
 *       {@code FloatingDialog}/{@code Bar}/{@code Config.autoUpdate}/{@code Vars.bebuildDirectory}，全部缺失）。</li>
 *   <li>{@link #download} <b>保留可用实现</b>（只用 java.net + {@link Fi}，与原版逐字一致），
 *       只是把 arc 的 {@code AsyncExecutor.submit} 换成 {@link Threads#daemon(Runnable)}。</li>
 * </ul>
 */
public class BeControl{
    private static final int updateInterval = 60 * 2;

    private boolean checkUpdates = true;
    private boolean updateAvailable;
    private String updateUrl;
    private int updateBuild;

    /** @return whether this is a bleeding edge build.
     * <p>已接 {@code Version.type}：本项目 {@code type = "official"} → 返回 false（与原版官方构建一致）。 */
    public boolean active(){
        return Version.type.equals("bleeding-edge");
    }

    public BeControl(){
        //TODO 原版：if(active()) Timer.schedule(() -> { if(checkUpdates && !mobile) checkUpdate(t -> {}); }, 1, updateInterval);
        //     本项目没有 arc.util.Timer，且 active() 恒 false → 不排任何任务。
        if(active()){
            Log.info("&lcBleeding edge build detected; automatic update checking is not ported.");
        }
    }

    /** asynchronously checks for updates.
     * <p>TODO 原版：{@code Core.net.httpGet("https://api.github.com/repos/Anuken/MindustryBuilds/releases/latest", ...)}
     * 后用 {@code Jval} 解析、比较 {@code Version.build}、从 assets 里找下载地址。
     * 本项目缺 {@code Core.net.httpGet}/{@code Jval} → 退化成"永远没有新版本"。 */
    public void checkUpdate(Boolc done){
        if(done != null){
            done.get(false);
        }
    }

    /** @return whether a new update is available */
    public boolean isUpdateAvailable(){
        return updateAvailable;
    }

    /** shows the dialog for updating the game on desktop, or a prompt for doing so on the server
     * <p>TODO 原版桌面端弹 {@code FloatingDialog} + {@code Bar} 显示下载进度并重启进程；
     * headless 端按 {@code Config.autoUpdate} 自动下载覆盖自身 jar。依赖
     * {@code Version}/{@code Config}/{@code Vars.bebuildDirectory}/{@code ui.showCustomConfirm} 等 → 只保留日志提示。 */
    public void showUpdateDialog(){
        if(!updateAvailable) return;

        Log.info("&lcA new update is available: &lyBleeding Edge build {0}", updateBuild);
    }

    /**
     * 下载 {@code furl} 到 {@code dest}，边下边回调长度与进度；{@code canceled} 返回 true 时中止。
     * <p>与原版逐字一致，只把 {@code executor.submit(...)} 换成后台 daemon 线程（本项目没有 AsyncExecutor）。
     */
    private void download(String furl, Fi dest, Intc length, Floatc progressor, Boolp canceled, Runnable done, Cons<Throwable> error){
        Threads.daemon(() -> {
            try{
                HttpURLConnection con = (HttpURLConnection)new URL(furl).openConnection();
                BufferedInputStream in = new BufferedInputStream(con.getInputStream());
                OutputStream out = dest.write(false, 4096);

                byte[] data = new byte[4096];
                long size = con.getContentLength();
                long counter = 0;
                length.get((int)size);
                int x;
                while((x = in.read(data, 0, data.length)) >= 0 && !canceled.get()){
                    counter += x;
                    progressor.get((float)counter / (float)size);
                    out.write(data, 0, x);
                }
                out.close();
                in.close();
                if(!canceled.get()) done.run();
            }catch(Throwable e){
                error.get(e);
            }
        });
    }
}
