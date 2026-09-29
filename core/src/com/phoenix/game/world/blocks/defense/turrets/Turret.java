package com.phoenix.game.world.blocks.defense.turrets;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Drawf;
import com.phoenix.game.core.Interval;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Predict;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.entities.traits.TargetTrait;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockFlag;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.StatUnit;
import io.anuke.mindustry.gen.Sounds;

import java.util.EnumSet;

import static com.phoenix.game.Vars.tilesize;

/**
 * 最小实现：炮塔。参照 Mindustry mindustry.world.blocks.defense.turrets.Turret 移植。
 * <p>简化点：只有一种弹药（ammoItem，为 null 时无限弹药）、不做液体冷却、不做双管特化绘制、
 * 敌方判定依赖 Bullet 的队伍字段（友方子弹会穿过友方单位与建筑）。
 */
public class Turret extends Block{
    /** 索敌计时器槽位 */
    public static final int timerTarget = 0;

    /** 重新索敌间隔（tick） */
    public int targetInterval = 20;
    /** 射程（世界单位） */
    public float range = 50f;
    /** 装填时间（tick） */
    public float reload = 10f;
    /** 炮管转速（度/tick） */
    public float rotatespeed = 5f;
    /** 开火所需瞄准夹角（度） */
    public float shootCone = 8f;
    public float inaccuracy = 0f;
    /** 每次射击的弹数 */
    public int shots = 1;
    /** 多发的角度间隔 */
    public float spread = 4f;
    /** 后坐力 */
    public float recoil = 1f;
    public float cooldown = 0.02f, restitution = 0.02f;
    public boolean targetAir = true, targetGround = true;
    /** 弹药物品；null 表示不消耗弹药 */
    public Item ammoItem;
    public int ammoPerShot = 1;
    /** 发射的子弹 */
    public BulletType bullet;
    public Sound shootSound = Sounds.shoot;
    public Effects.Effect shootEffect = Fx.none;
    /** 开火烟（对应原版 Turret.smokeEffect） */
    public Effects.Effect smokeEffect = Fx.none;
    /** 炮管发热时的叠加色（对应原版 Turret.heatColor） */
    public Color heatColor = Pal.turretHeat;
    /** 开火时从炮口后抛的弹壳特效（对应原版 Turret.ammoUseEffect） */
    public Effects.Effect ammoUseEffect = Fx.none;
    /** 弹壳后抛距离（对应原版 Turret.ammoEjectBack） */
    public float ammoEjectBack = 1f;
    /** 开火震屏强度，0 = 不震（对应原版 Turret.shootShake） */
    public float shootShake = 0f;
    /** 炮口横向随机抖动半径（对应原版 Turret.xRand） */
    public float xRand = 0f;

    /** 炮管贴图（按角度旋转绘制） */
    public TextureRegion barrelRegion;
    /** 炮管发热叠加贴图（对应原版 Turret.heatRegion = {@code <name>-heat}）；本项目 findRegion 缺图返回 null */
    public TextureRegion heatRegion;

    public Turret(String name){
        super(name);

        update = true;
        solid = true;
        destructible = true;
        health = 250;
        hasItems = true;
        itemCapacity = 20;
        flags = EnumSet.of(BlockFlag.turret);
        entityType = TurretEntity::new;
    }

    @Override
    public void setStats(){
        super.setStats();

        //对应原版 Turret.setStats（本项目是单一弹药，ammo 直接列弹药物品，不做 AmmoListValue 的子弹属性表）
        stats.add(BlockStat.shootRange, range / tilesize, StatUnit.blocks);
        stats.add(BlockStat.inaccuracy, (int)inaccuracy, StatUnit.degrees);
        if(reload > 0f) stats.add(BlockStat.reload, 60f / reload, StatUnit.none);
        stats.add(BlockStat.shots, shots, StatUnit.none);
        stats.add(BlockStat.targetsAir, targetAir);
        stats.add(BlockStat.targetsGround, targetGround);
        if(ammoItem != null) stats.add(BlockStat.ammo, ammoItem);
    }

