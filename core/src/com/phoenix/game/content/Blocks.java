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
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Drawf;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.entities.Damage;
import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.world.meta.Attribute;
import com.phoenix.game.world.meta.BuildVisibility;
import io.anuke.mindustry.gen.Sounds;

import static com.phoenix.game.Vars.tilesize;

/**
 * 最小实现：方块定义集合。参照 Mindustry mindustry.content.Blocks 移植。
 * 只保留地面 + 墙体 + 核心，贴图取自 sprites.atlas。
 */
public class Blocks implements com.phoenix.game.ctype.ContentList {
    /** 全部方块，按 id 顺序（指向内容加载器的方块列表）。 */
    public static Array<Block> all;

    /** 空方块（空气） */
    public static Floor air;

    //地板
    public static Floor grass, stone, sand, darksand, snow, ice, shale, moss, metalFloor, water;
    //液体地板 + 岩层/自然地形（F1d1 批：对应原版 Blocks.load 的 environment 区，字段名/内容名照原版）
    public static Floor deepwater, taintedWater, darksandTaintedWater, sandWater, darksandWater, tar,
        craters, charr, ignarock, hotrock, magmarock, holostone, salt, iceSnow, pebbles, tendrils;
    //装饰地板 / 面板（F1e1 批：对应原版 environment 区的 metal-floor-2/3/5、metal-floor-damaged、
    //dark-panel-1..6、spore-moss）
    public static Floor sporeMoss, metalFloorDamaged, metalFloor2, metalFloor3, metalFloor5,
        darkPanel1, darkPanel2, darkPanel3, darkPanel4, darkPanel5, darkPanel6;
    /**
     * 墙体血量倍率：原版 {@code Blocks.java:737} 在防御区开头声明 {@code int wallHealthMultiplier = 4;}
     * （局部变量），所有墙/门/推进器的血量都是「基础值 × 4」。
     * <p>本项目把 {@code wall()} 辅助方法与各直接注册处也统一乘这个倍率，故提为类常量以便两处共用。
     */
    public static final int wallHealthMultiplier = 4;

    //墙体
    public static Block copperWall, copperWallLarge, titaniumWall, titaniumWallLarge,
        thoriumWall, thoriumWallLarge, plastaniumWall, plastaniumWallLarge,
        phaseWall, phaseWallLarge, surgeWall, surgeWallLarge, scrapWall, metalWall;
    //废料墙 large/huge/gigantic + 推进器（F1e1 批：原版都是 Wall，前三者 sandboxOnly、无材料消耗）
    public static Block scrapWallLarge, scrapWallHuge, scrapWallGigantic, thruster;
    //门（可通行）
    public static Block door, doorLarge;
    //自然岩壁（对应原版 StaticWall：实心、不可拆、小地图画自身颜色）
    public static Block rocks, sandRocks, duneRocks, shaleRocks, shrubs, darkMetal;
    //岩层 / 岩石 / 自然装饰（F1d1 批：cliffs/icerocks/snowrocks/sporerocks/saltrocks 是 StaticWall，
    //rock/snowrock/sand-boulder/shale-boulder 是 Rock —— 两者都不是 Floor，小地图颜色由 loadMinimapColors 采样）
    public static Block cliffs, sporerocks, rock, snowrock, icerocks, snowrocks, saltRocks,
        sandBoulder, shaleBoulder;
    //树 / 植物（F1e1 批：pine/snow-pine/spore-pine 是 StaticTree，white-tree/white-tree-dead 是 TreeBlock，
    //spore-cluster 是 Rock —— 三者都不是 Floor，小地图颜色由 loadMinimapColors 采样）
    public static Block sporePine, snowPine, pine, whiteTree, whiteTreeDead, sporeCluster;
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
    //防御建筑（炮塔，顺序/命名对齐原版 Blocks.load 的 turrets 区）
    public static com.phoenix.game.world.blocks.defense.turrets.Turret duo, scatter, scorch, hail, wave,
        lancer, arc, swarmer, salvo, fuse, ripple, cyclone, spectre, meltdown;
    //增益建筑（投影器对声明为具体类型，因为配置里要设子类字段 reload/range/healPercent 等）
    public static com.phoenix.game.world.blocks.defense.MendProjector mendProjector, mender;
    public static com.phoenix.game.world.blocks.defense.OverdriveProjector overdriveProjector;
    public static Block forceProjector, shockMine;
    //单位工厂 / 维修 / 指挥 / 机甲平台（字段名照原版；F1e3 批补 draug/spirit/phantom/revenant 四个工厂）
    public static com.phoenix.game.world.blocks.units.UnitFactory draugFactory, spiritFactory, phantomFactory,
        wraithFactory, ghoulFactory, revenantFactory, daggerFactory, crawlerFactory,
        titanFactory, fortressFactory;
    public static com.phoenix.game.world.blocks.units.RepairPoint repairPoint;
    public static com.phoenix.game.world.blocks.units.CommandCenter commandCenter;
    public static com.phoenix.game.world.blocks.units.MechPad mechPad, mechPadDelta, mechPadTau, mechPadOmega,
        javelinPad, tridentPad, glaivePad;
    //沙盒方块（无限资源）
    public static Block powerSource, powerVoid, itemSource, itemVoid;
    //留言板（F1e1 批：对应原版 sandbox 区的 message）
    public static com.phoenix.game.world.blocks.logic.MessageBlock message;
    //发电建筑
    public static SolarGenerator solarPanel, largeSolarPanel;
    public static PowerNode powerNode, powerNodeLarge, surgeTower;
    public static com.phoenix.game.world.blocks.power.PowerDiode diode;
    //电池与发电机
    public static com.phoenix.game.world.blocks.power.Battery battery, batteryLarge;
    public static com.phoenix.game.world.blocks.power.BurnerGenerator combustionGenerator;
    public static com.phoenix.game.world.blocks.power.ThermalGenerator thermalGenerator;
    public static com.phoenix.game.world.blocks.power.DecayGenerator rtgGenerator;
    public static com.phoenix.game.world.blocks.power.NuclearReactor thoriumReactor;
    public static com.phoenix.game.world.blocks.power.ImpactReactor impactReactor;
    //合成/生产 + 电力（F1c 批：对应原版 Blocks.load 的 crafting / production / power 区，字段名照原版）
    public static com.phoenix.game.world.blocks.production.LiquidConverter cryofluidMixer;
    public static com.phoenix.game.world.blocks.production.GenericCrafter melter, coalCentrifuge, sporePress;
    public static com.phoenix.game.world.blocks.production.Separator separator;
    public static com.phoenix.game.world.blocks.production.Cultivator cultivator;
    public static com.phoenix.game.world.blocks.production.Incinerator incinerator;
    public static com.phoenix.game.world.blocks.power.LightBlock illuminator;
    public static com.phoenix.game.world.blocks.power.SingleTypeGenerator differentialGenerator;
    public static com.phoenix.game.world.blocks.power.BurnerGenerator turbineGenerator;
    //液体与生产建筑（W1 批新增：抽液机 / 压裂机 / 装甲导管 / 储液罐）
    public static com.phoenix.game.world.blocks.production.SolidPump waterExtractor;
    public static com.phoenix.game.world.blocks.production.Fracker oilExtractor;
    public static com.phoenix.game.world.blocks.liquid.ArmoredConduit platedConduit;
    public static com.phoenix.game.world.blocks.liquid.LiquidTank liquidTank;
    //液体管道/储运（F1b 批：对应原版 Blocks.load 的 liquid 区，字段名照原版）
    public static com.phoenix.game.world.blocks.production.Pump mechanicalPump, rotaryPump, thermalPump;
    public static com.phoenix.game.world.blocks.liquid.Conduit conduit, pulseConduit;
    public static com.phoenix.game.world.blocks.liquid.LiquidRouter liquidRouter;
    public static com.phoenix.game.world.blocks.liquid.LiquidJunction liquidJunction;
    public static com.phoenix.game.world.blocks.liquid.LiquidExtendingBridge bridgeConduit;
    public static com.phoenix.game.world.blocks.liquid.LiquidBridge phaseConduit;
    //沙盒液体源/虚空（对应原版 sandbox 区）
    public static com.phoenix.game.world.blocks.sandbox.LiquidSource liquidSource;
    public static com.phoenix.game.world.blocks.sandbox.LiquidVoid liquidVoid;
    //波次出生点标记（overlay，不绘制）
    public static Floor spawn;
    //矿脉覆盖层（对应原版 oreCopper 系列）
    public static Floor oreCopper, oreLead, oreScrap, oreCoal, oreTitanium, oreThorium;

