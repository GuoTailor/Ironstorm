package com.phoenix.game.content;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Fill;
import com.phoenix.game.core.Lines;
import com.phoenix.game.core.Time;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.entities.Damage;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.bullet.ArtilleryBulletType;
import com.phoenix.game.entities.bullet.BasicBulletType;
import com.phoenix.game.entities.bullet.BombBulletType;
import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.entities.bullet.FlakBulletType;
import com.phoenix.game.entities.bullet.HealBulletType;
import com.phoenix.game.entities.bullet.LiquidBulletType;
import com.phoenix.game.entities.bullet.MassDriverBolt;
import com.phoenix.game.entities.bullet.MissileBulletType;
import com.phoenix.game.entities.effect.Lightning;
import com.phoenix.game.entities.effect.Puddle;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Tile;

/**
 * 最小实现：子弹定义集合。参照 Mindustry mindustry.content.Bullets 移植。
 * <p>{@link #all} 按声明顺序登记全部子弹类型，供网络同步按 ID 引用（Bullets 静态字段声明序）。
 * <p>炮口特效（{@code shootEffect}/{@code smokeEffect}）按原版对应弹药配置：炮塔自身这两个字段为
 * {@link Fx#none} 时会退回这里（见 {@code Turret.effects}）。原版用默认值的项不再重复写出。
 * <p>F2 批：照原版补回了此前缺失的 44 个弹药（原版 50 个字段现已全覆盖，本项目共 58 个），
 * 全部追加在类末尾以保住既有 14 个弹药的 id；字段名换算与刻意降级见文末 F2 段的说明。
 */
public class Bullets implements com.phoenix.game.ctype.ContentList{
    /**
     * 全部弹药，按注册顺序索引（指向内容加载器的弹药列表）。
     * <p>本类的弹药用静态初始化创建，所以这里直接取内容加载器的列表；
     * {@code ContentLoader} 必须把本类列进内容集合，保证 {@code Vars.content} 已就绪后才会触发本类静态初始化。
     */
    public static Array<BulletType> all = com.phoenix.game.Vars.content.bullets();

    /** 兼容原版写法：id 由 {@code Content} 构造函数按注册顺序分配，这里不再手工登记。 */
    private static <T extends BulletType> T reg(T type){
        return type;
    }

    @Override
    public void load(){
        //弹药在静态初始化时已创建完毕
    }

    /** 质量驱动器的炮弹：携带 DriverBulletData，命中对端驱动器后投递物品。 */
    public static final BulletType driverBolt = reg(new MassDriverBolt());

    public static final BulletType basicBullet = reg(new BasicBulletType(2.5f, 10f){{
        lifetime = 45f;
        width = 7f;
        height = 9f;
    }});

    /** 炮塔标准弹药（对应原版 Bullets.standardCopper）。 */
    public static final BulletType standardCopper = reg(new BasicBulletType(2.5f, 9f){{
        lifetime = 60f;
        width = 7f;
        height = 9f;
        ammoMultiplier = 2f;
        //原版 standardCopper（Bullets.java:303）无 trail 字段，此前本项目多设了 trail=Fx.spark（已删）
        //原版 standardCopper：小口径炮口闪光 + 轻烟
        shootEffect = Fx.shootSmall;
        smokeEffect = Fx.shootSmallSmoke;
    }});

    /** 防空散弹（对应原版 Bullets.flakLead）：带近炸引信，靠近敌机就提前起爆。 */
    public static final BulletType flakBullet = reg(new FlakBulletType(3.2f, 8f){{
        lifetime = 38f;
        width = 5f;
        height = 7f;
        shootEffect = Fx.shootSmall;
    }});

    /** 迫击炮弹（对应原版 Bullets.artilleryDense）：飞行途中周期性撒拖尾。 */
    public static final BulletType artilleryBullet = reg(new ArtilleryBulletType(1.9f, 30f){{
        lifetime = 70f;
        width = 11f;
        height = 13f;
        splashDamageRadius = 26f;
        splashDamage = 22f;
        hitEffect = Fx.explosion;
        despawnEffect = Fx.explosion;
        hitShake = 2f;
    }});

    public static final BulletType flameBullet = reg(new BasicBulletType(2f, 6f){{
        lifetime = 24f;
        width = 9f;
        height = 9f;
        incendChance = 0.3f;
        incendAmount = 2;
        hitEffect = Fx.fire;
        despawnEffect = Fx.fire;
        //原版 basicFlame：喷火器专用炮口特效
        shootEffect = Fx.shootSmallFlame;
    }});