    @Override
    public void load(){
        super.load();

        //原版 region=炮管、baseRegion=block-<size>；这里相反：region 给 Renderer 平铺底座，炮管在 drawLayer 旋转绘制
        barrelRegion = region;
        region = Core.atlas.findRegion("block-" + size);

        //对应原版 Turret.load 的 heatRegion = Core.atlas.find(name + "-heat")；
        //本项目 findRegion 缺图返回 null（不是原版的 "error" 图），绘制前判空
        heatRegion = Core.atlas.findRegion(name + "-heat");
    }

    /**
     * 图标分层：底座 + 炮管，对应原版 {@code Turret.generateIcons()} 的 {@code {block-<size>, <name>}}。
     * <p>必须覆写：本类 {@link #load()} 把 {@code region} 换成了底座，走父类默认实现只会拿到底座，
     * 所有炮塔图标会长得一模一样（缺炮管）。
     */
    @Override
    protected TextureRegion[] generateIcons(){
        return new TextureRegion[]{region, barrelRegion};
    }

    /** 弹壳抛射的延时（对应原版 {@code Turret.useAmmo} 里的 {@code Time.run(reload / 2f, ...)}）。 */
    public float ejectDelay(){
        return reload / 2f;
    }

    /**
     * 是否处于"作弊 AI"控制之下（原版 {@code Tile.isEnemyCheat()} 的等价实现）：
     * 作弊 AI + 本方块属于波次阵营。作弊炮塔无限弹药、无视冷却液。
     * <p>本项目 {@code Tile} 没有该判定，故提到方块上供各炮塔子类共用。
     */
    protected boolean isEnemyCheat(Tile tile){
        return Vars.state != null && Vars.state.rules.enemyCheat && tile != null && tile.getTeam() == Vars.state.rules.waveTeam;
    }

    /**
     * 是否允许本帧转向（对应原版 {@code Turret.shouldTurn(Tile)}）：默认允许。
     * <p>{@link ChargeTurret} 覆写它，蓄力期间锁住炮管。
     */
    public boolean shouldTurn(Tile tile){
        return true;
    }

    @Override
    public void drawLayer(Tile tile){
        if(barrelRegion == null || !(tile.entity instanceof TurretEntity)) return;

        TurretEntity entity = (TurretEntity)tile.entity;

        //后坐力：沿炮管反方向偏移。中心取方块**几何中心**（原版 tile.drawx() 语义）；
        //只写 worldx() + 半格的话，偶数尺寸炮塔（scatter/wave/lancer/swarmer/salvo/spectre/meltdown）
        //的炮管会整体偏左下 4 单位（与底座、与 cx() 出弹点都对不上）
        float x = centerX(tile) - Angles.trnsx(entity.rotation, entity.recoil);
        float y = centerY(tile) - Angles.trnsy(entity.rotation, entity.recoil);

        Draw.rect(barrelRegion, x, y, entity.rotation - 90f);

        //炮管发热叠加（对应原版 Turret.heatDrawer）：heatColor 按 heat 作透明度、加色混合、位置与炮管一致
        if(heatRegion != null && entity.heat > 0.00001f){
            Draw.color(heatColor, entity.heat);
            //加色混合（对应原版 Draw.blend(Blending.additive)）；画完恢复默认混合
            Core.batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            Draw.rect(heatRegion, x, y, entity.rotation - 90f);
            Core.batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            Draw.color();
        }
    }

