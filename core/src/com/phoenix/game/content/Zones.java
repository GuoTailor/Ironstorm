package com.phoenix.game.content;

import com.phoenix.game.game.Objectives.*;
import com.phoenix.game.game.SpawnGroup;
import com.phoenix.game.maps.zonegen.*;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.type.Zone;

import static com.phoenix.game.content.Items.*;
import static com.phoenix.game.struct.Array.with;

/**
 * 战役区域定义集合。参照 Mindustry mindustry.content.Zones 移植。
 * <p>每个区域的生成器在构造时传入（{@code maps/zonegen/} 下的**区域专属程序化生成器**）。
 *
 * <p><b>与本项目的差异（逐条对应原版写法）</b>：
 * <ul>
 *   <li>⚠ <b>生成器全部换成「区域专属程序化生成器」</b>：原版这些区域用的是
 *       {@code new MapGenerator("区域名")}（= 加载内置 {@code assets/maps/*.msav}），而本项目
 *       {@code SaveIO} 与原版 MSAV 刻意不字节兼容、读不了内置图（见 {@code 转换.md} 324）→
 *       沿用 {@code MapGenerator} 会退化成"所有区域共用一张通用噪声图"。
 *       故改为每区域一个 {@code BasicGenerator} 子类（主题按区域名 + 原版 {@code .decor(...)} 线索设计）。
 *       原版的 {@code .decor(new Decoration(...))} 已并入对应生成器，不再在这里调用。</li>
 *   <li>{@code ItemStack.list(item, amount, ...)}：本项目 {@code ItemStack} 没有该静态工厂
 *       → 改用 {@code Array.with(new ItemStack(...))}（语义一致，刻意降级）。</li>
 *   <li>{@code new ZoneWave(zone, wave)} / {@code new Launched(zone)} <b>已启用</b>
 *       （Objectives 里那三个类随 Zone 移植一并启用）。</li>
 *   <li>{@code rules.launchWaveMultiplier} <b>已回填</b>（Rules 已有该字段）；
 *       {@code StatusEffects.boss} <b>已回填</b>（StatusEffects.boss 已存在）。</li>
 *   <li>以下方块的字段名与原版不同（同一内部名）→ 引用处已按本项目名改写：
 *       {@code siliconSmelter}→{@code Blocks.siliconCrafter}、{@code pneumaticDrill}→{@code Blocks.leadDrill}。</li>
 * </ul>
 */
public class Zones implements com.phoenix.game.ctype.ContentList{
    public static Zone
    groundZero, desertWastes,
    craters, frozenForest, ruinousShores, stainedMountains, tarFields, fungalPass,
    saltFlats, overgrowth, impact0078, crags,
    desolateRift, nuclearComplex;