    /**
     * 火球（对应原版 Bullets.fireball）：由燃烧的瓦片随机甩出。
     * <p>不参与任何碰撞、可穿透，靠飞行途中不断点火来引燃路径；初速随机（原版在 {@code init} 里重设速度长度）。
     */
    public static final BulletType fireball = reg(new BulletType(1f, 4f){
        {
            pierce = true;
            hitTiles = false;
            collides = false;
            collidesTiles = false;
            drag = 0.03f;
            hitEffect = Fx.none;
            despawnEffect = Fx.none;
        }

        @Override
        public void init(Bullet b){
            b.velocity().setLength(0.6f + Mathf.random(2f));
        }

        @Override
        public void draw(Bullet b){
            Draw.color(Pal.lightFlame, Pal.darkFlame, Color.GRAY, b.fin());
            Fill.circle(b.x, b.y, 3f * b.fout());
            Draw.color();
        }

        @Override
        public void update(Bullet b){
            if(Mathf.chance(0.04f * Time.delta())){
                com.phoenix.game.world.Tile tile = Vars.world == null ? null : Vars.world.tileWorld(b.x, b.y);
                if(tile != null){
                    com.phoenix.game.entities.effect.Fire.create(tile);
                }
            }

            if(Mathf.chance(0.1f * Time.delta())){
                Effects.effect(Fx.fireballsmoke, b.x, b.y);
            }

            if(Mathf.chance(0.1f * Time.delta())){
                Effects.effect(Fx.ballfire, b.x, b.y);
            }
        }
    });

    public static final BulletType chaosBullet = reg(new BasicBulletType(2.4f, 22f){{
        lifetime = 55f;
        width = 9f;
        height = 11f;
        splashDamageRadius = 18f;
        splashDamage = 14f;
    }});

    /** 追踪导弹（对应原版 Bullets.missileExplosive）：默认带追踪与导弹拖尾。 */
    public static final BulletType missileBullet = reg(new MissileBulletType(2.4f, 22f){{
        lifetime = 55f;
        width = 9f;
        height = 11f;
        splashDamageRadius = 18f;
        splashDamage = 14f;
        hitEffect = Fx.explosion;
        despawnEffect = Fx.explosion;
    }});

    public static final BulletType eradicationBullet = reg(new BasicBulletType(2.2f, 60f){{
        lifetime = 75f;
        width = 13f;
        height = 16f;
        splashDamageRadius = 34f;
        splashDamage = 40f;
        hitEffect = Fx.explosion;
        despawnEffect = Fx.explosion;
        hitShake = 3f;
        //大口径弹药：原版 standardThorium/standardDenseBig 一类都用 shootBig
        shootEffect = Fx.shootBig;
        smokeEffect = Fx.shootBigSmoke;
    }});

    /**
     * 长矛的激光束（对应原版 Bullets.lancerLaser）。
     * <p>瞬时命中：在 {@code init} 里沿光束做一次 {@link Damage#collideLine}；绘制是 3 层不同颜色、
     * 每层 4 档粗细的叠加线段，长度在前 20% 生命周期内由 0 伸长到全长。
     */
    public static final BulletType lancerLaser = reg(new BulletType(0.001f, 140f){
        final Color[] colors = {Pal.lancerLaser.cpy().mul(1f, 1f, 1f, 0.4f), Pal.lancerLaser, Color.WHITE};
        final float[] tscales = {1f, 0.7f, 0.5f, 0.2f};
        final float[] lenscales = {1f, 1.1f, 1.13f, 1.14f};
        final float length = 160f;

        {
            hitEffect = Fx.hitLancer;
            despawnEffect = Fx.none;
            hitSize = 4f;
            lifetime = 16f;
            pierce = true;
        }

        @Override
        public float range(){
            return length;
        }

        @Override
        public void init(Bullet b){
            Damage.collideLine(b, b.getTeam(), hitEffect, b.x, b.y, b.rot(), length);
        }

        @Override
        public void draw(Bullet b){
            float f = Mathf.curve(b.fin(), 0f, 0.2f);
            float baseLen = length * f;

            Lines.lineAngle(b.x, b.y, b.rot(), baseLen);
            for(int s = 0; s < 3; s++){
                Draw.color(colors[s]);
                for(int i = 0; i < tscales.length; i++){
                    Lines.stroke(7f * b.fout() * (s == 0 ? 1.5f : s == 1 ? 1f : 0.3f) * tscales[i]);
                    Lines.lineAngle(b.x, b.y, b.rot(), baseLen * lenscales[i]);
                }
            }
            Draw.color();
        }
    });

