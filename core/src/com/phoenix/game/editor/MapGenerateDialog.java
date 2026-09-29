package com.phoenix.game.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Scaling;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.func.Cons;
import com.phoenix.game.func.Prov;
import com.phoenix.game.game.Team;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.io.MapIO;
import com.phoenix.game.maps.filters.BlendFilter;
import com.phoenix.game.maps.filters.ClearFilter;
import com.phoenix.game.maps.filters.DistortFilter;
import com.phoenix.game.maps.filters.FilterOption;
import com.phoenix.game.maps.filters.GenerateFilter;
import com.phoenix.game.maps.filters.MedianFilter;
import com.phoenix.game.maps.filters.MirrorFilter;
import com.phoenix.game.maps.filters.NoiseFilter;
import com.phoenix.game.maps.filters.OreFilter;
import com.phoenix.game.maps.filters.OreMedianFilter;
import com.phoenix.game.maps.filters.RiverNoiseFilter;
import com.phoenix.game.maps.filters.ScatterFilter;
import com.phoenix.game.maps.filters.TerrainFilter;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.struct.Array;
import com.phoenix.game.ui.BorderImage;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.ui.dialogs.FloatingDialog;
import com.phoenix.game.util.Log;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Tile;
import io.anuke.mindustry.gen.Icon;
import io.anuke.mindustry.gen.Tex;

/**
 * 地图生成对话框（左侧滤镜链预览 + 右侧滤镜参数面板）。
 * 参照 Mindustry {@code mindustry.editor.MapGenerateDialog} 移植。
 *
 * <p>滤镜链直接作用在 {@link MapEditor} 的瓦片上（{@link #applyToEditor(Array)}），
 * 不经过 {@code maps/generators/*} 那套 {@code Generator} 体系 —— 与本类在原版里的用法一致。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li><b>异步生成器退化</b>：原版用 {@code arc.util.async.AsyncExecutor} 在后台线程跑滤镜、
 *       再把 {@code Pixmap} 用 {@code Core.app.post} 传回渲染线程上传纹理。本项目没有
 *       {@code AsyncExecutor} → 刻意降级：{@link #update()} 同步执行（编辑器地图通常只有几十×几十格、
 *       滤镜链也就几项，主线程跑完全够）。{@code AsyncResult}/{@code executor} 字段删除。</li>
 *   <li><b>{@code CachedTile} 本项目 {@code world.Tile} 里没有这个嵌套类</b>（原版是 {@code Tile.CachedTile}）
 *       → 本类自带一个 {@link CachedTile}（{@code extends Tile} + 覆写 {@code changed()} 为空），
 *       语义与原版一致：它只作为滤镜读取邻居瓦片的"只读视图"。</li>
 *   <li>{@code maps.readFilters(String)} / {@code maps.addDefaultOres(filters)} 本项目 {@code Maps} 已补
 *       （T8b）→ "重置"按钮与"默认矿脉"按钮照原版接线。</li>
 *   <li>{@code Core.scene} → {@link FloatingDialog#scene}；{@code Core.app.post} → {@code Gdx.app.postRunnable}；
 *       {@code Core.input.mouse()} → {@code Gdx.input} 的坐标 + y 翻转（libgdx 以上边缘为原点）。</li>
 *   <li>arc 的 {@code pane(Cons)} / {@code Cell.update} / {@code Cell.marginRight} / {@code addImageButton} /
 *       {@code addButton} / {@code visible(Boolp)} libgdx 都没有 → 自建 Table+ScrollPane、
 *       每帧回调改成 {@code act()} 覆写、按钮走 {@link #iconButton} / {@link FloatingDialog#textButton}。</li>
 *   <li>{@code BorderImage.draw()}（无参）→ libgdx 是 {@code draw(Batch, float)}；滤镜的 {@code draw(Image)}
 *       引导线照原版在绘制后追加（本项目滤镜的 {@code draw} 目前都是空实现，见 {@code GenerateFilter}）。</li>
 *   <li>{@code TextureRegion} 没有 {@code getWidth()}（本项目硬约束）→ 本类不涉及。</li>
 *   <li><b>写回编辑器时读的是 {@code blockRaw()}</b>：本项目 {@code Tile.block()} 会解析多格链接
 *       （原版 {@code block()} 就是瓦片自己存的值），用 {@code block()} 会把卫星格当成中心方块、
 *       拆散多格建筑（见 转换.md 地图批 D1c）。</li>
 *   <li>{@code icon.refresh/upOpen/downOpen} 本项目生成包 {@code Icon} 里没有 {@code upOpen/downOpen}
 *       → 刻意降级为 {@code Icon.arrowUp}/{@code Icon.arrowDown}（已查生成的 {@code Icon.java}）。</li>
 * </ul>
 */
