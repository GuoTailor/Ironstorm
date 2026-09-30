# G5 批次报告：图形与特效（10 项）

> 范围：`docs/port/audit-A4-world-content.md` / `audit-A5-entities-ai.md` / `audit-A6-ui-input-graphics.md` 的 G5-1~G5-10。
> 参照 `D:\IdeaProjects\Mindustry-126.2`（只读）。**未运行任何 gradle / git 命令**（按协议），以下为静态核对 + 人工读码。
> 铁律：数值/公式/绘制顺序/字段名一律照抄原版；凡与原版不同处一律写「刻意偏离 + 原因」。

## 完成情况：10/10 全部处理（9 项照抄改回/补齐 + 1 项附带资产补齐）

| # | 项 | 审计号 | 状态 | 改动文件 |
| --- | --- | --- | --- | --- |
| G5-1 | Weapon 补 shootEffect / smokeEffect | A4-M1 | ✅ 照抄（含 null 守卫） | `type/Weapon.java` |
| G5-2 | FlyingUnit.drawEngine() | A5-断裂 | ✅ 照抄（含 isFlying 守卫 + BLEND） | `entities/type/base/FlyingUnit.java` |
| G5-3 | FlyingUnit.wobble() | A5-缺失 | ✅ 照抄 | `entities/type/base/FlyingUnit.java` |
| G5-4 | BasicBulletType 尺寸对齐 | A5-偏离 | ✅ 照抄（含子类连坐） | `entities/bullet/BasicBulletType.java`、`ArtilleryBulletType.java`、`content/Bullets.java`(注释) |
| G5-5 | MassDriverBolt 补特效 | A5-偏离 | ✅ 照抄 | `entities/bullet/MassDriverBolt.java` |
| G5-6 | GroundUnit.draw 液体/溺水着色 | A5-偏离 | ✅ 照抄 | `entities/type/base/GroundUnit.java` |
| G5-7 | Battery.draw 电量色块 | A4-M2 | ✅ 照抄（+ 补 2 张缺失贴图） | `world/blocks/power/Battery.java`、**新增** `core/assets-raw/sprites{,_out}/blocks/power/battery{,-large}-top.png` |
| G5-8 | RepairPoint 旋转炮塔 + setStats(range) | A4-M7 | ✅ 照抄 | `world/blocks/units/RepairPoint.java` |
| G5-9 | MendProjector × efficiency() + isEnemyCheat | A4-D4 | ✅ 照抄 | `world/blocks/defense/MendProjector.java` |
| G5-10 | shieldBuffer dispose | A6-B4 | ✅ 照抄 | `core/Renderer.java`、`ClientLauncher.java` |

---

## G5-1 武器 shootEffect / smokeEffect（A4-M1，中）

**原版**：`type/Weapon.java:119-122`（静态 `shootDirect`）
```java
Effects.shake(weapon.shake, weapon.shake, x, y);
Effects.effect(weapon.ejectEffect, x, y, rotation * -Mathf.sign(left));
Effects.effect(ammo.shootEffect, x + Tmp.v1.x, y + Tmp.v1.y, rotation, shooter);   // Tmp.v1 = trns(rotation, 3f)
Effects.effect(ammo.smokeEffect, x + Tmp.v1.x, y + Tmp.v1.y, rotation, shooter);
```

**本项目改动**：`core/src/com/phoenix/game/type/Weapon.java:155-165`
- 新增炮口前移量：`float fx = x + Angles.trnsx(rotation, 3f), fy = y + Angles.trnsy(rotation, 3f);`
  （等价原版 `Tmp.v1.trns(rotation, 3f)`；本项目用 `Angles.trnsx/y` 直接算，避免与 recoil 共用的 `Tmp.v1` 冲突）。
- 在既有 `Effects.effect(ejectEffect, ...)` 之后补两行 `Effects.effect(bullet.shootEffect/smokeEffect, fx, fy, rotation, shooter)`。

