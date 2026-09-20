package com.phoenix.game.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.BuildRequest;
import com.phoenix.game.world.Pos;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.BuildBlock;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * 蓝图管理。参照 Mindustry mindustry.game.Schematics 移植。
 * <p>文件格式与**原版 .msch 完全一致**（这样能直接吃原版蓝图、导出的也能被原版读）：
 * <pre>
 *   'm' 's' 'c' 'h'  0x00            ← 明文头 + 版本号
 *   zlib 压缩的正文：
 *     short width, short height
 *     byte  标签数, 每项 (UTF key, UTF value)
 *     byte  方块字典长度, 每项 UTF 方块名
 *     int   瓦片数, 每项 (byte 字典下标, int Pos 打包坐标, int 配置, byte 朝向)
 * </pre>
 * <p>未移植：预览图生成（原版用 FrameBuffer 渲染一张 png）、创意工坊发布、目录监听。
 */
public class Schematics{
    /** 文件明文头（ASCII "msch"）。 */
    private static final byte[] header = {'m', 's', 'c', 'h'};
    /** 格式版本，与原版一致。 */
    private static final byte version = 0;
    /** 蓝图目录（应用本地目录下）。 */
    private static final String SCHEMATIC_DIR = "schematics";
    /** 蓝图文件扩展名（不含点）。 */
    public static final String schematicExtension = "msch";
    /** 单张蓝图的最大边长（格），与原版一致。 */
    public static final int maxSchematicSize = 128;

    /** 预览图每格的像素数（对应原版 resolution）。 */
    public static final int resolution = 32;
    /** 预览图四周留白的格数（对应原版 padding）。 */
    public static final int padding = 2;

    /** 已加载的蓝图，按名称排序。 */
    private final Array<Schematic> all = new Array<>();

    /** 预览图缓存（懒生成，按蓝图缓存 FrameBuffer）。 */
    private final ObjectMap<Schematic, com.badlogic.gdx.graphics.glutils.FrameBuffer> previews = new ObjectMap<>();

    public Array<Schematic> all(){
        return all;
    }

    /** 扫描蓝图目录并全部读入（应用启动后调用一次）。 */
    public void load(){
        all.clear();

        File dir = directory();
        if(!dir.exists() || dir.listFiles() == null) return;

        File[] files = dir.listFiles((d, name) -> name.endsWith("." + schematicExtension));
        if(files == null) return;

        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        for(File file : files){
            try{
                all.add(read(file));
            }catch(Exception e){
                //单个坏文件不该拖垮整个列表（原版同样跳过并打日志）
                System.err.println("DBG 蓝图读取失败 " + file.getName() + ": " + e);
            }
        }
    }

