package com.phoenix.game.world.blocks.liquid;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Edges;
import com.phoenix.game.world.Tile;

/**
 * 装甲导管（原版方块名 {@code plated-conduit}）。参照 Mindustry
 * mindustry.world.blocks.liquid.ArmoredConduit 移植。
 * <p>在 {@link Conduit} 的基础上：泄漏阻力更高（{@link #leakResistance} = 10）、只在**正面**收液、
 * 且贴图拼接只认"正对着它"的邻居（{@link #blendsArmored}）；不接液的那一端会画一个 {@code -cap} 堵头。
 *
 * <p>与原版的差异：
 * <ul>
 *     <li>原版堵头角度是 {@code tile.rotation() * 90}（原版 rotation 0 = +x）；本项目 rotation 0 = +y，
 *         按 {@code 转换.md} 的换算写成 {@code (1 - rotation) * 90}（与 {@code Conduit}/{@code LiquidBlock} 一致）。</li>
 *     <li>原版 {@code Edges.getFacingEdge(...)} 返回 arc 的 {@code Point2}，本项目返回 {@code Tile}
 *         （越界可能为 null）→ 加 null 守卫。</li>
 * </ul>
 */
public class ArmoredConduit extends Conduit{
    public TextureRegion capRegion;

    public ArmoredConduit(String name){
        super(name);
        leakResistance = 10f;
    }

    @Override
    public void load(){
        super.load();
        capRegion = Core.atlas == null ? null : Core.atlas.findRegion(name + "-cap");
    }

    @Override
    public void draw(Tile tile){
        super.draw(tile);

        //只在"下一格会漏液"时画堵头：下一格同队且能装液体就不画
        Tile next = tile.front();
        if(next != null && next.getTeam() == tile.getTeam() && next.block().hasLiquids) return;

        if(capRegion != null){
            //本项目 rotation 0 = +y（原版 0 = +x），角度换算见类注释
            Draw.rect(capRegion, centerX(tile), centerY(tile), (1 - (tile.rotation() & 3)) * 90f);
        }
    }

    @Override
    public boolean acceptLiquid(Tile tile, Tile source, Liquid liquid, float amount){
        //原版运算符优先级照抄：&& 先于 ||（只要来源是导管就放行，否则必须正对着本格）
        //注意 source 允许为 null（见 Block.acceptLiquid 的说明），原版会 NPE，这里直接拒绝
        if(source == null) return false;

        Tile edge = Edges.getFacingEdge(source, tile);
        return (super.acceptLiquid(tile, source, liquid, amount) && source.block() instanceof Conduit)
            || (edge != null && edge.relativeTo(tile) == tile.rotation());
    }

    @Override
    public boolean blends(Tile tile, int rotation, int otherx, int othery, int otherrot, Block otherblock){
        return otherblock.outputsLiquid && blendsArmored(tile, rotation, otherx, othery, otherrot, otherblock);
    }
}