    /**
     * 熔毁的激光束（对应原版 Bullets.meltdownLaser）。
     * <p>与长矛的区别：持续判定（每 5 tick 沿光束再打一次，且带加宽的四邻检测）+ 每帧震屏；
     * 绘制是 4 层颜色、逐层外推并带脉动粗细的粗光束。
     */
    public static final BulletType meltdownLaser = reg(new BulletType(0.001f, 70f){
        final Color tmpColor = new Color();
        final Color[] colors = {Color.valueOf("ec745855"), Color.valueOf("ec7458aa"), Color.valueOf("ff9c5a"), Color.WHITE};
        final float[] tscales = {1f, 0.7f, 0.5f, 0.2f};
        final float[] strokes = {2f, 1.5f, 1f, 0.3f};
        final float[] lenscales = {1f, 1.12f, 1.15f, 1.17f};
        final float length = 220f;

        {
            hitEffect = Fx.hitMeltdown;
            despawnEffect = Fx.none;
            hitSize = 4f;
            drawSize = 420f;
            lifetime = 16f;
            pierce = true;
        }

        @Override
        public void update(Bullet b){
            if(b.timer.get(1, 5f)){
                Damage.collideLine(b, b.getTeam(), hitEffect, b.x, b.y, b.rot(), length, true);
            }

            Effects.shake(1f, 1f, b.x, b.y);
        }

        @Override
        public void hit(Bullet b, float hitx, float hity){
            Effects.effect(hitEffect, colors[2], hitx, hity);
            //原版：熔毁光束有 40% 概率在命中点附近点火
            if(Mathf.chance(0.4f) && Vars.world != null){
                com.phoenix.game.entities.effect.Fire.create(
                    Vars.world.tileWorld(hitx + Mathf.range(5f), hity + Mathf.range(5f)));
            }
        }

        @Override
        public void draw(Bullet b){
            float baseLen = length * b.fout();

            Lines.lineAngle(b.x, b.y, b.rot(), baseLen);
            for(int s = 0; s < colors.length; s++){
                Draw.color(tmpColor.set(colors[s]).mul(1f + Mathf.absin(Time.time, 1f, 0.1f)));
                for(int i = 0; i < tscales.length; i++){
                    float offset = (lenscales[i] - 1f) * 35f;
                    Tmp.v1.set(Angles.trnsx(b.rot() + 180f, offset), Angles.trnsy(b.rot() + 180f, offset));
                    Lines.stroke((9f + Mathf.absin(Time.time, 0.8f, 1.5f)) * b.fout() * strokes[s] * tscales[i]);
                    Lines.lineAngle(b.x + Tmp.v1.x, b.y + Tmp.v1.y, b.rot(), baseLen * lenscales[i]);
                }
            }
            Draw.color();
        }
    });

    /** 飞行单位 flare 的子弹。 */
    public static final BulletType flareShot = reg(new BasicBulletType(3f, 8f){{
        lifetime = 40f;
        width = 4f;
        height = 6f;
        trail = Fx.spark;
    }});

    /**
     * 飞行单位 ghoul 的炸弹（抛体，靠 lifetime 落地后范围爆炸）。
     * <p>原版 Bullets.java:652 {@code BombBulletType(10f, 20f, "shell")}：注意 BombBulletType 的前两个参数是
     * <b>(damage, radius)</b>（见该类构造器），故原版运行时为 splashDamage=10、splashDamageRadius=20
     * —— 审计报告 §二.M 把这两个参数误标成了 speed/damage，这里按代码语义对齐（原 20f/24f → 10f/20f）。
     */
    public static final BulletType bombExplosive = reg(new BombBulletType(10f, 20f, "shell"){{
        //⚠ 刻意偏离（主会话已决策，见 plans/phoenix-remaining-port.md §6）：
        //原版无此覆盖，speed 继承 BombBulletType 的 0.7f；但原版轰炸机靠 FlyingUnit 的
        //「ignoreRotation 免朝向 + Predict.intercept(…, ammo.speed) 提前量」命中目标，
        //本项目 FlyingUnit 只移植了极简攻击逻辑（无提前量）→ 照抄 0.7f 会让 ghoul 的炸弹
        //几乎打不到移动目标。等 FlyingUnit 攻击逻辑补齐后再回退此覆盖。
        speed = 1.5f;
        width = 9f;
        height = 13f;
        //原版 Bullets.java:652-658：hitEffect=Fx.flakExplosion，且**无 despawnEffect、无 hitShake**（此处已按原版删除后两者）
        hitEffect = Fx.flakExplosion;
        //原版 bombExplosive：炸弹由机腹投下，不播炮口特效
        shootEffect = Fx.none;
        smokeEffect = Fx.none;
    }});

    // ==================================================================================
    // F2 批：照原版补回的弹药（原版 mindustry.content.Bullets 里本项目此前缺失的全部条目）
    //
    // ⚠ 一律追加在**本类末尾**：子弹 id = 在本列表里的下标，追加在最后才不会让上面 14 个既有弹药 id 错位
    //    （代价是它们的 id 与原版不一致，id 对齐留给后续的"内容 id 对齐"批次）。
    // ⚠ 新增 44 个弹药会让**其后注册的 Mechs/UnitTypes/Blocks 全体 id 后移**（内容 id 是跨类型全局计数器）
    //    → 网络包按 id 引用内容，**两端必须同版本**（见 转换.md F1 批第 58 条）。
    // ⚠ 本项目子弹类字段名与原版不同（见 转换.md F1 批第 56 条），本批统一换算：
    //    `bulletWidth` → `width`、`bulletHeight` → `height`、`bulletShrink` → `shrinkY`。
    //    （G5-4 已对齐原版公式：宽恒定、高 = height*((1-shrinkY)+shrinkY*fout())；原先多出来的
    //     `shrinkX`（额外缩宽 20%）与 back 放大 1.1/1.3 已删除，故这些赋值现在是 1:1 照抄原版。）
    // ⚠ 另一条系统性差异：`BasicBulletType` 的默认 hitEffect/despawnEffect 本项目是 `Fx.hit`
    //    （原版继承 BulletType 的 `Fx.hitBulletSmall`）→ 原版靠默认值的地方本项目会多一层命中特效。
    // ⚠ `ArtilleryBulletType`/`BombBulletType` 在本项目**刻意保持碰撞开启**（原版设
    //    `collides = collidesTiles = false` 并依赖抛物线落地检测，本项目没有该检测，照搬会让炮弹穿过目标）
    //    → 原版这些赋值一律以「刻意降级」标注后省略，与既有 `artilleryBullet` 的处理一致。
    // ==================================================================================

