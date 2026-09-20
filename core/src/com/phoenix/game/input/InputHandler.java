package com.phoenix.game.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Fill;
import com.phoenix.game.core.Lines;
import com.phoenix.game.core.Time;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.net.Packets;
import com.phoenix.game.type.Weapon;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Build;
import com.phoenix.game.world.Tile;

/**
 * 最小实现：输入处理基类。参照 Mindustry mindustry.input.InputHandler 移植。
 * arc 的 Core.input 接口用 libgdx 的 Gdx.input 替代。
 */
public abstract class InputHandler extends InputAdapter{
    /** Shift 加速时的速度倍率（对应原版 Mech.boostSpeed） */
    public static final float boostMultiplier = 1.8f;

    /** 本帧累计的滚轮量（对应 arc 的 SCROLL 轴） */
    protected float scrollAmount;
    /** 当前待放置方块；null 表示未进入建造模式。由 HUD 建造栏设置。 */
    public Block buildBlock;
    /** 拆除模式：左键点击拆除自己的方块并返还材料。 */
    public boolean breaking;
    /**
     * 待粘贴的蓝图（对应原版"选中蓝图后放置"的状态）。
     * <p>非 null 时点击世界会把蓝图转成一批建造请求入队；选方块或点取消会清掉它。
     */
    public com.phoenix.game.game.Schematic pasteSchematic;
    /**
     * 正在配置的瓦片（对应原版点击 configurable 方块后弹出的配置界面）。
     * <p>由 {@link com.phoenix.game.ui.BlockConfigFragment} 读取并显示方块自己的配置表；
     * 点关闭 / 选中其他方块 / 退出建造模式都会清掉它。
     */
    public com.phoenix.game.world.Tile configTile;
    /** 当前放置朝向（0=+y, 1=+x, 2=-y, 3=-x），R 键循环。 */
    public int buildRotation;

    /** @return 是否处于建造/拆除模式 */
    public boolean isPlacing(){
        return buildBlock != null || breaking || pasteSchematic != null;
    }

    /** 退出建造/拆除模式（对应原版 Binding.deselect）。 */
    public void clearBuild(){
        buildBlock = null;
        breaking = false;
        pasteSchematic = null;
        configTile = null;
        clearLine();
        //同时清掉玩家单位还没做完的建造/拆除请求（对应原版 player.clearBuilding()）
        if(Vars.player != null) Vars.player.clearBuilding();
    }

    // ---- 连线放置（拖拽连放，对应原版 InputHandler.updateLine / lineRequests）----

    /** 连线长度上限（避免拖太远铺满地图）。 */
    public static final int maxLineLength = 48;
    /** 复用数组：路径点（避免每次拖拽分配）。 */
    public final int[] pathX = new int[maxLineLength + 1], pathY = new int[maxLineLength + 1];
    /** 当前路径格数。 */
    public int pathSize;

    /** 按住 Ctrl 时强制直线（原版用 Binding.diagonal_placement 做相反的切换）。 */
    public boolean straightLine;
    /** 是否正在拖拽连线（对应原版 PlaceMode.placing）。 */
    public boolean lineMode;
    /** 连线起点瓦片。 */
    public int lineStartX, lineStartY;
    /** 当前连线的逐格结果（对应原版 lineRequests）：朝向指向路径的下一格。 */
    public final LineRequest[] lines = new LineRequest[maxLineLength + 1];
    /** 当前连线格数。 */
    public int lineSize;

    public InputHandler(){
        for(int i = 0; i < lines.length; i++){
            lines[i] = new LineRequest();
        }
    }

    /** 连线中的一格（对应原版 BuildRequest 里的最小字段）。 */
    public static class LineRequest{
        public int x, y, rotation;
    }

    /** @return 本局玩家的队伍（未进入游戏时为 sharded）。 */
    public Team playerTeam(){
        return Vars.player != null ? Vars.player.getTeam() : Team.sharded;
    }

    /**
     * 开始连线放置（按下左键时调用，对应原版 `mode = placing`）。
     * @param worldX 鼠标世界坐标 X
     * @param worldY 鼠标世界坐标 Y
     */
    public void beginLine(float worldX, float worldY){
        if(Vars.world == null) return;
        lineStartX = Vars.world.toTile(worldX);
        lineStartY = Vars.world.toTile(worldY);
        lineMode = true;
        updateLine(worldX, worldY);
    }

    /** 拖拽过程中按当前鼠标位置刷新连线（对应原版 updateLine(x1, y1)）。 */
    public void updateLine(float worldX, float worldY){
        if(Vars.world == null) return;
        updateLine(lineStartX, lineStartY, Vars.world.toTile(worldX), Vars.world.toTile(worldY));
    }

