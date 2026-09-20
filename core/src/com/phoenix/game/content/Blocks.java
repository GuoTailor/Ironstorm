package com.phoenix.game.content;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.blocks.BlockPart;
import com.phoenix.game.world.blocks.storage.CoreBlock;
import com.phoenix.game.world.blocks.storage.StorageBlock;
import com.phoenix.game.world.blocks.distribution.Conveyor;
import com.phoenix.game.world.blocks.distribution.TitaniumConveyor;
import com.phoenix.game.world.blocks.distribution.ArmoredConveyor;
import com.phoenix.game.world.blocks.distribution.Junction;
import com.phoenix.game.world.blocks.distribution.Router;
import com.phoenix.game.world.blocks.distribution.Sorter;
import com.phoenix.game.world.blocks.distribution.OverflowGate;
import com.phoenix.game.world.blocks.distribution.ItemBridge;
import com.phoenix.game.world.blocks.distribution.BufferedItemBridge;
import com.phoenix.game.world.blocks.distribution.MassDriver;
import com.phoenix.game.world.blocks.production.Drill;
import com.phoenix.game.world.blocks.production.GenericCrafter;
import com.phoenix.game.world.blocks.production.GenericSmelter;
import com.phoenix.game.world.blocks.power.SolarGenerator;
import com.phoenix.game.world.blocks.power.PowerNode;
import com.phoenix.game.content.Fx;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.type.Category;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FileTextureData;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.phoenix.game.core.Core;
import com.phoenix.game.ui.Cicon;

import static com.phoenix.game.Vars.tilesize;

/**
 * 最小实现：方块定义集合。参照 Mindustry mindustry.content.Blocks 移植。
 * 只保留地面 + 墙体 + 核心，贴图取自 sprites.atlas。
 */
public class Blocks {
    public static final Array<Block> all = new Array<>();

    /** 空方块（空气） */
    public static Floor air;

    //地板
    public static Floor grass, stone, sand, darksand, snow, ice, shale, moss, metalFloor, water;
    //墙体
    public static Block copperWall, copperWallLarge, titaniumWall, titaniumWallLarge,
        thoriumWall, thoriumWallLarge, plastaniumWall, plastaniumWallLarge,
        phaseWall, phaseWallLarge, surgeWall, surgeWallLarge, scrapWall, metalWall;
    //门（可通行）
    public static Block door, doorLarge;
    //自然岩壁（对应原版 StaticWall：实心、不可拆、小地图画自身颜色）
    public static Block rocks, sandRocks, duneRocks, shaleRocks, shrubs, darkMetal;
    //核心建筑
    public static Block core, coreFoundation, coreNucleus;
    //生产建筑
    public static Drill copperDrill, leadDrill, laserDrill, blastDrill;
    public static GenericCrafter siliconCrafter, graphitePress, multiPress, kiln, plastaniumCompressor,
        phaseWeaver, surgeSmelter, blastMixer, pyratiteMixer, pulverizer;
    //物流与仓储
    public static Conveyor conveyor, titaniumConveyor, armoredConveyor;
    public static Junction junction;
    public static Router router, distributor;
    public static Sorter sorter, invertedSorter;
    public static OverflowGate overflowGate, underflowGate;
    public static BufferedItemBridge itemBridge;
    public static ItemBridge phaseConveyor;
    public static MassDriver massDriver;
    public static Block storage, vault, unloader, launchPad, launchPadLarge;
    //防御建筑
    public static com.phoenix.game.world.blocks.defense.turrets.Turret duo, scatter, scorch, hail,
        lancer, arc, swarmer, salvo, fuse, ripple, cyclone, spectre, meltdown;
    //增益建筑
    public static Block mendProjector, overdriveProjector, mender, forceProjector, shockMine;
    //单位工厂 / 维修 / 指挥 / 机甲平台
    public static com.phoenix.game.world.blocks.units.UnitFactory daggerFactory, wraithFactory, crawlerFactory,
        titanFactory, ghoulFactory, fortressFactory;
    public static com.phoenix.game.world.blocks.units.RepairPoint repairPoint;
    public static com.phoenix.game.world.blocks.units.CommandCenter commandCenter;
    public static com.phoenix.game.world.blocks.units.MechPad mechPad, mechPadDelta, mechPadTau, mechPadOmega;
    //沙盒方块（无限资源）
    public static Block powerSource, powerVoid, itemSource, itemVoid;
    //发电建筑
    public static SolarGenerator solarPanel, largeSolarPanel;
    public static PowerNode powerNode, surgeTower;
    public static com.phoenix.game.world.blocks.power.PowerDiode diode;
    //电池与发电机
    public static Block battery, batteryLarge;
    public static com.phoenix.game.world.blocks.power.BurnerGenerator combustionGenerator;
    public static com.phoenix.game.world.blocks.power.ThermalGenerator thermalGenerator;
    public static com.phoenix.game.world.blocks.power.DecayGenerator rtgGenerator;
    public static com.phoenix.game.world.blocks.power.ThoriumReactor thoriumReactor;
    public static com.phoenix.game.world.blocks.power.ImpactReactor impactReactor;
    //波次出生点标记（overlay，不绘制）
    public static Floor spawn;
    //矿脉覆盖层（对应原版 oreCopper 系列）
    public static Floor oreCopper, oreLead, oreScrap, oreCoal, oreTitanium, oreThorium;