    //--- artillery ---

    /** 塑料炮弹的破片（原版 Bullets.artilleryPlasticFrag）。 */
    public static final BulletType artilleryPlasticFrag = reg(new BasicBulletType(2.5f, 10f, "bullet"){{
        width = 10f;
        height = 12f;
        shrinkY = 1f;
        lifetime = 15f;
        backColor = Pal.plastaniumBack;
        frontColor = Pal.plastaniumFront;
        despawnEffect = Fx.none;
    }});

    /** 玻璃破片（原版 Bullets.glassFrag）。 */
    public static final BulletType glassFrag = reg(new BasicBulletType(3f, 6f, "bullet"){{
        width = 5f;
        height = 12f;
        shrinkY = 1f;
        lifetime = 20f;
        backColor = Pal.gray;
        frontColor = Color.WHITE;
        despawnEffect = Fx.none;
    }});

    /** 石墨炮弹（原版 Bullets.artilleryDense）：hail/ripple 的默认弹药。 */
    public static final BulletType artilleryDense = reg(new ArtilleryBulletType(3f, 0f, "shell"){{
        hitEffect = Fx.flakExplosion;
        knockback = 0.8f;
        lifetime = 50f;
        width = 11f;
        height = 11f;
        //刻意降级：原版 collidesTiles = false（依赖抛物线落地检测；本项目 ArtilleryBulletType 刻意保持碰撞开启）
        splashDamageRadius = 25f;
        splashDamage = 33f;
    }});

    /** 塑料炮弹（原版 Bullets.artilleryPlastic）：命中炸出 10 片塑料破片。 */
    public static final BulletType artilleryPlastic = reg(new ArtilleryBulletType(3.4f, 0f, "shell"){{
        hitEffect = Fx.plasticExplosion;
        knockback = 1f;
        lifetime = 55f;
        width = 13f;
        height = 13f;
        //刻意降级：原版 collidesTiles = false（同 artilleryDense）
        splashDamageRadius = 35f;
        splashDamage = 45f;
        fragBullet = artilleryPlasticFrag;
        fragBullets = 10;
        backColor = Pal.plastaniumBack;
        frontColor = Pal.plastaniumFront;
    }});

    /** 追踪炮弹（原版 Bullets.artilleryHoming）。 */
    public static final BulletType artilleryHoming = reg(new ArtilleryBulletType(3f, 0f, "shell"){{
        hitEffect = Fx.flakExplosion;
        knockback = 0.8f;
        lifetime = 45f;
        width = 11f;
        height = 11f;
        //刻意降级：原版 collidesTiles = false（同 artilleryDense）
        splashDamageRadius = 25f;
        splashDamage = 33f;
        homingPower = 2f;
        homingRange = 50f;
    }});

    /** 燃烧炮弹（原版 Bullets.artilleryIncendiary）。 */
    public static final BulletType artilleryIncendiary = reg(new ArtilleryBulletType(3f, 0f, "shell"){{
        hitEffect = Fx.blastExplosion;
        knockback = 0.8f;
        lifetime = 60f;
        width = 13f;
        height = 13f;
        //刻意降级：原版 collidesTiles = false（同 artilleryDense）
        splashDamageRadius = 25f;
        splashDamage = 30f;
        status = StatusEffects.burning;
        frontColor = Pal.lightishOrange;
        backColor = Pal.lightOrange;
        trailEffect = Fx.incendTrail;
    }});

    /** 高爆炮弹（原版 Bullets.artilleryExplosive）：弹耗 4 倍，爆炸半径最大。 */
    public static final BulletType artilleryExplosive = reg(new ArtilleryBulletType(2f, 0f, "shell"){{
        hitEffect = Fx.blastExplosion;
        knockback = 0.8f;
        lifetime = 70f;
        width = 14f;
        height = 14f;
        //刻意降级：原版 collidesTiles = false（同 artilleryDense）
        ammoMultiplier = 4f;
        splashDamageRadius = 45f;
        splashDamage = 50f;
        backColor = Pal.missileYellowBack;
        frontColor = Pal.missileYellow;
    }});

