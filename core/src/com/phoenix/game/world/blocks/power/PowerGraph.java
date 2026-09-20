package com.phoenix.game.world.blocks.power;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;

/** 电力网络：按四邻域连通的一组带电建筑。
 * 每帧统计各建筑的发电/耗电，统一按满足率分配。 */
public class PowerGraph{
    private final Array<Tile> all = new Array<>();
    /** 本网络里的电池（储能型建筑），每帧重算。 */
    private final Array<Tile> batteries = new Array<>();

    private float lastProduced, lastNeeded, lastSatisfaction;

    /** BFS 临时缓冲，避免每次重建都新建数组。 */
    private final Array<Tile> queue = new Array<>();
    private final Array<Tile> conns = new Array<>();
    private final IntArray closed = new IntArray();

    /**
     * 重建本网络：从起点 BFS 所有连通的带电建筑。
     * <p>连通性完全交给 {@link com.phoenix.game.world.Block#getPowerConnections}，
     * 这样多格建筑（走邻接表，含卫星格解析）、电力节点（走连线范围）、二极管（单向）能共用一套 BFS。
     */
    public void reflow(Tile start){
        all.clear();
        queue.clear();
        closed.clear();

        queue.add(start);
        closed.add(start.pos());

        while(queue.size > 0){
            Tile tile = queue.pop();
            if(tile.entity == null || tile.entity.power == null) continue;
            all.add(tile);
            tile.entity.power.graph = this;

            conns.clear();
            tile.block().getPowerConnections(tile, conns);

            for(int i = 0; i < conns.size; i++){
                Tile next = conns.get(i);
                if(next == null || next.entity == null || next.entity.power == null) continue;
                if(closed.contains(next.pos())) continue;

                closed.add(next.pos());
                queue.add(next);
            }
        }
    }

    /** @return 本网络电池里存着的总电量（对应原版 {@code getBatteryStored}）。 */
    public float getBatteryStored(){
        float sum = 0f;
        for(int i = 0; i < batteries.size; i++){
            sum += batteries.get(i).entity.power.stored;
        }
        return sum;
    }

    /** @return 本网络电池的总容量（对应原版 {@code getTotalBatteryCapacity}）。 */
    public float getTotalBatteryCapacity(){
        float sum = 0f;
        for(int i = 0; i < batteries.size; i++){
            sum += batteryCapacity(batteries.get(i));
        }
        return sum;
    }

    /**
     * 从电池里取出 amount 电量（对应原版 {@code useBatteries}）。
     * @return 实际取出的电量（储量不足时更少）
     */
    public float useBatteries(float amount){
        if(amount <= 0f) return 0f;
        float used = Math.min(amount, getBatteryStored());
        if(used > 0f) distribute(batteries, used, false);
        return used;
    }

    /**
     * 往电池里充入 amount 电量（对应原版 {@code chargeBatteries}）。
     * @return 实际充入的电量（空间不足时更少）
     */
    public float chargeBatteries(float amount){
        if(amount <= 0f) return 0f;
        float free = getTotalBatteryCapacity() - getBatteryStored();
        float charged = Math.min(amount, Math.max(free, 0f));
        if(charged > 0f) distribute(batteries, charged, true);
        return charged;
    }

    /** 统计并分配本帧电力。先清空旧统计，让建筑上报生产/需求，再按比例分配，最后处理电池充放电。 */
    public void update(){
        //重置统计
        for(Tile tile : all){
            if(tile.entity != null && tile.entity.power != null){
                tile.entity.power.reset();
            }
        }

        //第一遍：统计生产与需求，并收集电池
        float produced = 0f, needed = 0f;
        batteries.clear();

        for(Tile tile : all){
            if(tile.entity == null || tile.entity.power == null) continue;

            float p = tile.block().getPowerProduction(tile);
            float n = tile.block().getPowerNeeded(tile);

            tile.entity.power.produced = p;
            tile.entity.power.needed = n;
            produced += p;
            needed += n;

            //储能型（电池）单独收集，参与充放电
            float cap = batteryCapacity(tile);
            if(cap > 0f) batteries.add(tile);
        }

        //第二遍：电池充放电（把多余的电存起来 / 不够时取出来）
        float net = produced - needed;

        if(net > 0f && batteries.size > 0){
            float free = 0f;
            for(int i = 0; i < batteries.size; i++){
                Tile t = batteries.get(i);
                free += batteryCapacity(t) - t.entity.power.stored;
            }

            float store = Math.min(net, free);
            if(store > 0f){
                distribute(batteries, store, true);
                produced -= store;
            }
        }else if(net < 0f && batteries.size > 0){
            float stored = 0f;
            for(int i = 0; i < batteries.size; i++){
                stored += batteries.get(i).entity.power.stored;
            }

            float draw = Math.min(-net, stored);
            if(draw > 0f){
                distribute(batteries, draw, false);
                produced += draw;
            }
        }

        //满足率：电够→1，不够→产出/需求
        lastProduced = produced;
        lastNeeded = needed;
        lastSatisfaction = needed <= 0.0001f ? 1f : Mathf.clamp(produced / needed);

        //第三遍：下发满足率
        for(Tile tile : all){
            if(tile.entity != null && tile.entity.power != null){
                tile.entity.power.status = lastSatisfaction;
            }
        }
    }

    /** @return 该建筑的储能容量（非电池返回 0）。 */
    private float batteryCapacity(Tile tile){
        if(tile.block().consumes == null || !tile.block().consumes.hasPower()) return 0f;

        com.phoenix.game.world.consumers.ConsumePower cp =
            tile.block().consumes.get(com.phoenix.game.world.consumers.ConsumeType.power);
        return cp.buffered ? cp.capacity : 0f;
    }

    /**
     * 把 amount 的电力按各电池的剩余空间/已有储量比例分摊。
     * @param store true = 充入，false = 取出
     */
    private void distribute(Array<Tile> batteries, float amount, boolean store){
        float total = 0f;
        for(int i = 0; i < batteries.size; i++){
            Tile t = batteries.get(i);
            total += store ? (batteryCapacity(t) - t.entity.power.stored) : t.entity.power.stored;
        }
        if(total <= 0.0001f) return;

        for(int i = 0; i < batteries.size; i++){
            Tile t = batteries.get(i);
            float share = store ? (batteryCapacity(t) - t.entity.power.stored) : t.entity.power.stored;
            float delta = amount * (share / total);
            t.entity.power.stored = store
                ? Math.min(t.entity.power.stored + delta, batteryCapacity(t))
                : Math.max(t.entity.power.stored - delta, 0f);
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