package com.phoenix.game.entities.type;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Interval;
import com.phoenix.game.core.Time;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.game.Team;
import com.phoenix.game.entities.traits.DamageTrait;
import com.phoenix.game.entities.traits.Entity;
import com.phoenix.game.entities.traits.SolidTrait;
import com.phoenix.game.entities.traits.VelocityTrait;
import com.phoenix.game.entities.type.SolidEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;

public class Bullet extends SolidEntity implements Pool.Poolable, VelocityTrait, DamageTrait{

    /** 所有在场子弹（最小实现，替代原版 EntityGroup） */
    public static final Array<Bullet> all = new Array<>();

    public Interval timer = new Interval(3);

    private float lifeScl;
    private Object data;
    private boolean supressCollision, supressOnce, initialized, deflected;

    protected BulletType type;
    protected Entity owner;
    protected float time;

    /** Internal use only! */
    public Bullet(){
    }

    public static Bullet create(BulletType type,  float x, float y, float angle){
        //必须显式转型为 Entity：裸 null 会被重载解析绑定到更具体的 create(..., Bullet parent, ...)，
        //而那条路径会读 parent.owner，导致必然 NPE。
        return create(type, (Entity)null, x, y, angle);
    }

    public static Bullet create(BulletType type, Entity owner, float x, float y, float angle){
        return create(type, owner, x, y, angle, 1f);
    }

    public static Bullet create(BulletType type, Entity owner, float x, float y, float angle, float velocityScl){
        return create(type, owner, x, y, angle, velocityScl, 1f);
    }

    public static Bullet create(BulletType type, Entity owner,  float x, float y, float angle, float velocityScl, float lifetimeScl){
        return create(type, owner, null, x, y, angle, velocityScl, lifetimeScl, null);
    }

    /** 指定队伍创建子弹（建筑/炮塔用：实体队伍可能滞后于瓦片队伍）。 */
    public static Bullet create(BulletType type, Entity owner, Team team, float x, float y, float angle){
        return create(type, owner, team, x, y, angle, 1f, 1f, null);
    }

    /** 指定队伍与速度倍率创建子弹。 */
    public static Bullet create(BulletType type, Entity owner, Team team, float x, float y, float angle, float velocityScl){
        return create(type, owner, team, x, y, angle, velocityScl, 1f, null);
    }

    public static Bullet create(BulletType type, Bullet parent, float x, float y, float angle){
        return create(type, parent.owner, x, y, angle);
    }

    public static Bullet create(BulletType type, Bullet parent, float x, float y, float angle, float velocityScl){
        return create(type, parent.owner, x, y, angle, velocityScl);
    }

    /**
     * 唯一创建入口：team 为 null 时继承 owner 队伍，所有公开重载最终都汇聚到这里。
     * <p>本方法**不做网络广播**：开火事件由 {@code Weapon.shoot()} 负责（见该类注释）。
     */
    public static Bullet create(BulletType type, Entity owner, Team team, float x, float y, float angle, float velocityScl, float lifetimeScl, Object data){
        Bullet bullet = Pools.obtain(Bullet.class);
        //id 只在构造器里分配过，池化复用会沿用旧 id（网络按 id 做映射，复用会导致错乱）
        bullet.incrementID();
        bullet.type = type;
        bullet.owner = owner;
        bullet.data = data;
        //从对象池取出时必须是存活状态（死亡标记在 remove() 时设置，供遍历时跳过已回收的子弹）
        bullet.setDead(false);

        //队伍：显式传入优先，否则继承发射者（对应原版 Bullet.create 的 team 参数）
        bullet.team = team != null ? team
                : (owner instanceof BaseEntity ? ((BaseEntity)owner).getTeam() : Team.derelict);

        bullet.velocity.set(0, type.speed).setAngle(angle).scl(velocityScl);
        if(type.keepVelocity){
            bullet.velocity.add(owner instanceof VelocityTrait ? ((VelocityTrait)owner).velocity() : Vector2.Zero);
        }

        bullet.lifeScl = lifetimeScl;

        bullet.set(x - bullet.velocity.x * Time.delta(), y - bullet.velocity.y * Time.delta());

        all.add(bullet);
        bullet.added();

        return bullet;
    }

    //网络同步说明：子弹不做位置同步，也**不在这里**广播 ——
    //开火事件由 Weapon.shoot() 广播（对应原版 Call.onGenericShootWeapon），客户端本地 shootDirect 生成子弹；
    //炮塔/建筑的子弹两端各自 Bullet.create，不广播（因为建筑两端都跑仿真）。详见 Weapon.java。

    public Entity getOwner(){
        return owner;
    }

    public boolean collidesTiles(){
        return type.collidesTiles;
    }

    public void deflect(){
        supressCollision = true;
        supressOnce = true;
        deflected = true;
    }

    public boolean isDeflected(){
        return deflected;
    }

    public BulletType getBulletType(){
        return type;
    }

    public void resetOwner(Entity entity){
        this.owner = entity;
    }

    public void scaleTime(float add){
        time += add;
    }

    public Object getData(){
        return data;
    }

    public void setData(Object data){
        this.data = data;
    }

    public float damageMultiplier(){
        //玩家发射的子弹继承玩家等级加成
        if(owner instanceof BaseUnit && ((BaseUnit)owner).isPlayer && Vars.player != null){
            return Vars.player.damageMultiplier();
        }
        return 1f;
    }

    public float drawSize(){
        return type.drawSize;
    }

