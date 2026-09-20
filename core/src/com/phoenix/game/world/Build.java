package com.phoenix.game.world;

import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.game.Team;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.world.modules.ItemModule;

/**
 * 最小实现：建造/拆除规则。参照 Mindustry mindustry.world.Build 移植。
 * <p>与原版的差异：没有建造进度/建造单位（放置即建成，但按 {@link Block#requirements} 扣材料），
 * 材料从队伍共享库存中扣除与返还（原版走核心/建造单位）。
 */
public class Build{
    /** 拆除返还比例（对应原版 rules.deconstructRefundMultiplier）。 */
    public static float refundMultiplier = 0.5f;

    /** 队伍共享库存（材料来源）。 */
    public static ItemModule items(Team team){
        return Vars.state.teams.items(team);
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
     * 放置校验：瓦片为空，多格建筑还要求覆盖范围内所有瓦片都为空。
     * @return 是否可以放置
     */
    public static boolean validPlace(Tile tile, Block block){
        if(tile == null || block == null || block == Blocks.air) return false;

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
        return true;
    }

    /** 是否可以拆除（自己的、可破坏的方块）。 */
    public static boolean canDeconstruct(Tile tile, Team team){
        if(tile == null) return false;

        Block block = tile.block();
        return block != Blocks.air && block.destructible && tile.getTeam() == team;
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

        tile.remove();
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