**照抄 / 偏离**：照抄。**唯一偏离**：本项目 `Weapon.bullet` 允许为 null（无弹武器），原版是 `@NonNull` →
两行特效外加 `if(bullet != null)` 守卫（已在注释声明）。`Effects.effect(Effect,float,float,float,Object)`
5 参重载本项目已存在（`entities/Effects.java:119`），`data` 传 `shooter` 与原版一致。

**遗留风险**：低。`Fx.shootSmall/shootSmallSmoke` 已存在；特效走 `data=shooter` 的 Effect 会拿到 BaseUnit，
与原版 `ShooterTrait` 的用法等价（本项目 Effect 的 data 只被个别特效读，无类型强转风险）。

---

## G5-2 FlyingUnit.drawEngine()（A5-断裂，低）

**原版**：`entities/type/base/FlyingUnit.java:139-141`（`drawUnder()` 调 `drawEngine()`）、`:163-172`
```java
Draw.color(Pal.engine);
Fill.circle(x + Angles.trnsx(rotation + 180, type.engineOffset), ..., type.engineSize + Mathf.absin(Time.time(), 2f, type.engineSize/4f));
Draw.color(Color.white);
Fill.circle(x + Angles.trnsx(rotation + 180, type.engineOffset - 1f), ..., (type.engineSize + Mathf.absin(...))/2f);
Draw.color();
```

**本项目改动**：`core/src/com/phoenix/game/entities/type/base/FlyingUnit.java:98-126`
- 新增 `protected void drawEngine()`：逐行照抄，`Time.time()` 用本项目同名方法。
- 新增覆写 `draw()`：`if(isFlying()) drawEngine();` 再 `super.draw();`
  —— 对应原版 `drawUnder()`（引擎）在 `draw()`（机体）**之前**的调用顺序；本项目没有 `drawUnder()` 钩子，
  且 `FlyingUnit extends GroundUnit`（共享绘制点）→ 加 `isFlying()` 守卫，避免万一有非飞行子类时误画。
- ⚠ 按协议「Fill 前自开 BLEND」：在 `drawEngine()` 开头 `glEnable(GL_BLEND)` + `glBlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)`
  （同 `Renderer.drawShields` 的既有写法；`SpriteBatch.flush` 会重设自己的混合，不会污染后续精灵）。

**照抄 / 偏离**：照抄。**刻意偏离（结构性）**：本项目无 `drawUnder()`，改用 `draw()` 覆写 + `isFlying()` 守卫
（已在注释声明）。`Pal.engine` 本项目已存在（`graphics/Pal.java:50`）。

**遗留风险**：低。所有 `FlyingUnit` 子类（BaseDrone/BuilderDrone/MinerDrone/RepairDrone/HoverUnit）均**未**覆写 `draw()`，
故引擎绘制对所有飞行单位生效（原版行为）。若某飞行单位 `type.engineSize` 为 0 则不画（与原版一致）。

---

## G5-3 FlyingUnit.wobble()（A5-缺失，低）

**原版**：`entities/type/base/FlyingUnit.java:135`（`update()` 末尾调 `wobble()`）、`:190-199`
```java
protected void wobble(){
    if(net.client()) return;
    x += Mathf.sin(Time.time() + id * 999, 25f, 0.05f) * Time.delta();
    y += Mathf.cos(Time.time() + id * 999, 25f, 0.05f) * Time.delta();
    if(velocity.len() <= 0.05f){ /* rotation += ... 原版即被注释 */ }
}
```

**本项目改动**：`core/src/com/phoenix/game/entities/type/base/FlyingUnit.java:134-154`
- 新增覆写 `update()`：`super.update(); wobble();`（原版 `update()` 里 wobble 就是最后一步）。
- 新增 `protected void wobble()`：逐行照抄；`net.client()` → `Vars.isClient()`；`Time.time()`/`Time.delta()` 用本项目方法。
- 原版被注释掉的 `rotation += ...` 一行原样保留为注释。

**照抄 / 偏离**：照抄。**刻意偏离**：原版 `update()` 里 wobble **之前**还有 `updateRotation()`，本项目未移植
（见类注释，接进去会覆盖所有飞行单位的 rotation）→ 只补 wobble（已在注释声明）。`Mathf.sin/cos(x,y,scl)`
本项目语义与 arc 一致（`sin(x/y)*scl`，见 `math/Mathf.java:188/208`）。

