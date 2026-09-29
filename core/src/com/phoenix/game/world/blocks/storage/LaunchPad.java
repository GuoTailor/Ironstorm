package com.phoenix.game.world.blocks.storage;

import com.phoenix.game.Vars;
import com.phoenix.game.content.Fx;
import com.phoenix.game.content.Items;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.StatUnit;

/**
 * 发射台。参照 Mindustry mindustry.world.blocks.storage.LaunchPad 移植。
 * <p>装满后按 {@link #launchTime} 的间隔把整仓物品"发射"进全局库存
 * （{@link com.phoenix.game.game.GlobalData}），供后续对局取用。
 * <p>原版要求 {@code world.isZone()}（只有战役区域能发射），本项目简化为总是允许。
 */
public class LaunchPad extends StorageBlock{
    /** 发射间隔（帧）；原版类里无默认值，由 Blocks.java 给（launch-pad 60*16、launch-pad-large 60*14）。 */
    public float launchTime;

    public LaunchPad(String name){
        super(name);
        update = true;
        hasItems = true;
        solid = true;
        //不写 itemCapacity / health：原版 LaunchPad 类都不设，由 Blocks.java 给
        //（itemCapacity：100 / 250；health：size3 默认 360 / size4 默认 640。
        // ⚠ 本项目 StorageBlock 硬编码 health = 120，size 默认不可达 → health 由 Blocks 显式给）
        entityType = LaunchPadEntity::new;
    }

    @Override
    public void setStats(){
        super.setStats();

        //对应原版 LaunchPad.setStats
        stats.add(BlockStat.launchTime, launchTime / 60f, StatUnit.seconds);
    }

    /**
     * 只收"材料类"物品（原版按 {@code ItemType} 过滤；本项目没有 ItemType，简化为容量判定）。
     * <p>注意必须覆写：父类 {@code StorageBlock.acceptItem} 会把 entity 强转成 StorageEntity，
     * 而本类用的是子类实体。
     */
    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        return tile.entity != null && tile.entity.items != null
            && tile.entity.items.total() < itemCapacity;
    }

    /**
     * 发射台实体：多一个发射计时（继承 StorageEntity 以复用父类的"被核心链接"语义）。
     * <p>注意逻辑写在实体里而不是 {@code Block.update(tile)}——本项目统一用实体内部类的 update()
     * （原版把方块逻辑放在 Block.update(tile)，两边约定不同）。
     */
    public class LaunchPadEntity extends StorageEntity{
        /** 距下次发射的计时。 */
        public float launchTimer;

        @Override
        public void update(){
            launchTimer += delta();

            if(Vars.data == null || items.total() < itemCapacity) return;
            //原版 LaunchPad.java:76：无电不发射（cons.valid()）
            if(!cons.valid()) return;
            if(launchTimer < launchTime) return;

            launchTimer = 0f;

            //整仓发射进全局库存
            for(int i = 0; i < Items.all.size; i++){
                Item item = Items.all.get(i);
                int used = items.get(item);
                if(used <= 0) continue;

                Vars.data.addItem(item, used);
                items.remove(item, used);
            }

            Effects.effect(Fx.explosion, centerX(tile), centerY(tile), 0f);
        }
    }
}
