package com.phoenix.game.entities.traits;

import com.badlogic.gdx.math.Vector2;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Fill;
import com.phoenix.game.core.Lines;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.struct.Queue;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.util.ArcAnnotate.Nullable;
import com.phoenix.game.world.Build;
import com.phoenix.game.world.BuildRequest;
import com.phoenix.game.world.Pos;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.BuildBlock;
import com.phoenix.game.world.blocks.BuildBlock.BuildEntity;
import com.phoenix.game.world.modules.ItemModule;

import java.io.*;
import java.util.*;

import static com.phoenix.game.Vars.*;

/**
 * Interface for units that build things.
 * <p>参照原版 mindustry.entities.traits.BuilderTrait 移植。
 * <p>**刻意偏离 / 未移植依赖（见下方清单）**：
 * <ul>
 *     <li>原版把 {@code BuildRequest} 作为嵌套类；本项目已有 {@link com.phoenix.game.world.BuildRequest}，直接复用。</li>
 *     <li>原版 {@code (Unit)this} 改成本项目的 {@link BaseUnit}。</li>
 *     <li>{@code Call.beginPlace/beginBreak/onTileConfig}（mindustry.gen.Call）刻意降级 → 本项目单机本地权威，
 *         直接走本地 {@link Build}（不再经网络回环）。</li>
 *     <li>{@code rules.infiniteResources} 本项目 Rules 没有该字段 → 按既定等价表达用 {@code rules.sandbox} 顶替
 *         （见 {@code game.Gamemode}）；{@code rules.buildSpeedMultiplier} 已在
 *         {@code BuildBlock.BuildEntity#progressStep} 内消费（见 {@code BuildBlock.java:342}）。</li>
 *     <li>{@code BuildSelectEvent}（EventType.BuildSelectEvent）**确实未移植**（grep 全工程无该类/该事件）→ 只置 initialized（B 类）。</li>
 *     <li>{@code state.isEditor()}：F5 已补（{@code GameState.isEditor()}）→ {@code drawBuilding} 里那句
 *         {@code dst(tile) > placeDistance && !state.isEditor()} 已照原版接上；
 *         {@code player.buildQueue()} 本项目没有 → 改查本单位队列。</li>
 * </ul>
 */
public interface BuilderTrait extends Entity, TeamTrait{
    //these are not instance variables!
    float placeDistance = 220f;
    float mineDistance = 70f;

    /** Updates building mechanism for this unit. */
    default void updateBuilding(){
        //原版：finalPlaceDst = state.rules.infiniteResources ? Float.MAX_VALUE : placeDistance
        //本项目无 infiniteResources 字段，按既定等价表达用 rules.sandbox 顶替（见 game.Gamemode）
        float finalPlaceDst = infiniteResources() ? Float.MAX_VALUE : placeDistance;
        BaseUnit unit = (BaseUnit)this;
        Queue<BuildRequest> queue = buildQueue();

        //原版用迭代器 remove；这里倒序下标删除，避免 libgdx 迭代器嵌套
        for(int i = queue.size - 1; i >= 0; i--){
            BuildRequest req = queue.get(i);
            Tile tile = Vars.world.tile(req.x, req.y);
            if(tile == null || (req.breaking && tile.block() == Blocks.air) || (!req.breaking && (tile.rotation() == req.rotation || !req.block.rotate) && tile.block() == req.block)){
                queue.removeIndex(i);
            }
        }

        //原版 getClosestCore() 返回 TileEntity；本项目返回核心瓦片（材料走队伍共享库存）
        Tile core = unit.getClosestCore();

        //nothing to build.
        if(buildRequest() == null) return;

        //find the next build request
        if(queue.size > 1){
            int total = 0;
            BuildRequest req;
            while((dst((req = buildRequest()).tile()) > finalPlaceDst || shouldSkip(req, core)) && total < queue.size){
                queue.removeFirst();
                queue.addLast(req);
                total++;
            }
        }

        BuildRequest current = buildRequest();

        if(dst(current.tile()) > finalPlaceDst) return;

        Tile tile = Vars.world.tile(current.x, current.y);
        if(tile == null){
            queue.removeFirst();
            return;
        }

        if(!(tile.block() instanceof BuildBlock)){
            if(!current.initialized && canCreateBlocks() && !current.breaking && Build.validPlace(tile, current.block, current.rotation, getTeam())){
                //刻意降级：原版走 Call.beginPlace(...) 网络调用；本项目单机本地权威 → 直接本地铺占位方块
                Build.beginPlace(tile, current, getTeam());
            }else if(!current.initialized && canCreateBlocks() && current.breaking && Build.canDeconstruct(tile, getTeam())){
                //刻意降级：原版走 Call.beginBreak(...)
                Build.beginPlace(tile, current, getTeam());
            }else{
                queue.removeFirst();
                return;
            }
        }else if(tile.getTeam() != getTeam()){
            queue.removeFirst();
            return;
        }

        if(tile.entity instanceof BuildEntity && !current.initialized){
            //B 类：原版 Events.fire(new BuildSelectEvent(tile, unit.getTeam(), this, current.breaking))；
            //BuildSelectEvent 确实未移植（全工程无该类），只置 initialized
            current.initialized = true;
        }

        //if there is no core to build with or no build entity, stop building!
        if((core == null && !infiniteResources()) || !(tile.entity instanceof BuildEntity)){
            return;
        }

        //otherwise, update it.
        BuildEntity entity = (BuildEntity)tile.entity;

        if(entity == null){
            return;
        }

        if(unit.dst(tile) <= finalPlaceDst){
            unit.rotation = Mathf.slerpDelta(unit.rotation, unit.angleTo(entity), 0.4f);
        }

        ItemModule inventory = Build.items(getTeam());
        //原版：1f / entity.buildCost * Time.delta() * getBuildPower(tile) * state.rules.buildSpeedMultiplier
        //本项目 BuildEntity.progressStep(buildPower) 已内含 Time.delta() 与 rules.buildSpeedMultiplier（见 BuildBlock.java:342）
        float amount = entity.progressStep(getBuildPower(tile));

        if(current.breaking){
            entity.deconstruct(inventory, amount);
        }else{
            if(entity.construct(inventory, amount)){
                //刻意降级：原版此处 Call.onTileConfig(null, tile, current.config)（单机本地权威，无网络回环）
                BuildBlock.constructed(tile, entity.cblock, getTeam(), tile.rotation(), current.hasConfig ? current.config : -1);
            }
        }

        //原版 Mathf.equal(a, b)；本项目 Mathf 没有 equal，这里等价展开
        current.stuck = Math.abs(current.progress - entity.progress) < 0.0001f;
        current.progress = entity.progress;
    }

