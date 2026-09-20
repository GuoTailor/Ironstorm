package com.phoenix.game.world.blocks.power;

import com.phoenix.game.content.Items;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.modules.ItemModule;

/**
 * 烧物品的发电机基类。
 * <p>参照 Mindustry mindustry.world.blocks.power.ItemLiquidGenerator 移植；
 * 因本工程未移植液体，砍掉液体分支后改名 ItemGenerator。
 * <p>工作机制（与原版一致）：库存里有"效率达标"的物品时取一个，进入 {@link #itemDuration} 帧的燃烧期，
 * 期间发电量 = {@code powerProduction × 该物品的效率}；烧完才取下一个，所以断料会立刻停发。
 * <p>子类只需实现 {@link #getItemEfficiency}：燃烧发电机用可燃性，衰变发电机用放射性。
 */
public class ItemGenerator extends Block{
    /** 一个物品的燃烧时长（帧）。对应原版 {@code itemDuration}。 */
    public float itemDuration = 120f;
    /** 效率低于该值的物品不接受。对应原版 {@code minItemEfficiency}。 */
    public float minItemEfficiency = 0.2f;

    public ItemGenerator(String name){
        super(name);
        update = true;
        solid = true;
        hasPower = true;
        hasItems = true;
        itemCapacity = 10;
        entityType = GeneratorEntity::new;
    }

    /** @return 该物品的发电效率（0 表示不能当燃料）。 */
    public float getItemEfficiency(Item item){
        return 0f;
    }

    /**
     * 取库存里第一个效率达标的物品（对应原版 {@code ConsumeItemFilter}）。
     * <p>按 {@code Items.all} 的固定顺序扫描，保证同一批库存每次选中的燃料一致。
     * @return 可用燃料；没有则 null
     */
    public Item acceptedItem(ItemModule items){
        for(int i = 0; i < Items.all.size; i++){
            Item item = Items.all.get(i);
            if(items.has(item, 1) && getItemEfficiency(item) >= minItemEfficiency){
                return item;
            }
        }
        return null;
    }

    /** 发电量按当前燃料的效率缩放（对应原版 {@code powerProduction * productionEfficiency}）。 */
    @Override
    public float getPowerProduction(Tile tile){
        if(!(tile.entity instanceof GeneratorEntity)) return 0f;
        return powerProduction * ((GeneratorEntity)tile.entity).productionEfficiency;
    }

    public class GeneratorEntity extends TileEntity{
        /** 当前燃料剩余燃烧进度（1 → 0）。 */
        public float generateTime;
        /** 发电效率 = 当前燃料的效率。 */
        public float productionEfficiency;

        @Override
        public void update(){
            //烧完了才取下一个：所以燃烧期内即使断料也还能把这一份烧完
            if(generateTime <= 0f && items != null && items.total() > 0){
                Item item = acceptedItem(items);
                if(item != null){
                    items.remove(item, 1);
                    productionEfficiency = getItemEfficiency(item);
                    generateTime = 1f;
                }
            }

            if(generateTime > 0f){
                generateTime = Math.max(0f, generateTime - delta() / itemDuration);

                //运转特效：间歇冒火星（对应原版燃烧发电机的火焰粒子）
                if(com.phoenix.game.math.Mathf.chance(0.15f * com.phoenix.game.core.Time.delta())){
                    com.phoenix.game.entities.Effects.effect(com.phoenix.game.content.Fx.fireSmoke,
                        com.phoenix.game.graphics.Pal.lightFlame,
                        block.centerX(tile) + com.phoenix.game.math.Mathf.range(2f), block.centerY(tile) + com.phoenix.game.math.Mathf.range(2f), 0f);
                }
            }else{
                productionEfficiency = 0f;
            }
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeFloat(generateTime);
            out.writeFloat(productionEfficiency);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            generateTime = in.readFloat();
            productionEfficiency = in.readFloat();
        }
    }
}
