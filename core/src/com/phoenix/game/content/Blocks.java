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
import com.phoenix.game.world.blocks.production.Drill;
import com.phoenix.game.world.blocks.production.GenericCrafter;
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
    public static Block copperWall, titaniumWall, thoriumWall, metalWall;
    //自然岩壁（对应原版 StaticWall：实心、不可拆、小地图画自身颜色）
    public static Block rocks, sandRocks, duneRocks, shaleRocks, shrubs, darkMetal;
    //核心建筑
    public static Block core, coreFoundation, coreNucleus;
    //生产建筑
    public static Drill copperDrill;
    public static Drill leadDrill;
    public static Block siliconCrafter;
    //物流与仓储
    public static Conveyor conveyor, titaniumConveyor, armoredConveyor;
    public static Junction junction;
    public static Router router, distributor;
    public static Sorter sorter, invertedSorter;
    public static OverflowGate overflowGate, underflowGate;
    public static Block storage, vault, unloader;
    //防御建筑
    public static com.phoenix.game.world.blocks.defense.turrets.Turret duo;
    //单位工厂
    public static com.phoenix.game.world.blocks.units.UnitFactory daggerFactory;
    //发电建筑
    public static SolarGenerator solarPanel;
    public static PowerNode powerNode;
    //波次出生点标记（overlay，不绘制）
    public static Floor spawn;

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
        moss = floor("moss", 3, "54634a");
        metalFloor = floor("metal-floor", 0, "808594");

        water = floor("water", 0, "4a6fa5");
        water.isLiquid = true;
        water.speedMultiplier = 0.7f;
        water.dragMultiplier = 0.7f;
        water.drownTime = 60f * 10f;

        //墙体（有血量，可被子弹摧毁）
        copperWall = wall("copper-wall", 60, new ItemStack(Items.copper, 6));
        copperWall.category = Category.defense;
        titaniumWall = wall("titanium-wall", 80, new ItemStack(Items.copper, 6), new ItemStack(Items.lead, 4));
        titaniumWall.category = Category.defense;
        thoriumWall = wall("thorium-wall", 100, new ItemStack(Items.lead, 8), new ItemStack(Items.silicon, 4));
        thoriumWall.category = Category.defense;
        metalWall = wall("metalWall", 120, new ItemStack(Items.silicon, 8), new ItemStack(Items.lead, 6));
        metalWall.category = Category.defense;

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

        //铜钻头：采铜（贴图用图集里的 mechanical-drill）
        copperDrill = new Drill("mechanical-drill");
        copperDrill.category = Category.production;
        copperDrill.result = Items.copper;
        copperDrill.drillTime = 45f;
        copperDrill.requirements = new ItemStack[]{ new ItemStack(Items.copper, 12) };
        all.add(copperDrill);

        //铅钻头：采铅（硅炉需要铜+铅，没有铅就造不出硅，工厂也就出不了兵）
        leadDrill = new Drill("pneumatic-drill");
        leadDrill.category = Category.production;
        leadDrill.result = Items.lead;
        leadDrill.drillTime = 60f;
        leadDrill.powerConsumption = 0.08f;
        leadDrill.requirements = new ItemStack[]{ new ItemStack(Items.copper, 20), new ItemStack(Items.lead, 10) };
        all.add(leadDrill);

        //硅合成器：copper+lead -> silicon
        siliconCrafter = new GenericCrafter("silicon-smelter");
        siliconCrafter.category = Category.crafting;
        GenericCrafter crafter = (GenericCrafter)siliconCrafter;
        crafter.inputItem = new ItemStack[]{ new ItemStack(Items.copper, 1), new ItemStack(Items.lead, 1) };
        crafter.outputItem = new ItemStack(Items.silicon, 1);
        crafter.craftTime = 90f;
        crafter.requirements = new ItemStack[]{ new ItemStack(Items.copper, 30), new ItemStack(Items.lead, 25) };
        all.add(siliconCrafter);

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
        duo.shootEffect = Fx.shootSmall;
        duo.ammoItem = Items.copper;
        duo.requirements = new ItemStack[]{ new ItemStack(Items.copper, 35) };
        all.add(duo);

        //太阳能发电机 + 电力节点
        solarPanel = new SolarGenerator("solar-panel");
        solarPanel.category = Category.power;
        solarPanel.requirements = new ItemStack[]{ new ItemStack(Items.lead, 10), new ItemStack(Items.silicon, 10) };
        all.add(solarPanel);

        powerNode = new PowerNode("power-node");
        powerNode.requirements = new ItemStack[]{ new ItemStack(Items.copper, 2) };
        all.add(powerNode);

        //单位工厂：消耗硅生产 dagger（出厂单位自动寻路前往敌方核心）
        daggerFactory = new com.phoenix.game.world.blocks.units.UnitFactory("dagger-factory");
        daggerFactory.unitTypeName = "dagger"; //UnitTypes 晚于 Blocks 加载，按名字延迟解析
        daggerFactory.produceTime = 850f;
        daggerFactory.maxSpawn = 4;
        daggerFactory.inputItem = new ItemStack[]{ new ItemStack(Items.silicon, 6) };
        daggerFactory.requirements = new ItemStack[]{ new ItemStack(Items.copper, 60), new ItemStack(Items.lead, 40) };
        all.add(daggerFactory);

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

        for(Block block : all){
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

    /** 墙体：实心、有血量（可被摧毁）、带建造材料。 */
    private static Block wall(String name, int health, ItemStack... requirements){
        Block block = new com.phoenix.game.world.blocks.defense.Wall(name);
        block.health = health;
        block.requirements = requirements;
        all.add(block);
        return block;
    }

    /** @return 该地板是否为液体 */
    public static boolean isLiquid(Block block){
        return block instanceof Floor && ((Floor)block).isLiquid;
    }
}