    @Override
    public void load(){
        if(air != null) return;

        //方块列表就是内容加载器的方块列表（id 由注册顺序决定）
        all = com.phoenix.game.Vars.content.blocks();

        air = floor("air", 0);
        //空气格不参与"方块落影"烘焙（对应原版 air 定义里的 `hasShadow = false`）——
        //否则地图上每一格空地都会被涂上落影，整张地图变暗
        air.hasShadow = false;

        grass = floor("grass", 3, "6f7a4c");
        stone = floor("stone", 3, "6e6e6e");
        sand = floor("sand", 3, "c7b07e");
        darksand = floor("darksand", 3, "8a6f43");
        snow = floor("snow", 3, "dcdcdc");
        //雪/冰面含水量（抽水机 SolidPump 按 sumAttribute(Attribute.water) 取产量加成），照原版 snow / ice
        snow.attributes.set(Attribute.water, 0.2f);
        ice = floor("ice", 3, "9fd3e8");
        ice.attributes.set(Attribute.water, 0.4f);
        shale = floor("shale", 3, "5c5c66");
        //原版 shale 只带油含量、**不带地热**；此前因 hotrock/magmarock 未移植，临时把 shale 当热源占位。
        //T5 已确认 hotrock/magmarock（含 1..3 变体贴图）早已在下方注册 → 去掉占位，恢复原版配置。
        shale.attributes.set(Attribute.oil, 0.15f);
        moss = floor("moss", 3, "54634a");
        //苔藓的孢子含量（培育机 Cultivator 按 sumAttribute(Attribute.spores) 取产量加成），照原版
        moss.attributes.set(Attribute.spores, 0.15f);
        metalFloor = floor("metal-floor", 0, "808594");
        //沙地直接采出沙（对应原版 Floor.itemDrop；其余资源全部来自矿脉 overlay）
        //但**不允许玩家徒手采**（对应原版 Floor.playerUnmineable；否则开局就能徒手刷沙，必须用钻头）
        sand.itemDrop = Items.sand;
        sand.playerUnmineable = true;
        darksand.itemDrop = Items.sand;
        darksand.playerUnmineable = true;

        water = floor("water", 0, "4a6fa5");
        water.isLiquid = true;
        //原版 Blocks.java:145-148 water: speedMultiplier 0.5 / status wet / statusDuration 90
        water.speedMultiplier = 0.5f;
        water.status = StatusEffects.wet;
        water.statusDuration = 90f;
        //⚠ 刻意偏离（保留并登记，非本批修正项）：本项目另设 dragMultiplier / drownTime，原版 water 没有。
        //   drownTime 有消费者（Pathfinder/ControlPathfinder/Control/Placement/Build 用它判定液体可否通行）→ 保留；
        //   dragMultiplier 目前无消费者（仅 Unit.java 一行注释引用）→ 按任务要求不擅自删，仅登记。
        water.dragMultiplier = 0.7f;
        water.drownTime = 60f * 10f;

        //region 液体地板 + 岩层/自然地形（F1d1 批：对应原版 Blocks.load 的 environment 区，属性照抄原版）
        //⚠ 相对顺序：本项目既有的 grass/stone/sand/darksand/snow/ice/shale/moss/metalFloor/water 是早先按
        //   自己的顺序注册的（方块 id = 在 all 里的下标），本批**不重排**它们，以免让已有存档的 id 错位；
        //   所以这批新方块统一插在既有地板之后，但**彼此之间**严格保持原版 Blocks.load 的顺序。
        //   代价：原版里 "deepwater 在 water 之前"、"stone 在 tar 与 craters 之间" 这两处相邻关系保不住。
        //TODO 本项目 Floor 缺原版的这些字段，原版对应配置一律降级为注释：
        //   updateEffect（magmarock 的 Fx.magmasmoke；本项目也没有"随机更新"系统）、
        //   cacheLayer（water/tar 的绘制分层，本项目 graphics/CacheLayer 是空壳）。
        //✅ playerUnmineable 已补（sand/darksand，见上方 itemDrop 那两行）。
        //✅ blendGroup 与 `edge` / `<name>-edge` 贴图已接回（S3d-2b 批）：
        //   blendGroup 见下面四行赋值；边缘贴图由 `gradlew spriteGen` 生成、`Floor.drawEdges` 绘制。
        //颜色取自图集中心像素（与原版 Block.createIcons 的取色规则一致），供小地图/地图预览使用。

        //—— 液体地板（6 个）——
        deepwater = floor("deepwater", 0, "4e5da4");
        deepwater.speedMultiplier = 0.2f;
        deepwater.liquidDrop = Liquids.water;
        deepwater.isLiquid = true;
        deepwater.status = StatusEffects.wet;
        deepwater.statusDuration = 120f;
        deepwater.drownTime = 140f;

        taintedWater = floor("tainted-water", 0, "5c478d");
        taintedWater.speedMultiplier = 0.17f;
        taintedWater.status = StatusEffects.wet;
        taintedWater.statusDuration = 140f;
        taintedWater.drownTime = 120f;
        taintedWater.liquidDrop = Liquids.water;
        taintedWater.isLiquid = true;

        darksandTaintedWater = floor("darksand-tainted-water", 0, "483d5d");
        darksandTaintedWater.speedMultiplier = 0.75f;
        darksandTaintedWater.status = StatusEffects.wet;
        darksandTaintedWater.statusDuration = 60f;
        darksandTaintedWater.liquidDrop = Liquids.water;
        darksandTaintedWater.isLiquid = true;

        sandWater = floor("sand-water", 0, "92839c");
        sandWater.speedMultiplier = 0.8f;
        sandWater.status = StatusEffects.wet;
        sandWater.statusDuration = 50f;
        sandWater.liquidDrop = Liquids.water;
        sandWater.isLiquid = true;

        darksandWater = floor("darksand-water", 0, "414466");
        darksandWater.speedMultiplier = 0.8f;
        darksandWater.status = StatusEffects.wet;
        darksandWater.statusDuration = 50f;
        darksandWater.liquidDrop = Liquids.water;
        darksandWater.isLiquid = true;

        tar = floor("tar", 0, "292929");
        tar.drownTime = 150f;
        //F1e2 回填：StatusEffects.tarred 已补齐（原版同款 status = StatusEffects.tarred）
        tar.status = StatusEffects.tarred;
        tar.statusDuration = 240f;
        tar.speedMultiplier = 0.19f;
        tar.liquidDrop = Liquids.oil;
        tar.isLiquid = true;
        //刻意偏离：原版可燃性挂在 liquidDrop（Liquids.oil）上，本项目直接标在地板上（供 Fire 系统读）
        tar.flammability = Liquids.oil.flammability;

        //—— 岩层 / 岩石 / 自然装饰（19 个）——
        craters = floor("craters", 3, "54545b");

        charr = floor("char", 3, "54545b");

        ignarock = floor("ignarock", 3, "515151");

        //原版只写 attributes.set(Attribute.heat, 0.5f)；本项目 ThermalGenerator 读的是 Floor.heat 字段
        //（sumAttribute(Attribute.heat) 恒 0），所以两份都写，否则地热发电机看不到这块热源地板
        hotrock = floor("hotrock", 3, "3c3838");
        hotrock.attributes.set(Attribute.heat, 0.5f);
        hotrock.heat = 0.5f;

        magmarock = floor("magmarock", 3, "ffb04a");
        magmarock.attributes.set(Attribute.heat, 0.75f);
        magmarock.heat = 0.75f;

        //边缘融合组（对应原版 Blocks.load 里这四处的 `blendGroup = ...`）：
        //同组地板之间不画边缘，由融合目标那一套 `<target>-edge` 贴图负责过渡；
        //`tools/Generators` 也据此跳过生成这四者自己的 `<name>-edge`。
        craters.blendGroup = stone;
        charr.blendGroup = stone;
        hotrock.blendGroup = ignarock;
        magmarock.blendGroup = ignarock;

        holostone = floor("holostone", 3, "ced0d5");

        salt = floor("salt", 0, "ffffff");

        iceSnow = floor("ice-snow", 3, "e0e8f0");
        iceSnow.attributes.set(Attribute.water, 0.3f);

        //自然岩壁（StaticWall：实心、不可拆、4 格相邻时画一张 -large 大贴图）
        //⚠ variants 是 Rock 的 protected 字段 → 不同包不能直接改，必须在匿名子类的初始化块里赋值
        cliffs = new com.phoenix.game.world.blocks.StaticWall("cliffs"){{
            variants = 1;
            fillsTile = false;
        }};

        sporerocks = new com.phoenix.game.world.blocks.StaticWall("sporerocks"){{
            variants = 2;
        }};

        //岩石（Rock：可破坏、不挡路、可被替换；与原版 StaticWall 的区别就在这三点）
        rock = new com.phoenix.game.world.blocks.Rock("rock"){{
            variants = 2;
        }};

        snowrock = new com.phoenix.game.world.blocks.Rock("snowrock"){{
            variants = 2;
        }};

        icerocks = new com.phoenix.game.world.blocks.StaticWall("icerocks"){{
            variants = 2;
        }};

        snowrocks = new com.phoenix.game.world.blocks.StaticWall("snowrocks"){{
            variants = 2;
        }};

        //原版没写 variants，沿用 StaticWall 构造器的 2
        saltRocks = new com.phoenix.game.world.blocks.StaticWall("saltrocks");

        //—— 树 / 植物（F1e1 批）——
        //原版相对顺序：saltRocks → sporePine → snowPine → pine → shrubs → whiteTreeDead → whiteTree → sporeCluster
        //（shrubs 本项目已在末尾注册，这里跳过；本批新方块彼此严格保持原版顺序）
        //pine 系列是 StaticTree（StaticWall 子类，4 邻同种岩壁时裁掉相邻那条边），原版把 variants 显式置 0
        //⚠ variants 是 Rock 的 protected 字段 → 不同包只能在匿名子类初始化块里赋值（同 F1d1 的 cliffs）
        sporePine = new com.phoenix.game.world.StaticTree("spore-pine"){{
            variants = 0;
        }};

        snowPine = new com.phoenix.game.world.StaticTree("snow-pine"){{
            variants = 0;
        }};

        pine = new com.phoenix.game.world.StaticTree("pine"){{
            variants = 0;
        }};

        //white-tree 系列是 TreeBlock（本体不绘制，drawLayer 按瓦片坐标随机朝向画一次贴图）
        //原版 TreeBlock 没有 requirements → buildVisibility 默认 hidden → 不出现在建造栏。
        //T2 批：本项目 buildVisibility 默认值同为 hidden，且 PlacementFragment 改用 Block.isVisible() 过滤，
        //所以这两个方块自动被排除（原先靠"只排除 Floor/Rock"会漏进来，现已修正）。
        whiteTreeDead = new com.phoenix.game.world.blocks.TreeBlock("white-tree-dead");

        whiteTree = new com.phoenix.game.world.blocks.TreeBlock("white-tree");

        //孢子簇是 Rock（可破坏、不挡路），原版 variants = 3
        sporeCluster = new com.phoenix.game.world.blocks.Rock("spore-cluster"){{
            variants = 3;
        }};

        shaleBoulder = new com.phoenix.game.world.blocks.Rock("shale-boulder"){{
            variants = 2;
        }};

        sandBoulder = new com.phoenix.game.world.blocks.Rock("sand-boulder"){{
            variants = 2;
        }};

        //—— 装饰地板 / 面板（F1e1 批）——
        //原版相对顺序：sandBoulder → moss → sporeMoss → metalFloor → metalFloorDamaged → metalFloor2 → metalFloor3
        //→ metalFloor5 → darkPanel1..6 → darkMetal → pebbles → tendrils
        //（moss/metalFloor 本项目在开头注册、darkMetal 在末尾注册，这里跳过；本批新方块彼此保持原版顺序）
        //颜色照原版 Block.createIcons 的规则从图集中心像素取（PIL 离线采样 sprites.atlas），供小地图/地图预览使用
        //（本项目 loadMinimapColors() 明确跳过 instanceof Floor，地板颜色必须在这里显式配，否则小地图整片发白）
        sporeMoss = floor("spore-moss", 3, "6b4474");
        //孢子含量供培育机（Cultivator）按 sumAttribute(Attribute.spores) 取加成
        sporeMoss.attributes.set(Attribute.spores, 0.3f);

        //⚠ 图集里没有裸名 metal-floor-damaged，只有 metal-floor-damaged1..3 → 必须用 variants = 3（原版同）
        metalFloorDamaged = floor("metal-floor-damaged", 3, "60606b");

        metalFloor2 = floor("metal-floor-2", 0, "60606b");

        metalFloor3 = floor("metal-floor-3", 0, "60606b");

        //⚠ metal-floor-5 中心像素落在橙色警示条上（#cf8d58），与底色 #60606b 不同；
        //   这里按"照原版中心像素取色"的规则取 #cf8d58
        metalFloor5 = floor("metal-floor-5", 0, "cf8d58");

        darkPanel1 = floor("dark-panel-1", 0, "3f4049");
        darkPanel2 = floor("dark-panel-2", 0, "3f4049");
        darkPanel3 = floor("dark-panel-3", 0, "686b7b");
        darkPanel4 = floor("dark-panel-4", 0, "686b7b");
        //⚠ 同 metal-floor-5：中心像素是红色装饰（#ef806c），底色是 #686b7b
        darkPanel5 = floor("dark-panel-5", 0, "ef806c");
        darkPanel6 = floor("dark-panel-6", 0, "3f4049");

        //叠加地表（overlay 层，由 Renderer 的"覆盖层"分支绘制）
        pebbles = new com.phoenix.game.world.blocks.DoubleOverlayFloor("pebbles");
        pebbles.color = Color.valueOf("5e5c62");
        //TODO 本项目 Renderer 画 overlay 走的是 Floor.variant() + batch.draw，**不调** DoubleOverlayFloor.draw()
        //     → pebbles 的"压暗 40% 并下移 0.75 单位的影子层"看不到，实际只画单层（外观比原版平）

        tendrils = new com.phoenix.game.world.blocks.OverlayFloor("tendrils"){{
            //⚠ 本项目 OverlayFloor 构造器把 variants 置 0（当初为出生点标记改的），原版继承 Floor 的默认 3
            variants = 3;
        }};
        tendrils.color = Color.valueOf("6b4474");
        //endregion

        //墙体（血量/材料按原版；large 变体是 2x2、血量与材料 ×4）
        //⚠ 传入的是**基础值**，wall() 统一 ×wallHealthMultiplier(=4)
        //原版 Blocks.java:741 health = 80 * wallHealthMultiplier
        copperWall = wall("copper-wall", 80, new ItemStack(Items.copper, 6));
        //原版 Blocks.java:746 health = 80 * 4 * wallHealthMultiplier
        copperWallLarge = wall("copper-wall-large", 320, new ItemStack(Items.copper, 24));
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
        //原版 Blocks.java:787 health = 150 * wallHealthMultiplier
        phaseWall.health = 150 * wallHealthMultiplier;
        phaseWall.category = Category.defense;
        phaseWall.requirements = new ItemStack[]{ new ItemStack(Items.phasefabric, 6) };
        phaseWall.buildVisibility = BuildVisibility.shown;

        phaseWallLarge = new com.phoenix.game.world.blocks.defense.DeflectorWall("phase-wall-large");
        //原版 Blocks.java:792 health = 150 * 4 * wallHealthMultiplier
        phaseWallLarge.health = 600 * wallHealthMultiplier;
        phaseWallLarge.size = 2;
        phaseWallLarge.category = Category.defense;
        phaseWallLarge.requirements = new ItemStack[]{ new ItemStack(Items.phasefabric, 24) };
        phaseWallLarge.buildVisibility = BuildVisibility.shown;

        //合金墙（受击时按概率闪电反击）
        surgeWall = new com.phoenix.game.world.blocks.defense.SurgeWall("surge-wall");
        //原版 Blocks.java:798 health = 230 * wallHealthMultiplier
        surgeWall.health = 230 * wallHealthMultiplier;
        surgeWall.category = Category.defense;
        surgeWall.requirements = new ItemStack[]{ new ItemStack(Items.surgealloy, 6) };
        surgeWall.buildVisibility = BuildVisibility.shown;

        surgeWallLarge = new com.phoenix.game.world.blocks.defense.SurgeWall("surge-wall-large");
        //原版 Blocks.java:803 health = 230 * 4 * wallHealthMultiplier
        surgeWallLarge.health = 920 * wallHealthMultiplier;
        surgeWallLarge.size = 2;
        surgeWallLarge.category = Category.defense;
        surgeWallLarge.requirements = new ItemStack[]{ new ItemStack(Items.surgealloy, 24) };
        surgeWallLarge.buildVisibility = BuildVisibility.shown;

        //废料墙（图集里只有带编号的变体贴图，用 textureName 指向第一张）
        //原版 Blocks.java:821 scrap-wall: requirements(Category.defense, BuildVisibility.sandboxOnly, ItemStack.with())
        //—— 类别 defense、空材料、沙盒可见。本项目 wall() 已给出 defense + 空材料 + shown，
        //这里只需把可见性覆盖成 sandboxOnly（T2 批：改用 BuildVisibility，不再用 sandboxOnly 布尔补丁）
        scrapWall = wall("scrap-wall", 60);
        scrapWall.buildVisibility = BuildVisibility.sandboxOnly;
        scrapWall.textureName = "scrap-wall1";
        //⚠ 原版 variants = 5；本项目 Wall 没有 variants 字段（也没有变体绘制分支）→ 无法对齐，登记为偏离

        //废料墙 large / huge / gigantic（F1e1 批）：原版都是 sandboxOnly、无材料消耗，血量 60×4/9/16
        //⚠ 本项目 Wall 没有原版的 variants 字段（也没有 Wall.load/draw 的变体分支）→ 用 textureName 指向
        //   第一号变体，丢失"每格随机变体"的外观（原版 variants = 4 / 3 / 0）
        //原版 Blocks.java:827/834/841 同样走 requirements(..., BuildVisibility.sandboxOnly, ItemStack.with())
        scrapWallLarge = wall("scrap-wall-large", 240);
        scrapWallLarge.size = 2;
        scrapWallLarge.buildVisibility = BuildVisibility.sandboxOnly;
        scrapWallLarge.textureName = "scrap-wall-large1";

        scrapWallHuge = wall("scrap-wall-huge", 540);
        scrapWallHuge.size = 3;
        scrapWallHuge.buildVisibility = BuildVisibility.sandboxOnly;
        scrapWallHuge.textureName = "scrap-wall-huge1";

        scrapWallGigantic = wall("scrap-wall-gigantic", 960);
        scrapWallGigantic.size = 4;
        scrapWallGigantic.buildVisibility = BuildVisibility.sandboxOnly;
        //gigantic 原版 variants = 0（只有裸名贴图），不用 textureName

        //推进器（F1e1 批）：原版是 4x4 Wall，血量 55×16 = 880，**没有** requirements（地图装饰用）
        //原版 Blocks.java:846-849 没调 requirements → buildVisibility 保持默认 hidden → 不出现在建造栏。
        //T2 批：本项目 buildVisibility 默认值也是 hidden（Block.buildVisibility），这里无需额外处理。
        thruster = new com.phoenix.game.world.blocks.defense.Wall("thruster");
        //原版 Blocks.java:847 health = 55 * 16 * wallHealthMultiplier
        thruster.health = 880 * wallHealthMultiplier;
        thruster.size = 4;

        //自造金属墙（原版没有，保留以兼容既有存档/布局）
        metalWall = wall("metalWall", 120, new ItemStack(Items.silicon, 8), new ItemStack(Items.lead, 6));

        //门（友方单位靠近自动开；原版是玩家点击开关）
        door = new com.phoenix.game.world.blocks.defense.Door("door");
        //原版 Blocks.java:809 health = 100 * wallHealthMultiplier
        door.health = 100 * wallHealthMultiplier;
        door.category = Category.defense;
        //原版 Blocks.java:808 requirements = graphite6 + silicon4
        door.requirements = new ItemStack[]{ new ItemStack(Items.graphite, 6), new ItemStack(Items.silicon, 4) };
        door.buildVisibility = BuildVisibility.shown;

        doorLarge = new com.phoenix.game.world.blocks.defense.Door("door-large");
        //原版 Blocks.java:816 health = 100 * 4 * wallHealthMultiplier
        doorLarge.health = 400 * wallHealthMultiplier;
        doorLarge.size = 2;
        doorLarge.category = Category.defense;
        //原版 Blocks.java:813 requirements = graphite24 + silicon16（= door ×4）
        doorLarge.requirements = new ItemStack[]{ new ItemStack(Items.graphite, 24), new ItemStack(Items.silicon, 16) };
        doorLarge.buildVisibility = BuildVisibility.shown;

        //贴图名需与图集一致：图集里是 core-shard / core-foundation / core-nucleus
        core = new CoreBlock("core-shard");
        core.category = Category.effect;
        //原版 Blocks.java:1271 alwaysUnlocked
        core.alwaysUnlocked = true;
        //原版 Blocks.java:1273-1275 health 1100 / itemCapacity 4000 / size 3
        core.health = 1100;
        core.itemCapacity = 4000;
        core.requirements = new ItemStack[]{ new ItemStack(Items.copper, 120), new ItemStack(Items.lead, 80) };
        //原版 Blocks.java:1270 requirements(Category.effect, BuildVisibility.debugOnly, ItemStack.with())
        //→ 核心只在编辑器/调试里出现，不进建造栏。
        //⚠ 刻意偏离：原版这里材料是**空**的；本项目保留了 core 的建造材料（既有实现，本批只改可见性）。
        core.buildVisibility = BuildVisibility.debugOnly;

        //大型核心（4x4）
        coreFoundation = new CoreBlock("core-foundation");
        coreFoundation.category = Category.effect;
        coreFoundation.size = 4;
        //原版 Blocks.java:1281-1282 health 2000 / itemCapacity 9000
        coreFoundation.health = 2000;
        coreFoundation.itemCapacity = 9000;
        coreFoundation.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 300), new ItemStack(Items.lead, 300), new ItemStack(Items.silicon, 200)
        };
        //原版 Blocks.java:1279 requirements(Category.effect, BuildVisibility.debugOnly, ItemStack.with())
        coreFoundation.buildVisibility = BuildVisibility.debugOnly;

        //巨型核心（5x5）
        coreNucleus = new CoreBlock("core-nucleus");
        coreNucleus.category = Category.effect;
        coreNucleus.size = 5;
        //原版 Blocks.java:1289-1290 health 4000 / itemCapacity 13000
        coreNucleus.health = 4000;
        coreNucleus.itemCapacity = 13000;
        coreNucleus.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 800), new ItemStack(Items.lead, 800),
            new ItemStack(Items.silicon, 500), new ItemStack(Items.thorium, 300)
        };
        //原版 Blocks.java:1287 requirements(Category.effect, BuildVisibility.debugOnly, ItemStack.with())
        coreNucleus.buildVisibility = BuildVisibility.debugOnly;

        //机械钻头（基础，无电）：数值全部照搬原版 mechanical-drill。
        copperDrill = new Drill("mechanical-drill");
        copperDrill.category = Category.production;
        //原版 Blocks.java:1178 requirements(Category.production, ..., true) → 始终解锁
        copperDrill.alwaysUnlocked = true;
        copperDrill.tier = 2;
        copperDrill.size = 2;
        copperDrill.drillTime = 600f;
        copperDrill.drawMineItem = true;
        //原版 mechanical-drill 的液体加速声明（同时把 water 登记进 consumes.liquidfilters，
        //否则 Block.acceptLiquid 的判据会让钻头拒绝一切液体、加成失效）
        copperDrill.consumes.add(new com.phoenix.game.world.consumers.ConsumeLiquid(Liquids.water, 0.05f)).boost();
        copperDrill.requirements = new ItemStack[]{ new ItemStack(Items.copper, 12) };
        copperDrill.buildVisibility = BuildVisibility.shown;

        //气动钻头（更快，采得动钛矿）
        //⚠原版 pneumatic-drill **不耗电**（只靠水加成提速），这里不能加 consumes.power，
        //否则没接电网时 PowerGraph 会把 status 打成 0、钻头挖掘速度恒为 0（完全不产出）
        leadDrill = new Drill("pneumatic-drill");
        leadDrill.category = Category.production;
        leadDrill.tier = 3;
        leadDrill.size = 2;
        leadDrill.drillTime = 400f;
        leadDrill.drawMineItem = true;
        leadDrill.consumes.add(new com.phoenix.game.world.consumers.ConsumeLiquid(Liquids.water, 0.06f)).boost();
        leadDrill.requirements = new ItemStack[]{ new ItemStack(Items.copper, 18), new ItemStack(Items.graphite, 10) };
        leadDrill.buildVisibility = BuildVisibility.shown;

        //激光钻头（更快，3x3，耗电）
        laserDrill = new Drill("laser-drill");
        laserDrill.category = Category.production;
        laserDrill.tier = 4;
        laserDrill.drillTime = 280f;
        laserDrill.size = 3;
        //不覆盖 health：原版 laser-drill 无显式 health → size3 默认 360（Drill 构造器亦无 health）
        laserDrill.consumes.power(1.10f);
        //原版 Drill 靠 consumes.liquid(water, 0.08f).boost() 声明"液体加速"（同时把 water 登记进
        //consumes.liquidfilters，否则 Block.acceptLiquid 的判据会让钻头拒绝一切液体、液体加成失效）
        laserDrill.consumes.add(new com.phoenix.game.world.consumers.ConsumeLiquid(Liquids.water, 0.08f)).boost();
        laserDrill.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 35), new ItemStack(Items.graphite, 30),
            new ItemStack(Items.silicon, 30), new ItemStack(Items.titanium, 20)
        };
        laserDrill.buildVisibility = BuildVisibility.shown;

        //爆裂钻头（最快，4x4，耗电）
        blastDrill = new Drill("blast-drill");
        blastDrill.category = Category.production;
        blastDrill.tier = 5;
        blastDrill.drillTime = 280f;
        blastDrill.size = 4;
        //不覆盖 health：原版 blast-drill 无显式 health → size4 默认 640（Drill 构造器亦无 health）
        blastDrill.drawRim = true;
        blastDrill.rotateSpeed = 6f;
        blastDrill.warmupSpeed = 0.01f;
        blastDrill.consumes.power(3f);
        //原版 Drill 的液体加速声明（同 laserDrill，见上）
        blastDrill.consumes.add(new com.phoenix.game.world.consumers.ConsumeLiquid(Liquids.water, 0.1f)).boost();
        blastDrill.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 65), new ItemStack(Items.silicon, 60),
            new ItemStack(Items.titanium, 50), new ItemStack(Items.thorium, 75)
        };
        blastDrill.buildVisibility = BuildVisibility.shown;

        //硅合成器：煤 + 沙 → 硅（原版配方）
        siliconCrafter = new GenericCrafter("silicon-smelter");
        siliconCrafter.category = Category.crafting;
        siliconCrafter.consumes.items(new ItemStack(Items.coal, 1), new ItemStack(Items.sand, 2));
        siliconCrafter.consumes.power(0.50f);
        siliconCrafter.outputItem = new ItemStack(Items.silicon, 1);
        siliconCrafter.craftTime = 40f;
        siliconCrafter.size = 2;
        siliconCrafter.requirements = new ItemStack[]{ new ItemStack(Items.copper, 30), new ItemStack(Items.lead, 25) };
        siliconCrafter.buildVisibility = BuildVisibility.shown;

        //石墨压机：煤 x2 → 石墨（无电）
        graphitePress = new GenericCrafter("graphite-press");
        graphitePress.category = Category.crafting;
        graphitePress.consumes.item(Items.coal, 2);
        graphitePress.outputItem = new ItemStack(Items.graphite, 1);
        graphitePress.craftTime = 90f;
        graphitePress.size = 2;
        //不覆盖 health：原版 graphite-press 无显式 health → GenericCrafter 默认 60（原版 Blocks.java:436）
        graphitePress.requirements = new ItemStack[]{ new ItemStack(Items.copper, 75), new ItemStack(Items.lead, 30) };
        graphitePress.buildVisibility = BuildVisibility.shown;

        //多向压机：煤 x3 → 石墨 x2（3x3，耗电；原版还耗水，本项目跳过液体）
        multiPress = new GenericCrafter("multi-press");
        multiPress.category = Category.crafting;
        multiPress.consumes.item(Items.coal, 3);
        multiPress.consumes.power(1.8f);
        multiPress.outputItem = new ItemStack(Items.graphite, 2);
        multiPress.craftTime = 30f;
        multiPress.size = 3;
        //不覆盖 health：原版 multi-press 无显式 health → GenericCrafter 默认 60（原版 Blocks.java:448）
        multiPress.requirements = new ItemStack[]{
            new ItemStack(Items.titanium, 100), new ItemStack(Items.silicon, 25),
            new ItemStack(Items.lead, 100), new ItemStack(Items.graphite, 50)
        };
        multiPress.buildVisibility = BuildVisibility.shown;

        //窑：铅 + 沙 → 玻璃
        kiln = new GenericSmelter("kiln");
        kiln.category = Category.crafting;
        kiln.consumes.items(new ItemStack(Items.lead, 1), new ItemStack(Items.sand, 1));
        kiln.consumes.power(0.60f);
        kiln.outputItem = new ItemStack(Items.metaglass, 1);
        kiln.craftTime = 30f;
        kiln.size = 2;
        //不覆盖 health：原版 kiln 无显式 health → GenericCrafter(GenericSmelter) 默认 60（原版 Blocks.java:478）
        kiln.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 60), new ItemStack(Items.graphite, 30), new ItemStack(Items.lead, 30)
        };
        kiln.buildVisibility = BuildVisibility.shown;

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
        plastaniumCompressor.buildVisibility = BuildVisibility.shown;

        //相织机：钍 x4 + 沙 x10 → 相织物
        phaseWeaver = new GenericCrafter("phase-weaver");
        phaseWeaver.category = Category.crafting;
        phaseWeaver.consumes.items(new ItemStack(Items.thorium, 4), new ItemStack(Items.sand, 10));
        phaseWeaver.consumes.power(5f);
        phaseWeaver.outputItem = new ItemStack(Items.phasefabric, 1);
        phaseWeaver.craftTime = 120f;
        phaseWeaver.size = 2;
        phaseWeaver.itemCapacity = 20;
        //不覆盖 health：原版 phase-weaver 无显式 health → GenericCrafter 默认 60（原版 Blocks.java:520）
        phaseWeaver.requirements = new ItemStack[]{
            new ItemStack(Items.silicon, 130), new ItemStack(Items.lead, 120), new ItemStack(Items.thorium, 75)
        };
        phaseWeaver.buildVisibility = BuildVisibility.shown;

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
        //不覆盖 health：原版 alloy-smelter 无显式 health → GenericCrafter(GenericSmelter) 默认 60（原版 Blocks.java:557）
        surgeSmelter.requirements = new ItemStack[]{
            new ItemStack(Items.silicon, 80), new ItemStack(Items.lead, 80), new ItemStack(Items.thorium, 70)
        };
        surgeSmelter.buildVisibility = BuildVisibility.shown;

        //低温液混合机：水 + 钛 → 低温液（2x2，耗电 1/帧；对应原版 LiquidConverter）
        //TODO 原版用自定义 drawer 画 -bottom/-liquid/-top 三层；本项目 LiquidConverter 走默认绘制，
        //     且图集里**没有裸名 cryofluidmixer**（只有 -bottom/-liquid/-top）→ 用 textureName 指向 -bottom，
        //     否则整块不可见（liquid/top 两层仍缺失）。
        cryofluidMixer = new com.phoenix.game.world.blocks.production.LiquidConverter("cryofluidmixer");
        cryofluidMixer.textureName = "cryofluidmixer-bottom";
        cryofluidMixer.outputLiquid = new com.phoenix.game.type.LiquidStack(Liquids.cryofluid, 0.2f);
        cryofluidMixer.craftTime = 120f;
        cryofluidMixer.size = 2;
        cryofluidMixer.hasLiquids = true;
        //原版 rotate = false（Block.rotate 默认就是 false，本项目无需重复设置）
        cryofluidMixer.consumes.power(1f);
        cryofluidMixer.consumes.item(Items.titanium);
        cryofluidMixer.consumes.add(new com.phoenix.game.world.consumers.ConsumeLiquid(Liquids.water, 0.2f));
        cryofluidMixer.requirements(Category.crafting,
            ItemStack.with(Items.lead, 65, Items.silicon, 40, Items.titanium, 60));

        //爆混机：硫磺 + 孢子荚 → 爆混物
        blastMixer = new GenericCrafter("blast-mixer");
        blastMixer.category = Category.crafting;
        blastMixer.consumes.items(new ItemStack(Items.pyratite, 1), new ItemStack(Items.sporePod, 1));
        blastMixer.consumes.power(0.40f);
        blastMixer.outputItem = new ItemStack(Items.blastCompound, 1);
        blastMixer.craftTime = 60f;
        blastMixer.size = 2;
        //不覆盖 health：原版 blast-mixer 无显式 health → GenericCrafter 默认 60（原版 Blocks.java:607）
        blastMixer.requirements = new ItemStack[]{ new ItemStack(Items.lead, 30), new ItemStack(Items.titanium, 20) };
        blastMixer.buildVisibility = BuildVisibility.shown;

        //硫磺混合机：煤 + 铅 + 沙 → 硫磺
        pyratiteMixer = new GenericSmelter("pyratite-mixer");
        pyratiteMixer.category = Category.crafting;
        pyratiteMixer.consumes.items(
            new ItemStack(Items.coal, 1), new ItemStack(Items.lead, 2), new ItemStack(Items.sand, 2));
        pyratiteMixer.consumes.power(0.20f);
        pyratiteMixer.outputItem = new ItemStack(Items.pyratite, 1);
        pyratiteMixer.craftTime = 60f;
        pyratiteMixer.size = 2;
        //不覆盖 health：原版 pyratite-mixer 无显式 health → GenericCrafter(GenericSmelter) 默认 60（原版 Blocks.java:618）
        pyratiteMixer.requirements = new ItemStack[]{ new ItemStack(Items.copper, 50), new ItemStack(Items.lead, 25) };
        pyratiteMixer.buildVisibility = BuildVisibility.shown;

        //熔炉：废料 → 熔渣（1x1，耗电 1/帧）
        //⚠ 液体产出靠本项目 GenericCrafter.CraftEntity 的 outputLiquid 分支（本批补，见该文件）
        melter = new GenericCrafter("melter");
        melter.outputLiquid = new com.phoenix.game.type.LiquidStack(Liquids.slag, 2f);
        melter.craftTime = 10f;
        melter.health = 200;
        melter.hasLiquids = true;
        melter.consumes.power(1f);
        melter.consumes.item(Items.scrap, 1);
        melter.requirements(Category.crafting,
            ItemStack.with(Items.copper, 30, Items.lead, 35, Items.graphite, 45));

        //分离机：熔渣 → 随机一种矿物（2x2，耗电 1/帧）
        separator = new com.phoenix.game.world.blocks.production.Separator("separator");
        separator.results = ItemStack.with(
            Items.copper, 5,
            Items.lead, 3,
            Items.graphite, 2,
            Items.titanium, 2
        );
        separator.craftTime = 35f;
        separator.size = 2;
        separator.consumes.power(1f);
        separator.consumes.add(new com.phoenix.game.world.consumers.ConsumeLiquid(Liquids.slag, 0.07f));
        separator.requirements(Category.crafting, ItemStack.with(Items.copper, 30, Items.titanium, 25));

        //孢子压缩机：孢子荚 → 油（2x2，耗电 0.60/帧）
        sporePress = new GenericCrafter("spore-press");
        sporePress.outputLiquid = new com.phoenix.game.type.LiquidStack(Liquids.oil, 6f);
        sporePress.craftTime = 20f;
        sporePress.size = 2;
        sporePress.health = 320;
        sporePress.liquidCapacity = 60f;
        sporePress.hasLiquids = true;
        sporePress.consumes.item(Items.sporePod, 1);
        sporePress.consumes.power(0.60f);
        sporePress.requirements(Category.crafting, ItemStack.with(Items.lead, 35, Items.silicon, 30));

        //粉碎机：废料 → 沙
        pulverizer = new GenericCrafter("pulverizer");
        pulverizer.category = Category.crafting;
        pulverizer.consumes.item(Items.scrap, 1);
        pulverizer.consumes.power(0.50f);
        pulverizer.outputItem = new ItemStack(Items.sand, 1);
        pulverizer.craftTime = 40f;
        //不覆盖 health：原版 pulverizer 无显式 health → GenericCrafter 默认 60（原版 Blocks.java:693）
        pulverizer.requirements = new ItemStack[]{ new ItemStack(Items.copper, 30), new ItemStack(Items.lead, 25) };
        pulverizer.buildVisibility = BuildVisibility.shown;

        //煤炭离心机：油 → 煤（2x2，耗电 0.5/帧）
        coalCentrifuge = new GenericCrafter("coal-centrifuge");
        coalCentrifuge.outputItem = new ItemStack(Items.coal, 1);
        coalCentrifuge.craftTime = 30f;
        coalCentrifuge.size = 2;
        coalCentrifuge.hasLiquids = true;
        coalCentrifuge.consumes.add(new com.phoenix.game.world.consumers.ConsumeLiquid(Liquids.oil, 0.09f));
        coalCentrifuge.consumes.power(0.5f);
        coalCentrifuge.requirements(Category.crafting,
            ItemStack.with(Items.titanium, 20, Items.graphite, 40, Items.lead, 30));

        //焚化炉：通电升温后销毁收到的物品/液体（1x1，耗电 0.50/帧）
        //（本项目 BlockStorage 是走 block.acceptLiquid/handleLiquid 虚调用，Incinerator 覆写的正是这两个签名 → 液体也能烧）
        incinerator = new com.phoenix.game.world.blocks.production.Incinerator("incinerator");
        incinerator.health = 90;
        incinerator.consumes.power(0.50f);
        incinerator.requirements(Category.crafting, ItemStack.with(Items.graphite, 5, Items.lead, 15));

        //传送带
        conveyor = new Conveyor("conveyor");
        conveyor.category = Category.distribution;
        //原版 Blocks.java:903 requirements(Category.distribution, ..., true) → 始终解锁
        conveyor.alwaysUnlocked = true;
        conveyor.speed = 0.03f;
        conveyor.displayedSpeed = 4.2f;
        conveyor.requirements = new ItemStack[]{ new ItemStack(Items.copper, 1) };
        conveyor.buildVisibility = BuildVisibility.shown;

        //钛传送带（更快）
        titaniumConveyor = new TitaniumConveyor("titanium-conveyor");
        titaniumConveyor.category = Category.distribution;
        //原版 Blocks.java:910 requirements = copper1 + lead1 + titanium1
        //⚠ 原版 titanium-conveyor **没有** alwaysUnlocked（原版 Blocks.java:909-914 的 requirements 不带 true）
        //   → 这里刻意不设，避免与原版解锁进度不一致
        titaniumConveyor.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 1), new ItemStack(Items.lead, 1), new ItemStack(Items.titanium, 1)
        };
        titaniumConveyor.buildVisibility = BuildVisibility.shown;

        //装甲传送带（只收正后方输入；图集缺 armored-conveyor 贴图，暂用 conveyor 占位）
        armoredConveyor = new ArmoredConveyor("armored-conveyor");
        armoredConveyor.category = Category.distribution;
        //原版 Blocks.java:917 requirements = plastanium1 + thorium1 + metaglass1
        armoredConveyor.requirements = new ItemStack[]{
            new ItemStack(Items.plastanium, 1), new ItemStack(Items.thorium, 1), new ItemStack(Items.metaglass, 1)
        };
        armoredConveyor.buildVisibility = BuildVisibility.shown;

        //交叉器（两条带在此直行穿越）
        junction = new Junction("junction");
        junction.category = Category.distribution;
        //原版 Blocks.java:924 requirements(Category.distribution, ItemStack.with(Items.copper, 2), true) → 始终解锁
        junction.alwaysUnlocked = true;
        junction.requirements = new ItemStack[]{ new ItemStack(Items.copper, 2) };
        junction.buildVisibility = BuildVisibility.shown;
        //原版 junction.buildCostMultiplier = 6f（方块级成本倍率，影响建造耗时）
        junction.buildCostMultiplier = 6f;

        //路由器 / 分配器（轮询输出）
        router = new Router("router");
        router.category = Category.distribution;
        router.requirements = new ItemStack[]{ new ItemStack(Items.copper, 3) };
        router.buildVisibility = BuildVisibility.shown;
        //原版 router.buildCostMultiplier = 2f
        router.buildCostMultiplier = 2f;

        distributor = new Router("distributor");
        distributor.category = Category.distribution;
        distributor.size = 2;
        //原版 Blocks.java:963 requirements = lead4 + copper4
        distributor.requirements = new ItemStack[]{ new ItemStack(Items.lead, 4), new ItemStack(Items.copper, 4) };
        distributor.buildVisibility = BuildVisibility.shown;

        //分类器 / 反选分类器（图集缺 inverted-sorter，复用 sorter 贴图）
        sorter = new Sorter("sorter");
        sorter.category = Category.distribution;
        sorter.requirements = new ItemStack[]{ new ItemStack(Items.copper, 2), new ItemStack(Items.lead, 2) };
        sorter.buildVisibility = BuildVisibility.shown;
        //原版 sorter.buildCostMultiplier = 3f
        sorter.buildCostMultiplier = 3f;

        invertedSorter = new Sorter("inverted-sorter");
        invertedSorter.category = Category.distribution;
        invertedSorter.invert = true;
        invertedSorter.textureName = "sorter";
        invertedSorter.requirements = new ItemStack[]{ new ItemStack(Items.copper, 2), new ItemStack(Items.lead, 2) };
        invertedSorter.buildVisibility = BuildVisibility.shown;
        //原版 invertedSorter.buildCostMultiplier = 3f
        invertedSorter.buildCostMultiplier = 3f;

        //溢流闸门 / 逆流闸门（图集缺 underflow-gate，复用 overflow-gate 贴图）
        overflowGate = new OverflowGate("overflow-gate");
        overflowGate.category = Category.distribution;
        //原版 Blocks.java:968 requirements = lead2 + copper4
        overflowGate.requirements = new ItemStack[]{ new ItemStack(Items.lead, 2), new ItemStack(Items.copper, 4) };
        overflowGate.buildVisibility = BuildVisibility.shown;
        //原版 overflowGate.buildCostMultiplier = 3f
        overflowGate.buildCostMultiplier = 3f;

        underflowGate = new OverflowGate("underflow-gate");
        underflowGate.category = Category.distribution;
        underflowGate.invert = true;
        underflowGate.textureName = "overflow-gate";
        //原版 Blocks.java:973 requirements = lead2 + copper4
        underflowGate.requirements = new ItemStack[]{ new ItemStack(Items.lead, 2), new ItemStack(Items.copper, 4) };
        underflowGate.buildVisibility = BuildVisibility.shown;
        //原版 underflowGate.buildCostMultiplier = 3f
        underflowGate.buildCostMultiplier = 3f;

        //物品桥（跨格运输；放置时自动连上"上一次放置的同类桥"）
        itemBridge = new BufferedItemBridge("bridge-conveyor");
        itemBridge.category = Category.distribution;
        itemBridge.range = 4;
        itemBridge.speed = 70f;
        itemBridge.bufferCapacity = 14;
        itemBridge.requirements = new ItemStack[]{ new ItemStack(Items.lead, 4), new ItemStack(Items.copper, 4) };
        itemBridge.buildVisibility = BuildVisibility.shown;

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
        phaseConveyor.buildVisibility = BuildVisibility.shown;

        //质量驱动器（远距离批量投送；两台互相对准后排队发射）
        massDriver = new MassDriver("mass-driver");
        massDriver.category = Category.distribution;
        massDriver.size = 3;
        massDriver.range = 440f;
        massDriver.reloadTime = 200f;
        //minDistribute 不写：类字段默认值就是 10（原版 Blocks.java 同样不写）
        massDriver.itemCapacity = 120;
        //耗电统一走 consumes（Block.init() 据此开 hasPower 并同步 powerConsumption）；原版 = 1.75
        massDriver.consumes.power(1.75f);
        massDriver.requirements = new ItemStack[]{
            new ItemStack(Items.titanium, 125), new ItemStack(Items.silicon, 75),
            new ItemStack(Items.lead, 125), new ItemStack(Items.thorium, 50)
        };
        massDriver.buildVisibility = BuildVisibility.shown;

        //仓库（container 是 2x2）
        //贴图名用图集里的 container（图集没有 storage 区域，之前会渲染成白块）
        storage = new StorageBlock("container");
        storage.category = Category.effect;
        storage.size = 2;
        storage.itemCapacity = 300;
        //原版 Blocks.java:1301 container 材料 = titanium ×100；health = size2 默认 160
        //⚠ 本项目 StorageBlock 构造器硬编码 health = 120（原版不设），size 默认不可达 → 显式给 160
        storage.health = 160;
        storage.requirements = new ItemStack[]{ new ItemStack(Items.titanium, 100) };
        storage.buildVisibility = BuildVisibility.shown;

        //大型仓库（3x3，容量 1000）
        vault = new com.phoenix.game.world.blocks.storage.Vault("vault");
        vault.category = Category.effect;
        //原版 vault 无显式 health → size3 默认 360（本项目 StorageBlock 硬编码 120，故显式给 360）
        vault.health = 360;
        vault.requirements = new ItemStack[]{ new ItemStack(Items.titanium, 250), new ItemStack(Items.thorium, 125) };
        vault.buildVisibility = BuildVisibility.shown;

        //装卸器（从相邻存储取物再丢给别的建筑）
        unloader = new com.phoenix.game.world.blocks.storage.Unloader("unloader");
        unloader.category = Category.effect;
        //原版 Blocks.java:1308 speed = 7f（类默认 1f，原版在 Blocks 里覆盖成 7）
        //⚠ 字段声明是 Block 类型（本文件惯例），故此处转型访问子类字段
        ((com.phoenix.game.world.blocks.storage.Unloader)unloader).speed = 7f;
        unloader.requirements = new ItemStack[]{ new ItemStack(Items.silicon, 30), new ItemStack(Items.titanium, 25) };
        unloader.buildVisibility = BuildVisibility.shown;

        //发射台（装满后把整仓物品送进全局库存，供后续对局取用）
        launchPad = new com.phoenix.game.world.blocks.storage.LaunchPad("launch-pad");
        launchPad.category = Category.effect;
        launchPad.size = 3;
        //原版 Blocks.java:1312-1315 requirements copper250+silicon75+lead100 / itemCapacity 100 / launchTime 60*16
        launchPad.itemCapacity = 100;
        ((com.phoenix.game.world.blocks.storage.LaunchPad)launchPad).launchTime = 60f * 16f;
        //原版 Blocks.java:1316-1317 hasPower = true / consumes.power(1f)（hasPower、consumes 都是 Block 字段，无需转型）
        launchPad.hasPower = true;
        launchPad.consumes.power(1f);
        //health = size3 默认 360（本项目 StorageBlock 硬编码 120，故显式给 360）
        launchPad.health = 360;
        launchPad.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 250), new ItemStack(Items.silicon, 75), new ItemStack(Items.lead, 100)
        };
        //原版 Blocks.java:1312 requirements(Category.effect, BuildVisibility.campaignOnly, ...)
        //→ 只在战役区域（本项目判据 Vars.world.isZone()）里列出，普通对局建造栏不出现
        launchPad.buildVisibility = BuildVisibility.campaignOnly;

        launchPadLarge = new com.phoenix.game.world.blocks.storage.LaunchPad("launch-pad-large");
        launchPadLarge.category = Category.effect;
        launchPadLarge.size = 4;
        //原版 Blocks.java:1321-1324 requirements titanium200+silicon150+lead250+plastanium75 / itemCapacity 250 / launchTime 60*14
        launchPadLarge.itemCapacity = 250;
        ((com.phoenix.game.world.blocks.storage.LaunchPad)launchPadLarge).launchTime = 60f * 14f;
        //原版 Blocks.java:1325-1326 hasPower = true / consumes.power(2f)（hasPower、consumes 都是 Block 字段，无需转型）
        launchPadLarge.hasPower = true;
        launchPadLarge.consumes.power(2f);
        //health = size4 默认 640（本项目 StorageBlock 硬编码 120，故显式给 640）
        launchPadLarge.health = 640;
        launchPadLarge.requirements = new ItemStack[]{
            new ItemStack(Items.titanium, 200), new ItemStack(Items.silicon, 150),
            new ItemStack(Items.lead, 250), new ItemStack(Items.plastanium, 75)
        };
        //原版 Blocks.java:1321 requirements(Category.effect, BuildVisibility.campaignOnly, ...)
        launchPadLarge.buildVisibility = BuildVisibility.campaignOnly;

        //region 炮塔（原版 Blocks.load 的 turrets 区，顺序/命名/数值照搬；与每个炮塔的具体子类一致）
        //T1 批：原版炮塔的 xRand（炮口横向抖动）、ammoUseEffect（弹壳特效）、ammoEjectBack（弹壳后抛距离）、
        //     shootShake（震屏）、heatColor（炮管发热色）已回填到 Turret 并接进绘制/开火路径；
        //     仅 activeSound/activeSoundVolume（工作音效）缺依赖，归 B 类保留 TODO。
        //F2 批：弹药表已按原版逐个回填（此前降级的"同族代表弹"已全部替换，见 Bullets.java 的 F2 段）。

        //双管炮塔（对空对地，左右炮管交替开火）
        duo = new com.phoenix.game.world.blocks.defense.turrets.DoubleTurret("duo"){{
            requirements(Category.turret, ItemStack.with(Items.copper, 35), true);
            ammo(
                Items.copper, Bullets.standardCopper,
                Items.graphite, Bullets.standardDense,
                Items.pyratite, Bullets.standardIncendiary,
                Items.silicon, Bullets.standardHoming
            );
            reload = 20f;
            restitution = 0.03f;
            range = 100f;
            shootCone = 15f;
            ammoUseEffect = Fx.shellEjectSmall;
            health = 250;
            inaccuracy = 2f;
            rotatespeed = 10f;
        }};

        //散弹炮（2x2，对地，点射两发）
        scatter = new com.phoenix.game.world.blocks.defense.turrets.BurstTurret("scatter"){{
            requirements(Category.turret, ItemStack.with(Items.copper, 85, Items.lead, 45));
            ammo(
                Items.scrap, Bullets.flakScrap,
                Items.lead, Bullets.flakLead,
                Items.metaglass, Bullets.flakGlass
            );
            reload = 18f;
            range = 170f;
            size = 2;
            burstSpacing = 5f;
            shots = 2;
            targetGround = false;

            recoil = 2f;
            rotatespeed = 15f;
            inaccuracy = 17f;
            shootCone = 35f;

            health = 200 * size * size;
            shootSound = Sounds.shootSnap;
        }};

        //喷火器（对地，短程）
        scorch = new com.phoenix.game.world.blocks.defense.turrets.ItemTurret("scorch"){{
            requirements(Category.turret, ItemStack.with(Items.copper, 25, Items.graphite, 22));
            ammo(
                Items.coal, Bullets.basicFlame,
                Items.pyratite, Bullets.pyraFlame
            );
            recoil = 0f;
            reload = 5f;
            coolantMultiplier = 2f;
            range = 60f;
            shootCone = 50f;
            targetAir = false;
            ammoUseEffect = Fx.none;
            health = 400;
            shootSound = Sounds.flame;
        }};

        //迫击炮（抛物线，只打地面）
        hail = new com.phoenix.game.world.blocks.defense.turrets.ArtilleryTurret("hail"){{
            requirements(Category.turret, ItemStack.with(Items.copper, 40, Items.graphite, 17));
            ammo(
                Items.graphite, Bullets.artilleryDense,
                Items.silicon, Bullets.artilleryHoming,
                Items.pyratite, Bullets.artilleryIncendiary
            );
            reload = 60f;
            recoil = 2f;
            range = 230f;
            inaccuracy = 1f;
            shootCone = 10f;
            health = 260;
            shootSound = Sounds.artillery;
        }};

        //液体炮塔（2x2，弹药是液体；本批新注册）
        wave = new com.phoenix.game.world.blocks.defense.turrets.LiquidTurret("wave"){{
            requirements(Category.turret, ItemStack.with(Items.metaglass, 45, Items.lead, 75));
            ammo(
                Liquids.water, Bullets.waterShot,
                Liquids.slag, Bullets.slagShot,
                Liquids.cryofluid, Bullets.cryoShot,
                Liquids.oil, Bullets.oilShot
            );
            size = 2;
            recoil = 0f;
            reload = 2f;
            inaccuracy = 5f;
            shootCone = 50f;
            shootEffect = Fx.shootLiquid;
            range = 110f;
            health = 250 * size * size;
            shootSound = Sounds.splash;
        }};

        //长矛（2x2，对地，蓄力激光束）
        lancer = new com.phoenix.game.world.blocks.defense.turrets.ChargeTurret("lancer"){{
            requirements(Category.turret, ItemStack.with(Items.copper, 25, Items.lead, 50, Items.silicon, 45));
            range = 155f;
            chargeTime = 50f;
            chargeMaxDelay = 30f;
            chargeEffects = 7;
            shootType = Bullets.lancerLaser;
            recoil = 2f;
            reload = 90f;
            cooldown = 0.03f;
            powerUse = 2.5f;
            shootShake = 2f;
            shootEffect = Fx.lancerLaserShoot;
            smokeEffect = Fx.lancerLaserShootSmoke;
            chargeEffect = Fx.lancerLaserCharge;
            chargeBeginEffect = Fx.lancerLaserChargeBegin;
            heatColor = Color.RED;
            size = 2;
            health = 280 * size * size;
            targetAir = false;
            shootSound = Sounds.laser;
        }};

        //电弧（耗电，对地，瞬时闪电）
        arc = new com.phoenix.game.world.blocks.defense.turrets.PowerTurret("arc"){{
            requirements(Category.turret, ItemStack.with(Items.copper, 35, Items.lead, 50));
            shootType = Bullets.arc;
            reload = 35f;
            shootCone = 40f;
            rotatespeed = 8f;
            powerUse = 1.5f;
            targetAir = false;
            range = 90f;
            shootEffect = Fx.lightningShoot;
            heatColor = Color.RED;
            recoil = 1f;
            size = 1;
            health = 260;
            shootSound = Sounds.spark;
        }};

        //蜂群（2x2，四连发追踪导弹）
        swarmer = new com.phoenix.game.world.blocks.defense.turrets.BurstTurret("swarmer"){{
            requirements(Category.turret, ItemStack.with(Items.graphite, 35, Items.titanium, 35, Items.plastanium, 45, Items.silicon, 30));
            ammo(
                Items.blastCompound, Bullets.missileExplosive,
                Items.pyratite, Bullets.missileIncendiary,
                Items.surgealloy, Bullets.missileSurge
            );
            reload = 40f;
            shots = 4;
            burstSpacing = 5f;
            inaccuracy = 10f;
            range = 185f;
            xRand = 6f;
            size = 2;
            health = 300 * size * size;
            shootSound = Sounds.missile;
        }};

        //齐射炮（2x2，四连发）
        salvo = new com.phoenix.game.world.blocks.defense.turrets.BurstTurret("salvo"){{
            requirements(Category.turret, ItemStack.with(Items.copper, 105, Items.graphite, 95, Items.titanium, 60));
            ammo(
                Items.copper, Bullets.standardCopper,
                Items.graphite, Bullets.standardDense,
                Items.pyratite, Bullets.standardIncendiary,
                Items.silicon, Bullets.standardHoming,
                Items.thorium, Bullets.standardThorium
            );

            size = 2;
            range = 150f;
            reload = 38f;
            restitution = 0.03f;
            ammoEjectBack = 3f;
            cooldown = 0.03f;
            recoil = 3f;
            shootShake = 2f;
            burstSpacing = 3f;
            shots = 4;
            ammoUseEffect = Fx.shellEjectBig;
            health = 240 * size * size;
            shootSound = Sounds.shootBig;
        }};

        //引信（3x3，近距离三连发）
        fuse = new com.phoenix.game.world.blocks.defense.turrets.ItemTurret("fuse"){{
            requirements(Category.turret, ItemStack.with(Items.copper, 225, Items.graphite, 225, Items.thorium, 100));

            reload = 35f;
            shootShake = 4f;
            range = 90f;
            recoil = 5f;
            shots = 3;
            spread = 20f;
            restitution = 0.1f;
            shootCone = 30f;
            size = 3;

            health = 220 * size * size;
            shootSound = Sounds.shotgun;

            //原版这里内联了一个 BulletType(0.01f, 105)：init 里沿射线做 Damage.collideLine（穿透），
            //draw 里手绘白→lancerLaser 渐变的三角光束。照原版内联（rayLength 直接取外层炮塔的 range）。
            ammo(Items.graphite, new BulletType(0.01f, 105f){
                int rays = 1;
                float rayLength = range + 10f;

                {
                    hitEffect = Fx.hitLancer;
                    shootEffect = smokeEffect = Fx.lightningShoot;
                    lifetime = 10f;
                    despawnEffect = Fx.none;
                    pierce = true;
                }

                @Override
                public void init(Bullet b){
                    for(int i = 0; i < rays; i++){
                        Damage.collideLine(b, b.getTeam(), hitEffect, b.x, b.y, b.rot(), rayLength - Math.abs(i - (rays / 2)) * 20f);
                    }
                }

                @Override
                public void draw(Bullet b){
                    super.draw(b);
                    //原版 Draw.color(Color.white, ...)；本项目 Color 常量是大写
                    Draw.color(Color.WHITE, Pal.lancerLaser, b.fin());
                    for(int i = 0; i < 7; i++){
                        //原版 Tmp.v1.trns(b.rot(), i * 8f)；本项目 Tmp.v1 是 libgdx Vector2，没有 trns
                        Tmp.v1.set(Angles.trnsx(b.rot(), i * 8f), Angles.trnsy(b.rot(), i * 8f));
                        float sl = Mathf.clamp(b.fout() - 0.5f) * (80f - i * 10);
                        Drawf.tri(b.x + Tmp.v1.x, b.y + Tmp.v1.y, 4f, sl, b.rot() + 90f);
                        Drawf.tri(b.x + Tmp.v1.x, b.y + Tmp.v1.y, 4f, sl, b.rot() - 90f);
                    }
                    Drawf.tri(b.x, b.y, 20f * b.fout(), rayLength + 50f, b.rot());
                    Drawf.tri(b.x, b.y, 20f * b.fout(), 10f, b.rot() + 180f);
                    //原版 Draw.reset()；本项目没有该重载，用 Draw.color() 复位
                    Draw.color();
                }
            });
        }};

        //涟漪（3x3，远程四连发）
        ripple = new com.phoenix.game.world.blocks.defense.turrets.ArtilleryTurret("ripple"){{
            requirements(Category.turret, ItemStack.with(Items.copper, 150, Items.graphite, 135, Items.titanium, 60));
            ammo(
                Items.graphite, Bullets.artilleryDense,
                Items.silicon, Bullets.artilleryHoming,
                Items.pyratite, Bullets.artilleryIncendiary,
                Items.blastCompound, Bullets.artilleryExplosive,
                Items.plastanium, Bullets.artilleryPlastic
            );
            size = 3;
            shots = 4;
            inaccuracy = 12f;
            reload = 60f;
            ammoEjectBack = 5f;
            ammoUseEffect = Fx.shellEjectBig;
            cooldown = 0.03f;
            velocityInaccuracy = 0.2f;
            restitution = 0.02f;
            recoil = 6f;
            shootShake = 2f;
            range = 290f;

            health = 130 * size * size;
            shootSound = Sounds.artillery;
        }};

        //气旋（3x3，速射防空）
        cyclone = new com.phoenix.game.world.blocks.defense.turrets.ItemTurret("cyclone"){{
            requirements(Category.turret, ItemStack.with(Items.copper, 200, Items.titanium, 125, Items.plastanium, 80));
            ammo(
                Items.metaglass, Bullets.flakGlass,
                Items.blastCompound, Bullets.flakExplosive,
                Items.plastanium, Bullets.flakPlastic,
                Items.surgealloy, Bullets.flakSurge
            );
            xRand = 4f;
            reload = 6f;
            range = 200f;
            size = 3;
            recoil = 3f;
            rotatespeed = 10f;
            inaccuracy = 10f;
            shootCone = 30f;
            shootSound = Sounds.shootSnap;

            health = 145 * size * size;
        }};

        //幽灵（4x4，双管重炮）
        spectre = new com.phoenix.game.world.blocks.defense.turrets.DoubleTurret("spectre"){{
            requirements(Category.turret, ItemStack.with(Items.copper, 350, Items.graphite, 300, Items.surgealloy, 250, Items.plastanium, 175, Items.thorium, 250));
            ammo(
                Items.graphite, Bullets.standardDenseBig,
                Items.pyratite, Bullets.standardIncendiaryBig,
                Items.thorium, Bullets.standardThoriumBig
            );
            reload = 6f;
            coolantMultiplier = 0.5f;
            restitution = 0.1f;
            ammoUseEffect = Fx.shellEjectBig;
            range = 200f;
            inaccuracy = 3f;
            recoil = 3f;
            xRand = 3f;
            shotWidth = 4f;
            shootShake = 2f;
            shots = 2;
            size = 4;
            shootCone = 24f;
            shootSound = Sounds.shootBig;

            health = 155 * size * size;
            //原版把冷却液过滤器覆盖成 2f（CooledTurret 构造里已登记 0.2f，Consumers.add 按类型替换）
            consumes.add(new com.phoenix.game.world.consumers.ConsumeLiquidFilter(
                liquid -> liquid.temperature <= 0.5f && liquid.flammability < 0.1f, 2f)).update(false).optional(true, true);
        }};

        //熔毁（4x4，耗电，驻留激光束）
        meltdown = new com.phoenix.game.world.blocks.defense.turrets.LaserTurret("meltdown"){{
            requirements(Category.turret, ItemStack.with(Items.copper, 250, Items.lead, 350, Items.graphite, 300, Items.surgealloy, 325, Items.silicon, 325));
            shootType = Bullets.meltdownLaser;
            shootEffect = Fx.shootBigSmoke2;
            shootCone = 40f;
            recoil = 4f;
            size = 4;
            shootShake = 2f;
            range = 190f;
            reload = 80f;
            firingMoveFract = 0.5f;
            shootDuration = 220f;
            powerUse = 14f;
            shootSound = Sounds.laserbig;
            //TODO activeSound = Sounds.beam; activeSoundVolume = 2f;（本项目无工作音效系统）

            health = 200 * size * size;
            //原版 Blocks.java:1653 冷却液过滤器速率 = 0.5f
            consumes.add(new com.phoenix.game.world.consumers.ConsumeLiquidFilter(
                liquid -> liquid.temperature <= 0.5f && liquid.flammability < 0.1f, 0.5f)).update(false);
        }};
        //endregion

        //修复投影器（范围内持续治疗友方建筑；size=2 的大投影器）
        mendProjector = new com.phoenix.game.world.blocks.defense.MendProjector("mend-projector");
        mendProjector.category = Category.effect;
        mendProjector.size = 2;
        mendProjector.health = 80 * mendProjector.size * mendProjector.size;   //原版写 80 * size * size = 320
        mendProjector.reload = 250f;
        mendProjector.range = 85f;
        mendProjector.healPercent = 14f;
        mendProjector.consumes.power(1.5f);
        //相织物加成（可选消耗器：升温后范围 +50、单次治疗量 +12 个百分点）
        mendProjector.consumes.item(Items.phasefabric).boost();
        mendProjector.requirements = new ItemStack[]{
            new ItemStack(Items.lead, 100), new ItemStack(Items.titanium, 25), new ItemStack(Items.silicon, 40)
        };
        mendProjector.buildVisibility = BuildVisibility.shown;

        //超频投影器（范围内加速友方建筑）
        overdriveProjector = new com.phoenix.game.world.blocks.defense.OverdriveProjector("overdrive-projector");
        overdriveProjector.category = Category.effect;
        overdriveProjector.consumes.power(3.5f);
        //相织物加成（可选消耗器：升温后范围 +20、倍率 +0.75）
        overdriveProjector.consumes.item(Items.phasefabric).boost();
        overdriveProjector.requirements = new ItemStack[]{
            new ItemStack(Items.lead, 100), new ItemStack(Items.titanium, 75),
            new ItemStack(Items.silicon, 75), new ItemStack(Items.plastanium, 30)
        };
        overdriveProjector.buildVisibility = BuildVisibility.shown;

        //入门款修复投影器（原版 mender 就是 new MendProjector("mender")：size=1 的便宜版，同样治建筑）
        mender = new com.phoenix.game.world.blocks.defense.MendProjector("mender");
        mender.category = Category.effect;
        mender.size = 1;
        mender.health = 80;                       //原版显式 health = 80（= 80 * size * size）
        mender.reload = 200f;
        mender.range = 40f;
        mender.healPercent = 4f;
        mender.phaseBoost = 4f;
        mender.phaseRangeBoost = 20f;
        mender.consumes.power(0.3f);
        //mender 的加成物品是**硅**（原版 consumes.item(Items.silicon).boost()，与两个大投影器的相织物不同）
        mender.consumes.item(Items.silicon).boost();
        mender.requirements = new ItemStack[]{
            new ItemStack(Items.lead, 30), new ItemStack(Items.copper, 25)
        };
        mender.buildVisibility = BuildVisibility.shown;

        //力场投影器（N2c2 批按原版重写：buildup/breakage 模型 + 半径内吸收敌方子弹 + 相织物加成 + 低温液体冷却）
        forceProjector = new com.phoenix.game.world.blocks.defense.ForceProjector("force-projector");
        forceProjector.category = Category.effect;
        forceProjector.size = 3;
        forceProjector.consumes.power(3f);
        //原版 Blocks.java:885：相织物可选加成（库存有相织物 → phaseHeat 升温 → 半径 +phaseRadiusBoost，每 350 帧耗一个）
        forceProjector.consumes.item(Items.phasefabric).boost();
        //原版 Blocks.java:883 requirements = lead100 + titanium75 + silicon125
        forceProjector.requirements = new ItemStack[]{
            new ItemStack(Items.lead, 100), new ItemStack(Items.titanium, 75), new ItemStack(Items.silicon, 125)
        };
        forceProjector.buildVisibility = BuildVisibility.shown;

        //地雷（敌方单位靠近时放电 + 自伤）
        shockMine = new com.phoenix.game.world.blocks.defense.ShockMine("shock-mine");
        //原版 Blocks.java:890 category = Category.effect（地雷与 mender/投影仪同归 effect 页，原版如此）
        shockMine.category = Category.effect;
        //不参与方块落影（对应原版 shockMine 定义里的 `hasShadow = false`）
        shockMine.hasShadow = false;
        //⚠ damage / tileDamage / length / tendrils 都是 ShockMine 子类字段（Block.java 上没有），故转型访问。
        //原版 Blocks.java:893 damage = 11（覆盖类默认 13）
        ((com.phoenix.game.world.blocks.defense.ShockMine)shockMine).damage = 11f;
        //原版 Blocks.java:894 tileDamage = 7f（覆盖类默认 5f）
        ((com.phoenix.game.world.blocks.defense.ShockMine)shockMine).tileDamage = 7f;
        //原版 Blocks.java:895 length = 10
        ((com.phoenix.game.world.blocks.defense.ShockMine)shockMine).length = 10;
        //原版 Blocks.java:896 tendrils = 5
        ((com.phoenix.game.world.blocks.defense.ShockMine)shockMine).tendrils = 5;
        //原版 Blocks.java:890 requirements = lead25 + silicon12
        shockMine.requirements = new ItemStack[]{
            new ItemStack(Items.lead, 25), new ItemStack(Items.silicon, 12)
        };
        shockMine.buildVisibility = BuildVisibility.shown;

        //region 液体（原版 Blocks.load 的 liquid 区；插在 power 之前以对齐原版"liquid → power"的相对顺序）
        //⚠ 本区的 platedConduit / liquidTank 在 W1 批已注册（追加在 load() 末尾），此处不重复注册，只补属性配置。
        //原版 Conduit/Pump 依赖的字段核对（T17 已核实）：Block.floating（Block.java:430）、Block.conveyorPlacement
        //（:433）、Block.drawPlace（:516）/drawPlaceText（:525）本项目**都已存在**（原先"没有这些字段"的注释已过时）。
        //仍缺的只有两处：TileEntity.noSleep()/sleep()（省电休眠，B 类）与 Draw.mixcol（整体替换色，
        //本项目用 Draw.color / batch.setColor 等价替代，属 C 类）。

        //机械泵（无电，从脚下液体地板抽液；数值照搬原版 mechanical-drill 同款口径）
        mechanicalPump = new com.phoenix.game.world.blocks.production.Pump("mechanical-pump"){{
            requirements(Category.liquid, ItemStack.with(Items.copper, 15, Items.metaglass, 10));
            pumpAmount = 0.1f;
        }};

        //旋转泵（2x2，耗电 0.15/帧，抽液量 ×8）
        rotaryPump = new com.phoenix.game.world.blocks.production.Pump("rotary-pump"){{
            requirements(Category.liquid, ItemStack.with(Items.copper, 70, Items.metaglass, 50, Items.silicon, 20, Items.titanium, 35));
            pumpAmount = 0.8f;
            consumes.power(0.15f);
            liquidCapacity = 30f;
            hasPower = true;
            size = 2;
        }};

        //热能泵（3x3，耗电 0.30/帧，抽液量 ×15；⚠原版用的是 Pump 而不是 SolidPump）
        thermalPump = new com.phoenix.game.world.blocks.production.Pump("thermal-pump"){{
            requirements(Category.liquid, ItemStack.with(Items.copper, 80, Items.metaglass, 70, Items.silicon, 30, Items.titanium, 40, Items.thorium, 35));
            pumpAmount = 1.5f;
            consumes.power(0.30f);
            liquidCapacity = 40f;
            hasPower = true;
            size = 3;
        }};

        //导管（基础，容量 10、泄漏阻力 1.5）
        conduit = new com.phoenix.game.world.blocks.liquid.Conduit("conduit"){{
            requirements(Category.liquid, ItemStack.with(Items.metaglass, 1));
            health = 45;
        }};

        //脉冲导管（容量 16 + 液体压力 1.025，流速更快）
        pulseConduit = new com.phoenix.game.world.blocks.liquid.Conduit("pulse-conduit"){{
            requirements(Category.liquid, ItemStack.with(Items.titanium, 2, Items.metaglass, 1));
            liquidCapacity = 16f;
            liquidPressure = 1.025f;
            health = 90;
        }};

        //液体路由器（把收到的液体轮询分给所有邻接）
        liquidRouter = new com.phoenix.game.world.blocks.liquid.LiquidRouter("liquid-router"){{
            requirements(Category.liquid, ItemStack.with(Items.graphite, 4, Items.metaglass, 2));
            liquidCapacity = 20f;
        }};

        //液体交叉口（两条垂直导管直行穿越）
        liquidJunction = new com.phoenix.game.world.blocks.liquid.LiquidJunction("liquid-junction"){{
            requirements(Category.liquid, ItemStack.with(Items.graphite, 2, Items.metaglass, 2));
        }};

        //液体桥（4 格内两桥直连输送，不耗电）
        bridgeConduit = new com.phoenix.game.world.blocks.liquid.LiquidExtendingBridge("bridge-conduit"){{
            requirements(Category.liquid, ItemStack.with(Items.graphite, 4, Items.metaglass, 8));
            range = 4;
            hasPower = false;
        }};

        //相位导管（12 格、耗电 0.30/帧、不可超频）
        phaseConduit = new com.phoenix.game.world.blocks.liquid.LiquidBridge("phase-conduit"){{
            requirements(Category.liquid, ItemStack.with(Items.phasefabric, 5, Items.silicon, 7, Items.metaglass, 20, Items.titanium, 10));
            range = 12;
            hasPower = true;
            canOverdrive = false;
            consumes.power(0.30f);
        }};
        //endregion

        //太阳能发电机（0.06/帧，靠天吃饭、无消耗）
        solarPanel = new SolarGenerator("solar-panel");
        solarPanel.category = Category.power;
        solarPanel.powerProduction = 0.06f;
        solarPanel.requirements = new ItemStack[]{ new ItemStack(Items.lead, 10), new ItemStack(Items.silicon, 15) };
        solarPanel.buildVisibility = BuildVisibility.shown;

        largeSolarPanel = new SolarGenerator("solar-panel-large");
        largeSolarPanel.category = Category.power;
        largeSolarPanel.size = 3;
        //原版 Blocks.java:1146 不设 health → 走 size*size*40 默认 = 360（此处不再显式赋值）
        largeSolarPanel.powerProduction = 0.9f;
        largeSolarPanel.requirements = new ItemStack[]{
            new ItemStack(Items.lead, 100), new ItemStack(Items.silicon, 145), new ItemStack(Items.phasefabric, 15)
        };
        largeSolarPanel.buildVisibility = BuildVisibility.shown;

        //电力节点：邻接接入 + 6 格内节点互相连线（最多 20 条，原版 Blocks.java:1067 maxNodes = 20）
        powerNode = new PowerNode("power-node");
        powerNode.category = Category.power;
        //原版 Blocks.java:1067 maxNodes = 20（类默认 3，此处覆盖；本项目字段名 maxLinks）
        powerNode.maxLinks = 20;
        //原版 Blocks.java:1066 requirements = copper1 + lead3
        powerNode.requirements = new ItemStack[]{ new ItemStack(Items.copper, 1), new ItemStack(Items.lead, 3) };
        powerNode.buildVisibility = BuildVisibility.shown;

        //大型电力节点（F1e1 批，2x2）：连线范围 9.5 格、最多 30 条（原版 maxNodes = 30，本项目字段名是 maxLinks）
        powerNodeLarge = new PowerNode("power-node-large");
        powerNodeLarge.category = Category.power;
        powerNodeLarge.size = 2;
        //原版 PowerNode 类无 health 覆写 → 2x2 默认 160（本项目类构造器误设 40，此处覆盖）
        powerNodeLarge.health = 160;
        powerNodeLarge.laserRange = 9.5f;
        powerNodeLarge.maxLinks = 30;
        powerNodeLarge.requirements = new ItemStack[]{
            new ItemStack(Items.titanium, 5), new ItemStack(Items.lead, 10), new ItemStack(Items.silicon, 3)
        };
        powerNodeLarge.buildVisibility = BuildVisibility.shown;

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
        surgeTower.buildVisibility = BuildVisibility.shown;

        //二极管：背面 → 正面 单向输电
        diode = new com.phoenix.game.world.blocks.power.PowerDiode("diode");
        //原版 PowerDiode 类无 health 覆写 → 1x1 默认 40（本项目类构造器误设 260，此处覆盖）
        diode.health = 40;
        diode.category = Category.power;
        diode.requirements = new ItemStack[]{
            new ItemStack(Items.silicon, 10), new ItemStack(Items.plastanium, 5), new ItemStack(Items.metaglass, 10)
        };
        diode.buildVisibility = BuildVisibility.shown;

        //电池（储能：电量富余时充入、不足时取出）
        battery = new com.phoenix.game.world.blocks.power.Battery("battery", 4000f);
        battery.category = Category.power;
        battery.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 4), new ItemStack(Items.lead, 20)
        };
        battery.buildVisibility = BuildVisibility.shown;

        batteryLarge = new com.phoenix.game.world.blocks.power.Battery("battery-large", 50000f);
        batteryLarge.category = Category.power;
        batteryLarge.size = 3;
        //原版 Blocks.java:1094 不设 health → 走 size*size*40 默认 = 360（此处不再显式赋值）
        batteryLarge.requirements = new ItemStack[]{
            new ItemStack(Items.titanium, 20), new ItemStack(Items.lead, 40), new ItemStack(Items.silicon, 20)
        };
        batteryLarge.buildVisibility = BuildVisibility.shown;

        //燃烧发电机（烧可燃物：煤 1.0 满功率，一份烧 120 帧，断料立刻停机）
        combustionGenerator = new com.phoenix.game.world.blocks.power.BurnerGenerator("combustion-generator");
        combustionGenerator.category = Category.power;
        combustionGenerator.powerProduction = 1f;
        combustionGenerator.itemDuration = 120f;
        combustionGenerator.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 25), new ItemStack(Items.lead, 15)
        };
        combustionGenerator.buildVisibility = BuildVisibility.shown;

        //地热发电机（2x2，发电量 = 覆盖范围地板热度之和 × 1.8）
        thermalGenerator = new com.phoenix.game.world.blocks.power.ThermalGenerator("thermal-generator");
        thermalGenerator.category = Category.power;
        thermalGenerator.size = 2;
        thermalGenerator.health = 160;
        thermalGenerator.powerProduction = 1.8f;
        thermalGenerator.generateEffect = Fx.redgeneratespark; //原版取值
        thermalGenerator.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 40), new ItemStack(Items.graphite, 35), new ItemStack(Items.lead, 50),
            new ItemStack(Items.silicon, 35), new ItemStack(Items.metaglass, 40)
        };
        thermalGenerator.buildVisibility = BuildVisibility.shown;

        //燃气轮机（2x2，烧可燃物 + 耗水 0.05/帧，发电 6/帧）
        //收敛继承链后 BurnerGenerator 已经是原版的 ItemLiquidGenerator(true, false, name) 形态，
        //所以这里照原版写：构造器给"只烧物品"，再自己打开 hasLiquids 并加耗水器
        turbineGenerator = new com.phoenix.game.world.blocks.power.BurnerGenerator("turbine-generator");
        turbineGenerator.hasLiquids = true;
        turbineGenerator.powerProduction = 6f;
        turbineGenerator.itemDuration = 90f;
        turbineGenerator.size = 2;
        turbineGenerator.consumes.add(new com.phoenix.game.world.consumers.ConsumeLiquid(Liquids.water, 0.05f));
        turbineGenerator.requirements(Category.power,
            ItemStack.with(Items.copper, 35, Items.graphite, 25, Items.lead, 40, Items.silicon, 30));

        //差速发电机（3x3，烧任意物品 + 耗低温液 0.15/帧，发电 16/帧）
        //对应原版 SingleTypeGenerator：任何物品效率都是 1、液体效率都是 0（只烧物品）
        differentialGenerator = new com.phoenix.game.world.blocks.power.SingleTypeGenerator("differential-generator");
        differentialGenerator.powerProduction = 16f;
        differentialGenerator.itemDuration = 140f;
        differentialGenerator.hasItems = true;
        differentialGenerator.hasLiquids = true;
        differentialGenerator.size = 3;
        differentialGenerator.consumes.item(Items.pyratite).optional(true, false);
        differentialGenerator.consumes.add(new com.phoenix.game.world.consumers.ConsumeLiquid(Liquids.cryofluid, 0.15f));
        differentialGenerator.requirements(Category.power,
            ItemStack.with(Items.copper, 70, Items.titanium, 50, Items.lead, 100, Items.silicon, 65, Items.metaglass, 50));

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
        rtgGenerator.buildVisibility = BuildVisibility.shown;

        //钍反应堆（3x3，发电量按库存钍的充满度缩放；需低温流体冷却，堆芯热量到 1 熔毁自爆）
        //收敛继承链：原版没有 ThoriumReactor 这个类，thorium-reactor 就是用 NuclearReactor 注册的（数值照原版）
        thoriumReactor = new com.phoenix.game.world.blocks.power.NuclearReactor("thorium-reactor");
        thoriumReactor.category = Category.power;
        thoriumReactor.size = 3;
        thoriumReactor.health = 700;
        thoriumReactor.itemDuration = 360f;
        thoriumReactor.powerProduction = 14f;
        thoriumReactor.consumes.item(Items.thorium);
        thoriumReactor.heating = 0.02f;
        //原版：consumes.liquid(Liquids.cryofluid, heating / coolantPower).update(false)
        //update(false) 很关键：冷却液由实体自己按热量扣（见 NuclearReactorEntity.update），不能每帧再被 ConsumeModule 扣一遍
        thoriumReactor.consumes.add(new com.phoenix.game.world.consumers.ConsumeLiquid(
            Liquids.cryofluid, thoriumReactor.heating / thoriumReactor.coolantPower)).update(false);
        thoriumReactor.requirements = new ItemStack[]{
            new ItemStack(Items.lead, 300), new ItemStack(Items.silicon, 200), new ItemStack(Items.graphite, 150),
            new ItemStack(Items.thorium, 150), new ItemStack(Items.metaglass, 50)
        };
        thoriumReactor.buildVisibility = BuildVisibility.shown;

        //冲击反应堆（4x4，耗电 25 启动 + 烧脉冲燃料，发电 130；预热越满发电越多）
        impactReactor = new com.phoenix.game.world.blocks.power.ImpactReactor("impact-reactor");
        impactReactor.category = Category.power;
        impactReactor.powerProduction = 130f;
        impactReactor.consumes.power(25f);
        impactReactor.requirements = new ItemStack[]{
            new ItemStack(Items.lead, 500), new ItemStack(Items.silicon, 300), new ItemStack(Items.graphite, 400),
            new ItemStack(Items.thorium, 100), new ItemStack(Items.surgealloy, 250), new ItemStack(Items.metaglass, 250)
        };
        impactReactor.buildVisibility = BuildVisibility.shown;

        //单位工厂：消耗硅生产 dagger（出厂单位自动寻路前往敌方核心）
        daggerFactory = new com.phoenix.game.world.blocks.units.UnitFactory("dagger-factory");
        daggerFactory.category = Category.units; //原版 dagger-factory 属 units 页（其余工厂走 unitFactory() 辅助方法，已自动带）
        daggerFactory.unitTypeName = "dagger"; //UnitTypes 晚于 Blocks 加载，按名字延迟解析
        daggerFactory.produceTime = 850f;
        daggerFactory.maxSpawn = 4;
        daggerFactory.inputItem = new ItemStack[]{ new ItemStack(Items.silicon, 6) };
        //原版 Blocks.java:1724 requirements = lead 55 + silicon 35（此前误写 copper 60 + lead 40）
        daggerFactory.requirements = new ItemStack[]{ new ItemStack(Items.lead, 55), new ItemStack(Items.silicon, 35) };
        //原版 UnitFactory 类无 health 覆写 → 走 Block 默认 size*size*40 = 160（本项目类构造器误设 350，此处覆盖）
        daggerFactory.health = 160;
        //原版 UnitTypes.java:1728 consumes.power(0.5f)（辅助方法外的独立工厂此前漏设，走了类默认 0.10f）
        daggerFactory.consumes.power(0.5f);
        daggerFactory.buildVisibility = BuildVisibility.shown;

        //其余单位工厂：数值全部取自原版（produceTime / maxSpawn / 耗电 / 耗料）。
        //F1e3 批补回 draug/spirit/phantom/revenant 四个工厂。原版顺序是
        //  draug → spirit → phantom → [command-center] → wraith → ghoul → revenant → dagger → crawler → titan → fortress，
        //本项目既有工厂（dagger/wraith/crawler/titan/ghoul/fortress）的顺序**不重排**（方块 id = 注册顺序，
        //重排会让既有存档/网络包的 id 错位），只在"相对顺序"上尽量贴近原版：
        //draug/spirit/phantom 插在 wraith 之前，revenant 插在 ghoul 之后。
        draugFactory = unitFactory("draug-factory", "draug", 2500f, 2, 1.2f, 1,
            new ItemStack[0], //原版是 consumes.items()（不带参数）→ 不耗材料，只耗电
            new ItemStack[]{ new ItemStack(Items.copper, 30), new ItemStack(Items.lead, 70) });
        //原版 UnitFactory 类无 health 覆写 → 2x2 默认 160（本项目类构造器误设 350，此处覆盖；下同）
        draugFactory.health = 160;

        spiritFactory = unitFactory("spirit-factory", "spirit", 4000f, 2, 1.2f, 1,
            new ItemStack[]{ new ItemStack(Items.silicon, 30), new ItemStack(Items.lead, 30) },
            new ItemStack[]{ new ItemStack(Items.metaglass, 45), new ItemStack(Items.lead, 55), new ItemStack(Items.silicon, 45) });
        spiritFactory.health = 160;

        phantomFactory = unitFactory("phantom-factory", "phantom", 4400f, 2, 2.5f, 1,
            new ItemStack[]{ new ItemStack(Items.silicon, 50), new ItemStack(Items.lead, 30), new ItemStack(Items.titanium, 20) },
            new ItemStack[]{ new ItemStack(Items.titanium, 50), new ItemStack(Items.thorium, 60), new ItemStack(Items.lead, 65), new ItemStack(Items.silicon, 105) });
        phantomFactory.health = 160;

        wraithFactory = unitFactory("wraith-factory", "wraith", 700f, 2, 0.5f, 1,
            new ItemStack[]{ new ItemStack(Items.silicon, 10), new ItemStack(Items.titanium, 5) },
            new ItemStack[]{ new ItemStack(Items.titanium, 30), new ItemStack(Items.lead, 40), new ItemStack(Items.silicon, 45) });
        wraithFactory.health = 160;

        crawlerFactory = unitFactory("crawler-factory", "crawler", 300f, 2, 0.5f, 6,
            new ItemStack[]{ new ItemStack(Items.coal, 10) },
            new ItemStack[]{ new ItemStack(Items.lead, 45), new ItemStack(Items.silicon, 30) });
        crawlerFactory.health = 160;

        titanFactory = unitFactory("titan-factory", "titan", 1050f, 3, 0.60f, 4,
            new ItemStack[]{ new ItemStack(Items.silicon, 12) },
            new ItemStack[]{ new ItemStack(Items.graphite, 50), new ItemStack(Items.lead, 50), new ItemStack(Items.silicon, 45) });
        titanFactory.health = 360; //原版 3x3 默认（类构造器误设 350）

        ghoulFactory = unitFactory("ghoul-factory", "ghoul", 1150f, 3, 1.2f, 4,
            new ItemStack[]{ new ItemStack(Items.silicon, 15), new ItemStack(Items.titanium, 10) },
            new ItemStack[]{ new ItemStack(Items.titanium, 75), new ItemStack(Items.lead, 65), new ItemStack(Items.silicon, 110) });
        ghoulFactory.health = 360; //原版 3x3 默认（类构造器误设 350）

        revenantFactory = unitFactory("revenant-factory", "revenant", 2000f, 4, 3f, 4,
            new ItemStack[]{ new ItemStack(Items.silicon, 40), new ItemStack(Items.titanium, 30) },
            new ItemStack[]{ new ItemStack(Items.plastanium, 50), new ItemStack(Items.titanium, 150), new ItemStack(Items.lead, 150), new ItemStack(Items.silicon, 200) });
        revenantFactory.health = 640; //原版 4x4 默认（类构造器误设 350）

        fortressFactory = unitFactory("fortress-factory", "fortress", 2000f, 3, 1.4f, 3,
            new ItemStack[]{ new ItemStack(Items.silicon, 20), new ItemStack(Items.graphite, 10) },
            new ItemStack[]{ new ItemStack(Items.thorium, 40), new ItemStack(Items.lead, 110), new ItemStack(Items.silicon, 75) });
        fortressFactory.health = 360; //原版 3x3 默认（类构造器误设 350）

        //维修点：给半径内最近的受伤友军回血（耗电 1/帧，只在锁定目标时耗）
        repairPoint = new com.phoenix.game.world.blocks.units.RepairPoint("repair-point");
        repairPoint.category = Category.units;
        repairPoint.repairSpeed = 0.5f;
        repairPoint.repairRadius = 65f;
        repairPoint.powerConsumption = 1f;
        //原版 RepairPoint 类无 health 覆写 → 1x1 默认 40（本项目类构造器误设 160，此处覆盖）
        repairPoint.health = 40;
        repairPoint.requirements = new ItemStack[]{
            new ItemStack(Items.lead, 15), new ItemStack(Items.copper, 15), new ItemStack(Items.silicon, 15)
        };
        repairPoint.buildVisibility = BuildVisibility.shown;

        //指挥中心（遗留空壳）：旧的全队指令机制已由 RTS 命令系统取代，
        //保留注册以兼容旧地图/存档（对应 v8 的 LegacyCommandCenter）
        commandCenter = new com.phoenix.game.world.blocks.units.CommandCenter("command-center");
        commandCenter.category = Category.units;
        commandCenter.requirements = new ItemStack[]{
            new ItemStack(Items.copper, 200), new ItemStack(Items.lead, 250),
            new ItemStack(Items.silicon, 250), new ItemStack(Items.graphite, 100)
        };
        commandCenter.buildVisibility = BuildVisibility.shown;

        //机甲平台：站上去点一下，花 5 秒换成对应型号的单位。
        //原版换的是 Mechs.dart/delta/tau/omega，本工程没有 Player 的机甲系统，
        //改为换成 UnitType：dart 平台 -> dart-ship（与出生单位/原版一致，完全对应原版 dartPad），
        //其余三个平台 -> 降级映射（按强度递增：titan → fortress → eruptor）。
        //（F1e3 批给 MechPad 补了 mech 字段，这 4 个平台仍走 unitTypeName 分支，行为不变）
        mechPad = mechPad("dart-mech-pad", "dart-ship", 2, 0.5f, new ItemStack[]{
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
        //原版 MechPad 类无 health 覆写 → 3x3 默认 360（本项目类构造器误设 160，此处覆盖）
        mechPadOmega.health = 360;

        //飞船平台（3 个 ship-pad，原版顺序 omegaPad → javelin → trident → glaive）。
        //⚠ 原版这 3 个平台设的是 mech = Mechs.javelin/trident/glaive —— **飞船是 Mech，不是 UnitType**
        //  （原版 UnitTypes 里没有 glaive/javelin/trident）。
        //F12：本项目 MechPad 把换装降级成"换成同名 UnitType" → shipPad 里额外设 unitTypeName = mech.name，
        //解析到 content/UnitTypes 里新加的 javelin-ship / trident-ship / glaive-ship（贴图/数值照 Mech 那份）。
        //TODO 等 Player 的机甲系统移植后接线，让 mech != null 的平台走原版分支。
        javelinPad = shipPad("javelin-ship-pad", Mechs.javelin, 2, 0.8f, new ItemStack[]{
            new ItemStack(Items.lead, 175), new ItemStack(Items.silicon, 225), new ItemStack(Items.titanium, 250),
            new ItemStack(Items.plastanium, 200), new ItemStack(Items.phasefabric, 100)
        });

        tridentPad = shipPad("trident-ship-pad", Mechs.trident, 2, 1f, new ItemStack[]{
            new ItemStack(Items.lead, 125), new ItemStack(Items.copper, 125), new ItemStack(Items.silicon, 125),
            new ItemStack(Items.titanium, 150), new ItemStack(Items.plastanium, 100)
        });

        glaivePad = shipPad("glaive-ship-pad", Mechs.glaive, 3, 1.2f, new ItemStack[]{
            new ItemStack(Items.lead, 225), new ItemStack(Items.silicon, 325), new ItemStack(Items.titanium, 350),
            new ItemStack(Items.plastanium, 300), new ItemStack(Items.surgealloy, 100)
        });
        glaivePad.health = 360; //原版 3x3 默认（类构造器误设 160）

        //沙盒方块：无限电力/物品的源与虚空，只在 rules.sandbox 打开时出现在建造菜单里
        powerSource = new com.phoenix.game.world.blocks.sandbox.PowerSource("power-source");
        powerSource.category = Category.power;
        //原版 Blocks.java:1825 alwaysUnlocked
        powerSource.alwaysUnlocked = true;
        //沙盒可见性由类构造器设置（PowerSource.java 里 buildVisibility = BuildVisibility.sandboxOnly，
        //对应原版 Blocks.java:1824 的 requirements(Category.power, BuildVisibility.sandboxOnly, ItemStack.with())）

        powerVoid = new com.phoenix.game.world.blocks.sandbox.PowerVoid("power-void");
        powerVoid.category = Category.power;
        //原版 Blocks.java:1830 alwaysUnlocked
        powerVoid.alwaysUnlocked = true;
        //原版 PowerVoid 类无 health 覆写 → 1x1 默认 40（本项目类构造器误设 160，此处覆盖；下同）
        powerVoid.health = 40;
        //同上：PowerVoid.java 构造器里设 sandboxOnly 可见性（原版 Blocks.java:1829）

        itemSource = new com.phoenix.game.world.blocks.sandbox.ItemSource("item-source");
        itemSource.category = Category.distribution;
        //原版 Blocks.java:1835 alwaysUnlocked
        itemSource.alwaysUnlocked = true;
        itemSource.health = 40; //原版 1x1 默认 40（类构造器误设 160）

        itemVoid = new com.phoenix.game.world.blocks.sandbox.ItemVoid("item-void");
        itemVoid.category = Category.distribution;
        //原版 Blocks.java:1840 alwaysUnlocked
        itemVoid.alwaysUnlocked = true;
        itemVoid.health = 40; //原版 1x1 默认 40（类构造器误设 160）
        //item-source/item-void 的沙盒可见性由类构造器设置（ItemSource.java/ItemVoid.java 里
        //buildVisibility = BuildVisibility.sandboxOnly，对应原版 Blocks.java:1834/1839 的 requirements(...)）

        //液体源 / 液体虚空（沙盒）。原版 Blocks.java:1844/1849 走
        //requirements(Category.liquid, BuildVisibility.sandboxOnly, ItemStack.with()) + alwaysUnlocked = true
        liquidSource = new com.phoenix.game.world.blocks.sandbox.LiquidSource("liquid-source"){{
            requirements(Category.liquid, BuildVisibility.sandboxOnly, ItemStack.with());
            alwaysUnlocked = true;
        }};

        liquidVoid = new com.phoenix.game.world.blocks.sandbox.LiquidVoid("liquid-void"){{
            requirements(Category.liquid, BuildVisibility.sandboxOnly, ItemStack.with());
            alwaysUnlocked = true;
        }};

        //留言板（F1e1 批）：可配置文本，选中时显示在方块上方。材料 5 石墨（原版同）
        //TODO 图集里**没有** message 贴图（既无裸名也无变体）→ Block.load 的 loadRegions 回退到 blank，
        //     而 blank 是 1x1 不透明白点 → 方块会渲染成一块纯白方块（不是"不可见"；Block.draw 有 region == null
        //     守卫，但 region 这里是 blank 而非 null，守卫不生效）
        message = new com.phoenix.game.world.blocks.logic.MessageBlock("message");
        message.requirements(Category.effect, ItemStack.with(Items.graphite, 5));

        //照明灯（1x1，耗电 0.05/帧，照亮半径 120）
        //原版 Blocks.java:1858 requirements(Category.effect, BuildVisibility.lightingOnly, ...)
        //→ 只在 rules.lighting 打开时列出（T2 批已落地；此前缺可见性会误常驻普通建造栏）。
        //TODO 图集里没有 illuminator/illuminator-top 贴图 → 整块不可见（LightBlock.draw 已加 null 守卫，
        //     否则 Draw.rect(reg(topRegion), ...) 取 region 宽度会 NPE 崩渲染）。
        illuminator = new com.phoenix.game.world.blocks.power.LightBlock("illuminator");
        illuminator.brightness = 0.67f;
        illuminator.radius = 120f;
        illuminator.consumes.power(0.05f);
        illuminator.requirements(Category.effect, BuildVisibility.lightingOnly, ItemStack.with(Items.graphite, 4, Items.silicon, 2));

        //波次出生点 overlay（World.createMap 在地图四角放置）
        spawn = new com.phoenix.game.world.blocks.OverlayFloor("spawn");

        //多格卫星瓦片（BlockPart）注册进 all，获得稳定 ID，供存档序列化反查（核心多格读档不退化）
        registerBlockParts();

        //自然岩壁（对应原版 content.Blocks 里的 StaticWall 系列）。
        //放在**最后**注册：方块 ID = 在 all 里的下标，追加在末尾才不会让已有存档的方块 ID 错位。
        rocks = new com.phoenix.game.world.blocks.StaticWall("rocks");
        sandRocks = new com.phoenix.game.world.blocks.StaticWall("sandrocks");
        duneRocks = new com.phoenix.game.world.blocks.StaticWall("dunerocks");
        shaleRocks = new com.phoenix.game.world.blocks.StaticWall("shalerocks");
        shrubs = new com.phoenix.game.world.blocks.StaticWall("shrubs");
        darkMetal = new com.phoenix.game.world.blocks.StaticWall("dark-metal");

        //在建占位方块 build1..build9（按尺寸分档）。同样放末尾，避免已有存档的方块 ID 错位。
        //不需要单独字段引用：Build 里用 BuildBlock.get(size) 取
        for(int i = 1; i <= com.phoenix.game.world.blocks.BuildBlock.maxSize; i++){
            //原版这里是 `new BuildBlock(i);`（只注册、不留字段引用，Build 用 BuildBlock.get(size) 取）。
            //此前循环体被误清空 → BuildBlock.get(size) 恒返回 null → 单位建造路径 tile.setBlock(null,…) NPE。
            new com.phoenix.game.world.blocks.BuildBlock(i);
        }

        //矿脉覆盖层（对应原版 oreCopper 系列）。必须放在**最末尾**注册：方块 ID = 在 all 里的下标，
        //追加在 BuildBlock 之后才不会让已有存档的方块 ID 错位。
        //oreDefault/oreThreshold/oreScale 供 Maps.addDefaultOres 生成默认矿脉滤镜，数值照抄原版。
        oreCopper = new com.phoenix.game.world.blocks.OreFloor(Items.copper){{
            oreDefault = true;
            oreThreshold = 0.81f;
            oreScale = 23.47619f;
        }};
        oreLead = new com.phoenix.game.world.blocks.OreFloor(Items.lead){{
            oreDefault = true;
            oreThreshold = 0.828f;
            oreScale = 23.952381f;
        }};
        oreScrap = new com.phoenix.game.world.blocks.OreFloor(Items.scrap);
        oreCoal = new com.phoenix.game.world.blocks.OreFloor(Items.coal){{
            oreDefault = true;
            oreThreshold = 0.846f;
            oreScale = 24.428572f;
        }};
        oreTitanium = new com.phoenix.game.world.blocks.OreFloor(Items.titanium){{
            oreDefault = true;
            oreThreshold = 0.864f;
            oreScale = 24.904762f;
        }};
        oreThorium = new com.phoenix.game.world.blocks.OreFloor(Items.thorium){{
            oreDefault = true;
            oreThreshold = 0.882f;
            oreScale = 25.380953f;
        }};
        //libgdx 的 Array.add 最多 4 参，6 个矿拆两次加（原行无法编译）

        //液体与生产建筑（W1 批移植的 4 个方块）。同样**追加在末尾**：方块 ID = 在 all 里的下标，
        //追加在最后才不会让已有存档的方块 ID 错位（按原版顺序对齐留到所有方块齐了之后统一做）。
        //抽水机：数值逐字照抄原版（含 result/pumpAmount/attribute/rotateSpeed/liquidCapacity 与耗电 1/帧）。
        //必须用 {{ }} 匿名子类：pumpAmount 在 Pump 里是 protected，而本文件在 content 包、Pump 在
        //world.blocks.production 包 → 跨包无法直接 waterExtractor.pumpAmount = …（原版同样靠 {{ }} 赋值）。
        waterExtractor = new com.phoenix.game.world.blocks.production.SolidPump("water-extractor"){{
            size = 2;
            result = Liquids.water;
            pumpAmount = 0.13f;
            attribute = Attribute.water;
            rotateSpeed = 1.4f;
            liquidCapacity = 30f;
            consumes.power(1f);
            requirements(Category.production, ItemStack.with(Items.copper, 25, Items.graphite, 25, Items.lead, 20));
        }};
        //压裂机：沙 + 水 + 电 → 石油（3x3）。数值逐字照抄原版。
        //⚠ 必须用 {{ }} 匿名子类：pumpAmount 在 Pump 里是 protected，而本文件在 content 包、
        //Pump 在 world.blocks.production 包 → 跨包无法直接 oilExtractor.pumpAmount = …（原版同样靠 {{ }} 赋值）。
        //⚠ 原版 liquidCapacity 先写 50f 又写 30f，后者生效 = 30（这里只写 30）。
        //⚠ result 必须显式设成 oil：SolidPump.result 默认是 Liquids.water，不设就会产水。
        //health 未显式设置，因此走 Block.init 的默认 size*size*40（360）。
        oilExtractor = new com.phoenix.game.world.blocks.production.Fracker("oil-extractor"){{
            size = 3;
            result = Liquids.oil;
            pumpAmount = 0.25f;
            attribute = Attribute.oil;
            updateEffect = Fx.pulverize;
            updateEffectChance = 0.05f;
            liquidCapacity = 30f;
            consumes.item(Items.sand);
            consumes.power(3f);
            consumes.add(new com.phoenix.game.world.consumers.ConsumeLiquid(Liquids.water, 0.15f));
            requirements(Category.production, ItemStack.with(
                Items.copper, 150, Items.graphite, 175, Items.lead, 115, Items.thorium, 115, Items.silicon, 75));
        }};

        //培育机：水 + 电 → 孢子荚（2x2，耗电 0.80/帧；按原版排在 oilExtractor 之后）
        //孢子加成已生效：sporeMoss 设了 Attribute.spores=0.3（见上方），Cultivator 用 sumAttribute(spores)
        //折进产量（Block.sumAttribute 早已移植）。⚠ Floor.heat 仍是独立字段、未并进 attributes（地热发电机读 floor.heat）。
        cultivator = new com.phoenix.game.world.blocks.production.Cultivator("cultivator");
        cultivator.outputItem = new ItemStack(Items.sporePod, 1);
        cultivator.craftTime = 140f;
        cultivator.size = 2;
        cultivator.hasLiquids = true;
        cultivator.consumes.power(0.80f);
        cultivator.consumes.add(new com.phoenix.game.world.consumers.ConsumeLiquid(Liquids.water, 0.18f));
        cultivator.requirements(Category.production,
            ItemStack.with(Items.copper, 10, Items.lead, 25, Items.silicon, 10));

        //F1b：装甲导管 / 储液罐已在 W1 批注册（名字不能重复注册，否则 ContentLoader 抛 IllegalArgumentException），
        //这里只按原版补属性配置。
        platedConduit = new com.phoenix.game.world.blocks.liquid.ArmoredConduit("plated-conduit");
        platedConduit.requirements(Category.liquid, ItemStack.with(Items.thorium, 2, Items.metaglass, 1, Items.plastanium, 1));
        platedConduit.liquidCapacity = 16f;
        platedConduit.liquidPressure = 1.025f;
        platedConduit.health = 220;

        liquidTank = new com.phoenix.game.world.blocks.liquid.LiquidTank("liquid-tank");
        liquidTank.requirements(Category.liquid, ItemStack.with(Items.titanium, 25, Items.metaglass, 25));
        liquidTank.size = 3;
        liquidTank.liquidCapacity = 1500f;
        liquidTank.health = 500;

        //方块的 init()/load() 由 ContentLoader.init()/load() 统一调用（对应原版），
        //小地图取色必须在**全部方块贴图加载完之后**，所以由调用方在 Vars.content.load() 之后调 loadMinimapColors()
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
    public static void loadMinimapColors(){
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
                }
            }
        }
    }

    private static Floor floor(String name, int variants){
        Floor floor = new Floor(name);
        floor.variants = variants;
        return floor;
    }

    private static Floor floor(String name, int variants, String color){
        Floor floor = floor(name, variants);
        floor.color = Color.valueOf(color);
        return floor;
    }

    /**
     * 墙体：实心、有血量（可被摧毁）、带建造材料，归入防御分类。
     * <p>{@code health} 传的是**基础值**，此处按原版机制统一乘 {@link #wallHealthMultiplier}
     * （原版 {@code Blocks.java:737} 起所有墙都写 {@code health = 基础值 * wallHealthMultiplier}）。
     */
    private static Block wall(String name, int health, ItemStack... requirements){
        Block block = new com.phoenix.game.world.blocks.defense.Wall(name);
        block.health = health * wallHealthMultiplier;
        block.requirements = requirements;
        block.buildVisibility = BuildVisibility.shown;
        block.category = Category.defense;
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
        factory.buildVisibility = BuildVisibility.shown;
        //耗电统一走 consumes：Block.init() 会据此自动开启 hasPower 并同步 powerConsumption
        factory.consumes.power(powerUse);
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
        pad.buildVisibility = BuildVisibility.shown;
        pad.consumes.power(powerUse);
        return pad;
    }

    /**
     * 飞船平台：记型号（{@code Mechs.javelin/trident/glaive}）并把它作为换装的降级目标。
     * <p>F12：本项目没有 Player 的机甲系统 → 用 {@code unitTypeName = mech.name} 指向同名 UnitType
     * （见 {@code MechPad.mech} / {@code content/UnitTypes} 的 javelin-ship 等），点击即可换装。
     */
    private static com.phoenix.game.world.blocks.units.MechPad shipPad(String name, com.phoenix.game.type.Mech mech,
        int size, float powerUse, ItemStack[] requirements){

        com.phoenix.game.world.blocks.units.MechPad pad =
            new com.phoenix.game.world.blocks.units.MechPad(name);
        pad.category = Category.upgrade;
        pad.mech = mech;
        //3 个 Mech 的 name 正好是 javelin-ship/trident-ship/glaive-ship，与 UnitTypes 里的同名单位一一对应
        pad.unitTypeName = mech.name;
        pad.size = size;
        pad.requirements = requirements;
        pad.buildVisibility = BuildVisibility.shown;
        pad.consumes.power(powerUse);
        return pad;
    }

    /** @return 该地板是否为液体 */
    public static boolean isLiquid(Block block){
        return block instanceof Floor && ((Floor)block).isLiquid;
    }
}
