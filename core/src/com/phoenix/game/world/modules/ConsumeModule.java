package com.phoenix.game.world.modules;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.consumers.Consume;

/**
 * 消耗状态模块。参照 Mindustry mindustry.world.modules.ConsumeModule 移植。
 * <p>挂在实体上（{@code entity.cons}），每帧聚合方块 {@code consumes} 的判定结果：
 * <ul>
 *     <li>{@link #valid()}：**非可选**消耗是否都满足 —— 方块能否工作；</li>
 *     <li>{@link #optionalValid()}：非可选满足，或某个可选（加成）消耗也满足。</li>
 * </ul>
 * <p>用法：工作逻辑里判 {@code entity.cons.valid()}，扣料时调 {@code entity.cons.trigger()}。
 * <p>注意：不继承 {@link BlockModule}——后者要求实现存档序列化（write/read），而消耗状态是每帧重算的派生数据，无需存盘。
 */
public class ConsumeModule{
    private final TileEntity entity;
    /** 非可选消耗是否都满足。 */
    private boolean valid;
    /** 是否有可选消耗满足。 */
    private boolean optionalValid;

    public ConsumeModule(TileEntity entity){
        this.entity = entity;
    }

    public boolean valid(){
        return valid;
    }

    /** @return 非可选满足，或任意可选（加成）也满足。 */
    public boolean optionalValid(){
        return valid || optionalValid;
    }

    /** 扣一次料（合成完成/开火时调用）；跳过"可选加成"类消耗。 */
    public void trigger(){
        Block block = entity.block();
        if(block.consumes == null) return;

        for(Consume cons : block.consumes.all()){
            if(cons.isOptional() && cons.isBoost()) continue;
            cons.trigger(entity);
        }
    }

    /** 每帧刷新（由 {@code World.updateTiles} 统一调用）。 */
    public void update(){
        Block block = entity.block();
        if(block.consumes == null) return;

        valid = true;
        optionalValid = false;

        for(Consume cons : block.consumes.all()){
            boolean ok = cons.valid(entity);
            if(!ok && !cons.isOptional()) valid = false;
            if(ok && cons.isOptional()) optionalValid = true;
        }

        //消耗器自身的每帧更新（如电力状态回写）
        for(Consume cons : block.consumes.all()){
            if(cons.isUpdate()) cons.update(entity);
        }
    }
}
