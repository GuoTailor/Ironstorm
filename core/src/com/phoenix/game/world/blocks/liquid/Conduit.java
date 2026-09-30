package com.phoenix.game.world.blocks.liquid;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Interval;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.BuildRequest;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.Autotiler;
import com.phoenix.game.world.blocks.LiquidBlock;

/**
 * 导管：把液体沿 rotation 方向压向相邻建筑。参照 Mindustry
 * mindustry.world.blocks.liquid.Conduit 移植。
 * <p>贴图分上下两层：{@code conduit-bottom-<拼接组>}（底色 + 液体填充）与
 * {@code conduit-top-<拼接组>}（顶盖），拼接组由四邻是否有可拼接的液体方块算出。
 * <p>与原版的差异：
 * <ul>
 *     <li>原版把 {@code update(Tile)} / {@code draw(Tile)} / {@code acceptLiquid(...)} 写在方块上、
 *         由 {@code Block.update(Tile)} 调用；本项目统一写在实体内部类 {@link ConduitEntity#update()}
 *         （见 {@code Drill}/{@code GenericCrafter}）。</li>
 *     <li>原版贴图基准朝向为"右"（{@code rotation * 90}）；本项目 rotation 0 = +y，
 *         所以角度写成 {@code (1 - rotation) * 90}（同 {@code LiquidBlock}）。</li>
 *     <li>原版 {@code drawRequestRegion}（建造预览）本项目没有对应钩子，未移植；
 *         {@code getReplacement}（拖拽连线时拐角自动换成 liquid-junction）已按原版补上。</li>
 *     <li>原版 {@code timers++} 的计时器槽位本项目没有（{@code Block.timers} 未移植），
 *         改为每个实体自带一个 {@link Interval}。</li>
 * </ul>
 * <p>已接线：{@code Block.conveyorPlacement}（拖拽连放走 {@code Placement.pathfindLine}）。
 * <p>刻意降级：{@code TileEntity.noSleep()/sleep()}（本项目没有方块休眠机制，调用等价于空操作，同 {@code BlockStorage}）。
 */
public class Conduit extends LiquidBlock implements Autotiler{
    /** 液体流动计时器槽位（原版 {@code timers++}）。 */
    public final int timerFlow = 0;

    public TextureRegion[] topRegions = new TextureRegion[7];
    public TextureRegion[] botRegions = new TextureRegion[7];

    public float leakResistance = 1.5f;

    public Conduit(String name){
        super(name);
        rotate = true;
        solid = false;
        //浮空方块：可覆盖深液格（对应原版 Conduit 的 floating = true；判据见 Build.validPlace）
        floating = true;
        //拖拽连放走传送带式寻路（对应原版 conveyorPlacement；由 InputHandler.buildPath → Placement.pathfindLine 消费）
        conveyorPlacement = true;
        entityType = ConduitEntity::new;
    }

    @Override
    public void load(){
        super.load();

        if(Core.atlas == null) return;

        //原版是 Core.atlas.find(...)（arc 的 Atlas 缺图返回 error 区域）；本项目用 findRegion（缺图返回 null）
        liquidRegion = Core.atlas.findRegion("conduit-liquid");
        for(int i = 0; i < topRegions.length; i++){
            topRegions[i] = Core.atlas.findRegion(name + "-top-" + i);
            botRegions[i] = Core.atlas.findRegion("conduit-bottom-" + i);
        }
    }

    @Override
    public void onProximityUpdate(Tile tile){
        super.onProximityUpdate(tile);

        if(!(tile.entity instanceof ConduitEntity)) return;
        ConduitEntity entity = (ConduitEntity)tile.entity;
        int[] bits = buildBlending(tile, tile.rotation(), null, true);
        entity.blendbits = bits[0];
    }

    @Override
    public void transformCase(int num, int[] bits){
        bits[0] = num == 0 ? 3 : num == 1 ? 6 : num == 2 ? 2 : num == 3 ? 4 : num == 4 ? 5 : num == 5 ? 1 : 0;
    }

