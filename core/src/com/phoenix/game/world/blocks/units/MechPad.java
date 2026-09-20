package com.phoenix.game.world.blocks.units;

import com.phoenix.game.Vars;
import com.phoenix.game.content.UnitTypes;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.UnitType;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockFlag;

import java.util.EnumSet;

import static com.phoenix.game.Vars.tilesize;

/**
 * 机甲平台。参照 Mindustry mindustry.world.blocks.units.MechPad 移植。
 * <p>站在平台上点击它，平台会用 {@link #buildTime} 帧"造"出该型号并把你换成它
 * （对应原版换机甲：建造期间玩家被固定在平台上，完成后以新机甲重生）。
 * <p>与原版的差异：原版换的是 {@code Mech}（带独立的血量/武器/建造力），
 * 本工程没有 Mech 系统，改为**直接换成对应的 {@link UnitType}**（即 {@code player.selectedType}）。
 * <p>未移植：平台上的重生进度动画、Fx.spawn 特效。
 */
public class MechPad extends Block{
    /** 要换成的单位类型（直接引用）。 */
    public UnitType unitType;
    /** 单位类型名：Blocks 早于 UnitTypes 加载，用它延迟解析。 */
    public String unitTypeName;
    /** 换机甲所需帧数（原版 5 秒）。 */
    public float buildTime = 60f * 5f;

    public MechPad(String name){
        super(name);
        update = true;
        //平台本身不挡路（原版 solid = false）
        solid = false;
        destructible = true;
        health = 160;
        size = 2;
        hasPower = true;
        flags = EnumSet.of(BlockFlag.mechPad);
        entityType = MechPadEntity::new;
    }

    /** @return 要换成的单位类型；名字是延迟解析的。 */
    public UnitType unitType(){
        if(unitType == null && unitTypeName != null){
            for(UnitType type : UnitTypes.all){
                if(type.name.equals(unitTypeName)){
                    unitType = type;
                    break;
                }
            }
        }
        return unitType;
    }

    /** 点击平台：开始换机甲。 */
    @Override
    public boolean tapped(Tile tile, Player player){
        if(!(tile.entity instanceof MechPadEntity) || player == null || player.isDead()) return false;
        if(!validTap(tile, player)) return false;

        ((MechPadEntity)tile.entity).player = player;
        return true;
    }

    /** 能否开始换：同队、玩家贴着平台、有电、平台当前空闲。 */
    private boolean validTap(Tile tile, Player player){
        if(tile.getTeam() != player.getTeam()) return false;

        MechPadEntity entity = (MechPadEntity)tile.entity;
        if(entity == null || entity.player != null) return false;
        if(!entity.cons.valid()) return false;

        float cx = centerX(tile), cy = centerY(tile);
        return Math.abs(player.getX() - cx) <= size * tilesize && Math.abs(player.getY() - cy) <= size * tilesize;
    }

    public class MechPadEntity extends TileEntity{
        /** 正在换机甲的玩家；null 表示空闲。 */
        public Player player;
        /** 换装进度 0~1。 */
        public float progress;
        /** 预热（绘制用）。 */
        public float heat;

        @Override
        public void update(){
            if(player == null || player.isDead() || player.unit() == null){
                player = null;
                progress = 0f;
                heat = Mathf.lerpDelta(heat, 0f, 0.1f * delta());
                return;
            }

            UnitType type = unitType();
            if(type == null){
                player = null;
                return;
            }

            //建造期间把玩家固定在平台中心（对应原版 entity.player.set(tile.drawx(), tile.drawy())）
            float cx = block.centerX(tile), cy = block.centerY(tile);
            player.unit().set(cx, cy);
            player.unit().velocity().set(0f, 0f);

            heat = Mathf.lerpDelta(heat, 1f, 0.1f * delta());
            progress += delta() / buildTime;

            if(progress >= 1f){
                progress = 0f;
                Player target = player;
                player = null;

                if(Vars.control != null){
                    BaseUnit unit = Vars.control.spawnPlayerUnitAt(target.getTeam(), type, cx, cy);
                    if(unit != null) unit.health(unit.maxHealth());
                }
            }
        }
    }
}
