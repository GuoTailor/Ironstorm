package com.phoenix.game.world;
import com.phoenix.game.world.meta.BlockFlag;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.BuildVisibility;
import com.phoenix.game.world.meta.BlockStats;
import com.phoenix.game.world.meta.StatUnit;
import com.phoenix.game.world.meta.values.ItemListValue;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.content.Items;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Lines;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.type.Category;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.world.blocks.OverlayFloor;
import com.phoenix.game.world.blocks.power.PowerGraph;
import com.phoenix.game.world.meta.BlockGroup;
import com.phoenix.game.world.modules.PowerModule;

import java.util.EnumSet;
import java.util.function.Supplier;

import static com.phoenix.game.Vars.tilesize;

/**
 * 最小实现：方块。参照 Mindustry mindustry.world.Block 移植（去掉建筑/合成/多格等复杂机制）。
 * <p>继承 {@link com.phoenix.game.ctype.UnlockableContent}：名字/本地化名/描述/图标缓存都来自它，
 * 构造时自动注册进 {@code Vars.content} 并按注册顺序分配 id。
 */
public class Block extends com.phoenix.game.ctype.UnlockableContent {
    /** 建造分类，顺序与 Mindustry Category 一致。 */
    public Category category = Category.effect;
    /** 多格大小 */
    public int size = 1;
    public boolean solid;
    /** 是否可破坏 */
    public boolean breakable;
    /** 是否有血量 */
    public boolean destructible;
    /** 是否每帧更新（有 TileEntity） */
    public boolean update;
    /** 是否可被替换 */
    public boolean alwaysReplace;
    /** 是否有朝向（放置时可用 R 旋转，对应原版 Block.rotate） */
    public boolean rotate;
    /** 是否占满整格（绘制地面用） */
    public boolean fillsTile = true;
    /**
     * 是否往"方块投影"缓冲里涂一格落影（对应原版 {@code Block.hasShadow}，默认 true）。
     * <p>见 {@code graphics/BlockRenderer}：地图加载时把每格烘焙成 1 像素，绘制时放大成柔和的落影。
     */
    public boolean hasShadow = true;
    /** 该方块是否具有绝缘属性（原版 Block.insulated，"whether this block has insulating properties."）：闪电行进路径扫到即折断（Lightning.createLighting）。塑钢墙/电力二极管为 true。 */
    public boolean insulated = false;
    /** 血量，-1 表示不可破坏 */
    public int health = -1;
    /** 方块标记，用于 AI 索引 */
    public EnumSet<BlockFlag> flags = EnumSet.noneOf(BlockFlag.class);
    /** 显示颜色 */
    public Color color = Color.WHITE.cpy();

    public TextureRegion region;
    public TextureRegion[] variantRegions = new TextureRegion[0];

    /** 受损裂纹的档位数与最大适用方块尺寸（对应原版 {@code Block.crackRegions = 8, maxCrackSize = 5}）。 */
    public static final int crackRegions = 8, maxCrackSize = 5;
    /**
     * 受损裂纹贴图表：{@code cracks[size-1][i]}，由 {@code tools} 的贴图生成器产出
     * （{@code cracks-<size>-<i>}，见 {@code tools/src/io/anuke/mindustry/Generators.java}）。
     * <p>静态共享（所有方块用同一套），在 {@link #load()} 里惰性填充。
     */
    protected static TextureRegion[][] cracks;
    /**
     * 需要延迟加载的贴图名（{@code name + suffix}），下标即 {@link #reg(int)} 的返回值。
     * <p>参照原版 {@code Block.cacheRegionStrings} 移植：方块在构造函数里用 {@link #reg(String)} 登记，
     * 等到 {@link #load()}（图集就绪）时才真正查图集。
     */
    protected final Array<String> cacheRegionStrings = new Array<>();
    /** 与 {@link #cacheRegionStrings} 一一对应的贴图（对应原版 {@code Block.cacheRegions}）。 */
    protected TextureRegion[] cacheRegions = {};
    /**
     * 贴图名覆盖（默认用 {@link #name}）。
     * <p>图集里缺某些变体贴图时（如 underflow-gate 复用 overflow-gate、inverted-sorter 复用 sorter），
     * 指向已有区域即可正常绘制，不必退回白块。
     */
    public String textureName;

    /** 建筑实体工厂（有实体时需要）。null 表示无实体。 */
    public Supplier<TileEntity> entityType;
    /** 建造所需材料（对应原版 requirements）；拆除时按比例返还。 */
    public ItemStack[] requirements = {};
    /**
     * 建造栏可见性（对应原版 {@code Block.java:139} {@code public BuildVisibility buildVisibility = BuildVisibility.hidden;}）。
     * <p>默认 {@code hidden}：没有调用过 {@link #requirements(Category, BuildVisibility, ItemStack[])}
     * 也没有显式赋值的方块（地板、岩壁、树、thruster 等）不会出现在建造栏里。
     * <p>⚠ 不要和 {@link #isHidden()} 混淆：那个是"不绘制"（BlockPart 卫星瓦片用），
     * 本字段只管"建造栏是否列出"（见 {@link #isVisible()}）。
     */
    public BuildVisibility buildVisibility = BuildVisibility.hidden;
    /** 是否始终解锁（对应原版 {@code Block.alwaysUnlocked}，由 {@link #requirements(Category, ItemStack[], boolean)} 设置）。 */
    public boolean alwaysUnlocked;

    public Block(String name){
        super(name);
    }

    @Override
    public boolean alwaysUnlocked(){
        return alwaysUnlocked;
    }

    /**
     * 设置建造分类与所需材料（对应原版 {@code Block.java:908} {@code requirements(Category, ItemStack[])}）。
     * <p>转发到 {@link #requirements(Category, BuildVisibility, ItemStack[])}，可见性用 {@code shown}
     * （与原版一致：调用这个方法就等于"要在建造栏里列出来"）。
     */
    public void requirements(Category cat, ItemStack[] stacks){
        requirements(cat, BuildVisibility.shown, stacks);
    }

    /** 同上，并额外设置"始终解锁"（对应原版 {@code Block.java:903}）。 */
    public void requirements(Category cat, ItemStack[] stacks, boolean unlocked){
        requirements(cat, BuildVisibility.shown, stacks);
        this.alwaysUnlocked = unlocked;
    }

    /**
     * 设置建造分类、可见性与所需材料（对应原版 {@code Block.java:912-919}）。
     * <p>照原版按 {@code item.id} 升序排序，保证建造菜单/统计里的材料顺序与原版一致。
     */
    public void requirements(Category cat, BuildVisibility visible, ItemStack[] stacks){
        this.category = cat;
        this.requirements = stacks;
        this.buildVisibility = visible;
        //ItemStack[] 是普通数组，用 java.util.Arrays（原版是 arc 的 Structs.comparingInt）
        java.util.Arrays.sort(requirements, (a, b) -> Integer.compare(a.item.id, b.item.id));
    }

    @Override
    public com.phoenix.game.ctype.ContentType getContentType(){
        return com.phoenix.game.ctype.ContentType.block;
    }

    /** 若本方块有建筑实体则创建之，否则返回 null。 */
    public TileEntity newEntity(){
        return entityType == null ? null : entityType.get();
    }

    /** 加载贴图（需要在 Core.atlas 就绪后调用）。 */
    public void load(){
        variantRegions = loadRegions(textureName != null ? textureName : name, 0);
        if(variantRegions.length > 0) region = variantRegions[0];

        //延迟登记的贴图（reg(String)）：到这一步图集才可用（对应原版 Block.load 的 cacheRegions 循环）
        cacheRegions = new TextureRegion[cacheRegionStrings.size];
        for(int i = 0; i < cacheRegions.length; i++){
            cacheRegions[i] = Core.atlas == null ? null : Core.atlas.findRegion(cacheRegionStrings.get(i));
        }

        //贴图重载后图标缓存失效（对应原版 Arrays.fill(cicons, null)）
        java.util.Arrays.fill(cicons, null);
        generatedIcons = null;
        editorIconCache = null;
        editorVariantRegionsCache = null;

        //受损裂纹贴图（对应原版 Block.load 末尾的 cracks 惰性加载）：所有方块共用同一套。
        //⚠ 原版判据里还有"贴图被 dispose 就重新取图"，但 **libgdx 的 Texture 没有 isDisposed()**
        //（同 ScorchDecal 的说明）→ 去掉该判定，只判空。
        if(cracks == null || cracks[0][0] == null){
            cracks = new TextureRegion[maxCrackSize][crackRegions];
            for(int size = 1; size <= maxCrackSize; size++){
                for(int i = 0; i < crackRegions; i++){
                    cracks[size - 1][i] = Core.atlas == null ? null : Core.atlas.findRegion("cracks-" + size + "-" + i);
                }
            }
        }
    }

