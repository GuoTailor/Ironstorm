# A1 审计：entities/ai/game（含 core/Logic）

> 审计范围：本项目 com/phoenix/game/entities/（含 bullet/effect/traits/type/units）、ai/、game/ ↔ 原版 mindustry/entities/、mindustry/ai/、mindustry/game/；原版 mindustry/core/Logic.java ↔ 本项目 core/Logic.java。
> 判据：行为级对照；**无注释声明的差异才算问题**。已知豁免：Player 非 Unit、无 Mech 系统、自研网络、@Remote 注释态、arc→libgdx 结构替换。
> 文件级结论：全部 75 对文件一一对应（唯 UnitCommand 从 entities/units 挪到 ai/，CommandAI/UnitGroup/UnitStance/ControlPathfinder/TaskQueue/PathTile 为 v7/v8 参照的自研扩展，不算缺失）。

## 摘要：缺失 15 / 未声明偏离 6 / 断裂 4（另有 20+ 项"偏离已声明"，见文末清单）

最严重的三条：
1. **开波清场与波次表现整链缺失**（WaveSpawner）：原版开波时对每个出生点放 spawnShockwave 特效 + 全额伤害（99999999）清空空投区，敌人延迟分批出生并带 spawn 特效；本项目敌人瞬间凭空出现、出生点不清场——玩家可以在出生点上堵门建塔，波次难度曲线被破坏。
2. **单位物理与环境交互五项缺失**（BaseUnit/Unit 注释保留但未落地）：地面单位卡墙不死、出生工厂被拆不死、出界不死、深水不溺亡（陆军可以渡海）、液体地板不掉血不上状态——Unit.java 头注释声称"能力已落在 BaseUnit"，实际 updateVelocityStatus 的这些分支没有落地，属声明与现实不符。
3. **Lightning 降级未声明完整**：原版是"沿途每段一发 damageLightning 子弹 + 30 格链式跳跃（maxChain 8）+ 绝缘墙（insulated）拦截"；本项目只画一条折线特效 + 末端一次伤害。Bullets 注释只声明了"伤害由 createLighting 结算"，未声明丢链式跳跃与绝缘拦截——surge 墙/激光塔/雷暴单位的目标选择与伤害分布全部失真。

## 明细表（按优先级排序）

### 高优先级

| 原版位置(file:line) | 项 | 状态 | 本项目位置 | 证据 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| ai/WaveSpawner.java:78-83 | 开波时对每个地面出生点 spawnShockwave 特效 + Damage.damage(waveTeam, x, y, dropZoneRadius, 99999999f, true) 清空空投区 | 缺失 | ai/WaveSpawner.java:58-95 | 本项目 spawnEnemies 无此段，也无 Damage 的 complete 重载 | 高 |
| ai/WaveSpawner.java:72,144-150 | 地面单位延迟分批出生（Time.run(min(i*5,120), spawnEffect)，unitSpawn/Fx.spawn 特效） | 缺失 | ai/WaveSpawner.java:84-89 | 本项目 unit.set(...) 立即生效，无延迟无特效 | 高 |
| ai/WaveSpawner.java:93-99,110-114 | attack 模式下从敌核附近刷增援（eachGroundSpawn/eachFlyerSpawn 的 attackMode 分支） | 缺失 | ai/WaveSpawner.java | 出生点只来自 spawn overlay，attack 图无核心增援 | 高 |
| entities/effect/Lightning.java:71-115 | 闪电沿途每段 Bullet.create(Bullets.damageLightning) + 30 格链式索敌（maxChain=8）+ insulated 绝缘墙拦截折断 | 偏离(部分声明) | entities/effect/Lightning.java:25-64 | 只剩一条折线特效 + 末端一次 Damage.damage(1.5 格)；Bullets.java:715 只声明了伤害结算方式，未声明丢链式跳跃/绝缘 | 高 |
| entities/type/Unit.java:288 | 地面单位卡在 solid 方块（非 BuildBlock）→ kill() | 缺失 | entities/type/BaseUnit.java:502-560 | 本项目 update 无此检查；地面单位可永久卡进建筑里活着 | 高 |
| entities/type/Unit.java:244-260 | 深水溺亡：drownTime 按地板 drownTime 累积，满则 damage(health+1) + Trigger.drown | 缺失 | entities/type/Unit.java:24-258（整段注释） | 本项目陆军可自由渡水；Floor.drownTime 只被 Pathfinder 当"不可通行"用。Unit.java 头注释声称能力已落在 BaseUnit，与现实不符 | 高 |
| entities/type/Unit.java:236-242 | 液体地板交互：floor.status（焦油起火/湿身）、floor.damageTaken（酸液掉血）、walkEffect 涟漪 | 缺失 | entities/type/BaseUnit.java:537-544 | 本项目只做了 speedMultiplier 减速（注释只声明了 dragMultiplier 未移植） | 高 |
| world/Block.java:604-647 | Block.onDestroyed：被毁方块按库存结算爆炸（物品爆炸性/可燃性、液体泼洒 Puddle.deposit、缓冲电力） | 偏离(声明不完整) | entities/type/TileEntity.java:281-298 | kill() 注释声明"没有该钩子"（架构层），但普通方块（容器/炼油厂）被毁不爆炸不泼洒的行为缺失只被顺带提及；仅 NuclearReactor 单独补了核爆 | 高 |
| entities/type/Bullet.java:168-170 | collides() 的对空语义：!(other.isFlying() && !type.collidesAir) —— collidesAir=false 只是打不到飞行单位 | 偏离(未声明) | entities/type/Bullet.java:182-184 | 本项目写成 && type.collidesAir，collidesAir=false 变成"打不到任何单位"；collideGroups 方向也从 (bullet,unit) 对调成 (unit,bullet)，BaseUnit.collides 不含对空判定 → BombBulletType(collidesAir=false) 对地面单位失去直击碰撞，只剩 splash | 高 |

