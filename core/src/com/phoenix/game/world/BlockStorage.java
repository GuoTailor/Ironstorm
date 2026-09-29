package com.phoenix.game.world;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Time;
import com.phoenix.game.ctype.UnlockableContent;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.effect.Puddle;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.world.consumers.Consumers;
import com.phoenix.game.world.meta.BlockBars;
import com.phoenix.game.world.meta.BlockStats;

/**
 * 可存储内容的方块基类（物品/液体/电力）。参照 Mindustry mindustry.world.BlockStorage 移植。
 * <p>原版继承链是 {@code Block extends BlockStorage extends UnlockableContent}，
 * 库存/液体流动/卸货这些逻辑都写在 BlockStorage 里。
 *
 * <p><b>与本项目的差异（重要）</b>：本项目的 {@code Block} **直接继承 UnlockableContent**，
 * 上述库存逻辑（{@code acceptItem}/{@code tryDump}/{@code offloadNear}/{@code tryMoveLiquid}…）
 * 已经就地写在 {@code world/Block.java} 里了（见该类注释与 {@code 转换.md} 的"建造系统"一节）。
 * 所以本类目前**不在继承链上**，属于"原版对照 + 后续拆分基类时的落点"：
 * 等把 Block 里的库存逻辑下移到本类后，把 Block 改成 {@code extends BlockStorage} 即可。
 * 在拆分之前，本类不会被实例化，只保证语义与原版一致。
 *
 * <p>落地下来的几处退化：
 * <ul>
 *   <li>（B 类，缺依赖）{@code TileEntity.noSleep()}（原版让方块跳过休眠优化）→ 本项目没有休眠机制，整段去掉。</li>
 *   <li>（C 类，等价替代）{@code TileEntity.damage(float)} → 本项目实体入口叫 {@code handleDamage(float)}。</li>
 *   <li>（C 类，等价替代）{@code TileEntity.proximity()} 是方法，本项目是公开字段 {@code proximity}。</li>
 *   <li>（C 类，等价替代）{@code ItemModule.has(Item)} 是单参重载，本项目只有 {@code has(Item, int)}（传 1 等值）。</li>
 * </ul>
 */
public abstract class BlockStorage extends UnlockableContent{
    public boolean hasItems;
    public boolean hasLiquids;
    public boolean hasPower;

    public boolean outputsLiquid = false;
    public boolean consumesPower = true;
    public boolean outputsPower = false;

    public int itemCapacity = 10;
    public float liquidCapacity = 10f;
    public float liquidPressure = 1f;

    public final BlockStats stats = new BlockStats();
    public final BlockBars bars = new BlockBars();
    public final Consumers consumes = new Consumers();

    public BlockStorage(String name){
        super(name);
    }

    public boolean shouldConsume(Tile tile){
        return true;
    }

    public boolean productionValid(Tile tile){
        return true;
    }

    public float getPowerProduction(Tile tile){
        return 0f;
    }

    /** Returns the amount of items this block can accept. */
    public int acceptStack(Item item, int amount, Tile tile, BaseUnit source){
        if(acceptItem(item, tile, tile) && hasItems && (source == null || source.getTeam() == tile.getTeam())){
            return Math.min(getMaximumAccepted(tile, item) - tile.entity.items.get(item), amount);
        }else{
            return 0;
        }
    }

    public int getMaximumAccepted(Tile tile, Item item){
        return itemCapacity;
    }

    /** Remove a stack from this inventory, and return the amount removed. */
    public int removeStack(Tile tile, Item item, int amount){
        if(tile.entity == null || tile.entity.items == null) return 0;
        amount = Math.min(amount, tile.entity.items.get(item));
        //TODO 原版：tile.entity.noSleep()（本项目没有方块休眠机制，去掉）
        tile.entity.items.remove(item, amount);
        return amount;
    }

    /** Handle a stack input. */
    public void handleStack(Item item, int amount, Tile tile, BaseUnit source){
        //TODO 原版：tile.entity.noSleep()（本项目没有方块休眠机制，去掉）
        tile.entity.items.add(item, amount);
    }

    public boolean outputsItems(){
        return hasItems;
    }

    /** Returns offset for stack placement. */
    public void getStackOffset(Item item, Tile tile, Vector2 trns){

    }

    public void onProximityUpdate(Tile tile){
        //TODO 原版：if(tile.entity != null) tile.entity.noSleep()；（本项目没有方块休眠机制，去掉）
    }

    public void handleItem(Item item, Tile tile, Tile source){
        tile.entity.items.add(item, 1);
    }