    /**
     * 登记一张延迟加载的贴图：最终贴图名为 {@code name + suffix}，返回一个供 {@link #reg(int)} 取用的下标。
     * <p>参照原版 {@code Block.reg(String)} 移植。必须在 {@link #load()} 之前调用（一般在构造函数里）。
     */
    protected int reg(String suffix){
        cacheRegionStrings.add(name + suffix);
        return cacheRegionStrings.size - 1;
    }

    /** 按下标取回 {@link #reg(String)} 登记的贴图（对应原版 {@code Block.reg(int)}）。 */
    protected TextureRegion reg(int id){
        return id < 0 || id >= cacheRegions.length ? null : cacheRegions[id];
    }

    /**
     * 从图标中心像素取小地图颜色（对应原版打包期 {@code Block.createIcons} 里的
     * {@code color.set(image.getPixel(image.width / 2, image.height / 2))}，原版取样对象是 {@code icon(Cicon.full)}）。
     * <p>原版这一步在 MultiPacker 里对每个方块做一次；phoenix 没有打包管线，改在世界内容加载后
     * 由 {@code Blocks.loadMinimapColors()} 调用一次，分页 PNG 由调用方缓存与释放。
     * <p>必须用 {@code icon(Cicon.full)} 而不是 {@code region}：自然岩壁（{@code Rock}）的贴图命名是
     * {@code name1..nameN}，裸名 {@code region} 会回退成 {@code blank}，采出来是透明色。
     * @param page 该方块 {@code icon(Cicon.full)} 所在的分页 PNG（可为 null，表示取不到，保持默认色）
     */
    public void loadMinimapColor(Pixmap page){
        TextureRegion icon = icon(Cicon.full);
        if(page == null || icon == null) return;

        //AtlasRegion 的 (x, y) 与 Pixmap.getPixel 同为「左上原点」
        int px = icon.getRegionX() + icon.getRegionWidth() / 2;
        int py = icon.getRegionY() + icon.getRegionHeight() / 2;
        if(px < 0 || py < 0 || px >= page.getWidth() || py >= page.getHeight()) return;

        color.set(page.getPixel(px, py));
        //贴图缺失时中心像素可能是透明的，兜一个深灰，避免方块在小地图上隐形
        if(color.a < 0.05f) color.set(Color.DARK_GRAY);
    }

    /** 依次尝试 name1..nameN / name / blank，返回所有能取到的贴图。 */
    protected static TextureRegion[] loadRegions(String name, int variants){
        Array<TextureRegion> found = new Array<>();

        if(variants > 0 && Core.atlas != null){
            for(int i = 1; i <= variants; i++){
                TextureRegion reg = Core.atlas.findRegion(name + i);
                if(reg != null) found.add(reg);
            }
        }

        if(found.isEmpty() && Core.atlas != null){
            TextureRegion reg = Core.atlas.findRegion(name);
            if(reg != null) found.add(reg);
        }

        if(found.isEmpty() && Core.atlas != null){
            TextureRegion reg = Core.atlas.findRegion("blank");
            if(reg != null) found.add(reg);
        }

        return found.toArray(TextureRegion.class);
    }

    /** 图标层数组缓存（base/rotator/top…），对应原版 Block.generatedIcons。 */
    private TextureRegion[] generatedIcons;
    /** 编辑器图标缓存（对应原版 {@code Block.editorIcon}）；贴图重载后由 {@link #load()} 清空。 */
    private TextureRegion editorIconCache;
    /** 编辑器地板变体缓存（对应原版 {@code Block.editorVariantRegions}）；贴图重载后由 {@link #load()} 清空。 */
    private TextureRegion[] editorVariantRegionsCache;

    /**
     * 生成图标分层贴图，对应原版 Block.generateIcons。
     * <p>原版在打包期把这些层合成成一张 {@code block-<name>-full}；phoenix 不引入打包管线，
     * 改为在 UI 侧按层叠加（{@link #getGeneratedIcons()}），视觉等价。
     * @return 自底向上的贴图层，允许含 null（该层不存在）
     */
    protected TextureRegion[] generateIcons(){
        return new TextureRegion[]{region};
    }

    /** @return 缓存的图标分层贴图（base/rotator/top…）。 */
    public TextureRegion[] getGeneratedIcons(){
        if(generatedIcons == null){
            generatedIcons = generateIcons();
        }
        return generatedIcons;
    }

    /**
     * 返回用于 HUD/详情的图标区域；图集没有对应尺寸图时回退到基础区域。
     * <p>覆写 {@code UnlockableContent.icon(Cicon)}：方块的图标是打包期合成的
     * {@code block-<name>-full}，本项目没有打包管线，改为回退到 {@link #getGeneratedIcons()} 的分层贴图。
     */
    public TextureRegion icon(Cicon cicon){
        int index = cicon.ordinal();
        if(cicons[index] == null){
            TextureRegion found = null;
            if(Core.atlas != null){
                found = Core.atlas.findRegion("block-" + name + "-" + cicon.name());
                if(found == null){
                    found = Core.atlas.findRegion("block-" + name + "-full");
                }
            }
            if(found == null){
                TextureRegion[] icons = getGeneratedIcons();
                found = icons.length > 0 && icons[0] != null ? icons[0] : region;
            }
            cicons[index] = found;
        }
        return cicons[index];
    }

    /**
     * 地图编辑器里画这个方块用的图标（对应原版 {@code Block.editorIcon()}）。
     *
     * <p>TODO 原版取图集里的 {@code <name>-icon-editor}（编辑器专用、去阴影的图标）；
     * <b>本项目图集没有该系列贴图</b>（只有 {@code clear-editor}/{@code block-border-editor}）
     * → 退化为 {@link #icon(Cicon)}。副作用：{@code BlockPart} 之类没有图标的内容会退化成
     * 纯白的 {@code blank}，所以 {@code MapRenderer} 对卫星格做了单独的守卫（见其类注释）。
     */
    public TextureRegion editorIcon(){
        if(editorIconCache == null){
            if(Core.atlas != null){
                editorIconCache = Core.atlas.findRegion(name + "-icon-editor");
            }
            if(editorIconCache == null){
                editorIconCache = icon(Cicon.full);
            }
        }
        return editorIconCache;
    }

    /**
     * 地图编辑器里画这个地板用的变体贴图数组（对应原版 {@code Block.editorVariantRegions()}）。
     *
     * <p>TODO 原版取 {@code editor-<variant 贴图名>}（编辑器专用的纯色地板）；
     * <b>本项目图集没有该系列贴图</b> → 逐个回退到 {@link #variantRegions} 里的原贴图
     * （地图看起来更"实景"、不像原版那样是纯色块，但每格都能正确显示）。
     */
    public TextureRegion[] editorVariantRegions(){
        if(editorVariantRegionsCache == null){
            TextureRegion[] src = variantRegions;
            TextureRegion[] out = new TextureRegion[src.length];
            for(int i = 0; i < src.length; i++){
                TextureRegion region = src[i];
                String regionName = region instanceof TextureAtlas.AtlasRegion ? ((TextureAtlas.AtlasRegion)region).name : null;
                TextureRegion editor = (Core.atlas != null && regionName != null)
                    ? Core.atlas.findRegion("editor-" + regionName) : null;
                out[i] = editor == null ? region : editor;
            }
            editorVariantRegionsCache = out;
        }
        return editorVariantRegionsCache;
    }

    /**
     * 详情/HUD 用的图标分层，对应原版 {@code getDisplayIcon(Tile)} 返回的那张合成图。
     * <p>图集里只有 base/rotator/top 等分层贴图，没有打包期合成的 {@code block-<name>-full}，
     * 所以返回层数组由 UI 侧叠加；直接取 {@link #icon(Cicon)} 只会拿到最底层，钻头之类会缺一半。
     */
    public TextureRegion[] getDisplayIcons(Tile tile){
        return getGeneratedIcons();
    }

    /** @return 该瓦片的小地图自定义颜色，0 表示走 {@code MapIO.colorFor} 的默认规则。 */
    public int minimapColor(Tile tile){
        return 0;
    }

    /** @return 本地化显示名（对应原版 localizedName 字段）；继承自 UnlockableContent。 */
    public String localizedName(){
        return localizedName;
    }

    /** @return 悬停瓦片时显示的名称（对应原版 getDisplayName(Tile)）。 */
    public String getDisplayName(Tile tile){
        return localizedName();
    }

    /** @return 本地化描述文本；继承自 UnlockableContent（缺失时为空串）。 */

