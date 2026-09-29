package com.phoenix.game.content;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.entities.bullet.BasicBulletType;
import com.phoenix.game.entities.bullet.BombBulletType;
import com.phoenix.game.entities.type.base.BuilderDrone;
import com.phoenix.game.entities.type.base.GroundUnit;
import com.phoenix.game.entities.type.base.FlyingUnit;
import com.phoenix.game.entities.type.base.HoverUnit;
import com.phoenix.game.entities.type.base.MinerDrone;
import com.phoenix.game.entities.type.base.RepairDrone;
import com.phoenix.game.type.UnitType;
import com.phoenix.game.type.Weapon;

import com.phoenix.game.content.Bullets;
import io.anuke.mindustry.gen.Sounds;

/**
 * create by GYH on 2025/3/14
 * 最小实现：单位类型定义。参照 Mindustry mindustry.content.UnitTypes 移植。
 * <p>F1e2 批照原版补回了 draug / spirit / phantom / revenant / lich / reaper 六个单位（追加在末尾，
 * 见 load() 末尾注释）；原版 UnitTypes 里还有的 15 个单位现已齐（本项目另有原版没有的
 * flare 一个，属本项目自建内容）。
 * <p>F7b 批删除了原本自建的 grenadier / tank 两个单位：它们没有贴图（任何来源都没有切图）→
 * 游戏里不可见，且定位与原版已有的 eruptor（榴弹）/ titan（重型）重复（DefaultWaves 里
 * 那两组本来就在刷）→ 直接删掉，波次编成改用原版单位。
 * <p>F12 批追加 javelin-ship / trident-ship / glaive-ship：原版这 3 个是 {@code Mechs.java} 里的
 * 飞船 Mech（不是 UnitType），本项目没有机甲系统 → 按 {@code MechPad} 的降级方案
 * （{@code unitTypeName = mech.name}）用同名 UnitType 承接，贴图/数值照 Mech 那份。
 */
public class UnitTypes implements com.phoenix.game.ctype.ContentList {
    /** 全部单位类型，按注册顺序索引（指向内容加载器的单位列表）。 */
    public static Array<UnitType> all;
    public static UnitType
            dagger, crawler, titan, fortress, eruptor, chaosArray, eradicator, flare, wraith, ghoul,
            //F1e2：照原版补回的单位
            draug, spirit, phantom, revenant, lich, reaper,
            //F12：3 个飞船平台换装用的同名单位（数值照 Mechs.javelin/trident/glaive）
            javelinShip, tridentShip, glaiveShip,
            //出生单位：原版 Mechs.starter = dart-ship（数值照 Mechs.dart）
            dartShip;