@SuppressWarnings("unchecked")
public class MapGenerateDialog extends FloatingDialog{
    private final Prov<GenerateFilter>[] filterTypes = new Prov[]{
        NoiseFilter::new, ScatterFilter::new, TerrainFilter::new, DistortFilter::new,
        RiverNoiseFilter::new, OreFilter::new, OreMedianFilter::new, MedianFilter::new,
        BlendFilter::new, MirrorFilter::new, ClearFilter::new
    };
    private final MapEditor editor;
    private final boolean applied;

    private Pixmap pixmap;
    private Texture texture;
    private GenerateFilter.GenerateInput input = new GenerateFilter.GenerateInput();
    private Array<GenerateFilter> filters = new Array<>();
    private int scaling = Vars.mobile ? 3 : 1;
    private Table filterTable;

    private boolean generating;
    private GenTile returnTile = new GenTile();

    private GenTile[][] buffer1, buffer2;
    private Cons<Array<GenerateFilter>> applier;
    private final CachedTile ctile = new CachedTile();

    /** @param applied whether or not to use the applied in-game mode. */
    public MapGenerateDialog(MapEditor editor, boolean applied){
        super("$editor.generate");
        this.editor = editor;
        this.applied = applied;

        shown(this::setup);
        addCloseButton();
        if(applied){
            buttons.add(textButton("$editor.apply", null, () -> {
                if(Vars.ui != null){
                    Vars.ui.loadAnd(() -> {
                        apply();
                        hide();
                    });
                }else{
                    apply();
                    hide();
                }
            })).size(Scl.scl(160f), Scl.scl(64f));
        }else{
            buttons.add(textButton("$settings.reset", null, () -> {
                //原版 = filters.set(maps.readFilters(""))（构造一套默认滤镜：ScatterFilter + 默认矿脉）
                filters = Vars.maps.readFilters("");
                rebuildFilters();
                update();
            })).size(Scl.scl(160f), Scl.scl(64f));
        }
        buttons.add(textButton("$editor.randomize", null, () -> {
            for(int i = 0; i < filters.size; i++){
                filters.get(i).randomize();
            }
            update();
        })).size(Scl.scl(160f), Scl.scl(64f));

        buttons.add(imageTextButton("$add", Icon.add, null, this::showAdd)).height(Scl.scl(64f)).width(Scl.scl(140f));

        if(!applied){
            hidden(this::apply);
        }

        onResize(this::rebuildFilters);
    }

    public void show(Array<GenerateFilter> filters, Cons<Array<GenerateFilter>> applier){
        this.filters = filters;
        this.applier = applier;
        show();
    }

    public void show(Cons<Array<GenerateFilter>> applier){
        show(this.filters, applier);
    }

    /** Applies the specified filters to the editor. */
    public void applyToEditor(Array<GenerateFilter> filters){
        //writeback buffer
        GenTile[][] writeTiles = new GenTile[editor.width()][editor.height()];

        for(int x = 0; x < editor.width(); x++){
            for(int y = 0; y < editor.height(); y++){
                writeTiles[x][y] = new GenTile();
            }
        }

        for(int fi = 0; fi < filters.size; fi++){
            final GenerateFilter filter = filters.get(fi);
            input.begin(filter, editor.width(), editor.height(), editor::tile);
            //write to buffer
            for(int x = 0; x < editor.width(); x++){
                for(int y = 0; y < editor.height(); y++){
                    Tile tile = editor.tile(x, y);
                    if(tile == null) continue;
                    //本项目 block() 会解析多格链接 → 用 blockRaw()（原版 block() 就是原始值）
                    input.apply(x, y, tile.floor(), tile.blockRaw(), tile.overlay());
                    filter.apply(input);
                    writeTiles[x][y].set(input.floor, input.block, input.ore, tile.getTeam(), tile.rotation());
                }
            }

            editor.load(() -> {
                //read from buffer back into tiles
                for(int x = 0; x < editor.width(); x++){
                    for(int y = 0; y < editor.height(); y++){
                        Tile tile = editor.tile(x, y);
                        if(tile == null) continue;
                        GenTile write = writeTiles[x][y];

                        tile.rotation(write.rotation);
                        tile.setFloor((Floor)Vars.content.block(write.floor));
                        tile.setBlock(Vars.content.block(write.block));
                        tile.setTeam(Team.get(write.team));
                        tile.setOverlay(Vars.content.block(write.ore));
                    }
                }
            });
        }

        //reset undo stack as generation... messes things up
        editor.load(editor::checkLinkedTiles);
        editor.renderer().updateAll();
        editor.clearOp();
    }