    /** @return 蓝图目录（不存在则创建）。 */
    public File directory(){
        File dir = Gdx.files.local(SCHEMATIC_DIR).file();
        if(!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** 加入列表并写入磁盘（重名会覆盖同名文件）。 */
    public void add(Schematic schematic){
        if(schematic.name().equals("unknown")){
            schematic.tags.put("name", "schematic-" + System.currentTimeMillis());
        }
        all.add(schematic);
        all.sort((a, b) -> a.name().compareTo(b.name()));
        saveChanges(schematic);
        //顺手导出一张预览 PNG（同名 .png），这样蓝图目录里能直接看到长什么样。
        //原版只在内存里缓存预览、发布到创意工坊时才写 PNG；这里落盘是为了让文件系统可见。
        savePreview(schematic, previewFile(schematic));
    }

    /** @return 蓝图对应的预览图文件（与 .msch 同目录同名，扩展名 png）。 */
    public File previewFile(Schematic schematic){
        String base = schematic.file != null
            ? schematic.file.getName()
            : schematic.name() + "." + schematicExtension;
        int dot = base.lastIndexOf('.');
        if(dot > 0) base = base.substring(0, dot);
        return new File(directory(), base + ".png");
    }

    /** 把蓝图写回它自己的文件（原版 saveChanges）。 */
    public void saveChanges(Schematic schematic){
        try{
            File file = schematic.file != null
                ? schematic.file
                : new File(directory(), schematic.name() + "." + schematicExtension);
            write(schematic, file);
            schematic.file = file;
        }catch(Exception e){
            System.err.println("DBG 蓝图保存失败: " + e);
        }
    }

    /**
     * 从世界抓一块区域生成蓝图（对应原版 {@code Schematics.create}）。
     * <p>只收"有实体且可见"的方块：自动跳过空气、自然地形和多格卫星格；
     * 多格建筑按整体边界扩张范围，避免切掉一半。
     * @param x1 起点格 X（含）
     * @param y1 起点格 Y（含）
     * @param x2 终点格 X（含）
     * @param y2 终点格 Y（含）
     */
    public Schematic create(int x1, int y1, int x2, int y2){
        if(Vars.world == null) return new Schematic(new Array<>(), new ObjectMap<>(), 1, 1);

        int minx = Math.min(x1, x2), maxx = Math.max(x1, x2);
        int miny = Math.min(y1, y2), maxy = Math.max(y1, y2);

        //先把范围按多格建筑的完整占地扩张一圈
        for(int cx = minx; cx <= maxx; cx++){
            for(int cy = miny; cy <= maxy; cy++){
                Tile tile = Vars.world.ltile(cx, cy);
                if(tile == null || tile.entity == null) continue;

                Block block = tile.block();
                if(block == null || block == Blocks.air) continue;

                int top = block.size / 2;
                int bot = block.size % 2 == 1 ? -block.size / 2 : -(block.size - 1) / 2;
                minx = Math.min(tile.x + bot, minx);
                miny = Math.min(tile.y + bot, miny);
                maxx = Math.max(tile.x + top, maxx);
                maxy = Math.max(tile.y + top, maxy);
            }
        }

        int width = maxx - minx + 1, height = maxy - miny + 1;
        int offsetX = -minx, offsetY = -miny;

        Array<Schematic.Stile> tiles = new Array<>();
        com.badlogic.gdx.utils.IntSet counted = new com.badlogic.gdx.utils.IntSet();

        for(int cx = minx; cx <= maxx; cx++){
            for(int cy = miny; cy <= maxy; cy++){
                Tile tile = Vars.world.ltile(cx, cy);
                if(tile == null || tile.entity == null) continue;
                if(counted.contains(tile.pos())) continue;

                Block block = tile.block();
                if(block == null || block == Blocks.air) continue;
                //在建占位方块不算建筑；卫星格由 link() 归到中心格，不会重复
                if(block instanceof BuildBlock) continue;

                int config = tile.entity.config();
                if(block.posConfig && config >= 0){
                    //"另一格的位置"类配置要跟着蓝图一起平移，否则粘贴后连到错误的地方
                    config = Pos.get(Pos.x(config) + offsetX, Pos.y(config) + offsetY);
                }

                tiles.add(new Schematic.Stile(block, tile.x + offsetX, tile.y + offsetY, config, tile.rotation()));
                counted.add(tile.pos());
            }
        }

        return new Schematic(tiles, new ObjectMap<>(), width, height);
    }

    /**
     * 把蓝图转成一串建造请求（对应原版 {@code Schematics.toRequests}）。
     * <p>坐标以 (x, y) 为中心展开；带位置的配置（桥的连接目标）也按同样的偏移平移。
     */
    public Array<BuildRequest> toRequests(Schematic schematic, int x, int y){
        Array<BuildRequest> requests = new Array<>();
        int ox = x - schematic.width / 2, oy = y - schematic.height / 2;

        for(int i = 0; i < schematic.tiles.size; i++){
            Schematic.Stile stile = schematic.tiles.get(i);
            if(stile.block == null || stile.block == Blocks.air) continue;

            BuildRequest req = new BuildRequest(stile.x + ox, stile.y + oy, stile.rotation, stile.block);
            if(stile.config >= 0){
                req.hasConfig = true;
                req.config = stile.block.posConfig
                    ? Pos.get(Pos.x(stile.config) + ox, Pos.y(stile.config) + oy)
                    : stile.config;
            }
            requests.add(req);
        }
        return requests;
    }

    // ---- 文件读写（格式与原版 .msch 一致） ----

    /** 从文件读一张蓝图（对应原版 {@code Schematics.read(Fi)}）。 */
    public Schematic read(File file) throws IOException{
        try(InputStream in = new FileInputStream(file)){
            Schematic s = read(in);
            if(!s.tags.containsKey("name")){
                String name = file.getName();
                int dot = name.lastIndexOf('.');
                s.tags.put("name", dot > 0 ? name.substring(0, dot) : name);
            }
            s.file = file;
            return s;
        }
    }

    /** 从流读一张蓝图。流不需要预先解压，本方法自己处理明文头与 zlib 正文。 */
    public static Schematic read(InputStream input) throws IOException{
        for(byte b : header){
            if(input.read() != b){
                throw new IOException("Not a schematic file (missing header).");
            }
        }

        int ver = input.read();
        if(ver != version){
            throw new IOException("Unknown version: " + ver);
        }

        try(DataInputStream stream = new DataInputStream(new InflaterInputStream(input))){
            short width = stream.readShort(), height = stream.readShort();

            ObjectMap<String, String> tags = new ObjectMap<>();
            byte tagCount = stream.readByte();
            for(int i = 0; i < tagCount; i++){
                tags.put(stream.readUTF(), stream.readUTF());
            }

            IntMap<Block> blockDict = new IntMap<>();
            byte dictLength = stream.readByte();
            for(int i = 0; i < dictLength; i++){
                Block block = blockByName(stream.readUTF());
                blockDict.put(i, block == null ? Blocks.air : block);
            }

            int total = stream.readInt();
            Array<Schematic.Stile> tiles = new Array<>(total);
            for(int i = 0; i < total; i++){
                Block block = blockDict.get(stream.readByte());
                int position = stream.readInt();
                int config = stream.readInt();
                byte rotation = stream.readByte();

                if(block != null && block != Blocks.air){
                    tiles.add(new Schematic.Stile(block, Pos.x(position), Pos.y(position), config, rotation));
                }
            }

            return new Schematic(tiles, tags, width, height);
        }
    }

    /** 写一张蓝图到文件。 */
    public static void write(Schematic schematic, File file) throws IOException{
        try(OutputStream out = new FileOutputStream(file)){
            write(schematic, out);
        }
    }

    /** 把蓝图写成原版 .msch 字节流。 */
    public static void write(Schematic schematic, OutputStream output) throws IOException{
        output.write(header);
        output.write(version);

        try(DataOutputStream stream = new DataOutputStream(new DeflaterOutputStream(output))){
            stream.writeShort(schematic.width);
            stream.writeShort(schematic.height);

            stream.writeByte(schematic.tags.size);
            for(ObjectMap.Entry<String, String> e : schematic.tags.entries()){
                stream.writeUTF(e.key);
                stream.writeUTF(e.value);
            }

            //方块字典：只写用到的方块名，瓦片里存下标（原版为了省空间这么做）
            Array<Block> dict = new Array<>();
            for(int i = 0; i < schematic.tiles.size; i++){
                Block block = schematic.tiles.get(i).block;
                if(block != null && !dict.contains(block, true)) dict.add(block);
            }

            stream.writeByte(dict.size);
            for(int i = 0; i < dict.size; i++){
                stream.writeUTF(dict.get(i).name);
            }

            stream.writeInt(schematic.tiles.size);
            for(int i = 0; i < schematic.tiles.size; i++){
                Schematic.Stile tile = schematic.tiles.get(i);
                stream.writeByte(dict.indexOf(tile.block, true));
                stream.writeInt(Pos.get(tile.x, tile.y));
                stream.writeInt(tile.config);
                stream.writeByte(tile.rotation);
            }
        }
    }

    // ---- 预览图（对应原版 Schematics.getBuffer / getPreview / savePreview） ----

    /** @return 是否已经为该蓝图生成过预览。 */
    public boolean hasPreview(Schematic schematic){
        return previews.containsKey(schematic);
    }

    /** @return 预览图纹理；GL 未就绪或图集缺失时返回 null。 */
    public com.badlogic.gdx.graphics.Texture getPreview(Schematic schematic){
        com.badlogic.gdx.graphics.glutils.FrameBuffer buffer = getBuffer(schematic);
        return buffer == null ? null : buffer.getColorBufferTexture();
    }

    /**
     * 把预览图写成 PNG（对应原版 {@code Schematics.savePreview}）。
     * <p>做法同原版：让 FBO 保持绑定，直接 {@code glReadPixels} 读回像素。
     * <p>注意 FBO 读回的像素是**自下而上**的，写盘前要翻正，否则图会上下颠倒。
     */
    public boolean savePreview(Schematic schematic, File file){
        com.badlogic.gdx.graphics.glutils.FrameBuffer buffer = getBuffer(schematic);
        if(buffer == null) return false;

        com.badlogic.gdx.graphics.Pixmap raw = null, flipped = null;
        try{
            int w = buffer.getWidth(), h = buffer.getHeight();

            //让预览 FBO 处于绑定状态，glReadPixels 才能读到它
            buffer.begin();
            raw = com.badlogic.gdx.graphics.Pixmap.createFromFrameBuffer(0, 0, w, h);
            buffer.end();

            flipped = flipVertically(raw);

            File parent = file.getParentFile();
            if(parent != null && !parent.exists()) parent.mkdirs();
            com.badlogic.gdx.graphics.PixmapIO.writePNG(
                com.badlogic.gdx.Gdx.files.absolute(file.getAbsolutePath()), flipped);
            return true;
        }catch(Exception e){
            System.err.println("DBG 蓝图预览图写出失败: " + e);
            return false;
        }finally{
            if(raw != null) raw.dispose();
            if(flipped != null) flipped.dispose();
        }
    }

    /**
     * 渲染并缓存蓝图预览图。参照原版 {@code Schematics.getBuffer}。
     * <p>画法：先把所有被占格子铺成一层半透明黑当投影（整体右下偏移一点），再按 1 格 = {@link #resolution} 像素
     * 把每个方块的贴图画上去。原版还多一道 shadowBuffer 中转，这里直接一层黑色方块代替，视觉等价。
     * <p>必须保存并恢复 batch 的投影/变换矩阵与颜色，否则会把当前帧的渲染状态带歪。
     * @return 预览 FBO；GL 上下文或图集未就绪时返回 null
     */
    public com.badlogic.gdx.graphics.glutils.FrameBuffer getBuffer(Schematic schematic){
        if(Vars.schematics == null) return null;
        com.badlogic.gdx.graphics.glutils.FrameBuffer cached = previews.get(schematic);
        if(cached != null) return cached;

        com.badlogic.gdx.graphics.g2d.SpriteBatch batch = com.phoenix.game.core.Core.batch;
        if(batch == null || com.phoenix.game.core.Core.atlas == null) return null;

        com.badlogic.gdx.graphics.g2d.TextureRegion white = com.phoenix.game.core.Core.atlas.findRegion("white");
        if(white == null) return null;

        int w = (schematic.width + padding) * resolution;
        int h = (schematic.height + padding) * resolution;
        if(w <= 0 || h <= 0) return null;

        com.badlogic.gdx.graphics.glutils.FrameBuffer buffer = null;
        com.badlogic.gdx.math.Matrix4 oldProj = new com.badlogic.gdx.math.Matrix4(batch.getProjectionMatrix());
        com.badlogic.gdx.math.Matrix4 oldTrans = new com.badlogic.gdx.math.Matrix4(batch.getTransformMatrix());
        com.badlogic.gdx.graphics.Color oldColor = batch.getColor().cpy();
        int oldSrcFunc = batch.getBlendSrcFunc(), oldDstFunc = batch.getBlendDstFunc();
        boolean oldBlending = batch.isBlendingEnabled();
        //调用方可能正处在 batch.begin() 状态（例如 UI 里画到一半），先收尾再自己开一轮
        boolean wasDrawing = batch.isDrawing();
        if(wasDrawing) batch.end();

        try{
            buffer = new com.badlogic.gdx.graphics.glutils.FrameBuffer(
                com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888, w, h, false);

            buffer.begin();
            com.badlogic.gdx.Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
            com.badlogic.gdx.Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);
            batch.begin();

            //投影：1 单位 = 1 格，原点左下，留白 padding/2
            batch.setProjectionMatrix(new com.badlogic.gdx.math.Matrix4()
                .setToOrtho2D(0f, 0f, schematic.width + padding, schematic.height + padding));
            batch.setTransformMatrix(new com.badlogic.gdx.math.Matrix4().idt());
            batch.enableBlending();
            batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);

            //1) 投影：每个被占格子画一个黑方块，整体向右下偏 0.35 格
            batch.setColor(0f, 0f, 0f, 0.45f);
            for(int i = 0; i < schematic.tiles.size; i++){
                Schematic.Stile stile = schematic.tiles.get(i);
                int size = stile.block.size;
                int offsetx = -(size - 1) / 2, offsety = -(size - 1) / 2;

                for(int dx = 0; dx < size; dx++){
                    for(int dy = 0; dy < size; dy++){
                        float cx = stile.x + dx + offsetx + 0.5f + 0.35f;
                        float cy = stile.y + dy + offsety + 0.5f + 0.35f;
                        batch.draw(white, padding / 2f + cx - 0.5f, padding / 2f + (schematic.height - cy) - 0.5f, 1f, 1f);
                    }
                }
            }

            //2) 方块本体
            batch.setColor(1f, 1f, 1f, 1f);
            for(int i = 0; i < schematic.tiles.size; i++){
                Schematic.Stile stile = schematic.tiles.get(i);
                Block block = stile.block;
                com.badlogic.gdx.graphics.g2d.TextureRegion region = block.region;
                if(region == null) continue;

                int size = block.size;
                int offsetx = -(size - 1) / 2, offsety = -(size - 1) / 2;

                //方块几何中心（格坐标，y 自顶向下）→ 投影坐标（y 自底向上）
                float ccx = stile.x + offsetx + size / 2f;
                float ccy = stile.y + offsety + size / 2f;
                float px = padding / 2f + ccx;
                float py = padding / 2f + (schematic.height - ccy);

                batch.draw(region, px - size / 2f, py - size / 2f, size / 2f, size / 2f,
                    size, size, 1f, 1f, block.rotate ? stile.rotation * 90f : 0f);
            }

            batch.flush();
            batch.end();
            buffer.end();
        }catch(Exception e){
            //GL 未就绪（如 headless）时不要炸掉调用方，退化成"没有预览"
            System.err.println("DBG 蓝图预览图生成失败: " + e);
            if(buffer != null){
                buffer.dispose();
                buffer = null;
            }
        }finally{
            batch.setProjectionMatrix(oldProj);
            batch.setTransformMatrix(oldTrans);
            batch.setColor(oldColor);
            if(oldBlending){
                batch.enableBlending();
            }else{
                batch.disableBlending();
            }
            batch.setBlendFunction(oldSrcFunc, oldDstFunc);
            if(wasDrawing && !batch.isDrawing()) batch.begin();
        }

        if(buffer != null) previews.put(schematic, buffer);
        return buffer;
    }

