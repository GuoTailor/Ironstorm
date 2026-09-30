package com.phoenix.game.world.blocks.units;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.GridPoint2;
import com.phoenix.game.content.UnitTypes;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Drawf;
import com.phoenix.game.core.Lines;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.graphics.Shaders;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.type.UnitType;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.StatUnit;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockFlag;

import java.util.EnumSet;

import static com.phoenix.game.Vars.tilesize;

/**
 * 最小实现：单位工厂。参照 Mindustry mindustry.world.blocks.units.UnitFactory 移植。
 * <p>消耗材料+电力在自身旁边生产本队单位；出厂单位走与波次单位相同的 AI（寻路 → 攻打敌方核心），
 * 用 {@link BaseUnit#getSpawner()} 记录出厂点，从而限制同一工厂的在场单位数。
 * <p>简化点：没有出厂烟雾与发射速度。
 * <p>{@code Shaders.build}（建造剪影 + accent 扫描线）已接线（S7a）；另有一层本项目新增的生产进度条
 * （红=缺料 / 灰=满员 / 亮色=生产中），便于玩家一眼看出工厂状态。
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
    public void setStats(){
        super.setStats();

        //对应原版 UnitFactory.setStats：工厂不存物品，去掉物品容量，改列生产时间与单位上限
        stats.remove(BlockStat.itemCapacity);
        stats.add(BlockStat.productionTime, produceTime / 60f, StatUnit.seconds);
        stats.add(BlockStat.maxUnits, maxSpawn, StatUnit.none);
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

    /**
     * @return 在场单位未达上限才消耗资源（对应原版 {@code UnitFactory.shouldConsume} 的 {@code spawned < maxSpawn}）。
     * <p>本项目不维护 {@code spawned} 计数（会改存档实体格式），改用等价的 {@link UnitFactoryEntity#aliveCount()}
     * 全量统计；满员时 {@code ConsumeModule} 不再扣料/占用电网需求。
     */
    @Override
    public boolean shouldConsume(Tile tile){
        return !(tile.entity instanceof UnitFactoryEntity) || ((UnitFactoryEntity)tile.entity).aliveCount() < maxSpawn;
    }

    /** 绘制建造剪影 + accent 扫描线 + 工厂上半部分 + 生产进度条。 */
    @Override
    public void drawLayer(Tile tile){
        if(!(tile.entity instanceof UnitFactoryEntity)) return;

        UnitFactoryEntity entity = (UnitFactoryEntity)tile.entity;

        //原版 UnitFactory.java:124-148 draw(Tile)：先画本体 → 建造剪影（Shaders.build）→ accent 扫描线 → 再画 topRegion。
        //本项目方块本体由 BlockRenderer 负责，drawLayer 只画上层，所以这里从"建造剪影"开始。
        UnitType type = unitType();
        if(type != null){
            TextureRegion region = type.icon(Cicon.full);

            if(region != null){
                //本项目 Shaders 是懒初始化（原版在 Renderer 构造器里建好）→ 画之前补一次幂等初始化
                Shaders.init();

                if(Shaders.build != null){
                    //原版 UnitFactory.java:127-145：region/progress/color(=accent, a=speedScl)/time 直接取实体字段
                    Shaders.build.region = region;
                    Shaders.build.progress = produceTime <= 0f ? 1f : Mathf.clamp(entity.buildTime / produceTime);
                    Shaders.build.color.set(Pal.accent);
                    Shaders.build.color.a = entity.speedScl;
                    Shaders.build.time = -entity.time / 20f;

                    Draw.shader(Shaders.build);
                    //⚠ libgdx 没有 arc 的 Shader.apply() 钩子 → 显式灌 uniform
                    //（此时 SpriteBatch.setShader 已在绘制中 flush 并 bind 了该 shader，setUniformf 才生效）
                    Shaders.build.applyUniforms();
                    Draw.rect(region, centerX(tile), centerY(tile));
                    Draw.shader();

                    Draw.color(Pal.accent);
                    Draw.alpha(entity.speedScl);
                    //⚠ 几何（Lines）与精灵混排的安全前提：画完几何必须把几何会话收尾
                    //（{@code Drawf.end()} 会把 SpriteBatch 的 program 抢回来，见 Drawf.end 注释）。
                    //少了这一句，后面 topRegion/进度条的精灵会**用几何着色器**绘制 → 整个世界层画花。
                    //原版 arc 的 Lines 直接写共享 Mesh、不切 program，所以原版没有这个问题。
                    Lines.lineAngleCenter(
                        centerX(tile) + Mathf.sin(entity.time, 20f, tilesize / 2f * size - 2f),
                        centerY(tile), 90, size * tilesize - 4f);
                    Drawf.end();
                    //本项目 Draw 没有 reset()，用 color() 复位颜色（等价）
                    Draw.color();
                }
            }
        }

        //上半部分贴图（原版会随生产进度旋转，这里静态绘制）
        if(topRegion != null){
            float s = size * tilesize;
            Core.batch.draw(topRegion, centerX(tile) - s / 2f, centerY(tile) - s / 2f, s, s);
        }

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
        /**
         * 建造剪影的相位（对应原版 {@code UnitFactoryEntity.time}，`UnitFactory.java:193`）：
         * 通电时按 `delta * speedScl * unitBuildSpeedMultiplier * efficiency` 累加，驱动扫描线滚动。
         */
        public float time;
        /**
         * 生产速度倍率 0~1（对应原版 {@code UnitFactoryEntity.speedScl}，`UnitFactory.java:194`）：
         * 通电时向 1 渐升、断电时向 0 渐降，只用于建造剪影/扫描线的透明度。
         * <p>⚠ 刻意不参与存档（原版会写进实体流）：纯视觉字段，读档后从 0 渐升即可，
         * 写进存档会改动实体格式 → 需要再升一次 {@code SaveIO.version}，不值得。
         */
        public float speedScl;

        @Override
        public void update(){
            UnitType type = unitType();
            if(type == null || isDead()) return;

            //供电不足时暂停生产（对应原版 consumes.power）
            float status = power == null ? 1f : power.status;
            boolean powered = status > 0.001f;

            //原版 UnitFactory.java:162-168：通电时累加 time 并把 speedScl 渐升到 1，断电时渐降到 0
            //（speedScl 只影响剪影/扫描线的透明度，不参与生产逻辑）
            if(powered){
                time += Time.delta() * speedScl * com.phoenix.game.Vars.state.rules.unitBuildSpeedMultiplier * status;
                speedScl = Mathf.lerpDelta(speedScl, 1f, 0.05f);
            }else{
                speedScl = Mathf.lerpDelta(speedScl, 0f, 0.05f);
                return;
            }

            //达到在场上限或材料不足时暂停（材料留在库存里等攒够）
            //⚠ 原版把"在场上限"检查放在 update 开头；本项目 aliveCount() 是 O(单位数) 的全量统计，
            //  放在供电检查之后可以省掉"断电时的全量扫描"（代价：上限时 speedScl 仍会渐升，纯视觉、可忽略）
            if(aliveCount() >= maxSpawn || !hasInputs()) return;

            //耗电越多、节奏越接近 1 时生产越快；供应不足按满足率减速
            //工厂生产速度倍率（对应原版 entity.buildTime += delta * efficiency() * state.rules.unitBuildSpeedMultiplier）
            buildTime += Time.delta() * status * com.phoenix.game.Vars.state.rules.unitBuildSpeedMultiplier;

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
