package com.phoenix.game.world.consumers;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Bits;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.util.ArcAnnotate.*;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.BlockStats;

/**
 * 单一液体消耗器。参照 Mindustry mindustry.world.consumers.ConsumeLiquid 移植。
 * <p>只接受 {@link #liquid} 这一种液体；每帧从建筑库存里扣 {@code amount}（乘 delta）。
 * <p>刻意降级：原版 {@code build(Tile, Table)} 用 {@code ReqImage} 在配置界面显示液体图标
 * （{@code ui/ReqImage} 已存在，但 {@code Liquid} 继承 {@code MappableContent} 没有 {@code icon(Cicon)}，
 * 且本项目尚无消耗器配置界面调用路径）。
 */
public class ConsumeLiquid extends ConsumeLiquidBase{
    public final @NonNull Liquid liquid;

    public ConsumeLiquid(Liquid liquid, float amount){
        super(amount);
        this.liquid = liquid;
    }

    protected ConsumeLiquid(){
        this(null, 0f);
    }

    @Override
    public void applyLiquidFilter(Bits filter){
        filter.set(liquid.id);
    }

    /**
     * 配置界面里的液体图标行。
     * <p>刻意降级：原版 {@code table.add(new ReqImage(liquid.icon(Cicon.medium), () -> valid(tile.entity))).size(8 * 4)}；
     * {@code ReqImage} 已存在，但 {@code Liquid.icon(Cicon)} 缺失（{@code Liquid} 继承 {@code MappableContent}）
     * 且无调用路径，故只保留签名。
     */
    public void build(Tile tile, Table table){
        //刻意降级：Liquid 无 icon(Cicon)，且无消耗器配置界面调用路径
    }

    /** @return 配置界面用的图标名（对应原版 {@code Consume.getIcon}）。 */
    public String getIcon(){
        return "icon-liquid-consume";
    }

    @Override
    public void update(TileEntity entity){
        entity.liquids.remove(liquid, Math.min(use(entity), entity.liquids.get(liquid)));
    }

    @Override
    public boolean valid(TileEntity entity){
        return entity != null && entity.liquids != null && entity.liquids.get(liquid) >= use(entity);
    }

    @Override
    public void display(BlockStats stats){
        stats.add(booster ? BlockStat.booster : BlockStat.input, liquid, amount * timePeriod, timePeriod == 60);
    }
}