    /**
     * 把该方块的信息渲染进表格（对应原版 {@code Block.displayInfo} → {@code ContentDisplay.displayBlock}）。
     * <p>内容信息对话框（建造菜单详情区的问号按钮）用它填充内容区。
     */
    public void displayInfo(com.badlogic.gdx.scenes.scene2d.ui.Table table){
        com.phoenix.game.ui.ContentDisplay.displayBlock(table, this);
    }

    /**
     * 把该建筑的运行状态条加入表格（对应原版 Block.displayBars + setBars）。
     * <p>顺序与原版一致：生命值 → 液体 → 电力 → 物品储量；无对应模块的条不显示。
     * @param tile 建筑所在瓦片（其 entity 必须非 null）
     * @param table 目标表格，每行一个条
     */
    public void displayBars(Tile tile, com.badlogic.gdx.scenes.scene2d.ui.Table table){
        TileEntity entity = tile.entity;
        if(entity == null) return;

        addHealthBar(entity, table);

        if(hasLiquids && displayLiquidBar){
            addLiquidBar(entity, table);
        }

        if(hasPower && entity.power != null){
            addPowerBar(entity, table);
        }

        if(hasItems && entity.items != null && itemCapacity > 0 && itemsBarEnabled){
            addItemsBar(entity, table);
        }
    }

    /**
     * 把该建筑"消耗什么"的图标行加入表格（对应原版 world/Block.java:696-702）。
     * <p>原版在 {@code display(Tile, Table)} 里紧跟在状态条表之后调用；本项目由
     * {@code ui/fragments/PlacementFragment.addTileDetail} 在 {@link #displayBars} 之后调用。
     * <p>可选加成（如相织机加速）不列出，与 {@code Consumers} 的触发语义一致。
     */
    public void displayConsumption(Tile tile, com.badlogic.gdx.scenes.scene2d.ui.Table table){
        table.left();
        for(com.phoenix.game.world.consumers.Consume cons : consumes.all()){
            if(cons.isOptional() && cons.isBoost()) continue;
            cons.build(tile, table);
        }
    }

    /** 是否显示物品储量条（对应原版 configurable 开关）。 */
    public boolean itemsBarEnabled;

    private void addHealthBar(TileEntity entity, com.badlogic.gdx.scenes.scene2d.ui.Table table){
        table.add(new com.phoenix.game.ui.Bar("生命值", com.phoenix.game.graphics.Pal.health, entity::healthf))
            .height(18f).growX().pad(4f);
        table.row();
    }

    private void addLiquidBar(TileEntity entity, com.badlogic.gdx.scenes.scene2d.ui.Table table){
        com.phoenix.game.ui.Bar bar = new com.phoenix.game.ui.Bar(
            () -> {
                boolean empty = entity.liquids == null || entity.liquids.isEmpty();
                return empty ? "液体" : String.format("液体：%.1f", entity.liquids.total());
            },
            () -> entity.liquids != null
                ? entity.liquids.current().color : com.phoenix.game.graphics.Pal.lightishGray,
            () -> entity.liquids == null ? 0f : entity.liquids.fullness());
        table.add(bar).height(18f).growX().pad(4f);
        table.row();
    }

    private void addPowerBar(TileEntity entity, com.badlogic.gdx.scenes.scene2d.ui.Table table){
        table.add(new com.phoenix.game.ui.Bar("电力", com.phoenix.game.graphics.Pal.powerBar, () -> entity.power.status))
            .height(18f).growX().pad(4f);
        table.row();
    }

    private void addItemsBar(TileEntity entity, com.badlogic.gdx.scenes.scene2d.ui.Table table){
        table.add(new com.phoenix.game.ui.Bar(
            () -> "物品：" + entity.items.total(),
            () -> com.phoenix.game.graphics.Pal.items,
            () -> (float)entity.items.total() / itemCapacity))
            .height(18f).growX().pad(4f);
        table.row();
    }

    public boolean isMultiblock(){
        return size > 1;
    }

    public boolean hasEntity(){
        //有血量的建筑（如墙体）也需要实体来承载血量，即使没有每帧逻辑。
        //必须同时要求 entityType != null：否则 newEntity() 会返回 null，
        //结果这一格"有方块、没实体"，血量和拆除都会静默失效
        return entityType != null && (update || destructible);
    }

    public boolean isSolidFor(Tile tile){
        return false;
    }

    /** @return 是否由玩家/单位放置（对应原版 {@code Block.synthetic()}）。小地图上人造方块画阵营色、自然地形画自身颜色。 */
    public boolean synthetic(){
        return update || destructible;
    }

    /** 多格建筑：返回该瓦片链接到的中心瓦片；非多格返回自身。默认不覆盖。 */
    public Tile linked(Tile tile){
        return tile;
    }

    /** 是否可被超频加速（对应原版 {@code Block.canOverdrive}，OverdriveProjector 的作用对象）。 */
    public boolean canOverdrive = true;

    /**
     * 能否建在深水/深液上（对应原版 {@code Block.floating}）。
     * <p>放置校验 {@link Build#validPlace} 会拒绝把非浮空方块放到深液格上；抽水机这类需架在水面的方块置 true。
     */
    public boolean floating = false;

    /** 拖拽连放时是否走传送带式寻路（对应原版 {@code Block.conveyorPlacement}），由传送带/导管置 true。 */
    public boolean conveyorPlacement;

    /** 运行状态条里是否显示液体条（对应原版 {@code setBars()} 里 {@code bars.remove("liquid")} 的效果）。 */
    public boolean displayLiquidBar = true;

    /** 物品是否"瞬移"（Sorter/OverflowGate 这类无缓冲直通方块，用于禁止链式堆叠）。 */
    public boolean instantTransfer;
    /** 是否可配置（放置后点选设置，如 Sorter 选物品、Bridge 选目标）。 */
    public boolean configurable;
    /** 是否能被装卸器（Unloader）取物（对应原版 {@code Block.unloadable}）。 */
    public boolean unloadable = true;
    /** 配置值是"另一格的位置"（物品桥/质量驱动器/电力节点），对应原版 {@code Block.posConfig}。 */
    public boolean posConfig;

    /** 玩家放置后回调（对应原版 {@code Block.playerPlaced}）。 */
    public void playerPlaced(Tile tile){
    }

    /**
     * 地形条件校验（对应原版 {@code Block.canPlaceOn}）：瓦片为空之外的地形要求。
     * @return false 表示该瓦片不满足放置条件（如钻头脚下没有可采矿物）
     */
    public boolean canPlaceOn(Tile tile){
        return true;
    }

    /**
     * 玩家点击本方块时回调（对应原版 {@code Block.tapped}）。
     * <p>用于"点一下就能操作"的方块：指挥中心切指令、机甲平台换机甲、在建方块续建。
     * @return true 表示本方块已处理这次点击，输入层不再把它当开火
     */
    public boolean tapped(Tile tile, com.phoenix.game.entities.type.Player player){
        return false;
    }

    /**
     * 收到配置值时回调（对应原版 {@code Block.configured}）。
     * @param value 配置值；-1 表示清除配置
     */
    public void configured(Tile tile, com.phoenix.game.entities.type.Player player, int value){
    }

    /** 打开配置界面（对应原版 {@code Block.buildConfiguration}）。 */
    public void buildConfiguration(Tile tile, com.badlogic.gdx.scenes.scene2d.ui.Table table){
    }

    /**
     * 邻接表重建后回调（对应原版 {@code Block.onProximityUpdate}）。
     * <p>物品/电力类方块在这里刷新"可输出目标"，例如 Router 的轮询指针、电力图的合并。
     */
    public void onProximityUpdate(Tile tile){
    }

    /** 本建筑加入邻接表时回调（对应原版 {@code Block.onProximityAdded}）。 */
    public void onProximityAdded(Tile tile){
    }

    /** 本建筑移出邻接表时回调（对应原版 {@code Block.onProximityRemoved}）。 */
    public void onProximityRemoved(Tile tile){
    }

    /**
     * 子弹命中本建筑时回调（对应原版 {@code Block.handleBulletHit}）。
     * <p>用于"受击时做事"的方块：反射墙（反弹子弹）、合金墙（闪电反击）等。
     * <p>注意调用时机在**子弹被移除之前**，因此在这里调 {@code bullet.deflect()} 可以让子弹不被消耗。
     */
    public void handleBulletHit(TileEntity entity, com.phoenix.game.entities.type.Bullet bullet){
    }

    /** 是否隐藏不绘制（如 BlockPart 卫星瓦片）。 */
    public boolean isHidden(){
        return false;
    }

