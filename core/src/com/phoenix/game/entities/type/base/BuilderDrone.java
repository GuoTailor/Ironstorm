package com.phoenix.game.entities.type.base;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntIntMap;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Events;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.traits.BuilderTrait;
import com.phoenix.game.entities.traits.TargetTrait;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.entities.units.UnitState;
import com.phoenix.game.game.Teams;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.struct.Queue;
import com.phoenix.game.util.Pack;
import com.phoenix.game.world.Build;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.BuildRequest;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.BuildBlock;
import com.phoenix.game.world.blocks.BuildBlock.BuildEntity;

import java.io.*;

import static com.phoenix.game.Vars.world;

/**
 * 建造无人机。参照 Mindustry mindustry.entities.type.base.BuilderDrone 移植。
 * <p>默认状态 {@code build}：跟着玩家跑，玩家在建造时它飞过去把 {@link BuildRequest} 排进自己的队列，
 * 由 {@link BuilderTrait#updateBuilding()} 真正推进建造进度。
 *
 * <p>刻意偏离 / 未移植依赖（都在方法内用 TODO 标注）：
 * <ul>
 *   <li>{@code playerGroup.all()} → **本批已接**：{@code Vars.playerGroup} 早已存在（{@code ClientLauncher}
 *       建组、{@code NetClient} 重建远程玩家），原注释称"只有本地玩家/多人列表未移植"已过时 →
 *       现按原版遍历 {@code Vars.playerGroup.all()}（单人时退化为只有本地玩家）。</li>
 *   <li>{@code unitGroup.all()} → {@link Units#units}。</li>
 *   <li>{@code StaticReset}（原版的"静态监听只注册一次"工具）→ 换成一个 static boolean。</li>
 *   <li>{@code Events.on(BuildSelectEvent.class, ...)} → <b>F-LOW 批已接</b>（BuildSelectEvent 已补进
 *       EventType，派发点在 {@code BuilderTrait.updateBuilding}）：对方块开始反方向建造时停手，见构造器。</li>
 *   <li>{@code team.data().brokenBlocks} → **本批已接**：本项目 {@code Teams} 没有 {@code TeamData} 包装类，
 *       改用 {@code state.teams.brokenBlocks(team)}（对应原版 {@code team.data().brokenBlocks}）→
 *       已补上"自动重建被拆建筑"的逻辑（见 {@link #update()}）。</li>
 *   <li>{@code Player.id} → **本批已接**：{@code Player.id} 早已存在（原注释称"未移植"已过时），
 *       不再用 {@code System.identityHashCode} 当 key。</li>
 *   <li>{@code Player.isValid() / buildRequest()} → 本项目 Player 只持有被控单位，改查 {@code unit()}。</li>
 * </ul>
 */
public class BuilderDrone extends BaseDrone implements BuilderTrait{
    /** 对应原版 StaticReset：保证静态监听只注册一次（原版用 Core.settings 的版本号，这里简化为一次性开关）。 */
    private static boolean resetChecked;
    private static final IntIntMap totals = new IntIntMap();

    protected Queue<BuildRequest> placeQueue = new Queue<>();
    protected BuildRequest lastFound;
    protected boolean isBreaking;
    protected Player playerTarget;

    public final UnitState

