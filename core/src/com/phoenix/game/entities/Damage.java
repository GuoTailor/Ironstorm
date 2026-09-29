package com.phoenix.game.entities;
import com.phoenix.game.content.Bullets;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.effect.Fire;
import com.phoenix.game.entities.effect.Lightning;
import com.phoenix.game.entities.traits.Entity;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.graphics.Pal;

import com.badlogic.gdx.graphics.Color;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.game.EventType.Trigger;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.struct.GridBits;
import com.phoenix.game.struct.IntQueue;
import com.phoenix.game.world.Tile;

import java.util.HashSet;

/**
 * 伤害/爆炸工具类。参照 Mindustry mindustry.entities.Damage 移植。
 * <p>重载口径与原版一致：4 参重载（可带 team）= 6 参 {@code complete=false} 的转发
 * （单位距离衰减 + 击退 + {@link #tileDamage} 菱形蔓延伤建筑）；{@code complete=true}
 * 走 {@link #completeDamage} 整块摧毁（波次空投区清场用）。
 */
public class Damage{

    /** tileDamage 传播用的已访问位图（对应原版 {@code Damage.bits}，Damage.java:28；30x30 覆盖半径上限 15）。 */
    private static GridBits bits = new GridBits(30, 30);
    /** tileDamage 传播队列（对应原版 {@code Damage.propagation}，Damage.java:29；addFirst+removeLast 实为 LIFO）。 */
    private static IntQueue propagation = new IntQueue();

    /** 对范围内所有单位与建筑造成伤害，不分敌我（对应原版 {@code Damage.damage(float...)，Damage.java:170}）。 */
    public static void damage(float x, float y, float radius, float damage){
        damage(null, x, y, radius, damage);
    }

    /** 对范围内敌方单位与建筑造成伤害（对应原版 {@code Damage.damage(Team, float...)，Damage.java:175}）。 */
    public static void damage(Team team, float x, float y, float radius, float damage){
        damage(team, x, y, radius, damage, false);
    }

    /**
     * 对范围内敌方单位（距离衰减 + 击退）造成伤害；建筑部分：
     * {@code complete=false} 走 {@link #tileDamage} 菱形蔓延（原版 Damage.java:203-208），
     * {@code complete=true} 走 {@link #completeDamage} 整块摧毁（原版 Damage.java:209-211）。
     * 对应原版 {@code Damage.damage(Team, float, float, float, float, boolean)}（Damage.java:180）。
     * <p>消费方：溅射子弹（BulletType，经 4 参转发进入）与波次开场的空投区清场
     * （WaveSpawner.spawnEnemies，{@code complete=true}）。
     * <p>{@code TileEntity.handleDamage} 即原版 {@code tile.entity.damage}（含血量归零销毁）。
     */
    public static void damage(Team team, float x, float y, float radius, float damage, boolean complete){
        java.util.function.Consumer<BaseUnit> cons = entity -> {
            if(entity.getTeam() == team || entity.dst(x, y) > radius){
                return;
            }
            float amount = calculateDamage(x, y, entity.getX(), entity.getY(), radius, damage);
            entity.damage(amount);
            //击退（原版 TODO 注释保留：TODO better velocity displacement）
            float dst = Tmp.v1.set(entity.getX() - x, entity.getY() - y).len();
            entity.velocity().add(Tmp.v1.setLength((1f - dst / radius) * 2f / entity.mass()));

            //玩家被"排除区清场"级别的伤害波及致死 → 广播事件（原版给 mod/重生逻辑用的钩子；126.2 里只有 fire 没有内置监听者）
            //⚠ 本项目 Player 不是 BaseUnit 子类，"玩家实体"等价物是玩家当前控制的单位
            if(complete && damage >= 9999999f && Vars.player != null && entity == Vars.player.unit()){
                Events.fire(Trigger.exclusionDeath);
            }
        };

        //rect.setSize(radius * 2).setCenter(x, y) 的矩形粗筛 + cons 里的圆形精筛，口径与原版一致
        if(team != null){
            Units.nearbyEnemies(team, x - radius, y - radius, radius * 2f, radius * 2f, cons);
        }else{
            Units.nearby(x - radius, y - radius, radius * 2f, radius * 2f, cons);
        }

        if(!complete){
            if(Vars.world == null) return;

            int trad = (int)(radius / Vars.tilesize);
            Tile tile = Vars.world.tileWorld(x, y);
            if(tile != null){
                tileDamage(team, tile.x, tile.y, trad, damage);
            }
        }else{
            completeDamage(team, x, y, radius, damage);
        }
    }