    void setup(){
        if(pixmap != null){
            pixmap.dispose();
            pixmap = null;
        }
        if(texture != null){
            texture.dispose();
            texture = null;
        }

        //原版直接 editor.width() / scaling；这里加 1 的下限守卫（编辑器还没载入地图时宽高为 0）
        int pw = Math.max(1, editor.width() / scaling), ph = Math.max(1, editor.height() / scaling);
        pixmap = new Pixmap(pw, ph, Pixmap.Format.RGBA8888);
        texture = new Texture(pixmap);

        cont.clear();

        Table outer = new Table();
        outer.pad(Scl.scl(8f));

        //预览图：原版用匿名 BorderImage 覆写 draw() 追加滤镜引导线
        BorderImage preview = new BorderImage(texture){
            @Override
            public void draw(Batch batch, float parentAlpha){
                super.draw(batch, parentAlpha);
                for(int i = 0; i < filters.size; i++){
                    filters.get(i).draw(this);
                }
            }
        };
        preview.setScaling(Scaling.fit);

        //"生成中"遮罩（原版 = new Stack(){{ add(new Image(Styles.black8)); add(new Image(Icon.refresh, Scaling.none));
        //   visible(() -> generating && !updateEditorOnChange); }}）
        Stack overlay = new Stack(){
            @Override
            public void act(float delta){
                super.act(delta);
                setVisible(generating && !Vars.updateEditorOnChange);
            }
        };
        overlay.add(new Image(Styles.black8));
        Image refresh = new Image(Icon.refresh);
        refresh.setScaling(Scaling.none);
        overlay.add(refresh);

        Stack stack = new Stack();
        stack.add(preview);
        stack.add(overlay);
        outer.add(stack).grow().padRight(Scl.scl(10f));

        //滤镜列表（原版 = t.pane(p -> filterTable = p.marginRight(6)).update(pane -> {...}).grow()）
        Table paneContent = new Table();
        filterTable = paneContent;
        paneContent.padRight(Scl.scl(6f));

        ScrollPane pane = new ScrollPane(paneContent, Styles.defaultPane){
            private final Vector2 tmp = new Vector2();

            @Override
            public void act(float delta){
                super.act(delta);

                Stage stage = getStage();
                if(stage == null) return;
                //原版：别的 Dialog 拿到键盘焦点时不要抢滚轮
                if(stage.getKeyboardFocus() instanceof Dialog && stage.getKeyboardFocus() != MapGenerateDialog.this){
                    return;
                }

                //原版 = pane.stageToLocalCoordinates(Core.input.mouse())；libgdx 的 y 以上边缘为原点 → 翻转
                tmp.set(Gdx.input.getX(), Core.graphics.getHeight() - Gdx.input.getY());
                stageToLocalCoordinates(tmp);

                if(tmp.x >= 0 && tmp.y >= 0 && tmp.x <= getWidth() && tmp.y <= getHeight()){
                    stage.setScrollFocus(this);
                }else if(stage.getScrollFocus() == this){
                    stage.setScrollFocus(null);
                }
            }
        };
        pane.setScrollingDisabled(true, false);
        outer.add(pane).grow();

        cont.add(outer).grow();

        buffer1 = create();
        buffer2 = create();

        update();
        rebuildFilters();
    }

    GenTile[][] create(){
        int pw = Math.max(1, editor.width() / scaling), ph = Math.max(1, editor.height() / scaling);
        GenTile[][] out = new GenTile[pw][ph];

        for(int x = 0; x < pw; x++){
            for(int y = 0; y < ph; y++){
                out[x][y] = new GenTile();
            }
        }
        return out;
    }