    build = new UnitState(){

        public void entered(){
            if(!(target instanceof BuildEntity)){
                target = null;
            }
        }

        public void update(){
            BuildEntity entity = (BuildEntity)target;
            Tile core = getClosestCore();

            if(isBuilding() && entity == null && canRebuild()){
                BuildRequest request = buildRequest();
                target = world.tile(request.x, request.y);
                circle(BuilderTrait.placeDistance * 0.7f);
                target = null;

                if(world.tile(request.x, request.y).entity instanceof BuildEntity){
                    target = world.tile(request.x, request.y).entity;
                }
            }else if(entity != null && core != null && (entity.progress < 1f || entity.progress > 0f) && entity.tile.block() instanceof BuildBlock){ //building is valid
                if(!isBuilding() && dst(target) < BuilderTrait.placeDistance * 0.9f){ //within distance, begin placing
                    if(isBreaking){
                        buildQueue().addLast(new BuildRequest(entity.tile.x, entity.tile.y));
                    }else{
                        buildQueue().addLast(new BuildRequest(entity.tile.x, entity.tile.y, entity.tile.rotation(), entity.cblock));
                        if(lastFound != null && lastFound.hasConfig){
                            buildQueue().last().configure(lastFound.config);
                        }
                    }
                }

                circle(BuilderTrait.placeDistance * 0.7f);
                velocity.scl(0.74f);
            }else{ //else, building isn't valid, follow a player
                target = null;

                //原版遍历 playerGroup.all()（本项目 Vars.playerGroup 已存在，含本地 + 联机远程玩家）
                if(playerTarget == null || playerTarget.getTeam() != team || !isValidPlayer(playerTarget)){
                    playerTarget = null;

                    if(retarget()){
                        float minDst = Float.POSITIVE_INFINITY;
                        int minDrones = Integer.MAX_VALUE;

                        Array<Player> players = Vars.playerGroup == null ? null : Vars.playerGroup.all();

                        if(players != null){
                            //下标循环（libgdx Array 的 for-each 迭代器不能嵌套）
                            for(int i = 0; i < players.size; i++){
                                Player player = players.get(i);
                                if(player.getTeam() != team) continue;

                                int drones = getDrones(player);
                                //本项目 Player 不是单位 → 位置取它当前控制的单位（原版直接 dst2(player)）
                                float dst = player.unit() != null ? dst2(player.unit()) : Float.POSITIVE_INFINITY;

                                if(playerTarget == null || drones < minDrones || (drones == minDrones && dst < minDst)){
                                    minDrones = drones;
                                    minDst = dst;
                                    playerTarget = player;
                                }
                            }
                        }
                    }

                    //原版用 getSpawner()（出生点实体）；本项目 BaseUnit.getSpawner() 返回 int 坐标，改用最近出生点瓦片
                    Tile spawner = getClosestSpawner();
                    if(spawner != null){
                        target = spawner;
                        circle(40f);
                        target = null;
                    }
                }else{
                    incDrones(playerTarget);
                    TargetTrait prev = target;
                    target = playerTarget == null ? null : playerTarget.unit();
                    float dst = 90f + (id % 10)*3;
                    float tdst = dst(target);
                    float scale = (Mathf.lerp(1f, 0.2f, 1f - Mathf.clamp((tdst - dst) / dst)));
                    circle(dst);
                    velocity.scl(scale);
                    target = prev;
                }
            }
        }
    };

    public BuilderDrone(){
        if(!resetChecked){
            resetChecked = true;
            //F-LOW 批按原版 BuilderDrone.java:112-127 恢复：对方块开始（ opposite 方向的）建造时停手。
            //原版 unitGroup.all() → 本项目 Units.units（类注释的既有映射）；
            //⚠ 原版 getTeam() 闭包捕获的是"监听者自己"（首个 BuilderDrone）的队伍 —— 照抄该行为。
            Events.on(com.phoenix.game.game.EventType.BuildSelectEvent.class, event -> {
                if(!(event.tile.entity instanceof BuildEntity)) return;
                for(int i = 0; i < Units.units.size; i++){
                    BaseUnit unit = Units.units.get(i);
                    if(unit instanceof BuilderDrone && unit.getTeam() == getTeam()){
                        BuilderDrone drone = (BuilderDrone)unit;
                        if(drone.isBuilding()){
                            BuildRequest req = drone.buildRequest();
                            if(req.breaking != event.breaking && req.x == event.tile.x && req.y == event.tile.y){
                                drone.clearBuilding();
                                drone.target = null;
                            }
                        }
                    }
                }
            });
        }
    }

    /** 对应原版 {@code player.isValid()}（本项目 Player 只是"被控单位的持有者"）。 */
    private static boolean isValidPlayer(Player player){
        return player != null && !player.isDead();
    }

    /** @return 该玩家当前挂着的建造单位数。 */
    int getDrones(Player player){
        return Pack.leftShort(totals.get(playerKey(player), 0));
    }