### 中优先级

| 原版位置(file:line) | 项 | 状态 | 本项目位置 | 证据 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| entities/Damage.java:214-255 | tileDamage：爆炸对建筑的菱形衰减蔓延伤害（GridBits+IntQueue 传播） | 缺失 | entities/Damage.java | 无对应实现；炸药/爆炸物对建筑群无连锁衰减伤害 | 中 |
| entities/Damage.java:269-274 | calculateDamage：范围伤害按距离衰减（falloff 0.4）+ 命中击退位移 | 偏离(未声明) | entities/Damage.java:26-48 | 本项目 damage()/damageAll() 直接 unit.damage(damage)，中心与边缘同伤、无位移 | 中 |
| entities/Damage.java:33-65 | dynamicExplosion：闪电分支 + fireball 弹 + 分层时序冲击波 + 震屏上限(min(expl/4+3,9)) + Fx.dynamicExplosion + shockwave 阈值 | 偏离(未声明) | entities/Damage.java:92-101 | 本项目为一次性全径伤害 + createIncend + shake(explosiveness+power) 无上限公式；无任何注释声明简化 | 中 |
| entities/type/Unit.java:99-101 | 单位死亡爆炸按携带物品加成：2f + item.explosiveness*amount，>7 且是玩家触发 Trigger.suicideBomb | 缺失 | entities/type/BaseUnit.java:183 | 固定 dynamicExplosion(x,y,0f,2f,...)；携带爆炸物不殉爆 | 中 |
| entities/type/BaseUnit.java:63 | 死亡音效：type.deathSound.at(unit)（onUnitDeath）+ Unit.onDeath 的 Sounds.bang | 缺失 | entities/type/BaseUnit.java:169-199 | 全工程 grep 无 deathSound（UnitTypes 亦无此字段）；单位死亡无声 | 中 |
| entities/type/BaseUnit.java:294-296 | spawner 失效 kill：出生工厂被拆/失效 → 单位死 | 缺失 | entities/type/BaseUnit.java:502-560 | update 无此检查（getSpawner/setSpawner 存在但无人消费它判死） | 中 |
| entities/type/Unit.java:196-198,210-216 | 出界 kill（finalWorldBounds）+ 玩家出界推回（warpDst 180f）+ dropZone 击退（非 wave 队被出生区推开） | 缺失 | entities/type/BaseUnit.java | updateVelocity/update 均无；clampPosition 也无（原版 Unit.java:183） | 中 |
| core/Logic.java:136-142 | runWave 里发射波间隔 waveSpacing * rules.launchWaveMultiplier；WaveEvent 监听器里 zone.updateWave(state.wave)（战役波次进度 bestWave） | 断裂 | core/Logic.java:149-164 | Zones.java:62 设了 launchWaveMultiplier=3f 但无消费者；Zone.updateWave/bestWave 无调用点 | 中 |
| entities/type/Unit.java:134-136 | 护甲减免 calculateDamage = amount * clamp(1 - armorMultiplier/100) | 断裂 | entities/units/Statuses.java:91（getArmorMultiplier 存在） | Statuses 计算了 armorMultiplier 但无任何消费者（armored 等状态无效） | 中 |
| entities/Units.java:84,101,113 | 索敌 derelict 豁免：closestTarget/closestEnemy/findEnemyTile 开头 if(team == Team.derelict) return null；findEnemyTile 带 tilePred | 偏离(未声明) | entities/Units.java:79-150 | 本项目无 derelict 检查；closestEnemyBuilding 替代实现还自加"核心>炮塔>其它"加权（注释自述了权值设计，未声明与原版不同） | 中 |
| entities/effect/Fire.java:75-78 | extinguish 熄灭时 Events.fire(Trigger.fireExtinguish) | 断裂 | entities/effect/Fire.java:166-170 | 只推进 time，事件枚举存在（EventType.java:20）但全工程无人 fire | 中 |
| entities/effect/Fire.java:151-157 | 火焰读脚下水洼可燃性 puddleFlammability = Puddle.getPuddle(tile).getFlammability()/3 | 断裂(注释过时) | entities/effect/Fire.java:36-46 | 类注释声明"未移植 Puddle"——Puddle 现已完整移植（Puddle.java 416 行，含 getFlammability），但 Fire 仍未接 | 中 |
| entities/type/TileEntity.java:287 | block.breakSound.at(tile) 建筑被毁音效 | 缺失 | entities/type/TileEntity.java:281-298 | kill() 无声音调用，全工程无 breakSound | 中 |
| entities/units/Statuses.java:124-148 | 状态效果 writeSave/readSave（进存档） | 缺失 | entities/units/Statuses.java | 读档后单位身上的燃烧/冰冻等状态丢失 | 中 |
| entities/type/BaseUnit.java:181-186 | targetClosest 用 max(weapon.bullet.range, type.range)；targetClosestEnemyFlag/AllyFlag 优先打 producer/turret | 偏离(部分声明) | entities/type/BaseUnit.java:289-297 | updateTargeting 固定用 type.attackLength；FlyingUnit 注释声明了 targetClosestEnemyFlag 未移植，GroundUnit 索敌范围变化未声明 | 中 |