    @Override
    public void load() {
        if(all != null) return;
        all = com.phoenix.game.Vars.content.units();
        dagger = new UnitType("dagger", GroundUnit::new) {{
            maxVelocity = 1.1f;
            speed = 0.2f;
            drag = 0.4f;
            hitsize = 8f;
            mass = 1.75f;
            health = 130;
            weapon = new Weapon("chain-blaster") {{
                //原版 UnitTypes.java:89：Bullets.standardCopper（此前误用自建别名 basicBullet）
                bullet = Bullets.standardCopper;
                length = 1.5f;
                reload = 28f;
                alternate = true;
                //原版 UnitTypes.java:88
                ejectEffect = Fx.shellEjectSmall;
            }};
        }};
        crawler = new UnitType("crawler", GroundUnit::new) {{
            maxVelocity = 1.27f;
            speed = 0.285f;
            drag = 0.4f;
            hitsize = 8f;
            mass = 1.75f;
            health = 120;
            weapon = new Weapon() {{
                //原版 UnitTypes.java:104-112 是内联 BombBulletType(2f,3f)：splashDamage30 / splashDamageRadius55 /
                //lifetime30 / speed1.1 / instantDisappear / killShooter（此前误用自建别名 artilleryBullet，数值差极大）
                //→ 本项目在 Bullets 新增等价弹药 bombCrawler（见 Bullets.java）
                bullet = Bullets.bombCrawler;
                reload = 12f;
                //原版 UnitTypes.java:102
                ejectEffect = Fx.none;
                shootSound = Sounds.explosion;
            }};
        }};

        titan = new UnitType("titan", GroundUnit::new) {{
            maxVelocity = 0.8f;
            speed = 0.22f;
            drag = 0.4f;
            mass = 3.5f;
            hitsize = 9f;
            range = 10f;
            rotatespeed = 0.1f;
            health = 460;
            //原版 UnitTypes.java:125：免疫 burning
            immunities.add(StatusEffects.burning);
            weapon = new Weapon("flamethrower") {{
                //原版 UnitTypes.java:133：Bullets.basicFlame（此前误用自建别名 flameBullet）
                bullet = Bullets.basicFlame;
                shootSound = Sounds.flame;
                length = 1f;
                reload = 14f;
                alternate = true;
                recoil = 1f;
                //原版 UnitTypes.java:132
                ejectEffect = Fx.none;
            }};
        }};

        fortress = new UnitType("fortress", GroundUnit::new) {{
            maxVelocity = 0.78f;
            speed = 0.15f;
            drag = 0.4f;
            mass = 5f;
            hitsize = 10f;
            rotatespeed = 0.06f;
            targetAir = false;
            health = 750;
            weapon = new Weapon("artillery") {{
                //原版 UnitTypes.java:154：Bullets.artilleryUnit（damage0/splashDamage38/radius20）
                bullet = Bullets.artilleryUnit;
                length = 1f;
                reload = 60f;
                width = 10f;
                alternate = true;
                //原版 UnitTypes.java:153
                ejectEffect = Fx.shellEjectMedium;
                recoil = 4f;
                shake = 2f;
                shootSound = Sounds.artillery;
            }};
        }};

        eruptor = new UnitType("eruptor", GroundUnit::new) {{
            maxVelocity = 0.81f;
            speed = 0.16f;
            drag = 0.4f;
            mass = 5f;
            hitsize = 9f;
            rotatespeed = 0.05f;
            targetAir = false;
            health = 600;
            //原版 UnitTypes.java:168：免疫 burning + melting
            immunities.add(StatusEffects.burning);
            immunities.add(StatusEffects.melting);
            weapon = new Weapon("eruption") {{
                //原版 UnitTypes.java:174：Bullets.eruptorShot（LiquidBulletType slag，damage2）
                bullet = Bullets.eruptorShot;
                length = 3f;
                reload = 10f;
                alternate = true;
                //原版 UnitTypes.java:173
                ejectEffect = Fx.none;
                recoil = 1f;
                width = 7f;
                shootSound = Sounds.flame;
            }};
        }};

        chaosArray = new UnitType("chaos-array", GroundUnit::new) {{
            maxVelocity = 0.68f;
            speed = 0.12f;
            drag = 0.4f;
            mass = 5f;
            hitsize = 20f;
            rotatespeed = 0.06f;
            health = 3000;
            boss = true;
            weapon = new Weapon("chaos") {{
                //原版 UnitTypes.java:200：Bullets.flakSurge（splashDamage33 + 2 道闪电）
                bullet = Bullets.flakSurge;
                length = 8f;
                reload = 50f;
                width = 17f;
                alternate = true;
                recoil = 3f;
                shake = 2f;
                shots = 4;
                spacing = 4f;
                shotDelay = 5;
                //原版 UnitTypes.java:199
                ejectEffect = Fx.shellEjectMedium;
                shootSound = Sounds.shootBig;
            }};
        }};

        eradicator = new UnitType("eradicator", GroundUnit::new) {{
            maxVelocity = 0.68f;
            speed = 0.12f;
            drag = 0.4f;
            mass = 5f;
            hitsize = 20f;
            rotatespeed = 0.06f;
            health = 9000;
            boss = true;
            weapon = new Weapon("eradication") {{
                //原版 UnitTypes.java:225：Bullets.standardThoriumBig（speed8/damage65）
                bullet = Bullets.standardThoriumBig;
                length = 13f;
                reload = 30f;
                width = 22f;
                alternate = true;
                recoil = 3f;
                shake = 2f;
                inaccuracy = 3f;
                shots = 4;
                spacing = 0f;
                shotDelay = 3;
                //原版 UnitTypes.java:224
                ejectEffect = Fx.shellEjectMedium;
                shootSound = Sounds.shootBig;
            }};
        }};

        //飞行单位：直线穿越地面障碍，激活 WaveSpawner.flySpawns 分支
        flare = new UnitType("flare", FlyingUnit::new) {{
            flying = true;
            maxVelocity = 2.2f;
            speed = 0.6f;
            drag = 0.15f;
            hitsize = 6f;
            mass = 0.8f;
            health = 90;
            weapon = new Weapon("flare-shot") {{
                bullet = Bullets.flareShot;
                reload = 22f;
                shootSound = Sounds.shoot;
            }};
        }};

        //飞行单位 wraith：快、小、散射
        wraith = new UnitType("wraith", FlyingUnit::new) {{
            flying = true;
            speed = 0.3f;
            maxVelocity = 1.9f;
            drag = 0.01f;
            mass = 1.5f;
            hitsize = 7f;
            health = 75;
            range = 140f;
            weapon = new Weapon(){{
                length = 1.5f;
                reload = 28f;
                alternate = true;
                //原版 UnitTypes.java:243
                ejectEffect = Fx.shellEjectSmall;
                bullet = Bullets.standardCopper;
                shootSound = Sounds.shoot;
            }};
        }};

        //飞行单位 ghoul：慢、厚、范围轰炸
        ghoul = new UnitType("ghoul", FlyingUnit::new) {{
            flying = true;
            speed = 0.2f;
            maxVelocity = 1.4f;
            mass = 3f;
            drag = 0.01f;
            //原版 UnitTypes.java:249 未设 hitsize → 走 UnitType 默认 7f（本项目此前误设 10f，已删）
            targetAir = false;
            health = 220;
            range = 140f;
            weapon = new Weapon(){{
                length = 0f;
                width = 2f;
                reload = 12f;
                alternate = true;
                //原版 UnitTypes.java:264-267
                ejectEffect = Fx.none;
                velocityRnd = 1f;
                inaccuracy = 40f;
                ignoreRotation = true;
                bullet = Bullets.bombExplosive;
                shootSound = Sounds.none;
            }};
        }};

        // ================= F1e2：照原版补回缺失的单位 =================
        // ⚠ 追加在 load() **末尾**：单位 ID = 在本列表里的下标，追加在最后才不会让已有单位 ID 错位。
        //    代价是它们的 id 与原版不一致（原版 draug/spirit/phantom 排在 dagger 之前、revenant/lich/reaper 排在 ghoul 之后）
        //    → 彻底的 id 对齐留给"内容 id 对齐"批次统一做。
        // ⚠ 单位在 ContentLoader.lists() 里排在 Blocks 之前 → 新增 6 个单位会让**后面注册的 Blocks 全体 id +6**。

        //采矿无人机（原版 MinerDrone）：飞到矿脉上开采，把物品运回核心。
        //原版 weapon 没设 reload（=0 → 每帧开火）；这里照抄，不臆造数值
        draug = new UnitType("draug", MinerDrone::new) {{
            flying = true;
            drag = 0.01f;
            speed = 0.3f;
            maxVelocity = 1.2f;
            range = 50f;
            health = 80;
            minePower = 0.9f;
            engineSize = 1.8f;
            engineOffset = 5.7f;
            weapon = new Weapon("you have incurred my wrath. prepare to die.") {{
                bullet = Bullets.lancerLaser;
            }};
        }};

        //维修无人机（原版 RepairDrone）：找受损的友方建筑飞过去用武器"修"它。
        spirit = new UnitType("spirit", RepairDrone::new) {{
            flying = true;
            drag = 0.01f;
            speed = 0.42f;
            maxVelocity = 1.6f;
            range = 50f;
            health = 100;
            engineSize = 1.8f;
            engineOffset = 5.7f;
            weapon = new Weapon() {{
                length = 1.5f;
                reload = 40f;
                width = 0.5f;
                alternate = true;
                ejectEffect = Fx.none;
                recoil = 2f;
                bullet = Bullets.healBulletBig;
                shootSound = Sounds.pew;
            }};
        }};

        //建造无人机（原版 BuilderDrone）：给玩家/单位当"建筑工"，飞过去建/拆方块。
        phantom = new UnitType("phantom", BuilderDrone::new) {{
            flying = true;
            drag = 0.01f;
            mass = 2f;
            speed = 0.45f;
            maxVelocity = 1.9f;
            range = 70f;
            itemCapacity = 70;
            health = 400;
            buildPower = 0.4f;
            engineOffset = 6.5f;
            //F12：UnitType 已补 toMine 字段（对应原版 UnitType.java:38）→ 照原版赋值
            toMine = com.badlogic.gdx.utils.ObjectSet.with(Items.lead, Items.copper, Items.titanium);
            weapon = new Weapon() {{
                length = 1.5f;
                reload = 20f;
                width = 0.5f;
                alternate = true;
                ejectEffect = Fx.none;
                recoil = 2f;
                bullet = Bullets.healBullet;
            }};
        }};

        //重型悬浮单位 revenant（原版 HoverUnit）：悬停绕圈打导弹。
        revenant = new UnitType("revenant", HoverUnit::new) {{
            health = 1000;
            mass = 5f;
            hitsize = 20f;
            speed = 0.1f;
            maxVelocity = 1f;
            drag = 0.01f;
            range = 80f;
            shootCone = 40f;
            flying = true;
            rotateWeapon = true;
            engineOffset = 12f;
            engineSize = 3f;
            rotatespeed = 0.01f;
            attackLength = 90f;
            baseRotateSpeed = 0.06f;
            weapon = new Weapon("revenant-missiles") {{
                length = 3f;
                reload = 70f;
                width = 10f;
                shots = 2;
                inaccuracy = 2f;
                alternate = true;
                ejectEffect = Fx.none;
                velocityRnd = 0.2f;
                spacing = 1f;
                shootSound = Sounds.missile;
                bullet = Bullets.missileRevenant;
            }};
        }};

        //重型悬浮单位 lich（原版 HoverUnit）：16 发导弹齐射。
        lich = new UnitType("lich", HoverUnit::new) {{
            health = 6000;
            mass = 20f;
            hitsize = 40f;
            speed = 0.01f;
            maxVelocity = 0.6f;
            drag = 0.02f;
            range = 80f;
            shootCone = 20f;
            flying = true;
            rotateWeapon = true;
            engineOffset = 21f; //原版写 21（int 字面量），本项目 engineOffset 是 float
            engineSize = 5.3f;
            rotatespeed = 0.01f;
            attackLength = 90f;
            baseRotateSpeed = 0.04f;
            weapon = new Weapon("lich-missiles") {{
                length = 4f;
                reload = 160f;
                width = 22f;
                shots = 16;
                //⚠ 原版这里写的 shootCone 其实是**外层 UnitType.shootCone**（Weapon 没有该字段），
                //   会把上面的 20f 覆盖成 100f；本项目 Weapon 同样没有 shootCone，行为与原版一致，故照抄
                shootCone = 100f;
                shotDelay = 2;
                inaccuracy = 10f;
                alternate = true;
                ejectEffect = Fx.none;
                velocityRnd = 0.2f;
                spacing = 1f;
                bullet = Bullets.missileRevenant;
                shootSound = Sounds.artillery;
            }};
        }};

        //最重型悬浮单位 reaper（原版 HoverUnit）：单发大口径炮。
        reaper = new UnitType("reaper", HoverUnit::new) {{
            health = 11000;
            mass = 30f;
            hitsize = 56f;
            speed = 0.01f;
            maxVelocity = 0.6f;
            drag = 0.02f;
            range = 80f;
            shootCone = 30f;
            flying = true;
            rotateWeapon = true;
            engineOffset = 40f;
            engineSize = 7.3f;
            rotatespeed = 0.01f;
            baseRotateSpeed = 0.04f;
            weapon = new Weapon("reaper-gun") {{
                length = 3f;
                reload = 10f;
                width = 32f;
                shots = 1;
                //同 lich：这行改的是外层 UnitType.shootCone（原版如此），会把上面的 30f 覆盖成 100f
                shootCone = 100f;
                shake = 1f;
                inaccuracy = 3f;
                alternate = true;
                ejectEffect = Fx.none;
                //原版是 BasicBulletType(7f, 42, "bullet") 的匿名子类：本项目尺寸字段叫 width/height（原版 bulletWidth/bulletHeight）
                bullet = new BasicBulletType(7f, 42f, "bullet") {
                    {
                        width = 15f;
                        height = 21f;
                        shootEffect = Fx.shootBig;
                    }

                    //原版覆写了 range() 返回 165f（否则按 speed*lifetime 会算成 ~274）
                    @Override
                    public float range(){
                        return 165f;
                    }
                };
                shootSound = Sounds.shootBig;
            }};
        }};

        // ================= F12：3 个飞船平台（ship-pad）换装用的同名单位 =================
        // 原版 Mechs.javelin/trident/glaive 是 **Mech**（玩家装备），本项目没有机甲系统 →
        // MechPad 降级为直接换成同名 UnitType（见 MechPad.mech / Blocks.shipPad 的 unitTypeName）。
        // 贴图沿用 Mech 的那份（atlas 里有 javelin-ship / trident-ship / glaive-ship 与
        // missiles-equip / bomber-equip），数值照 Mechs.java 逐字段搬运。
        // ⚠ 同 F1e2：必须追加在 load() **末尾**（单位 ID = 下标），否则后续所有单位 ID 错位。
        // ⚠ engineOffset/engineSize/shootCone/mass 在本项目无消费者（没有引擎绘制/单位背包），
        //    设了只是留档，不产生效果。

        //轻型截击机（原版 Mechs.javelin）：快、脆、4 发标枪导弹齐射。
        javelinShip = new UnitType("javelin-ship", FlyingUnit::new) {{
            flying = true;
            health = 170;
            hitsize = 6f;
            buildPower = 1f;
            itemCapacity = 30;
            //Mech 的 speed/drag 稳态速度模型 → 换算成 UnitType 的 maxVelocity（玩家移动只认这个字段）
            maxVelocity = 10f;
            drag = 0.01f;
            mass = 2f;
            weapon = new Weapon("missiles") {{
                length = 1.5f;
                reload = 70f;
                shots = 4;
                inaccuracy = 2f;
                alternate = true;
                ejectEffect = Fx.none;
                velocityRnd = 0.2f;
                spacing = 1f;
                //F12b：与 Mechs.javelin 同步升级为原版弹药
                bullet = Bullets.missileJavelin;
                shootSound = Sounds.missile;
            }};
        }

            //F12c：护盾绘制 + 随机雷击（原版 Mechs.javelin 的 draw/updateAlt：
            //  原版挂在 Player.mech 上，本项目 Player 不是 Mech → 改为 UnitType 的 drawOverlay/updateAlt 钩子）
            com.badlogic.gdx.graphics.g2d.TextureRegion shieldRegion;

            /** 速度比（原版 Mech.scld：minV=3.6 / maxV=6，速度越快护盾越亮）。 */
            float scld(com.phoenix.game.entities.type.BaseUnit unit){
                return com.phoenix.game.math.Mathf.clamp((unit.velocity().len() - 3.6f) / (6f - 3.6f));
            }

            @Override
            public void drawOverlay(com.phoenix.game.entities.type.BaseUnit unit){
                if(shieldRegion == null && com.phoenix.game.core.Core.atlas != null){
                    shieldRegion = com.phoenix.game.core.Core.atlas.findRegion("javelin-ship-shield");
                }
                if(shieldRegion == null) return;
                float scl = scld(unit);
                if(scl < 0.01f) return;
                com.phoenix.game.core.Draw.color(com.phoenix.game.graphics.Pal.lancerLaser);
                com.phoenix.game.core.Draw.alpha(scl / 2f);
                //原版 Draw.blend(Blending.additive)；本项目无 arc 的 Blending，用批次加色混合等价实现
                //（setBlendFunction 内部会先 flush，画完恢复默认混合，见 Drill/LightBlock）
                com.phoenix.game.core.Core.batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE);
                com.phoenix.game.core.Draw.rect(shieldRegion,
                    unit.x + com.phoenix.game.math.Mathf.range(scl / 2f),
                    unit.y + com.phoenix.game.math.Mathf.range(scl / 2f), unit.rotation - 90);
                com.phoenix.game.core.Core.batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
                com.phoenix.game.core.Draw.color();
            }

            @Override
            public void updateAlt(com.phoenix.game.entities.type.BaseUnit unit){
                float scl = scld(unit);
                if(scl <= 0f) return;
                if(com.phoenix.game.math.Mathf.chance(com.phoenix.game.core.Time.delta() * (0.15f * scl))){
                    com.phoenix.game.entities.Effects.effect(Fx.hitLancer,
                        com.phoenix.game.graphics.Pal.lancerLaser, unit.x, unit.y);
                    com.phoenix.game.entities.effect.Lightning.createLighting(
                        com.phoenix.game.entities.effect.Lightning.nextSeed(), unit.getTeam(),
                        com.phoenix.game.graphics.Pal.lancerLaser,
                        //照原版 Mechs.javelin：机甲闪电乘 rules.playerDamageMultiplier（不是 unitDamageMultiplier）
                        10f * com.phoenix.game.Vars.state.rules.playerDamageMultiplier,
                        unit.x + unit.velocity().x, unit.y + unit.velocity().y, unit.rotation, 14);
                }
            }
        };

