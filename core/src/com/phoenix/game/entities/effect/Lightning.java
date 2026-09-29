package com.phoenix.game.entities.effect;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.Pools;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Bullets;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Fill;
import com.phoenix.game.core.Lines;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.traits.DrawTrait;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.type.TimedEntity;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;
import static com.phoenix.game.Vars.world;

/**
 * 闪电。参照 Mindustry mindustry.entities.effect.Lightning 移植（F3 完整化）。
 * <p>原版是一个 {@code TimedEntity} 实体（折线特效）+ 静态 {@code createLighting} 工厂：
 * 工厂在**同步一次调用**里跑完"逐段放伤害子弹 → 沿途绝缘墙拦截 → 30 格内找最近敌人链式跳跃
 * （最多 8 跳）/ 无目标则随机偏转前进"的整条生成逻辑，实体只负责折线的绘制与到期回收。
 * <p>与原版的对应关系 / 刻意偏离：
 * <ul>
 *     <li>原版 {@code create()} 走 {@code Call.createLighting}（网络分发，{@code @Remote} 注解本项目
 *         必须保持注释状态）；本项目无网络层，{@code create()} 直接转调 {@link #createLighting}。</li>
 *     <li>原版 {@code random} 是 arc {@code Rand}（可 setSeed，供双端同 seed 复现同一条闪电路径）；
 *         本项目无同步需求，用 libgdx 的 {@link RandomXS128}，{@code arc Rand.range(amount)} 的
 *         {@code (random()*2-1)*amount} 语义就地展开。</li>
 *     <li>原版 {@code lines} 存 arc {@code Vec2}（兼作 Position）；本项目用 libgdx {@link Vector2}
 *         （不实现本项目 math.Position，raycast 起止点直接读 x/y 字段）。</li>
 *     <li>原版 {@code targetGroup()} 返回 {@code bulletGroup}；本项目该组未接线（仿真以
 *         {@code Bullet.all} 为准），实体登记进 {@link Vars#effectGroup}（ItemTransfer 先例），
 *         由 {@code Logic.update()} / {@code Renderer.draw()} 每帧遍历。</li>
 *     <li>原版 {@code draw()} 用 {@code Lines.beginLine/linePoint/endLine} 画整条折线；本项目
 *         {@code Lines} 没有折线会话 API → 逐段 {@code Lines.line}；{@code TimeTrait} 没有
 *         {@code fout()} → 就地 {@code 1 - fin()}（原版 TimeTrait 的 fout 语义）。</li>
 * </ul>
 */
public class Lightning extends TimedEntity implements DrawTrait{
    /** 闪电存活帧数（对应原版 {@code Lightning.lifetime}）。供 {@code Bullets.damageLightning} 取用。 */
    public static final float lifetime = 10f;

    /** 原版 arc Rand：可 setSeed 的随机数发生器（seed 由 create 调用方传入）。 */
    private static final RandomXS128 random = new RandomXS128();
    private static final Rectangle rect = new Rectangle();
    private static final Array<BaseUnit> entities = new Array<>();
    private static final IntSet hit = new IntSet();
    private static final int maxChain = 8;
    private static final float hitRange = 30f;
    private static int lastSeed = 0;

    private Array<Vector2> lines = new Array<>();
    private Color color = Pal.lancerLaser;

    /** For pooling use only. Do not call directly! */
    public Lightning(){
    }

    /** Create a lighting branch at a location. Use Team.none to damage everyone. */
    public static void create(Team team, Color color, float damage, float x, float y, float targetAngle, int length){
        createLighting(nextSeed(), team, color, damage, x, y, targetAngle, length);
    }

    /** @return a new seed for a lightning effect. */
    public static int nextSeed(){
        return lastSeed++;
    }

