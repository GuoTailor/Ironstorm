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
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
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
    /**
     * RTS 命令 AI（对应 Mindustry v7/v8 的 {@code Unit.controller == CommandAI}）。
     * <p>为 null 时单位走旧的状态机 AI；第一次被下达命令后创建，此后由它接管
     * 移动决策（旧状态机不再运行，对应原版“单位被指挥后一直保持 CommandAI”）。
     */
    public com.phoenix.game.ai.types.CommandAI commandAI;

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

        //死亡特效：小爆炸 + 抛洒的碎片（对应原版 Fx.unitDeath / Fx.unitDebris）
        Effects.effect(Fx.unitDeath, this);
        Effects.effect(Fx.unitDebris, Pal.rubble, x, y, rotation);
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

    /** @return RTS 命令 AI（懒创建；对应原版 {@code unit.command()}，只是不抛异常而是按需创建）。 */
    public com.phoenix.game.ai.types.CommandAI command(){
        if(commandAI == null){
            commandAI = new com.phoenix.game.ai.types.CommandAI(this);
        }
        return commandAI;
    }

    /** @return 该单位能否被 RTS 命令系统选中/指挥（对应原版 {@code isCommandable()}；玩家本体除外）。 */
    public boolean isCommandable(){
        return !isPlayer && !isDead();
    }

    /** 设置攻击目标（RTS 命令 AI 在 ai.types 包访问不到 protected target，走这个公开入口）。 */
    public void setTarget(TargetTrait target){
        this.target = target;
    }

    /** @return 当前攻击目标。 */
    public TargetTrait target(){
        return target;
    }

    public void setState(UnitState state){
        this.state.set(state);
    }

    public UnitState getStartState(){
        return null;
    }

    public boolean retarget(){
        return timer.get(timerTarget, 20);
    }

    /** @return 武器射程（无子弹时退回攻击距离；对应原版 GroundUnit.range 提升为公共方法供命令 AI 使用）。 */
    public float range(){
        Weapon weapon = getWeapon();
        return weapon == null || weapon.bullet == null ? type.attackLength : Math.max(weapon.bullet.range(), 20f);
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
            if(commandAI != null){
                //RTS 命令 AI 接管（对应原版 controller.updateUnit()）：移动决策由它做，
                //目标搜索/瞄准/开火在 CommandAI 内部走 setTarget + behavior()
                commandAI.updateUnit();
            }else{
                updateTargeting();
                state.update();
            }

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

        //建造队列（对应原版 BuilderTrait.updateBuilding，由单位每帧驱动）
        updateBuilding();
    }

    // ---- 建造（对应原版 BuilderTrait） ----

    /** 可建造的最远距离（世界单位）。 */
    public float placeDistance = 220f;
    /** 建造/拆除请求队列，队首是当前正在做的。 */
    public final com.badlogic.gdx.utils.Array<com.phoenix.game.world.BuildRequest> buildQueue = new com.badlogic.gdx.utils.Array<>();

    /** @return 建造功率（对应原版 {@code BuilderTrait.getBuildPower}，来自机甲的 buildPower）。 */
    public float getBuildPower(com.phoenix.game.world.Tile tile){
        return type == null ? 1f : type.buildPower;
    }

    /** @return 队首请求；队列为空返回 null。 */
    public com.phoenix.game.world.BuildRequest buildRequest(){
        return buildQueue.size == 0 ? null : buildQueue.first();
    }

    public boolean isBuilding(){
        return buildQueue.size > 0;
    }

    public void clearBuilding(){
        buildQueue.clear();
    }

    /**
     * 追加一条请求；同一格已有请求则替换（对应原版 {@code addBuildRequest}）。
     * @param tail true = 加到队尾（普通点击），false = 插到队首（拖动连放时优先做当前这一格）
     */
    public void addBuildRequest(com.phoenix.game.world.BuildRequest place, boolean tail){
        for(int i = 0; i < buildQueue.size; i++){
            com.phoenix.game.world.BuildRequest req = buildQueue.get(i);
            if(req.x == place.x && req.y == place.y){
                buildQueue.removeIndex(i);
                break;
            }
        }

        //这一格已经在建/在拆：继承已有进度，避免"重下一遍就白建"
        com.phoenix.game.world.Tile tile = place.tile();
        if(tile != null && tile.entity instanceof com.phoenix.game.world.blocks.BuildBlock.BuildEntity){
            place.progress = ((com.phoenix.game.world.blocks.BuildBlock.BuildEntity)tile.entity).progress;
            place.initialized = true;
        }

        if(tail){
            buildQueue.add(place);
        }else{
            buildQueue.insert(0, place);
        }
    }

    public void addBuildRequest(com.phoenix.game.world.BuildRequest place){
        addBuildRequest(place, true);
    }

    /**
     * 每帧推进建造队列（对应原版 {@code BuilderTrait.updateBuilding}）。
     * <p>流程：清掉已完成的请求 → 队首太远/卡住就轮转到下一个 → 目标格还没铺占位方块就铺上
     * → 往 {@code BuildEntity} 上推进度（材料从最近核心扣）→ 满进度就换成真方块。
     * <p>**双端一致**：客户端不本地建造（只在本地入队会和服务端分叉），
     * 联机时客户端的放置请求直接发服务端，由服务端上这个单位的队列驱动。
     */
    public void updateBuilding(){
        if(Vars.world == null || buildQueue.size == 0) return;

        //1) 已完成的请求出队（目标格已经是目标方块 / 已经空了）
        for(int i = buildQueue.size - 1; i >= 0; i--){
            com.phoenix.game.world.BuildRequest req = buildQueue.get(i);
            Tile tile = req.tile();
            if(tile == null
                || (req.breaking && tile.block() == Blocks.air)
                || (!req.breaking && tile.block() == req.block && (tile.rotation() == req.rotation || !req.block.rotate))){
                buildQueue.removeIndex(i);
            }
        }

        if(buildQueue.size == 0) return;

        //没有核心就不能建造（与原版一致）。材料取自队伍共享库存 ——
        //本工程把"核心 + 容器"统一成一个队伍库存（见 Teams.items），所以这里不用核心实体自己的 items
        if(getClosestCore() == null || Vars.state == null || Vars.state.teams == null) return;
        com.phoenix.game.world.modules.ItemModule inventory =
            com.phoenix.game.world.Build.items(getTeam());

        //2) 队首做不了（太远 / 材料不够且已卡住）就轮转到下一个，最多轮一圈
        int total = 0;
        while(buildQueue.size > 1 && total < buildQueue.size){
            com.phoenix.game.world.BuildRequest req = buildQueue.first();
            Tile tile = req.tile();
            boolean far = tile == null || Mathf.dst(x, y, tile.worldx(), tile.worldy()) > placeDistance;
            if(!far && !shouldSkip(req, inventory)) break;

            buildQueue.removeIndex(0);
            buildQueue.add(req);
            total++;
        }

        com.phoenix.game.world.BuildRequest current = buildQueue.first();
        Tile tile = current.tile();
        if(tile == null){
            buildQueue.removeIndex(0);
            return;
        }

        //3) 目标格还没铺占位方块：铺一个（放置与拆除都走这条路）
        if(!(tile.block() instanceof com.phoenix.game.world.blocks.BuildBlock)){
            if(!current.initialized && canStart(current, tile)){
                com.phoenix.game.world.Build.beginPlace(tile, current, getTeam());
            }else{
                buildQueue.removeIndex(0);
                return;
            }
        }else if(tile.getTeam() != getTeam()){
            buildQueue.removeIndex(0);
            return;
        }

        if(!(tile.entity instanceof com.phoenix.game.world.blocks.BuildBlock.BuildEntity)){
            return;
        }

        com.phoenix.game.world.blocks.BuildBlock.BuildEntity entity =
            (com.phoenix.game.world.blocks.BuildBlock.BuildEntity)tile.entity;

        current.initialized = true;

        //4) 面向正在施工的格子（原版用 slerp 转向，纯表现）
        if(Mathf.dst(x, y, tile.worldx(), tile.worldy()) <= placeDistance){
            rotation = Mathf.slerpDelta(rotation, Angles.angle(x, y, tile.worldx(), tile.worldy()), 0.4f);
        }

        //5) 推进度（材料从核心逐份扣）
        float amount = entity.progressStep(getBuildPower(tile));
        if(current.breaking){
            entity.deconstruct(inventory, amount);
        }else if(entity.construct(inventory, amount)){
            com.phoenix.game.world.blocks.BuildBlock.constructed(tile, entity.cblock, getTeam(), tile.rotation(),
                current.hasConfig ? current.config : -1);
        }

        current.stuck = Math.abs(current.progress - entity.progress) < 0.0001f;
        current.progress = entity.progress;
    }

    /** 目标格能不能开工：放置要位置合法 + 材料够；拆除只要可拆。 */
    private boolean canStart(com.phoenix.game.world.BuildRequest req, Tile tile){
        if(req.breaking){
            return com.phoenix.game.world.Build.canDeconstruct(tile, getTeam());
        }
        return com.phoenix.game.world.Build.validPlace(tile, req.block)
            && com.phoenix.game.world.Build.canAfford(getTeam(), req.block);
    }

    /** 已经开工但卡住、且材料不够：跳过它先做别的（对应原版 {@code shouldSkip}）。 */
    private boolean shouldSkip(com.phoenix.game.world.BuildRequest req, com.phoenix.game.world.modules.ItemModule inventory){
        if(req.breaking || !req.initialized || inventory == null) return false;
        if(!req.stuck) return false;

        for(com.phoenix.game.type.ItemStack stack : req.block.requirements){
            if(!inventory.has(stack.item, stack.amount)) return true;
        }
        return false;
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
