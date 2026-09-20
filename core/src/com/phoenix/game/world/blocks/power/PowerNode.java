package com.phoenix.game.world.blocks.power;

import com.badlogic.gdx.utils.Array;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

/**
 * 电力分配节点。参照 Mindustry mindustry.world.blocks.power.PowerNode 移植。
 * <p>节点自身不产生/消耗电力，作用是把邻接的建筑接入电网，并**远距离**连接其他节点
 * （原版的激光连线）：只要两节点在 {@link #laserRange} 格以内就属于同一电网。
 * <p>与邻接建筑（电池/发电机/用电器）仍按邻接表相连；节点之间走范围扫描，
 * 每个节点最多连 {@link #maxLinks} 条线（按距离取最近的，与原版一致，避免一个节点牵一大片）。
 */
public class PowerNode extends Block{
    /** 与其他电力节点的最大连线距离（格）。 */
    public float laserRange = 6f;
    /** 每个节点最多连出的线数（原版默认 3）。 */
    public int maxLinks = 3;

    public PowerNode(String name){
        super(name);
        solid = true;
        update = true;
        health = 40;
        hasPower = true;
        entityType = NodeEntity::new;
    }

    /** 连线候选：范围内的其他节点，按距离近的优先，取前 {@link #maxLinks} 个。 */
    @Override
    public void getPowerConnections(Tile tile, Array<Tile> out){
        //先接邻接建筑（电池、发电机、用电器……）
        super.getPowerConnections(tile, out);

        if(tile.entity == null) return;

        int r = (int)laserRange;
        Array<Tile> candidates = new Array<>();
        Array<Float> dists = new Array<>();

        for(int dx = -r; dx <= r; dx++){
            for(int dy = -r; dy <= r; dy++){
                if(dx == 0 && dy == 0) continue;

                Tile other = com.phoenix.game.Vars.world.tile(tile.x + dx, tile.y + dy);
                if(other == null || other == tile || other.entity == null || other.entity.power == null) continue;
                if(!(other.block() instanceof PowerNode)) continue;
                if(other.getTeam() != tile.getTeam()) continue;

                candidates.add(other);
                dists.add((float)(dx * dx + dy * dy));
            }
        }

        //按距离排序后取最近的 maxLinks 个：原版用"对方节点连线未满"做判据，顺序依赖放置顺序，
        //这里改成确定性的最近优先，效果等价且不依赖时序
        int limit = Math.min(maxLinks, candidates.size);
        for(int i = 0; i < limit; i++){
            int best = i;
            for(int j = i + 1; j < candidates.size; j++){
                if(dists.get(j) < dists.get(best)) best = j;
            }
            if(best != i){
                candidates.swap(i, best);
                dists.swap(i, best);
            }
            out.add(candidates.get(i));
        }
    }

    public class NodeEntity extends TileEntity{
        /** 缓存本帧的连线目标（含邻接建筑）；绘制时只取"节点到节点"的那部分。 */
        public final Array<Tile> links = new Array<>();

        @Override
        public void update(){
            links.clear();
            if(isDead()) return;
            getPowerConnections(tile, links);
        }
    }

    /**
     * 顶层绘制：节点之间的激光连线。
     * <p>放在 {@link Block#drawTopLayer} 而不是 {@code drawLayer}，是因为连线要**盖在相邻方块之上**
     * —— 逐格绘制时后画的方块会把先画的线压掉（对应原版把连线放在方块之后的独立绘制层）。
     */
    @Override
    public void drawTopLayer(Tile tile){
        if(!(tile.entity instanceof NodeEntity)) return;
        NodeEntity entity = (NodeEntity)tile.entity;
        if(entity.links.size == 0) return;

        com.badlogic.gdx.graphics.g2d.TextureRegion white =
            com.phoenix.game.core.Core.atlas == null ? null : com.phoenix.game.core.Core.atlas.findRegion("white");
        if(white == null) return;

        float x1 = centerX(tile), y1 = centerY(tile);

        for(int i = 0; i < entity.links.size; i++){
            Tile other = entity.links.get(i);
            if(other == null || other == tile || !(other.block() instanceof PowerNode)) continue;
            //每条线只画一遍：只画连到"坐标更大"的那一端
            if(other.x < tile.x || (other.x == tile.x && other.y < tile.y)) continue;

            drawLaser(white, x1, y1, other.block().centerX(other), other.block().centerY(other));
        }
    }

    /** 画一条带流动光点的电力连线（对应原版 PowerNode 的激光连线）。 */
    private void drawLaser(com.badlogic.gdx.graphics.g2d.TextureRegion white,
                           float x1, float y1, float x2, float y2){
        float angle = com.phoenix.game.math.Angles.angle(x1, y1, x2, y2);
        float length = com.phoenix.game.math.Mathf.dst(x1, y1, x2, y2);
        if(length < 0.5f) return;

        float thickness = 1.1f;

        //底线：半透明的橙色光束
        com.phoenix.game.core.Core.batch.setColor(com.phoenix.game.graphics.Pal.power2.r, com.phoenix.game.graphics.Pal.power2.g,
            com.phoenix.game.graphics.Pal.power2.b, 0.5f);
        com.phoenix.game.core.Core.batch.draw(white, x1, y1 - thickness / 2f, 0f, thickness / 2f, length, thickness, 1f, 1f, angle);

        //流动光点：沿线上按时间匀速移动的亮点（Time.time 单位是 tick，60 tick = 1 秒）
        float t = (com.phoenix.game.core.Time.time * 0.02f) % 1f;
        com.phoenix.game.core.Core.batch.setColor(1f, 1f, 1f, 0.85f);
        for(int i = 0; i < 3; i++){
            float p = (t + i / 3f) % 1f;
            float px = x1 + com.phoenix.game.math.Angles.trnsx(angle, length * p);
            float py = y1 + com.phoenix.game.math.Angles.trnsy(angle, length * p);
            com.phoenix.game.core.Core.batch.draw(white, px - 1.5f, py - 1.5f, 3f, 3f);
        }
        com.phoenix.game.core.Core.batch.setColor(1f, 1f, 1f, 1f);
    }
}