    /**
     * 放置预览钩子：光标处正在放本方块时调用（对应原版 {@code Block.drawPlace}）。
     * <p>由 {@code InputHandler.drawTop} 在画完 ghost 后调用；默认什么都不画，
     * 需要显示额外提示的方块（抽水机的水速、培育机的加成）覆写它。
     * @param x 光标所在瓦片 x
     * @param y 光标所在瓦片 y
     * @param rotation 将要放置的朝向
     * @param valid 当前是否可放置
     */
    public void drawPlace(int x, int y, int rotation, boolean valid){
    }

    /**
     * 在放置预览处画一行文字（对应原版 {@code Block.drawPlaceText}），返回文字宽度。
     * <p>原版用 arc 的 {@code Fonts.outline.draw(text, x, y, Align.center)} + 两段下划线；
     * 这里改用 libgdx 的 {@code BitmapFont.draw(Batch, GlyphLayout, x, y)}（手动减半宽实现居中），
     * 下划线用 {@link com.phoenix.game.core.Lines}。
     */
    public float drawPlaceText(String text, int x, int y, boolean valid){
        com.badlogic.gdx.graphics.g2d.BitmapFont font = com.phoenix.game.core.Fonts.outline;
        if(font == null || text == null) return 0f;

        Color color = valid ? com.phoenix.game.graphics.Pal.accent : com.phoenix.game.graphics.Pal.remove;
        com.badlogic.gdx.graphics.g2d.GlyphLayout layout = com.badlogic.gdx.utils.Pools.obtain(com.badlogic.gdx.graphics.g2d.GlyphLayout.class);
        boolean ints = font.usesIntegerPositions();
        font.setUseIntegerPositions(false);
        font.getData().setScale(1f / 4f / com.phoenix.game.core.Scl.scl(1f));
        //⚠ libgdx 的 GlyphLayout.setText 会把当前字体颜色固化进 layout，必须在 setText 之前设色
        font.setColor(color);
        layout.setText(font, text);

        float width = layout.width;
        float dx = x * tilesize + offset(), dy = y * tilesize + offset() + size * tilesize / 2f + 3;
        font.draw(Core.batch, layout, dx - width / 2f, dy + layout.height + 1);
        dy -= 1f;

        //两段下划线（深灰描边 + 本色芯线），与 arc 版一致
        com.phoenix.game.core.Draw.color(Color.DARK_GRAY);
        com.phoenix.game.core.Lines.stroke(2f);
        com.phoenix.game.core.Lines.line(dx - width / 2f - 2f, dy, dx + width / 2f + 1.5f, dy);
        com.phoenix.game.core.Draw.color(color);
        com.phoenix.game.core.Lines.stroke(1f);
        com.phoenix.game.core.Lines.line(dx - width / 2f - 2f, dy, dx + width / 2f + 1.5f, dy);

        font.setUseIntegerPositions(ints);
        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
        com.phoenix.game.core.Draw.color();
        com.badlogic.gdx.utils.Pools.free(layout);
        return width;
    }

    /**
     * 绘制方块本体（对应原版 Block.draw(Tile)）。
     * 默认把自身贴图按 size×size 格绘制；带朝向/动画的方块（传送带）覆盖此方法自己处理。
     * @param tile 本方块所在瓦片
     */
    public void draw(Tile tile){
        if(region == null) return;

        float size = this.size * tilesize;
        float dx = tile.worldx(), dy = tile.worldy();
        //偶数尺寸多格方块的中心在两格交界处，需要额外偏半格（对应 Block.offset）
        Core.batch.draw(region, dx + (tilesize - size) / 2f + offset(), dy + (tilesize - size) / 2f + offset(), size, size);
    }

    /**
     * 绘制**非己方**建筑的队伍色边框（对应原版 {@code Block.drawTeam(Tile)}，
     * 调用点 = 原版 {@code BlockRenderer.drawBlocks} 里 {@code block.synthetic() && tile.getTeam() != player.getTeam()}）。
     * <p>做法：把 {@code block-border} 贴图（1 格大小，32px × Draw.scl = 8）按队伍色染色后叠在方块上。
     * <p><b>坐标换算（⚠ 必读）</b>：原版写的是
     * {@code Draw.rect("block-border", tile.drawx() - size*tilesize/2f + 4, ...)}，
     * 其中 {@code tile.drawx()} 在**原版坐标口径**下就是方块中心；本项目 {@code Tile.drawx()} 比中心少半格
     * （见 {@code Tile.drawx()} 注释），而 {@code Draw.rect} 是中心语义 → 本项目等价写法为
     * {@code centerX(tile) - size*tilesize/2f + tilesize/2f}。
     * <p>⚠ 与原版一致地保留了那个"多格方块只画在左下那一格上"的行为（1 格方块正好居中；
     * 2×2 偏左下 4，3×3 偏左下 8）—— 新版 Mindustry 的 {@code BuildingComp.drawTeam} 也是同一公式，属原版既有行为。
     */
    public void drawTeam(Tile tile){
        if(Core.atlas == null || tile == null) return;
        TextureRegion border = Core.atlas.findRegion("block-border");
        if(border == null) return;

        Draw.color(tile.getTeam().color);
        Draw.rect(border, centerX(tile) - size * tilesize / 2f + tilesize / 2f,
            centerY(tile) - size * tilesize / 2f + tilesize / 2f);
        Draw.color();
    }

    /**
     * 绘制**受损裂纹**（对应原版 {@code Block.drawCracks(Tile)}）。
     * <p>调用点 = 原版 {@code BlockRenderer.drawBlocks} 里
     * {@code if(tile.entity != null && tile.entity.damaged()) block.drawCracks(tile);}（排在 {@code draw} 之后）。
     * <p>表现：按受损程度选一档裂纹贴图（越惨越靠后），用 {@code colorl(0.2f, alpha)} 画成**暗色**叠加，
     * 并按 {@code tile.pos() % 4} 随机转 0/90/180/270 度，避免相邻方块裂纹完全一样。
     * <p>⚠ 坐标口径：原版 {@code tile.drawx()/drawy()} 就是方块中心；本项目 {@code Tile.drawx()} 比中心少半格
     * （见 {@link #drawTeam(Tile)} 的说明），而 {@code Draw.rect} 是中心语义 → 这里改用 {@link #centerX}/{@link #centerY}。
     * <p>⚠ 尺寸：裂纹贴图是 {@code size*32} 像素，{@code Draw.rect} 按 region 原始尺寸绘制，
     * 乘 {@code Draw.scl} 后正好是 {@code size} 格（与方块本体一致）。
     */
    public void drawCracks(Tile tile){
        if(tile == null || tile.entity == null || cracks == null) return;
        if(!tile.entity.damaged() || size > maxCrackSize) return;
        if(cracks[size - 1] == null) return;

        int id = tile.pos();
        float healthf = tile.entity.healthf();
        TextureRegion crack = cracks[size - 1][Mathf.clamp((int)((1f - healthf) * crackRegions), 0, crackRegions - 1)];
        if(crack == null) return;

        Draw.colorl(0.2f, 0.1f + (1f - healthf) * 0.6f);
        Draw.rect(crack, centerX(tile), centerY(tile), (id % 4) * 90f);
        Draw.color();
    }

    /**
     * 绘制叠在方块贴图之上的内容（如传送带上的物品）。默认不绘制。
     * @param tile 本方块所在瓦片
     */
    public void drawLayer(Tile tile){
    }

    /**
     * 在所有方块绘制完成后的"顶层"绘制（电力节点的激光连线等需要盖住相邻方块的内容）。
     * <p>逐格绘制时后画的方块会压掉先画的内容，所以这类跨方块的连线必须放到独立的一遍里。
     * @param tile 本方块所在瓦片
     */
    public void drawTopLayer(Tile tile){
    }

    /**
     * 鼠标悬停在**己方**建筑上时的额外绘制（对应原版 {@code Block.drawSelect(Tile)}）：
     * 原版用来画炮塔射程圈、留言板文本、超频/维修范围等。默认不绘制，由具体方块覆写。
     * <p>调用点：{@code InputHandler.drawTop} 每帧在建造预览之前，对光标下的己方方块调用一次。
     * @param tile 本方块所在瓦片
     */
    public void drawSelect(Tile tile){
    }

    /**
     * 配置面板打开时绘制本方块的配置叠加（对应原版 {@code Block.drawConfigure(Tile)}，Block.java:520-525）：
     * 默认画一圈 accent 色描边方框；由具体方块覆写（物品桥/质量驱动器/电力节点会叠加连线范围等）。
     * <p>调用点：{@code Renderer.draw()} 在 {@code input.drawTop()} 之前对
     * {@code input.configTile}（配置面板正打开的瓦片）调用（对应原版 OverlayRenderer.drawTop 的
     * {@code if(input.frag.config.isShown()) tile.block().drawConfigure(tile)}）。
     * <p>⚠ 坐标口径：原版 {@code tile.drawx()} 就是贴图中心；本项目那是"左下角 + offset()"
     * → 用 {@link #centerX}/{@link #centerY}。
     */
    public void drawConfigure(Tile tile){
        Draw.color(Pal.accent);
        Lines.stroke(1f);
        Lines.square(centerX(tile), centerY(tile), size * tilesize / 2f + 1f);
        Draw.color();
        Lines.stroke(1f);
    }

