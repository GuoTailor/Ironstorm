package com.phoenix.game.content;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.entities.type.base.GroundUnit;
import com.phoenix.game.entities.type.base.FlyingUnit;
import com.phoenix.game.type.UnitType;
import com.phoenix.game.type.Weapon;

import com.phoenix.game.content.Bullets;
import io.anuke.mindustry.gen.Sounds;

/**
 * create by GYH on 2025/3/14
 * 最小实现：单位类型定义。参照 Mindustry mindustry.content.UnitTypes 移植。
 */
public class UnitTypes {
    /** 全部单位类型，按加载顺序索引（存档用）。 */
    public static final Array<UnitType> all = new Array<>();
    public static UnitType
            dagger, crawler, titan, fortress, eruptor, chaosArray, eradicator, flare, wraith, ghoul,
            grenadier, tank;

    public void load() {
        dagger = new UnitType("dagger", GroundUnit::new) {{
            maxVelocity = 1.1f;
            speed = 0.2f;
            drag = 0.4f;
            hitsize = 8f;
            mass = 1.75f;
            health = 130;
            weapon = new Weapon("chain-blaster") {{
                bullet = Bullets.basicBullet;
                length = 1.5f;
                reload = 28f;
                alternate = true;
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
                bullet = Bullets.artilleryBullet;
                reload = 12f;
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
            weapon = new Weapon("flamethrower") {{
                bullet = Bullets.flameBullet;
                shootSound = Sounds.flame;
                length = 1f;
                reload = 14f;
                alternate = true;
                recoil = 1f;
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
                bullet = Bullets.artilleryBullet;
                length = 1f;
                reload = 60f;
                width = 10f;
                alternate = true;
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
            weapon = new Weapon("eruption") {{
                bullet = Bullets.flameBullet;
                length = 3f;
                reload = 10f;
                alternate = true;
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
                bullet = Bullets.chaosBullet;
                length = 8f;
                reload = 50f;
                width = 17f;
                alternate = true;
                recoil = 3f;
                shake = 2f;
                shots = 4;
                spacing = 4f;
                shotDelay = 5;
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
                bullet = Bullets.eradicationBullet;
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
            hitsize = 10f;
            targetAir = false;
            health = 220;
            range = 140f;
            weapon = new Weapon(){{
                length = 0f;
                width = 2f;
                reload = 12f;
                alternate = true;
                inaccuracy = 40f;
                bullet = Bullets.bombExplosive;
                shootSound = Sounds.none;
            }};
        }};

        //地面单位 grenadier：榴弹，远程范围伤害
        grenadier = new UnitType("grenadier", GroundUnit::new) {{
            maxVelocity = 0.85f;
            speed = 0.2f;
            drag = 0.4f;
            mass = 2.5f;
            hitsize = 8f;
            health = 200;
            weapon = new Weapon("grenade-launcher") {{
                bullet = Bullets.artilleryBullet;
                length = 1f;
                reload = 40f;
                width = 5f;
                alternate = true;
                recoil = 3f;
                shake = 1f;
                shootSound = Sounds.artillery;
            }};
        }};

        //地面单位 tank：重型，厚血，慢速近战炮
        tank = new UnitType("tank", GroundUnit::new) {{
            maxVelocity = 0.6f;
            speed = 0.14f;
            drag = 0.4f;
            mass = 4f;
            hitsize = 11f;
            targetAir = false;
            health = 900;
            weapon = new Weapon("tank-cannon") {{
                bullet = Bullets.standardCopper;
                length = 2f;
                reload = 35f;
                width = 8f;
                alternate = true;
                recoil = 4f;
                shootSound = Sounds.shootBig;
            }};
        }};

        //全部单位按加载顺序入索引（存档用 ID）
        all.add(dagger);
        all.add(crawler);
        all.add(titan);
        all.add(fortress);
        all.add(eruptor);
        all.add(chaosArray);
        all.add(eradicator);
        all.add(flare);
        all.add(wraith);
        all.add(ghoul);
        all.add(grenadier);
        all.add(tank);
    }

}