    /**
     * 菱形蔓延的建筑伤害（对应原版 {@code Damage.tileDamage}，Damage.java:214-255）。
     * 从 ({@code startx}, {@code starty}) 起，按曼哈顿距离逐环向外传播，伤害每环衰减
     * {@code ×(1 − dst/radius)}（衰减归零即停，形成菱形图案）；命中敌方建筑先扣血
     * （{@code handleDamage}），扣剩的余量继续向四邻传播（穿透伤害）。
     * <p>⚠ 原版 {@code PropCell.damage} 是 short：单格伤害 &gt; 32767 会在打包时截断成负数
     * 而提前停止传播，本实现保持该行为。
     * <p>⚠ 原版 {@code tile.getTeam() != team} 是引用比较：{@code team == null} 时
     * 所有建筑（含友军）都吃伤害（{@code damage(null, ...)} 无差别口径的一部分）。
     */
    public static void tileDamage(Team team, int startx, int starty, int radius, float baseDamage){
        bits.clear();
        propagation.clear();
        int bitOffset = bits.width() / 2;

        propagation.addFirst(propCell(0, 0, (short)baseDamage));
        //clamp radius to fit bits
        radius = Math.min(radius, bits.width() / 2);

        while(!propagation.isEmpty()){
            int prop = propagation.removeLast();
            int x = propCellX(prop);
            int y = propCellY(prop);
            int damage = propCellDamage(prop);
            //manhattan distance used for calculating falloff, results in a diamond pattern
            int dst = Math.abs(x) + Math.abs(y);

            int scaledDamage = (int)(damage * (1f - (float)dst / radius));

            bits.set(bitOffset + x, bitOffset + y);
            Tile tile = Vars.world.ltile(startx + x, starty + y);

            if(scaledDamage <= 0 || tile == null) continue;

            //apply damage to entity if needed（TileEntity.health 为 protected → 走 health()；扣血走 handleDamage）
            if(tile.entity != null && tile.getTeam() != team){
                int health = (int)tile.entity.health();
                if(tile.entity.health() > 0){
                    tile.entity.handleDamage(scaledDamage);
                    scaledDamage -= health;

                    if(scaledDamage <= 0) continue;
                }
            }

            for(int i = 0; i < Geometry.d4.length; i++){
                int px = x + Geometry.d4[i].x, py = y + Geometry.d4[i].y;
                if(!bits.get(bitOffset + px, bitOffset + py)){
                    propagation.addFirst(propCell(px, py, (short)scaledDamage));
                }
            }
        }
    }

    // ---- PropCell：原版 @Struct 注解生成的打包类（mindustry.gen.PropCell），
    //      byte x + byte y + short damage 压进一个 int；此处用静态方法等价实现 ----

    private static int propCell(int x, int y, short damage){
        return (x & 0xFF) | ((y & 0xFF) << 8) | ((damage & 0xFFFF) << 16);
    }

    private static int propCellX(int cell){
        return (byte)cell;
    }

    private static int propCellY(int cell){
        return (byte)(cell >> 8);
    }

    private static int propCellDamage(int cell){
        return (short)(cell >> 16);
    }

    /**
     * 摧毁范围内敌方建筑（对应原版 {@code Damage.completeDamage}，Damage.java:257）。
     * 波次开场用它清空投区里玩家堵门的建筑/炮塔（{@code 99999999f} 一击必拆）。
     */
    private static void completeDamage(Team team, float x, float y, float radius, float damage){
        if(Vars.world == null) return;

        int trad = (int)(radius / Vars.tilesize);
        for(int dx = -trad; dx <= trad; dx++){
            for(int dy = -trad; dy <= trad; dy++){
                Tile tile = Vars.world.tile(Math.round(x / Vars.tilesize) + dx, Math.round(y / Vars.tilesize) + dy);
                if(tile != null && tile.entity != null && (team == null || team.isEnemy(tile.getTeam())) && Mathf.dst(dx, dy) <= trad){
                    //本项目 TileEntity.handleDamage 即原版 TileEntity.damage（含血量归零销毁）
                    tile.entity.handleDamage(damage);
                }
            }
        }
    }

