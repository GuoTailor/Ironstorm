package com.phoenix.game.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.GameState;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Time;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.Build;
import com.phoenix.game.world.Tile;
import io.anuke.mindustry.gen.Sounds;

/**
 * 最小实现：桌面端输入。参照 Mindustry mindustry.input.DesktopInput 移植。
 * 相机平移/缩放/全屏/暂停 + 建造栏放置 + 玩家单位控制（WASD 移动、鼠标瞄准、左键射击）。
 */
public class DesktopInput extends InputHandler{
    /** 默认窗口尺寸（与 Mindustry 桌面端一致） */
    public static final int defaultWidth = 900, defaultHeight = 700;

    /** 拖动拆除的上一格（未开始拖动时为 Integer.MIN_VALUE）。 */
    private int lastBreakX = Integer.MIN_VALUE, lastBreakY = Integer.MIN_VALUE;

    @Override
    public void update(){
        if(Vars.state.isMenu()) return;

        Player player = Vars.player;

        //M 键开关全屏小地图（对应原版 DesktopInput 的 Binding.minimap -> ui.minimapfrag.toggle()）
        if(Vars.hud != null && keyTap(Binding.minimap)){
            Vars.hud.minimapFragment().toggle();
        }

        //T 键开关蓝图库（对应原版 Binding.schematic_menu = KeyCode.T → ui.schematics 的显隐切换）
        if(keyTap(Binding.schematic_menu)){
            toggleSchematics();
        }

        //全屏小地图打开时让出输入：本类是轮询式（Gdx.input.isButtonPressed），
        //光靠 Stage 消费事件拦不住，不加这一步拖拽平移地图会误建造/误开火。
        //（原版靠 arc 的 scene.hasScroll() + 事件式输入实现，phoenix 没有等价物。）
        if(Vars.hud != null && Vars.hud.minimapFullShown()){
            //建造预览（InputHandler.drawTop）读的是 targetX/targetY，这里也要刷新一次，
            //否则小地图打开期间预览会停在上一帧的位置
            updateTarget();
            scroll(); //丢弃本帧累积的滚轮量，否则关闭地图后相机会突然缩放
            if(player != null) player.isShooting = false;
            return;
        }

        //滚轮：正在放置可旋转方块时旋转朝向，按住 R 时旋转光标下的已放置方块，否则缩放相机。
        //原版 Binding.rotate 与 Binding.zoom 都绑在滚轮轴上（Binding.java 的 rotate(new Axis(SCROLL)) / zoom(new Axis(SCROLL))），
        //靠"是否在放置可旋转方块 / 是否按住 rotateplaced"区分——原版 zoom 分支的条件里带
        //!isPlacing() || !block.rotate 与 !keyDown(Binding.rotateplaced)（DesktopInput.java:161）。
        float zoomAmount = scroll();
        if(Math.abs(zoomAmount) > 0.0001f){
            if(!isPlacing() && keyDown(Binding.rotateplaced)){
                //按住 R（Binding.rotateplaced）滚动：旋转光标下的已放置方块，不缩放（对应原版 DesktopInput.java:225-227）
                rotatePlacedAt(zoomAmount > 0f);
            }else if(isPlacing() && buildBlock != null && buildBlock.rotate && !keyDown(Binding.diagonal_placement)){
                //原版 rotation 序列是逆时针（0=右/1=上/2=左/3=下），上滚 = +1 就是逆时针；
                //本项目序列是顺时针（0=上/1=右/2=下/3=左），逆时针要 -1（等价 +3）。
                //⚠ 按住 Ctrl（diagonal_placement）时不转朝向：对应原版 update 的
                //!keyDown(Binding.diagonal_placement) 守卫（DesktopInput.java:188）
                buildRotation = Mathf.mod(buildRotation + (zoomAmount > 0 ? 3 : 1), 4);
            }else if(Vars.renderer != null){
                Vars.renderer.scaleCamera(zoomAmount);
            }
        }

        //鼠标世界坐标（瞄准与建造共用）
        updateTarget();

        //玩家死亡或暂停时：WASD 平移相机（对应原版 DesktopInput.update 的相机部分）
        if(player == null || player.isDead() || Vars.state.isPaused()){
            float camSpeed = !keyDown(Binding.dash) ? 3f : 8f;

            Tmp.v1.set(axisX(), axisY());
            if(!Tmp.v1.isZero()){
                Tmp.v1.nor().scl(Time.delta() * camSpeed);
                Core.camera.position.x += Tmp.v1.x;
                Core.camera.position.y += Tmp.v1.y;
            }

            //按住鼠标侧键：相机朝鼠标方向移动（对应原版 Binding.mouse_move）
            if(isButtonPressed(Binding.mouse_move)){
                Core.camera.position.x += Mathf.clamp((mouseX() - Core.graphics.getWidth() / 2f) * 0.005f, -1f, 1f) * camSpeed;
                Core.camera.position.y += Mathf.clamp((mouseY() - Core.graphics.getHeight() / 2f) * 0.005f, -1f, 1f) * camSpeed;
            }
        }

        //玩家单位控制（存活且未暂停）：Shift 加速 + WASD 移动 + 鼠标瞄准射击
        if(player != null && !player.isDead() && !Vars.state.isPaused()){
            player.isBoosting = keyDown(Binding.dash);

            updateMovement();
            updateShooting();
        }

        //数字键切换玩家单位类型（重生时生效）；命令模式下数字键让位给编组
        if(!commandMode){
            updateTypeSwitch();
        }
        //冲刺技能（空格）
        updateDash();

        //松开左键停止射击（对应原版 keyRelease(Binding.select)）
        if(player != null && !isButtonPressed(Binding.select)){
            player.isShooting = false;
        }

        // ---- RTS 命令模式（对应 v8 DesktopInput 的 commandMode 逻辑）----

        //进入建造状态时强制退出命令模式（对应原版 block != null 时 commandMode 失效）
        if(commandMode && isPlacing()){
            exitCommandMode();
        }

        //Q 键切换命令模式（对应原版 Binding.commandMode = shiftLeft 的 keyTap 切换；
        //phoenix 的 Shift 已被玩家加速占用，故改用 Q）
        if(keyTap(Input.Keys.Q) && !isPlacing() && player != null && !player.isDead()){
            commandMode = !commandMode;
            if(!commandMode){
                //退出命令模式：清空选择（已下达的命令继续执行）
                selectedUnits.clear();
                commandRect = false;
            }
        }

        //Esc（对应原版 Binding.menu = KeyCode.ESCAPE）。原版在 Control.update() 里处理：
        //  1) 有对话框/输入焦点/结算框时不响应（scene.hasDialog()/hasKeyboard()/ui.restart.isShown()）
        //  2) 否则 ui.paused.show()（PausedDialog 的 shouldPause 会把状态切成 paused）
        //本项目在此基础上还要保住既有的两个 Esc 用途（原版这两个分别绑在鼠标右键与 v8 的命令模式）：
        //  1) 命令模式 → 退出命令模式
        //  2) 建造/拆除预览 → 取消
        //  3) 否则 → 打开暂停菜单
        if(keyTap(Binding.menu)){
            if(commandMode){
                exitCommandMode();
            }else if(isPlacing()){
                clearBuild();
            }else{
                openPauseMenu();
            }
        }

        if(commandMode){
            //清理无效的选中单位（死亡/被玩家接管/换队伍）
            for(int i = selectedUnits.size - 1; i >= 0; i--){
                if(!canCommand(selectedUnits.get(i))){
                    selectedUnits.removeIndex(i);
                }
            }

            //拖框超过阈值后视为框选而非点选
            if(commandRect && isButtonPressed(Binding.select)){
                if(Mathf.dst(commandRectX, commandRectY, targetX, targetY) > 8f){
                    tappedOne = false;
                }
            }

            //数字键编组（对应原版 controlGroupBindings）：Ctrl+数字保存、数字恢复
            boolean ctrl = keyDown(Input.Keys.CONTROL_LEFT) || keyDown(Input.Keys.CONTROL_RIGHT);
            for(int i = 0; i < controlGroups.length; i++){
                int key = i == 9 ? Input.Keys.NUM_0 : Input.Keys.NUM_1 + i;
                if(!keyTap(key)) continue;

                if(ctrl && selectedUnits.size > 0){
                    //创建编组：记录当前选中单位的 id
                    controlGroups[i].clear();
                    for(int j = 0; j < selectedUnits.size; j++){
                        controlGroups[i].add(selectedUnits.get(j).getID());
                    }
                }else if(controlGroups[i].size > 0){
                    //恢复编组
                    selectedUnits.clear();
                    for(int j = 0; j < controlGroups[i].size; j++){
                        com.phoenix.game.entities.type.BaseUnit unit = findUnitById(controlGroups[i].get(j));
                        if(canCommand(unit)){
                            selectedUnits.add(unit);
                        }
                    }
                }
            }

            //G 键：全选屏幕内本队单位（对应原版 selectAllUnits + selectAcrossScreen）
            if(keyTap(Input.Keys.G)){
                float[] bounds = cameraBounds();
                selectedUnits.clear();
                for(int i = 0; i < com.phoenix.game.entities.Units.units.size; i++){
                    com.phoenix.game.entities.type.BaseUnit unit = com.phoenix.game.entities.Units.units.get(i);
                    if(canCommand(unit) && unit.x >= bounds[0] && unit.x <= bounds[1]
                        && unit.y >= bounds[2] && unit.y <= bounds[3]){
                        selectedUnits.add(unit);
                    }
                }
            }
        }

        //F11 切换全屏（对应 Binding.fullscreen）
        if(keyTap(Binding.fullscreen)){
            toggleFullscreen();
        }

        //空格暂停/继续（对应 Binding.pause）
        if(keyTap(Binding.pause)){
            Vars.state.set(Vars.state.isPaused() ? GameState.State.playing : GameState.State.paused);
        }

        //Tab 打开玩家列表（对应原版 DesktopInput 的 `net.active() && keyTap(Binding.player_list)` → `ui.listfrag.toggle()`；
        //原版守卫 net.active()，本项目 Vars.net 恒 null → 用 Vars.isClient()/isServer() 判"联机中"）
        if(keyTap(Binding.player_list) && Vars.ui != null && Vars.ui.listfrag != null
            && (Vars.isClient() || Vars.isServer())){
            Vars.ui.listfrag.toggle();
        }

        //建造队列空了自动暂停（对应原版 pollInput 开头的 buildautopause 分支，DesktopInput.java:272-276；
        //设置项在设置菜单"游戏"页 —— SettingsMenuDialog 的 buildautopause 检选框）。
        //⚠ player.isBuilding 是玩家侧开关字段；isBuilding() 是"队列非空"，同名不同义（见 Player.java 注释）
        if(player != null && Core.settings.getBool("buildautopause", false)
            && player.isBuilding && !player.isBuilding()){
            player.isBuilding = false;
            player.buildWasAutoPaused = true;
        }

        //E 键暂停/恢复建造队列（对应原版 pollInput 的 Binding.pause_building 分支，DesktopInput.java:346-349）：
        //手动切换时清掉自动暂停标记
        com.badlogic.gdx.scenes.scene2d.Stage keyStage = com.phoenix.game.ui.dialogs.FloatingDialog.scene;
        if(keyTap(Binding.pause_building) && player != null
            && !(keyStage != null && keyStage.getKeyboardFocus() instanceof com.badlogic.gdx.scenes.scene2d.ui.TextField)){
            player.isBuilding = !player.isBuilding;
            player.buildWasAutoPaused = false;
        }

        //C 切换 HUD 建造菜单（对应原版 Binding.toggle_menus = KeyCode.C，原版在 HudFragment.update 里处理）。
        //守卫照原版：有对话框 / 输入框有焦点时不响应（原版还判 !ui.chatfrag.shown()，本项目 ChatFragment
        //由 ClientLauncher 私有持有、没有 shown() 接口 → 该条未接）
        if(keyTap(Binding.toggle_menus) && Vars.hud != null && !hasDialog()
            && !(keyStage != null && keyStage.getKeyboardFocus() instanceof com.badlogic.gdx.scenes.scene2d.ui.TextField)){
            Vars.hud.toggleMenus();
        }

        //P 键整图截图（对应原版 Control.update 的 Binding.screenshot 分支，Control.java:468-470：
        //!mobile && 无文本焦点 → renderer.takeMapScreenshot()）
        if(!Vars.mobile && keyTap(Binding.screenshot) && Vars.renderer != null && !hasDialog()
            && !(keyStage != null && keyStage.getKeyboardFocus() instanceof com.badlogic.gdx.scenes.scene2d.ui.TextField)){
            Vars.renderer.takeMapScreenshot();
        }

        //F7 切换电力激光显隐（对应原版 Binding.toggle_power_lines = KeyCode.F7）：关掉时记下偏好值，
        //再按一次还原（设置页拖动 lasersopacity 滑块也会同步更新偏好值）
        if(keyTap(Binding.toggle_power_lines)){
            if(Core.settings.getInt("lasersopacity", 100) == 0){
                Core.settings.put("lasersopacity", Core.settings.getInt("preferredlaseropacity", 100));
            }else{
                Core.settings.put("preferredlaseropacity", Core.settings.getInt("lasersopacity", 100));
                Core.settings.put("lasersopacity", 0);
            }
            Core.settings.save();
        }

        //⚠ R 键不再切换"待放置朝向"（原版桌面端没有该绑定；R = Binding.rotateplaced，
        //按住 + 滚轮旋转**已放置**的方块，见 update() 开头的滚轮分支）——原照抄偏差已拆除

        //拖拽中：按当前鼠标位置刷新连线路径（对应原版每帧检查 cursor 变化后 updateLine）
        if(lineMode){
            if(!isPlacing() || buildBlock == null){
                clearLine();
            }else{
                updateLine(targetX, targetY);
            }
        }

        //拆除模式：按住左键拖动可沿路径连续拆除（快速拖动也不会漏格）
        if(breaking && Vars.world != null && !Vars.state.isPaused() && isButtonPressed(Binding.select)){
            int tx = Vars.world.toTile(targetX), ty = Vars.world.toTile(targetY);

            if(lastBreakX == Integer.MIN_VALUE){
                lastBreakX = tx;
                lastBreakY = ty;
            }else if(tx != lastBreakX || ty != lastBreakY){
                int count = buildPath(lastBreakX, lastBreakY, tx, ty);
                for(int i = 0; i < count; i++){
                    breakWorld(pathX[i], pathY[i]);
                }
                lastBreakX = tx;
                lastBreakY = ty;
            }
        }else if(!breaking){
            lastBreakX = Integer.MIN_VALUE;
        }
    }

