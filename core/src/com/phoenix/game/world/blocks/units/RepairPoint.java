package com.phoenix.game.world.blocks.units;

import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockFlag;

import java.util.EnumSet;

import static com.phoenix.game.Vars.tilesize;

/**
 * 维修点。参照 Mindustry mindustry.world.blocks.units.RepairPoint 移植。
 * <p>每 20 帧在半径内找**最近的一个受伤友军单位**，锁定后持续回血（消耗电力）；
 * 目标满血/死亡/离开范围就解除锁定。
 * <p>未移植：激光连线绘制（原版 drawLayer2 会画一条到目标的激光）。
 */
public class RepairPoint extends Block{
    /** 索敌间隔（帧）。 */
    public static final float targetInterval = 20f;

    /** 维修半径（像素）。 */
    public float repairRadius = 50f;
    /** 每帧回血量。 */
    public float repairSpeed = 0.3f;

    public RepairPoint(String name){
        super(name);
        update = true;
        solid = true;
        destructible = true;
        health = 160;
        hasPower = true;
        flags = EnumSet.of(BlockFlag.repair);
        entityType = RepairPointEntity::new;
    }

    /** 只有锁定了目标才耗电（对应原版 {@code consumes.powerCond}）。 */
    @Override
    public float getPowerNeeded(Tile tile){
        if(tile.entity instanceof RepairPointEntity && ((RepairPointEntity)tile.entity).target != null){
            return powerConsumption;
        }
        return 0f;
    }

    public class RepairPointEntity extends TileEntity{
        /** 当前锁定的维修目标。 */
        public BaseUnit target;
        /** 激光强度（0~1，用于绘制与视觉反馈）。 */
        public float strength;
        /** 炮口朝向（度）。 */
        public float rotation = 90f;
        /** 索敌计时。 */
        public float targetTimer;

        @Override
        public void update(){
            //注意用方块几何中心，不是瓦片左下角（原版同样取 tile.drawx()/drawy()）
            float cx = block.centerX(tile), cy = block.centerY(tile);

            //目标失效就解除锁定
            if(target != null && (target.isDead() || Mathf.dst(target.x, target.y, cx, cy) > repairRadius
                || target.health() >= target.maxHealth())){
                target = null;
            }

            boolean repairing = false;
            if(target != null && hasPower()){
                target.healBy(repairSpeed * delta() * strength * (power == null ? 1f : power.status));
                rotation = Mathf.slerpDelta(rotation, Angles.angle(cx, cy, target.x, target.y), 0.5f);
                repairing = true;
            }

            strength = Mathf.lerpDelta(strength, repairing ? 1f : 0f, (repairing ? 0.08f : 0.07f) * delta());

            targetTimer += delta();
            if(targetTimer >= targetInterval){
                targetTimer = 0f;
                target = findTarget();
            }
        }

        /**
         * @return 是否有电可修。
         * <p>原版用 {@code consumes.powerCond(powerUse, e -> target != null)} 表达"锁定目标才耗电"，
         * 本工程的 {@code Consumers} 还没有条件消耗器，所以耗电量由
         * {@link RepairPoint#getPowerNeeded} 按"有没有目标"上报给电网，这里再自己判一次供电率。
         */
        private boolean hasPower(){
            return power == null || power.status > 0.0001f;
        }

        /** @return 半径内最近的一个受伤友军单位；没有则 null。 */
        private BaseUnit findTarget(){
            BaseUnit best = null;
            float bestDst = Float.MAX_VALUE;
            float cx = block.centerX(tile), cy = block.centerY(tile);

            for(int i = 0; i < Units.units.size; i++){
                BaseUnit unit = Units.units.get(i);
                if(unit.isDead() || unit.getTeam() != getTeam()) continue;
                if(unit.health() >= unit.maxHealth()) continue;

                float dst = Mathf.dst(unit.x, unit.y, cx, cy);
                if(dst <= repairRadius && dst < bestDst){
                    best = unit;
                    bestDst = dst;
                }
            }
            return best;
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeFloat(rotation);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            rotation = in.readFloat();
            //target/strength/targetTimer 都是每帧重算的（目标不入档，读档后重新锁定）
            target = null;
            strength = 0f;
            targetTimer = 0f;
        }
    }
}