### 低优先级

| 原版位置(file:line) | 项 | 状态 | 本项目位置 | 证据 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| entities/units/Statuses.java:61-73 | getStatusColor（血条/单位按状态染色） | 缺失 | entities/units/Statuses.java | 无对应方法 | 低 |
| entities/units/Statuses.java | 无状态数量上限（本项目自加 maxEffects=8） | 偏离(未声明) | entities/units/Statuses.java:13,62 | 超过 8 个状态时第 9 个静默丢弃 | 低 |
| entities/type/TileEntity.java:97-119 | sleep/noSleep/isSleeping 休眠优化（timeToSleep=60） | 缺失 | entities/type/TileEntity.java | 纯性能优化，无行为差异 | 低 |
| entities/effect/Decal.java / RubbleDecal / ScorchDecal | targetGroup 机制与 public drawDecal（本项目改为 updateAll 静态管理+内部绘制） | 结构性差异 | entities/effect/Decal.java | 行为等价（贴花照样画/自动消失） | 低 |
| entities/type/Bullet.java:139-142 | Bullet.absorb() 公共方法（ForceProjector 经 AbsorbTrait 调用） | 结构性差异 | world/blocks/defense/ForceProjector.java:263 | 本项目在护盾侧直接移除子弹，行为等价（护盾照样吸弹） | 低 |
| game/GlobalData.java | items（战役发射资源池 data.addItem）+ exportData/importData | 缺失 | game/GlobalData.java | 与 launchZone 降级关联（Logic 已声明）；无跨局资源池 | 低 |
| core/Logic.java:202-207 | launchZone：attack 区域胜利=发射（Fx.launch、资源入库、LaunchItemEvent） | 偏离(已声明) | core/Logic.java:216-219 | 注释声明"区域发射/存档/解锁链未移植→统一按普通胜利弹结算框" | 低 |
| game/EventType.java | 非网络事件类缺失：UnitCreateEvent、DepositEvent/WithdrawEvent、TapEvent/TapConfigEvent、BlockBuildBeginEvent、BuildSelectEvent、CommandIssueEvent、MechChangeEvent、ZoneRequireComplete/ConfigureComplete | 缺失(影响待评估) | game/EventType.java | 多数功能已在别处直接调用（GameOverEvent 有等价替代）；成就/统计类监听点随之缺失 | 低 |
| game/Saves.java | SaveSlot 的 cautiousLoad、exportFile/importFile、getDate/getMods 等 | 结构性差异 | game/Saves.java | 本项目存档结构自研（SaveMeta 白名单），元数据功能齐（dateMillis/playtime/wave/preview）；导出/安全读档缺失 | 低 |
| game/Schematics.java | overwrite（覆盖保存蓝图）、loadSync | 缺失 | game/Schematics.java | 蓝图只能新增不能同名覆盖 | 低 |
| game/SpawnGroup.java | write/read（msav 序列化）+ toString | 缺失 | game/SpawnGroup.java | 本项目地图存档不序列化 spawns 表 | 低 |
| game/Team.java / Teams.java | Team.enemies()/active()、TeamData 包装类 | 结构性差异 | game/Team.java / game/Teams.java | TeamData 承担的 brokenBlocks/cores/items 已平铺到 Teams（BuilderDrone 注释声明）；TeamData 的战役 AI 字段未移植 | 低 |
| entities/type/BaseUnit.java:349-351 | added() 里 if(isCommanded()) onCommand(getCommand())（指挥中心命令） | 偏离(已声明) | entities/type/BaseUnit.java:996-1003 | 指挥中心已声明退役为空壳（CommandCenter.java），命令走 CommandAI | 低 |
| entities/traits/BuilderTrait.java（内部类 BuildRequest） | BuildRequest 内部类 | 结构性差异 | world/BuildRequest.java | 挪包而非缺失，字段/语义齐（buildQueue/progress/breaking/config） | 低 |
| ai/Pathfinder.java | debugValue（调试字段） | 缺失 | ai/Pathfinder.java | 仅调试用；其余方法/流场逻辑一一对应 | 低 |
| entities/type/Player.java | Player 特有行为（respawn 计时、respawns） | 已对齐 | entities/type/Player.java:89-94,434-441 | respawn/limitedRespawns/respawnTime 均有等价实现（架构豁免项） | 低 |

