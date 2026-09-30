# A4 审计：world / content / type（第二轮静默缺口普查）

> 审计范围：本项目 `com/phoenix/game/{world,content,type}` ↔ 原版 `mindustry/{world,content,type}`（只读参照 `D:\IdeaProjects\Mindustry-126.2`）。
> 判据：**原版有 + 本项目没有/不同 + 无注释声明刻意偏离**，三者同时满足才计入。
> 已扣除：A1/A2/A3 与 F1~F18/T1/R1/N1 已登记项、已知刻意偏离（acceptItem 默认语义、坐标口径、rotation 差 90°、d4 旋向、PowerVoid/MechPad 等）。
> **文件级结论**：`world/`（172 对）、`content/`（12 对）、`type/`（15 对）均 1:1，无缺文件。项目侧多出的 `world/BuildRequest`、`world/blocks/OreFloor`、`world/blocks/distribution/TitaniumConveyor`、`world/meta/{Attributes,I18n}` 是改名/挪位或自研，非缺口。
> **内容名覆盖**：Blocks/Bullets/Fx/UnitTypes/Mechs/Zones 名字全在（A2 已核），本轮不重复。

## 摘要：缺失 8 / 偏离 5 / 断裂 3

最严重的三条：
1. **传送带物品账目断裂（高）**：`Conveyor.update` 把带首物品交出去后**没有从 `entity.items` 扣减**（原版 Conveyor.java:201 有 `e.items.remove(...)`）。后果：① 悬停传送带显示的库存随搬运量单调增长（永远虚高）；② 拆掉一条跑过 N 个物品的传送带会**掉出 N 个幽灵物品（复制漏洞）**（`Block.onDestroyed` 按 `items.get()` 掉落）。
2. **单位/玩家武器没有炮口特效（中）**：原版 `Weapon.shootDirect` 在开火时放 `bullet.shootEffect` / `bullet.smokeEffect`（type/Weapon.java:121-122）；本项目 `Weapon.shootDirect` 只放 `ejectEffect`，**全项目再无任何地方消费 `shootEffect/smokeEffect`**（炮塔走 Turret.effects，武器没有）→ 所有单位/玩家武器开火没有炮口闪光/硝烟。
3. **三个方块缺 `shouldConsume` 覆写（中）**：原版 `Drill`/`GenericCrafter`/`UnitFactory` 覆写 `shouldConsume` 在"满仓/满单位"时停料；本项目只有 `Fracker`/`Separator` 覆写。后果：满仓的钻头/工厂**继续耗电**；合成器**满仓不停产**（`items.add` 不受 `itemCapacity` 限制 → 输出无限堆积、输入被持续消耗）。

---

## 一、缺失（原版有、本项目完全没有）

