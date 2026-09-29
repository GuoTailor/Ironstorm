package com.phoenix.game.world.blocks.defense;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Interval;
import com.phoenix.game.core.Lines;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.consumers.ConsumeLiquidFilter;
import com.phoenix.game.world.consumers.ConsumeType;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.StatUnit;

import static com.phoenix.game.Vars.tilesize;

/**
 * 力场投影器。参照 Mindustry mindustry.world.blocks.defense.ForceProjector（126.2 的 251 行版本）移植。
 *
 * <p><b>核心模型（原版 {@code buildup / breakage}）</b>：{@link ForceEntity#buildup} 累积被吸收的伤害，
 * 随时间衰减（有低温液体时衰减更快）；{@code buildup >= breakage} → {@link ForceEntity#broken} 破碎
 * （半径收缩到 0、衰减只剩 {@link #cooldownBrokenBase}），{@code buildup <= 0} 后恢复。
 * 护盾覆盖的是**半径内的所有敌方子弹**（不只是打中自己的）：
 * 每帧扫描 {@code Geometry.isInsideHexagon(...)} 内的子弹并吸收（原版是 {@code bulletGroup.intersect}，
 * 本项目没有子弹空间索引 → 遍历 {@link Bullet#all}）。
 *
 * <p><b>相织物加成</b>：库存里有相织物时 {@link ForceEntity#phaseHeat} 升温 → 半径 +{@link #phaseRadiusBoost}，
 * 并每 {@link #phaseUseTime} 帧消耗一次。
 *
 * <p><b>与 arc/libgdx 的差异</b>：
 * <ul>
 *   <li>原版把护盾挂在一个 {@code ShieldEntity}（BaseEntity，进 {@code shieldGroup}）上绘制；
 *       本项目**不新增实体组/实体类** → 绘制交给 {@code core/Renderer} 的护盾通道
 *       （{@code Renderer.drawShields}，对应原版 {@code Renderer.java:288-305}）。</li>
 *   <li>原版方块逻辑入口是 {@code Block.update(Tile)}；本项目方块逻辑写在实体的 {@code update()} 里
 *       （{@code World.updateTiles} 只调 {@code tile.entity.update()}）→ 本类把它放进 {@link ForceEntity#update()}。</li>
 *   <li>原版 {@code Block.timers++} 的计时器槽位本项目没有 → 用实体自带的 {@link Interval}（同 {@code NuclearReactor}）。</li>
 * </ul>
 */
public class ForceProjector extends Block{
    /** 相织物消耗的计时器槽位（原版 {@code timers++}；本项目计时器挂在实体上，这里只是槽位常量）。 */
    public final int timerUse = 0;
    /** 相织物消耗间隔（帧，对应原版 {@code phaseUseTime}）。 */
    public float phaseUseTime = 350f;

    /** 相织物加成：额外半径（世界单位）。 */
    public float phaseRadiusBoost = 80f;
    /** 护盾基础半径（世界单位）。 */
    public float radius = 101.7f;
    /** 破碎阈值：buildup 累积到此值护盾破碎。 */
    public float breakage = 550f;
    /** 正常状态下的 buildup 衰减速度（每帧）。 */
    public float cooldownNormal = 1.75f;
    /** 有冷却液体时的衰减倍率（还要乘液体的热容修正）。 */
    public float cooldownLiquid = 1.5f;
    /** 破碎状态下的衰减速度（每帧，替代 cooldownNormal）。 */
    public float cooldownBrokenBase = 0.35f;
    /** 统计用基础耗电（对应原版 {@code basePowerDraw}，注意它不是实际耗电 —— 实际是 Blocks.java 里的 3f）。 */
    public float basePowerDraw = 0.2f;
    /** 顶盖贴图（区域名 = 名称-top）。 */
    public TextureRegion topRegion;

