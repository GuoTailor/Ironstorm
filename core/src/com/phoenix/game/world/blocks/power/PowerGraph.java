package com.phoenix.game.world.blocks.power;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;

/** 电力网络：按四邻域连通的一组带电建筑。
 * 每帧统计各建筑的发电/耗电，统一按满足率分配。 */
public class PowerGraph{
    private final Array<Tile> all = new Array<>();

    private float lastProduced, lastNeeded, lastSatisfaction;

    /** 重建本网络（从起点 BFS 四邻域带电建筑）。 */
    public void reflow(Tile start){
        all.clear();

        IntArray closed = new IntArray();
        Array<Tile> queue = new Array<>();
        queue.add(start);
        closed.add(start.pos());

        while(queue.size > 0){
            Tile tile = queue.pop();
            if(tile.entity == null || tile.entity.power == null) continue;
            all.add(tile);
            tile.entity.power.graph = this;

            for(com.badlogic.gdx.math.GridPoint2 dir : Geometry.d4){
                Tile next = tile.getNearby(dir.x, dir.y);
                if(next != null && next.entity != null && next.entity.power != null && !closed.contains(next.pos())){
                    closed.add(next.pos());
                    queue.add(next);
                }
            }
        }
    }

    /** 统计并分配本帧电力。先清空旧统计，让建筑上报生产/需求，再按比例分配。 */
    public void update(){
        //重置统计
        for(Tile tile : all){
            if(tile.entity != null && tile.entity.power != null){
                tile.entity.power.reset();
            }
        }

        //第一遍：统计生产与需求
        for(Tile tile : all){
            if(tile.entity == null || tile.entity.power == null) continue;
            tile.entity.power.produced = tile.block().getPowerProduction(tile);
            tile.entity.power.needed = tile.block().getPowerNeeded(tile);
            lastProduced += tile.entity.power.produced;
            lastNeeded += tile.entity.power.needed;
        }

        //满足率：电够→1，不够→需求/产出
        lastSatisfaction = lastNeeded <= 0.0001f ? 1f : Mathf.clamp(lastProduced / lastNeeded);

        //第二遍：下发满足率
        for(Tile tile : all){
            if(tile.entity != null && tile.entity.power != null){
                tile.entity.power.status = lastSatisfaction;
            }
        }
    }

    /** 移除一个建筑（拆除时调）。 */
    public void remove(Tile tile){
        all.removeValue(tile, true);
    }

    public int size(){
        return all.size;
    }

    public float getSatisfaction(){
        return lastSatisfaction;
    }

    public float getPowerProduced(){
        return lastProduced;
    }

    public float getPowerNeeded(){
        return lastNeeded;
    }

    /** @return 本网络是否用电（有建筑请求电力）。 */
    public boolean hasConsumers(){
        return lastNeeded > 0.0001f;
    }
}