    /** @return 是否向相邻建筑输出物品（对应原版 Block.outputsItems），传送带贴图拼接会用到。 */
    public boolean outputsItems(){
        return hasItems;
    }

    /**
     * @return 该方块是否可被玩家直接存取物品（对应原版 {@code Block.isAccessible()}）：
     * 有物品库存且容量大于 0 才可存取。
     * <p>消费点：{@code ui/fragments/BlockInventoryFragment.showFor()} 与 {@code update()}
     * （悬停/点击方块时决定是否弹出物品面板）。
     */
    public boolean isAccessible(){
        return (hasItems && itemCapacity > 0);
    }

    // ---- 物品接口（参照 Mindustry mindustry.world.BlockStorage） ----

    /** 是否有物品库存（决定 TileEntity 是否分配 ItemModule）。 */
    public boolean hasItems;
    /** 物品容量上限（hasItems 时有效）。 */
    public int itemCapacity = 10;

    /** 是否有液体储量（决定 TileEntity 是否分配 LiquidModule），对应原版 Block.hasLiquids。 */
    public boolean hasLiquids;
    /** 液体容量上限（hasLiquids 时有效）。 */
    public float liquidCapacity = 10f;
    /** 是否向相邻建筑输出液体（对应原版 Block.outputsLiquid，管道/桥的贴图拼接与自动运输用它）。 */
    public boolean outputsLiquid;
    /** 是否绘制液体灯光（对应原版 Block.drawLiquidLight 开关）。 */
    public boolean drawLiquidLight = true;
    /** 液体压力（对应原版 Block.liquidPressure）：影响 tryMoveLiquid 的流动速率。 */
    public float liquidPressure = 1f;
    /** 方块所属的逻辑分组（对应原版 Block.group）。 */
    public BlockGroup group = BlockGroup.none;

    /** 是否连接电网（决定 TileEntity 是否分配 PowerModule）。 */
    public boolean hasPower;
    /** 生产功率（每帧）。 */
    public float powerProduction;
    /** 消耗功率（每帧）。 */
    public float powerConsumption;

    /**
     * 建造总成本（对应原版 {@code Block.buildCost}）：各材料数量 × 材料单价之和。
     * <p>由 {@link #init()} 算出。建造队列用它决定推进速度——成本越高，单位时间内涨的进度越少。
     * 无材料要求的方块成本为 0，会瞬间建成（与原版一致）。
     */
    public float buildCost;

    /**
     * 本方块的建造成本倍率（对应原版 {@code Block.buildCostMultiplier}，默认 1f）。
     * <p>只影响 {@link #buildCost}（建造耗时/统计），与 {@code rules.buildCostMultiplier}（玩家实际花费）是两回事。
     * 原版在 {@code Block.init()} 里 {@code buildCost *= buildCostMultiplier}；交叉器/路由器/分类器/闸门会调大它。
     */
    public float buildCostMultiplier = 1f;

    /** 资源消耗器集合（对应原版 {@code Block.consumes}）：物品/液体/电力统一在这里声明。 */
    public final com.phoenix.game.world.consumers.Consumers consumes = new com.phoenix.game.world.consumers.Consumers();

    /**
     * @return 本方块此刻是否应该消耗资源（对应原版 {@code BlockStorage.shouldConsume}）。
     * <p>⚠ 原版继承链是 {@code Block extends BlockStorage}，这两个钩子定义在 BlockStorage 上；
     * 本项目 Block 直接继承 UnlockableContent、库存逻辑内联在 Block 里，所以在这里补一份
     * （BlockStorage 里那份保留，作为将来拆分基类时的落点）。默认 true，由具体方块覆写。
     * <p>调用点：{@code ConsumeModule.update()/valid()}（消耗门槛）与 {@code productionValid} 一起决定
     * "本帧是否真的扣料/更新消耗器"。
     */
    public boolean shouldConsume(Tile tile){
        return true;
    }

    /** @return 本方块此刻是否"在生产"（对应原版 {@code BlockStorage.productionValid}）。默认 true。 */
    public boolean productionValid(Tile tile){
        return true;
    }

    /** 统计项集合（对应原版 {@code Block.stats}）：内容信息对话框按分类显示它。由 {@link #init()} 填充。 */
    public final BlockStats stats = new BlockStats();

    /**
     * 内容加载时调用一次（对应原版 {@code Block.init}）：拍平消耗器数组供热路径遍历。
     * <p>必须在方块字段配置完之后、{@link #load()} 之前调用（{@code Blocks.load} 里统一做）。
     */
    public void init(){
        consumes.init();

        //默认生命值：没显式设置（-1）时按占地格数算（对应原版 Block.init 的 size * size * 40）
        if(health < 0){
            health = size * size * 40;
        }

        //建造总成本 = Σ(材料数量 × 单价)（对应原版 Block.init）
        buildCost = 0f;
        for(ItemStack stack : requirements){
            buildCost += stack.amount * stack.item.cost;
        }
        //方块级成本倍率（对应原版 buildCost *= buildCostMultiplier）
        buildCost *= buildCostMultiplier;

        //把电力消耗器同步回旧字段：PowerGraph 仍按 powerConsumption / powerProduction 算电量平衡，
        //这样各方块只需声明 consumes.power(...)，不必再手写 powerConsumption（避免两处不一致）
        if(consumes.hasPower()){
            com.phoenix.game.world.consumers.ConsumePower power =
                consumes.get(com.phoenix.game.world.consumers.ConsumeType.power);

            hasPower = true;
            if(!power.buffered){
                powerConsumption = power.usage;
            }
        }

        //统计项要在 buildCost 算完之后登记；先清空是为了 init 万一被重复调用时不会累积
        stats.clear();
        setStats();
    }

    /** @return 玩家/单位能否手动拆除本方块（对应原版 {@code Block.canBreak}，原版 Block.java:167-169）。CoreBlock 覆写为 false，防止拆掉自家核心。 */
    public boolean canBreak(Tile tile){
        return true;
    }

    /**
     * @return 是否"可建造"（对应原版 {@code Block.java:171-173}）。
     * <p>原版语义是"可见性不是 hidden/debugOnly"，<b>不是</b>"有没有材料"。
     * 消费点：{@code setStats()}（是否登记建造时间/建造花费）、{@code CustomRulesDialog}（可禁用的方块列表）。
     * <p>刻意对齐原版：本方法原先在本项目里是 {@code requirements.length > 0}（"有没有材料"），
     * 那是没有 buildVisibility 字段时的替代判据，现按原版还原。
     */
    public boolean isBuildable(){
        return buildVisibility != BuildVisibility.hidden && buildVisibility != BuildVisibility.debugOnly;
    }

    /**
     * @return 是否在建造栏/内容列表里可见（对应原版 {@code Block.java:877-879}）。
     * <p>⚠ 内部调用的 {@link #isHidden()} 在本项目里是"**不绘制**"（BlockPart 卫星瓦片覆写为 true），
     * 与原版 {@code isHidden()}（"建造栏不可见"）语义不同；这里照原版形状转发，效果正确
     * （BlockPart 本来也不该出现在建造栏里）。
     */
    public boolean isVisible(){
        return buildVisibility.visible() && !isHidden();
    }

    /**
     * 登记本方块的统计项（对应原版 {@code Block.setStats}），由 {@link #init()} 调用。
     * <p>子类覆写时先调 {@code super.setStats()} 再补自己的项。
     */
    public void setStats(){
        stats.add(BlockStat.size, "{0}x{0}", size);
        if(health > 0) stats.add(BlockStat.health, health, StatUnit.none);

        if(isBuildable()){
            stats.add(BlockStat.buildTime, buildCost / 60f, StatUnit.seconds);
            //建造花费只显示图标 + 数量，不显示物品名（与原版一致）
            stats.add(BlockStat.buildCost, new ItemListValue(false, requirements));
        }

        //耗电 / 耗物品由消耗器自己登记（对应原版 consumes.display(stats)）
        consumes.display(stats);

        if(hasLiquids) stats.add(BlockStat.liquidCapacity, liquidCapacity, StatUnit.liquidUnits);
        if(hasItems) stats.add(BlockStat.itemCapacity, itemCapacity, StatUnit.items);
    }