        //重型支援轰炸机（原版 Mechs.trident）：8 发炸弹覆盖、血量厚。
        tridentShip = new UnitType("trident-ship", FlyingUnit::new) {{
            flying = true;
            health = 250;
            hitsize = 6f;
            buildPower = 2.5f;
            itemCapacity = 30;
            //Mech 的 speed/drag 稳态速度模型（0.15/0.034）→ maxVelocity
            maxVelocity = 4.4f;
            drag = 0.034f;
            mass = 2.5f;
            weapon = new Weapon("bomber") {{
                length = 0f;
                width = 2f;
                reload = 25f;
                //照抄 Mechs.java：先赋 2 后被 8 覆盖（原版如此，最终生效 8）
                shots = 2;
                shotDelay = 1f;
                shots = 8;
                alternate = true;
                ejectEffect = Fx.none;
                velocityRnd = 1f;
                inaccuracy = 20f;
                ignoreRotation = true;
                bullet = new BombBulletType(16f, 25f, "shell") {{
                    //原版是 bulletWidth/bulletHeight；本项目尺寸字段在 BasicBulletType 上叫 width/height
                    width = 10f;
                    height = 14f;
                    hitEffect = Fx.flakExplosion;
                    shootEffect = Fx.none;
                    smokeEffect = Fx.none;
                    //原版 bullet.shootSound = Sounds.artillery（本项目 BulletType 没有 shootSound 字段）→ 挂到 Weapon 上
                }};
                shootSound = Sounds.artillery;
            }};
        }

            //F12c：投弹速度门槛（原版 Mechs.trident.canShoot：速度 > 1.2 才投弹，炸弹需要俯冲速度）
            @Override
            public boolean canShoot(com.phoenix.game.entities.type.BaseUnit unit){
                return unit.velocity().len() > 1.2f;
            }
        };

