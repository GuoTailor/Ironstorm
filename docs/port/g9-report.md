# G9 批次报告（全量移植收尾·第二轮）

范围：G9-1 建造菜单 SHIFT+点击复制图标 / G9-2 聊天滚轮翻历史 / G9-3 UnitCreateEvent 派发 /
G9-4 DeflectorWall 补 resetOwner / G9-5 复核 M3·M5·M6·D8。

约定：原版 = `D:\IdeaProjects\Mindustry-126.2`；本项目 = `d:\IdeaProjects\phoenix`。
本批**未运行任何 gradle/git**，编译由主会话负责。未触碰用户并行改动文件（Player.java / InputHandler.java / SaveIO.java / ClientLauncher `//DEBUG-MINE-START`）。

---

## G9-1 建造菜单 SHIFT+点击复制方块图标（A6-M1）

**原版位置**：`core/src/mindustry/ui/fragments/PlacementFragment.java:209-219`
（`blockTable.addImageButton(...)` 的点击回调内）：

```java
if(unlocked(block)){
    if(Core.input.keyDown(KeyCode.SHIFT_LEFT) && Fonts.getUnicode(block.name) != 0){
        Core.app.setClipboardText((char)Fonts.getUnicode(block.name) + "");
        ui.showInfoFade("$copied");
    }else{
        control.input.block = control.input.block == block ? null : block;
        selectedBlocks.put(currentCategory, control.input.block);
    }
}
```

**先例**：F18 批已给 `ui/dialogs/DatabaseDialog.java:149-170` 补过同款彩蛋（`Fonts.getUnicode` +
`Gdx.app.getClipboard().setContents(...)` + `Vars.ui.showInfoFade("$copied")`），本项照抄其 API 等价风格。

**本项目改动**：`core/src/com/phoenix/game/ui/fragments/PlacementFragment.java`
- 新增 import：`com.phoenix.game.core.Fonts`、`com.phoenix.game.input.KeyCode`。
- `rebuildGrid()` 里方块按钮的 `ClickListener.clicked()`（原 420-429 行，现 422-440 行）**开头**插入：

```java
if(Gdx.input.isKeyPressed(KeyCode.SHIFT_LEFT) && Fonts.getUnicode(block.name) != 0){
    Gdx.app.getClipboard().setContents((char)Fonts.getUnicode(block.name) + "");
    if(Vars.ui != null) Vars.ui.showInfoFade("$copied");
    return;
}
```

**照抄/偏离**：
- 判据/流程照原版（SHIFT 按住 + `getUnicode != 0` → 复制字符 → 提示 `$copied`）。
- API 等价替换（与原版不同处已写注释）：`Core.input.keyDown(KeyCode.SHIFT_LEFT)` → `Gdx.input.isKeyPressed(KeyCode.SHIFT_LEFT)`（`KeyCode.SHIFT_LEFT == Input.Keys.SHIFT_LEFT`）；`Core.app.setClipboardText` → `Gdx.app.getClipboard().setContents`。
- **i18n**：`copied` 键**存在**（`assets/i18n/bundle.properties:1133`、`bundle_zh_CN.properties:1087 = 已复制。`）→ 无需兜底、未改 bundle。
- 刻意保留：原版外层还有 `unlocked(block)` 守卫，本项目该回调本来就没有解锁判定（解锁过滤在 `buildable()`/`isVisible()` 处），故只在回调开头加 SHIFT 分支，未引入 unlocked 判断（避免扩大改动面）。
- 分支 `return`，不影响非 SHIFT 的原有选中逻辑。

**遗留风险**：低。`Fonts.loadContentIcons()` 依赖 `icons/icons.properties` 存在；文件缺失时 `getUnicode` 恒返回 0 → SHIFT 点击退化为普通选中（与原版"无图标即不复制"一致）。

---

## G9-2 聊天滚轮翻历史（A6-M2）

**原版位置**：`core/src/mindustry/ui/fragments/ChatFragment.java:79`（`update()` 内）：

```java
if(shown){
    ...
    scrollPos = (int)Mathf.clamp(scrollPos + input.axis(Binding.chat_scroll), 0,
                                 Math.max(0, messages.size - messagesShown));
}
```

