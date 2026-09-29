package com.phoenix.game.world.blocks.power;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.core.Strings;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.ui.Bar;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockFlag;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.I18n;
import com.phoenix.game.world.meta.StatUnit;

import java.util.EnumSet;

/**
 * 发电机基类。参照 Mindustry mindustry.world.blocks.power.PowerGenerator 移植。
 *
 * <p>本工程原先把发电机继承链**扁平化**了（各发电机直接 {@code extends Block} 并各自持有
 * {@code powerProduction}）；已收敛回原版继承链：{@code SolarGenerator}/{@code ThermalGenerator}/
 * {@code ImpactReactor}/{@code NuclearReactor}/{@code ItemLiquidGenerator} 及其子类
 * （{@code BurnerGenerator}/{@code DecayGenerator}/{@code SingleTypeGenerator}）都继承本类。
 *
 * <p><b>⚠ 子类必须逐帧（或放置时一次性地）给实体 {@link GeneratorEntity#productionEfficiency} 赋正确值</b>：
 * {@link #getPowerProduction(Tile)} 是 {@code powerProduction * productionEfficiency}，
 * 效率为 0 就等于不发电（与 {@code Block.getPowerProduction} 返回裸 {@code powerProduction} 的扁平版语义不同）。
 *
 * <p>与原版的差异（每处都有 {@code //TODO} 标注）：
 * <ul>
 *     <li>{@code sync = true}（原版构造器，B 类）：本项目 {@code Block} 没有 sync 字段，且
 *         {@code Vars.net} 恒 null（网络同步未移植）→ 补了也没有消费方。</li>
 *     <li>{@code baseExplosiveness = 5f}（原版构造器，B 类）：本项目 {@code Block} 没有该字段；
 *         它的消费方是原版 {@code Block.onDestroyed} 里的"库存爆炸强度"计算，而本项目没有
 *         {@code onDestroyed} 钩子、也没有方块被摧毁时的爆炸强度逻辑 → 无消费方。</li>
 *     <li>{@code flags = EnumSet.of(BlockFlag.producer)}：本项目 {@link BlockFlag} 没有 {@code producer}，
 *         退化成最接近的 {@link BlockFlag#generator}。</li>
 *     <li>{@code setBars()}：本项目 {@code Block} 没有 {@code bars}/{@code setBars}，
 *         改写成覆写 {@link #displayBars(Tile, Table)}（等价效果：在通用条之后追加一条发电条）；
 *         判据里的 {@code outputsPower} 本项目也没有，用 {@code !consumes.hasPower()} 近似"纯发电机"。</li>
 *     <li>{@code tile.ent()}（原版泛型取实体）→ 本项目用 {@code tile.entity instanceof} 判定。</li>
 *     <li>实体内部类照 {@code Drill}/{@code Pump} 的写法改成**非静态**内部类（可直接访问外层字段）。</li>
 * </ul>
 */
public class PowerGenerator extends PowerDistributor{
    /** 效率为 1.0（100%）时每帧的发电量。 */
    public float powerProduction;
    /** 发电量登记到哪个统计项（子类可换成别的类别）。 */
    public BlockStat generationType = BlockStat.basePowerGeneration;

    public PowerGenerator(String name){
        super(name);
        //TODO（B 类）原版 sync = true：本项目 Block 没有 sync 字段，Vars.net 恒 null（无消费方）
        //TODO（B 类）原版 baseExplosiveness = 5f：本项目 Block 没有该字段，也没有读它的
        //     Block.onDestroyed 爆炸强度逻辑（无消费方）
        //原版是 BlockFlag.producer（本项目枚举没有 producer，用最接近的 generator）
        flags = EnumSet.of(BlockFlag.generator);
        entityType = GeneratorEntity::new;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(generationType, powerProduction * 60f, StatUnit.powerSecond);
    }

    @Override
    public float getPowerProduction(Tile tile){
        return tile != null && tile.entity instanceof GeneratorEntity
            ? powerProduction * ((GeneratorEntity)tile.entity).productionEfficiency : 0f;
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    /**
     * 对应原版 {@code setBars()} 里的 "power" 条（本项目 {@code Block} 没有 bars，改在这里追加）。
     * <p>原版判据 {@code hasPower && outputsPower && !consumes.hasPower()}；本项目没有 {@code outputsPower}，
     * 用 {@code !consumes.hasPower()} 近似。
     */
    @Override
    public void displayBars(Tile tile, Table table){
        super.displayBars(tile, table);
        if(!(tile.entity instanceof GeneratorEntity)) return;

        GeneratorEntity entity = (GeneratorEntity)tile.entity;
        if(hasPower && !consumes.hasPower()){
            table.add(new Bar(
                () -> Strings.format(I18n.get("bar.poweroutput", "Power Output: {0}"),
                    Strings.fixed(getPowerProduction(tile) * 60f * entity.timeScale, 1)),
                () -> Pal.powerBar,
                () -> entity.productionEfficiency)).height(18f).growX().pad(4f);
            table.row();
        }
    }

    /** 发电机实体。对应原版 {@code PowerGenerator.GeneratorEntity}。 */
    public class GeneratorEntity extends TileEntity{
        public float generateTime;
        /** 发电效率：1.0 表示 100%。 */
        public float productionEfficiency = 0.0f;

        @Override
        public void write(java.io.DataOutputStream stream) throws java.io.IOException{
            super.write(stream);
            stream.writeFloat(productionEfficiency);
        }

        @Override
        public void read(java.io.DataInputStream stream, byte revision) throws java.io.IOException{
            super.read(stream, revision);
            productionEfficiency = stream.readFloat();
        }
    }
}