    /**
     * 生成连线路径（对应原版 InputHandler.iterateLine + Placement.normalizeLine）。
     * <p>直线模式下只沿偏差较大的一个轴推进；可旋转方块（传送带）默认走 L 形路径，
     * 且每一格的朝向指向路径的下一格、最后一格用"起点 → 终点"的方向，
     * 因此拖出拐角时带子会自动跟着转弯。
     */
    public void updateLine(int startX, int startY, int endX, int endY){
        lineSize = 0;
        Block block = buildBlock;
        if(block == null) return;

        //可旋转方块（传送带）走 L 形路径，其余方块走直线
        buildPath(startX, startY, endX, endY, block.rotate && !straightLine);

        int count = pathSize;
        //路径终点（用于最后一格的朝向）
        int lastX = pathX[count - 1], lastY = pathY[count - 1];
        int baseRotation = (startX == lastX && startY == lastY) ? buildRotation : relativeTo(startX, startY, lastX, lastY);

        for(int i = 0; i < count; i++){
            LineRequest req = lines[lineSize];
            req.x = pathX[i];
            req.y = pathY[i];
            //朝向指向路径下一格（拐角自然成立），最后一格用起点→终点的方向
            req.rotation = i < count - 1 ? relativeTo(pathX[i], pathY[i], pathX[i + 1], pathY[i + 1]) : baseRotation;
            lineSize++;
        }
    }

    /**
     * 生成轴对齐的路径并填充 {@link #pathX}/{@link #pathY}（对应原版 Placement.normalizeLine / pathfindLine）。
     * <p>直线模式只走偏差较大的一个轴；L 形模式先走长边再走短边（拖动拐角用）。
     * @return 路径格数
     */
    public int buildPath(int startX, int startY, int endX, int endY, boolean lShape){
        endX = startX + Mathf.clamp(endX - startX, -maxLineLength, maxLineLength);
        endY = startY + Mathf.clamp(endY - startY, -maxLineLength, maxLineLength);

        int sx = Integer.signum(endX - startX), sy = Integer.signum(endY - startY);
        int remainingX = Math.abs(endX - startX), remainingY = Math.abs(endY - startY);

        int count = 0;
        int x = startX, y = startY;
        pathX[count] = x;
        pathY[count] = y;
        count++;

        if(remainingX >= remainingY){
            for(int i = 0; i < remainingX; i++){ x += sx; pathX[count] = x; pathY[count] = y; count++; }
            if(lShape){
                for(int i = 0; i < remainingY; i++){ y += sy; pathX[count] = x; pathY[count] = y; count++; }
            }
        }else{
            for(int i = 0; i < remainingY; i++){ y += sy; pathX[count] = x; pathY[count] = y; count++; }
            if(lShape){
                for(int i = 0; i < remainingX; i++){ x += sx; pathX[count] = x; pathY[count] = y; count++; }
            }
        }

        pathSize = count;
        return count;
    }

    /** 把连线上的每一格**入建造队列**（对应原版 flushRequests）：非法格由队列在开工时自然跳过。
     * 联机时逐格发建造请求（服务端权威）。 */
    public void flushLine(){
        if(buildBlock != null && Vars.world != null){
            //联机：逐格发建造请求
            if(Vars.netClient != null && Vars.netClient.isConnected()){
                for(int i = 0; i < lineSize; i++){
                    LineRequest req = lines[i];
                    Vars.netClient.sendBuild(req.x, req.y, buildBlock, req.rotation);
                }
                clearLine();
                return;
            }

            if(Build.useBuildQueue() && Vars.player != null){
                for(int i = 0; i < lineSize; i++){
                    LineRequest req = lines[i];
                    Vars.player.addBuildRequest(
                        new com.phoenix.game.world.BuildRequest(req.x, req.y, req.rotation, buildBlock));
                }
            }else{
                Team team = playerTeam();
                for(int i = 0; i < lineSize; i++){
                    LineRequest req = lines[i];
                    Build.placeBlock(Vars.world.tile(req.x, req.y), buildBlock, team, req.rotation);
                }
            }
        }
        clearLine();
    }

    /** 丢弃当前连线（右键退出建造、切换方块时调用）。 */
    public void clearLine(){
        lineSize = 0;
        lineMode = false;
    }