    /**
     * 悬停己方炮塔时显示射程圈（对应原版 {@code Turret.drawSelect}，原版用阵营色虚线圆）：
     * 深灰描边 + 阵营色虚线，半径 = {@link #range}（世界单位）。
     * <p>调用点：{@code InputHandler.drawTop} → {@code drawSelectedBlock()}，每帧对光标下的己方建筑调一次
     * （只在非建造、非拆除状态下，与建造预览互斥）。
     * <p>⚠ 与原版的差异（坐标口径）：原版写 {@code tile.drawx()/drawy()}，那里它就等于方块贴图的几何中心；
     * 本项目 {@code tile.drawx()} 是"锚点瓦片左下角 + offset()"，比贴图中心少半格（见 {@code Block.draw} 的落笔位置
     * 与 {@link Block#centerX}／{@code InputHandler.drawGhost}），照抄会让圈整体偏到炮塔的左下角。
     * 这里改用本项目自己的方块几何中心，保证圈与炮塔贴图、放置预览同心。
     * <p>另外取 {@code tile.link()}：光标可能落在多格炮塔的卫星瓦片上，而 {@code worldx()} 用的是当前瓦片的坐标，
     * 不先解析到锚点瓦片的话圈会整个偏一格（原版靠调用点 {@code world.ltileWorld(...)} 保证传进来的是锚点瓦片，
     * 本项目 {@code InputHandler} 传的是光标所在瓦片，故在这里解析）。
     */
    @Override
    public void drawSelect(Tile tile){
        Tile anchor = tile.link();
        Drawf.dashCircle(centerX(anchor), centerY(anchor), range, anchor.getTeam().color);
    }