    /** @return whether this request should be skipped, in favor of the next one. */
    default boolean shouldSkip(BuildRequest request, @Nullable Tile core){
        //原版 state.rules.infiniteResources；本项目用 rules.sandbox 等价表达
        if(infiniteResources() || request.breaking || !request.initialized || core == null) return false;
        if(!request.stuck) return false;

        ItemModule inventory = Build.items(getTeam());
        for(ItemStack stack : request.block.requirements){
            if(!inventory.has(stack.item, stack.amount)) return true;
        }
        return false;
    }

    /**
     * @return 是否无限资源（原版 {@code rules.infiniteResources}）。
     * <p>本项目 Rules 没有该字段，按既定等价表达用 {@code rules.sandbox} 顶替（见 {@code game.Gamemode}）。
     */
    default boolean infiniteResources(){
        return Vars.state != null && Vars.state.rules != null && Vars.state.rules.sandbox;
    }

    default void removeRequest(int x, int y, boolean breaking){
        //刻意等价替代：原版查 player.buildQueue()；本项目 Player 不持有建造队列（Player 不是单位基类），
        //改为查本单位（BuilderTrait 实现者）自己的队列，语义一致
        int idx = buildQueue().indexOf(req -> req.breaking == breaking && req.x == x && req.y == y);
        if(idx != -1){
            buildQueue().removeIndex(idx);
        }
    }

    /** Returns the queue for storing build requests. */
    Queue<BuildRequest> buildQueue();

    /** Build power, can be any float. 1 = builds recipes in normal time, 0 = doesn't build at all. */
    float getBuildPower(Tile tile);

    /** Whether this type of builder can begin creating new blocks. */
    default boolean canCreateBlocks(){
        return true;
    }

    default void writeBuilding(DataOutput output) throws IOException{
        BuildRequest request = buildRequest();

        if(request != null && (request.block != null || request.breaking)){
            output.writeByte(request.breaking ? 1 : 0);
            output.writeInt(Pos.get(request.x, request.y));
            output.writeFloat(request.progress);
            if(!request.breaking){
                //刻意等价替代：原版写 content 里的方块 id（content.block(id) 反查）；本项目 Block 没有 id，改写 Blocks.all 下标
                output.writeShort((short)Blocks.all.indexOf(request.block, true));
                output.writeByte(request.rotation);
            }
        }else{
            output.writeByte(-1);
        }
    }

    default void readBuilding(DataInput input) throws IOException{
        readBuilding(input, true);
    }

