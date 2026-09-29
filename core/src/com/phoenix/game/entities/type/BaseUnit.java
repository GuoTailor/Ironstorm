package com.phoenix.game.entities.type;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.phoenix.game.Vars;
import com.phoenix.game.ai.Pathfinder.PathTarget;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Interval;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Damage;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.effect.ScorchDecal;
import com.phoenix.game.entities.traits.DamageTrait;
import com.phoenix.game.entities.traits.HealthTrait;
import com.phoenix.game.entities.traits.MinerTrait;
import com.phoenix.game.entities.traits.SolidTrait;
import com.phoenix.game.entities.traits.TargetTrait;
import com.phoenix.game.entities.units.StateMachine;
import com.phoenix.game.entities.units.Statuses;
import com.phoenix.game.entities.units.UnitDrops;
import com.phoenix.game.entities.units.UnitState;
import com.phoenix.game.game.EventType;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.StatusEffect;
import com.phoenix.game.type.UnitType;
import com.phoenix.game.type.Weapon;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockFlag;

/**
 * create by GYH on 2024/12/25
 * 最小实现：单位基类。参照 Mindustry mindustry.entities.type.BaseUnit 移植。
 * <p>实现 {@link MinerTrait}（原版是 {@code Player}/{@code MinerDrone} 各自实现）：本项目玩家单位就是
 * 一个 {@code BaseUnit}（{@code Player} 只持有引用）→ 采矿接口必须落在基类上，玩家才能采矿。
 * 只有 {@code isPlayer} 的单位会被驱动 {@code updateMining()/drawMining()}（见 {@link #update()}/{@link #drawOver()}），
 * 其余单位不采矿（无人机自己覆写 {@code drawOver}/{@code update} 走各自的状态机）。
 */
public class BaseUnit extends SolidEntity implements TargetTrait, HealthTrait, MinerTrait {
    protected static int timerIndex = 0;

    protected static final int timerTarget = timerIndex++;
    protected static final int timerTarget2 = timerIndex++;
    protected static final int timerShootLeft = timerIndex++;
    protected static final int timerShootRight = timerIndex++;

    protected boolean loaded;
    protected UnitType type;
    public Weapon weapon;
    public float rotation;
    /** 当前正在开采的瓦片（对应原版 {@code Unit.mining}/{@code Player.mining}）。 */
    protected Tile mineTile;
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

    // ---- 载物背包（对应原版 Unit.item：单位只能携带一种物品；容量 = 单位类型的 itemCapacity）----
    //刻意偏离：原版是 Unit 上的**可变** ItemStack（item.item / item.amount）；本项目 type/ItemStack 是
    //final 不可变值对象（还被方块配方常量共享，见 type/Zone.java 里"创建新对象代替改 amount"的先例）
    //→ 这里拆成两个可变字段，API 仍按原版命名。玩家背包在 Player 上单独一份（见 Player 的"背包"注释）。
    /** 背包内的物品类型（null 表示空）。对应原版 {@code Unit.item.item}。 */
    public com.phoenix.game.type.Item item;
    /** 背包内的物品数量。对应原版 {@code Unit.item.amount}。 */
    public int itemAmount;
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

        //死亡掉落（对应原版 BaseUnit.onUnitDeath 开头的 UnitDrops.dropItems(unit)）：
        //只有 wave 队单位在敌方核心附近、且 rules.unitDrops 打开时，才按概率把材料掉进敌方核心
        UnitDrops.dropItems(this);

        //玩家击杀敌方单位时获得经验
        if(Vars.player != null && !Vars.player.isDead() && this != Vars.player.unit()
                && team != null && Vars.player.team != null && team.isEnemy(Vars.player.team)){
            Vars.player.addXp(1);
        }

        Damage.dynamicExplosion(x, y, 0f, 2f, 0f, type.hitsize, Pal.darkFlame);
        //爆炸焦痕贴花（对应原版 Unit.onDeath 里紧跟在 dynamicExplosion 之后的 ScorchDecal.create(x, y)）
        ScorchDecal.create(x, y);