### 断裂汇总（方法/字段存在但调用链没接上）

| 项 | 本项目位置 | 证据 |
| --- | --- | --- |
| Trigger.fireExtinguish 无人 fire | game/EventType.java:20 | 枚举存在，唯一应触发的 Fire.extinguish 未发 |
| rules.launchWaveMultiplier 有值无消费 | content/Zones.java:62 → core/Logic.java:149 | Zones 设置 3f，runWave 不读 |
| Zone.updateWave/bestWave 无调用点 | type/Zone.java（存在）↔ core/Logic.java | 原版在 WaveEvent 监听器调用，本项目 runWave 后无调用 |
| Statuses.getArmorMultiplier 无消费者 | entities/units/Statuses.java:91 | 原版消费者 calculateDamage 未移植 |
| SoundLoop 类无消费者 | game/SoundLoop.java | activeSound 工作音效系统缺失（Blocks.java:1043 已声明 B 类 TODO），该类成死代码 |

## 偏离(已声明)清单 —— 不计为问题

- entities/effect/Fire.java:36-46：联机不同步 / 音效无距离衰减 / 存档不落盘（"无 Puddle"一条已过时，见断裂表）
- entities/type/base/FlyingUnit.java:17-23：updateRotation/drawWeapons/attack/onCommand 刻意未移植（B 类）；flyAttack 改"追击最近单位"
- entities/type/base/HoverUnit.java:23-24：drawWeapons 无调用点（TODO 声明）
- entities/type/BaseUnit.java:791-793：avoidOthers 推力改为直接加速度（F15 修复声明）；updateVelocity:992 floor.dragMultiplier 未移植声明
- entities/type/BaseUnit.java:1010-1020：write/read 死代码声明（联机走 Snap、存档走 SaveIO）
- entities/type/Unit.java:1-5：整类注释保留 + 能力去向说明（但部分能力实际未落地，见高优先级表）
- world/blocks/units/CommandCenter.java：指挥中心退役为空壳（对应 v8 LegacyCommandCenter），命令走 CommandAI
- ai/UnitCommand.java:12-20：RTS 命令替代 v6 枚举广播；repair/rebuild/mine 系命令待子系统移植
- entities/Damage.java:113-117：collideLine 用"点线距离"替代 raycastRect（声明"效果等价"）
- core/Logic.java:167-176：checkGameOver 按原版两分支重写（含此前误判修复记录）；GameOverEvent→直接弹结算框
- entities/effect/Puddle.java:232：客机插值降级声明
- content/Bullets.java:807,834：Lightning.create → createLighting(seed,...) 签名差异声明
- world/blocks/defense/turrets/LaserTurret.java:64：工作音效系统缺失（B 类）
- entities/type/TileEntity.java:168-175：config() 默认 -1 语义声明
- game/DefaultWaves / Difficulty / Gamemode / LoopControl / MusicControl / Objective(s) / Stats / Schematic(主体) / Tutorial / Team / Teams(主体) / ai/BlockIndexer / ai/Pathfinder / ai/WaveSpawner(除上表) / entities/EntityGroup / EntityCollisions / Entities / Predict / Effects / traits 全部小接口 / units/StateMachine / StatusEntry / UnitDrops / UnitState / type/BaseEntity / DestructibleEntity / SolidEntity / TimedEntity / EffectEntity / base/BaseDrone / BuilderDrone / MinerDrone / RepairDrone / bullet 全部子类：方法级与行为级对齐，无发现问题