渲染在 `draw()`（:141）只画窗口 `[scrollPos, scrollPos+messagesShown)`；`addMessage`（:235）有
`if(scrollPos > 0) scrollPos++;`；`toggle()` 收起时 `scrollPos = 0`。

**本项目改动**：`core/src/com/phoenix/game/ui/fragments/ChatFragment.java`
- 新增 import `com.phoenix.game.math.Mathf`。
- 删除 `maxMessages = 8` 的裁剪（原版不裁剪）；新增字段 `private int scrollPos = 0;` 与
  `private final IntArray indices`（记录每个 label 对应的原版索引 i）。类注释同步更新。
- `update()` 的 `if(open){...}` 内补滚轮读取（现 128-145 行）：
  `float amount = ih.scroll(); ... scrollPos = (int)Mathf.clamp(scrollPos + amount, 0, Math.max(0, messages.size() - messagesShown));`
- `close()` 收起时 `scrollPos = 0` + `rebuildList()`（对应原版 `toggle()`）。
- `addMessage()` 补 `if(scrollPos > 0) scrollPos++;`（对应原版 :235）。
- `rebuildList()` 只渲染窗口 `[scrollPos, scrollPos+messagesShown)`（`i = size-1-位置`，越窗 `continue`）。
- `refresh()` 改用 `indices` 里的 i，并补上原版循环条件 `(i < fadetime || shown)` 的"不绘制"分支（alpha=0）——
  原先代码缺这个 gate，导致非 open 时旧消息会以 `alpha=opacity` 全亮显示（窗口化后必须补，否则滚回的历史也会亮）。

**照抄/偏离**：
- 判据/公式/窗口范围/`addMessage` 与收起复位均照原版。
- **等价替换（已注释）**：`input.axis(Binding.chat_scroll)` → `InputHandler.scroll()`。
  原版 `axis()` 读一帧累积的滚轮轴；本项目滚轮由 `InputHandler.scrolled()` 累积，
  `scroll()` 读取并清零。`ChatFragment.update()`（ClientLauncher:550）在 `input.update()`（:562）**之前**执行，
  因此这里消费掉后 `DesktopInput` 看到 0 → **聊天打开时不缩放相机**，正对应原版 `DesktopInput.java:161` 的
  `!ui.chatfrag.shown()` 守卫。
- **刻意偏离**：原版消息列表不裁剪（本项目原先裁到 8 条）。裁到 8 会让 `scrollPos` 永远夹在 0（`8 <= messagesShown(10)`）→ 滚轮翻历史失效，故改回原版语义（只保留收到的全部消息）。Actor 数量仍受窗口限制，不会随消息数无界增长。
- **未破坏 G8**：`handleCommand` 的"未知 `/` 命令转发服务端"逻辑未触碰。

**遗留风险**：
- 若鼠标正悬停在某个 `ScrollPane` 上，`stage` 会先吃掉滚轮事件 → `scrollAmount` 保持 0，聊天不滚（可接受，原版 `hasScroll` 亦有同类交互）。
- 消息列表改为不裁剪，长会话下 `messages` 无界增长（同原版；只存字符串，重建列表 O(n)，可接受）。
- 滚轮消费发生在放置可旋转方块时也会拦下（原版 `rotate` 分支未按 `chatfrag.shown()` 设门）——仅"聊天框打开且正在放置可旋转方块"这种罕见组合下朝向不转，属可忽略偏差。

---

## G9-3 UnitCreateEvent 派发（A4-B3）

**原版位置**：`core/src/mindustry/world/blocks/units/UnitFactory.java:60-67`
（`onUnitFactorySpawn`）：`unit.add(); unit.velocity().y = ...; Events.fire(new UnitCreateEvent(unit));`。

**本项目改动**：`core/src/com/phoenix/game/world/blocks/units/UnitFactory.java`
- 新增 import `com.phoenix.game.core.Events`、`com.phoenix.game.game.EventType`。
- `UnitFactoryEntity.spawnUnit()` 生成单位之后（现 310-313 行）补：

```java
Events.fire(new EventType.UnitCreateEvent(unit));
```

**照抄/偏离**：位置与构造参数照原版。原版在 `unit.add()` 之后 fire；本项目 `UnitType.create()`
内部（`BaseUnit.init()`）已 `Units.add(this)`，故在 create 并设好 team/spawner/位置/血量之后 fire 等价。

