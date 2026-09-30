# A6 审计：UI / 输入 / 图形（第二轮静默缺口普查）

> 审计范围：本项目 `com/phoenix/game/{ui(含 fragments,dialogs,layout), input, graphics}` 与 `core/` 的图形部分
> （`Renderer`/`Draw`/`Drawf`/`Fill`/`Lines`/`Fonts`）↔ 原版 `mindustry/{ui, input, graphics, core/Renderer}`（只读参照 `D:\IdeaProjects\Mindustry-126.2`）。
> 判据：原版**有** + 本项目**没有/行为不同** + **无注释声明刻意偏离**，三者同时满足才计入。
> 已读前置：`2026-09-29-full-port-completion.md` 批次登记表、`2026-09-30-round2-gap-audit.md`、
> `audit-A3-ui-input-graphics.md`、`todo-classified.md`、`f13/f14/f15/f18-report.md`、`f-low-report.md`、`r1-report.md`、
> 本轮并行产出的 `audit-A4-world-content.md` / `audit-A5-entities-ai.md`。A3/F 批已登记项一律 cross-ref、不重报。
>
> **文件级结论**：ui（18+18 dialogs、13+13 fragments、4 layout）、input（6 对 + 自研 KeyBinds/KeyCode/InputDevice）、
> graphics（缺 Drawf/FloorRenderer/IndexedRenderer/MultiPacker/OverlayRenderer，均已在类注释声明：Drawf 挪 `core/Drawf`、
> 后三者合并进自研 `core/Renderer`、MultiPacker 是 arc 打包设施）、core 图形类 —— **无未声明缺文件**。
> 逐文件行数比（phx/orig）扫描：除 `ui/Cicon.java`（0.52，A3 已记 all/scaled 缺失）外，**无任何 ui/graphics/core/input 文件明显欠移植**。
>
> **本轮新增**：缺失 3 / 偏离 3 / 断裂 4（另有 1 条"声明存疑"，不计入统计）。

## 摘要：缺失 3 / 偏离 3 / 断裂 4

最严重的三条：
1. **effects 设置项无消费者（断裂，中）**：`SettingsMenuDialog` 的 effects 复选框（原版 `Renderer.java:58` 是唯一消费点，
   `if(Core.settings.getBool("effects"))` 守卫特效实体生成）在本项目**全工程 grep 零匹配** → 关掉"特效"画面不变（也是性能选项）。
2. **minimap 设置项无消费者（断裂，中）**：原版 `HudFragment.java:257` `t.visible(() -> Core.settings.getBool("minimap") && !state.rules.tutorial)`
   在本项目缺失，HUD 右上角小地图恒显示 → 设置里关"小地图"无反应，教程局也不隐藏小地图。
3. **桌面端右键不再进入拆除模式（偏离，中）**：原版右键 = `Binding.break_block`（非建造态进入拆除、可拖拽连拆），
   本项目 `Binding.break_block` **零消费**、右键恒走 `Binding.deselect`→`clearBuild()`（只取消建造），拆除改由建造栏"拆除"按钮进入。

---

## 一、缺失（原版有、本项目完全没有、无声明）

| # | 原版位置 | 项 | 本项目现状 | 严重度 | 修复建议 |
| --- | --- | --- | --- | --- | --- |
| M1 | `ui/fragments/PlacementFragment.java:211-213` | 建造菜单里 **SHIFT + 点击方块按钮** → 把该方块名对应的字体图标 unicode 复制到剪贴板并弹 `$copied`（`Fonts.getUnicode(block.name)`） | 本项目 `PlacementFragment` 无 `getUnicode`/剪贴板分支（F18 只给 `DatabaseDialog` 补了同款复制彩蛋） | 低 | 在方块按钮点击回调里补 `if(keyDown(SHIFT) && Fonts.getUnicode(block.name)!=0){ Gdx.app.setClipboardText((char)...); ui.showInfoFade("$copied"); }` |
| M2 | `ui/fragments/ChatFragment.java:79` | `scrollPos = clamp(scrollPos + input.axis(Binding.chat_scroll), 0, max(0, messages.size - messagesShown))`：滚轮翻看聊天历史 | 本项目 `ChatFragment` 无 `scrollPos`，消息列表不响应滚轮；`Binding.chat_scroll` 无实际消费 | 低 | 在 `ChatFragment.update()` 里读滚轮增量改 `scrollPos`，只渲染 `[scrollPos, scrollPos+messagesShown)` 区间 |
| M3 | `ui/fragments/HudFragment.java:387-394`（消费点 `core/NetClient.java:267-283`） | `setHudText(String)` / `toggleHudText(boolean)` + 顶部 `hudText` 覆盖层（服务器下发的 HUD 文本消息） | 本项目 `HudFragment` 无这两个方法；`NetClient` 侧亦无 `setHudText/hideHudText`（属 net 自研简化，A7 范围，此处 cross-ref） | 低 | 若后续接 net 文本包，补 `setHudText/toggleHudText` + 顶部黑底 label 覆盖层（原版 `HudFragment.java:347-358`） |

