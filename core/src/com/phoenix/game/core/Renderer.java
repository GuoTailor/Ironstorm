package com.phoenix.game.core;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.Vars;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Units;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Block;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Tile;
import com.phoenix.game.core.World;

import static com.phoenix.game.Vars.tilesize;

/**
 * 最小实现：世界渲染器。参照 Mindustry mindustry.core.Renderer 移植。
 * 绘制顺序：地板/方块 -> 单位 -> 子弹 -> 特效。
 * 相机缩放沿用原版模型：targetscale / camerascale + 逐帧插值，范围 [1.5, 6] * Scl.scl(1)。
 */
public class Renderer {
    /** 目标缩放（对应原版 targetscale，默认 Scl.scl(4)） */
    private float targetscale = -1f;
    /** 当前缩放，逐帧插值逼近 targetscale */
    private float camerascale = -1f;

    /** 是否绘制格子边框（调试用） */
    public boolean drawGrid = false;

    /**
     * 小地图渲染器（对应原版 {@code Vars.renderer.minimap}）。
     * <p>只在客户端创建（Renderer 由 ClientLauncher new），headless 服务端不会挂上它的事件监听。
     */
    public final com.phoenix.game.graphics.MinimapRenderer minimap = new com.phoenix.game.graphics.MinimapRenderer();

    /** 每帧调用：缩放插值、相机跟随玩家，并同步到相机。 */
    public void update(){
        initScale();

        camerascale = Mathf.lerpDelta(camerascale, targetscale, 0.1f);

        //相机跟随玩家（对应原版 Renderer.draw 里的 camera.position.lerpDelta(player, 0.08f)）
        if(Vars.player != null && !Vars.player.isDead() && !Vars.state.isPaused() && Core.camera != null){
            Core.camera.position.lerp(Tmp.v31.set(Vars.player.getX(), Vars.player.getY(), 0f), 0.08f);
        }

        applyScale();
    }

    /** 缩放相机（对应原版 Renderer.scaleCamera）。 */
    public void scaleCamera(float amount){
        initScale();
        targetscale += amount;
        clampScale();
    }

    public void clampScale(){
        //先确保已经初始化，否则会把 -1 夹到下限
        initScale();

        float s = Scl.scl(1f);
        targetscale = Mathf.clamp(targetscale, s * 1.5f, Math.round(s * 6));
    }

    public float getScale(){
        return targetscale;
    }

    public void setScale(float scl){
        targetscale = scl;
        clampScale();
    }

    /** @return 当前实际缩放（插值结果） */
    public float cameraScale(){
        initScale();
        return camerascale;
    }

    private void initScale(){
        if(targetscale < 0f){
            targetscale = Scl.scl(4f);
            camerascale = targetscale;
        }
    }

    private void applyScale(){
        if(Core.camera instanceof OrthographicCamera){
            //原版的 camerascale 是放大倍数（1 个世界单位 = camerascale 像素，camera.width = 屏幕宽 / camerascale），
            //libgdx 的 OrthographicCamera.zoom 是缩小倍数（可视世界宽 = 屏幕宽 * zoom），两者互为倒数。
            ((OrthographicCamera)Core.camera).zoom = 1f / camerascale;
            Core.camera.update();
        }
    }

    /** @return 当前可视区域的世界宽度。 */
    private float viewWidth(){
        return Core.camera.viewportWidth / camerascale;
    }

    /** @return 当前可视区域的世界高度。 */
    private float viewHeight(){
        return Core.camera.viewportHeight / camerascale;
    }

    public void draw(){
        World world = Vars.world;
        if(world == null || world.tiles.length == 0) return;

        initScale();

        float ox = 0f, oy = 0f;
        if(Effects.shakeTime > 0f){
            ox = Mathf.random(-1f, 1f) * Effects.shakeIntensity;
            oy = Mathf.random(-1f, 1f) * Effects.shakeIntensity;
        }

        Core.camera.position.add(ox, oy, 0f);
        applyScale();

        Core.batch.setProjectionMatrix(Core.camera.combined);
        Core.batch.begin();

        drawTiles(world);
        drawUnits();
        drawBullets();
        drawPowerLinks(world);
        drawHealthBars(world);
        drawBossHealthBars();
        drawSelectionBox();
        Effects.render();

        Core.batch.end();

        Core.camera.position.sub(ox, oy, 0f);
        Core.camera.update();
    }

