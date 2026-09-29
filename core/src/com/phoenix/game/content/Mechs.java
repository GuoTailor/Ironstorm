package com.phoenix.game.content;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.effect.Lightning;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Mech;
import com.phoenix.game.type.Weapon;
import com.phoenix.game.entities.bullet.BombBulletType;
import io.anuke.mindustry.gen.Sounds;

/**
 * 机甲（玩家装备）定义集合。参照 Mindustry mindustry.content.Mechs 移植。
 * <p>id 顺序 = 本类的创建顺序（由 {@code ContentLoader} 注册分配），与原版加载顺序一致。
 *
 * <p><b>与本项目的差异（逐条对应原版写法）</b>：
 * <ul>
 *   <li><b>弹药</b>：已全部使用原版弹药（F12b 修正：此前注释说这些弹药"未移植"，其实
 *       {@code Bullets.standardMechSmall/lightning/healBullet/missileSwarm/missileJavelin/standardGlaive}
 *       早已在 {@code Bullets} 里注册，只是本类的 TODO 未同步更新）。</li>
 *   <li><b>Player 相关</b>：原版 {@code Player} 自身就是单位（{@code player.healBy/velocity()/rotation/
 *       shootHeat/timer}）；本项目 {@code Player} 只是"被控单位的持有者" → 一律改成走
 *       {@code player.unit()}；{@code shootHeat} 完全没有对应物，依赖它的覆写（omega 的
 *       绘制/护甲/散布）已整段 TODO 掉。</li>
 *   <li><b>计时器</b>：原版 {@code Player.timer} + {@code Player.timerAbility}；本项目计时器在
 *       {@code BaseUnit} 上 → 见 {@link #timerAbility}。</li>
 *   <li><b>着色器</b>：omega 的装甲依赖 {@code Shaders.build}；本项目 {@code graphics/Shaders} 类<b>存在</b>
 *       （已实现 {@code fog}），但缺 {@code build} 成员 → 对应绘制整段 TODO 掉。</li>
 *   <li><b>加色混合</b>：本项目没有 arc 的 {@code Draw.blend(Blending.additive)}，但可用
 *       {@code Core.batch.setBlendFunction(GL_SRC_ALPHA, GL_ONE)} 等价实现（javelin 护盾已改用它；
 *       同款设施见 {@code world/blocks/production/Drill} 与 {@code power/LightBlock}）。
 *       {@code SpriteBatch.setBlendFunction} 内部会先 {@code flush()}，所以「切→画→恢复」是安全的。</li>
 *   <li><b>规则</b>：原版 {@code rules.playerDamageMultiplier} 已在 {@code Rules} 中（T2 补），
 *       本类机甲闪电照原版乘它（与子弹统一乘 {@code unitDamageMultiplier} 的约定不同：
 *       子弹 owner 恒为单位，无法区分是否玩家）。</li>
 *   <li><b>闪电</b>：原版 {@code Lightning.create(team, color, damage, x, y, angle, length)}；
 *       本项目是 {@code createLighting(seed, ...)} → 补一个 {@code Lightning.nextSeed()}。</li>
 *   <li><b>子弹字段</b>：原版 {@code BulletType.bulletWidth/bulletHeight/shootSound} 未移植
 *       （本项目 {@code BasicBulletType} 叫 {@code width/height}，炮口音效挂在 {@code Weapon} 上）。</li>
 *   <li><b>⚠ 钩子的实际调用方</b>：本项目没有原版的 {@code player.mech} 装备系统，
 *       {@link Mech} 的 {@code updateAlt/draw/onLand/getExtraArmor/spreadX/getRotationAlpha/canShoot}
 *       **目前没有任何调用方**（原版由 Player 驱动）。机甲行为被搬到了 {@code content/UnitTypes}
 *       里的同名 UnitType（javelin-ship / trident-ship / glaive-ship 的
 *       {@code updateAlt/drawOverlay/canShoot}），那才是本类这些改动的**真实运行路径**。</li>
 * </ul>
 */
public class Mechs implements com.phoenix.game.ctype.ContentList{
    public static Mech alpha, delta, tau, omega, dart, javelin, trident, glaive;

    public static Mech starter;

    /**
     * TODO 原版是 {@code Player.timerAbility}（Player 自带计时器与索引）；本项目 Player 没有计时器系统，
     * 计时器在 {@link BaseUnit} 上（{@code new Interval(5)}，索引 0~3 已被 AI 的
     * timerTarget/timerTarget2/timerShootLeft/timerShootRight 占用）→ 借用空闲槽位 4。
     * 等 Player 的机甲计时器移植后改回原版写法。
     */
    private static final int timerAbility = 4;