    /** @return 从 (x1,y1) 指向 (x2,y2) 的朝向索引（对应原版 Tile.relativeTo；非四方向时取主导轴）。 */
    public static int relativeTo(int x1, int y1, int x2, int y2){
        int dx = x2 - x1, dy = y2 - y1;

        for(int i = 0; i < Geometry.d4.length; i++){
            if(Geometry.d4[i].x == dx && Geometry.d4[i].y == dy) return i;
        }

        if(Math.abs(dx) >= Math.abs(dy)){
            return dx >= 0 ? 1 : 3;
        }
        return dy >= 0 ? 0 : 2;
    }

    // ---- RTS 命令系统（对应 v8 InputHandler 的 command 部分）----

    /** 当前选中的单位（对应原版 selectedUnits）。 */
    public final com.badlogic.gdx.utils.Array<com.phoenix.game.entities.type.BaseUnit> selectedUnits =
        new com.badlogic.gdx.utils.Array<>();
    /** 命令模式开关（对应原版 commandMode；phoenix 用 Q 键切换 —— 原版的 Shift 与本工程玩家加速冲突）。 */
    public boolean commandMode;
    /** 正在拖框选单位（对应原版 commandRect）。 */
    public boolean commandRect;
    /** 本次按下是“点选”而非“框选”（拖动超过阈值后置 false，对应原版 tappedOne）。 */
    public boolean tappedOne;
    /** 框选起点（世界坐标，对应原版 commandRectX/Y）。 */
    public float commandRectX, commandRectY;
    /** 数字键编组（Ctrl+数字保存、数字恢复选中，对应原版 controlGroups）。 */
    @SuppressWarnings("unchecked")
    public final com.badlogic.gdx.utils.IntArray[] controlGroups = new com.badlogic.gdx.utils.IntArray[10];
    /** 双击点选检测。 */
    protected long lastTapTime;
    protected com.phoenix.game.entities.type.BaseUnit lastTapUnit;

    /** 命令队列最大批量（单包容量内，保证编队不会被分包拆散；对应原版 maxChunkSize）。 */
    public static final int maxChunkSize = 500;

    {
        for(int i = 0; i < controlGroups.length; i++){
            controlGroups[i] = new com.badlogic.gdx.utils.IntArray();
        }
    }

    /** @return 单位是否可被本机玩家选中指挥（v8 语义：controller 是 CommandAI；phoenix 按 isCommandable 判定）。 */
    public boolean canCommand(com.phoenix.game.entities.type.BaseUnit unit){
        return unit != null && !unit.isDead() && unit.isCommandable() && unit.getTeam() == playerTeam();
    }

    /** 退出命令模式并清空选择（对应原版 Esc/切建造时的行为）。 */
    public void exitCommandMode(){
        commandMode = false;
        commandRect = false;
        selectedUnits.clear();
    }

    // ---- 选择（对应原版 selectUnitsRect / tapCommandUnit / selectTypedUnits）----

    /** 松开左键：按框选范围重设选择集；若只是单击则交给 tapCommandUnit。 */
    public void selectUnitsRect(float worldX, float worldY){
        if(!commandMode) return;

        if(tappedOne){
            //单击：点选 / 双击选同类
            com.phoenix.game.entities.type.BaseUnit hit = selectedCommandUnit(worldX, worldY);
            long now = System.currentTimeMillis();

            if(hit != null && hit == lastTapUnit && now - lastTapTime < 400){
                selectTypedUnits(hit);
            }else{
                tapCommandUnit(hit);
            }

            lastTapTime = now;
            lastTapUnit = hit;
            return;
        }

        lastTapUnit = null;

        //框选：矩形范围内的本队可指挥单位
        selectedUnits.clear();
        float x = Math.min(commandRectX, worldX), y = Math.min(commandRectY, worldY);
        float w = Math.abs(worldX - commandRectX), h = Math.abs(worldY - commandRectY);

        for(int i = 0; i < com.phoenix.game.entities.Units.units.size; i++){
            com.phoenix.game.entities.type.BaseUnit unit = com.phoenix.game.entities.Units.units.get(i);
            if(canCommand(unit) && unit.x >= x && unit.x <= x + w && unit.y >= y && unit.y <= y + h){
                selectedUnits.add(unit);
            }
        }
    }

    /** 双击：选中屏幕内所有同类型单位（对应原版 selectTypedUnits）。 */
    public void selectTypedUnits(com.phoenix.game.entities.type.BaseUnit unit){
        selectedUnits.clear();

        float[] bounds = cameraBounds();
        for(int i = 0; i < com.phoenix.game.entities.Units.units.size; i++){
            com.phoenix.game.entities.type.BaseUnit other = com.phoenix.game.entities.Units.units.get(i);
            if(canCommand(other) && other.getType() == unit.getType()
                && other.x >= bounds[0] && other.x <= bounds[1] && other.y >= bounds[2] && other.y <= bounds[3]){
                selectedUnits.add(other);
            }
        }
    }