    private void drawTiles(World world){
        float viewW = viewWidth();
        float viewH = viewHeight();
        float cx = Core.camera.position.x, cy = Core.camera.position.y;

        int minx = world.toTile(cx - viewW / 2f) - 1;
        int maxx = world.toTile(cx + viewW / 2f) + 1;
        int miny = world.toTile(cy - viewH / 2f) - 1;
        int maxy = world.toTile(cy + viewH / 2f) + 1;

        //第一遍：地板。必须整体先画完，否则多格建筑贴图会被它"上方/右侧"格子的地板盖掉一部分
        //（多格贴图由中心格绘制、会延伸到相邻格，而循环是自下而上/自左向右的）
        for(int y = miny; y <= maxy; y++){
            for(int x = minx; x <= maxx; x++){
                Tile tile = world.tile(x, y);
                if(tile == null) continue;

                Floor floor = tile.floor();
                if(floor != null && floor != Blocks.air){
                    TextureRegion region = floor.variant(tile.x, tile.y);
                    if(region != null){
                        Core.batch.draw(region, tile.x * tilesize, tile.y * tilesize, tilesize, tilesize);
                    }
                }
            }
        }

        //第二遍：方块与叠层（对应原版 Renderer 的 drawFloor/drawBlocks 分离）
        for(int y = miny; y <= maxy; y++){
            for(int x = minx; x <= maxx; x++){
                Tile tile = world.tile(x, y);
                if(tile == null) continue;

                float dx = tile.x * tilesize;
                float dy = tile.y * tilesize;

                //卫星瓦片不单独绘制：整个多格建筑由中心瓦片一次性绘制
                //（注意不能用 block.isHidden() 判断——tile.block() 对卫星瓦片会转发到中心方块）
                Block block = tile.block();
                if(block != null && block != Blocks.air && !tile.isLinked() && !block.isHidden()){
                    //方块本体（默认按 region 铺满；传送带等带朝向/动画的方块自行绘制）
                    block.draw(tile);
                    //叠层内容（如传送带上的物品）
                    block.drawLayer(tile);
                }

                if(drawGrid){
                    Core.batch.setColor(0f, 0f, 0f, 0.15f);
                    Core.batch.draw(region("white"), dx, dy, tilesize, 1f);
                    Core.batch.draw(region("white"), dx, dy, 1f, tilesize);
                    Core.batch.setColor(1f, 1f, 1f, 1f);
                }
            }
        }
    }

    private void drawUnits(){
        //下标遍历，避免 libgdx Array 迭代器嵌套
        for(int i = 0; i < Units.units.size; i++){
            BaseUnit unit = Units.units.get(i);
            if(unit.isDead()) continue;
            unit.draw();
        }
    }

    private void drawBullets(){
        for(int i = 0; i < Bullet.all.size; i++){
            Bullet bullet = Bullet.all.get(i);
            if(bullet.isDead()) continue;
            bullet.draw();
        }
    }

    /** 绘制电网连接线：把相邻的带电建筑连成拓扑（黄色细线）。 */
    private void drawPowerLinks(World world){
        TextureRegion white = region("white");
        if(white == null) return;
        if(world.powerGraphs.size == 0) return;

        for(Tile tile : world.tiles){
            if(tile == null || tile.entity == null || tile.entity.power == null) continue;
            if(tile.link() != tile) continue;

            for(com.badlogic.gdx.math.GridPoint2 dir : com.phoenix.game.math.geom.Geometry.d4){
                Tile next = tile.getNearby(dir.x, dir.y);
                if(next == null || next.entity == null || next.entity.power == null) continue;
                //只画一次（避免重复）：按坐标序比较
                if(next.pos() < tile.pos()) continue;

                float x1 = tile.worldx() + tilesize / 2f, y1 = tile.worldy() + tilesize / 2f;
                float x2 = next.worldx() + tilesize / 2f, y2 = next.worldy() + tilesize / 2f;
                float len = com.badlogic.gdx.math.Vector2.dst(x1, y1, x2, y2);
                float ang = com.phoenix.game.math.Angles.angle(x1, y1, x2, y2);

                Core.batch.setColor(1f, 0.85f, 0.2f, 0.7f); //电力黄
                Core.batch.draw(white, x1, y1, 0f, 0f, len, 2f, 1f, 1f, ang);
                Core.batch.setColor(1f, 1f, 1f, 1f);
            }
        }
    }

