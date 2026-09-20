package com.phoenix.game.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Geometry;
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
    /** 当前放置朝向（0=+y, 1=+x, 2=-y, 3=-x），R 键循环。 */
    public int buildRotation;

    /** @return 是否处于建造/拆除模式 */
    public boolean isPlacing(){
        return buildBlock != null || breaking;
    }

    /** 退出建造/拆除模式（对应原版 Binding.deselect）。 */
    public void clearBuild(){
        buildBlock = null;
        breaking = false;
        clearLine();
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

    /** 逐格放置连线上的方块（对应原版 flushRequests）：位置非法或材料不足的格子自然放置失败。
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

            Team team = playerTeam();
            for(int i = 0; i < lineSize; i++){
                LineRequest req = lines[i];
                Build.placeBlock(Vars.world.tile(req.x, req.y), buildBlock, team, req.rotation);
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