**遗留风险**：低。原版 126.2 亦**无消费者**（`Events.fire` 后无 listener），本项仅对齐派发点。

---

## G9-4 DeflectorWall 补 resetOwner（G2 遗留）

**原版位置**：`core/src/mindustry/world/blocks/defense/DeflectorWall.java:44-74`（`handleBulletHit` 全文）。
逐行核对：`super.handleBulletHit` → `bullet.damage() > maxDamageDeflect || bullet.isDeflected()` 早退 →
`penX/penY` → `bullet.hitbox(rect2)` → `Geometry.raycastRect(...)` 修正位置 → `penX>penY` 翻转速度 →
`bullet.resetOwner(entity, entity.getTeam())` → `bullet.scaleTime(1f)` → `bullet.deflect()` → `hit = 1f`。

**本项目改动**：
1. `core/src/com/phoenix/game/entities/type/Bullet.java:144` — `resetOwner` 由单参 `(Entity)`
   改为原版的双参 `(Entity entity, Team team)`（`team` 一起换）。全工程仅此一处定义、无其他调用点（已 grep 确认）。
2. `core/src/com/phoenix/game/math/geom/Geometry.java` — 新增 `raycastRect` 两个重载，
   逐字照抄 arc `Geometry.raycastRect`（`Arc/arc-core/.../Geometry.java:255-292`），用共享 `tmp1` 返回。
3. `core/src/com/phoenix/game/world/blocks/defense/DeflectorWall.java` — 新增 `rect`/`rect2` 字段；
   `handleBulletHit` 补 `super.handleBulletHit`、位置修正（`Geometry.raycastRect` + `bullet.set`）、
   `bullet.resetOwner(entity, entity.getTeam())`、`bullet.scaleTime(1f)`；类注释删掉"简化"声明。
4. `core/src/com/phoenix/game/entities/type/BaseUnit.java:987-994` — 订正 `onHit` 注释：
   原写"`getOwner() instanceof DeflectorEntity` 当前恒为 false、本分支不会触发"已过时，
   改为"G9 起已补 resetOwner，分支可触发"。

**照抄/偏离**：
- 流程/字段名/调用顺序照原版。
- 等价替换：arc `Rect` → libgdx `Rectangle`；`Vec2` → `Vector2`；`bullet.hitbox(Rectangle)` 本项目已有同签名。
- 保留原版之外的 `bullet == null` 空判（本项目原有防御，未删）。
- `resetOwner` 签名对齐原版（原来只有 1 参、只设 owner）。

**遗留风险**：
- 偏转后子弹 `team` 变为反射墙队伍；本项目碰撞体系按 `team.isEnemy(...)` 过滤（`BaseUnit.java:904` 等），
  语义与原版一致。`deflect()` 置 `supressCollision` 一帧、`update()` 内 `supressOnce` 复位，与原版同。
- `Geometry.raycastRect` 返回共享 `tmp1`，DeflectorWall 内立即取用，无跨帧持有。

---

## G9-5 复核结论

### M3 `HudFragment.setHudText/toggleHudText`（A6-M3）→ **跳过**

**依据**：本项目 grep `setHudText|toggleHudText|hudText` **零命中** —— `HudFragment` 无 HUD 文本字段、
`NetClient` 无对应接收方法。原版该链路是 `NetClient.java:267-281` 的 `setHudText/setHudTextReliable`
（由服务端 HUD 文本网络包触发）+ `HudFragment.java:387-392`。
本项目网络层（`NetClient`/`NetServer`）**没有该包**，硬接需自造协议 → **跳过并注明**（不硬造协议）。

### M5 `ItemModule.forEach/hasOne/remove(ItemStack)`（A4-M5）→ **跳过**

**依据**：
- `forEach`：原版唯一疑似消费点是 `PlacementFragment.java:225/229` 的 `button.forEach(...)`，那是 arc `Element.forEach`，**与 `ItemModule.forEach` 无关** → 死方法。
- `hasOne`：原版全仓 grep 仅命中定义处，**无消费者**。
- `remove(ItemStack)`：原版消费点是 `ConsumeItems.java:58` 的 `entity.items.remove(stack)`；本项目 `ConsumeItems.java:41` 用 `remove(stack.item, stack.amount)`，**已有等价实现**。
本项目 `ItemModule` 未实现这三个方法（grep 零命中），无消费者 → **跳过并注明**。