    public ForceProjector(String name){
        super(name);
        update = true;
        solid = true;
        hasPower = true;
        //原版构造器里的四项属性（本项目此前漏了 canOverdrive/hasLiquids/hasItems）
        canOverdrive = false;
        hasLiquids = true;
        hasItems = true;
        //液体加成：低温且不可燃的液体（水/冷冻液）→ 加速 buildup 衰减
        //（原版 ForceProjector 构造器：`.boost().update(false)` —— update(false) 表示不参与每帧自动扣料，
        // 由实体逻辑在衰减时手动 cons.update(entity)，见 ForceEntity.update 的 buildup 分支）
        consumes.add(new ConsumeLiquidFilter(liquid -> liquid.temperature <= 0.5f && liquid.flammability < 0.1f, 0.1f)).boost().update(false);
        //相织物加成消耗器由 `Blocks.java` 注册（照原版位置：原版 `Blocks.java:885` 的
        //`consumes.item(Items.phasefabric).boost();`），本类只负责读 `ConsumeType.item` 的 valid 状态。
        entityType = ForceEntity::new;
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    @Override
    public void load(){
        super.load();
        topRegion = Core.atlas == null ? null : Core.atlas.findRegion(name + "-top");
    }

    /** 对应原版 {@code ForceProjector.setStats}：耗电 + 相织物加成范围。 */
    @Override
    public void setStats(){
        super.setStats();

        stats.add(BlockStat.powerUse, basePowerDraw * 60f, StatUnit.powerSecond);
        stats.add(BlockStat.boostEffect, phaseRadiusBoost / tilesize, StatUnit.blocks);
    }

    /**
     * 放置预览：画护盾作用半径的六边形轮廓（对应原版 {@code ForceProjector.drawPlace} 的
     * {@code Lines.poly(..., 6, radius)}）。
     * <p>⚠ 坐标口径：原版写 {@code x * tilesize, y * tilesize}，那里它就是方块贴图中心；本项目贴图中心是
     * 「锚点瓦片左下角 + offset() + 半格」（见 {@link Block#centerX}），照抄会偏半格（本方块 size=3）。
     */
    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        Draw.color(Pal.accent);
        Lines.stroke(1f);
        Lines.poly(x * tilesize + offset() + tilesize / 2f, y * tilesize + offset() + tilesize / 2f, 6, radius);
        Draw.color();
    }

    /**
     * 顶盖叠加（对应原版 {@code ForceProjector.draw(Tile)} 里 {@code super.draw} 之后的那段）：
     * buildup 越高顶盖越亮，加色混合。
     * <p>本项目 {@code drawLayer} = "画在方块本体之上"（本体由 {@code Block.draw} 画），与原版
     * {@code draw(Tile)} 里先画本体再叠 topRegion 的次序等价。
     * <p>⚠ 坐标口径：原版 {@code tile.drawx()/drawy()} 就是方块中心 → 这里用 {@link #centerX}/{@link #centerY}。
     */
    @Override
    public void drawLayer(Tile tile){
        if(topRegion == null || !(tile.entity instanceof ForceEntity)) return;

        ForceEntity entity = (ForceEntity)tile.entity;
        if(entity.buildup <= 0f) return;

        Draw.alpha(entity.buildup / breakage * 0.75f);
        //加色混合（对应原版 Draw.blend(Blending.additive)）；画完恢复默认混合
        Core.batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        Draw.rect(topRegion, centerX(tile), centerY(tile));
        Core.batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Draw.color();
    }

    /**
     * @return 该建筑当前的护盾半径（{@code (radius + phaseHeat * phaseRadiusBoost) * radscl}，
     * 对应原版包内可见的 {@code realRadius(ForceEntity)}）；实体不是本方块的 {@link ForceEntity} 时返回 0。
     * <p>提为 public 供 {@code core/Renderer} 的护盾绘制通道取半径（原版那边通过 {@code shieldGroup} 直接读字段）。
     */
    public float realRadius(TileEntity entity){
        if(!(entity instanceof ForceEntity)) return 0f;
        ForceEntity e = (ForceEntity)entity;
        return (radius + e.phaseHeat * phaseRadiusBoost) * e.radscl;
    }

