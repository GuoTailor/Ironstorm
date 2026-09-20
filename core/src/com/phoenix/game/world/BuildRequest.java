package com.phoenix.game.world;

import com.phoenix.game.Vars;

/**
 * 一条建造/拆除请求。参照 Mindustry {@code BuilderTrait.BuildRequest} 移植。
 * <p>同一个类既表示"放置某个方块"也表示"拆除这一格"：{@link #breaking} 为 true 时 {@link #block} 无意义。
 */
public class BuildRequest{
    /** 目标格坐标与朝向。 */
    public int x, y, rotation;
    /** 要放置的方块；拆除请求为 null。 */
    public Block block;
    /** 是否为拆除请求。 */
    public boolean breaking;
    /** 是否带配置值（蓝图粘贴时用：桥的连接目标、分拣器的物品…）。 */
    public boolean hasConfig;
    /** 配置值；仅 {@link #hasConfig} 为 true 时有效。 */
    public int config = -1;
    /** 是否已经在该格铺好占位方块（避免每帧重复走放置分支）。 */
    public boolean initialized;
    /** 上一帧进度没动（材料不够或距离太远），用于跳过排队中的其他请求。 */
    public boolean stuck;
    /** 上一次读到的进度。 */
    public float progress;

    public BuildRequest(int x, int y, int rotation, Block block){
        this.x = x;
        this.y = y;
        this.rotation = rotation;
        this.block = block;
    }

    /** 拆除请求。 */
    public BuildRequest(int x, int y){
        this.x = x;
        this.y = y;
        this.breaking = true;
    }

    /** @return 目标瓦片；越界返回 null。 */
    public Tile tile(){
        return Vars.world == null ? null : Vars.world.tile(x, y);
    }
}