    public boolean acceptItem(Item item, Tile tile, Tile source){
        //刻意偏离：原版这里还要判 consumes.itemFilters.get(item.id)（只收消耗器声明过的物品），
        //本项目 Block.acceptItem 的默认语义是"有空间就收"（纯产出方方块不覆写就会被相邻同类灌满），
        //**故意不接**这条判据，避免改掉既有玩法（见 转换.md）。
        //注：位图内容现已由各消耗器的 applyItemFilter 完整填充（含 ConsumeItems）—— 只是没有消费点，
        //将来若按原版恢复判据，变化是纯粹的"启用"，不会叠加"位图是空的"这个坑
        return tile.entity.items.get(item) < getMaximumAccepted(tile, item);
    }

    public boolean acceptLiquid(Tile tile, Tile source, Liquid liquid, float amount){
        //判据已照原版补齐（与 Block.acceptLiquid 一致）：本方块声明消耗该液体才收。
        //注意 acceptItem 上方那条"刻意不接 itemFilters"的偏离是**仍然生效**的，两者不同。
        return hasLiquids && tile.entity.liquids.get(liquid) + amount < liquidCapacity && consumes.liquidfilters.get(liquid.id);
    }

    public void handleLiquid(Tile tile, Tile source, Liquid liquid, float amount){
        tile.entity.liquids.add(liquid, amount);
    }

    public void tryDumpLiquid(Tile tile, Liquid liquid){
        //等价替代（C 类）：原版 tile.entity.proximity()（方法）；本项目 TileEntity.proximity 是公开字段
        Array<Tile> proximity = tile.entity.proximity;
        int dump = tile.rotation();

        for(int i = 0; i < proximity.size; i++){
            incrementDump(tile, proximity.size);
            Tile other = proximity.get((i + dump) % proximity.size);
            Tile in = Edges.getFacingEdge(tile, other);

            other = other.block().getLiquidDestination(other, in, liquid);

            if(other != null && other.getTeam() == tile.getTeam() && other.block().hasLiquids && canDumpLiquid(tile, other, liquid) && other.entity.liquids != null){
                float ofract = other.entity.liquids.get(liquid) / other.block().liquidCapacity;
                float fract = tile.entity.liquids.get(liquid) / liquidCapacity;

                if(ofract < fract) tryMoveLiquid(tile, in, other, (fract - ofract) * liquidCapacity / 2f, liquid);
            }
        }
    }

    public boolean canDumpLiquid(Tile tile, Tile to, Liquid liquid){
        return true;
    }

    public void tryMoveLiquid(Tile tile, Tile tileSource, Tile next, float amount, Liquid liquid){
        float flow = Math.min(next.block().liquidCapacity - next.entity.liquids.get(liquid) - 0.001f, amount);

        if(next.block().acceptLiquid(next, tileSource, liquid, flow)){
            next.block().handleLiquid(next, tileSource, liquid, flow);
            tile.entity.liquids.remove(liquid, flow);
        }
    }

    public float tryMoveLiquid(Tile tile, Tile next, boolean leak, Liquid liquid){
        return tryMoveLiquid(tile, next, leak ? 1.5f : 100, liquid);
    }

    public float tryMoveLiquid(Tile tile, Tile next, float leakResistance, Liquid liquid){
        if(next == null) return 0;

        next = next.link();
        next = next.block().getLiquidDestination(next, tile, liquid);

        if(next.getTeam() == tile.getTeam() && next.block().hasLiquids && tile.entity.liquids.get(liquid) > 0f){

            if(next.block().acceptLiquid(next, tile, liquid, 0f)){
                float ofract = next.entity.liquids.get(liquid) / next.block().liquidCapacity;
                float fract = tile.entity.liquids.get(liquid) / liquidCapacity * liquidPressure;
                float flow = Math.min(Mathf.clamp((fract - ofract) * (1f)) * (liquidCapacity), tile.entity.liquids.get(liquid));
                flow = Math.min(flow, next.block().liquidCapacity - next.entity.liquids.get(liquid) - 0.001f);

                if(flow > 0f && ofract <= fract && next.block().acceptLiquid(next, tile, liquid, flow)){
                    next.block().handleLiquid(next, tile, liquid, flow);
                    tile.entity.liquids.remove(liquid, flow);
                    return flow;
                }else if(ofract > 0.1f && fract > 0.1f){
                    Liquid other = next.entity.liquids.current();
                    if((other.flammability > 0.3f && liquid.temperature > 0.7f) || (liquid.flammability > 0.3f && other.temperature > 0.7f)){
                        //等价替代（C 类）：原版是 tile.entity.damage(...)；本项目实体伤害入口叫 handleDamage(float)
                        tile.entity.handleDamage(1 * Time.delta());
                        next.entity.handleDamage(1 * Time.delta());
                        if(Mathf.chance(0.1f * Time.delta())){
                            Effects.effect(Fx.fire, (tile.worldx() + next.worldx()) / 2f, (tile.worldy() + next.worldy()) / 2f);
                        }
                    }else if((liquid.temperature > 0.7f && other.temperature < 0.55f) || (other.temperature > 0.7f && liquid.temperature < 0.55f)){
                        tile.entity.liquids.remove(liquid, Math.min(tile.entity.liquids.get(liquid), 0.7f * Time.delta()));
                        if(Mathf.chance(0.2f * Time.delta())){
                            Effects.effect(Fx.steam, (tile.worldx() + next.worldx()) / 2f, (tile.worldy() + next.worldy()) / 2f);
                        }
                    }
                }
            }
        }else if(leakResistance != 100f && !next.block().solid && !next.block().hasLiquids){
            float leakAmount = tile.entity.liquids.get(liquid) / leakResistance;
            Puddle.deposit(next, tile, liquid, leakAmount);
            tile.entity.liquids.remove(liquid, leakAmount);
        }
        return 0;
    }