    @Override
    public void load(){

        groundZero = new Zone("groundZero", new GroundZeroGenerator(180, 180)){{
            baseLaunchCost = with(new ItemStack(copper, -60));
            startingItems = with(new ItemStack(copper, 60));
            alwaysUnlocked = true;
            conditionWave = 5;
            launchPeriod = 5;
            resources = with(copper, scrap, lead);
        }};

        desertWastes = new Zone("desertWastes", new DesertWastesGenerator(260, 260)){{
            startingItems = with(new ItemStack(copper, 120));
            conditionWave = 20;
            launchPeriod = 10;
            loadout = Loadouts.advancedShard;
            resources = with(copper, lead, coal, sand);
            rules = r -> {
                r.waves = true;
                r.waveTimer = true;
                r.launchWaveMultiplier = 3f;
                r.waveSpacing = 60 * 50f;
                r.spawns = with(
                    new SpawnGroup(UnitTypes.crawler){{
                        unitScaling = 3f;
                    }},
                    new SpawnGroup(UnitTypes.dagger){{
                        unitScaling = 4f;
                        begin = 2;
                        spacing = 2;
                    }},
                    new SpawnGroup(UnitTypes.wraith){{
                        unitScaling = 3f;
                        begin = 11;
                        spacing = 3;
                    }},
                    new SpawnGroup(UnitTypes.eruptor){{
                        unitScaling = 3f;
                        begin = 22;
                        unitAmount = 1;
                        spacing = 4;
                    }},
                    new SpawnGroup(UnitTypes.titan){{
                        unitScaling = 3f;
                        begin = 37;
                        unitAmount = 2;
                        spacing = 4;
                    }},
                    new SpawnGroup(UnitTypes.fortress){{
                        unitScaling = 2f;
                        effect = StatusEffects.boss;
                        begin = 41;
                        spacing = 20;
                    }}
                );
            };
            requirements = with(
            new ZoneWave(groundZero, 20),
            new Unlock(Blocks.combustionGenerator)
            );
        }};

        saltFlats = new Zone("saltFlats", new SaltFlatsGenerator(200, 200)){{
            startingItems = with(new ItemStack(copper, 200), new ItemStack(Items.silicon, 200), new ItemStack(lead, 200));
            loadout = Loadouts.basicFoundation;
            conditionWave = 10;
            launchPeriod = 5;
            configureObjective = new Launched(this);
            resources = with(copper, scrap, lead, coal, sand, titanium);
            requirements = with(
            new ZoneWave(desertWastes, 60),
            new Unlock(Blocks.daggerFactory),
            new Unlock(Blocks.draugFactory),
            new Unlock(Blocks.door),
            new Unlock(Blocks.waterExtractor)
            );
        }};

        frozenForest = new Zone("frozenForest", new FrozenForestGenerator(200, 200)){{
            loadout = Loadouts.basicFoundation;
            startingItems = with(new ItemStack(copper, 250));
            conditionWave = 10;
            resources = with(copper, lead, coal);
            requirements = with(
            new ZoneWave(groundZero, 10),
            new Unlock(Blocks.junction),
            new Unlock(Blocks.router)
            );
        }};

        craters = new Zone("craters", new CratersGenerator(220, 220)){{
            startingItems = with(new ItemStack(copper, 100));
            conditionWave = 10;
            resources = with(copper, lead, coal, sand, scrap);
            requirements = with(
            new ZoneWave(frozenForest, 10),
            new Unlock(Blocks.mender),
            new Unlock(Blocks.combustionGenerator)
            );
        }};

        ruinousShores = new Zone("ruinousShores", new RuinousShoresGenerator(240, 240)){{
            loadout = Loadouts.basicFoundation;
            startingItems = with(new ItemStack(copper, 140), new ItemStack(lead, 50));
            conditionWave = 20;
            launchPeriod = 20;
            resources = with(copper, scrap, lead, coal, sand);
            requirements = with(
            new ZoneWave(desertWastes, 20),
            new ZoneWave(craters, 15),
            new Unlock(Blocks.graphitePress),
            new Unlock(Blocks.combustionGenerator),
            new Unlock(Blocks.kiln),
            new Unlock(Blocks.mechanicalPump)
            );
        }};

        stainedMountains = new Zone("stainedMountains", new StainedMountainsGenerator(240, 240)){{
            loadout = Loadouts.basicFoundation;
            startingItems = with(new ItemStack(copper, 200), new ItemStack(lead, 50));
            conditionWave = 10;
            launchPeriod = 10;
            resources = with(copper, scrap, lead, coal, titanium, sand);
            requirements = with(
            new ZoneWave(frozenForest, 15),
            //原版是 Blocks.pneumaticDrill；本项目同内部名的字段叫 leadDrill
            new Unlock(Blocks.leadDrill),
            new Unlock(Blocks.powerNode),
            new Unlock(Blocks.turbineGenerator)
            );
        }};

        fungalPass = new Zone("fungalPass", new FungalPassGenerator(240, 240)){{
            startingItems = with(new ItemStack(copper, 250), new ItemStack(lead, 250), new ItemStack(Items.metaglass, 100), new ItemStack(Items.graphite, 100));
            resources = with(copper, lead, coal, titanium, sand);
            configureObjective = new Launched(this);
            requirements = with(
            new ZoneWave(stainedMountains, 15),
            new Unlock(Blocks.daggerFactory),
            new Unlock(Blocks.crawlerFactory),
            new Unlock(Blocks.door),
            //原版是 Blocks.siliconSmelter；本项目同内部名的字段叫 siliconCrafter
            new Unlock(Blocks.siliconCrafter)
            );
        }};

        overgrowth = new Zone("overgrowth", new OvergrowthGenerator(260, 260)){{
            startingItems = with(new ItemStack(copper, 1500), new ItemStack(lead, 1000), new ItemStack(Items.silicon, 500), new ItemStack(Items.metaglass, 250));
            conditionWave = 12;
            launchPeriod = 4;
            loadout = Loadouts.basicNucleus;
            configureObjective = new Launched(this);
            resources = with(copper, lead, coal, titanium, sand, thorium, scrap);
            requirements = with(
            new ZoneWave(craters, 40),
            new Launched(fungalPass),
            new Unlock(Blocks.cultivator),
            new Unlock(Blocks.sporePress),
            new Unlock(Blocks.titanFactory),
            new Unlock(Blocks.wraithFactory)
            );
        }};

        tarFields = new Zone("tarFields", new TarFieldsGenerator(260, 260)){{
            loadout = Loadouts.basicFoundation;
            startingItems = with(new ItemStack(copper, 250), new ItemStack(lead, 100));
            conditionWave = 15;
            launchPeriod = 10;
            resources = with(copper, scrap, lead, coal, titanium, thorium, sand);
            requirements = with(
            new ZoneWave(ruinousShores, 20),
            new Unlock(Blocks.coalCentrifuge),
            new Unlock(Blocks.conduit),
            new Unlock(Blocks.wave)
            );
        }};

        desolateRift = new Zone("desolateRift", new DesolateRiftGenerator(280, 280)){{
            loadout = Loadouts.basicNucleus;
            startingItems = with(new ItemStack(copper, 1000), new ItemStack(lead, 1000), new ItemStack(Items.graphite, 250), new ItemStack(titanium, 250), new ItemStack(Items.silicon, 250));
            conditionWave = 3;
            launchPeriod = 2;
            resources = with(copper, scrap, lead, coal, titanium, sand, thorium);
            requirements = with(
            new ZoneWave(tarFields, 20),
            new Unlock(Blocks.thermalGenerator),
            new Unlock(Blocks.thoriumReactor)
            );
        }};

        /*
        //未启用（依赖本项目没有的 blockRequirements）；若将来启用，需同样给它配一个专属生成器。
        crags = new Zone("crags", new MapGenerator("crags").dist(2f)){{
            loadout = Loadouts.basicFoundation;
            baseLaunchCost = ItemStack.with();
            startingItems = ItemStack.list(Items.copper, 2000, Items.lead, 2000, Items.graphite, 500, Items.titanium, 500, Items.silicon, 500);
            conditionWave = 3;
            launchPeriod = 2;
            requirements = with(stainedMountains, 40);
            blockRequirements = new Block[]{Blocks.thermalGenerator};
            resources = Array.with(Items.copper, Items.scrap, Items.lead, Items.coal, Items.sand};
        }};*/

        nuclearComplex = new Zone("nuclearComplex", new NuclearComplexGenerator(280, 280)){{
            loadout = Loadouts.basicNucleus;
            startingItems = with(new ItemStack(copper, 1250), new ItemStack(lead, 1500), new ItemStack(Items.silicon, 400), new ItemStack(Items.metaglass, 250));
            conditionWave = 30;
            launchPeriod = 15;
            resources = with(copper, scrap, lead, coal, titanium, thorium, sand);
            requirements = with(
            new Launched(fungalPass),
            new Unlock(Blocks.thermalGenerator),
            new Unlock(Blocks.laserDrill)
            );
        }};

        /*
        //未启用（依赖本项目没有的 blockRequirements）；若将来启用，需同样给它配一个专属生成器。
        impact0078 = new Zone("impact0078", new MapGenerator("impact0078").dist(2f)){{
            loadout = Loadouts.basicNucleus;
            baseLaunchCost = ItemStack.with();
            startingItems = ItemStack.list(Items.copper, 2000, Items.lead, 2000, Items.graphite, 500, Items.titanium, 500, Items.silicon, 500);
            conditionWave = 3;
            launchPeriod = 2;
            requirements = with(nuclearComplex, 40);
            blockRequirements = new Block[]{Blocks.thermalGenerator};
            resources = Array.with(Items.copper, Items.scrap, Items.lead, Items.coal, Items.titanium, Items.thorium};
        }};*/
    }
}