    /**
     * @return 该瓦片上此刻是否有可见护盾（半径 >= 0.5）。
     * <p>判据取原版 {@code ShieldEntity.drawSimple} 开头的提前返回 {@code if(realRadius(entity) < 0.5f) return;}
     * —— 原版护盾的可见性只由 realRadius 决定（radscl 断电/破碎时会自然收缩到 0）；
     * ⚠ <b>刻意不用 {@code buildup > 0}</b>：那会让"刚开局的投影器"在第一次被命中前完全不显示护盾（背离原版）。
     */
    public boolean hasShield(Tile tile){
        return tile != null && tile.entity != null && realRadius(tile.entity) >= 0.5f;
    }

    /** @return 命中白闪强度（0~1；对应原版 {@code ShieldEntity.drawOver} 读的 {@code entity.hit}）。 */
    public float shieldHit(TileEntity entity){
        return entity instanceof ForceEntity ? ((ForceEntity)entity).hit : 0f;
    }

    /**
     * 力场投影器实体。字段与存档项照原版 {@code ForceProjector.ForceEntity}（只存 5 项，{@code hit} 不存）。
     */
    public class ForceEntity extends TileEntity{
        /** 护盾是否已破碎（破碎时半径向 0 收缩、衰减只剩 {@link #cooldownBrokenBase}）。 */
        public boolean broken = true;
        /** 已累积的吸收量：0 = 满护盾，>= {@link #breakage} = 破碎。 */
        public float buildup = 0f;
        /** 半径系数（0~1，断电/破碎时向 0 收缩）。 */
        public float radscl = 0f;
        /** 命中白闪强度（0~1，吸收子弹时置 1 后衰减；纯视觉，不存盘）。 */
        public float hit;
        /** 用电效率插值（有电时向 {@code efficiency()} 上涨）。 */
        public float warmup;
        /** 相织物热度（0~1，有相织物时上涨 → 半径 +{@link #phaseRadiusBoost}）。 */
        public float phaseHeat;

        /** 相织物消耗计时器（原版 {@code entity.timer.get(timerUse, phaseUseTime)}；槽位 0 独占）。 */
        public final Interval timer = new Interval(1);

        /**
         * 对应原版 {@code ForceProjector.update(Tile)}（本项目方块逻辑入口是实体 update，见类注释）。
         */
        @Override
        public void update(){
            //相织物供给 → 升温；断了 → 降温。原版：consumes.get(ConsumeType.item).valid(tile.entity)
            boolean phaseValid = consumes.get(ConsumeType.item).valid(this);
            phaseHeat = Mathf.lerpDelta(phaseHeat, phaseValid ? 1f : 0f, 0.1f);

            //相织物定时消耗（原版那行四连判据）
            if(phaseValid && !broken && timer.get(timerUse, phaseUseTime) && efficiency() > 0f){
                cons.trigger();
            }

            //半径系数：破碎 → 0，否则向 warmup 插值
            radscl = Mathf.lerpDelta(radscl, broken ? 0f : warmup, 0.05f);

            //过热冒烟：概率与 buildup 成正比（原版 Mathf.chance(Time.delta() * buildup / breakage * 0.1f)）
            if(Mathf.chance(Time.delta() * buildup / breakage * 0.1f)){
                Effects.effect(Fx.reactorsmoke, centerX(tile) + Mathf.range(tilesize / 2f),
                    centerY(tile) + Mathf.range(tilesize / 2f));
            }

            warmup = Mathf.lerpDelta(warmup, efficiency(), 0.1f);

            if(buildup > 0){
                float scale = !broken ? cooldownNormal : cooldownBrokenBase;
                //⚠ 局部变量名不叫 cons：会遮蔽 TileEntity 的 ConsumeModule cons 字段
                ConsumeLiquidFilter liquidCons = consumes.get(ConsumeType.liquid);
                if(liquidCons.valid(this)){
                    //原版在这里手动扣液体（因为构造器里 update(false) 关掉了自动扣料）
                    liquidCons.update(this);
                    //液体热容修正照原版；⚠ 通用液体（无水）时 liquids.current() 是 content.liquid(0)，
                    //本项目 Liquid.heatCapacity 已存在（不是降级项）
                    scale *= cooldownLiquid * (1f + (liquids.current().heatCapacity - 0.4f) * 0.9f);
                }

                buildup -= Time.delta() * scale;
            }

            if(broken && buildup <= 0){
                broken = false;
            }

            if(buildup >= breakage && !broken){
                broken = true;
                buildup = breakage;
                //破碎特效：以方块中心扩散（原版传 tile.drawx()/drawy()，本项目取几何中心）
                Effects.effect(Fx.shieldBreak, centerX(tile), centerY(tile), radius);
            }

            if(hit > 0f){
                hit -= 1f / 5f * Time.delta();
            }

            absorbBullets();
        }