**遗留风险**：中低。`wobble()` 直接改 `x/y`（不碰撞、不入流场），与原版一致；服务端单机路径生效、客户端早退。
若日后引入"服务端权威位置每帧覆盖"，需确认 wobble 不会被同步回退（原版同样存在此行为）。

---

## G5-4 BasicBulletType 尺寸对齐（A5-偏离，低）

**原版**：`entities/bullet/BasicBulletType.java:13-14,37-44`
```java
public float bulletWidth = 5f, bulletHeight = 7f;
public float bulletShrink = 0.5f;
// draw:
float height = bulletHeight * ((1f - bulletShrink) + bulletShrink * b.fout());
Draw.rect(backRegion,  b.x, b.y, bulletWidth, height, b.rot() - 90);
Draw.rect(frontRegion, b.x, b.y, bulletWidth, height, b.rot() - 90);   // back/front 同尺寸
```

**本项目改动**
- `entities/bullet/BasicBulletType.java:16-19`：`height 8f → 7f`；**删除** `shrinkX`（原版宽度不收缩）。
  字段名沿用本项目既有的 `width/height/shrinkY`（对应原版 `bulletWidth/bulletHeight/bulletShrink`，注释已标）。
- `:60-72` `drawRegions`：back 不再放大（原 `w*1.1, h*1.3`）→ back/front 同尺寸 `(w, h)`。
- `:74-82` `draw`：改为原版公式 `h = height * ((1f - shrinkY) + shrinkY * b.fout())`，宽恒定 `width`。
- 连坐（先 grep 全工程）：`entities/bullet/ArtilleryBulletType.java:39-50` 原用 `shrinkX` → 改为原版
  `ArtilleryBulletType.draw` 写法（宽 `bulletWidth*scale` 不收缩、高按同公式 ×scale）。
- `content/Bullets.java:330-334` 更新过时注释（原注释描述"另有 shrinkX 缩宽 20%，未逐个改"）。

**照抄 / 偏离**：完全照抄原版公式与尺寸，无偏离。附带修正了一处**语义错配**：本项目旧公式
`height*(1-shrinkY*fin)` 与 arc 的 `bulletShrink` 语义相反（尤其 `shrinkY=1f` 会"从满缩到 0"，
原版是"从 0 涨到满"）→ 改回原版公式后，`Bullets.java` 里那些 `shrinkY = 1f/0f/0.5f` 的赋值现在 1:1 等于原版。

**遗留风险**：低。默认高 8→7、收缩曲线变化会让**所有**基础子弹略变小（这是对齐原版的预期结果）。
`content/UnitTypes.java` / `Mechs.java` 里设置 `width/height` 的地方都是尺寸语义，不受影响（已核对两处注释与数值）。

---

## G5-5 MassDriverBolt 补特效（A5-偏离，低）

**原版**：`entities/bullet/MassDriverBolt.java:19-21`
```java
despawnEffect = Fx.smeltsmoke;
hitEffect = Fx.hitBulletBig;
```

**本项目改动**：`entities/bullet/MassDriverBolt.java:26-28`，在构造器 `drag` 前补这两行。

**照抄 / 偏离**：完全照抄。`Fx.smeltsmoke`(`content/Fx.java:1027`) / `Fx.hitBulletBig`(`:241`) 均已存在。

**遗留风险**：无。

---

## G5-6 GroundUnit.draw 液体/溺水着色（A5-偏离，低）

**原版**：`entities/type/base/GroundUnit.java:133-152`
```java
Floor floor = getFloorOn();
if(floor.isLiquid){ Draw.color(Color.white, floor.color, 0.5f); }   // 腿部染色
for(...) Draw.rect(legRegion, ...);
if(floor.isLiquid){ Draw.color(Color.white, floor.color, drownTime * 0.4f); }  // 本体：随溺水加深
else { Draw.color(Color.white); }
```

