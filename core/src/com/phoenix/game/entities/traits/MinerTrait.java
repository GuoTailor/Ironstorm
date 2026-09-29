package com.phoenix.game.entities.traits;

import com.badlogic.gdx.graphics.Color;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Lines;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.effect.ItemTransfer;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.*;

/**
 * 参照原版 mindustry.entities.traits.MinerTrait 移植。
 * <p>原版 {@code (Unit)this} 改成本项目的 {@link BaseUnit}（本项目没有 Unit 基类）。
 * <p><b>依赖已全部就位（玩家背包/单位载物背包批次补齐）</b>：单位载物背包
 * （{@code BaseUnit.acceptsItem/addItem/clearItem/getItemCapacity}）、
 * {@code Block.acceptStack}/{@code handleStack}（原版就在 {@code Block} 上，本批从 {@code BlockStorage} 兼容层补回）、
 * {@link com.phoenix.game.entities.effect.ItemTransfer#transferItemToUnit}（动画结束时回调 {@code addItem}）与
 * {@link com.phoenix.game.entities.effect.ItemTransfer#transferItemTo}。
 * 核心库存 = 队伍共享库存（{@code CoreBlock} 把 {@code entity.items} 指向 {@code state.teams.items(team)}）。
 * 因此原版 {@link #updateMining()} 的三条分支都能照抄：背包满→卸给核心 / {@code offloadImmediately} 直进核心 /
 * 否则进单位背包。
 */
public interface MinerTrait extends Entity{

    /** Returns the range at which this miner can mine blocks. */
    default float getMiningRange(){
        return 70f;
    }

    default boolean isMining(){
        return getMineTile() != null;
    }

    /** Returns the tile this builder is currently mining. */
    Tile getMineTile();

    /** Sets the tile this builder is currently mining. */
    void setMineTile(Tile tile);

    /** Returns the mining speed of this miner. 1 = standard, 0.5 = half speed, 2 = double speed, etc. */
    float getMinePower();

    /** Returns whether or not this builder can mine a specific item type. */
    boolean canMine(Item item);

    /** @return whether to offload mined items immediately at the core. if false, items are collected and dropped in a burst. */
    default boolean offloadImmediately(){
        return false;
    }

    default void updateMining(){
        BaseUnit unit = (BaseUnit)this;
        Tile tile = getMineTile();
        //原版 getClosestCore() 返回 TileEntity；本项目返回核心瓦片（材料走队伍共享库存）
        Tile core = unit.getClosestCore();

        //1) 背包收不下当前矿、且离核心够近 → 先把背包里的矿卸给核心（对应原版 updateMining 开头那一段）
        if(core != null && tile != null && tile.drop() != null && !unit.acceptsItem(tile.drop())
            && unit.dst(core) < mineTransferRange && core.entity != null && core.entity.items != null){
            int accepted = core.block().acceptStack(unit.item, unit.itemAmount, core, unit);
            if(accepted > 0){
                ItemTransfer.transferItemTo(unit.item, accepted,
                        tile.worldx() + Mathf.range(tilesize / 2f),
                        tile.worldy() + Mathf.range(tilesize / 2f), core);
                unit.clearItem();
            }
        }

        if(tile == null || core == null || tile.block() != Blocks.air || dst(tile.worldx(), tile.worldy()) > getMiningRange()
                || tile.drop() == null || !unit.acceptsItem(tile.drop()) || !canMine(tile.drop())){
            setMineTile(null);
        }else{
            Item item = tile.drop();
            unit.rotation = Mathf.slerpDelta(unit.rotation, unit.angleTo(tile.worldx(), tile.worldy()), 0.4f);

            if(Mathf.chance(Time.delta() * (0.06f - item.hardness * 0.01f) * getMinePower())){
                //原版：核心在转移范围内 + 核心能收下 1 个 + offloadImmediately（玩家为 true）→ 产出直接进核心；
                //否则进单位自己的载物背包（ItemTransfer 动画结束时回调 addItem）
                if(unit.dst(core) < mineTransferRange && core.entity != null && core.entity.items != null
                        && core.block().acceptStack(item, 1, core, unit) == 1 && offloadImmediately()){
                    ItemTransfer.transferItemTo(item, 1,
                            tile.worldx() + Mathf.range(tilesize / 2f),
                            tile.worldy() + Mathf.range(tilesize / 2f), core);
                }else if(unit.acceptsItem(item)){
                    ItemTransfer.transferItemToUnit(item,
                            tile.worldx() + Mathf.range(tilesize / 2f),
                            tile.worldy() + Mathf.range(tilesize / 2f), unit);
                }
            }

            if(Mathf.chance(0.06f * Time.delta())){
                //B 类：原版把 item.color 当 data 传给特效（pulverizeSmall 的 renderer 在本项目里固定用 Pal.stoneGray）
                Effects.effect(Fx.pulverizeSmall,
                        tile.worldx() + Mathf.range(tilesize / 2f),
                        tile.worldy() + Mathf.range(tilesize / 2f), 0f);
            }
        }
    }

    default void drawMining(){
        BaseUnit unit = (BaseUnit)this;
        Tile tile = getMineTile();

        if(tile == null) return;

        float focusLen = 4f + Mathf.absin(Time.time, 1.1f, 0.5f);
        float swingScl = 12f, swingMag = tilesize / 8f;
        float flashScl = 0.3f;

        float px = unit.x + Angles.trnsx(unit.rotation, focusLen);
        float py = unit.y + Angles.trnsy(unit.rotation, focusLen);

        float ex = tile.worldx() + Mathf.sin(Time.time + 48, swingScl, swingMag);
        float ey = tile.worldy() + Mathf.sin(Time.time + 48, swingScl + 2f, swingMag);

        Draw.color(Color.LIGHT_GRAY, Color.WHITE, 1f - flashScl + Mathf.absin(Time.time, 0.5f, flashScl));

        //B 类：原版依赖 Drawf.laser(region, regionEnd, ...) 画贴图光束。
        //⚠ 贴图本身**存在**（atlas 里有 minelaser / minelaser-end，grep 已证），缺的是 Drawf.laser
        //   与 arc 的"贴图线"原语（Lines.line(TextureRegion,...)/precise/CapStyle，本项目 Lines 只有
        //   ShapeRenderer 版纯色线）→ 退化成一条纯色线段。
        Lines.stroke(0.75f);
        Lines.line(px, py, ex, ey);

        //刻意等价替代：原版判定 ((Player)unit).isLocal；本项目 Player 不是单位基类（持有 unit 引用），改为比较本地玩家的当前单位
        if(unit == (player == null ? null : player.unit())){
            Draw.color(Pal.accent);
            Lines.stroke(1f);
            Lines.poly(tile.worldx(), tile.worldy(), 4, tilesize / 2f * Mathf.sqrt(2f), Time.time);
        }

        Draw.color();
    }
}
