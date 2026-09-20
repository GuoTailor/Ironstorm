package com.phoenix.game.world.blocks.units;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.GridPoint2;
import com.phoenix.game.content.UnitTypes;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.type.UnitType;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockFlag;

import java.util.EnumSet;

import static com.phoenix.game.Vars.tilesize;

/**
 * 最小实现：单位工厂。参照 Mindustry mindustry.world.blocks.units.UnitFactory 移植。
 * <p>消耗材料+电力在自身旁边生产本队单位；出厂单位走与波次单位相同的 AI（寻路 → 攻打敌方核心），
 * 用 {@link BaseUnit#getSpawner()} 记录出厂点，从而限制同一工厂的在场单位数。
 * <p>简化点：没有出厂烟雾与发射速度。
 */
public class UnitFactory extends Block{
    /** 生产的单位类型（直接引用） */
    public UnitType unitType;
    /** 单位类型名：Blocks 早于 UnitTypes 加载，用它延迟解析 */
    public String unitTypeName;
    /** 生产一个单位所需 tick */
    public float produceTime = 850f;
    /** 同时存在的出厂单位上限（对应原版 maxSpawn） */
    public int maxSpawn = 4;
    /** 生产消耗的材料（对应原版 consumes.items） */
    public ItemStack[] inputItem = {};
    /** 上半部分贴图（<name>-top）：工厂的画面上层，原版是随生产旋转的转子 */
    public TextureRegion topRegion;

    public UnitFactory(String name){
        super(name);

        update = true;
        solid = true;
        destructible = true;
        health = 350;
        size = 2;
        hasItems = true;
        itemCapacity = 30;
        hasPower = true;
        powerConsumption = 0.10f;
        flags = EnumSet.of(BlockFlag.unitFactory);
        entityType = UnitFactoryEntity::new;
    }

    @Override
    public void load(){
        super.load();
        topRegion = Core.atlas == null ? null : Core.atlas.findRegion(name + "-top");
    }

    /** @return 生产的单位类型；名字是延迟解析的（Blocks 先于 UnitTypes 加载） */
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

    /** 只接收生产所需材料，其余物品拒收（避免堵塞库存）。 */
    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        for(ItemStack stack : inputItem){
            if(stack.item == item){
                return super.acceptItem(item, tile, source);
            }
        }
        return false;
    }

    /** 绘制工厂上半部分与生产进度条（对应原版工厂的 topRegion 绘制）。 */
    @Override
    public void drawLayer(Tile tile){
        if(!(tile.entity instanceof UnitFactoryEntity)) return;

        //上半部分贴图（原版会随生产进度旋转，这里静态绘制）
        if(topRegion != null){
            float s = size * tilesize;
            Core.batch.draw(topRegion, centerX(tile) - s / 2f, centerY(tile) - s / 2f, s, s);
        }

        UnitFactoryEntity entity = (UnitFactoryEntity)tile.entity;
        if(produceTime <= 0f) return;

        TextureRegion white = Core.atlas == null ? null : Core.atlas.findRegion("white");
        if(white == null) return;

        //进度条常驻显示，并按状态着色，玩家一眼能看出工厂在干什么：
        // 红底   = 缺材料（需要硅）
        // 灰满条 = 在场单位已达上限
        // 亮色条 = 正在生产
        boolean missing = !entity.hasInputs();
        boolean full = entity.aliveCount() >= maxSpawn;
        float progress = entity.progress();
        float w = size * tilesize * 0.7f, h = 2f;
        float x = centerX(tile) - w / 2f;
        float y = tile.worldy() + offset() + size * tilesize - 2f;

        if(missing){
            Core.batch.setColor(0.45f, 0.06f, 0.06f, 0.85f);
        }else{
            Core.batch.setColor(0f, 0f, 0f, 0.55f);
        }
        Core.batch.draw(white, x, y, w, h);

        Core.batch.setColor(full ? Color.GRAY : Pal.accent);
        Core.batch.draw(white, x, y, w * (full ? 1f : progress), h);
        Core.batch.setColor(Color.WHITE);
    }

    /** 工厂实体：攒够材料与时间就出厂一个单位。 */
    public class UnitFactoryEntity extends TileEntity{
        /** 生产进度（tick） */
        public float buildTime;

        @Override
        public void update(){
            UnitType type = unitType();
            if(type == null || isDead()) return;

            //供电不足时暂停生产（对应原版 consumes.power）
            float status = power == null ? 1f : power.status;
            if(status <= 0.001f) return;

            //达到在场上限或材料不足时暂停（材料留在库存里等攒够）
            if(aliveCount() >= maxSpawn || !hasInputs()) return;

            //耗电越多、节奏越接近 1 时生产越快；供应不足按满足率减速
            buildTime += Time.delta() * status;

            if(buildTime >= produceTime){
                buildTime = 0f;
                spawnUnit(type);
            }
        }

        /** @return 本工厂出厂且仍存活的单位数量（对应原版 entity.spawned 的统计口径） */
        public int aliveCount(){
            int count = 0;
            for(int i = 0; i < Units.units.size; i++){
                if(Units.units.get(i).getSpawner() == tile.pos()){
                    count++;
                }
            }
            return count;
        }

        /** @return 生产进度 0~1（绘制进度条用） */
        public float progress(){
            return produceTime <= 0f ? 1f : Mathf.clamp(buildTime / produceTime);
        }

        /** @return 库存是否满足生产所需材料（绘制状态条与外部查询用） */
        public boolean hasInputs(){
            if(inputItem.length == 0) return true;
            if(items == null) return false;

            for(ItemStack stack : inputItem){
                if(!items.has(stack.item, stack.amount)) return false;
            }
            return true;
        }

        /** 扣材料，并在工厂外侧生成一个本队单位。 */
        private void spawnUnit(UnitType type){
            for(ItemStack stack : inputItem){
                if(items != null){
                    items.remove(stack.item, stack.amount);
                }
            }

            //出厂位置：多格外侧第 2 圈的可通行瓦片（工厂本身是实心的，避免单位一出厂就贴着建筑）
            float sx = centerX(tile), sy = centerY(tile);
            int reach = (size + 1) / 2 + 1;

            for(GridPoint2 dir : Geometry.d4){
                Tile near = tile.getNearby(dir.x * reach, dir.y * reach);
                if(near != null && !near.solid()){
                    sx = near.worldx() + tilesize / 2f;
                    sy = near.worldy() + tilesize / 2f;
                    break;
                }
            }

            BaseUnit unit = type.create();
            unit.setTeam(getTeam());
            unit.setSpawner(tile);
            unit.set(sx + Mathf.range(3f), sy + Mathf.range(3f));
            unit.health(unit.maxHealth());
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeFloat(buildTime);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            buildTime = in.readFloat();
        }
    }
}
