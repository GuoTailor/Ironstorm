package com.phoenix.game.entities.type;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.phoenix.game.Vars;
import com.phoenix.game.ai.Pathfinder.PathTarget;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Interval;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Damage;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.traits.DamageTrait;
import com.phoenix.game.entities.traits.HealthTrait;
import com.phoenix.game.entities.traits.SolidTrait;
import com.phoenix.game.entities.traits.TargetTrait;
import com.phoenix.game.entities.units.StateMachine;
import com.phoenix.game.entities.units.Statuses;
import com.phoenix.game.entities.units.UnitState;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.type.StatusEffect;
import com.phoenix.game.type.UnitType;
import com.phoenix.game.type.Weapon;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockFlag;

/**
 * create by GYH on 2024/12/25
 * 最小实现：单位基类。参照 Mindustry mindustry.entities.type.BaseUnit 移植。
 */
public class BaseUnit extends SolidEntity implements TargetTrait, HealthTrait {
    protected static int timerIndex = 0;

    protected static final int timerTarget = timerIndex++;
    protected static final int timerTarget2 = timerIndex++;
    protected static final int timerShootLeft = timerIndex++;
    protected static final int timerShootRight = timerIndex++;

    protected boolean loaded;
    protected UnitType type;
    public Weapon weapon;
    public float rotation;
    protected Interval timer = new Interval(5);
    protected int spawner = -65535;
    protected TargetTrait target;
    /** 状态效果 */
    protected final Statuses status = new Statuses();
    /** 状态机 */
    protected final StateMachine state = new StateMachine();
    /** 当前生命值 */
    protected float health;
    /** 是否由玩家控制（对应原版 Player 自身就是单位）。玩家单位不跑 AI 状态机，改由输入驱动。 */
    public boolean isPlayer;
    /** 控制该单位的玩家名（小地图上给其他玩家显示标签用，对应原版 {@code Player.name}）。 */
    public String playerName = "noname";
    /** 联机远端代理单位：本地只做渲染，位置/血量由服务器快照覆盖，不跑 AI/物理。 */
    public boolean isRemoteProxy;
    /** 远端代理单位的快照插值器（仅联机客户端创建；服务端与本地单位恒为 null）。 */
    public com.phoenix.game.net.Interpolator interpolator;

    public int getShootTimer(boolean left){
        return left ? timerShootLeft : timerShootRight;
    }

    /** 设置出厂/出生点标记（记录瓦片打包坐标，工厂靠它统计自己出厂的存活单位数）。 */
    public void setSpawner(Tile tile){
        this.spawner = tile == null ? -65535 : tile.pos();
    }

    /** @return 出厂/出生点的瓦片打包坐标；没有则返回 -65535。 */
    public int getSpawner(){
        return spawner;
    }

    public Interval getTimer() {
        return timer;
    }

    /** Initialize the type and team of this unit. Only call once! */
    public void init(UnitType type){
        if(this.type != null) throw new RuntimeException("This unit is already initialized!");

        this.type = type;
        this.health = type.health;
        type.load();

        Units.add(this);
        added();
    }

    public UnitType getType(){
        return type;
    }

    /** @return 单位图标区域（对应原版 BaseUnit.getIconRegion）；小地图/单位列表用。 */
    public com.badlogic.gdx.graphics.g2d.TextureRegion getIconRegion(){
        return type.icon(com.phoenix.game.ui.Cicon.full);
    }

    public Statuses status(){
        return status;
    }

    public float getSize(){
        return type.hitsize;
    }

    @Override
    public void health(float health){
        this.health = health;
    }

    @Override
    public float health(){
        return health;
    }

    @Override
    public float maxHealth(){
        return type.health * Vars.state.rules.unitHealthMultiplier * (isPlayer && Vars.player != null ? Vars.player.healthMultiplier() : 1f);
    }