    /** 单位用炮弹（原版 Bullets.artilleryUnit）：碰撞照常开启，命中范围比炮塔弹小。 */
    public static final BulletType artilleryUnit = reg(new ArtilleryBulletType(2f, 0f, "shell"){{
        hitEffect = Fx.blastExplosion;
        knockback = 0.8f;
        lifetime = 90f;
        width = 14f;
        height = 14f;
        //原版这里显式写 collides = collidesTiles = true（= 本项目默认值，故不重复写出）
        splashDamageRadius = 20f;
        splashDamage = 38f;
        backColor = Pal.bulletYellowBack;
        frontColor = Pal.bulletYellow;
    }});

    //--- flak ---

    /** 铅弹（原版 Bullets.flakLead）：scatter 的默认弹药。 */
    public static final BulletType flakLead = reg(new FlakBulletType(4.2f, 3f){{
        lifetime = 60f;
        ammoMultiplier = 4f;
        shootEffect = Fx.shootSmall;
        width = 6f;
        height = 8f;
        hitEffect = Fx.flakExplosion;
        splashDamage = 27f;
        splashDamageRadius = 15f;
    }});

    /** 废料弹（原版 Bullets.flakScrap）：射速减半换更大爆炸半径。 */
    public static final BulletType flakScrap = reg(new FlakBulletType(4f, 3f){{
        lifetime = 60f;
        ammoMultiplier = 5f;
        shootEffect = Fx.shootSmall;
        reloadMultiplier = 0.5f;
        width = 6f;
        height = 8f;
        hitEffect = Fx.flakExplosion;
        splashDamage = 22f;
        splashDamageRadius = 24f;
    }});

    /** 玻璃弹（原版 Bullets.flakGlass）：爆炸后溅 6 片玻璃破片。 */
    public static final BulletType flakGlass = reg(new FlakBulletType(4f, 3f){{
        lifetime = 70f;
        ammoMultiplier = 5f;
        shootEffect = Fx.shootSmall;
        reloadMultiplier = 0.8f;
        width = 6f;
        height = 8f;
        hitEffect = Fx.flakExplosion;
        splashDamage = 30f;
        splashDamageRadius = 26f;
        fragBullet = glassFrag;
        fragBullets = 6;
    }});

    /** 塑料弹（原版 Bullets.flakPlastic）：爆炸半径极大，溅 6 片塑料破片。 */
    public static final BulletType flakPlastic = reg(new FlakBulletType(4f, 6f){{
        splashDamageRadius = 50f;
        splashDamage = 25f;
        fragBullet = artilleryPlasticFrag;
        fragBullets = 6;
        hitEffect = Fx.plasticExplosion;
        frontColor = Pal.plastaniumFront;
        backColor = Pal.plastaniumBack;
        shootEffect = Fx.shootBig;
    }});

    /** 高爆弹（原版 Bullets.flakExplosive）：原版注释写明"默认弹，无改动"。 */
    public static final BulletType flakExplosive = reg(new FlakBulletType(4f, 5f){{
        //原版：default bullet type, no changes
        shootEffect = Fx.shootBig;
        ammoMultiplier = 4f;
    }});

    /** 浪涌弹（原版 Bullets.flakSurge）：命中时额外放出 2 道闪电。 */
    public static final BulletType flakSurge = reg(new FlakBulletType(4f, 7f){{
        splashDamage = 33f;
        lightining = 2;
        lightningLength = 12;
        shootEffect = Fx.shootBig;
    }});

    //--- missiles ---

    /** 高爆导弹（原版 Bullets.missileExplosive）：swarmer 的默认弹药。 */
    public static final BulletType missileExplosive = reg(new MissileBulletType(2.7f, 10f, "missile"){{
        width = 8f;
        height = 8f;
        shrinkY = 0f;
        drag = -0.01f;
        splashDamageRadius = 30f;
        splashDamage = 30f;
        ammoMultiplier = 4f;
        lifetime = 150f;
        hitEffect = Fx.blastExplosion;
        despawnEffect = Fx.blastExplosion;
    }});

    /** 燃烧导弹（原版 Bullets.missileIncendiary）。 */
    public static final BulletType missileIncendiary = reg(new MissileBulletType(2.9f, 12f, "missile"){{
        frontColor = Pal.lightishOrange;
        backColor = Pal.lightOrange;
        width = 7f;
        height = 8f;
        shrinkY = 0f;
        drag = -0.01f;
        homingPower = 7f;
        splashDamageRadius = 10f;
        splashDamage = 10f;
        lifetime = 160f;
        hitEffect = Fx.blastExplosion;
        status = StatusEffects.burning;
    }});

    /** 浪涌导弹（原版 Bullets.missileSurge）：命中时放出 2 道闪电。 */
    public static final BulletType missileSurge = reg(new MissileBulletType(4.4f, 15f, "bullet"){{
        width = 8f;
        height = 8f;
        shrinkY = 0f;
        drag = -0.01f;
        splashDamageRadius = 30f;
        splashDamage = 22f;
        lifetime = 150f;
        hitEffect = Fx.blastExplosion;
        despawnEffect = Fx.blastExplosion;
        lightining = 2;
        lightningLength = 14;
    }});

