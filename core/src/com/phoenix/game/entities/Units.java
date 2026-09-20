package com.phoenix.game.entities;
import com.phoenix.game.entities.type.BaseUnit;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.game.Team;
import com.phoenix.game.entities.traits.TargetTrait;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 最小实现：单位与阵营交互工具类。参照 Mindustry mindustry.entities.Units 移植。
 * 原版使用 EntityGroup / indexer，这里先用一个简单的单位列表替代。
 */
public class Units {
    /** 当前登记的所有单位（原版为 EntityGroup）。 */
    public static final Array<BaseUnit> units = new Array<>();

    private static final Rectangle hitrect = new Rectangle();
    private static BaseUnit result;
    private static float cdist;

    public static void add(BaseUnit unit){
        units.add(unit);
    }

    public static void remove(BaseUnit unit){
        units.removeValue(unit, true);
    }

    /**
     * Validates a target.
     * @return whether the target is invalid
     */
    public static boolean invalidateTarget(TargetTrait target, float x, float y, float range){
        return target == null || (range != Float.MAX_VALUE && !target.withinDst(x, y, range)) || !target.isValid();
    }

    /** See {@link #invalidateTarget(TargetTrait, float, float, float)} */
    public static boolean invalidateTarget(TargetTrait target, BaseUnit targeter){
        return invalidateTarget(target, targeter.getX(), targeter.getY(), targeter.getWeapon().bullet.range());
    }

    /** Returns whether there are any entities in this rectangle. */
    public static boolean anyEntities(float x, float y, float width, float height){
        for(int i = 0; i < units.size; i++){
            BaseUnit unit = units.get(i);
            if(!unit.isFlying()){
                unit.hitbox(hitrect);

                if(overlaps(hitrect, x, y, width, height)){
                    return true;
                }
            }
        }

        return false;
    }

    /** libgdx 的 Rectangle 没有 overlaps(x, y, w, h) 重载，这里补一个。 */
    private static boolean overlaps(Rectangle rect, float x, float y, float width, float height){
        return rect.x < x + width && rect.x + rect.width > x && rect.y < y + height && rect.y + rect.height > y;
    }

    /** Returns the closest target enemy. First, units are checked, then tile entities. */
    public static TargetTrait closestTarget(Team team, float x, float y, float range){
        return closestTarget(team, x, y, range, u -> true);
    }

    /** Returns the closest target enemy. First, units are checked, then tile entities. */
    public static TargetTrait closestTarget(Team team, float x, float y, float range, Predicate<BaseUnit> unitPred){
        TargetTrait unit = closestEnemy(team, x, y, range, unitPred);
        if(unit != null) return unit;

        //单位无可打目标时，退而打敌方建筑
        return closestEnemyBuilding(team, x, y, range);
    }

    /** Returns the closest enemy of this team. Filter by predicate. */
    public static BaseUnit closestEnemy(Team team, float x, float y, float range, Predicate<BaseUnit> predicate){
        result = null;
        cdist = 0f;

        //注意：下标遍历，避免 libgdx Array 迭代器嵌套
        for(int i = 0; i < units.size; i++){
            BaseUnit e = units.get(i);
            if(e.isDead() || !team.isEnemy(e.getTeam()) || !predicate.test(e)) continue;

            float dst2 = Mathf.dst2(e.getX(), e.getY(), x, y);
            if(dst2 < range*range && (result == null || dst2 < cdist)){
                result = e;
                cdist = dst2;
            }
        }

        return result;
    }

    /** 最近的敌方建筑目标（单位都处理完后，攻击建筑的兜底）。
     * 参考 {@code Vars.world.closestTile}+建筑优先级：核心>炮塔>其它。
     * @param building predicate 需乱码「判定该队伍的敌对」；这里统一按 team.isEnemy
     * @return 敌方建筑瓦片（Tile 实现 TargetTrait，可直接当目标）
     */
    public static TargetTrait closestEnemyBuilding(Team team, float x, float y, float range){
        if(Vars.world == null) return null;

        Tile best = null;
        float bestScore = Float.MAX_VALUE;

        for(Tile tile : Vars.world.tiles){
            if(tile == null || tile.entity == null) continue;
            if(!team.isEnemy(tile.getTeam())) continue;
            //多格建筑只认中心
            if(tile.link() != tile) continue;

            float dst = Mathf.dst2(x, y, tile.worldx(), tile.worldy());
            if(dst > range * range) continue;

            //优先级权值：核心最近优先，炮塔其次，其它建筑最后
            float weight;
            if(tile.block().flags.contains(com.phoenix.game.world.meta.BlockFlag.core)){
                weight = 0f;
            }else if(tile.block().flags.contains(com.phoenix.game.world.meta.BlockFlag.turret)){
                weight = 1f;
            }else{
                weight = 2f;
            }
            float score = dst * (2f + weight); //乘性，保证近的同类优先、跨类按权值分档

            if(score < bestScore){
                bestScore = score;
                best = tile;
            }
        }

        return best;
    }

    /** Returns the closest ally of this team. Filter by predicate. */
    public static BaseUnit closest(Team team, float x, float y, float range, Predicate<BaseUnit> predicate){
        result = null;
        cdist = 0f;

        for(int i = 0; i < units.size; i++){
            BaseUnit e = units.get(i);
            if(e.getTeam() != team || !predicate.test(e)) continue;

            float dst = Mathf.dst2(e.getX(), e.getY(), x, y);
            if(result == null || dst < cdist){
                result = e;
                cdist = dst;
            }
        }

        return result;
    }

    /** Iterates over all units in a rectangle. */
    public static void nearby(float x, float y, float width, float height, Consumer<BaseUnit> cons){
        for(int i = 0; i < units.size; i++){
            BaseUnit unit = units.get(i);
            if(unit.getX() >= x && unit.getY() >= y && unit.getX() <= x + width && unit.getY() <= y + height){
                cons.accept(unit);
            }
        }
    }

    /** Iterates over all units in a circle around this position. */
    public static void nearby(float x, float y, float radius, Consumer<BaseUnit> cons){
        nearby(x - radius, y - radius, radius * 2f, radius * 2f, unit -> {
            if(unit.withinDst(x, y, radius)){
                cons.accept(unit);
            }
        });
    }

    /** Iterates over all units of a team. */
    public static void nearby(Team team, float x, float y, float radius, Consumer<BaseUnit> cons){
        nearby(x, y, radius, unit -> {
            if(unit.getTeam() == team){
                cons.accept(unit);
            }
        });
    }

    /** Iterates over all units that are enemies of this team. */
    public static void nearbyEnemies(Team team, float x, float y, float width, float height, Consumer<BaseUnit> cons){
        nearby(x, y, width, height, u -> {
            if(team.isEnemy(u.getTeam())){
                cons.accept(u);
            }
        });
    }

    /** Iterates over all units. */
    public static void all(Consumer<BaseUnit> cons){
        for(int i = 0; i < units.size; i++){
            cons.accept(units.get(i));
        }
    }

    /** Iterates over all units of a team. */
    public static void each(Team team, Consumer<BaseUnit> cons){
        for(int i = 0; i < units.size; i++){
            BaseUnit unit = units.get(i);
            if(unit.getTeam() == team){
                cons.accept(unit);
            }
        }
    }
}