    /**
     * 原版 {@code @Remote createLighting} 的主体（本项目去掉网络注解，直接静态调用）。
     * <p>三段核心逻辑（照抄原版 Lightning.java:56-116）：
     * ① 每段一发 {@code Bullets.damageLightning}（speed≈0 站在原地、lifetime=10、命中挂 shocked），
     *    伤害由 dmg 经子弹 data 携带（{@code Bullet.damage()} 优先读 Float data）；
     * ② 每段线段做 {@code world.raycastEach}，碰到 {@code Block.insulated} 方块就把末点钉在墙上并折断；
     * ③ 以当前点为中心 30 格方框收集敌人（{@code hit} 集合去重，最多 8 跳），跳到最远者；
     *    没有目标则随机偏转 ±20° 前进半段。
     */
    public static void createLighting(int seed, Team team, Color color, float damage, float x, float y, float rotation, int length){
        Lightning l = Pools.obtain(Lightning.class);
        Float dmg = damage;

        l.x = x;
        l.y = y;
        l.color = color;
        //本项目 BaseEntity 的 dead 标记不在 Poolable.reset() 里复位（ItemTransfer 先例），池化复用必须显式清
        l.setDead(false);
        //原版 l.add() → bulletGroup；本项目该组未接线，登记进 effectGroup（ItemTransfer 同款）
        l.add();

        random.setSeed(seed);
        hit.clear();

        boolean[] bhit = {false};

        //length / 2 段：每段一发伤害子弹 + 折线一点 + 链式跳跃/随机偏转
        for(int i = 0; i < length / 2; i++){
            //① 沿途伤害：Bullet.create 最后一个参数是 data（原版传 Float dmg）
            Bullet.create(Bullets.damageLightning, l, team, x, y, 0f, 1f, 1f, dmg);
            l.lines.add(new Vector2(x + Mathf.range(3f), y + Mathf.range(3f)));

            //② 绝缘墙拦截
            if(l.lines.size > 1){
                bhit[0] = false;
                Vector2 from = l.lines.get(l.lines.size - 2);
                Vector2 to = l.lines.get(l.lines.size - 1);
                world.raycastEach(world.toTile(from.x), world.toTile(from.y), world.toTile(to.x), world.toTile(to.y), (wx, wy) -> {

                    Tile tile = world.ltile(wx, wy);
                    if(tile != null && tile.block().insulated){
                        bhit[0] = true;
                        //snap it instead of removing
                        l.lines.get(l.lines.size - 1).set(wx * tilesize, wy * tilesize);
                        return true;
                    }
                    return false;
                });
                if(bhit[0]) break;
            }

            //③ 链式跳跃（原版 nearbyEnemies 传 Rect，本项目是 x/y/w/h 重载 → 展开 Rect 的左下角）
            rect.setSize(hitRange).setCenter(x, y);
            entities.clear();
            if(hit.size < maxChain){
                Units.nearbyEnemies(team, rect.x, rect.y, rect.width, rect.height, u -> {
                    if(!hit.contains(u.getID())){
                        entities.add(u);
                    }
                });
            }

            BaseUnit furthest = Geometry.findFurthest(x, y, entities);

            if(furthest != null){
                hit.add(furthest.getID());
                x = furthest.x;
                y = furthest.y;
            }else{
                //原版 rotation += random.range(20f)（arc Rand.range = (random()*2-1)*amount）
                rotation += (random.nextFloat() * 2f - 1f) * 20f;

                x += Angles.trnsx(rotation, hitRange / 2f);
                y += Angles.trnsy(rotation, hitRange / 2f);
            }
        }
    }

    @Override
    public float lifetime(){
        return lifetime;
    }

    @Override
    public void reset(){
        super.reset();
        color = Pal.lancerLaser;
        lines.clear();
    }

    @Override
    public void removed(){
        Pools.free(this);
    }

    @Override
    public void draw(){
        //⚠ 与 ItemTransfer.draw 同款：实体可能已被同步回收（reset() 清空 lines），本帧仍会被
        //effectGroup.draw() 遍历到（组成员数组下一帧才摘）→ lines 为空时直接跳过
        if(lines.size == 0) return;

        //原版 Lines.stroke(3f * fout())
        Lines.stroke(3f * (1f - fin()));
        Draw.color(color, com.badlogic.gdx.graphics.Color.WHITE, fin());

        //原版 Lines.beginLine()/linePoint()/endLine() 画整条折线；本项目无折线会话 API → 逐段 line
        float px = x, py = y;
        for(int i = 0; i < lines.size; i++){
            Vector2 p = lines.get(i);
            Lines.line(px, py, p.x, p.y);
            px = p.x;
            py = p.y;
        }

        //折点小方块：尺寸 5 → 3 随序号递减（原版 Fill.square(..., 45)）
        for(int i = 0; i < lines.size; i++){
            Vector2 p = lines.get(i);
            Fill.square(p.x, p.y, (5f - (float)i / lines.size * 2f) * (1f - fin()), 45);
        }
        Draw.color();
    }

    /** @return 本特效归属 {@link Vars#effectGroup}（原版是 bulletGroup，本项目该组未接线，ItemTransfer 先例）。 */
    @Override
    public com.phoenix.game.entities.EntityGroup targetGroup(){
        return Vars.effectGroup;
    }
}