    /** @return 方块每帧发电量。 */
    public float getPowerProduction(Tile tile){
        return powerProduction;
    }

    /**
     * @return 方块每帧耗电量（对应原版 PowerGraph 里 {@code consumePower.requestedPower(entity)} 的统计口径）。
     * <p>声明了电力消耗器的方块走 {@link com.phoenix.game.world.consumers.ConsumePower#requestedPower}，
     * 于是 {@code shouldConsume == false}（如库存满的分拣器）时不再占电网需求，与原版一致；
     * 没有消耗器的方块（仍手工设 {@code powerConsumption}，如 phaseConveyor/massDriver）回退到静态值。
     * <p>⚠ 储能型（电池）的充放电在本项目由 {@link com.phoenix.game.world.blocks.power.PowerGraph}
     * 单独结算，所以这里不取储能分支的申请量（否则会和电池充放电重复计入需求）。
     */
    public float getPowerNeeded(Tile tile){
        if(tile != null && tile.entity != null && consumes.hasPower()){
            com.phoenix.game.world.consumers.ConsumePower power =
                consumes.get(com.phoenix.game.world.consumers.ConsumeType.power);
            if(!power.buffered){
                return power.requestedPower(tile.entity);
            }
        }
        return powerConsumption;
    }

    /** 电网连线距离（格）；0 表示只连邻接建筑。对应原版 {@code PowerNode.laserRange}。 */
    public float powerRange;

    /**
     * 收集本建筑在电网中**直接相连**的瓦片（对应原版 {@code PowerBlock.getPowerConnections}）。
     * <p>{@link PowerGraph} 只通过这个入口做连通性 BFS，所以覆写它就能改变电网拓扑：
     * 默认 = 邻接表（外围一圈同队建筑）；{@code PowerNode} 覆写成"范围内其他节点"以支持远距离连线；
     * {@code PowerDiode} 覆写成"只连背面"以实现单向输电。
     * @param out 输出列表（调用方负责清空），只会被加入带电瓦片
     */
    public void getPowerConnections(Tile tile, Array<Tile> out){
        if(tile.entity == null) return;

        for(int i = 0; i < tile.entity.proximity.size; i++){
            Tile other = tile.entity.proximity.get(i);
            if(other.entity != null && other.entity.power != null){
                out.add(other);
            }
        }
    }

    /**
     * 是否接受该物品进入本方块。
     * @param item 待接收物品
     * @param tile 本方块所在瓦片
     * @param source 来源瓦片，可为 null
     * @return true 表示可接收并应由 {@link #handleItem} 处理
     */
    public boolean acceptItem(Item item, Tile tile, Tile source){
        return hasItems && tile.entity != null && tile.entity.items != null
            && tile.entity.items.get(item) < getMaximumAccepted(tile, item);
    }

    /** @return 本方块还能再收多少个该物品（对应原版 Block.getMaximumAccepted）。 */
    public int getMaximumAccepted(Tile tile, Item item){
        return itemCapacity;
    }

    /**
     * 接收物品（默认存入本方块库存）。
     * @param item 物品
     * @param tile 本方块所在瓦片
     * @param source 来源瓦片，可为 null
     */
    public void handleItem(Item item, Tile tile, Tile source){
        if(tile.entity != null && tile.entity.items != null){
            tile.entity.items.add(item, 1);
        }
    }

    /**
     * 从本方块库存取出指定数量（对应原版 Block.removeStack）。
     * @return 实际取出的数量
     */
    public int removeStack(Tile tile, Item item, int amount){
        if(tile.entity == null || tile.entity.items == null) return 0;
        amount = Math.min(amount, tile.entity.items.get(item));
        tile.entity.items.remove(item, amount);
        return amount;
    }

    /**
     * @return 本方块实际能接收该物品的数量（对应原版 {@code Block.acceptStack}）。
     * <p>刻意补的 API：原版这两个方法就在 {@code Block} 上，本项目此前只把它们复制在**未接线**的
     * {@code world/BlockStorage}（兼容层）里 → 这里补回 {@code Block}，供玩家投递（{@code InputHandler.transferInventory}）
     * 与单位卸货（{@code MinerTrait}）使用。
     * @param source 来源单位（用于队伍判定），可为 null
     * @return 0 表示不收（不可收 / 队伍不符 / 已满）
     */
    public int acceptStack(Item item, int amount, Tile tile, com.phoenix.game.entities.type.BaseUnit source){
        if(acceptItem(item, tile, tile) && hasItems && (source == null || source.getTeam() == tile.getTeam())){
            return Math.min(getMaximumAccepted(tile, item) - tile.entity.items.get(item), amount);
        }else{
            return 0;
        }
    }

    /**
     * 接收一叠物品（对应原版 {@code Block.handleStack}）：直接加进本方块库存。
     * <p>刻意偏离：原版会调 {@code tile.entity.noSleep()}（方块休眠机制），本项目没有该机制 → 省略。
     */
    public void handleStack(Item item, int amount, Tile tile, com.phoenix.game.entities.type.BaseUnit source){
        if(tile.entity != null && tile.entity.items != null){
            tile.entity.items.add(item, amount);
        }
    }

    /**
     * 单位站上本方块时的每帧回调（对应原版 {@code Block.unitOn}，mindustry/world/Block.java:361）。
     * 由 {@code BaseUnit.updateVelocity()} 的地面分支调用（对应原版 Unit.java:226 的调用点），
     * 飞行分支不调用。默认空实现，由具体方块覆写（如 {@code Conveyor} 沿带推动单位）。
     * <p>⚠ 本项目 {@code ShockMine}/{@code UnitFactory} 此前已有各自等价的"单位站上"检测实现
     * （刻意偏离、未改走本钩子）——本钩子只供新覆写使用。
     */
    public void unitOn(Tile tile, com.phoenix.game.entities.type.BaseUnit unit){
    }

    /**
     * 物品飞向本方块时的落点偏移（对应原版 {@code Block.getStackOffset}）：默认无偏移，由具体方块覆写。
     * <p>调用点：{@code InputHandler.transferInventory}（玩家投递物品的飞行动画起点/落点）。
     */
    public void getStackOffset(Item item, Tile tile, com.badlogic.gdx.math.Vector2 trns){
    }

    /** 是否允许把物品丢向某方块（默认允许）。 */
    public boolean canDump(Tile tile, Tile to, Item item){
        return true;
    }

    /**
     * 尝试把物品交给邻接表里的任一建筑；无处可去则存入自身库存。
     * <p>对应原版 {@code BlockStorage.offloadNear}：用 {@code tile.rotation()} 当轮询起点，
     * 每试一个就把 rotation 前移一格。无朝向的机器（钻头/合成器）rotation 无意义，正好当指针用。
     * @param tile 本方块所在瓦片
     * @param item 待交出物品
     */
    public void offloadNear(Tile tile, Item item){
        if(tile.entity == null){
            handleItem(item, tile, tile);
            return;
        }

        Array<Tile> proximity = tile.entity.proximity;
        int dump = tile.rotation();

        for(int i = 0; i < proximity.size; i++){
            incrementDump(tile, proximity.size);
            Tile other = proximity.get((i + dump) % proximity.size);
            Tile in = Edges.getFacingEdge(tile, other);

            if(other.getTeam() == tile.getTeam() && other.block().acceptItem(item, other, in) && canDump(tile, other, item)){
                other.block().handleItem(item, other, in);
                return;
            }
        }

        handleItem(item, tile, tile);
    }

    /**
     * 尝试把物品交给正前方（按 rotation）建筑。
     * <p>注意用 {@link Tile#front()}（会解析多格链接）：多格建筑只有中心瓦片有实体，
     * 直接拿卫星瓦片调 acceptItem 会因为 entity 为 null 而失败。
     * @param tile 本方块所在瓦片
     * @param item 待交出物品
     * @return true 表示已成功交出
     */
    public boolean offloadDir(Tile tile, Item item){
        Tile other = tile.front();
        if(other != null && other.getTeam() == tile.getTeam() && other.block().acceptItem(item, other, tile)){
            other.block().handleItem(item, other, tile);
            return true;
        }
        return false;
    }

    /**
     * 尝试从自身库存中丢出 1 个指定物品给相邻建筑。
     * @param tile 本方块所在瓦片
     * @param item 待丢出物品
     * @return true 表示已成功丢出
     */
    public boolean tryDump(Tile tile, Item item){
        if(tile.entity == null || tile.entity.items == null || !tile.entity.items.has(item, 1)) return false;

        Array<Tile> proximity = tile.entity.proximity;
        if(proximity.size == 0) return false;

        int dump = tile.rotation();

        for(int i = 0; i < proximity.size; i++){
            Tile other = proximity.get((i + dump) % proximity.size);
            Tile in = Edges.getFacingEdge(tile, other);

            if(other.getTeam() == tile.getTeam() && other.block().acceptItem(item, other, in) && canDump(tile, other, item)){
                other.block().handleItem(item, other, in);
                tile.entity.items.remove(item, 1);
                incrementDump(tile, proximity.size);
                return true;
            }

            incrementDump(tile, proximity.size);
        }

        return false;
    }