    /** 单击点选：命中单位则 toggle 选中，否则清空选择（对应原版 tapCommandUnit）。 */
    public void tapCommandUnit(com.phoenix.game.entities.type.BaseUnit hit){
        if(hit != null){
            if(selectedUnits.contains(hit, true)){
                selectedUnits.removeValue(hit, true);
            }else{
                selectedUnits.add(hit);
            }
        }else{
            selectedUnits.clear();
        }
    }

    // ---- 选择查询（对应原版 selectedCommandUnit / selectedEnemyUnit）----

    /** @return 点选命中的本队可指挥单位（按中心距最近，命中半径 hitSize/2 + 8）。 */
    public com.phoenix.game.entities.type.BaseUnit selectedCommandUnit(float x, float y){
        com.phoenix.game.entities.type.BaseUnit result = null;
        float cdist = Float.MAX_VALUE;

        for(int i = 0; i < com.phoenix.game.entities.Units.units.size; i++){
            com.phoenix.game.entities.type.BaseUnit unit = com.phoenix.game.entities.Units.units.get(i);
            if(!canCommand(unit)) continue;

            float dst = Mathf.dst2(unit.x, unit.y, x, y);
            float bound = unit.getSize() / 2f + 8f;
            if(dst <= bound * bound && dst < cdist){
                result = unit;
                cdist = dst;
            }
        }
        return result;
    }

    /** @return 点选命中的敌方单位（对应原版 selectedEnemyUnit；建筑目标未移植）。 */
    public com.phoenix.game.entities.type.BaseUnit selectedEnemyUnit(float x, float y){
        com.phoenix.game.entities.type.BaseUnit result = null;
        float cdist = Float.MAX_VALUE;
        Team team = playerTeam();

        for(int i = 0; i < com.phoenix.game.entities.Units.units.size; i++){
            com.phoenix.game.entities.type.BaseUnit unit = com.phoenix.game.entities.Units.units.get(i);
            if(unit.isDead() || !team.isEnemy(unit.getTeam())) continue;

            float dst = Mathf.dst2(unit.x, unit.y, x, y);
            float bound = unit.getSize() / 2f + 8f;
            if(dst <= bound * bound && dst < cdist){
                result = unit;
                cdist = dst;
            }
        }
        return result;
    }

    // ---- 命令下发（对应原版 @Remote commandUnits / setUnitCommand / setUnitStance）----

    /**
     * 右键下达命令：有敌方单位则攻击，否则移动（对应原版 {@code commandTap(x, y, queue)}）。
     * 单机/主机直接应用；联机客户端本地应用表现 + 发包给服务器。
     */
    public void commandTap(float x, float y, boolean queue){
        if(selectedUnits.size == 0) return;

        com.phoenix.game.entities.type.BaseUnit unitTarget = selectedEnemyUnit(x, y);

        int[] ids = new int[selectedUnits.size];
        for(int i = 0; i < ids.length; i++){
            ids[i] = selectedUnits.get(i).getID();
        }

        if(Vars.isClient()){
            //联机客户端：本地只做表现（代理单位记录目标供画线），权威执行在服务器
            for(int i = 0; i < ids.length; i++){
                com.phoenix.game.entities.type.BaseUnit unit = selectedUnits.get(i);
                applyCommandVisual(unit, unitTarget, x, y);
            }
            if(Vars.netClient != null){
                Vars.netClient.sendUnitCommand(Packets.TYPE_ORDERS, ids, unitTarget == null ? -1 : unitTarget.getID(), x, y, queue);
            }
        }else{
            //单机 / 主机：直接权威应用；主机还要广播给其他客户端做表现
            applyCommand(playerTeam(), ids, unitTarget, x, y, queue);
            if(Vars.isServer() && Vars.net != null){
                Packets.UnitCommandPacket packet = new Packets.UnitCommandPacket();
                packet.type = Packets.TYPE_ORDERS;
                packet.unitIds = ids;
                packet.targetUnitId = unitTarget == null ? -1 : unitTarget.getID();
                packet.x = x;
                packet.y = y;
                packet.queue = queue;
                Vars.net.send(packet, com.phoenix.game.net.Net.SendMode.tcp);
            }
        }

        //命令确认特效（对应原版 Fx.moveCommand / Fx.attackCommand）
        com.phoenix.game.entities.Effects.effect(
            unitTarget != null ? com.phoenix.game.content.Fx.attackCommand : com.phoenix.game.content.Fx.moveCommand, x, y);
    }

