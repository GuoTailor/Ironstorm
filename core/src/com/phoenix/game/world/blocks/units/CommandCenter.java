package com.phoenix.game.world.blocks.units;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.meta.BlockFlag;

import java.util.EnumSet;

/**
 * 指挥中心（**遗留空壳**）。对应 Mindustry v7/v8 的 {@code LegacyCommandCenter}：
 * v7 起旧的全队指令机制（{@code TeamData.command} + 单位状态机）被 RTS 命令系统
 * （{@code UnitCommand} 内容 + {@code CommandAI} + 输入侧选人下命令）取代，指挥中心方块
 * 退役为兼容旧地图/存档的空壳 —— 保留注册名 {@code command-center}、碰撞与集结点
 * （{@link BlockFlag#rally}）语义，不再有任何配置界面和指令下发逻辑。
 *
 * <p>新机制入口：Q 键进入命令模式 → 框选单位 → 右键下达移动/攻击命令
 * （见 {@code InputHandler.commandTap} / {@code com.phoenix.game.ai.types.CommandAI}）。
 */
public class CommandCenter extends Block{
    public CommandCenter(String name){
        super(name);
        destructible = true;
        solid = true;
        size = 2;
        health = size * size * 55;
        //旧机制已退役：不再是可配置方块（对应 v8 LegacyCommandCenter 无逻辑）
        configurable = false;
        flags = EnumSet.of(BlockFlag.rally, BlockFlag.commandCenter);
        entityType = CommandCenterEntity::new;
    }

    /**
     * 旧指令下发逻辑（同步到全队指挥中心 + 广播 {@code unit.onCommand}）已随 RTS 命令系统
     * 的引入整体移除 —— 对应 v8 提交 {@code c324f2124b} 删除旧 CommandCenter。
     * 旧 v6 实现保留在 git 历史（本文件旧版本）中。
     */

    /** 遗留实体：只保留 1 字节的占位数据布局（与旧版存档/BlockState 字节流对齐）。 */
    public static class CommandCenterEntity extends TileEntity{
        @Override
        public int config(){
            return -1;
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeByte(0);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            in.readUnsignedByte(); //旧版指令序号占位，读掉保持流对齐
        }
    }
}