    /** 绘制 Boss 单位的专属血条：金色、始终显示（含满血），长于普通血条。 */
    private void drawBossHealthBars(){
        TextureRegion white = region("white");
        if(white == null) return;

        for(int i = 0; i < Units.units.size; i++){
            BaseUnit unit = Units.units.get(i);
            if(unit.isDead() || !unit.getType().boss) continue;

            float bw = 42f, bh = 5f;
            float x = unit.x - bw / 2f;
            float y = unit.y + unit.getType().hitsize + 8f;
            float hp = Mathf.clamp(unit.health() / unit.maxHealth(), 0f, 1f);

            //背景
            Core.batch.setColor(0f, 0f, 0f, 0.65f);
            Core.batch.draw(white, x, y, bw, bh);
            //血条：红色渐变到整体（受击越惨越偏蓝紫）
            Core.batch.setColor(1f - hp * 0.6f, hp, hp, 1f);
            Core.batch.draw(white, x + 1f, y + 1f, (bw - 2f) * hp, bh - 2f);
            Core.batch.setColor(1f, 1f, 1f, 1f);
        }
    }

    /** 绘制受损建筑的浮空血条。 */
    private void drawHealthBars(World world){
        TextureRegion white = region("white");
        if(white == null) return;

        for(Tile tile : world.tiles){
            if(tile == null || tile.entity == null) continue;
            //卫星瓦片不重复绘制（链接回中心）
            if(tile.link() != tile) continue;

            float hp = tile.entity.healthf();
            if(hp <= 0f || hp >= 0.999f) continue;

            float size = tile.block().size * tilesize;
            float x = tile.worldx() + size / 2f;
            float y = tile.worldy() + size + 3f;
            float bw = size + 2f;
            float bh = 3f;

            //背景
            Core.batch.setColor(0f, 0f, 0f, 0.6f);
            Core.batch.draw(white, x - bw / 2f, y, bw, bh);
            //血条
            Core.batch.setColor(1f, hp, hp, 1f);
            Core.batch.draw(white, x - bw / 2f + 1f, y + 1f, (bw - 2f) * hp, bh - 2f);
            Core.batch.setColor(1f, 1f, 1f, 1f);
        }
    }

    /**
     * 绘制建造/拆除预览（对应原版 DesktopInput.drawTop）：
     * 半透明方块贴图（带朝向的会按朝向旋转，传送带因此能直接看出输送方向）+ 朝向箭头，
     * 绿色表示可放置、红色表示位置非法或材料不足。
     */
    private void drawSelectionBox(){
        if(Vars.world == null || Vars.control == null || Vars.control.input == null || Vars.state.isMenu()) return;

        com.phoenix.game.input.InputHandler input = Vars.control.input;
        if(input.buildBlock == null && !input.breaking) return;

        TextureRegion white = region("white");
        if(white == null) return;

        //鼠标世界坐标
        com.badlogic.gdx.math.Vector3 v = tmp3;
        v.set(com.badlogic.gdx.Gdx.input.getX(), com.badlogic.gdx.Gdx.input.getY(), 0);
        Core.camera.unproject(v);
        int tx = Vars.world.toTile(v.x), ty = Vars.world.toTile(v.y);
        Tile tile = Vars.world.tile(tx, ty);
        if(tile == null) return;

        //拆除预览：整个方块范围标红
        if(input.buildBlock == null){
            float s = tile.block().size * tilesize;
            Core.batch.setColor(1f, 0.2f, 0.2f, 0.35f);
            Core.batch.draw(white, tile.worldx() + (tilesize - s) / 2f, tile.worldy() + (tilesize - s) / 2f, s, s);
            Core.batch.setColor(1f, 1f, 1f, 1f);
            return;
        }

        Block block = input.buildBlock;
        com.phoenix.game.game.Team team = Vars.player != null ? Vars.player.getTeam() : com.phoenix.game.game.Team.sharded;

        //拖拽连线：把路径上每一格都画成半透明 ghost（各自带自己的朝向），最后再画一个朝向箭头
        if(input.lineMode && input.lineSize > 0){
            for(int i = 0; i < input.lineSize; i++){
                com.phoenix.game.input.InputHandler.LineRequest req = input.lines[i];
                Tile t = Vars.world.tile(req.x, req.y);
                if(t == null) continue;

                boolean ok = com.phoenix.game.world.Build.validPlace(t, block) && com.phoenix.game.world.Build.canAfford(team, block);
                drawGhost(t, block, req.rotation, ok, white);
            }

            com.phoenix.game.input.InputHandler.LineRequest last = input.lines[input.lineSize - 1];
            Tile lastTile = Vars.world.tile(last.x, last.y);
            if(block.rotate && lastTile != null){
                drawArrow(last.x, last.y, last.rotation,
                        com.phoenix.game.world.Build.validPlace(lastTile, block) && com.phoenix.game.world.Build.canAfford(team, block),
                        block, white);
            }

            Core.batch.setColor(1f, 1f, 1f, 1f);
            return;
        }

        boolean valid = com.phoenix.game.world.Build.validPlace(tile, block) && com.phoenix.game.world.Build.canAfford(team, block);
        int rotation = input.buildRotation & 3;

        //占地格（半透明绿）
        Core.batch.setColor(0.2f, 1f, 0.2f, 0.25f);
        Core.batch.draw(white, tile.worldx(), tile.worldy(), tilesize, tilesize);

        drawGhost(tile, block, rotation, valid, white);

        //朝向箭头（对应原版 DesktopInput.drawArrow）
        if(block.rotate){
            drawArrow(tile.x, tile.y, rotation, valid, block, white);
        }

        Core.batch.setColor(1f, 1f, 1f, 1f);
    }