    /**
     * 权威应用一批命令（对应原版 @Remote commandUnits 的方法体）：过滤有效单位、
     * 非追加命令时按飞行/地面分组建编队，然后逐单位下发。
     */
    public static void applyCommand(com.phoenix.game.game.Team team, int[] unitIds,
                                    com.phoenix.game.entities.type.BaseUnit unitTarget, float x, float y, boolean queue){
        com.phoenix.game.ai.UnitGroup groundGroup = null, flyingGroup = null;
        int applied = 0;

        for(int i = 0; i < unitIds.length; i++){
            com.phoenix.game.entities.type.BaseUnit unit = findUnitById(unitIds[i]);
            if(unit == null || unit.isDead() || unit.getTeam() != team || !unit.isCommandable()) continue;
            applied++;

            //编队（对应原版 finalBatch 阶段按 collisionLayer 分组建 UnitGroup）
            if(!queue){
                //脱离旧编队
                if(unit.commandAI != null && unit.commandAI.group != null){
                    unit.commandAI.group.units.removeValue(unit, true);
                }
                if(unit.isFlying()){
                    if(flyingGroup == null) flyingGroup = new com.phoenix.game.ai.UnitGroup();
                    flyingGroup.units.add(unit);
                }else{
                    if(groundGroup == null) groundGroup = new com.phoenix.game.ai.UnitGroup();
                    groundGroup.units.add(unit);
                }
            }
        }

        if(groundGroup != null){
            //把编队赋回给成员（对应原版 commandUnits finalBatch 的 unit.command().group = group）
            for(int i = 0; i < groundGroup.units.size; i++){
                groundGroup.units.get(i).command().group = groundGroup;
            }
            groundGroup.calculateFormation(Tmp.v1.set(x, y));
        }
        if(flyingGroup != null){
            for(int i = 0; i < flyingGroup.units.size; i++){
                flyingGroup.units.get(i).command().group = flyingGroup;
            }
            flyingGroup.calculateFormation(Tmp.v1.set(x, y));
        }

        if(applied == 0) return;

        for(int i = 0; i < unitIds.length; i++){
            com.phoenix.game.entities.type.BaseUnit unit = findUnitById(unitIds[i]);
            if(unit == null || unit.isDead() || unit.getTeam() != team || !unit.isCommandable()) continue;

            com.phoenix.game.ai.types.CommandAI ai = unit.command();
            if(unitTarget != null){
                ai.commandTarget(unitTarget);
            }else if(queue){
                ai.commandQueue(Tmp.v2.set(x, y));
            }else{
                ai.commandPosition(Tmp.v2.set(x, y));
            }
        }
    }

    /** 客户端专用：把命令“表现”到远端代理单位上（代理单位不跑 AI，只记录目标供画线/特效）。 */
    protected void applyCommandVisual(com.phoenix.game.entities.type.BaseUnit unit,
                                      com.phoenix.game.entities.type.BaseUnit unitTarget, float x, float y){
        com.phoenix.game.ai.types.CommandAI ai = unit.command();
        if(unitTarget != null){
            ai.commandTarget(unitTarget);
        }else{
            ai.commandPosition(Tmp.v2.set(x, y));
        }
    }

    /** 切换命令（对应原版 @Remote setUnitCommand）。 */
    public void setUnitCommand(com.phoenix.game.ai.UnitCommand command){
        if(command == null || selectedUnits.size == 0) return;

        int[] ids = new int[selectedUnits.size];
        for(int i = 0; i < ids.length; i++){
            ids[i] = selectedUnits.get(i).getID();
        }

        if(Vars.isClient()){
            if(Vars.netClient != null){
                Vars.netClient.sendUnitCommand(Packets.TYPE_COMMAND, ids, command.id, 0f, 0f, false);
            }
            //客户端不本地应用（命令字段影响服务端 AI 语义），等广播回来统一表现
        }else{
            applySetCommand(playerTeam(), ids, command);
            if(Vars.isServer() && Vars.net != null){
                Packets.UnitCommandPacket packet = new Packets.UnitCommandPacket();
                packet.type = Packets.TYPE_COMMAND;
                packet.unitIds = ids;
                packet.commandId = command.id;
                Vars.net.send(packet, com.phoenix.game.net.Net.SendMode.tcp);
            }
        }
    }

    /** @see #setUnitCommand（权威应用，服务端/单机用）。 */
    public static void applySetCommand(com.phoenix.game.game.Team team, int[] unitIds, com.phoenix.game.ai.UnitCommand command){
        for(int i = 0; i < unitIds.length; i++){
            com.phoenix.game.entities.type.BaseUnit unit = findUnitById(unitIds[i]);
            if(unit == null || unit.isDead() || unit.getTeam() != team || !unit.isCommandable()) continue;

            com.phoenix.game.ai.types.CommandAI ai = unit.command();
            boolean reset = command.resetTarget || ai.currentCommand().resetTarget;
            ai.command(command);
            if(reset){
                ai.clearCommands();
            }
        }
    }

