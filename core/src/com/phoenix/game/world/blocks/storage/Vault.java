package com.phoenix.game.world.blocks.storage;

/**
 * 大型仓库。参照 Mindustry mindustry.world.blocks.storage.Vault 移植。
 * <p>3x3、容量 1000；其余行为与 {@link StorageBlock} 一致（不主动外送、可被核心链接）。
 */
public class Vault extends StorageBlock{
    public Vault(String name){
        super(name);
        size = 3;
        itemCapacity = 1000;
        //不写 health：原版 Vault 构造器不设 health（走 size*size*40 → size3 = 360）。
        //⚠ 本项目 StorageBlock 硬编码 health = 120，size 默认不可达 → health 由 Blocks.java 显式给 360。
    }
}