    public float damage(){
        if(data instanceof Float){
            return (Float)data;
        }
        return type.damage * damageMultiplier();
    }

    public float getShieldDamage(){
        return Math.max(damage(), type.splashDamage);
    }

    public boolean collides(SolidTrait other){
        return type.collides && (other != owner) && !supressCollision && type.collidesAir;
    }

    @Override
    public void collision(SolidTrait other, float x, float y){
        //已被回收的子弹不再处理；先取到类型引用，因为 remove() 会清空字段
        BulletType current = type;
        if(current == null) return;

        current.hit(this, x, y);

        if(other instanceof BaseUnit){
            BaseUnit unit = (BaseUnit)other;
            unit.velocity().add(Tmp.v1.set(other.getX(), other.getY()).sub(x, y).setLength(current.knockback / unit.mass()));
            unit.applyEffect(current.status, current.statusDuration);
        }

        if(!current.pierce) remove();
    }

    @Override
    public void update(){
        if(isDead()) return;

        //记录上一帧位置（瓦片射线用）
        lastPosition().set(x, y);

        type.update(this);

        //注意：type.update 里的回调可能已经把这颗子弹回收了（remove() 会清空 type，见 MassDriver 的投递回调），
        //此时必须立刻返回，否则下面读 type.drag / type.lifetime 会 NPE
        if(isDead() || type == null) return;

        x += velocity.x * Time.delta();
        y += velocity.y * Time.delta();

        velocity.scl(Mathf.clamp(1f - type.drag * Time.delta()));

        time += Time.delta() * 1f / (lifeScl);
        time = Mathf.clamp(time, 0, type.lifetime);

        if(time >= type.lifetime){
            if(!supressCollision) type.despawned(this);
            remove();
            return;
        }

        //瓦片碰撞：命中建筑实体则造成伤害，其次实心方块
        if(type.hitTiles && collidesTiles() && !supressCollision && initialized && Vars.world != null){
            Vars.world.raycastEach(
                    Vars.world.toTile(lastPosition().x), Vars.world.toTile(lastPosition().y),
                    Vars.world.toTile(x), Vars.world.toTile(y), (tx, ty) -> {

                        Tile tile = Vars.world.ltile(tx, ty);
                        if(tile == null) return false;

                        //只有实心方块会阻挡子弹（与原版一致：传送带等非实心建筑不阻挡）
                        if(!tile.solid()) return false;

                        if(tile.entity != null){
                            //先给方块一次拦截机会（护盾/反射墙）；被拦截的子弹不再造成伤害
                            tile.block().handleBulletHit(tile.entity, this);

                            //只有敌方建筑会被扣血；友方墙体只挡子弹、不吃伤害
                            if(!deflected && team.isEnemy(tile.getTeam())){
                                tile.entity.handleDamage(type.damage);
                                //受击火花反馈
                                com.phoenix.game.entities.Effects.effect(com.phoenix.game.content.Fx.spark, tile.drawx(), tile.drawy(), rot());
                            }
                        }

                        if(!deflected && !supressCollision){
                            type.hitTile(this);
                            remove();
                        }
                        return true;
                    });
        }

        if(isDead()) return;

        if(supressOnce){
            supressCollision = false;
            supressOnce = false;
        }

        initialized = true;
    }

    @Override
    public void reset(){
        type = null;
        owner = null;
        team = Team.derelict;
        velocity.setZero();
        time = 0f;
        timer.clear();
        lifeScl = 1f;
        data = null;
        supressCollision = false;
        supressOnce = false;
        deflected = false;
        initialized = false;
    }

    @Override
    public void hitbox(Rectangle rect){
        rect.setSize(type.hitSize).setCenter(x, y);
    }

    @Override
    public void hitboxTile(Rectangle rect){
        rect.setSize(type.hitSize).setCenter(x, y);
    }

    public float lifetime(){
        return type.lifetime;
    }

    public void time(float time){
        this.time = time;
    }

    public float time(){
        return time;
    }

    @Override
    public void removed(){
        all.removeValue(this, true);
        Pools.free(this);
    }

    @Override
    public void added(){
        if(type == null) return;
        type.init(this);
    }

    /** 绘制自身（对应原版 DrawTrait.draw，待 DrawTrait 移植后可加回 @Override）。 */
    public void draw(){
        if(type != null) type.draw(this);
    }

    /** 生命周期进度（对应原版 Scaled.fin，待 Scaled 移植后可加回 @Override）。 */
    public float fin(){
        return type.lifetime <= 0f ? 1f : time / type.lifetime;
    }

    /** @return 1 - fin()，即"剩余比例"（对应原版 Scaled.fout）。 */
    public float fout(){
        return 1f - fin();
    }

    /** @return 0→1→0 的三角波（对应原版 Scaled.fslope），用于"中途最大"的粒子尺寸/拖尾。 */
    public float fslope(){
        float f = fin();
        return (0.5f - Math.abs(f - 0.5f)) * 2f;
    }

    @Override
    public Vector2 velocity(){
        return velocity;
    }

    public void velocity(float speed, float angle){
        velocity.set(0, speed).setAngle(angle);
    }

    public void limit(float f){
        velocity.limit(f);
    }

    /** Sets the bullet's rotation in degrees. */
    public void rot(float angle){
        velocity.setAngle(angle);
    }

    /** @return the bullet's rotation. */
    public float rot(){
        float angle = MathUtils.atan2(velocity.y, velocity.x) * Mathf.radiansToDegrees;
        if(angle < 0) angle += 360;
        return angle;
    }
}