        //大型武装炮艇（原版 Mechs.glaive）：均衡、载重大。
        glaiveShip = new UnitType("glaive-ship", FlyingUnit::new) {{
            flying = true;
            health = 240;
            hitsize = 6f;
            buildPower = 1.2f;
            itemCapacity = 60;
            //Mech 的 speed/drag 稳态速度模型（0.32/0.06）→ maxVelocity
            maxVelocity = 5.3f;
            drag = 0.06f;
            mass = 3f;
            weapon = new Weapon("bomber") {{
                length = 1.5f;
                reload = 13f;
                alternate = true;
                ejectEffect = Fx.shellEjectSmall;
                //F12b：与 Mechs.glaive 同步升级为原版弹药
                bullet = Bullets.standardGlaive;
                shootSound = Sounds.shootSnap;
            }};
        }};

        //================= 出生单位：原版 Mechs.starter = dart = dart-ship（飞行）=================
        // 原版玩家出生/重生装备的就是它：Player.java:56 `mech = Mechs.starter`（死亡重生时 Player.java:816
        // 也会重置回 starter），CoreBlock 重生则 `player.mech = mech`（核心的 mech = Mechs.starter）。
        // dart-ship 是**飞行**机甲、可自行采集铜/铅（drillPower=1, mineSpeed=3f）——「出生即飞行 + 可采矿」。
        // 本项目没有机甲系统 → 与 F12 同法降级成同名 UnitType，由 Player.selectedType 承载出生默认值。
        // ⚠ 同 F12：必须追加在 load() 末尾（单位 ID = 下标），否则后续所有单位 ID 错位。
        dartShip = new UnitType("dart-ship", FlyingUnit::new) {{
            flying = true;
            health = 200;
            hitsize = 6f;
            buildPower = 1.1f;
            itemCapacity = 30;
            //原版 Mechs.dart 三件套：speed=0.5 是**每帧加速度**（updateMovement 里 velocity.add），
            //drag=0.09 是阻尼 → 稳态速度 ≈ 0.5/0.09 ≈ 5.55；maxVelocity 对应原版 mech.maxSpeed
            //（dart 未覆写，取默认 10）——只是钳位上限，稳态速度由 speed/drag 决定
            speed = 0.5f;
            maxVelocity = 10f;
            drag = 0.09f;
            //原版 Mech.mineSpeed = 3f → 本项目 MinerTrait.getMinePower() 消费的 minePower；
            //原版 Mech.drillPower = 1（可采硬度 ≤1 的物品 = 铜/铅）→ 与 UnitType.toMine 默认值 {铜,铅} 一致，
            //无需显式赋值（见 UnitType.java 的 toMine 字段注释）
            minePower = 3f;
            weapon = new Weapon("blaster") {{
                length = 1.5f;
                reload = 15f;
                alternate = true;
                ejectEffect = Fx.shellEjectSmall;
                //F12b 口径：与 Mechs.dart 同步为原版弹药
                bullet = Bullets.standardCopper;
            }};
        }};

        //全部单位按加载顺序入索引（存档用 ID）
    }

}
