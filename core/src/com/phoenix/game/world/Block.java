package com.phoenix.game.world;
import com.phoenix.game.world.meta.BlockFlag;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.content.Items;
import com.phoenix.game.core.Core;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.type.Category;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.world.blocks.power.PowerGraph;
import com.phoenix.game.world.modules.PowerModule;

import java.util.EnumSet;
import java.util.function.Supplier;

import static com.phoenix.game.Vars.tilesize;

/**
 * 最小实现：方块。参照 Mindustry mindustry.world.Block 移植（去掉建筑/合成/多格等复杂机制）。
 */
public class Block {
    public final String name;
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
    /** 血量，-1 表示不可破坏 */
    public int health = -1;
    /** 方块标记，用于 AI 索引 */
    public EnumSet<BlockFlag> flags = EnumSet.noneOf(BlockFlag.class);
    /** 显示颜色 */
    public Color color = Color.WHITE.cpy();

    public TextureRegion region;
    public TextureRegion[] variantRegions = new TextureRegion[0];
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

    public Block(String name){
        this.name = name;
    }

    /** 若本方块有建筑实体则创建之，否则返回 null。 */
    public TileEntity newEntity(){
        return entityType == null ? null : entityType.get();
    }

    /** 加载贴图（需要在 Core.atlas 就绪后调用）。 */
    public void load(){
        variantRegions = loadRegions(textureName != null ? textureName : name, 0);
        if(variantRegions.length > 0) region = variantRegions[0];
        //贴图重载后图标缓存失效（对应原版 Arrays.fill(cicons, null)）
        java.util.Arrays.fill(cicons, null);
        generatedIcons = null;
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

    /** 图标层缓存（按 Cicon 尺寸），对应原版 Block.cicons。 */
    private final TextureRegion[] cicons = new TextureRegion[Cicon.values().length];
    /** 图标层数组缓存（base/rotator/top…），对应原版 Block.generatedIcons。 */
    private TextureRegion[] generatedIcons;

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

    /** 返回用于 HUD/详情的图标区域；图集没有对应尺寸图时回退到基础区域。 */
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

    /** 原版 getDisplayIcon(Tile) 的 libgdx 等价实现。 */
    public TextureRegion getDisplayIcon(Tile tile){
        return icon(Cicon.medium);
    }

    /** @return 该瓦片的小地图自定义颜色，0 表示走 {@code MapIO.colorFor} 的默认规则。 */
    public int minimapColor(Tile tile){
        return 0;
    }

    /** @return 本地化显示名（对应原版 localizedName）；缺失时回退到内部名。 */
    public String localizedName(){
        if(Core.bundle == null) return name;
        String key = "block." + name + ".name";
        String value = Core.bundle.get(key);
        //I18NBundle 缺键时返回键本身，这里视为缺失并回退
        return value.equals(key) ? name : value;
    }

    /** @return 悬停瓦片时显示的名称（对应原版 getDisplayName(Tile)）。 */
    public String getDisplayName(Tile tile){
        return localizedName();
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

        if(hasLiquids){
            addLiquidBar(entity, table);
        }

        if(hasPower && entity.power != null){
            addPowerBar(entity, table);
        }

        if(hasItems && entity.items != null && itemCapacity > 0 && itemsBarEnabled){
            addItemsBar(entity, table);
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
                return empty ? "液体" : String.format("液体：%.1f", entity.liquids.amount);
            },
            () -> entity.liquids != null && entity.liquids.current != null
                ? entity.liquids.current.color : com.phoenix.game.graphics.Pal.lightishGray,
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
        //有血量的建筑（如墙体）也需要实体来承载血量，即使没有每帧逻辑
        return update || destructible;
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

    /** 物品是否"瞬移"（Sorter/OverflowGate 这类无缓冲直通方块，用于禁止链式堆叠）。 */
    public boolean instantTransfer;
    /** 是否可配置（放置后点选设置，如 Sorter 选物品、Bridge 选目标）。 */
    public boolean configurable;
    /** 是否能被装卸器（Unloader）取物（对应原版 {@code Block.unloadable}）。 */
    public boolean unloadable = true;

    /** 玩家放置后回调（对应原版 {@code Block.playerPlaced}）。 */
    public void playerPlaced(Tile tile){
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

    /** 是否隐藏不绘制（如 BlockPart 卫星瓦片）。 */
    public boolean isHidden(){
        return false;
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
     * 绘制叠在方块贴图之上的内容（如传送带上的物品）。默认不绘制。
     * @param tile 本方块所在瓦片
     */
    public void drawLayer(Tile tile){
    }

    /** @return 是否向相邻建筑输出物品（对应原版 Block.outputsItems），传送带贴图拼接会用到。 */
    public boolean outputsItems(){
        return hasItems;
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

    /** 是否连接电网（决定 TileEntity 是否分配 PowerModule）。 */
    public boolean hasPower;
    /** 生产功率（每帧）。 */
    public float powerProduction;
    /** 消耗功率（每帧）。 */
    public float powerConsumption;

    /** @return 方块每帧发电量。 */
    public float getPowerProduction(Tile tile){
        return powerProduction;
    }

    /** @return 方块每帧耗电量。 */
    public float getPowerNeeded(Tile tile){
        return powerConsumption;
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

    @Override
    public String toString(){
        return name;
    }
}
