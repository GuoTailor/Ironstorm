package com.phoenix.game.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.GameState;
import com.phoenix.game.core.Time;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.Mathf;
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
        if(Vars.hud != null && keyTap(Input.Keys.M)){
            Vars.hud.minimapFragment().toggle();
        }

        //全屏小地图打开时让出输入：本类是轮询式（Gdx.input.isButtonPressed），
        //光靠 Stage 消费事件拦不住，不加这一步拖拽平移地图会误建造/误开火。
        //（原版靠 arc 的 scene.hasScroll() + 事件式输入实现，phoenix 没有等价物。）
        if(Vars.hud != null && Vars.hud.minimapFullShown()){
            scroll(); //丢弃本帧累积的滚轮量，否则关闭地图后相机会突然缩放
            if(player != null) player.isShooting = false;
            return;
        }

        //视角缩放（对应 Mindustry 的 Binding.zoom + renderer.scaleCamera）
        float zoomAmount = scroll();
        if(Math.abs(zoomAmount) > 0.0001f && Vars.renderer != null){
            Vars.renderer.scaleCamera(zoomAmount);
        }

        //鼠标世界坐标（瞄准与建造共用）
        updateTarget();

        //玩家死亡或暂停时：WASD 平移相机（对应原版 DesktopInput.update 的相机部分）
        if(player == null || player.isDead() || Vars.state.isPaused()){
            float camSpeed = !keyDown(Input.Keys.SHIFT_LEFT) ? 3f : 8f;

            Tmp.v1.set(axisX(), axisY());
            if(!Tmp.v1.isZero()){
                Tmp.v1.nor().scl(Time.delta() * camSpeed);
                Core.camera.position.x += Tmp.v1.x;
                Core.camera.position.y += Tmp.v1.y;
            }

            //按住鼠标侧键：相机朝鼠标方向移动
            if(Gdx.input.isButtonPressed(Input.Buttons.BACK)){
                Core.camera.position.x += Mathf.clamp((mouseX() - Core.graphics.getWidth() / 2f) * 0.005f, -1f, 1f) * camSpeed;
                Core.camera.position.y += Mathf.clamp((mouseY() - Core.graphics.getHeight() / 2f) * 0.005f, -1f, 1f) * camSpeed;
            }
        }

        //玩家单位控制（存活且未暂停）：Shift 加速 + WASD 移动 + 鼠标瞄准射击
        if(player != null && !player.isDead() && !Vars.state.isPaused()){
            player.isBoosting = keyDown(Input.Keys.SHIFT_LEFT);

            updateMovement();
            updateShooting();
        }

        //数字键切换玩家单位类型（重生时生效）
        updateTypeSwitch();
        //冲刺技能（空格）
        updateDash();

        //松开左键停止射击（对应原版 keyRelease(Binding.select)）
        if(player != null && !Gdx.input.isButtonPressed(Input.Buttons.LEFT)){
            player.isShooting = false;
        }

        //F11 切换全屏（对应 Binding.fullscreen）
        if(keyTap(Input.Keys.F11)){
            toggleFullscreen();
        }

        //空格暂停/继续（对应 Binding.pause）
        if(keyTap(Input.Keys.SPACE)){
            Vars.state.set(Vars.state.isPaused() ? GameState.State.playing : GameState.State.paused);
        }

        //Tab 切换 HUD 建造菜单，Esc 取消当前建造/拆除（对应 Mindustry toggle_menus/deselect）
        if(keyTap(Input.Keys.TAB) && Vars.hud != null){
            Vars.hud.toggleMenus();
        }
        if(keyTap(Input.Keys.ESCAPE) && isPlacing()){
            clearBuild();
        }

        //R 键切换放置朝向
        if(keyTap(Input.Keys.R)){
            buildRotation = (buildRotation + 1) % 4;
        }

        //按住 Ctrl 强制直线连线（否则可旋转方块会走 L 形路径、自动拐弯）
        straightLine = keyDown(Input.Keys.CONTROL_LEFT) || keyDown(Input.Keys.CONTROL_RIGHT);

        //拖拽中：按当前鼠标位置刷新连线路径（对应原版每帧检查 cursor 变化后 updateLine）
        if(lineMode){
            if(!isPlacing() || buildBlock == null){
                clearLine();
            }else{
                updateLine(targetX, targetY);
            }
        }

        //拆除模式：按住左键拖动可沿路径连续拆除（快速拖动也不会漏格）
        if(breaking && Vars.world != null && !Vars.state.isPaused() && Gdx.input.isButtonPressed(Input.Buttons.LEFT)){
            int tx = Vars.world.toTile(targetX), ty = Vars.world.toTile(targetY);

            if(lastBreakX == Integer.MIN_VALUE){
                lastBreakX = tx;
                lastBreakY = ty;
            }else if(tx != lastBreakX || ty != lastBreakY){
                int count = buildPath(lastBreakX, lastBreakY, tx, ty, true);
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
     * 左键：建造模式下开始连线（拖拽可连放，松手才建造）、拆除模式下拆除方块，否则开始射击
     * （对应原版 DesktopInput 的“非建造状态按下 select 即开火”）。
     * 右键：退出建造/拆除模式（对应原版 Binding.deselect）。
     */
    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button){
        if(button == Input.Buttons.RIGHT){
            clearBuild();
            return false;
        }

        if(button != Input.Buttons.LEFT) return false;

        if(isPlacing() && Vars.world != null){
            if(breaking){
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

        if(Vars.player != null && !Vars.player.isDead()){
            Vars.player.isShooting = true;
        }

        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button){
        if(button == Input.Buttons.LEFT){
            //松开左键：把连线上的方块一次性放下（对应原版 flushRequests）
            if(lineMode){
                flushLine();
                if(Sounds.place != null){
                    Sounds.place.play(1f);
                }
            }
            if(Vars.player != null){
                Vars.player.isShooting = false;
            }
        }
        return false;
    }

    /** 在世界坐标放置当前 `buildBlock`：校验位置、扣除材料，可连续放置。
     * 联机时改为向服务器发建造请求（服务端权威执行，等 BlockState 确认）。 */
    public void placeWorld(int tileX, int tileY){
        if(buildBlock == null || Vars.world == null) return;

        //联机：发请求，不做本地执行（避免与服务端状态分叉）
        if(Vars.netClient != null && Vars.netClient.isConnected()){
            Vars.netClient.sendBuild(tileX, tileY, buildBlock, buildRotation);
            return;
        }

        //用玩家（sharded）阵营放置；多格建筑以中心瓦片铺开
        if(Build.placeBlock(Vars.world.tile(tileX, tileY), buildBlock, Team.sharded, buildRotation)){
            if(Sounds.place != null){
                Sounds.place.play(1f);
            }
        }
    }

    /** 在世界坐标拆除方块（只能拆自己阵营的、可破坏的方块），返还一半材料。
     * 联机时改为向服务器发拆除请求。 */
    public void breakWorld(int tileX, int tileY){
        if(Vars.world == null) return;

        //联机：发请求，不做本地执行
        if(Vars.netClient != null && Vars.netClient.isConnected()){
            Vars.netClient.sendDeconstruct(tileX, tileY);
            return;
        }

        if(Build.deconstruct(Vars.world.tile(tileX, tileY), Team.sharded)){
            if(Sounds.breaks != null){
                Sounds.breaks.play(1f);
            }
        }
    }

    /** 全屏切换：退出全屏时恢复默认窗口尺寸。 */
    public void toggleFullscreen(){
        if(Gdx.graphics.isFullscreen()){
            Gdx.graphics.setWindowedMode(defaultWidth, defaultHeight);
        }else{
            Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        }
    }

}