    public Tile getLiquidDestination(Tile tile, Tile from, Liquid liquid){
        return tile;
    }

    /**
     * Tries to put this item into a nearby container, if there are no available
     * containers, it gets added to the block's inventory.
     */
    public void offloadNear(Tile tile, Item item){
        //等价替代（C 类）：原版是 tile.entity.proximity()（方法）；本项目 TileEntity.proximity 是公开字段
        Array<Tile> proximity = tile.entity.proximity;
        int dump = tile.rotation();

        for(int i = 0; i < proximity.size; i++){
            incrementDump(tile, proximity.size);
            Tile other = proximity.get((i + dump) % proximity.size);
            Tile in = Edges.getFacingEdge(tile, other);
            if(other.getTeam() == tile.getTeam() && other.block().acceptItem(item, other, in) && canDump(tile, other, item)){
                other.block().handleItem(item, other, in);
                return;
            }
        }

        handleItem(item, tile, tile);
    }

    /** Try dumping any item near the tile. */
    public boolean tryDump(Tile tile){
        return tryDump(tile, null);
    }

    /**
     * Try dumping a specific item near the tile.
     * @param todump Item to dump. Can be null to dump anything.
     */
    public boolean tryDump(Tile tile, Item todump){
        TileEntity entity = tile.entity;
        if(entity == null || !hasItems || tile.entity.items.total() == 0
            //等价替代（C 类）：原版是 entity.items.has(todump)（单参重载）；本项目 ItemModule 只有 has(Item, int)，传 1 等值
            || (todump != null && !entity.items.has(todump, 1)))
            return false;

        Array<Tile> proximity = entity.proximity;
        int dump = tile.rotation();

        if(proximity.size == 0) return false;

        for(int i = 0; i < proximity.size; i++){
            Tile other = proximity.get((i + dump) % proximity.size);
            Tile in = Edges.getFacingEdge(tile, other);

            if(todump == null){

                for(int ii = 0; ii < Vars.content.items().size; ii++){
                    Item item = Vars.content.item(ii);

                    if(other.getTeam() == tile.getTeam() && entity.items.has(item, 1) && other.block().acceptItem(item, other, in) && canDump(tile, other, item)){
                        other.block().handleItem(item, other, in);
                        tile.entity.items.remove(item, 1);
                        incrementDump(tile, proximity.size);
                        return true;
                    }
                }
            }else{

                if(other.getTeam() == tile.getTeam() && other.block().acceptItem(todump, other, in) && canDump(tile, other, todump)){
                    other.block().handleItem(todump, other, in);
                    tile.entity.items.remove(todump, 1);
                    incrementDump(tile, proximity.size);
                    return true;
                }
            }

            incrementDump(tile, proximity.size);
        }

        return false;
    }

    protected void incrementDump(Tile tile, int prox){
        tile.rotation((tile.rotation() + 1) % prox);
    }

    /** Used for dumping items. */
    public boolean canDump(Tile tile, Tile to, Item item){
        return true;
    }

    /** Try offloading an item to a nearby container in its facing direction. Returns true if success. */
    public boolean offloadDir(Tile tile, Item item){
        Tile other = tile.front();
        if(other != null && other.getTeam() == tile.getTeam() && other.block().acceptItem(item, other, tile)){
            other.block().handleItem(item, other, tile);
            return true;
        }
        return false;
    }
}