---

## 二、偏离（两边都有、行为/语义不同、无声明）

| # | 原版位置 | 项 | 本项目位置 | 证据 | 严重度 | 修复建议 |
| --- | --- | --- | --- | --- | --- | --- |
| D1 | `core/Renderer.java:124-135` | `update()` 里 `if(player.isDead())`：相机 lerp 跟随 `player.getClosestCore()`（死亡观战视角） | `core/Renderer.java:150-156` | 只写 `!Vars.player.isDead()` 分支；玩家死亡时相机不再跟随最近核心（PvP/attack 图死亡后视角停在原地） | 中 | 补死亡分支：`core = player.getClosestCore(); if(core!=null) camera.position.lerp(core.x, core.y, 0.08f)` |
| D2 | `input/DesktopInput.java:360,390,416` | 右键（`Binding.break_block`）非建造态进入拆除模式、按住拖拽连拆 | `input/DesktopInput.java:319,334`（`input/Binding.java:15`） | 本项目右键恒比对 `Binding.deselect`→`clearBuild()`；`Binding.break_block` 全工程 0 消费；拆除改由 `PlacementFragment.java:283-291` 的"拆除"按钮 `input.breaking = true` 进入 | 中 | 按原版在 `touchDown` 里补 `break_block` 分支：`!isPlacing()` 时进入 `breaking` 并记起点（`deselect` 只在 isPlacing 时取消） |
| D3 | `ui/fragments/HudFragment.java:673-688` | 跳波按钮受 `canSkipWave()` 门控：`waves && (server || admin || !net.active()) && enemies==0 && !spawner.isSpawning() && !tutorial`，且客户端走 `Call.onAdminRequest(AdminAction.wave)` | `ui/fragments/HudFragment.java:105-115` | "下一波"按钮只判 `isPlaying() && rules.waves` 就把 `wavetime=0`，**无敌人/正在刷怪/教程/权限门控** → 波次进行中也能跳过 | 低 | 补 `canSkipWave()` 判据；教程局/有敌人时禁用按钮 |

---

## 三、断裂（本项目已有实现/设置项，但无消费点）