    /** 画一格半透明方块预览（带朝向的按朝向旋转）。 */
    private void drawGhost(Tile tile, Block block, int rotation, boolean valid, TextureRegion white){
        if(block.region == null) return;

        float size = block.size * tilesize;
        //多格建筑以锚点铺开：几何中心 = 瓦片左下角 + offset + 半格
        float cx = tile.worldx() + block.offset() + tilesize / 2f;
        float cy = tile.worldy() + block.offset() + tilesize / 2f;

        //合法=白，非法=偏红
        Core.batch.setColor(1f, valid ? 1f : 0.25f, valid ? 1f : 0.25f, 0.6f);
        Core.batch.draw(block.region, cx - size / 2f, cy - size / 2f, size / 2f, size / 2f,
                size, size, 1f, 1f, block.rotate ? spriteAngle(rotation) : 0f);
    }

    /** 画朝向箭头（对应原版 DesktopInput.drawArrow）。 */
    private void drawArrow(int tx, int ty, int rotation, boolean valid, Block block, TextureRegion white){
        float cx = tx * tilesize + block.offset() + tilesize / 2f;
        float cy = ty * tilesize + block.offset() + tilesize / 2f;
        float a = spriteAngle(rotation);
        float len = tilesize * 0.7f, hl = tilesize * 0.35f;

        Core.batch.setColor(valid ? com.phoenix.game.graphics.Pal.accent : com.phoenix.game.graphics.Pal.remove);

        //箭杆
        Core.batch.draw(white, cx - len / 2f, cy - 1f, len / 2f, 1f, len, 2f, 1f, 1f, a);

        //箭头（两撇，从箭尖往回各偏 145 度）
        float tipx = cx + com.phoenix.game.math.Angles.trnsx(a, len / 2f);
        float tipy = cy + com.phoenix.game.math.Angles.trnsy(a, len / 2f);
        float bx = tipx - com.phoenix.game.math.Angles.trnsx(a, hl / 2f);
        float by = tipy - com.phoenix.game.math.Angles.trnsy(a, hl / 2f);

        for(int s = -1; s <= 1; s += 2){
            Core.batch.draw(white, bx - hl / 2f, by - 1f, hl / 2f, 1f, hl, 2f, 1f, 1f, a + 145f * s);
        }
    }

    /** 本项目 rotation（0=上/1=右/2=下/3=左）→ 贴图旋转角（建筑贴图基准朝向为“右”，对应原版 rotation*90）。 */
    private static float spriteAngle(int rotation){
        return (1 - (rotation & 3)) * 90f;
    }

    private static final com.badlogic.gdx.math.Vector3 tmp3 = new com.badlogic.gdx.math.Vector3();

    private static TextureRegion region(String name){
        if(Core.atlas == null) return null;
        TextureRegion region = Core.atlas.findRegion(name);
        if(region == null) region = Core.atlas.findRegion("blank");
        return region;
    }
}