    void rebuildFilters(){
        if(filterTable == null) return;

        float parentWidth = filterTable.getParent() == null ? 0f : filterTable.getParent().getWidth();
        int cols = Math.max((int)(Math.max(parentWidth, Core.graphics.getWidth() / 2f * 0.9f) / Scl.scl(290f)), 1);
        filterTable.clearChildren();
        filterTable.top().left();
        int i = 0;

        for(int fi = 0; fi < filters.size; fi++){
            final GenerateFilter filter = filters.get(fi);

            //main container
            Table box = new Table();
            box.setBackground(Tex.button);

            //icons to perform actions
            Table top = new Table();
            top.top();
            Label nameLabel = new Label(filter.name(), Styles.defaultLabel);
            nameLabel.setColor(Pal.accent);
            top.add(nameLabel).padTop(Scl.scl(5f)).growX().left();
            top.row();

            Table actions = new Table();
            actions.defaults().size(Scl.scl(50f));
            actions.add(iconButton(Icon.refresh, Styles.cleari, () -> {
                filter.randomize();
                update();
            }));
            //刻意降级：原版用 Icon.upOpen / Icon.downOpen；本项目生成包 Icon 里没有这两个 → 用 arrowUp/arrowDown
            actions.add(iconButton(Icon.arrowUp, Styles.cleari, () -> {
                int idx = filters.indexOf(filter, true);
                filters.swap(idx, Math.max(0, idx - 1));
                rebuildFilters();
                update();
            }));
            actions.add(iconButton(Icon.arrowDown, Styles.cleari, () -> {
                int idx = filters.indexOf(filter, true);
                filters.swap(idx, Math.min(filters.size - 1, idx + 1));
                rebuildFilters();
                update();
            }));
            actions.add(iconButton(Icon.trash, Styles.cleari, () -> {
                filters.removeValue(filter, true);
                rebuildFilters();
                update();
            }));
            top.add(actions);
            box.add(top).fillX();
            box.row();

            //all the options
            Table options = new Table();
            options.left().top();
            if(filter.options != null){
                for(int oi = 0; oi < filter.options.length; oi++){
                    FilterOption option = filter.options[oi];
                    option.changed = this::update;

                    Table t = new Table();
                    t.left();
                    option.build(t);

                    options.add(t).growX().left();
                    options.row();
                }
            }
            box.add(options).grow().left().pad(Scl.scl(2f)).top();

            filterTable.add(box).width(Scl.scl(280f)).pad(Scl.scl(3f)).top().left().fillY();
            if(++i % cols == 0){
                filterTable.row();
            }
        }

        if(filters.isEmpty()){
            Label empty = new Label(bundle("$filters.empty"), Styles.defaultLabel);
            empty.setWrap(true);
            filterTable.add(empty).width(Scl.scl(200f));
        }
    }

    void showAdd(){
        FloatingDialog selection = new FloatingDialog("$add");
        selection.setFillParent(false);

        Table c = selection.getContentTable();
        c.defaults().size(Scl.scl(210f), Scl.scl(60f));
        int i = 0;
        for(int k = 0; k < filterTypes.length; k++){
            final GenerateFilter filter = filterTypes[k].get();

            if(!applied && filter.buffered) continue;

            c.add(textButton(filter.name(), null, () -> {
                filters.add(filter);
                rebuildFilters();
                update();
                selection.hide();
            }));
            if(++i % 2 == 0) c.row();
        }

        c.add(textButton("$filter.defaultores", null, () -> {
            //原版 = maps.addDefaultOres(filters)
            Vars.maps.addDefaultOres(filters);
            rebuildFilters();
            update();
            selection.hide();
        }));

        selection.addCloseButton();
        selection.show();
    }

    GenTile dset(Tile tile){
        returnTile.set(tile);
        return returnTile;
    }

    void apply(){
        buffer1 = null;
        buffer2 = null;
        generating = false;
        if(pixmap != null){
            pixmap.dispose();
            pixmap = null;
        }
        if(texture != null){
            texture.dispose();
            texture = null;
        }

        if(applier != null){
            applier.get(filters);
        }
    }