    /** 切换姿态（对应原版 @Remote setUnitStance）。 */
    public void setUnitStance(com.phoenix.game.ai.UnitStance stance){
        if(stance == null || selectedUnits.size == 0) return;

        int[] ids = new int[selectedUnits.size];
        for(int i = 0; i < ids.length; i++){
            ids[i] = selectedUnits.get(i).getID();
        }

        if(Vars.isClient()){
            if(Vars.netClient != null){
                Vars.netClient.sendUnitCommand(Packets.TYPE_STANCE, ids, stance.id, 0f, 0f, false);
            }
        }else{
            applySetStance(playerTeam(), ids, stance);
            if(Vars.isServer() && Vars.net != null){
                Packets.UnitCommandPacket packet = new Packets.UnitCommandPacket();
                packet.type = Packets.TYPE_STANCE;
                packet.unitIds = ids;
                packet.commandId = stance.id;
                Vars.net.send(packet, com.phoenix.game.net.Net.SendMode.tcp);
            }
        }
    }

    /** @see #setUnitStance（权威应用）。 */
    public static void applySetStance(com.phoenix.game.game.Team team, int[] unitIds, com.phoenix.game.ai.UnitStance stance){
        for(int i = 0; i < unitIds.length; i++){
            com.phoenix.game.entities.type.BaseUnit unit = findUnitById(unitIds[i]);
            if(unit == null || unit.isDead() || unit.getTeam() != team || !unit.isCommandable()) continue;

            com.phoenix.game.ai.types.CommandAI ai = unit.command();
            if(stance == com.phoenix.game.ai.UnitStance.stop){
                ai.clearCommands();
            }else{
                //toggle：已开启则关闭（对应原版 stance.toggle 语义的最小实现）
                ai.setStance(stance, !ai.hasStance(stance));
            }
        }
    }

    /** @return 按 id 找单位（本工程单位在单个全局数组里，线性扫描）。 */
    public static com.phoenix.game.entities.type.BaseUnit findUnitById(int id){
        for(int i = 0; i < com.phoenix.game.entities.Units.units.size; i++){
            com.phoenix.game.entities.type.BaseUnit unit = com.phoenix.game.entities.Units.units.get(i);
            if(unit.getID() == id) return unit;
        }
        return null;
    }

    /** @return 当前视野的世界范围 {minX, maxX, minY, maxY}。 */
    public float[] cameraBounds(){
        Vector3 min = tmp3.set(0f, 0f, 0f);
        Core.camera.unproject(min);
        Vector3 max = tmp3.set(Core.graphics.getWidth(), Core.graphics.getHeight(), 0f);
        Core.camera.unproject(max);
        return new float[]{Math.min(min.x, max.x), Math.max(min.x, max.x), Math.min(min.y, max.y), Math.max(min.y, max.y)};
    }

    // ---- 渲染（对应原版 drawUnitSelection / drawCommanded / drawCommand，由 Renderer 每帧调用）----

    /** 框选拖动时的半透明矩形 + 框内单位高亮（对应原版 {@code drawUnitSelection()}）。 */
    public void drawUnitSelection(){
        if(!commandMode || !commandRect || Vars.world == null) return;

        float x = Math.min(commandRectX, targetX), y = Math.min(commandRectY, targetY);
        float w = Math.abs(targetX - commandRectX), h = Math.abs(targetY - commandRectY);

        if(w > 0.1f && h > 0.1f){
            Draw.color(Pal.accent);
            Draw.alpha(0.3f);
            Fill.rect(x, y, w, h);
        }

        //框内单位实时高亮（对应原版拖框时的脉动多边形）
        for(int i = 0; i < com.phoenix.game.entities.Units.units.size; i++){
            com.phoenix.game.entities.type.BaseUnit unit = com.phoenix.game.entities.Units.units.get(i);
            if(canCommand(unit) && unit.x >= x && unit.x <= x + w && unit.y >= y && unit.y <= y + h){
                drawCommand(unit, Pal.accent);
            }
        }
    }

    /** 选中单位高亮圈（对应原版 {@code drawCommand(Unit)}：脉动六边形）。 */
    public void drawCommand(com.phoenix.game.entities.type.BaseUnit unit, com.badlogic.gdx.graphics.Color color){
        Draw.color(color);
        Lines.stroke(1f);
        Lines.poly(unit.x, unit.y, 6, unit.getSize() * 0.9f + 2f + Mathf.absin(Time.time, 4f, 1f));
    }

