package com.phoenix.game.entities;
import com.phoenix.game.entities.effect.Fire;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Bullet;

import com.badlogic.gdx.graphics.Color;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.world.Tile;

import java.util.HashSet;

/**
 * 最小实现：伤害/爆炸工具类。参照 Mindustry mindustry.entities.Damage 移植。
 * 只做单位范围伤害，瓦片伤害待建筑系统完善后补充。
 */
public class Damage{

    /** 对指定区域内的敌方单位造成伤害。 */
    public static void damage(Team team, float x, float y, float radius, float damage){
        if(damage <= 0f) return;

        //注意：用下标遍历，避免 libgdx Array 迭代器嵌套（伤害可能触发单位死亡→再遍历单位列表）
        for(int i = 0; i < Units.units.size; i++){
            BaseUnit unit = Units.units.get(i);
            if(unit.isDead() || !unit.withinDst(x, y, radius)) continue;
            if(team != null && !team.isEnemy(unit.getTeam())) continue;

            unit.damage(damage);
        }
    }

    /** 对区域内所有单位造成伤害（不分敌我）。 */
    public static void damageAll(float x, float y, float radius, float damage){
        if(damage <= 0f) return;

        for(int i = 0; i < Units.units.size; i++){
            BaseUnit unit = Units.units.get(i);
            if(unit.isDead() || !unit.withinDst(x, y, radius)) continue;
            unit.damage(damage);
        }
    }

    /**
     * 在指定位置附近随机点若干处火（对应原版 {@code Damage.createIncend}）。
     * <p>原版只是随机取点调 {@code Fire.create}，落在哪一格就烧哪一格；同一格反复点到只会刷新寿命。
     */
    public static void createIncend(float x, float y, float spread, int amount){
        if(amount <= 0 || Vars.world == null) return;

        for(int i = 0; i < amount; i++){
            Tile tile = Vars.world.tileWorld(x + Mathf.range(spread), y + Mathf.range(spread));
            if(tile != null){
                Fire.create(tile);
            }
        }
    }

    /**
     * 对区域内满足条件的单位造成伤害，并对每个受击单位执行一次回调。
     * 参照 Mindustry mindustry.entities.Damage.damageUnits 移植。
     * @param team 非 null 时只打它的敌对单位
     * @param size 半径
     * @param predicate 额外过滤；不满足者连伤害都不吃
     * @param acceptor 受击后的回调（火系统用它给地面单位挂燃烧状态）
     */
    public static void damageUnits(Team team, float x, float y, float size, float damage,
                                   java.util.function.Predicate<BaseUnit> predicate,
                                   java.util.function.Consumer<BaseUnit> acceptor){
        java.util.function.Consumer<BaseUnit> cons = unit -> {
            if(predicate != null && !predicate.test(unit)) return;
            unit.damage(damage);
            if(acceptor != null) acceptor.accept(unit);
        };

        if(team == null){
            Units.nearby(x, y, size, cons);
        }else{
            Units.nearby(x, y, size, unit -> {
                if(team.isEnemy(unit.getTeam())) cons.accept(unit);
            });
        }
    }

    /** 动态爆炸（带可燃/爆炸物属性）。 */
    public static void dynamicExplosion(float x, float y, float flammability, float explosiveness, float power, float radius, Color color){
        Effects.effect(Fx.explosion, color, x, y, 0f);
        Effects.shake(explosiveness + power, explosiveness + power, x, y);

        damageAll(x, y, radius, 4f * (explosiveness + power + 1f));

        if(flammability > 0f){
            createIncend(x, y, radius, (int)Math.min(flammability, 8f));
        }
    }

    /** 激光弹命中过的瓦片（同一条光束里同一格只打一次，large 的四邻检测会重复覆盖到）。 */
    private static final HashSet<Integer> hitTiles = new HashSet<>();

    public static void collideLine(Bullet hitter, Team team, Effects.Effect effect, float x, float y, float angle, float length){
        collideLine(hitter, team, effect, x, y, angle, length, false);
    }

    /**
     * 对一条线段上的敌方单位与建筑造成伤害（激光类武器用）。
     * 参照 Mindustry mindustry.entities.Damage.collideLine 移植。
     * <p>简化点：原版用 {@code Geometry.raycastRect} 求"线段与单位包围盒"的精确交点，
     * 这里退化成「点到线段距离 &lt; 单位半径」判定（先用线段包围盒粗筛），效果等价且更省。
     *
     * @param large true 时额外检测线段所过格子的四邻（对应原版粗光束的加宽判定）
     */
    public static void collideLine(Bullet hitter, Team team, Effects.Effect effect, float x, float y, float angle, float length, boolean large){
        if(hitter == null || hitter.getBulletType() == null) return;

        float x2 = x + Angles.trnsx(angle, length);
        float y2 = y + Angles.trnsy(angle, length);

        //单位：包围盒粗筛 → 点到线段距离判定
        float pad = 24f;
        Units.nearbyEnemies(team, Math.min(x, x2) - pad, Math.min(y, y2) - pad,
                Math.abs(x2 - x) + pad * 2f, Math.abs(y2 - y) + pad * 2f, unit -> {

            unit.hitbox(Tmp.r1);
            float radius = Tmp.r1.width * 0.5f;

            if(distanceToSegment(unit.getX(), unit.getY(), x, y, x2, y2) <= radius + 3f){
                Effects.effect(effect, unit.getX(), unit.getY());
                hitter.collision(unit, unit.getX(), unit.getY());
            }
        });

        //建筑：沿线段逐格检测
        if(Vars.world == null) return;
        hitTiles.clear();

        Vars.world.raycastEach(Vars.world.toTile(x), Vars.world.toTile(y),
                Vars.world.toTile(x2), Vars.world.toTile(y2), (tx, ty) -> {
            damageTile(hitter, team, effect, tx, ty);
            if(large){
                for(int i = 0; i < Geometry.d4.length; i++){
                    damageTile(hitter, team, effect, tx + Geometry.d4[i].x, ty + Geometry.d4[i].y);
                }
            }
            return false;
        });
    }

    /** 对单格敌方建筑造成一次激光伤害（这一格已经打过就跳过）。 */
    private static void damageTile(Bullet hitter, Team team, Effects.Effect effect, int tx, int ty){
        Tile tile = Vars.world.ltile(tx, ty);
        if(tile == null || tile.entity == null || tile.block() == Blocks.air) return;
        if(!team.isEnemy(tile.getTeam())) return;
        if(!hitTiles.add(tile.pos())) return;

        tile.entity.handleDamage(hitter.getBulletType().damage);
        Effects.effect(effect, tile.drawx(), tile.drawy());
    }

    /** @return 点 (px,py) 到线段 (x1,y1)-(x2,y2) 的最短距离。 */
    private static float distanceToSegment(float px, float py, float x1, float y1, float x2, float y2){
        float dx = x2 - x1, dy = y2 - y1;
        float len2 = dx * dx + dy * dy;
        if(len2 < 0.0001f) return Mathf.dst(px, py, x1, y1);

        float t = Mathf.clamp(((px - x1) * dx + (py - y1) * dy) / len2);
        return Mathf.dst(px, py, x1 + dx * t, y1 + dy * t);
    }
}