**本项目改动**：`core/src/com/phoenix/game/entities/type/base/GroundUnit.java:138-160`
- 取 `Floor floor = getFloorOn();`（`BaseUnit.getFloorOn()` 已存在，tile 为空时返回 `Blocks.air`，永不 null）。
- 腿部绘制**之前**补 `if(floor.isLiquid) Draw.color(Color.WHITE, floor.color, 0.5f);`。
- 腿部绘制之后、本体绘制之前，把原来的 `Draw.color(Color.WHITE)` 换成原版的
  `if(isLiquid) color(WHITE, floor.color, drownTime*0.4f) else color(WHITE)`。
- 新增 import `com.phoenix.game.world.Floor`。

**照抄 / 偏离**：完全照抄。`Floor.isLiquid`(`world/Floor.java:59`)、`Floor.color`（继承 `Block.color`，
液体地板在 `Blocks.java` 里已按原版取色）均就绪；`drownTime` 是 `BaseUnit` protected 字段。

**遗留风险**：低。地面单位站在液体上时腿部/本体会按液体色淡染（原版行为）；不影响飞行单位（飞行走 `FlyingUnit.draw`）。

---

## G5-7 Battery.draw 电量色块（A4-M2，中低）

**原版**：`world/blocks/power/Battery.java:10-28`
```java
public int topRegion = reg("-top");
public Color emptyLightColor = Color.valueOf("f8c266");
public Color fullLightColor  = Color.valueOf("fb9567");
public void draw(Tile tile){
    Draw.color(emptyLightColor, fullLightColor, tile.entity.power.status);
    Fill.square(tile.drawx(), tile.drawy(), tilesize * size / 2f - 1);
    Draw.color();
    Draw.rect(reg(topRegion), tile.drawx(), tile.drawy());
}
```

**本项目改动**：`world/blocks/power/Battery.java`
- 新增字段 `topRegion = reg("-top")`、`emptyLightColor`、`fullLightColor`（原版同值）。
- 新增覆写 `draw(Tile)`：判空 `entity/power`；`Draw.color(empty, full, entity.power.status)`；
  `Fill.square(centerX, centerY, tilesize*size - 2f)`；`Draw.color()`；`reg(topRegion) != null` 时 `Draw.rect(top, ...)`。
- ⚠ 坐标口径：原版 `tile.drawx()/drawy()` 即中心 → 本项目用 `centerX/centerY`（角点约定，已在注释声明）。
- ⚠ BLEND：`Fill` 前 `glEnable(GL_BLEND)` + `glBlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)`（按协议）。
- ⚠ 语义换算：原版 arc `Fill.square(x,y,**radius**)`，本项目 `Fill.square(x,y,**边长**)`
  → `tilesize*size/2f - 1`（半径）等价于 `tilesize*size - 2f`（边长），已在注释声明。

**新增资产（额外发现）**：本项目 `core/assets-raw/sprites{,_out}/blocks/power/` **缺** `battery-top.png` /
`battery-large-top.png`（原版有），导致图集无 `battery-top` 区域 → 已从原版拷贝这两张 PNG 到
`sprites/blocks/power/` 与 `sprites_out/blocks/power/`（`reg("-top")` 找不到时 `reg(int)` 返回 null，绘制已判空兜底）。

**照抄 / 偏离**：数值/公式/颜色照抄。**刻意偏离（换算）**：坐标（角点→中心）、`Fill.square` 半径→边长、
BLEND 显式开启（均已注释声明）。

**遗留风险**：⚠ **必须重跑 `gradlew texturePacker`** 才能把 `battery-top`/`battery-large-top` 打进
`assets/sprites/sprites.atlas`（texturePacker 输入是 `sprites_out/`）。未重跑时 `-top` 层不显示（只显示色块，
不会崩）。色块在未重跑时仍可见（色块尺寸 6 单位 > 贴图透明窗口 2 单位，视觉上与重跑后基本一致）。

---

## G5-8 RepairPoint 旋转炮塔 + setStats(range)（A4-M7，低）

**原版**：`world/blocks/units/RepairPoint.java:54-57`（setStats）、`:76-85`（draw/drawLayer）、`:105-107`（generateIcons）
```java
stats.add(BlockStat.range, repairRadius / tilesize, StatUnit.blocks);
public void draw(Tile tile){ Draw.rect(baseRegion, tile.drawx(), tile.drawy()); }
public void drawLayer(Tile tile){ Draw.rect(region, tile.drawx(), tile.drawy(), tile.ent().rotation - 90); }
generateIcons() -> { name + "-base", name }
```

