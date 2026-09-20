package com.phoenix.game.entities.effect;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Bullets;
import com.phoenix.game.content.Fx;
import com.phoenix.game.content.StatusEffects;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Damage;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.traits.Entity;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Pos;
import com.phoenix.game.world.Tile;
import io.anuke.mindustry.gen.Sounds;

import java.util.HashMap;
import java.util.Map;

import static com.phoenix.game.Vars.tilesize;

/**
 * 燃烧：每格一个火。参照 Mindustry mindustry.entities.effect.Fire 移植。
 *
 * <p>行为：火焰本身不绘制（原版同理），每帧随机喷 {@link Fx#fire}/{@link Fx#fireSmoke} 粒子来表现；
 * 只要瓦片上有可燃物（库存物品/液体）就会一直烧下去并蔓延到邻格，烧到一定概率还会甩出火球；
 * 同时周期性灼烧该格建筑、给附近地面单位挂 {@code burning} 状态。
 *
 * <p>刻意偏离（与原版不同之处）：
 * <ul>
 *   <li><b>不做联机同步</b>：原版 {@code Fire.create} 在客机直接 return（火由服务端权威生成 + SyncTrait 同步）。
 *       本工程的建筑逻辑两端都跑仿真（子弹也是两端各自 {@code Bullet.create}），所以火同样两端各自生成；
 *       建筑掉血走 {@link TileEntity#handleDamage}，它在客机是 no-op，血量仍由服务端广播，不会两端打架。</li>
 *   <li><b>没有液体水洼（Puddle）</b>：原版会读脚下水洼的可燃性让火烧得更久，本工程未移植 Puddle 系统，跳过。
 *       液体地板（水）靠 {@link Block#getFlammability} 返回 0 让火以 8 倍速烧完，效果上等于被浇灭。</li>
 *   <li><b>音效无距离衰减</b>：原版是 {@code Sounds.fire.at(this)}，本工程音效系统没有位置衰减，
 *       改成只在镜头附近才播，否则隔着半张地图的火也会响。</li>
 *   <li><b>存档</b>：原版实现 SaveTrait 把火写进存档，本工程存档只存瓦片/实体，火不落盘（读档后不恢复）。</li>
 * </ul>
 */
public class Fire{
    /** 场上所有的火（对应原版 fireGroup）。倒序遍历，允许更新中移除自身。 */
    public static final Array<Fire> all = new Array<>();
    /** 按瓦片 pos 索引的火（对应原版 Fire.map）：同一格只会有一团火。 */
    private static final Map<Integer, Fire> map = new HashMap<>();

    private static final float baseLifetime = 1000f, spreadChance = 0.05f, fireballChance = 0.07f;
    /** 音效只在这个距离内播放（原版由音效系统的距离衰减处理）。 */
    private static final float soundRange = 600f;

    private Tile tile;
    private Block block;
    private float baseFlammability = -1f;
    private float lifetime;
    private float time;
    private float x, y;
    private boolean dead;

    /** 在瓦片上点火。该格已经有火时刷新它的寿命（对应原版 {@code Fire.create}）。 */
    public static void create(Tile tile){
        if(tile == null || Vars.world == null) return;

        Fire fire = map.get(tile.pos());

        if(fire == null){
            fire = new Fire();
            fire.tile = tile;
            fire.lifetime = baseLifetime;
            fire.x = tile.worldx();
            fire.y = tile.worldy();
            map.put(tile.pos(), fire);
            all.add(fire);
        }else{
            fire.lifetime = baseLifetime;
            fire.time = 0f;
        }
    }

    /** @return 该格是否有正在燃烧的火。 */
    public static boolean has(int x, int y){
        if(Vars.world == null) return false;
        if(x < 0 || y < 0 || x >= Vars.world.width() || y >= Vars.world.height()) return false;

        Fire fire = map.get(Pos.get(x, y));
        return fire != null && fire.tile != null && fire.tile.x == x && fire.tile.y == y && fire.fin() < 1f;
    }

