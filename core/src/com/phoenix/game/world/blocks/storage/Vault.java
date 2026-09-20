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
        health = 500;
    }
}
