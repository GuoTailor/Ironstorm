package com.phoenix.game.world.blocks.power;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

/**
 * 电力二极管。参照 Mindustry mindustry.world.blocks.power.PowerDiode 移植。
 * <p>**单向输电**，做法很取巧（与原版一致）：二极管自己**不带电力模块**（{@code hasPower} 保持 false），
 * 于是电网 BFS 走到它这里就断了 —— 背面与正面天然是两张独立电网，不会因为贴在一起而合并。
 * 再由实体每帧比较两侧电池的储量百分比，把背面多出来的那部分（差值的一半）搬去正面。
 * <p>因此**两侧都至少要有储能**（电池）才会输电，与原版判据相同。
 */
public class PowerDiode extends Block{
    public PowerDiode(String name){
        super(name);
        update = true;
        solid = true;
        rotate = true;
        health = 260;
        entityType = DiodeEntity::new;
    }

    public class DiodeEntity extends TileEntity{
        @Override
        public void update(){
            Tile back = tile.getNearbyLink((tile.rotation() + 2) % 4);
            Tile front = tile.getNearbyLink(tile.rotation());

            if(back == null || front == null) return;
            if(back.entity == null || back.entity.power == null) return;
            if(front.entity == null || front.entity.power == null) return;
            if(back.getTeam() != front.getTeam()) return;

            PowerGraph backGraph = back.entity.power.graph, frontGraph = front.entity.power.graph;
            if(backGraph == null || frontGraph == null || backGraph == frontGraph) return;

            float backCap = backGraph.getTotalBatteryCapacity(), frontCap = frontGraph.getTotalBatteryCapacity();
            if(backCap <= 0f || frontCap <= 0f) return;

            //两侧电池的"充满百分比"，只有背面更满才往前送
            float backStored = backGraph.getBatteryStored() / backCap;
            float frontStored = frontGraph.getBatteryStored() / frontCap;

            if(backStored > frontStored){
                //送差值的一半，且不超过正面还能装下的量
                float amount = backGraph.getBatteryStored() * (backStored - frontStored) / 2f;
                amount = Mathf.clamp(amount, 0f, frontCap * (1f - frontStored));

                backGraph.useBatteries(amount);
                frontGraph.chargeBatteries(amount);
            }
        }
    }
}