    public static void load(){
        if(air != null) return;

        //物品定义需在方块定义前加载
        Items.load();

        air = floor("air", 0);

        grass = floor("grass", 3, "6f7a4c");
        stone = floor("stone", 3, "6e6e6e");
        sand = floor("sand", 3, "c7b07e");
        darksand = floor("darksand", 3, "8a6f43");
        snow = floor("snow", 3, "dcdcdc");
        ice = floor("ice", 3, "9fd3e8");
        shale = floor("shale", 3, "5c5c66");
        //地热发电机要的热源地板：原版是 basalt/hotrock，本工程还没这些贴图，暂用 shale 顶替（TODO）
        shale.heat = 0.5f;
        moss = floor("moss", 3, "54634a");
        metalFloor = floor("metal-floor", 0, "808594");
        //沙地直接采出沙（对应原版 Floor.itemDrop；其余资源全部来自矿脉 overlay）
        sand.itemDrop = Items.sand;
        darksand.itemDrop = Items.sand;

        water = floor("water", 0, "4a6fa5");
        water.isLiquid = true;
        water.speedMultiplier = 0.7f;
        water.dragMultiplier = 0.7f;
        water.drownTime = 60f * 10f;

        //墙体（血量/材料按原版；large 变体是 2x2、血量与材料 ×4）
        copperWall = wall("copper-wall", 60, new ItemStack(Items.copper, 6));
        copperWallLarge = wall("copper-wall-large", 240, new ItemStack(Items.copper, 24));
        copperWallLarge.size = 2;

        titaniumWall = wall("titanium-wall", 110, new ItemStack(Items.titanium, 6));
        titaniumWallLarge = wall("titanium-wall-large", 440, new ItemStack(Items.titanium, 24));
        titaniumWallLarge.size = 2;

        thoriumWall = wall("thorium-wall", 200, new ItemStack(Items.thorium, 6));
        thoriumWallLarge = wall("thorium-wall-large", 800, new ItemStack(Items.thorium, 24));
        thoriumWallLarge.size = 2;

        plastaniumWall = wall("plastanium-wall", 190,
            new ItemStack(Items.plastanium, 5), new ItemStack(Items.metaglass, 2));
        plastaniumWallLarge = wall("plastanium-wall-large", 760,
            new ItemStack(Items.plastanium, 20), new ItemStack(Items.metaglass, 8));
        plastaniumWallLarge.size = 2;

        //相织物墙（反射弱子弹：伤害 ≤ 10 的子弹会被弹回）
        phaseWall = new com.phoenix.game.world.blocks.defense.DeflectorWall("phase-wall");
        phaseWall.health = 150;
        phaseWall.category = Category.defense;
        phaseWall.requirements = new ItemStack[]{ new ItemStack(Items.phasefabric, 6) };
        all.add(phaseWall);

        phaseWallLarge = new com.phoenix.game.world.blocks.defense.DeflectorWall("phase-wall-large");
        phaseWallLarge.health = 600;
        phaseWallLarge.size = 2;
        phaseWallLarge.category = Category.defense;
        phaseWallLarge.requirements = new ItemStack[]{ new ItemStack(Items.phasefabric, 24) };
        all.add(phaseWallLarge);

        //合金墙（受击时按概率闪电反击）
        surgeWall = new com.phoenix.game.world.blocks.defense.SurgeWall("surge-wall");
        surgeWall.health = 230;
        surgeWall.category = Category.defense;
        surgeWall.requirements = new ItemStack[]{ new ItemStack(Items.surgealloy, 6) };
        all.add(surgeWall);

        surgeWallLarge = new com.phoenix.game.world.blocks.defense.SurgeWall("surge-wall-large");
        surgeWallLarge.health = 920;
        surgeWallLarge.size = 2;
        surgeWallLarge.category = Category.defense;
        surgeWallLarge.requirements = new ItemStack[]{ new ItemStack(Items.surgealloy, 24) };
        all.add(surgeWallLarge);

        //废料墙（图集里只有带编号的变体贴图，用 textureName 指向第一张）
        scrapWall = wall("scrap-wall", 40, new ItemStack(Items.scrap, 6));
        scrapWall.textureName = "scrap-wall1";

        //自造金属墙（原版没有，保留以兼容既有存档/布局）
        metalWall = wall("metalWall", 120, new ItemStack(Items.silicon, 8), new ItemStack(Items.lead, 6));

        //门（友方单位靠近自动开；原版是玩家点击开关）
        door = new com.phoenix.game.world.blocks.defense.Door("door");
        door.health = 100;
        door.category = Category.defense;
        door.requirements = new ItemStack[]{ new ItemStack(Items.titanium, 6), new ItemStack(Items.silicon, 4) };
        all.add(door);

        doorLarge = new com.phoenix.game.world.blocks.defense.Door("door-large");
        doorLarge.health = 400;
        doorLarge.size = 2;
        doorLarge.category = Category.defense;
        doorLarge.requirements = new ItemStack[]{ new ItemStack(Items.titanium, 24), new ItemStack(Items.silicon, 16) };
        all.add(doorLarge);

        //贴图名需与图集一致：图集里是 core-shard / core-foundation / core-nucleus
        core = new CoreBlock("core-shard");
        core.category = Category.effect;
        core.requirements = new ItemStack[]{ new ItemStack(Items.copper, 120), new ItemStack(Items.lead, 80) };
        all.add(core);

        //大型核心（4x4）
        coreFoundation = new CoreBlock("core-foundation");
        coreFoundation.category = Category.effect;
        coreFoundation.size = 4;
        coreFoundation.health = 3500;
        coreFoundation.itemCapacity = 6000;
        coreFoundation.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 300), new ItemStack(Items.lead, 300), new ItemStack(Items.silicon, 200)
        };
        all.add(coreFoundation);

        //巨型核心（5x5）
        coreNucleus = new CoreBlock("core-nucleus");
        coreNucleus.category = Category.effect;
        coreNucleus.size = 5;
        coreNucleus.health = 6000;
        coreNucleus.itemCapacity = 10000;
        coreNucleus.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 800), new ItemStack(Items.lead, 800),
            new ItemStack(Items.silicon, 500), new ItemStack(Items.thorium, 300)
        };
        all.add(coreNucleus);

        //机械钻头（基础，无电）：产出由脚下矿脉决定（tier 决定能采的矿物硬度，见 Drill）
        copperDrill = new Drill("mechanical-drill");
        copperDrill.category = Category.production;
        copperDrill.tier = 2;
        copperDrill.drillTime = 45f;
        copperDrill.requirements = new ItemStack[]{ new ItemStack(Items.copper, 12) };
        all.add(copperDrill);

        //气动钻头（更快、耗电）
        leadDrill = new Drill("pneumatic-drill");
        leadDrill.category = Category.production;
        leadDrill.tier = 3;
        leadDrill.drillTime = 60f;
        leadDrill.consumes.power(0.08f);
        leadDrill.requirements = new ItemStack[]{ new ItemStack(Items.copper, 20), new ItemStack(Items.lead, 10) };
        all.add(leadDrill);

        //激光钻头（更快，2x2）
        laserDrill = new Drill("laser-drill");
        laserDrill.category = Category.production;
        laserDrill.tier = 4;
        laserDrill.drillTime = 30f;
        laserDrill.size = 2;
        laserDrill.health = 320;
        laserDrill.consumes.power(0.6f);
        laserDrill.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 35), new ItemStack(Items.lead, 30), new ItemStack(Items.silicon, 20)
        };
        all.add(laserDrill);

        //爆裂钻头（最快，3x3）
        blastDrill = new Drill("blast-drill");
        blastDrill.category = Category.production;
        blastDrill.tier = 5;
        blastDrill.drillTime = 20f;
        blastDrill.size = 3;
        blastDrill.health = 480;
        blastDrill.consumes.power(1.2f);
        blastDrill.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 65), new ItemStack(Items.lead, 50),
            new ItemStack(Items.silicon, 75), new ItemStack(Items.titanium, 80)
        };
        all.add(blastDrill);

        //硅合成器：煤 + 沙 → 硅（原版配方）
        siliconCrafter = new GenericCrafter("silicon-smelter");
        siliconCrafter.category = Category.crafting;
        siliconCrafter.consumes.items(new ItemStack(Items.coal, 1), new ItemStack(Items.sand, 2));
        siliconCrafter.consumes.power(0.50f);
        siliconCrafter.outputItem = new ItemStack(Items.silicon, 1);
        siliconCrafter.craftTime = 40f;
        siliconCrafter.size = 2;
        siliconCrafter.health = 180;
        siliconCrafter.requirements = new ItemStack[]{ new ItemStack(Items.copper, 30), new ItemStack(Items.lead, 25) };
        all.add(siliconCrafter);

        //石墨压机：煤 x2 → 石墨（无电）
        graphitePress = new GenericCrafter("graphite-press");
        graphitePress.category = Category.crafting;
        graphitePress.consumes.item(Items.coal, 2);
        graphitePress.outputItem = new ItemStack(Items.graphite, 1);
        graphitePress.craftTime = 90f;
        graphitePress.size = 2;
        graphitePress.health = 180;
        graphitePress.requirements = new ItemStack[]{ new ItemStack(Items.copper, 75), new ItemStack(Items.lead, 30) };
        all.add(graphitePress);

        //多向压机：煤 x3 → 石墨 x2（3x3，耗电；原版还耗水，本项目跳过液体）
        multiPress = new GenericCrafter("multi-press");
        multiPress.category = Category.crafting;
        multiPress.consumes.item(Items.coal, 3);
        multiPress.consumes.power(1.8f);
        multiPress.outputItem = new ItemStack(Items.graphite, 2);
        multiPress.craftTime = 30f;
        multiPress.size = 3;
        multiPress.health = 360;
        multiPress.requirements = new ItemStack[]{
            new ItemStack(Items.titanium, 100), new ItemStack(Items.silicon, 25),
            new ItemStack(Items.lead, 100), new ItemStack(Items.graphite, 50)
        };
        all.add(multiPress);

        //窑：铅 + 沙 → 玻璃
        kiln = new GenericSmelter("kiln");
        kiln.category = Category.crafting;
        kiln.consumes.items(new ItemStack(Items.lead, 1), new ItemStack(Items.sand, 1));
        kiln.consumes.power(0.60f);
        kiln.outputItem = new ItemStack(Items.metaglass, 1);
        kiln.craftTime = 30f;
        kiln.size = 2;
        kiln.health = 180;
        kiln.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 60), new ItemStack(Items.graphite, 30), new ItemStack(Items.lead, 30)
        };
        all.add(kiln);

        //塑钢压缩机：钛 x2 → 塑钢（原版还耗油，本项目跳过液体）
        plastaniumCompressor = new GenericCrafter("plastanium-compressor");
        plastaniumCompressor.category = Category.crafting;
        plastaniumCompressor.consumes.item(Items.titanium, 2);
        plastaniumCompressor.consumes.power(3f);
        plastaniumCompressor.outputItem = new ItemStack(Items.plastanium, 1);
        plastaniumCompressor.craftTime = 60f;
        plastaniumCompressor.size = 2;
        plastaniumCompressor.health = 320;
        plastaniumCompressor.requirements = new ItemStack[]{
            new ItemStack(Items.silicon, 80), new ItemStack(Items.lead, 115),
            new ItemStack(Items.graphite, 60), new ItemStack(Items.titanium, 80)
        };
        all.add(plastaniumCompressor);

        //相织机：钍 x4 + 沙 x10 → 相织物
        phaseWeaver = new GenericCrafter("phase-weaver");
        phaseWeaver.category = Category.crafting;
        phaseWeaver.consumes.items(new ItemStack(Items.thorium, 4), new ItemStack(Items.sand, 10));
        phaseWeaver.consumes.power(5f);
        phaseWeaver.outputItem = new ItemStack(Items.phasefabric, 1);
        phaseWeaver.craftTime = 120f;
        phaseWeaver.size = 2;
        phaseWeaver.itemCapacity = 20;
        phaseWeaver.health = 180;
        phaseWeaver.requirements = new ItemStack[]{
            new ItemStack(Items.silicon, 130), new ItemStack(Items.lead, 120), new ItemStack(Items.thorium, 75)
        };
        all.add(phaseWeaver);

        //合金炉：铜+铅+钛+硅 → 合金（3x3）
        surgeSmelter = new GenericSmelter("alloy-smelter");
        surgeSmelter.category = Category.crafting;
        surgeSmelter.consumes.items(
            new ItemStack(Items.copper, 3), new ItemStack(Items.lead, 4),
            new ItemStack(Items.titanium, 2), new ItemStack(Items.silicon, 3));
        surgeSmelter.consumes.power(4f);
        surgeSmelter.outputItem = new ItemStack(Items.surgealloy, 1);
        surgeSmelter.craftTime = 75f;
        surgeSmelter.size = 3;
        surgeSmelter.health = 360;
        surgeSmelter.requirements = new ItemStack[]{
            new ItemStack(Items.silicon, 80), new ItemStack(Items.lead, 80), new ItemStack(Items.thorium, 70)
        };
        all.add(surgeSmelter);

        //爆混机：硫磺 + 孢子荚 → 爆混物
        blastMixer = new GenericCrafter("blast-mixer");
        blastMixer.category = Category.crafting;
        blastMixer.consumes.items(new ItemStack(Items.pyratite, 1), new ItemStack(Items.sporePod, 1));
        blastMixer.consumes.power(0.40f);
        blastMixer.outputItem = new ItemStack(Items.blastCompound, 1);
        blastMixer.craftTime = 60f;
        blastMixer.size = 2;
        blastMixer.health = 180;
        blastMixer.requirements = new ItemStack[]{ new ItemStack(Items.lead, 30), new ItemStack(Items.titanium, 20) };
        all.add(blastMixer);

        //硫磺混合机：煤 + 铅 + 沙 → 硫磺
        pyratiteMixer = new GenericSmelter("pyratite-mixer");
        pyratiteMixer.category = Category.crafting;
        pyratiteMixer.consumes.items(
            new ItemStack(Items.coal, 1), new ItemStack(Items.lead, 2), new ItemStack(Items.sand, 2));
        pyratiteMixer.consumes.power(0.20f);
        pyratiteMixer.outputItem = new ItemStack(Items.pyratite, 1);
        pyratiteMixer.craftTime = 60f;
        pyratiteMixer.size = 2;
        pyratiteMixer.health = 180;
        pyratiteMixer.requirements = new ItemStack[]{ new ItemStack(Items.copper, 50), new ItemStack(Items.lead, 25) };
        all.add(pyratiteMixer);

        //粉碎机：废料 → 沙
        pulverizer = new GenericCrafter("pulverizer");
        pulverizer.category = Category.crafting;
        pulverizer.consumes.item(Items.scrap, 1);
        pulverizer.consumes.power(0.50f);
        pulverizer.outputItem = new ItemStack(Items.sand, 1);
        pulverizer.craftTime = 40f;
        pulverizer.health = 120;
        pulverizer.requirements = new ItemStack[]{ new ItemStack(Items.copper, 30), new ItemStack(Items.lead, 25) };
        all.add(pulverizer);

        //传送带
        conveyor = new Conveyor("conveyor");
        conveyor.category = Category.distribution;
        conveyor.speed = 0.03f;
        conveyor.displayedSpeed = 4.5f;
        conveyor.requirements = new ItemStack[]{ new ItemStack(Items.copper, 1) };
        all.add(conveyor);

        //钛传送带（更快）
        titaniumConveyor = new TitaniumConveyor("titanium-conveyor");
        titaniumConveyor.category = Category.distribution;
        titaniumConveyor.requirements = new ItemStack[]{ new ItemStack(Items.copper, 1), new ItemStack(Items.titanium, 1) };
        all.add(titaniumConveyor);

        //装甲传送带（只收正后方输入；图集缺 armored-conveyor 贴图，暂用 conveyor 占位）
        armoredConveyor = new ArmoredConveyor("armored-conveyor");
        armoredConveyor.category = Category.distribution;
        armoredConveyor.requirements = new ItemStack[]{ new ItemStack(Items.titanium, 1), new ItemStack(Items.metaglass, 1) };
        all.add(armoredConveyor);

        //交叉器（两条带在此直行穿越）
        junction = new Junction("junction");
        junction.category = Category.distribution;
        junction.requirements = new ItemStack[]{ new ItemStack(Items.copper, 2) };
        all.add(junction);

        //路由器 / 分配器（轮询输出）
        router = new Router("router");
        router.category = Category.distribution;
        router.requirements = new ItemStack[]{ new ItemStack(Items.copper, 3) };
        all.add(router);

        distributor = new Router("distributor");
        distributor.category = Category.distribution;
        distributor.size = 2;
        distributor.requirements = new ItemStack[]{ new ItemStack(Items.copper, 10), new ItemStack(Items.lead, 10) };
        all.add(distributor);

        //分类器 / 反选分类器（图集缺 inverted-sorter，复用 sorter 贴图）
        sorter = new Sorter("sorter");
        sorter.category = Category.distribution;
        sorter.requirements = new ItemStack[]{ new ItemStack(Items.copper, 2), new ItemStack(Items.lead, 2) };
        all.add(sorter);

        invertedSorter = new Sorter("inverted-sorter");
        invertedSorter.category = Category.distribution;
        invertedSorter.invert = true;
        invertedSorter.textureName = "sorter";
        invertedSorter.requirements = new ItemStack[]{ new ItemStack(Items.copper, 2), new ItemStack(Items.lead, 2) };
        all.add(invertedSorter);

        //溢流闸门 / 逆流闸门（图集缺 underflow-gate，复用 overflow-gate 贴图）
        overflowGate = new OverflowGate("overflow-gate");
        overflowGate.category = Category.distribution;
        overflowGate.requirements = new ItemStack[]{ new ItemStack(Items.copper, 4), new ItemStack(Items.lead, 4) };
        all.add(overflowGate);

        underflowGate = new OverflowGate("underflow-gate");
        underflowGate.category = Category.distribution;
        underflowGate.invert = true;
        underflowGate.textureName = "overflow-gate";
        underflowGate.requirements = new ItemStack[]{ new ItemStack(Items.copper, 4), new ItemStack(Items.lead, 4) };
        all.add(underflowGate);

        //物品桥（跨格运输；放置时自动连上"上一次放置的同类桥"）
        itemBridge = new BufferedItemBridge("bridge-conveyor");
        itemBridge.category = Category.distribution;
        itemBridge.range = 4;
        itemBridge.speed = 70f;
        itemBridge.bufferCapacity = 14;
        itemBridge.requirements = new ItemStack[]{ new ItemStack(Items.lead, 4), new ItemStack(Items.copper, 4) };
        all.add(itemBridge);

        //相位桥（远距离、耗电、不可超频）
        phaseConveyor = new ItemBridge("phase-conveyor");
        phaseConveyor.category = Category.distribution;
        phaseConveyor.range = 12;
        phaseConveyor.canOverdrive = false;
        phaseConveyor.hasPower = true;
        phaseConveyor.powerConsumption = 0.30f;
        phaseConveyor.requirements = new ItemStack[]{
            new ItemStack(Items.phasefabric, 5), new ItemStack(Items.silicon, 7),
            new ItemStack(Items.lead, 10), new ItemStack(Items.graphite, 10)
        };
        all.add(phaseConveyor);

        //质量驱动器（远距离批量投送；两台互相对准后排队发射）
        massDriver = new MassDriver("mass-driver");
        massDriver.category = Category.distribution;
        massDriver.range = 440f;
        massDriver.reloadTime = 200f;
        massDriver.minDistribute = 10;
        massDriver.itemCapacity = 120;
        massDriver.powerConsumption = 0.1f;
        massDriver.requirements = new ItemStack[]{
            new ItemStack(Items.lead, 120), new ItemStack(Items.silicon, 75), new ItemStack(Items.thorium, 50)
        };
        all.add(massDriver);

        //仓库（container 是 2x2）
        //贴图名用图集里的 container（图集没有 storage 区域，之前会渲染成白块）
        storage = new StorageBlock("container");
        storage.category = Category.effect;
        storage.size = 2;
        storage.itemCapacity = 300;
        storage.health = 250;
        storage.requirements = new ItemStack[]{ new ItemStack(Items.copper, 30), new ItemStack(Items.lead, 20) };
        all.add(storage);

        //大型仓库（3x3，容量 1000）
        vault = new com.phoenix.game.world.blocks.storage.Vault("vault");
        vault.category = Category.effect;
        vault.requirements = new ItemStack[]{ new ItemStack(Items.titanium, 250), new ItemStack(Items.thorium, 125) };
        all.add(vault);

        //装卸器（从相邻存储取物再丢给别的建筑）
        unloader = new com.phoenix.game.world.blocks.storage.Unloader("unloader");
        unloader.category = Category.effect;
        unloader.requirements = new ItemStack[]{ new ItemStack(Items.silicon, 30), new ItemStack(Items.titanium, 25) };
        all.add(unloader);

        //发射台（装满后把整仓物品送进全局库存，供后续对局取用）
        launchPad = new com.phoenix.game.world.blocks.storage.LaunchPad("launch-pad");
        launchPad.category = Category.effect;
        launchPad.size = 3;
        launchPad.requirements = new ItemStack[]{
            new ItemStack(Items.titanium, 200), new ItemStack(Items.silicon, 150)
        };
        all.add(launchPad);

        launchPadLarge = new com.phoenix.game.world.blocks.storage.LaunchPad("launch-pad-large");
        launchPadLarge.category = Category.effect;
        launchPadLarge.size = 4;
        launchPadLarge.itemCapacity = 300;
        launchPadLarge.health = 600;
        launchPadLarge.requirements = new ItemStack[]{
            new ItemStack(Items.titanium, 500), new ItemStack(Items.silicon, 400), new ItemStack(Items.thorium, 200)
        };
        all.add(launchPadLarge);

        //炮塔 duo（双管，消耗铜弹药）
        duo = new com.phoenix.game.world.blocks.defense.turrets.Turret("duo");
        duo.category = Category.turret;
        duo.range = 100f;
        duo.reload = 20f;
        duo.rotatespeed = 10f;
        duo.shootCone = 15f;
        duo.inaccuracy = 2f;
        duo.health = 250;
        duo.shots = 2;
        duo.spread = 4f;
        duo.bullet = Bullets.standardCopper;
        duo.consumes.item(Items.copper);
        duo.requirements = new ItemStack[]{ new ItemStack(Items.copper, 35) };
        all.add(duo);

        //散弹炮（2x2，对地，双发）
        scatter = turret("scatter", Bullets.flakBullet, 800, 170f, 18f);
        scatter.size = 2;
        scatter.targetAir = false;
        scatter.shots = 2;
        scatter.spread = 8f;
        scatter.inaccuracy = 17f;
        scatter.shootCone = 35f;
        scatter.recoil = 2f;
        scatter.consumes.item(Items.lead);
        scatter.requirements = new ItemStack[]{ new ItemStack(Items.copper, 85), new ItemStack(Items.lead, 45) };

        //喷火器（对地）
        scorch = turret("scorch", Bullets.flameBullet, 400, 60f, 5f);
        scorch.targetAir = false;
        scorch.shootCone = 50f;
        scorch.recoil = 0f;
        scorch.consumes.item(Items.copper);
        scorch.requirements = new ItemStack[]{ new ItemStack(Items.copper, 25), new ItemStack(Items.graphite, 22) };

        //迫击炮（远程）
        hail = turret("hail", Bullets.artilleryBullet, 260, 230f, 60f);
        hail.recoil = 2f;
        hail.inaccuracy = 1f;
        hail.shootCone = 10f;
        hail.consumes.item(Items.graphite);
        hail.requirements = new ItemStack[]{ new ItemStack(Items.copper, 40), new ItemStack(Items.graphite, 17) };

        //长矛（2x2，对地，激光束）
        lancer = turret("lancer", Bullets.lancerLaser, 1120, 155f, 90f);
        lancer.size = 2;
        lancer.targetAir = false;
        lancer.recoil = 2f;
        //炮口特效（对应原版 lancer 的 shootEffect/smokeEffect）
        lancer.shootEffect = Fx.lancerLaserShoot;
        lancer.smokeEffect = Fx.lancerLaserShootSmoke;
        lancer.consumes.item(Items.copper);
        lancer.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 25), new ItemStack(Items.lead, 50), new ItemStack(Items.silicon, 45)
        };

        //电弧（耗电，对地）
        arc = turret("arc", Bullets.chaosBullet, 300, 90f, 35f);
        arc.targetAir = false;
        arc.shootCone = 40f;
        arc.recoil = 1f;
        arc.shootEffect = Fx.lightningShoot;
        arc.consumes.power(0.3f);
        arc.requirements = new ItemStack[]{ new ItemStack(Items.copper, 35), new ItemStack(Items.lead, 50) };

        //蜂群（2x2，三连发，追踪导弹）
        swarmer = turret("swarmer", Bullets.missileBullet, 800, 220f, 30f);
        swarmer.size = 2;
        swarmer.shots = 3;
        swarmer.spread = 6f;
        swarmer.inaccuracy = 6f;
        swarmer.consumes.item(Items.silicon);
        swarmer.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 120), new ItemStack(Items.graphite, 60), new ItemStack(Items.silicon, 45)
        };

        //齐射炮（2x2，四连发）
        salvo = turret("salvo", Bullets.basicBullet, 500, 190f, 25f);
        salvo.size = 2;
        salvo.shots = 4;
        salvo.spread = 5f;
        salvo.inaccuracy = 4f;
        salvo.consumes.item(Items.copper);
        salvo.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 100), new ItemStack(Items.graphite, 80), new ItemStack(Items.titanium, 40)
        };

        //引信（近距离速射）
        fuse = turret("fuse", Bullets.eradicationBullet, 200, 90f, 35f);
        fuse.consumes.item(Items.titanium);
        fuse.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 60), new ItemStack(Items.lead, 45), new ItemStack(Items.titanium, 20)
        };

        //涟漪（3x3，远程四连发）
        ripple = turret("ripple", Bullets.artilleryBullet, 1170, 290f, 60f);
        ripple.size = 3;
        ripple.shots = 4;
        ripple.spread = 10f;
        ripple.inaccuracy = 12f;
        ripple.recoil = 6f;
        ripple.consumes.item(Items.graphite);
        ripple.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 150), new ItemStack(Items.graphite, 135), new ItemStack(Items.titanium, 60)
        };

        //气旋（3x3，速射）
        cyclone = turret("cyclone", Bullets.flakBullet, 1305, 200f, 6f);
        cyclone.size = 3;
        cyclone.inaccuracy = 10f;
        cyclone.shootCone = 30f;
        cyclone.recoil = 3f;
        cyclone.consumes.item(Items.metaglass);
        cyclone.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 200), new ItemStack(Items.titanium, 125), new ItemStack(Items.plastanium, 80)
        };

        //幽灵（4x4，双管）
        spectre = turret("spectre", Bullets.standardCopper, 2480, 200f, 6f);
        spectre.size = 4;
        spectre.shots = 2;
        spectre.spread = 6f;
        spectre.inaccuracy = 3f;
        spectre.shootCone = 24f;
        spectre.recoil = 3f;
        spectre.consumes.item(Items.graphite);
        spectre.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 350), new ItemStack(Items.graphite, 300),
            new ItemStack(Items.surgealloy, 250), new ItemStack(Items.plastanium, 175), new ItemStack(Items.thorium, 250)
        };

        //熔毁（4x4，耗电，激光束）
        meltdown = turret("meltdown", Bullets.meltdownLaser, 3200, 190f, 80f);
        meltdown.size = 4;
        meltdown.shootCone = 40f;
        meltdown.recoil = 4f;
        meltdown.shootEffect = Fx.shootBigSmoke2;
        meltdown.consumes.power(1.5f);
        meltdown.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 250), new ItemStack(Items.lead, 350), new ItemStack(Items.graphite, 300),
            new ItemStack(Items.surgealloy, 325), new ItemStack(Items.silicon, 325)
        };

        //修复投影器（范围内持续治疗友方建筑）
        mendProjector = new com.phoenix.game.world.blocks.defense.MendProjector("mend-projector");
        mendProjector.category = Category.effect;
        mendProjector.consumes.power(0.3f);
        mendProjector.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 50), new ItemStack(Items.lead, 80), new ItemStack(Items.silicon, 40)
        };
        all.add(mendProjector);

        //超频投影器（范围内加速友方建筑）
        overdriveProjector = new com.phoenix.game.world.blocks.defense.OverdriveProjector("overdrive-projector");
        overdriveProjector.category = Category.effect;
        overdriveProjector.consumes.power(1f);
        overdriveProjector.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 100), new ItemStack(Items.lead, 120),
            new ItemStack(Items.silicon, 60), new ItemStack(Items.titanium, 50)
        };
        all.add(overdriveProjector);

        //修复器（治疗范围内友方单位；与投影器分工：投影器治建筑，修复器治单位）
        mender = new com.phoenix.game.world.blocks.defense.Mender("mender");
        mender.category = Category.effect;
        mender.consumes.power(0.3f);
        mender.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 30), new ItemStack(Items.lead, 40), new ItemStack(Items.silicon, 25)
        };
        all.add(mender);

        //力场投影器（护盾吸收命中自身的子弹；本项目简化为只保护自身）
        forceProjector = new com.phoenix.game.world.blocks.defense.ForceProjector("force-projector");
        forceProjector.category = Category.effect;
        forceProjector.size = 3;
        forceProjector.consumes.power(3f);
        forceProjector.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 100), new ItemStack(Items.lead, 125),
            new ItemStack(Items.titanium, 75), new ItemStack(Items.silicon, 100)
        };
        all.add(forceProjector);

        //地雷（敌方单位靠近时放电 + 自伤）
        shockMine = new com.phoenix.game.world.blocks.defense.ShockMine("shock-mine");
        shockMine.category = Category.defense;
        shockMine.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 25), new ItemStack(Items.lead, 25)
        };
        all.add(shockMine);

        //太阳能发电机（0.06/帧，靠天吃饭、无消耗）
        solarPanel = new SolarGenerator("solar-panel");
        solarPanel.category = Category.power;
        solarPanel.powerProduction = 0.06f;
        solarPanel.requirements = new ItemStack[]{ new ItemStack(Items.lead, 10), new ItemStack(Items.silicon, 15) };
        all.add(solarPanel);

        largeSolarPanel = new SolarGenerator("solar-panel-large");
        largeSolarPanel.category = Category.power;
        largeSolarPanel.size = 3;
        largeSolarPanel.health = 240;
        largeSolarPanel.powerProduction = 0.9f;
        largeSolarPanel.requirements = new ItemStack[]{
            new ItemStack(Items.lead, 100), new ItemStack(Items.silicon, 145), new ItemStack(Items.phasefabric, 15)
        };
        all.add(largeSolarPanel);

        //电力节点：邻接接入 + 6 格内节点互相连线（最多 3 条）
        powerNode = new PowerNode("power-node");
        powerNode.category = Category.power;
        powerNode.requirements = new ItemStack[]{ new ItemStack(Items.copper, 2) };
        all.add(powerNode);

        surgeTower = new PowerNode("surge-tower");
        surgeTower.category = Category.power;
        surgeTower.size = 2;
        surgeTower.health = 160;
        surgeTower.maxLinks = 2;
        surgeTower.laserRange = 30f;
        surgeTower.requirements = new ItemStack[]{
            new ItemStack(Items.titanium, 7), new ItemStack(Items.lead, 10),
            new ItemStack(Items.silicon, 15), new ItemStack(Items.surgealloy, 15)
        };
        all.add(surgeTower);

        //二极管：背面 → 正面 单向输电
        diode = new com.phoenix.game.world.blocks.power.PowerDiode("diode");
        diode.category = Category.power;
        diode.requirements = new ItemStack[]{
            new ItemStack(Items.silicon, 10), new ItemStack(Items.plastanium, 5), new ItemStack(Items.metaglass, 10)
        };
        all.add(diode);

        //电池（储能：电量富余时充入、不足时取出）
        battery = new com.phoenix.game.world.blocks.power.Battery("battery", 4000f);
        battery.category = Category.power;
        battery.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 4), new ItemStack(Items.lead, 20)
        };
        all.add(battery);

        batteryLarge = new com.phoenix.game.world.blocks.power.Battery("battery-large", 50000f);
        batteryLarge.category = Category.power;
        batteryLarge.size = 3;
        batteryLarge.health = 400;
        batteryLarge.requirements = new ItemStack[]{
            new ItemStack(Items.titanium, 20), new ItemStack(Items.lead, 40), new ItemStack(Items.silicon, 20)
        };
        all.add(batteryLarge);

        //燃烧发电机（烧可燃物：煤 1.0 满功率，一份烧 120 帧，断料立刻停机）
        combustionGenerator = new com.phoenix.game.world.blocks.power.BurnerGenerator("combustion-generator");
        combustionGenerator.category = Category.power;
        combustionGenerator.powerProduction = 1f;
        combustionGenerator.itemDuration = 120f;
        combustionGenerator.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 25), new ItemStack(Items.lead, 15)
        };
        all.add(combustionGenerator);

        //地热发电机（2x2，发电量 = 覆盖范围地板热度之和 × 1.8）
        thermalGenerator = new com.phoenix.game.world.blocks.power.ThermalGenerator("thermal-generator");
        thermalGenerator.category = Category.power;
        thermalGenerator.size = 2;
        thermalGenerator.health = 160;
        thermalGenerator.powerProduction = 1.8f;
        thermalGenerator.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 40), new ItemStack(Items.graphite, 35), new ItemStack(Items.lead, 50),
            new ItemStack(Items.silicon, 35), new ItemStack(Items.metaglass, 40)
        };
        all.add(thermalGenerator);

        //衰变发电机（烧放射性物品，功率低但一份烧 440 帧、不用管）
        rtgGenerator = new com.phoenix.game.world.blocks.power.DecayGenerator("rtg-generator");
        rtgGenerator.category = Category.power;
        rtgGenerator.size = 2;
        rtgGenerator.health = 160;
        rtgGenerator.powerProduction = 3f;
        rtgGenerator.itemDuration = 440f;
        rtgGenerator.requirements = new ItemStack[]{
            new ItemStack(Items.lead, 100), new ItemStack(Items.silicon, 75), new ItemStack(Items.phasefabric, 25),
            new ItemStack(Items.plastanium, 75), new ItemStack(Items.thorium, 50)
        };
        all.add(rtgGenerator);

        //钍反应堆（3x3，发电量按库存钍的充满度缩放；被摧毁且库存 ≥5 钍时核爆）
        thoriumReactor = new com.phoenix.game.world.blocks.power.ThoriumReactor("thorium-reactor");
        thoriumReactor.category = Category.power;
        thoriumReactor.powerProduction = 14f;
        thoriumReactor.requirements = new ItemStack[]{
            new ItemStack(Items.lead, 300), new ItemStack(Items.silicon, 200), new ItemStack(Items.graphite, 150),
            new ItemStack(Items.thorium, 150), new ItemStack(Items.metaglass, 50)
        };
        all.add(thoriumReactor);

        //冲击反应堆（4x4，耗电 25 启动 + 烧脉冲燃料，发电 130；预热越满发电越多）
        impactReactor = new com.phoenix.game.world.blocks.power.ImpactReactor("impact-reactor");
        impactReactor.category = Category.power;
        impactReactor.powerProduction = 130f;
        impactReactor.consumes.power(25f);
        impactReactor.requirements = new ItemStack[]{
            new ItemStack(Items.lead, 500), new ItemStack(Items.silicon, 300), new ItemStack(Items.graphite, 400),
            new ItemStack(Items.thorium, 100), new ItemStack(Items.surgealloy, 250), new ItemStack(Items.metaglass, 250)
        };
        all.add(impactReactor);

        //单位工厂：消耗硅生产 dagger（出厂单位自动寻路前往敌方核心）
        daggerFactory = new com.phoenix.game.world.blocks.units.UnitFactory("dagger-factory");
        daggerFactory.unitTypeName = "dagger"; //UnitTypes 晚于 Blocks 加载，按名字延迟解析
        daggerFactory.produceTime = 850f;
        daggerFactory.maxSpawn = 4;
        daggerFactory.inputItem = new ItemStack[]{ new ItemStack(Items.silicon, 6) };
        daggerFactory.requirements = new ItemStack[]{ new ItemStack(Items.copper, 60), new ItemStack(Items.lead, 40) };
        all.add(daggerFactory);

        //其余单位工厂：数值全部取自原版（produceTime / maxSpawn / 耗电 / 耗料）。
        //原版的 draug / spirit / phantom / revenant 本工程还没有对应单位类型，故未注册。
        wraithFactory = unitFactory("wraith-factory", "wraith", 700f, 2, 0.5f, 1,
            new ItemStack[]{ new ItemStack(Items.silicon, 10), new ItemStack(Items.titanium, 5) },
            new ItemStack[]{ new ItemStack(Items.titanium, 30), new ItemStack(Items.lead, 40), new ItemStack(Items.silicon, 45) });

        crawlerFactory = unitFactory("crawler-factory", "crawler", 300f, 2, 0.5f, 6,
            new ItemStack[]{ new ItemStack(Items.coal, 10) },
            new ItemStack[]{ new ItemStack(Items.lead, 45), new ItemStack(Items.silicon, 30) });

        titanFactory = unitFactory("titan-factory", "titan", 1050f, 3, 0.60f, 4,
            new ItemStack[]{ new ItemStack(Items.silicon, 12) },
            new ItemStack[]{ new ItemStack(Items.graphite, 50), new ItemStack(Items.lead, 50), new ItemStack(Items.silicon, 45) });

        ghoulFactory = unitFactory("ghoul-factory", "ghoul", 1150f, 3, 1.2f, 4,
            new ItemStack[]{ new ItemStack(Items.silicon, 15), new ItemStack(Items.titanium, 10) },
            new ItemStack[]{ new ItemStack(Items.titanium, 75), new ItemStack(Items.lead, 65), new ItemStack(Items.silicon, 110) });

        fortressFactory = unitFactory("fortress-factory", "fortress", 2000f, 3, 1.4f, 3,
            new ItemStack[]{ new ItemStack(Items.silicon, 20), new ItemStack(Items.graphite, 10) },
            new ItemStack[]{ new ItemStack(Items.thorium, 40), new ItemStack(Items.lead, 110), new ItemStack(Items.silicon, 75) });

        //维修点：给半径内最近的受伤友军回血（耗电 1/帧，只在锁定目标时耗）
        repairPoint = new com.phoenix.game.world.blocks.units.RepairPoint("repair-point");
        repairPoint.category = Category.units;
        repairPoint.repairSpeed = 0.5f;
        repairPoint.repairRadius = 65f;
        repairPoint.powerConsumption = 1f;
        repairPoint.requirements = new ItemStack[]{
            new ItemStack(Items.lead, 15), new ItemStack(Items.copper, 15), new ItemStack(Items.silicon, 15)
        };
        all.add(repairPoint);

        //指挥中心（遗留空壳）：旧的全队指令机制已由 RTS 命令系统取代，
        //保留注册以兼容旧地图/存档（对应 v8 的 LegacyCommandCenter）
        commandCenter = new com.phoenix.game.world.blocks.units.CommandCenter("command-center");
        commandCenter.category = Category.units;
        commandCenter.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 200), new ItemStack(Items.lead, 250),
            new ItemStack(Items.silicon, 250), new ItemStack(Items.graphite, 100)
        };
        all.add(commandCenter);

        //机甲平台：站上去点一下，花 5 秒换成对应型号的单位。
        //原版换的是 Mechs.alpha/delta/tau/omega，本工程没有 Mech 系统，
        //改为换成现有的单位类型（按强度递增：dagger → titan → fortress → eruptor）
        mechPad = mechPad("dart-mech-pad", "dagger", 2, 0.5f, new ItemStack[]{
            new ItemStack(Items.lead, 100), new ItemStack(Items.graphite, 50), new ItemStack(Items.copper, 75)
        });
        mechPadDelta = mechPad("delta-mech-pad", "titan", 2, 0.7f, new ItemStack[]{
            new ItemStack(Items.lead, 175), new ItemStack(Items.titanium, 175), new ItemStack(Items.copper, 200),
            new ItemStack(Items.silicon, 225), new ItemStack(Items.thorium, 150)
        });
        mechPadTau = mechPad("tau-mech-pad", "fortress", 2, 1f, new ItemStack[]{
            new ItemStack(Items.lead, 125), new ItemStack(Items.titanium, 125),
            new ItemStack(Items.copper, 125), new ItemStack(Items.silicon, 125)
        });
        mechPadOmega = mechPad("omega-mech-pad", "eruptor", 3, 1.2f, new ItemStack[]{
            new ItemStack(Items.lead, 225), new ItemStack(Items.graphite, 275), new ItemStack(Items.silicon, 325),
            new ItemStack(Items.thorium, 300), new ItemStack(Items.surgealloy, 120)
        });

        //沙盒方块：无限电力/物品的源与虚空，只在 rules.sandbox 打开时出现在建造菜单里
        powerSource = new com.phoenix.game.world.blocks.sandbox.PowerSource("power-source");
        powerSource.category = Category.power;
        all.add(powerSource);

        powerVoid = new com.phoenix.game.world.blocks.sandbox.PowerVoid("power-void");
        powerVoid.category = Category.power;
        all.add(powerVoid);

        itemSource = new com.phoenix.game.world.blocks.sandbox.ItemSource("item-source");
        itemSource.category = Category.distribution;
        all.add(itemSource);

        itemVoid = new com.phoenix.game.world.blocks.sandbox.ItemVoid("item-void");
        itemVoid.category = Category.distribution;
        all.add(itemVoid);

        //波次出生点 overlay（World.createMap 在地图四角放置）
        spawn = new com.phoenix.game.world.blocks.OverlayFloor("spawn");
        all.add(spawn);

        //多格卫星瓦片（BlockPart）注册进 all，获得稳定 ID，供存档序列化反查（核心多格读档不退化）
        registerBlockParts();

        //自然岩壁（对应原版 content.Blocks 里的 StaticWall 系列）。
        //放在**最后**注册：方块 ID = 在 all 里的下标，追加在末尾才不会让已有存档的方块 ID 错位。
        rocks = new com.phoenix.game.world.blocks.StaticWall("rocks");
        all.add(rocks);
        sandRocks = new com.phoenix.game.world.blocks.StaticWall("sandrocks");
        all.add(sandRocks);
        duneRocks = new com.phoenix.game.world.blocks.StaticWall("dunerocks");
        all.add(duneRocks);
        shaleRocks = new com.phoenix.game.world.blocks.StaticWall("shalerocks");
        all.add(shaleRocks);
        shrubs = new com.phoenix.game.world.blocks.StaticWall("shrubs");
        all.add(shrubs);
        darkMetal = new com.phoenix.game.world.blocks.StaticWall("dark-metal");
        all.add(darkMetal);

        //在建占位方块 build1..build9（按尺寸分档）。同样放末尾，避免已有存档的方块 ID 错位。
        //不需要单独字段引用：Build 里用 BuildBlock.get(size) 取
        for(int i = 1; i <= com.phoenix.game.world.blocks.BuildBlock.maxSize; i++){
            all.add(new com.phoenix.game.world.blocks.BuildBlock(i));
        }

        //矿脉覆盖层（对应原版 oreCopper 系列）。必须放在**最末尾**注册：方块 ID = 在 all 里的下标，
        //追加在 BuildBlock 之后才不会让已有存档的方块 ID 错位。
        oreCopper = new com.phoenix.game.world.blocks.OreFloor(Items.copper);
        oreLead = new com.phoenix.game.world.blocks.OreFloor(Items.lead);
        oreScrap = new com.phoenix.game.world.blocks.OreFloor(Items.scrap);
        oreCoal = new com.phoenix.game.world.blocks.OreFloor(Items.coal);
        oreTitanium = new com.phoenix.game.world.blocks.OreFloor(Items.titanium);
        oreThorium = new com.phoenix.game.world.blocks.OreFloor(Items.thorium);
        //libgdx 的 Array.add 最多 4 参，6 个矿拆两次加（原行无法编译）
        all.add(oreCopper, oreLead, oreScrap, oreCoal);
        all.add(oreTitanium, oreThorium);

        for(Block block : all){
            //先拍平消耗器数组（对应原版 Block.init），再加载贴图
            block.init();
            block.load();
        }

        loadMinimapColors();
    }

    /**
     * 采样每个方块图标中心像素，写入 {@link Block#color} 作为小地图颜色。
     * <p>对应原版打包期的 {@code Block.createIcons()}（那里对每个方块做一次
     * {@code color.set(image.getPixel(image.width / 2, image.height / 2))}）；phoenix 没有打包管线，
     * 改在世界内容加载后做一次。具体的取样在 {@link Block#loadMinimapColor(Pixmap)} 里。
     * <p>分页 PNG 从**已加载的图集纹理**反查（{@code FileTextureData.getFileHandle()}），
     * 这样 headless 服务端的空图集（{@code ServerControl} 塞的是空 TextureAtlas）天然跳过，
     * 也不会去读不存在的 {@code sprites/sprites.atlas}。
     */
    private static void loadMinimapColors(){
        if(Core.atlas == null) return;

        ObjectMap<Texture, Pixmap> pages = new ObjectMap<>();
        int sampled = 0;
        try{
            for(Block block : all){
                //地板颜色是显式配的（见 floor(name, variants, color)），覆盖会毁掉地面配色；
                //BlockPart 的 region 会回退到 blank，采样到透明色
                if(block instanceof Floor || block instanceof BlockPart) continue;

                //取样对象是 icon(Cicon.full)（对应原版），不是 region —— 自然岩壁的裸名在图集里不存在
                TextureRegion icon = block.icon(Cicon.full);
                if(icon == null || icon.getTexture() == null) continue;

                //按需加载分页（只解码真正含有方块的页），同一个 Texture 只读一次
                Texture page = icon.getTexture();
                if(!pages.containsKey(page)){
                    TextureData data = page.getTextureData();
                    pages.put(page, data instanceof FileTextureData ? new Pixmap(((FileTextureData)data).getFileHandle()) : null);
                }
                Pixmap pixmap = pages.get(page);
                if(pixmap == null) continue;

                block.loadMinimapColor(pixmap);
                sampled++;
            }
        }finally{
            for(Pixmap page : pages.values()){
                if(page != null) page.dispose();
            }
        }

        System.out.println("[小地图] 已采样 " + sampled + " 个方块颜色");
    }

    /** 把全部可能的 BlockPart（maxSize x maxSize 去中心）注册进 all，保证存档 ID 稳定。 */
    private static void registerBlockParts(){
        int max = com.phoenix.game.world.blocks.BlockPart.maxSize;
        for(int dx = -max / 2; dx <= max / 2; dx++){
            for(int dy = -max / 2; dy <= max / 2; dy++){
                if(dx == 0 && dy == 0) continue;
                BlockPart part = com.phoenix.game.world.blocks.BlockPart.get(dx, dy);
                if(!all.contains(part, true)){
                    all.add(part);
                }
            }
        }
    }

    private static Floor floor(String name, int variants){
        Floor floor = new Floor(name);
        floor.variants = variants;
        all.add(floor);
        return floor;
    }

    private static Floor floor(String name, int variants, String color){
        Floor floor = floor(name, variants);
        floor.color = Color.valueOf(color);
        return floor;
    }

    /**
     * 炮塔辅助：统一设置分类、子弹与基础数值；size/shots/弹药等由调用方按原版补。
     * <p>弹药统一走 {@code consumes.item(...)}（或 {@code consumes.power(...)}），不再用 ammoItem 字段。
     * <p>这里**不设** shootEffect/smokeEffect：保持 Fx.none 才能退回弹药自带的炮口特效
     * （对应原版 Turret.effects 的 {@code this.shootEffect == Fx.none ? peekAmmo(tile).shootEffect : this.shootEffect}）。
     */
    private static com.phoenix.game.world.blocks.defense.turrets.Turret turret(String name,
        com.phoenix.game.entities.bullet.BulletType bullet, int health, float range, float reload){
        com.phoenix.game.world.blocks.defense.turrets.Turret t =
            new com.phoenix.game.world.blocks.defense.turrets.Turret(name);
        t.category = Category.turret;
        t.bullet = bullet;
        t.health = health;
        t.range = range;
        t.reload = reload;
        all.add(t);
        return t;
    }

    /** 墙体：实心、有血量（可被摧毁）、带建造材料，归入防御分类。 */
    private static Block wall(String name, int health, ItemStack... requirements){
        Block block = new com.phoenix.game.world.blocks.defense.Wall(name);
        block.health = health;
        block.requirements = requirements;
        block.category = Category.defense;
        all.add(block);
        return block;
    }

    /**
     * 单位工厂：产 {@code unitTypeName}，出厂单位走与波次单位相同的 AI。
     * @param powerUse 每帧耗电
     * @param maxSpawn 同时在场单位上限
     * @param inputs 生产消耗的材料
     */
    private static com.phoenix.game.world.blocks.units.UnitFactory unitFactory(String name, String unitTypeName,
        float produceTime, int size, float powerUse, int maxSpawn, ItemStack[] inputs, ItemStack[] requirements){

        com.phoenix.game.world.blocks.units.UnitFactory factory =
            new com.phoenix.game.world.blocks.units.UnitFactory(name);
        factory.category = Category.units;
        factory.unitTypeName = unitTypeName; //UnitTypes 晚于 Blocks 加载，按名字延迟解析
        factory.produceTime = produceTime;
        factory.size = size;
        factory.maxSpawn = maxSpawn;
        factory.inputItem = inputs;
        factory.requirements = requirements;
        //耗电统一走 consumes：Block.init() 会据此自动开启 hasPower 并同步 powerConsumption
        factory.consumes.power(powerUse);
        all.add(factory);
        return factory;
    }

    /** 机甲平台：站上去点一下换成 {@code unitTypeName}。 */
    private static com.phoenix.game.world.blocks.units.MechPad mechPad(String name, String unitTypeName,
        int size, float powerUse, ItemStack[] requirements){

        com.phoenix.game.world.blocks.units.MechPad pad =
            new com.phoenix.game.world.blocks.units.MechPad(name);
        pad.category = Category.upgrade;
        pad.unitTypeName = unitTypeName;
        pad.size = size;
        pad.requirements = requirements;
        pad.consumes.power(powerUse);
        all.add(pad);
        return pad;
    }

    /** @return 该地板是否为液体 */
    public static boolean isLiquid(Block block){
        return block instanceof Floor && ((Floor)block).isLiquid;
    }
}