### M6 `ItemStack.copy/equals/mult/list/compareTo`（A4-M6）→ **跳过**

**依据**：本项目 `ItemStack.java` 仅 `item/amount/构造器/with/toString`（grep 零命中这 5 个方法），无消费者。
- `mult`：原版消费点是 `Blocks.java:745-813` 的"大型墙材料 ×4"；本项目 `Blocks.java` 直接把数值写死（等价），无需 `mult`。
- `list`：原版消费点是 `Zones.java:218/244`；本项目 `Zones.java` 改用 `Array.with(new ItemStack(...))`（`Zones.java:24-25` 已注明降级），另两处 `ItemStack.list(...)`（:237/:263）在**注释块内**、未启用。
- `copy/equals/compareTo`：本项目无消费点。
→ **跳过并注明**。

### D8 `DefaultWaves` 复核（A7-偏离8）→ **已补齐到原版**

**前置确认**：grep 确认 `UnitTypes.revenant`（`UnitTypes.java:345`）与 `UnitTypes.lich`（:377）**均已存在**，
且 `SpawnGroup` 已支持 `items`/`effect`（`SpawnGroup.java:29/31`）、`Items.blastCompound/pyratite` 与
`StatusEffects.overdrive/shielded` 均存在 → 满足"低成本补齐"条件。

**改动**：`core/src/com/phoenix/game/game/DefaultWaves.java` 整体照原版 `game/DefaultWaves.java:12-187`
逐组重写，共 **24 组**（任务描述写"20 组"，实际原版为 24 组）：dagger×5、titan×4、crawler、wraith×3、
fortress、ghoul×3、eruptor、revenant、chaosArray、eradicator、lich，含 `items`（blastCompound 60 / pyratite 100）
与 `effect`（overdrive/shielded）两组。原来自建的 10 组（含 flare）按原版删除（原版无 flare 编成）。

**偏离**：仅保留本项目既有的 `private static Array<SpawnGroup> spawns`（原版为实例字段），未改字段静态性以免牵连调用方；
分组内容与原版逐字一致。

**遗留风险**：低。`UnitTypes.revenant/lich` 为 `HoverUnit`（重型悬浮），其 AI/贴图已在 F1e2 补齐；
波次生成路径 `SpawnGroup.createUnit` 已支持 effect/items。

---

## 改动文件清单

| 文件 | 项 |
| --- | --- |
| `core/src/com/phoenix/game/ui/fragments/PlacementFragment.java` | G9-1 |
| `core/src/com/phoenix/game/ui/fragments/ChatFragment.java` | G9-2 |
| `core/src/com/phoenix/game/world/blocks/units/UnitFactory.java` | G9-3 |
| `core/src/com/phoenix/game/entities/type/Bullet.java` | G9-4 |
| `core/src/com/phoenix/game/math/geom/Geometry.java` | G9-4 |
| `core/src/com/phoenix/game/world/blocks/defense/DeflectorWall.java` | G9-4 |
| `core/src/com/phoenix/game/entities/type/BaseUnit.java` | G9-4（注释订正） |
| `core/src/com/phoenix/game/game/DefaultWaves.java` | G9-5 / D8 |

## 额外发现

1. **`UnitFactory.spawnUnit` 未显式 `unit.add()`**：本项目 `UnitType.create()` → `BaseUnit.init()` 内部已
   `Units.add(this)`，故功能正常（与波次路径的"延迟 add"不同）。G9-3 的 fire 已按此前提放置，无需补 add。
2. **ChatFragment 消息原先裁到 8 条**（`maxMessages=8`）小于 `messagesShown=10`，导致原版滚轮窗口恒为 0 ——
   本批 G9-2 一并修正（改回原版不裁剪语义）。
3. **ChatFragment `refresh()` 原先缺少原版 `(i < fadetime || shown)` 的"不绘制"gate**，非 open 时旧消息会全亮；
   窗口化后必须补（G9-2 已补），否则滚回的历史消息也会常亮。