    /**
     * 伤害入口。对应原版 {@code Unit.damage()}：**联机客户端空操作** —— 单位血量由服务端权威快照同步，
     * 客户端本地子弹只做表现。若这里不拦，本地伤害会和服务器血量互相打架（表现为血量抖动/单位本地误死）。
     */
    @Override
    public void damage(float amount){
        if(Vars.isClient()) return;
        HealthTrait.super.damage(amount);
    }

    @Override
    public void onDeath(){
        //先标记死亡，否则爆炸伤害会再次触发 onDeath 造成无限递归
        setDead(true);

        //玩家击杀敌方单位时获得经验
        if(Vars.player != null && !Vars.player.isDead() && this != Vars.player.unit()
                && team != null && Vars.player.team != null && team.isEnemy(Vars.player.team)){
            Vars.player.addXp(1);
        }

        Damage.dynamicExplosion(x, y, 0f, 2f, 0f, type.hitsize, Pal.darkFlame);

        Effects.effect(Fx.explosion, this);
        Effects.shake(2f, 2f, this);

        status.clear();
        remove();
    }

    public void applyEffect(StatusEffect effect, float duration){
        if(isDead() || effect == null) return;
        status.handleApply(this, effect, duration);
    }

    public boolean hasEffect(StatusEffect effect){
        return status.hasEffect(effect);
    }

    public boolean isImmune(StatusEffect effect){
        return false;
    }

    public float getDamageMultipler(){
        float base = status.getDamageMultiplier() * Vars.state.rules.unitDamageMultiplier;
        //玩家单位按等级加成
        if(isPlayer && Vars.player != null){
            base *= Vars.player.damageMultiplier();
        }
        return base;
    }

    /** @return 单位当前所在地板 */
    public Floor getFloorOn(){
        Tile tile = tileOn();
        return tile == null ? Blocks.air : tile.floor();
    }

    /** @return 单位当前所在瓦片 */
    public Tile tileOn(){
        return Vars.world == null ? null : Vars.world.tileWorld(x, y);
    }

    // ---- AI ----

    public void setState(UnitState state){
        this.state.set(state);
    }

    public UnitState getStartState(){
        return null;
    }

    public boolean retarget(){
        return timer.get(timerTarget, 20);
    }

    public void updateTargeting(){
        if(target != null && (!target.isValid() || target.isDead())){
            target = null;
        }

        if(target == null && retarget()){
            target = Units.closestTarget(team, x, y, type.attackLength, u -> type.targetAir || !u.isFlying());
        }
    }

    /** Only runs when the unit has a target. */
    public void behavior(){
    }

    /** 朝指定坐标直线移动。进攻目标（移动单位）走这条，不做寻路。 */
    public void moveTo(float destX, float destY){
        velocity.set(0, type.maxVelocity).setAngle(angleTo(destX, destY));
    }

    /**
     * 朝某个 PathTarget 流场的目标流动（绕墙）。用于核心/集结点等静态目标。
     * 流场数据未就绪时退化为直线朝目标移动。
     */
    public void moveTo(PathTarget path){
        if(Vars.pathfinder != null){
            Tile next = Vars.pathfinder.getTargetTile(tileOn(), team, path);
            if(next != null){
                moveTo(next.getX(), next.getY());
                return;
            }
        }
        moveToCore(path);
    }

    public void moveToCore(PathTarget path){
        Tile tile = path == PathTarget.enemyCores ? getClosestEnemyCore() : getClosest(BlockFlag.rally);

        if(tile == null){
            tile = getClosestCore();
        }

        if(tile != null){
            moveTo(tile.getX(), tile.getY());
        }
    }

    public void moveAwayFromCore(){
        Tile core = getClosestEnemyCore();
        if(core == null) return;

        velocity.set(0, type.maxVelocity).setAngle(angleTo(core) + 180f);
    }

    public Tile getClosestCore(){
        return Vars.state.teams.closestCore(x, y, team);
    }

    public Tile getClosestEnemyCore(){
        return Vars.state.teams.closestEnemyCore(x, y, team);
    }

