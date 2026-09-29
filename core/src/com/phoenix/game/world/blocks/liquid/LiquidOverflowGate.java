package com.phoenix.game.world.blocks.liquid;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.LiquidBlock;
import com.phoenix.game.world.meta.BlockStat;

/**
 * 溢流闸门：只在"前方容器装不下"时才把液体转到侧面（相当于液体的 OverflowGate）。
 * 参照 Mindustry mindustry.world.blocks.liquid.LiquidOverflowGate 移植（原版类注释即标注为 "implement later"）。
 * <p>刻意偏离（本项目结构）：
 * <ul>
 *     <li>原版 {@code topRegion = reg("-top")} 存的是打包期纹理 id，绘制时 {@code Draw.rect(reg(topRegion), ...)}；
 *         本项目没有 {@code Block.reg}，改为直接缓存 {@code TextureRegion topRegion}（在 {@link #load()} 里取）。
 *         ⚠图集里没有 {@code overflow-gate-top}（只有 {@code overflow-gate}），取不到时跳过该层。</li>
 *     <li>原版 {@code getLiquidDestination} 用 {@code next.block().getLiquidDestination(...)} 虚调用；
 *         本项目 {@code Block} 无该钩子，走 {@link LiquidJunction#forward} 的 instanceof 链。</li>
 *     <li>原版 {@code setBars()} 里 {@code bars.remove("liquid")}：本项目 {@code Block} 没有 {@code bars} 字段。</li>
 *     <li>原版 {@code draw} 里 {@code tile.rotation() * 90} 是"原版 rotation 0 = +x"的换算；
 *         本项目 rotation 0 = +y，改用 {@code (1 - rotation) * 90}。</li>
 * </ul>
 * <p><b>依赖已就位</b>：{@code Block.getLiquidDestination} 已补上，且
 * {@code Block.tryDumpLiquid} / {@code tryMoveLiquid} 都会调用它（照原版）→ 本类的
 * {@link #getLiquidDestination} 现在是真正的虚覆写。
 */
public class LiquidOverflowGate extends LiquidBlock{
    /** 顶盖贴图（原版是打包期纹理 id {@code topRegion}）。 */
    public TextureRegion topRegion;

    public LiquidOverflowGate(String name){
        super(name);
        rotate = true;
        entityType = LiquidOverflowGateEntity::new;
    }

    @Override
    public void load(){
        super.load();

        if(Core.atlas == null) return;
        //原版：topRegion = reg("-top")
        topRegion = Core.atlas.findRegion(name + "-top");
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.remove(BlockStat.liquidCapacity);
    }

    /**
     * ⚠ <b>坐标口径修正</b>：原版用 {@code tile.drawx()/drawy()}（原版口径下即方块中心）；
     * 本项目 {@code Tile.drawx()} 少半格，而 {@code Draw.rect} 是中心语义 → 改用
     * {@code centerX/centerY(tile)}（= {@code drawx() + tilesize/2f}），否则整体偏左下 4。
     */
    @Override
    public void draw(Tile tile){
        if(region != null) Draw.rect(region, centerX(tile), centerY(tile));
        //图集缺 overflow-gate-top 时 topRegion 为 null，跳过该层
        if(topRegion != null) Draw.rect(topRegion, centerX(tile), centerY(tile), (1 - (tile.rotation() & 3)) * 90f);
    }

    @Override
    public TextureRegion[] generateIcons(){
        if(Core.atlas == null) return super.generateIcons();
        return new TextureRegion[]{Core.atlas.findRegion(name), Core.atlas.findRegion(name + "-top")};
    }

    /** 液体流向下一格（对应原版 {@code LiquidOverflowGate.getLiquidDestination}）。 */
    @Override
    public Tile getLiquidDestination(Tile tile, Tile source, Liquid liquid){
        int dir = source.relativeTo(tile.x, tile.y);
        dir = (dir + 4) % 4;
        Tile next = tile.getNearby(dir);
        if(next == null) return tile;
        next = next.link();
        if(!next.block().acceptLiquid(next, tile, liquid, 0.0001f)
            && !(next.block() instanceof LiquidOverflowGate || next.block() instanceof LiquidJunction)){
            return tile;
        }
        return next.block().getLiquidDestination(next, tile, liquid);
    }

    /** 闸门实体：只为承载液体模块（原版用 {@code Block} 默认的 {@code TileEntity}）。 */
    public class LiquidOverflowGateEntity extends TileEntity{
    }
}
