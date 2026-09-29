package com.phoenix.game.entities.units;

import com.badlogic.gdx.math.MathUtils;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Items;
import com.phoenix.game.entities.effect.ItemTransfer;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.*;

public class UnitDrops{
    private static Item[] dropTable;

    public static void dropItems(BaseUnit unit){
        //items only dropped in waves for enemy team
        //对应原版 !state.rules.unitDrops 判定（T2 批已补该字段）
        if(unit.getTeam() != state.rules.waveTeam || !state.rules.unitDrops){
            return;
        }

        //原版 getClosestEnemyCore() 返回 TileEntity；本项目返回核心瓦片
        Tile core = unit.getClosestEnemyCore();

        if(core == null || Mathf.dst(core.worldx(), core.worldy(), unit.x, unit.y) > Vars.mineTransferRange){
            return;
        }

        if(dropTable == null){
            dropTable = new Item[]{Items.titanium, Items.silicon, Items.lead, Items.copper};
        }

        for(int i = 0; i < 3; i++){
            for(int j = 0; j < dropTable.length; j++){
                Item item = dropTable[j];
                //only drop unlocked items
                //原版 entities/units/UnitDrops.java:34：if(!Vars.headless && !Vars.data.isUnlocked(item))
                if(!Vars.headless && !Vars.data.isUnlocked(item)){
                    continue;
                }

                //原版写 Mathf.chance(0.03)（arc 的 chance 收 double）；本项目 Mathf.chance 只收 float
                if(Mathf.chance(0.03f)){
                    int amount = MathUtils.random(20, 40);
                    //原版 core.tile.block().acceptStack(item, amount, core.tile, null)：算"核心还能收下多少"。
                    //（此前 Block 没有 acceptStack、只能在调用点展开 getMaximumAccepted 的判据；
                    //  "玩家背包/单位背包"批次已把 acceptStack 补回 Block → 现在照原版写。）
                    int accepted = core.entity == null || core.entity.items == null ? 0
                        : core.block().acceptStack(item, amount, core, null);
                    if(accepted > 0){
                        //原版走 Call.transferItemTo(...) 网络调用；本项目用本地物品转移特效（同时入核心库存）
                        ItemTransfer.transferItemTo(item, accepted, unit.x + Mathf.range(2f), unit.y + Mathf.range(2f), core);
                    }
                }
            }
        }
    }
}