    void incDrones(Player player){
        int key = playerKey(player);
        int num = totals.get(key, 0);
        int amount = Pack.leftShort(num), frame = Pack.rightShort(num);
        short curFrame = (short)(Core.graphics.getFrameId() % Short.MAX_VALUE);

        if(frame != curFrame){
            totals.put(key, Pack.shortInt((short)1, curFrame));
        }else{
            totals.put(key, Pack.shortInt((short)(amount + 1), curFrame));
        }
    }

    /** 对应原版 {@code totals.get(player.id)}（本项目 {@code Player.id} 已存在，不再用对象标识哈希）。 */
    private static int playerKey(Player player){
        return player.id;
    }

    boolean canRebuild(){
        return true;
    }

    @Override
    public float getBuildPower(Tile tile){
        return type.buildPower;
    }

    @Override
    public Queue<BuildRequest> buildQueue(){
        return placeQueue;
    }

    @Override
    public void update(){
        super.update();

        if(!isBuilding() && timer.get(timerTarget2, 15)){
            //原版遍历 playerGroup.all()（本项目建造队列挂在玩家单位上，Player 没有 buildRequest()）
            Array<Player> players = Vars.playerGroup == null ? null : Vars.playerGroup.all();

            if(players != null){
                for(int i = 0; i < players.size; i++){
                    Player player = players.get(i);
                    if(player.getTeam() != team || player.unit() == null) continue;

                    BuildRequest req = player.unit().buildRequest();
                    if(req == null) continue;

                    Tile tile = world.tile(req.x, req.y);
                    if(tile != null && tile.entity instanceof BuildEntity){
                        BuildEntity b = (BuildEntity)tile.entity;
                        float dist = Math.min(b.dst(x, y) - BuilderTrait.placeDistance, 0);
                        if(dist / type.maxVelocity < b.buildCost * 0.9f){
                            lastFound = req;
                            target = b;
                            this.isBreaking = req.breaking;
                            setState(build);
                            break;
                        }
                    }
                }
            }

            //原版 BuilderDrone.java:188-197：从 team.data().brokenBlocks 取最后一条被拆建筑，校验后插到建造队列头部自动重建。
            //本项目 Teams 没有 TeamData → 用 state.teams.brokenBlocks(team) 代替 team.data().brokenBlocks。
            if(timer.get(timerTarget, 80) && Units.closestEnemy(getTeam(), x, y, 100f, u -> !(u instanceof BaseDrone)) == null && !isBuilding()){
                Array<Teams.BrokenBlock> broken = (Vars.state == null || Vars.state.teams == null)
                    ? null : Vars.state.teams.brokenBlocks(getTeam());
                if(broken != null && broken.size > 0){
                    //原版 removeLast()；libgdx Array 没有 removeLast → 用 pop()（同为"取出并移除末尾"）
                    Teams.BrokenBlock block = broken.pop();
                    Block rebuildBlock = Vars.content.block(block.block);
                    //原版 Build.validPlace(getTeam(), block.x, block.y, block, block.rotation)（5 参重载）；
                    //本项目没有该重载 → 先取瓦片再调 4 参版（传 team，带上禁建/敌方核心半径校验）
                    Tile rebuildTile = world.tile(block.x, block.y);
                    if(rebuildBlock != null && rebuildTile != null
                        && Build.validPlace(rebuildTile, rebuildBlock, block.rotation, getTeam())){
                        placeQueue.addFirst(new BuildRequest(block.x, block.y, block.rotation, rebuildBlock).configure(block.config));
                        setState(build);
                    }
                }
            }
        }

        updateBuilding();
    }

    @Override
    public boolean shouldRotate(){
        return isBuilding();
    }

    @Override
    public UnitState getStartState(){
        return build;
    }

    /** 绘制建造预览（对应原版 {@code Unit.drawOver()}；由 {@code Renderer.drawUnits()} 的 drawOver 通道调用）。 */
    @Override
    public void drawOver(){
        drawBuilding();
    }

    @Override
    public float drawSize(){
        return isBuilding() ? BuilderTrait.placeDistance * 2f : 30f;
    }

    @Override
    public boolean canCreateBlocks(){
        return true;
    }

    @Override
    public void write(DataOutput data) throws IOException{
        super.write(data);
        writeBuilding(data);
    }

    @Override
    public void read(DataInput data) throws IOException{
        super.read(data);
        readBuilding(data);
    }
}
