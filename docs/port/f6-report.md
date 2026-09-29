# F6 批报告：Damage 家族（tileDamage / 距离衰减 / 静态入口接线）

原版参照：`D:\IdeaProjects\Mindustry-126.2\core\src\mindustry\entities\Damage.java`（下称"原版 Lxx"）。
本项目落点：`core/src/com/phoenix/game/entities/Damage.java`。
约束遵守：未跑 gradlew、未做 git 操作。

## 1. tileDamage 菱形蔓延 —— 完成

- 原版 L214-255 `tileDamage(Team, int startx, int starty, int radius, float baseDamage)` → 本项目 `Damage.tileDamage`（Damage.java:108-150），逐行照抄：
  - 曼哈顿距离逐环衰减 `scaledDamage = (int)(damage * (1f - dst/radius))`，衰减归零即停（菱形图案）；
  - 命中建筑先扣血、余量继续向四邻传播（穿透）；
  - `bits.set` 在 `scaledDamage <= 0` 检查**之前**的顺序、`tile.getTeam() != team` 的**引用比较**（team==null 时含友军全部吃伤）、short 打包截断等细节均与原版一致。
- 依赖复用：`struct/GridBits`、`struct/IntQueue` 已存在且 API 与 arc 一致，直接使用。
- 原版 `@Struct` 生成的 `mindustry.gen.PropCell`（byte x + byte y + short damage 压一个 int）无注解处理器 → 在 Damage.java 内用 4 个私有静态方法等价实现（Damage.java:155-169）。
- API 适配：`tile.entity.health`（原版公开字段）→ `health()`（本项目 protected）；`tile.entity.damage(int)` → `handleDamage(float)`（本项目 TileEntity 的等价入口，含血量归零销毁/网络广播）；`Geometry.d4` 是 libgdx `GridPoint2[]` → 下标循环（同时符合热路径下标循环硬约束）。
- **任务描述中"可燃方块起火"未做**：原版 126.2 的 tileDamage 没有起火逻辑（Fire 的引燃/蔓延在 Fire 类自身 update 里，本项目已移植）；本批不添加原版没有的行为。
- 接线：6 参 `damage(..., complete=false)` 的空 TODO 分支 → 原版 L203-208 逻辑（`trad = radius/tilesize`；`world.tileWorld(x,y)` 取起点格后调 `tileDamage`），TODO 已消化。**dynamicExplosion 的多波伤害由此自动走 tileDamage 路径**（其调用代码本身未改，仅更新注释），dynamicExplosion 本体未动。

## 2. Damage.calculateDamage 距离衰减 —— 已存在，核对一致

- 原版 L269-274（`falloff=0.4f` 的 lerp 衰减）与本项目实现逐字符一致（Damage.java:191-196），无改动。

## 3. 静态入口接线核对 —— 3 处补齐

- **新增 4 参无 team 重载** `damage(float x, float y, float radius, float damage)`（原版 L170-172，Damage.java:42-44）。
- **(Team,...) 4 参改为转发** `damage(team, ..., false)`（原版 L175-177，Damage.java:47-49）。原先它是"无衰减、只伤单位"的早期最小实现；转发后其唯一外部消费方 `BulletType.java:150`（溅射子弹）自动获得原版行为：**距离衰减（边缘 ~40%）+ tileDamage 伤建筑**。
- **反应堆爆炸迁移**：`damageAll`（原版没有的口径，无衰减、不伤建筑）删除；两个调用方改为原版 L170 重载：
  - `ImpactReactor.java:124`：`damageAll(cx, cy, explosionRadius, ...)` → `damage(cx, cy, explosionRadius * tilesize, ...)`。**顺带修复真 bug**：原版 `explosionRadius=50` 是**格数**、调用处乘 tilesize，本项目漏乘导致爆炸半径只有 50px（约半格）；字段注释"爆炸半径（像素）"更正为"格数"。
  - `NuclearReactor.java:257`：`damageAll(...)` → `damage(...)`（本就乘了 tilesize，仅换入口）；javadoc 里"→ 本项目对应 damageAll"的偏离声明更新为"已直接调用"。
- 原版 126.2 无 collideDamage/collideTeam/collideAir（任务描述中提到的方法不在此版本），无需处理。`collideLine`/`damageUnits`/`completeDamage`/`createIncend` 现状与原版语义对齐（collideLine 有已注释声明的简化：点到线段距离替代 raycastRect），不动。

## 行为变化提示（需实机回归的点）

1. 溅射子弹现在会**拆建筑**（tileDamage）且伤害带距离衰减——与原版一致，但改变既有手感；
2. 反应堆核爆现在波及建筑（含友军，原版 damage(null) 无差别）；
3. dynamicExplosion 多波伤害开始侵蚀地形建筑。

## 已知保留的原版自身特性（未"修复"）

- `tileDamage` 的静态 `bits/propagation` 在重入（传播途中建筑被炸毁 → onDestroyed → dynamicExplosion → 再次 tileDamage）时会被内层清空、截断外层传播——原版同样使用静态字段，行为保持一致。
- `PropCell.damage` 为 short：单格伤害 >32767 截断成负数、提前停止传播——照抄。
- 传播算法 pop 时不去重（同一格可能被两个邻居先后入队而结算两次）——原版如此，照抄。

## 文件清单

| 文件 | 改动 |
| --- | --- |
| `core/src/com/phoenix/game/entities/Damage.java` | +bits/propagation 字段、+PropCell 打包方法、+tileDamage、+4 参 float 重载、4 参 Team 重载改转发、!complete 分支接线、删 damageAll、注释更新 |
| `core/src/com/phoenix/game/world/blocks/power/ImpactReactor.java` | 爆炸调用迁到 `Damage.damage(cx,cy,r*tilesize,...)`（修漏乘 tilesize）、+static import、注释更正 |
| `core/src/com/phoenix/game/world/blocks/power/NuclearReactor.java` | 爆炸调用迁移、javadoc 差异表更新 |

未验证项：按协议禁止编译/运行，以上为静态对照结论；建议实机回归点见"行为变化提示"。