**本项目改动**：`world/blocks/units/RepairPoint.java:38-92`
- 新增字段 `baseRegion`；新增 `load()` 取 `name + "-base"`。
- 新增 `setStats()` 加 `BlockStat.range`（`repairRadius / tilesize`，`StatUnit.blocks`）。
- 新增 `generateIcons()` 返回 `{baseRegion, region}`（原版同形状；本项目 `generateIcons` 是 `protected`）。
- 新增 `draw(Tile)` 画底座 `baseRegion`（原默认实现会画炮塔贴图，故必须覆写）；
  新增 `drawLayer(Tile)` 按 `entity.rotation - 90f` 旋转画 `region`（炮塔）。
- ⚠ 坐标口径：`tile.drawx()/drawy()`（原版中心）→ `centerX/centerY`（本项目角点，已在注释声明）。
- ⚠ 图层：原版用 `Layer.turret`/`Layer.power`，本项目 `Block` 无 layer 字段；方块绘制路径固定为
  `draw(Tile)` → `drawLayer(Tile)`（`Renderer.drawTiles:892/899`），顺序天然是"底座在下、炮塔在上"，语义等价（已在注释声明）。
- 新增 import：`TextureRegion`、`Core`、`Draw`、`BlockStat`、`StatUnit`。

**照抄 / 偏离**：绘制内容/顺序照抄。**刻意偏离（换算）**：坐标口径 + 无 layer 字段（均已注释声明）。
**保持声明不补**：原版 `drawLayer2` 的激光连线**仍未移植**（类注释已声明，按任务要求保持）。

**遗留风险**：低。图集已有 `repair-point` 与 `repair-point-base` 两个区域（`assets/sprites/sprites.atlas:2051/2058`），
`baseRegion` 不会为 null。炮塔现在会随锁定目标转向（原版行为）。

---

## G5-9 MendProjector × efficiency() + isEnemyCheat（A4-D4，低）

**原版**：`world/blocks/defense/MendProjector.java:68`（heat 判据含作弊）、`:93`（治疗量 × efficiency）
```java
entity.heat = Mathf.lerpDelta(entity.heat, entity.cons.valid() || tile.isEnemyCheat() ? 1f : 0f, 0.08f);
other.entity.healBy(other.entity.maxHealth() * (healPercent + entity.phaseHeat * phaseBoost) / 100f * entity.efficiency());
```

**本项目改动**：`world/blocks/defense/MendProjector.java`
- `:170-171` `MendEntity.update()`：`cons.valid()` → `cons.valid() || tile.isEnemyCheat()`。
- `:207-208` `healArea(...)`：新增 `float efficiency = tile.entity == null ? 1f : tile.entity.efficiency();`
- `:228-229`：`healBy(maxHealth * percent)` → `healBy(maxHealth * percent * efficiency)`。

**照抄 / 偏离**：照抄。`Tile.isEnemyCheat()` 本项目**已存在**（`world/Tile.java:304`，`rules.enemyCheat && team == waveTeam`，
带 state/rules null 守卫）；`TileEntity.efficiency()` 已存在（`entities/type/TileEntity.java:109`，语义与原版一致：
非缓冲耗电返回 `power.status`，否则 1）。写法与 `OverdriveProjector`（本项目）用 efficiency 的方式一致。

**遗留风险**：低。`healArea` 的 efficiency 取的是**投影器自己**的（`tile.entity`），与原版 `entity.efficiency()` 同源。

---

## G5-10 shieldBuffer dispose（A6-B4，低）

**原版**：`core/Renderer.java:151-160`
```java
public void dispose(){
    minimap.dispose();
    shieldBuffer.dispose();
    blocks.dispose();
    ...
    Events.fire(new DisposeEvent());
}
```