    /**
     * 命令模式下：左键开始框选（松手结算）、右键下达移动/攻击命令、中键追加队列命令
     * （对应原版 DesktopInput 的 commandMode 分支：select/commandQueue 键位）。
     * 非命令模式保持原行为：右键取消建造、左键建造/配置/开火。
     */
    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button){
        //命令模式：输入全部让给命令系统
        if(commandMode && !isPlacing() && Vars.player != null && !Vars.player.isDead() && !Vars.state.isPaused()){
            float wx = mouseWorldX(screenX), wy = mouseWorldY(screenY);

            if(mouseCode(button) == KeyBinds.key(Binding.select)){
                //开始框选（对应原版 keyTap(select) && commandMode → commandRect = true）
                commandRect = true;
                tappedOne = true;
                commandRectX = wx;
                commandRectY = wy;
                return true;
            }else if(button == Input.Buttons.RIGHT){
                //移动/攻击命令（对应原版 Binding.commandQueue 的 touchDown 分支 —— 原版右键即命令）
                //⚠ commandQueue/commandMode 键不在 126.2 的 Binding 枚举里（v8 特性）→ 不参与重绑
                commandTap(wx, wy, false);
                return true;
            }else if(button == Input.Buttons.MIDDLE){
                //追加队列命令（对应原版 Binding.commandQueue = mouseMiddle）
                commandTap(wx, wy, true);
                return true;
            }
            return false;
        }

        //右键取消建造/拆除（对应原版 Binding.deselect；libgdx 的 Buttons 常量先换算成
        //本项目负数鼠标键码再与绑定比对，见 InputHandler.mouseCode）
        if(mouseCode(button) == KeyBinds.key(Binding.deselect)){
            clearBuild();
            return false;
        }

        //中键吸取方块（对应原版 PlacementFragment.gridUpdate 的 Binding.pick 分支，PlacementFragment.java:93-109：
        //按住/按下中键，把光标下的方块或建造队列里的占位方块设为当前建造方块）
        if(mouseCode(button) == KeyBinds.key(Binding.pick)){
            if(Vars.world != null && Vars.player != null && !Vars.player.isDead() && !Vars.state.isPaused()
                && Vars.hud != null && Vars.hud.placement() != null
                && Vars.hud.placement().pickBlock(mouseWorldX(screenX), mouseWorldY(screenY))){
                return true;
            }
            return false;
        }

        //左键（对应原版 Binding.select）：建造/开火等主要交互
        if(mouseCode(button) != KeyBinds.key(Binding.select)) return false;

        if(isPlacing() && Vars.world != null){
            if(pasteSchematic != null){
                //蓝图粘贴：点一下贴一整张
                placeWorld(Vars.world.toTile(mouseWorldX(screenX)), Vars.world.toTile(mouseWorldY(screenY)));
            }else if(breaking){
                int tx = Vars.world.toTile(mouseWorldX(screenX)), ty = Vars.world.toTile(mouseWorldY(screenY));
                breakWorld(tx, ty);
                //记录起点，之后按住拖动即可沿路径连续拆除
                lastBreakX = tx;
                lastBreakY = ty;
            }else{
                //对应原版按下 select：进入连线模式，拖动时预览路径，松手时整体建造
                beginLine(mouseWorldX(screenX), mouseWorldY(screenY));
            }
            return false;
        }

        //非建造状态点击方块：可配置的方块打开配置面板（对应原版点 configurable 方块弹配置界面），
        //其余交给方块自己的 tapped（机甲平台换机甲等）；处理了就不开火
        if(Vars.world != null && Vars.player != null && !Vars.player.isDead()){
            com.phoenix.game.world.Tile tappedTile = Vars.world.tile(
                Vars.world.toTile(mouseWorldX(screenX)), Vars.world.toTile(mouseWorldY(screenY)));

            if(tappedTile != null && tappedTile.entity != null && tappedTile.block().configurable){
                //再点同一格就关掉（方便快速收起面板）
                //原版对非库存分支一律收起库存面板（tileTapped 末尾的 inv.hide），这里提前返回前同样处理
                if(Vars.hud != null) Vars.hud.inv.hide();
                configTile = configTile == tappedTile ? null : tappedTile;
                return false;
            }

            configTile = null;

            if(tappedTile != null && tappedTile.block().tapped(tappedTile, Vars.player)){
                if(Vars.hud != null) Vars.hud.inv.hide();
                return false;
            }

            //库存面板（对应原版 InputHandler.tileTapped 的 inv 分支）：
            //点核心/容器等有物品的可存取方块 → 弹出物品列表；点其他方块/空地 → 收起
            if(tileTapped(tappedTile)){
                return false;
            }

            //点自己 → 进入"投递物品"状态（对应原版 DesktopInput 里 `!tryTapPlayer(mouseWorld.x, mouseWorld.y)`）。
            //之后按住鼠标拖到目标方块再松开，由 touchUp 里的 tryDropItems 结算
            if(tryTapPlayer(mouseWorldX(screenX), mouseWorldY(screenY))){
                return false;
            }

            //采矿（对应原版 DesktopInput 里 `!tryBeginMine(selected) && player.getMineTile() == null`）：
            //点可采瓦片 → 开始/取消开采（本次点击不开火）；已有采矿目标时也不开火
            if(tryBeginMine(tappedTile) || Vars.player.getMineTile() != null){
                return false;
            }
        }

        if(Vars.player != null && !Vars.player.isDead()){
            Vars.player.isShooting = true;
        }

        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button){
        if(mouseCode(button) == KeyBinds.key(Binding.select)){
            //命令模式松开左键：结算框选/点选（对应原版 keyRelease(select) && commandRect → selectUnitsRect）
            if(commandMode && commandRect){
                commandRect = false;
                selectUnitsRect(mouseWorldX(screenX), mouseWorldY(screenY));
                return true;
            }

            //松开左键：把连线上的方块一次性放下（对应原版 flushRequests）
            if(lineMode){
                flushLine();
                if(Sounds.place != null){
                    Sounds.place.play(1f);
                }
            }

            //松开左键：结算"投递物品"（对应原版 keyRelease(select) 里的
            //`if(selected != null) tryDropItems(selected.link(), mouseWorld.x, mouseWorld.y)`）：
            //光标下的方块能收就存进去，不能收就丢弃
            if(Vars.world != null && Vars.player != null){
                com.phoenix.game.world.Tile dropTile = Vars.world.tile(
                    Vars.world.toTile(mouseWorldX(screenX)), Vars.world.toTile(mouseWorldY(screenY)));
                if(dropTile != null){
                    tryDropItems(dropTile.link() != null ? dropTile.link() : dropTile,
                        mouseWorldX(screenX), mouseWorldY(screenY));
                }
            }

            if(Vars.player != null){
                Vars.player.isShooting = false;
            }
        }
        return false;
    }

    /** 在世界坐标放置当前 `buildBlock`：**入建造队列**，由玩家单位逐帧建（不再是瞬间建成）。
     * 联机时改为向服务器发建造请求（服务端权威执行，等 BlockState 确认）。 */
    public void placeWorld(int tileX, int tileY){
        if(Vars.world == null) return;

        //蓝图粘贴：把整张蓝图转成建造请求入队（单机走队列，联机暂不支持——见 Build.useBuildQueue）
        com.phoenix.game.game.Schematic schematic = Vars.control != null && Vars.control.input != null
            ? Vars.control.input.pasteSchematic : null;
        if(schematic != null){
            com.badlogic.gdx.utils.Array<com.phoenix.game.world.BuildRequest> reqs =
                Vars.schematics.toRequests(schematic, tileX, tileY);

            if(Vars.netClient != null && Vars.netClient.isConnected()){
                //联机：逐格发建造请求，由服务端的玩家单位权威建造（进度再从 BlockState 广播回来）。
                //注意蓝图里的"配置值"（桥的连接目标、分拣器物品…）不带在请求里 —— 服务端会按自己的
                //playerPlaced 规则自动连，粘贴出来的机器需要玩家手动改配置
                for(int i = 0; i < reqs.size; i++){
                    com.phoenix.game.world.BuildRequest req = reqs.get(i);
                    if(req.block != null){
                        Vars.netClient.sendBuild(req.x, req.y, req.block, req.rotation);
                    }
                }
            }else if(Vars.player != null){
                for(int i = 0; i < reqs.size; i++){
                    Vars.player.addBuildRequest(reqs.get(i));
                }
            }
            return;
        }

        if(buildBlock == null) return;

        //联机：发请求，不做本地执行（避免与服务端状态分叉）
        if(Vars.netClient != null && Vars.netClient.isConnected()){
            Vars.netClient.sendBuild(tileX, tileY, buildBlock, buildRotation);
            return;
        }

        //单机：入建造队列，由玩家单位逐帧建
        if(Build.useBuildQueue() && Vars.player != null){
            Vars.player.addBuildRequest(new com.phoenix.game.world.BuildRequest(tileX, tileY, buildRotation, buildBlock));
            return;
        }

        //联机：服务端即时建造（见 Build.useBuildQueue 的说明）
        if(Build.placeBlock(Vars.world.tile(tileX, tileY), buildBlock, Team.sharded, buildRotation)){
            if(Sounds.place != null){
                Sounds.place.play(1f);
            }
        }
    }

    /** 在世界坐标拆除方块（只能拆自己阵营的、可破坏的方块），同样入队由单位拆，按比例返还材料。
     * 联机时改为向服务器发拆除请求。 */
    public void breakWorld(int tileX, int tileY){
        if(Vars.world == null) return;

        //联机：发请求，不做本地执行
        if(Vars.netClient != null && Vars.netClient.isConnected()){
            Vars.netClient.sendDeconstruct(tileX, tileY);
            return;
        }

        //单机：入队列，由玩家单位逐帧拆
        if(Build.useBuildQueue() && Vars.player != null){
            Vars.player.addBuildRequest(new com.phoenix.game.world.BuildRequest(tileX, tileY));
            return;
        }

        //联机：服务端即时拆除
        if(Build.deconstruct(Vars.world.tile(tileX, tileY), Team.sharded)){
            if(Sounds.breaks != null){
                Sounds.breaks.play(1f);
            }
        }
    }

    /**
     * 按住 R + 滚动时旋转光标下的**已放置**方块（对应原版 DesktopInput.java:225-227 的
     * {@code Call.rotateBlock(player, cursor, direction)}）。
     * <p>原版 {@code Call.rotateBlock} 是 @Remote（本项目 Remote 注释态）→ 落到服务端后只做
     * {@code tile.rotation(mod(rotation + sign(direction), 4))} + {@code updateProximity()} +
     * {@code noSleep()}，这里等价本地直调（本项目 TileEntity 没有休眠机制，noSleep 省略）。
     * <p>守卫照原版：{@code cursor.interactable(team) && !isPlacing() && keyDown(rotateplaced) && cursor.block().rotate}。
     */
    private void rotatePlacedAt(boolean direction){
        if(Vars.world == null || Vars.player == null || Vars.player.isDead()) return;

        Tile cursor = Vars.world.tile(
            Vars.world.toTile(mouseWorldX(Gdx.input.getX())), Vars.world.toTile(mouseWorldY(Gdx.input.getY())));
        if(cursor == null) return;
        cursor = cursor.link();

        if(!cursor.interactable(Vars.player.getTeam()) || !cursor.block().rotate) return;

        //原版 Mathf.mod(tile.rotation() + Mathf.sign(direction), 4)；本项目 rotation 约定相反但
        //"+1/-1 循环"在两种约定下都是旋转 90°，直接用 sign(Boolean) 的 ±1 即可
        cursor.rotation(Mathf.mod(cursor.rotation() + Mathf.sign(direction), 4));

        if(cursor.entity != null){
            cursor.entity.updateProximity();
        }
    }

    /**
     * 底部建造提示条（对应原版 DesktopInput.buildUI 的第一张 fill 表，DesktopInput.java:44-58；
     * 受 hints 设置控制）：
     * 三行提示 —— 暂停/恢复建造（E）、清除建造规划（Q）、选择蓝图（F），玩家正在建造时淡入、闲时淡出。
     * <p>原版用 arc 的 label(Prov)/visible(Boolp)/update(Runnable) 每帧钩子；libgdx 是静态文本
     * → 用 {@link com.phoenix.game.ui.MenuFragment.Act} 每帧刷新 alpha/显隐/首行文本。
     * <p><b>降级</b>：原版 visible 还带 {@code selectRequests.isEmpty()}（桌面端框选蓝图期间隐藏）；
     * 本项目桌面端没有该状态 → 省略。原版第二张 fill 表（蓝图翻转提示 + 保存按钮）未移植。
     * <p>挂载点：由 HudFragment 建好 bottom 对齐的 fill-parent 容器传入（对应原版往 uiGroup 塞 fill 表）。
     */
    @Override
    public void buildUI(com.badlogic.gdx.scenes.scene2d.ui.Table table){
        table.bottom();

        com.phoenix.game.ui.Label buildLabel = new com.phoenix.game.ui.Label("", Styles.outlineLabel);
        com.phoenix.game.ui.Label cancelLabel = new com.phoenix.game.ui.Label(
            com.phoenix.game.core.Core.bundle.format("cancelbuilding", bindingKey(Binding.clear_building)),
            Styles.outlineLabel);
        com.phoenix.game.ui.Label schemLabel = new com.phoenix.game.ui.Label(
            com.phoenix.game.core.Core.bundle.format("selectschematic", bindingKey(Binding.schematic_select)),
            Styles.outlineLabel);

        com.badlogic.gdx.scenes.scene2d.ui.Table inner = new com.badlogic.gdx.scenes.scene2d.ui.Table();
        inner.setBackground(Styles.black6);
        inner.defaults().left();
        inner.pad(Scl.scl(10f));
        inner.add(buildLabel).row();
        inner.add(cancelLabel).row();
        inner.add(schemLabel).row();

        table.add(inner);

        table.addActor(new com.phoenix.game.ui.MenuFragment.Act(() -> {
            //alpha 渐隐（对应原版 t.update 里的 lerpDelta(t.getColor().a, player.isBuilding() ? 1 : 0, 0.15f)）
            Player player = Vars.player;
            float target = player != null && player.isBuilding() ? 1f : 0f;
            float a = table.getColor().a = Mathf.lerpDelta(table.getColor().a, target, 0.15f);

            //显隐（对应原版 t.visible(...)）
            table.setVisible(com.phoenix.game.core.Core.settings.getBool("hints", true));

            //首行文本随玩家侧开关切换（对应原版 label(() -> bundle.format(!player.isBuilding ? "resumebuilding" : "pausebuilding", ...))）
            buildLabel.setText(com.phoenix.game.core.Core.bundle.format(
                player != null && !player.isBuilding ? "resumebuilding" : "pausebuilding",
                bindingKey(Binding.pause_building)));

            //触摸穿透（对应原版 t.touchable(...)；提示条纯展示 → 低透明度时彻底禁触摸）
            table.setTouchable(a < 0.1f ? com.badlogic.gdx.scenes.scene2d.Touchable.disabled
                : com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly);
        }));
    }

    /**
     * @return 键位显示名（对应原版 {@code Core.keybinds.get(Binding).key.toString()}）。
     * <p>F15 起经 {@link KeyBinds} 查生效键码（重绑后显示新键位），键名统一走 {@link KeyBinds#keyCodeName}。
     */
    private static String bindingKey(Binding binding){
        return KeyBinds.keyCodeName(KeyBinds.key(binding));
    }

    /** 全屏切换：退出全屏时恢复默认窗口尺寸。 */
    public void toggleFullscreen(){
        if(Gdx.graphics.isFullscreen()){
            Gdx.graphics.setWindowedMode(defaultWidth, defaultHeight);
        }else{
            Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        }
    }

    /**
     * 打开暂停菜单（对应原版 {@code Control.update()} 里 {@code Binding.menu} 的那个分支）。
     * <p>原版守卫：{@code !scene.hasDialog() && !scene.hasKeyboard() && !ui.restart.isShown()}，
     * 满足则 {@code ui.paused.show(); state.set(State.paused);}。
     * <p>本项目 {@link com.phoenix.game.ui.dialogs.PausedDialog} 的 {@code shouldPause = true}，
     * {@code show()} 内部（FloatingDialog 的 shown 回调）已经会把状态切成 paused → 这里不再重复 state.set。
     * <p>TODO 原版还判 {@code !ui.minimapfrag.shown()}：本项目全屏小地图是
     * {@code Vars.hud.minimapFullShown()}，但 {@link #update()} 开头遇到它就提前 return 了，走不到这里。
     * <p>TODO 原版还判 {@code !scene.hasKeyboard()}（有文本焦点时不响应）：libgdx 的 Stage 用
     * {@code getKeyboardFocus()} 表达，但输入框弹在自己的 Dialog 里（已被 hasDialog 覆盖）→ 未单独实现。
     */
    private void openPauseMenu(){
        if(Vars.ui == null || Vars.ui.paused == null) return;
        if(hasDialog()) return;
        Vars.ui.paused.show();
    }

    /**
     * 开关蓝图库（对应原版 {@code DesktopInput} 里 {@code Binding.schematic_menu} 的分支：
     * 已开就 hide，否则 show）。
     * <p>原版守卫 {@code !Core.scene.hasKeyboard()}；这里换成 {@code !hasDialog()}——
     * 蓝图库自己开着时走 hide 分支，不会被守卫挡住。
     */
    private void toggleSchematics(){
        if(Vars.ui == null || Vars.ui.schematics == null) return;
        if(Vars.ui.schematics.getStage() != null){
            Vars.ui.schematics.hide();
        }else if(!hasDialog()){
            Vars.ui.schematics.show();
        }
    }

    /**
     * @return 当前 stage 上是否还有打开的对话框（对应原版 {@code Core.scene.hasDialog()}）。
     * <p>判定依据：{@code FloatingDialog.hide()} 走 {@code remove()}，从 stage 上摘掉后
     * {@code getStage()} 变成 null；遮罩是 {@code Image}（不是 {@code Dialog}），不会误判。
     * <p>F15 起教程浮层（{@code Tutorial.draw}）也用它做"有对话框不画"判定。
     */
    public static boolean hasDialog(){
        com.badlogic.gdx.scenes.scene2d.Stage stage = com.phoenix.game.ui.dialogs.FloatingDialog.scene;
        if(stage == null) return false;
        com.badlogic.gdx.utils.Array<com.badlogic.gdx.scenes.scene2d.Actor> children = stage.getActors();
        for(int i = 0; i < children.size; i++){
            com.badlogic.gdx.scenes.scene2d.Actor actor = children.get(i);
            if(actor instanceof com.badlogic.gdx.scenes.scene2d.ui.Dialog && actor.getStage() != null && actor.isVisible()){
                return true;
            }
        }
        return false;
    }

}
