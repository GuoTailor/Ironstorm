package com.phoenix.game.core;

import com.badlogic.gdx.math.MathUtils;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.game.EventType;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Pos;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockFlag;

import static com.phoenix.game.Vars.tilesize;

/**
 * 最小实现：地图/世界。参照 Mindustry mindustry.core.World 移植。
 * 目前支持程序化生成地图、瓦片查询与射线遍历，暂不支持存档读写。
 */
public class World {
    public Tile[] tiles = {};
    public int width, height;
    private boolean generating;

    /** 程序化生成一张地图。 */
    public void createMap(int width, int height){
        generating = true;
        this.width = width;
        this.height = height;
        this.tiles = new Tile[width * height];

        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){
                Tile tile = new Tile(x, y);
                Floor floor = floorAt(x, y);
                tile.setFloor(floor);

                //边界墙：原版拿 darkMetal 当地图边缘（实心、不可拆），顺便防止单位跑出地图
                if(x == 0 || y == 0 || x == width - 1 || y == height - 1){
                    tile.setBlock(Blocks.darkMetal);
                }else if(wallAt(x, y)){
                    //零星的自然岩壁：种类与所在地表匹配（对应原版按生物群系挑 StaticWall）
                    Block rock = rockFor(floor);
                    if(rock != null) tile.setBlock(rock);
                }

                tiles[y * width + x] = tile;
            }
        }

        placeSpawnPoints();

        generating = false;
        rebuildPowerGraphs();
    }

    /**
     * 在地图四角内侧放置波次出生点（对应原版地图的 spawn overlay）。
     * 瓦片不可行走时向外螺旋查找最近的可行走瓦片；找不到就跳过。
     */
    private void placeSpawnPoints(){
        int inset = 6;
        int[][] points = {
            {inset, inset}, {width - 1 - inset, inset},
            {inset, height - 1 - inset}, {width - 1 - inset, height - 1 - inset}
        };

        for(int[] point : points){
            Tile spawn = findWalkable(point[0], point[1]);
            if(spawn != null){
                spawn.setOverlay(Blocks.spawn);
            }
        }
    }

    /** @return 以 (x,y) 为中心向外查找的最近可行走瓦片 */
    private Tile findWalkable(int x, int y){
        for(int radius = 0; radius < 10; radius++){
            for(int dx = -radius; dx <= radius; dx++){
                for(int dy = -radius; dy <= radius; dy++){
                    Tile tile = tile(x + dx, y + dy);
                    if(tile != null && !tile.solid() && tile.floor() != Blocks.water){
                        return tile;
                    }
                }
            }
        }

        return null;
    }

    /** 依据噪声决定地表类型。 */
    private Floor floorAt(int x, int y){
        float n = noise(x / 20f, y / 20f) * 0.65f + noise(x / 7f, y / 7f) * 0.35f;

        if(n < 0.34f) return Blocks.water;
        if(n < 0.44f) return Blocks.sand;
        if(n < 0.60f) return Blocks.grass;
        if(n < 0.74f) return Blocks.stone;
        if(n < 0.86f) return Blocks.darksand;
        return Blocks.shale;
    }

    /** 少量散布的岩壁。 */
    private boolean wallAt(int x, int y){
        return noise(x / 11f + 40f, y / 11f + 40f) > 0.83f;
    }

    /** @return 该地表上应该长的自然岩壁；水面不长岩壁（返回 null）。 */
    private Block rockFor(Floor floor){
        if(floor == Blocks.stone) return Blocks.rocks;
        if(floor == Blocks.sand) return Blocks.sandRocks;
        if(floor == Blocks.darksand) return Blocks.duneRocks;
        if(floor == Blocks.shale) return Blocks.shaleRocks;
        if(floor == Blocks.grass) return Blocks.shrubs;
        return null;
    }

    private float noise(float x, float y){
        int xi = (int)Math.floor(x), yi = (int)Math.floor(y);
        float xf = x - xi, yf = y - yi;
        float v00 = hash(xi, yi), v10 = hash(xi + 1, yi), v01 = hash(xi, yi + 1), v11 = hash(xi + 1, yi + 1);
        float sx = xf * xf * (3f - 2f * xf), sy = yf * yf * (3f - 2f * yf);
        return MathUtils.lerp(MathUtils.lerp(v00, v10, sx), MathUtils.lerp(v01, v11, sx), sy);
    }

    private float hash(int x, int y){
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        return ((h ^ (h >> 16)) & 0x7FFFFFFF) / (float)0x7FFFFFFF;
    }

    public Tile tile(int x, int y){
        if(x < 0 || y < 0 || x >= width || y >= height) return null;
        return tiles[y * width + x];
    }

    /** 通过打包坐标取瓦片（对应原版 World.tile(int pos)）。 */
    public Tile tile(int pos){
        if(tiles == null) return null;
        return tile(Pos.x(pos), Pos.y(pos));
    }

    public Tile tileWorld(float x, float y){
        return tile(toTile(x), toTile(y));
    }

    /**
     * 取瓦片并**解析多格链接**（对应原版 {@code World.ltile} 的 {@code tile.block().linked(tile)}）：
     * 卫星瓦片返回其中心瓦片，非多格瓦片返回自身。
     * <p>子弹/爆炸等「以建筑为单位」的判定必须走这里 —— 直接用 {@link #tile} 会拿到卫星瓦片，
     * 而卫星瓦片（{@code BlockPart}）没有实体（{@code entity == null}），
     * 结果多格建筑只有锚点那一格能被打到。
     */
    public Tile ltile(int x, int y){
        Tile tile = tile(x, y);
        return tile == null ? null : tile.link();
    }

    /** 世界坐标 → 瓦片坐标。注意：瓦片 n 覆盖 [n*tilesize, (n+1)*tilesize)，所以这里是向下取整而不是四舍五入。 */
    public int toTile(float coord){
        return (int)Math.floor(coord / tilesize);
    }

    public int width(){
        return width;
    }

    public int height(){
        return height;
    }

    public float unitWidth(){
        return width * tilesize;
    }

    public float unitHeight(){
        return height * tilesize;
    }

    public boolean isGenerating(){
        return generating;
    }

    /** 更新所有带实体的瓦片。 */
    public void updateTiles(){
        updatePower();
        for(Tile tile : tiles){
            if(tile != null && tile.entity != null && tile.block().update){
                //超频计时衰减（对应原版 TileEntity.update 开头）
                tile.entity.updateTimeScale();
                tile.entity.update();
            }
        }
    }

    /** 遍历两点之间的瓦片（Bresenham），返回 true 时停止。 */
    public void raycastEach(int x1, int y1, int x2, int y2, TileRaycaster caster){
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        while(true){
            if(caster.accept(x1, y1)) return;
            if(x1 == x2 && y1 == y2) return;
            int e2 = 2 * err;
            if(e2 > -dy){
                err -= dy;
                x1 += sx;
            }
            if(e2 < dx){
                err += dx;
                y1 += sy;
            }
        }
    }

    /** 查找最近的指定标记方块。 */
    public Tile closestTile(float x, float y, Team team, BlockFlag flag, boolean enemy){
        Tile best = null;
        float bestDst = Float.MAX_VALUE;

        for(Tile tile : tiles){
            if(tile == null || tile.block() == Blocks.air || !tile.block().flags.contains(flag)) continue;
            Team tileTeam = tile.getTeam();
            if(enemy ? !team.isEnemy(tileTeam) : tileTeam != team) continue;

            float dst = Mathf.dst2(x, y, tile.getX(), tile.getY());
            if(dst < bestDst){
                bestDst = dst;
                best = tile;
            }
        }

        return best;
    }

    /** 全局电网列表（电网重建时替换）。 */
    public final com.badlogic.gdx.utils.Array<com.phoenix.game.world.blocks.power.PowerGraph> powerGraphs = new com.badlogic.gdx.utils.Array<>();

    /** 重建全部电网并更新一次电力分配。 */
    public void rebuildPowerGraphs(){
        powerGraphs.clear();
        for(Tile tile : tiles){
            if(tile == null || tile.entity == null || tile.entity.power == null) continue;
            if(tile.entity.power.graph != null) continue;

            com.phoenix.game.world.blocks.power.PowerGraph graph = new com.phoenix.game.world.blocks.power.PowerGraph();
            graph.reflow(tile);
            powerGraphs.add(graph);
        }
    }

    /** 每帧更新全部电网。 */
    public void updatePower(){
        for(com.phoenix.game.world.blocks.power.PowerGraph graph : powerGraphs){
            graph.update();
        }
    }

    public void notifyChanged(Tile tile){
        //建筑/地形改变：刷新寻路网格，让流场重算
        if(Vars.pathfinder != null){
            Vars.pathfinder.updateTile(tile);
        }
        rebuildPowerGraphs();
        //广播瓦片变化：小地图据此更新对应像素（对应原版 TileChangeEvent）。
        //createMap 期间每个 setBlock 都会走到这里，此时小地图的 pixmap 还是旧世界的，
        //MinimapRenderer.update 里靠 pixmap == null / 越界判断早退。
        Events.fire(new EventType.TileChangeEvent(tile));
    }

    /** 瓦片射线回调。 */
    public interface TileRaycaster{
        /** @return true 表示停止遍历 */
        boolean accept(int x, int y);
    }
}