**本项目改动**
- `core/src/com/phoenix/game/core/Renderer.java`：在 `disposeBloom()` 之后新增
  `public void dispose()`，`if(shieldBuffer != null){ shieldBuffer.dispose(); shieldBuffer = null; }`
  （写法参照同文件 `disposeBloom()`；判空是因为 `drawShields` 会在窗口尺寸变化时 dispose 重建它）。
- `ClientLauncher.java:2877-2878`：在 `renderer.disposeBloom();` 之后新增 `renderer.dispose();`，
  并同步修正下方那条"本项目 Renderer 没有 dispose()"的过时注释。
- ⚠ **未**重复添加 `DisposeEvent`（G3 已在 `ClientLauncher.dispose()` 里 fire，保持原样）。

**照抄 / 偏离**：照抄原版 `dispose` 中与 shieldBuffer 有关的部分。**刻意偏离（结构）**：本项目把 minimap/pixelator/
blocks/bloom 的释放留在 `ClientLauncher.dispose()`（既有安排），只把 shieldBuffer 收进 `Renderer.dispose()`（已在注释声明）。

**遗留风险**：低。`shieldBuffer` 是懒建的（首帧/护盾渲染时才建），判空已覆盖"从未建过"的情况。

---

## 额外发现（供后续批次参考）

1. **图集缺区域（已修数据，待重打）**：`battery-top` / `battery-large-top` 两个区域此前**从未**进入项目图集
   （源目录与 `sprites_out/` 都没有这两张 PNG）。已补 PNG，但**必须重跑 `gradlew texturePacker`** 才会生效。
   —— 与 memory「新贴图必须同时拷进 `sprites_out/`，否则图集缺区域且静默不报错」一致，本次是"源目录也缺"。
2. **`Fill.square` 语义系统性错配（未在本批修）**：本项目 `Fill.square(x,y,size)` 第三参是**边长**，
   而 arc 是**半径**；`content/Fx.java` 里大量 `Fill.square` 调用是从原版**逐字拷贝**的（如 `:107/:632/:982`…），
   因此这些特效方块实际只有原版的一半大小。本批只在 Battery（新代码）里做了换算并注释，
   **未改动** Fx 的既有调用（避免超出 G5 范围、且无法实机逐帧比对）。建议后续单独立项核对。
3. **`BasicBulletType` 旧公式与 `bulletShrink` 语义相反**：G5-4 已修（见上），顺带让 `Bullets.java` 里
   那些 `shrinkY = 1f/0f/0.5f` 的赋值重新与原版 1:1 对应。
4. **FlyingUnit 类注释更新**：原注释称"本项目没有 update/draw 钩子"，G5-2/3 已补 `draw()`/`update()`（仅引擎+wobble），
   注释已同步（`updateRotation()`/`drawWeapons()` 仍未移植，保持声明）。

---

## 改动文件清单

```
core/src/com/phoenix/game/type/Weapon.java
core/src/com/phoenix/game/entities/type/base/FlyingUnit.java
core/src/com/phoenix/game/entities/type/base/GroundUnit.java
core/src/com/phoenix/game/entities/bullet/BasicBulletType.java
core/src/com/phoenix/game/entities/bullet/ArtilleryBulletType.java
core/src/com/phoenix/game/entities/bullet/MassDriverBolt.java
core/src/com/phoenix/game/content/Bullets.java              (仅注释)
core/src/com/phoenix/game/world/blocks/power/Battery.java
core/src/com/phoenix/game/world/blocks/units/RepairPoint.java
core/src/com/phoenix/game/world/blocks/defense/MendProjector.java
core/src/com/phoenix/game/core/Renderer.java
core/src/com/phoenix/game/ClientLauncher.java
core/assets-raw/sprites/blocks/power/battery-top.png        (新增)
core/assets-raw/sprites/blocks/power/battery-large-top.png  (新增)
core/assets-raw/sprites_out/blocks/power/battery-top.png        (新增)
core/assets-raw/sprites_out/blocks/power/battery-large-top.png  (新增)
```

## 需主会话执行的后续动作

1. `gradlew :core:compileJava`（编译验证；本批未编译）。
2. **`gradlew texturePacker`**（G5-7 新增的电池顶盖贴图必须重打图集才生效）。