    /** 释放全部预览图 FBO（切场景/重载蓝图时调用）。 */
    public void disposePreviews(){
        for(com.badlogic.gdx.graphics.glutils.FrameBuffer buffer : previews.values()){
            buffer.dispose();
        }
        previews.clear();
    }

    /** @return 上下翻转后的像素图（FBO 读回是自下而上的）。 */
    private static com.badlogic.gdx.graphics.Pixmap flipVertically(com.badlogic.gdx.graphics.Pixmap src){
        int w = src.getWidth(), h = src.getHeight();
        com.badlogic.gdx.graphics.Pixmap dst = new com.badlogic.gdx.graphics.Pixmap(w, h, src.getFormat());

        java.nio.ByteBuffer sb = src.getPixels(), db = dst.getPixels();
        int stride = w * 4;
        byte[] row = new byte[stride];
        for(int y = 0; y < h; y++){
            sb.position(y * stride);
            sb.get(row);
            db.position((h - 1 - y) * stride);
            db.put(row);
        }
        return dst;
    }

    /** @return 按名字找方块；找不到返回 null。 */
    private static Block blockByName(String name){
        for(int i = 0; i < Blocks.all.size; i++){
            if(Blocks.all.get(i).name.equals(name)) return Blocks.all.get(i);
        }
        return null;
    }
}