    /**
     * 放置预览时在待放置位置显示射程圈（对应原版 {@code Turret.drawPlace}，原版用 {@code Pal.placing} 本色虚线圆）。
     * <p>调用点：{@code InputHandler.drawTop} 画完 ghost 之后（{@code x, y} 即将要放置的锚点瓦片）。
     * <p>⚠ 坐标口径同 {@link #drawSelect(Tile)}：中心用「锚点瓦片左下角 + offset() + 半格」，
     * 与 {@code InputHandler.drawGhost} 算出的 ghost 中心完全一致（原版是 {@code x * tilesize + offset()}）。
     */
    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        Drawf.dashCircle(x * tilesize + offset() + tilesize / 2f, y * tilesize + offset() + tilesize / 2f, range, Pal.placing);
    }

    /** 只接收消耗器声明过的弹药（对应原版 ItemTurret.acceptItem）。 */
    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        //没声明物品消耗器的炮塔（纯耗电/激光类）不收物品
        if(!consumes.has(com.phoenix.game.world.consumers.ConsumeType.item)) return false;

        com.phoenix.game.world.consumers.ConsumeItems ci =
            consumes.get(com.phoenix.game.world.consumers.ConsumeType.item);
        if(!ci.hasItem(item)) return false;

        return super.acceptItem(item, tile, source);
    }

    /** 炮塔实体：索敌、转向、开火。 */
    public class TurretEntity extends TileEntity{
        public final Interval timer = new Interval(1);
        /** 装填进度（tick） */
        public float reload;
        /** 炮管角度 */
        public float rotation = 90f;
        public float recoil;
        public float heat;
        public TargetTrait target;

        //cx()/cy()（瓦片中心）已提到 TileEntity 上，这里不再重复定义

        @Override
        public void update(){
            recoil = Mathf.lerpDelta(recoil, 0f, restitution);
            heat = Mathf.lerpDelta(heat, 0f, cooldown);

            if(bullet == null || isDead()) return;

            if(!validateTarget()) target = null;
            if(!hasAmmo()) return;

            if(timer.get(timerTarget, targetInterval)){
                findTarget();
            }

            if(!validateTarget()) return;

            //提前量瞄准（对应原版 Turret.update 的 Predict.intercept）
            float speed = bullet.speed < 0.1f ? 9999999f : bullet.speed;
            Vector2 aim = Predict.intercept(cx(), cy(), target.getX(), target.getY(),
                    target.getTargetVelocityX(), target.getTargetVelocityY(), speed);
            float targetRot = Angles.angle(cx(), cy(), aim.x, aim.y);

            if(shouldTurn(tile)){
                rotation = Angles.moveToward(rotation, targetRot, rotatespeed * Time.delta());
            }

            if(Angles.angleDist(rotation, targetRot) < shootCone){
                updateShooting();
            }
        }

        protected boolean validateTarget(){
            return !Units.invalidateTarget(target, cx(), cy(), range);
        }

        protected void findTarget(){
            if(targetAir && !targetGround){
                target = Units.closestTarget(getTeam(), cx(), cy(), range, u -> !u.isDead() && u.isFlying());
            }else{
                target = Units.closestTarget(getTeam(), cx(), cy(), range,
                        u -> !u.isDead() && (!u.isFlying() || targetAir) && (u.isFlying() || targetGround));
            }
        }

        /** @return 能否开火：弹药（或电力）由消耗器统一判定。 */
        public boolean hasAmmo(){
            return cons.valid();
        }

        /** 开火后扣料（ConsumeItems 扣物品，ConsumePower 的 trigger 是空实现）。 */
        protected void useAmmo(){
            cons.trigger();
        }

        /**
         * 装填速度倍率（对应原版 {@code Turret.baseReloadSpeed(Tile)}）：默认 1。
         * <p>参照原版 mindustry.world.blocks.defense.turrets.Turret 移植；耗电炮塔覆写它返回供电率。
         * <p>注意：原版在 {@code Turret.update} 里把 {@code reload} 的推进乘上它；本项目现有的
         * {@code updateShooting()} 没有乘（PowerTurret 自己用"没电直接 return"顶替），
         * 为不改动既有行为，这里只补方法、暂不接进推进公式（留给主会话决定）。
         */
        protected float baseReloadSpeed(){
            return 1f;
        }

        protected void updateShooting(){
            if(reload >= Turret.this.reload){
                shoot();
                reload = 0f;
            }else{
                reload += Time.delta() * (bullet == null ? 1f : bullet.reloadMultiplier);
            }
        }

        protected void shoot(){
            recoil = Turret.this.recoil;
            heat = 1f;

            float angle = rotation + Mathf.range(inaccuracy + bullet.inaccuracy);

            //炮口位置：沿炮管方向偏移半个方块，并叠加 xRand 的横向随机抖动
            //（对应原版 tr.trns(rotation, size * tilesize / 2, Mathf.range(xRand))；x/y 必须共用同一次随机值）
            float xr = Mathf.range(xRand);
            float ox = Angles.trnsx(angle, size * tilesize / 2f, xr);
            float oy = Angles.trnsy(angle, size * tilesize / 2f, xr);

            for(int i = 0; i < shots; i++){
                float shotAngle = angle + (i - shots / 2) * spread;
                Bullet.create(bullet, this, getTeam(), cx() + ox, cy() + oy, shotAngle);
            }

            useAmmo();

            if(shootSound != null) shootSound.play(1f);
            effects(cx() + ox, cy() + oy);
        }

        /** 播放炮口特效：炮塔自身为 {@link Fx#none} 时退回弹药自带的特效（对应原版 Turret.effects）。 */
        protected void effects(float x, float y){
            Effects.Effect shoot = Turret.this.shootEffect == Fx.none ? bullet.shootEffect : Turret.this.shootEffect;
            Effects.Effect smoke = Turret.this.smokeEffect == Fx.none ? bullet.smokeEffect : Turret.this.smokeEffect;

            Effects.effect(shoot, x, y, rotation);
            Effects.effect(smoke, x, y, rotation);

            //开火震屏（对应原版 Turret.effects 里的 shootShake）
            if(shootShake > 0f){
                Effects.shake(shootShake, shootShake, cx(), cy());
            }

            scheduleEject();
        }

        /** 弹壳后抛特效：从炮口后方 ammoEjectBack 处播放（对应原版 Turret.ejectEffects）。 */
        protected void ejectEffects(){
            if(isDead()) return;
            Effects.effect(ammoUseEffect, cx() - Angles.trnsx(rotation, ammoEjectBack),
                    cy() - Angles.trnsy(rotation, ammoEjectBack), rotation);
        }

        /** 开火后延时抛出弹壳（对应原版 {@code Turret.useAmmo} 里的 {@code Time.run(reload / 2f, ...)}）。 */
        protected void scheduleEject(){
            if(ammoUseEffect == Fx.none) return;
            Time.run(ejectDelay(), this::ejectEffects);
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeFloat(rotation);
            out.writeFloat(reload);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            rotation = in.readFloat();
            reload = in.readFloat();
            //目标不入档：读档后下一帧重新索敌（recoil/heat 是纯视觉量，不存）
            target = null;
        }
    }
}
