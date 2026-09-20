package com.phoenix.game.world.blocks.sandbox;

import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.world.Block;

/**
 * 电力虚空（沙盒）。参照 Mindustry mindustry.world.blocks.sandbox.PowerVoid 移植：把电网里多余的电吃掉。
 * <p>**与原版的差异**：原版写 {@code consumes.power(Float.MAX_VALUE)}，靠"需求极大"来吸电；
 * 但本工程的满足率是 {@code produced / needed}，需求量取 MAX_VALUE 会把整张电网的满足率压到 0，
 * 连带饿死同网的其他用电器。这里改为**每帧直接把本网电池里的电抽走**（和二极管搬电用的是同一套接口），
 * 既不污染满足率，效果也更贴近"虚空"的语义。
 */
public class PowerVoid extends Block{
    /** 每帧抽走的电量。 */
    public float powerUsage = 10000f;

    public PowerVoid(String name){
        super(name);
        sandboxOnly = true;
        update = true;
        solid = true;
        destructible = true;
        health = 160;
        hasPower = true;
        entityType = PowerVoidEntity::new;
    }

    public class PowerVoidEntity extends TileEntity{
        @Override
        public void update(){
            if(power == null || power.graph == null) return;
            //直接抽干本网电池；没有电池时无事可做（虚空只吃存量，不制造需求）
            power.graph.useBatteries(powerUsage * delta());
        }
    }
}