    /**
     * 选中单位的指示：高亮圈 + 到目的地的连线 + 目的地标记 / 攻击目标红叉
     * （对应原版 {@code drawCommanded()} 的最小实现，不含编队扇形光）。
     */
    public void drawCommanded(){
        if(selectedUnits.size == 0 || Vars.world == null) return;

        for(int i = 0; i < selectedUnits.size; i++){
            com.phoenix.game.entities.type.BaseUnit unit = selectedUnits.get(i);
            drawCommand(unit, Pal.accent);

            com.phoenix.game.ai.types.CommandAI ai = unit.commandAI;
            if(ai == null) continue;

            if(ai.attackTarget != null && !ai.attackTarget.isDead()){
                //攻击目标：暗红线 + 红叉
                Draw.color(Pal.remove);
                Draw.alpha(0.7f);
                Lines.line(unit.x, unit.y, ai.attackTarget.getX(), ai.attackTarget.getY());
                drawCross(ai.attackTarget.getX(), ai.attackTarget.getY());
            }else if(ai.targetPos != null){
                //移动目的地：暗指挥色连线 + 目的地方块
                Draw.color(Pal.command);
                Draw.alpha(0.7f);
                Lines.line(unit.x, unit.y, ai.targetPos.x, ai.targetPos.y);
                Lines.square(ai.targetPos.x, ai.targetPos.y, 3.5f);

                //命令队列逐段连线（对应原版队列绘制）
                Vector2 prev = ai.targetPos;
                for(int j = 0; j < ai.commandQueue.size; j++){
                    Vector2 next = ai.commandQueue.get(j);
                    Lines.line(prev.x, prev.y, next.x, next.y);
                    Lines.square(next.x, next.y, 3.5f);
                    prev = next;
                }
            }
        }
    }

    /** 画一个红叉（攻击目标标记，对应原版 {@code Drawf.target} 的最小实现）。 */
    private void drawCross(float x, float y){
        Lines.line(x - 4f, y - 4f, x + 4f, y + 4f);
        Lines.line(x - 4f, y + 4f, x + 4f, y - 4f);
    }

    /** 玩家移动向量（对应原版 Player.movement），长度不超过 1 */
    protected final Vector2 movement = new Vector2();
    /** 鼠标世界坐标（对应原版 pointerX/pointerY） */
    public float targetX, targetY;

    /** 屏幕坐标 → 世界坐标用的临时向量 */
    private static final Vector3 tmp3 = new Vector3();

    /** 每帧调用一次。 */
    public void update(){
    }

    /** 进入游戏时把输入处理器切换为自己。实际输入路由由 ClientLauncher 统一用 InputMultiplexer 管理（stage + 本处理器）。 */
    public void use(){
    }

    // ---- 基础输入查询（对应 arc 的 Core.input）----

    /** @return 水平移动轴：-1 / 0 / 1（对应 Binding.move_x） */
    public float axisX(){
        return (keyDown(Input.Keys.D) || keyDown(Input.Keys.RIGHT) ? 1f : 0f)
                + (keyDown(Input.Keys.A) || keyDown(Input.Keys.LEFT) ? -1f : 0f);
    }

    /** @return 垂直移动轴：-1 / 0 / 1（对应 Binding.move_y） */
    public float axisY(){
        return (keyDown(Input.Keys.W) || keyDown(Input.Keys.UP) ? 1f : 0f)
                + (keyDown(Input.Keys.S) || keyDown(Input.Keys.DOWN) ? -1f : 0f);
    }

    /** @return 当前本地玩家水平输入（网络上报用；无输入处理器时为 0）。 */
    public static float movementX(){
        return Vars.control != null && Vars.control.input != null ? Vars.control.input.axisX() : 0f;
    }

    /** @return 当前本地玩家垂直输入（网络上报用；无输入处理器时为 0）。 */
    public static float movementY(){
        return Vars.control != null && Vars.control.input != null ? Vars.control.input.axisY() : 0f;
    }

    public boolean keyDown(int key){
        return Gdx.input.isKeyPressed(key);
    }

    public boolean keyTap(int key){
        return Gdx.input.isKeyJustPressed(key);
    }

    /** @return 鼠标 X（屏幕左边缘为 0） */
    public float mouseX(){
        return Gdx.input.getX();
    }

    /** @return 鼠标 Y（下边缘为 0，与 arc 的约定一致） */
    public float mouseY(){
        return Core.graphics.getHeight() - Gdx.input.getY();
    }

