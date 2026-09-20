package com.phoenix.game.world.consumers;

import com.phoenix.game.entities.type.TileEntity;

/**
 * 资源消耗器基类。参照 Mindustry mindustry.world.consumers.Consume 移植。
 * <p>定义方块运行所需的一种资源（物品/液体/电力）。方块的 {@code consumes} 里登记消耗器后，
 * 实体每帧用 {@code entity.cons.valid()} 判断"能否工作"，用 {@code entity.cons.trigger()} 扣料。
 */
public abstract class Consume{
    /** true 表示该消耗不参与"能否工作"的判定（可选加成，如相织机加速）。 */
    protected boolean optional;
    /** true 表示该消耗是"加成输入"（可选且提供增益）。 */
    protected boolean booster;
    /** false 表示不需要每帧 update。 */
    protected boolean update = true;

    public Consume optional(boolean optional, boolean boost){
        this.optional = optional;
        this.booster = boost;
        return this;
    }

    public Consume boost(){
        return optional(true, true);
    }

    public Consume update(boolean update){
        this.update = update;
        return this;
    }

    public boolean isOptional(){
        return optional;
    }

    public boolean isBoost(){
        return booster;
    }

    public boolean isUpdate(){
        return update;
    }

    /** @return 本消耗器属于哪一类（决定它在 {@link Consumers} 里的槽位）。 */
    public abstract ConsumeType type();

    /** 手动触发一次消耗（如合成完成时扣料）。默认什么都不做。 */
    public void trigger(TileEntity entity){
    }

    /** 每帧更新（如电力状态写入）。默认什么都不做。 */
    public void update(TileEntity entity){
    }

    /** @return 当前是否满足消耗条件（不足则方块不能工作）。 */
    public abstract boolean valid(TileEntity entity);
}