    /**
     * 灭火：不做删除，而是把它的"已燃烧时长"往前推，推过寿命自然就熄了。
     * 参照 Mindustry {@code Fire.extinguish}。
     * @param intensity 每秒推进的时长（原版液体子弹用 100f 这种大值直接浇灭）
     */
    public static void extinguish(Tile tile, float intensity){
        if(tile == null) return;
        Fire fire = map.get(tile.pos());
        if(fire != null) fire.time += intensity * Time.delta();
    }

    /** 每帧更新所有火（由 {@code Logic.update} 调用）。 */
    public static void updateAll(){
        for(int i = all.size - 1; i >= 0; i--){
            Fire fire = all.get(i);
            fire.update();
            if(fire.dead) all.removeIndex(i);
        }
    }

    /** 清空所有火（回菜单/重开一局时用）。 */
    public static void clear(){
        all.clear();
        map.clear();
    }

    /** @return 场上火的数量（调试用）。 */
    public static int count(){
        return all.size;
    }

    /** @return 已燃烧比例 0~1。 */
    public float fin(){
        return lifetime <= 0f ? 1f : Mathf.clamp(time / lifetime);
    }

    private void update(){
        if(Mathf.chance(0.1f * Time.delta())){
            Effects.effect(Fx.fire, x + Mathf.range(4f), y + Mathf.range(4f));
        }

        if(Mathf.chance(0.05f * Time.delta())){
            Effects.effect(Fx.fireSmoke, x + Mathf.range(4f), y + Mathf.range(4f));
        }

        if(Mathf.chance(0.001f * Time.delta()) && (Core.camera == null || Core.camera.position.dst(x, y, 0f) < soundRange)){
            Sounds.fire.play(1f);
        }

        time = Mathf.clamp(time + Time.delta(), 0f, lifetime);

        if(time >= lifetime || tile == null){
            remove();
            return;
        }

        //该格上的建筑（多格建筑解析到中心瓦片）：有建筑才有"烧到东西"的额外寿命
        TileEntity entity = tile.link().entity;
        boolean damage = entity != null;

        float flammability = baseFlammability;

        //没有任何可燃物（空瓦片 / 水地板）：加速烧完，相当于自己熄灭
        if(!damage && flammability <= 0f){
            time += Time.delta() * 8f;
        }

        //方块换了（被拆/被建）要重算可燃性
        if(baseFlammability < 0f || block != tile.block()){
            baseFlammability = tile.block().getFlammability(tile);
            block = tile.block();
            flammability = baseFlammability;
        }

        //烧的东西越可燃，火越难灭（寿命持续延长）
        if(damage){
            lifetime += Mathf.clamp(flammability / 8f, 0f, 0.6f) * Time.delta();
        }

        //蔓延到邻格：可燃性 > 1 才有机会
        if(flammability > 1f && Mathf.chance(spreadChance * Time.delta() * Mathf.clamp(flammability / 5f, 0.3f, 2f))){
            GridPoint2 p = Geometry.d4[Mathf.random(3)];
            create(Vars.world.tile(tile.x + p.x, tile.y + p.y));

            //偶尔甩出一个火球（原版 Call.createBullet(Bullets.fireball, Team.derelict, ...)）
            if(Mathf.chance(fireballChance * Time.delta() * Mathf.clamp(flammability / 10f))){
                Bullet.create(Bullets.fireball, (Entity)null, Team.derelict, x, y, Mathf.random(360f), 1f, 1f, null);
            }
        }

        //周期性灼烧：烧建筑 + 给附近地面单位挂燃烧状态
        if(Mathf.chance(0.1f * Time.delta())){
            if(damage){
                entity.handleDamage(0.4f);
            }

            Damage.damageUnits(null, tile.worldx(), tile.worldy(), tilesize, 3f,
                unit -> !unit.isFlying() && !unit.isImmune(StatusEffects.burning),
                unit -> unit.applyEffect(StatusEffects.burning, 60 * 5));
        }
    }

    /** 熄灭并摘掉自己（对应原版 {@code Fire.removed}）。 */
    private void remove(){
        if(tile != null){
            //只摘掉"指向自己"的那条记录：同格可能已经被重新点燃成了另一团火
            map.remove(tile.pos(), this);
        }
        dead = true;
    }

    @Override
    public String toString(){
        return "Fire[" + (tile == null ? "?" : tile.x + "," + tile.y) + " " + fin() + "]";
    }
}