    /** @return 本帧滚轮量并清零（对应 arc 的 axisTap(SCROLL)）。 */
    public float scroll(){
        float amount = scrollAmount;
        scrollAmount = 0f;
        return amount;
    }

    @Override
    public boolean scrolled(float amountX, float amountY){
        //libgdx 向上滚动为正值，对应 Mindustry 的放大
        scrollAmount += amountY;
        return false;
    }

    // ---- 坐标换算 ----

    /** @return 屏幕 X 对应的世界 X（libgdx 的 unproject 内部会处理屏幕 Y 翻转）。 */
    public float mouseWorldX(float screenX){
        tmp3.set(screenX, 0f, 0f);
        Core.camera.unproject(tmp3);
        return tmp3.x;
    }

    /** @return 屏幕 Y 对应的世界 Y（screenY 为 libgdx 原始值，原点在上方）。 */
    public float mouseWorldY(float screenY){
        tmp3.set(0f, screenY, 0f);
        Core.camera.unproject(tmp3);
        return tmp3.y;
    }

    /** 每帧把鼠标位置换算成世界坐标（瞄准/建造共用）。 */
    public void updateTarget(){
        if(Core.camera == null) return;

        targetX = mouseWorldX(Gdx.input.getX());
        targetY = mouseWorldY(Gdx.input.getY());
    }

    // ---- 玩家单位控制（对应原版 Player.updateKeyboard / updateShooting）----

    /** WASD 驱动玩家单位移动；玩家死亡时不做任何事（此时 WASD 用于平移相机）。 */
    public void updateMovement(){
        Player player = Vars.player;
        if(player == null || player.isDead()) return;

        BaseUnit unit = player.unit();
        movement.set(axisX(), axisY());

        float speed = player.isBoosting ? unit.getType().maxVelocity * boostMultiplier : unit.getType().maxVelocity;

        if(movement.isZero()){
            //松开方向键后快速停下（速度衰减由 drag 负责）
            unit.velocity().scl(0.6f);
        }else{
            movement.nor();
            unit.velocity().set(movement.x * speed, movement.y * speed);
        }
    }

    /** 鼠标瞄准与射击：按住左键朝光标开火，未射击时朝移动方向转身。
     * 联机时只更新瞄准点与朝向，开火由服务器权威执行（不本地生成子弹）。 */
    public void updateShooting(){
        Player player = Vars.player;
        if(player == null || player.isDead()) return;

        BaseUnit unit = player.unit();
        Weapon weapon = unit.getWeapon();
        if(weapon == null) return;

        player.pointerX = targetX;
        player.pointerY = targetY;

        if(player.isShooting){
            unit.rotation = Mathf.slerpDelta(unit.rotation, Angles.angle(unit.x, unit.y, targetX, targetY), 0.25f);
            //本地也开火（对应原版 Player.updateKeyboard → updateShooting）：视觉即时。
            //服务端仍按 PlayerInput 权威开火；它广播的开火事件在射手自己的客户端会被跳过，不会重复生成。
            weapon.update(unit, targetX, targetY);
        }else if(!movement.isZero()){
            unit.rotation = Mathf.slerpDelta(unit.rotation, movement.angle(), 0.13f);
        }
    }

    /** 可切换的玩家单位类型（数字键 1~3 对应下标+1）。 */
    protected final com.badlogic.gdx.utils.Array<com.phoenix.game.type.UnitType> playableTypes =
        com.badlogic.gdx.utils.Array.with(
            com.phoenix.game.content.UnitTypes.dagger,
            com.phoenix.game.content.UnitTypes.crawler,
            com.phoenix.game.content.UnitTypes.titan
        );

    /** 数字键切换玩家单位类型（重生时生效）。 */
    public void updateTypeSwitch(){
        Player player = Vars.player;
        if(player == null) return;

        for(int i = 0; i < playableTypes.size; i++){
            if(keyTap(Input.Keys.NUM_1 + i)){
                player.selectedType = playableTypes.get(i);
            }
        }
    }

    /** 冲刺技能（空格触发）：沿移动方向（无移动则朝瞄准方向）瞬时位移，有冷却。 */
    public void updateDash(){
        Player player = Vars.player;
        if(player == null || player.isDead()) return;

        //冷却计时
        if(player.dashCooldown > 0f){
            player.dashCooldown -= 1f;
        }

        if(keyTap(Input.Keys.SPACE) && player.dashCooldown <= 0f && !movement.isZero()){
            BaseUnit unit = player.unit();
            float ang = movement.angle();
            float dist = 40f;
            unit.x += Angles.trnsx(ang, dist);
            unit.y += Angles.trnsy(ang, dist);
            player.dashCooldown = 60f; //1 秒
        }
    }
}