    /** 距离衰减（对应原版 {@code Damage.calculateDamage}，Damage.java:269）：中心全额、边缘打 6 折。 */
    private static float calculateDamage(float x, float y, float tx, float ty, float radius, float damage){
        float dist = Mathf.dst(x, y, tx, ty);
        float falloff = 0.4f;
        float scaled = Mathf.lerp(1f - dist / radius, 1f, falloff);
        return damage * scaled;
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

    /**
     * 动态爆炸（带可燃/爆炸物属性，对应原版 {@code Damage.dynamicExplosion}，Damage.java:33-65）。
     * <ul>
     *   <li>{@code power}（电力缓冲）：延迟放出的闪电（{@code Lightning.create}，队属 derelict），
     *       即"电力缓冲爆炸"的电击视觉；</li>
     *   <li>{@code flammability}（可燃物）：延迟甩出的火球（{@code Bullets.fireball}）；</li>
     *   <li>{@code explosiveness}（爆炸物）：多波伤害（全场单位 + 建筑）与冲击波特效；</li>
     *   <li>末尾固定：按威力震屏 + 可变强度爆炸特效（强度 = radius/8）。</li>
     * </ul>
     * <p>调用点：{@code Block.onDestroyed}（建筑摧毁）与 {@code BaseUnit.onDeath}（单位死亡）。
     */
    public static void dynamicExplosion(float x, float y, float flammability, float explosiveness, float power, float radius, Color color){
        //电力缓冲爆炸：闪电（原版 Lightning.create(Team.derelict, Pal.power, 3, x, y, angle, branches)）
        for(int i = 0; i < Mathf.clamp(power / 20, 0, 6); i++){
            int branches = 5 + Mathf.clamp((int)(power / 30), 1, 20);
            Time.run(i * 2f + Mathf.random(4f), () -> Lightning.create(Team.derelict, Pal.power, 3,
                x, y, Mathf.random(360f), branches + (int)Mathf.range(2)));
        }

        //可燃物爆炸：火球（原版 Call.createBullet(Bullets.fireball, Team.derelict, ...)；
        //本项目无网络层 Call → 本地直接生成，写法与 Fire/Puddle 的火球一致）
        for(int i = 0; i < Mathf.clamp(flammability / 4, 0, 30); i++){
            Time.run(i / 2f, () -> Bullet.create(Bullets.fireball, (Entity)null, Team.derelict, x, y, Mathf.random(360f), 1f, 1f, null));
        }

        int waves = Mathf.clamp((int)(explosiveness / 4), 0, 30);

        for(int i = 0; i < waves; i++){
            int f = i;
            Time.run(i * 2f, () -> {
                //原版 Damage.damage(x, y, rad, dmg) → damage(null, ..., false)：全场单位（含友军，距离衰减）+ tileDamage 菱形蔓延伤建筑
                damage(null, x, y, Mathf.clamp(radius + explosiveness, 0, 50f) * ((f + 1f) / waves), explosiveness / 2f, false);
                Effects.effect(Fx.blockExplosionSmoke, x + Mathf.range(radius), y + Mathf.range(radius));
            });
        }

        if(explosiveness > 15f){
            Effects.effect(Fx.shockwave, x, y);
        }

        if(explosiveness > 30f){
            Effects.effect(Fx.bigShockwave, x, y);
        }

        float shake = Math.min(explosiveness / 4f + 3f, 9f);
        Effects.shake(shake, shake, x, y);
        Effects.effect(Fx.dynamicExplosion, x, y, radius / 8f);
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
        Effects.effect(effect, tile.block().centerX(tile), tile.block().centerY(tile));
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