    default void readBuilding(DataInput input, boolean applyChanges) throws IOException{
        if(applyChanges) buildQueue().clear();

        byte type = input.readByte();
        if(type != -1){
            int position = input.readInt();
            float progress = input.readFloat();
            BuildRequest request;

            if(type == 1){ //remove
                request = new BuildRequest(Pos.x(position), Pos.y(position));
            }else{ //place
                int blockIndex = input.readShort();
                byte rotation = input.readByte();
                //刻意等价替代：原版 content.block(block) 按内容 id 反查；本项目改用 Blocks.all 下标
                request = new BuildRequest(Pos.x(position), Pos.y(position), rotation,
                    blockIndex < 0 || blockIndex >= Blocks.all.size ? Blocks.air : Blocks.all.get(blockIndex));
            }

            request.progress = progress;

            if(applyChanges){
                buildQueue().addLast(request);
            }else if(isBuilding()){
                BuildRequest last = buildRequest();
                last.progress = progress;
                if(last.tile() != null && last.tile().entity instanceof BuildEntity){
                    ((BuildEntity)last.tile().entity).progress = progress;
                }
            }
        }
    }

    /** Return whether this builder's place queue contains items. */
    default boolean isBuilding(){
        return buildQueue().size != 0;
    }

    /** Clears the placement queue. */
    default void clearBuilding(){
        buildQueue().clear();
    }

    /** Add another build requests to the tail of the queue, if it doesn't exist there yet. */
    default void addBuildRequest(BuildRequest place){
        addBuildRequest(place, true);
    }

    /** Add another build requests to the queue, if it doesn't exist there yet. */
    default void addBuildRequest(BuildRequest place, boolean tail){
        Queue<BuildRequest> queue = buildQueue();
        BuildRequest replace = null;
        for(int i = 0; i < queue.size; i++){
            BuildRequest request = queue.get(i);
            if(request.x == place.x && request.y == place.y){
                replace = request;
                break;
            }
        }
        if(replace != null){
            queue.remove(replace);
        }
        Tile tile = Vars.world.tile(place.x, place.y);
        if(tile != null && tile.entity instanceof BuildEntity){
            place.progress = ((BuildEntity)tile.entity).progress;
        }
        if(tail){
            queue.addLast(place);
        }else{
            queue.addFirst(place);
        }
    }

    /**
     * Return the build requests currently active, or the one at the top of the queue.
     * May return null.
     */
    default @Nullable BuildRequest buildRequest(){
        return buildQueue().size == 0 ? null : buildQueue().first();
    }

    //due to iOS weirdness, this is apparently required
    class BuildDataStatic{
        static Vector2[] tmptr = new Vector2[]{new Vector2(), new Vector2(), new Vector2(), new Vector2()};
    }

    /** Draw placement effects for an entity. */
    default void drawBuilding(){
        if(!isBuilding()) return;

        BaseUnit unit = (BaseUnit)this;
        BuildRequest request = buildRequest();
        Tile tile = Vars.world.tile(request.x, request.y);

        if(tile == null) return;

        //原版：dst(tile) > placeDistance && !state.isEditor()（F5 已补 GameState.isEditor()）
        if(dst(tile) > placeDistance && !Vars.state.isEditor()){
            return;
        }

        //原版 Lines.stroke(1f, Pal.accent)；本项目 Lines 只有单参 stroke(width)，颜色走 Draw.color
        Draw.color(Pal.accent);
        Lines.stroke(1f);
        float focusLen = 3.8f + Mathf.absin(Time.time, 1.1f, 0.6f);
        float px = unit.x + Angles.trnsx(unit.rotation, focusLen);
        float py = unit.y + Angles.trnsy(unit.rotation, focusLen);

        float sz = tilesize * tile.block().size / 2f;
        float ang = unit.angleTo(tile);

        Vector2[] tmptr = BuildDataStatic.tmptr;
        tmptr[0].set(tile.block().centerX(tile) - sz, tile.block().centerY(tile) - sz);
        tmptr[1].set(tile.block().centerX(tile) + sz, tile.block().centerY(tile) - sz);
        tmptr[2].set(tile.block().centerX(tile) - sz, tile.block().centerY(tile) + sz);
        tmptr[3].set(tile.block().centerX(tile) + sz, tile.block().centerY(tile) + sz);

        Arrays.sort(tmptr, (a, b) -> -Float.compare(Angles.angleDist(Angles.angle(unit.x, unit.y, a.x, a.y), ang),
                Angles.angleDist(Angles.angle(unit.x, unit.y, b.x, b.y), ang)));

        float x1 = tmptr[0].x, y1 = tmptr[0].y,
                x3 = tmptr[1].x, y3 = tmptr[1].y;

        Draw.alpha(1f);

        Lines.line(px, py, x1, y1);
        Lines.line(px, py, x3, y3);

        Fill.circle(px, py, 1.6f + Mathf.absin(Time.time, 0.8f, 1.5f));

        Draw.color();
    }
}
