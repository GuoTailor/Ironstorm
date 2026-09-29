package com.phoenix.game.world.modules;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.consumers.Consume;

/**
 * 消耗状态模块。参照 Mindustry mindustry.world.modules.ConsumeModule 移植。
 * <p>挂在实体上（{@code entity.cons}），每帧聚合方块 {@code consumes} 的判定结果：
 * <ul>
 *     <li>{@link #valid()}：**非可选**消耗是否都满足（且过 {@code shouldConsume} 门槛）—— 方块能否工作；</li>
 *     <li>{@link #optionalValid()}：非可选满足，**且**可选（加成）消耗也满足。</li>
 * </ul>
 * <p>消耗器自身的 {@code update()}（如扣液体）只在 {@code shouldConsume && productionValid && 上帧也有效 && 本消耗器有效}
 * 时才调用 —— 对应原版 {@code ConsumeModule.update} 的 {@code docons && cons.isUpdate() && prevValid && cons.valid(entity)}。
 * <p>用法：工作逻辑里判 {@code entity.cons.valid()}，扣料时调 {@code entity.cons.trigger()}。
 * <p>注意：不继承 {@link BlockModule}——后者要求实现存档序列化（write/read），而消耗状态是每帧重算的派生数据，无需存盘。
 */
public class ConsumeModule{
    private final TileEntity entity;
    /** 非可选消耗是否都满足（对外还要叠加 {@code shouldConsume} 门槛，见 {@link #valid()}）。 */
    private boolean valid;
    /** 可选（加成）消耗是否都满足。 */
    private boolean optionalValid;

    public ConsumeModule(TileEntity entity){
        this.entity = entity;
    }

    public boolean valid(){
        return valid && entity.block().shouldConsume(entity.tile);
    }

    /** @return 非可选满足（含 {@code shouldConsume} 门槛），且任意可选（加成）也满足。 */
    public boolean optionalValid(){
        return valid() && optionalValid;
    }

    /**
     * 扣一次料（合成完成/开火时调用）。
     * <p>照原版遍历 {@code consumes.all()} 全部消耗器 —— **不跳过**可选加成类。
     */
    public void trigger(){
        Block block = entity.block();
        if(block.consumes == null) return;

        for(Consume cons : block.consumes.all()){
            cons.trigger(entity);
        }
    }

    /** 每帧刷新（由 {@code World.updateTiles} 统一调用）。 */
    public void update(){
        Block block = entity.block();
        if(block.consumes == null) return;

        //敌方作弊波次：该建筑不消耗任何资源，直接判有效（对应原版 ConsumeModule.update 开头）
        if(entity.tile.isEnemyCheat()){
            valid = optionalValid = true;
            return;
        }

        //上一帧的"能否工作"；只有它也为真时才更新消耗器（避免状态抖动时反复扣料）
        boolean prevValid = valid();
        valid = true;
        optionalValid = true;
        //消耗门槛（对应原版 docons）：库存满 / 不在生产时，本帧既不扣料也不更新消耗器
        boolean docons = block.shouldConsume(entity.tile) && block.productionValid(entity.tile);

        //非可选消耗：决定方块能否工作
        for(Consume cons : block.consumes.all()){
            if(cons.isOptional()) continue;

            if(docons && cons.isUpdate() && prevValid && cons.valid(entity)){
                cons.update(entity);
            }

            valid &= cons.valid(entity);
        }

        //可选（加成）消耗：不决定能否工作，只决定加成是否生效
        for(Consume cons : block.consumes.optionals()){
            if(docons && cons.isUpdate() && prevValid && cons.valid(entity)){
                cons.update(entity);
            }

            optionalValid &= cons.valid(entity);
        }
    }
}
