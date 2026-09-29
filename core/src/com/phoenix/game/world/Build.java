package com.phoenix.game.world;

import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.world.blocks.BuildBlock;
import com.phoenix.game.world.modules.ItemModule;

/**
 * 建造/拆除规则。参照 Mindustry mindustry.world.Build 移植。
 * <p>两条路径：
 * <ul>
 *     <li>{@link #beginPlace}：铺 {@link BuildBlock} 占位方块，交给建造单位逐帧推进（正常玩法走这条）；</li>
 *     <li>{@link #placeBlock} / {@link #deconstruct}：立即完成，供读档/服务端补建/自动化验证使用。</li>
 * </ul>
 * 材料从队伍共享库存中扣除与返还（原版走核心/建造单位）。
 */
public class Build{
    /** 拆除返还比例（对应原版 rules.deconstructRefundMultiplier）。 */
    public static float refundMultiplier = 0.5f;
    /**
     * 玩家建造花费倍率（对应原版 {@code rules.buildCostMultiplier}）。
     * <p>与原版的 {@code refundMultiplier} 同款做法：静态字段，由 {@code Control} 在开局/读档时从
     * {@code rules.buildCostMultiplier} 赋值（原版是每次读 {@code state.rules}，本项目集中一份）。
     */
    public static float costMultiplier = 1f;

    /** @return 该材料在花费倍率下的实际需求数量（对应原版 {@code Math.round(rules.buildCostMultiplier * amount)}）。 */
    public static int scaled(ItemStack stack){
        return Math.round(costMultiplier * stack.amount);
    }

    /** 队伍共享库存（材料来源）。 */
    public static ItemModule items(Team team){
        return Vars.state.teams.items(team);
    }

    /**
     * 是否走"逐帧建造"队列（{@link #beginPlace}），否则走即时建造（{@link #placeBlock}）。
     * <p>只有**联机客机**不走本地队列：它把请求发给服务端（{@code Packets.BuildRequest}），
     * 由服务端的玩家单位权威执行同一套队列逻辑，再把"在建方块"的状态（含进度）广播回来
     * （见 {@code NetServer.sync} 里的在建方块广播）。
     * <p>单机与主机都走队列 —— 主机本身就是服务端，本地执行即权威，不需要绕一圈。
     */
    public static boolean useBuildQueue(){
        return !Vars.isClient();
    }

    /** 是否负担得起该方块的材料。 */
    public static boolean canAfford(Team team, Block block){
        if(block == null || block.requirements.length == 0) return true;

        ItemModule inv = items(team);
        for(ItemStack stack : block.requirements){
            if(stack.item == null || !inv.has(stack.item, scaled(stack))) return false;
        }
        return true;
    }

    /**
     * 放置校验：瓦片为空，多格建筑还要求覆盖范围内所有瓦片都为空，
     * 最后过 {@link Block#canPlaceOn}（钻头要求脚下有矿等地形条件，对应原版）。
     * <p>例外：**同种可旋转方块换一个朝向**时允许直接覆盖，对应原版
     * {@code Block.canReplace} 与 {@code Build.validPlace} 里的
     * {@code !(type == tile.block() && rotation == tile.rotation() && type.rotate)} 排除项。
     * 这样玩家能在已放好的传送带上原地转方向，不必先拆再放；
     * 朝向完全相同则仍然拒绝，避免无意义的重复放置。
     * @param rotation 将要放置的朝向（0=上/1=右/2=下/3=左）
     * @return 是否可以放置
     */
    public static boolean validPlace(Tile tile, Block block, int rotation){
        return validPlace(tile, block, rotation, null);
    }