    @Override
    public void load(){

        alpha = new Mech("alpha-mech", false){
            {
                drillPower = 1;
                mineSpeed = 1.5f;
                mass = 1.2f;
                speed = 0.5f;
                itemCapacity = 40;
                boostSpeed = 0.95f;
                buildPower = 1.2f;
                engineColor = Color.valueOf("ffd37f");
                health = 250f;

                weapon = new Weapon("blaster"){{
                    length = 1.5f;
                    reload = 14f;
                    alternate = true;
                    ejectEffect = Fx.shellEjectSmall;
                    //F12b：原版弹药（此前 TODO 说"未移植"是过时信息，Bullets.standardMechSmall 早已存在）
                    bullet = Bullets.standardMechSmall;
                }};
            }

            @Override
            public void updateAlt(Player player){
                //TODO 原版 player.healBy(...)（Player 自身是单位）；本项目 Player 只是持有者 → 给被控单位回血
                if(player.unit() != null) player.unit().healBy(Time.delta() * 0.09f);
            }

        };

        delta = new Mech("delta-mech", false){
            float cooldown = 120;

            {
                drillPower = -1;
                speed = 0.75f;
                boostSpeed = 0.95f;
                itemCapacity = 15;
                mass = 0.9f;
                health = 150f;
                buildPower = 0.9f;
                weaponOffsetX = -1;
                weaponOffsetY = -1;
                engineColor = Color.valueOf("d3ddff");

                weapon = new Weapon("shockgun"){{
                    shake = 2f;
                    length = 1f;
                    reload = 55f;
                    shotDelay = 3f;
                    alternate = true;
                    shots = 2;
                    inaccuracy = 0f;
                    ejectEffect = Fx.none;
                    //F12b：原版弹药（Bullets.lightning 早已存在，此前 TODO 是过时信息）
                    bullet = Bullets.lightning;
                    shootSound = Sounds.spark;
                }};
            }

            @Override
            public void onLand(Player player){
                //TODO 原版 player.timer.get(Player.timerAbility, cooldown)；本项目 Player 没有计时器 → 借用单位计时器
                BaseUnit unit = player.unit();
                if(unit == null || !unit.getTimer().get(timerAbility, cooldown)) return;

                Effects.shake(1f, 1f, player.x, player.y);
                //TODO 原版 Effects.effect(Fx.landShock, player)（Player 是 Position）；本项目传坐标
                Effects.effect(Fx.landShock, player.x, player.y);
                for(int i = 0; i < 8; i++){
                    Time.run(Mathf.random(8f), () -> {
                        //原版是 Lightning.create(team, color, damage, x, y, angle, length)；本项目是 createLighting(seed, ...)（等价）
                        Lightning.createLighting(Lightning.nextSeed(), unit.getTeam(), Pal.lancerLaser,
                            17f * Vars.state.rules.playerDamageMultiplier, player.x, player.y, Mathf.random(360f), 14);
                    });
                }
            }
        };

        tau = new Mech("tau-mech", false){
            float healRange = 60f;
            float healAmount = 10f;
            float healReload = 160f;
            boolean wasHealed;

            {
                drillPower = 4;
                mineSpeed = 3f;
                itemCapacity = 70;
                weaponOffsetY = -1;
                weaponOffsetX = 1;
                mass = 1.75f;
                speed = 0.44f;
                drag = 0.35f;
                boostSpeed = 0.8f;
                canHeal = true;
                health = 200f;
                buildPower = 1.6f;
                engineColor = Pal.heal;

                weapon = new Weapon("heal-blaster"){{
                    length = 1.5f;
                    reload = 24f;
                    alternate = false;
                    ejectEffect = Fx.none;
                    recoil = 2f;
                    //F12b：原版弹药（Bullets.healBullet 早已存在，此前 TODO 是过时信息）
                    bullet = Bullets.healBullet;
                    shootSound = Sounds.pew;
                }};
            }

            @Override
            public void updateAlt(Player player){
                BaseUnit unit = player.unit();
                if(unit == null) return;

                //TODO 原版 player.timer.get(Player.timerAbility, healReload)；本项目 Player 没有计时器 → 借用单位计时器
                if(unit.getTimer().get(timerAbility, healReload)){
                    wasHealed = false;

                    Units.nearby(unit.getTeam(), unit.x, unit.y, healRange, other -> {
                        //TODO 原版 unit.health（字段）；本项目 BaseUnit 是 health() 方法
                        if(other.health() < other.maxHealth()){
                            Effects.effect(Fx.heal, other);
                            wasHealed = true;
                        }
                        other.healBy(healAmount);
                    });

                    if(wasHealed){
                        //TODO 原版 Effects.effect(Fx.healWave, player)（Player 是 Position）；本项目传坐标
                        Effects.effect(Fx.healWave, player.x, player.y);
                    }
                }
            }
        };

        omega = new Mech("omega-mech", false){
            protected TextureRegion armorRegion;

            {
                drillPower = 2;
                mineSpeed = 1.5f;
                itemCapacity = 80;
                speed = 0.36f;
                boostSpeed = 0.6f;
                mass = 4f;
                shake = 4f;
                weaponOffsetX = 1;
                weaponOffsetY = 0;
                engineColor = Color.valueOf("feb380");
                health = 350f;
                buildPower = 1.5f;
                weapon = new Weapon("swarmer"){{
                    length = 1.5f;
                    recoil = 4f;
                    reload = 38f;
                    shots = 4;
                    spacing = 8f;
                    inaccuracy = 8f;
                    alternate = true;
                    ejectEffect = Fx.none;
                    shake = 3f;
                    //F12b：原版弹药（Bullets.missileSwarm 早已存在，此前 TODO 是过时信息）
                    bullet = Bullets.missileSwarm;
                    shootSound = Sounds.shootBig;
                }};
            }

            //TODO 以下覆写全部依赖 Player.shootHeat（"开火热度"，随开火累积，本项目 Player 没有该字段，
            //     也没有可替代的热量机制）→ 整段注释保留，等 Player 的机甲系统移植后启用：
            //
            //  @Override
            //  public float getRotationAlpha(Player player){
            //      return 0.6f - player.shootHeat * 0.3f;
            //  }
            //
            //  @Override
            //  public float spreadX(Player player){
            //      return player.shootHeat * 2f;
            //  }
            //
            //  @Override
            //  public void updateAlt(Player player){
            //      float scl = 1f - player.shootHeat / 2f * Time.delta();
            //      player.velocity().scl(scl);
            //  }
            //
            //  @Override
            //  public float getExtraArmor(Player player){
            //      return player.shootHeat * 30f;
            //  }
            //
            //  @Override
            //  public void draw(Player player){
            //      if(player.shootHeat <= 0.01f) return;
            //      Shaders.build.progress = player.shootHeat;
            //      Shaders.build.region = armorRegion;
            //      Shaders.build.time = Time.time() / 10f;
            //      Shaders.build.color.set(Pal.accent).a = player.shootHeat;
            //      Draw.shader(Shaders.build);
            //      Draw.rect(armorRegion, player.x, player.y, player.rotation);
            //      Draw.shader();
            //  }
            //
            // 注：draw(...) 另外还依赖 Shaders.build（本项目 graphics/Shaders 类存在、但缺 build 成员，见 graphics/CacheLayer 的说明）。

            @Override
            public void load(){
                super.load();
                //原版是 Core.atlas.find(name + "-armor")；本项目统一用 findRegion（缺图返回 null，见 world/blocks/LiquidBlock 的说明）
                armorRegion = Core.atlas == null ? null : Core.atlas.findRegion(name + "-armor");
            }
        };

        dart = new Mech("dart-ship", true){
            {
                drillPower = 1;
                mineSpeed = 3f;
                speed = 0.5f;
                drag = 0.09f;
                health = 200f;
                weaponOffsetX = -1;
                weaponOffsetY = -1;
                engineColor = Pal.lightTrail;
                cellTrnsY = 1f;
                buildPower = 1.1f;
                weapon = new Weapon("blaster"){{
                    length = 1.5f;
                    reload = 15f;
                    alternate = true;
                    ejectEffect = Fx.shellEjectSmall;
                    bullet = Bullets.standardCopper;
                }};
            }

            @Override
            public boolean alwaysUnlocked(){
                return true;
            }
        };

        javelin = new Mech("javelin-ship", true){
            float minV = 3.6f;
            float maxV = 6f;
            TextureRegion shield;

            {
                drillPower = -1;
                speed = 0.11f;
                drag = 0.01f;
                mass = 2f;
                health = 170f;
                engineColor = Color.valueOf("d3ddff");
                cellTrnsY = 1f;
                weapon = new Weapon("missiles"){{
                    length = 1.5f;
                    reload = 70f;
                    shots = 4;
                    inaccuracy = 2f;
                    alternate = true;
                    ejectEffect = Fx.none;
                    velocityRnd = 0.2f;
                    spacing = 1f;
                    //F12b：原版弹药（Bullets.missileJavelin 早已存在，此前 TODO 是过时信息）
                    bullet = Bullets.missileJavelin;
                    shootSound = Sounds.missile;
                }};
            }

            @Override
            public void load(){
                super.load();
                //原版是 Core.atlas.find(name + "-shield")；本项目统一用 findRegion
                shield = Core.atlas == null ? null : Core.atlas.findRegion(name + "-shield");
            }

            @Override
            public float getRotationAlpha(Player player){
                return 0.5f;
            }

            @Override
            public void updateAlt(Player player){
                BaseUnit unit = player.unit();
                if(unit == null) return;

                float scl = scld(player);
                if(Mathf.chance(Time.delta() * (0.15f * scl))){
                    Effects.effect(Fx.hitLancer, Pal.lancerLaser, unit.x, unit.y);
                    //原版是 Lightning.create(team, color, damage, x, y, angle, length)；本项目是 createLighting(seed, ...)（等价）
                    Lightning.createLighting(Lightning.nextSeed(), unit.getTeam(), Pal.lancerLaser,
                        10f * Vars.state.rules.playerDamageMultiplier,
                        unit.x + unit.velocity().x, unit.y + unit.velocity().y, unit.rotation, 14);
                }
            }

            @Override
            public void draw(Player player){
                BaseUnit unit = player.unit();
                if(unit == null || shield == null) return;

                float scl = scld(player);
                if(scl < 0.01f) return;
                Draw.color(Pal.lancerLaser);
                Draw.alpha(scl / 2f);
                //原版 Draw.blend(Blending.additive) / Draw.blend()；本项目无 arc 的 Blending，
                //用批次加色混合等价实现（setBlendFunction 内部会先 flush，画完恢复默认混合）
                Core.batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE);
                Draw.rect(shield, unit.x + Mathf.range(scl / 2f), unit.y + Mathf.range(scl / 2f), unit.rotation - 90);
                Core.batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
                Draw.color();
            }

            float scld(Player player){
                //TODO 原版 player.velocity()（Player 自身是单位）；本项目 Player 只是持有者 → 用被控单位的速度
                BaseUnit unit = player.unit();
                if(unit == null) return 0f;
                return Mathf.clamp((unit.velocity().len() - minV) / (maxV - minV));
            }
        };

