# G1 批次报告：物流与库存记账（第二轮审计 A4/A6 产出，5 项）

> 范围：`docs/port/audit-A4-world-content.md`（D1/M3/M4/B2）+ `docs/port/audit-A6-ui-input-graphics.md`（B3 前半）。
> 参照 `D:\IdeaProjects\Mindustry-126.2`（只读）。未运行任何编译/git 命令。

## 完成情况：5/5 全部完成

| # | 项 | 状态 | 文件 |
| --- | --- | --- | --- |
| 1 | Conveyor 卸载物品不扣库存（复制漏洞） | ✅ | `world/blocks/distribution/Conveyor.java` |
| 2 | Conveyor 缺 acceptStack/handleStack 覆写 | ✅ | 同上 |
| 3 | Drill/GenericCrafter/UnitFactory 缺 shouldConsume | ✅ | `world/blocks/production/Drill.java`、`GenericCrafter.java`、`world/blocks/units/UnitFactory.java` |
| 4 | Turret 基类 updateShooting 未乘 baseReloadSpeed | ✅ | `world/blocks/defense/turrets/Turret.java` |
| 5 | Block.getReplacement 默认 + Conveyor 覆写（A6-B3 前半） | ✅（另补 Conduit） | `world/Block.java`、`Conveyor.java`、`world/blocks/liquid/Conduit.java` |

---

## 1. Conveyor 卸载扣库存（原版 Conveyor.java:201）

**改动**：`ConveyorEntity.update()` 的卸载分支（约 :492-508）在清空数组前插入
`items.remove(offloaded, len - i)`，并把 `ids[i]` 先取到局部变量 `offloaded`（随后数组被清空，不能再读 `ids[i]`）。

- 与 `handleItem`（:241 的 `e.items.add`）配对后，`items` 记账与带上的并行数组一致。
- 后果修复：悬停库存不再随搬运量单调虚高；拆掉跑过 N 个物品的带子不再按 `items.get` 掉出 N 个幽灵物品。
- `ItemModule.remove(Item, int)` 签名已核对存在（`world/modules/ItemModule.java:33`），语义为 `set(item, max(0, get-amount))`。

## 2. Conveyor acceptStack/handleStack 覆写（原版 :258-278）

在 `Conveyor` 上补两个方块级覆写（基类 `Block.acceptStack/handleStack` 在 :991/:1003）：

- `acceptStack` = `min((int)(entity.minitem / itemSpace), amount)`。
- `handleStack` = 把整叠物品按 `i * itemSpace` 摆进 `ids/xs/ys` 并行数组 + `items.add(item, 1)`，不再只 `items.add`（否则物品"凭空"在带上、无位置）。
- 刻意偏离：原版末尾 `entity.noSleep()` 本项目无（同 `handleItem`）。

## 3. 三个方块补 shouldConsume

`Block.shouldConsume(Tile)`（:802）与 `ConsumeModule`（`valid()`/`update()` 的 `docons`）是既有钩子，返回语义 = "此刻是否该消耗资源"。

- **Drill**：`tile.entity.items.total() < itemCapacity`。
- **GenericCrafter**：`outputItem` 满 → false；否则 `outputLiquid == null || !(liquids.get >= liquidCapacity - 0.001f)`。
  修复"满仓不停产、输出无限堆积、输入持续被吃"。
- **UnitFactory**：`aliveCount() < maxSpawn`。
  ⚠ **偏离**：原版用实体字段 `spawned`（O(1) 计数，`unitRemoved` 递减）。本项目不维护该字段（加字段会改实体存档格式 → 需升 `SaveIO.version`），改用既有的 `UnitFactoryEntity.aliveCount()`（遍历 `Units.units` 按 `getSpawner()==tile.pos()` 统计），语义等价、代价是每帧 O(单位数)。

三者均只在 `ConsumeModule` 路径生效，无其它副作用。

## 4. Turret 基类 updateShooting × baseReloadSpeed（原版 Turret.java:251）