    /**
     * 带队伍上下文的放置校验（对应原版 {@code Build.validPlace(Team, int, int, Block, int)}）。
     * <p>比 3 参版本多两项原版校验：{@code rules.bannedBlocks} 禁建方块（敌方波次阵营不受限，与原版一致）、
     * 以及 {@code rules.enemyCoreBuildRadius} 敌方核心周围禁建半径。
     * @param team 放置方阵营；传 {@code null} 表示"无队伍上下文"（幽灵预览等）→ 禁建方块仍对所有方生效，
     *             但敌方核心半径校验会跳过（无法判定敌我，对应原版 team 恒非 null 的调用口径）。
     */
    public static boolean validPlace(Tile tile, Block block, int rotation, Team team){
        if(tile == null || block == null || block == Blocks.air) return false;

        //禁建方块（对应原版 Build.validPlace 开头的 state.rules.bannedBlocks.contains(type)）：
        //敌方波次阵营不受限（原版 !(state.rules.waves && team == state.rules.waveTeam)）
        com.phoenix.game.game.Rules rules = Vars.state == null ? null : Vars.state.rules;
        if(rules != null && rules.bannedBlocks.contains(block)
            && !(rules.waves && team == rules.waveTeam)) return false;

        //敌方核心禁建半径（对应原版 state.teams.eachEnemyCore(team, core -> Mathf.dst(...) < radius + size*tilesize/2f)）。
        //本项目用 closestEnemyCore（语义等价：**最近**的敌方核心不在圈内 ⇒ 所有敌方核心都不在圈内）。
        //⚠ `Teams.eachEnemyCore` 与核心列表（`Teams.cores`）**都已存在**（S3 批次）；这里仍用 closestEnemyCore
        //是因为"最近的不在圈内"能一次判定全部，无需遍历。另按 radius>0 短路（对应原版 Gamemode.editor/sandbox
        //把该值设 0 的语义，同时省掉绝大多数放置校验的开销）。
        if(rules != null && team != null && rules.enemyCoreBuildRadius > 0f){
            float wx = tile.worldx() + block.offset(), wy = tile.worldy() + block.offset();
            Tile enemyCore = Vars.state.teams.closestEnemyCore(wx, wy, team);
            if(enemyCore != null
                && Mathf.dst(wx, wy, enemyCore.getX(), enemyCore.getY())
                    < rules.enemyCoreBuildRadius + block.size * Vars.tilesize / 2f){
                return false;
            }
        }

        if(!block.canPlaceOn(tile)) return false;

        Block previous = tile.block();
        boolean replaceSame = previous == block && block.rotate && (rotation & 3) != tile.rotation();

        if(block.isMultiblock()){
            //与 Tile.setBlock 的铺开范围保持一致（支持偶数尺寸）
            int offsetx = -(block.size - 1) / 2, offsety = -(block.size - 1) / 2;

            for(int dx = 0; dx < block.size; dx++){
                for(int dy = 0; dy < block.size; dy++){
                    Tile other = tile.getNearby(dx + offsetx, dy + offsety);
                    if(other == null || other.block() != Blocks.air) return false;
                    //深液格只有浮空方块（抽水机等）能覆盖，对应原版 Build.validPlace 的 type.floating 判据
                    if(isDeep(other.floor()) && !block.floating) return false;
                }
            }
            return true;
        }

        //深液格只有浮空方块能建（对应原版 (!tile.floor().isDeep() || type.floating)）
        if(isDeep(tile.floor()) && !block.floating) return false;

        return previous == Blocks.air || replaceSame;
    }

    /** @return 该地板是否为深液（对应原版 {@code Floor.isDeep()}：是液体且会淹没）。 */
    private static boolean isDeep(Floor floor){
        return floor != null && floor.isLiquid && floor.drownTime > 0f;
    }

    /**
     * 扣除材料并放置方块。
     * @return true 表示放置成功
     */
    public static boolean placeBlock(Tile tile, Block block, Team team, int rotation){
        if(!validPlace(tile, block, rotation, team) || !canAfford(team, block)) return false;

        ItemModule inv = items(team);
        for(ItemStack stack : block.requirements){
            inv.remove(stack.item, scaled(stack));
        }

        tile.setBlock(block, team, rotation);
        //放置粒子（对应原版 Fx.placeBlock；原版把方块边长当 e.rotation 用来缩放方框）
        com.phoenix.game.entities.Effects.effect(com.phoenix.game.content.Fx.placeBlock,
            block.centerX(tile), block.centerY(tile), block.size);
        //放置后回调（物品桥靠它自动连接上一次放置的同类桥，对应原版 playerPlaced）
        block.playerPlaced(tile);
        return true;
    }