        trident = new Mech("trident-ship", true){
            {
                drillPower = 2;
                speed = 0.15f;
                drag = 0.034f;
                mass = 2.5f;
                turnCursor = false;
                health = 250f;
                itemCapacity = 30;
                engineColor = Color.valueOf("84f491");
                cellTrnsY = 1f;
                buildPower = 2.5f;
                weapon = new Weapon("bomber"){{
                    length = 0f;
                    width = 2f;
                    reload = 25f;
                    shots = 2;
                    shotDelay = 1f;
                    shots = 8;
                    alternate = true;
                    ejectEffect = Fx.none;
                    velocityRnd = 1f;
                    inaccuracy = 20f;
                    ignoreRotation = true;
                    bullet = new BombBulletType(16f, 25f, "shell"){{
                        //TODO 原版是 bulletWidth/bulletHeight（BulletType 的字段）；本项目叫 width/height（在 BasicBulletType 上）
                        width = 10f;
                        height = 14f;
                        hitEffect = Fx.flakExplosion;
                        shootEffect = Fx.none;
                        smokeEffect = Fx.none;
                        //TODO 原版 bullet.shootSound = Sounds.artillery（本项目 BulletType 没有 shootSound 字段）→ 挂到 Weapon 上
                    }};
                    shootSound = Sounds.artillery;
                }};
            }

            @Override
            public boolean canShoot(Player player){
                //TODO 原版 player.velocity()（Player 自身是单位）；本项目 Player 只是持有者 → 用被控单位的速度
                BaseUnit unit = player.unit();
                return unit != null && unit.velocity().len() > 1.2f;
            }
        };

        glaive = new Mech("glaive-ship", true){
            {
                drillPower = 4;
                mineSpeed = 1.3f;
                speed = 0.32f;
                drag = 0.06f;
                mass = 3f;
                health = 240f;
                itemCapacity = 60;
                engineColor = Color.valueOf("feb380");
                cellTrnsY = 1f;
                buildPower = 1.2f;

                weapon = new Weapon("bomber"){{
                    length = 1.5f;
                    reload = 13f;
                    alternate = true;
                    ejectEffect = Fx.shellEjectSmall;
                    //F12b：原版弹药（Bullets.standardGlaive 早已存在，此前 TODO 是过时信息）
                    bullet = Bullets.standardGlaive;
                    shootSound = Sounds.shootSnap;
                }};
            }
        };

        starter = dart;
    }
}