| # | 原版位置 | 项 | 本项目现状 | 严重度 | 修复建议 |
| --- | --- | --- | --- | --- | --- |
| M1 | `type/Weapon.java:121-122` | `Effects.effect(bullet.shootEffect, ...)` / `smokeEffect` | `type/Weapon.java:132-160` `shootDirect` 只放 `ejectEffect`；全项目无 `.shootEffect` 消费（仅炮塔 `Turret.effects` 有） | 中 | 在 `shootDirect` 末尾照原版补两行：`Effects.effect(bullet.shootEffect, x, y, rotation)` + `smokeEffect`（`bullet==null` 守卫） |
| M2 | `world/blocks/power/Battery.java:21-28` | `draw()`：按 `power.status` 在 `emptyLightColor↔fullLightColor` 之间给电池画色块 + 叠 `-top` 贴图 | `world/blocks/power/Battery.java` 无 `draw`，电池本体不显示电量 | 中低 | 补 `draw`（`Fill.square`/`Draw.rect` 等价），色由 `entity.power.status` 插值 |
| M3 | `world/blocks/distribution/Conveyor.java:258-278` | `acceptStack`（返回 `min(minitem/itemSpace, amount)`）/`handleStack`（把整叠物品按 `i*itemSpace` 摆进带子数组） | `Conveyor` 未覆写 → 走 `Block` 默认（`world/Block.java:991-1007`）：`handleStack` 只 `items.add`，**不进 `ids/xs/ys` 数组** | 中 | 照原版覆写 `acceptStack`/`handleStack`（把物品摆进并行数组） |
| M4 | `Drill.java:130-133`、`GenericCrafter.java:137-143`、`UnitFactory.java:194-197` | `shouldConsume(tile)`：满仓/满单位时返回 false | 三者在 `world/blocks/production/*`、`world/blocks/units/UnitFactory.java` 均未覆写（仅 `Fracker`/`Separator` 覆写） | 中 | 逐类照原版补覆写（Drill= `items.total()<itemCapacity`；GenericCrafter= 输出物品/液体未满；UnitFactory= `spawned<maxSpawn`） |
| M5 | `world/modules/ItemModule.java:17-23,60-65,122-124` | `forEach(ItemConsumer)`、`hasOne(ItemStack[])`、`remove(ItemStack)` | `world/modules/ItemModule.java` 均无（原版 `hasOne` 亦无消费者 → 死方法） | 低 | 仅当后续需要 UI 遍历/整叠移除时补；当前可不动 |
| M6 | `type/ItemStack.java:20-60` | `copy`/`equals`/`mult`/`list`/`compareTo`（`Comparable`） | `type/ItemStack.java` 仅有 `with`；原版 `mult` 用于大型墙 `ItemStack.mult(wall.requirements,4)`，本项目改为在 `Blocks.java` 直接写死数量（等价） | 低 | 视需要补；`mult` 可留作工具 |
| M7 | `world/blocks/units/RepairPoint.java:47-64,72-75` | `draw`+`drawLayer`（按 `entity.rotation` 画旋转炮塔）/`setStats(range)` | 项目 `RepairPoint` 无 draw 覆写（用默认单张贴图，炮塔不随目标转向）；无 `setStats`（缺 range 统计）。**激光连线已在类注释声明未移植** | 低 | 补旋转炮塔绘制与 range 统计 |
| M8 | `world/blocks/storage/CoreBlock.java:42-53,186-232` | `onUnitRespawn(@Remote)`+`update`(respawn 进度/heat)+`drawLayer`(重生动画)+`shouldActiveSound`+`handleItem`(CoreItemDeliverEvent) | 项目 `CoreBlock` 无这些；重生被改写到 `Player.update()`（`entities/type/Player.java:420-455`）→ 无核心处重生动画/进度条、无 `Fx.spawn`。**类头注释已声明"单位重生…尚未移植"（非静默）** | 中低 | 若追求表现一致，按原版把重生计数/动画搬回核心 |

## 二、偏离（两边都有，行为/数值不同，无声明）

| # | 原版位置 | 项 | 本项目现状 | 严重度 | 修复建议 |
| --- | --- | --- | --- | --- | --- |
| D1 | `world/blocks/distribution/Conveyor.java:201` | `e.items.remove(e.ids[i], e.len - i)`：卸载物品时同步扣库存 | `world/blocks/distribution/Conveyor.java:425-436` 只清空 `ids/xs/ys` 数组，**未扣 `e.items`**（`handleItem` 却在 :241 `items.add`） | **高** | 在 `len = i` 前补 `e.items.remove(ids[i], len - i)`（注意先取局部变量） |
| D2 | `world/Tile.java:394-440` | `updateOcclusion()`：按液体/遮挡/血量/drownTime 累加 `cost`（+4/+2/+health/+10/+70） | `world/Tile.java:267-269` 是空实现（`cost = 1`），而 `ai/Pathfinder.java:53,283` 确实读 `tile.cost` | 中 | 照原版补 `updateOcclusion` 的 cost 累加（`Geometry.d8` 邻域判定） |
| D3 | `world/Build.java:77` | `validPlace` 里 `Units.anyEntities(...)` 禁止把实心方块压在单位身上 | `world/Build.java:84-132` 无该判定（`entities/Units.java:64` 已实现 `anyEntities` 但无人调用） | 中低 | 在 `validPlace` 开头补 `(block.solid||block.solidifes) && Units.anyEntities(...)` 早退 |
| D4 | `world/blocks/defense/MendProjector.java:68,93` | 治疗量 `× entity.efficiency()`；`heat` 目标含 `tile.isEnemyCheat()` | `world/blocks/defense/MendProjector.java:170,225` 只 `cons.valid()`、`healBy(maxHealth*percent)`（无 `×efficiency()`）；无 `isEnemyCheat` | 低 | 补 `× efficiency()`（与 `OverdriveProjector` 一致）与作弊豁免 |
| D5 | `world/blocks/storage/CoreBlock.java:214-221` | 重生在核心格 `set(tile.drawx,drawy)` + `applyImpulse(0,8)` | `core/Control.java:790-803` 重生点取 `core.getY()+32f`（核心上方一格）、无冲量；同 M8 | 低 | 见 M8 |