| # | 原版位置 | 项 | 本项目位置 | 证据 | 严重度 | 修复建议 |
| --- | --- | --- | --- | --- | --- | --- |
| B1 | `core/Renderer.java:58` | `if(Core.settings.getBool("effects"))` 守卫特效实体生成（关"特效"即不产生 EffectEntity） | `ui/dialogs/SettingsMenuDialog.java:259`（设置项）；`core/Renderer.java`（无消费） | 全工程 grep getBool("effects") / "effects" = **仅 SettingsMenuDialog 一处**；`Effects.effect(...)` 无开关 → 关"特效"画面不变 | 中 | 在特效生成入口（`Renderer` 构造的 effect provider 或 `Effects.effect`）加 `if(!Core.settings.getBool("effects")) return;` |
| B2 | `ui/fragments/HudFragment.java:257` | `t.visible(() -> Core.settings.getBool("minimap") && !state.rules.tutorial)` | `ui/dialogs/SettingsMenuDialog.java:265`（设置项）；`ui/fragments/HudFragment.java:193-201`（无可见性门控） | 全工程 grep getBool("minimap") = 0（`MinimapFragment.java:57` 的 `bundle.get("minimap")` 是标题文案，非设置）；小地图表恒显示 | 中 | 给 `minimapTable` 挂每帧 `setVisible(settings.getBool("minimap") && !rules.tutorial)`（与 `positionLabel` 同模式） |
| B3 | `input/InputHandler.java:547-554` | `updateLine` 末尾 `if(Core.settings.getBool("blockreplace"))` 对每个 `BuildRequest` 调 `block.getReplacement(req, lineRequests)`（拖拽时把传送带/导管在拐角自动换成 armored-conveyor/junction） | `ui/dialogs/SettingsMenuDialog.java:179`（设置项）；`input/InputHandler.java:666-697`（`updateLine`，无该段） | 全工程 grep blockreplace/getReplacement：`blockreplace` 仅设置项；`getReplacement` 仅 `Conduit.java:27` 的"未移植"注释；`Conveyor` 无覆写（cross-ref A4：`world/blocks/distribution/Conveyor.java` 缺 `getReplacement`） | 中 | 补 `Block.getReplacement` 默认返回自身 + `Conveyor`/`Conduit` 覆写；在 `updateLine` 末尾按设置应用（与 A4 的 M/D 项合并修） |
| B4 | `core/Renderer.java:151-160` | `dispose()` 释放 `shieldBuffer`（并 fire `DisposeEvent`） | `core/Renderer.java:110`（`private FrameBuffer shieldBuffer`）；`ClientLauncher.java:2850-2865`（dispose 链） | `ClientLauncher.dispose()` 释放了 minimap/pixelator/blocks/bloom/menufrag，**漏了 Renderer 的 `shieldBuffer`** → 进程退出时该 FBO 泄漏（`Renderer` 无 dispose 方法） | 低 | 给 `Renderer` 加 `dispose()` 释放 `shieldBuffer`（或让 `ClientLauncher.dispose()` 直接调），并考虑补 `DisposeEvent` |

---

## 四、声明存疑（不计入统计，建议主会话复核）

| # | 原版位置 | 项 | 本项目现状 | 已有声明 | 严重度 |
| --- | --- | --- | --- | --- | --- |
| S1 | `ui/fragments/HudFragment.java:529-688`（`showLaunch`/`showLaunchConfirm`/`inLaunchWave`/`canLaunch`/`addWaveTable` 发射分支/`addPlayButton`）+ `core/Logic.java:163,173-200`（`launchZone` → `state.launched=true` + `zone.setLaunched()`） | 战役"发射到下一区域"整链：波次面板显示发射倒计时/条件、点击弹确认、`Call.launchZone` 推进战役进度 | `type/Zone.java:106`（`isLaunchWave`，仅被 `Logic.java:177` 用于波次间隔倍率）、`:132-141`（`hasLaunched`/`setLaunched`，**`setLaunched` 无调用者**）、`core/GameState.java:30`（`state.launched`，仅被置 false）、`game/Objectives.java:79-97`（`Launched` 目标读 `zone.hasLaunched()`）；无 HUD 发射按钮、无 `Logic.launchZone` | `ui/dialogs/GameOverDialog.java:28` 写"本项目还没有发射流程 → 恒 false"；`core/Logic.java:237-238` 写"区域发射/存档/解锁链未移植"（其中"存档/解锁链"已由 F 批补齐，该句已过时） | 高（玩法：`Launched` 目标永不达成、区域无法推进；但已有"状态陈述"式声明，故列此节请主会话判定是否算刻意偏离） |

---

## 附：本轮核对后**判定为非缺口**的项（避免后续重报）