`Turret.TurretEntity.updateShooting()` 的 `reload += Time.delta() * bullet.reloadMultiplier` 追加 `* baseReloadSpeed()`，并删掉原"留给主会话决定"的注释。

- 影响面：`baseReloadSpeed()` 默认 1f，故 `CooledTurret`/`LiquidTurret`（继承基类推进）**无行为变化**；
  仅 `PowerTurret`（覆写 = `power.status`）及其子类 `ChargeTurret`/`LaserTurret` 生效 —— 正是原版行为。
- 不双乘：`ItemTurretEntity.updateShooting`（:265-279）完全覆写、自行乘过 `baseReloadSpeed()`，不走基类；
  `LaserTurretEntity.updateShooting` 也完全覆写。
- `CooledTurretEntity.updateCoolant()`（:89）本来就乘 `baseReloadSpeed()`，与原版 `CooledTurret` 一致（基类推进与冷却推进各乘一次）。

## 5. Block.getReplacement 默认实现 + Conveyor/Conduit 覆写（A6-B3 前半）

- **Block**（`world/Block.java`，加在 `handleStack` 之后）：`public Block getReplacement(BuildRequest req, Array<BuildRequest> requests){ return this; }`。
- **Conveyor**：照原版覆写 —— 前后（`Geometry.d4[rotation]` 与反向 `mod(rotation-2,4)`）都有同向的传送带/交叉器请求、且本格已有垂直朝向的传送带时返回 `Blocks.junction`，否则自身。
- **Conduit**（额外补，同属 A6-B3）：同款逻辑，返回 `Blocks.liquidJunction`；并更新类头那句过时的"`getReplacement` 未移植"注释。
- **坐标口径**：本项目 `Geometry.d4` 是数组（无 `d4(int)` 方法），`d4[rotation]` 即前进方向（0=上），与原版 `Geometry.d4(rotation)` 语义等价；反向用 `Mathf.mod(rotation-2, 4)`。垂直判据 `mod(tile.rotation()-req.rotation, 2)==1` 旋向无关，照抄。
- **未越界**：未改 `InputHandler.updateLine`（G6 负责接线 `blockreplace` 设置 + 调用点）。当前 `getReplacement` 无调用者，纯 additive。

---

## 文件清单

| 文件 | 改动 |
| --- | --- |
| `core/src/com/phoenix/game/world/Block.java` | +`getReplacement` 默认实现 |
| `core/src/com/phoenix/game/world/blocks/distribution/Conveyor.java` | 卸载扣库存；+`acceptStack`/`handleStack`/`getReplacement`/`containsAt`；+imports(Array/Blocks/BaseUnit/BuildRequest) |
| `core/src/com/phoenix/game/world/blocks/liquid/Conduit.java` | +`getReplacement`/`containsAt`；+imports；更新类头注释 |
| `core/src/com/phoenix/game/world/blocks/production/Drill.java` | +`shouldConsume` |
| `core/src/com/phoenix/game/world/blocks/production/GenericCrafter.java` | +`shouldConsume` |
| `core/src/com/phoenix/game/world/blocks/units/UnitFactory.java` | +`shouldConsume` |
| `core/src/com/phoenix/game/world/blocks/defense/turrets/Turret.java` | 基类 `updateShooting` × `baseReloadSpeed()`；更新注释 |

## 不确定 / 待主会话确认

1. **UnitFactory 用 `aliveCount()` 替代原版 `spawned` 字段**：语义等价（在场出厂单位数），但每帧多一次 O(单位数) 扫描（`shouldConsume` 每帧被 `ConsumeModule` 调用）。若主会话希望严格照抄原版计数，需要新增实体字段并升 `SaveIO.version`。
2. **Conduit.getReplacement 是否在 G1 范围内**：任务描述点名了 Conveyor/Conduit，正文只写"Conveyor 照原版覆写"。已按审计条目 B3 一并补齐（低风险 additive），如认为越界可单独回退。
3. **未验证运行**：按协议禁止跑 gradlew，故以上仅静态核对（签名/import/覆写一致性）。`getReplacement` 目前无调用者，实际连线行为要等 G6 接线后才能实跑验证。