    /**
     * 在目标格铺上"在建/待拆"占位方块（对应原版 {@code Build.beginPlace} / {@code beginBreak}）。
     * <p>这是建造队列的第一步：把目标格换成一个同尺寸的 {@link BuildBlock}，
     * 并把"要建成什么（cblock）"和"原本是什么（previous）"记进它的实体里，
     * 之后由建造单位逐帧推进度，满了才换成真正的方块。
     * <p>占位方块的尺寸必须取**目标方块**的尺寸（建造时）或**原方块**的尺寸（拆除时），
     * 否则多格建筑占不满格子，拆除时也会剩下残缺的卫星格。
     * @param req 建造或拆除请求
     * @return true 表示占位方块已铺好
     */
    public static boolean beginPlace(Tile tile, BuildRequest req, Team team){
        if(tile == null || req == null) return false;

        Block previous = tile.block();
        Block target = req.breaking ? previous : req.block;
        if(target == null || target == Blocks.air) return false;

        Block buildBlock = BuildBlock.get(target.size);
        tile.setBlock(buildBlock, team, req.rotation);

        if(!(tile.entity instanceof BuildBlock.BuildEntity)) return false;

        BuildBlock.BuildEntity entity = (BuildBlock.BuildEntity)tile.entity;
        if(req.breaking){
            entity.setDeconstruct(previous);
        }else{
            entity.setConstruct(previous, req.block);
        }
        return true;
    }

    /**
     * 是否可以拆除（自己的、可拆的方块）。
     * <p>对应原版 {@code Build.validBreak}（原版 Build.java:149-152）的判据子集：
     * {@code tile.block().canBreak(tile) && tile.breakable() && 队伍校验} —— 原版的
     * {@code interactable(team)} 在本项目等价实现为 {@code tile.getTeam() == team}；
     * {@code canBreak} 是 F10 补的：CoreBlock 覆写 false，此前缺它导致玩家/建造无人机能拆自家核心
     * （玩家拆除 MobileInput、无人机拆线 BuilderTrait/BaseUnit 都走本判据，一处补齐全覆盖）。
     */
    public static boolean canDeconstruct(Tile tile, Team team){
        if(tile == null) return false;

        return tile.block() != Blocks.air && tile.block().canBreak(tile) && tile.breakable() && tile.getTeam() == team;
    }

    /**
     * 拆除方块并按比例返还材料。
     * @return true 表示拆除成功
     */
    public static boolean deconstruct(Tile tile, Team team){
        if(!canDeconstruct(tile, team)) return false;

        Block block = tile.block();

        ItemModule inv = items(team);
        for(ItemStack stack : block.requirements){
            int amount = (int)(scaled(stack) * refundMultiplier);
            if(amount > 0 && stack.item != null){
                inv.add(stack.item, amount);
            }
        }

        //拆除粒子要在 remove 之前取坐标（拆完瓦片上的方块信息就没了）
        float cx = block.centerX(tile), cy = block.centerY(tile);
        tile.remove();
        com.phoenix.game.entities.Effects.effect(com.phoenix.game.content.Fx.breakBlock, block.color, cx, cy, 0f);

        //服务端：即时拆除也要广播移除（否则客机上这一格还留着）
        if(Vars.isServer()){
            Vars.netServer.broadcastBlockRemove(tile);
        }
        return true;
    }

    /** @return 材料的简短描述（如 "12铜" / "4铜+6铅"），无材料返回空串。 */
    public static String costText(Block block, String[] itemNames){
        if(block == null || block.requirements.length == 0) return "";

        StringBuilder sb = new StringBuilder();
        for(ItemStack stack : block.requirements){
            if(sb.length() > 0) sb.append('+');
            sb.append(stack.amount).append(itemNames[stack.item.id]);
        }
        return sb.toString();
    }
}