    /**
     * 拖拽连线时的替换方块（对应原版 {@code Conduit.getReplacement}）：
     * 本格前后都有同向的导管/液交叉器请求、且本格已有一条**垂直朝向**的导管时，
     * 自动把请求换成液交叉器（{@code liquidJunction}）。
     * <p>消费点：{@code InputHandler.updateLine}（受设置项 {@code blockreplace} 门控）。
     * <p>坐标口径：本项目 {@code Geometry.d4[rotation]} 即前进方向（0=上/1=右/2=下/3=左），
     * 与原版 {@code Geometry.d4(rotation)} 语义等价；反向用 {@code mod(rotation - 2, 4)}。
     */
    @Override
    public Block getReplacement(BuildRequest req, Array<BuildRequest> requests){
        GridPoint2 fwd = Geometry.d4[Mathf.mod(req.rotation, 4)];
        GridPoint2 back = Geometry.d4[Mathf.mod(req.rotation - 2, 4)];

        return containsAt(requests, req, fwd) && containsAt(requests, req, back)
            && req.tile() != null
            && req.tile().block() instanceof Conduit
            && Mathf.mod(req.tile().rotation() - req.rotation, 2) == 1 ? Blocks.liquidJunction : this;
    }

    /** @return 请求列表里是否存在"与本请求同向、且位于本格 + offset 处"的导管/液交叉器请求。 */
    private boolean containsAt(Array<BuildRequest> requests, BuildRequest req, GridPoint2 offset){
        for(BuildRequest o : requests){
            if(o.x == req.x + offset.x && o.y == req.y + offset.y && o.rotation == req.rotation
                && (req.block instanceof Conduit || req.block instanceof LiquidJunction)){
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean blends(Tile tile, int rotation, int otherx, int othery, int otherrot, Block otherblock){
        return otherblock.hasLiquids && otherblock.outputsLiquid && lookingAt(tile, rotation, otherx, othery, otherrot, otherblock);
    }

    @Override
    public void draw(Tile tile){
        if(!(tile.entity instanceof ConduitEntity)) return;
        ConduitEntity entity = (ConduitEntity)tile.entity;

        int group = Mathf.clamp(entity.blendbits, 0, topRegions.length - 1);
        //本项目 rotation 0 = +y（原版 0 = +x），角度按 LiquidBlock 的换算
        float rotation = (1 - (tile.rotation() & 3)) * 90f;

        TextureRegion bot = botRegions[group], top = topRegions[group];
        if(bot == null || top == null) return;

        Draw.colorl(0.34f);
        Draw.rect(bot, centerX(tile), centerY(tile), rotation);

        Draw.color(entity.liquids.current().color);
        Draw.alpha(entity.smoothLiquid);
        Draw.rect(bot, centerX(tile), centerY(tile), rotation);
        Draw.color();

        Draw.rect(top, centerX(tile), centerY(tile), rotation);
    }

    @Override
    public TextureRegion[] generateIcons(){
        if(Core.atlas == null) return super.generateIcons();
        return new TextureRegion[]{Core.atlas.findRegion("conduit-bottom"), Core.atlas.findRegion(name + "-top-0")};
    }

    @Override
    public boolean acceptLiquid(Tile tile, Tile source, Liquid liquid, float amount){
        //刻意降级：本项目没有方块休眠机制（无 TileEntity.noSleep()），原版此处仅用于唤醒方块
        return tile.entity.liquids.get(liquid) + amount < liquidCapacity
            && (tile.entity.liquids.current() == liquid || tile.entity.liquids.get(tile.entity.liquids.current()) < 0.2f)
            && ((source.absoluteRelativeTo(tile.x, tile.y) + 2) % 4 != tile.rotation());
    }

    /** 导管实体：每帧把液体压向正前方（原版 {@code Conduit.update(Tile)}）。 */
    public class ConduitEntity extends TileEntity{
        /** 平滑后的液体充满度（0~1），只影响绘制。 */
        public float smoothLiquid;

        /** 流动计时器（对应原版 {@code tile.entity.timer.get(timerFlow, 1)}）。 */
        public final Interval timer = new Interval(1);

        public int blendbits;

        @Override
        public void update(){
            smoothLiquid = Mathf.lerpDelta(smoothLiquid, liquids.currentAmount() / liquidCapacity, 0.05f);

            if(liquids.total() > 0.001f && timer.get(timerFlow, 1)){
                tryMoveLiquid(tile, tile.getNearby(tile.rotation()), leakResistance, liquids.current());
                //刻意降级：本项目没有方块休眠机制，noSleep() 等价于空操作
            }else{
                //刻意降级：本项目没有方块休眠机制，sleep() 等价于空操作
            }
        }
    }
}