    /** 标枪导弹（原版 Bullets.missileJavelin）：高速、蛇形摆动。 */
    public static final BulletType missileJavelin = reg(new MissileBulletType(5f, 10.5f, "missile"){{
        width = 8f;
        height = 8f;
        shrinkY = 0f;
        drag = -0.003f;
        keepVelocity = false;
        splashDamageRadius = 20f;
        splashDamage = 1f;
        lifetime = 90f;
        trailColor = Color.valueOf("b6c6fd");
        hitEffect = Fx.blastExplosion;
        despawnEffect = Fx.blastExplosion;
        backColor = Pal.bulletYellowBack;
        frontColor = Pal.bulletYellow;
        weaveScale = 8f;
        weaveMag = 2f;
    }});

    /** 蜂群导弹（原版 Bullets.missileSwarm）：追踪 + 蛇形摆动。 */
    public static final BulletType missileSwarm = reg(new MissileBulletType(2.7f, 12f, "missile"){{
        width = 8f;
        height = 8f;
        shrinkY = 0f;
        drag = -0.003f;
        homingRange = 60f;
        keepVelocity = false;
        splashDamageRadius = 25f;
        splashDamage = 10f;
        lifetime = 120f;
        trailColor = Color.GRAY;
        backColor = Pal.bulletYellowBack;
        frontColor = Pal.bulletYellow;
        hitEffect = Fx.blastExplosion;
        despawnEffect = Fx.blastExplosion;
        weaveScale = 8f;
        weaveMag = 2f;
    }});

    /** 亡魂导弹（原版 Bullets.missileRevenant）：revenant/lich 的弹药，短命、摆动更小。 */
    public static final BulletType missileRevenant = reg(new MissileBulletType(2.7f, 12f, "missile"){{
        width = 8f;
        height = 8f;
        shrinkY = 0f;
        drag = -0.003f;
        homingRange = 60f;
        keepVelocity = false;
        splashDamageRadius = 25f;
        splashDamage = 10f;
        lifetime = 60f;
        trailColor = Pal.unitBack;
        backColor = Pal.unitBack;
        frontColor = Pal.unitFront;
        hitEffect = Fx.blastExplosion;
        despawnEffect = Fx.blastExplosion;
        weaveScale = 6f;
        weaveMag = 1f;
    }});

    //--- standard ---

    /** 石墨标准弹（原版 Bullets.standardDense）：伤害高、弹耗大、射速降 40%。 */
    public static final BulletType standardDense = reg(new BasicBulletType(3.5f, 18f, "bullet"){{
        width = 9f;
        height = 12f;
        reloadMultiplier = 0.6f;
        ammoMultiplier = 4f;
        lifetime = 60f;
    }});

    /** 钍标准弹（原版 Bullets.standardThorium）。 */
    public static final BulletType standardThorium = reg(new BasicBulletType(4f, 29f, "bullet"){{
        width = 10f;
        height = 13f;
        shootEffect = Fx.shootBig;
        smokeEffect = Fx.shootBigSmoke;
        ammoMultiplier = 4f;
        lifetime = 60f;
    }});

    /** 追踪标准弹（原版 Bullets.standardHoming）：射速 +40%。 */
    public static final BulletType standardHoming = reg(new BasicBulletType(3f, 9f, "bullet"){{
        width = 7f;
        height = 9f;
        homingPower = 5f;
        reloadMultiplier = 1.4f;
        ammoMultiplier = 5f;
        lifetime = 60f;
    }});

    /** 燃烧标准弹（原版 Bullets.standardIncendiary）。 */
    public static final BulletType standardIncendiary = reg(new BasicBulletType(3.2f, 11f, "bullet"){{
        width = 10f;
        height = 12f;
        frontColor = Pal.lightishOrange;
        backColor = Pal.lightOrange;
        status = StatusEffects.burning;
        inaccuracy = 3f;
        lifetime = 60f;
    }});

    /** 机甲小口径弹（原版 Bullets.standardMechSmall）：Mechs 专用，散布大。 */
    public static final BulletType standardMechSmall = reg(new BasicBulletType(4f, 9f, "bullet"){{
        width = 11f;
        height = 14f;
        lifetime = 40f;
        inaccuracy = 5f;
        despawnEffect = Fx.hitBulletSmall;
    }});

    /** 阔剑弹（原版 Bullets.standardGlaive）：Mechs.glaive 专用，燃烧弹。 */
    public static final BulletType standardGlaive = reg(new BasicBulletType(4f, 7.5f, "bullet"){{
        width = 10f;
        height = 12f;
        frontColor = Color.valueOf("feb380");
        backColor = Color.valueOf("ea8878");
        status = StatusEffects.burning;
        lifetime = 60f;
    }});

    /** 大口径石墨弹（原版 Bullets.standardDenseBig）：spectre 专用。 */
    public static final BulletType standardDenseBig = reg(new BasicBulletType(7f, 42f, "bullet"){{
        width = 15f;
        height = 21f;
        shootEffect = Fx.shootBig;
    }});