        /**
         * 吸收半径内的敌方子弹（对应原版 {@code bulletGroup.intersect(...)} + {@code shieldConsumer}）。
         *
         * <p>⚠ 本项目没有子弹空间索引 → 遍历 {@link Bullet#all}，用
         * {@code Geometry.isInsideHexagon(bx, by, realRadius * 2f, 中心 x, 中心 y)} 判定
         * （与原版 shieldConsumer 的第三个判据逐字一致；d 传 realRadius*2f 也与原版一致，
         * 这样六边形正好是 {@code Fill.poly(x, y, 6, realRadius)} 画出来的那个形状）。
         * <p>原版第一个判据 {@code canBeAbsorbed()} 在 126.2 里是 {@code AbsorbTrait} 的默认实现（恒 true，
         * Bullet 没有覆写）→ 此处省略（本项目 Bullet 也没实现 AbsorbTrait）。
         * <p>⚠ 原版 {@code absorb()} = {@code supressCollision = true; remove();}；本项目 {@code remove()}
         * 是**同步**的（立刻回收进对象池并清空 {@code type}）→ 坐标与伤害必须在 remove() 之前取出。
         * <p>热路径用下标循环；移除后 {@code i--} 重测当前下标（removed() 已把后面的元素顶上来）。
         */
        private void absorbBullets(){
            float realRadius = realRadius(this);
            if(realRadius <= 0f) return;

            float cx = centerX(tile), cy = centerY(tile);

            for(int i = 0; i < Bullet.all.size; i++){
                Bullet bullet = Bullet.all.get(i);
                if(bullet == null || bullet.isDead() || bullet.getBulletType() == null) continue;
                //队伍判据照原版：trait.getTeam() != paramTile.getTeam()
                if(bullet.getTeam() == tile.getTeam()) continue;
                if(!Geometry.isInsideHexagon(bullet.getX(), bullet.getY(), realRadius * 2f, cx, cy)) continue;

                //先取样：remove() 会清空 type，之后再调 getShieldDamage() 会 NPE
                float bx = bullet.getX(), by = bullet.getY();
                float shieldDamage = bullet.getShieldDamage();

                bullet.remove();
                Effects.effect(Fx.absorb, bx, by);

                hit = 1f;
                //原版：buildup += trait.getShieldDamage() * paramEntity.warmup
                buildup += shieldDamage * warmup;

                i--;
            }
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            //照原版只存这 5 项（hit 是纯视觉量，不存）
            out.writeBoolean(broken);
            out.writeFloat(buildup);
            out.writeFloat(radscl);
            out.writeFloat(warmup);
            out.writeFloat(phaseHeat);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            broken = in.readBoolean();
            buildup = in.readFloat();
            radscl = in.readFloat();
            warmup = in.readFloat();
            phaseHeat = in.readFloat();
        }
    }
}
