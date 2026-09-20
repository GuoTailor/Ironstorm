package com.phoenix.game.world;

import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.game.Team;
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
            if(stack.item == null || !inv.has(stack.item, stack.amount)) return false;
        }
        return true;
    }

    /**
     * 放置校验：瓦片为空，多格建筑还要求覆盖范围内所有瓦片都为空，
     * 最后过 {@link Block#canPlaceOn}（钻头要求脚下有矿等地形条件，对应原版）。
     * @return 是否可以放置
     */
    public static boolean validPlace(Tile tile, Block block){
        if(tile == null || block == null || block == Blocks.air) return false;
        if(!block.canPlaceOn(tile)) return false;

        if(block.isMultiblock()){
            //与 Tile.setBlock 的铺开范围保持一致（支持偶数尺寸）
            int offsetx = -(block.size - 1) / 2, offsety = -(block.size - 1) / 2;

            for(int dx = 0; dx < block.size; dx++){
                for(int dy = 0; dy < block.size; dy++){
                    Tile other = tile.getNearby(dx + offsetx, dy + offsety);
                    if(other == null || other.block() != Blocks.air) return false;
                }
            }
            return true;
        }

        return tile.block() == Blocks.air;
    }

    /**
     * 扣除材料并放置方块。
     * @return true 表示放置成功
     */
    public static boolean placeBlock(Tile tile, Block block, Team team, int rotation){
        if(!validPlace(tile, block) || !canAfford(team, block)) return false;

        ItemModule inv = items(team);
        for(ItemStack stack : block.requirements){
            inv.remove(stack.item, stack.amount);
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

    /** 是否可以拆除（自己的、可拆的方块）。判据见 {@link Tile#breakable()}。 */
    public static boolean canDeconstruct(Tile tile, Team team){
        if(tile == null) return false;

        return tile.block() != Blocks.air && tile.breakable() && tile.getTeam() == team;
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
            int amount = (int)(stack.amount * refundMultiplier);
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