    /** 大口径钍弹（原版 Bullets.standardThoriumBig）：spectre 专用。 */
    public static final BulletType standardThoriumBig = reg(new BasicBulletType(8f, 65f, "bullet"){{
        width = 16f;
        height = 23f;
        shootEffect = Fx.shootBig;
    }});

    /** 大口径燃烧弹（原版 Bullets.standardIncendiaryBig）：spectre 专用。 */
    public static final BulletType standardIncendiaryBig = reg(new BasicBulletType(7f, 38f, "bullet"){{
        width = 16f;
        height = 21f;
        frontColor = Pal.lightishOrange;
        backColor = Pal.lightOrange;
        status = StatusEffects.burning;
        shootEffect = Fx.shootBig;
    }});

    //--- electric ---

    /**
     * 闪电伤害载体（原版 Bullets.damageLightning）：只用于给闪电落点挂"感电"状态，
     * 伤害本身由 {@link Lightning#createLighting} 直接结算。
     */
    public static final BulletType damageLightning = reg(new BulletType(0.0001f, 0f){{
        //原版 lifetime = Lightning.lifetime（T5 已给 Lightning 补上该静态常量 = 10f）
        lifetime = Lightning.lifetime;
        hitEffect = Fx.hitLancer;
        despawnEffect = Fx.none;
        status = StatusEffects.shocked;
        statusDuration = 10f;
    }});

    //--- heal ---

    /** 治疗弹（原版 Bullets.healBullet）：phantom 的弹药。 */
    public static final BulletType healBullet = reg(new HealBulletType(5.2f, 13f){{
        healPercent = 3f;
    }});

    /** 大型治疗弹（原版 Bullets.healBulletBig）：spirit 的弹药。 */
    public static final BulletType healBulletBig = reg(new HealBulletType(5.2f, 15f){{
        healPercent = 5.5f;
    }});

    //--- environment / misc ---

    /** 喷火器火焰（原版 Bullets.basicFlame）：scorch 的煤弹。 */
    public static final BulletType basicFlame = reg(new BulletType(3f, 6f){
        {
            ammoMultiplier = 3f;
            hitSize = 7f;
            lifetime = 42f;
            pierce = true;
            drag = 0.05f;
            statusDuration = 60f * 4;
            shootEffect = Fx.shootSmallFlame;
            hitEffect = Fx.hitFlameSmall;
            despawnEffect = Fx.none;
            status = StatusEffects.burning;
        }

        @Override
        public float range(){
            return 50f;
        }

        @Override
        public void draw(Bullet b){
        }
    });

    /** 黄玉火焰（原版 Bullets.pyraFlame）：scorch 的黄玉弹，燃烧更久。 */
    public static final BulletType pyraFlame = reg(new BulletType(3.3f, 9f){
        {
            ammoMultiplier = 4f;
            hitSize = 7f;
            lifetime = 42f;
            pierce = true;
            drag = 0.05f;
            statusDuration = 60f * 6;
            shootEffect = Fx.shootPyraFlame;
            hitEffect = Fx.hitFlameSmall;
            despawnEffect = Fx.none;
            status = StatusEffects.burning;
        }

        @Override
        public void draw(Bullet b){
        }
    });

    /** 闪电弹（原版 Bullets.lightning）：Mechs 专用，开火瞬间在炮口放一道闪电。 */
    public static final BulletType lightning = reg(new BulletType(0.001f, 12f){
        {
            lifetime = 1f;
            shootEffect = Fx.hitLancer;
            smokeEffect = Fx.none;
            despawnEffect = Fx.none;
            hitEffect = Fx.hitLancer;
            keepVelocity = false;
        }

        @Override
        public float range(){
            return 70f;
        }

        @Override
        public void draw(Bullet b){
        }

        @Override
        public void init(Bullet b){
            //本项目是 Lightning.createLighting(seed, team, color, damage, x, y, angle, length)（多一个 seed 参数）
            //原版写的是 damage * (b.getOwner() instanceof Player ? rules.playerDamageMultiplier : 1f)。
            //本项目 Player 是"持有单位引用"的轻量对象、不会当 owner 传进来（子弹 owner 始终是 BaseUnit）
            //→ 用 BaseUnit.isPlayer 判定"是否玩家单位"，等价于原版的 instanceof Player。
            //Rules.playerDamageMultiplier 已由 T2 补齐（消费点 Player#damageMultiplier）。
            boolean playerOwned = b.getOwner() instanceof com.phoenix.game.entities.type.BaseUnit
                && ((com.phoenix.game.entities.type.BaseUnit)b.getOwner()).isPlayer;
            //原版 Bullets.java:620：damage * (b.getOwner() instanceof Player ? rules.playerDamageMultiplier : 1f)
            //——非玩家分支乘 1f（此前误写 unitDamageMultiplier，已按原版修正）
            float dmgMult = playerOwned ? Vars.state.rules.playerDamageMultiplier : 1f;
            Lightning.createLighting(Lightning.nextSeed(), b.getTeam(), Pal.lancerLaser,
                damage * dmgMult, b.x, b.y, b.rot(), 30);
        }
    });