        //死亡特效：小爆炸 + 抛洒的碎片（对应原版 Fx.unitDeath / Fx.unitDebris）
        Effects.effect(Fx.unitDeath, this);
        Effects.effect(Fx.unitDebris, Pal.rubble, x, y, rotation);
        Effects.shake(2f, 2f, this);

        status.clear();

        //单位销毁事件（对应原版 Unit.onDeath 里在 remove() 前派发的 UnitDestroyEvent）：
        //结算框的"消灭敌人数"由监听它的 Control 按阵营累加
        Events.fire(new EventType.UnitDestroyEvent(this));

        remove();
    }

    public void applyEffect(StatusEffect effect, float duration){
        if(isDead() || effect == null) return;
        status.handleApply(this, effect, duration);
    }

    public boolean hasEffect(StatusEffect effect){
        return status.hasEffect(effect);
    }

    /**
     * 是否为 BOSS（对应原版 {@code BaseUnit.isBoss()}，实现就是"带 {@code StatusEffects.boss} 效果"）。
     * <p>⚠ 注意与 {@code getType().boss} 区分：原版判定用的是**状态效果**，
     * 效果由 {@code SpawnGroup.effect = StatusEffects.boss} 在生成时施加（见 {@code content/Zones}），
     * {@code UnitType.boss} 只用于波次提示文案。BOSS 血条走这个判定。
     */
    public boolean isBoss(){
        return hasEffect(com.phoenix.game.content.StatusEffects.boss);
    }

    public boolean isImmune(StatusEffect effect){
        //原版 BaseUnit.java:214：return type.immunities.contains(effect);
        //⚠ 本项目 type 由 init() 赋值、对象池取出到 init 之间可能为 null，故判空
        return type != null && type.immunities.contains(effect);
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

    /**
     * 当前目标是否带某个 {@code BlockFlag}（对应原版 {@code BaseUnit.targetHasFlag}）。
     * <p>目标既可能是建筑实体（{@link TileEntity}）也可能是瓦片（{@link Tile}），两者都要判。
     */
    public boolean targetHasFlag(BlockFlag flag){
        return (target instanceof TileEntity && ((TileEntity)target).tile.block().flags.contains(flag))
            || (target instanceof Tile && ((Tile)target).block().flags.contains(flag));
    }

    // ---- 载物背包 API（对应原版 Unit.java:333-357；原版 BaseUnit 覆写 getItemCapacity 返回 type.itemCapacity）----

    /** @return 背包容量（对应原版 {@code BaseUnit.getItemCapacity() → type.itemCapacity}，默认 30）。 */
    public int getItemCapacity(){
        return type == null ? 0 : type.itemCapacity;
    }

    /** 能否再收下该物品（对应原版 {@code Unit.acceptsItem}）：背包为空，或同种物品且未超容量。 */
    public boolean acceptsItem(com.phoenix.game.type.Item item){
        return itemAmount <= 0 || (this.item == item && itemAmount <= getItemCapacity());
    }

    /** 收下 1 个物品（对应原版 {@code Unit.addItem(Item)}）。 */
    public void addItem(com.phoenix.game.type.Item item){
        addItem(item, 1);
    }

    /** 收下物品（对应原版 {@code Unit.addItem(Item,int)}）：换物品类型时数量重置，最后夹到容量。 */
    public void addItem(com.phoenix.game.type.Item item, int amount){
        this.itemAmount = this.item == item ? this.itemAmount + amount : amount;
        this.item = item;
        this.itemAmount = Mathf.clamp(this.itemAmount, 0, getItemCapacity());
    }

    /** 清空背包（对应原版 {@code Unit.clearItem}）。 */
    public void clearItem(){
        itemAmount = 0;
    }

    /**
     * @return 还能再收下多少个该物品（对应原版 {@code Unit.maxAccepted}）。
     * <p>刻意补的 API：原版在 {@code InputHandler} 里直接写 {@code player.item().amount -= accepted}，
     * 本项目背包不是可变 ItemStack → 用 {@link #removeItem(int)} 承担同样的语义。
     */
    public int maxAccepted(com.phoenix.game.type.Item item){
        return this.item != item && itemAmount > 0 ? 0 : getItemCapacity() - itemAmount;
    }

    /** 从背包取走指定数量（本项目自定，替代原版 {@code item().amount -= amount} 的写法）。 */
    public void removeItem(int amount){
        itemAmount = Mathf.clamp(itemAmount - amount, 0, getItemCapacity());
    }

    // ---- 采矿（MinerTrait；对应原版 Player/MinerDrone 各自实现的那些方法）----

    @Override
    public Tile getMineTile(){
        return mineTile;
    }

    @Override
    public void setMineTile(Tile tile){
        this.mineTile = tile;
    }

    /**
     * @return 采矿速度（对应原版 {@code Player.getMinePower() → mech.mineSpeed}）。
     * <p>刻意偏离：本项目没有机甲系统 → 取单位类型的 {@code minePower}（dagger 默认 0.7）。
     */
    @Override
    public float getMinePower(){
        return type == null ? 1f : type.minePower;
    }

    /**
     * @return 能否开采该物品（对应原版 {@code MinerDrone.canMine → type.toMine.contains(item)}）。
     * <p>刻意偏离：原版**玩家**用 {@code item.hardness <= mech.drillPower}（硬度阈值），本项目没有机甲系统
     * → 玩家与无人机统一用 {@code UnitType.toMine} 白名单（dagger = {铅,铜}，与原版 starter
     * {@code Mechs.dart} 的 {@code drillPower=1} 可采集合一致）。
     */
    @Override
    public boolean canMine(Item item){
        return type != null && item != null && type.toMine.contains(item);
    }

    /**
     * @return 是否把采到的矿**立刻送进核心**（对应原版 {@code Player.offloadImmediately() → true}）。
     * <p>玩家为 true；普通单位/无人机沿用接口默认 false（先攒在背包里、再一次性卸货）。
     */
    @Override
    public boolean offloadImmediately(){
        return isPlayer;
    }

    /**
     * 在单位背面绘制"背包物品"（对应原版 {@code Unit.drawBackItems(itemtime, number)}）。
     * <p>玩家背包与单位载物背包共用这一套画法（只是数据来源与位置不同）：玩家走
     * {@code Player.drawBackItems()}（{@code number = isLocal}），单位走 {@code drawStats()} 里的调用
     * （{@code number = false}，原版普通单位不显示数量）。
     * <p><b>降级（与原版的差异）</b>：
     * <ul>
     *   <li>{@code Draw.mixcol(color, amount)}（贴图与强调色按权重混合）本项目没有 → 用
     *       {@code Draw.color(Pal.accent)} 直接染色（贴图本色被完全覆盖）。</li>
     *   <li>原版 {@code Fonts.outline.draw(text, x, y, color, scale, ints, align)} 是 arc 签名 →
     *       改用 libgdx 的 {@code BitmapFont}（设 scale/color 后 {@code draw(batch, text, x, y, 0, Align.center, false)}），
     *       用完还原。</li>
     *   <li>原版末尾 {@code Draw.reset()} 本项目没有 → 用 {@code Draw.color()} 复位。</li>
     * </ul>
     */
    public static void drawBackItems(com.phoenix.game.type.Item item, int amount, float itemtime,
                                     boolean number, float x, float y, float rotation){
        if(itemtime <= 0.01f || item == null) return;

        //原版常量 backTrns = 5f：物品画在单位背面（rotation + 180°）偏移 5 世界单位处
        float backTrns = 5f;
        float bx = x + Angles.trnsx(rotation + 180f, backTrns);
        float by = y + Angles.trnsy(rotation + 180f, backTrns);
        float size = (Vars.itemSize + Mathf.absin(Time.time, 5f, 1f)) * itemtime;

        Draw.color(Pal.accent);
        Draw.rect(item.icon(com.phoenix.game.ui.Cicon.medium), bx, by, size, size, rotation);
        Draw.color();

        com.phoenix.game.core.Lines.stroke(1f, Pal.accent);
        com.phoenix.game.core.Lines.circle(bx, by, (3f + Mathf.absin(Time.time, 5f, 1f)) * itemtime);

        if(number){
            com.badlogic.gdx.graphics.g2d.BitmapFont font = com.phoenix.game.core.Fonts.outline;
            if(font != null){
                boolean ints = font.usesIntegerPositions();
                font.setUseIntegerPositions(false);
                font.getData().setScale(0.25f * itemtime / com.phoenix.game.core.Scl.scl(1f));
                font.setColor(Pal.accent);
                font.draw(Core.batch, amount + "", bx, by - 3f, 0f, com.badlogic.gdx.utils.Align.center, false);
                font.getData().setScale(1f);
                font.setColor(Color.WHITE);
                font.setUseIntegerPositions(ints);
            }
        }

        Draw.color();
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

        //液体上减速（⚠ 仅地面单位：原版 updateVelocityStatus 里 floor.speedMultiplier/dragMultiplier
        //只作用于地面分支，飞行单位完全无视地板 —— 此前飞行单位过水面/焦油也被拖慢，手感发滞）
        if(!isFlying()){
            Floor floor = getFloorOn();
            if(floor != null && floor != Blocks.air && floor.isLiquid){
                velocity.scl(MathUtils.lerp(1f, floor.speedMultiplier, 0.1f));
            }
        }

        //建造队列（对应原版 BuilderTrait.updateBuilding，由单位每帧驱动）
        updateBuilding();

        //玩家单位采矿（对应原版 Player.updateMechanics：不在建造时 updateMining）
        if(isPlayer && buildRequest() == null){
            updateMining();
        }

        //F12c：每帧额外逻辑钩子（对应原版 Mech.updateAlt(Player)；如 javelin-ship 的随机雷击）
        type.updateAlt(this);

        //背包数量每帧夹到容量（对应原版 Unit.updateVelocityStatus 里的
        //item.amount = Mathf.clamp(item.amount, 0, getItemCapacity())）
        itemAmount = Mathf.clamp(itemAmount, 0, getItemCapacity());
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
     * 绘制建造队列里"还没开工 / 还没拆完"的请求的幽灵预览
     * （对应原版 {@code Player.drawBuildRequests()}，由原版 {@code Renderer} 的
     * {@code playerGroup.draw(p -> p.isLocal, Player::drawBuildRequests)} 每帧调用；
     * 本项目 {@code Player} 不是单位基类 → 由 {@code Renderer} 直接对本地玩家单位调一次）。
     * <p>跳过两类请求：
     * <ul>
     *   <li>{@code progress > 0.01f} —— 已经在建了，占位方块本身可见；</li>
     *   <li>队首 + 已 {@code initialized} + 在可建距离内（或编辑器）—— 下一帧就会被推进，不需要幽灵。</li>
     * </ul>
     * <p><b>降级</b>：原版走 {@code Block.drawRequestRegion}（Autotiler 合成"连通外观"贴图，并用
     * {@code req.animScale} 做缩放动画）；本项目没有该调用链，改用与光标预览同一套 ghost 画法
     * （{@code InputHandler.drawRequest}：逐层 icon 半透明 + 合法绿/非法红），外观与光标预览一致。
     */
    public void drawBuildRequests(){
        if(Vars.world == null || buildQueue.size == 0) return;
        com.phoenix.game.input.InputHandler input = Vars.control == null ? null : Vars.control.input;
        if(input == null) return;

        com.phoenix.game.world.BuildRequest first = buildRequest();
        for(int i = 0; i < buildQueue.size; i++){
            com.phoenix.game.world.BuildRequest req = buildQueue.get(i);
            if(req.progress > 0.01f) continue;

            if(req == first && req.initialized){
                Tile tile = req.tile();
                float reqX = tile == null ? req.x * Vars.tilesize : tile.worldx();
                float reqY = tile == null ? req.y * Vars.tilesize : tile.worldy();
                if(Mathf.dst(x, y, reqX, reqY) <= placeDistance
                    || (Vars.state != null && Vars.state.isEditor())) continue;
            }

            req.animScale = 1f;
            input.drawRequest(req);
        }
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
        return com.phoenix.game.world.Build.validPlace(tile, req.block, req.rotation, getTeam())
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

    /**
     * 单位间斥力，防止堆叠成一坨（对应原版 {@code Unit.avoidOthers}）。
     * <p>照原版的语义：**只避让同飞行层级**的单位（原版 {@code avoid()} 里 {@code en.isFlying() != isFlying()} 跳过）；
     * 玩家单位的两条例外也照原版（敌方玩家不避让、玩家自己不避让飞行单位）。
     * <p><b>F15 修的三处</b>：
     * <ol>
     *   <li><b>精确重合（dist == 0）时永久锁死</b>：原逻辑有 {@code dist > 0.001f} 守卫，两单位坐标完全相同时
     *       斥力永不触发；又因为 AI 每帧 {@code moveTo} 给的是**完全确定性的速度**，同点出生的单位会一直
     *       保持重合（实测：同点刷 6 个单位，10 秒后坐标仍两两完全相同）。现在重合时按两单位 id 取一个
     *       确定性方向分开。</li>
     *   <li><b>飞行单位原先直接 {@code return}</b>（原版对同层飞行单位同样避让）→ 飞行单位会完全重叠。</li>
     *   <li><b>推力系数 0.3 太弱</b>：{@code moveTo} 每帧把速度设成满速（0.6~0.85），0.3 的斥力会被完全盖过，
     *       表现为"挤成一团"。提到与移动速度同量级（0.6）。</li>
     * </ol>
     * <p><b>与原版的差异</b>：原版推力按加速度施加（{@code velocity.add(moveVector / mass * Time.delta())}，
     * 量级约 0.003/帧）；本项目 AI 每帧 {@code moveTo} 直接 set 速度，按加速度施加会被下一帧覆盖 →
     * 保留"直接加到速度"的写法，否则斥力形同虚设。
     */
    private void avoidOthers(){
        float size = getSize();
        float mx = 0f, my = 0f;

        for(int i = 0; i < Units.units.size; i++){
            BaseUnit other = Units.units.get(i);
            if(other == this || other.isDead()) continue;
            //只避让同飞行层级（对应原版 en.isFlying() != isFlying() 跳过）
            if(other.isFlying() != isFlying()) continue;
            //玩家例外（照原版 avoid() 的判据）
            if(other.isPlayer && other.getTeam() != getTeam()) continue;
            if(isPlayer && other.isFlying()) continue;

            float dx = x - other.x, dy = y - other.y;
            float dist = (float)Math.sqrt(dx * dx + dy * dy);
            float minDist = (size + other.getSize()) * 0.6f;
            if(dist >= minDist) continue;

            if(dist < 0.001f){
                //精确重合：dx/dist 是 0/0 无方向 → 取一个确定性方向，两个单位用**同一角度但方向相反**
                //（保证互相远离）。
                //用对象身份哈希而不是 getID()：identityHashCode 天然满足 ha != hb，方向必然相反；
                //而相邻 id 经 (idA*31+idB*17)%360 算出的角度只差十几度，两个单位会朝同侧飘、分不开。
                int ha = System.identityHashCode(this), hb = System.identityHashCode(other);
                float ang = (Math.abs(ha ^ hb) % 360) * MathUtils.degreesToRadians;
                float s = ha < hb ? 1f : -1f;
                mx += MathUtils.cos(ang) * s * 0.6f;
                my += MathUtils.sin(ang) * s * 0.6f;
                continue;
            }

            float scl = (1f - dist / minDist) * 0.6f;
            mx += dx / dist * scl;
            my += dy / dist * scl;
        }

        velocity.x += mx;
        velocity.y += my;
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

    /**
     * 飞行单位的投影（对应原版 {@code Unit.drawShadow}）：把自身图标按偏移画成剪影。
     * <p>颜色由调用方（{@code Renderer.drawFlyerShadows}）先 {@code Draw.color} 设好。
     * <p>本项目单位 {@code rotation} 与原版同为「度、0 = 右、逆时针」——同一口径见 {@link #drawStats()} 里的
     * {@code rotation - 90f} → 直接照抄原版的 {@code rotation - 90}。
     * <p><b>偏离原版</b>：原版未判空（arc 的 {@code Core.atlas.find} 缺图返回 error 贴图）；本项目
     * {@code getIconRegion()} 在图标缺失时可能返回 null → 这里加一道判空，避免渲染循环 NPE。
     */
    public void drawShadow(float offsetX, float offsetY){
        TextureRegion icon = getIconRegion();
        if(icon == null) return;
        Draw.rect(icon, x + offsetX, y + offsetY, rotation - 90f);
    }

    /**
     * 绘制单位顶部的"能量核心"覆盖层（对应原版 {@code Unit.drawStats}）：
     * 用 {@code power-cell} 贴图按血量在**黑 → 队伍色**之间插值（受伤越重越偏黑，并带轻微脉动）。
     * <p>调用点：{@code Renderer.drawUnits()} 在 {@code draw()} 之后（对应原版 {@code Unit.drawAll()} 的
     * {@code draw(); drawStats();} 顺序）。
     * <p><b>降级说明（与原版的差异）</b>：
     * <ul>
     *   <li>原版末尾的 {@code drawBackItems(item.amount > 0 ? 1f : 0f, false)}：<b>已补</b> —— 但本项目
     *       "玩家背包"在 {@code Player} 上（Player 不是 Unit），所以本地玩家单位改画玩家背包
     *       （{@code number = isLocal}），其余单位画自己的载物背包（{@code number = false}）。</li>
     *   <li>原版末尾的 {@code drawLight()}（{@code renderer.lights.add(x, y, 50f, Pal.powerLight, 0.6f)}）：
     *       <b>已接线（S5b 批次）</b>，见 {@link #drawLight()}（原版 Unit.java:391 调用 / :394-396 方法）。</li>
     *   <li>原版 {@code getPowerCellRegion()} 用 {@code Core.atlas.find}（缺图返回 error 贴图）；
     *       本项目用 {@code findRegion}（缺图返回 null）→ 判空跳过能量核心（但**不能**直接 return，
     *       否则背包物品也画不出来）。</li>
     * </ul>
     */
    public void drawStats(){
        TextureRegion cell = getPowerCellRegion();
        if(cell != null){
            Draw.color(Color.BLACK, team.color,
                healthf() + Mathf.absin(Time.time(), Math.max(healthf() * 5f, 1f), 1f - healthf()));
            Draw.rect(cell, x, y, rotation - 90);
            Draw.color();
        }

        //原版末尾的 drawBackItems(item.amount > 0 ? 1f : 0f, false)：
        //本地玩家单位画玩家背包（Player 上的那份，带数量），其余单位画自己的载物背包
        if(Vars.player != null && this == Vars.player.unit()){
            Vars.player.drawBackItems();
        }else{
            drawBackItems(item, itemAmount, itemAmount > 0 ? 1f : 0f, false, x, y, rotation);
        }

        //原版 Unit.drawStats 末尾的 drawLight()（见原版 Unit.java:391）
        drawLight();
    }

    /**
     * 单位自发光（对应原版 {@code Unit.drawLight}，见原版 Unit.java:394-396）：
     * {@code renderer.lights.add(x, y, 50f, Pal.powerLight, 0.6f)}。
     * <p>已接线（S5b 批次）：本项目已移植灯光管线（{@code graphics/LightRenderer}）。
     * <p>⚠ 调用点：原版由 {@code Unit.drawStats()} 末尾调用；本项目 {@code Unit} 里那段 {@code draw()}/
     * {@code drawStats()} 是整段注释掉的历史留档（{@code entities/type/Unit.java:325-335}），
     * 真正生效的绘制入口是 {@link #drawStats()}（由 {@code Renderer.drawUnits()} 每帧调用）
     * → 本方法挂在 {@code drawStats()} 末尾。
     * <p>⚠ 坐标：单位自身的 {@code x/y} 就是中心（原版亦然），不涉及瓦片角点换算。
     */
    public void drawLight(){
        if(Vars.renderer == null) return;
        Vars.renderer.lights.add(x, y, 50f, Pal.powerLight, 0.6f);
    }

    /** @return 能量核心贴图（对应原版 {@code Unit.getPowerCellRegion}）；图集缺图返回 null。 */
    public TextureRegion getPowerCellRegion(){
        return Core.atlas == null ? null : Core.atlas.findRegion("power-cell");
    }

    /**
     * 绘制"压在单位之上"的叠加内容（对应原版 {@code Unit.drawOver()}，参照 mindustry.entities.type.Unit 移植）：
     * 玩家单位画采矿激光（{@code drawMining()}），建造无人机覆写它画建造预览（{@code BuilderTrait.drawBuilding()}）。
     * <p>建造预览对玩家单位由 {@code Renderer} 单独调 {@code drawBuildRequests()}（见其说明），不走这里。
     */
    public void drawOver(){
        if(isPlayer){
            drawMining();
        }
    }

    /**
     * @return 绘制/裁剪尺寸（对应原版 {@code DrawTrait.drawSize()} 的默认值 20f）。
     * <p>本项目 {@code BaseUnit} 尚未实现 {@code DrawTrait}，所以这里补一份默认实现供无人机覆写。
     */
    public float drawSize(){
        return 20f;
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

    /**
     * 对应原版 {@code Unit.updateVelocityStatus} 的收尾三步（飞行分支）：先 {@code velocity.limit(maxVelocity())}
     * 钳最大速度，再按速度移动，最后统一乘阻尼。此前用 VelocityTrait 默认实现（先阻尼再移动、且从不钳最大速度），
     * 稳态速度比原版低约一档，加速/减速节奏也不同。
     * <p>飞行单位（{@link FlyingUnit}）覆写走无碰撞直移；地面单位走 {@code SolidTrait.move()}（带建筑碰撞）。
     */
    @Override
    public void updateVelocity(){
        velocity.limit(maxVelocity());

        if(this instanceof SolidTrait){
            ((SolidTrait)this).move(velocity.x * Time.delta(), velocity.y * Time.delta());
        }else{
            moveBy(velocity.x * Time.delta(), velocity.y * Time.delta());
        }

        //原版行 330：阻尼在移动**之后**统一乘（地面单位还有 floor.dragMultiplier，本项目未移植地板阻尼系数）
        velocity.scl(Mathf.clamp(1f - drag() * Time.delta()));
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

    /**
     * 网络同步用的状态序列化（对应原版 {@code Unit.write} —— 原版由 {@code SyncTrait} 提供，
     * 写入位置/朝向/血量等；本项目的单位同步尚未移植，没有配套的 {@code SyncTrait} 接线与
     * {@code Interpolator} 读写约定）。
     * <p>TODO 退化为空实现，仅供子类（{@code BuilderDrone}/{@code MinerDrone}/{@code RepairDrone}）
     * 的 {@code super.write(data)} 链调用；等移植单位同步时按原版补齐字段。
     * <p><b>现状（已 grep 核实）</b>：全工程没有任何调用方调用单位的 {@code write/read}——
     * 联机单位状态走 {@code NetClient} 的 {@code Snap} 快照（x/y/rotation 数组 + {@code Interpolator}），
     * 存档走 {@code SaveMeta}/{@code SaveIO}，都不经过这两个方法。所以这三个子类的 {@code write/read}
     * 目前是**死代码**，只在彼此的 {@code super} 链里互相调用。
     */
    public void write(java.io.DataOutput data) throws java.io.IOException{
    }

    /** 对应 {@link #write(java.io.DataOutput)} 的反序列化，TODO 同上（退化实现）。 */
    public void read(java.io.DataInput data) throws java.io.IOException{
    }
}