    /**
     * 尝试把自身库存中的任意物品丢向相邻建筑。
     * @param tile 本方块所在瓦片
     * @return true 表示本帧至少成功丢出 1 个物品
     */
    public boolean tryDump(Tile tile){
        if(tile.entity == null || tile.entity.items == null || tile.entity.items.total() <= 0) return false;

        Array<Tile> proximity = tile.entity.proximity;
        if(proximity.size == 0) return false;

        int dump = tile.rotation();

        for(int i = 0; i < proximity.size; i++){
            Tile other = proximity.get((i + dump) % proximity.size);
            Tile in = Edges.getFacingEdge(tile, other);

            for(int ii = 0; ii < Items.all.size; ii++){
                Item item = Items.all.get(ii);

                if(other.getTeam() == tile.getTeam() && tile.entity.items.has(item, 1)
                    && other.block().acceptItem(item, other, in) && canDump(tile, other, item)){
                    other.block().handleItem(item, other, in);
                    tile.entity.items.remove(item, 1);
                    incrementDump(tile, proximity.size);
                    return true;
                }
            }

            incrementDump(tile, proximity.size);
        }

        return false;
    }

    // ---- 液体接口（参照 Mindustry mindustry.world.BlockStorage 的液体部分） ----

    /**
     * 是否接受该液体进入本方块（对应原版 {@code BlockStorage.acceptLiquid}，判据已照原版补齐）：
     * {@code hasLiquids && 没装满 && consumes.liquidfilters.get(liquid.id)}（本方块声明消耗该液体才收）。
     * <p>即"只收自己消耗器声明过的液体"——纯产出方（如 melter 产熔渣、sporePress 产油）不会收下自己
     * 或别人的液体，这与原版一致（此前刻意缺该判据是过渡状态，见 {@code 转换.md}）。
     * <p>以下方块靠**自己的 acceptLiquid 覆写**绕过该判据（与原版一致，它们本来就不声明液体消耗器）：
     * {@code Conduit}/{@code ArmoredConduit}/{@code LiquidRouter}/{@code LiquidVoid}/{@code Incinerator}/
     * {@code LiquidTurret}；{@code LiquidBridge}/{@code LiquidExtendingBridge} 走 {@code ItemBridge.acceptLiquid}；
     * 卫星格走 {@code BlockPart.acceptLiquid}（转发到中心格）。
     * <p>⚠ {@code source} 允许为 null（本项目约定，原版会 NPE）；本判据不用 source，故不受影响。
     */
    public boolean acceptLiquid(Tile tile, Tile source, Liquid liquid, float amount){
        return hasLiquids && tile.entity != null && tile.entity.liquids != null
            && tile.entity.liquids.get(liquid) + amount < liquidCapacity
            && consumes.liquidfilters.get(liquid.id);
    }

    /** 接收液体（默认存入本方块 LiquidModule），对应原版 {@code BlockStorage.handleLiquid}。 */
    public void handleLiquid(Tile tile, Tile source, Liquid liquid, float amount){
        if(tile.entity != null && tile.entity.liquids != null){
            tile.entity.liquids.add(liquid, amount);
        }
    }

    /** 是否允许把液体丢向某方块（对应原版 {@code BlockStorage.canDumpLiquid}，默认允许）。 */
    public boolean canDumpLiquid(Tile tile, Tile to, Liquid liquid){
        return true;
    }

    /**
     * @return 液体实际流入的下一格（对应原版 {@code BlockStorage.getLiquidDestination}）：
     * 普通方块就是自身；分液器/溢流门等会覆写它来改变流向。
     */
    public Tile getLiquidDestination(Tile tile, Tile from, Liquid liquid){
        return tile;
    }

    /**
     * 累加本方块的各格地板属性（对应原版 {@code Block.sumAttribute}）：
     * 培育机取 {@code spores}、抽水机类取 {@code water}/{@code oil}。
     * <p>原版用 {@code tile.getLinkedTilesAs(this, tempTiles)} 复用缓存数组；本项目没有该数组，
     * 就地按多格范围遍历（与 {@code Pump.linkedTiles} 同一套范围算法）。
     */
    public float sumAttribute(com.phoenix.game.world.meta.Attribute attr, int x, int y){
        Tile tile = com.phoenix.game.Vars.world == null ? null : com.phoenix.game.Vars.world.tile(x, y);
        if(tile == null) return 0f;

        float sum = 0f;
        int offset = -(size - 1) / 2;
        for(int dx = 0; dx < size; dx++){
            for(int dy = 0; dy < size; dy++){
                Tile other = tile.getNearby(dx + offset, dy + offset);
                if(other != null) sum += other.floor().attributes.get(attr);
            }
        }
        return sum;
    }

    /**
     * @return 该多格方块覆盖面里"非液体地板"格数占比（对应原版 {@code Block.percentSolid}）。
     * <p>用于抽液机放置预览的产量预估。
     */
    public float percentSolid(int x, int y){
        Tile tile = com.phoenix.game.Vars.world == null ? null : com.phoenix.game.Vars.world.tile(x, y);
        if(tile == null) return 0f;

        float sum = 0f;
        int offset = -(size - 1) / 2;
        for(int dx = 0; dx < size; dx++){
            for(int dy = 0; dy < size; dy++){
                Tile other = tile.getNearby(dx + offset, dy + offset);
                if(other != null && !other.floor().isLiquid) sum += 1f;
            }
        }
        return sum / size / size;
    }

    /**
     * 尝试把自身液体丢给相邻建筑（对应原版 {@code BlockStorage.tryDumpLiquid}）。
     * <p>只流向"充满度比自己低"的邻居，避免来回震荡。
     */
    public void tryDumpLiquid(Tile tile, Liquid liquid){
        if(tile.entity == null || tile.entity.liquids == null || tile.entity.liquids.get(liquid) <= 0f) return;

        Array<Tile> proximity = tile.entity.proximity;
        if(proximity.size == 0) return;

        int dump = tile.rotation();

        for(int i = 0; i < proximity.size; i++){
            incrementDump(tile, proximity.size);
            Tile other = proximity.get((i + dump) % proximity.size);
            Tile in = Edges.getFacingEdge(tile, other);

            //把"要丢向的格子"换成液体真正流入的那一格（对应原版 BlockStorage.tryDumpLiquid 里的
            //  other = other.block().getLiquidDestination(other, in, liquid);）。
            //⚠ 必须放在下面的可倾倒判定**之前**：分液交叉口/溢流闸门自己没有液体消耗器（liquidfilters 为空），
            //  acceptLiquid 会返回 false；原版靠这里的重定向把液流绕过它们直接送到对侧。
            other = other.block().getLiquidDestination(other, in, liquid);

            if(other != null && other.getTeam() == tile.getTeam() && other.block().hasLiquids
                && other.entity != null && other.entity.liquids != null && canDumpLiquid(tile, other, liquid)){
                float ofract = other.entity.liquids.get(liquid) / other.block().liquidCapacity;
                float fract = tile.entity.liquids.get(liquid) / liquidCapacity;

                if(ofract < fract){
                    tryMoveLiquid(tile, in, other, (fract - ofract) * liquidCapacity / 2f, liquid);
                }
            }
        }
    }

    /**
     * 定量把液体从本方块移到 next（对应原版 {@code BlockStorage.tryMoveLiquid(Tile, Tile, Tile, float, Liquid)}）。
     */
    public void tryMoveLiquid(Tile tile, Tile tileSource, Tile next, float amount, Liquid liquid){
        if(next == null || next.entity == null || next.entity.liquids == null) return;

        float flow = Math.min(next.block().liquidCapacity - next.entity.liquids.get(liquid) - 0.001f, amount);

        if(flow > 0f && next.block().acceptLiquid(next, tileSource, liquid, flow)){
            next.block().handleLiquid(next, tileSource, liquid, flow);
            tile.entity.liquids.remove(liquid, flow);
        }
    }

    /**
     * 尝试把液体压向指定邻居（对应原版 {@code BlockStorage.tryMoveLiquid(Tile, Tile, boolean, Liquid)}）。
     * @return 实际移动的量
     */
    public float tryMoveLiquid(Tile tile, Tile next, boolean leak, Liquid liquid){
        return tryMoveLiquid(tile, next, leak ? 1.5f : 100f, liquid);
    }