## 三、断裂（本项目已有实现/监听，但调用链没接上）

| # | 原版位置 | 项 | 本项目现状 | 严重度 | 修复建议 |
| --- | --- | --- | --- | --- | --- |
| B1 | `world/blocks/storage/CoreBlock.java:194-202` | `handleItem` 里 `Events.fire(new CoreItemDeliverEvent())` | `game/EventType.java:85` 有类、`game/Tutorial.java:86` 在监听，但**全项目无 `new CoreItemDeliverEvent()`**（项目 CoreBlock 无 `handleItem`）→ 教程 `coreitem` 步骤永不完成 | 中 | 在 `CoreBlock` 覆写 `handleItem`（`state.rules.tutorial` 时 fire），或等价处补 fire |
| B2 | `world/blocks/defense/turrets/Turret.java:251` | 装填推进 `× baseReloadSpeed(tile)` | 项目 `Turret.TurretEntity.updateShooting`（:308-315）**未乘** `baseReloadSpeed()`；注释自称"暂不接进推进公式（留给主会话决定）"。多数炮塔走 `ItemTurret.updateShooting`（已乘），故仅影响直接用基类推进的炮塔 | 低 | 把 `baseReloadSpeed()` 乘进基类 `updateShooting` 的 `reload +=` |
| B3 | `world/blocks/units/UnitFactory.java:66` | `Events.fire(new UnitCreateEvent(unit))` | `game/EventType.java:342` 有类但从不触发（原版 126.2 亦无消费者 → 死事件） | 低 | 视需要补 fire；否则可作死代码清理 |

---

## 附：本轮核对后**判定为非缺口**的项（避免后续重报）

- `BlockBuildBeginEvent`（`world/Build.java:43,64` 触发）：本项目未触发，但**原版也无任何监听者**（死事件）→ 不计。
- `shouldActiveSound` / `shouldIdleSound`（`world/Block.java:237-243`）：音效系统属已声明 B 类降级（A2 已记），不计。
- `PowerGraph` 整体重写（`distributePower/getPowerBalance/getSatisfaction/…`）、`PowerNode` 自动连线替代手动 `onConfigureTileTapped`：A2 已声明为等价重写；`PowerNode` 类注释亦说明连接判据 → 不计。
- `Tile.setFloorUnder`：本项目在 `editor/EditorTool.java:279-282` 内联等价实现 → 不计。
- `Fx` 的 `GroundEffect`：`content/Fx.java:29-30` 类头已声明"未移植地面层，退化为普通 Effect" → 不计。
- `ItemStack.mult/list`：`content/Zones.java` 的 `ItemStack.list` 调用全在块注释内；大型墙 `mult` 已由 `Blocks.java` 写死等价数值（N1 已核）→ 不计。
- `ImpactReactor.onDestroyed`：本项目用 `FusionReactorEntity.kill()` 覆写实现（`ImpactReactor.java:115-132`），行为等价 → 不计。
- `Tile.getLinkedTiles/getHitbox/getNearby(Point2)`、`Block.canReplace/drawRequest*`、`StorageBlock.removeItem/hasItem`、`ItemModule.sum/addAll`：A2 已判定为等价自研/死方法 → 不计。
- `Weapon.shoot` 网络分支（`onPlayerShootWeapon/onGenericShootWeapon`）：本项目走自研 `Packets.ShootWeapon` 广播 → 结构差异，不计。
- `content/StatusEffects` 各项颜色：项目自设 `color`（原版默认白），属美术选择、非行为缺口 → 不计。

## 备注
- 本报告为**只读审计**，未改动任何代码。
- 数值细节以 `docs/port/n1-report.md` 为准，本报告只报行为/接线类缺口。