    /**
     * 重跑滤镜链并刷新预览像素。
     * <p>刻意降级：原版把这段丢进 {@code AsyncExecutor} 后台线程、并用 {@code Core.app.post} 回主线程上传纹理；
     * 本项目没有 {@code AsyncExecutor} → 同步执行（编辑器地图规模小，主线程无压力）。
     */
    void update(){
        if(generating || pixmap == null) return;

        generating = true;
        try{
            Array<GenerateFilter> copy = new Array<>(filters);

            if(!filters.isEmpty()){
                //write to buffer1 for reading
                for(int px = 0; px < pixmap.getWidth(); px++){
                    for(int py = 0; py < pixmap.getHeight(); py++){
                        buffer1[px][py].set(editor.tile(px * scaling, py * scaling));
                    }
                }
            }

            for(int fi = 0; fi < copy.size; fi++){
                final GenerateFilter filter = copy.get(fi);
                input.begin(filter, editor.width(), editor.height(),
                    (x, y) -> buffer1[Mathf.clamp(x / scaling, 0, pixmap.getWidth() - 1)]
                                    [Mathf.clamp(y / scaling, 0, pixmap.getHeight() - 1)].tile());

                //read from buffer1 and write to buffer2
                for(int px = 0; px < pixmap.getWidth(); px++){
                    for(int py = 0; py < pixmap.getHeight(); py++){
                        int x = px * scaling, y = py * scaling;
                        GenTile tile = buffer1[px][py];
                        input.apply(x, y, Vars.content.block(tile.floor), Vars.content.block(tile.block), Vars.content.block(tile.ore));
                        filter.apply(input);
                        buffer2[px][py].set(input.floor, input.block, input.ore, Team.get(tile.team), tile.rotation);
                    }
                }
                for(int px = 0; px < pixmap.getWidth(); px++){
                    for(int py = 0; py < pixmap.getHeight(); py++){
                        buffer1[px][py].set(buffer2[px][py]);
                    }
                }
            }

            for(int px = 0; px < pixmap.getWidth(); px++){
                for(int py = 0; py < pixmap.getHeight(); py++){
                    int color;
                    //get result from buffer1 if there's filters left, otherwise get from editor directly
                    if(filters.isEmpty()){
                        Tile tile = editor.tile(px * scaling, py * scaling);
                        if(tile == null) continue;
                        //本项目 block() 会解析多格链接 → 预览也按 blockRaw() 取色（与写回一致）
                        color = MapIO.colorFor(tile.floor(), tile.blockRaw(), tile.overlay(), Team.derelict);
                    }else{
                        GenTile tile = buffer1[px][py];
                        color = MapIO.colorFor(Vars.content.block(tile.floor), Vars.content.block(tile.block),
                            Vars.content.block(tile.ore), Team.derelict);
                    }
                    //libgdx 的 Pixmap 没有 draw(int,int,int) → drawPixel
                    pixmap.drawPixel(px, pixmap.getHeight() - 1 - py, color);
                }
            }

            if(texture != null && pixmap != null){
                texture.draw(pixmap, 0, 0);
            }
        }catch(Exception e){
            Log.err(e);
        }
        generating = false;
    }

    /** 对应 arc 的 {@code Table.addImageButton(icon, style, action)}（libgdx 的 ImageButton 没有该构造器）。 */
    private static ImageButton iconButton(Drawable icon, ImageButton.ImageButtonStyle style, final Runnable action){
        ImageButton.ImageButtonStyle bs = new ImageButton.ImageButtonStyle(style);
        //imageUp 必须设：否则 hover/按下状态一变 updateImage() 会把 drawable 重置为 null
        bs.imageUp = icon;
        ImageButton button = new ImageButton(bs);
        button.getImage().setDrawable(icon);
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                action.run();
            }
        });
        return button;
    }

    private class GenTile{
        public byte team, rotation;
        public short block, floor, ore;

        public void set(Block floor, Block wall, Block ore, Team team, int rotation){
            this.floor = floor.id;
            this.block = wall.id;
            this.ore = ore.id;
            this.team = (byte)team.id;
            this.rotation = (byte)rotation;
        }

        public void set(GenTile other){
            this.floor = other.floor;
            this.block = other.block;
            this.ore = other.ore;
            this.team = other.team;
            this.rotation = other.rotation;
        }

        public GenTile set(Tile other){
            if(other == null) return this;
            //本项目 block() 会解析多格链接 → 用 blockRaw()（原版 block() 就是瓦片自己存的值）
            set(other.floor(), other.blockRaw(), other.overlay(), other.getTeam(), other.rotation());
            return this;
        }

        Tile tile(){
            ctile.setFloor((Floor)Vars.content.block(floor));
            ctile.setBlock(Vars.content.block(block));
            ctile.setOverlay(Vars.content.block(ore));
            ctile.rotation(rotation);
            ctile.setTeam(Team.get(team));
            return ctile;
        }
    }

    /**
     * 滤镜读取邻居瓦片用的"只读瓦片"。
     * <p>原版是 {@code mindustry.world.Tile.CachedTile}（{@code extends Tile} 且 {@code changed()} 为空）；
     * 本项目 {@code world.Tile} 没有这个嵌套类 → 在这里自带一个，语义一致。
     */
    private static class CachedTile extends Tile{
        CachedTile(){
            super(0, 0);
        }

        @Override
        protected void changed(){
            //nothing.
        }
    }
}
