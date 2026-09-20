package com.phoenix.game.net;

import com.phoenix.game.core.Time;
import com.phoenix.game.math.Mathf;

/**
 * 远端实体位置插值器。对应原版 mindustry.net.Interpolator（最小实现）。
 * <p>服务端单位快照间隔是 {@code syncInterval} = 12 tick（约 200ms），把代理单位直接贴到快照位置会一跳一跳。
 * 这里保存「上次显示位置」与「本次快照目标」，每帧按**实测快照间隔**把显示位置推进过去；
 * alpha 上限 2 允许轻微外推，使快速移动的目标不至于落后一个快照周期。
 */
public class Interpolator {
    /** 插值起点（上次显示位置）。 */
    public float lastX, lastY, lastRot;
    /** 插值终点（本次快照位置）。 */
    public float targetX, targetY, targetRot;
    /** 上次收到快照的时间（毫秒）与实测间隔（毫秒）。 */
    public long lastUpdated, updateSpacing = 16;

    /** 收到新快照：以当前显示位置为起点、新快照位置为终点。 */
    public void read(float curX, float curY, float curRot, float x, float y, float rot){
        long now = Time.millis();
        if(lastUpdated != 0){
            updateSpacing = Math.max(1, now - lastUpdated);
        }
        lastUpdated = now;

        lastX = curX;
        lastY = curY;
        lastRot = curRot;
        targetX = x;
        targetY = y;
        targetRot = rot;
    }

    /** 直接就位（创建代理单位时用）：起点与终点都设为给定位置，避免从 (0,0) 飞过来。 */
    public void snap(float x, float y, float rot){
        lastUpdated = Time.millis();
        lastX = targetX = x;
        lastY = targetY = y;
        lastRot = targetRot = rot;
    }

    /** @return 当前插值进度：0 = 起点，1 = 终点，上限 2（允许外推）。 */
    public float alpha(){
        if(lastUpdated == 0) return 1f;
        return Math.min((Time.millis() - lastUpdated) / (float)updateSpacing, 2f);
    }

    public float x(float a){
        return lastX + (targetX - lastX) * a;
    }

    public float y(float a){
        return lastY + (targetY - lastY) * a;
    }

    /** 角度插值（slerp 处理 360° 环绕）。 */
    public float rotation(float a){
        return Mathf.slerp(lastRot, targetRot, a);
    }
}