    /**
     * 尝试把液体压向指定邻居，带泄漏阻力（对应原版 {@code BlockStorage.tryMoveLiquid(Tile, Tile, float, Liquid)}）。
     * <p>当 {@code leakResistance != 100f}（即调用方声明会漏，如 {@code Conduit.leakResistance=1.5} /
     * {@code ArmoredConduit=10}）且邻居既不是实心方块也没有液体容量时，把液体按阻力渗成**水洼**
     * （对应原版 {@code Puddle.deposit}）；水洼系统已在 T6b 接线，这里恢复原版行为。
     */
    public float tryMoveLiquid(Tile tile, Tile next, float leakResistance, Liquid liquid){
        if(next == null || tile.entity == null || tile.entity.liquids == null) return 0f;

        next = next.link();
        //把流向重定向到液体真正流入的那一格（对应原版 BlockStorage.tryMoveLiquid 里的
        //  next = next.block().getLiquidDestination(next, tile, liquid);）——理由同 tryDumpLiquid
        next = next.block().getLiquidDestination(next, tile, liquid);

        if(next.getTeam() == tile.getTeam() && next.block().hasLiquids && next.entity != null
            && next.entity.liquids != null && tile.entity.liquids.get(liquid) > 0f){

            if(next.block().acceptLiquid(next, tile, liquid, 0f)){
                float ofract = next.entity.liquids.get(liquid) / next.block().liquidCapacity;
                float fract = tile.entity.liquids.get(liquid) / liquidCapacity * liquidPressure;
                float flow = Math.min(Mathf.clamp(fract - ofract) * liquidCapacity, tile.entity.liquids.get(liquid));
                flow = Math.min(flow, next.block().liquidCapacity - next.entity.liquids.get(liquid) - 0.001f);

                if(flow > 0f && ofract <= fract && next.block().acceptLiquid(next, tile, liquid, flow)){
                    next.block().handleLiquid(next, tile, liquid, flow);
                    tile.entity.liquids.remove(liquid, flow);
                    return flow;
                }
            }
        }else if(leakResistance != 100f && !next.block().solid && !next.block().hasLiquids){
            //原版 BlockStorage.tryMoveLiquid 的渗漏分支：邻居不是实心方块也没有液体容量 → 漏成水洼
            float leakAmount = tile.entity.liquids.get(liquid) / leakResistance;
            com.phoenix.game.entities.effect.Puddle.deposit(next, tile, liquid, leakAmount);
            tile.entity.liquids.remove(liquid, leakAmount);
        }
        return 0f;
    }

    /**
     * 往渲染器登记本方块的光源（对应原版 {@code Block.drawLight}，默认无光源）。
     * <p>原版基类体只处理"液体自发光"这一支：装了 {@code lightColor.a > 0} 的液体时按 size 加一个光斑
     * （转调 {@link #drawLiquidLight(Tile, Liquid, float)}）；灯/反应堆等各自覆写本方法。
     * <p>由 {@code core/Renderer.drawLights} 逐格调用（原版是 {@code BlockRenderer} 的 {@code Layer.lights} 请求）。
     */
    public void drawLight(Tile tile){
        //原版 Block.java:303-307
        if(tile != null && tile.entity != null && hasLiquids && drawLiquidLight
            && tile.entity.liquids != null && tile.entity.liquids.current() != null
            && tile.entity.liquids.current().lightColor.a > 0.001f){
            drawLiquidLight(tile, tile.entity.liquids.current(), tile.entity.liquids.smoothAmount());
        }
    }

    /**
     * 绘制液体灯光（对应原版 {@code Block.drawLiquidLight}）：液体自带 {@code lightColor} 且储量够多时，
     * 按 {@code size * 30} 的半径往渲染器登记一个光斑。
     * <p>已接线（S5a）：{@code Vars.renderer.lights} 现已存在（见 {@code graphics/LightRenderer}）。
     */
    public void drawLiquidLight(Tile tile, Liquid liquid, float amount){
        //原版 Block.java:309-318
        if(amount > 0.01f){
            Color color = liquid.lightColor;
            float opacity = color.a;
            if(opacity > 0.001f && com.phoenix.game.Vars.renderer != null){
                //原版用 tile.drawx()/drawy()（方块中心）；本项目 worldx()/worldy() 是瓦片角点 → 用 centerX/centerY
                com.phoenix.game.Vars.renderer.lights.add(centerX(tile), centerY(tile), size * 30f, color, opacity);
            }
        }
    }

    /**
     * 某内容被生产出来时调用（对应原版 {@code Block.useContent}）：战役模式下解锁该内容。
     * <p>TODO（B 类，缺依赖）：原版体是
     * {@code if(!headless && tile.getTeam() == player.getTeam() && world.isZone()) logic.handleContent(content);}，
     * 而 {@code logic.handleContent} → {@code data.unlockContent(content)} 这条解锁链本项目没有：
     * {@code GlobalData} 无 {@code isUnlocked}/{@code unlockContent}（grep 已证），{@code ContentLoader.handleContent}
     * 是"注册内容"不是"解锁内容"，两者同名但无关。注意 {@code Vars.world.isZone()} 本身**已存在**，
     * 缺的只是解锁动作，所以整个方法体仍留空。
     * <p>刻意偏离：参数从原版的 {@code UnlockableContent} 放宽到 {@code MappableContent}
     * （本项目 Liquid 继承 MappableContent，否则 {@code useContent(tile, liquid)} 无法通过编译）。
     */
    public void useContent(Tile tile, com.phoenix.game.ctype.MappableContent content){
    }

    /** 轮询指针前移（rotation 兼作 dump 起点，对应原版 incrementDump）。 */
    protected void incrementDump(Tile tile, int prox){
        if(prox > 0) tile.rotation((tile.rotation() + 1) % prox);
    }

    /**
     * 多格方块的中心偏移（对应原版 Block.offset）。
     * 偶数尺寸的几何中心落在瓦片边界上，需要偏半格；奇数尺寸中心就是锚点瓦片中心，偏移为 0。
     */
    public float offset(){
        return ((size + 1) % 2) * tilesize / 2f;
    }

    /** @return 本方块在指定锚点瓦片处的几何中心 X（锚点瓦片左下角 + offset + 半格） */
    public float centerX(Tile tile){
        return tile.worldx() + offset() + tilesize / 2f;
    }

    /** @return 本方块在指定锚点瓦片处的几何中心 Y */
    public float centerY(Tile tile){
        return tile.worldy() + offset() + tilesize / 2f;
    }

    /**
     * 本瓦片的可燃性（对应原版 {@code Block.getFlammability}）：火系统的蔓延/熄灭判据。
     * <p>判据分两支：<br>
     * ① 没有物品库存（空瓦片、墙、地板）时返回**地板液体**的可燃性——水是 0，火落在水上没有燃料，
     * 会被 {@code Fire.update} 按 8 倍速烧完（相当于被浇灭）；<br>
     * ② 有物品库存时累加库存物品的可燃性，有液体储量再按 1/3 折算进去。
     * <p>刻意偏离：原版读 {@code tile.floor().liquidDrop.flammability}（每个液体地板挂一个 Liquid 内容），
     * 本工程没有液体地板内容，改为 {@link Floor#flammability} 直接标在地板上。
     */
    public float getFlammability(Tile tile){
        if(tile == null) return 0f;

        if(!hasItems || tile.entity == null){
            Floor floor = tile.floor();
            return floor != null && floor.isLiquid && !solid ? floor.flammability : 0f;
        }

        float result = 0f;
        if(tile.entity.items != null){
            for(int i = 0; i < Items.all.size; i++){
                Item item = Items.all.get(i);
                int amount = tile.entity.items.get(item);
                if(amount > 0) result += item.flammability * amount;
            }
        }

        if(hasLiquids && tile.entity.liquids != null){
            //照原版：按液体逐种累加（LiquidModule 现在能同时装多种液体，不能只看 current）
            result += tile.entity.liquids.sum((liquid, liquidAmount) -> liquid.flammability * liquidAmount / 3f);
        }

        return result;
    }

    /** 本方块是不是地板层（对应原版 {@code Block.isFloor()}）。 */
    public boolean isFloor(){
        return this instanceof Floor;
    }

    /** 本方块是不是覆盖层（对应原版 {@code Block.isOverlay()}）。 */
    public boolean isOverlay(){
        return this instanceof OverlayFloor;
    }

    /** 当作地板使用（对应原版 {@code Block.asFloor()}）。 */
    public Floor asFloor(){
        return (Floor)this;
    }

    @Override
    public String toString(){
        return name;
    }
}
