package com.phoenix.game.world.blocks.power;

import com.phoenix.game.content.Items;
import com.phoenix.game.entities.Damage;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

/**
 * 钍反应堆。参照 Mindustry mindustry.world.blocks.power.NuclearReactor（原版 thorium-reactor）移植。
 * <p>烧钍发电：燃料越多发电效率越高（{@code productionEfficiency = 库存/容量}），
 * 每 {@link #itemDuration} 帧消耗一个钍；被打爆时核爆（原版还有"堆芯升温→熔毁自爆"，见下）。
 * <p>简化：原版靠低温流体冷却来控制堆芯 {@code heat}，heat 到 1 就熔毁自爆。
 * 本工程未移植液体系统，因此**堆芯不会升温**：反应堆只会在被摧毁时爆炸，
 * 判据退化成"库存钍 ≥ 5 才爆"（原版是 {@code fuel < 5 && heat < 0.5} 则不爆）。
 */
public class ThoriumReactor extends Block{
    /** 消耗一个燃料所需帧数。 */
    public float itemDuration = 360f;
    /** 爆炸半径（像素，原版是世界单位）。 */
    public float explosionRadius = 40f;
    /** 爆炸基础伤害（原版实际按 ×4 施加）。 */
    public float explosionDamage = 1350f;
    /** 库存燃料少于该值时被摧毁不会爆炸。 */
    public int explosionMinFuel = 5;

    public ThoriumReactor(String name){
        super(name);
        update = true;
        solid = true;
        size = 3;
        health = 700;
        //发电方块必须显式开启：Block.init() 只会因为 consumes 里有电力消耗器而自动开启 hasPower，
        //而反应堆只烧物品不耗电，漏了这行就不会分配 PowerModule（等于不在电网上、发不出电）
        hasPower = true;
        hasItems = true;
        itemCapacity = 30;
        consumes.items(new ItemStack(Items.thorium, 1));
        entityType = ReactorEntity::new;
    }

    /** 发电量按燃料充满度缩放（对应原版 {@code powerProduction * productionEfficiency}）。 */
    @Override
    public float getPowerProduction(Tile tile){
        if(!(tile.entity instanceof ReactorEntity)) return 0f;
        return powerProduction * ((ReactorEntity)tile.entity).productionEfficiency;
    }

    public class ReactorEntity extends TileEntity{
        /** 发电效率 = 库存燃料 / 容量。 */
        public float productionEfficiency;
        /** 距下次消耗燃料的计时（帧）。 */
        public float fuelTimer;
        /** 防止爆炸伤害把同一座堆反复引爆（kill → 爆炸 → 再次伤害 → kill 的递归）。 */
        private boolean exploded;

        @Override
        public void update(){
            int fuel = items == null ? 0 : items.get(Items.thorium);
            productionEfficiency = (float)fuel / itemCapacity;

            if(fuel > 0){
                fuelTimer += delta();
                if(fuelTimer >= itemDuration){
                    fuelTimer = 0f;
                    cons.trigger();
                }

                //运转特效：间歇冒烟（对应原版钍反应堆的烟雾）
                if(com.phoenix.game.math.Mathf.chance(0.08f * delta())){
                    com.phoenix.game.entities.Effects.effect(com.phoenix.game.content.Fx.smokeCloud,
                        block.centerX(tile), block.centerY(tile) + 6f, 0f);
                }
            }
        }

        @Override
        public void kill(){
            //先核爆再移除：tile.remove() 之后瓦片上已无方块信息，坐标也就取不到了
            if(!exploded && tile != null){
                exploded = true;

                int fuel = items == null ? 0 : items.get(Items.thorium);
                if(fuel >= explosionMinFuel){
                    float cx = block.centerX(tile), cy = block.centerY(tile);
                    //核爆特效（对应原版 Fx.reactorExplosion）
                    Effects.effect(com.phoenix.game.content.Fx.reactorExplosion, cx, cy);
                    Effects.shake(6f, 16f, cx, cy);
                    Damage.damageAll(cx, cy, explosionRadius, explosionDamage * 4f);
                }
            }
            super.kill();
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeFloat(fuelTimer);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            fuelTimer = in.readFloat();
            //productionEfficiency 由库存重算；exploded 是瞬时防重入标记，不入档
        }
    }
}
