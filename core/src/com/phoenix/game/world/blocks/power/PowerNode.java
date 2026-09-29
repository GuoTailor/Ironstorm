package com.phoenix.game.world.blocks.power;

import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Drawf;
import com.phoenix.game.core.Lines;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.blocks.PowerBlock;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.StatUnit;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 电力分配节点。参照 Mindustry mindustry.world.blocks.power.PowerNode 移植。
 * <p>节点自身不产生/消耗电力，作用是把邻接的建筑接入电网，并**远距离**连接其他节点
 * （原版的激光连线）：只要两节点在 {@link #laserRange} 格以内就属于同一电网。
 * <p>与邻接建筑（电池/发电机/用电器）仍按邻接表相连；节点之间走范围扫描，
 * 每个节点最多连 {@link #maxLinks} 条线（按距离取最近的，与原版一致，避免一个节点牵一大片）。
 * <p>收敛继承链：原版 {@code extends PowerBlock}（update/solid/hasPower/group 都由基类设）。
 * 原版构造器里的 {@code expanded/layer/configurable} 本项目 {@code Block} 没有对应字段，
 * {@code consumesPower = false; outputsPower = false;} 见 {@link PowerDistributor} 类注释（本项目没有这两个字段）。
 */
public class PowerNode extends PowerBlock{
    /** 与其他电力节点的最大连线距离（格）。 */
    public float laserRange = 6f;
    /** 每个节点最多连出的线数（原版字段名 maxNodes）。
     * 原版 PowerNode.java:29 类默认 maxNodes = 3；power-node 由 Blocks.java:1067 覆盖成 20
     * （power-node-large = 30、surge-tower = 2 由 Blocks 各自覆盖）。 */
    public int maxLinks = 3;

    public PowerNode(String name){
        super(name);
        health = 40;
        entityType = NodeEntity::new;
    }

    @Override
    public void setStats(){
        super.setStats();

        //对应原版 PowerNode.setStats
        stats.add(BlockStat.powerRange, laserRange, StatUnit.blocks);
        stats.add(BlockStat.powerConnections, maxLinks, StatUnit.none);
    }

    /**
     * 悬停时显示与其他节点的最大连线范围（对应原版 {@code PowerNode.drawSelect} 的实心双描边圈）。
     * <p>原版是 {@code Lines.stroke(1f); Draw.color(Pal.accent); Drawf.circles(...); Draw.reset();}，
     * 本项目 {@link Drawf#circles(float, float, float)} 内部已经做了「3f 深灰 + 1f Pal.accent」并复位颜色，
     * 所以只调它即可。
     * <p>⚠ 坐标口径：原版写 {@code tile.drawx()/drawy()}，那里它就是方块贴图中心；本项目那是
     * 「锚点瓦片左下角 + offset()」，比贴图中心少半格（见 {@link Block#centerX}），照抄圈会整体偏到左下角。
     * <p>PowerNode 是 1x1（不会出现卫星瓦片），仍取 {@code tile.link()} 以防调用点传进别的瓦片。
     */
    @Override
    public void drawSelect(Tile tile){
        Tile anchor = tile.link();
        Drawf.circles(centerX(anchor), centerY(anchor), laserRange * tilesize);
    }

    /**
     * 放置预览：连线范围圈 + 与已有节点的连线预览（对应原版 {@code PowerNode.drawPlace}）。
     * <p>原版画范围用 {@code Drawf.circles}，再用 {@code getPotentialLinks(tile, other -> Drawf.square(other...))}
     * 把每个潜在连线目标框出来。
     * <p><b>与原版的差异</b>：
     * <ul>
     *     <li>原版 {@code getPotentialLinks} 的判据更宽（含发电/用电建筑，并用 {@code graphs} 去重）；
     *         本项目节点只连「范围内的其他节点」（见 {@link #getPowerConnections}），所以预览也按同一判据扫描，
     *         这样框出来的才是真正会连上的目标（否则会框出一堆其实连不上的建筑）。</li>
     *     <li>原版还用 {@code Pal.plastanium} 标出绝缘子（{@code insulators/insulated}）；
     *         本项目没有绝缘子概念 → 整段省略。</li>
     * </ul>
     * <p>⚠ 坐标口径同 {@link #drawSelect(Tile)}：中心 = 锚点瓦片左下角 + offset() + 半格，
     * 与 {@code InputHandler.drawGhost} 算出的 ghost 中心一致（原版是 {@code x * tilesize + offset()}）。
     */
    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        float cx = x * tilesize + offset() + tilesize / 2f, cy = y * tilesize + offset() + tilesize / 2f;
        Drawf.circles(cx, cy, laserRange * tilesize, Pal.placing);

        int r = (int)laserRange;
        for(int dx = -r; dx <= r; dx++){
            for(int dy = -r; dy <= r; dy++){
                if(dx == 0 && dy == 0) continue;

                Tile other = Vars.world == null ? null : Vars.world.tile(x + dx, y + dy);
                if(other == null || other.entity == null || other.entity.power == null) continue;
                if(!(other.block() instanceof PowerNode)) continue;
                if(Vars.player != null && other.getTeam() != Vars.player.getTeam()) continue;

                Drawf.square(other.block().centerX(other), other.block().centerY(other),
                    other.block().size * tilesize / 2f + 2f, Pal.place);
            }
        }
    }

    /**
     * 配置叠加（对应原版 {@code PowerNode.drawConfigure}，PowerNode.java:214-240）：
     * 本体呼吸圆圈 + 激光连线范围圈 + 范围内**已连上**的其他节点画 place 色方框。
     * <p><b>与原版的差异（判据等价替换）</b>：原版用 {@code linkValid(tile, link, false)} +
     * {@code linked(tile, link)}；本项目节点连接 = "同队节点 + laserRange 内"（见
     * {@link #getPowerConnections}），linked 用 {@code NodeEntity.links}（每帧缓存）判。
     * 坐标口径同 {@link #drawSelect(Tile)}：中心 = centerX/centerY。
     */
    @Override
    public void drawConfigure(Tile tile){
        Tile anchor = tile.link();
        if(!(anchor.entity instanceof NodeEntity)) return;
        NodeEntity entity = (NodeEntity)anchor.entity;

        Draw.color(Pal.accent);

        Lines.stroke(1.5f);
        Lines.circle(centerX(anchor), centerY(anchor),
            size * tilesize / 2f + 1f + Mathf.absin(Time.time(), 4f, 1f));

        Drawf.circles(centerX(anchor), centerY(anchor), laserRange * tilesize);

        Lines.stroke(1.5f);

        int r = (int)laserRange + 2;
        for(int x = anchor.x - r; x <= anchor.x + r; x++){
            for(int y = anchor.y - r; y <= anchor.y + r; y++){
                Tile link = Vars.world == null ? null : Vars.world.tile(x, y);

                if(link == null || link == anchor) continue;
                if(!(link.block() instanceof PowerNode) || link.getTeam() != anchor.getTeam()) continue;
                //范围内（linkValid(tile, link, false) 的本项目等价判据）
                int dx = anchor.x - link.x, dy = anchor.y - link.y;
                if((float)(dx * dx + dy * dy) > laserRange * laserRange) continue;

                if(entity.links.contains(link, true)){
                    Drawf.square(link.block().centerX(link), link.block().centerY(link),
                        link.block().size * tilesize / 2f + 1f, Pal.place);
                }
            }
        }

        Draw.color();
        Lines.stroke(1f);
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
        //激光透明度设置（对应原版 PowerNode.drawLayer/drawLaser：lasersopacity 是整数百分比，0 = 整条不画）
        int opacityPercent = com.phoenix.game.core.Core.settings.getInt("lasersopacity", 100);
        if(opacityPercent == 0) return;
        float opacity = opacityPercent / 100f;

        float angle = com.phoenix.game.math.Angles.angle(x1, y1, x2, y2);
        float length = com.phoenix.game.math.Mathf.dst(x1, y1, x2, y2);
        if(length < 0.5f) return;

        float thickness = 1.1f;

        //底线：半透明的橙色光束
        com.phoenix.game.core.Core.batch.setColor(com.phoenix.game.graphics.Pal.power2.r, com.phoenix.game.graphics.Pal.power2.g,
            com.phoenix.game.graphics.Pal.power2.b, 0.5f * opacity);
        com.phoenix.game.core.Core.batch.draw(white, x1, y1 - thickness / 2f, 0f, thickness / 2f, length, thickness, 1f, 1f, angle);

        //流动光点：沿线上按时间匀速移动的亮点（Time.time 单位是 tick，60 tick = 1 秒）
        float t = (com.phoenix.game.core.Time.time * 0.02f) % 1f;
        com.phoenix.game.core.Core.batch.setColor(1f, 1f, 1f, 0.85f * opacity);
        for(int i = 0; i < 3; i++){
            float p = (t + i / 3f) % 1f;
            float px = x1 + com.phoenix.game.math.Angles.trnsx(angle, length * p);
            float py = y1 + com.phoenix.game.math.Angles.trnsy(angle, length * p);
            com.phoenix.game.core.Core.batch.draw(white, px - 1.5f, py - 1.5f, 3f, 3f);
        }
        com.phoenix.game.core.Core.batch.setColor(1f, 1f, 1f, 1f);
    }
}