    public Tile getClosest(BlockFlag flag){
        return Vars.world == null ? null : Vars.world.closestTile(x, y, team, flag, false);
    }

    public Tile getClosestSpawner(){
        return Vars.spawner == null ? null : Vars.spawner.getClosestSpawner(x, y);
    }

    // ---- 更新 ----

    @Override
    public void update(){
        if(isDead()){
            remove();
            return;
        }

        //联机远端代理单位：位置由服务器快照驱动，本地不跑 AI/物理（避免和快照打架）
        if(isRemoteProxy){
            status.update(this);
            return;
        }

        status.update(this);

        //玩家单位由输入驱动，不跑 AI（对应原版 Player.update 覆盖了 Unit 的 AI 逻辑）
        if(!isPlayer){
            updateTargeting();
            state.update();

            if(target != null){
                behavior();
            }
        }

        //速度积分 + 碰撞移动
        avoidOthers();
        updateVelocity();

        //液体上减速
        Floor floor = getFloorOn();
        if(floor != null && floor != Blocks.air && floor.isLiquid){
            velocity.scl(MathUtils.lerp(1f, floor.speedMultiplier, 0.1f));
        }
    }

    @Override
    public boolean collides(SolidTrait other){
        if(isDead()) return false;

        if(other instanceof DamageTrait){
            //子弹：只与敌方子弹碰撞（友方子弹穿过，避免误伤）
            if(other instanceof Bullet) return team.isEnemy(((Bullet)other).getTeam());
            return other instanceof BaseUnit ? team.isEnemy(((BaseUnit)other).getTeam()) : true;
        }

        return other instanceof BaseUnit && ((BaseUnit)other).isFlying() == isFlying();
    }

    /** 单位间轻微斥力，防止堆叠成一坨。仅对同飞行层级的地面单位生效。 */
    private void avoidOthers(){
        if(isFlying()) return;

        for(int i = 0; i < Units.units.size; i++){
            BaseUnit other = Units.units.get(i);
            if(other == this || other.isDead() || other.isFlying()) continue;

            float dx = x - other.x, dy = y - other.y;
            float dist = (float)Math.sqrt(dx * dx + dy * dy);
            float minDist = (getSize() + other.getSize()) * 0.6f;
            if(dist > 0.001f && dist < minDist){
                float scl = (1f - dist / minDist) * 0.3f;
                velocity.x += dx / dist * scl;
                velocity.y += dy / dist * scl;
            }
        }
    }

    @Override
    public void collision(SolidTrait other, float x, float y){
        if(other instanceof DamageTrait){
            damage(((DamageTrait)other).getShieldDamage());
        }
    }

    @Override
    public void hitbox(Rectangle rect) {
        rect.setSize(type.hitsize).setCenter(x, y);
    }

    @Override
    public void hitboxTile(Rectangle rect) {
        rect.setSize(type.hitsizeTile).setCenter(x, y);
    }

    public void rotate(float angle){
        rotation = MathUtils.lerpAngleDeg(rotation, angle, MathUtils.clamp(type.rotatespeed * Time.delta(), 0f, 1f));
    }

    public Weapon getWeapon(){
        return type.weapon;
    }

    /** @return whether this unit is flying. Corresponds to the unit type's flying flag. */
    public boolean isFlying(){
        return type.flying;
    }

    /** 绘制自身（原版来自 DrawTrait，待该接口移植后可加回 @Override）。 */
    public void draw(){
        //具体绘制由子类实现
    }

    @Override
    public float mass(){
        return type.mass;
    }

    @Override
    public float maxVelocity(){
        return type.maxVelocity;
    }

    @Override
    public float drag(){
        return type.drag;
    }

    @Override
    public void added(){
        state.set(getStartState());

        if(!loaded){
            health(maxHealth());
        }
    }

    @Override
    public void removed(){
        Units.remove(this);
    }
}