- **`ui/Cicon.all/scaled`、`graphics/Pal.plastanium`、`graphics/Shaders` 缺 shadow/blockbuild/menu/water/tar、bloom no-op、MenuRenderer 双 FBO**：A3 已登记（已声明）。
- **`FlyingUnit.drawEngine()` / `type.engineOffset/engineSize` 无消费**：**A5 已报**（`audit-A5-entities-ai.md` 断裂，低），本报告不重复；其消费侧 `Renderer.drawUnits`（`core/Renderer.java:1073-1092`）确实未调 `drawUnder`（原版 `Renderer.drawAllTeams` 会调），随 A5 那条一并修即可。
- **`Block` 配置钩子 `shouldShowConfigure`/`shouldHideConfigure`**：原版 126.2 里除基类外**无任何覆写**（默认恒 true/false），缺失等价 no-op，不计。
- **`Block.updateTableAlign`/`onConfigureTileTapped`**：本项目配置面板改为"固定右上角常驻面板"（`BlockConfigFragment.java:29` 已声明），连线用途 A2/A4 已声明为等价重写，不计。
- **`HudFragment.showToast(String)`**：`core/Control.java:166` 已声明降级为 `UI.showInfoFade`，不计。
- **`LoadingFragment` 缺 `Bar.reset/set`**：`LoadingFragment.java:34-35,91` 已声明（进度由 Supplier 每帧读取，等价），不计。
- **`MenuFragment.checkPlay`（mod 内容错误守卫）**：`MenuFragment.java:151,255-256` 已声明"本项目 mods 无内容校验"，不计。
- **`OverlayFragment` 无调用方、`FadeInFragment` 未 build、`UI.showExceptions` 死方法**：A3 已登记（已声明/死代码）。
- **`Binding.schematic_flip_x/y`、`category_prev/next`、`block_select_left/right/up/down` 零消费**：A3 已登记（蓝图翻转/分类键/方向键部分，均有 TODO 声明）。
- **`SettingsMenuDialog.sfxvol` 无消费**：`SettingsMenuDialog.java:156` 已声明，且原版核心代码同样只注册不读取（死设置），不计。
- **`vsync`/`fullscreen`/`borderlesswindow` 在设置项外零引用**：均在 `checkPref` 回调里生效（`Core.graphics.setVSync/setFullscreenMode/setUndecorated`），非断裂。
- **`ChatFragment` 结构偏离（Fragment+Label vs Table+font cache）、消息上限 8 vs 10、`chat_history_prev/next` 轮询方式**：`ChatFragment.java:25-27,100-102` 已声明，不计。
- **`MinimapFragment` 缺捏合缩放（`ElementGestureListener.zoom`）**：桌面端无捏合手势，滚轮缩放已实现，移动端边角，不计。
- **`Fonts.loadFonts/loadDefaultFont/mergeFontAtlas/getGlyph` 等**：字体加载搬到 `ClientLauncher`（`Fonts.java:33-37` 已声明 icon 字体半边刻意不移植），不计。
- **`Renderer` 无 `init/resize/resume` 钩子**：bloom 生命周期改由 `Renderer.draw` 首帧懒做 + `ClientLauncher` 生命周期等价承担（`Renderer.java:122,328-332` 已声明），不计。
- **`DesktopInput`/`InputHandler` 的 `getMouseX/getMouseY`/`updateState`/`isPlacing`/`useSchematic` 等"缺失方法名"**：已就地改名等价实现（`mouseX()`/`isButtonPressed(Binding)`/`pasteSchematic` 等），不计。

## 备注 / 未完全核实的项

- `ui/dialogs/` 里 `SaveDialog`/`LoadDialog`/`MapsDialog`/`MapPlayDialog`/`CustomGameDialog`/`ZoneInfoDialog`/`LoadoutDialog`/`DatabaseDialog`/`AdminsDialog`/`BansDialog`/`TraceDialog`/`LanguageDialog`/`CustomRulesDialog` 未逐方法级展开；行数比均 >=1.4（本项目含大量声明注释），抽查未见未声明的高优先级缺口，但细节未逐条核对。
- `graphics/{BlockRenderer,LightRenderer,MinimapRenderer,Pixelator}` 与自研 `Renderer.draw()` 的帧序未逐帧比对原版 `blocks.drawBlocks(Layer.*)` 完整顺序（A3 已注明同一限制）。
- `ui/` 各 `*Display`（`ItemDisplay`/`ItemsDisplay`/`LiquidDisplay`/`ReqImage`/`MultiReqImage`/`GridImage`/`Links`/`BorderImage`）方法级未见缺失（行数比 1.19~1.95），未逐条展开。