    /** 电弧弹（原版 Bullets.arc）：电弧炮塔的弹药，开火瞬间放一道 25 段闪电。 */
    public static final BulletType arc = reg(new BulletType(0.001f, 21f){
        {
            lifetime = 1f;
            despawnEffect = Fx.none;
            hitEffect = Fx.hitLancer;
        }

        @Override
        public void draw(Bullet b){
        }

        @Override
        public void init(Bullet b){
            //同 lightning：本项目是 createLighting(seed, ...)（原版是 Lightning.create(...)）
            Lightning.createLighting(Lightning.nextSeed(), b.getTeam(), Pal.lancerLaser, damage, b.x, b.y, b.rot(), 25);
        }
    });

    //--- liquid ---

    /** 水弹（原版 Bullets.waterShot）：可灭火。 */
    public static final BulletType waterShot = reg(new LiquidBulletType(Liquids.water){{
        knockback = 0.7f;
    }});

    /** 低温液弹（原版 Bullets.cryoShot）：可灭火 + 挂 freezing 状态。 */
    public static final BulletType cryoShot = reg(new LiquidBulletType(Liquids.cryofluid));

    /** 熔渣弹（原版 Bullets.slagShot）。 */
    public static final BulletType slagShot = reg(new LiquidBulletType(Liquids.slag){{
        damage = 4f;
        drag = 0.03f;
    }});

    /** 石油弹（原版 Bullets.oilShot）。 */
    public static final BulletType oilShot = reg(new LiquidBulletType(Liquids.oil){{
        drag = 0.03f;
    }});

    /** 喷发弹（原版 Bullets.eruptorShot）：eruptor 单位的熔渣弹，伤害更低但更快。 */
    public static final BulletType eruptorShot = reg(new LiquidBulletType(Liquids.slag){{
        damage = 2f;
        speed = 2.1f;
        drag = 0.02f;
    }});

    /** 通用破片（原版 Bullets.frag）。 */
    public static final BulletType frag = reg(new BasicBulletType(5f, 8f, "bullet"){{
        width = 8f;
        height = 9f;
        shrinkY = 0.5f;
        lifetime = 50f;
        drag = 0.04f;
    }});

    //--- bombs ---

    /** 燃烧弹（原版 Bullets.bombIncendiary）：命中必点火。 */
    public static final BulletType bombIncendiary = reg(new BombBulletType(7f, 10f, "shell"){{
        width = 8f;
        height = 12f;
        hitEffect = Fx.flakExplosion;
        backColor = Pal.lightOrange;
        frontColor = Pal.lightishOrange;
        incendChance = 1f;
        incendAmount = 3;
        incendSpread = 10f;
    }});

    /** 油弹（原版 Bullets.bombOil）：命中后在落点附近留下 3 摊石油。 */
    public static final BulletType bombOil = reg(new BombBulletType(2f, 3f, "shell"){
        {
            width = 8f;
            height = 12f;
            hitEffect = Fx.pulverize;
            backColor = new Color(0x4f4f4fff);
            frontColor = Color.GRAY;
        }

        @Override
        public void hit(Bullet b, float x, float y){
            super.hit(b, x, y);

            for(int i = 0; i < 3; i++){
                Tile tile = Vars.world == null ? null : Vars.world.tileWorld(x + Mathf.range(8f), y + Mathf.range(8f));
                Puddle.deposit(tile, Liquids.oil, 5f);
            }
        }
    });

    // ==================================================================================
    // N3 批：单位弹药对齐新增（原版把该弹写在使用它的单位里，本项目提取成具名弹药）
    // ⚠ 同样追加在**本类末尾**以保住既有弹药 id；代价是其后注册的内容 id +1（见类头 F2 段说明）。
    // ==================================================================================

    /**
     * crawler 的自爆弹（原版 UnitTypes.java:104-112 在 crawler 武器里**内联**的
     * {@code BombBulletType(2f, 3f, "clear")}）。
     * <p>原版内联配置：splashDamage=30、splashDamageRadius=55、lifetime=30、speed=1.1、
     * instantDisappear、killShooter、hitEffect=Fx.pulverize。
     * <p>这里直接写成 {@code BombBulletType(30f, 55f, "clear")}（该类前两参是 damage/radius）即等价于
     * 原版「先构造 2f/3f 再用 30f/55f 覆盖」的结果；其余字段逐项照抄。
     * <p>⚠ 刻意降级：BombBulletType 在本项目保持碰撞开启（原版 collides=collidesTiles=false），
     * 与 {@link #artilleryBullet} 的同类说明一致。
     */
    public static final BulletType bombCrawler = reg(new BombBulletType(30f, 55f, "clear"){{
        hitEffect = Fx.pulverize;
        lifetime = 30f;
        speed = 1.1f;
        instantDisappear = true;
        killShooter = true;
    }});
}
